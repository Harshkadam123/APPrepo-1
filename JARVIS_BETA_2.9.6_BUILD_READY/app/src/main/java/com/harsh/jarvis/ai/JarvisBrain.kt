package com.harsh.jarvis.ai

import com.harsh.jarvis.actions.*
import com.harsh.jarvis.evolution.EvolutionRepository
import com.harsh.jarvis.history.ActionHistoryRepository
import com.harsh.jarvis.memory.MemoryRepository
import com.harsh.jarvis.security.ActionLevel
import com.harsh.jarvis.tools.ToolRegistry
import com.harsh.jarvis.privacy.PrivacyCapability
import com.harsh.jarvis.privacy.PrivacyGateway
import org.json.JSONObject
import kotlinx.coroutines.flow.first
import com.harsh.jarvis.proactive.ProactiveEngine

/**
 * Deterministic agent coordinator. The future large model can replace/augment
 * intent understanding and planning without bypassing this execution gateway.
 */
class JarvisBrain(
    private val memory: MemoryRepository,
    private val tools: ToolRegistry,
    private val executor: ActionExecutor,
    private val history: ActionHistoryRepository,
    private val privacy: PrivacyGateway,
    private val model: PersonalIntentModel = PersonalIntentModel(),
    private val conversation: ConversationContext = ConversationContext(),
    private val entities: CommandEntityExtractor = CommandEntityExtractor(),
    private val personalData: com.harsh.jarvis.tools.PersonalDataTools? = null,
    private val profile: UserProfile? = null,
    private val routines: RoutineStore? = null,
    private val planner: LocalPlanner = LocalPlanner(),
    private val evolution: EvolutionRepository? = null,
    private val proactive: ProactiveEngine? = null,
    private val qwen: LocalQwenBrain? = null
) {
    private var pendingHistoryId: Long? = null
    private var lastRetryableAction: JarvisAction? = null
    private var remainingPlanSteps: MutableList<String> = mutableListOf()

    suspend fun process(command: String): BrainResponse {
        val raw = command.trim()
        if (raw.isBlank()) return BrainResponse("Please say or type a command.", IntentType.UNKNOWN, 0.0)

        if (executor.hasPending()) return handleConfirmation(raw)

        // Resolve a short-lived follow-up such as: "Remind me to study" -> "tomorrow at 7 PM".
        val input = conversation.resolve(raw) ?: raw

        // Safe compound plans: "A, then B" / "A and then B".
        val routineMatch = Regex("^\\s*(?:run|start)\\s+(?:my\\s+)?routine\\s+(.+)$", RegexOption.IGNORE_CASE).find(input)
        if (routineMatch != null && routines != null) {
            val commands = routines.get(routineMatch.groupValues[1])
            if (commands.isEmpty()) return BrainResponse("I couldn't find that routine.", IntentType.CREATE_ROUTINE, .95)
            remainingPlanSteps = commands.toMutableList()
            return continuePlan(BrainResponse("Starting routine '${routineMatch.groupValues[1]}'.", IntentType.CREATE_ROUTINE, .98))
        }
        val steps = planner.plan(input)
        if (steps.size > 1) {
            remainingPlanSteps = steps.drop(1).toMutableList()
            val first = processSingle(steps.first())
            if (first.confirmationPending) return first
            if (first.text.startsWith("Failed") || first.text.startsWith("Partially")) {
                remainingPlanSteps.clear()
                return first.copy(text = "Plan stopped. ${first.text}")
            }
            return continuePlan(first)
        }
        return processSingle(input)
    }

    private suspend fun handleConfirmation(input: String): BrainResponse {
        val normalized = normalize(input)
        if (isAffirmative(normalized)) {
            val result = executor.confirm()
            pendingHistoryId?.let { history.updateResult(it, result) }
            pendingHistoryId = null
            rememberRetryable(result, executor.lastExecutedAction())
            executor.lastExecutedAction()?.let { recordPrivacyOutcome(it, result) }
            if (result.status == ActionStatus.SUCCESS && remainingPlanSteps.isNotEmpty()) return continuePlan(
                BrainResponse(result.userFeedback(), IntentType.CONFIRM_ACTION, 1.0)
            )
            return BrainResponse(result.userFeedback(), IntentType.CONFIRM_ACTION, 1.0)
        }
        if (isNegative(normalized)) {
            executor.cancel()
            pendingHistoryId?.let { history.updateResult(it, ActionResult(
                ActionStatus.CANCELLED, "The pending action must not execute", "The pending action was cancelled by the user."
            )) }
            pendingHistoryId = null
            remainingPlanSteps.clear()
            return BrainResponse("Cancelled. I didn't execute the action.", IntentType.CANCEL_ACTION, 1.0)
        }
        return BrainResponse("I still need a confirmation. Say yes to continue or no to cancel.", IntentType.CONFIRM_ACTION, 1.0, true)
    }

    private suspend fun continuePlan(previous: BrainResponse): BrainResponse {
        while (remainingPlanSteps.isNotEmpty()) {
            val step = remainingPlanSteps.removeAt(0)
            val result = processSingle(step)
            if (result.confirmationPending) return BrainResponse(
                "Step needs confirmation: ${result.text}",
                result.intent, result.confidence, true
            )
            if (result.text.startsWith("Failed") || result.text.startsWith("Partially")) {
                remainingPlanSteps.clear()
                return BrainResponse("Plan stopped after '${step}': ${result.text}", result.intent, result.confidence)
            }
        }
        return BrainResponse("Plan completed. ${previous.text}", previous.intent, previous.confidence)
    }

    private fun splitPlan(input: String): List<String> =
        input.split(Regex("""\s+(?:and then|then)\s+|\s*;\s*"""), limit = 10)
            .map { it.trim() }.filter { it.isNotBlank() }

    private suspend fun processSingle(input: String): BrainResponse {
        val result = model.classify(input)

        // Ask for missing high-value information instead of silently guessing.
        if (result.intent == IntentType.CREATE_REMINDER && !entities.extract(input).hasSchedule) {
            conversation.set(ConversationContext.PendingFollowUp(
                ConversationContext.PendingFollowUp.Type.REMINDER_SCHEDULE,
                input,
                "When should I remind you? You can say 'tomorrow at 7 PM', 'in 30 minutes', or a weekday and time."
            ))
            return BrainResponse(
                "${entities.extract(input).reminderTitle?.let { "I can remind you to $it. " } ?: "I can create that reminder. "}${conversation.peek()?.prompt}",
                result.intent, result.confidence, false
            )
        }

        val response = when (result.intent) {
            IntentType.CREATE_REMINDER -> executeAndReport(input, tools.createReminderAction(input))
            IntentType.OPEN_APP -> {
                val (action, error) = tools.openAppAction(input)
                if (action != null) executeAndReport(input, action) else error ?: "I couldn't open that app."
            }
            IntentType.SAVE_MEMORY -> {
                if (privacy.isBlocked(PrivacyCapability.JARVIS_MEMORY))
                    return BrainResponse("Saving JARVIS memory is blocked by your Privacy policy.", result.intent, result.confidence)
                val memoryText = input.substringAfter("remember", input, ignoreCase = true)
                    .removePrefix("that").trim().ifBlank { input }
                val action = JarvisAction(
                    name = "save_memory", description = "save the memory '$memoryText'", level = if (privacy.requiresUserApproval(PrivacyCapability.JARVIS_MEMORY)) ActionLevel.CONFIRM else ActionLevel.SAFE,
                    payload = mapOf("text" to memoryText),
                    execute = {
                        val id = memory.save(memoryText)
                        if (id != null && memory.findById(id) != null)
                            ActionResult(ActionStatus.SUCCESS, "Memory '$memoryText' exists in persistent storage", "Saved that memory.", verified=true, evidence="Memory was read back after insertion.")
                        else ActionResult(ActionStatus.FAILED, "Memory '$memoryText' exists in persistent storage", "The memory could not be verified after saving.", problem="The memory write was not confirmed in the database.", fix="Try saving the memory again.")
                    }
                )
                executeAndReport(input, action)
            }
            IntentType.SEARCH_MEMORY -> if (privacy.isBlocked(PrivacyCapability.JARVIS_MEMORY)) "Reading JARVIS memory is blocked by your Privacy policy." else searchMemory(input)
            IntentType.SHOW_EVOLUTION, IntentType.SHOW_EVOLUTION_XP -> evolution?.todaySummary() ?: "Evolution system is not initialized."
            IntentType.SHOW_EVOLUTION_QUESTS -> {
                val q = evolution?.quests()?.first()?.filter { it.status == "PENDING" }?.take(3).orEmpty()
                if (q.isEmpty()) "You have no pending evolution quests." else q.joinToString("\n") { "□ ${it.title} — +${it.xpReward} XP" }
            }
            IntentType.COMPLETE_EVOLUTION_QUEST -> {
                val result = evolution?.completeFirstPending() ?: Result.success("Evolution system is not initialized.")
                result.getOrElse { "I could not complete that quest: ${it.message}" }
            }
            IntentType.SHOW_EVOLUTION_WEAKNESS -> evolution?.weaknessSummary() ?: "Evolution system is not initialized."
            IntentType.NEXT_EVOLUTION_CHALLENGE -> {
                val q = evolution?.recommendNextChallenge()
                if (q == null) "No skills are configured yet." else "Next challenge: ${q.title}. Difficulty ${q.difficulty}/10. Reward +${q.xpReward} XP."
            }
            IntentType.TODAY_BRIEFING -> proactive?.dailyBriefing() ?: "Proactive intelligence is not initialized."
            IntentType.NEXT_BEST_ACTION -> proactive?.nextBestAction()?.let { "I recommend: ${it.title}. ${it.detail}" } ?: "You have no queued priority. Choose your highest-value available work."
            IntentType.PLAN_DAY -> proactive?.planDay()?.joinToString("\n") ?: "I cannot plan the day until proactive intelligence is initialized."
            IntentType.SHOW_DEADLINES -> proactive?.refresh()?.filter { it.deadline != null }.sortedBy { it.deadline }.take(5)?.joinToString("\n") { "${it.title} — ${it.priority}" } ?: "No deadlines are currently tracked."
            IntentType.SHOW_MISSING -> proactive?.refresh()?.filter { it.status == "PENDING" }.take(5)?.joinToString("\n") { "□ ${it.title}" } ?: "Nothing is currently missing."
            IntentType.SHOW_YESTERDAY_INCOMPLETE -> proactive?.yesterdayIncomplete() ?: "Proactive intelligence is not initialized."
            IntentType.SET_AVAILABILITY -> {
                if (proactive == null) "Proactive intelligence is not initialized." else {
                    val lower = input.lowercase()
                    val now = java.time.LocalDateTime.now()
                    val hours = Regex("(?:for|next)\\s+(\\d+)\\s+hours?").find(lower)?.groupValues?.getOrNull(1)?.toIntOrNull()
                    if (hours != null) {
                        val start = now.hour * 60 + now.minute
                        val end = (start + hours * 60).coerceAtMost(1440)
                        proactive.setAvailability(com.harsh.jarvis.proactive.AvailabilityState.BUSY, start, end, label = "BUSY / protected")
                        "Understood. I will not interrupt you for $hours hour(s), except configured critical alerts."
                    } else {
                        proactive.setAvailability(com.harsh.jarvis.proactive.AvailabilityState.BUSY, 0, 1440, label = "BUSY / protected")
                        "I will protect this period from proactive interruptions until you change the availability rule."
                    }
                }
            }
            IntentType.REMIND_WHEN_FREE -> proactive?.remindWhenFree() ?: "Proactive intelligence is not initialized."
            IntentType.SNOOZE_PROACTIVE -> proactive?.nextBestAction()?.let { proactive.snooze(it.id, System.currentTimeMillis() + 60 * 60_000L); "Snoozed ${it.title} for one hour." } ?: "There is nothing to snooze."
            IntentType.COMPLETE_PROACTIVE -> proactive?.nextBestAction()?.let { proactive.complete(it.id); "Marked ${it.title} complete." } ?: "There is nothing to complete."
            IntentType.BREAK_DOWN_TASK -> {
                val title = input.replace(Regex("\\b(break|breakdown|break down|into|steps|task)\\b", RegexOption.IGNORE_CASE), " ").trim().ifBlank { "Task" }
                val steps = proactive?.createSubtasks(title) ?: emptyList(); if (steps.isEmpty()) "I could not create subtasks." else "I broke it into ${steps.size} local subtasks."
            }
            IntentType.EVENING_REVIEW -> proactive?.eveningReview() ?: "Proactive intelligence is not initialized."
            IntentType.START_EVOLUTION_CHALLENGE -> {
                val q = evolution?.startRecommendedChallenge()
                if (q == null) "No challenge can be prepared until you configure a skill." else "Challenge ready: ${q.title}. Difficulty ${q.difficulty}/10. Reward +${q.xpReward} XP."
            }
            IntentType.SHOW_TASKS -> tools.showTasks()
            IntentType.DELETE_TASK -> {
                val (action, error) = tools.deleteTaskAction(input)
                if (action == null) error ?: "I couldn't find an active task matching that." else executeAndReport(input, action)
            }
            IntentType.SHOW_HISTORY -> showHistory()
            IntentType.RETRY_ACTION -> retryLastAction()
            IntentType.CALENDAR_QUERY -> {
                val query = input.replace(Regex("\\b(calendar|schedule|show|my|what|do|have|events?|appointments?)\\b", RegexOption.IGNORE_CASE), "").trim()
                val action = personalData?.calendarAction(query)
                if (action == null) "Calendar tools are not initialized." else executeAndReport(input, action)
            }
            IntentType.CONTACT_LOOKUP -> {
                val name = input.replace(Regex("\\b(find|lookup|look up|contact|phone|number|of|call|dial|the)\\b", RegexOption.IGNORE_CASE), "").trim()
                val action = if (Regex("\\b(call|dial)\\b", RegexOption.IGNORE_CASE).containsMatchIn(input)) {
                    personalData?.callAction(name)
                } else {
                    personalData?.contactAction(name)
                }
                if (action == null) "Contact tools are not initialized." else executeAndReport(input, action)
            }
            IntentType.CREATE_ROUTINE -> handleRoutine(input)
            IntentType.SHOW_PROFILE -> profile?.summary() ?: "Profile is not initialized."
            IntentType.SET_PROFILE -> handleProfile(input)
            IntentType.CONVERSATION -> conversationReply(input)
            IntentType.HELP -> "I can create and verify reminders, converse with short-lived context, search local semantic-style memory, open allowed apps, inspect calendar/contact data only through the Privacy Gateway, save local routines, manage tasks, verify actions, keep history, retry safe failures, and execute bounded multi-step plans. Messages, notifications, files, photos, location, clipboard and credentials remain blocked by default."
            else -> conversationReply(input)
        }
        return BrainResponse(response, result.intent, result.confidence, executor.hasPending())
    }


    private suspend fun conversationReply(input: String): String {
        if (qwen == null) return "I can handle that once the local Qwen3 brain is connected. For actions, try a supported command or ask for help."
        val relevant = if (privacy.canUse(PrivacyCapability.JARVIS_MEMORY)) {
            memory.search(input).take(3).joinToString(" | ").ifBlank { null }
        } else null
        // Profile data is deliberately not injected into the LLM context yet;
        // it has no dedicated generative-data consent capability in the current release.
        return qwen.reply(input, relevant, null)
    }

    private suspend fun executeAndReport(request: String, action: JarvisAction): String {
        val lifecycleId = history.recordLifecycle(
            request, action,
            if (action.level == ActionLevel.SAFE) "EXECUTING" else "WAITING_CONFIRMATION"
        )
        val result = executor.request(action)
        if (result.status == ActionStatus.NEEDS_USER) {
            pendingHistoryId = lifecycleId
            return result.userFeedback()
        }
        history.updateResult(lifecycleId, result)
        rememberRetryable(result, action)
        recordPrivacyOutcome(action, result)
        return result.userFeedback()
    }

    private fun rememberRetryable(result: ActionResult, action: JarvisAction? = null) {
        if (result.status == ActionStatus.FAILED || result.status == ActionStatus.PARTIAL) {
            if (action != null && action.level == ActionLevel.SAFE) lastRetryableAction = action
        } else if (result.status == ActionStatus.SUCCESS) lastRetryableAction = null
    }

    private suspend fun retryLastAction(): String {
        val action = lastRetryableAction ?: history.latestRetryable()?.let { record ->
            val obj = runCatching { JSONObject(record.payloadJson) }.getOrNull() ?: return@let null
            val map = obj.keys().asSequence().associateWith { obj.optString(it) }
            tools.rebuildAction(record.actionName, map)
        } ?: return "I don't have a safe failed action to retry yet."

        val request = "Retry: ${action.description}"
        val historyId = history.recordLifecycle(request, action,
            if (action.level == ActionLevel.SAFE) "EXECUTING" else "WAITING_CONFIRMATION")
        val result = executor.request(action)
        if (result.status == ActionStatus.NEEDS_USER) {
            pendingHistoryId = historyId
        } else {
            history.updateResult(historyId, result)
            rememberRetryable(result, action)
        }
        return result.userFeedback()
    }

    private suspend fun showHistory(): String {
        if (privacy.isBlocked(PrivacyCapability.ACTION_HISTORY))
            return "Reading action history is blocked by your Privacy policy. You can still view it locally in the History tab."
        val records = history.latest(10)
        if (records.isEmpty()) return "I don't have any action history yet."
        return records.joinToString(prefix = "Recent actions: ", separator = " | ") {
            "${it.status}: ${it.request} → ${it.actual}"
        }
    }

    private suspend fun recordPrivacyOutcome(action: JarvisAction, result: ActionResult) {
        val capability = when (action.name) {
            "open_app" -> PrivacyCapability.APP_CONTROL
            "save_memory" -> PrivacyCapability.JARVIS_MEMORY
            "contact_lookup" -> PrivacyCapability.CONTACT_LOOKUP
            "calendar_query" -> PrivacyCapability.CALENDAR_DATA
            "call_contact", "message_contact" -> PrivacyCapability.COMMUNICATIONS
            else -> null
        } ?: return
        privacy.record(
            capability = capability,
            purpose = action.description,
            dataExposed = when (capability) {
                PrivacyCapability.APP_CONTROL -> "App package/label only"
                PrivacyCapability.JARVIS_MEMORY -> "Explicitly provided JARVIS memory text"
                PrivacyCapability.CONTACT_LOOKUP -> "One matching contact record"
                PrivacyCapability.CALENDAR_DATA -> "Limited upcoming calendar event data"
                PrivacyCapability.COMMUNICATIONS -> "One resolved phone number and/or user-authored message content"
                else -> "None"
            },
            outcome = result.status.name
        )
    }

    fun hasPendingAction() = executor.hasPendingAction()
    fun pendingDescription() = executor.pendingDescription()

    private fun normalize(input: String): String = input.lowercase()
        .replace(Regex("""[^a-z0-9\s']"""), " ").replace(Regex("""\s+"""), " ").trim()

    private fun isAffirmative(input: String): Boolean =
        Regex("""\b(yes|yeah|yep|yup|sure|confirm|confirmed|do it|go ahead|okay|ok|proceed|send it|that's correct|that is correct|please do)\b""").containsMatchIn(input)

    private fun isNegative(input: String): Boolean =
        Regex("""\b(no|nope|nah|cancel|cancel it|stop|don't|dont|do not|never mind)\b""").containsMatchIn(input)

    private suspend fun searchMemory(command: String): String {
        val query = command.replace(
            Regex("""\b(what do you remember about|what did you remember about|what did i tell you about|recall|search my memory for|show my memories|what do you remember|what did i tell you)\b""", RegexOption.IGNORE_CASE), ""
        ).replace("?", "").trim()
        val memories = memory.search(query)
        if (memories.isEmpty()) return if (query.isBlank()) "I don't have any saved memories yet." else "I couldn't find a saved memory matching \"$query\"."
        return memories.joinToString(prefix = "I remember: ", separator = " | ") { it.text }
    }
}

data class BrainResponse(
    val text: String,
    val intent: IntentType,
    val confidence: Double,
    val confirmationPending: Boolean = false
)
