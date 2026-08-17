package com.harsh.jarvis.proactive

import com.harsh.jarvis.tasks.Task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class DailyPlannerTest {
    @Test fun deadline_and_goal_priority_drive_first_slot() {
        val now = LocalDateTime.of(2026, 8, 17, 9, 0)
        val tasks = listOf(
            Task(title = "Optional quest", estimatedMinutes = 30, goalPriority = .4),
            Task(title = "DBMS assignment", dueTime = now.plusDays(1).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(), estimatedMinutes = 60, consequence = .9, goalPriority = .9)
        )
        val plan = DailyPlanner.plan(now, tasks, emptyList(), emptyList())
        assertTrue(plan.isNotEmpty())
        assertEquals("DBMS assignment", plan.first().title)
    }

    @Test fun protected_window_is_respected() {
        val now = LocalDateTime.of(2026, 8, 17, 9, 0)
        val task = Task(title = "Study", estimatedMinutes = 30)
        val rule = AvailabilityRule(state = AvailabilityState.BUSY.name, startMinute = 9*60, endMinute = 10*60, label = "quiet")
        val plan = DailyPlanner.plan(now, listOf(task), emptyList(), listOf(rule))
        assertTrue(plan.first().start.hour >= 10)
    }
}
