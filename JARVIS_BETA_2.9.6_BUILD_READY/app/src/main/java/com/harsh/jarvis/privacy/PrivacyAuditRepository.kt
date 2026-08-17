package com.harsh.jarvis.privacy

import kotlinx.coroutines.flow.Flow

class PrivacyAuditRepository(private val dao: PrivacyAuditDao) {
    suspend fun record(
        capability: PrivacyCapability,
        mode: PrivacyMode,
        purpose: String,
        dataExposed: String,
        outcome: String
    ) {
        dao.insert(
            PrivacyAudit(
                capability = capability.name,
                mode = mode.name,
                purpose = purpose,
                dataExposed = dataExposed,
                outcome = outcome
            )
        )
    }

    fun observeLatest(): Flow<List<PrivacyAudit>> = dao.observeLatest()
    suspend fun clear() = dao.clear()
}
