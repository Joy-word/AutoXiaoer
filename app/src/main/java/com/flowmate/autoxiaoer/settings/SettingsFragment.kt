package com.flowmate.autoxiaoer.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.flowmate.autoxiaoer.R
import com.flowmate.autoxiaoer.agent.PhoneAgentConfig
import com.flowmate.autoxiaoer.agent.LLMAgentConfig
import com.flowmate.autoxiaoer.config.LLMAgentPrompts
import com.flowmate.autoxiaoer.model.ModelClient
import com.flowmate.autoxiaoer.model.ModelConfig
import com.flowmate.autoxiaoer.ui.MainViewModel
import com.flowmate.autoxiaoer.ui.PermissionStates
import com.flowmate.autoxiaoer.util.LogFileManager
import com.flowmate.autoxiaoer.util.Logger
import com.flowmate.autoxiaoer.util.applyPrimaryButtonColors
import com.flowmate.autoxiaoer.util.showWithPrimaryButtons
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

/**
 * Settings Fragment for app configuration and permissions.
 *
 * Migrated from SettingsActivity to support bottom navigation architecture.
 * Includes permissions center (collapsible), model configuration, agent configuration,
 * task templates, voice settings, advanced settings, and debug logs.
 *
 * _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 4.6, 4.7, 4.8, 5.3_
 */
class SettingsFragment : Fragment() {
    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var settingsManager: SettingsManager

    // Permissions center views
    private lateinit var permissionsCenterCard: View
    private lateinit var permissionsHeader: View
    private lateinit var permissionsContent: View
    private lateinit var permissionsSummary: TextView
    private lateinit var btnExpandCollapse: ImageButton
    private var isPermissionsExpanded = false

    // Permission item views
    private lateinit var permissionShizuku: View
    private lateinit var permissionOverlay: View
    private lateinit var permissionKeyboard: View
    private lateinit var permissionBattery: View

