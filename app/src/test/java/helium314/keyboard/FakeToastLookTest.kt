// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.R
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The in-keyboard toast used on Android 13+ should look like the text toast of the system (and so like the toasts
 * of other apps): the system's toast frame in the device colours, following the system dark mode, the app icon and
 * the system's text style. Expected values come from the framework resources, not from copies of them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowInputMethodService::class, ShadowLooper::class], qualifiers = "w384dp-h896dp-xxhdpi")
class FakeToastLookTest {
    @Test fun looksLikeTheSystemToast() = assertLooksLikeTheSystemToast(night = false)

    @Config(qualifiers = "+night")
    @Test fun looksLikeTheSystemToastInDarkMode() = assertLooksLikeTheSystemToast(night = true)

    // the system toast cuts messages off after two lines, which would hide the end of the longer messages
    @GraphicsMode(GraphicsMode.Mode.NATIVE) // real text measurement, so the text wraps
    @Test fun showsLongMessagesCompletely() {
        val (_, toast) = showToast { it.getString(R.string.gesture_data_ends_at, "31.12.2026") }
        val text = toast.descendants().filterIsInstance<TextView>().single()
        val cutOff = (0 until text.lineCount).sumOf { text.layout.getEllipsisCount(it) }
        println("GRID long message: lines=${text.lineCount} ellipsized chars=$cutOff")
        assertTrue(text.lineCount > 2, "the message needs more than two lines here, but has ${text.lineCount}")
        assertEquals(0, cutOff, "characters cut off")
    }

    private fun showToast(message: (LatinIME) -> String): Pair<LatinIME, View> {
        val latinIME = Robolectric.setupService(LatinIME::class.java)
        val keyboardSwitcher = KeyboardSwitcher.getInstance()
        val inputView = keyboardSwitcher.onCreateInputView(latinIME, true)
        keyboardSwitcher.reloadMainKeyboard()
        val dm = latinIME.resources.displayMetrics
        fun layout() {
            inputView.measure(View.MeasureSpec.makeMeasureSpec(dm.widthPixels, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(dm.heightPixels, View.MeasureSpec.EXACTLY))
            inputView.layout(0, 0, dm.widthPixels, dm.heightPixels)
        }
        layout()
        keyboardSwitcher.showToast(message(latinIME), true)
        layout()
        return latinIME to inputView.findViewById(R.id.fakeToast)
    }

    private fun assertLooksLikeTheSystemToast(night: Boolean) {
        val (latinIME, toast) = showToast { "Recent emojis cleared" }
        val dm = latinIME.resources.displayMetrics
        val text = toast.descendants().filterIsInstance<TextView>().single()
        val icon = toast.descendants().filterIsInstance<ImageView>().singleOrNull()

        val system = ContextThemeWrapper(latinIME, android.R.style.Theme_DeviceDefault_DayNight)
        val systemFrame = system.getDrawable(android.R.drawable.toast_frame) as GradientDrawable
        val systemTextColor = TypedValue().let {
            system.theme.resolveAttribute(android.R.attr.textColorPrimary, it, true)
            system.getColorStateList(it.resourceId).defaultColor
        }
        val background = toast.background as GradientDrawable
        val backgroundColor = background.color!!.defaultColor
        println("GRID look night=$night: background=${hex(backgroundColor)} (system ${hex(systemFrame.color!!.defaultColor)}) " +
            "text=${hex(text.currentTextColor)} (system ${hex(systemTextColor)}) textSize=${text.textSize} " +
            "corner=${background.cornerRadius} (system ${systemFrame.cornerRadius}) icon=${icon?.layoutParams?.width}")

        assertEquals(hex(systemFrame.color!!.defaultColor), hex(backgroundColor), "background colour of the system toast")
        assertEquals(systemFrame.cornerRadius, background.cornerRadius, "corner radius of the system toast")
        assertEquals(hex(systemTextColor), hex(text.currentTextColor), "text colour of the system toast")
        assertEquals(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 14f, dm), text.textSize, "text size of the system toast")
        assertNotNull(icon?.drawable, "the system toast shows the app icon")
        assertEquals(Math.round(24 * dm.density), icon!!.layoutParams.width, "icon size of the system toast")
        if (night) assertTrue(luminance(backgroundColor) < luminance(text.currentTextColor), "dark toast in dark mode")
        else assertTrue(luminance(backgroundColor) > luminance(text.currentTextColor), "light toast in light mode")
    }

    private fun View.descendants(): List<View> =
        if (this is ViewGroup) listOf(this) + (0 until childCount).flatMap { getChildAt(it).descendants() } else listOf(this)

    private fun hex(color: Int) = String.format("#%08X", color)

    private fun luminance(color: Int) = 0.2126 * Color.red(color) + 0.7152 * Color.green(color) + 0.0722 * Color.blue(color)
}
