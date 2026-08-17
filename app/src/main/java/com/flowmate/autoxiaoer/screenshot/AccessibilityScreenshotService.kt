package com.flowmate.autoxiaoer.screenshot

import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Build
import android.util.Base64
import androidx.annotation.RequiresApi
import com.flowmate.autoxiaoer.ui.FloatingWindowService
import com.flowmate.autoxiaoer.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * [IScreenshotService] implementation for API 30+ that uses
 * [android.accessibilityservice.AccessibilityService.takeScreenshot].
 *
 * Requires [AutoXiaoerAccessibilityService] to be connected.
 */
@RequiresApi(Build.VERSION_CODES.R)
class AccessibilityScreenshotService(
    private val floatingWindowControllerProvider: () -> FloatingWindowController? = { null },
) : IScreenshotService {

    companion object {
        private const val TAG = "A11yScreenshotService"
        private const val HIDE_DELAY_MS = 200L
        private const val SHOW_DELAY_MS = 100L
        private const val WEBP_QUALITY = 65
        private const val MAX_WIDTH = 720
        private const val MAX_HEIGHT = 1280

        // System enforces a minimum interval between takeScreenshot() calls; calling it
        // sooner fails with ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT. Retry after backing off.
        private const val ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT = 3
        private const val RETRY_DELAY_MS = 600L
        private const val MAX_RETRIES = 3

        // Some devices/emulators (e.g. MuMu) fail to populate the HardwareBuffer content,
        // yielding a technically "successful" but blank/black bitmap. Detect and retry those.
        private const val BLANK_RETRY_DELAY_MS = 400L
        private const val MAX_BLANK_RETRIES = 3
        private const val BLANK_SAMPLE_STEP = 17
        private const val BLANK_VARIANCE_THRESHOLD = 4
    }

    // Tracks the last takeScreenshot() call so we can pre-emptively wait out the
    // system's minimum interval instead of relying purely on reactive retries.
    @Volatile
    private var lastCaptureAtMs: Long = 0L

    override suspend fun capture(): Screenshot = withContext(Dispatchers.Main) {
        val controller = floatingWindowControllerProvider()
        controller?.hide()
        if (controller != null) delay(HIDE_DELAY_MS)

        try {
            val service = com.flowmate.autoxiaoer.device.AutoXiaoerAccessibilityService.instance
                ?: return@withContext createFallback()

            val elapsed = System.currentTimeMillis() - lastCaptureAtMs
            if (elapsed in 0 until RETRY_DELAY_MS) delay(RETRY_DELAY_MS - elapsed)

            var screenshot: Screenshot? = null
            var attempt = 0
            while (screenshot == null && attempt <= MAX_RETRIES) {
                if (attempt > 0) delay(RETRY_DELAY_MS)
                screenshot = suspendTakeScreenshot(service)
                attempt++
            }
            lastCaptureAtMs = System.currentTimeMillis()

            screenshot = screenshot?.let { retryIfBlank(service, it) }

            screenshot ?: run {
                Logger.e(TAG, "Accessibility screenshot failed after $MAX_RETRIES retries")
                createFallback()
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Accessibility screenshot failed", e)
            createFallback()
        } finally {
            if (controller != null) {
                delay(SHOW_DELAY_MS)
                withContext(Dispatchers.Main) { controller.showAndBringToFront() }
            }
        }
    }

    /**
     * Re-captures if the bitmap looks blank (HardwareBuffer content not populated),
     * which the model would otherwise misread as a "sensitive/black screen".
     */
    private suspend fun retryIfBlank(
        service: com.flowmate.autoxiaoer.device.AutoXiaoerAccessibilityService,
        initial: Screenshot,
    ): Screenshot? {
        var current = initial
        var attempt = 0
        while (isLikelyBlank(current) && attempt < MAX_BLANK_RETRIES) {
            Logger.w(TAG, "Screenshot looks blank, retrying (attempt ${attempt + 1}/$MAX_BLANK_RETRIES)")
            delay(BLANK_RETRY_DELAY_MS)
            current = suspendTakeScreenshot(service) ?: return current
            attempt++
        }
        return current
    }

    private fun isLikelyBlank(screenshot: Screenshot): Boolean {
        if (screenshot.base64Data.isEmpty()) return false
        val bytes = Base64.decode(screenshot.base64Data, Base64.NO_WRAP)
        val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return false
        return try {
            var sum = 0L
            var sumSq = 0L
            var count = 0
            var y = 0
            while (y < bitmap.height) {
                var x = 0
                while (x < bitmap.width) {
                    val pixel = bitmap.getPixel(x, y)
                    val luma = (android.graphics.Color.red(pixel) + android.graphics.Color.green(pixel) + android.graphics.Color.blue(pixel)) / 3
                    sum += luma
                    sumSq += luma.toLong() * luma
                    count++
                    x += BLANK_SAMPLE_STEP
                }
                y += BLANK_SAMPLE_STEP
            }
            if (count == 0) return false
            val mean = sum.toDouble() / count
            val variance = (sumSq.toDouble() / count) - (mean * mean)
            variance < BLANK_VARIANCE_THRESHOLD * BLANK_VARIANCE_THRESHOLD
        } finally {
            bitmap.recycle()
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun suspendTakeScreenshot(
        service: com.flowmate.autoxiaoer.device.AutoXiaoerAccessibilityService,
    ): Screenshot? = kotlinx.coroutines.suspendCancellableCoroutine { cont ->
        service.takeScreenshot(
            android.view.Display.DEFAULT_DISPLAY,
            service.mainExecutor,
            object : android.accessibilityservice.AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(result: android.accessibilityservice.AccessibilityService.ScreenshotResult) {
                    val hardwareBuffer = result.hardwareBuffer
                    try {
                        val hardwareBitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, result.colorSpace)
                        val softBitmap = hardwareBitmap?.copy(Bitmap.Config.ARGB_8888, false)
                        hardwareBitmap?.recycle()
                        if (softBitmap == null) {
                            Logger.w(TAG, "Failed to wrap hardware buffer as bitmap")
                            if (cont.isActive) cont.resume(null) {}
                            return
                        }
                        val screenshot = bitmapToScreenshot(softBitmap)
                        softBitmap.recycle()
                        if (cont.isActive) cont.resume(screenshot) {}
                    } catch (e: Exception) {
                        Logger.e(TAG, "Bitmap conversion failed", e)
                        if (cont.isActive) cont.resume(null) {}
                    } finally {
                        hardwareBuffer.close()
                    }
                }

                override fun onFailure(errorCode: Int) {
                    val reason = if (errorCode == ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT) {
                        "called too soon after previous screenshot"
                    } else {
                        "errorCode=$errorCode"
                    }
                    Logger.w(TAG, "takeScreenshot failed: $reason")
                    if (cont.isActive) cont.resume(null) {}
                }
            },
        )
    }

    private fun bitmapToScreenshot(bitmap: Bitmap): Screenshot {
        val originalWidth = bitmap.width
        val originalHeight = bitmap.height
        val (sw, sh) = calcDimensions(originalWidth, originalHeight)
        val scaled = if (sw != originalWidth || sh != originalHeight) {
            Bitmap.createScaledBitmap(bitmap, sw, sh, true).also { bitmap.recycle() }
        } else bitmap

        @Suppress("DEPRECATION")
        val fmt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            Bitmap.CompressFormat.WEBP
        }
        val baos = ByteArrayOutputStream()
        scaled.compress(fmt, WEBP_QUALITY, baos)
        scaled.recycle()
        val b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
        return Screenshot(b64, sw, sh, originalWidth, originalHeight)
    }

    private fun calcDimensions(w: Int, h: Int): Pair<Int, Int> {
        if (w <= MAX_WIDTH && h <= MAX_HEIGHT) return w to h
        val ratio = minOf(MAX_WIDTH.toFloat() / w, MAX_HEIGHT.toFloat() / h)
        return (w * ratio).toInt() to (h * ratio).toInt()
    }

    private fun createFallback() = Screenshot(
        base64Data = "",
        width = MAX_WIDTH,
        height = MAX_HEIGHT,
    )
}

