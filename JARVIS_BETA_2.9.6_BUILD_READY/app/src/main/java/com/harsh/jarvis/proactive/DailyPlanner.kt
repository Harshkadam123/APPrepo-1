package com.harsh.jarvis.proactive

import com.harsh.jarvis.tasks.Task
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.max

/**
 * Deterministic, local-only daily planner.
 *
 * The planner only calculates a proposed schedule.
 * It does NOT execute tasks, send messages, change alarms,
 * or perform external actions.
 */
object DailyPlanner {

    data class PlanItem(
        val title: String,
        val start: LocalDateTime,
        val end: LocalDateTime,
        val reason: String
    )

    /**
     * Creates a plan for the remaining part of the current day.
     *
     * The planner:
     * - ignores completed tasks
     * - prioritizes important/urgent tasks
     * - considers deadlines
     * - considers goal priority
     * - considers consequence
     * - avoids scheduled busy periods
     * - avoids protected/quiet periods
     * - limits the number of generated plan items
     */
    fun plan(
        now: LocalDateTime,
        tasks: List<Task>,
        schedule: List<ProactiveScheduleBlock>,
        quiet: List<AvailabilityRule>,
        maxHours: Int = 16
    ): List<PlanItem> {

        if (maxHours <= 0) {
            return emptyList()
        }

        val endOfDay: LocalDateTime =
            now.toLocalDate()
                .plusDays(1)
                .atStartOfDay()

        if (now >= endOfDay) {
            return emptyList()
        }

        /*
         * Convert scheduled blocks into LocalDateTime using an
         * explicit ZoneId conversion.
         *
         * Instant.toLocalDateTime() is NOT used because it requires
         * a ZoneOffset. Instead we explicitly use:
         *
         * Instant.ofEpochMilli(...).atZone(...).toLocalDateTime()
         */
        val zone: ZoneId =
            ZoneId.systemDefault()

        val nowMillis: Long =
            runCatching {
                now.atZone(zone)
                    .toInstant()
                    .toEpochMilli()
            }.getOrElse {
                System.currentTimeMillis()
            }

        val busy: List<Pair<LocalDateTime, LocalDateTime>> =
            schedule
                .asSequence()
                .filter { block ->
                    block.endTime > nowMillis
                }
                .mapNotNull { block ->

                    val start: LocalDateTime? =
                        epochToLocalDateTime(
                            block.startTime,
                            zone
                        )

                    val end: LocalDateTime? =
                        epochToLocalDateTime(
                            block.endTime,
                            zone
                        )

                    if (
                        start != null &&
                        end != null &&
                        end.isAfter(start)
                    ) {
                        Pair(start, end)
                    } else {
                        null
                    }
                }
                .sortedBy { pair ->
                    pair.first
                }
                .toList()

        /*
         * Determine today's schedule bit.
         *
         * Java's DayOfWeek.value:
         * Monday = 1
         * ...
         * Sunday = 7
         *
         * Convert it into:
         * Monday = bit 0
         * Tuesday = bit 1
         * ...
         * Sunday = bit 6
         */
        val dayBit: Int =
            1 shl (
                (now.dayOfWeek.value - 1)
                    .coerceIn(0, 6)
            )

        /*
         * Protected time ranges.
         *
         * Each rule is represented as:
         * startMinute -> endMinute
         */
        val protected: List<Pair<Int, Int>> =
            quiet
                .asSequence()
                .filter { rule ->
                    rule.enabled &&
                        (rule.daysMask and dayBit) != 0
                }
                .map { rule ->
                    Pair(
                        rule.startMinute.coerceIn(0, 1439),
                        rule.endMinute.coerceIn(0, 1439)
                    )
                }
                .toList()

        /*
         * Sort unfinished tasks by calculated priority.
         */
        val candidates: List<Task> =
            tasks
                .asSequence()
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
                .toList()

        if (candidates.isEmpty()) {
            return emptyList()
        }

        val result: MutableList<PlanItem> =
            mutableListOf()

        var cursor: LocalDateTime =
            now

        /*
         * maxHours is retained for API compatibility with your
         * existing code. It limits the number of planned task blocks,
         * just like the previous implementation.
         */
        for (task: Task in candidates) {

            if (result.size >= maxHours) {
                break
            }

            /*
             * Never allow an invalid/negative task duration.
             */
            val duration: Int =
                max(
                    15,
                    task.estimatedMinutes.coerceAtLeast(0)
                )

            val slot: LocalDateTime? =
                nextFreeSlot(
                    cursor0 = cursor,
                    minutes = duration,
                    busy = busy,
                    protected = protected,
                    end = endOfDay
                )

            if (slot == null) {
                /*
                 * No remaining suitable slot.
                 */
                break
            }

            val finish: LocalDateTime =
                runCatching {
                    slot.plusMinutes(
                        duration.toLong()
                    )
                }.getOrElse {
                    break
                }

            if (finish > endOfDay) {
                break
            }

            val reason: String =
                when {
                    isDeadlineReached(
                        task.dueTime,
                        finish,
                        zone
                    ) ->
                        "deadline proximity"

                    task.goalPriority >= 0.75 ->
                        "user goal priority"

                    task.consequence >= 0.75 ->
                        "high consequence of delay"

                    task.priority.equals(
                        "URGENT",
                        ignoreCase = true
                    ) ->
                        "urgent task"

                    task.priority.equals(
                        "HIGH",
                        ignoreCase = true
                    ) ->
                        "high priority task"

                    else ->
                        "highest current value"
                }

            result.add(
                PlanItem(
                    title = task.title,
                    start = slot,
                    end = finish,
                    reason = reason
                )
            )

            /*
             * Continue scheduling after the task just placed.
             */
            cursor = finish
        }

        return result.toList()
    }

