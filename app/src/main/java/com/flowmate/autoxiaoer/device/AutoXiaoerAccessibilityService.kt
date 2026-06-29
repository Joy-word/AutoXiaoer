package com.flowmate.autoxiaoer.device

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.flowmate.autoxiaoer.ComponentManager
import com.flowmate.autoxiaoer.util.Logger

/**
 * Accessibility service that acts as the execution engine for the ACCESSIBILITY backend.
 *
 * This service provides gesture-injection and window-content access capabilities
 * without requiring Shizuku or ADB. Users must enable it once in system Settings →
 * Accessibility → Auto小二.
 *
 * The service keeps a static [instance] reference so that [AccessibilityDeviceExecutor]
 * and [AccessibilityTextInputManager] can invoke methods on it directly. The reference
 * is cleared when the system unbinds the service.
 *
 * Configuration: res/xml/accessibility_service_config.xml
 *   - canPerformGestures = true  (required for tap/swipe injection)
 *   - canRetrieveWindowContent = true  (required for ACTION_SET_TEXT + getCurrentApp)
 *   - canTakeScreenshot = true  (declared for API 30+; the system enforces API level anyway)
 *   - accessibilityEventTypes = typeWindowStateChanged  (minimal, only to track foreground app)
 */
class AutoXiaoerAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "A11yService"

        /**
         * Singleton reference set when the service is bound by the system.
         * Null when the service is not running.
         */
        @Volatile
        var instance: AutoXiaoerAccessibilityService? = null
            private set

        /** Returns true if the service is currently bound and active. */
        fun isConnected(): Boolean = instance != null
    }

    /** Package name of the last foregrounded app, tracked via window-state events. */
    @Volatile
    var foregroundPackage: String? = null
        private set

    // region Lifecycle

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Logger.i(TAG, "AccessibilityService connected")
        // Notify ComponentManager so it can initialise the accessibility-backend components.
        try {
            ComponentManager.getInstance(applicationContext).onAccessibilityServiceConnected()
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to notify ComponentManager", e)
        }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Logger.i(TAG, "AccessibilityService unbound")
        instance = null
        foregroundPackage = null
        try {
            ComponentManager.getInstance(applicationContext).onAccessibilityServiceDisconnected()
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to notify ComponentManager on unbind", e)
        }
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        Logger.i(TAG, "AccessibilityService destroyed")
        instance = null
        foregroundPackage = null
        super.onDestroy()
    }

    // endregion

    // region Event handling

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString()
            if (!pkg.isNullOrEmpty()) {
                foregroundPackage = pkg
            }
        }
    }

    override fun onInterrupt() {
        Logger.w(TAG, "AccessibilityService interrupted")
    }

    // endregion
}
