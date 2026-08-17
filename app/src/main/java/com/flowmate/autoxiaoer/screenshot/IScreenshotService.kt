package com.flowmate.autoxiaoer.screenshot

/**
 * Abstraction over the screenshot backend.
 *
 * [ScreenshotService] (Shizuku screencap) and accessibility-based implementations
 * ([AccessibilityScreenshotService] for API 30+, [MediaProjectionScreenshotService]
 * for older devices) all implement this interface.
 */
interface IScreenshotService {

    /**
     * Captures the current screen.
     *
     * Implementations must hide the floating window before capture and restore it
     * afterward (following the same contract as [ScreenshotService]).
     *
     * @return [Screenshot] containing base64-encoded WebP image data and metadata.
     */
    suspend fun capture(): Screenshot

    /**
     * Releases any ongoing resources held by the service (e.g., MediaProjection,
     * VirtualDisplay, ImageReader). Safe to call multiple times.
     */
    fun release() { /* default no-op */ }
}
