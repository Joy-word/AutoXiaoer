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
    }

    override suspend fun capture(): Screenshot = withContext(Dispatchers.Main) {
        val controller = floatingWindowControllerProvider()
        controller?.hide()
        if (controller != null) delay(HIDE_DELAY_MS)

        try {
            val service = com.flowmate.autoxiaoer.device.AutoXiaoerAccessibilityService.instance
                ?: return@withContext createFallback()

            val screenshot = suspendTakeScreenshot(service)
                ?: return@withContext createFallback()

            screenshot
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

    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun suspendTakeScreenshot(
        service: com.flowmate.autoxiaoer.device.AutoXiaoerAccessibilityService,
    ): Screenshot? = kotlinx.coroutines.suspendCancellableCoroutine { cont ->
        service.takeScreenshot(
            android.view.Display.DEFAULT_DISPLAY,
            service.mainExecutor,
            object : android.accessibilityservice.AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(result: android.accessibilityservice.AccessibilityService.ScreenshotResult) {
                    val hardwareBitmap = result.hardwareBitmap
                    try {
                        val softBitmap = hardwareBitmap.copy(Bitmap.Config.ARGB_8888, false)
                        hardwareBitmap.recycle()
                        val screenshot = bitmapToScreenshot(softBitmap)
                        softBitmap.recycle()
                        if (cont.isActive) cont.resume(screenshot) {}
                    } catch (e: Exception) {
                        Logger.e(TAG, "Bitmap conversion failed", e)
                        if (cont.isActive) cont.resume(null) {}
                    }
                }

                override fun onFailure(errorCode: Int) {
                    Logger.w(TAG, "takeScreenshot failed: errorCode=$errorCode")
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
