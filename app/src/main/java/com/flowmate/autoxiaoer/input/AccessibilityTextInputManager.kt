package com.flowmate.autoxiaoer.input

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Base64
import android.view.accessibility.AccessibilityNodeInfo
import com.flowmate.autoxiaoer.device.AutoXiaoerAccessibilityService
import com.flowmate.autoxiaoer.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Accessibility backend text input manager.
 *
 * When XiaoEr Keyboard is already the default IME, we prefer the IME path directly via
 * broadcasts because it is the most reliable method and avoids unnecessary keyboard switching.
 * For other apps, we still try ACTION_SET_TEXT first and then fall back to the custom keyboard
 * path when the target view does not support direct text injection.
 */
class AccessibilityTextInputManager : ITextInputManager {

    companion object {
        private const val TAG = "A11yTextInputManager"
        private const val KEYBOARD_SWITCH_DELAY_MS = 500L
        private const val KEYBOARD_RESTORE_DELAY_MS = 300L
    }

    private var originalIme: String? = null

    override suspend fun typeText(text: String): InputResult = withContext(Dispatchers.Main) {
        val service = AutoXiaoerAccessibilityService.instance
            ?: return@withContext InputResult.failure("无障碍服务未连接")

        val currentIme = Settings.Secure.getString(service.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ?: ""

        if (KeyboardHelper.isAutoGLMKeyboard(currentIme)) {
            Logger.d(TAG, "Default IME is XiaoEr Keyboard, prefer IME input path")
            return@withContext sendTextViaKeyboard(service, text)
        }

        val directResult = trySetTextDirectly(service, text)
        if (directResult.success) {
            return@withContext directResult
        }

        Logger.w(TAG, "ACTION_SET_TEXT failed, fallback to keyboard path: ${directResult.message}")

        val softKeyboard = service.softKeyboardController

        try {
            originalIme = currentIme
            val switched = trySwitchToAutoGLMKeyboard(softKeyboard)
            if (!switched) {
                Logger.w(TAG, "SoftKeyboardController is unavailable or switch failed")
                return@withContext InputResult.failure(
                    "输入失败：ACTION_SET_TEXT 不支持，且当前系统不支持切换 XiaoEr Keyboard"
                )
            }
            delay(KEYBOARD_SWITCH_DELAY_MS)

            return@withContext sendTextViaKeyboard(service, text)
        } catch (e: Exception) {
            Logger.e(TAG, "typeText failed", e)
            return@withContext InputResult.failure("输入出错：${e.message}")
        } finally {
            val previousIme = originalIme
            if (!previousIme.isNullOrBlank() && previousIme != KeyboardHelper.IME_ID) {
                try {
                    tryRestoreKeyboard(softKeyboard, previousIme)
                    delay(KEYBOARD_RESTORE_DELAY_MS)
                } catch (_: Exception) {
                    // Ignore restore failure; keep app usable.
                }
            }
            originalIme = null
        }
    }

    private suspend fun sendTextViaKeyboard(service: AutoXiaoerAccessibilityService, text: String): InputResult {
        try {
            sendKeyboardBroadcast(service, AutoGLMKeyboardService.ACTION_CLEAR_TEXT, null)
            delay(120L)

            val encoded = Base64.encodeToString(text.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            sendKeyboardBroadcast(service, AutoGLMKeyboardService.ACTION_INPUT_B64, encoded)
            delay(200L)

            return InputResult.success("文本已输入")
        } catch (e: Exception) {
            Logger.e(TAG, "sendTextViaKeyboard failed", e)
            return InputResult.failure("输入出错：${e.message}")
        }
    }

    private fun trySetTextDirectly(service: AutoXiaoerAccessibilityService, text: String): InputResult {
        val root = service.rootInActiveWindow ?: return InputResult.failure("无法获取窗口内容")

        try {
            val focusedNode = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                ?: return InputResult.failure("未找到输入焦点")

            try {
                val args = Bundle().apply {
                    putCharSequence(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                        text
                    )
                }
                val ok = focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                return if (ok) {
                    Logger.d(TAG, "ACTION_SET_TEXT succeeded for text length ${text.length}")
                    InputResult.success("文本已输入")
                } else {
                    Logger.w(TAG, "ACTION_SET_TEXT failed, node may not support it")
                    InputResult.failure("目标控件不支持 ACTION_SET_TEXT")
                }
            } finally {
                focusedNode.recycle()
            }
        } catch (e: Exception) {
            Logger.e(TAG, "trySetTextDirectly failed", e)
            return InputResult.failure("输入出错：${e.message}")
        } finally {
            root.recycle()
        }
    }

    private fun trySwitchToAutoGLMKeyboard(softKeyboard: Any?): Boolean {
        if (softKeyboard == null) return false

        return try {
            val controllerClass = softKeyboard.javaClass

            try {
                val setEnabled = controllerClass.getMethod(
                    "setInputMethodEnabled",
                    String::class.java,
                    Boolean::class.javaPrimitiveType
                )
                val result = setEnabled.invoke(softKeyboard, KeyboardHelper.IME_ID, true)
                Logger.d(TAG, "setInputMethodEnabled -> $result")
            } catch (_: NoSuchMethodException) {
                Logger.d(TAG, "setInputMethodEnabled not available on this device")
            }

            val switchMethod = controllerClass.getMethod("switchToInputMethod", String::class.java)
            val switched = switchMethod.invoke(softKeyboard, KeyboardHelper.IME_ID) as? Boolean ?: false
            Logger.d(TAG, "switchToInputMethod(${KeyboardHelper.IME_ID}) -> $switched")
            switched
        } catch (e: Exception) {
            Logger.w(TAG, "SoftKeyboardController switch failed", e)
            false
        }
    }

    private fun tryRestoreKeyboard(softKeyboard: Any?, previousIme: String) {
        if (softKeyboard == null) return
        try {
            val method = softKeyboard.javaClass.getMethod("switchToInputMethod", String::class.java)
            val result = method.invoke(softKeyboard, previousIme) as? Boolean
            Logger.d(TAG, "restore keyboard to $previousIme -> $result")
        } catch (_: Exception) {
            Logger.w(TAG, "Failed to restore previous IME: $previousIme")
        }
    }

    private fun sendKeyboardBroadcast(service: AutoXiaoerAccessibilityService, action: String, payload: String?) {
        val intent = Intent(action).setPackage(service.packageName)
        if (payload != null) {
            intent.putExtra(AutoGLMKeyboardService.EXTRA_MSG, payload)
        }
        service.sendBroadcast(intent)
    }
}
