package com.flowmate.autoxiaoer.agent.tools

import com.flowmate.autoxiaoer.schedule.RepeatType
import com.flowmate.autoxiaoer.schedule.ScheduledTask
import com.flowmate.autoxiaoer.schedule.ScheduledTaskManager
import com.flowmate.autoxiaoer.util.Logger
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val TIME_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

private fun parseScheduledTime(timeStr: String): Long? {
    if (timeStr.isBlank()) return null
    return runCatching { TIME_FORMAT.parse(timeStr)?.time }.getOrNull()
}

private val REPEAT_TYPE_ENUM = listOf("ONCE", "DAILY", "WEEKDAYS", "WEEKLY")

/**
 * Adds a new scheduled task to the agent's own agenda.
 *
 * Mirrors the legacy `schedule_task` action.
 */
class ScheduleTaskTool : AgentTool {
    override val name: String = NAME
    override val description: String =
        "Add a new scheduled task to your own agenda. Time must be in the future."
    override val parametersSchema =
        objectSchema(required = listOf("taskDescription", "scheduledTime", "repeatType")) {
            stringField("taskDescription", "Description of the task to execute at the scheduled time.")
            stringField(
                "taskBackground",
                "Memo to your future self: why you scheduled this and any relevant notes. Optional.",
            )
            stringField(
                "scheduledTime",
                "Target execution time in `yyyy-MM-dd HH:mm` format, e.g. \"2024-05-01 09:00\".",
            )
            stringField(
                "repeatType",
                "Repeat policy. ONCE = run once, DAILY = every day, WEEKDAYS = Mon–Fri, WEEKLY = same day each week.",
                enum = REPEAT_TYPE_ENUM,
            )
        }

    override suspend fun execute(args: JSONObject, ctx: ToolContext): ToolResult {
        val taskDescription = args.optString("taskDescription").ifBlank {
            return ToolResult.Continue(
                if (ctx.isEnglish) "schedule_task requires `taskDescription`." else "schedule_task 缺少 taskDescription 字段。",
            )
        }
        val taskBackground = args.optString("taskBackground").ifBlank { null }
        val scheduledMillis = parseScheduledTime(args.optString("scheduledTime"))
            ?: return ToolResult.Continue(
                if (ctx.isEnglish) "schedule_task `scheduledTime` is missing or not in `yyyy-MM-dd HH:mm` format."
                else "schedule_task 的 scheduledTime 缺失或格式错误，应为 yyyy-MM-dd HH:mm。",
            )
        val repeatType = runCatching {
            RepeatType.valueOf(args.optString("repeatType").uppercase())
        }.getOrDefault(RepeatType.ONCE)

        val appCtx = ctx.appContext ?: return ToolResult.Continue(
            if (ctx.isEnglish) "schedule_task failed: app context unavailable." else "日程记录失败：缺少系统上下文，无法访问任务管理器",
        )

        val resultMessage = try {
            val timeStr = TIME_FORMAT.format(Date(scheduledMillis))
            if (scheduledMillis <= System.currentTimeMillis()) {
                Logger.w(TAG, "Scheduled task time is in the past: $timeStr")
                if (ctx.isEnglish) {
                    "Failed to record schedule: the specified time \"$timeStr\" is in the past. " +
                        "Pick a future time. Current time: ${TIME_FORMAT.format(Date())}."
                } else {
                    "日程记录失败：指定时间「$timeStr」已是过去时刻，无法设置。" +
                        "请重新指定一个未来的时间（当前时间：${TIME_FORMAT.format(Date())}）。"
                }
            } else {
                val taskManager = ScheduledTaskManager.getInstance(appCtx)
                val newTask = ScheduledTask(
                    id = taskManager.generateTaskId(),
                    taskDescription = taskDescription,
                    taskBackground = taskBackground,
                    scheduledTimeMillis = scheduledMillis,
                    repeatType = repeatType,
                )
                taskManager.saveTask(newTask)
                Logger.i(TAG, "Scheduled task created: id=${newTask.id}, desc=${taskDescription.take(50)}")
                if (ctx.isEnglish) {
                    "Scheduled task created (id: ${newTask.id}): \"$taskDescription\", " +
                        "time: $timeStr, repeat: ${repeatType.name}."
                } else {
                    "日程已记录成功（id: ${newTask.id}）：「$taskDescription」，执行时间：$timeStr，重复类型：${repeatType.name}"
                }
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to create scheduled task", e)
            if (ctx.isEnglish) "Failed to record schedule: ${e.message}" else "日程记录失败：${e.message}"
        }

        val observation = if (ctx.isEnglish) {
            "[Schedule Task Result]\n$resultMessage\n\nDecide your next action based on the result."
        } else {
            "【定时任务操作结果】\n$resultMessage\n\n请根据结果决定下一步操作。"
        }
        return ToolResult.Continue(observation)
    }

    companion object {
        const val NAME = "schedule_task"
        private const val TAG = "ScheduleTaskTool"
    }
}

/**
 * Lists all scheduled tasks in the agent's agenda.
 *
 * Mirrors the legacy `query_scheduled_tasks` action.
 */
class QueryScheduledTasksTool : AgentTool {
    override val name: String = NAME
    override val description: String =
        "List all scheduled tasks in your agenda (id, description, time, repeat type, status)."
    override val parametersSchema = EmptyObjectSchema

