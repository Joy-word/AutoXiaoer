package com.flowmate.autoxiaoer.history

import com.flowmate.autoxiaoer.action.AgentAction
import com.flowmate.autoxiaoer.model.TokenUsage
import java.util.UUID

/**
 * Represents a single step in task execution history.
 *
 * Each step captures the model's thinking, the action taken, and the result.
 * Screenshots are stored as file paths to avoid memory issues.
 *
 * @property stepNumber Step number within the sub-task or direct PhoneAgent run (1-based)
 * @property planningRound LLM planning round that owns this step, or null for legacy / direct execution
 * @property timestamp Unix timestamp when the step was recorded
 * @property thinking Model's reasoning/thinking for this step
 * @property action The agent action executed, or null if no action
 * @property actionDescription Human-readable description of the action
 * @property screenshotPath File path to the original screenshot, or null
 * @property annotatedScreenshotPath File path to the annotated screenshot, or null
 * @property success Whether the step executed successfully
 * @property message Optional additional message or error details
 * @property tokenUsage Token consumption for this step's model call, or null if unavailable
 *
 */
data class HistoryStep(
    val stepNumber: Int,
    val planningRound: Int? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val thinking: String,
    val action: AgentAction?,
    val actionDescription: String,
    val screenshotPath: String?,
    val annotatedScreenshotPath: String?,
    val success: Boolean,
    val message: String? = null,
    val tokenUsage: TokenUsage? = null,
) {
    /** Display label: "R{round}-{step}" when tied to a planning round, else step number only. */
    fun displayLabel(): String =
        if (planningRound != null) {
            "R$planningRound-$stepNumber"
        } else {
            stepNumber.toString()
        }
}

/**
 * Represents a single ReAct planning round executed by [com.flowmate.autoxiaoer.agent.LLMAgent].
 *
 * Each round captures the LLM's reasoning, the action it decided to take, and
 * (when a sub-task was dispatched) the result fed back as an observation.
 *
 * @property round Sequential planning round number (1-based)
 * @property timestamp Unix timestamp when the round was recorded
 * @property thinking LLM's reasoning/thinking text for this round
 * @property actionDescription Raw JSON/text inside the model's `<action>...</action>` block
 * @property actionType One of "execute_subtask", "finish", "request_user", or "unknown"
 * @property subTaskDescription Description of the sub-task dispatched, or null if not applicable
 * @property subTaskId Transient ID of the sub-task, or null if not applicable
 * @property subTaskSuccess Whether the sub-task executed successfully, or null if not applicable
 * @property subTaskStepCount Number of PhoneAgent steps the sub-task consumed, or null
 * @property message Action result or feedback text fed back after execution (full text)
 * @property tokenUsage Token consumption for the LLMAgent (controller) call in this round, or null if unavailable
 * @property brainTokenUsage Token consumption for the BrainLLM call in this round (only set for
 *   [actionType] == "request_brain"), or null if unavailable or not applicable
 * @property steps PhoneAgent execution steps belonging to this round (e.g. sub-task steps)
 */
data class LLMPlanningRound(
    val round: Int,
    val timestamp: Long = System.currentTimeMillis(),
    val thinking: String,
    val actionDescription: String = "",
    val actionType: String,
    val subTaskDescription: String? = null,
    val subTaskId: Int? = null,
    val subTaskSuccess: Boolean? = null,
    val subTaskStepCount: Int? = null,
    val message: String? = null,
    val tokenUsage: TokenUsage? = null,
    val brainTokenUsage: TokenUsage? = null,
    val steps: MutableList<HistoryStep> = mutableListOf(),
)

/** Action type for PhoneAgent-only tasks with no LLMAgent planning rounds. */
const val ACTION_DIRECT_EXECUTION = "direct_execution"

/**
 * Represents a complete task execution history.
 *
 * Contains all information about a task execution including metadata,
 * status, and all recorded steps.
 *
 * @property id Unique identifier for the task (UUID)
 * @property taskDescription Human-readable description of the task
 * @property startTime Unix timestamp when the task started
 * @property endTime Unix timestamp when the task ended, or null if still running
 * @property success Whether the task completed successfully
 * @property completionMessage Optional message describing the completion result
 * @property planningRounds List of all planning rounds, each optionally containing PhoneAgent steps
 *
 */
