package com.harsh.jarvis.history

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ActionHistoryDao {
    @Insert suspend fun insert(record: ActionHistory): Long
    @Query("""UPDATE action_history SET status=:status, lifecycle=:lifecycle, actual=:actual,
        problem=:problem, cause=:cause, fix=:fix, evidence=:evidence, verified=:verified,
        updatedAt=:updatedAt WHERE id=:id""")
    suspend fun updateResult(id: Long, status: String, lifecycle: String, actual: String,
        problem: String?, cause: String?, fix: String?, evidence: String?, verified: Boolean,
        updatedAt: Long = System.currentTimeMillis())

    @Query("SELECT * FROM action_history ORDER BY updatedAt DESC LIMIT :limit")
    suspend fun latest(limit: Int = 50): List<ActionHistory>

    @Query("SELECT * FROM action_history WHERE id=:id LIMIT 1")
    suspend fun findById(id: Long): ActionHistory?

    @Query("""SELECT * FROM action_history WHERE actionLevel='SAFE'
        AND status IN ('FAILED','PARTIAL') ORDER BY updatedAt DESC LIMIT 1""")
    suspend fun latestRetryable(): ActionHistory?

    @Query("SELECT * FROM action_history ORDER BY updatedAt DESC LIMIT 100")
    fun observeLatest(): Flow<List<ActionHistory>>
}
