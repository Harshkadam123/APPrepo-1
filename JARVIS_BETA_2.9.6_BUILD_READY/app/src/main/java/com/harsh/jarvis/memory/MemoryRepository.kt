package com.harsh.jarvis.memory

/**
 * Lightweight semantic-ish memory retrieval.
 *
 * Retrieval order:
 * 1. Exact normalized phrase match
 * 2. Token overlap
 * 3. Prefix similarity
 * 4. Synonym similarity
 * 5. Phrase coverage
 * 6. Small recency bonus
 *
 * This class is completely local and deterministic.
 */
class MemoryRepository(
    private val dao: MemoryDao
) {

    /**
     * Saves a memory after trimming and validating the text.
     *
     * Returns the database ID when the insert succeeds and can
     * subsequently be found, otherwise null.
     */
    suspend fun save(text: String): Long? {

        val clean: String = text.trim()

        if (clean.isBlank()) {
            return null
        }

        return runCatching {

            val id: Long =
                dao.insert(
                    Memory(
                        text = clean
                    )
                )

            val saved: Memory? =
                dao.findById(id)

            if (saved != null) {
                id
            } else {
                null
            }

        }.getOrNull()
    }

    /**
     * Finds one memory by database ID.
     */
    suspend fun findById(id: Long): Memory? {
        return dao.findById(id)
    }

    /**
     * Observes all memories.
     */
    fun observeAll() = dao.observeAll()

    /**
     * Returns the latest memories.
     */
    suspend fun latest(): List<Memory> {
        return dao.latest()
    }

    /**
     * Searches memories using deterministic local ranking.
     */
    suspend fun search(query: String): List<Memory> {

        val clean: String =
            query.trim()

        if (clean.isBlank()) {
            return latest()
        }

        val all: List<Memory> =
            runCatching {
                dao.all()
            }.getOrElse {
                emptyList()
            }

        if (all.isEmpty()) {
            return emptyList()
        }

        val normalizedQuery: String =
            normalize(clean)

        if (normalizedQuery.isBlank()) {
            return latest()
        }

        /*
         * First: exact normalized phrase.
         */
        val exact: List<Memory> =
            all.filter { memory ->
                normalize(memory.text)
                    .contains(normalizedQuery)
            }

        if (exact.isNotEmpty()) {
            return exact.take(MAX_RESULTS)
        }

        /*
         * Tokenize the query once.
         */
        val queryTokens: Set<String> =
            tokens(normalizedQuery)

        if (queryTokens.isEmpty()) {
            return emptyList()
        }

        /*
         * Calculate a score for every memory.
         *
         * Explicit Int types are intentionally used here.
         * This avoids Kotlin's sumOf(Int)/sumOf(Long) overload
         * ambiguity that caused the previous GitHub compilation error.
         */
        val scored: List<Pair<Memory, Int>> =
            all.map { memory ->

                val memoryTokens: Set<String> =
                    tokens(
                        normalize(memory.text)
                    )

                /*
                 * Token overlap score.
                 */
                var overlapScore: Int = 0

                for (token: String in queryTokens) {

                    if (token in memoryTokens) {

                        overlapScore += 3

                    } else {

                        val prefixMatch: Boolean =
                            memoryTokens.any { memoryToken ->
                                memoryToken.startsWith(token) ||
                                    token.startsWith(memoryToken)
                            }

                        if (prefixMatch) {
                            overlapScore += 2
                        } else {

                            val synonymMatch: Boolean =
                                SYNONYMS[token]
                                    .orEmpty()
                                    .any { synonym ->
                                        synonym in memoryTokens
                                    }

                            if (synonymMatch) {
                                overlapScore += 2
                            }
                        }
                    }
                }

                /*
                 * Coverage score.
                 */
                var coverage: Int = 0

                for (token: String in queryTokens) {

                    val directMatch: Boolean =
                        token in memoryTokens

                    val prefixMatch: Boolean =
                        memoryTokens.any { memoryToken ->
                            memoryToken.startsWith(token) ||
                                token.startsWith(memoryToken)
                        }

                    val synonymMatch: Boolean =
                        SYNONYMS[token]
                            .orEmpty()
                            .any { synonym ->
                                synonym in memoryTokens
                            }

                    if (
                        directMatch ||
                        prefixMatch ||
                        synonymMatch
                    ) {
                        coverage += 1
                    }
                }

                /*
                 * Phrase bonus.
                 *
                 * If every query token is represented directly
                 * or through a synonym, give the memory an additional
                 * score.
                 */
                val phraseBonus: Int =
                    if (
                        queryTokens.size > 1 &&
                        queryTokens.all { token ->

                            val directMatch: Boolean =
                                token in memoryTokens

                            val synonymMatch: Boolean =
                                SYNONYMS[token]
                                    .orEmpty()
                                    .any { synonym ->
                                        synonym in memoryTokens
                                    }

                            directMatch || synonymMatch
                        }
                    ) {
                        3
                    } else {
                        0
                    }

                /*
                 * Recent-memory bonus.
                 */
                val now: Long =
                    System.currentTimeMillis()

                val sevenDaysMillis: Long =
                    7L * 24L * 60L * 60L * 1000L

                val age: Long =
                    now - memory.createdAt

                val recencyBonus: Int =
                    if (
                        age >= 0L &&
                        age < sevenDaysMillis
                    ) {
                        1
                    } else {
                        0
                    }

                /*
                 * Explicit Int prevents overload ambiguity.
                 */
                val totalScore: Int =
                    overlapScore +
                        coverage +
                        phraseBonus +
                        recencyBonus

                Pair(
                    memory,
                    totalScore
                )
            }

        /*
         * Only return memories with a positive score.
         */
        val ranked: List<Pair<Memory, Int>> =
            scored
                .filter { pair ->
                    pair.second > 0
                }
                .sortedWith(
                    compareByDescending<Pair<Memory, Int>> { pair ->
                        pair.second
                    }.thenByDescending { pair ->
                        pair.first.createdAt
                    }
                )

        return ranked
            .take(MAX_RESULTS)
            .map { pair ->
                pair.first
            }
    }

    /**
     * Normalizes text for deterministic comparison.
     */
    private fun normalize(
        value: String
    ): String {

        return value
            .lowercase()
            .replace(
                Regex("[^a-z0-9 ]"),
                " "
            )
            .replace(
                Regex("\\s+"),
                " "
            )
            .trim()
    }

    /**
     * Converts normalized text into useful search tokens.
     */
    private fun tokens(
        value: String
    ): Set<String> {

        val normalized: String =
            normalize(value)

        if (normalized.isBlank()) {
            return emptySet()
        }

        return normalized
            .split(" ")
            .filter { token ->
                token.length > 2 &&
                    token !in STOPWORDS
            }
            .toSet()
    }

    companion object {

        private const val MAX_RESULTS: Int = 5

        /**
         * Small deterministic synonym dictionary.
         */
        private val SYNONYMS:
            Map<String, Set<String>> =
            mapOf(

                "project" to setOf(
                    "app",
                    "assistant",
                    "application"
                ),

                "projects" to setOf(
                    "app",
                    "assistant",
                    "application"
                ),

                "app" to setOf(
                    "project",
                    "application"
                ),

                "application" to setOf(
                    "app",
                    "project"
                ),

                "study" to setOf(
                    "learn",
                    "learning",
                    "practice"
                ),

                "learn" to setOf(
                    "study",
                    "learning",
                    "practice"
                ),

                "learning" to setOf(
                    "study",
                    "learn",
                    "practice"
                ),

                "practice" to setOf(
                    "study",
                    "learn",
                    "learning"
                ),

                "ai" to setOf(
                    "artificial",
                    "intelligence",
                    "assistant"
                ),

                "assistant" to setOf(
                    "ai",
                    "jarvis"
                ),

                "jarvis" to setOf(
                    "assistant",
                    "ai"
                )
            )

        /**
         * Common words that should not influence retrieval.
         */
        private val STOPWORDS:
            Set<String> =
            setOf(
                "the",
                "and",
                "that",
                "this",
                "about",
                "what",
                "which",
                "with",
                "for",
                "from",
                "you",
                "your",
                "my",
                "are",
                "was",
                "were",
                "how",
                "did",
                "do"
            )
    }
}
