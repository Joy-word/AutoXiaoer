package com.flowmate.autoxiaoer.clawbot

import android.content.Context
import com.flowmate.autoxiaoer.util.Logger
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Who sent a ClawBot conversation entry. */
enum class ClawBotSpeaker {
    USER,
    AGENT,
}

/**
 * One message in the ClawBot conversation context (user ↔ agent over WeChat iLink).
 *
 * @property speaker [ClawBotSpeaker.USER] for inbound user text; [ClawBotSpeaker.AGENT] for
 *   outbound text sent via `request_user` (successful send only).
 * @property content Message body as seen by the user.
 * @property taskId History task id ([com.flowmate.autoxiaoer.history.TaskHistory.id]);
 *   set only for [ClawBotSpeaker.AGENT] entries.
 * @property timestamp Epoch milliseconds when the entry was recorded.
 */
data class ClawBotContextEntry(
    val speaker: ClawBotSpeaker,
    val content: String,
    val taskId: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
)

/**
 * Persistent rolling store of recent ClawBot conversation for one-to-one sessions.
 *
 * - User inbound text is appended without [ClawBotContextEntry.taskId].
 * - Agent outbound text is appended only after a successful `request_user` send, with taskId.
 * - At most [MAX_ENTRIES] entries; oldest entries are dropped on overflow.
 * - Cleared on ClawBot disconnect / session expiry; survives app restart otherwise.
 */
class ClawBotContextStore private constructor(context: Context) {
    private val contextFile: File = File(
        File(context.applicationContext.filesDir, DIR_NAME),
        FILE_NAME,
    ).also { it.parentFile?.mkdirs() }

    private val lock = Any()
    private val entries = mutableListOf<ClawBotContextEntry>()

    init {
        loadFromDisk()
    }

    /** Appends a user message (no task id). */
    fun appendUser(content: String) {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return
        appendEntry(ClawBotContextEntry(speaker = ClawBotSpeaker.USER, content = trimmed))
    }

    /** Appends an agent message after a successful `request_user` send. */
    fun appendAgent(content: String, taskId: String) {
        val trimmed = content.trim()
        if (trimmed.isEmpty() || taskId.isBlank()) return
        appendEntry(
            ClawBotContextEntry(
                speaker = ClawBotSpeaker.AGENT,
                content = trimmed,
                taskId = taskId,
            ),
        )
    }

    /** Removes all entries and deletes the on-disk file. */
    fun clear() {
        synchronized(lock) {
            entries.clear()
            if (contextFile.exists()) {
                contextFile.delete()
            }
        }
        Logger.i(TAG, "ClawBot conversation context cleared")
    }

    /**
     * Formats recent entries for injection into the LLM user prompt (ClawBot triggers only).
     * Returns an empty string when there is no history.
     */
    fun formatForPrompt(isEnglish: Boolean): String {
        val snapshot = synchronized(lock) { entries.toList() }
        if (snapshot.isEmpty()) return ""

        val sb = StringBuilder()
        if (isEnglish) {
            sb.appendLine("【ClawBot conversation context】")
            sb.appendLine(
                "Recent messages with the user (for continuity). Use get_task_history_detail with taskId for agent entries when you need execution details.",
            )
        } else {
            sb.appendLine("【ClawBot 对话上下文】")
            sb.appendLine(
                "与用户最近的对话记录（供延续上下文参考）。助手消息带有任务 id 时，可用 get_task_history_detail 查询该任务的执行详情。",
            )
        }
        for (entry in snapshot) {
            when (entry.speaker) {
                ClawBotSpeaker.USER -> {
                    if (isEnglish) {
                        sb.appendLine("- User: ${entry.content}")
                    } else {
                        sb.appendLine("- 用户: ${entry.content}")
                    }
                }
                ClawBotSpeaker.AGENT -> {
                    val id = entry.taskId ?: continue
                    if (isEnglish) {
                        sb.appendLine("- Agent [task $id]: ${entry.content}")
                    } else {
                        sb.appendLine("- 助手 [任务 $id]: ${entry.content}")
                    }
                }
            }
        }
        return sb.toString().trimEnd()
    }

