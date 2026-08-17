package com.harsh.jarvis.tools

import android.content.Context
import android.content.Intent
import com.harsh.jarvis.actions.ActionResult
import com.harsh.jarvis.actions.ActionStatus
import com.harsh.jarvis.actions.JarvisAction
import com.harsh.jarvis.security.ActionLevel
import com.harsh.jarvis.security.PermissionManager
import com.harsh.jarvis.tasks.JarvisViewModel
import com.harsh.jarvis.time.ReminderParser
import com.harsh.jarvis.verification.AppLaunchVerifier
import com.harsh.jarvis.privacy.PrivacyCapability
import com.harsh.jarvis.privacy.PrivacyGateway

/** Android capability layer. Brain never executes Android APIs directly. */
class ToolRegistry(
    private val context: Context,
    private val tasks: JarvisViewModel,
    private val permissions: PermissionManager,
    private val privacy: PrivacyGateway
) {
    data class InstalledApp(val label: String, val packageName: String)
    private val reminderParser = ReminderParser()
    private val appLaunchVerifier = AppLaunchVerifier(context)

    fun availableApps(): List<InstalledApp> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return context.packageManager.queryIntentActivities(intent, 0)
            .map { InstalledApp(it.loadLabel(context.packageManager).toString(), it.activityInfo.packageName) }
            .distinctBy { it.packageName }
            .filter { it.packageName != context.packageName }
            .sortedBy { it.label.lowercase() }
    }

    fun isAllowed(packageName: String) = permissions.isAppAllowed(packageName) && !privacy.isBlocked(PrivacyCapability.APP_CONTROL)
    fun setAllowed(packageName: String, allowed: Boolean) = permissions.setAppAllowed(packageName, allowed)

    fun createReminderAction(command: String): JarvisAction {
        val parsed = reminderParser.parse(command)
        val title = parsed.title
        val expected = if (parsed.time == null) "A task named '$title' exists" else "A reminder named '$title' exists at ${parsed.time.description}"
        return JarvisAction(
            name = "create_reminder",
            description = if (parsed.time == null) "create the task '$title'" else "create the reminder '$title' ${parsed.time.description}",
            level = ActionLevel.SAFE,
            payload = mapOf("title" to title, "dueTime" to (parsed.time?.dueTime?.toString() ?: "")),
            execute = {
                val task = tasks.addTask(title, dueTime = parsed.time?.dueTime)
                val observed = tasks.findTask(task.id)
                val verified = observed?.id == task.id && observed.title == title && observed.dueTime == parsed.time?.dueTime && !observed.completed
                if (verified) ActionResult(ActionStatus.SUCCESS, expected, "Created '$title'${parsed.time?.let { " and scheduled it ${it.description}" } ?: ""}.", verified = true)
                else ActionResult(ActionStatus.FAILED, expected, "The task insertion was not visible after creation.", "The reminder could not be verified in the task store.", "The database state did not reflect the inserted task.", "Try creating the reminder again.")
            }
        )
    }

    fun deleteTaskAction(command: String): Pair<JarvisAction?, String?> {
        val current = tasks.currentTasks()
        if (current.isEmpty()) return null to "You have no active tasks to delete."

        val query = command.replace(
            Regex("^.*?\\b(delete|remove)\\s+(?:the\\s+)?(?:task|reminder|todo|to-do)\\s*", RegexOption.IGNORE_CASE),
            ""
        ).trim()

        val candidates = if (query.isBlank() || query.equals("last", true) || query.equals("latest", true)) {
            listOf(current.first())
        } else {
            current.filter { it.title.contains(query, ignoreCase = true) }
        }

        if (candidates.isEmpty()) {
            return null to "I couldn't find an active task matching '$query'. Say 'show my tasks' to see the exact names."
        }
        if (candidates.size > 1) {
            val names = candidates.take(5).joinToString("; ") { "'${it.title}'" }
            return null to "I found multiple matching tasks: $names. Please include more of the task name."
        }

        val target = candidates.single()
        return JarvisAction(
            name = "delete_task",
            description = "delete the task '${target.title}'",
            level = ActionLevel.CONFIRM,
            payload = mapOf("taskId" to target.id.toString(), "title" to target.title),
            execute = { tasks.deleteTaskAndVerify(target) }
        ) to null
    }

    fun openAppAction(command: String): Pair<JarvisAction?, String?> {
        val requested = command.lowercase()
            .replace(Regex("\\b(open|launch|start|run|please)\\b"), "")
            .trim()
        if (requested.isBlank()) return null to "Tell me which app you want to open."

        val app = availableApps().firstOrNull { requested.contains(it.label.lowercase()) || it.label.lowercase().contains(requested) }
            ?: return null to "I couldn't find an installed app matching '$requested'."

        if (privacy.isBlocked(PrivacyCapability.APP_CONTROL)) {
            return null to "App control is blocked by your JARVIS Privacy policy."
        }
        if (!permissions.isAppAllowed(app.packageName)) {
            return null to "${app.label} is installed, but JARVIS is not allowed to open it. Enable it in App Access first."
        }

        return JarvisAction(
            name = "open_app",
            description = "open ${app.label}",
            level = if (privacy.requiresUserApproval(PrivacyCapability.APP_CONTROL)) ActionLevel.CONFIRM else ActionLevel.SAFE,
            allowed = { permissions.isAppAllowed(app.packageName) && !privacy.isBlocked(PrivacyCapability.APP_CONTROL) },
            payload = mapOf("packageName" to app.packageName, "label" to app.label),
            execute = {
                val launch = context.packageManager.getLaunchIntentForPackage(app.packageName)
                    ?: return@JarvisAction ActionResult(ActionStatus.FAILED, "Android accepts a launch request for ${app.label}", "No launch intent is available for ${app.label}.", "Android could not find a launch activity.", "The app may not expose a launcher activity.", "Open the app manually or choose another app.")
                try {
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launch)
                    val installed = permissions.isAppInstalled(app.packageName)
                    val stillLaunchable = context.packageManager.getLaunchIntentForPackage(app.packageName) != null
                    if (installed && stillLaunchable) {
                        val observation = appLaunchVerifier.verifyForeground(app.packageName)
                        if (observation.verified) {
                            ActionResult(
                                ActionStatus.SUCCESS,
                                "${app.label} is in the foreground",
                                "Opened ${app.label} and independently verified it became the most recently used app.",
                                verified = true
                            )
                        } else if (!observation.available) {
                            ActionResult(
                                ActionStatus.PARTIAL,
                                "${app.label} is in the foreground",
                                "Android accepted the launch request for ${app.label}.",
                                problem = "Foreground state could not be independently verified.",
                                cause = observation.evidence,
                                fix = "Enable Usage Access for JARVIS in Android Settings, then try again."
                            )
                        } else {
                            ActionResult(
                                ActionStatus.FAILED,
                                "${app.label} is in the foreground",
                                "${app.label} did not appear as the most recently used app.",
                                problem = "The target app was not independently observed in the foreground.",
                                cause = observation.evidence,
                                fix = "Check whether Android blocked the launch and try again."
                            )
                        }
                    } else {
                        ActionResult(
                            ActionStatus.FAILED,
                            "${app.label} is launchable",
                            "Android accepted the request, but the app is no longer launchable.",
                            problem = "${app.label} could not be verified as launchable.",
                            cause = "The package is unavailable or its launch activity changed.",
                            fix = "Check that the app is installed and enabled, then try again."
                        )
                    }
                } catch (t: Throwable) {
                    ActionResult(ActionStatus.FAILED, "${app.label} launches", "Android rejected the launch.", "${app.label} could not be opened.", t.message ?: t::class.simpleName, "Check whether the app is enabled and try again.")
                }
            }
        ) to null
    }

    /**
     * Rebuilds an action from stable structured payload persisted in history.
     * Retry never parses the original English request.
     */
    fun rebuildAction(name: String, payload: Map<String, String>): JarvisAction? = when (name) {
        "create_reminder" -> {
            val title = payload["title"]?.takeIf { it.isNotBlank() } ?: return null
            val due = payload["dueTime"]?.takeIf { it.isNotBlank() }?.toLongOrNull()
            JarvisAction(
                name = "create_reminder",
                description = "create the task '$title'",
                level = ActionLevel.SAFE,
                payload = payload,
                execute = {
                    val task = tasks.addTask(title, dueTime = due)
                    val observed = tasks.findTask(task.id)
                    if (observed?.id == task.id && observed.title == title && observed.dueTime == due && !observed.completed)
                        ActionResult(ActionStatus.SUCCESS, "Task '$title' exists", "Recreated '$title'.", verified = true, evidence = "Task read-back matched persisted payload.")
                    else ActionResult(ActionStatus.FAILED, "Task '$title' exists", "The recreated task could not be verified.", problem = "Read-back did not match the stored action parameters.", fix = "Try the action again.")
                }
            )
        }
        "open_app" -> {
            val pkg = payload["packageName"] ?: return null
            val label = payload["label"] ?: pkg
            val launch = context.packageManager.getLaunchIntentForPackage(pkg) ?: return null
            if (privacy.isBlocked(PrivacyCapability.APP_CONTROL)) return null
            if (!permissions.isAppAllowed(pkg)) return null
            JarvisAction(
                name = "open_app", description = "open $label",
                level = if (privacy.requiresUserApproval(PrivacyCapability.APP_CONTROL)) ActionLevel.CONFIRM else ActionLevel.SAFE,
                allowed = { permissions.isAppAllowed(pkg) && !privacy.isBlocked(PrivacyCapability.APP_CONTROL) }, payload = payload,
                execute = {
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launch)
                    val observation = appLaunchVerifier.verifyForeground(pkg)
                    if (observation.verified) ActionResult(ActionStatus.SUCCESS, "$label is in the foreground", "Opened $label.", verified = true, evidence = observation.evidence)
                    else ActionResult(ActionStatus.PARTIAL, "$label is in the foreground", "Android accepted the launch request for $label.", problem = "Foreground state could not be verified.", cause = observation.evidence, fix = "Enable Usage Access and try again.", evidence = observation.evidence)
                }
            )
        }
        "save_memory" -> {
            val text = payload["text"]?.takeIf { it.isNotBlank() } ?: return null
            JarvisAction(
                name="save_memory", description="save the memory '$text'",
                level=if (privacy.requiresUserApproval(PrivacyCapability.JARVIS_MEMORY)) ActionLevel.CONFIRM else ActionLevel.SAFE,
                payload=payload,
                execute={
                    val id = tasks.saveMemoryAndVerify(text)
                    if (id != null) ActionResult(ActionStatus.SUCCESS, "Memory exists in persistent storage", "Saved that memory.", verified=true, evidence="Memory was read back after insertion.")
                    else ActionResult(ActionStatus.FAILED, "Memory exists in persistent storage", "Memory could not be verified.", problem="The memory write was not confirmed.", fix="Try saving it again.")
                }
            )
        }
        else -> null
    }

    suspend fun showTasks(): String {
        val current = tasks.currentTasks()
        return if (current.isEmpty()) "You have no active tasks."
        else current.joinToString(prefix = "You have ${current.size} active task${if (current.size == 1) "" else "s"}: ", separator = "; ") { it.title }
    }
}
