package com.flowmate.autoxiaoer.agent.tools

import com.flowmate.autoxiaoer.agent.ScreenshotReviewLevel
import com.flowmate.autoxiaoer.agent.SubTask
import com.flowmate.autoxiaoer.util.Logger
import kotlinx.coroutines.isActive
import kotlinx.coroutines.coroutineScope
import org.json.JSONObject

/**
 * Dispatches a sub-task to PhoneAgent and feeds the result back as a tool observation.
 *
 * Mirrors the legacy `execute_subtask` action including:
 * - resolving `brain:` prefixed entries in `preGeneratedTexts` via [com.flowmate.autoxiaoer.agent.BrainLLM]
 * - binding the planning round to the history manager so PhoneAgent's steps appear under the round
 * - building a friend-readable observation from the [com.flowmate.autoxiaoer.agent.SubTaskResult]
 */
class ExecuteSubtaskTool : AgentTool {
    override val name: String = NAME
    override val description: String =
        "Dispatch one concrete operation to phone-agent (the executor that drives the screen). " +
            "Issue ONE sub-task at a time and wait for the observation before the next step. " +
            "preGeneratedTexts must contain any text that needs to be typed verbatim during the sub-task; " +
            "pass an empty object {} when no text input is needed."
    override val parametersSchema =
        objectSchema(required = listOf("description")) {
            stringField(
                "description",
                "Specific, actionable instruction including target app, screen, and action.",
            )
            stringMapField(
                "preGeneratedTexts",
                "Map of purpose label → exact text to type. " +
                    "Human-facing wording must come from request_brain first. " +
                    "Pass an empty object when no text input is needed.",
            )
        }

    override suspend fun execute(args: JSONObject, ctx: ToolContext): ToolResult = coroutineScope {
        val description = args.optString("description").ifBlank {
            return@coroutineScope ToolResult.Continue(
                if (ctx.isEnglish) "execute_subtask.description must not be blank."
                else "execute_subtask.description 不能为空。",
            )
        }

        val preGeneratedTexts = mutableMapOf<String, String>()
        args.optJSONObject("preGeneratedTexts")?.let { textsJson ->
            textsJson.keys().forEach { key ->
                preGeneratedTexts[key] = textsJson.optString(key)
            }
        }

        val rawSubTask = SubTask(
            id = (System.currentTimeMillis().toInt() and 0xFFFF),
            description = description,
            preGeneratedTexts = preGeneratedTexts,
        )

        // Capture timestamp BEFORE running, so the planning round sorts before the steps it owns.
        val planningRoundTimestamp = System.currentTimeMillis()

        // Resolve brain-delegated preGeneratedTexts.
        val resolved = resolveBrainTexts(rawSubTask, ctx)

        Logger.i(TAG, "Dispatching SubTask ${resolved.id}: ${resolved.description.take(80)}")
        ctx.listener?.onSubTaskStarted(resolved)

        // Bind the upcoming PhoneAgent steps to the active planning round so the history shows them under it.
        val historyManager = ctx.historyManager
        val planningRound = ctx.currentPlanningRound

        val subTaskResult = try {
            if (historyManager != null && planningRound != null) {
                historyManager.setRecordingPlanningRound(planningRound)
            }
            ctx.phoneAgent.runSubTask(resolved)
        } finally {
            historyManager?.clearRecordingPlanningRound()
        }

        Logger.i(
            TAG,
            "SubTask ${resolved.id} done: success=${subTaskResult.success}, " +
                "steps=${subTaskResult.stepCount}, summary=${subTaskResult.summary.take(100)}",
        )
        ctx.listener?.onSubTaskCompleted(subTaskResult)

        if (ctx.cancelRequested.get() || !isActive) {
            return@coroutineScope ToolResult.Terminate(
                success = false,
                message = if (ctx.isEnglish) "Task cancelled" else "任务已取消",
            )
        }

        val observation = buildObservation(resolved, subTaskResult, ctx)
        ctx.listener?.onObservationReceived(resolved, subTaskResult, observation)

        val reviewScreenshot = when (ctx.config.screenshotReviewLevel) {
            ScreenshotReviewLevel.NONE -> null
            ScreenshotReviewLevel.ON_FAILURE -> subTaskResult.lastScreenshotBase64.takeIf { !subTaskResult.success }
            ScreenshotReviewLevel.EVERY_ROUND -> subTaskResult.lastScreenshotBase64
        }

        val finalObservation = if (reviewScreenshot != null) {
            observation + "\n\n" + if (ctx.isEnglish) {
                "[Screenshot Review] The last step's screenshot is attached. Please verify the screen state matches the expected outcome."
            } else {
                "【截图回检】已附带最后一步截图，请结合截图核对任务完成情况。"
            }
        } else {
            observation
        }

        ToolResult.Continue(
            observation = finalObservation,
            subTaskMeta = SubTaskMeta(
                subTaskDescription = resolved.description,
                subTaskId = resolved.id,
                subTaskSuccess = subTaskResult.success,
                subTaskStepCount = subTaskResult.stepCount,
                planningRoundTimestamp = planningRoundTimestamp,
            ),
            reviewScreenshotBase64 = reviewScreenshot,
        )
    }

