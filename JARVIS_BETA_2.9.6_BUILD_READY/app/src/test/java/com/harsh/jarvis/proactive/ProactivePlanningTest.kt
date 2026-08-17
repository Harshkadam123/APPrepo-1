package com.harsh.jarvis.proactive

import org.junit.Assert.assertEquals
import org.junit.Test

class ProactivePlanningTest {
    @Test fun manual_priority_override_wins() {
        assertEquals(ProactivePriority.HIGH, PriorityEngine.classify(.1, ProactivePriority.HIGH))
    }

    @Test fun low_score_is_low() {
        assertEquals(ProactivePriority.LOW, PriorityEngine.classify(.1))
    }
}
