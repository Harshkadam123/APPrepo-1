package com.harsh.jarvis.proactive

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.harsh.jarvis.evolution.EvolutionRepository
import com.harsh.jarvis.tasks.JarvisDatabase
import com.harsh.jarvis.tools.PersonalDataTools

/**
 * Runs the expensive proactive refresh outside BroadcastReceiver lifetime limits.
 * WorkManager persists/retries this work across process death and reboot.
 */
class ProactiveWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        return try {
            val db = JarvisDatabase.get(context)
            val personalData = PersonalDataTools(
                context,
                com.harsh.jarvis.privacy.PrivacyGateway(
                    com.harsh.jarvis.privacy.PrivacyPolicyStore(context),
                    com.harsh.jarvis.privacy.PrivacyAuditRepository(db.privacyAuditDao())
                )
            )
            val engine = ProactiveEngine(
                db.proactiveDao(),
                db.taskDao(),
                EvolutionRepository(db.evolutionDao()),
                personalData
            )
            val events = engine.refresh()
            events.filter { engine.communicationDecision(it) }.forEach {
                ProactiveNotification.show(context, it)
                engine.markCommunicated(it.id)
            }

            when (inputData.getString(KEY_KIND)) {
                KIND_MORNING -> {
                    val prefs = ProactivePreferences(context)
                    if (prefs.morningEnabled && prefs.shouldRun("morning")) {
                        ProactiveNotification.show(
                            context,
                            ProactiveEvent(
                                id = 2800001,
                                type = "BRIEFING",
                                title = "JARVIS Morning Briefing",
                                detail = engine.dailyBriefing(),
                                source = "PROACTIVE",
                                dedupeKey = "briefing-${java.time.LocalDate.now()}"
                            )
                        )
                        prefs.markRun("morning")
                    }
                }
                KIND_EVENING -> {
                    val prefs = ProactivePreferences(context)
                    if (prefs.eveningEnabled && prefs.shouldRun("evening")) {
                        ProactiveNotification.show(
                            context,
                            ProactiveEvent(
                                id = 2800002,
                                type = "REVIEW",
                                title = "JARVIS Evening Review",
                                detail = engine.eveningReview(),
                                source = "PROACTIVE",
                                dedupeKey = "review-${java.time.LocalDate.now()}"
                            )
                        )
                        prefs.markRun("evening")
                    }
                }
            }
            ProactiveScheduler.scheduleDailyAlarms(context)
            Result.success()
        } catch (_: Throwable) {
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val KEY_KIND = "kind"
        const val KIND_PERIODIC = "periodic"
        const val KIND_MORNING = "morning"
        const val KIND_EVENING = "evening"
    }
}
