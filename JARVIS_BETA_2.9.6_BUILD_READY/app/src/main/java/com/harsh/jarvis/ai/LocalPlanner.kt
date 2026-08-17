package com.harsh.jarvis.ai

/** Deterministic planner: converts compound requests/routines into bounded steps. */
class LocalPlanner {
    fun plan(input: String): List<String> = input
        .split(Regex("\\s+(?:and then|then|after that)\\s+|\\s*;\\s*"), limit = 12)
        .map { it.trim() }.filter { it.isNotBlank() }.take(10)
}
