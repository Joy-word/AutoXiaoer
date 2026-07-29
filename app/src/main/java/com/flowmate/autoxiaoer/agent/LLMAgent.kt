package com.flowmate.autoxiaoer.agent

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.flowmate.autoxiaoer.agent.tools.ExecuteSubtaskTool
import com.flowmate.autoxiaoer.agent.tools.FinishTool
import com.flowmate.autoxiaoer.agent.tools.RequestUserTool
import com.flowmate.autoxiaoer.agent.tools.SubTaskMeta
import com.flowmate.autoxiaoer.agent.tools.ToolContext
import com.flowmate.autoxiaoer.agent.tools.ToolRegistry
import com.flowmate.autoxiaoer.agent.tools.ToolResult
import com.flowmate.autoxiaoer.clawbot.ClawBotContextStore
import com.flowmate.autoxiaoer.config.LLMAgentPrompts
import com.flowmate.autoxiaoer.history.HistoryManager
import com.flowmate.autoxiaoer.history.LLMPlanningRound
import com.flowmate.autoxiaoer.history.TaskHistory
import com.flowmate.autoxiaoer.model.ChatMessage
import com.flowmate.autoxiaoer.model.ModelClient
import com.flowmate.autoxiaoer.model.ModelResponse
import com.flowmate.autoxiaoer.model.ModelResponseParser
import com.flowmate.autoxiaoer.model.ModelResult
import com.flowmate.autoxiaoer.model.ParsedToolCall
import com.flowmate.autoxiaoer.model.TokenUsage
import com.flowmate.autoxiaoer.model.ToolDto
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
     */
    fun onObservationReceived(subTask: SubTask, result: SubTaskResult, observation: String)

    /** Called when the overall task is done (success or failure). */
    fun onTaskFinished(result: LLMTaskResult)

    /**
     * Called when the task has failed and is about to be retried.
     */
    fun onTaskRetrying(attempt: Int, reason: String) {}
}

/**
 * The controller (控制者) layer of the two-agent architecture, implemented on top of
 * OpenAI Function-Calling.
 *
 * Each ReAct round:
 *   1. **Think** — call the LLM with the registered tool catalogue. The model returns
 *      `<think>三步思考</think>` in `assistant.content` and a single `tool_call`.
 *   2. **Act**   — dispatch the tool call to the matching [com.flowmate.autoxiaoer.agent.tools.AgentTool].
 *   3. **Observe** — the tool returns either a [ToolResult.Continue] observation (echoed
 *      back as `role: "tool"`) or a [ToolResult.Terminate] that ends the loop.
 *
 * Persona / interpersonal expression remains fully isolated in [BrainLLM] and is invoked
 * through the `request_brain` tool.
 *
 * @param config LLM-agent configuration (independent from PhoneAgent's ModelConfig)
 * @param modelClient Pre-built [ModelClient] constructed from [config] by [ComponentManager]
 * @param phoneAgent The PhoneAgent used to execute sub-tasks
 * @param brainLLM Optional [BrainLLM] for persona-aware text generation
 * @param toolRegistry Tool catalogue advertised to the model. Defaults to [ToolRegistry.default].
 */
