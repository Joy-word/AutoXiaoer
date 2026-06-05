package com.flowmate.autoxiaoer.util

import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.flowmate.autoxiaoer.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Handles export and import of app data for device migration ("软件搬家").
 *
 * Users can selectively export/import these sections:
 * - Persona, behavior rules, relationships (file-based context)
 * - System prompts (SharedPreferences + PromptManager files)
 * - Task history, scheduled tasks, task templates
 *
 * API keys are intentionally excluded from all exports.
 *
 * Zip layout (v2):
 * ```
 * autoxiaoer_backup_<timestamp>.zip
 * ├── manifest.json
 * ├── prefs/
 * │   ├── system_prompts.json   ← (optional) SharedPreferences prompt keys
 * │   ├── scheduled_tasks.json  ← (optional) scheduled task list
 * │   └── task_templates.json   ← (optional) task template list
 * └── files/
 *     ├── persona/...
 *     ├── relationships/...
 *     ├── behavior_rules/...
 *     ├── prompts/...
 *     └── task_history/...
 * ```
 */
object DataMigrationManager {

    private const val TAG = "DataMigrationManager"
    private const val MANIFEST_FILE = "manifest.json"
    private const val PREFS_DIR = "prefs"
    private const val FILES_DIR = "files"
    private const val PREFS_PROMPTS_FILE = "prompts.json"
    private const val PREFS_SYSTEM_PROMPTS_FILE = "system_prompts.json"
    private const val PREFS_SCHEDULED_TASKS_FILE = "scheduled_tasks.json"
    private const val PREFS_TASK_TEMPLATES_FILE = "task_templates.json"
    private const val FORMAT_VERSION = 2
    private const val LEGACY_FORMAT_VERSION = 1
    private const val EXPORT_TIMESTAMP_FORMAT = "yyyyMMdd_HHmmss"
    private const val SETTINGS_PREFS_NAME = "autoglm_settings"
    private const val SCHEDULED_TASKS_PREFS_NAME = "scheduled_tasks"
    private const val KEY_SCHEDULED_TASKS = "tasks"
    private const val KEY_TASK_TEMPLATES = "task_templates"

    const val SECTION_PERSONA = "persona"
    const val SECTION_BEHAVIOR_RULES = "behavior_rules"
    const val SECTION_RELATIONSHIPS = "relationships"
    const val SECTION_SYSTEM_PROMPTS = "system_prompts"
    const val SECTION_TASK_HISTORY = "task_history"
    const val SECTION_SCHEDULED_TASKS = "scheduled_tasks"
    const val SECTION_TASK_TEMPLATES = "task_templates"

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

    private val SECTION_TO_DIR = mapOf(
        SECTION_PERSONA to "persona",
        SECTION_BEHAVIOR_RULES to "behavior_rules",
        SECTION_RELATIONSHIPS to "relationships",
        SECTION_SYSTEM_PROMPTS to "prompts",
        SECTION_TASK_HISTORY to "task_history",
    )

    data class ExportOptions(
        val persona: Boolean = true,
        val behaviorRules: Boolean = true,
        val relationships: Boolean = true,
        val systemPrompts: Boolean = false,
        val taskHistory: Boolean = false,
        val scheduledTasks: Boolean = false,
        val taskTemplates: Boolean = false,
    ) {
        fun hasAnySelected(): Boolean =
            persona || behaviorRules || relationships || systemPrompts ||
                taskHistory || scheduledTasks || taskTemplates

        fun selectedSections(): List<String> = buildList {
            if (persona) add(SECTION_PERSONA)
            if (behaviorRules) add(SECTION_BEHAVIOR_RULES)
            if (relationships) add(SECTION_RELATIONSHIPS)
            if (systemPrompts) add(SECTION_SYSTEM_PROMPTS)
            if (taskHistory) add(SECTION_TASK_HISTORY)
            if (scheduledTasks) add(SECTION_SCHEDULED_TASKS)
            if (taskTemplates) add(SECTION_TASK_TEMPLATES)
        }

        fun isSectionSelected(section: String): Boolean = when (section) {
            SECTION_PERSONA -> persona
            SECTION_BEHAVIOR_RULES -> behaviorRules
            SECTION_RELATIONSHIPS -> relationships
            SECTION_SYSTEM_PROMPTS -> systemPrompts
            SECTION_TASK_HISTORY -> taskHistory
            SECTION_SCHEDULED_TASKS -> scheduledTasks
            SECTION_TASK_TEMPLATES -> taskTemplates
            else -> false
        }

        companion object {
            val DEFAULT = ExportOptions(
                persona = true,
                behaviorRules = true,
                relationships = true,
            )

            fun fromAvailableSections(sections: Set<String>) = ExportOptions(
                persona = SECTION_PERSONA in sections,
                behaviorRules = SECTION_BEHAVIOR_RULES in sections,
                relationships = SECTION_RELATIONSHIPS in sections,
                systemPrompts = SECTION_SYSTEM_PROMPTS in sections,
                taskHistory = SECTION_TASK_HISTORY in sections,
                scheduledTasks = SECTION_SCHEDULED_TASKS in sections,
                taskTemplates = SECTION_TASK_TEMPLATES in sections,
            )
        }
    }

