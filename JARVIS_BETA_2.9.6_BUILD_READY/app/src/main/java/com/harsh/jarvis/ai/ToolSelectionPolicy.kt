package com.harsh.jarvis.ai

/** Deterministic tool grounding: if a capability has a tool, do not invent its result. */
object ToolSelectionPolicy {
    enum class Need { NONE, LOCATION, CALENDAR, CONTACT, TASK, EVOLUTION, PROACTIVE, MEMORY, APP }

    fun requiredCapability(intent: IntentType): Need = when (intent) {
        IntentType.CALENDAR_QUERY -> Need.CALENDAR
        IntentType.CONTACT_LOOKUP -> Need.CONTACT
        IntentType.SHOW_TASKS, IntentType.CREATE_REMINDER, IntentType.DELETE_TASK -> Need.TASK
        IntentType.SHOW_EVOLUTION, IntentType.SHOW_EVOLUTION_XP, IntentType.SHOW_EVOLUTION_QUESTS,
        IntentType.COMPLETE_EVOLUTION_QUEST, IntentType.SHOW_EVOLUTION_WEAKNESS,
        IntentType.NEXT_EVOLUTION_CHALLENGE, IntentType.START_EVOLUTION_CHALLENGE -> Need.EVOLUTION
        IntentType.TODAY_BRIEFING, IntentType.NEXT_BEST_ACTION, IntentType.PLAN_DAY,
        IntentType.SHOW_DEADLINES, IntentType.SHOW_MISSING, IntentType.SHOW_YESTERDAY_INCOMPLETE,
        IntentType.SET_AVAILABILITY, IntentType.REMIND_WHEN_FREE, IntentType.SNOOZE_PROACTIVE,
        IntentType.COMPLETE_PROACTIVE, IntentType.BREAK_DOWN_TASK, IntentType.EVENING_REVIEW -> Need.PROACTIVE
        IntentType.SAVE_MEMORY, IntentType.SEARCH_MEMORY -> Need.MEMORY
        IntentType.OPEN_APP -> Need.APP
        else -> Need.NONE
    }
}
