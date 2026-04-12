package com.flowmate.autoxiaoer.notification

/**
 * Represents a rule that triggers a task when a notification from a specific app is received.
 *
 * @property id Unique identifier for the rule
 * @property appLabel Display name of the target app (stored for display without re-querying)
 * @property packageName Package name of the target app to monitor
 * @property taskPrompt The task description to execute when a notification is received
 * @property isEnabled Whether this rule is currently active
 * @property createdAt Timestamp when the rule was created
 */
data class NotificationTriggerRule(
    val id: String,
    val appLabel: String,
    val packageName: String,
    val taskPrompt: String,
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
)
