package com.flowmate.autoxiaoer.history

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Base64
import com.flowmate.autoxiaoer.action.AgentAction
import com.flowmate.autoxiaoer.model.TokenUsage
import com.flowmate.autoxiaoer.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/**
 * Manages task execution history storage and retrieval.
 *
 * This singleton class handles all operations related to task history including:
 * - Recording task execution steps with screenshots
 * - Persisting history to local storage
 * - Loading and querying historical tasks
 * - Managing history lifecycle (creation, deletion, cleanup)
 *
 * Usage example:
 * ```kotlin
 * val historyManager = HistoryManager.getInstance(context)
 * val task = historyManager.startTask("Open Settings app")
 * historyManager.recordStep(1, "Thinking...", action, "Tap on Settings", true)
 * historyManager.completeTask(true, "Task completed successfully")
 * ```
 *
 */
class HistoryManager private constructor(private val context: Context) {
    /** Directory for storing task history files. */
    private val historyDir: File by lazy {
        File(context.filesDir, HISTORY_DIR).also { it.mkdirs() }
    }

    /** Currently recording task, null if no task is being recorded. */
    private var currentTask: TaskHistory? = null

    /** Steps buffered before the next [recordPlanningRound] or [completeTask] flush. */
    private val pendingSteps = mutableListOf<HistoryStep>()

    /** Base64-encoded screenshot data for the current step. */
    private var currentScreenshotBase64: String? = null

    /** Width of the current screenshot in pixels. */
    private var currentScreenshotWidth: Int = 0

    /** Height of the current screenshot in pixels. */
    private var currentScreenshotHeight: Int = 0

    private val _historyList = MutableStateFlow<List<TaskHistory>>(emptyList())

    /** Observable list of all task histories, sorted by most recent first. */
    val historyList: StateFlow<List<TaskHistory>> = _historyList.asStateFlow()

    init {
        loadHistoryIndex()
    }

    /**
     * Starts recording a new task.
     *
     * Creates a new [TaskHistory] instance and sets it as the current task being recorded.
     * All subsequent calls to [recordStep] will add steps to this task until [completeTask] is called.
     *
     * @param taskDescription Human-readable description of the task being executed
     * @return The newly created [TaskHistory] instance
     *
     */
    fun startTask(taskDescription: String): TaskHistory {
        val task = TaskHistory(taskDescription = taskDescription)
        currentTask = task
        pendingSteps.clear()
        Logger.d(TAG, "Started recording task: ${task.id}")
        return task
    }

    /**
     * Sets the current screenshot for the next step.
     *
     * The screenshot data will be used when [recordStep] is called to save
     * both the original and annotated versions of the screenshot.
     *
     * @param base64Data Base64-encoded screenshot image data (WebP format)
     * @param width Screenshot width in pixels
     * @param height Screenshot height in pixels
     *
     */
    fun setCurrentScreenshot(base64Data: String, width: Int, height: Int) {
        currentScreenshotBase64 = base64Data
        currentScreenshotWidth = width
        currentScreenshotHeight = height
    }

    /**
     * Records a step in the current task.
     *
     * Saves the step information including thinking, action, and screenshot to the current task.
     * If a screenshot is available (set via [setCurrentScreenshot]), it will be saved to disk
     * and optionally annotated with action visualization.
     *
     * @param stepNumber Sequential step number within the task
     * @param thinking Model's reasoning/thinking for this step
     * @param action The agent action executed, or null if no action
     * @param actionDescription Human-readable description of the action
     * @param success Whether the step executed successfully
     * @param message Optional additional message or error details
     * @param tokenUsage Token consumption for this step's model call, or null if unavailable
     *
     */
    suspend fun recordStep(
        stepNumber: Int,
        thinking: String,
        action: AgentAction?,
        actionDescription: String,
        success: Boolean,
        message: String? = null,
        tokenUsage: TokenUsage? = null,
    ) = withContext(Dispatchers.IO) {
        val task = currentTask ?: return@withContext

        var screenshotPath: String? = null
        var annotatedPath: String? = null

        // Save screenshot if available
        currentScreenshotBase64?.let { base64 ->
            try {
                // Decode base64 to raw bytes (already WebP format)
                val webpBytes = Base64.decode(base64, Base64.DEFAULT)

                // Save original screenshot directly without re-compression
                screenshotPath = saveScreenshotBytes(task.id, stepNumber, webpBytes, false)

                // Create and save annotated screenshot if action has visual annotation
                if (action != null) {
                    val annotation =
                        ScreenshotAnnotator.createAnnotation(
                            action,
                            currentScreenshotWidth,
                            currentScreenshotHeight,
                        )
                    if (annotation !is ActionAnnotation.None) {
                        // Only decode bitmap when we need to annotate
                        val bitmap = BitmapFactory.decodeByteArray(webpBytes, 0, webpBytes.size)
                        if (bitmap != null) {
                            // Calculate scaled density based on screenshot size vs typical screen size
                            // This ensures annotations look proportional on scaled screenshots
                            val baseDensity = context.resources.displayMetrics.density
                            val scaleFactor = bitmap.width.toFloat() / context.resources.displayMetrics.widthPixels
                            val scaledDensity = baseDensity * scaleFactor
                            val annotatedBitmap = ScreenshotAnnotator.annotate(bitmap, annotation, scaledDensity)
                            annotatedPath = saveScreenshotBitmap(task.id, stepNumber, annotatedBitmap, true)
                            annotatedBitmap.recycle()
                            bitmap.recycle()
                        }
                    }
                }
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to save screenshot for step $stepNumber", e)
            }
        }

        val step =
            HistoryStep(
                stepNumber = stepNumber,
                thinking = thinking,
                action = action,
                actionDescription = actionDescription,
                screenshotPath = screenshotPath,
                annotatedScreenshotPath = annotatedPath,
                success = success,
                message = message,
                tokenUsage = tokenUsage,
            )

        pendingSteps.add(step)
        Logger.d(TAG, "Recorded step $stepNumber for task ${task.id}")

        // Clear current screenshot
        currentScreenshotBase64 = null
    }

