package com.harsh.jarvis.proactive

import kotlin.math.max
import kotlin.math.min

object PriorityEngine {
    fun score(importance: Double, urgency: Double, deadlineProximity: Double, relevance: Double, consequence: Double, feedbackPenalty: Double = 0.0): Double {
        val raw = (importance.coerceIn(0.0, 1.0) * 0.28) +
                (urgency.coerceIn(0.0, 1.0) * 0.22) +
                (deadlineProximity.coerceIn(0.0, 1.0) * 0.22) +
                (relevance.coerceIn(0.0, 1.0) * 0.16) +
                (consequence.coerceIn(0.0, 1.0) * 0.12) - feedbackPenalty.coerceIn(0.0, 0.4)
        return min(1.0, max(0.0, raw))
    }

    fun classify(score: Double, manual: ProactivePriority? = null): ProactivePriority = manual ?: when {
        score >= 0.85 -> ProactivePriority.CRITICAL
        score >= 0.68 -> ProactivePriority.HIGH
        score >= 0.42 -> ProactivePriority.MEDIUM
        else -> ProactivePriority.LOW
    }

    fun deadlineFactor(deadline: Long?, now: Long = System.currentTimeMillis()): Double {
        if (deadline == null) return 0.0
        val hours = (deadline - now) / 3_600_000.0
        return when {
            hours <= 0 -> 1.0
            hours <= 6 -> 0.98
            hours <= 24 -> 0.90
            hours <= 48 -> 0.78
            hours <= 72 -> 0.62
            hours <= 168 -> 0.42
            else -> 0.18
        }
    }
}
