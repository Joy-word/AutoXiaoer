package com.flowmate.autoxiaoer.config

/**
 * Internationalization (i18n) module for Phone Agent UI messages.
 *
 * This object provides localized strings for the application's user interface,
 * supporting both Chinese and English languages. It serves as a centralized
 * repository for all UI text, ensuring consistent translations across the app.
 *
 * Usage:
 * ```kotlin
 * // Get a single message
 * val text = I18n.getMessage("loading", "cn")
 *
 * // Get all messages for a language
 * val messages = I18n.getMessages("en")
 *
 * // Get formatted message with arguments
 * val formatted = I18n.getFormattedMessage("step", "cn", 1)
 * ```
 *
 */
object I18n {
    /**
     * Chinese UI messages map.
     *
     * Contains all localized strings for the Chinese language interface.
     * Keys are message identifiers used throughout the application.
     */
    private val MESSAGES_ZH =
        mapOf(
            "thinking" to "思考过程",
            "action" to "执行动作",
            "task_completed" to "任务完成",
            "done" to "完成",
            "starting_task" to "开始执行任务",
            "final_result" to "最终结果",
            "task_result" to "任务结果",
            "confirmation_required" to "需要确认",
            "continue_prompt" to "是否继续？",
            "manual_operation_required" to "需要人工操作",
            "manual_operation_hint" to "请手动完成操作...",
            "press_enter_when_done" to "完成后点击继续",
            "connection_failed" to "连接失败",
            "connection_successful" to "连接成功",
            "step" to "步骤",
            "task" to "任务",
            "result" to "结果",
            "performance_metrics" to "性能指标",
            "time_to_first_token" to "首 Token 延迟 (TTFT)",
            "time_to_thinking_end" to "思考完成延迟",
            "total_inference_time" to "总推理时间",
            "cancel" to "取消",
            "confirm" to "确认",
            "yes" to "是",
            "no" to "否",
            "error" to "错误",
            "warning" to "警告",
            "info" to "信息",
            "loading" to "加载中...",
            "capturing_screenshot" to "正在截图...",
            "executing_action" to "正在执行操作...",
            "waiting_for_model" to "等待模型响应...",
            "task_cancelled" to "任务已取消",
            "step_failed" to "步骤执行失败",
            "max_steps_reached_with_count" to "最大步数（%d）已达到",
            "task_failed" to "任务失败",
            "max_steps_reached" to "已达到最大步数",
            "empty_task_error" to "任务描述不能为空",
            "task_already_running" to "已有任务正在运行",
            "shizuku_not_available" to "Shizuku 服务不可用",
            "permission_denied" to "权限被拒绝",
            "network_error" to "网络错误",
            "timeout_error" to "请求超时",
            "parse_error" to "解析错误",
            "unknown_error" to "未知错误",
            "app_not_found" to "未找到应用",
            "sensitive_operation" to "敏感操作",
            "sensitive_operation_hint" to "此操作可能涉及支付或隐私，是否继续？",
            "takeover_title" to "需要用户接管",
            "takeover_hint" to "请手动完成以下操作，完成后点击继续",
            "interact_title" to "请选择",
            "interact_hint" to "请从以下选项中选择一个",
            "settings" to "设置",
            "api_settings" to "API 设置",
            "agent_settings" to "代理设置",
            "base_url" to "API 地址",
            "api_key" to "API 密钥",
            "model_name" to "模型名称",
            "max_steps" to "最大步数",
            "language" to "语言",
            "chinese" to "中文",
            "english" to "英文",
            "save" to "保存",
            "reset" to "重置",
            "start" to "开始",
            "stop" to "停止",
            "status_idle" to "空闲",
            "status_running" to "运行中",
            "status_completed" to "已完成",
            "status_failed" to "失败",
            "status_cancelled" to "已取消",
            "status_waiting" to "等待中",
            "action_tap" to "点击 (%d, %d)",
            "action_swipe" to "滑动 从(%d, %d) 到(%d, %d)",
            "action_type" to "输入: \"%s\"",
            "action_type_name" to "输入名称: \"%s\"",
            "action_launch" to "启动: %s",
            "action_list_apps" to "列出已安装应用",
            "action_back" to "返回",
            "action_home" to "主页",
            "action_volume_up" to "音量+",
            "action_volume_down" to "音量-",
            "action_power" to "电源键",
            "action_long_press" to "长按 (%d, %d)",
            "action_double_tap" to "双击 (%d, %d)",
            "action_wait" to "等待 %s秒",
            "action_takeover" to "手动接管",
            "action_interact" to "用户交互",
            "action_note" to "备注: %s",
            "action_call_api" to "API调用",
            "action_finish" to "完成: %s",
            "action_batch" to "批量操作: %d步 (间隔%dms)",
            "action_cancelled" to "点击已被用户取消",
            "action_tap_failed" to "点击失败: %s",
            "action_tap_success" to "点击 (%d, %d)",
            "action_swipe_failed" to "滑动失败: %s",
            "action_swipe_success" to "滑动 从(%d, %d) 到(%d, %d)",
            "action_type_name_result" to "输入名称: %s",
            "action_launch_success" to "启动应用: %s",
            "action_launch_failed_hint" to "启动应用'%s'失败，已返回主屏幕。请在主屏幕或应用列表中查找并点击'%s'应用图标来启动它。",
            "action_app_package_not_found_hint" to "找不到应用包名'%s'，已返回主屏幕。请在主屏幕或应用列表中查找并点击'%s'应用图标来启动它。如果主屏幕没有，请上滑打开应用列表查找。",
            "action_app_not_found" to "未找到已安装的应用",
            "action_back_failed" to "返回键失败: %s",
            "action_home_failed" to "主页键失败: %s",
            "action_volume_up_failed" to "音量+键失败: %s",
            "action_volume_down_failed" to "音量-键失败: %s",
            "action_power_failed" to "电源键失败: %s",
            "action_long_press_failed" to "长按失败: %s",
            "action_long_press_success" to "长按 (%d, %d) %d毫秒",
            "action_double_tap_failed" to "双击失败: %s",
            "action_double_tap_success" to "双击 (%d, %d)",
            "action_wait_result" to "等待 %s秒",
            "action_takeover_result" to "请求手动接管: %s",
            "action_user_selected" to "用户选择: %s",
            "action_interaction_cancelled" to "交互已取消",
            "action_note_result" to "备注: %s",
            "action_api_result" to "API调用: %s",
            "action_batch_step" to "第%d步: %s",
            "action_batch_summary" to "批量操作完成: %d步，%s",
            "action_batch_all_succeeded" to "全部成功",
            "action_batch_some_failed" to "部分失败",
            "phone_empty_task" to "任务描述不能为空或只能包含空白字符",
            "phone_task_already_running" to "已有任务正在运行，请等待当前任务完成或先取消它",
            "phone_context_not_initialized" to "执行上下文未初始化",
            "phone_first_task_required" to "第一步需要提供任务描述",
            "phone_step_already_running" to "已有任务正在运行",
            "phone_step_error" to "步骤执行错误: %s",
            "task_completed_fallback" to "任务完成",
            "phone_no_action" to "模型响应中没有操作（已重试%d次）",
            "phone_model_error" to "模型错误",
            "phone_coordinate_error_label" to "坐标越界: %s",
            "phone_parse_error_label" to "解析错误: %s",
            "phone_task_cancelled" to "任务已被用户取消",
            "phone_task_paused" to "任务已暂停",
            "task_cancelled" to "任务已取消",
        )

