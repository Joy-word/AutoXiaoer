package com.flowmate.autoxiaoer.input

/**
 * Abstraction over the text-input backend.
 *
 * [TextInputManager] (Shizuku + custom IME) and [AccessibilityTextInputManager]
 * (AccessibilityService ACTION_SET_TEXT) both implement this interface.
 */
interface ITextInputManager {

    /**
     * Types [text] into the currently focused input field.
     *
     * @return [InputResult] describing success or failure.
     */
    suspend fun typeText(text: String): InputResult
}
