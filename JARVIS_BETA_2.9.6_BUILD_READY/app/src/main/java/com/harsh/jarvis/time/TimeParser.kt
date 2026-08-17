package com.harsh.jarvis.time

import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

data class ParsedTime(
    val dueTime: Long,
    val description: String,
    /** Exact scheduling text that was consumed from the command. */
    val matchedText: String = ""
)

/**
 * Deterministic, offline parser for common reminder language.
 * It deliberately returns a concrete time only when the command contains
 * enough information to schedule safely.
 */
class TimeParser {
    private val weekdays = mapOf(
        "monday" to Calendar.MONDAY, "tuesday" to Calendar.TUESDAY,
        "wednesday" to Calendar.WEDNESDAY, "thursday" to Calendar.THURSDAY,
        "friday" to Calendar.FRIDAY, "saturday" to Calendar.SATURDAY,
        "sunday" to Calendar.SUNDAY
    )

    fun parse(text: String, now: Long = System.currentTimeMillis()): ParsedTime? {
        val original = text.trim()
        if (original.isBlank()) return null
        val s = original.lowercase(Locale.US).replace(Regex("\\s+"), " ").trim()
        val baseNow = Calendar.getInstance().apply { timeInMillis = now }

        // Relative durations: "in 30 minutes", "after 2 hours", "in 1h 30m",
        // "2 hours from now", and the common "half an hour" form.
        Regex("\\b(?:in|after)\\s+(?:half\\s+(?:an|a)\\s+hour)\\b", RegexOption.IGNORE_CASE).find(s)?.let {
            return ParsedTime(now + TimeUnit.MINUTES.toMillis(30), "in 30 minutes", it.value)
        }
        Regex("\\b(?:in|after)\\s+(?:(\\d+)\\s*(?:hours?|hrs?|h)\\s*)?(?:(\\d+)\\s*(?:minutes?|mins?|m))\\b", RegexOption.IGNORE_CASE).find(s)?.let {
            val h = it.groupValues[1].ifBlank { "0" }.toLong()
            val m = it.groupValues[2].ifBlank { "0" }.toLong()
            if (h > 0 || m > 0) return ParsedTime(now + TimeUnit.HOURS.toMillis(h) + TimeUnit.MINUTES.toMillis(m), durationDescription(h, m), it.value)
        }
        Regex("\\b(?:in|after)\\s+(\\d+)\\s*(?:hours?|hrs?|h)\\b", RegexOption.IGNORE_CASE).find(s)?.let {
            val h = it.groupValues[1].toLong()
            return ParsedTime(now + TimeUnit.HOURS.toMillis(h), "in $h hour${if (h == 1L) "" else "s"}", it.value)
        }
        Regex("\\b(?:in|after)\\s+(\\d+)\\s*(?:minutes?|mins?|min|m)\\b", RegexOption.IGNORE_CASE).find(s)?.let {
            val m = it.groupValues[1].toLong()
            return ParsedTime(now + TimeUnit.MINUTES.toMillis(m), "in $m minute${if (m == 1L) "" else "s"}", it.value)
        }
        Regex("\\b(\\d+)\\s*(?:hours?|hrs?|h)\\s+from\\s+now\\b", RegexOption.IGNORE_CASE).find(s)?.let {
            val h = it.groupValues[1].toLong()
            return ParsedTime(now + TimeUnit.HOURS.toMillis(h), "in $h hour${if (h == 1L) "" else "s"}", it.value)
        }

        // Explicit day + optional part of day + optional clock.
        val tomorrowMatch = Regex("\\btomorrow(?:\\s+(morning|afternoon|evening|night))?\\b", RegexOption.IGNORE_CASE).find(s)
        val todayMatch = Regex("\\btoday(?:\\s+(morning|afternoon|evening|night))?\\b", RegexOption.IGNORE_CASE).find(s)
        val weekdayMatch = weekdays.entries
            .mapNotNull { (name, value) -> Regex("\\b(?:next\\s+)?$name\\b", RegexOption.IGNORE_CASE).find(s)?.let { name to Pair(value, it) } }
            .firstOrNull()

        if (tomorrowMatch != null || todayMatch != null || weekdayMatch != null) {
            val cal = Calendar.getInstance().apply { timeInMillis = now }
            val dayLabel: String
            val dayMatchText: String
            val defaultPart: String?
            when {
                tomorrowMatch != null -> {
                    cal.add(Calendar.DAY_OF_YEAR, 1)
                    dayLabel = "tomorrow"
                    dayMatchText = tomorrowMatch.value
                    defaultPart = tomorrowMatch.groupValues[1].ifBlank { null }
                }
                todayMatch != null -> {
                    dayLabel = "today"
                    dayMatchText = todayMatch.value
                    defaultPart = todayMatch.groupValues[1].ifBlank { null }
                }
                else -> {
                    val (name, pair) = weekdayMatch!!
                    val current = cal.get(Calendar.DAY_OF_WEEK)
                    var delta = (pair.first - current + 7) % 7
                    if (delta == 0) delta = 7
                    cal.add(Calendar.DAY_OF_YEAR, delta)
                    dayLabel = name
                    dayMatchText = pair.second.value
                    defaultPart = null
                }
            }

            val clock = findClock(s)
            if (clock != null) {
                val (hour, minute, clockText) = clock
                val due = setTime(cal, hour to minute)
                return ParsedTime(due, "$dayLabel at ${format(hour to minute)}", "$dayMatchText $clockText".trim())
            }

            val hm = when (defaultPart) {
                "morning" -> 9 to 0
                "afternoon" -> 15 to 0
                "evening" -> 18 to 0
                "night" -> 21 to 0
                else -> return null
            }
            return ParsedTime(setTime(cal, hm), "$dayLabel ${format(hm)}", dayMatchText)
        }

        // "at 7 PM", "for 19:30", "7:30 PM" and "7 PM".
        val clock = findClock(s)
        if (clock != null) {
            val (hour, minute, clockText) = clock
            val cal = Calendar.getInstance().apply { timeInMillis = now }
            if (hour < cal.get(Calendar.HOUR_OF_DAY) || (hour == cal.get(Calendar.HOUR_OF_DAY) && minute <= cal.get(Calendar.MINUTE))) {
                cal.add(Calendar.DAY_OF_YEAR, 1)
            }
            return ParsedTime(setTime(cal, hour to minute), "at ${format(hour to minute)}", clockText)
        }

        return null
    }

