package com.flowmate.autoxiaoer.task

import android.content.Context
import com.flowmate.autoxiaoer.ComponentManager
import com.flowmate.autoxiaoer.action.AgentAction
import com.flowmate.autoxiaoer.agent.PhoneAgentState
import com.flowmate.autoxiaoer.agent.LLMAgentListener
import com.flowmate.autoxiaoer.agent.LLMTaskResult
import com.flowmate.autoxiaoer.agent.PhoneAgentListener
import com.flowmate.autoxiaoer.agent.SubTask
import com.flowmate.autoxiaoer.agent.SubTaskResult
import com.flowmate.autoxiaoer.settings.PostTaskAction
import com.flowmate.autoxiaoer.settings.SettingsManager
import com.flowmate.autoxiaoer.ui.FloatingWindowStateManager
import com.flowmate.autoxiaoer.ui.TaskStatus
import com.flowmate.autoxiaoer.util.Logger
import com.flowmate.autoxiaoer.util.ScreenKeepAliveManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Singleton manager for task execution state.
 *
 * This is the single source of truth for task execution state in the application.
 * Both [MainViewModel] and [FloatingWindowService] observe this manager's StateFlows
 * to keep their UIs synchronized.
 *
 * Implements [PhoneAgentListener] to receive callbacks from [PhoneAgent] and
 * [LLMAgentListener] to receive callbacks from [LLMAgent], and updates the
 * shared state accordingly so the floating window can color-code each step.
 */
object TaskExecutionManager : PhoneAgentListener, LLMAgentListener {
    private const val TAG = "TaskExecutionManager"
    private const val PHONE_AGENT_POLL_INTERVAL_MS = 500L
    private const val POST_TASK_ACTION_DELAY_MS = 2000L
    private const val KEYCODE_POWER = 26

    private var applicationContext: Context? = null
    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Primary state flow for task execution state
    private val _taskState = MutableStateFlow(TaskExecutionState())

    /**
     * Observable task execution state.
     *
     * UI components should observe this StateFlow to receive task state updates.
     */
    val taskState: StateFlow<TaskExecutionState> = _taskState.asStateFlow()

    // Steps list for waterfall display
    private val _steps = MutableStateFlow<List<TaskStep>>(emptyList())

    /**
     * Observable list of task steps for waterfall display.
     *
     * Each step contains the step number, thinking text, and action.
     */
    val steps: StateFlow<List<TaskStep>> = _steps.asStateFlow()

    /**
     * Initializes the TaskExecutionManager with application context.
     *
     * Should be called in [AutoGLMApplication.onCreate] after [ComponentManager] is initialized.
     *
     * @param context Application context
     */
    fun initialize(context: Context) {
        applicationContext = context.applicationContext
        Logger.i(TAG, "TaskExecutionManager initialized")
        observePhoneAgentAvailability()
        observeLLMAgentAvailability()
        observeTaskStateForScreenKeepAlive()
    }

    /**
     * Observes ComponentManager.phoneAgent availability and registers/unregisters as listener.
     *
     * When phoneAgent becomes available, registers this manager as PhoneAgentListener.
     * When phoneAgent becomes null, unregisters.
     */
    private fun observePhoneAgentAvailability() {
        managerScope.launch {
            var lastPhoneAgent: com.flowmate.autoxiaoer.agent.PhoneAgent? = null

            // Poll for phoneAgent availability changes
            // This is a simple approach since ComponentManager doesn't expose a StateFlow for phoneAgent
            while (true) {
                val componentManager = getComponentManager()
                val currentPhoneAgent = componentManager?.phoneAgent

                if (currentPhoneAgent != lastPhoneAgent) {
                    if (lastPhoneAgent != null) {
                        // Unregister from previous phoneAgent
                        lastPhoneAgent.setListener(null)
                        Logger.i(TAG, "Unregistered as PhoneAgentListener")
                    }

                    if (currentPhoneAgent != null) {
                        // Register with new phoneAgent
                        currentPhoneAgent.setListener(this@TaskExecutionManager)
                        Logger.i(TAG, "Registered as PhoneAgentListener")
                    }

                    lastPhoneAgent = currentPhoneAgent
                }

                kotlinx.coroutines.delay(PHONE_AGENT_POLL_INTERVAL_MS)
            }
        }
    }

