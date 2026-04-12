package com.flowmate.autoxiaoer.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.flowmate.autoxiaoer.task.TaskExecutionManager
import com.flowmate.autoxiaoer.task.TriggerContext
import com.flowmate.autoxiaoer.task.TriggerType
import com.flowmate.autoxiaoer.util.Logger

/**
 * BroadcastReceiver that handles execution of scheduled tasks when AlarmManager triggers.
 *
 * This receiver is triggered by AlarmManager at the scheduled time and:
 * - Retrieves the task details from ScheduledTaskManager
 * - Checks if another task is currently running
 * - Executes the task if possible, or skips it if a task is already running
 * - For repeating tasks, reschedules them for the next execution
 * - For one-time tasks, disables them after execution
 */
class ScheduledTaskReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ScheduledTaskScheduler.ACTION_EXECUTE_SCHEDULED_TASK) {
            return
        }

        val taskId = intent.getStringExtra(ScheduledTaskScheduler.EXTRA_TASK_ID)
        if (taskId == null) {
            Logger.w(TAG, "Received scheduled task intent without task ID")
            return
        }

        Logger.i(TAG, "Received scheduled task execution intent for task: $taskId")

        // Get the task from ScheduledTaskManager
        val taskManager = ScheduledTaskManager.getInstance(context)
        val task = taskManager.getTaskById(taskId)

        if (task == null) {
            Logger.w(TAG, "Task $taskId not found in ScheduledTaskManager")
            return
        }

        if (!task.isEnabled) {
            Logger.d(TAG, "Task $taskId is disabled, skipping execution")
            return
        }

        // Check if another task is currently running
        if (TaskExecutionManager.isTaskRunning()) {
            Logger.w(TAG, "Another task is currently running, skipping scheduled task $taskId")
            // For repeating tasks, reschedule for next execution
            if (task.repeatType != RepeatType.ONCE) {
                taskManager.rescheduleTask(taskId)
            }
            return
        }

        // Check if Shizuku is connected (required for task execution)
        val blockReason = TaskExecutionManager.getStartTaskBlockReason()
        if (blockReason != TaskExecutionManager.StartTaskBlockReason.NONE) {
            Logger.w(TAG, "Cannot execute scheduled task $taskId: $blockReason")
            // For repeating tasks, reschedule for next execution
            if (task.repeatType != RepeatType.ONCE) {
                taskManager.rescheduleTask(taskId)
            }
            return
        }

        Logger.i(TAG, "Executing scheduled task: ${task.taskDescription.take(50)}...")

        // Execute the task
        // Note: Both FloatingWindowStateManager.onTaskStarted() and ScreenKeepAliveManager.onTaskStarted()
        // are now called automatically by TaskExecutionManager when task state changes to RUNNING
        val triggerContext = TriggerContext(
            triggerType = TriggerType.SCHEDULED,
            scheduledTaskBackground = task.taskBackground,
        )
        val success = TaskExecutionManager.startTask(task.taskDescription, triggerContext)
        
        if (success) {
            // Update last executed timestamp
            taskManager.updateLastExecutedAt(taskId, System.currentTimeMillis())

            // Handle task rescheduling based on repeat type
            if (task.repeatType == RepeatType.ONCE) {
                // For one-time tasks, disable after execution
                taskManager.updateTaskEnabled(taskId, false)
                Logger.i(TAG, "One-time task $taskId executed, disabled")
            } else {
                // For repeating tasks, schedule the next execution
                taskManager.rescheduleTask(taskId)
                Logger.i(TAG, "Repeating task $taskId executed, rescheduled for next execution")
            }
        } else {
            Logger.e(TAG, "Failed to start scheduled task $taskId")
            // For repeating tasks, still reschedule for next execution
            if (task.repeatType != RepeatType.ONCE) {
                taskManager.rescheduleTask(taskId)
            }
        }
    }

    companion object {
        private const val TAG = "ScheduledTaskReceiver"
    }
}
