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
     * Returns a trimmed snapshot that keeps the system prompt, the initial task message,
     * an optional retry-context message, and the most recent [maxRounds] **complete**
     * planning rounds. Used when [LLMAgentConfig.limitContextRounds] is enabled to reduce
     * token consumption while preserving message-sequence integrity required by the
     * OpenAI function-calling protocol.
     *
     * A "complete round" is defined as an unbroken block ending with a [ChatMessage.Tool]
     * message whose immediately-preceding assistant turn carries `tool_calls`. The scan
     * works backwards from the tail of the message list so that partial/nudge turns at
     * the current round boundary are naturally included.
     *
     * Always preserves (never trimmed):
     *  - System prompt
     *  - First user message (original task description from `buildInitialMessage`)
     *  - Second user message if it looks like a retry-context summary
     *
     * @param maxRounds Number of complete assistant+tool round pairs to keep from the end.
     */
    fun getTrimmedMessages(maxRounds: Int): List<ChatMessage> {
        val system = messages.firstOrNull { it is ChatMessage.System }
            ?: return messages.toList()
        val nonSystem = messages.filter { it !is ChatMessage.System }

        // Always keep the initial task message (1st user) and optional retry context (2nd user).
        val pinned = mutableListOf<ChatMessage>()
        var pinCount = 0
        for (msg in nonSystem) {
            if (msg is ChatMessage.User && pinCount < 2) {
                pinned.add(msg)
                pinCount++
                if (pinCount == 2) break
            }
        }
        val pinnedSet = pinned.toSet()

        // Walk backwards collecting complete assistant(tool_calls)+tool groups.
        // A "group" is: everything from the assistant-with-tool-calls message up to and
        // including all consecutive tool messages that follow it.  Round-context user
        // messages that immediately precede the assistant are folded into the same group
        // so we never split a round-context/assistant/tool triple.
        val groups = ArrayDeque<List<ChatMessage>>() // front = oldest kept group
        var i = nonSystem.size - 1
        var roundsCollected = 0

        while (i >= 0 && roundsCollected < maxRounds) {
            val msg = nonSystem[i]
            if (msg in pinnedSet) { i--; continue }

            if (msg is ChatMessage.Tool) {
                // Collect all trailing tool messages for this round.
                val group = mutableListOf<ChatMessage>()
                while (i >= 0 && nonSystem[i] is ChatMessage.Tool) {
                    group.add(0, nonSystem[i])
                    i--
                }
                // Now find the assistant message with tool_calls.
                while (i >= 0) {
                    val candidate = nonSystem[i]
                    if (candidate is ChatMessage.Assistant && candidate.toolCalls.isNotEmpty()) {
                        group.add(0, candidate)
                        i--
                        break
                    }
                    // Anything between the tool messages and the assistant (shouldn't normally
                    // exist, but include it to avoid orphaned messages).
                    group.add(0, candidate)
                    i--
                }
                // Optionally pull in the immediately-preceding round-context user message.
                if (i >= 0 && nonSystem[i] is ChatMessage.User && nonSystem[i] !in pinnedSet) {
                    group.add(0, nonSystem[i])
                    i--
                }
                groups.addFirst(group)
                roundsCollected++
            } else {
                // Non-tool trailing message (e.g. nudge user message at current boundary):
                // include it as a single-item group without counting as a full round.
                groups.addFirst(listOf(msg))
                i--
            }
        }

        val recentMessages = groups.flatten()
        return listOf(system) + pinned + recentMessages
    }

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