    /**
     * English UI messages map.
     *
     * Contains all localized strings for the English language interface.
     * Keys are message identifiers used throughout the application.
     */
    private val MESSAGES_EN =
        mapOf(
            "thinking" to "Thinking",
            "action" to "Action",
            "task_completed" to "Task Completed",
            "done" to "Done",
            "starting_task" to "Starting task",
            "final_result" to "Final Result",
            "task_result" to "Task Result",
            "confirmation_required" to "Confirmation Required",
            "continue_prompt" to "Continue?",
            "manual_operation_required" to "Manual Operation Required",
            "manual_operation_hint" to "Please complete the operation manually...",
            "press_enter_when_done" to "Tap continue when done",
            "connection_failed" to "Connection Failed",
            "connection_successful" to "Connection Successful",
            "step" to "Step",
            "task" to "Task",
            "result" to "Result",
            "performance_metrics" to "Performance Metrics",
            "time_to_first_token" to "Time to First Token (TTFT)",
            "time_to_thinking_end" to "Time to Thinking End",
            "total_inference_time" to "Total Inference Time",
            "cancel" to "Cancel",
            "confirm" to "Confirm",
            "yes" to "Yes",
            "no" to "No",
            "error" to "Error",
            "warning" to "Warning",
            "info" to "Info",
            "loading" to "Loading...",
            "capturing_screenshot" to "Capturing screenshot...",
            "executing_action" to "Executing action...",
            "waiting_for_model" to "Waiting for model response...",
            "task_cancelled" to "Task cancelled",
            "step_failed" to "Step execution failed",
            "max_steps_reached_with_count" to "Maximum steps (%d) reached",
            "task_failed" to "Task failed",
            "max_steps_reached" to "Maximum steps reached",
            "empty_task_error" to "Task description cannot be empty",
            "task_already_running" to "A task is already running",
            "shizuku_not_available" to "Shizuku service not available",
            "permission_denied" to "Permission denied",
            "network_error" to "Network error",
            "timeout_error" to "Request timeout",
            "parse_error" to "Parse error",
            "unknown_error" to "Unknown error",
            "app_not_found" to "App not found",
            "sensitive_operation" to "Sensitive Operation",
            "sensitive_operation_hint" to "This operation may involve payment or privacy. Continue?",
            "takeover_title" to "User Takeover Required",
            "takeover_hint" to "Please complete the following operation manually, then tap continue",
            "interact_title" to "Please Select",
            "interact_hint" to "Please select one of the following options",
            "settings" to "Settings",
            "api_settings" to "API Settings",
            "agent_settings" to "Agent Settings",
            "base_url" to "API Base URL",
            "api_key" to "API Key",
            "model_name" to "Model Name",
            "max_steps" to "Max Steps",
            "language" to "Language",
            "chinese" to "Chinese",
            "english" to "English",
            "save" to "Save",
            "reset" to "Reset",
            "start" to "Start",
            "stop" to "Stop",
            "status_idle" to "Idle",
            "status_running" to "Running",
            "status_completed" to "Completed",
            "status_failed" to "Failed",
            "status_cancelled" to "Cancelled",
            "status_waiting" to "Waiting",
            "action_tap" to "Tap (%d, %d)",
            "action_swipe" to "Swipe from (%d, %d) to (%d, %d)",
            "action_type" to "Type: \"%s\"",
            "action_type_name" to "Type name: \"%s\"",
            "action_launch" to "Launch: %s",
            "action_list_apps" to "List installed apps",
            "action_back" to "Back",
            "action_home" to "Home",
            "action_volume_up" to "Volume up",
            "action_volume_down" to "Volume down",
            "action_power" to "Power",
            "action_long_press" to "Long press (%d, %d)",
            "action_double_tap" to "Double tap (%d, %d)",
            "action_wait" to "Wait %ss",
            "action_takeover" to "Manual takeover",
            "action_interact" to "User interaction",
            "action_note" to "Note: %s",
            "action_call_api" to "API call",
            "action_finish" to "Finish: %s",
            "action_batch" to "Batch actions: %d steps (%dms interval)",
            "action_cancelled" to "Tap cancelled by user",
            "action_tap_failed" to "Tap failed: %s",
            "action_tap_success" to "Tap (%d, %d)",
            "action_swipe_failed" to "Swipe failed: %s",
            "action_swipe_success" to "Swipe from (%d, %d) to (%d, %d)",
            "action_type_name_result" to "Type name: %s",
            "action_launch_success" to "Launch app: %s",
            "action_launch_failed_hint" to "Failed to launch '%s' and returned to the home screen. Find and tap the '%s' app icon on the home screen or app list.",
            "action_app_package_not_found_hint" to "App package for '%s' was not found and the home screen was opened. Find and tap the '%s' app icon on the home screen or app list.",
            "action_app_not_found" to "No installed app found",
            "action_back_failed" to "Back key failed: %s",
            "action_home_failed" to "Home key failed: %s",
            "action_volume_up_failed" to "Volume up key failed: %s",
            "action_volume_down_failed" to "Volume down key failed: %s",
            "action_power_failed" to "Power key failed: %s",
            "action_long_press_failed" to "Long press failed: %s",
            "action_long_press_success" to "Long press (%d, %d) %dms",
            "action_double_tap_failed" to "Double tap failed: %s",
            "action_double_tap_success" to "Double tap (%d, %d)",
            "action_wait_result" to "Wait %ss",
            "action_takeover_result" to "Manual takeover requested: %s",
            "action_user_selected" to "User selected: %s",
            "action_interaction_cancelled" to "Interaction cancelled",
            "action_note_result" to "Note: %s",
            "action_api_result" to "API call: %s",
            "action_batch_step" to "Step %d: %s",
            "action_batch_summary" to "Batch completed: %d steps, %s",
            "action_batch_all_succeeded" to "all succeeded",
            "action_batch_some_failed" to "some failed",
            "phone_empty_task" to "Task description cannot be empty or whitespace only",
            "phone_task_already_running" to "A task is already running. Please wait or cancel it first.",
            "phone_context_not_initialized" to "Agent context not initialized",
            "phone_first_task_required" to "Task description required for first step",
            "phone_step_already_running" to "A task is already running",
            "phone_step_error" to "Step execution error: %s",
            "task_completed_fallback" to "Task completed",
            "phone_no_action" to "No action in model response (retried %d times)",
            "phone_model_error" to "Model error",
            "phone_coordinate_error_label" to "Coordinate out of range: %s",
            "phone_parse_error_label" to "Parse error: %s",
            "phone_task_cancelled" to "Task cancelled by user",
            "phone_task_paused" to "Task paused",
            "task_cancelled" to "Task cancelled",
        )

