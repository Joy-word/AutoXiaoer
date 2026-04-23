package com.flowmate.autoxiaoer.notification

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.flowmate.autoxiaoer.task.TaskExecutionManager
import com.flowmate.autoxiaoer.task.TriggerContext
import com.flowmate.autoxiaoer.task.TriggerType
import com.flowmate.autoxiaoer.util.Logger

/**
 * NotificationListenerService that monitors all system notifications.
 *
 * When a notification arrives from an app that matches an enabled [NotificationTriggerRule],
 * this service attempts to start the configured task via [TaskExecutionManager].
 * If another task is already running, the trigger is silently discarded to avoid conflicts.
 *
 * The user must grant notification listener permission manually via system settings.
 */
class AutoGLMNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        Logger.i(TAG, "Notification listener connected")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Logger.w(TAG, "Notification listener disconnected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName ?: return

        val manager = NotificationTriggerManager.getInstance(applicationContext)
        val rule = manager.findMatchingRule(packageName) ?: return

        Logger.d(TAG, "Notification from $packageName matched rule '${rule.appLabel}'")

        // Drop immediately if a fundamental precondition is missing (no service / no agent).
        // TASK_ALREADY_RUNNING is handled by the queue, so we allow that case through.
        val blockReason = TaskExecutionManager.getStartTaskBlockReason()
        if (blockReason != TaskExecutionManager.StartTaskBlockReason.NONE &&
            blockReason != TaskExecutionManager.StartTaskBlockReason.TASK_ALREADY_RUNNING) {
            Logger.w(TAG, "Cannot enqueue task, block reason: $blockReason")
            return
        }

        val triggerContext = buildTriggerContext(sbn, rule)
        Logger.i(TAG, "Enqueueing task for notification from ${rule.appLabel} prompt=${rule.taskPrompt.take(50)}")
        TaskExecutionManager.enqueuePassiveTask(rule.taskPrompt, triggerContext)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // Not needed for this feature
    }

    /**
     * Reads raw notification fields from [sbn] and passes them through to [TriggerContext]
     * without interpretation. The LLM receives all fields as-is and reasons about them directly.
     *
     * Fields extracted from [android.app.Notification.extras]:
     * - `android.title`   — notification title
     * - `android.text`    — short body text
     * - `android.bigText` — expanded body (BigTextStyle); omitted when identical to [android.text]
     * - `android.subText` — secondary label (e.g. unread count)
     *
     * [android.app.Notification.category] is read directly from the Notification object.
     */
    private fun buildTriggerContext(sbn: StatusBarNotification, rule: NotificationTriggerRule): TriggerContext {
        val extras = sbn.notification.extras
        val title = extras.getCharSequence("android.title")?.toString()
        val text = extras.getCharSequence("android.text")?.toString()
        val bigText = extras.getCharSequence("android.bigText")?.toString()
        val subText = extras.getCharSequence("android.subText")?.toString()
        val category = sbn.notification.category

        return TriggerContext(
            triggerType = TriggerType.NOTIFICATION,
            notificationApp = rule.appLabel,
            notificationPackageName = sbn.packageName,
            notificationTitle = title,
            notificationText = text,
            notificationBigText = bigText?.takeIf { it.isNotBlank() && it != text },
            notificationSubText = subText?.takeIf { it.isNotBlank() },
            notificationCategory = category,
        )
    }

    companion object {
        private const val TAG = "NotificationListener"
    }
}
