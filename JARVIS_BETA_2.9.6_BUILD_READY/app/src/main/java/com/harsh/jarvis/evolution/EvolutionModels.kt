package com.harsh.jarvis.evolution

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "evolution_profile")
data class EvolutionProfile(
    @PrimaryKey val id: Int = 1,
    val level: Int = 1,
    val totalXp: Long = 0,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "evolution_skills")
data class EvolutionSkill(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val category: String,
    val xp: Long = 0,
    val level: Int = 1,
    val active: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "evolution_quests")
data class EvolutionQuest(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String = "",
    val skillId: Long? = null,
    val category: String = "",
    val type: String = "SIDE",
    val difficulty: Int = 1,
    val xpReward: Long = 50,
    val deadline: Long? = null,
    val status: String = "PENDING",
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null
)

@Entity(tableName = "evolution_goals")
data class EvolutionGoal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String = "",
    val progress: Int = 0,
    val target: Int = 100,
    val active: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "evolution_achievements", indices = [androidx.room.Index(value = ["key"], unique = true)])
data class EvolutionAchievement(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val key: String,
    val title: String,
    val description: String,
    val unlockedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "evolution_history")
data class EvolutionHistory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entityType: String,
    val entityId: Long,
    val fromLevel: Int,
    val toLevel: Int,
    val xpAwarded: Long,
    val reason: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "evolution_streaks")
data class EvolutionStreak(
    @PrimaryKey val key: String,
    val current: Int = 0,
    val best: Int = 0,
    val lastActivityDay: String? = null
)

data class EvolutionDashboard(
    val profile: EvolutionProfile,
    val skills: List<EvolutionSkill>,
    val todayQuests: List<EvolutionQuest>,
    val nextChallenge: EvolutionQuest?,
    val goals: List<EvolutionGoal>,
    val achievements: List<EvolutionAchievement>,
    val streaks: List<EvolutionStreak> = emptyList()
)

@Entity(tableName = "evolution_performance")
data class EvolutionPerformance(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val questId: Long,
    val skillId: Long?,
    val difficulty: Int,
    val completed: Boolean,
    val quality: Double,
    val recordedAt: Long = System.currentTimeMillis()
)
