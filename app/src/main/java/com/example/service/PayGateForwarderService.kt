package com.paygate.smsfwd.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.StatFs
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.paygate.smsfwd.MainActivity
import com.paygate.smsfwd.R
import com.paygate.smsfwd.data.local.db.AppDatabase
import com.paygate.smsfwd.data.prefs.PreferenceManager
import com.paygate.smsfwd.data.remote.ApiClient
import com.paygate.smsfwd.data.remote.model.HeartbeatRequest
import com.paygate.smsfwd.data.remote.model.SimSlotState
import com.paygate.smsfwd.data.security.KeystoreSecretManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PayGateForwarderService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
    private lateinit var prefs: PreferenceManager
    private lateinit var syncManager: QueueSyncManager
    private lateinit var database: AppDatabase
    private lateinit var secretManager: KeystoreSecretManager

    private var heartbeatJob: Job? = null
    private var queueObservationJob: Job? = null
    private var pendingCount = 0
    private var staleCount = 0
    private var failedCount = 0

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getInstance(applicationContext)
        prefs = PreferenceManager(applicationContext)
        secretManager = KeystoreSecretManager(applicationContext)

        syncManager = QueueSyncManager(
            context = applicationContext,
            dao = database.queuedSmsDao(),
            prefs = prefs,
            secretManager = secretManager,
            alertCallback = { title, message ->
                showLocalAlertNotification(title, message)
            }
        )

        createNotificationChannels()
        startAsForeground()

        observeQueue()
        startPeriodicHeartbeat()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_TRIGGER_DRAIN -> {
                serviceScope.launch {
                    syncManager.drainQueue()
                }
            }
            ACTION_HEARTBEAT -> {
                serviceScope.launch {
                    sendHeartbeatInternal()
                }
            }
            else -> {
                serviceScope.launch {
                    syncManager.drainQueue()
                }
            }
        }
        return START_STICKY
    }

    private fun startAsForeground() {
        val notification = buildForegroundNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun observeQueue() {
        queueObservationJob?.cancel()
        queueObservationJob = serviceScope.launch {
            launch {
                database.queuedSmsDao().observePendingCount().collect { count ->
                    pendingCount = count
                    updateNotification()
                    if (count > 0 && prefs.isPaired && !prefs.isAuthRevoked) {
                        syncManager.drainQueue()
                    }
                }
            }
            launch {
                val cutoff = System.currentTimeMillis() - 86_400_000L
                database.queuedSmsDao().observeStaleCount(cutoff).collect { count ->
                    staleCount = count
                    updateNotification()
                }
            }
            launch {
                database.queuedSmsDao().observeFailedCount().collect { count ->
                    failedCount = count
                }
            }
        }
    }

    private fun startPeriodicHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = serviceScope.launch {
            while (isActive) {
                if (prefs.isPaired && !prefs.isAuthRevoked) {
                    sendHeartbeatInternal()
                }
                // PRD v3.0: 120s heartbeat
                val intervalMs = (prefs.heartbeatSeconds.coerceAtLeast(60L)) * 1000L
                delay(intervalMs)
            }
        }
    }

    private suspend fun sendHeartbeatInternal() {
        val token = secretManager.getDeviceToken() ?: return
        val batteryInfo = getBatteryInfo()
        val networkType = getNetworkType()
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        val isBatteryExempt = powerManager.isIgnoringBatteryOptimizations(packageName)
        val hasSmsPerm = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECEIVE_SMS
        ) == PackageManager.PERMISSION_GRANTED
        val hasNotifPerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else true

        val freeStorageMb = try {
            val stat = StatFs(filesDir.absolutePath)
            (stat.availableBytes / (1024 * 1024))
        } catch (_: Exception) { 1024L }

        // Check low storage alert (< 200 MB)
        if (freeStorageMb < 200) {
            showLocalAlertNotification("Low Storage Warning", "Device free storage is critically low (${freeStorageMb}MB).")
        }

        val simState = listOf(
            SimSlotState(1, true),
            SimSlotState(2, prefs.getWallets().any { it.slot == 2 })
        )

        val request = HeartbeatRequest(
            battery = batteryInfo.level,
            charging = batteryInfo.isCharging,
            network = networkType,
            queueDepth = pendingCount,
            staleCount = staleCount,
            failedCount = failedCount,
            permSms = hasSmsPerm,
            permNotifications = hasNotifPerm,
            batteryExempt = isBatteryExempt,
            autostartConfirmed = true,
            clockSkewMs = 0L,
            freeStorageMb = freeStorageMb,
            simState = simState,
            refSeenCount = prefs.refSeenCount,
            noRefCount = prefs.noRefCount,
            lastSmsAt = prefs.lastSyncAt,
            deviceSeq = prefs.deviceSeq,
            appVersion = "3.0.0"
        )

        try {
            val apiService = ApiClient.getService(prefs.apiBaseUrl)
            val response = apiService.sendHeartbeat("Bearer $token", request)
            if (response.isSuccessful) {
                prefs.lastHeartbeatAt = System.currentTimeMillis()
                val body = response.body()

                // Dynamic allowlist update
                val newSenders = body?.allowedSenders
                if (!newSenders.isNullOrEmpty()) {
                    prefs.allowedSenders = newSenders.take(10).toSet()
                }

                // Token rotation (FR-13)
                val rotatedToken = body?.rotateToken
                if (!rotatedToken.isNullOrBlank()) {
                    secretManager.saveDeviceToken(rotatedToken)
                }

                // Maintenance mode
                if (body?.maintenance == true) {
                    prefs.isMaintenanceMode = true
                }

                // Remote commands (FR-27)
                body?.commands?.forEach { cmd ->
                    when (cmd) {
                        "FLUSH_QUEUE" -> syncManager.drainQueue()
                        "REVOKE_TOKEN" -> {
                            prefs.isAuthRevoked = true
                            showLocalAlertNotification("PayGate Revoked", "Device was remotely revoked from Admin console.")
                        }
                        "MAINTENANCE" -> prefs.isMaintenanceMode = true
                    }
                }
            } else if (response.code() == 401 || response.code() == 403) {
                prefs.isAuthRevoked = true
                showLocalAlertNotification(
                    "PayGate Disconnected",
                    "Device token was revoked or merchant hold. Delivery paused."
                )
            }
        } catch (_: Exception) {
            // Transient network failure
        }
    }

    private fun buildForegroundNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val syncIntent = Intent(this, PayGateForwarderService::class.java).apply {
            action = ACTION_TRIGGER_DRAIN
        }
        val syncPendingIntent = PendingIntent.getService(
            this,
            1,
            syncIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val statusText = when {
            prefs.isAuthRevoked -> "⚠️ Token Revoked — Tap to re-pair"
            !prefs.isPaired -> "⚠️ Not Paired — Tap to connect"
            staleCount > 0 -> "⚠️ $pendingCount pending ($staleCount stale >24h)"
            pendingCount > 0 -> "📤 $pendingCount SMS in outbox queue"
            else -> "✓ Listening for bKash, Nagad & Rocket"
        }

        val lastSyncStr = if (prefs.lastSyncAt > 0) {
            val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(prefs.lastSyncAt))
            " • Synced $time"
        } else ""

        return NotificationCompat.Builder(this, CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.paygate_logo_1791826977584)
            .setContentTitle("PayGate is running")
            .setContentText(statusText + lastSyncStr)
            .setStyle(NotificationCompat.BigTextStyle().bigText(statusText + lastSyncStr))
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .addAction(R.drawable.paygate_logo_1791826977584, "Sync Now", syncPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification() {
        val notification = buildForegroundNotification()
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun showLocalAlertNotification(title: String, message: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            2,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.paygate_logo_1791826977584)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(ALERT_NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_SERVICE,
                "PayGate Forwarder Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the SMS forwarder running reliably in the background"
                setShowBadge(false)
            }

            val alertsChannel = NotificationChannel(
                CHANNEL_ALERTS,
                "PayGate Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when queue is stuck or authorization is revoked"
                setShowBadge(true)
            }

            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(serviceChannel)
            manager.createNotificationChannel(alertsChannel)
        }
    }

    private fun getBatteryInfo(): BatteryStatus {
        val intent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return BatteryStatus(100, false)
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
        val pct = if (level >= 0 && scale > 0) (level * 100) / scale else 100
        return BatteryStatus(pct, isCharging)
    }

    private fun getNetworkType(): String {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = cm.activeNetwork ?: return "none"
        val caps = cm.getNetworkCapabilities(activeNetwork) ?: return "none"
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            else -> "other"
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    private data class BatteryStatus(val level: Int, val isCharging: Boolean)

    companion object {
        const val NOTIFICATION_ID = 1001
        const val ALERT_NOTIFICATION_ID = 1002
        const val CHANNEL_SERVICE = "paygate_forwarder_channel"
        const val CHANNEL_ALERTS = "paygate_alerts_channel"

        const val ACTION_START = "com.paygate.smsfwd.service.ACTION_START"
        const val ACTION_STOP = "com.paygate.smsfwd.service.ACTION_STOP"
        const val ACTION_TRIGGER_DRAIN = "com.paygate.smsfwd.service.ACTION_TRIGGER_DRAIN"
        const val ACTION_HEARTBEAT = "com.paygate.smsfwd.service.ACTION_HEARTBEAT"

        fun start(context: Context) {
            val intent = Intent(context, PayGateForwarderService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun triggerDrain(context: Context) {
            val intent = Intent(context, PayGateForwarderService::class.java).apply {
                action = ACTION_TRIGGER_DRAIN
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun triggerHeartbeat(context: Context) {
            val intent = Intent(context, PayGateForwarderService::class.java).apply {
                action = ACTION_HEARTBEAT
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, PayGateForwarderService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
