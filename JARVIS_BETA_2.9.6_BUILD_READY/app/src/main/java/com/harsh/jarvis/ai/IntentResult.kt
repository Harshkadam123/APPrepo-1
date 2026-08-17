package com.harsh.jarvis.ai

data class IntentResult(
    val intent: IntentType,
    val confidence: Double,
    val arguments: Map<String, String> = emptyMap()
)
