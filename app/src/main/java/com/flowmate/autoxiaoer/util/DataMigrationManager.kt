package com.flowmate.autoxiaoer.util

import android.content.Context
import android.content.SharedPreferences
import com.flowmate.autoxiaoer.BuildConfig
import com.flowmate.autoxiaoer.settings.SettingsManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Handles export and import of app data for device migration ("软件搬家").
 *
 * Export modes:
 * - **Persona-only**: packages all user-authored prompts and context files
 *   (persona, relationships, behavior rules, PhoneAgent/LLMAgent/BrainLLM system prompts).
 * - **Full**: persona-only content **plus** task execution history.
 *
 * API keys are intentionally excluded from both modes.
 *
 * Zip layout:
 * ```
 * autoxiaoer_backup_<timestamp>.zip
 * ├── manifest.json          ← format version + export mode
 * ├── prefs/
 * │   └── prompts.json       ← SharedPreferences prompt keys (no API keys)
 * ├── files/
 * │   ├── persona/...        ← PersonaContext files
 * │   ├── relationships/...  ← RelationshipContext files
 * │   ├── behavior_rules/... ← BehaviorContext files
 * │   ├── prompts/...        ← PromptManager files
 * │   └── task_history/...   ← (full mode only) HistoryManager files
 * ```
 */
object DataMigrationManager {

    private const val TAG = "DataMigrationManager"
    private const val MANIFEST_FILE = "manifest.json"
    private const val PREFS_DIR = "prefs"
    private const val FILES_DIR = "files"
    private const val PREFS_PROMPTS_FILE = "prompts.json"
    private const val FORMAT_VERSION = 1
    private const val EXPORT_TIMESTAMP_FORMAT = "yyyyMMdd_HHmmss"

    private val exportTimestampFormat = SimpleDateFormat(EXPORT_TIMESTAMP_FORMAT, Locale.getDefault())

    // SharedPreferences keys that hold user-authored prompts (no API keys).
    private val PROMPT_PREF_KEYS = listOf(
        "custom_system_prompt_cn",
        "custom_system_prompt_en",
        "llm_agent_custom_prompt_cn",
        "llm_agent_custom_prompt_en",
        "brain_llm_custom_prompt_cn",
        "brain_llm_custom_prompt_en",
        "agent_name",
        "agent_language",
        "llm_agent_language",
    )

    // Directories under filesDir to include in persona-only mode.
    private val PERSONA_DIRS = listOf(
        "persona",
        "relationships",
        "behavior_rules",
        "prompts",
    )

    // Directories under filesDir to include additionally in full mode.
    private val HISTORY_DIRS = listOf(
        "task_history",
    )

    // ──────────────────────────────────────────────────────────────────────────
    // Export
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Exports app data to a zip file in the app's external cache directory.
     *
     * @param context Application context.
     * @param personaOnly If true only prompt/persona data is included; if false task
     *                    history is also included.
     * @return The generated [File], or null if export failed.
     */
    fun exportData(context: Context, personaOnly: Boolean): File? = try {
        val exportDir = File(context.cacheDir, "migration_export").also { it.mkdirs() }
        val timestamp = exportTimestampFormat.format(Date())
        val mode = if (personaOnly) "persona" else "full"
        val zipFile = File(exportDir, "autoxiaoer_backup_${mode}_$timestamp.zip")

        ZipOutputStream(FileOutputStream(zipFile)).use { zip ->
            writeManifest(zip, personaOnly)
            writePromptPrefs(zip, context)
            writeFilesDirs(zip, context, personaOnly)
        }

        Logger.i(TAG, "Export complete: ${zipFile.name} (${zipFile.length()} bytes)")
        zipFile
    } catch (e: Exception) {
        Logger.e(TAG, "Export failed", e)
        null
    }

    private fun writeManifest(zip: ZipOutputStream, personaOnly: Boolean) {
        val manifest = JSONObject().apply {
            put("formatVersion", FORMAT_VERSION)
            put("appVersion", BuildConfig.VERSION_NAME)
            put("exportMode", if (personaOnly) "persona" else "full")
            put("exportedAt", System.currentTimeMillis())
        }
        zip.putNextEntry(ZipEntry(MANIFEST_FILE))
        zip.write(manifest.toString(2).toByteArray())
        zip.closeEntry()
    }

