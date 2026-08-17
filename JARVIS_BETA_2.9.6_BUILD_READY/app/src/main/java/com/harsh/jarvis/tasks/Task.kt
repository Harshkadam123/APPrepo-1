package com.harsh.jarvis.tasks

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String = "",
    val dueTime: Long? = null,
    val priority: String = "NORMAL",
    val completed: Boolean = false,
    val estimatedMinutes: Int = 45,
    val completionPercent: Int = 0,
    val consequence: Double = 0.5,
    val goalPriority: Double = 0.5
)
