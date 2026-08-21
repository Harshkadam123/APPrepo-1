package com.harsh.jarvis.actions

import com.harsh.jarvis.security.ActionLevel
import com.harsh.jarvis.security.ConfirmationManager
import com.harsh.jarvis.security.PermissionManager
import com.harsh.jarvis.verification.VerificationEngine

/**
 * Central execution gateway for JARVIS actions.
 *
 * Responsibilities:
 * - Check whether an action requires confirmation.
 * - Check permission policy before execution.
 * - Execute the action.
 * - Verify the result.
 * - Track the current executor state.
 * - Keep the last executed action available for retry/history logic.
 */
class ActionExecutor(
    private val confirmationManager: ConfirmationManager,
    private val permissionManager: PermissionManager,
    private val verifier: VerificationEngine = VerificationEngine()
) {

    @Volatile
    private var state: ExecutorState = ExecutorState.IDLE

    @Volatile
    private var lastAction: JarvisAction? = null

    /**
     * Requests execution of an action.
     *
     * SAFE actions execute immediately.
     * CONFIRM and CRITICAL actions wait for user confirmation.
     */
    suspend fun request(action: JarvisAction): ActionResult {
        if (action.level != ActionLevel.SAFE) {
            state = ExecutorState.WAITING_CONFIRMATION

            confirmationManager.request(action)

            val levelText =
                if (action.level == ActionLevel.CRITICAL) {
                    "critical "
                } else {
                    ""
                }

            return ActionResult(
                status = ActionStatus.NEEDS_USER,
                actual = action.description,
                userFeedback =
                    "Confirmation required before this ${levelText}action."
            )
        }

        return executeAndVerify(action)
    }

    /**
     * Confirms and executes the currently pending action.
     */
    suspend fun confirm(): ActionResult {
        val action = confirmationManager.takePending()

        if (action == null) {
            state = ExecutorState.IDLE

            return ActionResult(
                status = ActionStatus.FAILED,
                expected = "A pending action",
                userFeedback = "There is no pending action.",
                problem = "No pending action exists."
            )
        }

        return executeAndVerify(action)
    }

    /**
     * Executes an action after permission checking and verifies its result.
     */
    private suspend fun executeAndVerify(
        action: JarvisAction
    ): ActionResult {

        state = ExecutorState.EXECUTING
        lastAction = action

        return try {

            /*
             * If an action has an action-specific permission callback,
             * it must allow execution.
             */
            val allowed = action.allowed?.invoke()

            if (allowed == false) {
                state = ExecutorState.IDLE

                return ActionResult(
                    status = ActionStatus.FAILED,
                    expected = action.description,
                    actual = "The action is blocked by JARVIS permissions.",
                    problem = "Permission policy denied the action.",
                    cause = "Required access is no longer allowed.",
                    fix = "Enable the required access and try again."
                )
            }

            state = ExecutorState.VERIFYING

            val executionResult = action.execute()

            val verifiedResult = verifier.verify(executionResult)

            state = ExecutorState.IDLE

            verifiedResult

        } catch (throwable: Throwable) {

            state = ExecutorState.IDLE

            ActionResult(
                status = ActionStatus.FAILED,
                expected = action.description,
                actual = "The Android operation threw an error.",
                problem = "The action could not be completed.",
                cause =
                    throwable.message
                        ?: throwable::class.simpleName
                        ?: "Unknown error",
                fix = "Check the required permission/app state and try again."
            )
        }
    }

    /**
     * Cancels the currently pending confirmation.
     */
    fun cancel() {
        confirmationManager.cancel()
        state = ExecutorState.IDLE
    }

    /**
     * Returns true when an action is waiting for confirmation.
     */
    fun hasPending(): Boolean {
        return confirmationManager.hasPendingAction()
    }

    /**
     * Returns the description of the pending action.
     */
    fun pendingDescription(): String? {
        return confirmationManager.pendingDescription()
    }

    /**
     * Returns the current executor state.
     */
    fun currentState(): ExecutorState {
        return state
    }

    /**
     * Returns the last action that was executed or attempted.
     */
    fun lastExecutedAction(): JarvisAction? {
        return lastAction
    }
}

/**
 * Current state of the action execution pipeline.
 */
enum class ExecutorState {
    IDLE,
    WAITING_CONFIRMATION,
    EXECUTING,
    VERIFYING
}