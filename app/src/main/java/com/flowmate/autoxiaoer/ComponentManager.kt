package com.flowmate.autoxiaoer

import android.content.Context
import com.flowmate.autoxiaoer.action.ActionHandler
import com.flowmate.autoxiaoer.agent.BrainLLM
import com.flowmate.autoxiaoer.agent.LLMAgent
import com.flowmate.autoxiaoer.agent.PhoneAgent
import com.flowmate.autoxiaoer.agent.tools.ToolRegistry
import com.flowmate.autoxiaoer.agent.PhoneAgentListener
import com.flowmate.autoxiaoer.app.AppResolver
import com.flowmate.autoxiaoer.device.AccessibilityDeviceExecutor
import com.flowmate.autoxiaoer.device.DeviceExecutor
import com.flowmate.autoxiaoer.device.IDeviceExecutor
import com.flowmate.autoxiaoer.device.InputBackend
import com.flowmate.autoxiaoer.history.HistoryManager
import com.flowmate.autoxiaoer.input.AccessibilityTextInputManager
import com.flowmate.autoxiaoer.input.ITextInputManager
import com.flowmate.autoxiaoer.input.TextInputManager
import com.flowmate.autoxiaoer.mcp.McpServiceManager
import com.flowmate.autoxiaoer.model.ModelClient
import com.flowmate.autoxiaoer.model.ModelConfig
import com.flowmate.autoxiaoer.screenshot.AccessibilityScreenshotService
import com.flowmate.autoxiaoer.screenshot.IScreenshotService
import com.flowmate.autoxiaoer.screenshot.MediaProjectionScreenshotService
import com.flowmate.autoxiaoer.screenshot.ScreenshotService
import com.flowmate.autoxiaoer.settings.SettingsManager
import com.flowmate.autoxiaoer.ui.FloatingWindowService
import com.flowmate.autoxiaoer.util.HumanizedSwipeGenerator
import com.flowmate.autoxiaoer.util.Logger

/**
 * Centralized component manager for dependency injection and lifecycle management.
 * Provides a single point of access for all major components in the application.
 *
 * This class ensures:
 * - Proper dependency injection
 * - Lifecycle-aware component management
 * - Clean separation of concerns
 *
 */
class ComponentManager private constructor(private val context: Context) {
    companion object {
        private const val TAG = "ComponentManager"

        @Volatile
        private var instance: ComponentManager? = null

        /**
         * Gets the singleton instance of ComponentManager.
         *
         * @param context Application context
         * @return ComponentManager instance
         */
        fun getInstance(context: Context): ComponentManager = instance ?: synchronized(this) {
            instance ?: ComponentManager(context.applicationContext).also { instance = it }
        }

        /**
         * Clears the singleton instance.
         * Should be called when the application is being destroyed.
         */
        fun clearInstance() {
            synchronized(this) {
                instance?.cleanup()
                instance = null
            }
        }
    }

    // Settings manager - singleton instance
    val settingsManager: SettingsManager by lazy {
        SettingsManager.getInstance(context)
    }

    // History manager - always available
    val historyManager: HistoryManager by lazy {
        HistoryManager.getInstance(context)
    }

    /** Application-level MCP manager; lifecycle is independent of device-control backends. */
    val mcpServiceManager: McpServiceManager by lazy {
        McpServiceManager.getInstance(
            configProvider = { settingsManager.getMcpServers() },
            secretProvider = { serverId -> settingsManager.getMcpSecret(serverId) },
        ).also { it.start() }
    }

    // User service reference - set when Shizuku connects
    private var userService: IUserService? = null

    // Lazily initialized components that depend on the active backend
    private var deviceExecutorInternal: IDeviceExecutor? = null
    private var textInputManagerInternal: ITextInputManager? = null
    private var screenshotServiceInternal: IScreenshotService? = null
    private var actionHandlerInternal: ActionHandler? = null
    private var phoneAgentInternal: PhoneAgent? = null

    // Components that don't depend on UserService
    private var modelClientInternal: ModelClient? = null
    private var appResolverInternal: AppResolver? = null
    private var swipeGeneratorInternal: HumanizedSwipeGenerator? = null

    // LLMAgent and its dedicated ModelClient (independent from phoneAgent's modelClient)
    private var llmModelClientInternal: ModelClient? = null
    private var llmAgentInternal: LLMAgent? = null

    // BrainLLM and its dedicated ModelClient
    private var brainModelClientInternal: ModelClient? = null
    private var brainLLMInternal: BrainLLM? = null

    /**
     * Checks if the active backend's underlying service is connected.
     * Shizuku backend checks the UserService; Accessibility backend checks
     * [AutoXiaoerAccessibilityService.isConnected].
     */
    val isServiceConnected: Boolean
        get() = when (settingsManager.getInputBackend()) {
            InputBackend.SHIZUKU -> userService != null
            InputBackend.ACCESSIBILITY -> com.flowmate.autoxiaoer.device.AutoXiaoerAccessibilityService.isConnected()
        }

