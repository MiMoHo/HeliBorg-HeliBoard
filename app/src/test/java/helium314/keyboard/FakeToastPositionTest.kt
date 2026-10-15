// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard

import android.view.View
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.R
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import org.robolectric.shadows.ShadowLooper
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The in-keyboard toast used on Android 13+ should sit a little above the keyboard, where it neither
 * covers the keys under the fingers nor ends up mid-screen with a low keyboard. The input view it lives in
 * is laid out over the whole screen when not in fullscreen mode (updateSoftInputWindowLayoutParameters),
 * so this lays it out the same way: exactly as high as the screen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowInputMethodService::class, ShadowLooper::class], qualifiers = "w384dp-h896dp-xxhdpi")
class FakeToastPositionTest {
    private val latinIME = Robolectric.setupService(LatinIME::class.java)
    private val keyboardSwitcher = KeyboardSwitcher.getInstance()
    private val inputView: View

    init {
        ShadowLog.setupLogging()
        ShadowLog.stream = System.out
        inputView = keyboardSwitcher.onCreateInputView(latinIME, true)
        keyboardSwitcher.reloadMainKeyboard()
    }

    private fun layoutLikeTheImeWindow() {
        val dm = latinIME.resources.displayMetrics
        inputView.measure(View.MeasureSpec.makeMeasureSpec(dm.widthPixels, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(dm.heightPixels, View.MeasureSpec.EXACTLY))
        inputView.layout(0, 0, dm.widthPixels, dm.heightPixels)
    }

    private fun assertToastJustAboveKeyboard(label: String) {
        layoutLikeTheImeWindow()
        keyboardSwitcher.showToast("Recent emojis cleared", true)
        layoutLikeTheImeWindow()
        val toast = inputView.findViewById<View>(R.id.fakeToast)
        val frame = inputView.findViewById<View>(R.id.main_keyboard_frame)
        val toastTop = toast.y
        val toastBottom = toast.y + toast.height
        val toastCentre = toast.y + toast.height / 2f
        val frameCentre = frame.y + frame.height / 2f
        val gap = 16 * latinIME.resources.displayMetrics.density
        println("GRID toast $label: inputView=${inputView.height} frame=${frame.y.toInt()}..${(frame.y + frame.height).toInt()} " +
            "frameCentre=${frameCentre.toInt()} toast=${toastTop.toInt()}..${toastBottom.toInt()} toastCentre=${toastCentre.toInt()} " +
            "offset=${(toastCentre - frameCentre).toInt()} visible=${toast.visibility == View.VISIBLE}")
        assertTrue(toast.visibility == View.VISIBLE, "toast not shown")
        assertTrue(abs(frame.y - gap - toastBottom) <= 1f,
            "toast bottom $toastBottom should be $gap px above the keyboard top ${frame.y}")
    }

    @Test fun toastIsJustAboveTheMainKeyboard() = assertToastJustAboveKeyboard("alphabet")

    @Test fun toastIsJustAboveTheEmojiKeyboard() {
        keyboardSwitcher.setEmojiKeyboard()
        assertToastJustAboveKeyboard("emoji")
    }
}
