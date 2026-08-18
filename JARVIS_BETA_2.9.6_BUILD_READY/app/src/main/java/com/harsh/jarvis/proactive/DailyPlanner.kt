package com.harsh.jarvis.proactive

import com.harsh.jarvis.tasks.Task
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.max

/**
 * Deterministic, local-only time-block planner.
 *
 * It only calculates a plan.
 * It never sends messages, performs actions, or accesses the network.
 */
object DailyPlanner {

    private val zone: ZoneId = ZoneId.systemDefault()

    data class PlanItem(
        val title: String,
        val start: LocalDateTime,
        val end: LocalDateTime,
        val reason: String
    )

    fun plan(
        now: LocalDateTime,
        tasks: List<Task>,
        schedule: List<ProactiveScheduleBlock>,
        quiet: List<AvailabilityRule>,
        maxHours: Int = 16
    ): List<PlanItem> {

        val endOfDay = now
            .toLocalDate()
            .plusDays(1)
            .atStartOfDay()

        val busy: List<Pair<LocalDateTime, LocalDateTime>> =
            schedule
                .filter { block ->
                    block.endTime > now.toInstant(zone).toEpochMilli()
                }
                .sortedBy { it.startTime }
                .map { block ->
                    Instant.ofEpochMilli(block.startTime)
                        .atZone(zone)
                        .toLocalDateTime() to
                        Instant.ofEpochMilli(block.endTime)
                            .atZone(zone)
                            .toLocalDateTime()
                }

        val dayBit = 1 shl (now.dayOfWeek.value - 1)

        val protected: List<Pair<Int, Int>> =
            quiet
                .filter { rule ->
                    rule.enabled &&
                        (rule.daysMask and dayBit) != 0
                }
                .map { rule ->
                    rule.startMinute to rule.endMinute
                }

        val candidates: List<Task> =
            tasks
                .filter { task ->
                    !task.completed
                }
                .sortedWith(
                    compareByDescending<Task> { task ->
                        priority(task)
                    }.thenBy { task ->
                        task.dueTime ?: Long.MAX_VALUE
                    }
                )

        val result = mutableListOf<PlanItem>()

        var cursor = now
        var plannedMinutes = 0
        val maximumMinutes = max(1, maxHours) * 60

        for (task in candidates) {

            if (plannedMinutes >= maximumMinutes) {
                break
            }

            val requestedDuration = max(
                15,
                task.estimatedMinutes
            )

            val remainingMinutes =
                maximumMinutes - plannedMinutes

            val duration =
                minOf(requestedDuration, remainingMinutes)

            val slot = nextFreeSlot(
                cursor0 = cursor,
                minutes = duration,
                busy = busy,
                protected = protected,
                end = endOfDay
            ) ?: break

            val finish = slot.plusMinutes(
                duration.toLong()
            )

            val finishEpoch =
                finish.toInstant(zone).toEpochMilli()

            val reason = when {
                task.dueTime != null &&
                    task.dueTime <= finishEpoch ->
                    "deadline proximity"

                task.goalPriority >= 0.75 ->
                    "user goal priority"

                task.consequence >= 0.75 ->
                    "high consequence of delay"

                else ->
                    "highest current value"
            }

            result += PlanItem(
                title = task.title,
                start = slot,
                end = finish,
                reason = reason
            )

            plannedMinutes += duration
            cursor = finish
        }

        return result
    }

    private fun priority(task: Task): Double {

        val deadline =
            task.dueTime?.let { due ->
                PriorityEngine.deadlineFactor(due)
            } ?: 0.15

        val completion =
            (
                100 -
                    task.completionPercent.coerceIn(
                        0,
                        100
                    )
                ) / 100.0

        val importance =
            when {
                task.priority.equals("URGENT", ignoreCase = true) ->
                    1.0

                task.priority.equals("HIGH", ignoreCase = true) ->
                    0.9

                task.priority.equals("LOW", ignoreCase = true) ->
                    0.25

                else ->
                    0.55
            }

        return PriorityEngine.score(
            importance = importance,
            urgency = if (task.dueTime != null) 0.8 else 0.35,
            deadlineProximity = deadline,
            relevance = task.goalPriority,
            consequence = task.consequence,
            feedbackPenalty = 0.0
        ) * (
            0.65 + 0.35 * completion
        )
    }

    private fun nextFreeSlot(
        cursor0: LocalDateTime,
        minutes: Int,
        busy: List<Pair<LocalDateTime, LocalDateTime>>,
        protected: List<Pair<Int, Int>>,
        end: LocalDateTime
    ): LocalDateTime? {

        var cursor = cursor0

        repeat(64) {

            if (cursor >= end) {
                return null
            }

            val minuteOfDay =
                cursor.hour * 60 + cursor.minute

            val protectedHit =
                protected.firstOrNull { range ->
                    contains(
                        start = range.first,
                        end = range.second,
                        minute = minuteOfDay
                    )
                }

            if (protectedHit != null) {

                val protectedEnd = protectedHit.second

                cursor =
                    if (protectedHit.first <= protectedEnd) {
                        cursor
                            .toLocalDate()
                            .atStartOfDay()
                            .plusMinutes(
                                protectedEnd.toLong()
                            )
                    } else {
                        cursor
                            .toLocalDate()
                            .plusDays(1)
                            .atStartOfDay()
                    }

                return@repeat
            }

            val finish =
                cursor.plusMinutes(
                    minutes.toLong()
                )

            val conflict =
                busy.firstOrNull { block ->
                    cursor < block.second &&
                        finish > block.first
                }

            if (conflict != null) {
                cursor = conflict.second
                return@repeat
            }

            if (finish <= end) {
                return cursor
            }

            return null
        }

        return null
    }

    private fun contains(
        start: Int,
        end: Int,
        minute: Int
    ): Boolean {

        return if (start <= end) {
            minute in start until end
        } else {
            minute >= start || minute < end
        }
    }
}
