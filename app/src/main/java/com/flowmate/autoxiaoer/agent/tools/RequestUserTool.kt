package com.flowmate.autoxiaoer.agent.tools

import com.flowmate.autoxiaoer.clawbot.ClawBotContextStore
import com.flowmate.autoxiaoer.clawbot.ClawBotManager
import com.flowmate.autoxiaoer.task.TriggerType
import com.flowmate.autoxiaoer.ui.FloatingWindowService
import com.flowmate.autoxiaoer.util.Logger
import org.json.JSONObject

/**
 * Sends a natural-language message back to the human user.
 *
 * Routing:
 * - ClawBot connected & task triggered by ClawBot → reply via the original conversation
 * - ClawBot connected, other triggers → proactive push to the last conversation
 * - ClawBot not connected → fall back to the floating window and terminate the task
 *
 * Mirrors the legacy `request_user` action behaviour exactly.
 */
class RequestUserTool : AgentTool {
    override val name: String = NAME
    override val description: String =
        "Send a human-facing message to the user (reply, question, or notification). " +
            "When the expressor (BrainLLM) is enabled, call request_brain first to obtain the wording, " +
            "then place that wording in this tool's `message` argument. " +
            "If the user just asked a question and the answer fits in this message, follow this call with `finish`."
    override val parametersSchema =
        objectSchema(required = listOf("message")) {
            stringField("message", "The exact text to deliver to the user.")
        }

    override suspend fun execute(args: JSONObject, ctx: ToolContext): ToolResult {
        val msg = args.optString("message").ifBlank {
            return ToolResult.Continue(
                if (ctx.isEnglish) "request_user is missing the required `message` field." else "request_user 缺少 message 字段，请重新输出。",
            )
        }
        Logger.i(TAG, "request_user: ${msg.take(80)}")

        val appCtx = ctx.appContext

        if (appCtx == null || !ClawBotManager.isConnected(appCtx)) {
            Logger.w(TAG, "ClawBot not available — showing in floating window")
            FloatingWindowService.getInstance()?.showResult(msg, true)
            val observation =
                if (ctx.isEnglish) "[ClawBot Not Connected] Reminder shown in the floating window. Task ends."
                else "【ClawBot 未连接】已将提醒内容显示在悬浮窗，任务结束。"
            return ToolResult.Terminate(success = true, message = msg, observation = observation)
        }

        val sent = try {
            val trigger = ctx.triggerContext
            val fromUserId = trigger?.clawBotFromUserId
            val contextToken = trigger?.clawBotContextToken
            if (trigger?.triggerType == TriggerType.CLAWBOT &&
                !fromUserId.isNullOrBlank() &&
                !contextToken.isNullOrBlank()
            ) {
                ClawBotManager.sendMessage(appCtx, fromUserId, contextToken, msg)
            } else {
                ClawBotManager.sendProactiveMessage(appCtx, msg)
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to send ClawBot request_user message", e)
            false
        }

        Logger.i(TAG, "ClawBot request_user sent=$sent")

        if (sent) {
            val taskId = ctx.historyManager?.getCurrentTaskId()
            if (taskId != null) {
                ClawBotContextStore.getInstance(appCtx).appendAgent(msg, taskId)
            }
        }

        val observation = if (ctx.isEnglish) {
            if (sent) {
                "[User Notification] Successfully sent the following message to the user: \"$msg\"\n\n" +
                    "Decide your next step (use `finish` if the task is complete)."
            } else {
                "[User Notification] Failed to send the following message to the user: \"$msg\"\n\n" +
                    "Decide whether to retry, change strategy, or finish the task."
            }
        } else {
            if (sent) {
                "【用户通知结果】已成功将以下消息发送给用户：「$msg」\n\n请根据此结果继续决定下一步操作（如任务已完成可使用 finish）。"
            } else {
                "【用户通知结果】消息发送失败，无法将以下内容传达给用户：「$msg」\n\n请根据此情况决定下一步操作（可以重试、调整策略，或使用 finish 结束任务）。"
            }
        }
        return ToolResult.Continue(observation)
    }

    companion object {
        const val NAME = "request_user"
        private const val TAG = "RequestUserTool"
    }
}
