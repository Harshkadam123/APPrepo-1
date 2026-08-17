package com.harsh.jarvis.proactive

import com.harsh.jarvis.evolution.EvolutionRepository
import com.harsh.jarvis.tasks.TaskDao
import com.harsh.jarvis.tools.PersonalDataTools
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.flow.first

class ProactiveEngine(
    private val dao: ProactiveDao,
    private val tasks: TaskDao,
    private val evolution: EvolutionRepository,
    private val personalData: PersonalDataTools? = null
) {
    private val availability = AvailabilityManager(dao)

    suspend fun refresh(now: Long = System.currentTimeMillis()): List<ProactiveEvent> {
        evolution.ensureProfile()
        val taskList = tasks.observeAll().first()
        syncAuthorizedCalendar()
        taskList.filter { !it.completed }.forEach { task ->
            val deadline = task.dueTime
            val deadlineFactor = PriorityEngine.deadlineFactor(deadline, now)
            val importance = when (task.priority.uppercase()) { "HIGH" -> .9; "URGENT" -> 1.0; "LOW" -> .25; else -> .55 }
            val score = PriorityEngine.score(
                importance = importance,
                urgency = if (deadline != null) .8 else .35,
                deadlineProximity = deadlineFactor,
                relevance = task.goalPriority,
                consequence = task.consequence
            ) * (0.65 + 0.35 * (100 - task.completionPercent.coerceIn(0, 100)) / 100.0)
            upsert(
                type = if (task.title.contains("assignment", true)) "ASSIGNMENT" else "TASK",
                title = task.title,
                detail = task.description,
                source = "TASK",
                sourceId = task.id,
                deadline = deadline,
                requiredAction = "Complete task",
                score = score,
                now = now
            )
        }
        val quests = evolution.quests().first().filter { it.status == "PENDING" }
        quests.forEach { quest ->
            val deadlineFactor = PriorityEngine.deadlineFactor(quest.deadline, now)
            val score = PriorityEngine.score(.55, .45, deadlineFactor, .75, .35)
            upsert("EVOLUTION", quest.title, quest.description, "EVOLUTION", quest.id, quest.deadline, "Complete quest", score, now)
        }
        expireOld(now)
        return dao.activeEvents()
    }

    suspend fun snapshot(now: Long = System.currentTimeMillis()): ProactiveSnapshot {
        val events = refresh(now)
        val (state, label) = availability.current()
        val today = LocalDate.now(ZoneId.systemDefault())
        val schedule = dao.schedule(System.currentTimeMillis())
        val dashboard = evolution.dashboard()
        val xpToday = evolution.history().first().filter { java.time.Instant.ofEpochMilli(it.createdAt).atZone(ZoneId.systemDefault()).toLocalDate() == today }.sumOf { it.xpAwarded }
        val availableEvents = events.filter { !it.dismissedForever && (it.snoozeUntil ?: 0L) <= now }
        val important = availableEvents.sortedByDescending { priorityRank(it.priority) }.take(3)
        val recommendation = nextBestAction(now)
        return ProactiveSnapshot(state, label, important, important.firstOrNull(), schedule, dashboard.profile.level, dashboard.profile.totalXp, xpToday, recommendation, events.size)
    }

    suspend fun nextBestAction(now: Long = System.currentTimeMillis()): ProactiveEvent? {
        val events = refresh(now).filter { it.status != ProactiveStatus.DISMISSED.name && !it.dismissedForever && (it.snoozeUntil ?: 0L) <= now }
        val (state, _) = availability.current()
        val free = state == AvailabilityState.FREE || state == AvailabilityState.STUDY
        val minutes = availableMinutes(now)
        val tasksNow = tasks.observeAll().first().filter { !it.completed }
        val weakest = evolution.dashboard().skills.minByOrNull { it.level to it.xp }
        return events.sortedWith(
            compareByDescending<ProactiveEvent> { event ->
                val matchingTask = tasksNow.firstOrNull { it.id == event.sourceId && event.source == "TASK" }
                val fit = matchingTask?.let { if (it.estimatedMinutes <= minutes) 1.0 else 0.55 } ?: 0.8
                priorityRank(event.priority) * 10.0 + fit + if (weakest != null && event.title.contains(weakest.name, true)) .5 else 0.0
            }.thenBy { it.deadline ?: Long.MAX_VALUE }
        ).firstOrNull { free || it.priority == ProactivePriority.CRITICAL.name }
    }

    suspend fun markCommunicated(id: Long) = dao.markCommunicated(id)

    suspend fun communicationDecision(event: ProactiveEvent): Boolean {
        val now = System.currentTimeMillis()
        val (state, _) = availability.current()
        if (event.dismissedForever || event.status == ProactiveStatus.DISMISSED.name || (event.snoozeUntil ?: 0L) > now) return false
        val inProtectedCalendar = dao.schedule(now).any { now in it.startTime until it.endTime && it.protected }
        val criticalAllowed = availability.criticalOverrideAllowed()
        if ((state != AvailabilityState.FREE || inProtectedCalendar) && !(event.priority == ProactivePriority.CRITICAL.name && criticalAllowed)) {
            if (event.status != ProactiveStatus.WAITING.name) dao.updateEvent(event.copy(status = ProactiveStatus.WAITING.name, communicationState = "WAITING_FOR_AVAILABILITY"))
            return false
        }
        if (event.communicationCount > 0 && event.priority != ProactivePriority.CRITICAL.name) return false
        return event.priority != ProactivePriority.LOW.name
    }

    suspend fun setAvailability(state: AvailabilityState, startMinute: Int, endMinute: Int, daysMask: Int = 127, label: String = state.name, criticalOverride: Boolean = false) =
        dao.insertAvailability(AvailabilityRule(state = state.name, startMinute = startMinute, endMinute = endMinute, daysMask = daysMask, label = label, criticalOverride = criticalOverride))

    suspend fun clearAvailability() = dao.availability().forEach { dao.deleteAvailability(it) }

    suspend fun snooze(id: Long, until: Long) { dao.event(id)?.let { dao.updateEvent(it.copy(status = ProactiveStatus.SNOOZED.name, snoozeUntil = until, communicationState = "SNOOZED")) ; learn(it.type, "snooze") } }
    suspend fun dismiss(id: Long, forever: Boolean = false) { dao.event(id)?.let { dao.updateEvent(it.copy(status = ProactiveStatus.DISMISSED.name, dismissedForever = forever, communicationState = "DISMISSED")); learn(it.type, "dismiss") } }
    suspend fun complete(id: Long) { dao.event(id)?.let { dao.updateEvent(it.copy(status = ProactiveStatus.COMPLETED.name, communicationState = "COMPLETED")); learn(it.type, "follow") } }
    suspend fun reschedule(id: Long, deadline: Long?) { dao.event(id)?.let { dao.updateEvent(it.copy(status = ProactiveStatus.PENDING.name, deadline = deadline, snoozeUntil = null, communicationState = "UNCOMMUNICATED")) } }
    suspend fun changePriority(id: Long, priority: ProactivePriority) { dao.event(id)?.let { dao.updateEvent(it.copy(priority = priority.name, manualPriority = priority.name)) } }

    suspend fun createSubtasks(title: String, skillName: String? = null): List<String> {
        val steps = if (title.contains("ml", true) || title.contains("machine learning", true))
            listOf("Clean dataset", "Explore data", "Train baseline", "Evaluate model", "Improve model", "Save model", "Create API", "Test deployment", "Document project")
        else listOf("Define the outcome", "Gather required material", "Do the main work", "Review the result", "Finish and document")
        val skill = skillName?.let { evolution.findSkill(it) }
        steps.forEachIndexed { index, step ->
            tasks.insert(com.harsh.jarvis.tasks.Task(title = "$title — ${index + 1}. $step", description = "Subtask of: $title${skill?.let { "; Evolution skill: ${it.name}" } ?: ""}", priority = "NORMAL"))
            if (skill != null) {
                evolution.addQuest(com.harsh.jarvis.evolution.EvolutionQuest(title = "$title — ${index + 1}. $step", description = "Optional skill-linked subtask for ${skill.name}.", skillId = skill.id, category = skill.category, type = "TASK", difficulty = 2, xpReward = 15))
            }
        }
        return steps
    }

    suspend fun yesterdayIncomplete(): String {
        val start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val tasks = tasks.incompleteBefore(start)
        return if (tasks.isEmpty()) "You have no overdue unfinished tasks from before today." else tasks.take(8).joinToString("\n") { "Still open: ${it.title}" }
    }

    suspend fun remindWhenFree(): String {
        val next = refresh().filter { it.status != ProactiveStatus.DISMISSED.name && !it.dismissedForever }.sortedByDescending { it.priority }.firstOrNull() ?: return "There is nothing important queued to remind you about."
        val (state, _) = availability.current()
        return if (state == AvailabilityState.FREE || state == AvailabilityState.STUDY) "You are free now. I recommend: ${next.title}." else {
            dao.updateEvent(next.copy(status = ProactiveStatus.WAITING.name, communicationState = "WAITING_FOR_AVAILABILITY"))
            "Understood. I will wait until you become available before bringing up ${next.title}."
        }
    }

    suspend fun planDay(now: LocalDateTime = LocalDateTime.now()): List<String> {
        val taskList = tasks.observeAll().first()
        val blocks = dao.schedule(System.currentTimeMillis())
        val rules = dao.availability()
        val plan = DailyPlanner.plan(now, taskList, blocks, rules)
        if (plan.isEmpty()) return listOf("No schedulable work remains in the current free time. Protect your recovery or use the next available block.")
        return plan.map { item ->
            val start = item.start.toLocalTime().toString().substring(0, 5)
            val end = item.end.toLocalTime().toString().substring(0, 5)
            "$start–$end  ${item.title} (${item.reason})"
        }
    }

    suspend fun availableMinutes(now: Long = System.currentTimeMillis()): Int {
        val start = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(now), ZoneId.systemDefault())
        val end = start.toLocalDate().plusDays(1).atStartOfDay()
        val busy = dao.schedule(now).filter { it.endTime > now }.sumOf {
            val from = maxOf(now, it.startTime); val to = minOf(end.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), it.endTime)
            ((to - from).coerceAtLeast(0L) / 60_000L).toInt()
        }
        return ((end.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() - now) / 60_000L).toInt().minus(busy).coerceAtLeast(0)
    }

    suspend fun dailyBriefing(): String {
        val s = snapshot()
        val tasksToday = tasks.observeAll().first().filter { !it.completed && (it.dueTime == null || java.time.Instant.ofEpochMilli(it.dueTime).atZone(ZoneId.systemDefault()).toLocalDate() == LocalDate.now()) }
        val classes = s.schedule.take(3).joinToString(", ") { block -> "${java.time.Instant.ofEpochMilli(block.startTime).atZone(ZoneId.systemDefault()).toLocalTime().toString().substring(0,5)} ${block.title}" }
        if (tasksToday.isEmpty() && s.important.isEmpty()) return "Good morning. Your day has no high-priority unfinished work detected."
        val recommended = s.recommendation?.title ?: tasksToday.firstOrNull()?.title ?: "protect your highest-value goal"
        return "Good morning. You have ${tasksToday.size} task(s) due today and ${s.important.size} important proactive item(s). Recommended priority: $recommended.${if (classes.isNotBlank()) " Schedule: $classes." else ""}"
    }

    suspend fun eveningReview(): String {
        val s = snapshot()
        val allTasks = tasks.observeAll().first()
        val completed = allTasks.count { it.completed }
        val incomplete = allTasks.count { !it.completed }
        val tomorrow = LocalDate.now().plusDays(1)
        val tomorrowDue = allTasks.count { !it.completed && it.dueTime != null && java.time.Instant.ofEpochMilli(it.dueTime).atZone(ZoneId.systemDefault()).toLocalDate() == tomorrow }
        val weakest = evolution.dashboard().skills.minByOrNull { it.level to it.xp }?.name
        return "Evening review: $completed task(s) completed, $incomplete incomplete, ${s.xpToday} XP gained today, $tomorrowDue deadline(s) tomorrow. ${if (weakest != null) "Skill needing attention: $weakest. " else ""}Next priority: ${s.recommendation?.title ?: "none"}."
    }

    suspend fun queueMessage(event: ProactiveEvent) {
        if (dao.byDedupeKey(event.dedupeKey) == null) dao.insertEvent(event)
    }

    private suspend fun upsert(type: String, title: String, detail: String, source: String, sourceId: Long?, deadline: Long?, requiredAction: String, score: Double, now: Long) {
        val key = "$source:${sourceId ?: title.lowercase().trim()}"
        val existing = dao.byDedupeKey(key)
        val feedback = dao.feedback(type)
        val penalty = ((feedback?.dismissals ?: 0) * .025 + (feedback?.snoozes ?: 0) * .01 - (feedback?.follows ?: 0) * .008).coerceIn(-.10, .25)
        val priority = PriorityEngine.classify((score - penalty).coerceIn(0.0, 1.0), existing?.manualPriority?.let { runCatching { ProactivePriority.valueOf(it) }.getOrNull() })
        val event = (existing ?: ProactiveEvent(type = type, title = title, source = source, sourceId = sourceId, dedupeKey = key)).copy(
            title = title, detail = detail, priority = priority.name, baseImportance = score, urgency = if (deadline != null) .8 else .35,
            deadlineProximity = PriorityEngine.deadlineFactor(deadline, now), relevance = .8, deadline = deadline, requiredAction = requiredAction, lastEvaluatedAt = now,
            status = if (existing?.status == ProactiveStatus.SNOOZED.name && (existing.snoozeUntil ?: 0L) > now) existing.status else ProactiveStatus.PENDING.name
        )
        if (existing == null) dao.insertEvent(event) else if (existing.status !in listOf(ProactiveStatus.COMPLETED.name, ProactiveStatus.DISMISSED.name)) dao.updateEvent(event)
    }

    private suspend fun learn(type: String, action: String) {
        val old = dao.feedback(type) ?: ProactiveFeedback(type)
        val next = when (action) { "snooze" -> old.copy(snoozes = old.snoozes + 1); "dismiss" -> old.copy(dismissals = old.dismissals + 1); else -> old.copy(follows = old.follows + 1) }
        dao.saveFeedback(next.copy(updatedAt = System.currentTimeMillis()))
    }

    private suspend fun syncAuthorizedCalendar() {
        val signals = personalData?.calendarSignals().orEmpty()
        if (signals.isEmpty()) return
        dao.clearSchedule()
        signals.forEach { dao.insertSchedule(ProactiveScheduleBlock(title = it.title, startTime = it.begin, endTime = it.end, state = AvailabilityState.BUSY.name, protected = true)) }
    }

    private suspend fun expireOld(now: Long) {
        dao.activeEvents().filter { it.deadline != null && it.deadline < now - 86_400_000L }.forEach { dao.setStatus(it.id, ProactiveStatus.EXPIRED.name) }
    }

    private fun priorityRank(p: String): Int = when (p) { "CRITICAL" -> 4; "HIGH" -> 3; "MEDIUM" -> 2; else -> 1 }
}
