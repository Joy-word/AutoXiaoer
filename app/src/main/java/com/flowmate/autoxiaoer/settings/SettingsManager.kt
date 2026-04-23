package com.flowmate.autoxiaoer.settings

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.flowmate.autoxiaoer.agent.PhoneAgentConfig
import com.flowmate.autoxiaoer.model.ModelConfig
import com.flowmate.autoxiaoer.util.Logger
import org.json.JSONArray
import org.json.JSONObject

/**
 * Represents a saved model profile with a display name.
 *
 * Used to store and manage multiple model configurations that users can switch between.
 *
 * @property id Unique identifier for the profile
 * @property displayName User-friendly name shown in the dropdown selector
 * @property config The actual model configuration containing API settings
 *
 */
data class SavedModelProfile(val id: String, val displayName: String, val config: ModelConfig)

/**
 * Represents a task template with a name and description.
 *
 * Task templates allow users to save frequently used task descriptions for quick access.
 *
 * @property id Unique identifier for the template
 * @property name Template name shown in the list
 * @property description Task description to fill in when the template is selected
 *
 */
data class TaskTemplate(val id: String, val name: String, val description: String)

/**
 * Represents the action to perform after a task completes.
 *
 * @property value The string value stored in SharedPreferences
 */
enum class PostTaskAction(val value: String) {
    /** Do nothing after task completes. */
    NONE("none"),

    /** Lock the device screen after task completes. */
    LOCK_SCREEN("lock_screen");

    companion object {
        /**
         * Converts a string value to a PostTaskAction.
         *
         * @param value The string value to convert
         * @return The corresponding PostTaskAction, defaults to NONE if not found
         */
        fun fromValue(value: String): PostTaskAction =
            entries.find { it.value == value } ?: NONE
    }
}

/**
 * Manages application configuration and settings (Singleton).
 *
 * Handles persistence of model and agent configurations using SharedPreferences.
 * Uses EncryptedSharedPreferences for sensitive data (API Key) on supported devices.
 *
 * This class provides methods to:
 * - Save and retrieve model configurations
 * - Save and retrieve agent configurations
 * - Manage multiple saved model profiles
 * - Manage task templates
 * - Handle custom system prompts
 *
 * 采用单例模式确保：
 * - 所有模块共享同一个实例
 * - 避免重复创建 SharedPreferences 访问
 * - 配置变更检测更准确
 */
class SettingsManager private constructor(private val context: Context) {
    companion object {
        private const val TAG = "SettingsManager"
        private const val PREFS_NAME = "autoglm_settings"
        private const val SECURE_PREFS_NAME = "autoglm_secure_settings"

        // 单例实例
        @Volatile
        private var instance: SettingsManager? = null

        /**
         * 获取 SettingsManager 单例实例
         *
         * @param context Android 上下文，会自动转换为 ApplicationContext
         * @return SettingsManager 单例实例
         */
        fun getInstance(context: Context): SettingsManager = instance ?: synchronized(this) {
            instance ?: SettingsManager(context.applicationContext).also {
                instance = it
                Logger.d(TAG, "SettingsManager singleton instance created")
            }
        }

        // ModelConfig keys
        private const val KEY_BASE_URL = "model_base_url"
        private const val KEY_API_KEY = "model_api_key"
        private const val KEY_MODEL_NAME = "model_name"
        private const val KEY_MAX_TOKENS = "model_max_tokens"
        private const val KEY_TEMPERATURE = "model_temperature"
        private const val KEY_TOP_P = "model_top_p"
        private const val KEY_FREQUENCY_PENALTY = "model_frequency_penalty"
        private const val KEY_TIMEOUT_SECONDS = "model_timeout_seconds"

        // PhoneAgentConfig keys
        private const val KEY_MAX_STEPS = "agent_max_steps"
        private const val KEY_LANGUAGE = "agent_language"
        private const val KEY_VERBOSE = "agent_verbose"
        private const val KEY_SCREENSHOT_DELAY_MS = "agent_screenshot_delay_ms"

        // Saved model profiles keys
        private const val KEY_SAVED_PROFILES = "saved_model_profiles"
        private const val KEY_CURRENT_PROFILE_ID = "current_profile_id"

        // Task templates keys
        private const val KEY_TASK_TEMPLATES = "task_templates"

        // Custom system prompt keys
        private const val KEY_CUSTOM_SYSTEM_PROMPT_CN = "custom_system_prompt_cn"
        private const val KEY_CUSTOM_SYSTEM_PROMPT_EN = "custom_system_prompt_en"

        // Dev profiles import key
        private const val KEY_DEV_PROFILES_IMPORTED = "dev_profiles_imported"

        // Post-task action key
        private const val KEY_POST_TASK_ACTION = "post_task_action"

        // Voice settings keys
        private const val KEY_VOICE_CONTINUOUS_LISTENING = "voice_continuous_listening"
        private const val KEY_VOICE_WAKE_WORDS = "voice_wake_words"
        private const val KEY_VOICE_WAKE_SENSITIVITY = "voice_wake_sensitivity"

        // Notification trigger rules key
        private const val KEY_NOTIFICATION_TRIGGER_RULES = "notification_trigger_rules"

        // ClawBot credential keys
        private const val KEY_CLAWBOT_BOT_TOKEN = "clawbot_bot_token"
        private const val KEY_CLAWBOT_BASE_URL = "clawbot_base_url"
        private const val KEY_CLAWBOT_ILINK_BOT_ID = "clawbot_ilink_bot_id"
        private const val KEY_CLAWBOT_ILINK_USER_ID = "clawbot_ilink_user_id"
        // Last incoming ClawBot conversation (used for proactive sends when no triggerContext)
        private const val KEY_CLAWBOT_LAST_FROM_USER_ID = "clawbot_last_from_user_id"
        private const val KEY_CLAWBOT_LAST_CONTEXT_TOKEN = "clawbot_last_context_token"

        // Floating window UI state keys
        private const val KEY_FLOATING_WINDOW_MINIMIZED = "floating_window_minimized"

        // LLMAgentConfig keys
        private const val KEY_LLM_AGENT_BASE_URL = "llm_agent_base_url"
        private const val KEY_LLM_AGENT_API_KEY = "llm_agent_api_key"
        private const val KEY_LLM_AGENT_MODEL_NAME = "llm_agent_model_name"
        private const val KEY_LLM_AGENT_MAX_TOKENS = "llm_agent_max_tokens"
        private const val KEY_LLM_AGENT_TEMPERATURE = "llm_agent_temperature"
        private const val KEY_LLM_AGENT_MAX_PLANNING_STEPS = "llm_agent_max_planning_steps"
        private const val KEY_LLM_AGENT_LANGUAGE = "llm_agent_language"
        private const val KEY_LLM_AGENT_CUSTOM_PROMPT_CN = "llm_agent_custom_prompt_cn"
        private const val KEY_LLM_AGENT_CUSTOM_PROMPT_EN = "llm_agent_custom_prompt_en"

        // BrainLLMConfig keys
        private const val KEY_BRAIN_LLM_BASE_URL = "brain_llm_base_url"
        private const val KEY_BRAIN_LLM_API_KEY = "brain_llm_api_key"
        private const val KEY_BRAIN_LLM_MODEL_NAME = "brain_llm_model_name"
        private const val KEY_BRAIN_LLM_MAX_TOKENS = "brain_llm_max_tokens"
        private const val KEY_BRAIN_LLM_TEMPERATURE = "brain_llm_temperature"
        private const val KEY_BRAIN_LLM_ENABLED = "brain_llm_enabled"
        private const val KEY_BRAIN_LLM_CUSTOM_PROMPT_CN = "brain_llm_custom_prompt_cn"
        private const val KEY_BRAIN_LLM_CUSTOM_PROMPT_EN = "brain_llm_custom_prompt_en"

        // Default values
        private val DEFAULT_MODEL_CONFIG = ModelConfig()
        private val DEFAULT_PHONE_AGENT_CONFIG = PhoneAgentConfig()
        private val DEFAULT_LLM_AGENT_CONFIG = com.flowmate.autoxiaoer.agent.LLMAgentConfig()
    }

