package com.flowmate.autoxiaoer.clawbot

import com.flowmate.autoxiaoer.util.Logger
import org.json.JSONArray
import org.json.JSONObject

/**
 * A single turn in a ClawBot conversation.
 *
 * @property role "user" or "assistant"
 * @property content The message text
 * @property timestampMs Epoch millis when this turn was recorded
 */
data class ConversationTurn(
    val role: String,
    val content: String,
    val timestampMs: Long = System.currentTimeMillis(),
) {
    companion object {
        const val ROLE_USER = "user"
        const val ROLE_ASSISTANT = "assistant"

        fun fromJson(json: JSONObject): ConversationTurn = ConversationTurn(
            role = json.optString("role", ROLE_USER),
            content = json.optString("content", ""),
            timestampMs = json.optLong("timestampMs", System.currentTimeMillis()),
        )
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("role", role)
        put("content", content)
        put("timestampMs", timestampMs)
    }
}

/**
 * Manages multi-turn conversation history for ClawBot (WeChat iLink) chats.
 *
 * ## Strategy: Sliding Window
 *
 * Each user (identified by [fromUserId]) gets an independent conversation buffer.
 * The buffer retains the most recent [maxTurns] turns (a "turn" = one user message
 * followed by one assistant response). Older turns are automatically evicted.
 *
 * ### Future: Dynamic Compression
 *
 * When token usage approaches the model's context limit, older turns can be
 * replaced by a compact LLM-generated summary via the `compress()` extension.
 * The [compressedSummary] field stores this aggregated summary, which is injected
 * into the system prompt alongside recent turns.
 *
 * ## Persistence
 *
 * History is serialized as JSON and stored in SharedPreferences via
 * [SettingsManager]. Each user's history is keyed by `clawbot_history_{fromUserId}`.
 *
 * @property fromUserId The WeChat iLink user ID this history belongs to.
 * @property maxTurns Maximum number of complete user+assistant turn pairs to retain.
 * @property turns Chronological list of individual messages (user & assistant interleaved).
 * @property compressedSummary Optional LLM-generated summary of evicted older turns.
 */
