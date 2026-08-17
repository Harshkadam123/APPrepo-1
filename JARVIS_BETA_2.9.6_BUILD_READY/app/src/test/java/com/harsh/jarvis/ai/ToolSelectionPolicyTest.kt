package com.harsh.jarvis.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class ToolSelectionPolicyTest {
    @Test fun location_like_capability_is_not_fabricated_by_unknown_intent() {
        assertEquals(ToolSelectionPolicy.Need.NONE, ToolSelectionPolicy.requiredCapability(IntentType.CONVERSATION))
    }

    @Test fun existing_tools_are_mapped_deterministically() {
        assertEquals(ToolSelectionPolicy.Need.CALENDAR, ToolSelectionPolicy.requiredCapability(IntentType.CALENDAR_QUERY))
        assertEquals(ToolSelectionPolicy.Need.EVOLUTION, ToolSelectionPolicy.requiredCapability(IntentType.SHOW_EVOLUTION_XP))
        assertEquals(ToolSelectionPolicy.Need.MEMORY, ToolSelectionPolicy.requiredCapability(IntentType.SEARCH_MEMORY))
    }
}
