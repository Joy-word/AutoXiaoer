package com.flowmate.autoxiaoer.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.flowmate.autoxiaoer.util.Logger
import java.util.Calendar

/**
 * Manages scheduling and canceling of scheduled tasks using AlarmManager.
 *
 * This class provides methods to schedule tasks at specific times and handle
 * repeating tasks by calculating the next execution time.
 */
class ScheduledTaskScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /**
     * Schedules a task to execute at the specified time.
     *
     * @param task The scheduled task to schedule
     * @return true if scheduling succeeded, false otherwise
     */
    fun scheduleTask(task: ScheduledTask): Boolean {
        if (!task.isEnabled) {
            Logger.d(TAG, "Task ${task.id} is disabled, skipping scheduling")
            return false
        }

        val triggerTime = task.scheduledTimeMillis
        if (triggerTime <= System.currentTimeMillis()) {
            Logger.w(TAG, "Task ${task.id} scheduled time is in the past, skipping")
            return false
        }

        try {
            val pendingIntent = createPendingIntent(task.id)
            
            // Use setExactAndAllowWhileIdle for precise timing even in doze mode
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                pendingIntent
            )

            Logger.i(TAG, "Scheduled task ${task.id} for ${formatTime(triggerTime)}")
            return true
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to schedule task ${task.id}", e)
            return false
        }
    }

    /**
     * Cancels a scheduled task.
     *
     * @param taskId The ID of the task to cancel
     */
    fun cancelTask(taskId: String) {
        try {
            val pendingIntent = createPendingIntent(taskId)
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            Logger.i(TAG, "Cancelled task $taskId")
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to cancel task $taskId", e)
        }
    }

    /**
     * Calculates the next execution time for a repeating task.
     *
     * @param task The scheduled task
     * @param fromTime The base time to calculate from (usually current time or last execution time)
     * @return The next execution time in milliseconds, or null if task doesn't repeat
     */
    fun calculateNextExecutionTime(task: ScheduledTask, fromTime: Long = System.currentTimeMillis()): Long? {
        return when (task.repeatType) {
            RepeatType.ONCE -> null

            RepeatType.DAILY -> {
                val calendar = Calendar.getInstance()
                calendar.timeInMillis = task.scheduledTimeMillis
                val hour = calendar.get(Calendar.HOUR_OF_DAY)
                val minute = calendar.get(Calendar.MINUTE)

                val nextCalendar = Calendar.getInstance()
                nextCalendar.timeInMillis = fromTime
                nextCalendar.set(Calendar.HOUR_OF_DAY, hour)
                nextCalendar.set(Calendar.MINUTE, minute)
                nextCalendar.set(Calendar.SECOND, 0)
                nextCalendar.set(Calendar.MILLISECOND, 0)

                // If the time has passed today, schedule for tomorrow
                if (nextCalendar.timeInMillis <= fromTime) {
                    nextCalendar.add(Calendar.DAY_OF_MONTH, 1)
                }

                nextCalendar.timeInMillis
            }

            RepeatType.WEEKDAYS -> {
                val calendar = Calendar.getInstance()
                calendar.timeInMillis = task.scheduledTimeMillis
                val hour = calendar.get(Calendar.HOUR_OF_DAY)
                val minute = calendar.get(Calendar.MINUTE)

                val nextCalendar = Calendar.getInstance()
                nextCalendar.timeInMillis = fromTime
                nextCalendar.set(Calendar.HOUR_OF_DAY, hour)
                nextCalendar.set(Calendar.MINUTE, minute)
                nextCalendar.set(Calendar.SECOND, 0)
                nextCalendar.set(Calendar.MILLISECOND, 0)

                // If the time has passed today, move to tomorrow
                if (nextCalendar.timeInMillis <= fromTime) {
                    nextCalendar.add(Calendar.DAY_OF_MONTH, 1)
                }

                // Skip weekends
                while (isWeekend(nextCalendar)) {
                    nextCalendar.add(Calendar.DAY_OF_MONTH, 1)
                }

                nextCalendar.timeInMillis
            }

            RepeatType.WEEKLY -> {
                val calendar = Calendar.getInstance()
                calendar.timeInMillis = task.scheduledTimeMillis
                val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
                val hour = calendar.get(Calendar.HOUR_OF_DAY)
                val minute = calendar.get(Calendar.MINUTE)

                val nextCalendar = Calendar.getInstance()
                nextCalendar.timeInMillis = fromTime
                nextCalendar.set(Calendar.DAY_OF_WEEK, dayOfWeek)
                nextCalendar.set(Calendar.HOUR_OF_DAY, hour)
                nextCalendar.set(Calendar.MINUTE, minute)
                nextCalendar.set(Calendar.SECOND, 0)
                nextCalendar.set(Calendar.MILLISECOND, 0)

                // If the time has passed this week, schedule for next week
                if (nextCalendar.timeInMillis <= fromTime) {
                    nextCalendar.add(Calendar.WEEK_OF_YEAR, 1)
                }

                nextCalendar.timeInMillis
            }
        }
    }

    /**
     * Checks if the given calendar represents a weekend day (Saturday or Sunday).
     */
    private fun isWeekend(calendar: Calendar): Boolean {
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        return dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY
    }

    /**
     * Creates a PendingIntent for the scheduled task.
     *
     * @param taskId The ID of the task
     * @return PendingIntent that will be triggered when the alarm fires
     */
    private fun createPendingIntent(taskId: String): PendingIntent {
        val intent = Intent(context, ScheduledTaskReceiver::class.java).apply {
            action = ACTION_EXECUTE_SCHEDULED_TASK
            putExtra(EXTRA_TASK_ID, taskId)
        }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        return PendingIntent.getBroadcast(
            context,
            taskId.hashCode(), // Use taskId hash as unique request code
            intent,
            flags
        )
    }

    /**
     * Formats a timestamp to a readable time string for logging.
     */
    private fun formatTime(timeMillis: Long): String {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timeMillis
        return java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
            .format(calendar.time)
    }

    companion object {
        private const val TAG = "ScheduledTaskScheduler"
        const val ACTION_EXECUTE_SCHEDULED_TASK = "com.flowmate.autoxiaoer.EXECUTE_SCHEDULED_TASK"
        const val EXTRA_TASK_ID = "task_id"
    }
}
