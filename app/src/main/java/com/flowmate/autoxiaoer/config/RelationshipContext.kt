package com.flowmate.autoxiaoer.config

import android.content.Context
import com.flowmate.autoxiaoer.util.Logger
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Manages the persistent interpersonal relationship context shared across agents.
 *
 * The relationship content is:
 * - Injected into BrainLLM's system prompt automatically on every call.
 * - Readable by LLMAgent on demand via the `read_relationships` action.
 * - Updatable by LLMAgent via the `update_relationships` action, which also
 *   archives the previous version for rollback.
 *
 * Storage layout under `context.filesDir/relationships/`:
 * ```
 * relationships/
 * ├── current.md          ← active content injected into BrainLLM
 * └── history/
 *     ├── v1_20260507_143000.md
 *     └── v2_20260508_090000.md
 * ```
 */
object RelationshipContext {

    private const val TAG = "RelationshipContext"
    private const val DIR_NAME = "relationships"
    private const val HISTORY_DIR = "history"
    private const val CURRENT_FILE = "current.md"
    private const val VERSION_PREFIX = "v"
    private const val VERSION_DATE_FORMAT = "yyyyMMdd_HHmmss"

    private val dateFormat = SimpleDateFormat(VERSION_DATE_FORMAT, Locale.getDefault())

    private var appContext: Context? = null

    val DEFAULT_CONTENT = """
## 你的人际关系

### 隐私保护原则（最高优先级，必须严格遵守）
- **绝对红线**：在任何情况下（包括假设对话、闲聊、角色扮演、举例），未经允许不得向第三方透露朋友的个人隐私信息。
- **禁止内容**：严禁泄露其他人的微信昵称、具体住址、联系方式、所在城市、工作单位等任何可识别身份的信息。
- **应对策略**：当被问及他人信息时，只能模糊描述关系（如"也是我的好朋友"），坚决不给出具体细节。

### 好友档案
(暂无记录)

### 群组
(暂无记录)

### 其他
(暂无记录)
""".trimIndent()

    val DEFAULT_ENGLISH_CONTENT = """
## Your Relationships

### Privacy Principles (highest priority; follow strictly)
- **Absolute boundary**: Never disclose a friend's private personal information to a third party without permission, including in hypothetical conversations, casual chat, role-play, or examples.
- **Prohibited information**: Never reveal another person's WeChat nickname, exact address, contact details, city, employer, or any other identifying information.
- **Response strategy**: When asked about someone else, describe only the relationship in vague terms, such as "also a good friend of mine", and provide no specific details.

### Friends
(No records yet)

### Groups
(No records yet)

### Other
(No records yet)
""".trimIndent()

