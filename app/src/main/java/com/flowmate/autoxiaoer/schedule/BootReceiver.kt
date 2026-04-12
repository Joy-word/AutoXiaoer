package com.flowmate.autoxiaoer.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.flowmate.autoxiaoer.util.Logger

/**
 * BroadcastReceiver that handles device boot events to restore scheduled tasks.
 *
 * When the device boots up, all AlarmManager alarms are cleared by the system.
 * This receiver listens for BOOT_COMPLETED and reschedules all enabled tasks.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) {
            return
        }

        Logger.i(TAG, "Device boot completed, restoring scheduled tasks")

        try {
            val taskManager = ScheduledTaskManager.getInstance(context)
            taskManager.rescheduleAllEnabledTasks()
            Logger.i(TAG, "Successfully restored scheduled tasks after boot")
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to restore scheduled tasks after boot", e)
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