    private fun findClock(text: String): Triple<Int, Int, String>? {
        val match = Regex("\\b(?:at|for)\\s+(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?\\b|\\b(\\d{1,2}):(\\d{2})\\s*(am|pm)?\\b|\\b(\\d{1,2})\\s*(am|pm)\\b", RegexOption.IGNORE_CASE).find(text)
            ?: return null
        val groups = match.groupValues
        val rawHour = groups[1].ifBlank { groups[4] }.ifBlank { groups[7] }
        val rawMinute = groups[2].ifBlank { groups[5] }.ifBlank { "0" }
        val meridiem = groups[3].ifBlank { groups[6] }.ifBlank { groups[8] }.lowercase(Locale.US)
        var hour = rawHour.toIntOrNull() ?: return null
        val minute = rawMinute.toIntOrNull() ?: return null
        if (minute !in 0..59) return null
        if (meridiem.isNotBlank()) {
            if (hour !in 1..12) return null
            if (meridiem == "pm" && hour < 12) hour += 12
            if (meridiem == "am" && hour == 12) hour = 0
        } else if (hour !in 0..23) return null
        return Triple(hour, minute, match.value.trim())
    }

    private fun durationDescription(hours: Long, minutes: Long): String = buildString {
        append("in ")
        if (hours > 0) append("$hours hour${if (hours == 1L) "" else "s"}")
        if (hours > 0 && minutes > 0) append(" ")
        if (minutes > 0) append("$minutes minute${if (minutes == 1L) "" else "s"}")
    }

    private fun setTime(cal: Calendar, hm: Pair<Int, Int>): Long {
        cal.set(Calendar.HOUR_OF_DAY, hm.first)
        cal.set(Calendar.MINUTE, hm.second)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun format(hm: Pair<Int, Int>) = String.format(Locale.US, "%02d:%02d", hm.first, hm.second)
}
