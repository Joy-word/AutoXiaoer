package com.flowmate.autoxiaoer.agent.tools

import org.json.JSONObject

/**
 * Marks the current task as successfully completed and ends the ReAct loop.
 *
 * Mirrors the legacy `finish` action.
 */
class FinishTool : AgentTool {
    override val name: String = NAME
    override val description: String =
        "Mark the current task as completed. Provide a short summary describing what was accomplished."
    override val parametersSchema =
        objectSchema(required = listOf("message")) {
            stringField("message", "Summary of what the task accomplished, shown back to the caller.")
        }

    override suspend fun execute(args: JSONObject, ctx: ToolContext): ToolResult {
        val msg = args.optString("message").ifBlank {
            if (ctx.isEnglish) "Task completed" else "任务已完成"
        }
        val observation = if (ctx.isEnglish) "[Task Completed] $msg" else "【任务完成】$msg"
        return ToolResult.Terminate(success = true, message = msg, observation = observation)
    }

    companion object {
        const val NAME = "finish"
    }
}