    private val messagesByLanguage = mapOf(
        "cn" to MESSAGES_ZH,
        "zh" to MESSAGES_ZH,
        "chinese" to MESSAGES_ZH,
        "en" to MESSAGES_EN,
        "english" to MESSAGES_EN,
    )

    /**
     * Gets all UI messages for the specified language.
     *
     * Returns the complete message map for the requested language.
     * This is useful when you need to access multiple messages at once
     * or pass the entire message set to a UI component.
     *
     * @param language Language code: "cn" for Chinese, "en" or "english" for English.
     *                 Defaults to Chinese for unrecognized codes.
     * @return Map of message keys to localized strings for the specified language
     */
    fun getMessages(language: String): Map<String, String> =
        messagesByLanguage[language.lowercase()] ?: MESSAGES_ZH

    /**
     * Gets a single UI message by key and language.
     *
     * Retrieves a specific localized message string. If the key is not found
     * in the message map, the key itself is returned as a fallback.
     *
     * @param key Message key (e.g., "loading", "error", "confirm")
     * @param language Language code: "cn" for Chinese, "en" or "english" for English.
     *                 Defaults to Chinese for unrecognized codes.
     * @return Localized message string, or the key itself if not found
     */
    fun getMessage(key: String, language: String): String {
        val messages = getMessages(language)
        return messages[key] ?: key
    }

