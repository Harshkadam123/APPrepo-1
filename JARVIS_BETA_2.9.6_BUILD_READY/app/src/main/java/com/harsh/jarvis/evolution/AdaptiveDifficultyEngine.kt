package com.harsh.jarvis.evolution

data class PerformanceSignal(
    val completed: Boolean,
    val difficulty: Int,
    val recentSuccessRate: Double,
    val neglectedDays: Int = 0
)

class AdaptiveDifficultyEngine {
    fun nextDifficulty(signal: PerformanceSignal): Int {
        val d = signal.difficulty.coerceIn(1, 10)
        return when {
            !signal.completed || signal.recentSuccessRate < 0.40 -> (d - 1).coerceAtLeast(1)
            signal.recentSuccessRate >= 0.85 -> (d + 1).coerceAtMost(10)
            signal.neglectedDays >= 3 -> d.coerceAtLeast(2)
            else -> d
        }
    }
}
