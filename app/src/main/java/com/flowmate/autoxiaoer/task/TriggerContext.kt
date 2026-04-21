package com.flowmate.autoxiaoer.task

/**
 * Represents the type of trigger that started a task.
 */
enum class TriggerType {
    /** Task started manually by the user via the UI. */
    MANUAL,

    /** Task started by a scheduled alarm. */
    SCHEDULED,

    /** Task started by a matching notification. */
    NOTIFICATION,

    /** Task started by a voice command. */
    VOICE,

    /** Task started by an incoming ClawBot (WeChat iLink) message. */
    CLAWBOT,
}

/**
 * Contextual information about how a task was triggered.
 *
 * This context is passed to [com.flowmate.autoxiaoer.agent.LLMAgent] so it can
 * incorporate trigger details (e.g. the actual notification content) into its planning.
 *
 * For notification triggers, raw fields from [android.app.Notification.extras] are passed
 * through as-is without interpretation, so the LLM can reason about them directly.
 *
 * @property triggerType How the task was triggered
 * @property notificationApp The display label of the app that sent the notification
 * @property notificationPackageName The package name of the app that sent the notification
 * @property notificationTitle Raw `android.title` from the notification extras
 * @property notificationText Raw `android.text` (short body) from the notification extras
 * @property notificationBigText Raw `android.bigText` (expanded body) when available;
 *   typically more complete than [notificationText]
 * @property notificationSubText Raw `android.subText` (e.g. unread count label)
 * @property notificationCategory Raw [android.app.Notification.category] value
 * @property scheduledTaskBackground Background memo the agent wrote for itself when it created
 *   the scheduled task, explaining why it was scheduled and any relevant context.
 *   Only set for [TriggerType.SCHEDULED] triggers.
 * @property clawBotContextToken The `context_token` from the incoming ClawBot message.
 *   Must be echoed back in every `sendmessage` call to route the reply correctly.
 *   Only set for [TriggerType.CLAWBOT] triggers.
 * @property clawBotFromUserId The `from_user_id` of the WeChat user who sent the message.
 *   Only set for [TriggerType.CLAWBOT] triggers.
 * @property notificationTexts Accumulated notification text messages for queue-merged entries.
 *   When multiple notifications from the same session are merged in the passive task queue,
 *   their individual [notificationText] values are collected here in arrival order.
 *   Empty for non-merged (directly started) tasks.
 * @property triggerTime Epoch milliseconds when the trigger occurred
 */
data class TriggerContext(
    val triggerType: TriggerType,
    val notificationApp: String? = null,
    val notificationPackageName: String? = null,
    val notificationTitle: String? = null,
    val notificationText: String? = null,
    val notificationBigText: String? = null,
    val notificationSubText: String? = null,
    val notificationCategory: String? = null,
    val scheduledTaskBackground: String? = null,
    val clawBotContextToken: String? = null,
    val clawBotFromUserId: String? = null,
    val notificationTexts: List<String> = emptyList(),
    val triggerTime: Long = System.currentTimeMillis(),
) {
    /**
     * Convenience property returning the richest available notification body text.
     * Prefers [notificationBigText] over [notificationText].
     */
    val notificationContent: String?
        get() {
            val body = when {
                notificationTexts.isNotEmpty() -> notificationTexts.joinToString("\n")
                else -> notificationBigText?.takeIf { it.isNotBlank() } ?: notificationText
            }
            val parts = listOfNotNull(
                notificationTitle?.takeIf { it.isNotBlank() },
                body?.takeIf { it.isNotBlank() },
            )
            return if (parts.isEmpty()) null else parts.joinToString(": ")
        }
}
