package com.example.data.prefs

import android.content.Context
import android.content.SharedPreferences
import com.example.data.remote.ApiClient
import com.example.data.remote.model.WalletSlotInfo
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PreferenceManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("paygate_forwarder_prefs", Context.MODE_PRIVATE)

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val walletListType = Types.newParameterizedType(List::class.java, WalletSlotInfo::class.java)
    private val walletAdapter = moshi.adapter<List<WalletSlotInfo>>(walletListType)

    private val _isPairedFlow = MutableStateFlow(isPaired)
    val isPairedFlow: StateFlow<Boolean> = _isPairedFlow.asStateFlow()

    private val _allowedSendersFlow = MutableStateFlow(allowedSenders)
    val allowedSendersFlow: StateFlow<Set<String>> = _allowedSendersFlow.asStateFlow()

    private val _languageFlow = MutableStateFlow(language)
    val languageFlow: StateFlow<String> = _languageFlow.asStateFlow()

    private val _walletsFlow = MutableStateFlow(getWallets())
    val walletsFlow: StateFlow<List<WalletSlotInfo>> = _walletsFlow.asStateFlow()

    private val _isKioskFlow = MutableStateFlow(isKioskMode)
    val isKioskFlow: StateFlow<Boolean> = _isKioskFlow.asStateFlow()

    init {
        ApiClient.deviceIdProvider = { deviceId }
        ApiClient.mockServerEnabled = mockServerMode
    }

    var isPaired: Boolean
        get() = prefs.getBoolean(KEY_IS_PAIRED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_IS_PAIRED, value).apply()
            _isPairedFlow.value = value
        }

    var apiBaseUrl: String
        get() = prefs.getString(KEY_API_BASE_URL, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL
        set(value) {
            val normalized = if (value.endsWith("/")) value.dropLast(1) else value
            prefs.edit().putString(KEY_API_BASE_URL, normalized).apply()
        }

    var deviceId: String
        get() = prefs.getString(KEY_DEVICE_ID, "dev_98213") ?: "dev_98213"
        set(value) {
            prefs.edit().putString(KEY_DEVICE_ID, value).apply()
            ApiClient.deviceIdProvider = { value }
        }

    var deviceName: String
        get() = prefs.getString(KEY_DEVICE_NAME, android.os.Build.MODEL) ?: android.os.Build.MODEL
        set(value) = prefs.edit().putString(KEY_DEVICE_NAME, value).apply()

    var allowedSenders: Set<String>
        get() = prefs.getStringSet(KEY_ALLOWED_SENDERS, DEFAULT_ALLOWED_SENDERS) ?: DEFAULT_ALLOWED_SENDERS
        set(value) {
            prefs.edit().putStringSet(KEY_ALLOWED_SENDERS, value).apply()
            _allowedSendersFlow.value = value
        }

    var heartbeatSeconds: Long
        get() = prefs.getLong(KEY_HEARTBEAT_SECONDS, 120L) // PRD v3.0: 120s
        set(value) = prefs.edit().putLong(KEY_HEARTBEAT_SECONDS, value).apply()

    var lastHeartbeatAt: Long
        get() = prefs.getLong(KEY_LAST_HEARTBEAT_AT, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_HEARTBEAT_AT, value).apply()

    var lastSyncAt: Long
        get() = prefs.getLong(KEY_LAST_SYNC_AT, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_SYNC_AT, value).apply()

    var isServiceEnabled: Boolean
        get() = prefs.getBoolean(KEY_SERVICE_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_SERVICE_ENABLED, value).apply()

    var language: String
        get() = prefs.getString(KEY_LANGUAGE, "en") ?: "en"
        set(value) {
            prefs.edit().putString(KEY_LANGUAGE, value).apply()
            _languageFlow.value = value
        }

    var isAuthRevoked: Boolean
        get() = prefs.getBoolean(KEY_AUTH_REVOKED, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTH_REVOKED, value).apply()

    var backfillEnabled: Boolean
        get() = prefs.getBoolean(KEY_BACKFILL_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_BACKFILL_ENABLED, value).apply()

    var deviceSeq: Long
        get() = prefs.getLong(KEY_DEVICE_SEQ, 1000L)
        set(value) = prefs.edit().putLong(KEY_DEVICE_SEQ, value).apply()

    @Synchronized
    fun nextDeviceSeq(): Long {
        val next = deviceSeq + 1
        deviceSeq = next
        return next
    }

    var appPin: String?
        get() = prefs.getString(KEY_APP_PIN, null)
        set(value) = prefs.edit().putString(KEY_APP_PIN, value).apply()

    val isPinSet: Boolean
        get() = !appPin.isNullOrBlank()

    var isKioskMode: Boolean
        get() = prefs.getBoolean(KEY_IS_KIOSK_MODE, false)
        set(value) {
            prefs.edit().putBoolean(KEY_IS_KIOSK_MODE, value).apply()
            _isKioskFlow.value = value
        }

    var mockServerMode: Boolean
        get() = prefs.getBoolean(KEY_MOCK_SERVER_MODE, true) // Simulated mock server default
        set(value) {
            prefs.edit().putBoolean(KEY_MOCK_SERVER_MODE, value).apply()
            ApiClient.mockServerEnabled = value
        }

    var refSeenCount: Int
        get() = prefs.getInt(KEY_REF_SEEN_COUNT, 0)
        set(value) = prefs.edit().putInt(KEY_REF_SEEN_COUNT, value).apply()

    var noRefCount: Int
        get() = prefs.getInt(KEY_NO_REF_COUNT, 0)
        set(value) = prefs.edit().putInt(KEY_NO_REF_COUNT, value).apply()

    var isMaintenanceMode: Boolean
        get() = prefs.getBoolean(KEY_MAINTENANCE_MODE, false)
        set(value) = prefs.edit().putBoolean(KEY_MAINTENANCE_MODE, value).apply()

    fun getWallets(): List<WalletSlotInfo> {
        val json = prefs.getString(KEY_WALLETS, null) ?: return listOf(
            WalletSlotInfo(1, "bkash", "017•••••78"),
            WalletSlotInfo(2, "nagad", "018•••••44")
        )
        return try {
            walletAdapter.fromJson(json) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveWallets(wallets: List<WalletSlotInfo>) {
        val json = walletAdapter.toJson(wallets)
        prefs.edit().putString(KEY_WALLETS, json).apply()
        _walletsFlow.value = wallets
    }

    fun isSlotMapped(slot: Int): Boolean {
        val wallets = getWallets()
        return wallets.any { it.slot == slot }
    }

    fun getWalletForSlot(slot: Int): WalletSlotInfo? {
        return getWallets().firstOrNull { it.slot == slot }
    }

    fun resetPairing() {
        prefs.edit()
            .putBoolean(KEY_IS_PAIRED, false)
            .remove(KEY_DEVICE_ID)
            .remove(KEY_WALLETS)
            .putBoolean(KEY_AUTH_REVOKED, false)
            .apply()
        _isPairedFlow.value = false
        _walletsFlow.value = emptyList()
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://api.paygate.com"
        val DEFAULT_ALLOWED_SENDERS = setOf("bKash", "NAGAD", "16216")

        private const val KEY_IS_PAIRED = "is_paired"
        private const val KEY_API_BASE_URL = "api_base_url"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_DEVICE_NAME = "device_name"
        private const val KEY_ALLOWED_SENDERS = "allowed_senders"
        private const val KEY_HEARTBEAT_SECONDS = "heartbeat_seconds"
        private const val KEY_LAST_HEARTBEAT_AT = "last_heartbeat_at"
        private const val KEY_LAST_SYNC_AT = "last_sync_at"
        private const val KEY_SERVICE_ENABLED = "service_enabled"
        private const val KEY_LANGUAGE = "app_language"
        private const val KEY_AUTH_REVOKED = "auth_revoked"
        private const val KEY_BACKFILL_ENABLED = "backfill_enabled"
        private const val KEY_DEVICE_SEQ = "device_seq"
        private const val KEY_WALLETS = "wallet_registry_v3"
        private const val KEY_APP_PIN = "app_security_pin"
        private const val KEY_IS_KIOSK_MODE = "is_kiosk_mode"
        private const val KEY_MOCK_SERVER_MODE = "mock_server_mode"
        private const val KEY_REF_SEEN_COUNT = "ref_seen_count"
        private const val KEY_NO_REF_COUNT = "no_ref_count"
        private const val KEY_MAINTENANCE_MODE = "maintenance_mode"
    }
}
