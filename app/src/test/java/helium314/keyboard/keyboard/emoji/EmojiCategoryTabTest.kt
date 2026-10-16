// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.emoji

import android.app.Activity
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.edit
import helium314.keyboard.ShadowInputMethodManager2
import helium314.keyboard.ShadowInputMethodService
import helium314.keyboard.ShadowProximityInfo
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.prefs
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Long-pressing the category tabs of the emoji view: category name as tooltip, clearing recents only from the recents page */
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowInputMethodManager2::class, ShadowInputMethodService::class, ShadowProximityInfo::class])
class EmojiCategoryTabTest {
    private val latinIME = Robolectric.setupService(LatinIME::class.java)
    private val recents = listOf("😀", "😂", "😍")
    private lateinit var inputView: View

    private fun openEmojiView(showDescriptions: Boolean = true): LinearLayout {
        setShowDescriptions(showDescriptions)
        RecentEmojis.set(recents)
        val switcher = KeyboardSwitcher.getInstance()
        inputView = switcher.onCreateInputView(latinIME, true)
        // attach the views to a window, so the tabs can show popups and tooltips
        Robolectric.buildActivity(Activity::class.java).setup().get().setContentView(inputView)
        switcher.reloadMainKeyboard()
        switcher.setEmojiKeyboard()
        idle()
        return switcher.emojiTabStrip as LinearLayout
    }

    private fun setShowDescriptions(show: Boolean) =
        latinIME.prefs().edit { putBoolean(Settings.PREF_SHOW_EMOJI_DESCRIPTIONS, show) }

    private fun idle(millis: Long = 0) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis))

    private fun LinearLayout.tab(category: EmojiCategory.Category) = findViewWithTag<View>(category)

    private fun categoryName(category: EmojiCategory.Category) = latinIME.getString(category.element.descriptionResId)

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

    private fun tooltipText(tab: View): CharSequence? =
        ReflectionHelpers.callInstanceMethod<View?>(tab, "getTooltipView")
            ?.findViewById<TextView>(android.R.id.message)?.text

    private fun shownPopup() = shadowOf(RuntimeEnvironment.getApplication()).latestPopupWindow?.takeIf { it.isShowing }

    private fun toastShown() = inputView.findViewById<View>(R.id.fakeToast).visibility == View.VISIBLE

    @Test fun everyTabHasItsCategoryNameAsTooltip() {
        val tabs = openEmojiView()
        assertTrue(tabs.childCount > 0)
        for (i in 0 until tabs.childCount) {
            val tab = tabs.getChildAt(i)
            val category = tab.tag as EmojiCategory.Category
            assertEquals(categoryName(category), tab.tooltipText?.toString(), "tooltip of $category")
        }
    }

    @Test fun noTooltipsWhenEmojiDescriptionsAreOff() {
        val tabs = openEmojiView(showDescriptions = false)
        for (i in 0 until tabs.childCount) {
            assertNull(tabs.getChildAt(i).tooltipText, "tooltip of ${tabs.getChildAt(i).tag}")
        }
    }

    @Test fun tooltipsFollowTheSettingWhenEmojiViewIsShownAgain() {
        val tabs = openEmojiView()
        assertEquals(categoryName(EmojiCategory.Category.SMILEYS), tabs.tab(EmojiCategory.Category.SMILEYS).tooltipText?.toString())
        setShowDescriptions(false)
        KeyboardSwitcher.getInstance().reloadMainKeyboard()
        KeyboardSwitcher.getInstance().setEmojiKeyboard()
        idle()
        assertNull(tabs.tab(EmojiCategory.Category.SMILEYS).tooltipText)
    }

    @Test fun longPressOnCategoryTabShowsItsNameAndStaysOnCurrentCategory() {
        val tabs = openEmojiView()
        select(tabs, EmojiCategory.Category.SMILEYS)
        val nature = tabs.tab(EmojiCategory.Category.NATURE)
        holdOn(nature)
        assertEquals(categoryName(EmojiCategory.Category.NATURE), tooltipText(nature)?.toString())
        touch(nature, MotionEvent.ACTION_UP, center(nature).first, center(nature).second)
        idle()
        assertEquals(EmojiCategory.Category.SMILEYS.ordinal, latinIME.prefs().getInt(Settings.PREF_LAST_SHOWN_EMOJI_CATEGORY_ID, -1))
    }

    @Test fun longPressOnRecentsTabFromOtherCategoryShowsItsName() {
        val tabs = openEmojiView()
        select(tabs, EmojiCategory.Category.SMILEYS)
        val recentsTab = tabs.tab(EmojiCategory.Category.RECENTS)
        holdOn(recentsTab)
        assertEquals(categoryName(EmojiCategory.Category.RECENTS), tooltipText(recentsTab)?.toString())
        assertNull(shownPopup(), "delete button shown outside the recents page")
    }

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
        assertNull(tooltipText(recentsTab), "tooltip shown together with the delete button")
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