    /**
     * Records a LLMAgent planning round in the current task.
     *
     * Should be called after each ReAct iteration completes (i.e. once the action type is
     * known and, for sub-task actions, once the sub-task result and observation are available).
     *
     * @param round The planning round to record
     */
    fun recordPlanningRound(round: LLMPlanningRound) {
        val task = currentTask ?: return
        val steps = pendingSteps.toMutableList()
        pendingSteps.clear()
        task.planningRounds.add(round.copy(steps = steps))
        Logger.d(TAG, "Recorded LLM planning round ${round.round} for task ${task.id} with ${steps.size} steps")
    }

    /**
     * Completes the current task recording.
     *
     * Finalizes the task with success/failure status and saves it to persistent storage.
     * Empty tasks (no steps recorded) are discarded. The history list is updated and
     * old entries are trimmed if the maximum count is exceeded.
     *
     * @param success Whether the task completed successfully
     * @param message Optional completion message or error description
     *
     */
    suspend fun completeTask(success: Boolean, message: String?) = withContext(Dispatchers.IO) {
        val task = currentTask ?: return@withContext

        // Don't save tasks with no recorded activity at all
        if (task.planningRounds.isEmpty() && pendingSteps.isEmpty()) {
            Logger.d(TAG, "Skipping empty task ${task.id}")
            currentTask = null
            return@withContext
        }

        flushPendingSteps(task)

        task.endTime = System.currentTimeMillis()
        task.success = success
        task.completionMessage = message

        // Save task to disk
        saveTask(task)

        // Update history list
        val updatedList = _historyList.value.toMutableList()
        updatedList.add(0, task)

        // Trim old history if needed
        while (updatedList.size > MAX_HISTORY_COUNT) {
            val removed = updatedList.removeAt(updatedList.size - 1)
            deleteTaskFiles(removed.id)
        }

        _historyList.value = updatedList
        saveHistoryIndex()

        Logger.d(TAG, "Completed task ${task.id}, success=$success")
        currentTask = null
    }

    /**
     * Gets a task history by ID.
     *
     * Loads the complete task history including all steps from persistent storage.
     *
     * @param taskId Unique identifier of the task to retrieve
     * @return The [TaskHistory] if found, null otherwise
     *
     */
    suspend fun getTask(taskId: String): TaskHistory? = withContext(Dispatchers.IO) {
        loadTask(taskId)
    }

    /**
     * Deletes a task history.
     *
     * Removes the task and all associated files (screenshots) from storage.
     *
     * @param taskId Unique identifier of the task to delete
     *
     */
    suspend fun deleteTask(taskId: String) = withContext(Dispatchers.IO) {
        deleteTaskFiles(taskId)
        _historyList.value = _historyList.value.filter { it.id != taskId }
        saveHistoryIndex()
    }

    /**
     * Deletes multiple task histories.
     *
     * Batch deletion of tasks and their associated files.
     *
     * @param taskIds Set of unique identifiers of tasks to delete
     *
     */
    suspend fun deleteTasks(taskIds: Set<String>) = withContext(Dispatchers.IO) {
        taskIds.forEach { taskId ->
            deleteTaskFiles(taskId)
        }
        _historyList.value = _historyList.value.filter { it.id !in taskIds }
        saveHistoryIndex()
    }

