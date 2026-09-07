package com.flowmate.autoxiaoer.mcp

/**
 * Narrow interface for invoking a single MCP tool call.
 * McpAgentTool holds a reference to this; MCP SDK details stay in McpConnection.
 */
interface McpToolInvoker {
    /**
     * Calls the tool on the remote server.
     * @param serverToolName Original tool name as returned by tools/list (not the namespaced name).
     * @param argumentsJson  JSON-serialized arguments object string.
     * @return formatted text observation for the LLM, already truncated to config limits.
     */
    suspend fun callTool(serverToolName: String, argumentsJson: String): String
}
