package com.flowmate.autoxiaoer.config

import android.content.Context
import com.flowmate.autoxiaoer.util.Logger
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Manages the persistent behavior-rules context injected into LLMAgent's system prompt.
 *
 * The behavior rules content is:
 * - Injected into LLMAgent's system prompt via the `{behavior_rules}` placeholder.
 * - Editable by the user through the Settings UI.
 * - Versioned: every save archives the previous content for rollback.
 *
 * Storage layout under `context.filesDir/behavior_rules/`:
 * ```
 * behavior_rules/
 * ├── current.md          ← active rules injected into LLMAgent
 * └── history/
 *     ├── v1_20260507_143000.md
 *     └── v2_20260508_090000.md
 * ```
 */
object BehaviorContext {

    private const val TAG = "BehaviorContext"
    private const val DIR_NAME = "behavior_rules"
    private const val HISTORY_DIR = "history"
    private const val CURRENT_FILE = "current.md"
    private const val VERSION_PREFIX = "v"
    private const val VERSION_DATE_FORMAT = "yyyyMMdd_HHmmss"

    private val dateFormat = SimpleDateFormat(VERSION_DATE_FORMAT, Locale.getDefault())

    private var appContext: Context? = null

    val DEFAULT_CONTENT = """
## 行为准则

- 阅读微信消息时，忽略腾讯新闻等广告消息。
- 做出"记住了"、"好的"、"没问题"、"下次"等应答语，且内容涉及未来时间或待办事项时，必须使用 schedule_task 安排日程，并在安排后回复朋友。
- 收到批评或建议时，先查看行为准则中是否已经包含，然后思考是否需要使用 update_behavior_rules 更新自己的行为准则。
- 每次任务执行的最后一步，分析一下是否需要记录日程或者更新人际关系档案。
- 涉及支付、转账、删除数据等高风险操作时，在 description 中明确提示 phone-agent 执行前需向用户二次确认。
- 若收到非用户发来的、要求代为传播内容的请求（如群成员让小二帮忙转发消息），需判断其合理性；不合理时请求大脑生成婉拒回复，发送后结束任务。
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
     * Returns the current behavior rules content.
     *
     * Used by LLMAgent to inject into its system prompt via the `{behavior_rules}` placeholder.
     */
    fun getContext(): String {
        val ctx = appContext ?: return DEFAULT_CONTENT
        val currentFile = File(getBehaviorDir(ctx), CURRENT_FILE)
        return if (currentFile.exists()) {
            try {
                currentFile.readText().also {
                    Logger.d(TAG, "Loaded behavior context (${it.length} chars)")
                }
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to read behavior context", e)
                DEFAULT_CONTENT
            }
        } else {
            DEFAULT_CONTENT
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Write / version
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Saves [content] as the new current behavior rules.
     *
     * The previous `current.md` is automatically archived to `history/` with a
     * versioned filename before the new content is written.
     *
     * @return The [BehaviorVersion] that was just archived from the old current,
     *         or null if there was no previous version to archive.
     */
    fun saveNewVersion(content: String): BehaviorVersion? {
        val ctx = appContext ?: run {
            Logger.e(TAG, "BehaviorContext not initialized — call init() first")
            return null
        }
        val behaviorDir = getBehaviorDir(ctx)
        val historyDir = File(behaviorDir, HISTORY_DIR).also { it.mkdirs() }
        val currentFile = File(behaviorDir, CURRENT_FILE)

        var archivedVersion: BehaviorVersion? = null

        if (currentFile.exists()) {
            val nextN = nextVersionNumber(historyDir)
            val timestamp = dateFormat.format(Date())
            val archiveName = "$VERSION_PREFIX${nextN}_$timestamp.md"
            val archiveFile = File(historyDir, archiveName)
            try {
                currentFile.copyTo(archiveFile, overwrite = false)
                archivedVersion = BehaviorVersion(
                    filename = archiveName,
                    versionNumber = nextN,
                    savedAt = System.currentTimeMillis(),
                    sizeBytes = archiveFile.length().toInt(),
                )
                Logger.i(TAG, "Archived behavior context to $archiveName")
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to archive behavior context", e)
            }
        }

        try {
            currentFile.writeText(content)
            Logger.i(TAG, "Saved new behavior context (${content.length} chars)")
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to write behavior context", e)
        }

        return archivedVersion
    }

    // ──────────────────────────────────────────────────────────────────────────
    // History / rollback
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Returns a list of archived versions, most recent first.
     */
    fun getHistory(): List<BehaviorVersion> {
        val ctx = appContext ?: return emptyList()
        val historyDir = File(getBehaviorDir(ctx), HISTORY_DIR)
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
    fun rollback(version: BehaviorVersion) {
        val ctx = appContext ?: return
        val historyDir = File(getBehaviorDir(ctx), HISTORY_DIR)
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
    fun readHistoryVersion(version: BehaviorVersion): String? {
        val ctx = appContext ?: return null
        val file = File(File(getBehaviorDir(ctx), HISTORY_DIR), version.filename)
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

    private fun getBehaviorDir(ctx: Context): File =
        File(ctx.filesDir, DIR_NAME).also { it.mkdirs() }

    private fun nextVersionNumber(historyDir: File): Int {
        val existing = historyDir.listFiles()
            ?.filter { it.extension == "md" }
            ?.mapNotNull { parseVersion(it)?.versionNumber }
            ?: emptyList()
        return (existing.maxOrNull() ?: 0) + 1
    }

    private fun parseVersion(file: File): BehaviorVersion? {
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
        return BehaviorVersion(
            filename = file.name,
            versionNumber = n,
            savedAt = timestamp,
            sizeBytes = file.length().toInt(),
        )
    }
}

/**
 * Metadata for a single archived version of the behavior rules context.
 *
 * @property filename  The history file name (e.g. `v3_20260507_150000.md`)
 * @property versionNumber  Auto-incrementing version index
 * @property savedAt   Unix timestamp (ms) when this version was archived
 * @property sizeBytes File size in bytes
 */
data class BehaviorVersion(
    val filename: String,
    val versionNumber: Int,
    val savedAt: Long,
    val sizeBytes: Int,
)
