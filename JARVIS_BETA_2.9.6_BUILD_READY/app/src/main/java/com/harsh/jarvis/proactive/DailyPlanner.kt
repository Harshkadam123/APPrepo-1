package com.harsh.jarvis.proactive

import com.harsh.jarvis.tasks.Task
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.max

/** Deterministic, local-only time-block planner. It never sends or performs actions. */
object DailyPlanner {
    data class PlanItem(val title: String, val start: LocalDateTime, val end: LocalDateTime, val reason: String)

    fun plan(
        now: LocalDateTime,
        tasks: List<Task>,
        schedule: List<ProactiveScheduleBlock>,
        quiet: List<AvailabilityRule>,
        maxHours: Int = 16
    ): List<PlanItem> {
        val endOfDay = now.toLocalDate().plusDays(1).atStartOfDay()
        val busy = schedule.filter { it.endTime > now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() }
            .sortedBy { it.startTime }
            .map { it.startTime.toLocalDateTime() to it.endTime.toLocalDateTime() }
        val dayBit = 1 shl ((now.dayOfWeek.value - 1) % 7)
        val protected = quiet.filter { it.enabled && (it.daysMask and dayBit) != 0 }.map { it.startMinute to it.endMinute }
        val candidates = tasks.filter { !it.completed }.sortedWith(compareByDescending<Task> { priority(it) }.thenBy { it.dueTime ?: Long.MAX_VALUE })
        val result = mutableListOf<PlanItem>()
        var cursor = now
        for (task in candidates) {
            if (result.size >= maxHours) break
            val duration = max(15, task.estimatedMinutes)
            val slot = nextFreeSlot(cursor, duration, busy, protected, endOfDay) ?: break
            val finish = slot.plusMinutes(duration.toLong())
            val reason = when {
                task.dueTime != null && task.dueTime <= finish.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() -> "deadline proximity"
                task.goalPriority >= .75 -> "user goal priority"
                task.consequence >= .75 -> "high consequence of delay"
                else -> "highest current value"
            }
            result += PlanItem(task.title, slot, finish, reason)
            cursor = finish
        }
        return result
    }

    private fun priority(t: Task): Double {
        val deadline = t.dueTime?.let { PriorityEngine.deadlineFactor(it) } ?: 0.15
        val completion = (100 - t.completionPercent.coerceIn(0,100)) / 100.0
        return PriorityEngine.score(
            importance = if (t.priority.equals("URGENT", true)) 1.0 else if (t.priority.equals("HIGH", true)) .9 else .55,
            urgency = if (t.dueTime != null) .8 else .35,
            deadlineProximity = deadline,
            relevance = t.goalPriority,
            consequence = t.consequence,
            feedbackPenalty = 0.0
        ) * (.65 + .35 * completion)
    }

    private fun nextFreeSlot(cursor0: LocalDateTime, minutes: Int, busy: List<Pair<LocalDateTime, LocalDateTime>>, protected: List<Pair<Int,Int>>, end: LocalDateTime): LocalDateTime? {
        var cursor = cursor0
        repeat(64) {
            if (cursor >= end) return null
            val protectedHit = protected.firstOrNull { contains(it.first, it.second, cursor.hour * 60 + cursor.minute) }
            if (protectedHit != null) {
                cursor = cursor.toLocalDate().atStartOfDay().plusMinutes(protectedHit.second.toLong())
                if (protectedHit.first > protectedHit.second) cursor = cursor.plusDays(1)
                return@repeat
            }
            val finish = cursor.plusMinutes(minutes.toLong())
            val conflict = busy.firstOrNull { cursor < it.second && finish > it.first }
            if (conflict != null) { cursor = conflict.second; return@repeat }
            if (finish <= end) return cursor
            return null
        }
        return null
    }

    private fun contains(start: Int, end: Int, minute: Int): Boolean = if (start <= end) minute in start until end else minute >= start || minute < end
}
