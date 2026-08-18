package com.harsh.jarvis.memory

/** Lightweight semantic-ish retrieval: exact phrase first, then token-overlap ranking. */
class MemoryRepository(
    private val dao: MemoryDao
) {

    suspend fun save(text: String): Long? {
        val clean = text.trim()
        if (clean.isBlank()) return null

        val id: Long = dao.insert(
            Memory(text = clean)
        )

        return if (dao.findById(id) != null) id else null
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

        val all: List<Memory> = dao.all()

        if (all.isEmpty()) {
            return emptyList()
        }

        val normalizedQuery: String = normalize(clean)

        val exact: List<Memory> = all.filter { memory ->
            normalize(memory.text).contains(normalizedQuery)
        }

        if (exact.isNotEmpty()) {
            return exact.take(5)
        }

        val queryTokens: Set<String> = tokens(normalizedQuery)

        if (queryTokens.isEmpty()) {
            return emptyList()
        }

        val ranked = all.map { memory ->

            val memoryTokens: Set<String> =
                tokens(normalize(memory.text))

            var overlap: Int = 0

            for (token in queryTokens) {
                when {
                    token in memoryTokens -> {
                        overlap += 3
                    }

                    memoryTokens.any { memoryToken ->
                        memoryToken.startsWith(token) ||
                            token.startsWith(memoryToken)
                    } -> {
                        overlap += 2
                    }

                    SYNONYMS[token].orEmpty().any { synonym ->
                        synonym in memoryTokens
                    } -> {
                        overlap += 2
                    }
                }
            }

            var coverage: Int = 0

            for (token in queryTokens) {
                val matched =
                    token in memoryTokens ||
                        memoryTokens.any { memoryToken ->
                            memoryToken.startsWith(token) ||
                                token.startsWith(memoryToken)
                        } ||
                        SYNONYMS[token].orEmpty().any { synonym ->
                            synonym in memoryTokens
                        }

                if (matched) {
                    coverage += 1
                }
            }

            var phraseBonus: Int = 0

            if (queryTokens.size > 1) {
                var allMatched = true

                for (token in queryTokens) {
                    val matched =
                        token in memoryTokens ||
                            SYNONYMS[token].orEmpty().any { synonym ->
                                synonym in memoryTokens
                            }

                    if (!matched) {
                        allMatched = false
                        break
                    }
                }

                if (allMatched) {
                    phraseBonus = 3
                }
            }

            val age: Long =
                System.currentTimeMillis() - memory.createdAt

            val sevenDays: Long =
                7L * 24L * 60L * 60L * 1000L

            val recencyBonus: Int =
                if (age < sevenDays) 1 else 0

            val score: Int =
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
                token.length > 2 &&
                    token !in STOPWORDS
            }
            .toSet()
    }

    companion object {

        private val SYNONYMS: Map<String, Set<String>> = mapOf(
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

        private val STOPWORDS: Set<String> = setOf(
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
