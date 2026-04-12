package com.flowmate.autoxiaoer.schedule

import android.content.Context
import android.content.SharedPreferences
import com.flowmate.autoxiaoer.util.Logger
import com.flowmate.autoxiaoer.util.ScreenKeepAliveManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Manages scheduled tasks with persistence using SharedPreferences (Singleton).
 *
 * This class provides methods to:
 * - Create, read, update, and delete scheduled tasks
 * - Persist tasks to SharedPreferences using JSON
 * - Automatically schedule/cancel tasks with AlarmManager when tasks are modified
 * - Expose a StateFlow for UI observation
 */
class ScheduledTaskManager private constructor(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val scheduler = ScheduledTaskScheduler(context)

    // StateFlow for observing task list changes
    private val _tasks = MutableStateFlow<List<ScheduledTask>>(emptyList())
    val tasks: StateFlow<List<ScheduledTask>> = _tasks.asStateFlow()

    init {
        // Load tasks from storage on initialization
        _tasks.value = loadTasksFromStorage()
        
        // Notify ScreenKeepAliveManager about scheduled tasks
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
            // Cancel old alarm if it exists
            scheduler.cancelTask(task.id)
            currentTasks[existingIndex] = task
        } else {
            currentTasks.add(task)
        }

        // Schedule the task if it's enabled
        if (task.isEnabled) {
            scheduler.scheduleTask(task)
        }

        // Save to storage and update StateFlow
        saveTasksToStorage(currentTasks)
        _tasks.value = currentTasks
        
        // Update screen keep-alive state
        updateScreenKeepAliveState()
    }

    /**
     * Deletes a scheduled task.
     *
     * @param taskId The ID of the task to delete
     */
    fun deleteTask(taskId: String) {
        Logger.d(TAG, "Deleting scheduled task: id=$taskId")
        
        // Cancel the alarm
        scheduler.cancelTask(taskId)

        val currentTasks = _tasks.value.filter { it.id != taskId }
        saveTasksToStorage(currentTasks)
        _tasks.value = currentTasks
        
        // Update screen keep-alive state
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
            
            // Update screen keep-alive state
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
            // For one-time tasks, disable them after execution
            updateTaskEnabled(taskId, false)
            return
        }

        // Calculate next execution time
        val nextExecutionTime = scheduler.calculateNextExecutionTime(task)
        if (nextExecutionTime != null) {
            val updatedTask = task.copy(scheduledTimeMillis = nextExecutionTime)
            saveTask(updatedTask)
            Logger.i(TAG, "Rescheduled task $taskId for next execution")
        }
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
     * Loads tasks from SharedPreferences storage.
     *
     * @return List of tasks loaded from storage, empty list if parsing fails
     */
    private fun loadTasksFromStorage(): List<ScheduledTask> {
        val json = prefs.getString(KEY_SCHEDULED_TASKS, null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
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
                    }
                )
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to parse scheduled tasks", e)
            emptyList()
        }
    }

    /**
     * Saves tasks to SharedPreferences storage.
     *
     * @param tasks The list of tasks to save
     */
    private fun saveTasksToStorage(tasks: List<ScheduledTask>) {
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
        prefs.edit().putString(KEY_SCHEDULED_TASKS, array.toString()).apply()
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
        private const val PREFS_NAME = "scheduled_tasks"
        private const val KEY_SCHEDULED_TASKS = "tasks"

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
