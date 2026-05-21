package com.flowmate.autoxiaoer.model

/**
 * Parser for model response content.
 *
 * Extracts thinking and action components from model responses.
 * This is extracted from ModelClient to enable unit testing.
 *
 */
object ModelResponseParser {
    /**
     * Parses the thinking and action from the model response content.
     *
     * The response format typically contains:
     * - Thinking section (before the action)
     * - Action in format: do(action="...", ...) or finish(message="...")
     *
     * Note: The model may wrap content in <think> and <answer> tags, which we strip out.
     *
     * @param content The raw response content to parse
     * @return Pair of (thinking, action) strings
     */
    fun parseThinkingAndAction(content: String, reasoningSideChannel: String = ""): Pair<String, String> {
        val taggedThinking = extractTaggedThinking(content)

        // LLMAgent: <action>{json}</action>
        val llmActionBlock = extractTaggedBlock(content, "action")?.trim()
        if (llmActionBlock != null) {
            val thinking =
                resolveThinking(
                    taggedThinking = taggedThinking,
                    fallbackBeforeTag = content.substringBefore("<action>").trim(),
                    reasoningSideChannel = reasoningSideChannel,
                )
            return Pair(thinking, llmActionBlock)
        }

        // PhoneAgent: do()/finish() inside <answer> or bare text
        val answerBlock = extractTaggedBlock(content, "answer")?.trim()
        val actionSearchText = answerBlock ?: stripPhoneAgentWrapperTags(content)

        val doAction = findActionWithBalancedParens(actionSearchText, "do")
        val finishAction = findActionWithBalancedParens(actionSearchText, "finish")
        val actionMatch =
            listOfNotNull(doAction, finishAction)
                .minByOrNull { it.first }

        return if (actionMatch != null) {
            val action = actionMatch.second.trim()
            val thinking =
                resolveThinking(
                    taggedThinking = taggedThinking,
                    fallbackBeforeTag = actionSearchText.substring(0, actionMatch.first).trim(),
                    reasoningSideChannel = reasoningSideChannel,
                )
            Pair(thinking, action)
        } else {
            val thinking =
                resolveThinking(
                    taggedThinking = taggedThinking,
                    fallbackBeforeTag = actionSearchText,
                    reasoningSideChannel = reasoningSideChannel,
                )
            Pair(thinking, "")
        }
    }

    /** Reads thinking from known XML tags, then optional side-channel / plain-text fallbacks. */
    private fun extractTaggedThinking(content: String): String {
        for (tag in TAGGED_THINKING_NAMES) {
            val block = extractTaggedBlock(content, tag)?.trim()
            if (!block.isNullOrBlank()) return block
        }
        return ""
    }

    private fun resolveThinking(
        taggedThinking: String,
        fallbackBeforeTag: String,
        reasoningSideChannel: String,
    ): String =
        taggedThinking.ifBlank { fallbackBeforeTag }
            .ifBlank { reasoningSideChannel.trim() }

    private fun stripPhoneAgentWrapperTags(content: String): String {
        var stripped = content
        for (tag in TAGGED_THINKING_NAMES) {
            stripped = stripped.replace(Regex("""<$tag>[\s\S]*?</$tag>"""), "")
        }
        return stripped
            .replace(Regex("""<answer>\s*"""), "")
            .replace(Regex("""\s*</answer>"""), "")
            .trim()
    }

