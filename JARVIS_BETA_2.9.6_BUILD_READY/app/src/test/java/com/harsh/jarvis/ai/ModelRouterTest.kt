package com.harsh.jarvis.ai

import org.junit.Assert.*
import org.junit.Test

class ModelRouterTest {
    @Test fun intent_selects_smallest_supported_model() {
        val router = ModelRouter()
        assertEquals(ModelRole.INTENT, router.select(ModelTask.INTENT)?.role)
    }

    @Test fun unsupported_generative_tasks_do_not_claim_a_model() {
        val router = ModelRouter()
        assertTrue(router.requiresGenerativeModel(ModelTask.REASONING))
        assertNull(router.select(ModelTask.REASONING))
    }

    @Test fun catalog_matches_actual_shipped_model() {
        assertEquals("intent_model.json", ModelCatalog.intent.path)
        assertEquals("JSON Naive-Bayes statistics", ModelCatalog.intent.format)
    }
}
