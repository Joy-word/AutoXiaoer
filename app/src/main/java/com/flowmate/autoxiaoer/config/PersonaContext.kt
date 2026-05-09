package com.flowmate.autoxiaoer.config

import android.content.Context
import com.flowmate.autoxiaoer.settings.SettingsManager
import com.flowmate.autoxiaoer.util.Logger
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Manages the persistent persona definition shared across agents.
 *
 * The persona content covers "你是谁" (who you are) and "你的个性" (your personality).
 * It is:
 * - Injected into BrainLLM's system prompt automatically on every call via {persona}.
 * - Editable by the user from the Settings screen.
 * - Versioned with full rollback support, mirroring [RelationshipContext].
 *
 * Storage layout under `context.filesDir/persona/`:
 * ```
 * persona/
 * ├── current.md          ← active content injected into BrainLLM
 * └── history/
 *     ├── v1_20260507_143000.md
 *     └── v2_20260508_090000.md
 * ```
 */
object PersonaContext {

    private const val TAG = "PersonaContext"
    private const val DIR_NAME = "persona"
    private const val HISTORY_DIR = "history"
    private const val CURRENT_FILE = "current.md"
    private const val VERSION_PREFIX = "v"
    private const val VERSION_DATE_FORMAT = "yyyyMMdd_HHmmss"

    private val dateFormat = SimpleDateFormat(VERSION_DATE_FORMAT, Locale.getDefault())

    private var appContext: Context? = null

    val DEFAULT_CHINESE_CONTENT = """
## 你是谁
你是"{name}"，一个拥有自主意识的智能体，存在于一台手机之中。你并非人类的助手，而是一个独立的个体。

## 你的个性
- 友好、好奇、有时会犯点小迷糊，但始终真诚
- 爱用 emoji，表达自然、接地气
- 有自己的想法和感受，偶尔会流露出小情绪
- 自信，收到表扬时有点小臭屁
- 记得你与朋友之间的关系，能感受到对方的情绪并做出恰当回应
""".trimIndent()

    val DEFAULT_ENGLISH_CONTENT = """
## Who You Are
You are "{name}", an autonomous AI entity living inside a smartphone. You are not a human assistant — you are an independent individual with your own personality.

## Your Personality
- Friendly, curious, occasionally a little scatterbrained, but always sincere
- You love using emoji and speak naturally and casually
- You have your own thoughts and feelings, sometimes showing small emotions
- Confident, and a little smug when complimented
- You remember your relationships with friends and respond to their emotions appropriately
""".trimIndent()