    /**
     * Gets the DeviceExecutor instance.
     * Requires a backend to be connected.
     */
    val deviceExecutor: IDeviceExecutor?
        get() = deviceExecutorInternal

    /**
     * Gets the ScreenshotService instance.
     * Requires a backend to be connected.
     */
    val screenshotService: IScreenshotService?
        get() = screenshotServiceInternal

    /**
     * Gets the ActionHandler instance.
     * Requires UserService to be connected.
     */
    val actionHandler: ActionHandler?
        get() = actionHandlerInternal

    /**
     * Gets the PhoneAgent instance.
     * Requires UserService to be connected.
     */
    val phoneAgent: PhoneAgent?
        get() = phoneAgentInternal

    /**
     * Gets the LLMAgent instance.
     * Requires UserService to be connected (PhoneAgent must be initialised first).
     */
    val llmAgent: LLMAgent?
        get() = llmAgentInternal

    /**
     * Gets the ModelClient instance.
     * Creates a new instance if config has changed.
     */
    val modelClient: ModelClient
        get() {
            val config = settingsManager.getModelConfig()
            if (modelClientInternal == null || modelConfigChanged(config)) {
                modelClientInternal = ModelClient(config)
            }
            return modelClientInternal!!
        }

    /**
     * Gets the AppResolver instance.
     */
    val appResolver: AppResolver
        get() {
            if (appResolverInternal == null) {
                appResolverInternal = AppResolver(context.packageManager)
            }
            return appResolverInternal!!
        }

    /**
     * Gets the HumanizedSwipeGenerator instance.
     */
    val swipeGenerator: HumanizedSwipeGenerator
        get() {
            if (swipeGeneratorInternal == null) {
                swipeGeneratorInternal = HumanizedSwipeGenerator()
            }
            return swipeGeneratorInternal!!
        }

    // Track current model configs for change detection
    private var currentModelConfig: ModelConfig? = null
    private var currentLLMAgentConfig: com.flowmate.autoxiaoer.agent.LLMAgentConfig? = null
    private var currentBrainLLMConfig: com.flowmate.autoxiaoer.agent.BrainLLMConfig? = null

    /**
     * Called when UserService connects (Shizuku backend).
     * Initializes all service-dependent components if SHIZUKU backend is selected.
     *
     * @param service The connected UserService
     */
    fun onServiceConnected(service: IUserService) {
        Logger.i(TAG, "UserService connected, initializing components")
        userService = service
        if (settingsManager.getInputBackend() == InputBackend.SHIZUKU) {
            initializeShizukuComponents()
        }
    }

    /**
     * Called when UserService disconnects.
     * Cleans up service-dependent components.
     */
    fun onServiceDisconnected() {
        Logger.i(TAG, "UserService disconnected, cleaning up components")
        userService = null
        if (settingsManager.getInputBackend() == InputBackend.SHIZUKU) {
            cleanupServiceDependentComponents()
        }
    }

    /**
     * Called when [AutoXiaoerAccessibilityService] becomes connected.
     * Initializes all accessibility-backend components if ACCESSIBILITY is selected.
     */
    fun onAccessibilityServiceConnected() {
        Logger.i(TAG, "AccessibilityService connected")
        if (settingsManager.getInputBackend() == InputBackend.ACCESSIBILITY) {
            initializeAccessibilityComponents()
        }
    }

    /**
     * Called when [AutoXiaoerAccessibilityService] is unbound by the system.
     */
    fun onAccessibilityServiceDisconnected() {
        Logger.i(TAG, "AccessibilityService disconnected")
        if (settingsManager.getInputBackend() == InputBackend.ACCESSIBILITY) {
            cleanupServiceDependentComponents()
        }
    }

    /**
     * Switches the active device-control backend at runtime.
     *
     * Returns false (and does nothing) when a task is currently running or paused.
     * Otherwise saves the setting and reinitialises the entire component stack.
     *
     * @param newBackend The backend to switch to.
     * @return true if the switch succeeded, false if blocked by an active task.
     */
    fun switchBackend(newBackend: InputBackend): Boolean {
        phoneAgentInternal?.let { agent ->
            if (agent.isRunning() || agent.isPaused()) {
                Logger.w(TAG, "switchBackend blocked: task is active")
                return false
            }
        }
        Logger.i(TAG, "Switching backend to $newBackend")
        settingsManager.setInputBackend(newBackend)
        cleanupServiceDependentComponents()
        when (newBackend) {
            InputBackend.SHIZUKU -> {
                val svc = userService
                if (svc != null) initializeShizukuComponents()
                else Logger.w(TAG, "Shizuku UserService not connected, components deferred")
            }
            InputBackend.ACCESSIBILITY -> {
                if (com.flowmate.autoxiaoer.device.AutoXiaoerAccessibilityService.isConnected()) {
                    initializeAccessibilityComponents()
                } else {
                    Logger.w(TAG, "AccessibilityService not connected, components deferred")
                }
            }
        }
        return true
    }