data class ClawBotConversationHistory(
    val fromUserId: String,
    val maxTurns: Int = DEFAULT_MAX_TURNS,
    val turns: List<ConversationTurn> = emptyList(),
    val compressedSummary: String? = null,
) {
    companion object {
        private const val TAG = "ClawBotConvHistory"

        /** Default number of complete user+assistant turn pairs to retain. */
        const val DEFAULT_MAX_TURNS = 5

        /** Minimum allowed value for [maxTurns]. */
        const val MIN_MAX_TURNS = 1

        /** Maximum allowed value for [maxTurns] (safety cap for storage). */
        const val MAX_MAX_TURNS = 20

        /**
         * Deserializes a [ClawBotConversationHistory] from its JSON representation.
         * Returns null if [jsonStr] is blank or malformed.
         */
        fun fromJson(jsonStr: String): ClawBotConversationHistory? {
            if (jsonStr.isBlank()) return null
            return try {
                val json = JSONObject(jsonStr)
                val fromUserId = json.optString("fromUserId").ifBlank { return null }
                val maxTurns = json.optInt("maxTurns", DEFAULT_MAX_TURNS)
                    .coerceIn(MIN_MAX_TURNS, MAX_MAX_TURNS)
                val turnsArray = json.optJSONArray("turns") ?: JSONArray()
                val turns = (0 until turnsArray.length()).map { i ->
                    ConversationTurn.fromJson(turnsArray.getJSONObject(i))
                }
                val compressedSummary = json.optString("compressedSummary").ifBlank { null }
                ClawBotConversationHistory(
                    fromUserId = fromUserId,
                    maxTurns = maxTurns,
                    turns = turns,
                    compressedSummary = compressedSummary,
                )
            } catch (e: Exception) {
                Logger.w(TAG, "Failed to parse conversation history JSON", e)
                null
            }
        }

        /**
         * Creates an empty history for a new user.
         */
        fun createEmpty(fromUserId: String, maxTurns: Int = DEFAULT_MAX_TURNS): ClawBotConversationHistory =
            ClawBotConversationHistory(fromUserId = fromUserId, maxTurns = maxTurns)
    }

    /**
     * Serializes this history to a JSON string for persistence.
     */
    fun toJson(): String = JSONObject().apply {
        put("fromUserId", fromUserId)
        put("maxTurns", maxTurns)
        put("turns", JSONArray().apply {
            turns.forEach { put(it.toJson()) }
        })
        if (!compressedSummary.isNullOrBlank()) {
            put("compressedSummary", compressedSummary)
        }
    }.toString()

    /**
     * Appends a user message and assistant response as a complete turn.
     * Automatically evicts oldest turns if the window exceeds [maxTurns].
     *
     * @return A new [ClawBotConversationHistory] with the turn appended and eviction applied.
     */
    fun appendTurn(userMessage: String, assistantMessage: String): ClawBotConversationHistory {
        val newTurns = turns.toMutableList()
        newTurns.add(ConversationTurn(role = ConversationTurn.ROLE_USER, content = userMessage))
        newTurns.add(ConversationTurn(role = ConversationTurn.ROLE_ASSISTANT, content = assistantMessage))

        // Evict oldest complete turns (user+assistant pairs) if over capacity.
        // Count complete user+assistant pairs.
        var pairCount = 0
        val roles = newTurns.map { it.role }
        var i = 0
        while (i < roles.size - 1) {
            if (roles[i] == ConversationTurn.ROLE_USER && roles[i + 1] == ConversationTurn.ROLE_ASSISTANT) {
                pairCount++
                i += 2
            } else {
                i++
            }
        }

        val evictedTurns = mutableListOf<ConversationTurn>()
        while (pairCount > maxTurns) {
            // Remove the first complete user+assistant pair from the front
            val trimmed = mutableListOf<ConversationTurn>()
            var removed = 0
            var j = 0
            while (j < newTurns.size && removed < 2) {
                if (removed == 0 && newTurns[j].role == ConversationTurn.ROLE_USER) {
                    evictedTurns.add(newTurns[j])
                    removed++
                    j++
                    if (j < newTurns.size && newTurns[j].role == ConversationTurn.ROLE_ASSISTANT) {
                        evictedTurns.add(newTurns[j])
                        removed++
                        j++
                    }
                } else {
                    trimmed.add(newTurns[j])
                    j++
                }
            }
            trimmed.addAll(newTurns.drop(j))
            newTurns.clear()
            newTurns.addAll(trimmed)
            pairCount--
        }

        Logger.d(TAG, "Appended turn for $fromUserId: turns=${newTurns.size}, pairs=$pairCount, evicted=${evictedTurns.size}")

        return copy(turns = newTurns)
    }

    /**
     * Builds a human-readable context string suitable for injection into the LLM prompt.
     *
     * Format:
     * ```
     * 【近期对话历史】
     * User: xxx
     * 小二: yyy
     * ---
     * User: aaa
     * 小二: bbb
     * ```
     *
     * If [compressedSummary] is present, it is prepended as an earlier-conversation summary.
     */
    fun toPromptContext(): String? {
        if (turns.isEmpty() && compressedSummary.isNullOrBlank()) return null

        val sb = StringBuilder()
        sb.appendLine("【近期对话历史】")

        if (!compressedSummary.isNullOrBlank()) {
            sb.appendLine("【更早对话摘要】$compressedSummary")
            sb.appendLine()
        }

        turns.forEach { turn ->
            when (turn.role) {
                ConversationTurn.ROLE_USER -> sb.appendLine("User: ${turn.content}")
                ConversationTurn.ROLE_ASSISTANT -> sb.appendLine("小二: ${turn.content}")
            }
        }

        return sb.toString().trimEnd()
    }

    /**
     * Returns the number of complete user+assistant turn pairs.
     */
    fun getTurnPairCount(): Int {
        var count = 0
        var i = 0
        while (i < turns.size - 1) {
            if (turns[i].role == ConversationTurn.ROLE_USER &&
                turns[i + 1].role == ConversationTurn.ROLE_ASSISTANT
            ) {
                count++
                i += 2
            } else {
                i++
            }
        }
        return count
    }

    /**
     * Returns true if this history contains any turns or a compressed summary.
     */
    fun hasContent(): Boolean = turns.isNotEmpty() || !compressedSummary.isNullOrBlank()
}
