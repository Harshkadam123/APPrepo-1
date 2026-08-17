package com.harsh.jarvis.evolution

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface EvolutionDao {
    @Insert
    suspend fun insertPerformance(performance: EvolutionPerformance)

    @Query("SELECT * FROM evolution_performance WHERE skillId = :skillId ORDER BY recordedAt DESC LIMIT 10")
    suspend fun recentPerformance(skillId: Long): List<EvolutionPerformance>

    @Query("SELECT * FROM evolution_profile WHERE id = 1")
    suspend fun profile(): EvolutionProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfile(profile: EvolutionProfile)

    @Query("SELECT * FROM evolution_skills WHERE active = 1 ORDER BY category, level DESC, name")
    fun observeSkills(): Flow<List<EvolutionSkill>>

    @Query("SELECT * FROM evolution_skills WHERE id = :id LIMIT 1")
    suspend fun skill(id: Long): EvolutionSkill?

    @Insert
    suspend fun insertSkill(skill: EvolutionSkill): Long

    @Update
    suspend fun updateSkill(skill: EvolutionSkill)

    @Query("UPDATE evolution_skills SET name = :name WHERE id = :id")
    suspend fun renameSkill(id: Long, name: String)

    @Query("UPDATE evolution_skills SET active = 0 WHERE id = :id")
    suspend fun deactivateSkill(id: Long)

    @Query("SELECT * FROM evolution_quests ORDER BY CASE status WHEN 'PENDING' THEN 0 ELSE 1 END, deadline ASC, id DESC")
    fun observeQuests(): Flow<List<EvolutionQuest>>

    @Query("SELECT * FROM evolution_quests WHERE id = :id LIMIT 1")
    suspend fun quest(id: Long): EvolutionQuest?

    @Insert
    suspend fun insertQuest(quest: EvolutionQuest): Long

    @Update
    suspend fun updateQuest(quest: EvolutionQuest)

    @Query("SELECT COUNT(*) FROM evolution_quests WHERE title = :title AND status = 'COMPLETED'")
    suspend fun completedCount(title: String): Int

    @Query("SELECT * FROM evolution_goals WHERE active = 1 ORDER BY id DESC")
    fun observeGoals(): Flow<List<EvolutionGoal>>

    @Insert
    suspend fun insertGoal(goal: EvolutionGoal): Long

    @Update
    suspend fun updateGoal(goal: EvolutionGoal)

    @Query("SELECT * FROM evolution_achievements ORDER BY unlockedAt DESC")
    fun observeAchievements(): Flow<List<EvolutionAchievement>>

    @Query("SELECT * FROM evolution_achievements WHERE key = :key LIMIT 1")
    suspend fun achievement(key: String): EvolutionAchievement?

    @Insert
    suspend fun insertAchievement(achievement: EvolutionAchievement): Long

    @Insert
    suspend fun insertHistory(history: EvolutionHistory)

    @Query("SELECT * FROM evolution_history ORDER BY createdAt DESC")
    fun observeHistory(): Flow<List<EvolutionHistory>>

    @Query("SELECT * FROM evolution_streaks WHERE key = :key LIMIT 1")
    suspend fun streak(key: String): EvolutionStreak?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStreak(streak: EvolutionStreak)

    @Query("SELECT * FROM evolution_streaks ORDER BY `key`")
    fun observeStreaks(): Flow<List<EvolutionStreak>>
}
