package com.harsh.jarvis.tasks

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.harsh.jarvis.alarm.AlarmScheduler
import com.harsh.jarvis.memory.MemoryRepository
import com.harsh.jarvis.actions.ActionResult
import com.harsh.jarvis.actions.ActionStatus
import com.harsh.jarvis.actions.JarvisAction
import com.harsh.jarvis.history.ActionHistoryRepository
import com.harsh.jarvis.security.ActionLevel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class JarvisViewModel(application: Application) : AndroidViewModel(application) {
    private val db = JarvisDatabase.get(application)
    private val dao = db.taskDao()
    private val alarms = AlarmScheduler(application)
    private val memoryRepository = MemoryRepository(db.memoryDao())
    private val historyRepository = ActionHistoryRepository(db.actionHistoryDao())

    val memories = memoryRepository.observeAll().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList()
    )

    val tasks = dao.observeAll().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList()
    )

    suspend fun addTask(title: String, description: String = "", dueTime: Long? = null): Task {
        val id = dao.insert(Task(title = title, description = description, dueTime = dueTime))
        val task = Task(id = id, title = title, description = description, dueTime = dueTime)
        if (dueTime != null) alarms.schedule(task)
        return task
    }

    suspend fun findTask(id: Long): Task? = dao.findById(id)

    fun addTaskAsync(title: String, description: String = "", dueTime: Long? = null) {
        viewModelScope.launch { addTask(title, description, dueTime) }
    }

    fun completeTask(id: Long) {
        viewModelScope.launch {
            val task = dao.findById(id) ?: return@launch
            dao.complete(id)
            val observed = dao.findById(id)
            val result = if (observed?.completed == true) {
                ActionResult(
                    ActionStatus.SUCCESS,
                    "Task '${task.title}' is completed",
                    "Completed '${task.title}' from the Tasks screen.",
                    verified = true
                )
            } else {
                ActionResult(
                    ActionStatus.FAILED,
                    "Task '${task.title}' is completed",
                    "The task is not marked completed after the update.",
                    problem = "Completion could not be verified.",
                    fix = "Try marking the task complete again."
                )
            }
            val action = JarvisAction(
                name = "complete_task_ui",
                description = "complete the task '${task.title}' from the Tasks screen",
                level = ActionLevel.SAFE,
                execute = { result }
            )
            historyRepository.record("UI: complete task '${task.title}'", action, result)
        }
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch {
            dao.delete(task)
            alarms.cancel(task)
            val stillExists = dao.findById(task.id) != null
            val result = if (!stillExists) {
                ActionResult(
                    ActionStatus.SUCCESS,
                    "Task '${task.title}' no longer exists",
                    "Deleted '${task.title}' from the Tasks screen.",
                    verified = true
                )
            } else {
                ActionResult(
                    ActionStatus.FAILED,
                    "Task '${task.title}' no longer exists",
                    "The task is still present after deletion.",
                    problem = "UI deletion could not be verified.",
                    fix = "Try deleting the task again."
                )
            }
            val action = JarvisAction(
                name = "delete_task_ui",
                description = "delete the task '${task.title}' from the Tasks screen",
                level = ActionLevel.SAFE,
                execute = { result }
            )
            historyRepository.record("UI: delete task '${task.title}'", action, result)
        }
    }

    suspend fun deleteTaskAndVerify(task: Task): com.harsh.jarvis.actions.ActionResult {
        dao.delete(task)
        alarms.cancel(task)
        val stillExists = dao.findById(task.id) != null
        return if (!stillExists) {
            com.harsh.jarvis.actions.ActionResult(
                com.harsh.jarvis.actions.ActionStatus.SUCCESS,
                "Task '${task.title}' no longer exists",
                "Deleted '${task.title}'.",
                verified = true
            )
        } else {
            com.harsh.jarvis.actions.ActionResult(
                com.harsh.jarvis.actions.ActionStatus.FAILED,
                "Task '${task.title}' no longer exists",
                "The task is still present after deletion.",
                problem = "Deletion could not be verified in the task store.",
                fix = "Try deleting the task again."
            )
        }
    }

    fun currentTasks(): List<Task> = tasks.value.filter { !it.completed }.sortedByDescending { it.id }

    suspend fun saveMemoryAndVerify(text: String): Long? {
        val id = memoryRepository.save(text) ?: return null
        return if (memoryRepository.findById(id) != null) id else null
    }

    fun saveMemory(text: String) {
        viewModelScope.launch { saveMemoryAndVerify(text) }
    }
}
