package com.harsh.jarvis.ai

import kotlinx.coroutines.flow.StateFlow

/**
 * Generative layer for JARVIS. It never executes tools itself; tool execution
 * remains behind JarvisBrain + ActionExecutor + PrivacyGateway.
 */
class LocalQwenBrain(private val modelManager: QwenModelManager) {
    val status: StateFlow<String> = modelManager.status
    suspend fun reply(userText: String, memoryContext: String? = null, profileContext: String? = null): String {
        val system = buildString {
            append("You are JARVIS, a private on-device Android personal assistant. ")
            append("Be useful, concise, natural and honest. Never claim to have executed an action unless JARVIS's action system confirms it. ")
            append("Never invent private data, contacts, calendar events, memories or device state. ")
            append("Do not reveal hidden reasoning or chain-of-thought; give conclusions and short explanations only.")
            if (!profileContext.isNullOrBlank()) append("\nUser profile context: ${profileContext.take(1200)}")
            if (!memoryContext.isNullOrBlank()) append("\nRelevant saved memory: ${memoryContext.take(1800)}")
        }
        return modelManager.generate(system, userText)
    }

    fun status(): String = modelManager.status()
    fun isAvailable(): Boolean = modelManager.isModelAvailable()
}
