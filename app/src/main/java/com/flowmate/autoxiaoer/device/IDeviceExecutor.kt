package com.flowmate.autoxiaoer.device

import com.flowmate.autoxiaoer.util.Point

/**
 * Abstraction over the device-control backend.
 *
 * Both [DeviceExecutor] (Shizuku shell) and [AccessibilityDeviceExecutor]
 * (AccessibilityService gesture injection) implement this interface so that
 * the rest of the code can remain backend-agnostic.
 */
interface IDeviceExecutor {

    /** Performs a tap at the given absolute pixel coordinates. */
    suspend fun tap(x: Int, y: Int): String

    /** Performs a double tap at the given absolute pixel coordinates. */
    suspend fun doubleTap(x: Int, y: Int): String

    /** Performs a long press at the given absolute pixel coordinates. */
    suspend fun longPress(x: Int, y: Int, durationMs: Int = DEFAULT_LONG_PRESS_DURATION_MS): String

    /**
     * Performs a swipe gesture through the given list of absolute pixel points.
     *
     * @param points  At least two points defining the path.
     * @param durationMs Total gesture duration in milliseconds.
     */
    suspend fun swipe(points: List<Point>, durationMs: Int): String

    /**
     * Sends a key event.
     *
     * Keycodes are the same Android constants used by [DeviceExecutor]:
     * [KEYCODE_BACK], [KEYCODE_HOME], [KEYCODE_POWER], etc.
     */
    suspend fun pressKey(keyCode: Int): String

    /**
     * Launches the app with the given package name.
     *
     * @return A result string describing success or failure.
     */
    suspend fun launchApp(packageName: String): String

    /**
     * Returns the package name of the currently foregrounded app,
     * or an empty string if it cannot be determined.
     */
    suspend fun getCurrentApp(): String

    companion object {
        const val DEFAULT_LONG_PRESS_DURATION_MS = 3000
    }
}
