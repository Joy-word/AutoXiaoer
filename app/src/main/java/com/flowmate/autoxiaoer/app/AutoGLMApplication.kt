package com.flowmate.autoxiaoer.app

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.flowmate.autoxiaoer.config.BrainLLMPrompts
import com.flowmate.autoxiaoer.config.LLMAgentPrompts
import com.flowmate.autoxiaoer.config.PromptManager
import com.flowmate.autoxiaoer.config.RelationshipContext
import com.flowmate.autoxiaoer.config.SystemPrompts
import com.flowmate.autoxiaoer.notification.NotificationTriggerManager
import com.flowmate.autoxiaoer.schedule.ScheduledTaskManager
import com.flowmate.autoxiaoer.settings.SettingsManager
import com.flowmate.autoxiaoer.task.TaskExecutionManager
import com.flowmate.autoxiaoer.ui.FloatingWindowStateManager
import com.flowmate.autoxiaoer.util.KeepAliveManager
import com.flowmate.autoxiaoer.util.LogFileManager
import com.flowmate.autoxiaoer.util.Logger
import com.flowmate.autoxiaoer.util.ScreenKeepAliveManager

/**
 * Application class that manages app-wide lifecycle events.
 *
 * This class is responsible for:
 * - Tracking activity lifecycle to determine foreground/background state
 * - Managing floating window visibility based on app state
 * - Loading custom system prompts from settings on startup
 *
 * The floating window is automatically hidden when the app is in the foreground
 * and shown when the app goes to the background, providing a seamless user experience.
 *
 */
class AutoGLMApplication : Application() {
    /**
     * Counter tracking the number of started (visible) activities.
     * When this reaches 0, the app is considered to be in the background.
     */
    private var activityCount = 0

