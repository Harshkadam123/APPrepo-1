package com.harsh.jarvis.evolution

data class CommunicationContext(
    val isQuietPeriod: Boolean,
    val isBusy: Boolean,
    val freeMinutes: Int,
    val priority: Int = 0
)

class EvolutionProactiveAdvisor(private val repository: EvolutionRepository) {
    suspend fun suggestion(context: CommunicationContext): String? {
        if (context.isQuietPeriod || context.isBusy || context.freeMinutes < 15) return null
        val challenge = repository.recommendNextChallenge() ?: return null
        return "You have ${context.freeMinutes} minutes free. Your next recommended challenge is ${challenge.title}."
    }
}
