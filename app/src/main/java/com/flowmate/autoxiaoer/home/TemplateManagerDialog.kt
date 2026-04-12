package com.flowmate.autoxiaoer.home

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.flowmate.autoxiaoer.R
import com.flowmate.autoxiaoer.settings.SettingsManager
import com.flowmate.autoxiaoer.settings.TaskTemplate
import com.flowmate.autoxiaoer.util.showWithPrimaryButtons

/**
 * Dialog for managing task templates.
 *
 * Supports template selection (apply to task input), and template management
 * (add / edit / delete).
 */
class TemplateManagerDialog(
    private val context: Context,
    private val settingsManager: SettingsManager,
    private val onTemplateSelected: (TaskTemplate) -> Unit,
) {
    private val dialogView = LayoutInflater.from(context)
        .inflate(R.layout.dialog_template_manager, null)

    private val rvTemplates: RecyclerView = dialogView.findViewById(R.id.rvTemplates)
    private val layoutTemplateEmpty: View = dialogView.findViewById(R.id.layoutTemplateEmpty)
    private val btnAddTemplate: MaterialButton = dialogView.findViewById(R.id.btnAddTemplate)

    private val adapter = TemplateAdapter()

    init {
        setupViews()
        loadTemplates()
    }

    private fun setupViews() {
        rvTemplates.layoutManager = LinearLayoutManager(context)
        rvTemplates.adapter = adapter
        btnAddTemplate.setOnClickListener { showTemplateDialog(null) }
    }

    private fun loadTemplates() {
        val templates = settingsManager.getTaskTemplates()
        adapter.submitList(templates)
        updateEmptyState(templates.isEmpty())
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        if (isEmpty) {
            rvTemplates.visibility = View.GONE
            layoutTemplateEmpty.visibility = View.VISIBLE
        } else {
            rvTemplates.visibility = View.VISIBLE
            layoutTemplateEmpty.visibility = View.GONE
        }
    }

    private fun showTemplateDialog(template: TaskTemplate?) {
        val isEdit = template != null
        val editView = LayoutInflater.from(context).inflate(R.layout.dialog_task_template, null)
        val nameInput = editView.findViewById<TextInputEditText>(R.id.templateNameInput)
        val descInput = editView.findViewById<TextInputEditText>(R.id.templateDescriptionInput)

        if (isEdit) {
            nameInput.setText(template!!.name)
            descInput.setText(template.description)
        }

        MaterialAlertDialogBuilder(context)
            .setTitle(if (isEdit) R.string.settings_edit_template else R.string.settings_add_template)
            .setView(editView)
            .setPositiveButton(R.string.dialog_confirm) { _, _ ->
                val name = nameInput.text?.toString()?.trim() ?: ""
                val description = descInput.text?.toString()?.trim() ?: ""
                if (name.isEmpty()) {
                    Toast.makeText(context, R.string.settings_template_name_empty, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (description.isEmpty()) {
                    Toast.makeText(context, R.string.settings_template_desc_empty, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val newTemplate = TaskTemplate(
                    id = template?.id ?: settingsManager.generateTemplateId(),
                    name = name,
                    description = description,
                )
                settingsManager.saveTaskTemplate(newTemplate)
                loadTemplates()
                Toast.makeText(context, R.string.settings_template_saved, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .showWithPrimaryButtons()
    }

    private fun showDeleteTemplateDialog(template: TaskTemplate) {
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.settings_delete_template)
            .setMessage(R.string.settings_delete_template_confirm)
            .setPositiveButton(R.string.dialog_confirm) { _, _ ->
                settingsManager.deleteTaskTemplate(template.id)
                loadTemplates()
                Toast.makeText(context, R.string.settings_template_deleted, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .showWithPrimaryButtons()
    }

    fun show() {
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.task_select_template)
            .setView(dialogView)
            .setNegativeButton(R.string.cancel, null)
            .showWithPrimaryButtons()
    }

    private inner class TemplateAdapter : RecyclerView.Adapter<TemplateAdapter.ViewHolder>() {

        private var templates = emptyList<TaskTemplate>()

        fun submitList(newTemplates: List<TaskTemplate>) {
            templates = newTemplates
            notifyDataSetChanged()
        }

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val nameText: TextView = view.findViewById(R.id.templateName)
            val descText: TextView = view.findViewById(R.id.templateDescription)
            val editBtn: ImageButton = view.findViewById(R.id.btnEdit)
            val deleteBtn: ImageButton = view.findViewById(R.id.btnDelete)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(context).inflate(R.layout.item_task_template, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val template = templates[position]
            holder.nameText.text = template.name
            holder.descText.text = template.description
            holder.itemView.setOnClickListener { onTemplateSelected(template) }
            holder.editBtn.setOnClickListener { showTemplateDialog(template) }
            holder.deleteBtn.setOnClickListener { showDeleteTemplateDialog(template) }
        }

        override fun getItemCount() = templates.size
    }
}
