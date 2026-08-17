package com.harsh.jarvis.privacy

/**
 * Single privacy boundary for personal-data capabilities.
 *
 * No Android contacts/messages/files/notifications APIs are exposed here yet.
 * Future tools must request a narrow capability and return minimized data.
 */
class PrivacyGateway(
    private val policy: PrivacyPolicyStore,
    private val audit: PrivacyAuditRepository
) {
    fun mode(capability: PrivacyCapability): PrivacyMode = policy.mode(capability)

    fun canUse(capability: PrivacyCapability): Boolean =
        mode(capability) == PrivacyMode.ALLOW

    fun requiresUserApproval(capability: PrivacyCapability): Boolean =
        mode(capability) == PrivacyMode.ASK

    fun isBlocked(capability: PrivacyCapability): Boolean =
        mode(capability) == PrivacyMode.NEVER

    suspend fun record(
        capability: PrivacyCapability,
        purpose: String,
        dataExposed: String,
        outcome: String
    ) {
        audit.record(capability, mode(capability), purpose, dataExposed, outcome)
    }

    fun policyStore(): PrivacyPolicyStore = policy

    fun allowedOrAsk(capability: PrivacyCapability): Boolean = !isBlocked(capability)
}