    /**
     * Observes LLMAgent availability and registers/unregisters this manager as LLMAgentListener.
     */
    private fun observeLLMAgentAvailability() {
        managerScope.launch {
            var lastLLMAgent: com.flowmate.autoxiaoer.agent.LLMAgent? = null

            while (true) {
                val currentLLMAgent = getComponentManager()?.llmAgent

                if (currentLLMAgent != lastLLMAgent) {
                    lastLLMAgent?.setListener(null)

                    if (currentLLMAgent != null) {
                        currentLLMAgent.setListener(this@TaskExecutionManager)
                        Logger.i(TAG, "Registered as LLMAgentListener")
                    } else {
                        Logger.i(TAG, "Unregistered as LLMAgentListener")
                    }

                    lastLLMAgent = currentLLMAgent
                }

                kotlinx.coroutines.delay(PHONE_AGENT_POLL_INTERVAL_MS)
            }
        }
    }

    /**
     * Observes task state changes to manage screen keep-alive behavior, floating window, and post-task actions.
     *
     * When task starts running: keeps screen bright and shows floating window
     * When task completes/fails: allows screen to turn off, hides floating window, then executes post-task action
     */
    private fun observeTaskStateForScreenKeepAlive() {
        val ctx = applicationContext ?: return
        
        managerScope.launch {
            taskState.collect { state ->
                try {
                    when (state.status) {
                        TaskStatus.RUNNING -> {
                            // Task started - keep screen bright and show floating window
                            ScreenKeepAliveManager.onTaskStarted(ctx)
                            FloatingWindowStateManager.onTaskStarted(ctx)
                        }
                        TaskStatus.COMPLETED, TaskStatus.FAILED -> {
                            // Task ended - allow screen to turn off and hide floating window
                            ScreenKeepAliveManager.onTaskCompleted()
                            FloatingWindowStateManager.onTaskCompleted()
                            // Execute configured post-task action (e.g., screen off, lock screen)
                            executePostTaskAction(ctx)
                        }
                        else -> {
                            // IDLE, PAUSED - no action needed
                        }
                    }
                } catch (e: Exception) {
                    Logger.e(TAG, "Error handling task state change (${state.status}): ${e.message}", e)
                }
            }
        }
    }

