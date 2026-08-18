package com.harsh.jarvis.memory

/** Lightweight semantic-ish retrieval: exact phrase first, then token-overlap ranking. */
class MemoryRepository(
    private val dao: MemoryDao
) {

    suspend fun save(text: String): Long? {
        val clean = text.trim()
        if (clean.isBlank()) return null

        val id = dao.insert(
            Memory(text = clean)
        )

        return if (dao.findById(id) != null) {
            id
        } else {
            null
        }
    }

    suspend fun findById(id: Long): Memory? {
        return dao.findById(id)
    }

    fun observeAll() = dao.observeAll()

    suspend fun latest(): List<Memory> {
        return dao.latest()
    }

    suspend fun search(query: String): List<Memory> {
        val clean = query.trim()

        if (clean.isBlank()) {
            return latest()
        }

        val all = dao.all()
        val normalizedQuery = normalize(clean)

        val exact = all.filter { memory ->
            normalize(memory.text).contains(normalizedQuery)
        }

        if (exact.isNotEmpty()) {
            return exact.take(5)
        }

        val queryTokens = tokens(normalizedQuery)

        if (queryTokens.isEmpty()) {
            return emptyList()
        }

        val ranked: List<Pair<Memory, Int>> = all.map { memory ->
            val memoryTokens = tokens(normalize(memory.text))

            val overlap = queryTokens.sumOf { token ->
                when {
                    token in memoryTokens -> 3

                    memoryTokens.any { memoryToken ->
                        memoryToken.startsWith(token) ||
                            token.startsWith(memoryToken)
                    } -> 2

                    SYNONYMS[token].orEmpty().any { synonym ->
                        synonym in memoryTokens
                    } -> 2

                    else -> 0
                }
            }

            val coverage = queryTokens.count { token ->
                token in memoryTokens ||
                    memoryTokens.any { memoryToken ->
                        memoryToken.startsWith(token) ||
                            token.startsWith(memoryToken)
                    } ||
                    SYNONYMS[token].orEmpty().any { synonym ->
                        synonym in memoryTokens
                    }
            }

            val phraseBonus =
                if (
                    queryTokens.size > 1 &&
                    queryTokens.all { token ->
                        token in memoryTokens ||
                            SYNONYMS[token].orEmpty().any { synonym ->
                                synonym in memoryTokens
                            }
                    }
                ) {
                    3
                } else {
                    0
                }

            val recencyBonus =
                if (
                    System.currentTimeMillis() - memory.createdAt <
                    7L * 24L * 60L * 60L * 1000L
                ) {
                    1
                } else {
                    0
                }

            val score =
                overlap +
                    coverage +
                    phraseBonus +
                    recencyBonus

            Pair(memory, score)
        }

        return ranked
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
            .take(5)
            .map { pair ->
                pair.first
            }
    }

    private fun normalize(value: String): String {
        return value
            .lowercase()
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun tokens(value: String): Set<String> {
        return normalize(value)
            .split(" ")
            .filter { token ->
                token.length > 2 && token !in STOPWORDS
            }
            .toSet()
    }

    companion object {

        private val SYNONYMS = mapOf(
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
            "ai" to setOf(
                "artificial",
                "intelligence",
                "assistant"
            ),
            "assistant" to setOf(
                "ai",
                "jarvis"
            )
        )

        private val STOPWORDS = setOf(
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
