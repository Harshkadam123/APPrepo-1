package com.harsh.jarvis.focus

import android.content.Context
import java.text.SimpleDateFormat
import java.util.*

class FocusStats(context: Context) {
    private val manager = FocusManager(context)
    private val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    data class DayStat(val day: String, val minutes: Int)
    data class Achievement(val id: String, val title: String, val description: String, val unlocked: Boolean)
    fun daily(days: Int = 7): List<DayStat> {
        val cal = Calendar.getInstance(); cal.add(Calendar.DAY_OF_YEAR, -(days - 1))
        val map = manager.sessions().groupBy { fmt.format(Date(it.endedAt)) }.mapValues { (_, v) -> v.sumOf { (it.durationMs / 60000).toInt() } }
        return (0 until days).map { i -> val d = Calendar.getInstance().apply { timeInMillis = cal.timeInMillis; add(Calendar.DAY_OF_YEAR, i) }; val key = fmt.format(d.time); DayStat(key, map[key] ?: 0) }
    }
    fun achievements(): List<Achievement> {
        val total = manager.totalMinutes(36500); val sessions = manager.sessions().size; val streak = manager.currentStreak()
        return listOf(
            Achievement("first", "First Focus", "Complete your first focus session", sessions >= 1),
            Achievement("hour", "One Hour", "Accumulate 60 focused minutes", total >= 60),
            Achievement("ten", "Ten Sessions", "Complete 10 sessions", sessions >= 10),
            Achievement("seven", "7-Day Streak", "Focus on seven consecutive days", streak >= 7),
            Achievement("tenhour", "10 Hours", "Accumulate 600 focused minutes", total >= 600),
            Achievement("month", "30-Day Streak", "Focus on thirty consecutive days", streak >= 30)
        )
    }
}
