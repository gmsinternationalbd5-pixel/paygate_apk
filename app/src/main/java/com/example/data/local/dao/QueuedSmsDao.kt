package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.QueuedSms
import kotlinx.coroutines.flow.Flow

@Dao
interface QueuedSmsDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertOrIgnore(sms: QueuedSms): Long

    @Update
    suspend fun update(sms: QueuedSms)

    @Query("SELECT * FROM queued_sms WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): QueuedSms?

    @Query("SELECT * FROM queued_sms WHERE status IN (:statuses) ORDER BY received_at ASC LIMIT :limit")
    suspend fun getDeliverableSms(
        statuses: List<String> = listOf(QueuedSms.STATUS_PENDING, QueuedSms.STATUS_STALE),
        limit: Int = 50
    ): List<QueuedSms>

    @Query("SELECT COUNT(*) FROM queued_sms WHERE status IN ('PENDING', 'STALE')")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM queued_sms WHERE status = 'STALE' OR (status = 'PENDING' AND received_at < :cutoff)")
    fun observeStaleCount(cutoff: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM queued_sms WHERE status IN ('FAILED', 'FAILED_PERMANENT', 'AUTH_PAUSED')")
    fun observeFailedCount(): Flow<Int>

    @Query("SELECT * FROM queued_sms ORDER BY received_at DESC LIMIT :limit")
    fun observeRecentLogs(limit: Int = 200): Flow<List<QueuedSms>>

    @Query("SELECT * FROM queued_sms WHERE status = :status ORDER BY delivered_at DESC LIMIT 1")
    fun observeLastDelivered(status: String = QueuedSms.STATUS_DELIVERED): Flow<QueuedSms?>

    @Query("SELECT COALESCE(MAX(device_seq), 0) FROM queued_sms")
    suspend fun getMaxDeviceSeq(): Long

    @Query("UPDATE queued_sms SET status = 'STALE' WHERE status = 'PENDING' AND received_at < :cutoff")
    suspend fun markStaleItems(cutoff: Long): Int

    @Query("UPDATE queued_sms SET status = 'PENDING', attempt_count = 0 WHERE status = 'AUTH_PAUSED'")
    suspend fun resetAuthPausedToPending(): Int

    @Query("UPDATE queued_sms SET status = 'PENDING', attempt_count = 0 WHERE id = :id")
    suspend fun retryItem(id: Long)

    @Query("DELETE FROM queued_sms WHERE status = :status AND delivered_at IS NOT NULL AND delivered_at < :cutoffTimestamp")
    suspend fun purgeOldDelivered(status: String = QueuedSms.STATUS_DELIVERED, cutoffTimestamp: Long): Int

    @Query("DELETE FROM queued_sms")
    suspend fun deleteAll()
}