    // Cache for detecting config changes
    private var lastModelConfig: ModelConfig? = null
    private var lastPhoneAgentConfig: PhoneAgentConfig? = null

    // Regular preferences for non-sensitive data
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // Encrypted preferences for sensitive data (API Key)
    private val securePrefs: SharedPreferences by lazy {
        createEncryptedPrefs()
    }

    /**
     * Creates encrypted SharedPreferences for sensitive data.
     *
     * Falls back to regular SharedPreferences on older devices (below Android M)
     * or if encryption initialization fails.
     *
     * @return SharedPreferences instance for storing sensitive data
     *
     */
    private fun createEncryptedPrefs(): SharedPreferences = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val masterKey =
                MasterKey
                    .Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()

            EncryptedSharedPreferences.create(
                context,
                SECURE_PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } else {
            Logger.w(TAG, "Device does not support EncryptedSharedPreferences, using regular prefs")
            context.getSharedPreferences(SECURE_PREFS_NAME, Context.MODE_PRIVATE)
        }
    } catch (e: Exception) {
        Logger.e(TAG, "Failed to create encrypted prefs, using fallback", e)
        context.getSharedPreferences(SECURE_PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Gets the current ModelConfig from storage.
     *
     * Returns default values if not previously saved.
     *
     * @return The current model configuration
     *
     */
    fun getModelConfig(): ModelConfig {
        Logger.d(TAG, "Loading model configuration")
        return ModelConfig(
            baseUrl =
            prefs.getString(KEY_BASE_URL, DEFAULT_MODEL_CONFIG.baseUrl)
                ?: DEFAULT_MODEL_CONFIG.baseUrl,
            apiKey =
            securePrefs
                .getString(KEY_API_KEY, DEFAULT_MODEL_CONFIG.apiKey)
                ?.ifEmpty { "EMPTY" } ?: "EMPTY",
            modelName =
            prefs.getString(KEY_MODEL_NAME, DEFAULT_MODEL_CONFIG.modelName)
                ?: DEFAULT_MODEL_CONFIG.modelName,
            maxTokens = prefs.getInt(KEY_MAX_TOKENS, DEFAULT_MODEL_CONFIG.maxTokens),
            temperature = prefs.getFloat(KEY_TEMPERATURE, DEFAULT_MODEL_CONFIG.temperature),
            topP = prefs.getFloat(KEY_TOP_P, DEFAULT_MODEL_CONFIG.topP),
            frequencyPenalty = prefs.getFloat(KEY_FREQUENCY_PENALTY, DEFAULT_MODEL_CONFIG.frequencyPenalty),
            timeoutSeconds = prefs.getLong(KEY_TIMEOUT_SECONDS, DEFAULT_MODEL_CONFIG.timeoutSeconds),
        )
    }

    /**
     * Saves the ModelConfig to storage.
     *
     * API Key is stored in encrypted preferences for security.
     * Other settings are stored in regular preferences.
     *
     * @param config The model configuration to save
     *
     */
    fun saveModelConfig(config: ModelConfig) {
        Logger.d(TAG, "Saving model configuration: baseUrl=${config.baseUrl}, modelName=${config.modelName}")
        // Save non-sensitive data to regular prefs
        prefs.edit().apply {
            putString(KEY_BASE_URL, config.baseUrl)
            putString(KEY_MODEL_NAME, config.modelName)
            putInt(KEY_MAX_TOKENS, config.maxTokens)
            putFloat(KEY_TEMPERATURE, config.temperature)
            putFloat(KEY_TOP_P, config.topP)
            putFloat(KEY_FREQUENCY_PENALTY, config.frequencyPenalty)
            putLong(KEY_TIMEOUT_SECONDS, config.timeoutSeconds)
            apply()
        }

        // Save API Key to encrypted prefs
        securePrefs.edit().apply {
            putString(KEY_API_KEY, config.apiKey)
            apply()
        }
    }

    /**
     * Gets the current PhoneAgentConfig from storage.
     *
     * Returns default values if not previously saved.
     *
     * @return The current agent configuration
     *
     */
    fun getPhoneAgentConfig(): PhoneAgentConfig {
        Logger.d(TAG, "Loading agent configuration")
        return PhoneAgentConfig(
            maxSteps = prefs.getInt(KEY_MAX_STEPS, DEFAULT_PHONE_AGENT_CONFIG.maxSteps),
            language =
            prefs.getString(KEY_LANGUAGE, DEFAULT_PHONE_AGENT_CONFIG.language)
                ?: DEFAULT_PHONE_AGENT_CONFIG.language,
            verbose = prefs.getBoolean(KEY_VERBOSE, DEFAULT_PHONE_AGENT_CONFIG.verbose),
            screenshotDelayMs = prefs.getLong(KEY_SCREENSHOT_DELAY_MS, DEFAULT_PHONE_AGENT_CONFIG.screenshotDelayMs),
        )
    }

    /**
     * Saves the PhoneAgentConfig to storage.
     *
     * @param config The agent configuration to save
     *
     */
    fun savePhoneAgentConfig(config: PhoneAgentConfig) {
        Logger.d(TAG, "Saving agent configuration: maxSteps=${config.maxSteps}, language=${config.language}")
        prefs.edit().apply {
            putInt(KEY_MAX_STEPS, config.maxSteps)
            putString(KEY_LANGUAGE, config.language)
            putBoolean(KEY_VERBOSE, config.verbose)
            putLong(KEY_SCREENSHOT_DELAY_MS, config.screenshotDelayMs)
            apply()
        }
    }

    /**
     * Clears all saved settings and resets to defaults.
     *
     * This will clear both regular and encrypted preferences.
     */
    fun clearAll() {
        Logger.i(TAG, "Clearing all settings")
        prefs.edit().clear().apply()
        securePrefs.edit().clear().apply()
    }

    /**
     * Checks if settings have been saved before.
     *
     * @return true if any settings have been saved, false otherwise
     */
    fun hasSettings(): Boolean = prefs.contains(KEY_BASE_URL) || prefs.contains(KEY_MAX_STEPS)

    /**
     * Migrates API Key from old unencrypted storage to encrypted storage.
     *
     * Call this once during app upgrade to ensure API keys are securely stored.
     */
    fun migrateApiKeyToSecureStorage() {
        val oldApiKey = prefs.getString(KEY_API_KEY, null)
        if (oldApiKey != null && oldApiKey != "EMPTY") {
            // Move to secure storage
            securePrefs.edit().putString(KEY_API_KEY, oldApiKey).apply()
            // Remove from old storage
            prefs.edit().remove(KEY_API_KEY).apply()
            Logger.i(TAG, "Migrated API Key to secure storage")
        }
    }

    /**
     * Checks if configuration has changed since last check.
     *
     * Updates the cached config after checking.
     *
     * @return true if config has changed, false otherwise
     */
    fun hasConfigChanged(): Boolean {
        val currentModelConfig = getModelConfig()
        val currentPhoneAgentConfig = getPhoneAgentConfig()

        val changed = lastModelConfig != currentModelConfig || lastPhoneAgentConfig != currentPhoneAgentConfig

        // Update cache
        lastModelConfig = currentModelConfig
        lastPhoneAgentConfig = currentPhoneAgentConfig

        return changed
    }

    // ==================== Saved Model Profiles ====================

    /**
     * Gets all saved model profiles.
     *
     * @return List of saved model profiles, empty list if none exist or parsing fails
     */
    fun getSavedProfiles(): List<SavedModelProfile> {
        val json = prefs.getString(KEY_SAVED_PROFILES, null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                val id = obj.getString("id")
                SavedModelProfile(
                    id = id,
                    displayName = obj.getString("displayName"),
                    config =
                    ModelConfig(
                        baseUrl = obj.getString("baseUrl"),
                        apiKey = securePrefs.getString("profile_apikey_$id", "EMPTY") ?: "EMPTY",
                        modelName = obj.getString("modelName"),
                        maxTokens = obj.optInt("maxTokens", DEFAULT_MODEL_CONFIG.maxTokens),
                        temperature =
                        obj
                            .optDouble(
                                "temperature",
                                DEFAULT_MODEL_CONFIG.temperature.toDouble(),
                            ).toFloat(),
                        topP =
                        obj
                            .optDouble(
                                "topP",
                                DEFAULT_MODEL_CONFIG.topP.toDouble(),
                            ).toFloat(),
                        frequencyPenalty =
                        obj
                            .optDouble(
                                "frequencyPenalty",
                                DEFAULT_MODEL_CONFIG.frequencyPenalty.toDouble(),
                            ).toFloat(),
                        timeoutSeconds =
                        obj.optLong(
                            "timeoutSeconds",
                            DEFAULT_MODEL_CONFIG.timeoutSeconds,
                        ),
                    ),
                )
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to parse saved profiles", e)
            emptyList()
        }
    }

    /**
     * Saves a new model profile or updates existing one.
     *
     * If a profile with the same ID exists, it will be updated.
     * Otherwise, a new profile will be added.
     *
     * @param profile The profile to save
     */
    fun saveProfile(profile: SavedModelProfile) {
        Logger.d(TAG, "Saving profile: id=${profile.id}, name=${profile.displayName}")
        val profiles = getSavedProfiles().toMutableList()
        val existingIndex = profiles.indexOfFirst { it.id == profile.id }

        if (existingIndex >= 0) {
            profiles[existingIndex] = profile
        } else {
            profiles.add(profile)
        }

        saveProfilesList(profiles)

        // Save API key separately in secure storage
        securePrefs.edit().putString("profile_apikey_${profile.id}", profile.config.apiKey).apply()
    }

    /**
     * Deletes a saved model profile.
     *
     * Also removes the associated API key from secure storage.
     * If the deleted profile was the current profile, clears the current profile ID.
     *
     * @param profileId The ID of the profile to delete
     */
    fun deleteProfile(profileId: String) {
        Logger.d(TAG, "Deleting profile: id=$profileId")
        val profiles = getSavedProfiles().filter { it.id != profileId }
        saveProfilesList(profiles)

        // Remove API key from secure storage
        securePrefs.edit().remove("profile_apikey_$profileId").apply()

        // If deleted profile was current, clear current profile ID
        if (getCurrentProfileId() == profileId) {
            setCurrentProfileId(null)
        }
    }

    /**
     * Gets the currently selected profile ID.
     *
     * @return The current profile ID, or null if no profile is selected
     */
    fun getCurrentProfileId(): String? = prefs.getString(KEY_CURRENT_PROFILE_ID, null)

    /**
     * Sets the currently selected profile ID.
     *
     * @param profileId The profile ID to set as current, or null to clear
     */
    fun setCurrentProfileId(profileId: String?) {
        prefs.edit().apply {
            if (profileId != null) {
                putString(KEY_CURRENT_PROFILE_ID, profileId)
            } else {
                remove(KEY_CURRENT_PROFILE_ID)
            }
            apply()
        }
    }

    /**
     * Gets a profile by ID.
     *
     * @param profileId The ID of the profile to retrieve
     * @return The profile if found, null otherwise
     */
    fun getProfileById(profileId: String): SavedModelProfile? = getSavedProfiles().find { it.id == profileId }

    /**
     * Generates a unique profile ID.
     *
     * @return A unique profile ID based on current timestamp
     */
    fun generateProfileId(): String = "profile_${System.currentTimeMillis()}"

    /**
     * Saves the list of profiles to storage.
     *
     * @param profiles The list of profiles to save
     */
    private fun saveProfilesList(profiles: List<SavedModelProfile>) {
        val array = JSONArray()
        profiles.forEach { profile ->
            val obj =
                JSONObject().apply {
                    put("id", profile.id)
                    put("displayName", profile.displayName)
                    put("baseUrl", profile.config.baseUrl)
                    put("modelName", profile.config.modelName)
                    put("maxTokens", profile.config.maxTokens)
                    put("temperature", profile.config.temperature)
                    put("topP", profile.config.topP)
                    put("frequencyPenalty", profile.config.frequencyPenalty)
                    put("timeoutSeconds", profile.config.timeoutSeconds)
                }
            array.put(obj)
        }
        prefs.edit().putString(KEY_SAVED_PROFILES, array.toString()).apply()
    }

    // ==================== Task Templates ====================

    /**
     * Gets all saved task templates.
     *
     * @return List of saved task templates, empty list if none exist or parsing fails
     */
    fun getTaskTemplates(): List<TaskTemplate> {
        val json = prefs.getString(KEY_TASK_TEMPLATES, null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                TaskTemplate(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    description = obj.getString("description"),
                )
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to parse task templates", e)
            emptyList()
        }
    }

    /**
     * Saves a new task template or updates existing one.
     *
     * If a template with the same ID exists, it will be updated.
     * Otherwise, a new template will be added.
     *
     * @param template The template to save
     */
    fun saveTaskTemplate(template: TaskTemplate) {
        Logger.d(TAG, "Saving task template: id=${template.id}, name=${template.name}")
        val templates = getTaskTemplates().toMutableList()
        val existingIndex = templates.indexOfFirst { it.id == template.id }

        if (existingIndex >= 0) {
            templates[existingIndex] = template
        } else {
            templates.add(template)
        }

        saveTemplatesList(templates)
    }

    /**
     * Deletes a task template.
     *
     * @param templateId The ID of the template to delete
     */
    fun deleteTaskTemplate(templateId: String) {
        Logger.d(TAG, "Deleting task template: id=$templateId")
        val templates = getTaskTemplates().filter { it.id != templateId }
        saveTemplatesList(templates)
    }

    /**
     * Gets a template by ID.
     *
     * @param templateId The ID of the template to retrieve
     * @return The template if found, null otherwise
     */
    fun getTemplateById(templateId: String): TaskTemplate? = getTaskTemplates().find { it.id == templateId }

    /**
     * Generates a unique template ID.
     *
     * @return A unique template ID based on current timestamp
     */
    fun generateTemplateId(): String = "template_${System.currentTimeMillis()}"

    /**
     * Saves the list of templates to storage.
     *
     * @param templates The list of templates to save
     */
    private fun saveTemplatesList(templates: List<TaskTemplate>) {
        val array = JSONArray()
        templates.forEach { template ->
            val obj =
                JSONObject().apply {
                    put("id", template.id)
                    put("name", template.name)
                    put("description", template.description)
                }
            array.put(obj)
        }
        prefs.edit().putString(KEY_TASK_TEMPLATES, array.toString()).apply()
    }

    // ==================== Custom System Prompt ====================

    /**
     * Gets the custom system prompt for the specified language.
     * Returns null if no custom prompt is set.
     *
     * @param language Language code: "cn" for Chinese, "en" for English
     * @return The custom system prompt or null if not set
     */
    fun getCustomSystemPrompt(language: String): String? {
        val key =
            if (language.lowercase() == "en" || language.lowercase() == "english") {
                KEY_CUSTOM_SYSTEM_PROMPT_EN
            } else {
                KEY_CUSTOM_SYSTEM_PROMPT_CN
            }
        return prefs.getString(key, null)
    }

    /**
     * Saves a custom system prompt for the specified language.
     *
     * @param language Language code: "cn" for Chinese, "en" for English
     * @param prompt The custom system prompt to save
     */
    fun saveCustomSystemPrompt(language: String, prompt: String) {
        val key =
            if (language.lowercase() == "en" || language.lowercase() == "english") {
                KEY_CUSTOM_SYSTEM_PROMPT_EN
            } else {
                KEY_CUSTOM_SYSTEM_PROMPT_CN
            }
        prefs.edit().putString(key, prompt).apply()
    }

    /**
     * Clears the custom system prompt for the specified language.
     *
     * @param language Language code: "cn" for Chinese, "en" for English
     */
    fun clearCustomSystemPrompt(language: String) {
        val key =
            if (language.lowercase() == "en" || language.lowercase() == "english") {
                KEY_CUSTOM_SYSTEM_PROMPT_EN
            } else {
                KEY_CUSTOM_SYSTEM_PROMPT_CN
            }
        prefs.edit().remove(key).apply()
    }

    /**
     * Checks if a custom system prompt is set for the specified language.
     *
     * @param language Language code: "cn" for Chinese, "en" for English
     * @return true if a custom prompt is set, false otherwise
     */
    fun hasCustomSystemPrompt(language: String): Boolean = getCustomSystemPrompt(language) != null

    // ==================== Dev Profiles Import ====================

    /**
     * Checks if dev profiles have already been imported.
     *
     * @return true if dev profiles have been imported, false otherwise
     */
    fun hasImportedDevProfiles(): Boolean = prefs.getBoolean(KEY_DEV_PROFILES_IMPORTED, false)

    /**
     * Marks dev profiles as imported.
     */
    fun markDevProfilesImported() {
        prefs.edit().putBoolean(KEY_DEV_PROFILES_IMPORTED, true).apply()
    }

    /**
     * Imports dev profiles from JSON string.
     *
     * @param json JSON string containing profiles configuration
     * @return Number of profiles imported, or -1 if parsing failed
     */
    fun importDevProfiles(json: String): Int = try {
        val root = JSONObject(json)
        val profilesArray = root.getJSONArray("profiles")
        val defaultProfileName = root.optString("defaultProfile", "")

        var importedCount = 0
        var defaultProfileId: String? = null

        for (i in 0 until profilesArray.length()) {
            val obj = profilesArray.getJSONObject(i)
            val name = obj.getString("name")
            val profileId = generateProfileId()

            val profile =
                SavedModelProfile(
                    id = profileId,
                    displayName = name,
                    config =
                    ModelConfig(
                        baseUrl = obj.getString("baseUrl"),
                        apiKey = obj.optString("apiKey", "EMPTY").ifEmpty { "EMPTY" },
                        modelName = obj.getString("modelName"),
                    ),
                )

            saveProfile(profile)
            importedCount++

            if (name == defaultProfileName) {
                defaultProfileId = profileId
            }

            Logger.d(TAG, "Imported dev profile: $name")
        }

        // Set default profile and apply its config
        if (defaultProfileId != null) {
            setCurrentProfileId(defaultProfileId)
            getProfileById(defaultProfileId)?.let { profile ->
                saveModelConfig(profile.config)
            }
        } else if (importedCount > 0) {
            // Use first profile as default
            getSavedProfiles().firstOrNull()?.let { profile ->
                setCurrentProfileId(profile.id)
                saveModelConfig(profile.config)
            }
        }

        markDevProfilesImported()
        Logger.i(TAG, "Imported $importedCount dev profiles")
        importedCount
    } catch (e: Exception) {
        Logger.e(TAG, "Failed to import dev profiles", e)
        -1
    }

    // ==================== Post-Task Action ====================

    /**
     * Gets the action to perform after a task completes.
     *
     * @return The post-task action setting
     */
    fun getPostTaskAction(): PostTaskAction {
        val value = prefs.getString(KEY_POST_TASK_ACTION, PostTaskAction.NONE.value)
            ?: PostTaskAction.NONE.value
        return PostTaskAction.fromValue(value)
    }

    /**
     * Sets the action to perform after a task completes.
     *
     * @param action The post-task action to set
     */
    fun setPostTaskAction(action: PostTaskAction) {
        Logger.d(TAG, "Setting post-task action: ${action.value}")
        prefs.edit().putString(KEY_POST_TASK_ACTION, action.value).apply()
    }

    // ==================== Voice Settings ====================

    /**
     * Sets whether continuous listening is enabled.
     *
     * @param enabled Whether continuous listening should be enabled
     */
    fun setContinuousListening(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_VOICE_CONTINUOUS_LISTENING, enabled).apply()
    }

    /**
     * Checks if continuous listening is enabled.
     *
     * @return true if continuous listening is enabled, false otherwise
     */
    fun isContinuousListeningEnabled(): Boolean = prefs.getBoolean(KEY_VOICE_CONTINUOUS_LISTENING, false)

    /**
     * Gets the wake words list.
     *
     * @return List of wake words
     */
    fun getWakeWordsList(): List<String> {
        val wordsStr = prefs.getString(KEY_VOICE_WAKE_WORDS, "你好小智") ?: "你好小智"
        return wordsStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }

    /**
     * Sets the wake words.
     *
     * @param wakeWords List of wake words
     */
    fun setWakeWords(wakeWords: List<String>) {
        prefs.edit().putString(KEY_VOICE_WAKE_WORDS, wakeWords.joinToString(",")).apply()
    }

    /**
     * Gets the wake word sensitivity.
     *
     * @return The sensitivity value (0.0 to 1.0)
     */
    fun getWakeWordSensitivity(): Float = prefs.getFloat(KEY_VOICE_WAKE_SENSITIVITY, 0.5f)

    /**
     * Sets the wake word sensitivity.
     *
     * @param sensitivity The sensitivity value (0.0 to 1.0)
     */
    fun setWakeWordSensitivity(sensitivity: Float) {
        prefs.edit().putFloat(KEY_VOICE_WAKE_SENSITIVITY, sensitivity.coerceIn(0f, 1f)).apply()
    }

    // ==================== Notification Trigger Rules ====================

    /**
     * Gets all notification trigger rules from storage.
     *
     * @return List of notification trigger rules, empty list if none saved
     */
    fun getNotificationTriggerRules(): List<com.flowmate.autoxiaoer.notification.NotificationTriggerRule> {
        val json = prefs.getString(KEY_NOTIFICATION_TRIGGER_RULES, null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            val rules = mutableListOf<com.flowmate.autoxiaoer.notification.NotificationTriggerRule>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                rules.add(
                    com.flowmate.autoxiaoer.notification.NotificationTriggerRule(
                        id = obj.getString("id"),
                        appLabel = obj.getString("appLabel"),
                        packageName = obj.getString("packageName"),
                        taskPrompt = obj.getString("taskPrompt"),
                        isEnabled = obj.optBoolean("isEnabled", true),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                    )
                )
            }
            rules
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to parse notification trigger rules", e)
            emptyList()
        }
    }

    /**
     * Saves all notification trigger rules to storage.
     *
     * @param rules The list of rules to persist
     */
    fun saveNotificationTriggerRules(rules: List<com.flowmate.autoxiaoer.notification.NotificationTriggerRule>) {
        val array = JSONArray()
        for (rule in rules) {
            val obj = JSONObject().apply {
                put("id", rule.id)
                put("appLabel", rule.appLabel)
                put("packageName", rule.packageName)
                put("taskPrompt", rule.taskPrompt)
                put("isEnabled", rule.isEnabled)
                put("createdAt", rule.createdAt)
            }
            array.put(obj)
        }
        prefs.edit().putString(KEY_NOTIFICATION_TRIGGER_RULES, array.toString()).apply()
        Logger.d(TAG, "Saved ${rules.size} notification trigger rules")
    }

    // ==================== LLM Agent Config ====================

    /**
     * Gets the current LLMAgentConfig from storage.
     *
     * Returns default values if not previously saved.
     */
    fun getLLMAgentConfig(): com.flowmate.autoxiaoer.agent.LLMAgentConfig {
        Logger.d(TAG, "Loading LLM agent configuration")
        return com.flowmate.autoxiaoer.agent.LLMAgentConfig(
            baseUrl = prefs.getString(KEY_LLM_AGENT_BASE_URL, DEFAULT_LLM_AGENT_CONFIG.baseUrl)
                ?: DEFAULT_LLM_AGENT_CONFIG.baseUrl,
            apiKey = securePrefs.getString(KEY_LLM_AGENT_API_KEY, DEFAULT_LLM_AGENT_CONFIG.apiKey)
                ?.ifEmpty { "EMPTY" } ?: "EMPTY",
            modelName = prefs.getString(KEY_LLM_AGENT_MODEL_NAME, DEFAULT_LLM_AGENT_CONFIG.modelName)
                ?: DEFAULT_LLM_AGENT_CONFIG.modelName,
            maxTokens = prefs.getInt(KEY_LLM_AGENT_MAX_TOKENS, DEFAULT_LLM_AGENT_CONFIG.maxTokens),
            temperature = prefs.getFloat(KEY_LLM_AGENT_TEMPERATURE, DEFAULT_LLM_AGENT_CONFIG.temperature),
            maxPlanningSteps = prefs.getInt(
                KEY_LLM_AGENT_MAX_PLANNING_STEPS,
                DEFAULT_LLM_AGENT_CONFIG.maxPlanningSteps,
            ),
            language = prefs.getString(KEY_LLM_AGENT_LANGUAGE, DEFAULT_LLM_AGENT_CONFIG.language)
                ?: DEFAULT_LLM_AGENT_CONFIG.language,
            customSystemPrompt = prefs.getString(
                if (isChineseLanguage()) KEY_LLM_AGENT_CUSTOM_PROMPT_CN else KEY_LLM_AGENT_CUSTOM_PROMPT_EN,
                "",
            ) ?: "",
        )
    }

    /**
     * Saves the LLMAgentConfig to storage.
     *
     * API Key is stored in encrypted preferences for security.
     */
    fun saveLLMAgentConfig(config: com.flowmate.autoxiaoer.agent.LLMAgentConfig) {
        Logger.d(TAG, "Saving LLM agent configuration: modelName=${config.modelName}")
        prefs.edit().apply {
            putString(KEY_LLM_AGENT_BASE_URL, config.baseUrl)
            putString(KEY_LLM_AGENT_MODEL_NAME, config.modelName)
            putInt(KEY_LLM_AGENT_MAX_TOKENS, config.maxTokens)
            putFloat(KEY_LLM_AGENT_TEMPERATURE, config.temperature)
            putInt(KEY_LLM_AGENT_MAX_PLANNING_STEPS, config.maxPlanningSteps)
            putString(KEY_LLM_AGENT_LANGUAGE, config.language)
            apply()
        }
        securePrefs.edit().apply {
            putString(KEY_LLM_AGENT_API_KEY, config.apiKey)
            apply()
        }
    }

    /**
     * Gets the LLM-agent custom system prompt for the specified language.
     *
     * @param language "cn" or "en"
     * @return The custom prompt, or null if not set
     */
    fun getLLMAgentCustomPrompt(language: String): String? {
        val key = if (language.lowercase() == "en" || language.lowercase() == "english") {
            KEY_LLM_AGENT_CUSTOM_PROMPT_EN
        } else {
            KEY_LLM_AGENT_CUSTOM_PROMPT_CN
        }
        return prefs.getString(key, null)
    }

    /**
     * Saves the LLM-agent custom system prompt for the specified language.
     *
     * @param language "cn" or "en"
     * @param prompt The custom prompt to save
     */
    fun saveLLMAgentCustomPrompt(language: String, prompt: String) {
        val key = if (language.lowercase() == "en" || language.lowercase() == "english") {
            KEY_LLM_AGENT_CUSTOM_PROMPT_EN
        } else {
            KEY_LLM_AGENT_CUSTOM_PROMPT_CN
        }
        prefs.edit().putString(key, prompt).apply()
    }

    /**
     * Clears the LLM-agent custom system prompt for the specified language.
     */
    fun clearLLMAgentCustomPrompt(language: String) {
        val key = if (language.lowercase() == "en" || language.lowercase() == "english") {
            KEY_LLM_AGENT_CUSTOM_PROMPT_EN
        } else {
            KEY_LLM_AGENT_CUSTOM_PROMPT_CN
        }
        prefs.edit().remove(key).apply()
    }

    private fun isChineseLanguage(): Boolean {
        val lang = prefs.getString(KEY_LLM_AGENT_LANGUAGE, DEFAULT_LLM_AGENT_CONFIG.language) ?: "cn"
        return lang.lowercase() != "en" && lang.lowercase() != "english"
    }

    // ==================== BrainLLM Config ====================

    /**
     * Gets the current [BrainLLMConfig] from storage.
     *
     * Returns default values if not previously saved.
     */
    fun getBrainLLMConfig(): com.flowmate.autoxiaoer.agent.BrainLLMConfig {
        Logger.d(TAG, "Loading BrainLLM configuration")
        val default = com.flowmate.autoxiaoer.agent.BrainLLMConfig()
        val language = prefs.getString(KEY_LLM_AGENT_LANGUAGE, "cn") ?: "cn"
        val customPromptKey = if (language.lowercase() == "en") KEY_BRAIN_LLM_CUSTOM_PROMPT_EN else KEY_BRAIN_LLM_CUSTOM_PROMPT_CN
        return com.flowmate.autoxiaoer.agent.BrainLLMConfig(
            baseUrl = prefs.getString(KEY_BRAIN_LLM_BASE_URL, default.baseUrl) ?: default.baseUrl,
            apiKey = securePrefs.getString(KEY_BRAIN_LLM_API_KEY, default.apiKey)
                ?.ifEmpty { "EMPTY" } ?: "EMPTY",
            modelName = prefs.getString(KEY_BRAIN_LLM_MODEL_NAME, default.modelName) ?: default.modelName,
            maxTokens = prefs.getInt(KEY_BRAIN_LLM_MAX_TOKENS, default.maxTokens),
            temperature = prefs.getFloat(KEY_BRAIN_LLM_TEMPERATURE, default.temperature),
            enabled = prefs.getBoolean(KEY_BRAIN_LLM_ENABLED, default.enabled),
            customSystemPrompt = prefs.getString(customPromptKey, "") ?: "",
        )
    }

    /**
     * Saves the [BrainLLMConfig] to storage.
     *
     * API Key is stored in encrypted preferences for security.
     */
    fun saveBrainLLMConfig(config: com.flowmate.autoxiaoer.agent.BrainLLMConfig) {
        Logger.d(TAG, "Saving BrainLLM configuration: enabled=${config.enabled}, modelName=${config.modelName}")
        prefs.edit().apply {
            putString(KEY_BRAIN_LLM_BASE_URL, config.baseUrl)
            putString(KEY_BRAIN_LLM_MODEL_NAME, config.modelName)
            putInt(KEY_BRAIN_LLM_MAX_TOKENS, config.maxTokens)
            putFloat(KEY_BRAIN_LLM_TEMPERATURE, config.temperature)
            putBoolean(KEY_BRAIN_LLM_ENABLED, config.enabled)
            apply()
        }
        securePrefs.edit().apply {
            putString(KEY_BRAIN_LLM_API_KEY, config.apiKey)
            apply()
        }
    }

    /**
     * Gets the BrainLLM custom system prompt for the specified language.
     *
     * @param language "cn" or "en"
     * @return The custom prompt, or null if not set
     */
    fun getBrainLLMCustomPrompt(language: String): String? {
        val key = if (language.lowercase() == "en" || language.lowercase() == "english") {
            KEY_BRAIN_LLM_CUSTOM_PROMPT_EN
        } else {
            KEY_BRAIN_LLM_CUSTOM_PROMPT_CN
        }
        return prefs.getString(key, null)
    }

    /**
     * Saves the BrainLLM custom system prompt for the specified language.
     */
    fun saveBrainLLMCustomPrompt(language: String, prompt: String) {
        val key = if (language.lowercase() == "en" || language.lowercase() == "english") {
            KEY_BRAIN_LLM_CUSTOM_PROMPT_EN
        } else {
            KEY_BRAIN_LLM_CUSTOM_PROMPT_CN
        }
        prefs.edit().putString(key, prompt).apply()
    }

    /**
     * Clears the BrainLLM custom system prompt for the specified language.
     */
    fun clearBrainLLMCustomPrompt(language: String) {
        val key = if (language.lowercase() == "en" || language.lowercase() == "english") {
            KEY_BRAIN_LLM_CUSTOM_PROMPT_EN
        } else {
            KEY_BRAIN_LLM_CUSTOM_PROMPT_CN
        }
        prefs.edit().remove(key).apply()
    }

    // ==================== ClawBot Credentials ====================

    /**
     * Saves ClawBot login credentials. bot_token is stored in encrypted prefs.
     */
    fun saveClawBotCredentials(creds: com.flowmate.autoxiaoer.clawbot.ClawBotCredentials) {
        Logger.i(TAG, "Saving ClawBot credentials, ilinkBotId=${creds.ilinkBotId}")
        securePrefs.edit().putString(KEY_CLAWBOT_BOT_TOKEN, creds.botToken).apply()
        prefs.edit().apply {
            putString(KEY_CLAWBOT_BASE_URL, creds.baseUrl)
            putString(KEY_CLAWBOT_ILINK_BOT_ID, creds.ilinkBotId)
            putString(KEY_CLAWBOT_ILINK_USER_ID, creds.ilinkUserId)
            apply()
        }
    }

    /**
     * Returns stored ClawBot credentials, or null if not yet connected.
     */
    fun getClawBotCredentials(): com.flowmate.autoxiaoer.clawbot.ClawBotCredentials? {
        val token = securePrefs.getString(KEY_CLAWBOT_BOT_TOKEN, null)
            ?.takeIf { it.isNotBlank() } ?: return null
        val rawBaseUrl = prefs.getString(KEY_CLAWBOT_BASE_URL, null)
            ?.takeIf { it.isNotBlank() } ?: return null
        // The server returns a bare host (e.g. "https://ilinkai.weixin.qq.com") but all
        // business endpoints live under the /ilink/bot path prefix.  Normalise here so the
        // rest of the code never needs to worry about it.
        val baseUrl = if (rawBaseUrl.contains("/ilink/bot")) {
            rawBaseUrl.trimEnd('/')
        } else {
            rawBaseUrl.trimEnd('/') + "/ilink/bot"
        }
        Logger.d(TAG, "getClawBotCredentials: baseUrl=$baseUrl (raw=$rawBaseUrl)")
        val botId = prefs.getString(KEY_CLAWBOT_ILINK_BOT_ID, null) ?: ""
        val userId = prefs.getString(KEY_CLAWBOT_ILINK_USER_ID, null) ?: ""
        return com.flowmate.autoxiaoer.clawbot.ClawBotCredentials(token, baseUrl, botId, userId)
    }

    /**
     * Removes all stored ClawBot credentials (used on disconnect or session expiry).
     */
    fun clearClawBotCredentials() {
        Logger.i(TAG, "Clearing ClawBot credentials")
        securePrefs.edit().remove(KEY_CLAWBOT_BOT_TOKEN).apply()
        prefs.edit().apply {
            remove(KEY_CLAWBOT_BASE_URL)
            remove(KEY_CLAWBOT_ILINK_BOT_ID)
            remove(KEY_CLAWBOT_ILINK_USER_ID)
            remove(KEY_CLAWBOT_LAST_FROM_USER_ID)
            remove(KEY_CLAWBOT_LAST_CONTEXT_TOKEN)
            apply()
        }
    }

    /**
     * Persists the most recent ClawBot conversation so the app can proactively push
     * messages to the user even when the task was not triggered by an incoming message.
     */
    fun saveClawBotLastConversation(fromUserId: String, contextToken: String) {
        prefs.edit().apply {
            putString(KEY_CLAWBOT_LAST_FROM_USER_ID, fromUserId)
            putString(KEY_CLAWBOT_LAST_CONTEXT_TOKEN, contextToken)
            apply()
        }
    }

    /**
     * Returns the last known ClawBot (fromUserId, contextToken) pair, or null if none stored.
     */
    fun getClawBotLastConversation(): Pair<String, String>? {
        val userId = prefs.getString(KEY_CLAWBOT_LAST_FROM_USER_ID, null)?.takeIf { it.isNotBlank() } ?: return null
        val token = prefs.getString(KEY_CLAWBOT_LAST_CONTEXT_TOKEN, null)?.takeIf { it.isNotBlank() } ?: return null
        return Pair(userId, token)
    }

    // ==================== Floating Window UI State ====================

    /**
     * Returns whether the floating window should be minimized.
     * Defaults to true so the window starts minimized by default.
     */
    fun isFloatingWindowMinimized(): Boolean = prefs.getBoolean(KEY_FLOATING_WINDOW_MINIMIZED, true)

    /**
     * Persists the floating window minimized state so it is restored on next open.
     *
     * @param minimized true if the window is currently minimized
     */
    fun setFloatingWindowMinimized(minimized: Boolean) {
        prefs.edit().putBoolean(KEY_FLOATING_WINDOW_MINIMIZED, minimized).apply()
    }

}
