package com.flowmate.autoxiaoer.input

import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import com.flowmate.autoxiaoer.device.AutoXiaoerAccessibilityService
import com.flowmate.autoxiaoer.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [ITextInputManager] implementation that uses the AccessibilityService
 * [ACTION_SET_TEXT] to inject text into the focused input field.
 *
 * This approach works with any editable field without requiring a custom IME.
 * The field must already have input focus (the agent taps first to focus it).
 */
class AccessibilityTextInputManager : ITextInputManager {

    companion object {
        private const val TAG = "A11yTextInputManager"
    }

    override suspend fun typeText(text: String): InputResult = withContext(Dispatchers.Main) {
        val service = AutoXiaoerAccessibilityService.instance
            ?: return@withContext InputResult.failure("无障碍服务未连接")

        // Find the focused editable node
        val root = service.rootInActiveWindow
            ?: return@withContext InputResult.failure("无法获取窗口内容")

        try {
            val focusedNode = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                ?: run {
                    Logger.w(TAG, "No focused input node found")
                    return@withContext InputResult.failure("未找到输入焦点")
                }

            try {
                val args = Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
                }
                val ok = focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                return@withContext if (ok) {
                    Logger.d(TAG, "ACTION_SET_TEXT succeeded for text length ${text.length}")
                    InputResult.success("文本已输入")
                } else {
                    Logger.w(TAG, "ACTION_SET_TEXT failed, node may not support it")
                    InputResult.failure("输入失败：目标控件不支持 ACTION_SET_TEXT")
                }
            } finally {
                focusedNode.recycle()
            }
        } catch (e: Exception) {
            Logger.e(TAG, "typeText failed", e)
            return@withContext InputResult.failure("输入出错：${e.message}")
        } finally {
            root.recycle()
        }
    }
}
