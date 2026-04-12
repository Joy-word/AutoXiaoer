package com.flowmate.autoxiaoer.task

/**
 * Indicates which agent produced a step, used for color-coding in the floating window.
 */
enum class StepSource {
    /** Step produced by LLMAgent (planning / ReAct round). */
    LLM_AGENT,

    /** Step produced by PhoneAgent (on-device action execution). */
    PHONE_AGENT,
}

/**
 * Data class representing a single step in task execution.
 *
 * Used for the waterfall display in the floating window to show
 * the history of steps executed during a task.
 *
 * For LLM_AGENT steps, the lifecycle is:
 *   thinking → action (sub-task dispatched) → observation (sub-task result)
 *
 * For PHONE_AGENT steps:
 *   thinking → action (device action executed)
 *
 * @property stepNumber The sequential number of this step in the task execution
 * @property thinking The model's reasoning/thinking text for this step
 * @property action The action being performed in this step
 * @property source Which agent produced this step (LLM_AGENT or PHONE_AGENT)
 * @property subTaskName Name/description of the sub-task (LLM_AGENT steps only)
 * @property observation LLMAgent's observation after a sub-task completes (LLM_AGENT steps only)
 */
data class TaskStep(
    val stepNumber: Int,
    val thinking: String,
    val action: String,
    val source: StepSource = StepSource.PHONE_AGENT,
    val subTaskName: String = "",
    val observation: String = "",
)
