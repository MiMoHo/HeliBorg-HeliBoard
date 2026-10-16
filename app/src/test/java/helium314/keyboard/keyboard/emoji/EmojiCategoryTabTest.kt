// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.emoji

import android.app.Activity
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.LinearLayout
import helium314.keyboard.ShadowInputMethodManager2
import helium314.keyboard.ShadowInputMethodService
import helium314.keyboard.ShadowProximityInfo
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.R
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** Long-pressing the recents tab of the emoji view: clearing recents only from the recents page, with a delete button */
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowInputMethodManager2::class, ShadowInputMethodService::class, ShadowProximityInfo::class])
class EmojiCategoryTabTest {
    private val latinIME = Robolectric.setupService(LatinIME::class.java)
    private val recents = listOf("😀", "😂", "😍")
    private lateinit var inputView: View

    private fun openEmojiView(): LinearLayout {
        RecentEmojis.set(recents)
        val switcher = KeyboardSwitcher.getInstance()
        inputView = switcher.onCreateInputView(latinIME, true)
        // attach the views to a window, so the tabs can show popups
        Robolectric.buildActivity(Activity::class.java).setup().get().setContentView(inputView)
        switcher.reloadMainKeyboard()
        switcher.setEmojiKeyboard()
        idle()
        return switcher.emojiTabStrip as LinearLayout
    }

    private fun idle(millis: Long = 0) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis))

    private fun LinearLayout.tab(category: EmojiCategory.Category) = findViewWithTag<View>(category)

    private fun select(tabs: LinearLayout, category: EmojiCategory.Category) {
        tabs.tab(category).performClick()
        idle()
    }

    private var downTime = 0L
    /** sends a touch event to [view] at the given screen position, like the window would */
    private fun touch(view: View, action: Int, screenX: Float, screenY: Float) {
        val now = SystemClock.uptimeMillis()
        if (action == MotionEvent.ACTION_DOWN) downTime = now
        val event = MotionEvent.obtain(downTime, now, action, screenX, screenY, 0)
        val location = IntArray(2).also { view.getLocationOnScreen(it) }
        event.setLocation(screenX - location[0], screenY - location[1]) // raw coordinates stay on screen
        view.dispatchTouchEvent(event)
        event.recycle()
    }

    private fun center(view: View): Pair<Float, Float> {
        val location = IntArray(2).also { view.getLocationOnScreen(it) }
        return location[0] + view.width / 2f to location[1] + view.height / 2f
    }

    /** puts a finger on [tab] and holds it until the long press fired */
    private fun holdOn(tab: View) {
        val (x, y) = center(tab)
        touch(tab, MotionEvent.ACTION_DOWN, x, y)
        idle(ViewConfiguration.getLongPressTimeout() + 100L)
    }

    private fun shownPopup() = shadowOf(RuntimeEnvironment.getApplication()).latestPopupWindow?.takeIf { it.isShowing }

    private fun toastShown() = inputView.findViewById<View>(R.id.fakeToast).visibility == View.VISIBLE

    @Test fun longPressOnRecentsTabFromOtherCategoryDoesNotClear() {
        val tabs = openEmojiView()
        select(tabs, EmojiCategory.Category.SMILEYS)
        val recentsTab = tabs.tab(EmojiCategory.Category.RECENTS)
        holdOn(recentsTab)
        assertNull(shownPopup(), "delete button shown outside the recents page")
        touch(recentsTab, MotionEvent.ACTION_UP, center(recentsTab).first, center(recentsTab).second)
        idle()
        assertEquals(recents, RecentEmojis.get())
        assertEquals(false, toastShown())
    }

    @Test fun releaseOnDeleteButtonClearsRecentsWithoutToast() {
        val tabs = openEmojiView()
        select(tabs, EmojiCategory.Category.RECENTS)
        val recentsTab = tabs.tab(EmojiCategory.Category.RECENTS)
        holdOn(recentsTab)
        assertEquals(recents, RecentEmojis.get(), "cleared before the finger was released")
        val bin = assertNotNull(shownPopup(), "no delete button on the recents page").contentView
        val (x, y) = center(bin)
        touch(recentsTab, MotionEvent.ACTION_MOVE, x, y)
        touch(recentsTab, MotionEvent.ACTION_UP, x, y)
        idle()
        assertEquals(emptyList(), RecentEmojis.get())
        assertNull(shownPopup())
        assertEquals(false, toastShown())
    }

    @Test fun releaseBesideDeleteButtonKeepsRecents() {
        val tabs = openEmojiView()
        select(tabs, EmojiCategory.Category.RECENTS)
        val recentsTab = tabs.tab(EmojiCategory.Category.RECENTS)
        holdOn(recentsTab)
        assertNotNull(shownPopup(), "no delete button on the recents page")
        touch(recentsTab, MotionEvent.ACTION_UP, center(recentsTab).first, center(recentsTab).second)
        idle()
        assertEquals(recents, RecentEmojis.get())
        assertNull(shownPopup())
    }
}
