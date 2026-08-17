package com.harsh.jarvis.evolution

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EvolutionFormulaTest {
    @Test fun levelStartsAtOne() {
        assertEquals(1, EvolutionFormula().levelForXp(0))
    }
    @Test fun levelIncreasesOnlyAfterThreshold() {
        val f = EvolutionFormula()
        val threshold = f.xpForNextLevel(1)
        assertEquals(1, f.levelForXp(threshold - 1))
        assertEquals(2, f.levelForXp(threshold))
    }
    @Test fun meaningfulRewardIsPositiveAndDifficultyMatters() {
        val f = EvolutionFormula()
        val easy = f.reward(100, 1)
        val hard = f.reward(100, 8)
        assertTrue(easy > 0)
        assertTrue(hard > easy)
    }
    @Test fun partialCompletionDoesNotGrantFullReward() {
        val f = EvolutionFormula()
        assertTrue(f.reward(100, 5, completion = .5) < f.reward(100, 5))
    }
}
