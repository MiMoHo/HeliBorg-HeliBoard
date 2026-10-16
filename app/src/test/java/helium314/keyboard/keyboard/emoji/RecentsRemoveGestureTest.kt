// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.emoji

import android.app.Activity
import android.os.Looper
import android.os.SystemClock
import android.view.ContextThemeWrapper
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import helium314.keyboard.ShadowInputMethodManager2
import helium314.keyboard.ShadowProximityInfo
import helium314.keyboard.keyboard.Key
import helium314.keyboard.keyboard.KeyboardLayoutSet
import helium314.keyboard.keyboard.KeyboardTheme
import helium314.keyboard.keyboard.PopupKeysKeyboardView
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.R
import helium314.keyboard.latin.RichInputMethodSubtype
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.ResourceUtils
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Drives the long-press gesture on a recent emoji through the real [EmojiPageKeyboardView] and its
 * popup panel: press an emoji, wait for the bin to appear, slide to a point, lift. Records whether
 * the emoji was removed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowInputMethodManager2::class, ShadowProximityInfo::class], qualifiers = "w384dp-h896dp-xxhdpi")
class RecentsRemoveGestureTest {
    private val latinIME = Robolectric.setupService(LatinIME::class.java)

    private fun emojiCategory(): EmojiCategory {
        val params = EmojiLayoutParams(latinIME.resources)
        val layoutSet = KeyboardLayoutSet.Builder(latinIME, null, null)
            .setSubtype(RichInputMethodSubtype.emojiSubtype)
            .setKeyboardGeometry(ResourceUtils.getKeyboardWidth(latinIME, Settings.getValues()), params.emojiKeyboardHeight)
            .build()
        val attrs = latinIME.obtainStyledAttributes(null, R.styleable.EmojiPalettesView,
            R.attr.emojiPalettesViewStyle, R.style.EmojiPalettesView)
        return EmojiCategory(latinIME, layoutSet, attrs)
    }

    private class Setup(val view: EmojiPageKeyboardView, val keyboard: DynamicGridKeyboard,
                        val removed: MutableList<Key>, val activity: Activity)

    private fun setup(): Setup {
        RecentEmojis.set(listOf("😀", "😂", "😍", "👍", "🙏", "🎉"))
        val keyboard = emojiCategory().getKeyboard(EmojiCategory.Category.RECENTS, 0)
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val themed = ContextThemeWrapper(activity, KeyboardTheme.getKeyboardTheme(activity).mStyleId)
        val view = EmojiPageKeyboardView(themed, null)
        val removed = mutableListOf<Key>()
        view.setEmojiViewCallback(object : EmojiViewCallback {
            override fun onPressKey(key: Key) {}
            override fun onReleaseKey(key: Key) {}
            override fun getDescription(emoji: String): String? = null
            override fun onRemoveRecentsKey(key: Key) {
                removed.add(key)
                keyboard.removeRecentsKey(key)
            }
        })
        view.setKeyboard(keyboard)
        val root = FrameLayout(activity)
        // put the grid near the bottom like in the keyboard, so the popup has room above it
        root.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 600 })
        activity.setContentView(root)
        shadowOf(Looper.getMainLooper()).idle()
        return Setup(view, keyboard, removed, activity)
    }

    private var downTime = 0L
    private fun send(view: View, action: Int, x: Float, y: Float) {
        val now = SystemClock.uptimeMillis()
        if (action == MotionEvent.ACTION_DOWN) downTime = now
        val e = MotionEvent.obtain(downTime, now, action, x, y, 0)
        view.onTouchEvent(e)
        e.recycle()
    }

    /** long-press the second recent, slide to (fx, fy) given as share of the drawn bin from its top left corner (null: stay), lift */
    private fun gesture(fx: Float?, fy: Float?, label: String): Boolean {
        val s = setup()
        val target = s.keyboard.sortedKeys[1]
        val name = target.label
        val kx = target.x + target.width / 2f
        val ky = target.y + target.height / 2f
        send(s.view, MotionEvent.ACTION_DOWN, kx, ky)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(Settings.getValues().mKeyLongpressTimeout + 50L))
        val panel = s.activity.findViewById<PopupKeysKeyboardView>(R.id.popup_keys_keyboard_view)
        val bin = panel.keyboard!!.sortedKeys[0]
        // the panel's key coordinates in the grid view's coordinate system (inverse of translateX/Y)
        val originX = -panel.translateX(0)
        val originY = -panel.translateY(0)
        val binLeft = originX + panel.paddingLeft + bin.x
        val binTop = originY + panel.paddingTop + bin.y
        // where the bin is really drawn, from window locations
        val vLoc = IntArray(2); s.view.getLocationInWindow(vLoc)
        val pLoc = IntArray(2); panel.getLocationInWindow(pLoc)
        val drawnLeft = pLoc[0] - vLoc[0] + panel.paddingLeft + bin.x
        val drawnTop = pLoc[1] - vLoc[1] + panel.paddingTop + bin.y
        val ux: Float
        val uy: Float
        if (fx == null || fy == null) { ux = kx; uy = ky } else {
            ux = drawnLeft + fx * bin.width
            uy = drawnTop + fy * bin.height
        }
        // slide there in a few steps, like a finger
        for (i in 1..5) {
            send(s.view, MotionEvent.ACTION_MOVE, kx + (ux - kx) * i / 5, ky + (uy - ky) * i / 5)
        }
        send(s.view, MotionEvent.ACTION_UP, ux, uy)
        shadowOf(Looper.getMainLooper()).idle()
        val gone = s.keyboard.sortedKeys.none { it.label == name } && RecentEmojis.get().none { it == name }
        println("GRID $label: key=${target.x},${target.y} ${target.width}x${target.height} " +
            "bin(translate)=$binLeft,$binTop bin(drawn)=$drawnLeft,$drawnTop ${bin.width}x${bin.height} " +
            "hitbox=${bin.hitBox} panelPad=${panel.paddingLeft},${panel.paddingTop},${panel.paddingRight},${panel.paddingBottom} " +
            "lift=${ux.toInt()},${uy.toInt()} callback=${s.removed.size} removed=$gone")
        return gone
    }

    @Test fun liftOnBinCentreRemoves() = assertEquals(true, gesture(0.5f, 0.5f, "bin centre"))
    @Test fun liftOnBinUpperPartRemoves() = assertEquals(true, gesture(0.5f, 0.2f, "bin upper part"))
    @Test fun liftOnBinLowerPartRemoves() = assertEquals(true, gesture(0.5f, 0.8f, "bin lower part"))
    @Test fun liftOnBinLeftEdgeRemoves() = assertEquals(true, gesture(0.1f, 0.5f, "bin left edge"))
    @Test fun liftWithoutMovingKeeps() = assertEquals(false, gesture(null, null, "no move"))
    @Test fun liftAboveBinKeeps() = assertEquals(false, gesture(0.5f, -0.3f, "above bin"))
    @Test fun liftBesideBinKeeps() = assertEquals(false, gesture(1.4f, 0.5f, "beside bin"))
}
