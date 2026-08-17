package com.harsh.jarvis.memory

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryDao {
    @Insert
    suspend fun insert(memory: Memory): Long

    @Query("SELECT * FROM memories WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): Memory?

    @Query("SELECT * FROM memories ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<Memory>>

    @Query("SELECT * FROM memories ORDER BY createdAt DESC LIMIT 5")
    suspend fun latest(): List<Memory>

    @Query("SELECT * FROM memories ORDER BY createdAt DESC")
    suspend fun all(): List<Memory>

    @Query("""
        SELECT * FROM memories
        WHERE instr(lower(text), lower(:query)) > 0
        ORDER BY createdAt DESC
        LIMIT 5
    """)
    suspend fun search(query: String): List<Memory>
}
