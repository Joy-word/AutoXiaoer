package com.flowmate.autoxiaoer.mcp

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.flowmate.autoxiaoer.ComponentManager
import com.flowmate.autoxiaoer.R
import com.flowmate.autoxiaoer.util.applyPrimaryButtonColors
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.sse.SSE
import io.ktor.serialization.kotlinx.json.json
import io.modelcontextprotocol.kotlin.sdk.shared.McpJson
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.ClientOptions
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpClientTransport
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Dialog for adding a new or editing an existing MCP server.
 * Built-in servers show only Key/timeout fields; endpoint is read-only.
 */
class McpServerEditDialog(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val onSaved: () -> Unit,
    private val existingConfig: McpServerConfig? = null,
) {
    private val dialogView = LayoutInflater.from(context)
        .inflate(R.layout.dialog_mcp_server_edit, null)

    private val tilName: TextInputLayout = dialogView.findViewById(R.id.tilMcpName)
    private val etName: TextInputEditText = dialogView.findViewById(R.id.etMcpName)
    private val tilEndpoint: TextInputLayout = dialogView.findViewById(R.id.tilMcpEndpoint)
    private val etEndpoint: TextInputEditText = dialogView.findViewById(R.id.etMcpEndpoint)
    private val tilSecret: TextInputLayout = dialogView.findViewById(R.id.tilMcpSecret)
    private val etSecret: TextInputEditText = dialogView.findViewById(R.id.etMcpSecret)
    private val etConnectTimeout: TextInputEditText = dialogView.findViewById(R.id.etMcpConnectTimeout)
    private val etCallTimeout: TextInputEditText = dialogView.findViewById(R.id.etMcpCallTimeout)
    private val btnTest: com.google.android.material.button.MaterialButton =
        dialogView.findViewById(R.id.btnMcpTestConnection)
    private val tvTestResult: TextView = dialogView.findViewById(R.id.tvMcpTestResult)

    private val settingsManager = ComponentManager.getInstance(context).settingsManager
    private val mcpManager = ComponentManager.getInstance(context).mcpServiceManager
    private val isBuiltIn = existingConfig?.isBuiltIn == true

    init {
        prefillFields()
        setupTestButton()

        // Lock name and endpoint for built-in servers
        if (isBuiltIn) {
            tilName.visibility = View.GONE
            tilEndpoint.visibility = View.GONE
        }

        // Pre-fill secret field with stored value so it's visible (masked) and editable
        val hasSecret = existingConfig?.let { settingsManager.hasMcpSecret(it.id) } == true
        if (hasSecret) {
            val stored = settingsManager.getMcpSecret(existingConfig!!.id)
            if (!stored.isNullOrBlank()) {
                etSecret.setText(stored)
            }
        }
    }

    private fun prefillFields() {
        val c = existingConfig ?: return
        etName.setText(c.displayName)
        etEndpoint.setText(c.endpointUrl)
        etConnectTimeout.setText((c.connectTimeoutMs / 1000).toString())
        etCallTimeout.setText((c.callTimeoutMs / 1000).toString())
    }

    private fun setupTestButton() {
        btnTest.setOnClickListener {
            val tempConfig = buildConfig() ?: return@setOnClickListener
            val secret = etSecret.text?.toString()?.trim()
                ?: existingConfig?.let { settingsManager.getMcpSecret(it.id) }
            testConnection(tempConfig, secret)
        }
    }

    private fun testConnection(config: McpServerConfig, secret: String?) {
        dialogView.clearFocus()
        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
            ?.hideSoftInputFromWindow(dialogView.windowToken, 0)
        tvTestResult.visibility = View.VISIBLE
        tvTestResult.setTextColor(context.getColor(R.color.text_secondary))
        tvTestResult.text = context.getString(R.string.mcp_server_test_connecting)
        btnTest.isEnabled = false

        lifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val endpointUrl = if (config.authMode == McpAuthMode.QUERY_PARAMETER && !secret.isNullOrBlank()) {
                        val sep = if (config.endpointUrl.contains('?')) '&' else '?'
                        val encodedParam = java.net.URLEncoder.encode(config.authQueryParam, "UTF-8")
                        val encodedSecret = java.net.URLEncoder.encode(secret, "UTF-8")
                        "${config.endpointUrl}${sep}${encodedParam}=${encodedSecret}"
                    } else {
                        config.endpointUrl
                    }
                    val http = HttpClient(OkHttp) {
                        install(ContentNegotiation) { json(McpJson) }
                        install(SSE)
                    }
                    try {
                        val transport = StreamableHttpClientTransport(http, endpointUrl)
                        val client = Client(
                            clientInfo = Implementation(name = "autoxiaoer-probe", version = "1.0", title = null),
                            options = ClientOptions(),
                        )
                        client.connect(transport)
                        val page = client.listTools()
                        page?.tools?.size ?: 0
                    } finally {
                        http.close()
                    }
                }
            }
            btnTest.isEnabled = true
            if (result.isSuccess) {
                tvTestResult.setTextColor(context.getColor(android.R.color.holo_green_dark))
                tvTestResult.text = context.getString(R.string.mcp_server_test_success, result.getOrThrow())
            } else {
                tvTestResult.setTextColor(context.getColor(android.R.color.holo_red_dark))
                tvTestResult.text = context.getString(
                    R.string.mcp_server_test_failed,
                    result.exceptionOrNull()?.message?.take(120) ?: "unknown"
                )
            }
        }
    }

    fun show() {
        val titleRes = if (existingConfig == null) R.string.mcp_server_add_title
        else R.string.mcp_server_edit_title

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(titleRes)
            .setView(dialogView)
            .setPositiveButton(R.string.dialog_confirm, null)
            .setNegativeButton(R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            dialog.applyPrimaryButtonColors()
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (validateAndSave()) dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun validateAndSave(): Boolean {
        val config = buildConfig() ?: return false
        settingsManager.saveMcpServer(config)

        val newSecret = etSecret.text?.toString()?.trim()
        if (!newSecret.isNullOrBlank()) {
            settingsManager.saveMcpSecret(config.id, newSecret)
        }

        mcpManager.reload()
        onSaved()
        return true
    }

    private fun buildConfig(): McpServerConfig? {
        val isNew = existingConfig == null

        val name = if (isBuiltIn) existingConfig!!.displayName
        else {
            val v = etName.text?.toString()?.trim() ?: ""
            if (v.isBlank()) {
                tilName.error = context.getString(R.string.mcp_error_name_required)
                return null
            }
            tilName.error = null
            v
        }

        val endpoint = if (isBuiltIn) existingConfig!!.endpointUrl
        else {
            val v = etEndpoint.text?.toString()?.trim() ?: ""
            if (!v.startsWith("https://")) {
                tilEndpoint.error = context.getString(R.string.mcp_error_endpoint_https)
                return null
            }
            tilEndpoint.error = null
            v
        }

        val connectMs = (etConnectTimeout.text?.toString()?.toLongOrNull() ?: 10L) * 1000L
        val callMs = (etCallTimeout.text?.toString()?.toLongOrNull() ?: 30L) * 1000L

        return (existingConfig ?: McpServerConfig(
            id = UUID.randomUUID().toString(),
            displayName = name,
            endpointUrl = endpoint,
            authMode = McpAuthMode.QUERY_PARAMETER,
            authQueryParam = "key",
        )).copy(
            displayName = name,
            endpointUrl = endpoint,
            connectTimeoutMs = connectMs,
            callTimeoutMs = callMs,
        )
    }
}
