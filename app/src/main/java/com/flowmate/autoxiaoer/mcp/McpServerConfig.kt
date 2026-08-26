package com.flowmate.autoxiaoer.mcp

import kotlinx.serialization.Serializable

/** How this server authenticates outbound requests. */
enum class McpAuthMode {
    NONE,
    QUERY_PARAMETER,
}

/**
 * Persisted configuration for a single MCP server.
 * Secrets (API keys) are stored separately in EncryptedSharedPreferences keyed by [id].
 */
@Serializable
data class McpServerConfig(
    /** Stable identifier; never changes after creation. */
    val id: String,
    val displayName: String,
    /** Base HTTPS endpoint without sensitive query parameters. */
    val endpointUrl: String,
    val enabled: Boolean = false,
    val authMode: McpAuthMode = McpAuthMode.NONE,
    /** Query parameter name used when authMode == QUERY_PARAMETER (e.g. "key" for AMap). */
    val authQueryParam: String = "",
    val connectTimeoutMs: Long = 10_000L,
    val callTimeoutMs: Long = 30_000L,
    /** Maximum number of content items returned per tool call. */
    val maxContentItems: Int = 5,
    /** Maximum total characters returned per tool call. */
    val maxContentChars: Int = 4_000,
    /** True for built-in servers whose endpoint/id cannot be modified. */
    val isBuiltIn: Boolean = false,
)

/** Version envelope wrapping the persisted list so future migrations are possible. */
@Serializable
data class McpServerConfigList(
    val version: Int = 1,
    val servers: List<McpServerConfig> = emptyList(),
)

object BuiltInMcpServers {
    const val AMAP_ID = "builtin_amap"

    fun amapTemplate(): McpServerConfig = McpServerConfig(
        id = AMAP_ID,
        displayName = "高德地图",
        endpointUrl = "https://mcp.amap.com/mcp",
        enabled = false,
        authMode = McpAuthMode.QUERY_PARAMETER,
        authQueryParam = "key",
        isBuiltIn = true,
    )
}
