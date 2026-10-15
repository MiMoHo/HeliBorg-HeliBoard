// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings

import androidx.core.content.edit
import helium314.keyboard.ShadowInputMethodManager2
import helium314.keyboard.keyboard.KeyboardActionListener.SwipeAction
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.ToolbarMode
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.screens.advancedItems
import helium314.keyboard.settings.screens.appearanceItems
import helium314.keyboard.settings.screens.gestureTypingItems
import helium314.keyboard.settings.screens.preferencesItems
import helium314.keyboard.settings.screens.textCorrectionItems
import helium314.keyboard.settings.screens.toolbarItems
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(shadows = [
    ShadowInputMethodManager2::class
])
class SubSettingTest {
    private val latinIME = Robolectric.setupService(LatinIME::class.java)
    private val prefs = latinIME.prefs()

    @BeforeTest fun reset() {
        prefs.edit { clear() }
    }

    @Test fun textCorrection() {
        prefs.edit {
            putBoolean(Settings.PREF_AUTO_CORRECTION, true)
            putString(Settings.PREF_TOOLBAR_MODE, ToolbarMode.EXPANDABLE.name)
            putBoolean(Settings.PREF_SHOW_SUGGESTIONS, true)
            putBoolean(Settings.PREF_ALWAYS_SHOW_SUGGESTIONS, true)
            putBoolean(Settings.PREF_SUGGEST_PUNCTUATION, true)
            putBoolean(Settings.PREF_KEY_USE_PERSONALIZED_DICTS, true)
        }
        // emoji suggestions are not indented, as they depend on two settings
        assertSubSettings(textCorrectionItems(prefs), mapOf(
            Settings.PREF_MORE_AUTO_CORRECTION to 1,
            Settings.PREF_AUTOCORRECT_SHORTCUTS to 1,
            Settings.PREF_AUTOCORRECT_CAPITALIZED_SUGGESTION to 1,
            Settings.PREF_AUTO_CORRECT_CONFIDENCE to 1,
            Settings.PREF_BACKSPACE_REVERTS_AUTOCORRECT to 1,
            Settings.PREF_ALWAYS_SHOW_SUGGESTIONS to 1,
            Settings.PREF_ALWAYS_SHOW_SUGGESTIONS_EXCEPT_WEB_TEXT to 2,
            Settings.PREF_CENTER_SUGGESTION_TEXT_TO_ENTER to 1,
            Settings.PREF_PUNCTUATION_SUGGESTIONS to 1,
            Settings.PREF_ADD_TO_PERSONAL_DICTIONARY to 1,
        ))
    }

    @Test fun preferences() {
        prefs.edit {
            putBoolean(Settings.PREF_SHOW_HINTS, true)
            putBoolean(Settings.PREF_VIBRATE_ON, true)
            putBoolean(Settings.PREF_SOUND_ON, true)
            putBoolean(Settings.PREF_ENABLE_CLIPBOARD_HISTORY, true)
            putBoolean(Settings.PREF_CLIPBOARD_USE_FILES, true)
            putBoolean(Settings.PREF_SHOW_NUMBER_ROW, true)
        }
        assertSubSettings(preferencesItems(prefs), mapOf(
            Settings.PREF_POPUP_KEYS_HINT_ORDER to 1,
            Settings.PREF_SHOW_NUMBER_ROW_HINTS to 1,
            Settings.PREF_VIBRATION_DURATION_SETTINGS to 1,
            Settings.PREF_VIBRATE_IN_DND_MODE to 1,
            Settings.PREF_KEYPRESS_SOUND_VOLUME to 1,
            Settings.PREF_CLIPBOARD_HISTORY_RETENTION_TIME to 1,
            Settings.PREF_CLIPBOARD_HISTORY_PINNED_FIRST to 1,
            Settings.PREF_CLIPBOARD_USE_FILES to 1,
            Settings.PREF_CLIPBOARD_FILES_SIZE_LIMIT to 2,
        ))
    }

    @Test fun appearance() {
        prefs.edit {
            putBoolean(Settings.PREF_ENABLE_SPLIT_KEYBOARD, true)
            putBoolean(Settings.PREF_THEME_KEY_BORDERS, true)
            putFloat(Settings.PREF_EMOJI_FONT_SCALE, 1.5f)
            putBoolean(Settings.PREF_THEME_DAY_NIGHT, true)
        }
        assertSubSettings(appearanceItems(prefs), mapOf(
            Settings.PREF_KEY_GAP_SCALE_PREFIX to 1,
            Settings.PREF_THEME_COLORS_NIGHT to 1,
            Settings.PREF_SPLIT_SPACER_SCALE_PREFIX to 1,
            Settings.PREF_EMOJI_KEY_FIT to 1,
        ))
    }

