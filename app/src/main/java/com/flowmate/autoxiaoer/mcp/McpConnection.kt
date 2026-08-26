package com.flowmate.autoxiaoer.mcp

import io.modelcontextprotocol.kotlin.sdk.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.ClientOptions
import io.modelcontextprotocol.kotlin.sdk.client.SseClientTransport
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.sse.SSE
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import com.flowmate.autoxiaoer.util.Logger
import io.modelcontextprotocol.kotlin.sdk.Tool as McpTool

private const val TAG = "McpConnection"

/**
 * Manages the lifecycle of a single MCP server connection.
 * Handles initialize, tools/list (with pagination), and tools/call.
 */
class McpConnection(
    private val config: McpServerConfig,
    private val secretProvider: () -> String?,
) : McpToolInvoker {

    private var client: Client? = null
    private var httpClient: HttpClient? = null

    /** Discovered tools after a successful initialize + listTools cycle. */
    var discoveredTools: List<McpTool> = emptyList()
        private set

    /**
     * Connects to the MCP server, runs initialize, and fetches the tool list.
     * Throws on failure — caller should catch and set server state to Error.
     */
    suspend fun connect() {
        val endpointUrl = buildEndpointUrl()
        Logger.i(TAG, "Connecting to MCP server: ${config.id} (${config.endpointUrl.substringBefore("?")})")

        val http = HttpClient(OkHttp) {
            install(SSE)
            engine {
                config {
                    connectTimeout(config.connectTimeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
                    readTimeout(config.callTimeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
                }
            }
        }
        httpClient = http

        val transport = SseClientTransport(http, endpointUrl)
        val mcpClient = Client(
            clientInfo = Implementation(name = "autoxiaoer", version = "1.0"),
            options = ClientOptions(),
        )
        client = mcpClient
        mcpClient.connect(transport)

        // 0.4.0 listTools() has no cursor parameter; fetch all tools in one call
        val page = mcpClient.listTools()
        discoveredTools = page?.tools ?: emptyList()
        Logger.i(TAG, "Connected to ${config.id}, discovered ${discoveredTools.size} tools")
    }

    override suspend fun callTool(serverToolName: String, argumentsJson: String): String {
        val mcpClient = client ?: error("Not connected: ${config.id}")
        val parsedArgs = try {
            kotlinx.serialization.json.Json.decodeFromString(JsonObject.serializer(), argumentsJson)
        } catch (e: Exception) {
            buildJsonObject {}
        }

        val result = withTimeout(config.callTimeoutMs) {
            mcpClient.callTool(
                CallToolRequest(name = serverToolName, arguments = parsedArgs),
            )
        } ?: return "[$serverToolName] returned no result"

        return formatResult(result)
    }

    private fun formatResult(result: io.modelcontextprotocol.kotlin.sdk.CallToolResultBase): String {
        val items = result.content.take(config.maxContentItems)
        val sb = StringBuilder()
        for (item in items) {
            when (item) {
                is TextContent -> sb.append(item.text)
                else -> sb.append("[non-text content omitted]")
            }
            sb.append("\n")
        }
        val raw = sb.toString().trimEnd()
        return if (raw.length > config.maxContentChars) {
            raw.take(config.maxContentChars) + "\n[truncated]"
        } else {
            raw
        }
    }

    private fun buildEndpointUrl(): String {
        if (config.authMode != McpAuthMode.QUERY_PARAMETER) return config.endpointUrl
        val secret = secretProvider()
        if (secret.isNullOrBlank()) return config.endpointUrl
        val sep = if (config.endpointUrl.contains('?')) '&' else '?'
        val encodedParam = java.net.URLEncoder.encode(config.authQueryParam, "UTF-8")
        val encodedSecret = java.net.URLEncoder.encode(secret, "UTF-8")
        // Secret appended only in memory; never stored or logged as a full URL
        return "${config.endpointUrl}${sep}${encodedParam}=${encodedSecret}"
    }

    fun close() {
        try {
            httpClient?.close()
        } catch (e: Exception) {
            Logger.w(TAG, "Error closing http client for ${config.id}: ${e.message}")
        }
        client = null
        httpClient = null
        discoveredTools = emptyList()
        Logger.i(TAG, "Closed connection for ${config.id}")
    }
}
