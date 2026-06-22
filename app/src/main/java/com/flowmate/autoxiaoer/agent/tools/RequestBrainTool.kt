package com.flowmate.autoxiaoer.agent.tools

import com.flowmate.autoxiaoer.util.Logger
import org.json.JSONObject

/**
 * Asks the expressor (BrainLLM) to compose human-facing wording. The result is fed back to the
 * controller, which is expected to place it into a subsequent `request_user.message` or
 * `execute_subtask.subtask.preGeneratedTexts` value.
 *
 * Mirrors the legacy `request_brain` action including the four observation variants:
 *   - successful generation → "[Expressor Result]" / "【表达者生成结果】"
 *   - brainLLM null         → "[Expressor Not Available]" / "【表达者未启用】"
 *   - brainLLM disabled     → "[Expressor Disabled]" / "【表达者已禁用】"
 *   - brainLLM call failed  → "[Expressor Disconnected]" / "【表达者断联】"
 */
class RequestBrainTool : AgentTool {
    override val name: String = NAME
    override val description: String =
        "Ask the expressor (BrainLLM) to write the human-facing wording. " +
            "Always call this before sending text to a friend (request_user) or typing a reply on screen, " +
            "unless the expressor is disabled or absent."
    override val parametersSchema =
        objectSchema(required = listOf("recipient", "intent")) {
            stringField("recipient", "Recipient name or group name.")
            objectField(
                name = "incomingMessage",
                description = "The message that triggered this reply. Pass an empty object when initiating proactively.",
            ) {
                stringField("sender", "Sender name; empty string when initiating proactively.")
                stringField("content", "Message text; empty string when initiating proactively.")
            }
            stringField(
                "intent",
                "The core goal to convey — only the goal itself, not who instructed it. " +
                    "E.g. \"ask everyone about their May Day plans\".",
            )
            stringMapField("facts", "Confirmed facts relevant to this reply, key → value.")
            stringField(
                "conversationBrief",
                "Brief summary of the recent conversation (optional, empty string if none).",
            )
        }

    override suspend fun execute(args: JSONObject, ctx: ToolContext): ToolResult {
        val recipient = args.optString("recipient").ifBlank {
            return ToolResult.Continue(
                if (ctx.isEnglish) "request_brain is missing the required `recipient` field."
                else "request_brain 缺少 recipient 字段，请重新输出。",
            )
        }
        val intent = args.optString("intent").ifBlank {
            return ToolResult.Continue(
                if (ctx.isEnglish) "request_brain is missing the required `intent` field."
                else "request_brain 缺少 intent 字段，请重新输出。",
            )
        }

        val incomingMessage = args.optJSONObject("incomingMessage")?.let { json ->
            buildMap<String, String> {
                json.keys().forEach { put(it, json.optString(it)) }
            }
        } ?: emptyMap()
        val facts = args.optJSONObject("facts")?.let { json ->
            buildMap<String, String> {
                json.keys().forEach { put(it, json.optString(it)) }
            }
        } ?: emptyMap()
        val conversationBrief = args.optString("conversationBrief").ifBlank { null }

        Logger.i(TAG, "request_brain: recipient=$recipient intent=${intent.take(60)}")

        val brain = ctx.brainLLM
        val result = brain?.generateMessage(
            recipient = recipient,
            incomingMessage = incomingMessage,
            intent = intent,
            facts = facts,
            conversationBrief = conversationBrief,
            language = ctx.config.language,
        )

        val text = result?.text
        val observation = when {
            text != null -> {
                if (ctx.isEnglish) {
                    "[Expressor Result]\n$text\n\n" +
                        "Place the above content into the next action " +
                        "(request_user's message, or the corresponding value in preGeneratedTexts)."
                } else {
                    "【表达者生成结果】\n$text\n\n" +
                        "请将以上内容填入后续 action（request_user 的 message 或 preGeneratedTexts 的对应 value）。"
                }
            }
            brain == null -> {
                if (ctx.isEnglish) {
                    "[Expressor Not Available] Expressor is not configured. Please generate the reply content yourself based on the context and intent provided, then fill it into the next action."
                } else {
                    "【表达者未启用】表达者未配置。请你根据以下意图自行生成回复内容，再填入后续 action。\n【意图】$intent"
                }
            }
            !brain.isEnabled -> {
                if (ctx.isEnglish) {
                    "[Expressor Disabled] Expressor is configured but currently disabled. Please generate the reply content yourself based on the context and intent provided, then fill it into the next action."
                } else {
                    "【表达者已禁用】表达者已配置但当前未启用。请你根据以下意图自行生成回复内容，再填入后续 action。\n【意图】$intent"
                }
            }
            else -> {
                Logger.w(TAG, "BrainLLM call failed for request_brain")
                if (ctx.isEnglish) {
                    "[Expressor Disconnected] The expressor failed to respond. Please generate the reply content yourself based on the context and intent provided, then fill it into the next action."
                } else {
                    "【表达者断联】表达者未能响应。请你根据以下意图自行生成回复内容，再填入后续 action。\n【意图】$intent"
                }
            }
        }

        return ToolResult.Continue(observation = observation, brainTokenUsage = result?.tokenUsage)
    }

    companion object {
        const val NAME = "request_brain"
        private const val TAG = "RequestBrainTool"
    }
}
