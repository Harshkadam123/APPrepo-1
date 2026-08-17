package com.harsh.jarvis.evolution

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.pow

/**
 * Pure deterministic progression rules. Keeping these outside Android/Room makes
 * the progression engine cheap to test and easy to tune later.
 */
data class EvolutionFormulaConfig(
    val baseXp: Long = 100,
    val growth: Double = 1.35,
    val maxQuestDifficulty: Int = 10,
    val repeatDiminishingFactor: Double = 0.25
)

class EvolutionFormula(private val config: EvolutionFormulaConfig = EvolutionFormulaConfig()) {
    fun xpForNextLevel(level: Int): Long =
        max(config.baseXp, floor(config.baseXp * (level.coerceAtLeast(1).toDouble().pow(config.growth))).toLong())

    fun levelForXp(xp: Long): Int {
        var level = 1
        var spent = 0L
        while (level < 1000) {
            val next = xpForNextLevel(level)
            if (spent + next > xp) break
            spent += next
            level++
        }
        return level
    }

    fun xpToReachLevel(level: Int): Long {
        var total = 0L
        for (l in 1 until level.coerceAtLeast(1)) total += xpForNextLevel(l)
        return total
    }

    fun currentXp(xp: Long, level: Int = levelForXp(xp)): Long = xp - xpToReachLevel(level)

    fun xpToNextLevel(level: Int): Long = xpForNextLevel(level)

    fun reward(base: Long, difficulty: Int, completion: Double = 1.0, quality: Double = 1.0,
               consistency: Double = 1.0, importance: Double = 1.0): Long {
        val difficultyMultiplier = 0.65 + difficulty.coerceIn(1, config.maxQuestDifficulty) * 0.15
        val value = base.coerceAtLeast(1) * difficultyMultiplier *
            completion.coerceIn(0.0, 1.0) * quality.coerceIn(0.25, 1.0) *
            consistency.coerceIn(0.5, 1.25) * importance.coerceIn(0.5, 1.5)
        return floor(value).toLong().coerceAtLeast(1)
    }
}