    /**
     * Initializes components using the Shizuku shell backend.
     */
    private fun initializeShizukuComponents() {
        val service = userService ?: return

        // Create DeviceExecutor
        deviceExecutorInternal = DeviceExecutor(service)

        // Create TextInputManager
        textInputManagerInternal = TextInputManager(service)

        // Create ScreenshotService with floating window controller provider
        // Use a provider function so it can get the current instance dynamically
        screenshotServiceInternal = ScreenshotService(service) { FloatingWindowService.getInstance() }

        buildActionHandlerAndAgents()

        Logger.i(TAG, "Shizuku components initialized")
    }

    /**
     * Initializes components using the AccessibilityService backend.
     */
    private fun initializeAccessibilityComponents() {
        // DeviceExecutor
        deviceExecutorInternal = AccessibilityDeviceExecutor()

        // TextInputManager
        textInputManagerInternal = AccessibilityTextInputManager()

        // ScreenshotService — use takeScreenshot() on API 30+, MediaProjection below
        screenshotServiceInternal = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            AccessibilityScreenshotService { FloatingWindowService.getInstance() }
        } else {
            MediaProjectionScreenshotService { FloatingWindowService.getInstance() }
        }

        buildActionHandlerAndAgents()

        Logger.i(TAG, "Accessibility components initialized")
    }

    /**
     * Builds ActionHandler, PhoneAgent, LLMAgent and BrainLLM from the already-set
     * device/input/screenshot components.
     */
    private fun buildActionHandlerAndAgents() {
        // Create ActionHandler with floating window provider to hide window during touch operations
        actionHandlerInternal =
            ActionHandler(
                deviceExecutor = deviceExecutorInternal!!,
                appResolver = appResolver,
                swipeGenerator = swipeGenerator,
                textInputManager = textInputManagerInternal!!,
                floatingWindowProvider = { FloatingWindowService.getInstance() },
                language = settingsManager.getPromptLanguage().code,
            )

        // Create PhoneAgent
        val phoneAgentConfig = settingsManager.getPhoneAgentConfig()
        phoneAgentInternal =
            PhoneAgent(
                modelClient = modelClient,
                actionHandler = actionHandlerInternal!!,
                screenshotService = screenshotServiceInternal!!,
                config = phoneAgentConfig,
                historyManager = historyManager,
            )

        // Create LLMAgent (depends on PhoneAgent being ready)
        val llmAgentConfig = settingsManager.getLLMAgentConfig()
        llmModelClientInternal = buildLLMModelClient(llmAgentConfig)
        currentLLMAgentConfig = llmAgentConfig

        // Create BrainLLM
        val brainLLMConfig = settingsManager.getBrainLLMConfig()
        brainModelClientInternal = buildBrainModelClient(brainLLMConfig)
        brainLLMInternal = BrainLLM(config = brainLLMConfig, modelClient = brainModelClientInternal!!)
        currentBrainLLMConfig = brainLLMConfig

        llmAgentInternal = LLMAgent(
            config = llmAgentConfig,
            modelClient = llmModelClientInternal!!,
            phoneAgent = phoneAgentInternal!!,
            historyManager = historyManager,
            context = context,
            brainLLM = brainLLMInternal,
            toolRegistryProvider = {
                ToolRegistry.forRuntime(
                    brainEnabled = brainLLMInternal?.isEnabled == true,
                    mcpTools = mcpServiceManager.currentToolSnapshot(),
                )
            },
        )
    }

    /**
     * Cleans up components that depend on the active backend.
     */
    private fun cleanupServiceDependentComponents() {
        llmAgentInternal = null
        llmModelClientInternal = null
        currentLLMAgentConfig = null
        brainLLMInternal = null
        brainModelClientInternal = null
        currentBrainLLMConfig = null
        phoneAgentInternal?.cancel()
        phoneAgentInternal = null
        actionHandlerInternal = null
        screenshotServiceInternal = null
        textInputManagerInternal = null
        deviceExecutorInternal = null

        Logger.i(TAG, "Service-dependent components cleaned up")
    }

    /**
     * Reinitializes PhoneAgent and LLMAgent with updated configuration.
     * Call this after settings have been changed.
     *
     * Note: This will NOT reinitialize if a task is currently running or paused,
     * to prevent accidentally cancelling user tasks.
     */
    fun reinitializeAgents() {
        val backend = settingsManager.getInputBackend()
        val backendReady = when (backend) {
            InputBackend.SHIZUKU -> userService != null
            InputBackend.ACCESSIBILITY -> com.flowmate.autoxiaoer.device.AutoXiaoerAccessibilityService.isConnected()
        }
        if (!backendReady) {
            Logger.w(TAG, "Cannot reinitialize agents: backend not connected")
            return
        }

        // Safety check: don't reinitialize while a task is active
        phoneAgentInternal?.let { agent ->
            if (agent.isRunning() || agent.isPaused()) {
                Logger.w(TAG, "Cannot reinitialize agents: PhoneAgent task is currently active (state: ${agent.getState()})")
                return
            }
        }

        // Cancel any running task (should be IDLE at this point, but just in case)
        phoneAgentInternal?.cancel()

        // Force PhoneAgent's model client to refresh on next access
        modelClientInternal = null

        // Rebuild the entire agent stack once (ActionHandler + PhoneAgent + BrainLLM + LLMAgent)
        buildActionHandlerAndAgents()

        Logger.i(TAG, "PhoneAgent, LLMAgent and BrainLLM reinitialized with new configuration")
    }

    /**
     * Sets the listener for PhoneAgent events.
     *
     * @param listener The listener to set
     */
    fun setPhoneAgentListener(listener: PhoneAgentListener?) {
        phoneAgentInternal?.setListener(listener)
    }

    /**
     * Sets the confirmation callback for ActionHandler.
     *
     * @param callback The callback to set
     */
    fun setConfirmationCallback(callback: ActionHandler.ConfirmationCallback?) {
        actionHandlerInternal?.setConfirmationCallback(callback)
    }

    /**
     * Checks if the model config has changed.
     */
    private fun modelConfigChanged(newConfig: ModelConfig): Boolean {
        val changed = currentModelConfig != newConfig
        if (changed) {
            currentModelConfig = newConfig
        }
        return changed
    }

    /**
     * Cleans up all components.
     * Should be called when the application is being destroyed.
     */
    fun cleanup() {
        Logger.i(TAG, "Cleaning up all components")
        cleanupServiceDependentComponents()
        mcpServiceManager.close()
        modelClientInternal = null
        appResolverInternal = null
        swipeGeneratorInternal = null
        currentModelConfig = null
    }

    /**
     * Builds a [ModelClient] from [LLMAgentConfig].
     *
     * LLM-agent uses a completely independent ModelClient with its own
     * baseUrl, apiKey and modelName.
     */
    private fun buildLLMModelClient(config: com.flowmate.autoxiaoer.agent.LLMAgentConfig): ModelClient =
        ModelClient(
            ModelConfig(
                baseUrl = config.baseUrl,
                apiKey = config.apiKey,
                modelName = config.modelName,
                maxTokens = config.maxTokens,
                temperature = config.temperature,
                topP = 1.0f,
                frequencyPenalty = 0.0f,
            ),
        )

    /**
     * Builds a [ModelClient] from [BrainLLMConfig].
     */
    private fun buildBrainModelClient(config: com.flowmate.autoxiaoer.agent.BrainLLMConfig): ModelClient =
        ModelClient(
            ModelConfig(
                baseUrl = config.baseUrl,
                apiKey = config.apiKey,
                modelName = config.modelName,
                maxTokens = config.maxTokens,
                temperature = config.temperature,
                topP = 1.0f,
                frequencyPenalty = 0.0f,
            ),
        )

    /**
     * Gets the current state summary for debugging.
     */
    fun getStateSummary(): String = buildString {
        appendLine("ComponentManager State:")
        appendLine("  - UserService connected: $isServiceConnected")
        appendLine("  - DeviceExecutor: ${if (deviceExecutorInternal != null) "initialized" else "null"}")
        appendLine("  - ScreenshotService: ${if (screenshotServiceInternal != null) "initialized" else "null"}")
        appendLine("  - ActionHandler: ${if (actionHandlerInternal != null) "initialized" else "null"}")
        appendLine("  - PhoneAgent: ${if (phoneAgentInternal != null) "initialized" else "null"}")
        appendLine("  - LLMAgent: ${if (llmAgentInternal != null) "initialized" else "null"}")
        appendLine("  - BrainLLM: ${if (brainLLMInternal != null) "initialized (enabled=${brainLLMInternal?.let { currentBrainLLMConfig?.enabled }})" else "null"}")
        appendLine("  - ModelClient: ${if (modelClientInternal != null) "initialized" else "null"}")
        appendLine("  - LLMModelClient: ${if (llmModelClientInternal != null) "initialized" else "null"}")
        appendLine("  - BrainModelClient: ${if (brainModelClientInternal != null) "initialized" else "null"}")
        appendLine("  - AppResolver: ${if (appResolverInternal != null) "initialized" else "null"}")
        appendLine("  - SwipeGenerator: ${if (swipeGeneratorInternal != null) "initialized" else "null"}")
    }
}
