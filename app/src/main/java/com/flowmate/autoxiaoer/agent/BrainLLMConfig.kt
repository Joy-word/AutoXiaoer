package com.flowmate.autoxiaoer.agent

/**
 * Configuration for [BrainLLM].
 *
 * Fully independent from [LLMAgentConfig] (小脑) — can point to a different provider,
 * a different model, and uses its own system prompt.
 *
 * @property baseUrl API base URL (OpenAI-compatible /chat/completions endpoint)
 * @property apiKey API key for the LLM service
 * @property modelName Model to use for persona / expression generation
 * @property maxTokens Maximum tokens per BrainLLM response
 * @property temperature Sampling temperature
 * @property enabled Whether BrainLLM is active. When false, LLMAgent generates text itself.
 * @property customSystemPrompt If non-empty, overrides the built-in BrainLLM system prompt
 */
data class BrainLLMConfig(
    val baseUrl: String = "https://open.bigmodel.cn/api/paas/v4",
    val apiKey: String = "EMPTY",
    val modelName: String = "glm-4-plus",
    val maxTokens: Int = 1000,
    val temperature: Float = 0.9f,
    val enabled: Boolean = true,
    val customSystemPrompt: String = "",
)
