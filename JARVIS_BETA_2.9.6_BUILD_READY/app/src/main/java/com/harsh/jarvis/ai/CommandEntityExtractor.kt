package com.harsh.jarvis.ai

import com.harsh.jarvis.time.ReminderParser
import com.harsh.jarvis.time.TimeParser

/** Deterministic entity extraction used before execution; no private Android data is exposed. */
class CommandEntityExtractor(
    private val timeParser: TimeParser = TimeParser(),
    private val reminderParser: ReminderParser = ReminderParser(timeParser)
) {
    data class Entities(
        val reminderTitle: String? = null,
        val hasSchedule: Boolean = false,
        val appName: String? = null,
        val memoryText: String? = null
    )

    fun extract(command: String, now: Long = System.currentTimeMillis()): Entities {
        val text = command.trim()
        val lower = text.lowercase()
        val schedule = timeParser.parse(text, now) != null
        val memory = Regex("^\\s*(?:remember|save this|store this|keep this in memory)\\s+(?:that\\s+)?(.+)$", RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.getOrNull(1)?.trim()
        val app = Regex("^\\s*(?:open|launch|start|run)\\s+(.+?)\\s*$", RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.getOrNull(1)?.trim()
        val isReminder = lower.matches(Regex(".*\\b(remind|reminder|set a task|create a task|make a reminder)\\b.*"))
        val title = if (isReminder) {
            reminderParser.parse(text, now).title.takeIf { it.isNotBlank() && it != "New JARVIS task" }
        } else null
        return Entities(title, schedule, app, memory)
    }
}
