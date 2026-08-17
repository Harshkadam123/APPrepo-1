package com.harsh.jarvis.time

import org.junit.Assert.*
import org.junit.Test

class ReminderParserTest {
    private val parser = ReminderParser()
    private val now = 1_760_000_000_000L

    @Test fun relativeDurationKeepsTitle() {
        val r = parser.parse("remind me to study Python in 30 minutes", now)
        assertEquals("study Python", r.title)
        assertNotNull(r.time)
    }

    @Test fun tomorrowClockKeepsTitle() {
        val r = parser.parse("remind me to revise mathematics tomorrow at 7 PM", now)
        assertEquals("revise mathematics", r.title)
        assertNotNull(r.time)
    }

    @Test fun aboutFormKeepsTitle() {
        val r = parser.parse("remind me about my exam tomorrow morning", now)
        assertEquals("my exam", r.title)
        assertNotNull(r.time)
    }

    @Test fun weekdayClockWorks() {
        val r = parser.parse("set a reminder to call mom Friday at 8:30 AM", now)
        assertEquals("call mom", r.title)
        assertNotNull(r.time)
    }

    @Test fun plainReminderStillNeedsSchedule() {
        val r = parser.parse("remind me to work on JARVIS", now)
        assertEquals("work on JARVIS", r.title)
        assertNull(r.time)
    }
}