    /**
     * Executes the configured post-task action after a task completes or fails.
     *
     * Reads the user's preference from [SettingsManager] and performs the corresponding action:
     * - [PostTaskAction.NONE]: Do nothing
     * - [PostTaskAction.LOCK_SCREEN]: Lock the screen (KEYCODE_POWER = 26)
     *
     * A short delay is added before executing the action to allow the user to briefly
     * see the task completion status.
     *
     * @param context Application context for accessing SettingsManager
     */
    private fun executePostTaskAction(context: Context) {
        val settingsManager = SettingsManager.getInstance(context)
        val action = settingsManager.getPostTaskAction()

        if (action == PostTaskAction.NONE) return

        val componentManager = getComponentManager() ?: run {
            Logger.w(TAG, "Cannot execute post-task action: ComponentManager not available")
            return
        }
        val deviceExecutor = componentManager.deviceExecutor ?: run {
            Logger.w(TAG, "Cannot execute post-task action: DeviceExecutor not available")
            return
        }

        managerScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            // Delay to let the user briefly see the task result
            kotlinx.coroutines.delay(POST_TASK_ACTION_DELAY_MS)

            try {
                when (action) {
                    PostTaskAction.LOCK_SCREEN -> {
                        // Small delay to ensure window closes before screen locks
                        kotlinx.coroutines.delay(300L)
                        
                        // KEYCODE_POWER (26) - locks the screen
                        deviceExecutor.pressKey(KEYCODE_POWER)
                        Logger.i(TAG, "Post-task action executed: lock screen")
                    }
                    PostTaskAction.NONE -> { /* should not reach here */ }
                }
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to execute post-task action: ${e.message}", e)
            }
        }
    }

    /**
     * Gets the ComponentManager instance.
     *
     * @return ComponentManager instance or null if not initialized
     */
    private fun getComponentManager(): ComponentManager? {
        val ctx = applicationContext ?: return null
        return ComponentManager.getInstance(ctx)
    }

    // region Task Control Methods

    /**
     * Starts a new task with the given description.
     *
     * The task is first routed through [com.flowmate.autoxiaoer.agent.LLMAgent] for planning.
     * LLMAgent decomposes it into sub-tasks and dispatches them to PhoneAgent.
     * If LLMAgent is not yet available (Shizuku not connected), the call returns false.
     *
     * @param description The task description to execute
     * @param triggerContext Optional context about what triggered this task
     * @return true if task was started successfully, false otherwise
     */
    fun startTask(
        description: String,
        triggerContext: com.flowmate.autoxiaoer.task.TriggerContext? = null,
    ): Boolean {
        if (!canStartTask()) {
            Logger.w(TAG, "Cannot start task: preconditions not met")
            return false
        }

        val componentManager = getComponentManager() ?: return false
        val llmAgent = componentManager.llmAgent ?: run {
            Logger.w(TAG, "Cannot start task: LLMAgent not available")
            return false
        }

        // Update state to running
        _taskState.value =
            TaskExecutionState(
                status = TaskStatus.RUNNING,
                taskDescription = description,
            )

        // Clear previous steps
        _steps.value = emptyList()

        Logger.i(TAG, "Starting task via LLMAgent: ${description.take(50)}...")

        // Launch task execution in coroutine
        managerScope.launch {
            try {
                val result = llmAgent.run(description, triggerContext)

                if (result.success) {
                    Logger.i(TAG, "Task completed successfully: ${result.message}")
                    onTaskCompleted(result.message)
                } else {
                    Logger.w(TAG, "Task failed: ${result.message}")
                    onTaskFailed(result.message)
                }
            } catch (e: Exception) {
                Logger.e(TAG, "Task error: ${e.message}", e)
                _taskState.value =
                    _taskState.value.copy(
                        status = TaskStatus.FAILED,
                        resultMessage = e.message ?: "Unknown error",
                    )
            }
        }

        return true
    }

    /**
     * Pauses the currently running task.
     *
     * Both [LLMAgent] (planning loop) and [PhoneAgent] (execution loop) are paused so
     * that neither issues new LLM/device calls while paused.
     *
     * @return true if pause was initiated on PhoneAgent successfully, false otherwise
     */
    fun pauseTask(): Boolean {
        val componentManager = getComponentManager() ?: return false
        val agent = componentManager.phoneAgent ?: return false

        // Pause LLMAgent's ReAct loop first so it stops before the next iteration
        componentManager.llmAgent?.pause()

        val paused = agent.pause()
        if (paused) {
            Logger.i(TAG, "Task paused (LLMAgent + PhoneAgent)")
            _taskState.value = _taskState.value.copy(status = TaskStatus.PAUSED)
        } else {
            // PhoneAgent couldn't be paused; undo LLMAgent pause to avoid deadlock
            componentManager.llmAgent?.resume()
            Logger.w(TAG, "Failed to pause task")
        }
        return paused
    }

    /**
     * Resumes the paused task.
     *
     * Both [LLMAgent] and [PhoneAgent] are resumed.
     *
     * @return true if resume was initiated on PhoneAgent successfully, false otherwise
     */
    fun resumeTask(): Boolean {
        val componentManager = getComponentManager() ?: return false
        val agent = componentManager.phoneAgent ?: return false

        // Resume PhoneAgent first so it's ready before LLMAgent dispatches new sub-tasks
        val resumed = agent.resume()
        if (resumed) {
            componentManager.llmAgent?.resume()
            Logger.i(TAG, "Task resumed (PhoneAgent + LLMAgent)")
            _taskState.value = _taskState.value.copy(status = TaskStatus.RUNNING)
        } else {
            Logger.w(TAG, "Failed to resume task")
        }
        return resumed
    }

    /**
     * Cancels the currently running task.
     *
     * Both [LLMAgent] (cancels in-flight LLM HTTP request and exits the ReAct loop)
     * and [PhoneAgent] (cancels current action/model call) are cancelled.
     */
    fun cancelTask() {
        val componentManager = getComponentManager() ?: return

        Logger.i(TAG, "Cancelling task (LLMAgent + PhoneAgent)")
        // Cancel LLMAgent first: it will also cancel its ModelClient HTTP request
        componentManager.llmAgent?.cancel()
        // Cancel PhoneAgent in parallel
        componentManager.phoneAgent?.cancel()

        _taskState.value =
            _taskState.value.copy(
                status = TaskStatus.FAILED,
                resultMessage = "任务已取消",
            )
    }

    /**
     * Resets the task state to idle.
     *
     * Should be called when user wants to start a new task after completion/failure.
     */
    fun resetTask() {
        Logger.i(TAG, "Resetting task state")
        _taskState.value = TaskExecutionState()
        _steps.value = emptyList()
    }

    // endregion

    // region Query Methods

    /**
     * Enum representing reasons why a task cannot be started.
     */
    enum class StartTaskBlockReason {
        /** No blocking reason, task can be started. */
        NONE,

        /** Shizuku service is not connected. */
        SERVICE_NOT_CONNECTED,

        /** PhoneAgent is not available. */
        PHONE_AGENT_NULL,

        /** A task is already running or paused. */
        TASK_ALREADY_RUNNING,
    }

    /**
     * Checks if a new task can be started.
     *
     * @return true if all preconditions are met to start a task
     */
    fun canStartTask(): Boolean = getStartTaskBlockReason() == StartTaskBlockReason.NONE

    /**
     * Gets the specific reason why a task cannot be started.
     *
     * This method provides more detailed information than [canStartTask] for
     * displaying appropriate error messages to the user.
     *
     * @return The blocking reason, or [StartTaskBlockReason.NONE] if task can be started
     */
    fun getStartTaskBlockReason(): StartTaskBlockReason {
        val componentManager =
            getComponentManager()
                ?: return StartTaskBlockReason.SERVICE_NOT_CONNECTED

        // Check if service is connected
        if (!componentManager.isServiceConnected) {
            Logger.d(TAG, "getStartTaskBlockReason: service not connected")
            return StartTaskBlockReason.SERVICE_NOT_CONNECTED
        }

        // Check if phone agent is available (llmAgent depends on it too)
        val agent = componentManager.phoneAgent
        if (agent == null || componentManager.llmAgent == null) {
            Logger.d(TAG, "getStartTaskBlockReason: phoneAgent or llmAgent is null")
            return StartTaskBlockReason.PHONE_AGENT_NULL
        }

        // Check if a task is already running
        if (agent.isRunning() || agent.isPaused()) {
            Logger.d(TAG, "getStartTaskBlockReason: task already running or paused")
            return StartTaskBlockReason.TASK_ALREADY_RUNNING
        }

        return StartTaskBlockReason.NONE
    }

    /**
     * Checks if a task is currently running.
     *
     * @return true if a task is running or paused
     */
    fun isTaskRunning(): Boolean {
        val status = _taskState.value.status
        return status == TaskStatus.RUNNING || status == TaskStatus.PAUSED
    }

    // endregion

    // region PhoneAgentListener Implementation

    /**
     * Called when a new step starts in the task execution.
     *
     * @param stepNumber The step number that is starting
     */
    override fun onStepStarted(stepNumber: Int) {
        Logger.d(TAG, "Step $stepNumber started")
        _taskState.value =
            _taskState.value.copy(
                stepNumber = stepNumber,
                thinking = "",
                currentAction = "",
            )

        // Add new step to the list
        val newStep =
            TaskStep(
                stepNumber = stepNumber,
                thinking = "",
                action = "",
            )
        _steps.value = _steps.value + newStep
    }

    /**
     * Called when the model's thinking text is updated.
     *
     * Shared by both [PhoneAgentListener] and [LLMAgentListener] (same signature).
     * Updates the most-recently-added step regardless of its source, which is correct
     * because only one agent is active at any given moment.
     *
     * @param thinking The current thinking text from the model
     */
    override fun onThinkingUpdate(thinking: String) {
        Logger.d(TAG, "Thinking update: ${thinking.take(50)}...")
        _taskState.value = _taskState.value.copy(thinking = thinking)

        val currentSteps = _steps.value.toMutableList()
        if (currentSteps.isNotEmpty()) {
            val lastIndex = currentSteps.lastIndex
            currentSteps[lastIndex] = currentSteps[lastIndex].copy(thinking = thinking)
            _steps.value = currentSteps
        }
    }

    /**
     * Called when an action is executed.
     *
     * @param action The action that was executed
     */
    override fun onActionExecuted(action: AgentAction) {
        val actionText = action.formatForDisplay()
        Logger.d(TAG, "Action executed: $actionText")
        _taskState.value = _taskState.value.copy(currentAction = actionText)

        // Update the last step's action
        val currentSteps = _steps.value.toMutableList()
        if (currentSteps.isNotEmpty()) {
            val lastIndex = currentSteps.lastIndex
            currentSteps[lastIndex] = currentSteps[lastIndex].copy(action = actionText)
            _steps.value = currentSteps
        }
    }

    /**
     * Called when the task completes successfully.
     *
     * @param message The completion message
     */
    override fun onTaskCompleted(message: String) {
        Logger.i(TAG, "Task completed: $message")
        
        // If lock screen is configured, mark floating window as user-disabled
        // so that onTaskCompleted() in the observer will transition to HIDDEN
        val ctx = applicationContext
        if (ctx != null) {
            val settingsManager = SettingsManager.getInstance(ctx)
            if (settingsManager.getPostTaskAction() == PostTaskAction.LOCK_SCREEN) {
                Logger.i(TAG, "Lock screen configured, disabling floating window")
                FloatingWindowStateManager.disableByUser()
            }
        }
        
        _taskState.value =
            _taskState.value.copy(
                status = TaskStatus.COMPLETED,
                resultMessage = message,
            )
        // Note: FloatingWindowStateManager.onTaskCompleted() is called automatically
        // by the observer in observeTaskStateForScreenKeepAlive()
    }

    /**
     * Called when the task fails.
     *
     * @param error The error message
     */
    override fun onTaskFailed(error: String) {
        Logger.e(TAG, "Task failed: $error")
        
        // If lock screen is configured, mark floating window as user-disabled
        // so that onTaskCompleted() in the observer will transition to HIDDEN
        val ctx = applicationContext
        if (ctx != null) {
            val settingsManager = SettingsManager.getInstance(ctx)
            if (settingsManager.getPostTaskAction() == PostTaskAction.LOCK_SCREEN) {
                Logger.i(TAG, "Lock screen configured, disabling floating window")
                FloatingWindowStateManager.disableByUser()
            }
        }
        
        _taskState.value =
            _taskState.value.copy(
                status = TaskStatus.FAILED,
                resultMessage = error,
            )
        // Note: FloatingWindowStateManager.onTaskCompleted() is called automatically
        // by the observer in observeTaskStateForScreenKeepAlive()
    }

    /**
     * Called when screenshot capture starts.
     */
    override fun onScreenshotStarted() {
        // Can be used to show loading indicator if needed
        Logger.d(TAG, "Screenshot started")
    }

    /**
     * Called when screenshot capture completes.
     */
    override fun onScreenshotCompleted() {
        Logger.d(TAG, "Screenshot completed")
    }

    /**
     * Called when the floating window needs to be refreshed.
     */
    override fun onFloatingWindowRefreshNeeded() {
        Logger.d(TAG, "Floating window refresh needed")
        // This will be handled by FloatingWindowService observing the state
    }

    /**
     * Called when task is paused.
     *
     * @param stepNumber The step number when paused
     */
    override fun onTaskPaused(stepNumber: Int) {
        Logger.i(TAG, "Task paused at step $stepNumber")
        _taskState.value = _taskState.value.copy(status = TaskStatus.PAUSED)
    }

    /**
     * Called when task is resumed.
     *
     * @param stepNumber The step number when resumed
     */
    override fun onTaskResumed(stepNumber: Int) {
        Logger.i(TAG, "Task resumed at step $stepNumber")
        _taskState.value = _taskState.value.copy(status = TaskStatus.RUNNING)
    }

    // endregion

    // region LLMAgentListener Implementation

    /** Global counter for LLM planning rounds, used as step number in the waterfall. */
    private var llmPlanningRound = 0

    /**
     * Called at the start of each LLMAgent ReAct planning round.
     *
     * Inserts a new step entry tagged as [StepSource.LLM_AGENT] so the floating
     * window can render it in a different color from PhoneAgent steps.
     */
    override fun onPlanningRoundStarted(round: Int) {
        Logger.d(TAG, "LLM planning round $round started")
        llmPlanningRound = round

        val newStep = TaskStep(
            stepNumber = round,
            thinking = "",
            action = "",
            source = StepSource.LLM_AGENT,
        )
        _steps.value = _steps.value + newStep
    }

    /**
     * Called when LLMAgent is about to dispatch a sub-task to PhoneAgent.
     */
    override fun onSubTaskStarted(subTask: SubTask) {
        Logger.d(TAG, "LLM sub-task started: ${subTask.description.take(50)}")
        val currentSteps = _steps.value.toMutableList()
        val lastLLMIdx = currentSteps.indexOfLast { it.source == StepSource.LLM_AGENT }
        if (lastLLMIdx != -1) {
            currentSteps[lastLLMIdx] = currentSteps[lastLLMIdx].copy(
                action = "▶ ${subTask.description.take(60)}",
                subTaskName = subTask.description,
            )
            _steps.value = currentSteps
        }
    }

    /**
     * Called when PhoneAgent has finished a sub-task.
     */
    override fun onSubTaskCompleted(result: SubTaskResult) {
        Logger.d(TAG, "LLM sub-task completed: success=${result.success}")
    }

    /**
     * Called after a sub-task completes and the observation message is built.
     *
     * Writes the observation text back into the most recent LLM_AGENT step so
     * the floating window can display "LLMAgent 的观察结果".
     */
    override fun onObservationReceived(subTask: SubTask, result: SubTaskResult, observation: String) {
        Logger.d(TAG, "LLM observation received for sub-task ${subTask.id}")
        val currentSteps = _steps.value.toMutableList()
        val lastLLMIdx = currentSteps.indexOfLast { it.source == StepSource.LLM_AGENT }
        if (lastLLMIdx != -1) {
            currentSteps[lastLLMIdx] = currentSteps[lastLLMIdx].copy(observation = observation)
            _steps.value = currentSteps
        }
    }

    /**
     * Called when LLMAgent finishes the overall task (success or failure).
     */
    override fun onTaskFinished(result: LLMTaskResult) {
        Logger.d(TAG, "LLMAgent task finished: success=${result.success}, rounds=${result.planningRounds}")
    }

    // endregion

    // region State Mapping Utilities

    /**
     * Maps PhoneAgentState to TaskStatus.
     *
     * Used for verifying state consistency between PhoneAgent and TaskExecutionManager.
     *
     * @param phoneAgentState The phone agent state to map
     * @return Corresponding TaskStatus
     */
    fun mapPhoneAgentStateToTaskStatus(phoneAgentState: PhoneAgentState): TaskStatus = when (phoneAgentState) {
        PhoneAgentState.IDLE -> TaskStatus.IDLE
        PhoneAgentState.RUNNING -> TaskStatus.RUNNING
        PhoneAgentState.PAUSED -> TaskStatus.PAUSED
        PhoneAgentState.CANCELLED -> TaskStatus.FAILED
    }

    // endregion
}
