package com.harsh.jarvis.proactive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PriorityEngineTest {
    @Test fun urgent_deadline_becomes_high_or_critical() {
        val score = PriorityEngine.score(1.0, 1.0, 0.98, 1.0, 1.0)
        assertTrue(score >= .85)
        assertEquals(ProactivePriority.CRITICAL, PriorityEngine.classify(score))
    }

    @Test fun distant_deadline_is_less_urgent() {
        assertTrue(PriorityEngine.deadlineFactor(System.currentTimeMillis() + 8L * 24 * 60 * 60 * 1000) < .3)
    }
}
