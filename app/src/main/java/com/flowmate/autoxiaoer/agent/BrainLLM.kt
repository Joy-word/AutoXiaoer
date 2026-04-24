package com.flowmate.autoxiaoer.agent

import com.flowmate.autoxiaoer.config.BrainLLMPrompts
import com.flowmate.autoxiaoer.model.ChatMessage
import com.flowmate.autoxiaoer.model.ModelClient
import com.flowmate.autoxiaoer.model.ModelConfig
import com.flowmate.autoxiaoer.model.ModelResult
import com.flowmate.autoxiaoer.util.Logger

/**
 * The "brain" (大脑) layer responsible for persona, interpersonal expression, and
 * natural-language generation.
 *
 * [LLMAgent] (小脑) handles task scheduling and capability invocation. Whenever text
 * needs to be sent to a friend or user, the cerebellum calls [BrainLLM.generateMessage]
 * and uses the result as the actual message content.
 *
 * This clean separation means:
 * - The brain can be pointed at a different, more expressive model.
 * - Persona and relationship knowledge lives entirely in the brain's system prompt.
 * - The cerebellum's system prompt can be stripped of character descriptions.
 *
 * Future extension: give the brain a persistent memory layer so it can recall past
 * conversations and relationship details across sessions.
 *
 * @param config [BrainLLMConfig] for this instance (independent model, key, etc.)
 * @param modelClient Pre-built [ModelClient] constructed from [config] by [ComponentManager]
 */
class BrainLLM(
    private val config: BrainLLMConfig,
    private val modelClient: ModelClient,
) {
    val isEnabled: Boolean get() = config.enabled

    companion object {
        private const val TAG = "BrainLLM"
    }

    /**
     * Generates an expressive, in-character message to be sent to [recipient].
     *
     * The cerebellum should call this method instead of letting the LLM directly
     * compose text inside the ReAct loop — keeping persona concerns fully isolated.
     *
     * @param recipient Who the message is addressed to (name, group name, or identifier)
     * @param receivedMessage The notification / message content that triggered this reply
     * @param background Operations already performed and information gathered by the cerebellum
     *   (e.g. WeChat history retrieved, task outcome, etc.)
     * @param memoryContext Optional memory content relevant to this conversation; pass null if none
     * @param language "cn" or "en", used to select the system prompt language
     * @return The generated message text extracted from the <answer> tag, or null if the call failed
     */
    suspend fun generateMessage(
        recipient: String,
        receivedMessage: String,
        background: String,
        memoryContext: String? = null,
        language: String = "cn",
    ): String? {
        if (!config.enabled) {
            Logger.w(TAG, "BrainLLM is disabled — returning null so caller falls back to self-generation")
            return null
        }

        val systemPrompt = if (config.customSystemPrompt.isNotBlank()) {
            config.customSystemPrompt
        } else {
            BrainLLMPrompts.getPrompt(language)
        }

        val userMessage = buildUserMessage(recipient, receivedMessage, background, memoryContext, language)

        val messages = listOf(
            ChatMessage.System(systemPrompt),
            ChatMessage.User(userMessage),
        )

        Logger.i(TAG, "Requesting BrainLLM: recipient=$recipient, background=${background.take(60)}")

        return when (val result = modelClient.request(messages, currentScreenshot = null)) {
            is ModelResult.Success -> {
                val raw = result.response.rawContent.trim()
                // Extract the content inside <answer>...</answer> tags
                val answerMatch = Regex("""<answer>\s*([\s\S]*?)\s*</answer>""").find(raw)
                val text = (answerMatch?.groupValues?.get(1) ?: raw).trim()
                Logger.i(TAG, "BrainLLM generated: ${text.take(100)}")
                text.ifBlank { null }
            }
            is ModelResult.Error -> {
                Logger.e(TAG, "BrainLLM request failed: ${result.error.message}")
                null
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────────────

    private fun buildUserMessage(
        recipient: String,
        receivedMessage: String,
        background: String,
        memoryContext: String?,
        language: String,
    ): String {
        return if (language.lowercase() == "en" || language.lowercase() == "english") {
            buildString {
                appendLine("[Recipient] $recipient")
                appendLine("[Received Message] $receivedMessage")
                appendLine("[Background] $background")
                appendLine("[Context] ${memoryContext ?: ""}")
            }.trimEnd()
        } else {
            buildString {
                appendLine("【对象】$recipient")
                appendLine("【当前收到的信息】$receivedMessage")
                appendLine("【背景】$background")
                appendLine("【上下文】${memoryContext ?: ""}")
            }.trimEnd()
        }
    }
}
