package com.harsh.jarvis.fitness

import android.content.Context

class SkillRepository(context: Context) {
    private val prefs = context.getSharedPreferences("jarvis_skills", Context.MODE_PRIVATE)

    fun progress(id: String): SkillProgress {
        val def = SkillCatalog.get(id)
        return SkillProgress(
            level = prefs.getInt("${id}_level", 0),
            xp = prefs.getInt("${id}_xp", 0),
            mastered = prefs.getBoolean("${id}_mastered", false),
            xpPerLevel = def?.xpPerLevel ?: 100
        )
    }

    fun allProgress(): Map<String, SkillProgress> = SkillCatalog.skills.associate { it.id to progress(it.id) }

    fun prerequisitesMet(skill: SkillDefinition): Boolean =
        skill.prerequisites.all { progress(it.skillId).level >= it.minLevel }

    fun isUnlocked(skill: SkillDefinition): Boolean =
        skill.prerequisites.isEmpty() || prerequisitesMet(skill)

    fun addXp(id: String, xp: Int): SkillProgress {
        val def = SkillCatalog.get(id) ?: return progress(id)
        val current = progress(id)
        if (current.mastered || !isUnlocked(def)) return current

        val safeXp = xp.coerceAtLeast(0)
        val newXp = (current.xp + safeXp).coerceAtMost(def.maxLevel * def.xpPerLevel)
        val newLevel = (newXp / def.xpPerLevel).coerceAtMost(def.maxLevel)
        val mastered = newLevel >= def.maxLevel

        prefs.edit()
            .putInt("${id}_xp", newXp)
            .putInt("${id}_level", newLevel)
            .putBoolean("${id}_mastered", mastered)
            .apply()

        return progress(id)
    }

    /** Every completed 100 m is one level; leftover metres are retained for the next log. */
    fun recordSwimmingMeters(meters: Int): SkillProgress {
        val safeMeters = meters.coerceAtLeast(0)
        val previousRemainder = prefs.getInt("swimming_remainder_m", 0)
        val total = previousRemainder + safeMeters
        val completedHundreds = total / 100
        val remainder = total % 100
        prefs.edit().putInt("swimming_remainder_m", remainder).apply()
        return if (completedHundreds > 0) addXp("swimming", completedHundreds * 100) else progress("swimming")
    }

    fun swimmingRemainderMeters(): Int = prefs.getInt("swimming_remainder_m", 0)

    /** One dataset-work unit is a real, completed cleaning task; size alone does not claim verification. */
    fun recordDatasetCleaned(units: Int): SkillProgress =
        addXp("data_cleaning", units.coerceAtLeast(0) * 10)

    /** Difficulty 1..5 changes XP while keeping a predictable base unit. */
    fun recordSkillWork(id: String, units: Int = 1, difficulty: Int = 1, note: String = ""): SkillProgress {
        val def = SkillCatalog.get(id) ?: return progress(id)
        val safeUnits = units.coerceAtLeast(0)
        val multiplier = difficulty.coerceIn(1, 5)
        val xpPerUnit = (def.xpPerLevel / 10).coerceAtLeast(1)
        if (safeUnits > 0) {
            val log = "${System.currentTimeMillis()}|$id|$safeUnits|$multiplier|${note.trim().take(160)}"
            val old = prefs.getString("activity_log", "").orEmpty()
            prefs.edit().putString("activity_log", (old + if (old.isEmpty()) "" else "\n") + log).apply()
        }
        return addXp(id, safeUnits * xpPerUnit * multiplier)
    }

    /**
     * SSS+ is only recorded when the user supplies a competition name, result/rank,
     * and evidence reference. JARVIS stores the evidence metadata but does not claim
     * independent verification.
     */
    fun recordCompetitionAchievement(
        id: String = "competition_wins",
        competition: String,
        result: String,
        evidence: String
    ): SkillProgress? {
        if (competition.isBlank() || result.isBlank() || evidence.isBlank()) return null
        val entry = "${System.currentTimeMillis()}|$competition|$result|${evidence.trim().take(240)}"
        val old = prefs.getString("competition_log", "").orEmpty()
        prefs.edit().putString("competition_log", (old + if (old.isEmpty()) "" else "\n") + entry).apply()
        return addXp(id, SkillCatalog.get(id)?.xpPerLevel ?: 500)
    }

    fun resetAll() {
        prefs.edit().clear().apply()
    }
}
