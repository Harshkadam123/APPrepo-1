package com.harsh.jarvis.tasks

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Task::class,
        com.harsh.jarvis.memory.Memory::class,
        com.harsh.jarvis.history.ActionHistory::class,
        com.harsh.jarvis.privacy.PrivacyAudit::class,
        com.harsh.jarvis.evolution.EvolutionProfile::class,
        com.harsh.jarvis.evolution.EvolutionSkill::class,
        com.harsh.jarvis.evolution.EvolutionQuest::class,
        com.harsh.jarvis.evolution.EvolutionGoal::class,
        com.harsh.jarvis.evolution.EvolutionAchievement::class,
        com.harsh.jarvis.evolution.EvolutionHistory::class,
        com.harsh.jarvis.evolution.EvolutionStreak::class,
        com.harsh.jarvis.evolution.EvolutionPerformance::class,
        com.harsh.jarvis.proactive.AvailabilityRule::class,
        com.harsh.jarvis.proactive.ProactiveEvent::class,
        com.harsh.jarvis.proactive.ProactiveFeedback::class,
        com.harsh.jarvis.proactive.ProactiveScheduleBlock::class
    ],
    version = 8,
    exportSchema = true
)
abstract class JarvisDatabase : RoomDatabase() {

    abstract fun taskDao(): TaskDao

    abstract fun memoryDao(): com.harsh.jarvis.memory.MemoryDao

    abstract fun actionHistoryDao(): com.harsh.jarvis.history.ActionHistoryDao

    abstract fun privacyAuditDao(): com.harsh.jarvis.privacy.PrivacyAuditDao
    abstract fun evolutionDao(): com.harsh.jarvis.evolution.EvolutionDao

    abstract fun proactiveDao(): com.harsh.jarvis.proactive.ProactiveDao

    companion object {
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS action_history (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, request TEXT NOT NULL, actionName TEXT NOT NULL, actionLevel TEXT NOT NULL, status TEXT NOT NULL, expected TEXT NOT NULL, actual TEXT NOT NULL, problem TEXT, cause TEXT, fix TEXT, verified INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE action_history ADD COLUMN lifecycle TEXT NOT NULL DEFAULT 'COMPLETED'")
                db.execSQL("ALTER TABLE action_history ADD COLUMN evidence TEXT")
                db.execSQL("ALTER TABLE action_history ADD COLUMN payloadJson TEXT NOT NULL DEFAULT '{}'")
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS memories (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, text TEXT NOT NULL, createdAt INTEGER NOT NULL)")
            }
        }


        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS evolution_profile (id INTEGER NOT NULL PRIMARY KEY, level INTEGER NOT NULL, totalXp INTEGER NOT NULL, updatedAt INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS evolution_skills (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, category TEXT NOT NULL, xp INTEGER NOT NULL, level INTEGER NOT NULL, active INTEGER NOT NULL, createdAt INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS evolution_quests (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT NOT NULL, description TEXT NOT NULL, skillId INTEGER, category TEXT NOT NULL, type TEXT NOT NULL, difficulty INTEGER NOT NULL, xpReward INTEGER NOT NULL, deadline INTEGER, status TEXT NOT NULL, notes TEXT NOT NULL, createdAt INTEGER NOT NULL, completedAt INTEGER)")
                db.execSQL("CREATE TABLE IF NOT EXISTS evolution_goals (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT NOT NULL, description TEXT NOT NULL, progress INTEGER NOT NULL, target INTEGER NOT NULL, active INTEGER NOT NULL, createdAt INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS evolution_achievements (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `key` TEXT NOT NULL, title TEXT NOT NULL, description TEXT NOT NULL, unlockedAt INTEGER NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_evolution_achievements_key ON evolution_achievements(`key`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS evolution_history (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, entityType TEXT NOT NULL, entityId INTEGER NOT NULL, fromLevel INTEGER NOT NULL, toLevel INTEGER NOT NULL, xpAwarded INTEGER NOT NULL, reason TEXT NOT NULL, createdAt INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS evolution_streaks (`key` TEXT NOT NULL PRIMARY KEY, current INTEGER NOT NULL, best INTEGER NOT NULL, lastActivityDay TEXT)")
                db.execSQL("CREATE TABLE IF NOT EXISTS evolution_performance (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, questId INTEGER NOT NULL, skillId INTEGER, difficulty INTEGER NOT NULL, completed INTEGER NOT NULL, quality REAL NOT NULL, recordedAt INTEGER NOT NULL)")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS proactive_availability (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, state TEXT NOT NULL, startMinute INTEGER NOT NULL, endMinute INTEGER NOT NULL, daysMask INTEGER NOT NULL, enabled INTEGER NOT NULL, criticalOverride INTEGER NOT NULL, label TEXT NOT NULL, createdAt INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS proactive_events (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, type TEXT NOT NULL, title TEXT NOT NULL, detail TEXT NOT NULL, priority TEXT NOT NULL, baseImportance REAL NOT NULL, urgency REAL NOT NULL, deadlineProximity REAL NOT NULL, relevance REAL NOT NULL, source TEXT NOT NULL, sourceId INTEGER, requiredAction TEXT NOT NULL, deadline INTEGER, createdAt INTEGER NOT NULL, status TEXT NOT NULL, snoozeUntil INTEGER, communicationState TEXT NOT NULL, communicationCount INTEGER NOT NULL, dedupeKey TEXT NOT NULL, dismissedForever INTEGER NOT NULL, manualPriority TEXT, lastEvaluatedAt INTEGER NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_proactive_events_dedupeKey ON proactive_events(dedupeKey)")
                db.execSQL("CREATE TABLE IF NOT EXISTS proactive_feedback (type TEXT NOT NULL PRIMARY KEY, snoozes INTEGER NOT NULL, dismissals INTEGER NOT NULL, follows INTEGER NOT NULL, updatedAt INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS proactive_schedule (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT NOT NULL, startTime INTEGER NOT NULL, endTime INTEGER NOT NULL, state TEXT NOT NULL, protected INTEGER NOT NULL)")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN estimatedMinutes INTEGER NOT NULL DEFAULT 45")
                db.execSQL("ALTER TABLE tasks ADD COLUMN completionPercent INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE tasks ADD COLUMN consequence REAL NOT NULL DEFAULT 0.5")
                db.execSQL("ALTER TABLE tasks ADD COLUMN goalPriority REAL NOT NULL DEFAULT 0.5")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS privacy_audit (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, capability TEXT NOT NULL, mode TEXT NOT NULL, purpose TEXT NOT NULL, dataExposed TEXT NOT NULL, outcome TEXT NOT NULL, createdAt INTEGER NOT NULL)")
            }
        }
        @Volatile
        private var INSTANCE: JarvisDatabase? = null

        fun get(context: Context): JarvisDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    JarvisDatabase::class.java,
                    "jarvis.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8).build().also { INSTANCE = it }
            }
    }
}
