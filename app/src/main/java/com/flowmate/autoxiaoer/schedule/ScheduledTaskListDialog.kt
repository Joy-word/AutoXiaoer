package com.flowmate.autoxiaoer.schedule

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Dialog for managing scheduled tasks.
 *
 * Displays a list of all scheduled tasks with options to:
 * - Enable/disable each task
 * - Edit tasks
 * - Delete tasks
 * - Create new tasks
 */
class ScheduledTaskListDialog(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val onNewTask: () -> Unit,
    private val onEditTask: ((ScheduledTask) -> Unit)? = null,
) {
    private val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_scheduled_task_list, null)
    private val rvScheduledTasks: RecyclerView = dialogView.findViewById(R.id.rvScheduledTasks)
    private val layoutEmpty: View = dialogView.findViewById(R.id.layoutEmpty)
    private val btnNewTask: MaterialButton = dialogView.findViewById(R.id.btnNewTask)

    private val taskManager = ScheduledTaskManager.getInstance(context)
    private val adapter = ScheduledTaskAdapter()

    init {
        setupViews()
        observeTasks()
    }

    private fun setupViews() {
        // Setup RecyclerView
        rvScheduledTasks.layoutManager = LinearLayoutManager(context)
        rvScheduledTasks.adapter = adapter

        // Setup new task button
        btnNewTask.setOnClickListener {
            onNewTask()
        }
    }

    private fun observeTasks() {
        lifecycleOwner.lifecycleScope.launch {
            taskManager.tasks.collect { tasks ->
                adapter.submitList(tasks)
                updateEmptyState(tasks.isEmpty())
            }
        }
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        if (isEmpty) {
            rvScheduledTasks.visibility = View.GONE
            layoutEmpty.visibility = View.VISIBLE
        } else {
            rvScheduledTasks.visibility = View.VISIBLE
            layoutEmpty.visibility = View.GONE
        }
    }

    fun show() {
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.scheduled_tasks)
            .setView(dialogView)
            .setPositiveButton(R.string.dialog_confirm, null)
            .showWithPrimaryButtons()
    }

    /**
     * Adapter for displaying scheduled tasks in a RecyclerView.
     */
    private inner class ScheduledTaskAdapter :
        RecyclerView.Adapter<ScheduledTaskAdapter.ViewHolder>() {

        private var tasks = emptyList<ScheduledTask>()

        fun submitList(newTasks: List<ScheduledTask>) {
            tasks = newTasks
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_scheduled_task, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(tasks[position])
        }

        override fun getItemCount(): Int = tasks.size

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val tvTaskDescription: TextView = itemView.findViewById(R.id.tvTaskDescription)
            private val tvScheduledTime: TextView = itemView.findViewById(R.id.tvScheduledTime)
            private val tvRepeatType: TextView = itemView.findViewById(R.id.tvRepeatType)
            private val switchEnabled: SwitchMaterial = itemView.findViewById(R.id.switchEnabled)
            private val btnEdit: ImageButton = itemView.findViewById(R.id.btnEdit)
            private val btnDelete: ImageButton = itemView.findViewById(R.id.btnDelete)

            fun bind(task: ScheduledTask) {
                // Set task description
                tvTaskDescription.text = task.taskDescription

                // Set scheduled time
                tvScheduledTime.text = formatScheduledTime(task.scheduledTimeMillis)

                // Set repeat type
                tvRepeatType.text = when (task.repeatType) {
                    RepeatType.ONCE -> context.getString(R.string.schedule_repeat_once)
                    RepeatType.DAILY -> context.getString(R.string.schedule_repeat_daily)
                    RepeatType.WEEKDAYS -> context.getString(R.string.schedule_repeat_weekdays)
                    RepeatType.WEEKLY -> context.getString(R.string.schedule_repeat_weekly)
                }

                // Set enabled switch without triggering listener during bind
                switchEnabled.setOnCheckedChangeListener(null)
                switchEnabled.isChecked = task.isEnabled
                switchEnabled.setOnCheckedChangeListener { _, isChecked ->
                    taskManager.updateTaskEnabled(task.id, isChecked)
                    val messageResId = if (isChecked) {
                        R.string.schedule_enabled
                    } else {
                        R.string.schedule_disabled
                    }
                    Toast.makeText(context, messageResId, Toast.LENGTH_SHORT).show()
                }

                // Set edit button
                btnEdit.setOnClickListener {
                    showEditTask(task)
                }

                // Set delete button
                btnDelete.setOnClickListener {
                    showDeleteConfirmation(task)
                }
            }

            private fun formatScheduledTime(timeMillis: Long): String {
                val calendar = Calendar.getInstance()
                calendar.timeInMillis = timeMillis
                
                val now = Calendar.getInstance()
                val dateFormat = SimpleDateFormat("MM月dd日", Locale.getDefault())
                val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                
                val dateStr = when {
                    isSameDay(calendar, now) -> context.getString(R.string.schedule_today)
                    isTomorrow(calendar, now) -> context.getString(R.string.schedule_tomorrow)
                    else -> dateFormat.format(calendar.time)
                }
                
                val timeStr = timeFormat.format(calendar.time)
                return "$dateStr $timeStr"
            }

            private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
                return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                        cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
            }

            private fun isTomorrow(cal1: Calendar, cal2: Calendar): Boolean {
                val tomorrow = cal2.clone() as Calendar
                tomorrow.add(Calendar.DAY_OF_MONTH, 1)
                return isSameDay(cal1, tomorrow)
            }

            private fun showEditTask(task: ScheduledTask) {
                if (onEditTask != null) {
                    onEditTask.invoke(task)
                } else {
                    ScheduleTaskDialog(
                        context = context,
                        taskDescription = task.taskDescription,
                        existingTask = task,
                        onScheduled = { updatedTask ->
                            taskManager.saveTask(updatedTask)
                            Toast.makeText(context, R.string.schedule_updated, Toast.LENGTH_SHORT).show()
                        }
                    ).show()
                }
            }

            private fun showDeleteConfirmation(task: ScheduledTask) {
                MaterialAlertDialogBuilder(context)
                    .setTitle(R.string.dialog_confirm_title)
                    .setMessage(R.string.schedule_delete_confirm)
                    .setPositiveButton(R.string.dialog_confirm) { _, _ ->
                        taskManager.deleteTask(task.id)
                        Toast.makeText(context, R.string.schedule_deleted, Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .showWithPrimaryButtons()
            }
        }
    }
}
