package com.harsh.jarvis.ai

/**
 * Capability router. Deterministic routing happens before any model work.
 * BETA 2.9 currently has no shipped generative/reasoning model, so complex
 * requests remain on the safe deterministic path rather than hallucinating.
 */
class ModelRouter(private val catalog: List<ModelSpec> = ModelCatalog.all) {
    fun select(task: ModelTask): ModelSpec? = catalog
        .filter { task in it.supportedTasks }
        .minByOrNull { it.ramEstimateMb * 1000 - it.priority }

    fun requiresGenerativeModel(task: ModelTask): Boolean = when (task) {
        ModelTask.REASONING, ModelTask.DOCUMENT_QA, ModelTask.CONVERSATION ->
            catalog.none { task in it.supportedTasks && it.role != ModelRole.INTENT }
        else -> false
    }
}
