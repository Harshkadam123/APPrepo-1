package com.harsh.jarvis.time

/** Parses a reminder into independent title and schedule entities. */
class ReminderParser(private val timeParser: TimeParser = TimeParser()) {
    data class ParsedReminder(val title: String, val time: ParsedTime?)

    fun parse(command: String, now: Long = System.currentTimeMillis()): ParsedReminder {
        val cleaned = command.trim()
        var title = cleaned
            .replace(
                Regex("^\\s*(?:remind\\s+me|set\\s+(?:a\\s+)?reminder|set\\s+(?:a\\s+)?task|create\\s+(?:a\\s+)?(?:reminder|task)|make\\s+(?:a\\s+)?reminder|reminder)\\s*", RegexOption.IGNORE_CASE),
                ""
            )
            .trim()

        // Support both "remind me to study" and "remind me about my exam".
        title = title.replace(Regex("^(?:to|about)\\s+", RegexOption.IGNORE_CASE), "").trim()

        val time = timeParser.parse(cleaned, now)
        if (time != null && time.matchedText.isNotBlank()) {
            // Remove the exact schedule span rather than using broad regexes that
            // can accidentally delete words from the user's actual task title.
            title = title.replace(time.matchedText, " ", ignoreCase = true)
        } else {
            // Fallback cleanup for common schedule words when no concrete time was parsed.
            title = title
                .replace(Regex("\\b(?:today|tomorrow|next|this)\\b", RegexOption.IGNORE_CASE), " ")
                .replace(Regex("\\b(?:morning|afternoon|evening|night)\\b", RegexOption.IGNORE_CASE), " ")
                .replace(Regex("\\b(?:monday|tuesday|wednesday|thursday|friday|saturday|sunday)\\b", RegexOption.IGNORE_CASE), " ")
        }

        title = title
            .replace(Regex("\\s+"), " ")
            .trim(' ', ',', '-', ':')
            .replace(Regex("^(?:to|about)\\s+", RegexOption.IGNORE_CASE), "")
            .trim()

        if (title.isBlank()) title = "New JARVIS task"
        return ParsedReminder(title, time)
    }
}
