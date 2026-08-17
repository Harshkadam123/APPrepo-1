package com.harsh.jarvis.proactive

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class AvailabilityState { BUSY, FREE, STUDY, CLASS, EXERCISE, MEETING, SLEEP, TRAVEL, CUSTOM }
enum class ProactiveStatus { PENDING, WAITING, SNOOZED, COMPLETED, DISMISSED, EXPIRED }
enum class ProactivePriority { LOW, MEDIUM, HIGH, CRITICAL }

@Entity(tableName = "proactive_availability")
data class AvailabilityRule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val state: String,
    val startMinute: Int,
    val endMinute: Int,
    val daysMask: Int = 127,
    val enabled: Boolean = true,
    val criticalOverride: Boolean = false,
    val label: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "proactive_events", indices = [Index(value = ["dedupeKey"], unique = true)])
data class ProactiveEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val title: String,
    val detail: String = "",
    val priority: String = ProactivePriority.LOW.name,
    val baseImportance: Double = 0.5,
    val urgency: Double = 0.5,
    val deadlineProximity: Double = 0.0,
    val relevance: Double = 0.5,
    val source: String,
    val sourceId: Long? = null,
    val requiredAction: String = "",
    val deadline: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val status: String = ProactiveStatus.PENDING.name,
    val snoozeUntil: Long? = null,
    val communicationState: String = "UNCOMMUNICATED",
    val communicationCount: Int = 0,
    val dedupeKey: String,
    val dismissedForever: Boolean = false,
    val manualPriority: String? = null,
    val lastEvaluatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "proactive_feedback")
data class ProactiveFeedback(
    @PrimaryKey val type: String,
    val snoozes: Int = 0,
    val dismissals: Int = 0,
    val follows: Int = 0,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "proactive_schedule")
data class ProactiveScheduleBlock(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val startTime: Long,
    val endTime: Long,
    val state: String = AvailabilityState.BUSY.name,
    val protected: Boolean = true
)

data class ProactiveSnapshot(
    val availability: AvailabilityState,
    val availabilityLabel: String,
    val important: List<ProactiveEvent>,
    val next: ProactiveEvent?,
    val schedule: List<ProactiveScheduleBlock>,
    val evolutionLevel: Int,
    val evolutionXp: Long,
    val xpToday: Long,
    val recommendation: ProactiveEvent?,
    val pendingCount: Int
)
