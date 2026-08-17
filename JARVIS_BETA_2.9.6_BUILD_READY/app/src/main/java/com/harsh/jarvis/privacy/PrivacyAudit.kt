package com.harsh.jarvis.privacy

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "privacy_audit")
data class PrivacyAudit(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val capability: String,
    val mode: String,
    val purpose: String,
    val dataExposed: String,
    val outcome: String,
    val createdAt: Long = System.currentTimeMillis()
)
