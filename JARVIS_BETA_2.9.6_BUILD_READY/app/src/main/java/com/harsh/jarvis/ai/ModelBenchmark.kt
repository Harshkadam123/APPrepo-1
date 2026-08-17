package com.harsh.jarvis.ai

/** Lightweight internal benchmark primitives; callers can supply real device timings. */
data class ModelBenchmarkSample(
    val task: ModelTask,
    val latencyMs: Long,
    val loadMs: Long,
    val unloadMs: Long,
    val success: Boolean,
    val confidence: Double,
    val ramBeforeMb: Long,
    val ramAfterMb: Long
)

data class ModelBenchmarkReport(val samples: List<ModelBenchmarkSample>) {
    val successRate: Double get() = if (samples.isEmpty()) 0.0 else samples.count { it.success }.toDouble() / samples.size
    val averageLatencyMs: Double get() = samples.map { it.latencyMs }.average().takeIf { !it.isNaN() } ?: 0.0
    val averageLoadMs: Double get() = samples.map { it.loadMs }.average().takeIf { !it.isNaN() } ?: 0.0
    val averageUnloadMs: Double get() = samples.map { it.unloadMs }.average().takeIf { !it.isNaN() } ?: 0.0
}
