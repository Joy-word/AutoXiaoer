package com.flowmate.autoxiaoer.agent.tools

import com.flowmate.autoxiaoer.config.MemoryContext
import com.flowmate.autoxiaoer.util.Logger
import org.json.JSONObject

/**
 * Reads the `_index.md` master index of the experience-memory store.
 *
 * The index is rebuilt automatically by [MemoryContext] on every write/delete,
 * so the returned content always reflects the current state of the store.
 */
class ReadMemoryIndexTool : AgentTool {
    override val name: String = NAME
    override val description: String =
        "Read the experience-memory master index (_index.md). " +
            "Use this at the start of a task to check whether relevant operation notes " +
            "or contact memories already exist before deciding to read a specific file."
    override val parametersSchema = EmptyObjectSchema

    override suspend fun execute(args: JSONObject, ctx: ToolContext): ToolResult {
        val content = MemoryContext.readIndex()
        Logger.i(TAG, "read_memory_index (${content.length} chars)")
        val observation = if (ctx.isEnglish) {
            "[Memory Index]\n$content"
        } else {
            "【经验记忆索引】\n$content"
        }
        return ToolResult.Continue(observation)
    }

    companion object {
        const val NAME = "read_memory_index"
        private const val TAG = "ReadMemoryIndexTool"
    }
}

/**
 * Reads a specific experience-memory file by its relative path.
 *
 * Example paths: `apps/支付宝/蚂蚁庄园.md`, `contacts/张三.md`, `notes/每日签到.md`.
 */
class ReadMemoryFileTool : AgentTool {
    override val name: String = NAME
    override val description: String =
        "Read a specific experience-memory file by its relative path " +
            "(e.g. `apps/Alipay/AntForest.md`, `contacts/Alice.md`). " +
            "Call read_memory_index first to confirm the path exists."
    override val parametersSchema =
        objectSchema(required = listOf("path")) {
            stringField(
                "path",
                "Relative path to the memory file within the memory/ directory, " +
                    "e.g. \"apps/支付宝/蚂蚁庄园.md\" or \"contacts/张三.md\".",
            )
        }

    override suspend fun execute(args: JSONObject, ctx: ToolContext): ToolResult {
        val path = args.optString("path").trim()
        if (path.isBlank()) {
            return ToolResult.Continue(
                if (ctx.isEnglish) "read_memory_file requires a `path` argument."
                else "read_memory_file 缺少 path 参数。",
            )
        }
        val content = MemoryContext.readFile(path)
        Logger.i(TAG, "read_memory_file: $path (found=${content != null})")
        return if (content != null) {
            val observation = if (ctx.isEnglish) {
                "[Memory: $path]\n$content"
            } else {
                "【记忆文件：$path】\n$content"
            }
            ToolResult.Continue(observation)
        } else {
            ToolResult.Continue(
                if (ctx.isEnglish) "Memory file not found: $path"
                else "记忆文件不存在：$path",
            )
        }
    }

    companion object {
        const val NAME = "read_memory_file"
        private const val TAG = "ReadMemoryFileTool"
    }
}

/**
 * Creates or overwrites a memory file at the given relative path.
 *
 * The backend (`_index.md`) is rebuilt automatically after every successful write.
 */
class WriteMemoryFileTool : AgentTool {
    override val name: String = NAME
    override val description: String =
        "Create or overwrite an experience-memory file. " +
            "The master index (_index.md) is updated automatically — no separate index call needed. " +
            "Use Markdown format. Paths: apps/{App}/{Feature}.md | contacts/{Name}.md | notes/{title}.md"
    override val parametersSchema =
        objectSchema(required = listOf("path", "content")) {
            stringField(
                "path",
                "Relative path for the memory file, e.g. \"apps/支付宝/蚂蚁庄园.md\".",
            )
            stringField(
                "content",
                "Full Markdown content of the memory file.",
            )
        }

    override suspend fun execute(args: JSONObject, ctx: ToolContext): ToolResult {
        val path = args.optString("path").trim()
        val content = args.optString("content")
        if (path.isBlank()) {
            return ToolResult.Continue(
                if (ctx.isEnglish) "write_memory_file requires a `path` argument."
                else "write_memory_file 缺少 path 参数。",
            )
        }
        if (content.isBlank()) {
            return ToolResult.Continue(
                if (ctx.isEnglish) "write_memory_file requires a non-empty `content` argument."
                else "write_memory_file 的 content 不能为空。",
            )
        }
        val ok = MemoryContext.writeFile(path, content)
        Logger.i(TAG, "write_memory_file: $path (success=$ok)")
        return if (ok) {
            ToolResult.Continue(
                if (ctx.isEnglish) "[Memory Saved] $path has been written and the index updated."
                else "【记忆已保存】$path 写入成功，索引已自动更新。",
            )
        } else {
            ToolResult.Continue(
                if (ctx.isEnglish) "Failed to write memory file: $path. Check that MemoryContext is initialised."
                else "写入失败：$path。请确认 MemoryContext 已初始化。",
            )
        }
    }

    companion object {
        const val NAME = "write_memory_file"
        private const val TAG = "WriteMemoryFileTool"
    }
}

/**
 * Deletes a memory file at the given relative path.
 *
 * Use this when migrating a file to a different path (read → write new → delete old)
 * or when a record is no longer relevant.
 * The backend (`_index.md`) is rebuilt automatically after deletion.
 */
class DeleteMemoryFileTool : AgentTool {
    override val name: String = NAME
    override val description: String =
        "Delete an experience-memory file by its relative path. " +
            "The master index (_index.md) is updated automatically. " +
            "Typical use: completing a migration (read old → write new path → delete old)."
    override val parametersSchema =
        objectSchema(required = listOf("path")) {
            stringField(
                "path",
                "Relative path of the memory file to delete, e.g. \"notes/旧笔记.md\".",
            )
        }

    override suspend fun execute(args: JSONObject, ctx: ToolContext): ToolResult {
        val path = args.optString("path").trim()
        if (path.isBlank()) {
            return ToolResult.Continue(
                if (ctx.isEnglish) "delete_memory_file requires a `path` argument."
                else "delete_memory_file 缺少 path 参数。",
            )
        }
        val deleted = MemoryContext.deleteFile(path)
        Logger.i(TAG, "delete_memory_file: $path (deleted=$deleted)")
        return ToolResult.Continue(
            when {
                deleted && ctx.isEnglish -> "[Memory Deleted] $path has been removed and the index updated."
                deleted -> "【记忆已删除】$path 已删除，索引已自动更新。"
                ctx.isEnglish -> "Memory file not found or could not be deleted: $path"
                else -> "文件不存在或删除失败：$path"
            },
        )
    }

    companion object {
        const val NAME = "delete_memory_file"
        private const val TAG = "DeleteMemoryFileTool"
    }
}
