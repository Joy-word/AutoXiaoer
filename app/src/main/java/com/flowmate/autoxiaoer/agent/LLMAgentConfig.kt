package com.flowmate.autoxiaoer.agent

/**
 * Configuration for [LLMAgent].
 *
 * Fully independent from [com.flowmate.autoxiaoer.model.ModelConfig] used by PhoneAgent —
 * baseUrl, apiKey and modelName can all point to a completely different provider.
 *
 * @property baseUrl API base URL (OpenAI-compatible /chat/completions endpoint)
 * @property apiKey API key for the LLM service
 * @property modelName Model to use for planning (e.g. "glm-4-plus", "deepseek-chat")
 * @property maxTokens Maximum tokens per LLM response
 * @property temperature Sampling temperature; higher values produce more creative plans
 * @property maxPlanningSteps Maximum number of ReAct iterations before giving up
 * @property language Response language: "cn" for Chinese, "en" for English
 * @property customSystemPrompt If non-empty, overrides the built-in system prompt
 */
data class LLMAgentConfig(
    val baseUrl: String = "https://open.bigmodel.cn/api/paas/v4",
    val apiKey: String = "EMPTY",
    val modelName: String = "glm-4-plus",
    val maxTokens: Int = 2000,
    val temperature: Float = 0.7f,
    val maxPlanningSteps: Int = 20,
    val language: String = "cn",
    val customSystemPrompt: String = "",
)
