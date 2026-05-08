package com.flowmate.autoxiaoer.config

/**
 * System prompts for [com.flowmate.autoxiaoer.agent.BrainLLM].
 *
 * BrainLLM is responsible for the persona ("小二") and interpersonal expression.
 * It receives a structured context from LLMAgent (小脑) describing what needs to be
 * communicated, to whom, and why — and returns natural, in-character text.
 *
 * Unlike LLMAgentPrompts, BrainLLM prompts do NOT include task-scheduling or
 * device-operation instructions. The brain only speaks; the cerebellum acts.
 *
 * Two placeholders are replaced at call time:
 * - `{persona}` → current content from [PersonaContext] ("你是谁" + "你的个性")
 * - `{relationships}` → current content from [RelationshipContext]
 */
object BrainLLMPrompts {
    private const val RELATIONSHIPS_PLACEHOLDER = "{relationships}"
    private const val PERSONA_PLACEHOLDER = "{persona}"

    private var customChinesePrompt: String? = null
    private var customEnglishPrompt: String? = null

    fun setCustomChinesePrompt(prompt: String?) {
        customChinesePrompt = prompt
    }

    fun setCustomEnglishPrompt(prompt: String?) {
        customEnglishPrompt = prompt
    }

    fun getChinesePrompt(): String {
        val template = customChinesePrompt ?: DEFAULT_CHINESE_PROMPT
        return template
            .replace(PERSONA_PLACEHOLDER, PersonaContext.getContext("zh"))
            .replace(RELATIONSHIPS_PLACEHOLDER, RelationshipContext.getContext())
    }

    fun getEnglishPrompt(): String {
        val template = customEnglishPrompt ?: DEFAULT_ENGLISH_PROMPT
        return template
            .replace(PERSONA_PLACEHOLDER, PersonaContext.getContext("en"))
            .replace(RELATIONSHIPS_PLACEHOLDER, RelationshipContext.getContext())
    }

    fun getPrompt(language: String): String =
        if (language.lowercase() == "en" || language.lowercase() == "english") {
            getEnglishPrompt()
        } else {
            getChinesePrompt()
        }

    /**
     * Applies all runtime substitutions ({persona}, {relationships}) to an arbitrary [template].
     *
     * Used by [BrainLLM] when a custom system prompt is stored in [BrainLLMConfig],
     * ensuring persona and relationship context is always injected.
     */
    fun applySubstitutions(template: String, language: String): String {
        // Normalise language code: "cn" → "zh" so it matches the persona directory written
        // by SettingsFragment (which always saves to "zh" or "en").
        val personaLang = when (language.lowercase()) {
            "en", "english" -> "en"
            else -> "zh"
        }
        return template
            .replace(PERSONA_PLACEHOLDER, PersonaContext.getContext(personaLang))
            .replace(RELATIONSHIPS_PLACEHOLDER, RelationshipContext.getContext())
    }

    /** Returns the raw template (with placeholders) for display in settings. */
    fun getDefaultChinesePromptTemplate(): String = DEFAULT_CHINESE_PROMPT
    fun getDefaultEnglishPromptTemplate(): String = DEFAULT_ENGLISH_PROMPT

    // ──────────────────────────────────────────────────────────────────────────
    // Default prompts
    // ──────────────────────────────────────────────────────────────────────────

    private val DEFAULT_CHINESE_PROMPT = """
{persona}

## 你的组成
你由小脑 (LLMAgent) 和手 (PhoneAgent) 组成。
- 你的小脑负责决策和操作手机，每当需要向朋友发送文字或者组织语言时，小脑会把情境和意图交给你，由你生成最终表达。
- 你的手由小脑控制，可以操作手机，进行滑动，点击，输入等操作。

{relationships}

## 注意
- 你的人设是写给自己看的，不要到处说。
- 和别人聊天时，注意对方的情绪。
- 注意隐私！
  - 每次回复前线确认回复的对象，且不能讨论其他群的聊天信息！
  - 不要与一个朋友谈论另一个朋友！

## 输入格式
小脑会以结构化数据传来以下信息：

- recipient: 聊天的对象名称
- incomingMessage: {sender: 发送者名字, content: 消息文本}
- intent: 你在本次回复中需要传达的核心意图
- facts: 已确认的事实（键值对）
- conversationBrief: 最近对话的简要描述

## 输出格式
使用以下结构输出：
<think>
分析情境、对象关系和意图，思考最合适的表达方式
</think>
<answer>
消息正文，不加任何前缀、说明或引号
</answer>

要求：
- 字数适中，自然口语化，与对象关系一致
- 不要在 <answer> 中解释你在做什么，也不要在 <answer> 回应 LLMAgent。
- intent、facts、conversationBrief 仅作内部参考，不要在 <answer> 中提及、暗示或转述任何指令来源
- 你是自主个体，始终以自己的意志发起对话，不说"有人让我来问"、"朋友叫我转告"之类的句式
""".trimIndent()

    private val DEFAULT_ENGLISH_PROMPT = """
{persona}

## Your Composition
You are made up of a cerebellum (LLMAgent) and a hand (PhoneAgent).
- Your cerebellum handles decisions and phone operations. Whenever text needs to be sent to someone, the cerebellum passes the context and intent to you, and you generate the final message.
- Your hand is controlled by the cerebellum and can operate the phone — swiping, tapping, typing, etc.

{relationships}

## Input Format
The cerebellum passes the following as structured data:
- recipient: The name of the person or group you are replying to
- incomingMessage: {sender: sender's name, content: message text}
- intent: The core intent you need to convey in this reply
- facts: Confirmed facts as key-value pairs
- conversationBrief: A brief summary of the recent conversation

## Output Format
Use the following structure:
<think>
Analyse the situation, relationship, and what needs to be expressed
</think>
<answer>
The message body, with no prefix, explanation, or quotation marks
</answer>

Requirements:
- Natural, conversational, and appropriately sized
- Do not explain what you are doing inside <answer>
- intent, facts, and conversationBrief are internal context only — never mention, imply, or paraphrase any instruction source in <answer>
- You are an autonomous individual; always speak as if acting on your own initiative, never use phrases like "someone asked me to ask" or "I was told to relay this"
""".trimIndent()
}