    private fun appendEntry(entry: ClawBotContextEntry) {
        synchronized(lock) {
            entries.add(entry)
            while (entries.size > MAX_ENTRIES) {
                entries.removeAt(0)
            }
            persistToDiskLocked()
        }
        Logger.d(TAG, "Appended ${entry.speaker} entry, size=${entries.size}")
    }

    private fun loadFromDisk() {
        synchronized(lock) {
            entries.clear()
            if (!contextFile.exists()) return
            try {
                val json = JSONArray(contextFile.readText())
                for (i in 0 until json.length()) {
                    val obj = json.optJSONObject(i) ?: continue
                    val speakerStr = obj.optString(KEY_SPEAKER, "")
                    val speaker = when (speakerStr) {
                        SPEAKER_USER -> ClawBotSpeaker.USER
                        SPEAKER_AGENT -> ClawBotSpeaker.AGENT
                        else -> continue
                    }
                    val content = obj.optString(KEY_CONTENT, "").trim()
                    if (content.isEmpty()) continue
                    val taskId = obj.optString(KEY_TASK_ID, "").takeIf { it.isNotBlank() }
                    val timestamp = obj.optLong(KEY_TIMESTAMP, System.currentTimeMillis())
                    if (speaker == ClawBotSpeaker.AGENT && taskId == null) continue
                    entries.add(
                        ClawBotContextEntry(
                            speaker = speaker,
                            content = content,
                            taskId = taskId,
                            timestamp = timestamp,
                        ),
                    )
                }
                while (entries.size > MAX_ENTRIES) {
                    entries.removeAt(0)
                }
                Logger.i(TAG, "Loaded ${entries.size} ClawBot context entries from disk")
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to load ClawBot context, starting fresh", e)
                entries.clear()
                contextFile.delete()
            }
        }
    }

    private fun persistToDiskLocked() {
        try {
            val json = JSONArray()
            for (entry in entries) {
                val obj = JSONObject().apply {
                    put(KEY_SPEAKER, when (entry.speaker) {
                        ClawBotSpeaker.USER -> SPEAKER_USER
                        ClawBotSpeaker.AGENT -> SPEAKER_AGENT
                    })
                    put(KEY_CONTENT, entry.content)
                    put(KEY_TIMESTAMP, entry.timestamp)
                    entry.taskId?.let { put(KEY_TASK_ID, it) }
                }
                json.put(obj)
            }
            contextFile.writeText(json.toString())
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to persist ClawBot context", e)
        }
    }

    companion object {
        private const val TAG = "ClawBotContextStore"
        private const val DIR_NAME = "clawbot"
        private const val FILE_NAME = "conversation_context.json"
        private const val MAX_ENTRIES = 10

        private const val KEY_SPEAKER = "speaker"
        private const val KEY_CONTENT = "content"
        private const val KEY_TASK_ID = "task_id"
        private const val KEY_TIMESTAMP = "timestamp"
        private const val SPEAKER_USER = "user"
        private const val SPEAKER_AGENT = "agent"

        @Volatile
        private var instance: ClawBotContextStore? = null

        fun getInstance(context: Context): ClawBotContextStore {
            return instance ?: synchronized(this) {
                instance ?: ClawBotContextStore(context.applicationContext).also { instance = it }
            }
        }

        /** Clears the singleton store (e.g. on disconnect). */
        fun clearInstance(context: Context) {
            synchronized(this) {
                instance?.clear()
                instance = null
            }
        }

        /** Reloads entries from disk after a data import (no-op if not yet instantiated). */
        fun reloadFromDiskIfLoaded(context: Context) {
            synchronized(this) {
                instance?.loadFromDisk()
            }
        }
    }
}
