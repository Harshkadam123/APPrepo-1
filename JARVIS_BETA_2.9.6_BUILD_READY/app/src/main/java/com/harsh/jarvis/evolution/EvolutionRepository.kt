package com.harsh.jarvis.evolution

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class EvolutionRepository(private val dao: EvolutionDao) {
    private val formula = EvolutionFormula()

    suspend fun ensureProfile() {
        if (dao.profile() == null) dao.upsertProfile(EvolutionProfile())
        if (dao.observeSkills().first().isEmpty()) {
            defaultSkills().forEach { dao.insertSkill(EvolutionSkill(name = it.first, category = it.second)) }
        }
    }

    private fun defaultSkills(): List<Pair<String, String>> = listOf(
        "Strength" to "PHYSICAL", "Endurance" to "PHYSICAL", "Speed" to "PHYSICAL",
        "Mobility" to "PHYSICAL", "Balance" to "PHYSICAL", "Coordination" to "PHYSICAL",
        "Fitness" to "PHYSICAL", "Consistency" to "PHYSICAL",
        "Programming" to "INTELLIGENCE", "Python" to "INTELLIGENCE", "DSA" to "INTELLIGENCE",
        "Mathematics" to "INTELLIGENCE", "Statistics" to "INTELLIGENCE", "Linear Algebra" to "INTELLIGENCE",
        "DBMS" to "INTELLIGENCE", "SQL" to "INTELLIGENCE", "AI/ML" to "INTELLIGENCE",
        "Deep Learning" to "INTELLIGENCE", "Logical Reasoning" to "INTELLIGENCE",
        "Problem Solving" to "INTELLIGENCE", "Communication" to "INTELLIGENCE",
        "Reading/Comprehension" to "INTELLIGENCE", "Project Development" to "INTELLIGENCE"
    )

    suspend fun findSkill(name: String): EvolutionSkill? =
        dao.observeSkills().first().firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }

    suspend fun addSkill(name: String, category: String): Long =
        dao.insertSkill(EvolutionSkill(name = name.trim(), category = category.trim()))

    suspend fun renameSkill(id: Long, name: String) = dao.renameSkill(id, name.trim())
    suspend fun removeSkill(id: Long) = dao.deactivateSkill(id)

    suspend fun addQuest(quest: EvolutionQuest): Long = dao.insertQuest(quest)

    suspend fun addGoal(goal: EvolutionGoal): Long = dao.insertGoal(goal)

    suspend fun setGoalProgress(id: Long, progress: Int) {
        val goal = dao.observeGoals().first().firstOrNull { it.id == id } ?: return
        dao.updateGoal(goal.copy(progress = progress.coerceIn(0, goal.target)))
    }

    suspend fun completeQuest(id: Long, quality: Double = 1.0, performance: Double = 1.0): Result<String> {
        val quest = dao.quest(id) ?: return Result.failure(IllegalArgumentException("Quest not found"))
        if (quest.status == "COMPLETED") return Result.success("Quest was already completed.")
        val repeatCount = dao.completedCount(quest.title)
        val repeatMultiplier = 1.0 / (1.0 + repeatCount * EvolutionFormulaConfig().repeatDiminishingFactor)
        val reward = (formula.reward(quest.xpReward, quest.difficulty, quality = quality, consistency = performance) * repeatMultiplier).toLong().coerceAtLeast(1)
        awardXp(reward, quest.skillId, "Completed quest: ${quest.title}")
        dao.insertPerformance(EvolutionPerformance(questId = quest.id, skillId = quest.skillId, difficulty = quest.difficulty, completed = true, quality = quality.coerceIn(0.0, 1.0)))
        dao.updateQuest(quest.copy(status = "COMPLETED", completedAt = System.currentTimeMillis()))
        updateStreak("quest")
        unlock("first_quest", "First Quest", "Completed your first evolution quest.")
        return Result.success("Quest complete. +$reward XP.")
    }

    suspend fun awardXp(amount: Long, skillId: Long?, reason: String) {
        if (amount <= 0) return
        val old = dao.profile() ?: EvolutionProfile()
        val oldLevel = old.level
        val newTotal = old.totalXp + amount
        val newLevel = formula.levelForXp(newTotal)
        dao.upsertProfile(old.copy(level = newLevel, totalXp = newTotal, updatedAt = System.currentTimeMillis()))
        if (newLevel > oldLevel) {
            dao.insertHistory(EvolutionHistory("PROFILE", 1, oldLevel, newLevel, amount, reason))
            unlock("level_$newLevel", "Level $newLevel", "Reached JARVIS Evolution Level $newLevel.")
        }
        if (skillId != null) {
            val skill = dao.skill(skillId)
            if (skill != null) {
                val oldSkillLevel = skill.level
                val newSkillXp = skill.xp + amount
                val newSkillLevel = formula.levelForXp(newSkillXp)
                dao.updateSkill(skill.copy(xp = newSkillXp, level = newSkillLevel))
                if (newSkillLevel > oldSkillLevel) {
                    dao.insertHistory(EvolutionHistory("SKILL", skill.id, oldSkillLevel, newSkillLevel, amount, "Skill progression: ${skill.name}"))
                    unlock("skill_${skill.id}_level_$newSkillLevel", "${skill.name} Level $newSkillLevel", "Reached ${skill.name} Level $newSkillLevel.")
                }
            }
        }
    }

    suspend fun completeFirstPending(): Result<String> {
        val pending = dao.observeQuests().first().firstOrNull { it.status == "PENDING" }
            ?: return Result.success("There is no pending evolution quest.")
        return completeQuest(pending.id)
    }

    suspend fun startRecommendedChallenge(): EvolutionQuest? = recommendNextChallenge()

    suspend fun updateStreak(key: String, day: LocalDate = LocalDate.now()) {
        val current = dao.streak(key)
        val today = day.toString()
        val next = when {
            current == null -> EvolutionStreak(key, 1, 1, today)
            current.lastActivityDay == today -> current
            current.lastActivityDay != null && runCatching {
                ChronoUnit.DAYS.between(LocalDate.parse(current.lastActivityDay), day) == 1L
            }.getOrDefault(false) -> current.copy(current = current.current + 1, best = maxOf(current.best, current.current + 1), lastActivityDay = today)
            else -> current.copy(current = 1, best = maxOf(current.best, 1), lastActivityDay = today)
        }
        dao.upsertStreak(next)
        if (next.current >= 7) unlock("seven_day_consistency", "7-Day Consistency", "Maintained a seven-day evolution streak.")
    }

    suspend fun unlock(key: String, title: String, description: String) {
        if (dao.achievement(key) == null) dao.insertAchievement(EvolutionAchievement(key = key, title = title, description = description))
    }

    fun skills(): Flow<List<EvolutionSkill>> = dao.observeSkills()
    fun quests(): Flow<List<EvolutionQuest>> = dao.observeQuests()
    fun goals(): Flow<List<EvolutionGoal>> = dao.observeGoals()
    fun achievements(): Flow<List<EvolutionAchievement>> = dao.observeAchievements()
    fun history(): Flow<List<EvolutionHistory>> = dao.observeHistory()

    suspend fun dashboard(): EvolutionDashboard {
        ensureProfile()
        val quests = dao.observeQuests().first()
        val skills = dao.observeSkills().first()
        return EvolutionDashboard(
            dao.profile() ?: EvolutionProfile(),
            skills,
            quests.filter { it.status == "PENDING" }.take(3),
            recommendNextChallenge(quests, skills),
            dao.observeGoals().first(),
            dao.observeAchievements().first(),
            dao.observeStreaks().first()
        )
    }

    suspend fun recommendNextChallenge(
        quests: List<EvolutionQuest> = dao.observeQuests().first(),
        skills: List<EvolutionSkill> = dao.observeSkills().first()
    ): EvolutionQuest? {
        val pending = quests.filter { it.status == "PENDING" }
        if (pending.isNotEmpty()) return pending.minWithOrNull(compareBy<EvolutionQuest> { it.difficulty }.thenBy { it.deadline ?: Long.MAX_VALUE })
        val weakest = skills.minByOrNull { it.level to it.xp } ?: return null
        val recent = dao.recentPerformance(weakest.id)
        val successRate = if (recent.isEmpty()) 0.5 else recent.count { it.completed }.toDouble() / recent.size
        val difficulty = AdaptiveDifficultyEngine().nextDifficulty(PerformanceSignal(true, weakest.level.coerceIn(1, 10), successRate))
        return EvolutionQuest(
            title = "Strengthen ${weakest.name}",
            description = "Create a meaningful challenge that demonstrates progress in ${weakest.name}.",
            skillId = weakest.id,
            category = weakest.category,
            type = "SIDE",
            difficulty = difficulty,
            xpReward = 50L + weakest.level * 10L
        )
    }

    suspend fun weaknessSummary(): String {
        val skills = dao.observeSkills().first()
        if (skills.isEmpty()) return "No skills are configured yet."
        val weakest = skills.sortedWith(compareBy<EvolutionSkill> { it.level }.thenBy { it.xp }).take(3)
        return "Skills needing attention: " + weakest.joinToString(", ") { "${it.name} Lv.${it.level}" }
    }

    suspend fun todaySummary(): String {
        val d = dashboard()
        val next = d.nextChallenge?.let { "${it.title} (${it.difficulty}/10, +${it.xpReward} XP)" } ?: "No challenge yet."
        return "Level ${d.profile.level}. XP ${d.profile.totalXp}. Next challenge: $next"
    }
}