    override suspend fun execute(args: JSONObject, ctx: ToolContext): ToolResult {
        val appCtx = ctx.appContext ?: return ToolResult.Continue(
            if (ctx.isEnglish) "query_scheduled_tasks failed: app context unavailable."
            else "日程查询失败：缺少系统上下文",
        )

        val message = try {
            val tasks = ScheduledTaskManager.getInstance(appCtx).getAllTasks()
            if (tasks.isEmpty()) {
                if (ctx.isEnglish) "[Scheduled Tasks] No tasks scheduled." else "【当前日程列表】\n暂无任何日程安排。"
            } else if (ctx.isEnglish) {
                buildString {
                    appendLine("[Scheduled Tasks]")
                    tasks.forEachIndexed { index, task ->
                        appendLine("${index + 1}. id: ${task.id}")
                        appendLine("   description: ${task.taskDescription}")
                        if (!task.taskBackground.isNullOrBlank()) appendLine("   memo: ${task.taskBackground}")
                        appendLine("   time: ${TIME_FORMAT.format(Date(task.scheduledTimeMillis))}")
                        appendLine("   repeat: ${task.repeatType.name}  status: ${if (task.isEnabled) "enabled" else "disabled"}")
                    }
                    append("Total: ${tasks.size} tasks.")
                }
            } else {
                buildString {
                    appendLine("【当前日程列表】")
                    tasks.forEachIndexed { index, task ->
                        appendLine("${index + 1}. id: ${task.id}")
                        appendLine("   描述：${task.taskDescription}")
                        if (!task.taskBackground.isNullOrBlank()) appendLine("   备注：${task.taskBackground}")
                        appendLine("   执行时间：${TIME_FORMAT.format(Date(task.scheduledTimeMillis))}")
                        appendLine("   重复：${task.repeatType.name}  状态：${if (task.isEnabled) "已启用" else "已禁用"}")
                    }
                    append("共 ${tasks.size} 个日程。")
                }
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to query scheduled tasks", e)
            if (ctx.isEnglish) "Failed to query scheduled tasks: ${e.message}" else "日程查询失败：${e.message}"
        }

        val observation = if (ctx.isEnglish) "$message\n\nDecide your next action based on the above."
        else "$message\n\n请根据上述信息决定下一步操作。"
        return ToolResult.Continue(observation)
    }

    companion object {
        const val NAME = "query_scheduled_tasks"
        private const val TAG = "QueryScheduledTasksTool"
    }
}

/**
 * Updates an existing scheduled task. Only the fields provided are changed.
 *
 * Mirrors the legacy `update_scheduled_task` action.
 */
class UpdateScheduledTaskTool : AgentTool {
    override val name: String = NAME
    override val description: String =
        "Update fields of an existing scheduled task. Only the fields you provide are changed."
    override val parametersSchema =
        objectSchema(required = listOf("taskId")) {
            stringField("taskId", "The id of the scheduled task to update.")
            stringField("taskDescription", "New description (optional).")
            stringField("taskBackground", "New memo (optional).")
            stringField("scheduledTime", "New execution time in `yyyy-MM-dd HH:mm` (optional).")
            stringField("repeatType", "New repeat policy (optional).", enum = REPEAT_TYPE_ENUM)
            booleanField("isEnabled", "Enable / disable this scheduled task (optional).")
        }