    private fun writePromptPrefs(zip: ZipOutputStream, context: Context) {
        val prefs: SharedPreferences =
            context.getSharedPreferences("autoglm_settings", Context.MODE_PRIVATE)
        val obj = JSONObject()
        for (key in PROMPT_PREF_KEYS) {
            val value = prefs.getString(key, null)
            if (value != null) obj.put(key, value)
        }
        zip.putNextEntry(ZipEntry("$PREFS_DIR/$PREFS_PROMPTS_FILE"))
        zip.write(obj.toString(2).toByteArray())
        zip.closeEntry()
    }

    private fun writeFilesDirs(zip: ZipOutputStream, context: Context, personaOnly: Boolean) {
        val dirs = if (personaOnly) PERSONA_DIRS else PERSONA_DIRS + HISTORY_DIRS
        for (dirName in dirs) {
            val dir = File(context.filesDir, dirName)
            if (dir.exists()) {
                addDirToZip(zip, dir, "$FILES_DIR/$dirName")
            }
        }
    }

    private fun addDirToZip(zip: ZipOutputStream, dir: File, zipPath: String) {
        dir.walkTopDown().forEach { file ->
            if (file.isFile) {
                val relativePath = file.relativeTo(dir).path.replace('\\', '/')
                zip.putNextEntry(ZipEntry("$zipPath/$relativePath"))
                FileInputStream(file).use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Import
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Imports data from a previously exported zip file.
     *
     * All matching data is overwritten. API keys in SharedPreferences are never touched.
     *
     * @param context Application context.
     * @param zipFile The zip file to import from.
     * @return [ImportResult] describing what was imported or the error that occurred.
     */
    fun importData(context: Context, zipFile: File): ImportResult {
        try {
            var manifest: JSONObject? = null
            var promptsJson: JSONObject? = null
            val fileEntries = mutableListOf<Pair<String, ByteArray>>()

            ZipInputStream(FileInputStream(zipFile)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    when {
                        entry.name == MANIFEST_FILE -> {
                            manifest = JSONObject(zip.readBytes().toString(Charsets.UTF_8))
                        }
                        entry.name == "$PREFS_DIR/$PREFS_PROMPTS_FILE" -> {
                            promptsJson = JSONObject(zip.readBytes().toString(Charsets.UTF_8))
                        }
                        entry.name.startsWith("$FILES_DIR/") && !entry.isDirectory -> {
                            fileEntries.add(entry.name to zip.readBytes())
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }

            if (manifest == null) {
                return ImportResult.Failure("无效的备份文件：缺少 manifest.json")
            }

            val formatVersion = manifest!!.optInt("formatVersion", 0)
            if (formatVersion != FORMAT_VERSION) {
                return ImportResult.Failure("不支持的备份格式版本：$formatVersion")
            }

            // Restore SharedPreferences prompt keys
            if (promptsJson != null) {
                restorePromptPrefs(context, promptsJson!!)
            }

            // Restore files
            val filesDir = context.filesDir
            for ((entryName, bytes) in fileEntries) {
                // entryName is like "files/persona/zh/current.md"
                val relativePath = entryName.removePrefix("$FILES_DIR/")
                val targetFile = File(filesDir, relativePath)
                targetFile.parentFile?.mkdirs()
                targetFile.writeBytes(bytes)
            }

            val exportMode = manifest!!.optString("exportMode", "unknown")
            Logger.i(TAG, "Import complete: mode=$exportMode, files=${fileEntries.size}")
            return ImportResult.Success(exportMode = exportMode, fileCount = fileEntries.size)
        } catch (e: Exception) {
            Logger.e(TAG, "Import failed", e)
            return ImportResult.Failure(e.message ?: "未知错误")
        }
    }

    private fun restorePromptPrefs(context: Context, obj: JSONObject) {
        val prefs = context.getSharedPreferences("autoglm_settings", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        for (key in PROMPT_PREF_KEYS) {
            if (obj.has(key)) {
                editor.putString(key, obj.getString(key))
            }
        }
        editor.apply()
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Result types
    // ──────────────────────────────────────────────────────────────────────────

    sealed class ImportResult {
        data class Success(val exportMode: String, val fileCount: Int) : ImportResult()
        data class Failure(val reason: String) : ImportResult()
    }
}
