package com.paygate.smsfwd.service

import android.content.Context
import com.paygate.smsfwd.data.local.dao.QueuedSmsDao
import com.paygate.smsfwd.data.local.entity.QueuedSms
import com.paygate.smsfwd.data.prefs.PreferenceManager
import com.paygate.smsfwd.data.remote.ApiClient
import com.paygate.smsfwd.data.remote.model.WebhookRequest
import com.paygate.smsfwd.data.security.KeystoreSecretManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Random

class QueueSyncManager(
    private val context: Context,
    private val dao: QueuedSmsDao,
    private val prefs: PreferenceManager,
    private val secretManager: KeystoreSecretManager,
    private val alertCallback: ((String, String) -> Unit)? = null
) {
    private val syncMutex = Mutex()
    private val random = Random()

    suspend fun drainQueue(): SyncResult = withContext(Dispatchers.IO) {
        if (!prefs.isPaired) {
            return@withContext SyncResult.NotPaired
        }

        if (prefs.isAuthRevoked) {
            return@withContext SyncResult.AuthRevoked
        }

        val token = secretManager.getDeviceToken()
        if (token.isNullOrBlank()) {
            prefs.isAuthRevoked = true
            alertCallback?.invoke(
                "PayGate Auth Error",
                "Device token missing or revoked. Please re-pair your device."
            )
            return@withContext SyncResult.AuthRevoked
        }

        syncMutex.withLock {
            val pendingList = dao.getDeliverableSms(
                statuses = listOf(QueuedSms.STATUS_PENDING, QueuedSms.STATUS_STALE),
                limit = 50
            )
            if (pendingList.isEmpty()) {
                return@withContext SyncResult.Success(0)
            }

            val apiService = ApiClient.getService(prefs.apiBaseUrl)
            val now = System.currentTimeMillis()
            var processedCount = 0

            for (sms in pendingList) {
                // FR-15: Block forwarding for a SIM slot with no registered wallet and alert operator
                if (!prefs.isSlotMapped(sms.simSlot)) {
                    dao.update(
                        sms.copy(
                            status = QueuedSms.STATUS_FAILED_PERMANENT,
                            errorMessage = "SIM Slot ${sms.simSlot} has no registered wallet"
                        )
                    )
                    alertCallback?.invoke(
                        "Unmapped SIM Slot",
                        "SMS arrived on SIM Slot ${sms.simSlot} with no registered wallet. Forwarding blocked."
                    )
                    continue
                }

                // FR-10: Retain until resolved: rows older than 24h become stale, stay visible and keep retrying
                val isStaleNow = (now - sms.receivedAt > 86_400_000L)
                val effectiveStatus = if (isStaleNow) QueuedSms.STATUS_STALE else sms.status

                // FR-8: Exponential backoff with jitter: base 2 s, cap 15 min; reset on success or network change
                if (sms.attemptCount > 0 && sms.lastAttemptAt != null) {
                    val backoffBaseSeconds = (2 * (1 shl (sms.attemptCount - 1).coerceAtMost(9)))
                        .coerceAtMost(15 * 60)
                    val jitter = random.nextInt(3)
                    val requiredDelayMs = (backoffBaseSeconds + jitter) * 1000L
                    if (now - sms.lastAttemptAt < requiredDelayMs) {
                        // Skip this item for now, wait for backoff window
                        continue
                    }
                }

                val request = WebhookRequest(
                    payloadVersion = 3,
                    sender = sms.sender,
                    body = sms.body,
                    clientMsgId = sms.clientMsgId,
                    deviceSeq = sms.deviceSeq,
                    receivedAt = sms.receivedAt / 1000L,
                    simSlot = sms.simSlot,
                    refHint = sms.refHint,
                    parserVersion = "app-3.0.0",
                    source = sms.source,
                    appVersion = "3.0.0"
                )

                val startTime = System.currentTimeMillis()
                try {
                    val response = apiService.postSms(
                        bearerToken = "Bearer $token",
                        request = request
                    )

                    val latency = System.currentTimeMillis() - startTime
                    val code = response.code()

                    when {
                        // 200 stored, ignored or duplicate -> Mark delivered
                        response.isSuccessful -> {
                            val body = response.body()
                            val finalStatus = if (body?.ignored == true) QueuedSms.STATUS_IGNORED else QueuedSms.STATUS_DELIVERED

                            dao.update(
                                sms.copy(
                                    status = finalStatus,
                                    deliveredAt = System.currentTimeMillis(),
                                    serverAckTrxId = body?.trxId ?: sms.trxId,
                                    deliveryLatencyMs = latency,
                                    errorMessage = null
                                )
                            )
                            prefs.lastSyncAt = System.currentTimeMillis()

                            // Track reference coverage stats
                            if (!sms.refHint.isNullOrBlank()) {
                                prefs.refSeenCount += 1
                            } else {
                                prefs.noRefCount += 1
                            }

                            processedCount++
                        }

                        // 401 or 403: Token revoked or merchant-side hold -> Pause, keep queue, show blocking re-pair screen
                        code == 401 || code == 403 -> {
                            dao.update(
                                sms.copy(
                                    status = QueuedSms.STATUS_AUTH_PAUSED,
                                    errorMessage = "HTTP $code: Token revoked or merchant hold. Delivery paused.",
                                    lastAttemptAt = System.currentTimeMillis()
                                )
                            )
                            prefs.isAuthRevoked = true
                            alertCallback?.invoke(
                                "PayGate Auth Paused",
                                "Device token was revoked or paused ($code). Re-pair to resume processing."
                            )
                            return@withLock SyncResult.AuthRevoked
                        }

                        // 413: Body too large (capped locally at 1 KB) -> failed_permanent
                        code == 413 -> {
                            dao.update(
                                sms.copy(
                                    status = QueuedSms.STATUS_FAILED_PERMANENT,
                                    lastAttemptAt = System.currentTimeMillis(),
                                    errorMessage = "HTTP 413: SMS body exceeded 1 KB"
                                )
                            )
                        }

                        // 422 and other 4xx: Retry 3 times over 1h, then failed_permanent with a visible Needs support badge
                        code == 422 || (code in 400..499 && code != 408 && code != 409 && code != 423 && code != 425 && code != 429) -> {
                            val newAttempts = sms.attemptCount + 1
                            val newStatus = if (newAttempts >= 3) QueuedSms.STATUS_FAILED_PERMANENT else effectiveStatus
                            dao.update(
                                sms.copy(
                                    status = newStatus,
                                    attemptCount = newAttempts,
                                    lastAttemptAt = System.currentTimeMillis(),
                                    errorMessage = if (newAttempts >= 3) "Needs support: Server rejected ($code)" else "HTTP $code: will retry (${newAttempts}/3)"
                                )
                            )
                        }

                        // Transient (408, 409, 423, 425, 429, 5xx) -> Retry with backoff
                        else -> {
                            dao.update(
                                sms.copy(
                                    status = effectiveStatus,
                                    attemptCount = sms.attemptCount + 1,
                                    lastAttemptAt = System.currentTimeMillis(),
                                    errorMessage = "HTTP $code: Transient error, will retry"
                                )
                            )
                        }
                    }
                } catch (e: IOException) {
                    // Network failure (offline, timeout, DNS error)
                    dao.update(
                        sms.copy(
                            status = effectiveStatus,
                            attemptCount = sms.attemptCount + 1,
                            lastAttemptAt = System.currentTimeMillis(),
                            errorMessage = "Network offline: ${e.message ?: "Connection failed"}"
                        )
                    )
                } catch (e: Exception) {
                    dao.update(
                        sms.copy(
                            status = effectiveStatus,
                            attemptCount = sms.attemptCount + 1,
                            lastAttemptAt = System.currentTimeMillis(),
                            errorMessage = "Error: ${e.message ?: "Unknown"}"
                        )
                    )
                }
            }

            // Purge delivered rows older than 7 days
            val sevenDaysAgo = System.currentTimeMillis() - (7 * 86_400_000L)
            dao.purgeOldDelivered(QueuedSms.STATUS_DELIVERED, sevenDaysAgo)

            SyncResult.Success(processedCount)
        }
    }

    sealed class SyncResult {
        data class Success(val itemsDelivered: Int) : SyncResult()
        object NotPaired : SyncResult()
        object AuthRevoked : SyncResult()
        data class Error(val message: String) : SyncResult()
    }
}
