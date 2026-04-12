package com.flowmate.autoxiaoer.notification

import android.content.Context
import android.content.pm.ApplicationInfo
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.Filter
import android.widget.Toast
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.flowmate.autoxiaoer.R
import com.flowmate.autoxiaoer.util.Logger
import com.flowmate.autoxiaoer.util.applyPrimaryButtonColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Dialog for creating or editing a notification trigger rule.
 *
 * Loads installed apps in the background and presents them via a searchable
 * [MaterialAutoCompleteTextView]. The user also enters the task prompt to
 * execute when a notification from the selected app arrives.
 *
 * When [existingRule] is provided, the dialog runs in edit mode with pre-filled fields.
 */
class NotificationTriggerEditDialog(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val onRuleSaved: () -> Unit,
    private val existingRule: NotificationTriggerRule? = null,
) {
    companion object {
        private const val TAG = "NotificationTriggerEditDialog"
    }

    private val dialogView = LayoutInflater.from(context)
        .inflate(R.layout.dialog_notification_trigger_edit, null)

    private val tilAppSelector: TextInputLayout =
        dialogView.findViewById(R.id.tilAppSelector)
    private val actvAppSelector: MaterialAutoCompleteTextView =
        dialogView.findViewById(R.id.actvAppSelector)
    private val tilTaskPrompt: TextInputLayout =
        dialogView.findViewById(R.id.tilTaskPrompt)
    private val etTaskPrompt: TextInputEditText =
        dialogView.findViewById(R.id.etTaskPrompt)

    private val isEditMode = existingRule != null
    private var allApps: List<InstalledApp> = emptyList()
    private var selectedApp: InstalledApp? = null

    fun show() {
        val titleRes = if (isEditMode) {
            R.string.notification_trigger_edit_title
        } else {
            R.string.notification_trigger_add_title
        }

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(titleRes)
            .setView(dialogView)
            .setPositiveButton(R.string.dialog_confirm, null)
            .setNegativeButton(R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            dialog.applyPrimaryButtonColors()

            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (validateAndSave()) {
                    dialog.dismiss()
                }
            }
        }

        // Pre-fill task prompt in edit mode
        if (isEditMode) {
            etTaskPrompt.setText(existingRule!!.taskPrompt)
        }

        dialog.show()
        loadInstalledApps()
    }

    // ==================== App Loading ====================

    private fun loadInstalledApps() {
        lifecycleOwner.lifecycleScope.launch {
            val apps = withContext(Dispatchers.IO) {
                val pm = context.packageManager
                pm.getInstalledApplications(0)
                    .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 }
                    .map { appInfo ->
                        InstalledApp(
                            packageName = appInfo.packageName,
                            label = pm.getApplicationLabel(appInfo).toString(),
                        )
                    }
                    .sortedBy { it.label.lowercase() }
            }

            allApps = apps
            setupAppDropdown(apps)
            Logger.d(TAG, "Loaded ${apps.size} installed apps")
        }
    }

    private fun setupAppDropdown(apps: List<InstalledApp>) {
        val adapter = AppDropdownAdapter(context, apps)
        actvAppSelector.setAdapter(adapter)
        actvAppSelector.threshold = 0

        actvAppSelector.setOnItemClickListener { _, _, position, _ ->
            selectedApp = adapter.getItem(position)
            tilAppSelector.error = null
        }

        // Pre-select app in edit mode
        if (isEditMode) {
            val rule = existingRule!!
            val preselectedApp = apps.find { it.packageName == rule.packageName }
                ?: InstalledApp(packageName = rule.packageName, label = rule.appLabel)
            selectedApp = preselectedApp
            actvAppSelector.setText(preselectedApp.label, false)
            tilAppSelector.isEnabled = false
        }

        // Clear selection if text is manually changed
        actvAppSelector.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus && !isEditMode && actvAppSelector.text.isNotEmpty()) {
                selectedApp = null
            }
        }
    }

    // ==================== Validation & Save ====================

    private fun validateAndSave(): Boolean {
        val app = selectedApp
        val prompt = etTaskPrompt.text?.toString()?.trim() ?: ""

        var valid = true

        if (app == null) {
            tilAppSelector.error = context.getString(R.string.notification_trigger_error_select_app)
            valid = false
        } else {
            tilAppSelector.error = null
        }

        if (prompt.isEmpty()) {
            tilTaskPrompt.error = context.getString(R.string.notification_trigger_error_empty_prompt)
            valid = false
        } else {
            tilTaskPrompt.error = null
        }

        if (!valid) return false

        val manager = NotificationTriggerManager.getInstance(context)
        if (isEditMode) {
            manager.updateRule(
                ruleId = existingRule!!.id,
                taskPrompt = prompt,
            )
        } else {
            manager.addRule(
                appLabel = app!!.label,
                packageName = app.packageName,
                taskPrompt = prompt,
            )
        }

        Toast.makeText(context, R.string.notification_trigger_saved, Toast.LENGTH_SHORT).show()
        onRuleSaved()
        return true
    }

    // ==================== Dropdown Adapter ====================

    /**
     * ArrayAdapter that filters installed apps by label for the AutoCompleteTextView.
     */
    private inner class AppDropdownAdapter(
        context: Context,
        private val originalItems: List<InstalledApp>,
    ) : ArrayAdapter<InstalledApp>(context, android.R.layout.simple_dropdown_item_1line) {

        private var filteredItems: List<InstalledApp> = originalItems

        init {
            addAll(originalItems)
        }

        override fun getCount(): Int = filteredItems.size

        override fun getItem(position: Int): InstalledApp = filteredItems[position]

        override fun getView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
            val view = super.getView(position, convertView, parent)
            val app = getItem(position)
            val textView = view as? android.widget.TextView
            textView?.text = app.label
            return view
        }

        override fun getFilter(): Filter = object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val query = constraint?.toString()?.lowercase() ?: ""
                val results = if (query.isEmpty()) {
                    originalItems
                } else {
                    originalItems.filter { it.label.lowercase().contains(query) }
                }
                return FilterResults().apply {
                    values = results
                    count = results.size
                }
            }

            @Suppress("UNCHECKED_CAST")
            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                filteredItems = (results?.values as? List<InstalledApp>) ?: originalItems
                notifyDataSetChanged()
            }

            override fun convertResultToString(resultValue: Any?): CharSequence {
                val app = resultValue as? InstalledApp
                if (app != null) selectedApp = app
                return app?.label ?: ""
            }
        }
    }
}
