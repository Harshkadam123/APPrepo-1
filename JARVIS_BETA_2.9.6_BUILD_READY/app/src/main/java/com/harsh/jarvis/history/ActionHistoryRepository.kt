package com.harsh.jarvis.history

import com.harsh.jarvis.actions.*
import org.json.JSONObject

class ActionHistoryRepository(private val dao: ActionHistoryDao) {
    private fun payloadJson(action: JarvisAction): String =
        JSONObject(action.payload).toString()

    suspend fun recordLifecycle(request: String, action: JarvisAction, lifecycle: String): Long =
        dao.insert(ActionHistory(
            request=request, actionName=action.name, actionLevel=action.level.name,
            status=ActionStatus.NEEDS_USER.name, lifecycle=lifecycle,
            expected=action.description, actual="Lifecycle: $lifecycle",
            payloadJson=payloadJson(action)
        ))

    suspend fun record(request: String, action: JarvisAction, result: ActionResult, lifecycle: String = "COMPLETED"): Long =
        dao.insert(ActionHistory(
            request=request, actionName=action.name, actionLevel=action.level.name,
            status=result.status.name, lifecycle=lifecycle, expected=result.expected, actual=result.actual,
            problem=result.problem, cause=result.cause, fix=result.fix, evidence=result.evidence,
            payloadJson=payloadJson(action), verified=result.verified
        ))

    suspend fun updateResult(id: Long, result: ActionResult, lifecycle: String = "COMPLETED") =
        dao.updateResult(id, result.status.name, lifecycle, result.actual, result.problem, result.cause,
            result.fix, result.evidence, result.verified)

    suspend fun latest(limit: Int = 10) = dao.latest(limit.coerceIn(1,100))
    suspend fun latestRetryable() = dao.latestRetryable()
    suspend fun findById(id: Long) = dao.findById(id)
    fun observeLatest() = dao.observeLatest()
}
