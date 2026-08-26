package com.flowmate.autoxiaoer.mcp

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial
import com.flowmate.autoxiaoer.ComponentManager
import com.flowmate.autoxiaoer.R
import com.flowmate.autoxiaoer.util.showWithPrimaryButtons
import kotlinx.coroutines.launch

/** Shows the MCP server list and lets the user add, edit, enable/disable, or delete servers. */
class McpServerListDialog(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val onEditServer: (McpServerConfig) -> Unit,
    private val onAddServer: () -> Unit,
) {
    private val dialogView = LayoutInflater.from(context)
        .inflate(R.layout.dialog_mcp_server_list, null)

    private val rvServers: RecyclerView = dialogView.findViewById(R.id.rvMcpServers)
    private val tvEmpty: TextView = dialogView.findViewById(R.id.tvMcpEmpty)
    private val btnAdd: MaterialButton = dialogView.findViewById(R.id.btnAddMcpServer)

    private val settingsManager = ComponentManager.getInstance(context).settingsManager
    private val mcpManager = ComponentManager.getInstance(context).mcpServiceManager
    private val adapter = McpServerAdapter()

    init {
        rvServers.layoutManager = LinearLayoutManager(context)
        rvServers.adapter = adapter
        btnAdd.setOnClickListener { onAddServer() }
        observeStates()
        refreshList()
    }

    private fun observeStates() {
        lifecycleOwner.lifecycleScope.launch {
            mcpManager.serverStates.collect { refreshList() }
        }
    }

    private fun refreshList() {
        val servers = settingsManager.getMcpServers()
        adapter.submitList(servers)
        tvEmpty.visibility = if (servers.isEmpty()) View.VISIBLE else View.GONE
        rvServers.visibility = if (servers.isEmpty()) View.GONE else View.VISIBLE
    }

    fun show() {
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.mcp_server_list_title)
            .setView(dialogView)
            .setPositiveButton(R.string.dialog_confirm, null)
            .showWithPrimaryButtons()
    }

    // ==================== Adapter ====================

    private inner class McpServerAdapter :
        ListAdapter<McpServerConfig, McpServerViewHolder>(McpServerDiff) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): McpServerViewHolder {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_mcp_server, parent, false)
            return McpServerViewHolder(v)
        }

        override fun onBindViewHolder(holder: McpServerViewHolder, position: Int) {
            holder.bind(getItem(position))
        }
    }

    private inner class McpServerViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvName: TextView = view.findViewById(R.id.tvMcpServerName)
        private val tvBadge: TextView = view.findViewById(R.id.tvMcpBuiltInBadge)
        private val tvStatus: TextView = view.findViewById(R.id.tvMcpServerStatus)
        private val switchEnabled: SwitchMaterial = view.findViewById(R.id.switchMcpEnabled)

        fun bind(config: McpServerConfig) {
            tvName.text = config.displayName
            tvBadge.visibility = if (config.isBuiltIn) View.VISIBLE else View.GONE

            val state = mcpManager.serverStates.value[config.id]
            tvStatus.text = when (state) {
                is McpServerState.Connected ->
                    context.getString(R.string.mcp_status_connected, state.toolCount)
                is McpServerState.Connecting ->
                    context.getString(R.string.mcp_status_connecting)
                is McpServerState.MissingCredential ->
                    context.getString(R.string.mcp_status_missing_key)
                is McpServerState.Error ->
                    context.getString(R.string.mcp_status_error, state.message.take(60))
                is McpServerState.Disabled, null ->
                    context.getString(R.string.mcp_status_disabled)
            }

            // Block listener to avoid recursive update when setting checked state
            switchEnabled.setOnCheckedChangeListener(null)
            switchEnabled.isChecked = config.enabled
            switchEnabled.setOnCheckedChangeListener { _, checked ->
                settingsManager.setMcpServerEnabled(config.id, checked)
                mcpManager.reload()
                refreshList()
            }

            itemView.setOnClickListener { onEditServer(config) }

            itemView.setOnLongClickListener {
                if (!config.isBuiltIn) showDeleteConfirm(config) else false
            }
        }

        private fun showDeleteConfirm(config: McpServerConfig): Boolean {
            MaterialAlertDialogBuilder(context)
                .setTitle(R.string.mcp_server_delete_title)
                .setMessage(context.getString(R.string.mcp_server_delete_message, config.displayName))
                .setPositiveButton(R.string.delete) { _, _ ->
                    settingsManager.deleteMcpServer(config.id)
                    mcpManager.reload()
                    refreshList()
                }
                .setNegativeButton(R.string.cancel, null)
                .showWithPrimaryButtons()
            return true
        }
    }

    private object McpServerDiff : DiffUtil.ItemCallback<McpServerConfig>() {
        override fun areItemsTheSame(a: McpServerConfig, b: McpServerConfig) = a.id == b.id
        override fun areContentsTheSame(a: McpServerConfig, b: McpServerConfig) = a == b
    }
}
