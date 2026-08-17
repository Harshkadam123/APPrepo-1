package com.harsh.jarvis.fitness

import android.content.Context

class FitnessRepository(context: Context) {
    private val prefs = context.getSharedPreferences("jarvis_fitness", Context.MODE_PRIVATE)

    fun profile(): FitnessProfile = FitnessProfile(
        level = prefs.getInt("level", 1),
        xp = prefs.getInt("xp", 0),
        goal = prefs.getString("goal", "General fitness") ?: "General fitness",
        mode = prefs.getString("mode", "Home") ?: "Home",
        minutes = prefs.getInt("minutes", 30),
        trainingDays = prefs.getInt("trainingDays", 4),
        equipment = prefs.getString("equipment", "Bodyweight") ?: "Bodyweight"
    )

    fun saveMode(mode: String) = prefs.edit().putString("mode", mode).apply()
    fun saveGoal(goal: String) = prefs.edit().putString("goal", goal).apply()
    fun saveMinutes(minutes: Int) = prefs.edit().putInt("minutes", minutes).apply()
    fun saveTrainingDays(days: Int) = prefs.edit().putInt("trainingDays", days).apply()
    fun saveEquipment(equipment: String) = prefs.edit().putString("equipment", equipment).apply()

    fun completeWorkout(): FitnessProfile {
        val current = profile()
        val newXp = current.xp + 100
        val newLevel = 1 + (newXp / 1000)
        prefs.edit().putInt("xp", newXp).putInt("level", newLevel).apply()
        return profile()
    }

    fun addExerciseXp(xp: Int): FitnessProfile {
        val current = profile()
        val newXp = current.xp + xp.coerceAtLeast(0)
        val newLevel = 1 + (newXp / 1000)
        prefs.edit().putInt("xp", newXp).putInt("level", newLevel).apply()
        return profile()
    }
}
