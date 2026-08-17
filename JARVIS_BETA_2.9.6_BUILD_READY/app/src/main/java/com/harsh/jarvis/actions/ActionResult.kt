package com.harsh.jarvis.actions

enum class ActionStatus { SUCCESS, FAILED, PARTIAL, NEEDS_USER, CANCELLED }

data class ActionResult(
    val status: ActionStatus,
    val expected: String,
    val actual: String,
    val problem: String? = null,
    val cause: String? = null,
    val fix: String? = null,
    val verified: Boolean = false,
    val evidence: String? = null
) {
    fun userFeedback(): String = when (status) {
        ActionStatus.SUCCESS -> "Done. $actual"
        ActionStatus.FAILED -> buildString {
            append("Failed — "); append(problem ?: actual)
            if (!cause.isNullOrBlank()) append(" Cause: $cause.")
            if (!fix.isNullOrBlank()) append(" Fix: $fix")
        }
        ActionStatus.PARTIAL -> buildString {
            append("Partially done — $actual")
            if (!problem.isNullOrBlank()) append(" Problem: $problem")
            if (!fix.isNullOrBlank()) append(" Fix: $fix")
        }
        ActionStatus.NEEDS_USER -> actual
        ActionStatus.CANCELLED -> "Cancelled. I didn't execute the action."
    }
}
