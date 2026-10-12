package com.paygate.smsfwd.ui.viewmodel

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.paygate.smsfwd.PayGateApplication
import com.paygate.smsfwd.data.local.entity.QueuedSms
import com.paygate.smsfwd.data.prefs.PreferenceManager
import com.paygate.smsfwd.data.remote.ApiClient
import com.paygate.smsfwd.data.remote.model.PairRequest
import com.paygate.smsfwd.data.remote.model.WalletSlotInfo
import com.paygate.smsfwd.domain.filter.SmsFilter
import com.paygate.smsfwd.service.PayGateForwarderService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class PairingUiState {
    object Idle : PairingUiState()
    object InProgress : PairingUiState()
    data class Success(val message: String) : PairingUiState()
    data class Error(val message: String) : PairingUiState()
}

data class HealthStatus(
    val hasSmsPermission: Boolean = false,
    val hasNotificationPermission: Boolean = false,
    val isBatteryExempt: Boolean = false,
    val isNetworkConnected: Boolean = true
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as PayGateApplication
    private val database = app.database
    val prefs: PreferenceManager = app.prefs
    private val secretManager = app.secretManager
    private val dao = database.queuedSmsDao()

    val pendingCount: StateFlow<Int> = dao.observePendingCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val staleCount: StateFlow<Int> = dao.observeStaleCount(System.currentTimeMillis() - 86_400_000L)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val failedCount: StateFlow<Int> = dao.observeFailedCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val lastDeliveredSms: StateFlow<QueuedSms?> = dao.observeLastDelivered()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val recentLogs: StateFlow<List<QueuedSms>> = dao.observeRecentLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isPairedFlow: StateFlow<Boolean> = prefs.isPairedFlow
    val allowedSendersFlow: StateFlow<Set<String>> = prefs.allowedSendersFlow
    val languageFlow: StateFlow<String> = prefs.languageFlow
    val walletsFlow: StateFlow<List<WalletSlotInfo>> = prefs.walletsFlow
    val isKioskFlow: StateFlow<Boolean> = prefs.isKioskFlow

    private val _healthStatus = MutableStateFlow(HealthStatus())
    val healthStatus: StateFlow<HealthStatus> = _healthStatus.asStateFlow()

    private val _pairingState = MutableStateFlow<PairingUiState>(PairingUiState.Idle)
    val pairingState: StateFlow<PairingUiState> = _pairingState.asStateFlow()

    private val _isMaskDetails = MutableStateFlow(true)
    val isMaskDetails: StateFlow<Boolean> = _isMaskDetails.asStateFlow()

    private val _actionFeedback = MutableStateFlow<String?>(null)
    val actionFeedback: StateFlow<String?> = _actionFeedback.asStateFlow()

    init {
        refreshHealthStatus()
    }

    fun refreshHealthStatus() {
        val ctx = getApplication<Application>()
        val hasSms = ContextCompat.checkSelfPermission(
            ctx,
            Manifest.permission.RECEIVE_SMS
        ) == PackageManager.PERMISSION_GRANTED

        val hasNotif = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                ctx,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        val isExempt = pm.isIgnoringBatteryOptimizations(ctx.packageName)

        _healthStatus.value = HealthStatus(
            hasSmsPermission = hasSms,
            hasNotificationPermission = hasNotif,
            isBatteryExempt = isExempt,
            isNetworkConnected = true
        )
    }

    fun toggleMaskDetails() {
        _isMaskDetails.value = !_isMaskDetails.value
    }

    fun clearActionFeedback() {
        _actionFeedback.value = null
    }

    fun toggleLanguage() {
        val next = if (prefs.language == "en") "bn" else "en"
        prefs.language = next
    }

    fun toggleKioskMode() {
        prefs.isKioskMode = !prefs.isKioskMode
    }

    fun setAppPin(pin: String?) {
        prefs.appPin = pin
        _actionFeedback.value = if (pin != null) "Security PIN enabled" else "Security PIN removed"
    }

    fun triggerSyncNow() {
        PayGateForwarderService.triggerDrain(getApplication())
        _actionFeedback.value = "Queue sync initiated"
    }

    fun triggerHeartbeatNow() {
        PayGateForwarderService.triggerHeartbeat(getApplication())
        _actionFeedback.value = "Heartbeat sent to server"
    }

    fun retryFailedItem(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.retryItem(id)
            PayGateForwarderService.triggerDrain(getApplication())
            _actionFeedback.value = "Retrying item delivery"
        }
    }

    fun pairDevice(pairingCode: String, customBaseUrl: String?) {
        val code = pairingCode.trim()
        if (code.isBlank()) {
            _pairingState.value = PairingUiState.Error("Pairing code cannot be empty")
            return
        }

        _pairingState.value = PairingUiState.InProgress
        viewModelScope.launch {
            try {
                if (!customBaseUrl.isNullOrBlank()) {
                    prefs.apiBaseUrl = customBaseUrl.trim()
                }

                val apiService = ApiClient.getService(prefs.apiBaseUrl)
                val request = PairRequest(
                    pairingCode = code,
                    deviceName = prefs.deviceName,
                    model = Build.MODEL ?: "Android",
                    androidVersion = Build.VERSION.SDK_INT,
                    appVersion = "3.0.0"
                )

                val response = withContext(Dispatchers.IO) {
                    apiService.pairDevice(request)
                }

                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    secretManager.saveDeviceToken(body.deviceToken)
                    prefs.deviceId = body.deviceId
                    prefs.isPaired = true
                    prefs.isAuthRevoked = false
                    if (!body.allowedSenders.isNullOrEmpty()) {
                        prefs.allowedSenders = body.allowedSenders.toSet()
                    }
                    if (body.heartbeatSeconds != null) {
                        prefs.heartbeatSeconds = body.heartbeatSeconds
                    }
                    if (!body.wallets.isNullOrEmpty()) {
                        prefs.saveWallets(body.wallets)
                    }

                    // Start background forwarder service
                    PayGateForwarderService.start(getApplication())
                    _pairingState.value = PairingUiState.Success("Successfully paired with PayGate!")
                } else {
                    val errCode = response.code()
                    val errMsg = when (errCode) {
                        400 -> "Invalid pairing code (code_invalid)"
                        409 -> "Pairing code already used (code_used)"
                        410 -> "Pairing code expired (code_expired)"
                        429 -> "Too many pairing attempts. Locked for 15 minutes."
                        else -> "Pairing failed: HTTP $errCode ${response.message()}"
                    }
                    _pairingState.value = PairingUiState.Error(errMsg)
                }
            } catch (e: Exception) {
                _pairingState.value = PairingUiState.Error(
                    "Network error: ${e.localizedMessage ?: "Unable to connect"}"
                )
            }
        }
    }

    fun quickPairLocalDev(mockToken: String = "dev_token_${System.currentTimeMillis()}") {
        secretManager.saveDeviceToken(mockToken)
        prefs.deviceId = "dev_phone_101"
        prefs.isPaired = true
        prefs.isAuthRevoked = false
        prefs.allowedSenders = PreferenceManager.DEFAULT_ALLOWED_SENDERS
        prefs.saveWallets(
            listOf(
                WalletSlotInfo(1, "bkash", "017•••••78"),
                WalletSlotInfo(2, "nagad", "018•••••44")
            )
        )
        PayGateForwarderService.start(getApplication())
        _pairingState.value = PairingUiState.Success("Device paired in simulated fleet mode!")
    }

    fun unpairDevice() {
        secretManager.clearSecrets()
        prefs.resetPairing()
        PayGateForwarderService.stop(getApplication())
        _pairingState.value = PairingUiState.Idle
        _actionFeedback.value = "Device disconnected"
    }

    fun simulateIncomingSms(sender: String, body: String, simSlot: Int = 1) {
        viewModelScope.launch(Dispatchers.IO) {
            val allowed = prefs.allowedSenders

            // Filter validation
            if (!SmsFilter.isAllowedSender(sender, allowed)) {
                _actionFeedback.value = "Dropped: '$sender' is not in allowed senders or is a phone number"
                return@launch
            }

            if (!SmsFilter.isWalletReceivedMessage(body)) {
                _actionFeedback.value = "Dropped: SMS does not contain payment keywords or is an OTP/exclusion"
                return@launch
            }

            val receivedAt = System.currentTimeMillis()
            val metadata = SmsFilter.extractMetadata(sender, body)
            val clientMsgId = SmsFilter.generateClientMsgId(sender, body, receivedAt, simSlot)
            val seq = prefs.nextDeviceSeq()

            val queuedSms = QueuedSms(
                clientMsgId = clientMsgId,
                deviceSeq = seq,
                sender = sender,
                body = body,
                receivedAt = receivedAt,
                simSlot = simSlot,
                refHint = metadata.refHint,
                source = QueuedSms.SOURCE_LIVE,
                status = QueuedSms.STATUS_PENDING,
                attemptCount = 0,
                trxId = metadata.trxId,
                amount = metadata.amount,
                provider = metadata.provider
            )

            val id = dao.insertOrIgnore(queuedSms)
            if (id != -1L) {
                PayGateForwarderService.triggerDrain(getApplication())
                val refStr = if (metadata.refHint != null) " [Ref: ${metadata.refHint}]" else ""
                _actionFeedback.value = "Queued: ${metadata.provider} ${metadata.amount}$refStr"
            } else {
                _actionFeedback.value = "Duplicate SMS ignored (duplicate client_msg_id)"
            }
        }
    }
}
