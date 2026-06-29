package com.flowmate.autoxiaoer.device

/**
 * Represents the backend used for device control operations.
 *
 * - [SHIZUKU]: Shell-command based control via Shizuku/ADB. Requires the user to
 *   install and activate the Shizuku app. Supports all key events including
 *   volume and power.
 * - [ACCESSIBILITY]: Gesture-injection based control via Android AccessibilityService.
 *   No extra app required; the user enables the service in system Settings.
 *   Volume key injection is not supported; Power maps to lock-screen action.
 */
enum class InputBackend {
    SHIZUKU,
    ACCESSIBILITY,
}
