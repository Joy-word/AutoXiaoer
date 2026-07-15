package com.flowmate.autoxiaoer.config

import android.content.Context
import com.flowmate.autoxiaoer.util.Logger
import java.io.File

/**
 * Manages the experience-memory file store used by [com.flowmate.autoxiaoer.agent.LLMAgent].
 *
 * Storage layout under `context.filesDir/memory/`:
 * ```
 * memory/
 * ├── _index.md              ← auto-maintained master index (rebuilt on every write/delete)
 * ├── apps/
 * │   └── {AppName}/
 * │       └── {Feature}.md
 * ├── contacts/
 * │   └── {Name}.md
 * └── notes/
 *     └── {custom}.md
 * ```
 *
 * The `_index.md` file is **never written directly by the LLM**; it is rebuilt automatically
 * after every [writeFile] or [deleteFile] call so that [readIndex] always returns a fresh view.
 */
object MemoryContext {

    private const val TAG = "MemoryContext"
    const val ROOT_DIR = "memory"
    private const val INDEX_FILE = "_index.md"

    private var appContext: Context? = null

    /** Must be called once at app startup (e.g. in Application.onCreate). */
    fun init(context: Context) {
        appContext = context.applicationContext
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Public API
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Returns the content of `_index.md`, rebuilding it first if it is stale or missing.
     */
    fun readIndex(): String {
        val ctx = appContext ?: return "(MemoryContext not initialised)"
        val root = getRoot(ctx)
        val indexFile = File(root, INDEX_FILE)
        // Always rebuild to ensure freshness; cheap for typical file counts.
        rebuildIndex(root)
        return try {
            indexFile.readText()
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to read index", e)
            "(index read error: ${e.message})"
        }
    }

    /**
     * Reads a memory file at the given relative [path] (e.g. `apps/支付宝/蚂蚁庄园.md`).
     * Returns null when the file does not exist.
     */
    fun readFile(path: String): String? {
        val ctx = appContext ?: return null
        val file = File(getRoot(ctx), sanitise(path))
        if (!file.exists()) return null
        return try {
            file.readText()
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to read $path", e)
            null
        }
    }

    /**
     * Creates or overwrites the memory file at [path] with [content].
     * Parent directories are created as needed.
     * Rebuilds `_index.md` automatically after writing.
     *
     * @return true on success.
     */
    fun writeFile(path: String, content: String): Boolean {
        val ctx = appContext ?: return false
        val root = getRoot(ctx)
        val file = File(root, sanitise(path))
        return try {
            file.parentFile?.mkdirs()
            file.writeText(content)
            Logger.i(TAG, "Wrote memory file: $path (${content.length} chars)")
            rebuildIndex(root)
            true
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to write $path", e)
            false
        }
    }

    /**
     * Deletes the memory file at [path].
     * Rebuilds `_index.md` automatically after deletion.
     *
     * @return true if the file was deleted, false if it did not exist or deletion failed.
     */
    fun deleteFile(path: String): Boolean {
        val ctx = appContext ?: return false
        val root = getRoot(ctx)
        val file = File(root, sanitise(path))
        if (!file.exists()) return false
        return try {
            val deleted = file.delete()
            if (deleted) {
                Logger.i(TAG, "Deleted memory file: $path")
                rebuildIndex(root)
            }
            deleted
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to delete $path", e)
            false
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Index rebuilding
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Scans `memory/apps/`, `memory/contacts/`, and `memory/notes/` and rewrites `_index.md`.
     *
     * Format:
     * ```
     * # 经验记忆索引 / Experience Memory Index
     *
     * ## Apps
     * - 支付宝：蚂蚁庄园、转账
     *
     * ## Contacts
     * - 张三
     *
     * ## Notes
     * - 每日签到流程
     * ```
     */
    private fun rebuildIndex(root: File) {
        val sb = StringBuilder()
        sb.appendLine("# 经验记忆索引 / Experience Memory Index")
        sb.appendLine()

        // Apps
        val appsDir = File(root, "apps")
        sb.appendLine("## Apps")
        if (appsDir.isDirectory) {
            appsDir.listFiles()
                ?.filter { it.isDirectory }
                ?.sortedBy { it.name }
                ?.forEach { appDir ->
                    val features = appDir.listFiles()
                        ?.filter { it.isFile && it.extension == "md" }
                        ?.map { it.nameWithoutExtension }
                        ?.sorted()
                        ?: emptyList()
                    if (features.isNotEmpty()) {
                        sb.appendLine("- ${appDir.name}：${features.joinToString("、")}")
                    }
                }
        }
        sb.appendLine()

        // Contacts
        val contactsDir = File(root, "contacts")
        sb.appendLine("## Contacts")
        if (contactsDir.isDirectory) {
            contactsDir.listFiles()
                ?.filter { it.isFile && it.extension == "md" }
                ?.sortedBy { it.name }
                ?.forEach { sb.appendLine("- ${it.nameWithoutExtension}") }
        }
        sb.appendLine()

        // Notes
        val notesDir = File(root, "notes")
        sb.appendLine("## Notes")
        if (notesDir.isDirectory) {
            notesDir.listFiles()
                ?.filter { it.isFile && it.extension == "md" }
                ?.sortedBy { it.name }
                ?.forEach { sb.appendLine("- ${it.nameWithoutExtension}") }
        }

        try {
            File(root, INDEX_FILE).writeText(sb.toString().trimEnd())
            Logger.d(TAG, "Rebuilt memory index")
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to rebuild index", e)
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────────────

    private fun getRoot(ctx: Context): File =
        File(ctx.filesDir, ROOT_DIR).also { it.mkdirs() }

    /**
     * Strips leading slashes and blocks path traversal (`..`).
     * The LLM is expected to provide relative paths like `apps/支付宝/蚂蚁庄园.md`.
     */
    private fun sanitise(path: String): String =
        path.trimStart('/', '\\')
            .split("/", "\\")
            .filter { it.isNotBlank() && it != ".." }
            .joinToString("/")
}
