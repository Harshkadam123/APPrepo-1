package com.harsh.jarvis.history

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "action_history")
data class ActionHistory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val request: String,
    val actionName: String,
    val actionLevel: String,
    val status: String,
    val lifecycle: String = "COMPLETED",
    val expected: String,
    val actual: String,
    val problem: String? = null,
    val cause: String? = null,
    val fix: String? = null,
    val evidence: String? = null,
    val payloadJson: String = "{}",
    val verified: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
