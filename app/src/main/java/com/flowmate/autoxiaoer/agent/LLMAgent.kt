package com.flowmate.autoxiaoer.agent

import android.content.Context
import com.flowmate.autoxiaoer.config.LLMAgentPrompts
import com.flowmate.autoxiaoer.history.HistoryManager
import com.flowmate.autoxiaoer.history.LLMPlanningRound
import com.flowmate.autoxiaoer.model.ModelClient
import com.flowmate.autoxiaoer.model.ModelResult
import com.flowmate.autoxiaoer.schedule.RepeatType
import com.flowmate.autoxiaoer.schedule.ScheduledTask
import com.flowmate.autoxiaoer.schedule.ScheduledTaskManager
import com.flowmate.autoxiaoer.task.TriggerContext
import com.flowmate.autoxiaoer.task.TriggerType
import com.flowmate.autoxiaoer.util.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Result of the full task execution by [LLMAgent].
 */
data class LLMTaskResult(
    val success: Boolean,
    val message: String,
    val planningRounds: Int,
)

/**
 * Listener for [LLMAgent] lifecycle events, enabling UI updates.
 */
interface LLMAgentListener {
    /** Called at the start of each ReAct planning round. */
    fun onPlanningRoundStarted(round: Int)

    /** Called when the LLM has produced its thinking text for the current round. */
    fun onThinkingUpdate(thinking: String)

    /** Called when a sub-task is about to be dispatched to PhoneAgent. */
    fun onSubTaskStarted(subTask: SubTask)

    /** Called when PhoneAgent has finished a sub-task. */
    fun onSubTaskCompleted(result: SubTaskResult)

    /**
     * Called after a sub-task completes and the observation message is built,
     * before it is fed back into the LLM context for the next round.
     *
     * @param subTask The sub-task that was executed
     * @param result The result returned by PhoneAgent
     * @param observation The human-readable observation text sent back to the LLM
     */
    fun onObservationReceived(subTask: SubTask, result: SubTaskResult, observation: String)

    /** Called when the overall task is done (success or failure). */
    fun onTaskFinished(result: LLMTaskResult)
}

/**
 * The "brain" layer of the two-agent architecture.
 *
 * [LLMAgent] implements a simple ReAct loop:
 *   1. **Think** — call the LLM to reason about the current state and decide on a sub-task
 *   2. **Act**   — dispatch the sub-task to [PhoneAgent] via [PhoneAgent.runSubTask]
 *   3. **Observe** — feed the sub-task result back into the LLM context and loop
 *
 * The agent terminates when the LLM emits a "finish" or "request_user" action,
 * or when [LLMAgentConfig.maxPlanningSteps] is exceeded.
 *
 * @param config LLM-agent configuration (independent from PhoneAgent's ModelConfig)
 * @param modelClient Pre-built [ModelClient] constructed from [config] by [ComponentManager]
 * @param phoneAgent The PhoneAgent used to execute sub-tasks
 */