    data class BackupInspection(
        val formatVersion: Int,
        val appVersion: String?,
        val exportedAt: Long?,
        val exportMode: String?,
        val availableSections: Set<String>,
    )

    sealed class InspectionResult {
        data class Success(val inspection: BackupInspection) : InspectionResult()
        data class Failure(val reason: String) : InspectionResult()
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Export
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Exports app data to a zip file in the app's cache directory.
     *
     * @param context Application context.
     * @param options Sections to include in the export.
     * @return The generated [File], or null if export failed or nothing was selected.
     */
    fun exportData(context: Context, options: ExportOptions): File? {
        if (!options.hasAnySelected()) {
            Logger.w(TAG, "Export aborted: no sections selected")
            return null
        }

        return try {
            val exportDir = File(context.cacheDir, "migration_export").also { it.mkdirs() }
            val timestamp = exportTimestampFormat.format(Date())
            val zipFile = File(exportDir, "autoxiaoer_backup_$timestamp.zip")

            ZipOutputStream(FileOutputStream(zipFile)).use { zip ->
                writeManifest(zip, options)
                if (options.systemPrompts) {
                    writeSystemPromptPrefs(zip, context)
                }
                if (options.scheduledTasks) {
                    writeScheduledTasksPrefs(zip, context)
                }
                if (options.taskTemplates) {
                    writeTaskTemplatesPrefs(zip, context)
                }
                writeSelectedFileDirs(zip, context, options)
            }

            Logger.i(TAG, "Export complete: ${zipFile.name} (${zipFile.length()} bytes)")
            zipFile
        } catch (e: Exception) {
            Logger.e(TAG, "Export failed", e)
            null
        }
    }

