package com.flowmate.autoxiaoer.home

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import android.widget.ImageButton
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputEditText
import com.flowmate.autoxiaoer.R
import com.flowmate.autoxiaoer.notification.NotificationTriggerEditDialog
import com.flowmate.autoxiaoer.notification.NotificationTriggerListDialog
import com.flowmate.autoxiaoer.schedule.ScheduleTaskDialog
import com.flowmate.autoxiaoer.schedule.ScheduledTaskListDialog
import com.flowmate.autoxiaoer.schedule.ScheduledTaskManager
import com.flowmate.autoxiaoer.settings.PostTaskAction
import com.flowmate.autoxiaoer.settings.SettingsManager
import com.flowmate.autoxiaoer.settings.TaskTemplate
import com.flowmate.autoxiaoer.ui.FloatingWindowStateManager
import com.flowmate.autoxiaoer.ui.MainUiState
import com.flowmate.autoxiaoer.ui.MainViewModel
import com.flowmate.autoxiaoer.ui.ShizukuStatus
import com.flowmate.autoxiaoer.util.Logger
import com.flowmate.autoxiaoer.util.showWithPrimaryButtons
import com.flowmate.autoxiaoer.voice.ContinuousListeningService
import com.flowmate.autoxiaoer.voice.VoiceError
import com.flowmate.autoxiaoer.voice.VoiceInputManager
import com.flowmate.autoxiaoer.voice.VoiceModelDownloadListener
import com.flowmate.autoxiaoer.voice.VoiceModelManager
import com.flowmate.autoxiaoer.voice.VoiceModelState
import com.flowmate.autoxiaoer.voice.VoiceRecognitionResult
import com.flowmate.autoxiaoer.voice.VoiceRecordingDialog
import kotlinx.coroutines.launch

/**
 * Home Fragment for task input and execution status display.
 *
 * Responsible for:
 * - Displaying task input field with voice input and template selection
 * - Showing task execution status and controls
 * - Managing floating window quick action
 * - Observing MainViewModel state for cross-Fragment synchronization
 */
class TaskFragment : Fragment() {
    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var settingsManager: SettingsManager
    private var voiceInputManager: VoiceInputManager? = null

    // Task Input Views
    private lateinit var taskInput: TextInputEditText
    private lateinit var btnVoiceInput: ImageButton
    private lateinit var btnSelectTemplate: ImageButton
    private lateinit var btnStartTask: MaterialButton
    private lateinit var btnScheduleTask: MaterialButton

    // Floating Window Button
    private lateinit var btnFloatingWindow: ImageButton

    // Scheduled Tasks Button
    private lateinit var btnScheduledTasks: ImageButton

    // Notification Trigger Button
    private lateinit var btnNotificationTrigger: ImageButton

    // Post-task Action Views
    private lateinit var radioGroupPostTaskAction: RadioGroup
    private lateinit var radioPostTaskNone: RadioButton
    private lateinit var radioPostTaskLock: RadioButton


    // Voice Settings Views
    private lateinit var voiceModelStatus: TextView
    private lateinit var btnVoiceModelAction: MaterialButton
    private lateinit var switchContinuousListening: MaterialSwitch
    private lateinit var wakeWordInput: TextInputEditText
    private lateinit var sensitivitySlider: Slider
    private var voiceModelManager: VoiceModelManager? = null
    private var isUpdatingContinuousListeningSwitch = false

    // Permission request launcher for one-shot voice input
    private val audioPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { isGranted ->
            if (isGranted) {
                showVoiceInputDialog()
            } else {
                Toast.makeText(
                    requireContext(),
                    R.string.voice_permission_denied,
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }

    // Permission launchers for continuous listening
    private val continuousListeningNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                startContinuousListeningService()
            } else {
                isUpdatingContinuousListeningSwitch = true
                switchContinuousListening.isChecked = false
                isUpdatingContinuousListeningSwitch = false
                settingsManager.setContinuousListening(false)
                Toast.makeText(
                    requireContext(),
                    R.string.voice_notification_permission_required,
                    Toast.LENGTH_LONG,
                ).show()
            }
        }

