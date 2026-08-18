package com.harsh.jarvis.proactive

import com.harsh.jarvis.evolution.EvolutionRepository
import com.harsh.jarvis.evolution.EvolutionSkill
import com.harsh.jarvis.evolution.EvolutionQuest
import com.harsh.jarvis.tasks.Task
import com.harsh.jarvis.tasks.TaskDao
import com.harsh.jarvis.tools.PersonalDataTools
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.flow.first

/**
 * Deterministic local-only proactive intelligence engine.
 *
 * Responsibilities:
 * - Evaluate unfinished tasks.
 * - Evaluate pending evolution quests.
 * - Synchronize authorized calendar signals.
 * - Select the next useful action.
 * - Manage availability, snoozing and dismissal.
 * - Generate daily/evening summaries.
 *
 * This class never performs external actions by itself.
 */
class ProactiveEngine(
    private val dao: ProactiveDao,
    private val tasks: TaskDao,
    private val evolution: EvolutionRepository,
    private val personalData: PersonalDataTools? = null
) {

    private val availability = AvailabilityManager(dao)

    companion object {
        private const val DAY_MILLIS = 86_400_000L
        private const val MINUTE_MILLIS = 60_000L
        private const val MAX_IMPORTANT_EVENTS = 3
    }

    /**
     * Re-evaluates the current proactive state.
     *
     * Failures in optional calendar synchronization do not prevent
     * task/evolution processing.
     */
    suspend fun refresh(
        now: Long = System.currentTimeMillis()
    ): List<ProactiveEvent> {

        val safeNow = now.coerceAtLeast(0L)

        runCatching {
            evolution.ensureProfile()
        }

        val taskList = runCatching {
            tasks.observeAll().first()
        }.getOrDefault(emptyList())

        runCatching {
            syncAuthorizedCalendar()
        }

        for (task in taskList) {
            if (task.completed) continue

            val deadline = task.dueTime

            val deadlineFactor = runCatching {
                PriorityEngine.deadlineFactor(deadline, safeNow)
            }.getOrDefault(0.0)

            val importance = when (task.priority.uppercase()) {
                "URGENT" -> 1.0
                "HIGH" -> 0.9
                "LOW" -> 0.25
                else -> 0.55
            }

            val completion =
                (100 - task.completionPercent.coerceIn(0, 100)) / 100.0

            val baseScore = runCatching {
                PriorityEngine.score(
                    importance = importance,
                    urgency = if (deadline != null) 0.8 else 0.35,
                    deadlineProximity = deadlineFactor,
                    relevance = task.goalPriority,
                    consequence = task.consequence
                )
            }.getOrDefault(0.0)

            val score = (
                baseScore *
                    (0.65 + 0.35 * completion)
                ).coerceIn(0.0, 1.0)

            val type =
                if (task.title.contains("assignment", ignoreCase = true)) {
                    "ASSIGNMENT"
                } else {
                    "TASK"
                }

            runCatching {
                upsert(
                    type = type,
                    title = task.title,
                    detail = task.description,
                    source = "TASK",
                    sourceId = task.id,
                    deadline = deadline,
                    requiredAction = "Complete task",
                    score = score,
                    now = safeNow
                )
            }
        }

        val quests = runCatching {
            evolution.quests()
                .first()
                .filter { it.status == "PENDING" }
        }.getOrDefault(emptyList())

        for (quest in quests) {
            val deadlineFactor = runCatching {
                PriorityEngine.deadlineFactor(
                    quest.deadline,
                    safeNow
                )
            }.getOrDefault(0.0)

            val score = runCatching {
                PriorityEngine.score(
                    importance = 0.55,
                    urgency = 0.45,
                    deadlineProximity = deadlineFactor,
                    relevance = 0.75,
                    consequence = 0.35
                )
            }.getOrDefault(0.55).coerceIn(0.0, 1.0)

            runCatching {
                upsert(
                    type = "EVOLUTION",
                    title = quest.title,
                    detail = quest.description,
                    source = "EVOLUTION",
                    sourceId = quest.id,
                    deadline = quest.deadline,
                    requiredAction = "Complete quest",
                    score = score,
                    now = safeNow
                )
            }
        }

        runCatching {
            expireOld(safeNow)
        }

        return runCatching {
            dao.activeEvents()
        }.getOrDefault(emptyList())
    }

    suspend fun snapshot(
        now: Long = System.currentTimeMillis()
    ): ProactiveSnapshot {

        val safeNow = now.coerceAtLeast(0L)

        val events = refresh(safeNow)

        val availabilityState = runCatching {
            availability.current()
        }.getOrDefault(
            AvailabilityState.FREE to "Available"
        )

        val state = availabilityState.first
        val label = availabilityState.second

        val today = currentDate(safeNow)

        val schedule = runCatching {
            dao.schedule(safeNow)
        }.getOrDefault(emptyList())

        val dashboard = runCatching {
            evolution.dashboard()
        }.getOrNull()

        val xpToday = runCatching {
            evolution.history()
                .first()
                .filter {
                    runCatching {
                        Instant.ofEpochMilli(it.createdAt)
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate() == today
                    }.getOrDefault(false)
                }
                .sumOf { it.xpAwarded }
        }.getOrDefault(0L)

        val availableEvents = events.filter { event ->
            !event.dismissedForever &&
                event.status != ProactiveStatus.DISMISSED.name &&
                event.status != ProactiveStatus.COMPLETED.name &&
                (event.snoozeUntil ?: 0L) <= safeNow
        }

        val important = availableEvents
            .sortedWith(
                compareByDescending<ProactiveEvent> {
                    priorityRank(it.priority)
                }.thenBy {
                    it.deadline ?: Long.MAX_VALUE
                }
            )
            .take(MAX_IMPORTANT_EVENTS)

        val recommendation = runCatching {
            nextBestAction(safeNow)
        }.getOrNull()

        val profile =
            dashboard?.profile

        return ProactiveSnapshot(
            state,
            label,
            important,
            important.firstOrNull(),
            schedule,
            profile?.level ?: 1,
            profile?.totalXp ?: 0L,
            xpToday,
            recommendation,
            events.size
        )
    }

    suspend fun nextBestAction(
        now: Long = System.currentTimeMillis()
    ): ProactiveEvent? {

        val safeNow = now.coerceAtLeast(0L)

        val events = refresh(safeNow)
            .filter {
                it.status != ProactiveStatus.DISMISSED.name &&
                    it.status != ProactiveStatus.COMPLETED.name &&
                    !it.dismissedForever &&
                    (it.snoozeUntil ?: 0L) <= safeNow
            }

        if (events.isEmpty()) return null

        val currentAvailability = runCatching {
            availability.current()
        }.getOrDefault(
            AvailabilityState.FREE to "Available"
        )

        val state = currentAvailability.first

        val free =
            state == AvailabilityState.FREE ||
                state == AvailabilityState.STUDY

        val minutes = runCatching {
            availableMinutes(safeNow)
        }.getOrDefault(0)

        val tasksNow = runCatching {
            tasks.observeAll().first()
        }.getOrDefault(emptyList())

        val weakest = runCatching {
            evolution
                .dashboard()
                .skills
                .minWithOrNull(
                    compareBy<EvolutionSkill> { it.level }
                        .thenBy { it.xp }
                )
        }.getOrNull()

        return events
            .sortedWith(
                compareByDescending<ProactiveEvent> { event ->

                    val matchingTask = tasksNow.firstOrNull {
                        it.id == event.sourceId &&
                            event.source == "TASK"
                    }

                    val fit = matchingTask?.let { task ->
                        if (task.estimatedMinutes <= minutes) {
                            1.0
                        } else {
                            0.55
                        }
                    } ?: 0.8

                    val weaknessBonus =
                        if (
                            weakest != null &&
                            event.title.contains(
                                weakest.name,
                                ignoreCase = true
                            )
                        ) {
                            0.5
                        } else {
                            0.0
                        }

                    priorityRank(event.priority) * 10.0 +
                        fit +
                        weaknessBonus

                }.thenBy {
                    it.deadline ?: Long.MAX_VALUE
                }
            )
            .firstOrNull {
                free ||
                    it.priority == ProactivePriority.CRITICAL.name
            }
    }

    suspend fun markCommunicated(id: Long) {
        runCatching {
            dao.markCommunicated(id)
        }
    }

    suspend fun communicationDecision(
        event: ProactiveEvent
    ): Boolean {

        val now = System.currentTimeMillis()

        if (
            event.dismissedForever ||
            event.status == ProactiveStatus.DISMISSED.name ||
            event.status == ProactiveStatus.COMPLETED.name ||
            (event.snoozeUntil ?: 0L) > now
        ) {
            return false
        }

        val currentAvailability = runCatching {
            availability.current()
        }.getOrDefault(
            AvailabilityState.FREE to "Available"
        )

        val state = currentAvailability.first

        val inProtectedCalendar = runCatching {
            dao.schedule(now).any {
                now >= it.startTime &&
                    now < it.endTime &&
                    it.protected
            }
        }.getOrDefault(false)

        val criticalAllowed = runCatching {
            availability.criticalOverrideAllowed()
        }.getOrDefault(false)

        val blocked =
            (state != AvailabilityState.FREE || inProtectedCalendar) &&
                !(
                    event.priority == ProactivePriority.CRITICAL.name &&
                        criticalAllowed
                    )

        if (blocked) {
            if (event.status != ProactiveStatus.WAITING.name) {
                runCatching {
                    dao.updateEvent(
                        event.copy(
                            status = ProactiveStatus.WAITING.name,
                            communicationState = "WAITING_FOR_AVAILABILITY"
                        )
                    )
                }
            }

            return false
        }

        if (
            event.communicationCount > 0 &&
            event.priority != ProactivePriority.CRITICAL.name
        ) {
            return false
        }

        return event.priority != ProactivePriority.LOW.name
    }

    suspend fun setAvailability(
        state: AvailabilityState,
        startMinute: Int,
        endMinute: Int,
        daysMask: Int = 127,
        label: String = state.name,
        criticalOverride: Boolean = false
    ) {
        val safeStart = startMinute.coerceIn(0, 1439)
        val safeEnd = endMinute.coerceIn(0, 1439)
        val safeDays = daysMask.coerceAtLeast(0)

        runCatching {
            dao.insertAvailability(
                AvailabilityRule(
                    state = state.name,
                    startMinute = safeStart,
                    endMinute = safeEnd,
                    daysMask = safeDays,
                    label = label.ifBlank { state.name },
                    criticalOverride = criticalOverride
                )
            )
        }
    }

    suspend fun clearAvailability() {
        runCatching {
            dao.availability().forEach {
                dao.deleteAvailability(it)
            }
        }
    }

    suspend fun snooze(
        id: Long,
        until: Long
    ) {
        if (until <= System.currentTimeMillis()) return

        runCatching {
            dao.event(id)?.let { event ->
                dao.updateEvent(
                    event.copy(
                        status = ProactiveStatus.SNOOZED.name,
                        snoozeUntil = until,
                        communicationState = "SNOOZED"
                    )
                )

                learn(event.type, "snooze")
            }
        }
    }

    suspend fun dismiss(
        id: Long,
        forever: Boolean = false
    ) {
        runCatching {
            dao.event(id)?.let { event ->
                dao.updateEvent(
                    event.copy(
                        status = ProactiveStatus.DISMISSED.name,
                        dismissedForever = forever,
                        communicationState = "DISMISSED"
                    )
                )

                learn(event.type, "dismiss")
            }
        }
    }

    suspend fun complete(id: Long) {
        runCatching {
            dao.event(id)?.let { event ->
                dao.updateEvent(
                    event.copy(
                        status = ProactiveStatus.COMPLETED.name,
                        communicationState = "COMPLETED"
                    )
                )

                learn(event.type, "follow")
            }
        }
    }

    suspend fun reschedule(
        id: Long,
        deadline: Long?
    ) {
        runCatching {
            dao.event(id)?.let { event ->
                dao.updateEvent(
                    event.copy(
                        status = ProactiveStatus.PENDING.name,
                        deadline = deadline,
                        snoozeUntil = null,
                        communicationState = "UNCOMMUNICATED"
                    )
                )
            }
        }
    }

    suspend fun changePriority(
        id: Long,
        priority: ProactivePriority
    ) {
        runCatching {
            dao.event(id)?.let { event ->
                dao.updateEvent(
                    event.copy(
                        priority = priority.name,
                        manualPriority = priority.name
                    )
                )
            }
        }
    }

    suspend fun createSubtasks(
        title: String,
        skillName: String? = null
    ): List<String> {

        val cleanTitle = title.trim()

        if (cleanTitle.isBlank()) {
            return emptyList()
        }

        val steps =
            if (
                cleanTitle.contains("ml", ignoreCase = true) ||
                cleanTitle.contains(
                    "machine learning",
                    ignoreCase = true
                )
            ) {
                listOf(
                    "Clean dataset",
                    "Explore data",
                    "Train baseline",
                    "Evaluate model",
                    "Improve model",
                    "Save model",
                    "Create API",
                    "Test deployment",
                    "Document project"
                )
            } else {
                listOf(
                    "Define the outcome",
                    "Gather required material",
                    "Do the main work",
                    "Review the result",
                    "Finish and document"
                )
            }

        val skill = if (!skillName.isNullOrBlank()) {
            runCatching {
                evolution.findSkill(skillName.trim())
            }.getOrNull()
        } else {
            null
        }

        steps.forEachIndexed { index, step ->

            val subtaskTitle =
                "$cleanTitle — ${index + 1}. $step"

            val description =
                buildString {
                    append("Subtask of: ")
                    append(cleanTitle)

                    if (skill != null) {
                        append("; Evolution skill: ")
                        append(skill.name)
                    }
                }

            runCatching {
                tasks.insert(
                    Task(
                        title = subtaskTitle,
                        description = description,
                        priority = "NORMAL"
                    )
                )
            }

            if (skill != null) {
                runCatching {
                    evolution.addQuest(
                        EvolutionQuest(
                            title = subtaskTitle,
                            description =
                                "Optional skill-linked subtask for ${skill.name}.",
                            skillId = skill.id,
                            category = skill.category,
                            type = "TASK",
                            difficulty = 2,
                            xpReward = 15L
                        )
                    )
                }
            }
        }

        return steps
    }

    suspend fun yesterdayIncomplete(): String {

        val start = runCatching {
            LocalDate
                .now(ZoneId.systemDefault())
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        }.getOrDefault(System.currentTimeMillis())

        val incompleteTasks = runCatching {
            tasks.incompleteBefore(start)
        }.getOrDefault(emptyList())

        return if (incompleteTasks.isEmpty()) {
            "You have no overdue unfinished tasks from before today."
        } else {
            incompleteTasks
                .take(8)
                .joinToString("\n") {
                    "Still open: ${it.title}"
                }
        }
    }

    suspend fun remindWhenFree(): String {

        val next = refresh()
            .filter {
                it.status != ProactiveStatus.DISMISSED.name &&
                    it.status != ProactiveStatus.COMPLETED.name &&
                    !it.dismissedForever
            }
            .sortedWith(
                compareByDescending<ProactiveEvent> {
                    priorityRank(it.priority)
                }.thenBy {
                    it.deadline ?: Long.MAX_VALUE
                }
            )
            .firstOrNull()
            ?: return "There is nothing important queued to remind you about."

        val currentAvailability = runCatching {
            availability.current()
        }.getOrDefault(
            AvailabilityState.FREE to "Available"
        )

        val state = currentAvailability.first

        return if (
            state == AvailabilityState.FREE ||
            state == AvailabilityState.STUDY
        ) {
            "You are free now. I recommend: ${next.title}."
        } else {

            runCatching {
                dao.updateEvent(
                    next.copy(
                        status = ProactiveStatus.WAITING.name,
                        communicationState =
                            "WAITING_FOR_AVAILABILITY"
                    )
                )
            }

            "Understood. I will wait until you become available before bringing up ${next.title}."
        }
    }

    suspend fun planDay(
        now: LocalDateTime = LocalDateTime.now()
    ): List<String> {

        val taskList = runCatching {
            tasks.observeAll().first()
        }.getOrDefault(emptyList())

        val blocks = runCatching {
            dao.schedule(System.currentTimeMillis())
        }.getOrDefault(emptyList())

        val rules = runCatching {
            dao.availability()
        }.getOrDefault(emptyList())

        val plan = runCatching {
            DailyPlanner.plan(
                now = now,
                tasks = taskList,
                schedule = blocks,
                quiet = rules
            )
        }.getOrDefault(emptyList())

        if (plan.isEmpty()) {
            return listOf(
                "No schedulable work remains in the current free time. Protect your recovery or use the next available block."
            )
        }

        return plan.map { item ->

            val start = item.start
                .toLocalTime()
                .toString()
                .take(5)

            val end = item.end
                .toLocalTime()
                .toString()
                .take(5)

            "$start–$end  ${item.title} (${item.reason})"
        }
    }

    suspend fun availableMinutes(
        now: Long = System.currentTimeMillis()
    ): Int {

        val safeNow = now.coerceAtLeast(0L)

        val start = runCatching {
            LocalDateTime.ofInstant(
                Instant.ofEpochMilli(safeNow),
                ZoneId.systemDefault()
            )
        }.getOrElse {
            LocalDateTime.now()
        }

        val end = start
            .toLocalDate()
            .plusDays(1)
            .atStartOfDay()

        val endMillis = runCatching {
            end.atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        }.getOrDefault(safeNow)

        val totalMinutes =
            ((endMillis - safeNow).coerceAtLeast(0L) /
                MINUTE_MILLIS)
                .toInt()

        val schedule = runCatching {
            dao.schedule(safeNow)
        }.getOrDefault(emptyList())

        val busy = schedule
            .filter { it.endTime > safeNow }
            .sumOf { block ->

                val from =
                    maxOf(safeNow, block.startTime)

                val to =
                    minOf(endMillis, block.endTime)

                (
                    (to - from)
                        .coerceAtLeast(0L) /
                        MINUTE_MILLIS
                    ).toInt()
            }

        return (totalMinutes - busy)
            .coerceAtLeast(0)
    }

    suspend fun dailyBriefing(): String {

        val snapshot = snapshot()

        val allTasks = runCatching {
            tasks.observeAll().first()
        }.getOrDefault(emptyList())

        val today = currentDate(System.currentTimeMillis())

        val tasksToday = allTasks.filter { task ->
            if (task.completed) {
                false
            } else {
                task.dueTime == null ||
                    runCatching {
                        Instant.ofEpochMilli(task.dueTime)
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate() == today
                    }.getOrDefault(false)
            }
        }

        val classes = snapshot.schedule
            .take(3)
            .joinToString(", ") { block ->

                val time = runCatching {
                    Instant.ofEpochMilli(block.startTime)
                        .atZone(ZoneId.systemDefault())
                        .toLocalTime()
                        .toString()
                        .take(5)
                }.getOrDefault("--:--")

                "$time ${block.title}"
            }

        if (
            tasksToday.isEmpty() &&
            snapshot.important.isEmpty()
        ) {
            return "Good morning. Your day has no high-priority unfinished work detected."
        }

        val recommended =
            snapshot.recommendation?.title
                ?: tasksToday.firstOrNull()?.title
                ?: "protect your highest-value goal"

        return buildString {
            append("Good morning. ")
            append("You have ")
            append(tasksToday.size)
            append(" task(s) due today and ")
            append(snapshot.important.size)
            append(" important proactive item(s). ")
            append("Recommended priority: ")
            append(recommended)
            append(".")

            if (classes.isNotBlank()) {
                append(" Schedule: ")
                append(classes)
                append(".")
            }
        }
    }

    suspend fun eveningReview(): String {

        val snapshot = snapshot()

        val allTasks = runCatching {
            tasks.observeAll().first()
        }.getOrDefault(emptyList())

        val completed =
            allTasks.count { it.completed }

        val incomplete =
            allTasks.count { !it.completed }

        val tomorrow =
            LocalDate.now(ZoneId.systemDefault())
                .plusDays(1)

        val tomorrowDue = allTasks.count { task ->
            !task.completed &&
                task.dueTime != null &&
                runCatching {
                    Instant.ofEpochMilli(task.dueTime)
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate() == tomorrow
                }.getOrDefault(false)
        }

        val weakest = runCatching {
            evolution
                .dashboard()
                .skills
                .minWithOrNull(
                    compareBy<EvolutionSkill> { it.level }
                        .thenBy { it.xp }
                )
                ?.name
        }.getOrNull()

        return buildString {

            append("Evening review: ")
            append(completed)
            append(" task(s) completed, ")
            append(incomplete)
            append(" incomplete, ")
            append(snapshot.xpToday)
            append(" XP gained today, ")
            append(tomorrowDue)
            append(" deadline(s) tomorrow. ")

            if (weakest != null) {
                append("Skill needing attention: ")
                append(weakest)
                append(". ")
            }

            append("Next priority: ")
            append(
                snapshot.recommendation?.title
                    ?: "none"
            )
            append(".")
        }
    }

    suspend fun queueMessage(
        event: ProactiveEvent
    ) {
        runCatching {
            if (dao.byDedupeKey(event.dedupeKey) == null) {
                dao.insertEvent(event)
            }
        }
    }

    private suspend fun upsert(
        type: String,
        title: String,
        detail: String,
        source: String,
        sourceId: Long?,
        deadline: Long?,
        requiredAction: String,
        score: Double,
        now: Long
    ) {

        val safeTitle = title.trim()

        if (safeTitle.isBlank()) return

        val key =
            "$source:${sourceId ?: safeTitle.lowercase()}"

        val existing = runCatching {
            dao.byDedupeKey(key)
        }.getOrNull()

        val feedback = runCatching {
            dao.feedback(type)
        }.getOrNull()

        val penalty = (
            (feedback?.dismissals ?: 0) * 0.025 +
                (feedback?.snoozes ?: 0) * 0.01 -
                (feedback?.follows ?: 0) * 0.008
            ).coerceIn(-0.10, 0.25)

        val finalScore =
            (score - penalty)
                .coerceIn(0.0, 1.0)

        val manualPriority =
            existing?.manualPriority?.let { value ->
                runCatching {
                    ProactivePriority.valueOf(value)
                }.getOrNull()
            }

        val priority = runCatching {
            PriorityEngine.classify(
                finalScore,
                manualPriority
            )
        }.getOrDefault(
            ProactivePriority.MEDIUM
        )

        val status =
            if (
                existing?.status ==
                    ProactiveStatus.SNOOZED.name &&
                (existing.snoozeUntil ?: 0L) > now
            ) {
                existing.status
            } else {
                ProactiveStatus.PENDING.name
            }

        val event = (
            existing
                ?: ProactiveEvent(
                    type = type,
                    title = safeTitle,
                    source = source,
                    sourceId = sourceId,
                    dedupeKey = key
                )
            ).copy(
                title = safeTitle,
                detail = detail,
                priority = priority.name,
                baseImportance = finalScore,
                urgency =
                    if (deadline != null) 0.8 else 0.35,
                deadlineProximity =
                    runCatching {
                        PriorityEngine.deadlineFactor(
                            deadline,
                            now
                        )
                    }.getOrDefault(0.0),
                relevance = 0.8,
                deadline = deadline,
                requiredAction = requiredAction,
                lastEvaluatedAt = now,
                status = status
            )

        if (existing == null) {
            runCatching {
                dao.insertEvent(event)
            }
        } else if (
            existing.status != ProactiveStatus.COMPLETED.name &&
            existing.status != ProactiveStatus.DISMISSED.name
        ) {
            runCatching {
                dao.updateEvent(event)
            }
        }
    }

    private suspend fun learn(
        type: String,
        action: String
    ) {

        runCatching {

            val old =
                dao.feedback(type)
                    ?: ProactiveFeedback(type)

            val next =
                when (action) {
                    "snooze" -> old.copy(
                        snoozes = old.snoozes + 1
                    )

                    "dismiss" -> old.copy(
                        dismissals = old.dismissals + 1
                    )

                    else -> old.copy(
                        follows = old.follows + 1
                    )
                }

            dao.saveFeedback(
                next.copy(
                    updatedAt =
                        System.currentTimeMillis()
                )
            )
        }
    }

    private suspend fun syncAuthorizedCalendar() {

        val signals = runCatching {
            personalData
                ?.calendarSignals()
                .orEmpty()
        }.getOrDefault(emptyList())

        if (signals.isEmpty()) {
            return
        }

        runCatching {
            dao.clearSchedule()
        }

        signals.forEach { signal ->
            runCatching {
                dao.insertSchedule(
                    ProactiveScheduleBlock(
                        title = signal.title,
                        startTime = signal.begin,
                        endTime = signal.end,
                        state = AvailabilityState.BUSY.name,
                        protected = true
                    )
                )
            }
        }
    }

    private suspend fun expireOld(
        now: Long
    ) {

        val cutoff =
            now - DAY_MILLIS

        val events = runCatching {
            dao.activeEvents()
        }.getOrDefault(emptyList())

        events
            .filter {
                it.deadline != null &&
                    it.deadline < cutoff &&
                    it.status != ProactiveStatus.COMPLETED.name &&
                    it.status != ProactiveStatus.DISMISSED.name
            }
            .forEach {
                runCatching {
                    dao.setStatus(
                        it.id,
                        ProactiveStatus.EXPIRED.name
                    )
                }
            }
    }

    private fun currentDate(
        millis: Long
    ): LocalDate {
        return runCatching {
            Instant.ofEpochMilli(millis)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
        }.getOrElse {
            LocalDate.now(ZoneId.systemDefault())
        }
    }

    private fun priorityRank(
        priority: String
    ): Int {
        return when (priority.uppercase()) {
            "CRITICAL" -> 4
            "HIGH" -> 3
            "MEDIUM" -> 2
            "LOW" -> 1
            else -> 1
        }
    }
}