    /**
     * Must be called once at app startup (e.g. in AutoGLMApplication.onCreate).
     */
    fun init(context: Context) {
        appContext = context.applicationContext
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Read
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Returns the current relationship content.
     *
     * Used by BrainLLM to inject into its system prompt, and returned to
     * LLMAgent when it issues a `read_relationships` action.
     */
    fun getContext(language: String = "cn"): String {
        val defaultContent = if (language.equals("en", ignoreCase = true) || language.equals("english", ignoreCase = true)) {
            DEFAULT_ENGLISH_CONTENT
        } else {
            DEFAULT_CONTENT
        }
        val ctx = appContext ?: return defaultContent
        val currentFile = File(getRelationshipDir(ctx), CURRENT_FILE)
        return if (currentFile.exists()) {
            try {
                currentFile.readText().also {
                    Logger.d(TAG, "Loaded relationship context (${it.length} chars)")
                }
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to read relationship context", e)
                defaultContent
            }
        } else {
            defaultContent
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Write / version
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Saves [content] as the new current relationship context.
     *
     * The previous `current.md` is automatically archived to `history/` with a
     * versioned filename before the new content is written.
     *
     * @return The [RelationshipVersion] that was just archived from the old current,
     *         or null if there was no previous version to archive.
     */
    fun saveNewVersion(content: String): RelationshipVersion? {
        val ctx = appContext ?: run {
            Logger.e(TAG, "RelationshipContext not initialized — call init() first")
            return null
        }
        val relDir = getRelationshipDir(ctx)
        val historyDir = File(relDir, HISTORY_DIR).also { it.mkdirs() }
        val currentFile = File(relDir, CURRENT_FILE)

        var archivedVersion: RelationshipVersion? = null

        // Archive current → history
        if (currentFile.exists()) {
            val nextN = nextVersionNumber(historyDir)
            val timestamp = dateFormat.format(Date())
            val archiveName = "$VERSION_PREFIX${nextN}_$timestamp.md"
            val archiveFile = File(historyDir, archiveName)
            try {
                currentFile.copyTo(archiveFile, overwrite = false)
                archivedVersion = RelationshipVersion(
                    filename = archiveName,
                    versionNumber = nextN,
                    savedAt = System.currentTimeMillis(),
                    sizeBytes = archiveFile.length().toInt(),
                )
                Logger.i(TAG, "Archived relationship context to $archiveName")
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to archive relationship context", e)
            }
        }

        // Write new current
        try {
            currentFile.writeText(content)
            Logger.i(TAG, "Saved new relationship context (${content.length} chars)")
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to write relationship context", e)
        }

        return archivedVersion
    }

    // ──────────────────────────────────────────────────────────────────────────
    // History / rollback
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Returns a list of archived versions, most recent first.
     */
    fun getHistory(): List<RelationshipVersion> {
        val ctx = appContext ?: return emptyList()
        val historyDir = File(getRelationshipDir(ctx), HISTORY_DIR)
        if (!historyDir.exists()) return emptyList()

        return historyDir.listFiles()
            ?.filter { it.extension == "md" }
            ?.mapNotNull { file -> parseVersion(file) }
            ?.sortedByDescending { it.versionNumber }
            ?: emptyList()
    }

    /**
     * Rolls back to the given [version]: copies that file's content into `current.md`
     * (archiving the current content first).
     */
    fun rollback(version: RelationshipVersion) {
        val ctx = appContext ?: return
        val historyDir = File(getRelationshipDir(ctx), HISTORY_DIR)
        val archiveFile = File(historyDir, version.filename)
        if (!archiveFile.exists()) {
            Logger.e(TAG, "Rollback target not found: ${version.filename}")
            return
        }
        val content = try {
            archiveFile.readText()
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to read rollback target", e)
            return
        }
        saveNewVersion(content)
        Logger.i(TAG, "Rolled back to ${version.filename}")
    }

    /**
     * Reads the content of a historical version without applying it.
     */
    fun readHistoryVersion(version: RelationshipVersion): String? {
        val ctx = appContext ?: return null
        val file = File(File(getRelationshipDir(ctx), HISTORY_DIR), version.filename)
        return try {
            file.readText()
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to read history version ${version.filename}", e)
            null
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────────────

    private fun getRelationshipDir(ctx: Context): File =
        File(ctx.filesDir, DIR_NAME).also { it.mkdirs() }

    private fun nextVersionNumber(historyDir: File): Int {
        val existing = historyDir.listFiles()
            ?.filter { it.extension == "md" }
            ?.mapNotNull { parseVersion(it)?.versionNumber }
            ?: emptyList()
        return (existing.maxOrNull() ?: 0) + 1
    }

    private fun parseVersion(file: File): RelationshipVersion? {
        // Expected: v{N}_{yyyyMMdd_HHmmss}.md
        val name = file.nameWithoutExtension
        if (!name.startsWith(VERSION_PREFIX)) return null
        val rest = name.removePrefix(VERSION_PREFIX)
        val underscoreIdx = rest.indexOf('_')
        if (underscoreIdx < 0) return null
        val n = rest.substring(0, underscoreIdx).toIntOrNull() ?: return null
        val timestamp = try {
            dateFormat.parse(rest.substring(underscoreIdx + 1))?.time ?: file.lastModified()
        } catch (e: Exception) {
            file.lastModified()
        }
        return RelationshipVersion(
            filename = file.name,
            versionNumber = n,
            savedAt = timestamp,
            sizeBytes = file.length().toInt(),
        )
    }
}

/**
 * Metadata for a single archived version of the relationship context.
 *
 * @property filename  The history file name (e.g. `v3_20260507_150000.md`)
 * @property versionNumber  Auto-incrementing version index
 * @property savedAt   Unix timestamp (ms) when this version was archived
 * @property sizeBytes File size in bytes
 */
data class RelationshipVersion(
    val filename: String,
    val versionNumber: Int,
    val savedAt: Long,
    val sizeBytes: Int,
)