class LLMAgent(
    private val config: LLMAgentConfig,
    private val modelClient: ModelClient,
    private val phoneAgent: PhoneAgent,
    private val historyManager: HistoryManager? = null,
    private val context: Context? = null,
    private val brainLLM: BrainLLM? = null,
    private val toolRegistry: ToolRegistry = ToolRegistry.default(),
) {
    private var listener: LLMAgentListener? = null

    /** Set to true when [cancel] is called; checked at each ReAct iteration boundary. */
    private val cancelRequested = AtomicBoolean(false)

    /** When true the ReAct loop will suspend at iteration boundaries until resumed. */
    private val pauseRequested = AtomicBoolean(false)

    fun setListener(listener: LLMAgentListener?) {
        this.listener = listener
    }

    /** Requests cancellation of the current ReAct loop. */
    fun cancel() {
        Logger.i(TAG, "Cancel requested")
        cancelRequested.set(true)
        pauseRequested.set(false)
        modelClient.cancelCurrentRequest()
    }

    /** Pauses the ReAct loop at the next iteration boundary. */
    fun pause() {
        Logger.i(TAG, "Pause requested")
        pauseRequested.set(true)
    }

    /** Resumes a paused ReAct loop. */
    fun resume() {
        Logger.i(TAG, "Resume requested")
        pauseRequested.set(false)
    }

    /**
     * Runs the full ReAct planning loop for the given task, with automatic retry on failure.
     *
     * The task will be attempted up to [LLMAgentConfig.maxTaskRetries] + 1 times in total.
     * User-initiated cancellation is never retried.
     */
    suspend fun run(
        taskDescription: String,
        triggerContext: TriggerContext? = null,
    ): LLMTaskResult {
        val maxAttempts = config.maxTaskRetries + 1
        var lastResult = LLMTaskResult(success = false, message = "未执行", planningRounds = 0)
        var previousAttemptHistory: TaskHistory? = null

        for (attempt in 1..maxAttempts) {
            val result = runOnce(taskDescription, triggerContext, previousAttemptHistory)
            lastResult = result

            if (result.success || cancelRequested.get() || result.message == "任务已取消") break

            if (attempt < maxAttempts) {
                Logger.i(TAG, "Task failed (attempt $attempt/$maxAttempts), retrying: ${result.message.take(80)}")
                previousAttemptHistory = historyManager?.historyList?.value?.firstOrNull()
                listener?.onTaskRetrying(attempt, result.message)
                cancelRequested.set(false)
                pauseRequested.set(false)
            }
        }

        return lastResult
    }

    /** Executes one attempt of the full ReAct planning loop. */
    private suspend fun runOnce(
        taskDescription: String,
        triggerContext: TriggerContext? = null,
        previousAttempt: TaskHistory? = null,
    ): LLMTaskResult = coroutineScope {
        Logger.i(TAG, "LLMAgent starting task: ${taskDescription.take(80)}")

        cancelRequested.set(false)
        pauseRequested.set(false)

        historyManager?.startTask(taskDescription)

        val systemPrompt = buildSystemPrompt()
        val ctx = LLMAgentContext(systemPrompt)
        ctx.addUserMessage(buildInitialMessage(taskDescription, triggerContext))
        if (previousAttempt != null) {
            ctx.addUserMessage(buildRetryContext(previousAttempt))
        }

        val toolContext = ToolContext(
            config = config,
            phoneAgent = phoneAgent,
            brainLLM = brainLLM,
            historyManager = historyManager,
            appContext = this@LLMAgent.context,
            triggerContext = triggerContext,
            listener = listener,
            cancelRequested = cancelRequested,
            pauseRequested = pauseRequested,
        )

        val advertisedTools = toolRegistry.openAIToolDtos()

        // Framework-managed plan state: retains the last <plan> block emitted by the model
        // so it is echoed back each round instead of relying on the model to retype it.
        var currentPlan = ""

        var round = 0
        try {
            while (round < config.maxPlanningSteps) {
                if (cancelRequested.get() || !isActive) {
                    return@coroutineScope finishCancelled(round)
                }
                while (pauseRequested.get() && !cancelRequested.get() && isActive) {
                    delay(PAUSE_POLL_MS)
                }
                if (cancelRequested.get() || !isActive) {
                    return@coroutineScope finishCancelled(round)
                }

                round++
                Logger.i(TAG, "LLMAgent planning round $round / ${config.maxPlanningSteps}")
                listener?.onPlanningRoundStarted(round)
                toolContext.currentPlanningRound = round

                ctx.addRoundContext(round, config.maxPlanningSteps, toolContext.isEnglish, currentPlan)

                val response = requestModel(ctx, advertisedTools)
                    ?: return@coroutineScope finishOnNetworkError(round)

                val thinking = response.thinking.ifBlank {
                    ModelResponseParser.parseLlmAgentThinking(response.rawContent)
                }
                Logger.d(TAG, "LLM thinking: ${thinking.take(200)}")
                listener?.onThinkingUpdate(thinking)

                val newPlan = ModelResponseParser.parseLlmAgentPlan(response.rawContent)
                if (newPlan == null) {
                    Logger.w(TAG, "LLM produced no <plan> block on round $round; nudging it")
                    ctx.addAssistantMessage(response.rawContent.ifBlank { "" })
                    ctx.addUserMessage(
                        if (toolContext.isEnglish) {
                            "You must output a <plan> block every round before your tool call, covering the task overview, done, and remaining items."
                        } else {
                            "每轮都必须先输出 <plan> 块，包含【任务全貌】【已完成】【待完成】三段，再进行工具调用。"
                        },
                    )
                    continue
                }
                if (newPlan != null) {
                    currentPlan = newPlan
                }

                val toolCall = response.toolCalls.firstOrNull()
                if (toolCall == null || toolCall.name.isBlank()) {
                    Logger.w(TAG, "LLM produced no tool_call; nudging it")
                    ctx.addAssistantMessage(response.rawContent.ifBlank { "" })
                    ctx.addUserMessage(
                        if (toolContext.isEnglish) {
                            "You must respond with a tool call. Pick the appropriate tool from the catalogue and call it now."
                        } else {
                            "请使用工具调用（tool_call）来回应。请从工具列表中选择合适的工具并发起调用，不要只回复纯文本。"
                        },
                    )
                    continue
                }

                ctx.addAssistantWithToolCalls(content = response.rawContent, toolCalls = listOf(toolCall))

                val tool = toolRegistry.find(toolCall.name)
                if (tool == null) {
                    val err = if (toolContext.isEnglish) {
                        "Unknown tool \"${toolCall.name}\". Pick a tool from the advertised catalogue."
                    } else {
                        "未知的 tool \"${toolCall.name}\"，请从已声明的工具列表中选择。"
                    }
                    ctx.addToolMessage(toolCall.id, toolCall.name, err)
                    historyManager?.recordPlanningRound(
                        LLMPlanningRound(
                            round = round,
                            thinking = thinking,
                            actionDescription = formatActionDescription(toolCall),
                            actionType = toolCall.name,
                            message = err,
                            tokenUsage = response.tokenUsage,
                            plan = newPlan,
                        ),
                    )
                    continue
                }

                val args = parseArguments(toolCall.arguments)
                if (args == null) {
                    val err = if (toolContext.isEnglish) {
                        "tool_call arguments are not valid JSON. Please retry."
                    } else {
                        "tool_call 的 arguments 不是合法 JSON，请重新输出。"
                    }
                    ctx.addToolMessage(toolCall.id, toolCall.name, err)
                    historyManager?.recordPlanningRound(
                        LLMPlanningRound(
                            round = round,
                            thinking = thinking,
                            actionDescription = formatActionDescription(toolCall),
                            actionType = toolCall.name,
                            message = err,
                            tokenUsage = response.tokenUsage,
                            plan = newPlan,
                        ),
                    )
                    continue
                }

                val result = tool.execute(args, toolContext)
                when (result) {
                    is ToolResult.Continue -> {
                        ctx.addToolMessage(toolCall.id, toolCall.name, result.observation)
                        historyManager?.recordPlanningRound(
                            buildPlanningRound(
                                round = round,
                                thinking = thinking,
                                toolCall = toolCall,
                                observation = result.observation,
                                roundTokenUsage = response.tokenUsage,
                                brainTokenUsage = result.brainTokenUsage,
                                subTaskMeta = result.subTaskMeta,
                                plan = newPlan,
                            ),
                        )
                        if (cancelRequested.get() || !isActive) {
                            return@coroutineScope finishCancelled(round)
                        }
                    }

                    is ToolResult.Terminate -> {
                        val obs = result.observation ?: result.message
                        historyManager?.recordPlanningRound(
                            buildPlanningRound(
                                round = round,
                                thinking = thinking,
                                toolCall = toolCall,
                                observation = obs,
                                roundTokenUsage = response.tokenUsage,
                                brainTokenUsage = null,
                                subTaskMeta = null,
                                plan = newPlan,
                            ),
                        )
                        val taskResult = LLMTaskResult(result.success, result.message, round)
                        historyManager?.completeTask(result.success, result.message)
                        listener?.onTaskFinished(taskResult)
                        return@coroutineScope taskResult
                    }
                }
            }

            // Max planning steps exceeded
            val msg = if (toolContext.isEnglish) {
                "Reached the maximum planning step limit (${config.maxPlanningSteps}); task did not finish."
            } else {
                "已达到最大规划步数上限（${config.maxPlanningSteps}），任务未能完成"
            }
            Logger.w(TAG, msg)
            val result = LLMTaskResult(success = false, message = msg, planningRounds = round)
            historyManager?.completeTask(false, msg)
            listener?.onTaskFinished(result)
            result
        } catch (e: CancellationException) {
            Logger.i(TAG, "LLMAgent task cancelled")
            finishCancelled(round)
        } catch (e: Exception) {
            Logger.e(TAG, "LLMAgent unexpected error: ${e.message}", e)
            val result = LLMTaskResult(success = false, message = e.message ?: "未知错误", planningRounds = round)
            historyManager?.completeTask(false, result.message)
            listener?.onTaskFinished(result)
            result
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Network helpers
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Issues one model request with one network-level retry on failure.
     * Returns null if both attempts failed or the loop was cancelled mid-retry.
     */
    private suspend fun requestModel(
        ctx: LLMAgentContext,
        tools: List<ToolDto>,
    ): ModelResponse? {
        logModelInput(ctx)
        val messages = if (config.limitContextRounds) {
            ctx.getTrimmedMessages(maxRounds = 3)
        } else {
            ctx.getMessages()
        }
        val first = modelClient.request(messages, currentScreenshot = null, tools = tools)
        if (first is ModelResult.Success) {
            logModelResponse(first.response)
            return first.response
        }

        val firstError = (first as ModelResult.Error).error.message
        Logger.e(TAG, "LLM request failed: $firstError")
        Logger.i(TAG, "Network error in LLMAgent, retrying after ${NETWORK_RETRY_DELAY_MS}ms...")
        delay(NETWORK_RETRY_DELAY_MS)
        if (cancelRequested.get()) return null

        val retry = modelClient.request(messages, currentScreenshot = null, tools = tools)
        if (retry is ModelResult.Success) {
            Logger.i(TAG, "LLM network retry succeeded")
            logModelResponse(retry.response)
            return retry.response
        }
        Logger.e(TAG, "LLM network retry also failed: ${(retry as ModelResult.Error).error.message}")
        return null
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Debug logging helpers for prompt optimisation
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Logs the last user message (current round input) and total message count
     * before each model request, so the prompt engineer can see what the LLM
     * is being asked each round without the noise of full conversation history.
     */
    private fun logModelInput(ctx: LLMAgentContext) {
        val messages = ctx.getMessages()
        val lastUser = messages.lastOrNull { it is ChatMessage.User } as? ChatMessage.User
        val inputPreview = lastUser?.text ?: "(no user message)"
        Logger.d(
            TAG,
            "📤 [LLM Input] totalMessages=${messages.size} | lastUserMessage:\n$inputPreview",
        )
    }

    /**
     * Logs the full model response (raw content, thinking, tool calls) after
     * each successful request, so the prompt engineer can correlate input→output.
     */
    private fun logModelResponse(response: ModelResponse) {
        val tcSummary = response.toolCalls.joinToString(", ") { "${it.name}(${it.arguments})" }
        Logger.d(
            TAG,
            "📥 [LLM Output] rawLen=${response.rawContent.length} | thinking=${response.thinking} | toolCalls=[$tcSummary] | rawContent:\n${response.rawContent}",
        )
    }

    private suspend fun finishCancelled(round: Int): LLMTaskResult {
        Logger.i(TAG, "LLMAgent cancelled at round $round")
        val result = LLMTaskResult(success = false, message = "任务已取消", planningRounds = round)
        historyManager?.completeTask(false, result.message)
        listener?.onTaskFinished(result)
        return result
    }

    private suspend fun finishOnNetworkError(round: Int): LLMTaskResult {
        val msg = "LLM request failed"
        val result = LLMTaskResult(success = false, message = msg, planningRounds = round)
        historyManager?.completeTask(false, msg)
        listener?.onTaskFinished(result)
        return result
    }

    private fun parseArguments(raw: String): JSONObject? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return JSONObject()
        return runCatching { JSONObject(trimmed) }.getOrNull()
    }

    /**
     * Encodes a tool call into the stable display string used for `actionDescription`
     * in [HistoryManager]. Format mirrors the OpenAI shape so [com.flowmate.autoxiaoer.agent.tools.GetTaskHistoryDetailTool]
     * can replay it back to the model verbatim.
     */
    private fun formatActionDescription(toolCall: ParsedToolCall): String {
        val argsValue: Any = runCatching { JSONObject(toolCall.arguments) }.getOrElse { toolCall.arguments }
        return JSONObject().apply {
            put("name", toolCall.name)
            put("arguments", argsValue)
        }.toString()
    }

    private fun buildPlanningRound(
        round: Int,
        thinking: String,
        toolCall: ParsedToolCall,
        observation: String,
        roundTokenUsage: TokenUsage?,
        brainTokenUsage: TokenUsage?,
        subTaskMeta: SubTaskMeta?,
        plan: String? = null,
    ): LLMPlanningRound {
        val timestamp = subTaskMeta?.planningRoundTimestamp ?: System.currentTimeMillis()
        return LLMPlanningRound(
            round = round,
            timestamp = timestamp,
            thinking = thinking,
            actionDescription = formatActionDescription(toolCall),
            actionType = toolCall.name,
            subTaskDescription = subTaskMeta?.subTaskDescription,
            subTaskId = subTaskMeta?.subTaskId,
            subTaskSuccess = subTaskMeta?.subTaskSuccess,
            subTaskStepCount = subTaskMeta?.subTaskStepCount,
            message = observation,
            tokenUsage = roundTokenUsage,
            brainTokenUsage = brainTokenUsage,
            plan = plan,
        )
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Prompt / message building
    // ──────────────────────────────────────────────────────────────────────────

    /** Returns the current battery percentage (0–100), or -1 if unavailable. */
    private fun getBatteryLevel(): Int {
        val ctx = context ?: return -1
        val intent = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return -1
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        return if (level >= 0 && scale > 0) level * 100 / scale else -1
    }

    private fun buildSystemPrompt(): String {
        return if (config.customSystemPrompt.isNotBlank()) {
            LLMAgentPrompts.applySubstitutions(config.customSystemPrompt, config.language)
        } else {
            LLMAgentPrompts.getPrompt(config.language)
        }
    }

    private fun buildInitialMessage(taskDescription: String, triggerContext: TriggerContext?): String {
        val sb = StringBuilder()
        sb.appendLine(LLMAgentPrompts.getCurrentDateTimePrefix(config.language))
        val brainConfigured = brainLLM != null
        val brainEnabled = brainLLM?.isEnabled == true
        sb.appendLine(LLMAgentPrompts.getBrainStatePrefix(config.language, brainConfigured, brainEnabled))
        val batteryPct = getBatteryLevel()
        val isEn = config.language.lowercase().let { it == "en" || it == "english" }
        if (batteryPct >= 0) {
            if (isEn) sb.appendLine("[Battery: $batteryPct%]") else sb.appendLine("【当前电量】$batteryPct%")
        }
        if (triggerContext?.triggerType == TriggerType.CLAWBOT && context != null) {
            val contextBlock = ClawBotContextStore.getInstance(context).formatForPrompt(isEn)
            if (contextBlock.isNotBlank()) {
                sb.appendLine(contextBlock)
                sb.appendLine()
            }
        }
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
                TriggerType.CLAWBOT -> {
                    sb.appendLine("【触发来源】ClawBot 消息")
                    sb.appendLine(
                        "【注意事项】如果需要回复用户消息，请调用 ${RequestUserTool.NAME} 工具发送回复，" +
                            "发送成功后你会收到反馈并继续执行后续步骤；如果已回复用户的提问，请调用 ${FinishTool.NAME} 工具结束任务。",
                    )
                    if (!triggerContext.clawBotFromUserId.isNullOrBlank()) {
                        sb.appendLine("【发送方】${triggerContext.clawBotFromUserId}")
                    }
                }
            }
        }

        sb.appendLine()
        sb.append("请开始规划并执行此任务。")
        return sb.toString().trimEnd()
    }

    private fun appendNotificationContext(sb: StringBuilder, ctx: TriggerContext) {
        val appLabel = ctx.notificationApp ?: ctx.notificationPackageName ?: "未知应用"
        sb.appendLine("【触发来源】收到来自「$appLabel」的新通知（包名：${ctx.notificationPackageName ?: "未知"}）")

        sb.appendLine("【通知原始内容】")

        if (!ctx.notificationTitle.isNullOrBlank()) {
            sb.appendLine("- 标题：${ctx.notificationTitle}")
        }

        if (ctx.notificationTexts.isNotEmpty()) {
            sb.appendLine("- 消息共 ${ctx.notificationTexts.size} 条（按时间顺序）：")
            ctx.notificationTexts.forEachIndexed { index, text ->
                sb.appendLine("  ${index + 1}. $text")
            }
        } else {
            val body = ctx.notificationBigText?.takeIf { it.isNotBlank() }
                ?: ctx.notificationText?.takeIf { it.isNotBlank() }
            if (!body.isNullOrBlank()) {
                sb.appendLine("- 正文：$body")
            }
        }

        if (!ctx.notificationSubText.isNullOrBlank()) {
            sb.appendLine("- 副标题：${ctx.notificationSubText}")
        }

        if (!ctx.notificationCategory.isNullOrBlank()) {
            sb.appendLine("- 通知类别：${ctx.notificationCategory}")
        }
    }

    /**
     * Builds a concise retry-context message from the last planning round of the
     * previous failed attempt.
     */
    private fun buildRetryContext(previousAttempt: TaskHistory): String {
        val isEn = config.language.lowercase().let { it == "en" || it == "english" }
        val lastRound = previousAttempt.planningRounds.lastOrNull()
        val sb = StringBuilder()

        if (isEn) {
            sb.appendLine("⚠️ [RETRY] The previous attempt failed. Please adjust your strategy based on the information below.")
            sb.appendLine("Failure reason: ${previousAttempt.completionMessage ?: "unknown"}")
            if (lastRound != null) {
                sb.appendLine()
                sb.appendLine("Last planning round before failure (round ${lastRound.round}):")
                if (lastRound.thinking.isNotBlank()) {
                    val brief = if (lastRound.thinking.length > 200) "${lastRound.thinking.take(200)}…" else lastRound.thinking
                    sb.appendLine("  Thinking: $brief")
                }
                sb.appendLine("  Action type: ${lastRound.actionType}")
                if (!lastRound.subTaskDescription.isNullOrBlank()) {
                    sb.appendLine("  Sub-task: ${lastRound.subTaskDescription}")
                }
                lastRound.message?.takeIf { it.isNotBlank() }?.let { obs ->
                    val brief = if (obs.length > 200) "${obs.take(200)}…" else obs
                    sb.appendLine("  Observation: $brief")
                }
            }
            sb.appendLine()
            sb.append("Please focus on what went wrong and try a different approach.")
        } else {
            sb.appendLine("⚠️ 【重试提示】上一次尝试已失败，以下是失败前最后一个规划轮次的信息，请据此调整策略。")
            sb.appendLine("失败原因：${previousAttempt.completionMessage ?: "未知"}")
            if (lastRound != null) {
                sb.appendLine()
                sb.appendLine("失败前最后一轮（第 ${lastRound.round} 轮）：")
                if (lastRound.thinking.isNotBlank()) {
                    val brief = if (lastRound.thinking.length > 200) "${lastRound.thinking.take(200)}…" else lastRound.thinking
                    sb.appendLine("  思考：$brief")
                }
                sb.appendLine("  动作类型：${lastRound.actionType}")
                if (!lastRound.subTaskDescription.isNullOrBlank()) {
                    sb.appendLine("  子任务：${lastRound.subTaskDescription}")
                }
                lastRound.message?.takeIf { it.isNotBlank() }?.let { obs ->
                    val brief = if (obs.length > 200) "${obs.take(200)}…" else obs
                    sb.appendLine("  观察：$brief")
                }
            }
            sb.appendLine()
            sb.append("请针对以上失败信息调整策略，重新规划并执行。")
        }

        return sb.toString().trimEnd()
    }

    companion object {
        private const val TAG = "LLMAgent"
        private const val PAUSE_POLL_MS = 200L
        private const val NETWORK_RETRY_DELAY_MS = 10_000L

        /**
         * Re-exported convenience constant so callers (especially settings UI) can keep
         * using `LLMAgent.BRAIN_KEY_PREFIX` without depending on the `tools` package.
         */
        const val BRAIN_KEY_PREFIX = ExecuteSubtaskTool.BRAIN_KEY_PREFIX
    }
}
