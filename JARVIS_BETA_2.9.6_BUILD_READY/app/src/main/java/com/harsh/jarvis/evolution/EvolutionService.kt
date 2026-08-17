package com.harsh.jarvis.evolution

/**
 * Small application-facing boundary. AI, proactive logic, tasks and voice can
 * depend on this interface later without knowing Room details.
 */
interface EvolutionService {
    suspend fun status(): String
    suspend fun weakness(): String
    suspend fun nextChallenge(): EvolutionQuest?
    suspend fun complete(id: Long): Result<String>
    suspend fun completeFirstPending(): Result<String>
    suspend fun startChallenge(): EvolutionQuest?
}

class LocalEvolutionService(private val repository: EvolutionRepository) : EvolutionService {
    override suspend fun status() = repository.todaySummary()
    override suspend fun weakness() = repository.weaknessSummary()
    override suspend fun nextChallenge() = repository.recommendNextChallenge()
    override suspend fun complete(id: Long) = repository.completeQuest(id)
    override suspend fun completeFirstPending() = repository.completeFirstPending()
    override suspend fun startChallenge() = repository.startRecommendedChallenge()
}
