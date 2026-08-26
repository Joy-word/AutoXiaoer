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
        // Strip the LLMAgent <plan> block as well: the plan is surfaced separately via
        // parseLlmAgentPlan(), so it must not leak into `thinking`. Without this, a tool-call
        // round (no <action>/do()/finish()) whose only text is a <plan> block would make the
        // fallback treat the whole plan as thinking, hiding the model's real reasoning_content.
        stripped = stripped.replace(Regex("""<plan>[\s\S]*?</plan>"""), "")
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
     * Returns the inner text of the first `<action>...</action>` block for LLMAgent, or null if absent.
     */
    fun parseLlmAgentActionBlock(content: String): String? = extractTaggedBlock(content, "action")?.trim()

    /**
     * Returns the inner text of the first `<plan>...</plan>` block for LLMAgent, or null if absent
     * or blank.
     *
     * Unlike `<think>`, the model is expected to emit a `<plan>` block **every round**, even when
     * the task overview / done / remaining items haven't changed from the previous round. This
     * redundancy is intentional: it lets [LLMAgent] safely truncate the conversation context (e.g.
     * keep only the most recent N rounds) for token-cost optimization while still guaranteeing the
     * model always has its own up-to-date plan in the trimmed window, without depending on
     * [LLMAgentContext.addRoundContext] to backfill it from an older turn that may have been dropped.
     */
    fun parseLlmAgentPlan(content: String): String? = extractTaggedBlock(content, "plan")?.trim()?.ifBlank { null }

    /**
     * Strips the `<plan>` block from an LLMAgent response before it is persisted to
     * [LLMAgentContext]. It is redundant in history: the plan is already re-injected fresh
     * every round via `addRoundContext`, so the stale copy in the assistant turn only wastes
    * tokens. Any provider-specific response content is otherwise left intact.
     */
    fun stripPersistedTags(content: String): String = removeTaggedBlock(content, "plan").trim()

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

    /** Removes the first `<tag>...</tag>` block (tags included), or returns [text] unchanged if absent. */
    private fun removeTaggedBlock(text: String, tag: String): String {
        val open = "<$tag>"
        val close = "</$tag>"
        val start = text.indexOf(open)
        val end = text.indexOf(close)
        if (start == -1 || end == -1 || end <= start) return text
        return text.substring(0, start) + text.substring(end + close.length)
    }

    private val TAGGED_THINKING_NAMES = listOf("redacted_thinking", "thinking", "think")
}
