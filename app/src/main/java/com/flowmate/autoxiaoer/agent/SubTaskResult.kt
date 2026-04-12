package com.flowmate.autoxiaoer.agent

/**
 * The outcome of a [SubTask] executed by [PhoneAgent], reported back to [LLMAgent].
 *
 * The summary is derived directly from PhoneAgent's Finish(message) text or failure
 * reason — no additional LLM summarisation call is required.
 *
 * @property subTaskId The [SubTask.id] this result corresponds to
 * @property success Whether PhoneAgent completed the sub-task successfully
 * @property summary Human-readable description of what happened (Finish message or error)
 * @property stepCount Number of individual action steps PhoneAgent executed
 * @property failureReason Populated when [success] is false; describes why execution failed
 * @property needsUserTakeOver true when PhoneAgent issued a TakeOver action, indicating
 *   the user must intervene before execution can continue
 * @property lastStepThinking The model's thinking/reasoning text from the final step,
 *   useful for LLMAgent to understand why PhoneAgent stopped
 * @property lastStepAction Human-readable description of the last action executed
 *   (e.g. "点击 (230, 450)"), null if no action was executed
 * @property lastStepMessage The message returned by the final step (Finish message,
 *   error description, or hint text)
 */
data class SubTaskResult(
    val subTaskId: Int,
    val success: Boolean,
    val summary: String,
    val stepCount: Int,
    val failureReason: String? = null,
    val needsUserTakeOver: Boolean = false,
    val lastStepThinking: String? = null,
    val lastStepAction: String? = null,
    val lastStepMessage: String? = null,
)
