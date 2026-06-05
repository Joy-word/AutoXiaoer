package com.flowmate.autoxiaoer.schedule

import android.content.Context
import com.flowmate.autoxiaoer.util.Logger
import com.flowmate.autoxiaoer.util.ScreenKeepAliveManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Manages scheduled tasks with persistence using file storage (Singleton).
 *
 * Storage layout under `context.filesDir/scheduled_tasks/`:
 * ```
 * scheduled_tasks/
 * └── tasks.json
 * ```
 *
 * This class provides methods to:
 * - Create, read, update, and delete scheduled tasks
 * - Persist tasks to JSON file
 * - Automatically schedule/cancel tasks with AlarmManager when tasks are modified
 * - Expose a StateFlow for UI observation
 */
class ScheduledTaskManager private constructor(private val context: Context) {
    private val scheduler = ScheduledTaskScheduler(context)
    private val storageFile: File = File(File(context.filesDir, DIR_NAME), TASKS_FILE)

    // StateFlow for observing task list changes
    private val _tasks = MutableStateFlow<List<ScheduledTask>>(emptyList())
    val tasks: StateFlow<List<ScheduledTask>> = _tasks.asStateFlow()

    init {
        migrateFromLegacyPrefsIfNeeded()
        _tasks.value = loadTasksFromStorage()
        updateScreenKeepAliveState()
    }

    /**
     * Gets all scheduled tasks.
     *
     * @return List of all scheduled tasks
     */
    fun getAllTasks(): List<ScheduledTask> = _tasks.value

    /**
     * Gets a task by its ID.
     *
     * @param taskId The ID of the task to retrieve
     * @return The task if found, null otherwise
     */
    fun getTaskById(taskId: String): ScheduledTask? = _tasks.value.find { it.id == taskId }

    /**
     * Saves a new scheduled task or updates an existing one.
     *
     * If a task with the same ID exists, it will be updated.
     * Otherwise, a new task will be added.
     *
     * @param task The task to save
     */
    fun saveTask(task: ScheduledTask) {
        Logger.d(TAG, "Saving scheduled task: id=${task.id}, description=${task.taskDescription.take(30)}")

        val currentTasks = _tasks.value.toMutableList()
        val existingIndex = currentTasks.indexOfFirst { it.id == task.id }

        if (existingIndex >= 0) {
            scheduler.cancelTask(task.id)
            currentTasks[existingIndex] = task
        } else {
            currentTasks.add(task)
        }

        if (task.isEnabled) {
            scheduler.scheduleTask(task)
        }

        saveTasksToStorage(currentTasks)
        _tasks.value = currentTasks
        updateScreenKeepAliveState()
    }

    /**
     * Deletes a scheduled task.
     *
     * @param taskId The ID of the task to delete
     */
    fun deleteTask(taskId: String) {
        Logger.d(TAG, "Deleting scheduled task: id=$taskId")

        scheduler.cancelTask(taskId)

        val currentTasks = _tasks.value.filter { it.id != taskId }
        saveTasksToStorage(currentTasks)
        _tasks.value = currentTasks
        updateScreenKeepAliveState()
    }

    /**
     * Updates the enabled state of a task.
     *
     * @param taskId The ID of the task to update
     * @param enabled Whether the task should be enabled
     */
    fun updateTaskEnabled(taskId: String, enabled: Boolean) {
        Logger.d(TAG, "Updating task enabled state: id=$taskId, enabled=$enabled")

        val currentTasks = _tasks.value.toMutableList()
        val taskIndex = currentTasks.indexOfFirst { it.id == taskId }

        if (taskIndex >= 0) {
            val task = currentTasks[taskIndex]
            val updatedTask = task.copy(isEnabled = enabled)
            currentTasks[taskIndex] = updatedTask

            if (enabled) {
                scheduler.scheduleTask(updatedTask)
            } else {
                scheduler.cancelTask(taskId)
            }

            saveTasksToStorage(currentTasks)
            _tasks.value = currentTasks
            updateScreenKeepAliveState()
        }
    }

    /**
     * Updates the last executed timestamp of a task.
     *
     * @param taskId The ID of the task
     * @param executedAt The timestamp when the task was executed
     */
    fun updateLastExecutedAt(taskId: String, executedAt: Long) {
        Logger.d(TAG, "Updating task last executed time: id=$taskId")

        val currentTasks = _tasks.value.toMutableList()
        val taskIndex = currentTasks.indexOfFirst { it.id == taskId }

        if (taskIndex >= 0) {
            val task = currentTasks[taskIndex]
            val updatedTask = task.copy(lastExecutedAt = executedAt)
            currentTasks[taskIndex] = updatedTask

            saveTasksToStorage(currentTasks)
            _tasks.value = currentTasks
        }
    }

    /**
     * Reschedules a task for its next execution time (for repeating tasks).
     *
     * @param taskId The ID of the task to reschedule
     */
    fun rescheduleTask(taskId: String) {
        val task = getTaskById(taskId) ?: return

        if (task.repeatType == RepeatType.ONCE) {
            updateTaskEnabled(taskId, false)
            return
        }

        val nextExecutionTime = scheduler.calculateNextExecutionTime(task)
        if (nextExecutionTime != null) {
            val updatedTask = task.copy(scheduledTimeMillis = nextExecutionTime)
            saveTask(updatedTask)
            Logger.i(TAG, "Rescheduled task $taskId for next execution")
        }
    }