    /**
     * Finds an action pattern with balanced parentheses.
     *
     * This correctly handles nested parentheses in text content like:
     * do(action=Type, text="hello (world)")
     *
     * @param content The content to search in
     * @param actionName The action name to find ("do" or "finish")
     * @return Pair of (startIndex, matchedString) or null if not found
     */
    internal fun findActionWithBalancedParens(content: String, actionName: String): Pair<Int, String>? {
        // Find the start of the action pattern (actionName followed by optional whitespace and '(')
        val startPattern = Regex("""$actionName\s*\(""")
        val startMatch = startPattern.find(content) ?: return null

        val startIndex = startMatch.range.first
        val openParenIndex = startMatch.range.last // Index of '('

        // Now find the matching closing parenthesis, accounting for nesting and quotes
        var depth = 1
        var i = openParenIndex + 1
        var inDoubleQuote = false
        var inSingleQuote = false
        var escaped = false

        while (i < content.length && depth > 0) {
            val char = content[i]

            if (escaped) {
                escaped = false
                i++
                continue
            }

            when (char) {
                '\\' -> escaped = true
                '"' -> if (!inSingleQuote) inDoubleQuote = !inDoubleQuote
                '\'' -> if (!inDoubleQuote) inSingleQuote = !inSingleQuote
                '(' -> if (!inDoubleQuote && !inSingleQuote) depth++
                ')' -> if (!inDoubleQuote && !inSingleQuote) depth--
            }
            i++
        }

        return if (depth == 0) {
            Pair(startIndex, content.substring(startIndex, i))
        } else {
            // Unbalanced parentheses, fall back to simple match
            null
        }
    }

    /**
     * Checks if the response indicates task completion.
     *
     * @param action The action string to check
     * @return True if the action is a finish action
     */
    fun isFinishAction(action: String): Boolean = action.startsWith("finish(") || action.startsWith("finish (")

    /**
     * Checks if the response indicates a do action.
     *
     * @param action The action string to check
     * @return True if the action is a do action
     */
    fun isDoAction(action: String): Boolean = action.startsWith("do(") || action.startsWith("do (")

    /**
     * Extracts the finish message from a finish action.
     *
     * Handles escaped quotes within the message.
     *
     * @param action The finish action string
     * @return The extracted message, or null if not a valid finish action
     */
    fun extractFinishMessage(action: String): String? {
        if (!isFinishAction(action)) return null

        // Find message= followed by a quote
        val messageStartPattern = Regex("""message\s*=\s*["']""")
        val startMatch = messageStartPattern.find(action) ?: return null

        val quoteChar = action[startMatch.range.last]
        val contentStart = startMatch.range.last + 1

        // Find the closing quote, handling escaped quotes
        val result = StringBuilder()
        var i = contentStart
        var escaped = false

        while (i < action.length) {
            val char = action[i]

            if (escaped) {
                result.append(char)
                escaped = false
                i++
                continue
            }

            when (char) {
                '\\' -> escaped = true

                quoteChar -> return result.toString()

                // Found closing quote
                else -> result.append(char)
            }
            i++
        }

        // No closing quote found, return what we have
        return result.toString().ifEmpty { null }
    }

    /**
     * Extracts LLMAgent reasoning from a response that uses
     * `<think>...</think>` and `<action>...</action>`.
     *
     * Tries the thinking tag first, then falls back to any text before `<action>`.
     */
    fun parseLlmAgentThinking(content: String, reasoningSideChannel: String = ""): String {
        val tagged = extractTaggedThinking(content)
        if (tagged.isNotBlank()) return tagged

        val actionStart = content.indexOf("<action>")
        val beforeAction = if (actionStart > 0) content.substring(0, actionStart).trim() else ""
        return beforeAction.ifBlank { reasoningSideChannel.trim() }
    }

    /**
     * Returns the inner text of the first `<action>...</action>` block for LLMAgent, or null if absent.
     */
    fun parseLlmAgentActionBlock(content: String): String? = extractTaggedBlock(content, "action")?.trim()

    /**
     * Returns the inner text of the first `<tag>...</tag>` block, or null if absent.
     */
    internal fun extractTaggedBlock(text: String, tag: String): String? {
        val open = "<$tag>"
        val close = "</$tag>"
        val start = text.indexOf(open)
        val end = text.indexOf(close)
        if (start == -1 || end == -1 || end <= start) return null
        return text.substring(start + open.length, end)
    }

    private val TAGGED_THINKING_NAMES = listOf("redacted_thinking", "thinking", "think")
}
