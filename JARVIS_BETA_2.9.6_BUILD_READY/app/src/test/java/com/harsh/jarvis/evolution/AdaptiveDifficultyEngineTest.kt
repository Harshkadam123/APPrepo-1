package com.harsh.jarvis.evolution

import org.junit.Assert.assertEquals
import org.junit.Test

class AdaptiveDifficultyEngineTest {
    private val engine = AdaptiveDifficultyEngine()

    @Test fun repeatedSuccessRaisesDifficulty() {
        assertEquals(5, engine.nextDifficulty(PerformanceSignal(true, 4, .9)))
    }
    @Test fun repeatedFailureLowersDifficulty() {
        assertEquals(3, engine.nextDifficulty(PerformanceSignal(false, 4, .2)))
    }
    @Test fun neglectedSkillDoesNotGetPunished() {
        assertEquals(4, engine.nextDifficulty(PerformanceSignal(true, 4, .7, neglectedDays = 3)))
    }
}
