package com.harsh.jarvis.ai

/** Central inventory for on-device AI assets. No model is loaded by this registry. */
data class ModelSpec(
    val name: String,
    val path: String,
    val role: ModelRole,
    val format: String,
    val quantization: String,
    val contextLength: Int,
    val ramEstimateMb: Int,
    val cpuClass: String,
    val priority: Int,
    val supportedTasks: Set<ModelTask>,
    val fallbackModel: String?,
    val loadPolicy: LoadPolicy
)

enum class ModelRole { INTENT, CONVERSATION, REASONING, DOCUMENT, TOOL_SELECTION }
enum class ModelTask { INTENT, CONVERSATION, REASONING, DOCUMENT_QA, TOOL_SELECTION, EXTRACTION }
enum class LoadPolicy { ON_DEMAND, REUSE_WHILE_ACTIVE }

object ModelCatalog {
    // JARVIS ships the intent model; Qwen3 is a user-provided GGUF loaded from shared storage or imported privately.
    val intent = ModelSpec(
        name = "PersonalIntentModel",
        path = "intent_model.json",
        role = ModelRole.INTENT,
        format = "JSON Naive-Bayes statistics",
        quantization = "N/A",
        contextLength = 256,
        ramEstimateMb = 8,
        cpuClass = "low",
        priority = 100,
        supportedTasks = setOf(ModelTask.INTENT, ModelTask.EXTRACTION),
        fallbackModel = null,
        loadPolicy = LoadPolicy.REUSE_WHILE_ACTIVE
    )

    val qwen3 = ModelSpec(
        name = "Qwen3-1.7B-Q4_K_M",
        // The model is user-provided; ResourceLocator/SAF determines the real device location.
        path = "Qwen3-1.7B-Q4_K_M.gguf",
        role = ModelRole.CONVERSATION,
        format = "GGUF / llama.cpp",
        quantization = "Q4_K_M",
        contextLength = 2048,
        ramEstimateMb = 1600,
        cpuClass = "arm64-v8a / NEON",
        priority = 90,
        supportedTasks = setOf(ModelTask.CONVERSATION, ModelTask.REASONING, ModelTask.DOCUMENT_QA),
        fallbackModel = "PersonalIntentModel",
        loadPolicy = LoadPolicy.ON_DEMAND
    )

    val all: List<ModelSpec> = listOf(intent, qwen3)
}
