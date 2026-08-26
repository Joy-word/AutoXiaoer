package com.flowmate.autoxiaoer.mcp

import com.flowmate.autoxiaoer.agent.tools.AgentTool
import com.flowmate.autoxiaoer.agent.tools.ToolContext
import com.flowmate.autoxiaoer.agent.tools.ToolResult
import kotlinx.serialization.json.JsonObject
import org.json.JSONObject

/**
 * Adapts a remote MCP tool into [AgentTool] so LLMAgent stays MCP-agnostic.
 *
 * @param name             Namespaced tool name exposed to the model (mcp__serverId__toolName).
 * @param description      Tool description from tools/list.
 * @param parametersSchema JSON Schema from tools/list inputSchema.
 * @param serverToolName   Original tool name sent to the MCP server in tools/call.
 * @param invoker          Performs the actual remote call.
 */
class McpAgentTool(
    override val name: String,
    override val description: String,
    override val parametersSchema: JsonObject,
    private val serverToolName: String,
    private val invoker: McpToolInvoker,
) : AgentTool {

    override suspend fun execute(args: JSONObject, ctx: ToolContext): ToolResult {
        if (ctx.cancelRequested.get()) {
            return ToolResult.Continue(observation = "[$name] cancelled before execution")
        }
        return try {
            val observation = invoker.callTool(serverToolName, args.toString())
            ToolResult.Continue(observation = observation)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e // must propagate
        } catch (e: Exception) {
            ToolResult.Continue(observation = "[$name] error: ${e.message?.take(300) ?: "unknown error"}")
        }
    }
}