class LLMAgent(
    private val config: LLMAgentConfig,
    private val modelClient: ModelClient,
    private val phoneAgent: PhoneAgent,
    private val historyManager: HistoryManager? = null,
    private val context: Context? = null,
) {
    private var listener: LLMAgentListener? = null

    /** Set to true when [cancel] is called; checked at each ReAct iteration boundary. */
    private val cancelRequested = AtomicBoolean(false)

    /** When true the ReAct loop will suspend at iteration boundaries until resumed. */
    private val pauseRequested = AtomicBoolean(false)

    fun setListener(listener: LLMAgentListener?) {
        this.listener = listener
    }

    /**
     * Requests cancellation of the current ReAct loop.
     *
     * The loop checks [cancelRequested] at each iteration boundary and after each
     * sub-task completes, so cancellation takes effect as soon as the current
     * in-flight LLM/PhoneAgent call returns.
     * Also cancels any ongoing [ModelClient] network request immediately.
     */
    fun cancel() {
        Logger.i(TAG, "Cancel requested")
        cancelRequested.set(true)
        pauseRequested.set(false)
        modelClient.cancelCurrentRequest()
    }

    /**
     * Pauses the ReAct loop at the next iteration boundary.
     *
     * While paused the loop suspends before issuing the next LLM call.
     * PhoneAgent is paused separately via [PhoneAgent.pause].
     */
    fun pause() {
        Logger.i(TAG, "Pause requested")
        pauseRequested.set(true)
    }

    /**
     * Resumes a paused ReAct loop.
     */
    fun resume() {
        Logger.i(TAG, "Resume requested")
        pauseRequested.set(false)
    }

    /**
     * Runs the full ReAct planning loop for the given task.
     *
     * @param taskDescription Natural-language description of the task to accomplish
     * @param triggerContext Optional context about how the task was triggered
     * @return [LLMTaskResult] with success/failure status and a summary message
     */
    suspend fun run(
        taskDescription: String,
        triggerContext: TriggerContext? = null,
    ): LLMTaskResult = coroutineScope {
        Logger.i(TAG, "LLMAgent starting task: ${taskDescription.take(80)}")

        // Reset control flags for this new run
        cancelRequested.set(false)
        pauseRequested.set(false)

        // Own the task history lifecycle: start recording before any planning rounds
        historyManager?.startTask(taskDescription)

        val systemPrompt = buildSystemPrompt()
        val context = LLMAgentContext(systemPrompt)

        // Build the initial user message, incorporating any trigger context
        val initialMessage = buildInitialMessage(taskDescription, triggerContext)
        context.addUserMessage(initialMessage)

        var round = 0

        try {
            while (round < config.maxPlanningSteps) {
                // ── Cancellation check (iteration boundary) ─────────────────
                if (cancelRequested.get() || !isActive) {
                    Logger.i(TAG, "LLMAgent cancelled before round ${round + 1}")
                    val result = LLMTaskResult(success = false, message = "任务已取消", planningRounds = round)
                    historyManager?.completeTask(false, result.message)
                    listener?.onTaskFinished(result)
                    return@coroutineScope result
                }

                // ── Pause check (iteration boundary) ────────────────────────
                while (pauseRequested.get() && !cancelRequested.get() && isActive) {
                    Logger.d(TAG, "LLMAgent paused, waiting to resume...")
                    delay(200)
                }
                if (cancelRequested.get() || !isActive) {
                    Logger.i(TAG, "LLMAgent cancelled while paused")
                    val result = LLMTaskResult(success = false, message = "任务已取消", planningRounds = round)
                    historyManager?.completeTask(false, result.message)
                    listener?.onTaskFinished(result)
                    return@coroutineScope result
                }

                round++
                Logger.i(TAG, "LLMAgent planning round $round / ${config.maxPlanningSteps}")
                listener?.onPlanningRoundStarted(round)

                // ── Think ──────────────────────────────────────────────────────
                val modelResult = modelClient.request(context.getMessages(), currentScreenshot = null)

                when (modelResult) {
                    is ModelResult.Error -> {
                        val msg = "LLM request failed: ${modelResult.error.message}"
                        Logger.e(TAG, msg)
                        val result = LLMTaskResult(success = false, message = msg, planningRounds = round)
                        historyManager?.completeTask(false, msg)
                        listener?.onTaskFinished(result)
                        return@coroutineScope result
                    }

                    is ModelResult.Success -> {
                        val response = modelResult.response
                        // val thinking = extractThinking(response.rawContent)
                        val thinking = response.thinking
                        Logger.d(TAG, "LLM thinking: ${thinking.take(200)}")
                        listener?.onThinkingUpdate(thinking)

                        // Store the assistant turn in context
                        context.addAssistantMessage(response.rawContent)

                        // ── Parse action ───────────────────────────────────────
                        val action = parseAction(response.rawContent)
                        if (action == null) {
                            Logger.w(TAG, "Could not parse action from LLM response, retrying...")
                            context.addUserMessage("你的上一次输出格式不正确，请严格按照要求的 <action>...</action> JSON 格式重新输出。")
                            continue
                        }

                        // ── Act ────────────────────────────────────────────────
                        when (action.type) {
                            ACTION_FINISH -> {
                                val msg = action.message ?: "任务已完成"
                                Logger.i(TAG, "LLMAgent finished: $msg")
                                historyManager?.recordPlanningRound(
                                    LLMPlanningRound(
                                        round = round,
                                        thinking = thinking,
                                        actionType = ACTION_FINISH,
                                        message = msg,
                                    ),
                                )
                                val result = LLMTaskResult(success = true, message = msg, planningRounds = round)
                                historyManager?.completeTask(true, msg)
                                listener?.onTaskFinished(result)
                                return@coroutineScope result
                            }

                            ACTION_REQUEST_USER -> {
                                val msg = action.message ?: "需要用户介入"
                                Logger.i(TAG, "LLMAgent requesting user: $msg")
                                historyManager?.recordPlanningRound(
                                    LLMPlanningRound(
                                        round = round,
                                        thinking = thinking,
                                        actionType = ACTION_REQUEST_USER,
                                        message = msg,
                                    ),
                                )
                                val result = LLMTaskResult(success = false, message = msg, planningRounds = round)
                                historyManager?.completeTask(false, msg)
                                listener?.onTaskFinished(result)
                                return@coroutineScope result
                            }

                            ACTION_EXECUTE_SUBTASK -> {
                                val subTask = action.subTask
                                if (subTask == null) {
                                    Logger.w(TAG, "execute_subtask action missing subtask field")
                                    context.addUserMessage("你输出的 execute_subtask 缺少 subtask 字段，请重新输出。")
                                    continue
                                }

                                // Capture timestamp now so the planning round sorts before the sub-task steps
                                val planningRoundTimestamp = System.currentTimeMillis()

                                Logger.i(TAG, "Dispatching SubTask ${subTask.id}: ${subTask.description.take(80)}")
                                listener?.onSubTaskStarted(subTask)

                                // ── Observe ────────────────────────────────────
                                val subTaskResult = phoneAgent.runSubTask(subTask)
                                Logger.i(
                                    TAG,
                                    "SubTask ${subTask.id} done: success=${subTaskResult.success}, " +
                                        "steps=${subTaskResult.stepCount}, summary=${subTaskResult.summary.take(100)}",
                                )
                                listener?.onSubTaskCompleted(subTaskResult)

                                // Check for cancellation after sub-task returns
                                if (cancelRequested.get() || !isActive) {
                                    Logger.i(TAG, "LLMAgent cancelled after sub-task ${subTask.id}")
                                    val result = LLMTaskResult(success = false, message = "任务已取消", planningRounds = round)
                                    historyManager?.completeTask(false, result.message)
                                    listener?.onTaskFinished(result)
                                    return@coroutineScope result
                                }

                                // Feed result back as user observation
                                val observation = buildObservationMessage(subTask, subTaskResult)
                                listener?.onObservationReceived(subTask, subTaskResult, observation)
                                context.addUserMessage(observation)

                                historyManager?.recordPlanningRound(
                                    LLMPlanningRound(
                                        round = round,
                                        timestamp = planningRoundTimestamp,
                                        thinking = thinking,
                                        actionType = ACTION_EXECUTE_SUBTASK,
                                        subTaskDescription = subTask.description,
                                        subTaskId = subTask.id,
                                        observation = observation,
                                        subTaskSuccess = subTaskResult.success,
                                        subTaskStepCount = subTaskResult.stepCount,
                                    ),
                                )
                            }

                            ACTION_SCHEDULE_TASK -> {
                                val params = action.scheduleTaskParams
                                if (params == null) {
                                    Logger.w(TAG, "schedule_task action missing required fields")
                                    context.addUserMessage("你输出的 schedule_task 缺少必要字段，请重新输出。")
                                    continue
                                }

                                val appContext = this@LLMAgent.context
                                val resultMessage = if (appContext != null) {
                                    try {
                                        val taskManager = ScheduledTaskManager.getInstance(appContext)
                                        val newTask = ScheduledTask(
                                            id = taskManager.generateTaskId(),
                                            taskDescription = params.taskDescription,
                                            taskBackground = params.taskBackground,
                                            scheduledTimeMillis = params.scheduledTimeMillis,
                                            repeatType = params.repeatType,
                                        )
                                        taskManager.saveTask(newTask)
                                        Logger.i(TAG, "Scheduled task created by LLM: id=${newTask.id}, desc=${params.taskDescription.take(50)}")
                                        val timeStr = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(params.scheduledTimeMillis))
                                        "日程已记录成功（id: ${newTask.id}）：「${params.taskDescription}」，执行时间：$timeStr，重复类型：${params.repeatType.name}"
                                    } catch (e: Exception) {
                                        Logger.e(TAG, "Failed to create scheduled task", e)
                                        "日程记录失败：${e.message}"
                                    }
                                } else {
                                    Logger.w(TAG, "Cannot create scheduled task: no Context available")
                                    "日程记录失败：缺少系统上下文，无法访问任务管理器"
                                }

                                historyManager?.recordPlanningRound(
                                    LLMPlanningRound(
                                        round = round,
                                        thinking = thinking,
                                        actionType = ACTION_SCHEDULE_TASK,
                                        message = resultMessage,
                                    ),
                                )

                                context.addUserMessage("【定时任务操作结果】\n$resultMessage\n\n请根据结果决定下一步操作。")
                            }

                            ACTION_QUERY_SCHEDULED_TASKS -> {
                                val appContext = this@LLMAgent.context
                                val resultMessage = if (appContext != null) {
                                    try {
                                        val taskManager = ScheduledTaskManager.getInstance(appContext)
                                        val tasks = taskManager.getAllTasks()
                                        if (tasks.isEmpty()) {
                                            "【当前日程列表】\n暂无任何日程安排。"
                                        } else {
                                            val fmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                                            val sb = StringBuilder("【当前日程列表】\n")
                                            tasks.forEachIndexed { index, task ->
                                                sb.appendLine("${index + 1}. id: ${task.id}")
                                                sb.appendLine("   描述：${task.taskDescription}")
                                                if (!task.taskBackground.isNullOrBlank()) {
                                                    sb.appendLine("   备注：${task.taskBackground}")
                                                }
                                                sb.appendLine("   执行时间：${fmt.format(java.util.Date(task.scheduledTimeMillis))}")
                                                sb.appendLine("   重复：${task.repeatType.name}  状态：${if (task.isEnabled) "已启用" else "已禁用"}")
                                            }
                                            sb.append("共 ${tasks.size} 个日程。")
                                            sb.toString()
                                        }
                                    } catch (e: Exception) {
                                        Logger.e(TAG, "Failed to query scheduled tasks", e)
                                        "日程查询失败：${e.message}"
                                    }
                                } else {
                                    "日程查询失败：缺少系统上下文"
                                }

                                historyManager?.recordPlanningRound(
                                    LLMPlanningRound(
                                        round = round,
                                        thinking = thinking,
                                        actionType = ACTION_QUERY_SCHEDULED_TASKS,
                                        message = resultMessage,
                                    ),
                                )
                                context.addUserMessage("$resultMessage\n\n请根据上述信息决定下一步操作。")
                            }

                            ACTION_UPDATE_SCHEDULED_TASK -> {
                                val params = action.updateScheduledTaskParams
                                if (params == null) {
                                    Logger.w(TAG, "update_scheduled_task action missing required fields")
                                    context.addUserMessage("你输出的 update_scheduled_task 缺少必要字段，请重新输出。")
                                    continue
                                }

                                val appContext = this@LLMAgent.context
                                val resultMessage = if (appContext != null) {
                                    try {
                                        val taskManager = ScheduledTaskManager.getInstance(appContext)
                                        val existing = taskManager.getTaskById(params.taskId)
                                        if (existing == null) {
                                            "更新失败：找不到 id 为「${params.taskId}」的日程"
                                        } else {
                                            val updated = existing.copy(
                                                taskDescription = params.taskDescription ?: existing.taskDescription,
                                                taskBackground = params.taskBackground ?: existing.taskBackground,
                                                scheduledTimeMillis = params.scheduledTimeMillis ?: existing.scheduledTimeMillis,
                                                repeatType = params.repeatType ?: existing.repeatType,
                                                isEnabled = params.isEnabled ?: existing.isEnabled,
                                            )
                                            taskManager.saveTask(updated)
                                            Logger.i(TAG, "Scheduled task updated by LLM: id=${params.taskId}")
                                            "日程已更新成功（id: ${params.taskId}）：「${updated.taskDescription}」"
                                        }
                                    } catch (e: Exception) {
                                        Logger.e(TAG, "Failed to update scheduled task", e)
                                        "日程更新失败：${e.message}"
                                    }
                                } else {
                                    "日程更新失败：缺少系统上下文"
                                }

                                historyManager?.recordPlanningRound(
                                    LLMPlanningRound(
                                        round = round,
                                        thinking = thinking,
                                        actionType = ACTION_UPDATE_SCHEDULED_TASK,
                                        message = resultMessage,
                                    ),
                                )
                                context.addUserMessage("【日程更新结果】\n$resultMessage\n\n请根据结果决定下一步操作。")
                            }

                            ACTION_DELETE_SCHEDULED_TASK -> {
                                val taskId = action.deleteTaskId
                                if (taskId == null) {
                                    Logger.w(TAG, "delete_scheduled_task action missing taskId")
                                    context.addUserMessage("你输出的 delete_scheduled_task 缺少 taskId 字段，请重新输出。")
                                    continue
                                }

                                val appContext = this@LLMAgent.context
                                val resultMessage = if (appContext != null) {
                                    try {
                                        val taskManager = ScheduledTaskManager.getInstance(appContext)
                                        val existing = taskManager.getTaskById(taskId)
                                        if (existing == null) {
                                            "删除失败：找不到 id 为「$taskId」的日程"
                                        } else {
                                            taskManager.deleteTask(taskId)
                                            Logger.i(TAG, "Scheduled task deleted by LLM: id=$taskId")
                                            "日程已删除（id: $taskId）：「${existing.taskDescription}」"
                                        }
                                    } catch (e: Exception) {
                                        Logger.e(TAG, "Failed to delete scheduled task", e)
                                        "日程删除失败：${e.message}"
                                    }
                                } else {
                                    "日程删除失败：缺少系统上下文"
                                }

                                historyManager?.recordPlanningRound(
                                    LLMPlanningRound(
                                        round = round,
                                        thinking = thinking,
                                        actionType = ACTION_DELETE_SCHEDULED_TASK,
                                        message = resultMessage,
                                    ),
                                )
                                context.addUserMessage("【日程删除结果】\n$resultMessage\n\n请根据结果决定下一步操作。")
                            }

                            else -> {
                                Logger.w(TAG, "Unknown action type: ${action.type}")
                                context.addUserMessage("未知的 action type \"${action.type}\"，请使用 execute_subtask、finish、request_user、schedule_task、query_scheduled_tasks、update_scheduled_task 或 delete_scheduled_task。")
                            }
                        }
                    }
                }
            }

            // Max planning steps exceeded
            val msg = "已达到最大规划步数上限（${config.maxPlanningSteps}），任务未能完成"
            Logger.w(TAG, msg)
            val result = LLMTaskResult(success = false, message = msg, planningRounds = round)
            historyManager?.completeTask(false, msg)
            listener?.onTaskFinished(result)
            result
        } catch (e: CancellationException) {
            Logger.i(TAG, "LLMAgent task cancelled")
            val result = LLMTaskResult(success = false, message = "任务已取消", planningRounds = round)
            historyManager?.completeTask(false, result.message)
            listener?.onTaskFinished(result)
            result
        } catch (e: Exception) {
            Logger.e(TAG, "LLMAgent unexpected error: ${e.message}", e)
            val result = LLMTaskResult(success = false, message = e.message ?: "未知错误", planningRounds = round)
            historyManager?.completeTask(false, result.message)
            listener?.onTaskFinished(result)
            result
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Prompt / message building
    // ──────────────────────────────────────────────────────────────────────────

    private fun buildSystemPrompt(): String {
        return if (config.customSystemPrompt.isNotBlank()) {
            config.customSystemPrompt
        } else {
            LLMAgentPrompts.getPrompt(config.language)
        }
    }

    private fun buildInitialMessage(taskDescription: String, triggerContext: TriggerContext?): String {
        val sb = StringBuilder()
        sb.appendLine("【用户任务】$taskDescription")

        if (triggerContext != null) {
            sb.appendLine()
            when (triggerContext.triggerType) {
                TriggerType.NOTIFICATION -> appendNotificationContext(sb, triggerContext)
                TriggerType.SCHEDULED -> {
                    sb.appendLine("【来自你自己的日程提醒】")
                    sb.appendLine("你之前安排了这个计划，现在是你设定的执行时间，请按计划行动。")
                    if (!triggerContext.scheduledTaskBackground.isNullOrBlank()) {
                        sb.appendLine("【当时的备注】${triggerContext.scheduledTaskBackground}")
                    }
                }
                TriggerType.VOICE -> sb.appendLine("【触发来源】语音指令触发")
                TriggerType.MANUAL -> { /* No extra context needed for manual triggers */ }
            }
        }

        sb.appendLine()
        sb.append("请开始规划并执行此任务。")
        return sb.toString().trimEnd()
    }

    /**
     * Appends raw notification fields to [sb] without interpretation.
     * The LLM receives the original values and reasons about them directly.
     */
    private fun appendNotificationContext(sb: StringBuilder, ctx: TriggerContext) {
        val appLabel = ctx.notificationApp ?: ctx.notificationPackageName ?: "未知应用"
        sb.appendLine("【触发来源】收到来自「$appLabel」的新通知（包名：${ctx.notificationPackageName ?: "未知"}）")

        sb.appendLine("【通知原始内容】")

        if (!ctx.notificationTitle.isNullOrBlank()) {
            sb.appendLine("- 标题：${ctx.notificationTitle}")
        }

        val body = ctx.notificationBigText?.takeIf { it.isNotBlank() }
            ?: ctx.notificationText?.takeIf { it.isNotBlank() }
        if (!body.isNullOrBlank()) {
            sb.appendLine("- 正文：$body")
        }

        if (!ctx.notificationSubText.isNullOrBlank()) {
            sb.appendLine("- 副标题：${ctx.notificationSubText}")
        }

        if (!ctx.notificationCategory.isNullOrBlank()) {
            sb.appendLine("- 通知类别：${ctx.notificationCategory}")
        }
    }

    private fun buildObservationMessage(subTask: SubTask, result: SubTaskResult): String {
        return if (result.success) {
            """
            【子任务执行结果】
            步骤 ${subTask.id}「${subTask.description}」已成功完成。
            执行了 ${result.stepCount} 个操作步骤。
            结果摘要：${result.summary}

            请根据上述结果决定下一步操作。
            """.trimIndent()
            } else {
                val reason = result.failureReason ?: result.summary
                if (result.needsUserTakeOver) {
                    """
                    【子任务执行结果】
                    步骤 ${subTask.id}「${subTask.description}」需要用户介入。
                    原因：$reason

                    请决定是否继续、调整策略，或请求用户处理。
                    """.trimIndent()
                } else {
                    val sb = StringBuilder()
                    sb.appendLine("【子任务执行结果】")
                    sb.appendLine("步骤 ${subTask.id}「${subTask.description}」执行失败。")
                    sb.appendLine("执行了 ${result.stepCount} 个操作步骤。")
                    sb.appendLine("失败原因：$reason")
                    if (result.lastStepAction != null) {
                        sb.appendLine("最后执行的操作：${result.lastStepAction}")
                    }
                    if (!result.lastStepThinking.isNullOrBlank()) {
                        sb.appendLine("最后一步的思考：${result.lastStepThinking}")
                    }
                    sb.appendLine()
                    sb.append("请重新规划：可以尝试不同方式完成同一目标，或跳过此步骤继续，或请求用户介入。")
                    sb.toString()
                }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Action parsing
    // ──────────────────────────────────────────────────────────────────────────

    private data class ScheduleTaskParams(
        val taskDescription: String,
        val taskBackground: String?,
        val scheduledTimeMillis: Long,
        val repeatType: RepeatType,
    )

    private data class UpdateScheduledTaskParams(
        val taskId: String,
        val taskDescription: String?,
        val taskBackground: String?,
        val scheduledTimeMillis: Long?,
        val repeatType: RepeatType?,
        val isEnabled: Boolean?,
    )

    /**
     * Parses a human-readable time string in "yyyy-MM-dd HH:mm" format into a Unix millisecond
     * timestamp. Returns null if the string is blank or unparseable.
     */
    private fun parseScheduledTime(timeStr: String): Long? {
        if (timeStr.isBlank()) return null
        return runCatching {
            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                .parse(timeStr)?.time
        }.getOrNull()
    }

    private data class ParsedAction(
        val type: String,
        val message: String?,
        val subTask: SubTask?,
        val scheduleTaskParams: ScheduleTaskParams? = null,
        val updateScheduledTaskParams: UpdateScheduledTaskParams? = null,
        val deleteTaskId: String? = null,
    )

    /**
     * Extracts the JSON object inside the first `<action>...</action>` block
     * and maps it to a [ParsedAction].
     *
     * Returns null when the block is missing or the JSON is malformed.
     */
    private fun parseAction(rawContent: String): ParsedAction? {
        return try {
            val actionBlock = extractBlock(rawContent, "action") ?: return null
            val json = JSONObject(actionBlock.trim())
            val type = json.optString("type").ifBlank { return null }

            when (type) {
                ACTION_FINISH, ACTION_REQUEST_USER -> {
                    ParsedAction(
                        type = type,
                        message = json.optString("message").ifBlank { null },
                        subTask = null,
                    )
                }

                ACTION_EXECUTE_SUBTASK -> {
                    val subtaskJson = json.optJSONObject("subtask") ?: return null
                    val description = subtaskJson.optString("description").ifBlank { return null }

                    val preGeneratedTexts = mutableMapOf<String, String>()
                    subtaskJson.optJSONObject("preGeneratedTexts")?.let { textsJson ->
                        textsJson.keys().forEach { key ->
                            preGeneratedTexts[key] = textsJson.optString(key)
                        }
                    }

                    val subtaskContext = subtaskJson.optString("context")

                    // Use round count + 1 as sequential id; caller increments separately.
                    // A stable id is not strictly required here — SubTaskResult just echoes it.
                    val subTask = SubTask(
                        id = System.currentTimeMillis().toInt() and 0xFFFF, // transient unique id
                        description = description,
                        preGeneratedTexts = preGeneratedTexts,
                        context = subtaskContext,
                    )

                    ParsedAction(type = type, message = null, subTask = subTask)
                }

                ACTION_SCHEDULE_TASK -> {
                    val taskDescription = json.optString("taskDescription").ifBlank { return null }
                    val taskBackground = json.optString("taskBackground").ifBlank { null }
                    val scheduledTimeMillis = parseScheduledTime(json.optString("scheduledTime")) ?: return null
                    val repeatTypeStr = json.optString("repeatType").ifBlank { RepeatType.ONCE.name }
                    val repeatType = runCatching { RepeatType.valueOf(repeatTypeStr.uppercase()) }
                        .getOrDefault(RepeatType.ONCE)
                    ParsedAction(
                        type = type,
                        message = null,
                        subTask = null,
                        scheduleTaskParams = ScheduleTaskParams(
                            taskDescription = taskDescription,
                            taskBackground = taskBackground,
                            scheduledTimeMillis = scheduledTimeMillis,
                            repeatType = repeatType,
                        ),
                    )
                }

                ACTION_QUERY_SCHEDULED_TASKS -> {
                    ParsedAction(type = type, message = null, subTask = null)
                }

                ACTION_UPDATE_SCHEDULED_TASK -> {
                    val taskId = json.optString("taskId").ifBlank { return null }
                    val taskDescription = json.optString("taskDescription").ifBlank { null }
                    val taskBackground = json.optString("taskBackground").ifBlank { null }
                    val scheduledTimeMillis = parseScheduledTime(json.optString("scheduledTime"))
                    val repeatTypeStr = json.optString("repeatType").ifBlank { null }
                    val repeatType = repeatTypeStr?.let {
                        runCatching { RepeatType.valueOf(it.uppercase()) }.getOrNull()
                    }
                    val isEnabled = if (json.has("isEnabled")) json.optBoolean("isEnabled") else null
                    ParsedAction(
                        type = type,
                        message = null,
                        subTask = null,
                        updateScheduledTaskParams = UpdateScheduledTaskParams(
                            taskId = taskId,
                            taskDescription = taskDescription,
                            taskBackground = taskBackground,
                            scheduledTimeMillis = scheduledTimeMillis,
                            repeatType = repeatType,
                            isEnabled = isEnabled,
                        ),
                    )
                }

                ACTION_DELETE_SCHEDULED_TASK -> {
                    val taskId = json.optString("taskId").ifBlank { return null }
                    ParsedAction(type = type, message = null, subTask = null, deleteTaskId = taskId)
                }

                else -> ParsedAction(type = type, message = null, subTask = null)
            }
        } catch (e: Exception) {
            Logger.w(TAG, "Failed to parse LLM action JSON: ${e.message}")
            null
        }
    }

    /**
     * Extracts the LLM's thinking text from the raw response.
     *
     * Tries <think>...</think> first (extended thinking models).
     * Falls back to any text before the <action> block so there is always something to show.
     */
    private fun extractThinking(rawContent: String): String {
        val fromThinkTag = extractBlock(rawContent, "think")?.trim()
        if (!fromThinkTag.isNullOrBlank()) return fromThinkTag

        // Fallback: text before <action>
        val actionStart = rawContent.indexOf("<action>")
        return if (actionStart > 0) rawContent.substring(0, actionStart).trim() else ""
    }

    /**
     * Extracts the content between `<tag>` and `</tag>` (first occurrence).
     */
    private fun extractBlock(text: String, tag: String): String? {
        val open = "<$tag>"
        val close = "</$tag>"
        val start = text.indexOf(open)
        val end = text.indexOf(close)
        if (start == -1 || end == -1 || end <= start) return null
        return text.substring(start + open.length, end)
    }

    companion object {
        private const val TAG = "LLMAgent"

        private const val ACTION_EXECUTE_SUBTASK = "execute_subtask"
        private const val ACTION_FINISH = "finish"
        private const val ACTION_REQUEST_USER = "request_user"
        private const val ACTION_SCHEDULE_TASK = "schedule_task"
        private const val ACTION_QUERY_SCHEDULED_TASKS = "query_scheduled_tasks"
        private const val ACTION_UPDATE_SCHEDULED_TASK = "update_scheduled_task"
        private const val ACTION_DELETE_SCHEDULED_TASK = "delete_scheduled_task"
    }
}