    /**
     * Calculates task priority.
     *
     * Kept separate so the planner remains deterministic and easy
     * to test.
     */
    private fun priority(
        task: Task
    ): Double {

        val deadline: Double =
            task.dueTime
                ?.let { due ->
                    runCatching {
                        PriorityEngine.deadlineFactor(due)
                    }.getOrDefault(0.15)
                }
                ?: 0.15

        val completionPercent: Int =
            task.completionPercent
                .coerceIn(0, 100)

        val completion: Double =
            (100 - completionPercent) / 100.0

        val importance: Double =
            when {
                task.priority.equals(
                    "URGENT",
                    ignoreCase = true
                ) ->
                    1.0

                task.priority.equals(
                    "HIGH",
                    ignoreCase = true
                ) ->
                    0.9

                task.priority.equals(
                    "MEDIUM",
                    ignoreCase = true
                ) ->
                    0.7

                task.priority.equals(
                    "LOW",
                    ignoreCase = true
                ) ->
                    0.4

                else ->
                    0.55
            }

        val urgency: Double =
            if (task.dueTime != null) {
                0.8
            } else {
                0.35
            }

        val relevance: Double =
            task.goalPriority
                .coerceIn(0.0, 1.0)

        val consequence: Double =
            task.consequence
                .coerceIn(0.0, 1.0)

        val score: Double =
            runCatching {
                PriorityEngine.score(
                    importance = importance,
                    urgency = urgency,
                    deadlineProximity = deadline,
                    relevance = relevance,
                    consequence = consequence,
                    feedbackPenalty = 0.0
                )
            }.getOrDefault(0.0)

        return score *
            (0.65 + 0.35 * completion)
    }

