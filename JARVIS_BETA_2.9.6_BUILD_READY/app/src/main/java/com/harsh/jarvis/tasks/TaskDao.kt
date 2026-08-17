package com.harsh.jarvis.tasks

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {

    @Insert
    suspend fun insert(task: Task): Long

    @Update
    suspend fun update(task: Task)

    @Delete
    suspend fun delete(task: Task)

    @Query("SELECT * FROM tasks ORDER BY completed ASC, dueTime ASC, id DESC")
    fun observeAll(): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): Task?

    @Query("UPDATE tasks SET completed = 1 WHERE id = :id")
    suspend fun complete(id: Long)

    @Query("SELECT * FROM tasks WHERE completed = 0 AND dueTime IS NOT NULL AND dueTime > :after ORDER BY dueTime ASC")
    suspend fun scheduledAfter(after: Long): List<Task>

    @Query("SELECT * FROM tasks WHERE completed = 0 AND dueTime IS NOT NULL AND dueTime < :before ORDER BY dueTime ASC")
    suspend fun incompleteBefore(before: Long): List<Task>
}
