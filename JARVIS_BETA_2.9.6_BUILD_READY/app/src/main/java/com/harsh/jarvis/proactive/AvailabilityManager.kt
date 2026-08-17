package com.harsh.jarvis.proactive

import java.time.LocalDateTime

class AvailabilityManager(private val dao: ProactiveDao) {
    suspend fun current(now: LocalDateTime = LocalDateTime.now()): Pair<AvailabilityState, String> {
        val minute = now.hour * 60 + now.minute
        val dayBit = 1 shl ((now.dayOfWeek.value - 1) % 7)
        val rules = dao.availability()
        val match = rules.firstOrNull { rule ->
            (rule.daysMask and dayBit) != 0 && contains(rule.startMinute, rule.endMinute, minute)
        }
        return if (match == null) AvailabilityState.FREE to "FREE" else {
            val state = runCatching { AvailabilityState.valueOf(match.state) }.getOrDefault(AvailabilityState.CUSTOM)
            state to (match.label.ifBlank { state.name })
        }
    }

    suspend fun criticalOverrideAllowed(now: LocalDateTime = LocalDateTime.now()): Boolean {
        val minute = now.hour * 60 + now.minute
        val dayBit = 1 shl ((now.dayOfWeek.value - 1) % 7)
        return dao.availability().any { it.enabled && it.criticalOverride && (it.daysMask and dayBit) != 0 && contains(it.startMinute, it.endMinute, minute) }
    }

    private fun contains(start: Int, end: Int, minute: Int): Boolean =
        if (start <= end) minute in start until end else minute >= start || minute < end
}