/**
 * [IScreenshotService] implementation for devices below API 30 using [MediaProjection].
 *
 * Requires a MediaProjection token obtained via the permission request flow in
 * [ScreenshotProjectionService]. After receiving the token, call [setMediaProjection]
 * before [capture].
 */
class MediaProjectionScreenshotService(
    private val floatingWindowControllerProvider: () -> FloatingWindowController? = { null },
) : IScreenshotService {

    companion object {
        private const val TAG = "MPScreenshotService"
        private const val HIDE_DELAY_MS = 200L
        private const val SHOW_DELAY_MS = 100L
        private const val WEBP_QUALITY = 65
        private const val MAX_WIDTH = 720
        private const val MAX_HEIGHT = 1280
        private const val IMAGE_SETTLE_MS = 150L
    }

    @Volatile
    private var mediaProjection: MediaProjection? = null

    @Volatile
    private var imageReader: ImageReader? = null

    @Volatile
    private var virtualDisplay: VirtualDisplay? = null

    @Volatile
    private var screenWidth: Int = 1080

    @Volatile
    private var screenHeight: Int = 1920

    @Volatile
    private var density: Int = 480

    /** Must be called after the MediaProjection permission is granted. */
    fun setMediaProjection(mp: MediaProjection, width: Int, height: Int, densityDpi: Int) {
        release()
        screenWidth = width
        screenHeight = height
        density = densityDpi
        mediaProjection = mp

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        virtualDisplay = mp.createVirtualDisplay(
            "AutoXiaoerCapture",
            width,
            height,
            densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader!!.surface,
            null,
            null,
        )
    }

    override suspend fun capture(): Screenshot = withContext(Dispatchers.IO) {
        val mp = mediaProjection
        val reader = imageReader
        if (mp == null || reader == null) {
            Logger.w(TAG, "MediaProjection not initialised, returning fallback")
            return@withContext createFallback()
        }

        val controller = floatingWindowControllerProvider()
        withContext(Dispatchers.Main) { controller?.hide() }
        if (controller != null) delay(HIDE_DELAY_MS)

        try {
            delay(IMAGE_SETTLE_MS)
            val image: Image? = reader.acquireLatestImage()
            if (image == null) {
                Logger.w(TAG, "acquireLatestImage returned null")
                return@withContext createFallback()
            }
            val screenshot = imageToScreenshot(image)
            image.close()
            screenshot
        } catch (e: Exception) {
            Logger.e(TAG, "MediaProjection screenshot failed", e)
            createFallback()
        } finally {
            if (controller != null) {
                delay(SHOW_DELAY_MS)
                withContext(Dispatchers.Main) { controller?.showAndBringToFront() }
            }
        }
    }

    private fun imageToScreenshot(image: Image): Screenshot {
        val planes = image.planes
        val buffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride * screenWidth

        val bitmap = Bitmap.createBitmap(
            screenWidth + rowPadding / pixelStride,
            screenHeight,
            Bitmap.Config.ARGB_8888,
        )
        bitmap.copyPixelsFromBuffer(buffer)

        // Crop any row padding
        val cropped = Bitmap.createBitmap(bitmap, 0, 0, screenWidth, screenHeight)
        bitmap.recycle()

        val (sw, sh) = calcDimensions(screenWidth, screenHeight)
        val scaled = if (sw != screenWidth || sh != screenHeight) {
            Bitmap.createScaledBitmap(cropped, sw, sh, true).also { cropped.recycle() }
        } else cropped

        @Suppress("DEPRECATION")
        val fmt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            Bitmap.CompressFormat.WEBP
        }
        val baos = ByteArrayOutputStream()
        scaled.compress(fmt, WEBP_QUALITY, baos)
        scaled.recycle()

        val b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
        return Screenshot(b64, sw, sh, screenWidth, screenHeight)
    }

    private fun calcDimensions(w: Int, h: Int): Pair<Int, Int> {
        if (w <= MAX_WIDTH && h <= MAX_HEIGHT) return w to h
        val ratio = minOf(MAX_WIDTH.toFloat() / w, MAX_HEIGHT.toFloat() / h)
        return (w * ratio).toInt() to (h * ratio).toInt()
    }

    override fun release() {
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        mediaProjection?.stop()
        mediaProjection = null
    }

    private fun createFallback() = Screenshot(
        base64Data = "",
        width = MAX_WIDTH,
        height = MAX_HEIGHT,
    )
}
