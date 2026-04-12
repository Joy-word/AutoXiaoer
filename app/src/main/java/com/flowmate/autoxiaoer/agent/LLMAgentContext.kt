package com.flowmate.autoxiaoer.agent

import com.flowmate.autoxiaoer.model.ChatMessage

/**
 * Manages the conversation context for [LLMAgent].
 *
 * Unlike [PhoneAgentContext], this context never attaches screenshots — all messages
 * are plain text. It follows the same pattern as [PhoneAgentContext] so the two agents
 * remain structurally consistent.
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
     * Appends the assistant's raw response to the context.
     */
    fun addAssistantMessage(content: String) {
        messages.add(ChatMessage.Assistant(content))
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
}
