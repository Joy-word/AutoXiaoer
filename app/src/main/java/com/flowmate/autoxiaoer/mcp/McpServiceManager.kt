package com.flowmate.autoxiaoer.mcp

import com.flowmate.autoxiaoer.agent.tools.AgentTool
import com.flowmate.autoxiaoer.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

private const val TAG = "McpServiceManager"

/** Per-server connection state visible to UI and LLMAgent. */
sealed class McpServerState {
    object Disabled : McpServerState()
    object MissingCredential : McpServerState()
    object Connecting : McpServerState()
    data class Connected(val toolCount: Int) : McpServerState()
    data class Error(val message: String) : McpServerState()
}

/**
 * Application-level manager owning all MCP server connections.
 * Independent of Shizuku/Accessibility lifecycle.
 */
class McpServiceManager(
    private val configProvider: () -> List<McpServerConfig>,
    private val secretProvider: (serverId: String) -> String?,
) {
    // Single-threaded dispatcher serializes all access to [connections], preventing
    // races when reload() is triggered concurrently (e.g. rapid switch toggling).
    @OptIn(ExperimentalCoroutinesApi::class)
    private val serialDispatcher = Dispatchers.IO.limitedParallelism(1)
    private val scope = CoroutineScope(SupervisorJob() + serialDispatcher)

    private val _serverStates = MutableStateFlow<Map<String, McpServerState>>(emptyMap())
    val serverStates: StateFlow<Map<String, McpServerState>> = _serverStates.asStateFlow()

    // serverId -> active McpConnection
    private val connections = mutableMapOf<String, McpConnection>()

    /** MCP tools available right now; updated after each successful connect/disconnect. */
    private val _mcpTools = MutableStateFlow<List<AgentTool>>(emptyList())
    val mcpTools: StateFlow<List<AgentTool>> = _mcpTools.asStateFlow()

    /** Loads config and connects all enabled servers. Call once at app startup. */
    fun start() {
        scope.launch {
            reload()
        }
    }

    /**
     * Re-reads config and reconnects changed servers.
     * Servers removed from config are closed; newly enabled ones are connected.
     */
    fun reload() {
        scope.launch {
            val configs = configProvider()
            val currentIds = connections.keys.toSet()
            val newIds = configs.map { it.id }.toSet()

            // Close servers no longer in config
            (currentIds - newIds).forEach { id ->
                connections.remove(id)?.close()
                updateState(id, null)
            }

            // Connect or refresh each server
            for (config in configs) {
                connectServer(config)
            }
            rebuildToolList()
        }
    }

    private suspend fun connectServer(config: McpServerConfig) {
        if (!config.enabled) {
            connections.remove(config.id)?.close()
            updateState(config.id, McpServerState.Disabled)
            return
        }
        if (config.authMode == McpAuthMode.QUERY_PARAMETER) {
            val secret = secretProvider(config.id)
            if (secret.isNullOrBlank()) {
                connections.remove(config.id)?.close()
                updateState(config.id, McpServerState.MissingCredential)
                return
            }
        }

        // Close stale connection before reconnecting
        connections.remove(config.id)?.close()
        updateState(config.id, McpServerState.Connecting)

        val conn = McpConnection(config) { secretProvider(config.id) }
        try {
            conn.connect()
            connections[config.id] = conn
            updateState(config.id, McpServerState.Connected(conn.discoveredTools.size))
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to connect ${config.id}: ${e.message}")
            updateState(config.id, McpServerState.Error(e.message?.take(200) ?: "unknown"))
        }
    }

    /** Builds a snapshot of all currently connected MCP tools. Call after any state change. */
    private fun rebuildToolList() {
        val tools = mutableListOf<AgentTool>()
        for ((serverId, conn) in connections) {
            val state = _serverStates.value[serverId]
            if (state !is McpServerState.Connected) continue
            for (mcpTool in conn.discoveredTools) {
                val namespacedName = buildNamespacedName(serverId, mcpTool.name)
                val schema = mcpTool.inputSchema?.let { schemaToJsonObject(it) } ?: emptyParamsSchema()
                tools += McpAgentTool(
                    name = namespacedName,
                    description = mcpTool.description ?: "",
                    parametersSchema = schema,
                    serverToolName = mcpTool.name,
                    invoker = conn,
                )
            }
        }
        _mcpTools.value = tools
    }

    /** Returns a stable snapshot of MCP tools for the current task. */
    fun currentToolSnapshot(): List<AgentTool> = _mcpTools.value

    fun close() {
        scope.launch {
            connections.values.forEach { it.close() }
            connections.clear()
            _mcpTools.value = emptyList()
        }
    }

    private fun updateState(id: String, state: McpServerState?) {
        _serverStates.value = if (state == null) {
            _serverStates.value - id
        } else {
            _serverStates.value + (id to state)
        }
    }

    companion object {
        /** mcp__serverId__toolName, normalized to [a-zA-Z0-9_-], max 64 chars. */
        fun buildNamespacedName(serverId: String, toolName: String): String {
            val safeId = serverId.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(20)
            val safeTool = toolName.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(40)
            return "mcp__${safeId}__${safeTool}"
        }

        private fun schemaToJsonObject(schema: io.modelcontextprotocol.kotlin.sdk.Tool.Input): JsonObject {
            // The SDK exposes properties and required as maps/lists; rebuild as JsonObject
            return buildJsonObject {
                put("type", JsonPrimitive("object"))
                if (schema.properties.isNotEmpty()) {
                    put("properties", buildJsonObject {
                        for ((k, v) in schema.properties) {
                            put(k, v)
                        }
                    })
                }
                val required = schema.required
                if (!required.isNullOrEmpty()) {
                    put("required", kotlinx.serialization.json.buildJsonArray {
                        required.forEach { add(JsonPrimitive(it)) }
                    })
                }
            }
        }

        private fun emptyParamsSchema(): JsonObject = buildJsonObject {
            put("type", JsonPrimitive("object"))
            put("properties", buildJsonObject {})
        }

        @Volatile
        private var instance: McpServiceManager? = null

        fun getInstance(
            configProvider: () -> List<McpServerConfig>,
            secretProvider: (String) -> String?,
        ): McpServiceManager = instance ?: synchronized(this) {
            instance ?: McpServiceManager(configProvider, secretProvider).also { instance = it }
        }
    }
}
