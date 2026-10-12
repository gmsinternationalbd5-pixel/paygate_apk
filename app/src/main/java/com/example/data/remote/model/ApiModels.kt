package com.example.data.remote.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class WebhookRequest(
    @Json(name = "payload_version")
    val payloadVersion: Int = 3,

    @Json(name = "sender")
    val sender: String,

    @Json(name = "body")
    val body: String,

    @Json(name = "client_msg_id")
    val clientMsgId: String,

    @Json(name = "device_seq")
    val deviceSeq: Long,

    @Json(name = "received_at")
    val receivedAt: Long,

    @Json(name = "sim_slot")
    val simSlot: Int,

    @Json(name = "ref_hint")
    val refHint: String? = null,

    @Json(name = "parser_version")
    val parserVersion: String = "app-3.0.0",

    @Json(name = "source")
    val source: String = "live",

    @Json(name = "app_version")
    val appVersion: String = "3.0.0"
)

@JsonClass(generateAdapter = true)
data class WebhookResponse(
    @Json(name = "stored")
    val stored: Boolean? = null,

    @Json(name = "ignored")
    val ignored: Boolean? = null,

    @Json(name = "duplicate")
    val duplicate: Boolean? = null,

    @Json(name = "trx_id")
    val trxId: String? = null,

    @Json(name = "error")
    val error: String? = null
)

@JsonClass(generateAdapter = true)
data class WalletSlotInfo(
    @Json(name = "slot")
    val slot: Int,

    @Json(name = "provider")
    val provider: String,

    @Json(name = "number_masked")
    val numberMasked: String
)

@JsonClass(generateAdapter = true)
data class PairRequest(
    @Json(name = "pairing_code")
    val pairingCode: String,

    @Json(name = "device_name")
    val deviceName: String,

    @Json(name = "model")
    val model: String,

    @Json(name = "android")
    val androidVersion: Int,

    @Json(name = "app_version")
    val appVersion: String = "3.0.0"
)

@JsonClass(generateAdapter = true)
data class PairResponse(
    @Json(name = "device_id")
    val deviceId: String,

    @Json(name = "device_token")
    val deviceToken: String,

    @Json(name = "allowed_senders")
    val allowedSenders: List<String>? = null,

    @Json(name = "heartbeat_seconds")
    val heartbeatSeconds: Long? = 120,

    @Json(name = "wallets")
    val wallets: List<WalletSlotInfo>? = null,

    @Json(name = "server_time")
    val serverTime: Long? = null
)

@JsonClass(generateAdapter = true)
data class SimSlotState(
    @Json(name = "slot")
    val slot: Int,

    @Json(name = "present")
    val present: Boolean
)

@JsonClass(generateAdapter = true)
data class HeartbeatRequest(
    @Json(name = "battery")
    val battery: Int,

    @Json(name = "charging")
    val charging: Boolean,

    @Json(name = "network")
    val network: String,

    @Json(name = "queue_depth")
    val queueDepth: Int,

    @Json(name = "stale_count")
    val staleCount: Int = 0,

    @Json(name = "failed_count")
    val failedCount: Int = 0,

    @Json(name = "perm_sms")
    val permSms: Boolean,

    @Json(name = "perm_notifications")
    val permNotifications: Boolean = true,

    @Json(name = "battery_exempt")
    val batteryExempt: Boolean,

    @Json(name = "autostart_confirmed")
    val autostartConfirmed: Boolean = true,

    @Json(name = "clock_skew_ms")
    val clockSkewMs: Long = 0,

    @Json(name = "free_storage_mb")
    val freeStorageMb: Long = 1024,

    @Json(name = "sim_state")
    val simState: List<SimSlotState> = emptyList(),

    @Json(name = "ref_seen_count")
    val refSeenCount: Int = 0,

    @Json(name = "no_ref_count")
    val noRefCount: Int = 0,

    @Json(name = "last_sms_at")
    val lastSmsAt: Long = 0,

    @Json(name = "device_seq")
    val deviceSeq: Long = 0,

    @Json(name = "app_version")
    val appVersion: String = "3.0.0"
)

@JsonClass(generateAdapter = true)
data class HeartbeatResponse(
    @Json(name = "server_time")
    val serverTime: Long? = null,

    @Json(name = "allowed_senders")
    val allowedSenders: List<String>? = null,

    @Json(name = "rotate_token")
    val rotateToken: String? = null,

    @Json(name = "min_app_version")
    val minAppVersion: String? = null,

    @Json(name = "maintenance")
    val maintenance: Boolean? = false,

    @Json(name = "commands")
    val commands: List<String>? = null
)
