package com.flowmate.autoxiaoer.ui

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import com.google.android.material.textfield.TextInputEditText

/**
 * Multiline [TextInputEditText] that claims touch events for itself so an ancestor
 * ScrollView/NestedScrollView doesn't steal swipes meant to scroll the text content.
 */
class ScrollSafeEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : TextInputEditText(context, attrs) {

    override fun onTouchEvent(event: MotionEvent): Boolean {
        parent?.requestDisallowInterceptTouchEvent(true)
        return super.onTouchEvent(event)
    }
}
