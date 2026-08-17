package com.harsh.jarvis.verification

import com.harsh.jarvis.actions.ActionResult
import com.harsh.jarvis.actions.ActionStatus

class VerificationEngine {
    fun verify(result: ActionResult): ActionResult {
        if (result.status != ActionStatus.SUCCESS || result.verified) return result
        return result.copy(
            status = ActionStatus.PARTIAL,
            problem = result.problem ?: "The action reported success without enough evidence.",
            cause = result.cause ?: "No concrete verification evidence was supplied.",
            fix = result.fix ?: "Check the result manually and retry if necessary.",
            evidence = result.evidence
        )
    }
}
