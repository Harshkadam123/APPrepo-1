package com.harsh.jarvis.privacy

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PrivacyAuditDao {
    @Insert
    suspend fun insert(entry: PrivacyAudit): Long

    @Query("SELECT * FROM privacy_audit ORDER BY createdAt DESC LIMIT :limit")
    fun observeLatest(limit: Int = 50): Flow<List<PrivacyAudit>>

    @Query("DELETE FROM privacy_audit")
    suspend fun clear()
}