    /**
     * Copies the exported zip file to the system Downloads directory so it can be
     * accessed via a file manager or adb.
     *
     * On Android 10+ (API 29+) uses [MediaStore] which requires no extra permissions.
     * On older versions falls back to [Environment.getExternalStoragePublicDirectory].
     *
     * @return The display-friendly path where the file was saved, or null on failure.
     */
    fun saveToDownloads(context: Context, zipFile: File): String? = try {
        val fileName = zipFile.name
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "application/zip")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri: Uri? = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { output ->
                    zipFile.inputStream().use { input -> input.copyTo(output) }
                }
                Logger.i(TAG, "Saved to Downloads via MediaStore: $fileName")
                "/sdcard/Download/$fileName"
            } else {
                Logger.e(TAG, "MediaStore insert returned null")
                null
            }
        } else {
            @Suppress("DEPRECATION")
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            downloadsDir.mkdirs()
            val target = File(downloadsDir, fileName)
            zipFile.copyTo(target, overwrite = true)
            Logger.i(TAG, "Saved to Downloads: ${target.absolutePath}")
            target.absolutePath
        }
    } catch (e: IOException) {
        Logger.e(TAG, "Failed to save to Downloads", e)
        null
    }

    private fun writeManifest(zip: ZipOutputStream, options: ExportOptions) {
        val sections = options.selectedSections()
        val manifest = JSONObject().apply {
            put("formatVersion", FORMAT_VERSION)
            put("appVersion", BuildConfig.VERSION_NAME)
            put("exportSections", JSONArray(sections))
            put("exportedAt", System.currentTimeMillis())
        }
        zip.putNextEntry(ZipEntry(MANIFEST_FILE))
        zip.write(manifest.toString(2).toByteArray())
        zip.closeEntry()
    }

    private fun writeSystemPromptPrefs(zip: ZipOutputStream, context: Context) {
        val prefs: SharedPreferences =
            context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
        val obj = JSONObject()
        for (key in PROMPT_PREF_KEYS) {
            val value = prefs.getString(key, null)
            if (value != null) obj.put(key, value)
        }
        zip.putNextEntry(ZipEntry("$PREFS_DIR/$PREFS_SYSTEM_PROMPTS_FILE"))
        zip.write(obj.toString(2).toByteArray())
        zip.closeEntry()
    }

    private fun writeScheduledTasksPrefs(zip: ZipOutputStream, context: Context) {
        val prefs = context.getSharedPreferences(SCHEDULED_TASKS_PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_SCHEDULED_TASKS, null) ?: "[]"
        zip.putNextEntry(ZipEntry("$PREFS_DIR/$PREFS_SCHEDULED_TASKS_FILE"))
        zip.write(json.toByteArray())
        zip.closeEntry()
    }

    private fun writeTaskTemplatesPrefs(zip: ZipOutputStream, context: Context) {
        val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_TASK_TEMPLATES, null) ?: "[]"
        zip.putNextEntry(ZipEntry("$PREFS_DIR/$PREFS_TASK_TEMPLATES_FILE"))
        zip.write(json.toByteArray())
        zip.closeEntry()
    }

    private fun writeSelectedFileDirs(zip: ZipOutputStream, context: Context, options: ExportOptions) {
        for ((section, dirName) in SECTION_TO_DIR) {
            val include = when (section) {
                SECTION_PERSONA -> options.persona
                SECTION_BEHAVIOR_RULES -> options.behaviorRules
                SECTION_RELATIONSHIPS -> options.relationships
                SECTION_SYSTEM_PROMPTS -> options.systemPrompts
                SECTION_TASK_HISTORY -> options.taskHistory
                else -> false
            }
            if (!include) continue

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
    // Inspect
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Scans a backup zip and returns which data sections it contains.
     *
     * Only reads entry names and [MANIFEST_FILE]; does not load file payloads.
     */
    fun inspectBackup(zipFile: File): InspectionResult {
        return try {
            var manifest: JSONObject? = null
            val availableSections = mutableSetOf<String>()

            ZipInputStream(FileInputStream(zipFile)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    when {
                        entry.name == MANIFEST_FILE -> {
                            manifest = JSONObject(zip.readBytes().toString(Charsets.UTF_8))
                        }
                        entry.name == "$PREFS_DIR/$PREFS_SYSTEM_PROMPTS_FILE" ||
                            entry.name == "$PREFS_DIR/$PREFS_PROMPTS_FILE" -> {
                            availableSections.add(SECTION_SYSTEM_PROMPTS)
                        }
                        entry.name == "$PREFS_DIR/$PREFS_SCHEDULED_TASKS_FILE" -> {
                            availableSections.add(SECTION_SCHEDULED_TASKS)
                        }
                        entry.name == "$PREFS_DIR/$PREFS_TASK_TEMPLATES_FILE" -> {
                            availableSections.add(SECTION_TASK_TEMPLATES)
                        }
                        entry.name.startsWith("$FILES_DIR/") && !entry.isDirectory -> {
                            val topDir = entry.name
                                .removePrefix("$FILES_DIR/")
                                .substringBefore('/')
                            if (topDir != "clawbot") {
                                val section = dirToSection(topDir)
                                if (section.isNotEmpty()) {
                                    availableSections.add(section)
                                }
                            }
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }

            if (manifest == null) {
                return InspectionResult.Failure("无效的备份文件：缺少 manifest.json")
            }

            val formatVersion = manifest!!.optInt("formatVersion", 0)
            if (formatVersion != FORMAT_VERSION && formatVersion != LEGACY_FORMAT_VERSION) {
                return InspectionResult.Failure("不支持的备份格式版本：$formatVersion")
            }

            val manifestSections = parseManifestSections(manifest!!)
            if (manifestSections.isNotEmpty()) {
                availableSections.retainAll(manifestSections)
            }

            if (availableSections.isEmpty()) {
                return InspectionResult.Failure("备份文件中没有可导入的数据")
            }

            InspectionResult.Success(
                BackupInspection(
                    formatVersion = formatVersion,
                    appVersion = manifest!!.optString("appVersion").takeIf { it.isNotEmpty() },
                    exportedAt = manifest!!.optLong("exportedAt", 0L).takeIf { it > 0L },
                    exportMode = manifest!!.optString("exportMode").takeIf { it.isNotEmpty() },
                    availableSections = availableSections,
                ),
            )
        } catch (e: Exception) {
            Logger.e(TAG, "Backup inspection failed", e)
            InspectionResult.Failure(e.message ?: "无法读取备份文件")
        }
    }

    private fun parseManifestSections(manifest: JSONObject): Set<String> {
        val sectionsArray = manifest.optJSONArray("exportSections") ?: return emptySet()
        return buildSet {
            for (i in 0 until sectionsArray.length()) {
                add(sectionsArray.getString(i))
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Import
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Imports selected sections from a previously exported zip file.
     *
     * Only sections both present in the backup and selected in [options] are restored.
     * API keys are never touched.
     *
     * @param context Application context.
     * @param zipFile The zip file to import from.
     * @param options Sections the user chose to import.
     * @return [ImportResult] describing what was imported or the error that occurred.
     */
    fun importData(context: Context, zipFile: File, options: ExportOptions): ImportResult {
        if (!options.hasAnySelected()) {
            return ImportResult.Failure("请至少选择一项导入内容")
        }
        try {
            var manifest: JSONObject? = null
            var systemPromptsJson: JSONObject? = null
            var scheduledTasksJson: String? = null
            var taskTemplatesJson: String? = null
            val fileEntries = mutableListOf<Pair<String, ByteArray>>()

            ZipInputStream(FileInputStream(zipFile)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    when {
                        entry.name == MANIFEST_FILE -> {
                            manifest = JSONObject(zip.readBytes().toString(Charsets.UTF_8))
                        }
                        entry.name == "$PREFS_DIR/$PREFS_SYSTEM_PROMPTS_FILE" ||
                            entry.name == "$PREFS_DIR/$PREFS_PROMPTS_FILE" -> {
                            systemPromptsJson = JSONObject(zip.readBytes().toString(Charsets.UTF_8))
                        }
                        entry.name == "$PREFS_DIR/$PREFS_SCHEDULED_TASKS_FILE" -> {
                            scheduledTasksJson = zip.readBytes().toString(Charsets.UTF_8)
                        }
                        entry.name == "$PREFS_DIR/$PREFS_TASK_TEMPLATES_FILE" -> {
                            taskTemplatesJson = zip.readBytes().toString(Charsets.UTF_8)
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
            if (formatVersion != FORMAT_VERSION && formatVersion != LEGACY_FORMAT_VERSION) {
                return ImportResult.Failure("不支持的备份格式版本：$formatVersion")
            }

            val importedSections = mutableListOf<String>()

            if (systemPromptsJson != null && options.systemPrompts) {
                restoreSystemPromptPrefs(context, systemPromptsJson!!)
                importedSections.add(SECTION_SYSTEM_PROMPTS)
            }

            if (scheduledTasksJson != null && options.scheduledTasks) {
                restoreScheduledTasksPrefs(context, scheduledTasksJson!!)
                importedSections.add(SECTION_SCHEDULED_TASKS)
            }

            if (taskTemplatesJson != null && options.taskTemplates) {
                restoreTaskTemplatesPrefs(context, taskTemplatesJson!!)
                importedSections.add(SECTION_TASK_TEMPLATES)
            }

            val filesDir = context.filesDir
            val importedDirs = mutableSetOf<String>()
            for ((entryName, bytes) in fileEntries) {
                val relativePath = entryName.removePrefix("$FILES_DIR/")
                val topDir = relativePath.substringBefore('/')
                if (topDir == "clawbot") continue
                val section = dirToSection(topDir)
                if (!options.isSectionSelected(section)) continue

                importedDirs.add(section)

                val targetFile = File(filesDir, relativePath)
                targetFile.parentFile?.mkdirs()
                targetFile.writeBytes(bytes)
            }
            importedSections.addAll(importedDirs.filter { it.isNotEmpty() })

            if (importedSections.isEmpty()) {
                return ImportResult.Failure("未导入任何数据，请检查勾选项")
            }

            val exportMode = manifest!!.optString("exportMode", "selective")
            Logger.i(
                TAG,
                "Import complete: mode=$exportMode, sections=${importedSections.distinct()}, files=${fileEntries.size}",
            )
            return ImportResult.Success(
                exportMode = exportMode,
                fileCount = fileEntries.size,
                importedSections = importedSections.distinct(),
            )
        } catch (e: Exception) {
            Logger.e(TAG, "Import failed", e)
            return ImportResult.Failure(e.message ?: "未知错误")
        }
    }

    private fun dirToSection(dirName: String): String =
        SECTION_TO_DIR.entries.find { it.value == dirName }?.key ?: dirName

    private fun restoreSystemPromptPrefs(context: Context, obj: JSONObject) {
        val prefs = context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        for (key in PROMPT_PREF_KEYS) {
            if (obj.has(key)) {
                editor.putString(key, obj.getString(key))
            }
        }
        editor.apply()
    }

    private fun restoreScheduledTasksPrefs(context: Context, json: String) {
        context.getSharedPreferences(SCHEDULED_TASKS_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SCHEDULED_TASKS, json)
            .apply()
    }

    private fun restoreTaskTemplatesPrefs(context: Context, json: String) {
        context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TASK_TEMPLATES, json)
            .apply()
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Result types
    // ──────────────────────────────────────────────────────────────────────────

    sealed class ImportResult {
        data class Success(
            val exportMode: String,
            val fileCount: Int,
            val importedSections: List<String> = emptyList(),
        ) : ImportResult()

        data class Failure(val reason: String) : ImportResult()
    }
}