data class TaskHistory(
    val id: String = UUID.randomUUID().toString(),
    val taskDescription: String,
    val startTime: Long = System.currentTimeMillis(),
    var endTime: Long? = null,
    var success: Boolean = false,
    var completionMessage: String? = null,
    val planningRounds: MutableList<LLMPlanningRound> = mutableListOf(),
) {
    /** Duration of the task in milliseconds. */
    val duration: Long
        get() = (endTime ?: System.currentTimeMillis()) - startTime

    /** Number of PhoneAgent steps recorded across all planning rounds. */
    val stepCount: Int
        get() = planningRounds.sumOf { it.steps.size }

    /** Number of planning rounds recorded in this task. */
    val planningRoundCount: Int
        get() = planningRounds.size

    /** All PhoneAgent steps in round order. */
    fun allSteps(): List<HistoryStep> = planningRounds.flatMap { it.steps }
}

/**
 * Action annotation info for drawing on screenshots.
 *
 * Sealed class hierarchy representing different types of visual annotations
 * that can be drawn on screenshots to indicate user actions.
 *
 */
sealed class ActionAnnotation {
    /**
     * Circle annotation for tap actions.
     *
     * @property x X coordinate in relative units (0-1000)
     * @property y Y coordinate in relative units (0-1000)
     * @property screenWidth Actual screen width in pixels
     * @property screenHeight Actual screen height in pixels
     */
    data class TapCircle(val x: Int, val y: Int, val screenWidth: Int, val screenHeight: Int) : ActionAnnotation()

    /**
     * Arrow annotation for swipe actions.
     *
     * @property startX Start X coordinate in relative units (0-1000)
     * @property startY Start Y coordinate in relative units (0-1000)
     * @property endX End X coordinate in relative units (0-1000)
     * @property endY End Y coordinate in relative units (0-1000)
     * @property screenWidth Actual screen width in pixels
     * @property screenHeight Actual screen height in pixels
     */
    data class SwipeArrow(
        val startX: Int,
        val startY: Int,
        val endX: Int,
        val endY: Int,
        val screenWidth: Int,
        val screenHeight: Int,
    ) : ActionAnnotation()

    /**
     * Long press annotation (circle with duration indicator).
     *
     * @property x X coordinate in relative units (0-1000)
     * @property y Y coordinate in relative units (0-1000)
     * @property screenWidth Actual screen width in pixels
     * @property screenHeight Actual screen height in pixels
     * @property durationMs Duration of the long press in milliseconds
     */
    data class LongPressCircle(
        val x: Int,
        val y: Int,
        val screenWidth: Int,
        val screenHeight: Int,
        val durationMs: Int,
    ) : ActionAnnotation()

    /**
     * Double tap annotation (two concentric circles).
     *
     * @property x X coordinate in relative units (0-1000)
     * @property y Y coordinate in relative units (0-1000)
     * @property screenWidth Actual screen width in pixels
     * @property screenHeight Actual screen height in pixels
     */
    data class DoubleTapCircle(val x: Int, val y: Int, val screenWidth: Int, val screenHeight: Int) :
        ActionAnnotation()

    /**
     * Text annotation for type actions.
     *
     * @property text The text that was typed
     */
    data class TypeText(val text: String) : ActionAnnotation()

    /**
     * Batch annotation for multiple sequential actions.
     *
     * Shows numbered circles/arrows for each step in the batch.
     *
     * @property steps List of individual action annotations
     * @property screenWidth Actual screen width in pixels
     * @property screenHeight Actual screen height in pixels
     */
    data class BatchSteps(val steps: List<ActionAnnotation>, val screenWidth: Int, val screenHeight: Int) :
        ActionAnnotation()

    /**
     * No visual annotation needed.
     */
    data object None : ActionAnnotation()
}