    /**
     * Finds the next available slot.
     *
     * The function advances the cursor when it encounters:
     * - a protected period
     * - a scheduled busy period
     *
     * It never returns a slot extending beyond the end of the day.
     */
    private fun nextFreeSlot(
        cursor0: LocalDateTime,
        minutes: Int,
        busy: List<Pair<LocalDateTime, LocalDateTime>>,
        protected: List<Pair<Int, Int>>,
        end: LocalDateTime
    ): LocalDateTime? {

        if (minutes <= 0) {
            return null
        }

        if (cursor0 >= end) {
            return null
        }

        var cursor: LocalDateTime =
            cursor0

        /*
         * Safety limit prevents an accidental infinite loop if
         * malformed availability data is supplied.
         */
        repeat(256) {

            if (cursor >= end) {
                return null
            }

            /*
             * Check protected periods.
             */
            val minuteOfDay: Int =
                cursor.hour * 60 +
                    cursor.minute

            val protectedHit:
                Pair<Int, Int>? =
                protected.firstOrNull { range ->

                    containsMinute(
                        start = range.first,
                        end = range.second,
                        minute = minuteOfDay
                    )
                }

            if (protectedHit != null) {

                val protectedEnd: Int =
                    protectedHit.second

                val protectedStart: Int =
                    protectedHit.first

                /*
                 * Normal range, e.g. 09:00 -> 12:00
                 */
                if (protectedStart <= protectedEnd) {

                    cursor =
                        cursor
                            .toLocalDate()
                            .atStartOfDay()
                            .plusMinutes(
                                protectedEnd.toLong()
                            )

                } else {

                    /*
                     * Overnight range, e.g. 22:00 -> 06:00.
                     *
                     * If currently inside the late-night portion,
                     * move to the next day's start.
                     *
                     * If inside the early-morning portion,
                     * move to today's 06:00.
                     */
                    if (minuteOfDay >= protectedStart) {

                        cursor =
                            cursor
                                .toLocalDate()
                                .plusDays(1)
                                .atStartOfDay()

                    } else {

                        cursor =
                            cursor
                                .toLocalDate()
                                .atStartOfDay()
                                .plusMinutes(
                                    protectedEnd.toLong()
                                )
                    }
                }

                return@repeat
            }

            val finish: LocalDateTime =
                runCatching {
                    cursor.plusMinutes(
                        minutes.toLong()
                    )
                }.getOrNull()
                    ?: return null

            if (finish > end) {
                return null
            }

            /*
             * Check calendar/scheduled conflicts.
             */
            val conflict:
                Pair<LocalDateTime, LocalDateTime>? =
                busy.firstOrNull { block ->

                    val busyStart =
                        block.first

                    val busyEnd =
                        block.second

                    cursor < busyEnd &&
                        finish > busyStart
                }

            if (conflict != null) {

                val conflictEnd: LocalDateTime =
                    conflict.second

                if (conflictEnd > cursor) {
                    cursor = conflictEnd
                } else {
                    /*
                     * Defensive fallback to prevent a loop.
                     */
                    cursor =
                        cursor.plusMinutes(1)
                }

                return@repeat
            }

            /*
             * No conflict.
             */
            return cursor
        }

        return null
    }

    /**
     * Determines whether a minute falls inside an availability
     * protection interval.
     */
    private fun containsMinute(
        start: Int,
        end: Int,
        minute: Int
    ): Boolean {

        val safeStart: Int =
            start.coerceIn(0, 1439)

        val safeEnd: Int =
            end.coerceIn(0, 1439)

        val safeMinute: Int =
            minute.coerceIn(0, 1439)

        return if (safeStart <= safeEnd) {

            safeMinute in safeStart until safeEnd

        } else {

            /*
             * Overnight interval.
             *
             * Example:
             * 22:00 -> 06:00
             */
            safeMinute >= safeStart ||
                safeMinute < safeEnd
        }
    }

    /**
     * Converts epoch milliseconds to LocalDateTime safely.
     *
     * This explicitly uses ZoneId correctly:
     *
     * Instant.ofEpochMilli(...)
     *     .atZone(zone)
     *     .toLocalDateTime()
     */
    private fun epochToLocalDateTime(
        millis: Long,
        zone: ZoneId
    ): LocalDateTime? {

        return runCatching {

            Instant
                .ofEpochMilli(millis)
                .atZone(zone)
                .toLocalDateTime()

        }.getOrNull()
    }

    /**
     * Checks whether a task deadline occurs before or at the
     * proposed completion time.
     */
    private fun isDeadlineReached(
        dueTime: Long?,
        finish: LocalDateTime,
        zone: ZoneId
    ): Boolean {

        if (dueTime == null) {
            return false
        }

        val finishMillis: Long =
            runCatching {
                finish
                    .atZone(zone)
                    .toInstant()
                    .toEpochMilli()
            }.getOrDefault(Long.MAX_VALUE)

        return dueTime <= finishMillis
    }
}
