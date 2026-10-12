package com.example.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "queued_sms",
    indices = [
        Index(value = ["client_msg_id"], unique = true),
        Index(value = ["status"]),
        Index(value = ["received_at"]),
        Index(value = ["device_seq"])
    ]
)
data class QueuedSms(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "client_msg_id")
    val clientMsgId: String,

    @ColumnInfo(name = "device_seq")
    val deviceSeq: Long = 0,

    @ColumnInfo(name = "sender")
    val sender: String,

    @ColumnInfo(name = "body")
    val body: String,

    @ColumnInfo(name = "received_at")
    val receivedAt: Long,

    @ColumnInfo(name = "sim_slot")
    val simSlot: Int = 1,

    @ColumnInfo(name = "status")
    val status: String = STATUS_PENDING,

    @ColumnInfo(name = "ref_hint")
    val refHint: String? = null,

    @ColumnInfo(name = "source")
    val source: String = SOURCE_LIVE,

    @ColumnInfo(name = "attempt_count")
    val attemptCount: Int = 0,

    @ColumnInfo(name = "last_attempt_at")
    val lastAttemptAt: Long? = null,

    @ColumnInfo(name = "delivered_at")
    val deliveredAt: Long? = null,

    @ColumnInfo(name = "error_message")
    val errorMessage: String? = null,

    @ColumnInfo(name = "trx_id")
    val trxId: String? = null,

    @ColumnInfo(name = "amount")
    val amount: String? = null,

    @ColumnInfo(name = "provider")
    val provider: String? = null,

    @ColumnInfo(name = "server_ack_trx_id")
    val serverAckTrxId: String? = null,

    @ColumnInfo(name = "delivery_latency_ms")
    val deliveryLatencyMs: Long? = null
) {
    val isStale: Boolean
        get() = status == STATUS_STALE || (status == STATUS_PENDING && (System.currentTimeMillis() - receivedAt > 86_400_000L))

    companion object {
        const val STATUS_PENDING = "PENDING"
        const val STATUS_DELIVERED = "DELIVERED"
        const val STATUS_IGNORED = "IGNORED"
        const val STATUS_FAILED = "FAILED"
        const val STATUS_FAILED_PERMANENT = "FAILED_PERMANENT"
        const val STATUS_STALE = "STALE"
        const val STATUS_AUTH_PAUSED = "AUTH_PAUSED"

        const val SOURCE_LIVE = "live"
        const val SOURCE_BACKFILL = "backfill"
    }
}