    /**
     * Clears all history.
     *
     * Removes all task histories and their associated files from storage.
     *
     */
    suspend fun clearAllHistory() = withContext(Dispatchers.IO) {
        historyDir.listFiles()?.forEach { it.deleteRecursively() }
        _historyList.value = emptyList()
        saveHistoryIndex()
    }

    /**
     * Gets the screenshot bitmap for a step.
     *
     * Loads and decodes a screenshot image from the given file path.
     *
     * @param path Absolute file path to the screenshot, or null
     * @return Decoded [Bitmap] if the file exists and is valid, null otherwise
     *
     */
    fun getScreenshotBitmap(path: String?): Bitmap? {
        if (path == null) return null
        val file = File(path)
        if (!file.exists()) return null
        return BitmapFactory.decodeFile(path)
    }

    // Private helper methods

    /**
     * Saves raw WebP bytes directly to file (no re-compression).
     *
     * @param taskId Task identifier for directory organization
     * @param stepNumber Step number for filename
     * @param webpBytes Raw WebP image bytes
     * @param annotated Whether this is an annotated screenshot
     * @return Absolute file path of the saved screenshot
     */
    private fun saveScreenshotBytes(
        taskId: String,
        stepNumber: Int,
        webpBytes: ByteArray,
        annotated: Boolean,
    ): String {
        val taskDir = File(historyDir, taskId).also { it.mkdirs() }
        val suffix = if (annotated) "_annotated" else ""
        val file = File(taskDir, "step_${stepNumber}$suffix.webp")

        FileOutputStream(file).use { out ->
            out.write(webpBytes)
        }

        return file.absolutePath
    }

    /**
     * Saves bitmap as WebP (used for annotated screenshots).
     *
     * @param taskId Task identifier for directory organization
     * @param stepNumber Step number for filename
     * @param bitmap Bitmap to save
     * @param annotated Whether this is an annotated screenshot
     * @return Absolute file path of the saved screenshot
     */
    private fun saveScreenshotBitmap(taskId: String, stepNumber: Int, bitmap: Bitmap, annotated: Boolean): String {
        val taskDir = File(historyDir, taskId).also { it.mkdirs() }
        val suffix = if (annotated) "_annotated" else ""
        val file = File(taskDir, "step_${stepNumber}$suffix.webp")

        FileOutputStream(file).use { out ->
            @Suppress("DEPRECATION")
            val format =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Bitmap.CompressFormat.WEBP_LOSSY
                } else {
                    Bitmap.CompressFormat.WEBP
                }
            bitmap.compress(format, 85, out)
        }

