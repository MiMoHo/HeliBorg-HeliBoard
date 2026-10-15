// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.emoji

import helium314.keyboard.ShadowInputMethodManager2
import helium314.keyboard.ShadowProximityInfo
import helium314.keyboard.keyboard.KeyboardLayoutSet
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.R
import helium314.keyboard.latin.RichInputMethodSubtype
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.ResourceUtils
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertFalse

@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowInputMethodManager2::class, ShadowProximityInfo::class])
class RecentsRemoveTest {
    private val latinIME = Robolectric.setupService(LatinIME::class.java)

    /** built the same way as in EmojiPalettesView */
    private fun recentsKeyboard(): DynamicGridKeyboard {
        val params = EmojiLayoutParams(latinIME.resources)
        val layoutSet = KeyboardLayoutSet.Builder(latinIME, null, null)
            .setSubtype(RichInputMethodSubtype.emojiSubtype)
            .setKeyboardGeometry(ResourceUtils.getKeyboardWidth(latinIME, Settings.getValues()), params.emojiKeyboardHeight)
            .build()
        val attrs = latinIME.obtainStyledAttributes(null, R.styleable.EmojiPalettesView,
            R.attr.emojiPalettesViewStyle, R.style.EmojiPalettesView)
        return EmojiCategory(latinIME, layoutSet, attrs).getKeyboard(EmojiCategory.Category.RECENTS, 0)
    }

    @Test fun removedRecentStaysRemovedAfterFlush() {
        RecentEmojis.set(listOf("😀", "😂", "😍"))
        val keyboard = recentsKeyboard()
        val used = keyboard.sortedKeys.first { it.label == "😂" }
        // using an emoji while the recents tab is shown only queues it (EmojiPalettesView.addRecentKey)
        keyboard.addPendingKey(used)
        keyboard.removeRecentsKey(used)
        // switching the tab or closing the keyboard flushes the queue
        keyboard.flushPendingRecentKeys()
        assertFalse(keyboard.sortedKeys.any { it.label == "😂" }, "removed emoji is back in the grid")
        assertFalse(RecentEmojis.get().contains("😂"), "removed emoji is back in the stored recents")
    }
}