    private suspend fun resolveBrainTexts(subTask: SubTask, ctx: ToolContext): SubTask {
        val brainLLM = ctx.brainLLM ?: return subTask
        val original = subTask.preGeneratedTexts
        if (original.isEmpty()) return subTask
        val hasBrainKeys = original.keys.any { it.startsWith(BRAIN_KEY_PREFIX) }
        if (!hasBrainKeys) return subTask

        val trigger = ctx.triggerContext
        val resolved = mutableMapOf<String, String>()
        for ((key, value) in original) {
            if (key.startsWith(BRAIN_KEY_PREFIX)) {
                val purpose = key.removePrefix(BRAIN_KEY_PREFIX)
                val incoming = if (!trigger?.notificationContent.isNullOrBlank()) {
                    mapOf(
                        "sender" to (trigger?.clawBotFromUserId ?: ""),
                        "content" to (trigger?.notificationContent ?: ""),
                    )
                } else {
                    emptyMap()
                }
                val generated = brainLLM.generateMessage(
                    recipient = trigger?.clawBotFromUserId ?: if (ctx.isEnglish) "friend" else "朋友",
                    incomingMessage = incoming,
                    intent = value,
                    facts = emptyMap(),
                    conversationBrief = if (purpose.isNotBlank()) purpose else null,
                    language = ctx.config.language,
                ).text
                resolved[purpose] = generated ?: if (ctx.isEnglish) "$value (unfiltered)" else "$value（没过脑子版）"
            } else {
                resolved[key] = value
            }
        }
        return subTask.copy(preGeneratedTexts = resolved)
    }

    private fun buildObservation(
        subTask: SubTask,
        result: com.flowmate.autoxiaoer.agent.SubTaskResult,
        ctx: ToolContext,
    ): String {
        if (ctx.isEnglish) {
            return if (result.success) {
                """
                [Sub-task Result]
                Step ${subTask.id} "${subTask.description}" completed successfully.
                Executed ${result.stepCount} action steps.
                Summary: ${result.summary}

                Decide your next action based on this result.
                """.trimIndent()
            } else if (result.needsUserTakeOver) {
                val reason = result.failureReason ?: result.summary
                """
                [Sub-task Result]
                Step ${subTask.id} "${subTask.description}" needs user takeover.
                Reason: $reason

                Decide whether to continue, adjust strategy, or request user intervention.
                """.trimIndent()
            } else {
                val reason = result.failureReason ?: result.summary
                buildString {
                    appendLine("[Sub-task Result]")
                    appendLine("Step ${subTask.id} \"${subTask.description}\" failed.")
                    appendLine("Executed ${result.stepCount} action steps.")
                    appendLine("Failure reason: $reason")
                    if (result.lastStepAction != null) appendLine("Last action: ${result.lastStepAction}")
                    if (!result.lastStepThinking.isNullOrBlank()) appendLine("Last thinking: ${result.lastStepThinking}")
                    appendLine()
                    append("Replan: try a different way to reach the same goal, skip this step, or escalate to the user.")
                }
            }
        }
        return if (result.success) {
            """
            【子任务执行结果】
            步骤 ${subTask.id}「${subTask.description}」已成功完成。
            执行了 ${result.stepCount} 个操作步骤。
            结果摘要：${result.summary}

            请根据上述结果决定下一步操作。
            """.trimIndent()
        } else if (result.needsUserTakeOver) {
            val reason = result.failureReason ?: result.summary
            """
            【子任务执行结果】
            步骤 ${subTask.id}「${subTask.description}」需要用户介入。
            原因：$reason

            请决定是否继续、调整策略，或请求用户处理。
            """.trimIndent()
        } else {
            val reason = result.failureReason ?: result.summary
            buildString {
                appendLine("【子任务执行结果】")
                appendLine("步骤 ${subTask.id}「${subTask.description}」执行失败。")
                appendLine("执行了 ${result.stepCount} 个操作步骤。")
                appendLine("失败原因：$reason")
                if (result.lastStepAction != null) appendLine("最后执行的操作：${result.lastStepAction}")
                if (!result.lastStepThinking.isNullOrBlank()) appendLine("最后一步的思考：${result.lastStepThinking}")
                appendLine()
                append("请重新规划：可以尝试不同方式完成同一目标，或跳过此步骤继续，或请求用户介入。")
            }
        }
    }

    companion object {
        const val NAME = "execute_subtask"
        private const val TAG = "ExecuteSubtaskTool"

        /**
         * If a preGeneratedTexts key starts with this prefix the value is treated as a
         * "brain consultation request" — the value is the intent description and the
         * actual text will be generated by [com.flowmate.autoxiaoer.agent.BrainLLM] at runtime.
         */
        const val BRAIN_KEY_PREFIX = "brain:"
    }
}