    override fun onCreate() {
        super.onCreate()

        // Initialize log file manager for file-based logging
        LogFileManager.init(this)

        // Initialize relationship context (must be before any prompt loading)
        RelationshipContext.init(this)

        // Import dev profiles if available (debug builds only)
        importDevProfilesIfNeeded()

        // Migrate legacy pref-based prompts to file storage (one-time, no-op if already done)
        migrateLegacyPrompts()

        // Load custom system prompts if set
        loadCustomSystemPrompts()

        // 初始化保活状态
        KeepAliveManager.syncFixState(this)

        // Initialize ScreenKeepAliveManager
        ScreenKeepAliveManager.initialize(this)

        // Initialize TaskExecutionManager (after ComponentManager is available)
        TaskExecutionManager.initialize(this)

        // Initialize NotificationTriggerManager to load persisted rules
        NotificationTriggerManager.getInstance(this)

        // Initialize ScheduledTaskManager and restore scheduled tasks
        initializeScheduledTasks()

        // Resume ClawBot polling if credentials are already stored from a previous session
        com.flowmate.autoxiaoer.clawbot.ClawBotManager.startPollingIfConnected(this)

        registerActivityLifecycleCallbacks(
            object : ActivityLifecycleCallbacks {
                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                    // No action needed
                }

                override fun onActivityStarted(activity: Activity) {
                    activityCount++
                    Logger.d(TAG, "Activity started: ${activity.localClassName}, count: $activityCount")

                    // App came to foreground
                    if (activityCount == 1) {
                        FloatingWindowStateManager.onAppForeground()
                    }
                }

                override fun onActivityResumed(activity: Activity) {
                    // 同步修复状态
                    KeepAliveManager.syncFixState(this@AutoGLMApplication)
                }

                override fun onActivityPaused(activity: Activity) {
                    // No action needed
                }

                override fun onActivityStopped(activity: Activity) {
                    activityCount--
                    Logger.d(TAG, "Activity stopped: ${activity.localClassName}, count: $activityCount")

                    // App went to background
                    if (activityCount == 0) {
                        FloatingWindowStateManager.onAppBackground(this@AutoGLMApplication)
                    }
                }

                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
                    // No action needed
                }

                override fun onActivityDestroyed(activity: Activity) {
                    // No action needed
                }
            },
        )
    }

    override fun onTerminate() {
        super.onTerminate()
        // Release all wake locks when app terminates
        com.flowmate.autoxiaoer.util.ScreenKeepAliveManager.releaseAll()
    }

    /**
     * Loads custom system prompts from settings (or from file storage) if they exist.
     *
     * Priority: file storage (PromptManager) → legacy SharedPreferences → built-in default.
     * After migration, legacy prefs are no longer the source of truth.
     */
    private fun loadCustomSystemPrompts() {
        val settingsManager = SettingsManager.getInstance(this)
        val promptManager = PromptManager.getInstance(this)

        // Phone Agent prompts
        for (lang in listOf("cn", "en")) {
            val fromFile = promptManager.getCurrent(PromptManager.PromptType.PHONE_AGENT, lang)
            val fromPrefs = settingsManager.getCustomSystemPrompt(lang)
            val active = fromFile ?: fromPrefs
            if (active != null) {
                if (lang == "en") SystemPrompts.setCustomEnglishPrompt(active)
                else SystemPrompts.setCustomChinesePrompt(active)
                Logger.d(TAG, "Loaded phone-agent prompt [$lang] from ${if (fromFile != null) "file" else "prefs"}")
            }
        }

        // LLM Agent prompts
        for (lang in listOf("cn", "en")) {
            val fromFile = promptManager.getCurrent(PromptManager.PromptType.LLM_AGENT, lang)
            val fromPrefs = settingsManager.getLLMAgentCustomPrompt(lang)
            val active = fromFile ?: fromPrefs
            if (active != null) {
                if (lang == "en") LLMAgentPrompts.setCustomEnglishPrompt(active)
                else LLMAgentPrompts.setCustomChinesePrompt(active)
                Logger.d(TAG, "Loaded llm-agent prompt [$lang] from ${if (fromFile != null) "file" else "prefs"}")
            }
        }

        // Brain LLM prompts
        for (lang in listOf("cn", "en")) {
            val fromFile = promptManager.getCurrent(PromptManager.PromptType.BRAIN_LLM, lang)
            val fromPrefs = settingsManager.getBrainLLMCustomPrompt(lang)
            val active = fromFile ?: fromPrefs
            if (active != null) {
                if (lang == "en") BrainLLMPrompts.setCustomEnglishPrompt(active)
                else BrainLLMPrompts.setCustomChinesePrompt(active)
                Logger.d(TAG, "Loaded brain-llm prompt [$lang] from ${if (fromFile != null) "file" else "prefs"}")
            }
        }
    }

    /**
     * One-time migration: copies legacy SharedPreferences prompts into file storage.
     * Safe to call on every startup — PromptManager.migrateFromPrefs() is a no-op
     * when the target file already exists.
     */
    private fun migrateLegacyPrompts() {
        val settingsManager = SettingsManager.getInstance(this)
        val promptManager = PromptManager.getInstance(this)

        for (lang in listOf("cn", "en")) {
            promptManager.migrateFromPrefs(
                PromptManager.PromptType.PHONE_AGENT, lang,
                settingsManager.getCustomSystemPrompt(lang),
            )
            promptManager.migrateFromPrefs(
                PromptManager.PromptType.LLM_AGENT, lang,
                settingsManager.getLLMAgentCustomPrompt(lang),
            )
            promptManager.migrateFromPrefs(
                PromptManager.PromptType.BRAIN_LLM, lang,
                settingsManager.getBrainLLMCustomPrompt(lang),
            )
        }
        Logger.d(TAG, "Legacy prompt migration completed (no-op if already migrated)")
    }

    /**
     * Imports dev profiles from assets if available and not already imported.
     *
     * This is used for debug builds to pre-populate model profiles for testing.
     * The dev_profiles.json file is only included in debug builds.
     */
    private fun importDevProfilesIfNeeded() {
        val settingsManager = SettingsManager.getInstance(this)

        // Skip if already imported
        if (settingsManager.hasImportedDevProfiles()) {
            return
        }

        try {
            val json = assets.open("dev_profiles.json").bufferedReader().readText()
            val count = settingsManager.importDevProfiles(json)
            if (count > 0) {
                Logger.i(TAG, "Imported $count dev profiles from assets")
            }
        } catch (e: java.io.FileNotFoundException) {
            // File not found - this is expected for release builds
            Logger.d(TAG, "dev_profiles.json not found in assets (expected for release builds)")
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to import dev profiles", e)
        }
    }

    /**
     * Initializes the ScheduledTaskManager and restores all scheduled tasks.
     *
     * This ensures that scheduled tasks are properly registered with AlarmManager
     * when the app starts.
     */
    private fun initializeScheduledTasks() {
        try {
            val taskManager = ScheduledTaskManager.getInstance(this)
            taskManager.rescheduleAllEnabledTasks()
            Logger.i(TAG, "ScheduledTaskManager initialized and tasks restored")
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to initialize scheduled tasks", e)
        }
    }

    companion object {
        private const val TAG = "AutoGLMApplication"
    }
}