        return file.absolutePath
    }

    /**
     * Flushes buffered steps into a direct-execution planning round (PhoneAgent-only tasks).
     */
    private fun flushPendingSteps(task: TaskHistory) {
        if (pendingSteps.isEmpty()) return
        task.planningRounds.add(
            LLMPlanningRound(
                round = task.planningRounds.size + 1,
                actionType = ACTION_DIRECT_EXECUTION,
                thinking = "",
                steps = pendingSteps.toMutableList(),
            ),
        )
        pendingSteps.clear()
    }

    /**
     * Saves a task's metadata to JSON file.
     *
     * Steps are nested under each planning round; there is no top-level steps array.
     *
     * @param task Task history to save
     */
    private fun saveTask(task: TaskHistory) {
        val taskDir = File(historyDir, task.id).also { it.mkdirs() }
        val metaFile = File(taskDir, "meta.json")

        val json =
            JSONObject().apply {
                put("id", task.id)
                put("taskDescription", task.taskDescription)
                put("startTime", task.startTime)
                put("endTime", task.endTime)
                put("success", task.success)
                put("completionMessage", task.completionMessage)

                val planningRoundsArray = JSONArray()
                task.planningRounds.forEach { planningRound ->
                    planningRoundsArray.put(planningRoundToJson(planningRound))
                }
                put("planningRounds", planningRoundsArray)
            }

        metaFile.writeText(json.toString(2))
    }

    /**
     * Loads a task from its JSON metadata file.
     *
     * Supports the current nested format and migrates legacy top-level steps arrays.
     *
     * @param taskId Task identifier to load
     * @return Loaded TaskHistory, or null if not found or invalid
     */
    private fun loadTask(taskId: String): TaskHistory? {
        val metaFile = File(historyDir, "$taskId/meta.json")
        if (!metaFile.exists()) return null

        return try {
            val json = JSONObject(metaFile.readText())

            val planningRounds = mutableListOf<LLMPlanningRound>()
            val planningRoundsArray = json.optJSONArray("planningRounds")
            if (planningRoundsArray != null) {
                for (i in 0 until planningRoundsArray.length()) {
                    planningRounds.add(planningRoundFromJson(planningRoundsArray.getJSONObject(i)))
                }
            }

            // Migrate legacy top-level steps into planning rounds
            val legacyStepsArray = json.optJSONArray("steps")
            if (legacyStepsArray != null && legacyStepsArray.length() > 0) {
                val legacySteps = parseStepsArray(legacyStepsArray)
                migrateLegacySteps(planningRounds, legacySteps)
            }

            TaskHistory(
                id = json.getString("id"),
                taskDescription = json.getString("taskDescription"),
                startTime = json.getLong("startTime"),
                endTime = json.optLong("endTime"),
                success = json.getBoolean("success"),
                completionMessage = json.optString("completionMessage").takeIf { it.isNotEmpty() },
                planningRounds = planningRounds,
            )
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to load task $taskId", e)
            null
        }
    }

    /**
     * Assigns legacy top-level steps to planning rounds that have no nested steps yet.
     */
    private fun migrateLegacySteps(
        planningRounds: MutableList<LLMPlanningRound>,
        legacySteps: MutableList<HistoryStep>,
    ) {
        if (legacySteps.isEmpty()) return

        if (planningRounds.isEmpty()) {
            planningRounds.add(
                LLMPlanningRound(
                    round = 1,
                    actionType = ACTION_DIRECT_EXECUTION,
                    thinking = "",
                    steps = legacySteps,
                ),
            )
            return
        }

        val updated = planningRounds.map { round ->
            if (round.steps.isNotEmpty()) {
                round
            } else if (round.actionType == "execute_subtask" && round.subTaskStepCount != null && round.subTaskStepCount > 0) {
                val count = minOf(round.subTaskStepCount, legacySteps.size)
                if (count > 0) {
                    val steps = legacySteps.take(count).toMutableList()
                    repeat(count) { legacySteps.removeAt(0) }
                    round.copy(steps = steps)
                } else {
                    round
                }
            } else {
                round
            }
        }.toMutableList()

        planningRounds.clear()
        planningRounds.addAll(updated)

        if (legacySteps.isNotEmpty()) {
            planningRounds.add(
                LLMPlanningRound(
                    round = planningRounds.size + 1,
                    actionType = ACTION_DIRECT_EXECUTION,
                    thinking = "",
                    steps = legacySteps,
                ),
            )
        }
    }

    private fun planningRoundToJson(planningRound: LLMPlanningRound): JSONObject =
        JSONObject().apply {
            put("round", planningRound.round)
            put("timestamp", planningRound.timestamp)
            put("thinking", planningRound.thinking)
            put("actionDescription", planningRound.actionDescription)
            put("actionType", planningRound.actionType)
            put("subTaskDescription", planningRound.subTaskDescription)
            put("subTaskId", planningRound.subTaskId)
            put("subTaskSuccess", planningRound.subTaskSuccess)
            put("subTaskStepCount", planningRound.subTaskStepCount)
            put("message", planningRound.message)
            planningRound.tokenUsage?.let { put("tokenUsage", tokenUsageToJson(it)) }
            planningRound.brainTokenUsage?.let { put("brainTokenUsage", tokenUsageToJson(it)) }

            val stepsArray = JSONArray()
            planningRound.steps.forEach { stepsArray.put(stepToJson(it)) }
            put("steps", stepsArray)
        }

    private fun planningRoundFromJson(roundJson: JSONObject): LLMPlanningRound {
        val steps = roundJson.optJSONArray("steps")?.let { parseStepsArray(it) } ?: mutableListOf()
        // Legacy meta.json may still have observation; prefer it as message.
        val message =
            roundJson.optString("observation").takeIf { it.isNotEmpty() }
                ?: roundJson.optString("message").takeIf { it.isNotEmpty() }
        return LLMPlanningRound(
            round = roundJson.getInt("round"),
            timestamp = roundJson.getLong("timestamp"),
            thinking = roundJson.getString("thinking"),
            actionDescription = roundJson.optString("actionDescription", ""),
            actionType = roundJson.getString("actionType"),
            subTaskDescription = roundJson.optString("subTaskDescription").takeIf { it.isNotEmpty() },
            subTaskId = roundJson.optInt("subTaskId").takeIf { roundJson.has("subTaskId") && !roundJson.isNull("subTaskId") },
            subTaskSuccess = if (roundJson.has("subTaskSuccess") && !roundJson.isNull("subTaskSuccess")) roundJson.getBoolean("subTaskSuccess") else null,
            subTaskStepCount = roundJson.optInt("subTaskStepCount").takeIf { roundJson.has("subTaskStepCount") && !roundJson.isNull("subTaskStepCount") },
            message = message,
            tokenUsage = roundJson.optJSONObject("tokenUsage")?.let { tokenUsageFromJson(it) },
            brainTokenUsage = roundJson.optJSONObject("brainTokenUsage")?.let { tokenUsageFromJson(it) },
            steps = steps,
        )
    }

    private fun stepToJson(step: HistoryStep): JSONObject =
        JSONObject().apply {
            put("stepNumber", step.stepNumber)
            put("timestamp", step.timestamp)
            put("thinking", step.thinking)
            put("actionDescription", step.actionDescription)
            put("screenshotPath", step.screenshotPath)
            put("annotatedScreenshotPath", step.annotatedScreenshotPath)
            put("success", step.success)
            put("message", step.message)
            step.tokenUsage?.let { put("tokenUsage", tokenUsageToJson(it)) }
        }

    private fun parseStepsArray(stepsArray: JSONArray): MutableList<HistoryStep> {
        val steps = mutableListOf<HistoryStep>()
        for (i in 0 until stepsArray.length()) {
            steps.add(stepFromJson(stepsArray.getJSONObject(i)))
        }
        return steps
    }

    private fun stepFromJson(stepJson: JSONObject): HistoryStep =
        HistoryStep(
            stepNumber = stepJson.getInt("stepNumber"),
            timestamp = stepJson.getLong("timestamp"),
            thinking = stepJson.getString("thinking"),
            action = null,
            actionDescription = stepJson.getString("actionDescription"),
            screenshotPath = stepJson.optString("screenshotPath").takeIf { it.isNotEmpty() },
            annotatedScreenshotPath = stepJson.optString("annotatedScreenshotPath").takeIf { it.isNotEmpty() },
            success = stepJson.getBoolean("success"),
            message = stepJson.optString("message").takeIf { it.isNotEmpty() },
            tokenUsage = stepJson.optJSONObject("tokenUsage")?.let { tokenUsageFromJson(it) },
        )

    private fun tokenUsageToJson(usage: TokenUsage): JSONObject =
        JSONObject().apply {
            put("promptTokens", usage.promptTokens)
            put("completionTokens", usage.completionTokens)
            put("totalTokens", usage.totalTokens)
        }

    private fun tokenUsageFromJson(json: JSONObject): TokenUsage =
        TokenUsage(
            promptTokens = json.getInt("promptTokens"),
            completionTokens = json.getInt("completionTokens"),
            totalTokens = json.getInt("totalTokens"),
        )

    /**
     * Deletes all files associated with a task.
     *
     * @param taskId Task identifier whose files should be deleted
     */
    private fun deleteTaskFiles(taskId: String) {
        File(historyDir, taskId).deleteRecursively()
    }

    /**
     * Loads the history index from persistent storage.
     *
     * Populates [_historyList] with all saved task histories.
     */
    private fun loadHistoryIndex() {
        val indexFile = File(historyDir, INDEX_FILE)
        if (!indexFile.exists()) return

        try {
            val json = JSONArray(indexFile.readText())
            val list = mutableListOf<TaskHistory>()

            for (i in 0 until json.length()) {
                val taskId = json.getString(i)
                loadTask(taskId)?.let { list.add(it) }
            }

            _historyList.value = list
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to load history index", e)
        }
    }

    /**
     * Saves the history index to persistent storage.
     *
     * Writes the list of task IDs to the index file for quick loading on startup.
     */
    private fun saveHistoryIndex() {
        val indexFile = File(historyDir, INDEX_FILE)
        val json = JSONArray()
        _historyList.value.forEach { json.put(it.id) }
        indexFile.writeText(json.toString())
    }

    companion object {
        private const val TAG = "HistoryManager"
        private const val HISTORY_DIR = "task_history"
        private const val INDEX_FILE = "history_index.json"
        private const val MAX_HISTORY_COUNT = 50

        @Volatile
        private var instance: HistoryManager? = null

        /**
         * Gets the singleton instance of HistoryManager.
         *
         * @param context Android context, application context will be used
         * @return The singleton HistoryManager instance
         */
        fun getInstance(context: Context): HistoryManager = instance ?: synchronized(this) {
            instance ?: HistoryManager(context.applicationContext).also { instance = it }
        }
    }
}
