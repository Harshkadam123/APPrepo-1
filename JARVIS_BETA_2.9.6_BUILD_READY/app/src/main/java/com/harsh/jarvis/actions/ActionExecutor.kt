package com.harsh.jarvis.actions

import com.harsh.jarvis.security.ActionLevel
import com.harsh.jarvis.security.ConfirmationManager
import com.harsh.jarvis.security.PermissionManager
import com.harsh.jarvis.verification.VerificationEngine

class ActionExecutor(
    private val confirmationManager: ConfirmationManager,
    private val permissionManager: PermissionManager,
    private val verifier: VerificationEngine = VerificationEngine()
) {
    @Volatile private var state: ExecutorState = ExecutorState.IDLE
    @Volatile private var lastAction: JarvisAction? = null

    suspend fun request(action: JarvisAction): ActionResult {
        if (action.level != ActionLevel.SAFE) {
            state = ExecutorState.WAITING_CONFIRMATION
            confirmationManager.request(action)
            return ActionResult(ActionStatus.NEEDS_USER, action.description,
                "Confirmation required before this ${if (action.level == ActionLevel.CRITICAL) "critical " else ""}action.")
        }
        return executeAndVerify(action)
    }

    suspend fun confirm(): ActionResult {
        val action = confirmationManager.takePending() ?: return ActionResult(
            ActionStatus.FAILED, "A pending action", "There is no pending action.",
            problem = "No pending action exists."
        )
        return executeAndVerify(action)
    }

    private suspend fun executeAndVerify(action: JarvisAction): ActionResult = try {
        state = ExecutorState.EXECUTING
        lastAction = action
        if (action.allowed?.invoke() == false) {
            state = ExecutorState.IDLE
            return ActionResult(ActionStatus.FAILED, action.description,
                "The action is blocked by JARVIS permissions.",
                problem = "Permission policy denied the action.",
                cause = "Required access is no longer allowed.",
                fix = "Enable the required access and try again.")
        }
        state = ExecutorState.VERIFYING
        val result = verifier.verify(action.execute())
        state = ExecutorState.IDLE
        result
    } catch (t: Throwable) {
        state = ExecutorState.IDLE
        ActionResult(ActionStatus.FAILED, action.description, "The Android operation threw an error.",
            problem = "The action could not be completed.",
            cause = t.message ?: t::class.simpleName ?: "Unknown error",
            fix = "Check the required permission/app state and try again.")
    }

    fun cancel() { confirmationManager.cancel(); state = ExecutorState.IDLE }
    fun hasPending() = confirmationManager.hasPendingAction()
    fun pendingDescription() = confirmationManager.pendingDescription()
    fun currentState(): ExecutorState = state
    fun lastExecutedAction(): JarvisAction? = lastAction
}
enum class ExecutorState { IDLE, WAITING_CONFIRMATION, EXECUTING, VERIFYING }
