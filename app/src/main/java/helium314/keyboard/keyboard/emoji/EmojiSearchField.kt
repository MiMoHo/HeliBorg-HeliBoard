// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.emoji

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.widget.TextView
import helium314.keyboard.latin.utils.dpToPx

/**
 * The search field of [EmojiSearch]. The query is typed on the keyboard and not into a focused text field, so the
 * field draws a blinking caret at the end of the query itself, like a focused text field.
 */
class EmojiSearchField(context: Context, attrs: AttributeSet?) : TextView(context, attrs) {
    private val caretPaint = Paint()
    private val caretWidth = 2.dpToPx(resources).toFloat() // like the cursor of text fields
    private val lineBounds = Rect()
    private var caretShown = false
    private var caretOn = false
    private val blink = object : Runnable {
        override fun run() {
            caretOn = !caretOn
            invalidate()
            postDelayed(this, BLINK_MILLIS)
        }
    }

    /** Shows or hides the caret. Showing it restarts the blinking with the caret on, like typing in a text field. */
    fun setCaretShown(shown: Boolean) {
        caretShown = shown
        caretOn = shown
        removeCallbacks(blink)
        if (shown) postDelayed(blink, BLINK_MILLIS)
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (caretShown) setCaretShown(true)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(blink)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!caretShown || !caretOn) return
        val layout = layout ?: return
        // right after the query, or where the hint starts if it is empty, like the cursor of text fields
        val x = compoundPaddingLeft + layout.getPrimaryHorizontal(text.length)
        val left = x.coerceAtMost(width - compoundPaddingRight - caretWidth).coerceAtLeast(compoundPaddingLeft.toFloat())
        getLineBounds(0, lineBounds)
        caretPaint.color = currentTextColor
        canvas.drawRect(left, lineBounds.top.toFloat(), left + caretWidth, lineBounds.bottom.toFloat(), caretPaint)
    }

    companion object {
        private const val BLINK_MILLIS = 500L // like text fields
    }
}
