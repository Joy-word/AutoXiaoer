package com.flowmate.autoxiaoer.util

import android.content.Context
import android.os.PowerManager
import androidx.core.content.getSystemService

/**
 * Manages screen wake locks to keep the screen alive during task execution
 * and to maintain device wakefulness when scheduled tasks are pending.
 *
 * This manager handles two types of wake locks:
 * - **Task wake lock**: Keeps the screen bright during task execution
 *   (acquired via [onTaskStarted], released via [onTaskCompleted])
 * - **Scheduled task wake lock**: Keeps the CPU awake when there are enabled
 *   scheduled tasks waiting to execute (managed via [onScheduledTasksChanged])
 *
 * Usage:
 * - Call [initialize] in Application.onCreate()
 * - Call [onTaskStarted] when a task begins execution
 * - Call [onTaskCompleted] when a task finishes (completed or failed)
 * - Call [onScheduledTasksChanged] when scheduled tasks are added/removed/toggled
 * - Call [releaseAll] when the application terminates
 */
@Suppress("DEPRECATION")
object ScreenKeepAliveManager {
    private const val TAG = "ScreenKeepAliveManager"
    private const val TASK_WAKELOCK_TAG = "AutoGLM:ScreenKeepAlive"
    private const val SCHEDULED_WAKELOCK_TAG = "AutoGLM:ScheduledTaskKeepAlive"
    private const val TASK_WAKELOCK_TIMEOUT = 30 * 60 * 1000L // 30 minutes
    private const val SCHEDULED_WAKELOCK_TIMEOUT = 24 * 60 * 60 * 1000L // 24 hours

    private var applicationContext: Context? = null

    // Wake lock to keep screen bright during task execution
    private var taskWakeLock: PowerManager.WakeLock? = null

    // Wake lock to keep device awake for scheduled tasks
    private var scheduledTaskWakeLock: PowerManager.WakeLock? = null

    /**
     * Initializes the ScreenKeepAliveManager with application context.
     *
     * Should be called in Application.onCreate().
     *
     * @param context Application context
     */
    fun initialize(context: Context) {
        applicationContext = context.applicationContext
        Logger.i(TAG, "ScreenKeepAliveManager initialized")
    }

    /**
     * Called when a task starts execution.
     *
     * Acquires a screen-bright wake lock to keep the screen on and visible
     * during task execution. Also wakes the device if the screen is off.
     *
     * @param context Application context
     */
    fun onTaskStarted(context: Context) {
        if (taskWakeLock?.isHeld == true) {
            Logger.d(TAG, "Task screen wake lock already held")
            return
        }

        try {
            val powerManager = context.getSystemService<PowerManager>() ?: return
            taskWakeLock = powerManager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                TASK_WAKELOCK_TAG,
            ).apply {
                acquire(TASK_WAKELOCK_TIMEOUT)
            }
            Logger.i(TAG, "Task screen wake lock acquired")
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to acquire task screen wake lock", e)
        }
    }

    /**
     * Called when a task completes (either successfully or with failure).
     *
     * Releases the screen-bright wake lock, allowing the screen to turn off
     * according to normal system timeout settings.
     */
    fun onTaskCompleted() {
        try {
            taskWakeLock?.let {
                if (it.isHeld) {
                    it.release()
                    Logger.i(TAG, "Task screen wake lock released")
                }
            }
            taskWakeLock = null
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to release task screen wake lock", e)
        }
    }

    /**
     * Called when the scheduled tasks list changes (task added, removed, enabled, or disabled).
     *
     * When there are enabled scheduled tasks, acquires a partial wake lock to ensure
     * the device stays awake enough to fire alarms. When no enabled scheduled tasks
     * remain, releases the wake lock.
     *
     * @param context Application context
     * @param hasEnabledTasks Whether there are any enabled scheduled tasks
     */
    fun onScheduledTasksChanged(context: Context, hasEnabledTasks: Boolean) {
        if (hasEnabledTasks) {
            acquireScheduledTaskWakeLock(context)
        } else {
            releaseScheduledTaskWakeLock()
        }
    }

    /**
     * Releases all wake locks held by this manager.
     *
     * Should be called when the application is terminating.
     */
    fun releaseAll() {
        Logger.i(TAG, "Releasing all screen wake locks")
        onTaskCompleted()
        releaseScheduledTaskWakeLock()
    }

    /**
     * Acquires a partial wake lock for scheduled tasks.
     */
    private fun acquireScheduledTaskWakeLock(context: Context) {
        if (scheduledTaskWakeLock?.isHeld == true) {
            Logger.d(TAG, "Scheduled task wake lock already held")
            return
        }

        try {
            val powerManager = context.getSystemService<PowerManager>() ?: return
            scheduledTaskWakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                SCHEDULED_WAKELOCK_TAG,
            ).apply {
                acquire(SCHEDULED_WAKELOCK_TIMEOUT)
            }
            Logger.i(TAG, "Scheduled task wake lock acquired")
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to acquire scheduled task wake lock", e)
        }
    }

    /**
     * Releases the scheduled task wake lock.
     */
    private fun releaseScheduledTaskWakeLock() {
        try {
            scheduledTaskWakeLock?.let {
                if (it.isHeld) {
                    it.release()
                    Logger.i(TAG, "Scheduled task wake lock released")
                }
            }
            scheduledTaskWakeLock = null
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to release scheduled task wake lock", e)
        }
    }
}
