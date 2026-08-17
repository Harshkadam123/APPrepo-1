package com.harsh.jarvis.memory

/** Lightweight semantic-ish retrieval: exact phrase first, then token-overlap ranking. */
class MemoryRepository(private val dao: MemoryDao) {
    suspend fun save(text: String): Long? {
        val clean = text.trim()
        if (clean.isBlank()) return null
        val id = dao.insert(Memory(text = clean))
        return if (dao.findById(id) != null) id else null
    }

    suspend fun findById(id: Long): Memory? = dao.findById(id)
    fun observeAll() = dao.observeAll()
    suspend fun latest(): List<Memory> = dao.latest()

    suspend fun search(query: String): List<Memory> {
        val clean = query.trim()
        if (clean.isBlank()) return latest()
        val all = dao.all()
        val normalizedQuery = normalize(clean)
        val exact = all.filter { normalize(it.text).contains(normalizedQuery) }
        if (exact.isNotEmpty()) return exact.take(5)

        val q = tokens(normalizedQuery)
        if (q.isEmpty()) return emptyList()
        return all.map { memory ->
            val mt = tokens(normalize(memory.text))
            val overlap = q.sumOf { token ->
                when {
                    token in mt -> 3
                    mt.any { it.startsWith(token) || token.startsWith(it) } -> 2
                    SYNONYMS[token].orEmpty().any { it in mt } -> 2
                    else -> 0
                }
            }
            val coverage = q.count { token ->
                token in mt || mt.any { it.startsWith(token) || token.startsWith(it) } || SYNONYMS[token].orEmpty().any { it in mt }
            }
            val phraseBonus = if (q.size > 1 && q.all { token -> token in mt || SYNONYMS[token].orEmpty().any { it in mt } }) 3 else 0
            val recencyBonus = if (System.currentTimeMillis() - memory.createdAt < 7 * 24 * 60 * 60 * 1000L) 1 else 0
            memory to (overlap + coverage + phraseBonus + recencyBonus)
        }.filter { it.second > 0 }
            .sortedWith(compareByDescending<Pair<Memory, Int>> { it.second }.thenByDescending { it.first.createdAt })
            .take(5)
            .map { it.first }
    }

    private fun normalize(value: String) = value.lowercase()
        .replace(Regex("[^a-z0-9 ]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun tokens(value: String): Set<String> = normalize(value).split(" ")
        .filter { it.length > 2 && it !in STOPWORDS }
        .toSet()

    companion object {
        private val SYNONYMS = mapOf(
            "project" to setOf("app", "assistant", "application"),
            "projects" to setOf("app", "assistant", "application"),
            "app" to setOf("project", "application"),
            "study" to setOf("learn", "learning", "practice"),
            "learn" to setOf("study", "learning", "practice"),
            "ai" to setOf("artificial", "intelligence", "assistant"),
            "assistant" to setOf("ai", "jarvis")
        )
        private val STOPWORDS = setOf("the", "and", "that", "this", "about", "what", "which", "with", "for", "from", "you", "your", "my", "are", "was", "were", "how", "did", "do")
    }
}
