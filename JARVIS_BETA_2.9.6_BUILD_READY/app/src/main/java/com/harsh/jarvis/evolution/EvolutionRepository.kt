package com.harsh.jarvis.evolution

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Central repository for JARVIS Evolution data.
 *
 * Design goals:
 * - All database access stays inside suspend functions.
 * - No suspend calls are used in default parameter values.
 * - Constructor arguments for EvolutionHistory use named parameters.
 * - Ranking logic uses explicit comparators instead of Pair comparison.
 * - Inputs are validated/clamped where practical.
 * - Missing database records are handled safely.
 */
class EvolutionRepository(
    private val dao: EvolutionDao
) {

    private val formula = EvolutionFormula()
    private val adaptiveDifficulty = AdaptiveDifficultyEngine()
    private val formulaConfig = EvolutionFormulaConfig()

    // -------------------------------------------------------------------------
    // INITIALIZATION
    // -------------------------------------------------------------------------

    suspend fun ensureProfile() {
        if (dao.profile() == null) {
            dao.upsertProfile(EvolutionProfile())
        }

        val existingSkills = dao.observeSkills().first()

        if (existingSkills.isEmpty()) {
            defaultSkills().forEach { (name, category) ->
                dao.insertSkill(
                    EvolutionSkill(
                        name = name,
                        category = category
                    )
                )
            }
        }
    }

    private fun defaultSkills(): List<Pair<String, String>> {
        return listOf(
            // Physical
            "Strength" to "PHYSICAL",
            "Endurance" to "PHYSICAL",
            "Speed" to "PHYSICAL",
            "Mobility" to "PHYSICAL",
            "Balance" to "PHYSICAL",
            "Coordination" to "PHYSICAL",
            "Fitness" to "PHYSICAL",
            "Consistency" to "PHYSICAL",

            // Intelligence
            "Programming" to "INTELLIGENCE",
            "Python" to "INTELLIGENCE",
            "DSA" to "INTELLIGENCE",
            "Mathematics" to "INTELLIGENCE",
            "Statistics" to "INTELLIGENCE",
            "Linear Algebra" to "INTELLIGENCE",
            "DBMS" to "INTELLIGENCE",
            "SQL" to "INTELLIGENCE",
            "AI/ML" to "INTELLIGENCE",
            "Deep Learning" to "INTELLIGENCE",
            "Logical Reasoning" to "INTELLIGENCE",
            "Problem Solving" to "INTELLIGENCE",
            "Communication" to "INTELLIGENCE",
            "Reading/Comprehension" to "INTELLIGENCE",
            "Project Development" to "INTELLIGENCE"
        )
    }

    // -------------------------------------------------------------------------
    // SKILLS
    // -------------------------------------------------------------------------

    suspend fun findSkill(name: String): EvolutionSkill? {
        val cleanName = name.trim()

        if (cleanName.isBlank()) {
            return null
        }

        return dao
            .observeSkills()
            .first()
            .firstOrNull {
                it.name.equals(cleanName, ignoreCase = true)
            }
    }

    suspend fun addSkill(
        name: String,
        category: String
    ): Long {
        val cleanName = name.trim()
        val cleanCategory = category.trim()

        require(cleanName.isNotBlank()) {
            "Skill name cannot be blank."
        }

        require(cleanCategory.isNotBlank()) {
            "Skill category cannot be blank."
        }

        return dao.insertSkill(
            EvolutionSkill(
                name = cleanName,
                category = cleanCategory
            )
        )
    }

    suspend fun renameSkill(
        id: Long,
        name: String
    ) {
        if (id <= 0L) return

        val cleanName = name.trim()

        if (cleanName.isBlank()) return

        dao.renameSkill(id, cleanName)
    }

    suspend fun removeSkill(id: Long) {
        if (id <= 0L) return
        dao.deactivateSkill(id)
    }

    // -------------------------------------------------------------------------
    // QUESTS / GOALS
    // -------------------------------------------------------------------------

    suspend fun addQuest(
        quest: EvolutionQuest
    ): Long {
        return dao.insertQuest(quest)
    }

    suspend fun addGoal(
        goal: EvolutionGoal
    ): Long {
        return dao.insertGoal(goal)
    }

    suspend fun setGoalProgress(
        id: Long,
        progress: Int
    ) {
        if (id <= 0L) return

        val goals = dao.observeGoals().first()

        val goal = goals.firstOrNull {
            it.id == id
        } ?: return

        val safeProgress = progress.coerceIn(
            0,
            goal.target.coerceAtLeast(0)
        )

        dao.updateGoal(
            goal.copy(
                progress = safeProgress
            )
        )
    }

    // -------------------------------------------------------------------------
    // QUEST COMPLETION
    // -------------------------------------------------------------------------

    suspend fun completeQuest(
        id: Long,
        quality: Double,
        performance: Double
    ): Result<String> {

        if (id <= 0L) {
            return Result.failure(
                IllegalArgumentException("Invalid quest ID.")
            )
        }

        val quest = dao.quest(id)
            ?: return Result.failure(
                IllegalArgumentException("Quest not found.")
            )

        if (quest.status.equals("COMPLETED", ignoreCase = true)) {
            return Result.success(
                "Quest was already completed."
            )
        }

        val safeQuality = quality.coerceIn(0.0, 1.0)
        val safePerformance = performance.coerceIn(0.0, 1.0)

        val repeatCount = dao.completedCount(quest.title)
            .coerceAtLeast(0)

        val repeatMultiplier =
            1.0 /
                (
                    1.0 +
                        repeatCount.toDouble() *
                        formulaConfig.repeatDiminishingFactor
                )

        val rawReward = formula.reward(
            quest.xpReward.coerceAtLeast(0L),
            quest.difficulty,
            quality = safeQuality,
            consistency = safePerformance
        )

        val reward = (
            rawReward * repeatMultiplier
            )
            .toLong()
            .coerceAtLeast(1L)

        awardXp(
            amount = reward,
            skillId = quest.skillId,
            reason = "Completed quest: ${quest.title}"
        )

        dao.insertPerformance(
            EvolutionPerformance(
                questId = quest.id,
                skillId = quest.skillId,
                difficulty = quest.difficulty,
                completed = true,
                quality = safeQuality
            )
        )

        dao.updateQuest(
            quest.copy(
                status = "COMPLETED",
                completedAt = System.currentTimeMillis()
            )
        )

        updateStreak("quest")

        unlock(
            key = "first_quest",
            title = "First Quest",
            description = "Completed your first evolution quest."
        )

        return Result.success(
            "Quest complete. +$reward XP."
        )
    }

    /**
     * Convenience overload preserving the original API:
     *
     * completeQuest(id)
     */
    suspend fun completeQuest(
        id: Long
    ): Result<String> {
        return completeQuest(
            id = id,
            quality = 1.0,
            performance = 1.0
        )
    }

    // -------------------------------------------------------------------------
    // XP
    // -------------------------------------------------------------------------

    suspend fun awardXp(
        amount: Long,
        skillId: Long?,
        reason: String
    ) {
        if (amount <= 0L) return

        val cleanReason = reason.trim().ifBlank {
            "Evolution XP awarded"
        }

        val oldProfile = dao.profile()
            ?: EvolutionProfile()

        val oldLevel = oldProfile.level

        val newTotalXp = (
            oldProfile.totalXp + amount
            ).coerceAtLeast(0L)

        val newLevel = formula.levelForXp(
            newTotalXp
        )

        dao.upsertProfile(
            oldProfile.copy(
                level = newLevel,
                totalXp = newTotalXp,
                updatedAt = System.currentTimeMillis()
            )
        )

        // Profile level-up
        if (newLevel > oldLevel) {

            dao.insertHistory(
                EvolutionHistory(
                    entityType = "PROFILE",
                    entityId = 1L,
                    fromLevel = oldLevel,
                    toLevel = newLevel,
                    xpAwarded = amount,
                    reason = cleanReason
                )
            )

            unlock(
                key = "level_$newLevel",
                title = "Level $newLevel",
                description = "Reached JARVIS Evolution Level $newLevel."
            )
        }

        // Skill XP
        if (skillId == null || skillId <= 0L) {
            return
        }

        val skill = dao.skill(skillId)
            ?: return

        val oldSkillLevel = skill.level

        val newSkillXp = (
            skill.xp + amount
            ).coerceAtLeast(0L)

        val newSkillLevel = formula.levelForXp(
            newSkillXp
        )

        dao.updateSkill(
            skill.copy(
                xp = newSkillXp,
                level = newSkillLevel
            )
        )

        // Skill level-up
        if (newSkillLevel > oldSkillLevel) {

            dao.insertHistory(
                EvolutionHistory(
                    entityType = "SKILL",
                    entityId = skill.id,
                    fromLevel = oldSkillLevel,
                    toLevel = newSkillLevel,
                    xpAwarded = amount,
                    reason = "Skill progression: ${skill.name}"
                )
            )

            unlock(
                key = "skill_${skill.id}_level_$newSkillLevel",
                title = "${skill.name} Level $newSkillLevel",
                description =
                    "Reached ${skill.name} Level $newSkillLevel."
            )
        }
    }

    // -------------------------------------------------------------------------
    // QUICK ACTIONS
    // -------------------------------------------------------------------------

    suspend fun completeFirstPending(): Result<String> {

        val quests = dao.observeQuests().first()

        val pending = quests.firstOrNull {
            it.status.equals("PENDING", ignoreCase = true)
        }

        if (pending == null) {
            return Result.success(
                "There is no pending evolution quest."
            )
        }

        return completeQuest(
            id = pending.id,
            quality = 1.0,
            performance = 1.0
        )
    }

    suspend fun startRecommendedChallenge(): EvolutionQuest? {
        return recommendNextChallenge()
    }

    // -------------------------------------------------------------------------
    // STREAK
    // -------------------------------------------------------------------------

    suspend fun updateStreak(
        key: String,
        day: LocalDate
    ) {
        val cleanKey = key.trim()

        if (cleanKey.isBlank()) {
            return
        }

        val current = dao.streak(cleanKey)
        val today = day.toString()

        val next = when {

            current == null -> {
                EvolutionStreak(
                    cleanKey,
                    1,
                    1,
                    today
                )
            }

            current.lastActivityDay == today -> {
                current
            }

            else -> {

                val consecutive = current.lastActivityDay
                    ?.let { previous ->
                        runCatching {
                            ChronoUnit.DAYS.between(
                                LocalDate.parse(previous),
                                day
                            ) == 1L
                        }.getOrDefault(false)
                    }
                    ?: false

                if (consecutive) {

                    val newCurrent =
                        current.current + 1

                    current.copy(
                        current = newCurrent,
                        best = maxOf(
                            current.best,
                            newCurrent
                        ),
                        lastActivityDay = today
                    )

                } else {

                    current.copy(
                        current = 1,
                        best = maxOf(
                            current.best,
                            1
                        ),
                        lastActivityDay = today
                    )
                }
            }
        }

        dao.upsertStreak(next)

        if (next.current >= 7) {
            unlock(
                key = "seven_day_consistency",
                title = "7-Day Consistency",
                description =
                    "Maintained a seven-day evolution streak."
            )
        }
    }

    /**
     * Convenience overload preserving:
     *
     * updateStreak("quest")
     */
    suspend fun updateStreak(
        key: String
    ) {
        updateStreak(
            key = key,
            day = LocalDate.now()
        )
    }

    // -------------------------------------------------------------------------
    // ACHIEVEMENTS
    // -------------------------------------------------------------------------

    suspend fun unlock(
        key: String,
        title: String,
        description: String
    ) {
        val cleanKey = key.trim()

        if (cleanKey.isBlank()) {
            return
        }

        if (dao.achievement(cleanKey) == null) {

            dao.insertAchievement(
                EvolutionAchievement(
                    key = cleanKey,
                    title = title.trim(),
                    description = description.trim()
                )
            )
        }
    }

    // -------------------------------------------------------------------------
    // OBSERVATION FLOWS
    // -------------------------------------------------------------------------

    fun skills(): Flow<List<EvolutionSkill>> {
        return dao.observeSkills()
    }

    fun quests(): Flow<List<EvolutionQuest>> {
        return dao.observeQuests()
    }

    fun goals(): Flow<List<EvolutionGoal>> {
        return dao.observeGoals()
    }

    fun achievements(): Flow<List<EvolutionAchievement>> {
        return dao.observeAchievements()
    }

    fun history(): Flow<List<EvolutionHistory>> {
        return dao.observeHistory()
    }

    // -------------------------------------------------------------------------
    // DASHBOARD
    // -------------------------------------------------------------------------

    suspend fun dashboard(): EvolutionDashboard {

        ensureProfile()

        val profile = dao.profile()
            ?: EvolutionProfile()

        val quests = dao.observeQuests().first()
        val skills = dao.observeSkills().first()
        val goals = dao.observeGoals().first()
        val achievements = dao.observeAchievements().first()
        val streaks = dao.observeStreaks().first()

        val pendingQuests = quests
            .filter {
                it.status.equals(
                    "PENDING",
                    ignoreCase = true
                )
            }
            .take(3)

        val nextChallenge = recommendNextChallenge(
            quests = quests,
            skills = skills
        )

        return EvolutionDashboard(
            profile,
            skills,
            pendingQuests,
            nextChallenge,
            goals,
            achievements,
            streaks
        )
    }

    // -------------------------------------------------------------------------
    // RECOMMENDATION
    // -------------------------------------------------------------------------

    suspend fun recommendNextChallenge(): EvolutionQuest? {

        val quests = dao.observeQuests().first()
        val skills = dao.observeSkills().first()

        return recommendNextChallenge(
            quests = quests,
            skills = skills
        )
    }

    /**
     * IMPORTANT:
     *
     * These lists are normal parameters.
     * We intentionally DO NOT write:
     *
     * quests = dao.observeQuests().first()
     *
     * as a default parameter because first() is suspend.
     */
    private suspend fun recommendNextChallenge(
        quests: List<EvolutionQuest>,
        skills: List<EvolutionSkill>
    ): EvolutionQuest? {

        val pending = quests.filter {
            it.status.equals(
                "PENDING",
                ignoreCase = true
            )
        }

        if (pending.isNotEmpty()) {

            return pending.minWithOrNull(
                compareBy<EvolutionQuest> {
                    it.difficulty
                }.thenBy {
                    it.deadline ?: Long.MAX_VALUE
                }
            )
        }

        if (skills.isEmpty()) {
            return null
        }

        // Explicit comparator avoids:
        // minByOrNull { it.level to it.xp }
        val weakest = skills.minWithOrNull(
            compareBy<EvolutionSkill> {
                it.level
            }.thenBy {
                it.xp
            }
        ) ?: return null

        val recent = dao.recentPerformance(
            weakest.id
        )

        val successRate =
            if (recent.isEmpty()) {
                0.5
            } else {
                recent.count {
                    it.completed
                }.toDouble() / recent.size.toDouble()
            }

        val safeLevel = weakest.level.coerceIn(
            1,
            10
        )

        val difficulty =
            adaptiveDifficulty.nextDifficulty(
                PerformanceSignal(
                    true,
                    safeLevel,
                    successRate.coerceIn(0.0, 1.0)
                )
            )

        return EvolutionQuest(
            title = "Strengthen ${weakest.name}",
            description =
                "Create a meaningful challenge that demonstrates progress in ${weakest.name}.",
            skillId = weakest.id,
            category = weakest.category,
            type = "SIDE",
            difficulty = difficulty,
            xpReward = (
                50L +
                    weakest.level.coerceAtLeast(0).toLong() * 10L
                ).coerceAtLeast(1L)
        )
    }

    // -------------------------------------------------------------------------
    // SUMMARIES
    // -------------------------------------------------------------------------

    suspend fun weaknessSummary(): String {

        val skills = dao.observeSkills().first()

        if (skills.isEmpty()) {
            return "No skills are configured yet."
        }

        val weakest = skills
            .sortedWith(
                compareBy<EvolutionSkill> {
                    it.level
                }.thenBy {
                    it.xp
                }
            )
            .take(3)

        return buildString {

            append("Skills needing attention: ")

            append(
                weakest.joinToString(", ") {
                    "${it.name} Lv.${it.level}"
                }
            )
        }
    }

    suspend fun todaySummary(): String {

        val dashboard = dashboard()

        val next =
            dashboard.nextChallenge?.let {
                "${it.title} (${it.difficulty}/10, +${it.xpReward} XP)"
            } ?: "No challenge yet."

        return buildString {

            append("Level ")
            append(dashboard.profile.level)

            append(". XP ")
            append(dashboard.profile.totalXp)

            append(". Next challenge: ")
            append(next)
        }
    }
}