    /**
     * Reloads tasks from storage after an external data import.
     *
     * Cancels existing alarms, reloads persisted tasks, and reschedules enabled ones.
     */
    fun reloadAfterImport() {
        _tasks.value.forEach { scheduler.cancelTask(it.id) }
        val tasks = loadTasksFromStorage()
        _tasks.value = tasks
        rescheduleAllEnabledTasks()
        updateScreenKeepAliveState()
        Logger.i(TAG, "Reloaded ${tasks.size} scheduled tasks after import")
    }

    /**
     * Reschedules all enabled tasks.
     *
     * This should be called on app startup and after device boot to ensure
     * all alarms are properly registered with the system.
     */
    fun rescheduleAllEnabledTasks() {
        Logger.i(TAG, "Rescheduling all enabled tasks")

        val enabledTasks = _tasks.value.filter { it.isEnabled }
        for (task in enabledTasks) {
            scheduler.scheduleTask(task)
        }
    }

    /**
     * Generates a unique task ID.
     *
     * @return A unique task ID based on current timestamp
     */
    fun generateTaskId(): String = "scheduled_${System.currentTimeMillis()}"

    /**
     * One-time migration: copies legacy SharedPreferences data into file storage.
     */
    private fun migrateFromLegacyPrefsIfNeeded() {
        if (storageFile.exists()) return

        val legacyPrefs = context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
        val json = legacyPrefs.getString(LEGACY_KEY_TASKS, null) ?: return

        try {
            storageFile.parentFile?.mkdirs()
            storageFile.writeText(json)
            legacyPrefs.edit().clear().apply()
            Logger.i(TAG, "Migrated scheduled tasks from legacy prefs to file")
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to migrate scheduled tasks from legacy prefs", e)
        }
    }

    /**
     * Loads tasks from file storage.
     *
     * @return List of tasks loaded from storage, empty list if parsing fails
     */
    private fun loadTasksFromStorage(): List<ScheduledTask> {
        if (!storageFile.exists()) return emptyList()
        return try {
            parseTasksJson(storageFile.readText())
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to load scheduled tasks from file", e)
            emptyList()
        }
    }

    /**
     * Saves tasks to file storage.
     *
     * @param tasks The list of tasks to save
     */
    private fun saveTasksToStorage(tasks: List<ScheduledTask>) {
        try {
            storageFile.parentFile?.mkdirs()
            storageFile.writeText(encodeTasksJson(tasks))
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to save scheduled tasks to file", e)
        }
    }

    private fun parseTasksJson(json: String): List<ScheduledTask> {
        val array = JSONArray(json)
        return (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            ScheduledTask(
                id = obj.getString("id"),
                taskDescription = obj.getString("taskDescription"),
                taskBackground = if (obj.has("taskBackground") && !obj.isNull("taskBackground")) {
                    obj.getString("taskBackground").takeIf { it.isNotBlank() }
                } else {
                    null
                },
                scheduledTimeMillis = obj.getLong("scheduledTimeMillis"),
                repeatType = RepeatType.valueOf(obj.getString("repeatType")),
                isEnabled = obj.getBoolean("isEnabled"),
                createdAt = obj.getLong("createdAt"),
                lastExecutedAt = if (obj.has("lastExecutedAt") && !obj.isNull("lastExecutedAt")) {
                    obj.getLong("lastExecutedAt")
                } else {
                    null
                },
            )
        }
    }

    private fun encodeTasksJson(tasks: List<ScheduledTask>): String {
        val array = JSONArray()
        tasks.forEach { task ->
            val obj = JSONObject().apply {
                put("id", task.id)
                put("taskDescription", task.taskDescription)
                put("taskBackground", task.taskBackground)
                put("scheduledTimeMillis", task.scheduledTimeMillis)
                put("repeatType", task.repeatType.name)
                put("isEnabled", task.isEnabled)
                put("createdAt", task.createdAt)
                put("lastExecutedAt", task.lastExecutedAt)
            }
            array.put(obj)
        }
        return array.toString()
    }

    /**
     * Updates the screen keep-alive state based on current scheduled tasks.
     * If there are any enabled scheduled tasks, keeps device unlocked.
     */
    private fun updateScreenKeepAliveState() {
        val hasEnabledTasks = _tasks.value.any { it.isEnabled }
        ScreenKeepAliveManager.onScheduledTasksChanged(context, hasEnabledTasks)
    }

    companion object {
        private const val TAG = "ScheduledTaskManager"
        const val DIR_NAME = "scheduled_tasks"
        const val TASKS_FILE = "tasks.json"
        private const val LEGACY_PREFS_NAME = "scheduled_tasks"
        private const val LEGACY_KEY_TASKS = "tasks"

        @Volatile
        private var instance: ScheduledTaskManager? = null

        /**
         * Gets the ScheduledTaskManager singleton instance.
         *
         * @param context Android context (will be converted to ApplicationContext)
         * @return ScheduledTaskManager singleton instance
         */
        fun getInstance(context: Context): ScheduledTaskManager {
            return instance ?: synchronized(this) {
                instance ?: ScheduledTaskManager(context.applicationContext).also {
                    instance = it
                    Logger.d(TAG, "ScheduledTaskManager singleton instance created")
                }
            }
        }
    }
}
