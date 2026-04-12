package com.flowmate.autoxiaoer.schedule

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.flowmate.autoxiaoer.R
import com.flowmate.autoxiaoer.util.showWithPrimaryButtons
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Dialog for creating or editing a scheduled task.
 *
 * Allows the user to:
 * - View or edit the task description
 * - Choose between specific time or delayed execution
 * - Select date and time (for specific time mode)
 * - Enter delay hours and minutes (for delay mode)
 * - Select repeat type (once, daily, weekdays, weekly)
 *
 * When [existingTask] is provided, the dialog runs in edit mode.
 */
class ScheduleTaskDialog(
    private val context: Context,
    private val taskDescription: String,
    private val onScheduled: (ScheduledTask) -> Unit,
    private val existingTask: ScheduledTask? = null,
) {
    private val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_schedule_task, null)
    
    private val tvTaskPreview: TextView = dialogView.findViewById(R.id.tvTaskPreview)
    private val tilTaskDescription: TextInputLayout = dialogView.findViewById(R.id.tilTaskDescription)
    private val etTaskDescription: TextInputEditText = dialogView.findViewById(R.id.etTaskDescription)
    private val rgTimeMode: RadioGroup = dialogView.findViewById(R.id.rgTimeMode)
    private val layoutSpecificTime: View = dialogView.findViewById(R.id.layoutSpecificTime)
    private val layoutDelayTime: View = dialogView.findViewById(R.id.layoutDelayTime)
    private val cardSelectDateTime: MaterialCardView = dialogView.findViewById(R.id.cardSelectDateTime)
    private val tvSelectedDateTime: TextView = dialogView.findViewById(R.id.tvSelectedDateTime)
    private val etDelayHours: TextInputEditText = dialogView.findViewById(R.id.etDelayHours)
    private val etDelayMinutes: TextInputEditText = dialogView.findViewById(R.id.etDelayMinutes)
    private val spinnerRepeatType: AutoCompleteTextView = dialogView.findViewById(R.id.spinnerRepeatType)

    private val isEditMode = existingTask != null

    private var selectedCalendar = Calendar.getInstance().apply {
        if (existingTask != null) {
            timeInMillis = existingTask.scheduledTimeMillis
        } else {
            add(Calendar.HOUR_OF_DAY, 1)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }

    init {
        setupViews()
    }

    private fun setupViews() {
        if (isEditMode) {
            // Edit mode: show editable text field, hide preview
            tvTaskPreview.visibility = View.GONE
            tilTaskDescription.visibility = View.VISIBLE
            etTaskDescription.setText(taskDescription)
        } else {
            // New task mode: show preview only
            tvTaskPreview.text = taskDescription
            tvTaskPreview.visibility = View.VISIBLE
            tilTaskDescription.visibility = View.GONE
        }

        // Setup time mode radio group (edit mode always uses specific time)
        if (isEditMode) {
            rgTimeMode.check(R.id.rbSpecificTime)
            layoutSpecificTime.visibility = View.VISIBLE
            layoutDelayTime.visibility = View.GONE
        }

        rgTimeMode.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.rbSpecificTime -> {
                    layoutSpecificTime.visibility = View.VISIBLE
                    layoutDelayTime.visibility = View.GONE
                }
                R.id.rbDelayTime -> {
                    layoutSpecificTime.visibility = View.GONE
                    layoutDelayTime.visibility = View.VISIBLE
                }
            }
        }

        // Setup date/time picker
        updateDateTimeDisplay()
        cardSelectDateTime.setOnClickListener {
            showDateTimePicker()
        }

        // Setup repeat type spinner
        val repeatTypes = listOf(
            context.getString(R.string.schedule_repeat_once),
            context.getString(R.string.schedule_repeat_daily),
            context.getString(R.string.schedule_repeat_weekdays),
            context.getString(R.string.schedule_repeat_weekly)
        )
        val adapter = ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, repeatTypes)
        spinnerRepeatType.setAdapter(adapter)

        // Pre-select repeat type from existing task
        val initialRepeatIndex = when (existingTask?.repeatType) {
            RepeatType.DAILY -> 1
            RepeatType.WEEKDAYS -> 2
            RepeatType.WEEKLY -> 3
            else -> 0
        }
        spinnerRepeatType.setText(repeatTypes[initialRepeatIndex], false)
    }

    fun show() {
        val titleRes = if (isEditMode) R.string.schedule_edit_task_title else R.string.schedule_task_title
        MaterialAlertDialogBuilder(context)
            .setTitle(titleRes)
            .setView(dialogView)
            .setPositiveButton(R.string.dialog_confirm) { _, _ ->
                saveScheduledTask()
            }
            .setNegativeButton(R.string.cancel, null)
            .showWithPrimaryButtons()
    }

    private fun showDateTimePicker() {
        // First show date picker
        val datePickerDialog = DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                selectedCalendar.set(Calendar.YEAR, year)
                selectedCalendar.set(Calendar.MONTH, month)
                selectedCalendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                
                // Then show time picker
                showTimePicker()
            },
            selectedCalendar.get(Calendar.YEAR),
            selectedCalendar.get(Calendar.MONTH),
            selectedCalendar.get(Calendar.DAY_OF_MONTH)
        )
        
        // Don't allow selecting past dates
        datePickerDialog.datePicker.minDate = System.currentTimeMillis()
        datePickerDialog.show()
    }

    private fun showTimePicker() {
        TimePickerDialog(
            context,
            { _, hourOfDay, minute ->
                selectedCalendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                selectedCalendar.set(Calendar.MINUTE, minute)
                selectedCalendar.set(Calendar.SECOND, 0)
                selectedCalendar.set(Calendar.MILLISECOND, 0)
                updateDateTimeDisplay()
            },
            selectedCalendar.get(Calendar.HOUR_OF_DAY),
            selectedCalendar.get(Calendar.MINUTE),
            true
        ).show()
    }

    private fun updateDateTimeDisplay() {
        val now = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("MM月dd日", Locale.getDefault())
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        
        val dateStr = when {
            isSameDay(selectedCalendar, now) -> context.getString(R.string.schedule_today)
            isTomorrow(selectedCalendar, now) -> context.getString(R.string.schedule_tomorrow)
            else -> dateFormat.format(selectedCalendar.time)
        }
        
        val timeStr = timeFormat.format(selectedCalendar.time)
        tvSelectedDateTime.text = "$dateStr $timeStr"
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

    private fun saveScheduledTask() {
        // Get task description
        val description = if (isEditMode) {
            etTaskDescription.text?.toString()?.trim() ?: ""
        } else {
            taskDescription
        }

        if (description.isEmpty()) {
            Toast.makeText(context, R.string.schedule_task_input_empty, Toast.LENGTH_SHORT).show()
            return
        }

        // Calculate execution time
        val executionTime = if (rgTimeMode.checkedRadioButtonId == R.id.rbSpecificTime) {
            // Specific time mode
            if (selectedCalendar.timeInMillis <= System.currentTimeMillis()) {
                Toast.makeText(context, R.string.schedule_time_past, Toast.LENGTH_SHORT).show()
                return
            }
            selectedCalendar.timeInMillis
        } else {
            // Delay mode
            val hours = etDelayHours.text.toString().toIntOrNull() ?: 0
            val minutes = etDelayMinutes.text.toString().toIntOrNull() ?: 0
            
            if (hours == 0 && minutes == 0) {
                Toast.makeText(context, R.string.schedule_time_past, Toast.LENGTH_SHORT).show()
                return
            }
            
            System.currentTimeMillis() + (hours * 3600000L) + (minutes * 60000L)
        }

        // Get repeat type
        val repeatType = when (spinnerRepeatType.text.toString()) {
            context.getString(R.string.schedule_repeat_daily) -> RepeatType.DAILY
            context.getString(R.string.schedule_repeat_weekdays) -> RepeatType.WEEKDAYS
            context.getString(R.string.schedule_repeat_weekly) -> RepeatType.WEEKLY
            else -> RepeatType.ONCE
        }

        // Create or update scheduled task
        val taskManager = ScheduledTaskManager.getInstance(context)
        val task = ScheduledTask(
            id = existingTask?.id ?: taskManager.generateTaskId(),
            taskDescription = description,
            taskBackground = existingTask?.taskBackground,
            scheduledTimeMillis = executionTime,
            repeatType = repeatType,
            isEnabled = existingTask?.isEnabled ?: true,
            createdAt = existingTask?.createdAt ?: System.currentTimeMillis(),
        )

        onScheduled(task)
    }
}
