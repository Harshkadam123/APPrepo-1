package com.harsh.jarvis.proactive

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ProactiveDao {
    @Query("SELECT * FROM proactive_availability WHERE enabled = 1 ORDER BY startMinute")
    fun observeAvailability(): Flow<List<AvailabilityRule>>
    @Query("SELECT * FROM proactive_availability WHERE enabled = 1")
    suspend fun availability(): List<AvailabilityRule>
    @Insert suspend fun insertAvailability(rule: AvailabilityRule): Long
    @Update suspend fun updateAvailability(rule: AvailabilityRule)
    @Delete suspend fun deleteAvailability(rule: AvailabilityRule)

    @Query("SELECT * FROM proactive_events WHERE status IN ('PENDING','WAITING','SNOOZED') ORDER BY createdAt DESC")
    fun observeActiveEvents(): Flow<List<ProactiveEvent>>
    @Query("SELECT * FROM proactive_events WHERE status IN ('PENDING','WAITING','SNOOZED') ORDER BY createdAt DESC")
    suspend fun activeEvents(): List<ProactiveEvent>
    @Query("SELECT * FROM proactive_events WHERE id = :id LIMIT 1") suspend fun event(id: Long): ProactiveEvent?
    @Query("SELECT * FROM proactive_events WHERE dedupeKey = :key LIMIT 1") suspend fun byDedupeKey(key: String): ProactiveEvent?
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertEvent(event: ProactiveEvent): Long
    @Update suspend fun updateEvent(event: ProactiveEvent)
    @Query("UPDATE proactive_events SET communicationState = 'COMMUNICATED', communicationCount = communicationCount + 1 WHERE id = :id") suspend fun markCommunicated(id: Long)
    @Query("UPDATE proactive_events SET status = :status WHERE id = :id") suspend fun setStatus(id: Long, status: String)

    @Query("SELECT * FROM proactive_feedback") suspend fun feedback(): List<ProactiveFeedback>
    @Query("SELECT * FROM proactive_feedback WHERE type = :type LIMIT 1") suspend fun feedback(type: String): ProactiveFeedback?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveFeedback(feedback: ProactiveFeedback)

    @Query("SELECT * FROM proactive_schedule WHERE endTime >= :from ORDER BY startTime")
    fun observeSchedule(from: Long): Flow<List<ProactiveScheduleBlock>>
    @Query("SELECT * FROM proactive_schedule WHERE endTime >= :from ORDER BY startTime") suspend fun schedule(from: Long): List<ProactiveScheduleBlock>
    @Query("DELETE FROM proactive_schedule") suspend fun clearSchedule()
    @Insert suspend fun insertSchedule(block: ProactiveScheduleBlock): Long
    @Delete suspend fun deleteSchedule(block: ProactiveScheduleBlock)
}
