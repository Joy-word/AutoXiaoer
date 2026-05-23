package com.flowmate.autoxiaoer.clawbot

import com.flowmate.autoxiaoer.task.TaskExecutionManager
import com.flowmate.autoxiaoer.ui.TaskStatus

/**
 * Handles ClawBot shortcut commands (#0–#5) that bypass the LLM and execute fixed logic.
 *
 * These commands are intercepted in [ClawBotPollingService] before normal task dispatch,
 * and are also available while another task is running.
 */
object ClawBotShortcutHandler {
    private val COMMAND_PATTERN = Regex("^#([0-5])$")

    const val BUSY_REPLY = """正在忙。可使用快捷指令对当前任务进行操作。
#0：显示正在执行的任务名
#1：暂停当前任务
#2：停止当前任务
#3：清空任务队列
#4：停止当前任务 + 清空队列
#5：恢复任务"""

    /** Parses [text] as a shortcut command; returns 0–5 or null if not a shortcut. */
    fun parse(text: String): Int? =
        COMMAND_PATTERN.matchEntire(text)?.groupValues?.get(1)?.toInt()

    /** Executes shortcut [command] and returns the reply text to send back via ClawBot. */
    fun execute(command: Int): String =
        when (command) {
            0 -> showCurrentTask()
            1 -> pauseCurrentTask()
            2 -> stopCurrentTask()
            3 -> clearQueue()
            4 -> stopAndClearQueue()
            5 -> resumeCurrentTask()
            else -> "未知指令"
        }

    private fun showCurrentTask(): String {
        val state = TaskExecutionManager.taskState.value
        val queueSize = TaskExecutionManager.getPassiveQueueSize()
        val queueInfo = if (queueSize > 0) "，队列中还有 $queueSize 个待执行任务" else ""
        return when (state.status) {
            TaskStatus.RUNNING ->
                "当前任务：${state.taskDescription}（运行中）$queueInfo"
            TaskStatus.PAUSED ->
                "当前任务：${state.taskDescription}（已暂停）$queueInfo"
            else ->
                if (queueSize > 0) {
                    "当前没有正在执行的任务，队列中有 $queueSize 个待执行任务"
                } else {
                    "当前没有正在执行的任务"
                }
        }
    }

    private fun pauseCurrentTask(): String {
        if (!TaskExecutionManager.isTaskRunning()) {
            return "当前没有可暂停的任务"
        }
        if (TaskExecutionManager.taskState.value.status == TaskStatus.PAUSED) {
            return "当前任务已处于暂停状态"
        }
        return if (TaskExecutionManager.pauseTask()) {
            "已暂停当前任务"
        } else {
            "暂停失败，请稍后重试"
        }
    }

    private fun stopCurrentTask(): String {
        if (!TaskExecutionManager.isTaskRunning()) {
            return "当前没有正在执行的任务"
        }
        TaskExecutionManager.cancelTask()
        return "已停止当前任务"
    }

    private fun clearQueue(): String {
        val count = TaskExecutionManager.clearPassiveTaskQueue()
        return if (count > 0) {
            "已清空任务队列（清除了 $count 个待执行任务）"
        } else {
            "任务队列已为空"
        }
    }

    private fun stopAndClearQueue(): String {
        val wasRunning = TaskExecutionManager.isTaskRunning()
        val cleared = TaskExecutionManager.cancelTaskAndClearQueue()
        return buildString {
            if (wasRunning) append("已停止当前任务")
            if (cleared > 0) {
                if (isNotEmpty()) append("，")
                append("已清空任务队列（清除了 $cleared 个待执行任务）")
            }
            if (isEmpty()) append("当前没有正在执行的任务，任务队列已为空")
        }
    }

    private fun resumeCurrentTask(): String {
        when (TaskExecutionManager.taskState.value.status) {
            TaskStatus.PAUSED ->
                return if (TaskExecutionManager.resumeTask()) {
                    "已恢复当前任务"
                } else {
                    "恢复失败，请稍后重试"
                }
            TaskStatus.RUNNING -> return "当前任务正在运行中，无需恢复"
            else -> return "当前没有已暂停的任务"
        }
    }
}