    private val continuousListeningAudioPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                checkNotificationPermissionAndStartListening()
            } else {
                isUpdatingContinuousListeningSwitch = true
                switchContinuousListening.isChecked = false
                isUpdatingContinuousListeningSwitch = false
                settingsManager.setContinuousListening(false)
                Toast.makeText(
                    requireContext(),
                    R.string.voice_audio_permission_required,
                    Toast.LENGTH_LONG,
                ).show()
            }
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        Logger.d(TAG, "TaskFragment onCreateView")
        return inflater.inflate(R.layout.fragment_task, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Logger.d(TAG, "TaskFragment onViewCreated")

        settingsManager = SettingsManager.getInstance(requireContext())

        initViews(view)
        setupListeners()
        observeViewModel()
        loadVoiceSettings()
    }


    override fun onDestroyView() {
        super.onDestroyView()
        voiceInputManager?.release()
        voiceInputManager = null
    }

    /**
     * Initializes all view references.
     */
    private fun initViews(view: View) {
        // Task Input Views
        taskInput = view.findViewById(R.id.taskInput)
        btnVoiceInput = view.findViewById(R.id.btnVoiceInput)
        btnSelectTemplate = view.findViewById(R.id.btnSelectTemplate)
        btnStartTask = view.findViewById(R.id.btnStartTask)
        btnScheduleTask = view.findViewById(R.id.btnScheduleTask)

        // Floating Window Button
        btnFloatingWindow = view.findViewById(R.id.btnFloatingWindow)

        // Scheduled Tasks Button
        btnScheduledTasks = view.findViewById(R.id.btnScheduledTasks)

        // Post-task Action
        radioGroupPostTaskAction = view.findViewById(R.id.radioGroupPostTaskAction)
        radioPostTaskNone = view.findViewById(R.id.radioPostTaskNone)
        radioPostTaskLock = view.findViewById(R.id.radioPostTaskLock)
        when (settingsManager.getPostTaskAction()) {
            PostTaskAction.LOCK_SCREEN -> radioPostTaskLock.isChecked = true
            else -> radioPostTaskNone.isChecked = true
        }

        // Notification Trigger Button
        btnNotificationTrigger = view.findViewById(R.id.btnNotificationTrigger)

        // Voice Settings
        voiceModelStatus = view.findViewById(R.id.voiceModelStatus)
        btnVoiceModelAction = view.findViewById(R.id.btnVoiceModelAction)
        switchContinuousListening = view.findViewById(R.id.switchContinuousListening)
        wakeWordInput = view.findViewById(R.id.wakeWordInput)
        sensitivitySlider = view.findViewById(R.id.sensitivitySlider)
    }

    /**
     * Sets up click listeners and text change listeners.
     */
    private fun setupListeners() {
        // Task input text change listener
        taskInput.doAfterTextChanged { text ->
            viewModel.updateTaskInput(text?.isNotBlank() == true)
        }

        // Voice input button
        btnVoiceInput.setOnClickListener {
            startVoiceInput()
        }

        // Template selection button - opens TemplateManagerDialog
        btnSelectTemplate.setOnClickListener {
            TemplateManagerDialog(
                context = requireContext(),
                settingsManager = settingsManager,
                onTemplateSelected = { template -> applyTemplate(template) },
            ).show()
        }

        // Start task button
        btnStartTask.setOnClickListener {
            startTask()
        }

        // Schedule task button
        btnScheduleTask.setOnClickListener {
            scheduleTask()
        }

        // Floating window button
        btnFloatingWindow.setOnClickListener {
            toggleFloatingWindow()
        }
        
        // Scheduled tasks management button
        btnScheduledTasks.setOnClickListener {
            showScheduledTasksList()
        }

        // Notification trigger button
        btnNotificationTrigger.setOnClickListener {
            showNotificationTriggerListDialog()
        }

        // Post-task action
        radioGroupPostTaskAction.setOnCheckedChangeListener { _, checkedId ->
            val action = if (checkedId == R.id.radioPostTaskLock) PostTaskAction.LOCK_SCREEN else PostTaskAction.NONE
            settingsManager.setPostTaskAction(action)
        }

        // Voice settings
        btnVoiceModelAction.setOnClickListener { onVoiceModelActionClick() }

        switchContinuousListening.setOnCheckedChangeListener { _, isChecked ->
            if (isUpdatingContinuousListeningSwitch) return@setOnCheckedChangeListener
            settingsManager.setContinuousListening(isChecked)
            if (isChecked) {
                if (voiceModelManager?.isModelDownloaded() == true) {
                    if (ContextCompat.checkSelfPermission(
                            requireContext(),
                            Manifest.permission.RECORD_AUDIO,
                        ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                    ) {
                        continuousListeningAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        return@setOnCheckedChangeListener
                    }
                    checkNotificationPermissionAndStartListening()
                } else {
                    isUpdatingContinuousListeningSwitch = true
                    switchContinuousListening.isChecked = false
                    isUpdatingContinuousListeningSwitch = false
                    settingsManager.setContinuousListening(false)
                    Toast.makeText(requireContext(), R.string.voice_model_required, Toast.LENGTH_SHORT).show()
                }
            } else {
                ContinuousListeningService.stop(requireContext())
            }
        }

        wakeWordInput.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) saveWakeWords()
        }

        sensitivitySlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) settingsManager.setWakeWordSensitivity(value / 100f)
        }
    }

    /**
     * Observes ViewModel state and updates UI accordingly.
     *
     * Observes both the main UI state and permission states for cross-Fragment
     * synchronization. When permissions change in SettingsFragment, this Fragment
     * will automatically update its UI.
     *
     * _Requirements: 5.2, 5.3_
     */
    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        updateUiState(state)
                    }
                }

                // Observe permission states for cross-Fragment synchronization
                // When permissions change in SettingsFragment, update UI here
                launch {
                    viewModel.permissionStates.collect { permissionStates ->
                        onPermissionStatesChanged(permissionStates)
                    }
                }
            }
        }
    }

    /**
     * Handles permission state changes from cross-Fragment synchronization.
     *
     * When permissions are granted or revoked in SettingsFragment, this method
     * updates the TaskFragment UI accordingly.
     *
     * @param permissionStates The updated permission states
     *
     * _Requirements: 5.2, 5.3_
     */
    private fun onPermissionStatesChanged(permissionStates: com.flowmate.autoxiaoer.ui.PermissionStates) {
        Logger.d(TAG, "Permission states changed: $permissionStates")

        // The start button state is already managed through uiState.canStartTask
        // which is updated when permissions change. However, we can add additional
        // UI feedback here if needed.

        // Update floating window button state based on overlay permission
        btnFloatingWindow.isEnabled = permissionStates.overlay

        // If Shizuku is not connected, we could show a hint or disable certain features
        if (!permissionStates.shizuku) {
            // The start button will already be disabled through canStartTask
            // Additional UI hints could be added here if needed
        }
    }

    /**
     * Updates UI based on the current state.
     */
    private fun updateUiState(state: MainUiState) {
        // Update start button state
        btnStartTask.isEnabled = state.canStartTask
    }

    /**
     * Starts a new task with the current input.
     */
    private fun startTask() {
        val taskDescription = taskInput.text?.toString()?.trim()

        if (taskDescription.isNullOrBlank()) {
            Toast.makeText(requireContext(), R.string.toast_task_empty, Toast.LENGTH_SHORT).show()
            return
        }

        val state = viewModel.uiState.value

        // Check Shizuku connection
        if (state.shizukuStatus != ShizukuStatus.CONNECTED) {
            Toast.makeText(
                requireContext(),
                R.string.toast_shizuku_not_running,
                Toast.LENGTH_SHORT,
            ).show()
            return
        }

        // Check overlay permission
        if (!state.hasOverlayPermission) {
            Toast.makeText(
                requireContext(),
                R.string.toast_overlay_permission_required,
                Toast.LENGTH_SHORT,
            ).show()
            return
        }

        Logger.i(TAG, "Starting task: ${taskDescription.take(50)}...")
        viewModel.startTask(taskDescription)
    }

    /**
     * Starts voice input for task description.
     */
    private fun startVoiceInput() {
        // Check audio permission
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.RECORD_AUDIO,
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }

        // Initialize voice input manager if needed
        if (voiceInputManager == null) {
            voiceInputManager = VoiceInputManager(requireContext())
        }

        // Check if model is ready
        if (!voiceInputManager!!.isModelReady()) {
            Toast.makeText(
                requireContext(),
                R.string.voice_model_required,
                Toast.LENGTH_SHORT,
            ).show()
            return
        }

        showVoiceInputDialog()
    }

    /**
     * Shows the voice recording dialog.
     */
    private fun showVoiceInputDialog() {
        val manager = voiceInputManager ?: return

        VoiceRecordingDialog(
            context = requireContext(),
            voiceInputManager = manager,
            onResult = { result: VoiceRecognitionResult ->
                handleVoiceResult(result)
            },
            onError = { error: VoiceError ->
                handleVoiceError(error)
            },
        ).show()
    }

    /**
     * Handles voice recognition result.
     */
    private fun handleVoiceResult(result: VoiceRecognitionResult) {
        if (result.text.isNotBlank()) {
            taskInput.setText(result.text)
            taskInput.setSelection(result.text.length)
        }
    }

    /**
     * Handles voice recognition error.
     */
    private fun handleVoiceError(error: VoiceError) {
        val messageResId =
            when (error) {
                VoiceError.PermissionDenied -> R.string.voice_permission_denied
                VoiceError.ModelNotDownloaded -> R.string.voice_model_required
                VoiceError.ModelLoadFailed -> R.string.voice_model_load_failed
                VoiceError.RecordingFailed -> R.string.voice_recording_failed
                VoiceError.RecognitionFailed -> R.string.voice_recognition_failed
                VoiceError.NetworkError -> R.string.voice_network_error
                is VoiceError.Unknown -> R.string.voice_unknown_error
            }
        Toast.makeText(requireContext(), messageResId, Toast.LENGTH_SHORT).show()
    }

    /**
     * Applies a template to the task input.
     */
    private fun applyTemplate(template: TaskTemplate) {
        taskInput.setText(template.description)
        taskInput.setSelection(template.description.length)
        Logger.d(TAG, "Applied template: ${template.name}")
    }

    /**
     * Toggles the floating window visibility.
     *
     * When enabling, minimizes the app first so the floating window becomes visible.
     */
    private fun toggleFloatingWindow() {
        val wasEnabled = FloatingWindowStateManager.isUserEnabled()
        FloatingWindowStateManager.toggleByUser(requireContext())

        // If enabling floating window, minimize app so it becomes visible
        if (!wasEnabled) {
            activity?.moveTaskToBack(true)
        }
    }

    /**
     * Shows the schedule task dialog to create a new scheduled task.
     */
    private fun scheduleTask() {
        val taskDescription = taskInput.text?.toString()?.trim()

        if (taskDescription.isNullOrBlank()) {
            Toast.makeText(requireContext(), R.string.schedule_task_input_empty, Toast.LENGTH_SHORT).show()
            return
        }

        val dialog = ScheduleTaskDialog(
            context = requireContext(),
            taskDescription = taskDescription,
            onScheduled = { task ->
                val taskManager = ScheduledTaskManager.getInstance(requireContext())
                taskManager.saveTask(task)
                Toast.makeText(requireContext(), R.string.schedule_saved, Toast.LENGTH_SHORT).show()
                Logger.i(TAG, "Scheduled task created: ${task.id}")
            }
        )
        dialog.show()
    }

    /**
     * Shows the scheduled tasks list dialog.
     */
    private fun showScheduledTasksList() {
        val dialog = ScheduledTaskListDialog(
            context = requireContext(),
            lifecycleOwner = viewLifecycleOwner,
            onNewTask = {
                scheduleTask()
            }
        )
        dialog.show()
    }

    // region Notification Triggers

    private fun showNotificationTriggerListDialog() {
        NotificationTriggerListDialog(
            context = requireContext(),
            lifecycleOwner = viewLifecycleOwner,
            onAddRule = { showNotificationTriggerEditDialog() },
        ).show()
    }

    private fun showNotificationTriggerEditDialog() {
        NotificationTriggerEditDialog(
            context = requireContext(),
            lifecycleOwner = viewLifecycleOwner,
            onRuleSaved = { },
        ).show()
    }

    // endregion

    // region Voice Settings

    private fun loadVoiceSettings() {
        if (voiceModelManager == null) {
            voiceModelManager = VoiceModelManager.getInstance(requireContext())
        }
        updateVoiceModelStatus()
        isUpdatingContinuousListeningSwitch = true
        switchContinuousListening.isChecked = settingsManager.isContinuousListeningEnabled()
        isUpdatingContinuousListeningSwitch = false
        val wakeWords = settingsManager.getWakeWordsList()
        wakeWordInput.setText(wakeWords.joinToString(", "))
        val sensitivity = settingsManager.getWakeWordSensitivity()
        sensitivitySlider.value = sensitivity * 100f
    }

    private fun updateVoiceModelStatus() {
        val state = voiceModelManager?.state?.value
        when (state) {
            is VoiceModelState.Downloaded -> {
                voiceModelStatus.text = getString(R.string.voice_model_downloaded, state.sizeMB)
                voiceModelStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_running))
                btnVoiceModelAction.text = getString(R.string.voice_delete_model)
                btnVoiceModelAction.setIconResource(R.drawable.ic_delete)
                btnVoiceModelAction.isEnabled = true
            }
            is VoiceModelState.Downloading -> {
                voiceModelStatus.text = getString(R.string.voice_downloading_progress, state.progress)
                voiceModelStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_running))
                btnVoiceModelAction.text = getString(R.string.voice_downloading)
                btnVoiceModelAction.isEnabled = false
            }
            is VoiceModelState.Error -> {
                voiceModelStatus.text = getString(R.string.voice_download_failed, state.message)
                voiceModelStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_failed))
                btnVoiceModelAction.text = getString(R.string.voice_download_model)
                btnVoiceModelAction.setIconResource(R.drawable.ic_download)
                btnVoiceModelAction.isEnabled = true
            }
            VoiceModelState.NotDownloaded, null -> {
                voiceModelStatus.text = getString(R.string.voice_model_not_downloaded)
                voiceModelStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
                btnVoiceModelAction.text = getString(R.string.voice_download_model)
                btnVoiceModelAction.setIconResource(R.drawable.ic_download)
                btnVoiceModelAction.isEnabled = true
            }
        }
    }

    private fun onVoiceModelActionClick() {
        when (voiceModelManager?.state?.value) {
            is VoiceModelState.Downloaded -> showDeleteModelDialog()
            is VoiceModelState.Downloading -> { /* Do nothing while downloading */ }
            else -> showDownloadModelDialog()
        }
    }

    private fun showDownloadModelDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_voice_download_confirm, null)
        MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setPositiveButton(R.string.voice_download_confirm_title) { _, _ -> startModelDownload() }
            .setNegativeButton(R.string.dialog_cancel, null)
            .showWithPrimaryButtons()
    }

    private fun startModelDownload() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_voice_download_progress, null)
        val progressBar = dialogView.findViewById<android.widget.ProgressBar>(R.id.downloadProgressBar)
        val progressText = dialogView.findViewById<TextView>(R.id.downloadProgressText)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setCancelable(false)
            .setNegativeButton(R.string.dialog_cancel) { _, _ -> voiceModelManager?.cancelDownload() }
            .create()

        dialog.show()

        viewLifecycleOwner.lifecycleScope.launch {
            voiceModelManager?.downloadModel(object : VoiceModelDownloadListener {
                override fun onDownloadStarted() {
                    activity?.runOnUiThread {
                        progressText.text = getString(R.string.voice_download_status_preparing)
                    }
                }
                override fun onDownloadProgress(progress: Int, downloadedBytes: Long, totalBytes: Long) {
                    activity?.runOnUiThread {
                        progressBar.progress = progress
                        progressText.text = getString(R.string.voice_downloading_progress, progress)
                    }
                }
                override fun onDownloadCompleted(modelPath: String) {
                    activity?.runOnUiThread {
                        dialog.dismiss()
                        updateVoiceModelStatus()
                        Toast.makeText(requireContext(), R.string.voice_download_complete, Toast.LENGTH_SHORT).show()
                    }
                }
                override fun onDownloadFailed(error: String) {
                    activity?.runOnUiThread {
                        dialog.dismiss()
                        updateVoiceModelStatus()
                        Toast.makeText(requireContext(), getString(R.string.voice_download_failed, error), Toast.LENGTH_LONG).show()
                    }
                }
                override fun onDownloadCancelled() {
                    activity?.runOnUiThread {
                        dialog.dismiss()
                        updateVoiceModelStatus()
                    }
                }
            })
        }
    }

    private fun showDeleteModelDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.voice_delete_confirm_title)
            .setMessage(R.string.voice_delete_confirm_message)
            .setPositiveButton(R.string.dialog_confirm) { _, _ -> deleteVoiceModel() }
            .setNegativeButton(R.string.dialog_cancel, null)
            .showWithPrimaryButtons()
    }

    private fun deleteVoiceModel() {
        val success = voiceModelManager?.deleteModel() == true
        if (success) {
            updateVoiceModelStatus()
            Toast.makeText(requireContext(), R.string.voice_delete_success, Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(requireContext(), R.string.voice_download_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveWakeWords() {
        val input = wakeWordInput.text?.toString() ?: ""
        val wakeWords = input.split(",", "，").map { it.trim() }.filter { it.isNotEmpty() }
        settingsManager.setWakeWords(wakeWords)
    }

    private fun checkNotificationPermissionAndStartListening() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.POST_NOTIFICATIONS,
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                continuousListeningNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        startContinuousListeningService()
    }

    private fun startContinuousListeningService() {
        ContinuousListeningService.start(requireContext())
        Toast.makeText(requireContext(), R.string.voice_listening_started, Toast.LENGTH_SHORT).show()
    }

    // endregion

    companion object {
        private const val TAG = "TaskFragment"
    }
}
