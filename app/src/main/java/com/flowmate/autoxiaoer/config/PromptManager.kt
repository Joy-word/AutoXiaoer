package com.flowmate.autoxiaoer.config

import android.content.Context
import com.flowmate.autoxiaoer.util.Logger
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Manages file-based storage and version history for all user-editable system prompts.
 *
 * Each prompt type and language combination gets its own subdirectory:
 * ```
 * filesDir/prompts/
 * ├── phone_agent/
 * │   ├── current_cn.md
 * │   ├── current_en.md
 * │   └── history/
 * │       ├── v1_20260507_143000_cn.md
 * │       └── v1_20260507_143000_en.md
 * ├── llm_agent/
 * │   └── ...
 * └── brain_llm/
 *     └── ...
 * ```
 *
 * Reading priority (highest to lowest):
 * 1. File in `current_{lang}.md` (set by this manager)
 * 2. Value from legacy SharedPreferences (one-time migration via [migrateFromPrefs])
 * 3. Hard-coded default template in the respective `*Prompts` object
 */
class PromptManager private constructor(private val context: Context) {

    enum class PromptType(val dirName: String) {
        PHONE_AGENT("phone_agent"),
        LLM_AGENT("llm_agent"),
        BRAIN_LLM("brain_llm"),
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Read
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Returns the current prompt content for [type] / [language], or null if no file exists.
     *
     * Callers should fall back to the hard-coded default when this returns null.
     *
     * @param language "cn" or "en"
     */
    fun getCurrent(type: PromptType, language: String): String? {
        val file = currentFile(type, language)
        if (!file.exists()) return null
        return try {
            file.readText().also {
                Logger.d(TAG, "Loaded prompt ${type.dirName}/$language (${it.length} chars)")
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to read prompt ${type.dirName}/$language", e)
            null
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Write / version
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Saves [content] as the new current prompt for [type] / [language].
     *
     * The previous `current_{lang}.md` is archived to `history/` before writing.
     *
     * @return The [PromptVersion] that was archived from the old current,
     *         or null if there was nothing to archive.
     */
    fun saveNewVersion(type: PromptType, language: String, content: String): PromptVersion? {
        val typeDir = typeDir(type)
        val historyDir = File(typeDir, HISTORY_DIR).also { it.mkdirs() }
        val current = currentFile(type, language)

        var archived: PromptVersion? = null

        if (current.exists()) {
            val nextN = nextVersionNumber(historyDir, language)
            val timestamp = dateFormat.format(Date())
            val archiveName = "$VERSION_PREFIX${nextN}_${timestamp}_$language.md"
            val archiveFile = File(historyDir, archiveName)
            try {
                current.copyTo(archiveFile, overwrite = false)
                archived = PromptVersion(
                    type = type,
                    language = language,
                    filename = archiveName,
                    versionNumber = nextN,
                    savedAt = System.currentTimeMillis(),
                    sizeBytes = archiveFile.length().toInt(),
                )
                Logger.i(TAG, "Archived ${type.dirName}/$language → $archiveName")
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to archive ${type.dirName}/$language", e)
            }
        }

        try {
            current.writeText(content)
            Logger.i(TAG, "Saved ${type.dirName}/$language (${content.length} chars)")
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to write ${type.dirName}/$language", e)
        }

        return archived
    }

    /**
     * Deletes the current file for [type] / [language], causing the system to
     * fall back to the hard-coded default on the next read.
     */
    fun deleteCurrent(type: PromptType, language: String) {
        currentFile(type, language).delete()
        Logger.i(TAG, "Deleted current prompt for ${type.dirName}/$language")
    }

    // ──────────────────────────────────────────────────────────────────────────
    // History / rollback
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Returns archived versions for [type] / [language], most recent first.
     */
    fun getHistory(type: PromptType, language: String): List<PromptVersion> {
        val historyDir = File(typeDir(type), HISTORY_DIR)
        if (!historyDir.exists()) return emptyList()

        return historyDir.listFiles()
            ?.filter { it.extension == "md" && it.nameWithoutExtension.endsWith("_$language") }
            ?.mapNotNull { parseVersion(it, type, language) }
            ?.sortedByDescending { it.versionNumber }
            ?: emptyList()
    }

    /**
     * Rolls back to [version] by writing its content into `current_{lang}.md`
     * (archiving the current content first).
     */
    fun rollback(version: PromptVersion) {
        val historyDir = File(typeDir(version.type), HISTORY_DIR)
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
        saveNewVersion(version.type, version.language, content)
        Logger.i(TAG, "Rolled back ${version.type.dirName}/${version.language} to ${version.filename}")
    }

    /**
     * Reads the content of a historical version without applying it.
     */
    fun readHistoryVersion(version: PromptVersion): String? {
        val file = File(File(typeDir(version.type), HISTORY_DIR), version.filename)
        return try {
            file.readText()
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to read history version ${version.filename}", e)
            null
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Migration
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * One-time migration: if [legacyContent] is non-null/non-blank and no current file
     * exists yet for [type] / [language], writes it as the first versioned file.
     *
     * Call this from AutoGLMApplication on first launch after upgrading.
     */
    fun migrateFromPrefs(type: PromptType, language: String, legacyContent: String?) {
        if (legacyContent.isNullOrBlank()) return
        if (currentFile(type, language).exists()) return
        saveNewVersion(type, language, legacyContent)
        Logger.i(TAG, "Migrated legacy pref for ${type.dirName}/$language to file")
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────────────

    private fun typeDir(type: PromptType): File =
        File(context.filesDir, "$PROMPTS_ROOT/${type.dirName}").also { it.mkdirs() }

    private fun currentFile(type: PromptType, language: String): File =
        File(typeDir(type), "current_$language.md")

    private fun nextVersionNumber(historyDir: File, language: String): Int {
        val existing = historyDir.listFiles()
            ?.filter { it.extension == "md" && it.nameWithoutExtension.endsWith("_$language") }
            ?.mapNotNull { parseVersion(it, null, language)?.versionNumber }
            ?: emptyList()
        return (existing.maxOrNull() ?: 0) + 1
    }

    private fun parseVersion(file: File, type: PromptType?, @Suppress("UNUSED_PARAMETER") language: String): PromptVersion? {
        // Expected: v{N}_{yyyyMMdd_HHmmss}_{lang}.md
        val name = file.nameWithoutExtension
        if (!name.startsWith(VERSION_PREFIX)) return null
        val rest = name.removePrefix(VERSION_PREFIX)
        // rest = "{N}_{yyyyMMdd_HHmmss}_{lang}"
        val parts = rest.split("_")
        if (parts.size < 4) return null  // need N + date(8) + time(6) + lang
        val n = parts[0].toIntOrNull() ?: return null
        val lang = parts.last()
        val timestamp = try {
            // parts[1] = yyyyMMdd, parts[2] = HHmmss
            dateFormat.parse("${parts[1]}_${parts[2]}")?.time ?: file.lastModified()
        } catch (e: Exception) {
            file.lastModified()
        }
        // Infer type from parent directory if not provided
        val resolvedType = type ?: run {
            val dirName = file.parentFile?.parentFile?.name ?: return null
            PromptType.entries.find { it.dirName == dirName }
        } ?: return null

        return PromptVersion(
            type = resolvedType,
            language = lang,
            filename = file.name,
            versionNumber = n,
            savedAt = timestamp,
            sizeBytes = file.length().toInt(),
        )
    }

    companion object {
        private const val TAG = "PromptManager"
        private const val PROMPTS_ROOT = "prompts"
        private const val HISTORY_DIR = "history"
        private const val VERSION_PREFIX = "v"
        private const val VERSION_DATE_FORMAT = "yyyyMMdd_HHmmss"

        private val dateFormat = SimpleDateFormat(VERSION_DATE_FORMAT, Locale.getDefault())

        @Volatile
        private var instance: PromptManager? = null

        fun getInstance(context: Context): PromptManager =
            instance ?: synchronized(this) {
                instance ?: PromptManager(context.applicationContext).also { instance = it }
            }
    }
}

/**
 * Metadata for a single archived version of a prompt.
 *
 * @property type           Which prompt type this version belongs to
 * @property language       "cn" or "en"
 * @property filename       Archive filename (e.g. `v2_20260507_150000_cn.md`)
 * @property versionNumber  Auto-incrementing version index
 * @property savedAt        Unix timestamp (ms) when this version was archived
 * @property sizeBytes      File size in bytes
 */
data class PromptVersion(
    val type: PromptManager.PromptType,
    val language: String,
    val filename: String,
    val versionNumber: Int,
    val savedAt: Long,
    val sizeBytes: Int,
)