    /**
     * Gets a formatted message with placeholders replaced.
     *
     * Retrieves a localized message and formats it using [String.format] with
     * the provided arguments. Supports standard format specifiers like %s, %d, etc.
     *
     * If formatting fails (e.g., wrong number of arguments), returns the
     * unformatted template string.
     *
     * @param key Message key
     * @param language Language code: "cn" for Chinese, "en" or "english" for English
     * @param args Arguments to replace placeholders (%s, %d, etc.) in the message
     * @return Formatted localized message string
     */
    fun getFormattedMessage(key: String, language: String, vararg args: Any): String {
        val template = getMessage(key, language)
        return try {
            String.format(template, *args)
        } catch (e: Exception) {
            template
        }
    }

    /**
     * Supported language codes and utilities.
     *
     * This object provides constants for language codes and helper methods
     * for working with supported languages.
     */
    object Languages {
        /** Language code for Simplified Chinese. */
        const val CHINESE = "cn"

        /** Language code for English. */
        const val ENGLISH = "en"

        /** List of all supported language codes. */
        val ALL = listOf(CHINESE, ENGLISH)

        /**
         * Gets the display name for a language code.
         *
         * Returns the localized name of the language (e.g., "中文" or "Chinese")
         * in the specified display language.
         *
         * @param code The language code to get the display name for
         * @param inLanguage The language to display the name in (defaults to the same language)
         * @return The localized display name, or the code itself if not recognized
         */
        fun getDisplayName(code: String, inLanguage: String = code): String = when (code.lowercase()) {
            CHINESE -> getMessage("chinese", inLanguage)
            ENGLISH -> getMessage("english", inLanguage)
            else -> code
        }
    }
}
