package com.flowmate.autoxiaoer.agent

import com.flowmate.autoxiaoer.model.ChatMessage
import com.flowmate.autoxiaoer.model.ParsedToolCall

/**
 * Manages the conversation context for [LLMAgent].
 *
 * Unlike [PhoneAgentContext], this context never attaches screenshots — all messages
 * are plain text. It follows the same pattern as [PhoneAgentContext] so the two agents
 * remain structurally consistent.
 *
 * In the OpenAI Function-Calling protocol an assistant turn may carry both natural-language
 * `content` and a list of `tool_calls`; each tool result is then echoed back as a separate
 * `role: "tool"` message. This class supports all three message shapes.
 */
class LLMAgentContext(private val systemPrompt: String) {
    private val messages: MutableList<ChatMessage> = mutableListOf()

    init {
        messages.add(ChatMessage.System(systemPrompt))
    }

    /**
     * Appends a user message (task description or observation) to the context.
     */
    fun addUserMessage(text: String) {
        messages.add(ChatMessage.User(text))
    }

    /**
     * Appends the assistant's raw response (text-only) to the context.
     */
    fun addAssistantMessage(content: String) {
        messages.add(ChatMessage.Assistant(content))
    }

    /**
     * Appends an assistant turn that carries tool calls. The OpenAI spec requires the same
     * assistant message to also include `content` (which may be empty); pass any natural-
     * language reasoning (e.g. the `<think>` block) as [content].
     */
    fun addAssistantWithToolCalls(content: String, toolCalls: List<ParsedToolCall>) {
        messages.add(ChatMessage.Assistant(content = content, toolCalls = toolCalls))
    }

    /**
     * Echoes a tool call's result back to the model as a `role: "tool"` message.
     *
     * The [toolCallId] must match the id of the tool call this reply is responding to,
     * otherwise the API will reject the next request.
     */
    fun addToolMessage(toolCallId: String, name: String, content: String) {
        messages.add(ChatMessage.Tool(toolCallId = toolCallId, name = name, content = content))
    }

    /**
     * Returns an immutable snapshot of the current message list.
     */
    fun getMessages(): List<ChatMessage> = messages.toList()

    /**
     * Total number of messages including the system prompt.
     */
    fun getMessageCount(): Int = messages.size

    /**
     * Resets the context, retaining only the system prompt.
     */
    fun reset() {
        messages.clear()
        messages.add(ChatMessage.System(systemPrompt))
    }

    /**
     * Returns true if the context contains only the system prompt (no turns yet).
     */
    fun isEmpty(): Boolean = messages.size == 1 && messages.first() is ChatMessage.System

    /**
     * Returns the number of completed planning rounds (assistant turns).
     */
    fun getPlanningRoundCount(): Int = messages.count { it is ChatMessage.Assistant }

    /**
     * Injects the current planning round number, and the last known plan, into the
     * conversation context so the LLM is aware of its progress within the ReAct loop.
     *
     * The plan text is framework-managed: [LLMAgent] retains whatever the model last put
     * inside a `<plan>` block and echoes it back here every round. The model is not expected
     * to recall or retype the plan from earlier turns — it only needs to emit a new `<plan>`
     * block when the task overview / done / remaining items actually change.
     *
     * Called by [LLMAgent] at the start of each planning round before the model request.
     *
     * @param currentPlan The last `<plan>` block content recorded so far, or blank if none yet
     *   (expected only before round 1's response has been parsed).
     */
    fun addRoundContext(round: Int, maxRounds: Int, isEnglish: Boolean, currentPlan: String) {
        val roundLine = if (isEnglish) {
            "[Current planning round: $round / $maxRounds]"
        } else {
            "【当前规划轮次】第 $round / $maxRounds 轮"
        }
        val planLine = if (currentPlan.isBlank()) {
            if (isEnglish) {
                "[No plan recorded — you must output a <plan> block this round.]"
            } else {
                "【计划】尚无记录，本轮必须先输出 <plan> 块。"
            }
        } else {
            val label = if (isEnglish) {
                "[Plan from the previous round — update the <plan> block first based on the tool result]"
            } else {
                "【上一轮计划】- 请先根据 tool 结果更新 <plan> 块）"
            }
            "$label\n$currentPlan"
        }
        messages.add(ChatMessage.User("$roundLine\n\n$planLine"))
    }
}
