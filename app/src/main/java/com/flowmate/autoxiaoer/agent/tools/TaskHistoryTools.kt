package com.flowmate.autoxiaoer.agent.tools

import com.flowmate.autoxiaoer.history.TaskHistory
import com.flowmate.autoxiaoer.util.Logger
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val HISTORY_TIME_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

/**
 * Maximum allowed value for `count` in [QueryTaskHistoryTool]. Larger requests are clamped.
 */
private const val MAX_HISTORY_QUERY_COUNT = 10

/**
 * Returns an overview of the most recent completed tasks.
 *
 * Mirrors the legacy `query_task_history` action.
 */
class QueryTaskHistoryTool : AgentTool {
    override val name: String = NAME
    override val description: String =
        "Return a JSON overview of recent completed tasks (id, description, completion message, " +
            "success flag, start/end time). Use this when the user references something \"earlier\" or \"last time\"."
    override val parametersSchema =
        objectSchema(required = listOf("count")) {
            integerField(
                "count",
                "How many of the most recent tasks to return.",
                minimum = 1,
                maximum = MAX_HISTORY_QUERY_COUNT,
            )
        }

    override suspend fun execute(args: JSONObject, ctx: ToolContext): ToolResult {
        val rawCount = parseCount(args)
            ?: return ToolResult.Continue(
                if (ctx.isEnglish) {
                    "query_task_history requires a positive integer `count` (1–$MAX_HISTORY_QUERY_COUNT)."
                } else {
                    "query_task_history 的 count 未填写或无效，取值应为 1–$MAX_HISTORY_QUERY_COUNT 的整数，请重新输出。"
                },
            )
        val count = rawCount.coerceIn(1, MAX_HISTORY_QUERY_COUNT)

        val historyManager = ctx.historyManager
            ?: return ToolResult.Continue(
                if (ctx.isEnglish) "Task history query failed: HistoryManager not available."
                else "历史任务查询失败：未启用历史记录",
            )

        val resultMessage = try {
            val tasks = historyManager.historyList.value.take(count)
            formatOverview(tasks, ctx.isEnglish)
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to query task history overview", e)
            if (ctx.isEnglish) "Task history query failed: ${e.message}" else "历史任务查询失败：${e.message}"
        }

        Logger.i(TAG, "query_task_history: count=$count")
        val observation = if (ctx.isEnglish) "$resultMessage\n\nDecide your next action based on the above."
        else "$resultMessage\n\n请根据上述信息决定下一步操作。"
        return ToolResult.Continue(observation)
    }

    private fun parseCount(json: JSONObject): Int? {
        if (!json.has("count") || json.isNull("count")) return null
        val raw = json.opt("count")
        val count = when (raw) {
            is Number -> raw.toInt()
            is String -> raw.trim().toIntOrNull()
            else -> null
        } ?: return null
        return if (count >= 1) count else null
    }

    private fun formatOverview(tasks: List<TaskHistory>, isEn: Boolean): String {
        val tasksArray = JSONArray()
        tasks.forEach { task ->
            tasksArray.put(
                JSONObject().apply {
                    put("id", task.id)
                    put("taskDescription", task.taskDescription)
                    put("completionMessage", task.completionMessage ?: "")
                    put("success", task.success)
                    put("startTime", HISTORY_TIME_FORMAT.format(Date(task.startTime)))
                    put("endTime", task.endTime?.let { HISTORY_TIME_FORMAT.format(Date(it)) } ?: "")
                },
            )
        }
        val jsonOut = JSONObject().apply {
            put("tasks", tasksArray)
            put("count", tasks.size)
        }
        val title = if (isEn) "[Task History Overview]" else "【历史任务概览】"
        return "$title\n```json\n${jsonOut.toString(2)}\n```"
    }

    companion object {
        const val NAME = "query_task_history"
        private const val TAG = "QueryTaskHistoryTool"
    }
}

/**
 * Returns planning-round detail for a specific past task by id.
 *
 * Mirrors the legacy `get_task_history_detail` action.
 */
class GetTaskHistoryDetailTool : AgentTool {
    override val name: String = NAME
    override val description: String =
        "Return per-round planning detail for a single past task. " +
            "Call query_task_history first to look up the id."
    override val parametersSchema =
        objectSchema(required = listOf("taskId")) {
            stringField("taskId", "Task id from query_task_history.")
        }

    override suspend fun execute(args: JSONObject, ctx: ToolContext): ToolResult {
        val taskId = args.optString("taskId").ifBlank {
            return ToolResult.Continue(
                if (ctx.isEnglish) "get_task_history_detail requires `taskId`."
                else "get_task_history_detail 缺少 taskId 字段，请重新输出。",
            )
        }
        val historyManager = ctx.historyManager
            ?: return ToolResult.Continue(
                if (ctx.isEnglish) "Task history detail query failed: HistoryManager not available."
                else "历史任务详情查询失败：未启用历史记录",
            )

        val resultMessage = try {
            val task = historyManager.getTask(taskId)
                ?: historyManager.historyList.value.find { it.id == taskId }
            if (task == null) {
                if (ctx.isEnglish) "Task not found: id=$taskId" else "未找到历史任务：id=$taskId"
            } else {
                formatDetail(task, ctx.isEnglish)
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to get task history detail", e)
            if (ctx.isEnglish) "Task history detail query failed: ${e.message}" else "历史任务详情查询失败：${e.message}"
        }

        Logger.i(TAG, "get_task_history_detail: taskId=$taskId")
        val observation = if (ctx.isEnglish) "$resultMessage\n\nDecide your next action based on the above."
        else "$resultMessage\n\n请根据上述信息决定下一步操作。"
        return ToolResult.Continue(observation)
    }

    private fun formatDetail(task: TaskHistory, isEn: Boolean): String {
        val roundsArray = JSONArray()
        task.planningRounds.forEach { round ->
            roundsArray.put(
                JSONObject().apply {
                    put("round", round.round)
                    put("actionDescription", round.actionDescription)
                    put("message", round.message ?: "")
                },
            )
        }
        val jsonOut = JSONObject().apply {
            put("id", task.id)
            put("taskDescription", task.taskDescription)
            put("planningRounds", roundsArray)
        }
        val title = if (isEn) "[Task History Detail]" else "【历史任务详情】"
        return "$title\n```json\n${jsonOut.toString(2)}\n```"
    }

    companion object {
        const val NAME = "get_task_history_detail"
        private const val TAG = "GetTaskHistoryDetailTool"
    }
}
