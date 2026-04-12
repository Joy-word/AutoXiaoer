package com.flowmate.autoxiaoer.notification

import android.content.Context
import com.flowmate.autoxiaoer.settings.SettingsManager
import com.flowmate.autoxiaoer.util.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Singleton manager for notification trigger rules.
 *
 * Provides CRUD operations for [NotificationTriggerRule] items,
 * exposes a [StateFlow] for UI observation, and offers [findMatchingRule]
 * for use by [AutoGLMNotificationListener] when a notification arrives.
 */
class NotificationTriggerManager private constructor(private val context: Context) {

    private val settingsManager = SettingsManager.getInstance(context)

    private val _rules = MutableStateFlow<List<NotificationTriggerRule>>(emptyList())

    /**
     * Observable list of all notification trigger rules.
     *
     * UI components should observe this StateFlow to receive updates.
     */
    val rules: StateFlow<List<NotificationTriggerRule>> = _rules.asStateFlow()

    companion object {
        private const val TAG = "NotificationTriggerManager"

        @Volatile
        private var instance: NotificationTriggerManager? = null

        fun getInstance(context: Context): NotificationTriggerManager =
            instance ?: synchronized(this) {
                instance ?: NotificationTriggerManager(context.applicationContext).also {
                    instance = it
                    Logger.d(TAG, "NotificationTriggerManager singleton created")
                }
            }
    }

    init {
        loadRules()
    }

    // ==================== Initialization ====================

    private fun loadRules() {
        val loaded = settingsManager.getNotificationTriggerRules()
        _rules.value = loaded
        Logger.d(TAG, "Loaded ${loaded.size} notification trigger rules")
    }

    // ==================== CRUD ====================

    /**
     * Returns the current list of all rules.
     */
    fun getAllRules(): List<NotificationTriggerRule> = _rules.value

    /**
     * Adds a new rule and persists it.
     *
     * @param appLabel Display name of the target app
     * @param packageName Package name of the target app
     * @param taskPrompt Task description to execute on notification
     */
    fun addRule(appLabel: String, packageName: String, taskPrompt: String) {
        val rule = NotificationTriggerRule(
            id = UUID.randomUUID().toString(),
            appLabel = appLabel,
            packageName = packageName,
            taskPrompt = taskPrompt,
        )
        val updated = _rules.value + rule
        _rules.value = updated
        settingsManager.saveNotificationTriggerRules(updated)
        Logger.i(TAG, "Added notification trigger rule for $packageName")
    }

    /**
     * Updates the task prompt of a rule and persists the change.
     *
     * @param ruleId ID of the rule to update
     * @param taskPrompt New task prompt
     */
    fun updateRule(ruleId: String, taskPrompt: String) {
        val updated = _rules.value.map { rule ->
            if (rule.id == ruleId) rule.copy(taskPrompt = taskPrompt) else rule
        }
        _rules.value = updated
        settingsManager.saveNotificationTriggerRules(updated)
        Logger.d(TAG, "Rule $ruleId updated taskPrompt")
    }

    /**
     * Updates the enabled state of a rule and persists the change.
     *
     * @param ruleId ID of the rule to update
     * @param enabled New enabled state
     */
    fun updateRuleEnabled(ruleId: String, enabled: Boolean) {
        val updated = _rules.value.map { rule ->
            if (rule.id == ruleId) rule.copy(isEnabled = enabled) else rule
        }
        _rules.value = updated
        settingsManager.saveNotificationTriggerRules(updated)
        Logger.d(TAG, "Rule $ruleId enabled=$enabled")
    }

    /**
     * Deletes a rule by ID and persists the change.
     *
     * @param ruleId ID of the rule to delete
     */
    fun deleteRule(ruleId: String) {
        val updated = _rules.value.filter { it.id != ruleId }
        _rules.value = updated
        settingsManager.saveNotificationTriggerRules(updated)
        Logger.i(TAG, "Deleted notification trigger rule $ruleId")
    }

    // ==================== Matching ====================

    /**
     * Finds the first enabled rule that matches the given package name.
     *
     * Returns null if no enabled rule matches.
     *
     * @param packageName The package name of the app that posted the notification
     */
    fun findMatchingRule(packageName: String): NotificationTriggerRule? =
        _rules.value.firstOrNull { it.isEnabled && it.packageName == packageName }
}