    // Debug logs views
    private lateinit var logSizeText: TextView
    private lateinit var btnExportLogs: Button
    private lateinit var btnClearLogs: Button

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        Logger.d(TAG, "SettingsFragment created")
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        settingsManager = SettingsManager.getInstance(requireContext())
        initViews(view)
        loadCurrentSettings()
        setupListeners()
        observePermissionStates()
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionStates()
        updateLogSizeDisplay()
    }

    /**
     * Initializes all view references.
     */
    private fun initViews(view: View) {
        // Permissions center
        permissionsCenterCard = view.findViewById(R.id.permissionsCenterCard)
        permissionsHeader = view.findViewById(R.id.permissionsHeader)
        permissionsContent = view.findViewById(R.id.permissionsContent)
        permissionsSummary = view.findViewById(R.id.permissionsSummary)
        btnExpandCollapse = view.findViewById(R.id.btnExpandCollapse)

        // Permission items
        permissionShizuku = view.findViewById(R.id.permissionShizuku)
        permissionOverlay = view.findViewById(R.id.permissionOverlay)
        permissionKeyboard = view.findViewById(R.id.permissionKeyboard)
        permissionBattery = view.findViewById(R.id.permissionBattery)

        // Phone-agent settings entry button
        view.findViewById<Button>(R.id.btnPhoneAgentSettings)
            .setOnClickListener { showPhoneAgentSettingsDialog() }

        // Debug logs
        logSizeText = view.findViewById(R.id.logSizeText)
        btnExportLogs = view.findViewById(R.id.btnExportLogs)
        btnClearLogs = view.findViewById(R.id.btnClearLogs)

        // LLM-agent settings entry button
        view.findViewById<Button>(R.id.btnLLMAgentSettings)
            .setOnClickListener { showLLMAgentSettingsDialog() }

        // Setup permission items
        setupPermissionItem(
            permissionShizuku,
            getString(R.string.shizuku_status_title),
            R.drawable.ic_layers,
        )
        setupPermissionItem(
            permissionOverlay,
            getString(R.string.overlay_permission_title),
            R.drawable.ic_layers,
        )
        setupPermissionItem(
            permissionKeyboard,
            getString(R.string.keyboard_title),
            R.drawable.ic_keyboard,
        )
        setupPermissionItem(
            permissionBattery,
            getString(R.string.battery_opt_title),
            R.drawable.ic_battery,
        )
    }

    /**
     * Sets up a permission item view with name and icon.
     */
    private fun setupPermissionItem(itemView: View, name: String, iconRes: Int) {
        itemView.findViewById<TextView>(R.id.permissionName).text = name
        itemView.findViewById<ImageView>(R.id.permissionIcon).setImageResource(iconRes)
    }

    /**
     * Loads current settings from storage and displays them.
     */
    private fun loadCurrentSettings() {
        Logger.d(TAG, "Loading current settings")
        updateLogSizeDisplay()
    }

    /**
     * Sets up click listeners for all interactive views.
     */
    private fun setupListeners() {
        // Permissions center expand/collapse
        permissionsHeader.setOnClickListener { togglePermissionsExpanded() }
        btnExpandCollapse.setOnClickListener { togglePermissionsExpanded() }

        // Permission action buttons
        setupPermissionActionListeners()

        btnExportLogs.setOnClickListener { exportDebugLogs() }
        btnClearLogs.setOnClickListener { showClearLogsDialog() }
    }

    /**
     * Sets up permission action button listeners.
     */
    private fun setupPermissionActionListeners() {
        permissionShizuku.findViewById<Button>(R.id.btnPermissionAction).setOnClickListener {
            requestShizukuPermission()
        }
        permissionOverlay.findViewById<Button>(R.id.btnPermissionAction).setOnClickListener {
            requestOverlayPermission()
        }
        permissionKeyboard.findViewById<Button>(R.id.btnPermissionAction).setOnClickListener {
            openKeyboardSettings()
        }
        permissionBattery.findViewById<Button>(R.id.btnPermissionAction).setOnClickListener {
            requestBatteryOptimization()
        }
    }

    /**
     * Toggles the permissions center expanded/collapsed state.
     */
    private fun togglePermissionsExpanded() {
        isPermissionsExpanded = !isPermissionsExpanded
        permissionsContent.visibility = if (isPermissionsExpanded) View.VISIBLE else View.GONE
        btnExpandCollapse.setImageResource(
            if (isPermissionsExpanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more,
        )
    }

    /**
     * Observes permission states from ViewModel for cross-Fragment synchronization.
     */
    private fun observePermissionStates() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.permissionStates.collect { states ->
                    updatePermissionUI(states)
                }
            }
        }
    }

    /**
     * Refreshes all permission states and updates ViewModel.
     */
    private fun refreshPermissionStates() {
        val context = requireContext()
        val states =
            PermissionStates(
                shizuku = isShizukuConnected(),
                overlay = Settings.canDrawOverlays(context),
                keyboard = isKeyboardEnabled(),
                battery = isBatteryOptimizationIgnored(),
            )
        viewModel.updateAllPermissionStates(states)
    }

    /**
     * Updates permission UI based on states.
     */
    private fun updatePermissionUI(states: PermissionStates) {
        updatePermissionItemUI(
            permissionShizuku,
            states.shizuku,
            getString(R.string.request_shizuku_permission),
        )
        updatePermissionItemUI(
            permissionOverlay,
            states.overlay,
            getString(R.string.request_overlay_permission),
        )
        updatePermissionItemUI(
            permissionKeyboard,
            states.keyboard,
            getString(R.string.enable_keyboard),
        )
        updatePermissionItemUI(
            permissionBattery,
            states.battery,
            getString(R.string.battery_opt_request),
        )

        // Update summary
        val grantedCount =
            listOf(
                states.shizuku,
                states.overlay,
                states.keyboard,
                states.battery,
            ).count { it }
        permissionsSummary.text =
            if (grantedCount == 4) {
                getString(R.string.permissions_summary_all_granted)
            } else {
                getString(R.string.permissions_summary_partial, grantedCount, 4)
            }
    }

    /**
     * Updates a single permission item UI.
     */
    private fun updatePermissionItemUI(itemView: View, isGranted: Boolean, actionText: String) {
        val icon = itemView.findViewById<ImageView>(R.id.permissionIcon)
        val status = itemView.findViewById<TextView>(R.id.permissionStatus)
        val actionBtn = itemView.findViewById<Button>(R.id.btnPermissionAction)

        if (isGranted) {
            icon.setImageResource(R.drawable.ic_check_circle)
            icon.setColorFilter(ContextCompat.getColor(requireContext(), R.color.status_running))
            status.text = getString(R.string.permission_granted)
            actionBtn.visibility = View.GONE
        } else {
            icon.setImageResource(R.drawable.ic_error)
            icon.setColorFilter(ContextCompat.getColor(requireContext(), R.color.status_failed))
            status.text = getString(R.string.permission_not_granted)
            actionBtn.text = actionText
            actionBtn.visibility = View.VISIBLE
        }
    }

    // region Permission Helpers

    private fun isShizukuConnected(): Boolean = try {
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (e: Exception) {
        false
    }

    private fun isKeyboardEnabled(): Boolean {
        val enabledInputMethods =
            Settings.Secure.getString(
                requireContext().contentResolver,
                Settings.Secure.ENABLED_INPUT_METHODS,
            ) ?: ""
        return enabledInputMethods.contains(requireContext().packageName)
    }

    private fun isBatteryOptimizationIgnored(): Boolean {
        val pm = requireContext().getSystemService(PowerManager::class.java)
        return pm?.isIgnoringBatteryOptimizations(requireContext().packageName) == true
    }

    private fun requestShizukuPermission() {
        try {
            if (!Shizuku.pingBinder()) {
                Toast.makeText(requireContext(), R.string.toast_shizuku_not_running, Toast.LENGTH_SHORT).show()
                return
            }
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(requireContext(), R.string.toast_shizuku_already_granted, Toast.LENGTH_SHORT).show()
                return
            }
            Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE)
        } catch (e: Exception) {
            Logger.e(TAG, "Error requesting Shizuku permission", e)
        }
    }

    private fun requestOverlayPermission() {
        val intent =
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${requireContext().packageName}"),
            )
        startActivity(intent)
    }

    private fun openKeyboardSettings() {
        startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
    }

    private fun requestBatteryOptimization() {
        val intent =
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${requireContext().packageName}")
            }
        startActivity(intent)
    }

    // endregion

    // region Settings Helpers

    private fun isValidUrl(url: String): Boolean = try {
        val uri = android.net.Uri.parse(url)
        uri.scheme?.startsWith("http") == true && !uri.host.isNullOrEmpty()
    } catch (e: Exception) {
        false
    }

    /**
     * Runs a connection test against the given API config and updates the provided button's
     * enabled/text state during the async call. Shows a Toast with the result.
     */
    private fun testConnectionInDialog(
        baseUrl: String,
        apiKey: String,
        modelName: String,
        testButton: Button,
    ) {
        if (baseUrl.isEmpty() || !isValidUrl(baseUrl) || modelName.isEmpty()) {
            Toast.makeText(requireContext(), R.string.settings_test_invalid_config, Toast.LENGTH_SHORT).show()
            return
        }

        val testConfig = ModelConfig(
            baseUrl = baseUrl,
            apiKey = if (apiKey.isEmpty()) "EMPTY" else apiKey,
            modelName = modelName,
            timeoutSeconds = 30,
        )

        val client = ModelClient(testConfig)
        testButton.isEnabled = false
        testButton.text = getString(R.string.settings_testing)

        viewLifecycleOwner.lifecycleScope.launch {
            val result = client.testConnection()
            Logger.d(TAG, "Connection test result: $result")

            testButton.isEnabled = true
            testButton.text = getString(R.string.settings_test_connection)

            val message = when (result) {
                is ModelClient.TestResult.Success ->
                    getString(R.string.settings_test_success, result.latencyMs)
                is ModelClient.TestResult.AuthError ->
                    getString(R.string.settings_test_auth_error, result.message)
                is ModelClient.TestResult.ModelNotFound ->
                    getString(R.string.settings_test_model_not_found, result.message)
                is ModelClient.TestResult.ServerError ->
                    getString(R.string.settings_test_server_error, result.code, result.message)
                is ModelClient.TestResult.ConnectionError ->
                    getString(R.string.settings_test_connection_error, result.message)
                is ModelClient.TestResult.Timeout ->
                    getString(R.string.settings_test_timeout, result.message)
            }
            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
        }
    }

    // endregion

    // region Phone-agent System Prompt

    /**
     * Shows a dialog for editing the Phone-agent's system prompt (Chinese or English).
     */
    private fun showPhoneAgentPromptDialog(language: String) {
        Logger.d(TAG, "Showing Phone-agent prompt dialog for language: $language")
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_system_prompt, null)
        val promptInput = dialogView.findViewById<TextInputEditText>(R.id.promptInput)
        val btnReset = dialogView.findViewById<Button>(R.id.btnResetPrompt)

        val currentPrompt = settingsManager.getCustomSystemPrompt(language)
            ?: if (language == "en") {
                com.flowmate.autoxiaoer.config.SystemPrompts.getEnglishPromptTemplate()
            } else {
                com.flowmate.autoxiaoer.config.SystemPrompts.getChinesePromptTemplate()
            }
        promptInput.setText(currentPrompt)

        val title = if (language == "en") {
            getString(R.string.settings_system_prompt_en)
        } else {
            getString(R.string.settings_system_prompt_cn)
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setView(dialogView)
            .setPositiveButton(R.string.dialog_confirm) { _, _ ->
                val newPrompt = promptInput.text?.toString() ?: ""
                if (newPrompt.isNotBlank()) {
                    settingsManager.saveCustomSystemPrompt(language, newPrompt)
                    if (language == "en") {
                        com.flowmate.autoxiaoer.config.SystemPrompts.setCustomEnglishPrompt(newPrompt)
                    } else {
                        com.flowmate.autoxiaoer.config.SystemPrompts.setCustomChinesePrompt(newPrompt)
                    }
                    Toast.makeText(requireContext(), R.string.settings_system_prompt_saved, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .create()

        btnReset.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.settings_system_prompt_reset)
                .setMessage(R.string.settings_system_prompt_reset_confirm)
                .setPositiveButton(R.string.dialog_confirm) { _, _ ->
                    settingsManager.clearCustomSystemPrompt(language)
                    if (language == "en") {
                        com.flowmate.autoxiaoer.config.SystemPrompts.setCustomEnglishPrompt(null)
                    } else {
                        com.flowmate.autoxiaoer.config.SystemPrompts.setCustomChinesePrompt(null)
                    }
                    Toast.makeText(requireContext(), R.string.settings_system_prompt_reset_done, Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
                .setNegativeButton(R.string.dialog_cancel, null)
                .showWithPrimaryButtons()
        }

        dialog.show()
        dialog.applyPrimaryButtonColors()
    }

    // endregion

    // region Debug Logs

    private fun updateLogSizeDisplay() {
        val totalSize = LogFileManager.getTotalLogSize()
        val formattedSize = LogFileManager.formatSize(totalSize)
        logSizeText.text = getString(R.string.settings_debug_logs_size, formattedSize)
    }

    private fun exportDebugLogs() {
        Logger.i(TAG, "Exporting debug logs")

        val logFiles = LogFileManager.getLogFiles()
        if (logFiles.isEmpty()) {
            Toast.makeText(requireContext(), R.string.settings_logs_empty, Toast.LENGTH_SHORT).show()
            return
        }

        val shareIntent = LogFileManager.exportLogs(requireContext())
        if (shareIntent != null) {
            startActivity(Intent.createChooser(shareIntent, getString(R.string.settings_export_logs)))
        } else {
            Toast.makeText(requireContext(), R.string.settings_logs_export_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showClearLogsDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings_clear_logs)
            .setMessage(R.string.settings_clear_logs_confirm)
            .setPositiveButton(R.string.dialog_confirm) { _, _ ->
                LogFileManager.clearAllLogs()
                updateLogSizeDisplay()
                Toast.makeText(requireContext(), R.string.settings_logs_cleared, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .showWithPrimaryButtons()
    }

    // endregion

    // region Phone-agent & LLM-agent Settings Dialogs

    /**
     * Shows a dialog for configuring Phone-agent (the executor).
     * UI structure mirrors showLLMAgentSettingsDialog().
     * Fields: Base URL, API Key, Model Name, Max Steps, Screenshot Delay, Language,
     *         Post-Task Action. Includes a test-connection button.
     * On save, persists config and reinitializes agent instances immediately.
     */
    private fun showPhoneAgentSettingsDialog() {
        Logger.d(TAG, "Showing Phone-agent settings dialog")
        val ctx = requireContext()
        val modelConfig = settingsManager.getModelConfig()
        val PhoneAgentConfig = settingsManager.getPhoneAgentConfig()

        val scrollView = android.widget.ScrollView(ctx)
        val container = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            val paddingPx = (16 * resources.displayMetrics.density).toInt()
            setPadding(paddingPx, paddingPx, paddingPx, paddingPx)
        }
        scrollView.addView(container)

        fun makeInputLayout(hint: String, helperText: String? = null): Pair<TextInputLayout, TextInputEditText> {
            val layout = TextInputLayout(ctx).apply {
                this.hint = hint
                boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
                if (helperText != null) {
                    isHelperTextEnabled = true
                    this.helperText = helperText
                }
                val lp = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                )
                lp.bottomMargin = (8 * resources.displayMetrics.density).toInt()
                layoutParams = lp
            }
            val edit = TextInputEditText(ctx)
            layout.addView(edit)
            return layout to edit
        }

        val (baseUrlLayout, baseUrlEdit) = makeInputLayout("API Base URL")
        baseUrlEdit.setText(modelConfig.baseUrl)
        baseUrlEdit.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
        container.addView(baseUrlLayout)

        val (apiKeyLayout, apiKeyEdit) = makeInputLayout("API Key")
        apiKeyEdit.setText(if (modelConfig.apiKey == "EMPTY") "" else modelConfig.apiKey)
        apiKeyEdit.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        container.addView(apiKeyLayout)

        val (modelNameLayout, modelNameEdit) = makeInputLayout(getString(R.string.settings_model_name))
        modelNameEdit.setText(modelConfig.modelName)
        container.addView(modelNameLayout)

        val (maxStepsLayout, maxStepsEdit) = makeInputLayout(
            getString(R.string.settings_max_steps),
            getString(R.string.settings_max_steps_hint),
        )
        maxStepsEdit.setText(PhoneAgentConfig.maxSteps.toString())
        maxStepsEdit.inputType = android.text.InputType.TYPE_CLASS_NUMBER
        container.addView(maxStepsLayout)

        val (screenshotDelayLayout, screenshotDelayEdit) = makeInputLayout(
            getString(R.string.settings_screenshot_delay),
            getString(R.string.settings_screenshot_delay_hint),
        )
        screenshotDelayEdit.setText((PhoneAgentConfig.screenshotDelayMs / 1000.0).toString())
        screenshotDelayEdit.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        container.addView(screenshotDelayLayout)

        // Language selection
        val dp4 = (4 * resources.displayMetrics.density).toInt()
        val dp8 = (8 * resources.displayMetrics.density).toInt()
        val langLabel = TextView(ctx).apply {
            text = getString(R.string.settings_language)
            setTextColor(android.graphics.Color.parseColor("#888888"))
            textSize = 14f
            val lp = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            lp.topMargin = dp4
            layoutParams = lp
        }
        container.addView(langLabel)

        val langGroup = RadioGroup(ctx).apply { orientation = RadioGroup.HORIZONTAL }
        val langCn = RadioButton(ctx).apply { text = getString(R.string.settings_language_chinese) }
        val langEn = RadioButton(ctx).apply { text = getString(R.string.settings_language_english) }
        langGroup.addView(langCn)
        langGroup.addView(langEn)
        if (PhoneAgentConfig.language == "en") langEn.isChecked = true else langCn.isChecked = true
        container.addView(langGroup)

        // Test connection button
        val btnTestConn = Button(ctx).apply {
            text = getString(R.string.settings_test_connection)
            val lp = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            lp.topMargin = dp8
            layoutParams = lp
        }
        container.addView(btnTestConn)

        // System prompt section header
        val promptSectionLabel = TextView(ctx).apply {
            text = getString(R.string.settings_advanced)
            setTextColor(android.graphics.Color.parseColor("#888888"))
            textSize = 14f
            val lp = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            lp.topMargin = dp8 * 2
            layoutParams = lp
        }
        container.addView(promptSectionLabel)

        val btnEditPromptCnDialog = Button(ctx).apply {
            text = getString(R.string.settings_system_prompt_cn)
            val lp = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            lp.topMargin = dp4
            layoutParams = lp
        }
        container.addView(btnEditPromptCnDialog)

        val btnEditPromptEnDialog = Button(ctx).apply {
            text = getString(R.string.settings_system_prompt_en)
            val lp = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            lp.topMargin = dp4
            layoutParams = lp
        }
        container.addView(btnEditPromptEnDialog)

        val dialog = MaterialAlertDialogBuilder(ctx)
            .setTitle(getString(R.string.settings_phone_agent_title))
            .setView(scrollView)
            .setPositiveButton(getString(R.string.settings_save)) { _, _ ->
                val baseUrl = baseUrlEdit.text?.toString()?.trim() ?: ""
                val apiKey = apiKeyEdit.text?.toString()?.trim().let {
                    if (it.isNullOrEmpty()) "EMPTY" else it
                }
                val modelName = modelNameEdit.text?.toString()?.trim() ?: ""
                val maxSteps = maxStepsEdit.text?.toString()?.trim()?.toIntOrNull() ?: PhoneAgentConfig.maxSteps
                val screenshotDelaySeconds = screenshotDelayEdit.text?.toString()?.trim()?.toDoubleOrNull()
                    ?: (PhoneAgentConfig.screenshotDelayMs / 1000.0)
                val language = if (langEn.isChecked) "en" else "cn"

                if (baseUrl.isEmpty() || !isValidUrl(baseUrl)) {
                    Toast.makeText(ctx, getString(R.string.settings_validation_error_url), Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (modelName.isEmpty()) {
                    Toast.makeText(ctx, getString(R.string.settings_validation_error_model), Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (maxSteps < 0) {
                    Toast.makeText(ctx, getString(R.string.settings_validation_error_steps), Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (screenshotDelaySeconds < 0) {
                    Toast.makeText(ctx, getString(R.string.settings_validation_error_delay), Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                settingsManager.saveModelConfig(ModelConfig(baseUrl = baseUrl, apiKey = apiKey, modelName = modelName))
                settingsManager.savePhoneAgentConfig(
                    PhoneAgentConfig(
                        maxSteps = maxSteps,
                        language = language,
                        screenshotDelayMs = (screenshotDelaySeconds * 1000).toLong(),
                    ),
                )

                com.flowmate.autoxiaoer.ComponentManager.getInstance(ctx).reinitializeAgents()
                Toast.makeText(ctx, getString(R.string.settings_phone_agent_saved), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(getString(R.string.dialog_cancel), null)
            .create()

        btnTestConn.setOnClickListener {
            testConnectionInDialog(
                baseUrl = baseUrlEdit.text?.toString()?.trim() ?: "",
                apiKey = apiKeyEdit.text?.toString()?.trim() ?: "",
                modelName = modelNameEdit.text?.toString()?.trim() ?: "",
                testButton = btnTestConn,
            )
        }

        btnEditPromptCnDialog.setOnClickListener { showPhoneAgentPromptDialog("cn") }
        btnEditPromptEnDialog.setOnClickListener { showPhoneAgentPromptDialog("en") }

        dialog.show()
        dialog.applyPrimaryButtonColors()
    }

    /**
     * Shows a dialog for configuring the LLM-agent (brain) independently from PhoneAgent.
     *
     * The dialog is built programmatically so no additional layout XML is required.
     * Fields: Base URL, API Key, Model Name, Max Tokens, Temperature, Max Planning Steps,
     *         Language, Custom System Prompt. Includes a test-connection button.
     */
    private fun showLLMAgentSettingsDialog() {
        Logger.d(TAG, "Showing LLM-agent settings dialog")
        val ctx = requireContext()
        val config = settingsManager.getLLMAgentConfig()

        val scrollView = android.widget.ScrollView(ctx)
        val container = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            val paddingPx = (16 * resources.displayMetrics.density).toInt()
            setPadding(paddingPx, paddingPx, paddingPx, paddingPx)
        }
        scrollView.addView(container)

        fun makeInputLayout(hint: String): Pair<TextInputLayout, TextInputEditText> {
            val layout = TextInputLayout(ctx).apply {
                this.hint = hint
                boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
                val lp = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                )
                lp.bottomMargin = (8 * resources.displayMetrics.density).toInt()
                layoutParams = lp
            }
            val edit = TextInputEditText(ctx)
            layout.addView(edit)
            return layout to edit
        }

        val (baseUrlLayout2, baseUrlEdit) = makeInputLayout("Base URL")
        baseUrlEdit.setText(config.baseUrl)
        container.addView(baseUrlLayout2)

        val (apiKeyLayout2, apiKeyEdit) = makeInputLayout("API Key")
        apiKeyEdit.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        apiKeyLayout2.endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
        apiKeyEdit.setText(if (config.apiKey == "EMPTY") "" else config.apiKey)
        container.addView(apiKeyLayout2)

        val (modelNameLayout2, modelNameEdit) = makeInputLayout("模型名称 (Model Name)")
        modelNameEdit.setText(config.modelName)
        container.addView(modelNameLayout2)

        val dp8 = (8 * resources.displayMetrics.density).toInt()

        // Test connection button
        val btnTestConn = Button(ctx).apply {
            text = getString(R.string.settings_test_connection)
            val lp = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            lp.bottomMargin = dp8
            layoutParams = lp
        }
        container.addView(btnTestConn)

        val (maxTokensLayout2, maxTokensEdit) = makeInputLayout("最大 Token 数 (Max Tokens)")
        maxTokensEdit.setText(config.maxTokens.toString())
        maxTokensEdit.inputType = android.text.InputType.TYPE_CLASS_NUMBER
        container.addView(maxTokensLayout2)

        val (temperatureLayout2, temperatureEdit) = makeInputLayout("Temperature (0.0 - 2.0)")
        temperatureEdit.setText(config.temperature.toString())
        temperatureEdit.inputType = android.text.InputType.TYPE_CLASS_NUMBER or
            android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        container.addView(temperatureLayout2)

        val (planningStepsLayout2, planningStepsEdit) = makeInputLayout("最大规划步数 (Max Planning Steps)")
        planningStepsEdit.setText(config.maxPlanningSteps.toString())
        planningStepsEdit.inputType = android.text.InputType.TYPE_CLASS_NUMBER
        container.addView(planningStepsLayout2)

        // Language radio group
        val dp4 = (4 * resources.displayMetrics.density).toInt()
        val langLabel = TextView(ctx).apply {
            text = "语言 / Language"
            val lp = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            lp.topMargin = dp4
            layoutParams = lp
        }
        container.addView(langLabel)

        val langGroup = RadioGroup(ctx).apply {
            orientation = RadioGroup.HORIZONTAL
        }
        val langCn = RadioButton(ctx).apply { text = "中文" }
        val langEn = RadioButton(ctx).apply { text = "English" }
        langGroup.addView(langCn)
        langGroup.addView(langEn)
        if (config.language.lowercase() == "en") langEn.isChecked = true else langCn.isChecked = true
        container.addView(langGroup)

        // Custom system prompt button
        val btnCustomPrompt = Button(ctx).apply {
            text = "编辑自定义 System Prompt"
            val lp = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            lp.topMargin = dp8
            layoutParams = lp
        }
        container.addView(btnCustomPrompt)

        val dialog = MaterialAlertDialogBuilder(ctx)
            .setTitle("LLM-agent 设置（大脑）")
            .setView(scrollView)
            .setPositiveButton("保存") { _, _ ->
                val baseUrl = baseUrlEdit.text?.toString()?.trim() ?: ""
                val apiKey = apiKeyEdit.text?.toString()?.trim().let {
                    if (it.isNullOrEmpty()) "EMPTY" else it
                }
                val modelName = modelNameEdit.text?.toString()?.trim() ?: ""
                val maxTokens = maxTokensEdit.text?.toString()?.trim()?.toIntOrNull()
                    ?: config.maxTokens
                val temperature = temperatureEdit.text?.toString()?.trim()?.toFloatOrNull()
                    ?: config.temperature
                val maxPlanningSteps = planningStepsEdit.text?.toString()?.trim()?.toIntOrNull()
                    ?: config.maxPlanningSteps
                val language = if (langEn.isChecked) "en" else "cn"

                if (baseUrl.isEmpty() || !isValidUrl(baseUrl)) {
                    Toast.makeText(ctx, "Base URL 格式不正确", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (modelName.isEmpty()) {
                    Toast.makeText(ctx, "模型名称不能为空", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val newConfig = LLMAgentConfig(
                    baseUrl = baseUrl,
                    apiKey = apiKey,
                    modelName = modelName,
                    maxTokens = maxTokens,
                    temperature = temperature.coerceIn(0f, 2f),
                    maxPlanningSteps = maxPlanningSteps,
                    language = language,
                    customSystemPrompt = settingsManager.getLLMAgentCustomPrompt(language) ?: "",
                )
                settingsManager.saveLLMAgentConfig(newConfig)
                // Reinitialize so the new config takes effect immediately without an app restart.
                com.flowmate.autoxiaoer.ComponentManager.getInstance(ctx).reinitializeAgents()
                Toast.makeText(ctx, "LLM-agent 设置已保存", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .create()

        btnTestConn.setOnClickListener {
            testConnectionInDialog(
                baseUrl = baseUrlEdit.text?.toString()?.trim() ?: "",
                apiKey = apiKeyEdit.text?.toString()?.trim() ?: "",
                modelName = modelNameEdit.text?.toString()?.trim() ?: "",
                testButton = btnTestConn,
            )
        }

        btnCustomPrompt.setOnClickListener {
            val language = if (langEn.isChecked) "en" else "cn"
            showLLMAgentPromptDialog(language)
        }

        dialog.show()
        dialog.applyPrimaryButtonColors()
    }

    /**
     * Shows an edit dialog for the LLM-agent's custom system prompt.
     */
    private fun showLLMAgentPromptDialog(language: String) {
        Logger.d(TAG, "Showing LLM-agent prompt dialog for language: $language")
        val ctx = requireContext()
        val dialogView = LayoutInflater.from(ctx).inflate(R.layout.dialog_system_prompt, null)
        val promptInput = dialogView.findViewById<TextInputEditText>(R.id.promptInput)
        val btnReset = dialogView.findViewById<Button>(R.id.btnResetPrompt)

        val currentPrompt = settingsManager.getLLMAgentCustomPrompt(language)
            ?: if (language == "en") {
                LLMAgentPrompts.getDefaultEnglishPromptTemplate()
            } else {
                LLMAgentPrompts.getDefaultChinesePromptTemplate()
            }
        promptInput.setText(currentPrompt)

        val title = if (language == "en") {
            "LLM-agent System Prompt (EN)"
        } else {
            "LLM-agent System Prompt（中文）"
        }

        val dialog = MaterialAlertDialogBuilder(ctx)
            .setTitle(title)
            .setView(dialogView)
            .setPositiveButton("保存") { _, _ ->
                val newPrompt = promptInput.text?.toString() ?: ""
                if (newPrompt.isNotBlank()) {
                    settingsManager.saveLLMAgentCustomPrompt(language, newPrompt)
                    if (language == "en") {
                        LLMAgentPrompts.setCustomEnglishPrompt(newPrompt)
                    } else {
                        LLMAgentPrompts.setCustomChinesePrompt(newPrompt)
                    }
                    Toast.makeText(ctx, "LLM-agent System Prompt 已保存", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .create()

        btnReset.setOnClickListener {
            MaterialAlertDialogBuilder(ctx)
                .setTitle("重置为默认")
                .setMessage("确定要恢复默认的 LLM-agent System Prompt 吗？")
                .setPositiveButton("确定") { _, _ ->
                    settingsManager.clearLLMAgentCustomPrompt(language)
                    if (language == "en") {
                        LLMAgentPrompts.setCustomEnglishPrompt(null)
                    } else {
                        LLMAgentPrompts.setCustomChinesePrompt(null)
                    }
                    Toast.makeText(ctx, "已恢复默认 System Prompt", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
                .setNegativeButton("取消", null)
                .showWithPrimaryButtons()
        }

        dialog.show()
        dialog.applyPrimaryButtonColors()
    }

    // endregion

    companion object {
        private const val TAG = "SettingsFragment"
        private const val SHIZUKU_PERMISSION_REQUEST_CODE = 1001
    }
}