    override suspend fun execute(args: JSONObject, ctx: ToolContext): ToolResult {
        val taskId = args.optString("taskId").ifBlank {
            return ToolResult.Continue(
                if (ctx.isEnglish) "update_scheduled_task requires `taskId`." else "update_scheduled_task 缺少 taskId 字段。",
            )
        }
        val newDescription = args.optString("taskDescription").ifBlank { null }
        val newBackground = args.optString("taskBackground").ifBlank { null }
        val newTime = parseScheduledTime(args.optString("scheduledTime"))
        val newRepeat = args.optString("repeatType").ifBlank { null }?.let {
            runCatching { RepeatType.valueOf(it.uppercase()) }.getOrNull()
        }
        val newIsEnabled = if (args.has("isEnabled")) args.optBoolean("isEnabled") else null

        val appCtx = ctx.appContext ?: return ToolResult.Continue(
            if (ctx.isEnglish) "update_scheduled_task failed: app context unavailable."
            else "日程更新失败：缺少系统上下文",
        )

        val resultMessage = try {
            val taskManager = ScheduledTaskManager.getInstance(appCtx)
            val existing = taskManager.getTaskById(taskId)
            if (existing == null) {
                if (ctx.isEnglish) "Update failed: no scheduled task with id \"$taskId\"." else "更新失败：找不到 id 为「$taskId」的日程"
            } else {
                val updated = existing.copy(
                    taskDescription = newDescription ?: existing.taskDescription,
                    taskBackground = newBackground ?: existing.taskBackground,
                    scheduledTimeMillis = newTime ?: existing.scheduledTimeMillis,
                    repeatType = newRepeat ?: existing.repeatType,
                    isEnabled = newIsEnabled ?: existing.isEnabled,
                )
                taskManager.saveTask(updated)
                Logger.i(TAG, "Scheduled task updated: id=$taskId")
                if (ctx.isEnglish) "Scheduled task updated (id: $taskId): \"${updated.taskDescription}\""
                else "日程已更新成功（id: $taskId）：「${updated.taskDescription}」"
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to update scheduled task", e)
            if (ctx.isEnglish) "Failed to update scheduled task: ${e.message}" else "日程更新失败：${e.message}"
        }

        val observation = if (ctx.isEnglish) {
            "[Schedule Update]\n$resultMessage\n\nDecide your next action based on the result."
        } else {
            "【日程更新结果】\n$resultMessage\n\n请根据结果决定下一步操作。"
        }
        return ToolResult.Continue(observation)
    }

    companion object {
        const val NAME = "update_scheduled_task"
        private const val TAG = "UpdateScheduledTaskTool"
    }
}

/**
 * Deletes an existing scheduled task by id.
 *
 * Mirrors the legacy `delete_scheduled_task` action.
 */
class DeleteScheduledTaskTool : AgentTool {
    override val name: String = NAME
    override val description: String = "Delete an existing scheduled task by id."
    override val parametersSchema =
        objectSchema(required = listOf("taskId")) {
            stringField("taskId", "The id of the scheduled task to delete.")
        }

    override suspend fun execute(args: JSONObject, ctx: ToolContext): ToolResult {
        val taskId = args.optString("taskId").ifBlank {
            return ToolResult.Continue(
                if (ctx.isEnglish) "delete_scheduled_task requires `taskId`." else "delete_scheduled_task 缺少 taskId 字段。",
            )
        }
        val appCtx = ctx.appContext ?: return ToolResult.Continue(
            if (ctx.isEnglish) "delete_scheduled_task failed: app context unavailable."
            else "日程删除失败：缺少系统上下文",
        )

        val resultMessage = try {
            val taskManager = ScheduledTaskManager.getInstance(appCtx)
            val existing = taskManager.getTaskById(taskId)
            if (existing == null) {
                if (ctx.isEnglish) "Delete failed: no scheduled task with id \"$taskId\"." else "删除失败：找不到 id 为「$taskId」的日程"
            } else {
                taskManager.deleteTask(taskId)
                Logger.i(TAG, "Scheduled task deleted: id=$taskId")
                if (ctx.isEnglish) "Scheduled task deleted (id: $taskId): \"${existing.taskDescription}\""
                else "日程已删除（id: $taskId）：「${existing.taskDescription}」"
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to delete scheduled task", e)
            if (ctx.isEnglish) "Failed to delete scheduled task: ${e.message}" else "日程删除失败：${e.message}"
        }

        val observation = if (ctx.isEnglish) {
            "[Schedule Delete]\n$resultMessage\n\nDecide your next action based on the result."
        } else {
            "【日程删除结果】\n$resultMessage\n\n请根据结果决定下一步操作。"
        }
        return ToolResult.Continue(observation)
    }

    companion object {
        const val NAME = "delete_scheduled_task"
        private const val TAG = "DeleteScheduledTaskTool"
    }
}
