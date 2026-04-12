package com.flowmate.autoxiaoer.notification

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial
import com.flowmate.autoxiaoer.R
import com.flowmate.autoxiaoer.util.showWithPrimaryButtons
import kotlinx.coroutines.launch

/**
 * Dialog for managing notification trigger rules.
 *
 * Displays all configured rules, shows a permission warning banner when
 * [NotificationListenerService] access has not been granted, and allows
 * adding new rules or editing / deleting / toggling existing ones.
 */
class NotificationTriggerListDialog(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val onAddRule: () -> Unit,
    private val onEditRule: ((NotificationTriggerRule) -> Unit)? = null,
) {
    private val dialogView = LayoutInflater.from(context)
        .inflate(R.layout.dialog_notification_trigger_list, null)

    private val rvTriggers: RecyclerView =
        dialogView.findViewById(R.id.rvNotificationTriggers)
    private val layoutEmpty: View =
        dialogView.findViewById(R.id.layoutEmpty)
    private val layoutPermissionWarning: View =
        dialogView.findViewById(R.id.layoutPermissionWarning)
    private val btnGrantPermission: MaterialButton =
        dialogView.findViewById(R.id.btnGrantPermission)
    private val btnAddRule: MaterialButton =
        dialogView.findViewById(R.id.btnAddRule)

    private val triggerManager = NotificationTriggerManager.getInstance(context)
    private val adapter = TriggerAdapter()

    init {
        setupViews()
        updatePermissionWarning()
        observeRules()
    }

    private fun setupViews() {
        rvTriggers.layoutManager = LinearLayoutManager(context)
        rvTriggers.adapter = adapter

        btnAddRule.setOnClickListener { onAddRule() }

        btnGrantPermission.setOnClickListener {
            openNotificationListenerSettings()
        }
    }

    private fun updatePermissionWarning() {
        val granted = isNotificationListenerEnabled(context)
        layoutPermissionWarning.visibility = if (granted) View.GONE else View.VISIBLE
    }

    private fun observeRules() {
        lifecycleOwner.lifecycleScope.launch {
            triggerManager.rules.collect { rules ->
                adapter.submitList(rules)
                updateEmptyState(rules.isEmpty())
            }
        }
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        rvTriggers.visibility = if (isEmpty) View.GONE else View.VISIBLE
        layoutEmpty.visibility = if (isEmpty) View.VISIBLE else View.GONE
    }

    fun show() {
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.notification_trigger_title)
            .setView(dialogView)
            .setPositiveButton(R.string.dialog_confirm, null)
            .showWithPrimaryButtons()
    }

    // ==================== Permission Helpers ====================

    private fun openNotificationListenerSettings() {
        openNotificationListenerSettings(context)
    }

    companion object {
        fun isNotificationListenerEnabled(context: Context): Boolean {
            val packageName = context.packageName
            val flat = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            ) ?: return false
            flat.split(":").forEach { name ->
                val cn = ComponentName.unflattenFromString(name) ?: return@forEach
                if (TextUtils.equals(packageName, cn.packageName)) return true
            }
            return false
        }

        fun openNotificationListenerSettings(context: Context) {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    // ==================== Adapter ====================

    private inner class TriggerAdapter :
        RecyclerView.Adapter<TriggerAdapter.ViewHolder>() {

        private var rules = emptyList<NotificationTriggerRule>()

        fun submitList(newRules: List<NotificationTriggerRule>) {
            rules = newRules
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_notification_trigger, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(rules[position])
        }

        override fun getItemCount(): Int = rules.size

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val ivAppIcon: ImageView = itemView.findViewById(R.id.ivAppIcon)
            private val tvAppLabel: TextView = itemView.findViewById(R.id.tvAppLabel)
            private val tvTaskPrompt: TextView = itemView.findViewById(R.id.tvTaskPrompt)
            private val switchEnabled: SwitchMaterial = itemView.findViewById(R.id.switchEnabled)
            private val btnEdit: ImageButton = itemView.findViewById(R.id.btnEdit)
            private val btnDelete: ImageButton = itemView.findViewById(R.id.btnDelete)

            fun bind(rule: NotificationTriggerRule) {
                tvAppLabel.text = rule.appLabel
                tvTaskPrompt.text = rule.taskPrompt

                // Load app icon
                try {
                    val pm = context.packageManager
                    val icon = pm.getApplicationIcon(rule.packageName)
                    ivAppIcon.setImageDrawable(icon)
                } catch (e: Exception) {
                    ivAppIcon.setImageResource(R.drawable.ic_notifications)
                }

                switchEnabled.setOnCheckedChangeListener(null)
                switchEnabled.isChecked = rule.isEnabled
                switchEnabled.setOnCheckedChangeListener { _, isChecked ->
                    triggerManager.updateRuleEnabled(rule.id, isChecked)
                    val msg = if (isChecked) {
                        context.getString(R.string.notification_trigger_enabled)
                    } else {
                        context.getString(R.string.notification_trigger_disabled)
                    }
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }

                btnEdit.setOnClickListener {
                    showEditRule(rule)
                }

                btnDelete.setOnClickListener {
                    showDeleteConfirmation(rule)
                }
            }

            private fun showEditRule(rule: NotificationTriggerRule) {
                if (onEditRule != null) {
                    onEditRule.invoke(rule)
                } else {
                    NotificationTriggerEditDialog(
                        context = context,
                        lifecycleOwner = lifecycleOwner,
                        existingRule = rule,
                        onRuleSaved = {},
                    ).show()
                }
            }

            private fun showDeleteConfirmation(rule: NotificationTriggerRule) {
                MaterialAlertDialogBuilder(context)
                    .setTitle(R.string.dialog_confirm_title)
                    .setMessage(
                        context.getString(
                            R.string.notification_trigger_delete_confirm,
                            rule.appLabel
                        )
                    )
                    .setPositiveButton(R.string.dialog_confirm) { _, _ ->
                        triggerManager.deleteRule(rule.id)
                        Toast.makeText(
                            context,
                            R.string.notification_trigger_deleted,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .showWithPrimaryButtons()
            }
        }
    }
}
