// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard

import android.view.Gravity
import android.view.View
import android.widget.Toast
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.R
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowToast
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Up to Android 12L the keyboard shows system toasts. Up to Android 10 their position can be set: a little above the
 * keyboard like the in-keyboard toast on Android 13+, not mid-screen. From Android 11 on the system ignores setGravity
 * for text toasts and places them itself.
 */
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowInputMethodService::class, ShadowLooper::class], qualifiers = "w384dp-h896dp-xxhdpi")
class SystemToastPositionTest {
    private fun showToastWithKeyboard(): Pair<View, Toast> {
        // below Android 13 ContextCompat.registerReceiver checks this permission: the merged manifest requests it,
        // but Robolectric doesn't grant it
        val app = RuntimeEnvironment.getApplication()
        shadowOf(app).grantPermissions(app.packageName + ".DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")
        val latinIME = Robolectric.setupService(LatinIME::class.java)
        val keyboardSwitcher = KeyboardSwitcher.getInstance()
        val inputView = keyboardSwitcher.onCreateInputView(latinIME, true)
        keyboardSwitcher.reloadMainKeyboard()
        val dm = latinIME.resources.displayMetrics
        inputView.measure(View.MeasureSpec.makeMeasureSpec(dm.widthPixels, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(dm.heightPixels, View.MeasureSpec.EXACTLY))
        inputView.layout(0, 0, dm.widthPixels, dm.heightPixels)
        keyboardSwitcher.showToast("Content copied", true)
        return inputView to ShadowToast.getLatestToast()
    }

    @Config(sdk = [29])
    @Test fun android10ToastIsJustAboveTheKeyboard() {
        val (inputView, toast) = showToastWithKeyboard()
        val frame = inputView.findViewById<View>(R.id.main_keyboard_frame)
        val expectedOffset = (inputView.height - frame.y + 16 * inputView.resources.displayMetrics.density).roundToInt()
        println("GRID system toast sdk 29: gravity=${toast.gravity} yOffset=${toast.yOffset} expected=$expectedOffset")
        assertEquals(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, toast.gravity, "gravity")
        assertEquals(expectedOffset, toast.yOffset, "distance from the bottom")
    }

    @Config(sdk = [30])
    @Test fun android11ToastIsPlacedByTheSystem() {
        val (inputView, toast) = showToastWithKeyboard()
        val systemDefault = Toast.makeText(inputView.context, "x", Toast.LENGTH_SHORT)
        println("GRID system toast sdk 30: gravity=${toast.gravity} yOffset=${toast.yOffset} " +
            "system=${systemDefault.gravity}/${systemDefault.yOffset}")
        assertEquals(systemDefault.gravity, toast.gravity, "gravity")
        assertEquals(systemDefault.yOffset, toast.yOffset, "distance from the bottom")
    }
}
