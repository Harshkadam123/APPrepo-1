package com.harsh.jarvis.ai

/**
 * Small in-memory conversation state. It is intentionally not persisted because
 * short-lived conversational context can contain private text and should not
 * become permanent memory unless the user explicitly asks JARVIS to remember it.
 */
class ConversationContext {
    data class PendingFollowUp(
        val type: Type,
        val originalCommand: String,
        val prompt: String
    ) {
        enum class Type { REMINDER_SCHEDULE, REMINDER_TITLE, APP_NAME }
    }

    private var pending: PendingFollowUp? = null
    private var lastTopic: String? = null

    fun rememberTopic(topic: String) { lastTopic = topic.trim().takeIf { it.isNotBlank() } }
    fun topic(): String? = lastTopic

    fun set(followUp: PendingFollowUp) { pending = followUp }
    fun peek(): PendingFollowUp? = pending
    fun clear() { pending = null }

    fun resolve(answer: String): String? {
        val state = pending ?: return null
        val value = answer.trim()
        if (value.isBlank()) return null
        return when (state.type) {
            PendingFollowUp.Type.REMINDER_SCHEDULE -> {
                pending = null
                "${state.originalCommand} $value"
            }
            PendingFollowUp.Type.REMINDER_TITLE -> {
                pending = null
                "$value ${state.originalCommand}"
            }
            PendingFollowUp.Type.APP_NAME -> {
                pending = null
                "open $value"
            }
        }
    }
}