    @Test fun toolbarHidden() {
        prefs.edit {
            putString(Settings.PREF_TOOLBAR_MODE, ToolbarMode.HIDDEN.name)
            putBoolean(Settings.PREF_TOOLBAR_HIDING_GLOBAL, false)
        }
        assertSubSettings(toolbarItems(prefs), mapOf(
            Settings.PREF_TOOLBAR_HIDING_GLOBAL to 1,
            Settings.PREF_CLIPBOARD_TOOLBAR_KEYS to 1,
            Settings.PREF_TOOLBAR_CUSTOM_KEY_CODES to 1,
        ))
    }

    @Test fun toolbarExpandable() {
        prefs.edit { putString(Settings.PREF_TOOLBAR_MODE, ToolbarMode.EXPANDABLE.name) }
        assertSubSettings(toolbarItems(prefs), mapOf(
            Settings.PREF_TOOLBAR_SWIPE_DOWN_TO_HIDE to 1,
            Settings.PREF_TOOLBAR_KEYS to 1,
            Settings.PREF_PINNED_TOOLBAR_KEYS to 1,
            Settings.PREF_CLIPBOARD_TOOLBAR_KEYS to 1,
            Settings.PREF_TOOLBAR_CUSTOM_KEY_CODES to 1,
            Settings.PREF_QUICK_PIN_TOOLBAR_KEYS to 1,
            Settings.PREF_AUTO_SHOW_TOOLBAR to 1,
            Settings.PREF_AUTO_HIDE_TOOLBAR to 1,
            Settings.PREF_SHOW_ONLY_TOOLBAR_WITH_HARDWARE_KEYBOARD to 1,
            Settings.PREF_VARIABLE_TOOLBAR_DIRECTION to 1,
        ))
    }

    @Test fun advanced() {
        prefs.edit {
            putString(Settings.PREF_SPACE_HORIZONTAL_SWIPE, SwipeAction.SWITCH_LANGUAGE.name)
            putString(Settings.PREF_SPACE_VERTICAL_SWIPE, SwipeAction.TOUCHPAD_MODE.name)
        }
        assertSubSettings(advancedItems(prefs), mapOf(
            Settings.PREF_LANGUAGE_SWIPE_DISTANCE to 1,
            Settings.PREF_TOUCHPAD_SENSITIVITY to 1,
            Settings.PREF_TOUCHPAD_EDGE_SCROLL to 1,
        ))
    }

    // switching back after a clipboard history entry only has an effect when the history can be opened
    @Test fun advancedWithoutClipboardHistory() {
        prefs.edit { putBoolean(Settings.PREF_ENABLE_CLIPBOARD_HISTORY, false) }
        assertTrue(Settings.PREF_ABC_AFTER_CLIP !in advancedItems(prefs))
        prefs.edit { putBoolean(Settings.PREF_ENABLE_CLIPBOARD_HISTORY, true) }
        assertTrue(Settings.PREF_ABC_AFTER_CLIP in advancedItems(prefs))
    }

    @Test fun gestureTyping() {
        prefs.edit {
            putBoolean(Settings.PREF_GESTURE_INPUT, true)
            putBoolean(Settings.PREF_GESTURE_FLOATING_PREVIEW_TEXT, true)
        }
        assertSubSettings(gestureTypingItems(prefs), mapOf(
            Settings.PREF_GESTURE_PREVIEW_TRAIL to 1,
            Settings.PREF_GESTURE_FLOATING_PREVIEW_TEXT to 1,
            Settings.PREF_GESTURE_FLOATING_PREVIEW_DYNAMIC to 2,
            Settings.PREF_GESTURE_SPACE_AWARE to 1,
            Settings.PREF_GESTURE_FAST_TYPING_COOLDOWN to 1,
            Settings.PREF_GESTURE_TRAIL_FADEOUT_DURATION to 1,
        ))
    }

    /**
     *  Checks that exactly the [expected] settings are indented, with the expected level, and that each of them
     *  directly follows a setting at most one level lower, so it never ends up right below a category title.
     */
    private fun assertSubSettings(items: List<Any?>, expected: Map<String, Int>) {
        val shown = items.filterNotNull()
        assertEquals(expected, shown.filterIsInstance<SubSetting>().associate { it.key to it.level })
        shown.forEachIndexed { i, item ->
            if (item !is SubSetting) return@forEachIndexed
            val previousLevel = when (val previous = shown.getOrNull(i - 1)) {
                is SubSetting -> previous.level
                is String -> 0
                else -> -1
            }
            assertTrue(previousLevel >= item.level - 1, "${item.key} at level ${item.level} follows level $previousLevel")
        }
    }
}
