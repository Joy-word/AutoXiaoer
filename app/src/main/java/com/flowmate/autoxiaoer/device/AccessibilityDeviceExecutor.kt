package com.flowmate.autoxiaoer.device

import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import com.flowmate.autoxiaoer.util.Logger
import com.flowmate.autoxiaoer.util.Point
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * [IDeviceExecutor] implementation that uses the [AutoXiaoerAccessibilityService]
 * for gesture injection and window inspection.
 *
 * Supported operations:
 * - tap / doubleTap / longPress / swipe → [GestureDescription] + dispatchGesture()
 * - pressKey(BACK)   → GLOBAL_ACTION_BACK
 * - pressKey(HOME)   → GLOBAL_ACTION_HOME
 * - pressKey(POWER)  → GLOBAL_ACTION_LOCK_SCREEN (API 28+, else returns failure)
 * - pressKey(VOLUME_UP/DOWN) → returns failure message (not supported)
 * - launchApp        → Context.startActivity with LAUNCH_APP intent
 * - getCurrentApp    → [AutoXiaoerAccessibilityService.foregroundPackage]
 */
class AccessibilityDeviceExecutor : IDeviceExecutor {

    companion object {
        private const val TAG = "A11yDeviceExecutor"
        private const val DOUBLE_TAP_INTERVAL_MS = 100L
        private const val GESTURE_TIMEOUT_MS = 5000L
    }

    private val service: AutoXiaoerAccessibilityService
        get() = AutoXiaoerAccessibilityService.instance
            ?: error("AccessibilityService is not connected")

    // region Gesture helpers

    private suspend fun dispatchGesture(description: GestureDescription): Boolean =
        withTimeoutOrNull(GESTURE_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                val svc = service
                val callback = object : android.accessibilityservice.AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription) {
                        if (cont.isActive) cont.resume(true)
                    }
                    override fun onCancelled(gestureDescription: GestureDescription) {
                        if (cont.isActive) cont.resume(false)
                    }
                }
                if (!svc.dispatchGesture(description, callback, null)) {
                    if (cont.isActive) cont.resume(false)
                }
            }
        } ?: false

    private fun buildTapGesture(x: Int, y: Int, duration: Long = 50L): GestureDescription {
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        return GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            .build()
    }

    private fun buildSwipeGesture(points: List<Point>, durationMs: Long): GestureDescription {
        val path = Path().apply {
            moveTo(points[0].x.toFloat(), points[0].y.toFloat())
            for (i in 1 until points.size) {
                lineTo(points[i].x.toFloat(), points[i].y.toFloat())
            }
        }
        return GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
            .build()
    }

    // endregion

    override suspend fun tap(x: Int, y: Int): String = withContext(Dispatchers.Main) {
        val ok = dispatchGesture(buildTapGesture(x, y))
        if (ok) "" else "tap failed at ($x, $y)"
    }

    override suspend fun doubleTap(x: Int, y: Int): String = withContext(Dispatchers.Main) {
        val ok1 = dispatchGesture(buildTapGesture(x, y))
        delay(DOUBLE_TAP_INTERVAL_MS)
        val ok2 = dispatchGesture(buildTapGesture(x, y))
        if (ok1 && ok2) "" else "doubleTap partially failed at ($x, $y)"
    }

    override suspend fun longPress(x: Int, y: Int, durationMs: Int): String =
        withContext(Dispatchers.Main) {
            val ok = dispatchGesture(buildTapGesture(x, y, durationMs.toLong()))
            if (ok) "" else "longPress failed at ($x, $y)"
        }

    override suspend fun swipe(points: List<Point>, durationMs: Int): String =
        withContext(Dispatchers.Main) {
            if (points.size < 2) return@withContext "Error: Swipe requires at least 2 points"
            val ok = dispatchGesture(buildSwipeGesture(points, durationMs.toLong()))
            if (ok) "" else "swipe failed"
        }

    override suspend fun pressKey(keyCode: Int): String = withContext(Dispatchers.Main) {
        when (keyCode) {
            DeviceExecutor.KEYCODE_BACK -> {
                val ok = service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
                if (ok) "" else "GLOBAL_ACTION_BACK failed"
            }
            DeviceExecutor.KEYCODE_HOME -> {
                val ok = service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)
                if (ok) "" else "GLOBAL_ACTION_HOME failed"
            }
            DeviceExecutor.KEYCODE_POWER -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val ok = service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
                    if (ok) "" else "GLOBAL_ACTION_LOCK_SCREEN failed"
                } else {
                    Logger.w(TAG, "Lock screen not supported below API 28")
                    "无障碍模式在当前系统版本不支持电源键操作"
                }
            }
            DeviceExecutor.KEYCODE_VOLUME_UP,
            DeviceExecutor.KEYCODE_VOLUME_DOWN -> {
                Logger.w(TAG, "Volume keys not supported in Accessibility backend")
                "无障碍模式不支持音量键操作"
            }
            else -> {
                Logger.w(TAG, "Unsupported keyCode in Accessibility backend: $keyCode")
                "无障碍模式不支持该按键 (keyCode=$keyCode)"
            }
        }
    }

    override suspend fun launchApp(packageName: String): String = withContext(Dispatchers.Default) {
        val ctx = service.applicationContext
        val pm = ctx.packageManager
        val intent = pm.getLaunchIntentForPackage(packageName)
        return@withContext if (intent != null) {
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(intent)
            ""
        } else {
            Logger.w(TAG, "No launch intent for $packageName")
            "找不到应用: $packageName"
        }
    }

    override suspend fun getCurrentApp(): String {
        val svc = AutoXiaoerAccessibilityService.instance ?: return ""
        svc.foregroundPackage?.let { return it }
        val root = svc.rootInActiveWindow ?: return ""
        return try {
            root.packageName?.toString() ?: ""
        } finally {
            root.recycle()
        }
    }
}