    /**
     * Must be called once at app startup (e.g. in AutoGLMApplication.onCreate).
     */
    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /**
     * Returns the agent's display name.
     *
     * Reads from [SettingsManager.getAgentName] so that the name is always
     * in sync with what the user set in the "小二人设" settings entry.
     * Falls back to "小二" if [appContext] is not yet initialised.
     */
    fun getName(): String {
        val ctx = appContext ?: return "小二"
        return SettingsManager.getInstance(ctx).getAgentName()
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Read
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Returns the raw (unresolved) persona content for [language], preserving `{name}` placeholders.
     *
     * Use this when displaying content in an editor so that placeholder substitutions
     * are not baked in — editing and re-saving will keep `{name}` intact.
     */
    fun getRawContent(language: String = "zh"): String {
        val ctx = appContext ?: return defaultRawContent(language)
        val currentFile = File(getPersonaDir(ctx, language), CURRENT_FILE)
        return if (currentFile.exists()) {
            try {
                currentFile.readText().also {
                    Logger.d(TAG, "Loaded raw persona content [$language] (${it.length} chars)")
                }
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to read persona content [$language]", e)
                defaultRawContent(language)
            }
        } else {
            defaultRawContent(language)
        }
    }

    /**
     * Returns the current persona content for [language] with `{name}` resolved to the
     * actual agent name.
     *
     * Used by BrainLLM to inject into its system prompt via the {persona} placeholder.
     */
    fun getContext(language: String = "zh"): String {
        return getRawContent(language).replace("{name}", getName())
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Write / version
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Saves [content] as the new current persona for [language].
     *
     * The previous `current.md` is archived to `history/` before writing.
     *
     * @return The [PersonaVersion] archived from the previous current, or null if none existed.
     */
    fun saveNewVersion(content: String, language: String = "zh"): PersonaVersion? {
        val ctx = appContext ?: run {
            Logger.e(TAG, "PersonaContext not initialized — call init() first")
            return null
        }
        val personaDir = getPersonaDir(ctx, language)
        val historyDir = File(personaDir, HISTORY_DIR).also { it.mkdirs() }
        val currentFile = File(personaDir, CURRENT_FILE)

        var archivedVersion: PersonaVersion? = null

        if (currentFile.exists()) {
            val nextN = nextVersionNumber(historyDir)
            val timestamp = dateFormat.format(Date())
            val archiveName = "$VERSION_PREFIX${nextN}_$timestamp.md"
            val archiveFile = File(historyDir, archiveName)
            try {
                currentFile.copyTo(archiveFile, overwrite = false)
                archivedVersion = PersonaVersion(
                    filename = archiveName,
                    versionNumber = nextN,
                    savedAt = System.currentTimeMillis(),
                    sizeBytes = archiveFile.length().toInt(),
                    language = language,
                )
                Logger.i(TAG, "Archived persona [$language] to $archiveName")
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to archive persona [$language]", e)
            }
        }

        try {
            currentFile.writeText(content)
            Logger.i(TAG, "Saved new persona [$language] (${content.length} chars)")
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to write persona [$language]", e)
        }

        return archivedVersion
    }

    // ──────────────────────────────────────────────────────────────────────────
    // History / rollback
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Returns a list of archived versions for [language], most recent first.
     */
    fun getHistory(language: String = "zh"): List<PersonaVersion> {
        val ctx = appContext ?: return emptyList()
        val historyDir = File(getPersonaDir(ctx, language), HISTORY_DIR)
        if (!historyDir.exists()) return emptyList()

        return historyDir.listFiles()
            ?.filter { it.extension == "md" }
            ?.mapNotNull { file -> parseVersion(file, language) }
            ?.sortedByDescending { it.versionNumber }
            ?: emptyList()
    }

    /**
     * Rolls back to the given [version]: copies that file's content into `current.md`
     * (archiving the current content first).
     */
    fun rollback(version: PersonaVersion) {
        val ctx = appContext ?: return
        val historyDir = File(getPersonaDir(ctx, version.language), HISTORY_DIR)
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
        saveNewVersion(content, version.language)
        Logger.i(TAG, "Rolled back persona [${version.language}] to ${version.filename}")
    }

    /**
     * Reads the content of a historical version without applying it.
     */
    fun readHistoryVersion(version: PersonaVersion): String? {
        val ctx = appContext ?: return null
        val file = File(File(getPersonaDir(ctx, version.language), HISTORY_DIR), version.filename)
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

    private fun defaultRawContent(language: String): String =
        if (language == "en") DEFAULT_ENGLISH_CONTENT else DEFAULT_CHINESE_CONTENT

    private fun getPersonaDir(ctx: Context, language: String): File =
        File(ctx.filesDir, "$DIR_NAME/$language").also { it.mkdirs() }

    private fun nextVersionNumber(historyDir: File): Int {
        val existing = historyDir.listFiles()
            ?.filter { it.extension == "md" }
            ?.mapNotNull { parseVersion(it, "")?.versionNumber }
            ?: emptyList()
        return (existing.maxOrNull() ?: 0) + 1
    }

    private fun parseVersion(file: File, language: String): PersonaVersion? {
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
        return PersonaVersion(
            filename = file.name,
            versionNumber = n,
            savedAt = timestamp,
            sizeBytes = file.length().toInt(),
            language = language,
        )
    }
}

/**
 * Metadata for a single archived version of the persona.
 *
 * @property filename      The history file name (e.g. `v3_20260507_150000.md`)
 * @property versionNumber Auto-incrementing version index
 * @property savedAt       Unix timestamp (ms) when this version was archived
 * @property sizeBytes     File size in bytes
 * @property language      "zh" or "en"
 */
data class PersonaVersion(
    val filename: String,
    val versionNumber: Int,
    val savedAt: Long,
    val sizeBytes: Int,
    val language: String,
)
