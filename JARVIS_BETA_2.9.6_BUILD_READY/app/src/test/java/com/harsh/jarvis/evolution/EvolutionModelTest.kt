package com.harsh.jarvis.evolution

import org.junit.Assert.assertEquals
import org.junit.Test

class EvolutionModelTest {
    @Test fun questDefaultsToPending() {
        assertEquals("PENDING", EvolutionQuest(title = "x").status)
    }
    @Test fun profileStartsAtLevelOne() {
        assertEquals(1, EvolutionProfile().level)
    }
}
