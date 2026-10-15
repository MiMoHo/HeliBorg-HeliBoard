// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings

import androidx.core.content.edit
import helium314.keyboard.ShadowInputMethodManager2
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.screens.readSwitchKeyboardAfter
import helium314.keyboard.settings.screens.switchKeyboardAfterOptions
import helium314.keyboard.settings.screens.writeSwitchKeyboardAfter
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(shadows = [
    ShadowInputMethodManager2::class
])
class SwitchKeyboardAfterTest {
    private val latinIME = Robolectric.setupService(LatinIME::class.java)
    private val prefs = latinIME.prefs()

    @BeforeTest fun reset() {
        prefs.edit { clear() }
    }

    @Test fun defaultsAreShown() {
        prefs.edit { putBoolean(Settings.PREF_ENABLE_CLIPBOARD_HISTORY, true) }
        val options = switchKeyboardAfterOptions(prefs)
        val expected = mapOf(
            Settings.PREF_ABC_AFTER_SYMBOL_SPACE to Defaults.PREF_ABC_AFTER_SYMBOL_SPACE,
            Settings.PREF_ABC_AFTER_NUMPAD_SPACE to Defaults.PREF_ABC_AFTER_NUMPAD_SPACE,
            Settings.PREF_ABC_AFTER_EMOJI to Defaults.PREF_ABC_AFTER_EMOJI,
            Settings.PREF_ABC_AFTER_CLIP to Defaults.PREF_ABC_AFTER_CLIP,
        )
        assertEquals(expected.keys.toList(), options)
        assertEquals(expected.filterValues { it }.keys.toList(), readSwitchKeyboardAfter(prefs, options))
    }

    @Test fun selectionIsWrittenToTheExistingSwitches() {
        prefs.edit { putBoolean(Settings.PREF_ENABLE_CLIPBOARD_HISTORY, true) }
        val options = switchKeyboardAfterOptions(prefs)
        writeSwitchKeyboardAfter(prefs, options, listOf(Settings.PREF_ABC_AFTER_EMOJI, Settings.PREF_ABC_AFTER_CLIP))
        assertEquals(false, prefs.getBoolean(Settings.PREF_ABC_AFTER_SYMBOL_SPACE, true))
        assertEquals(false, prefs.getBoolean(Settings.PREF_ABC_AFTER_NUMPAD_SPACE, true))
        assertEquals(true, prefs.getBoolean(Settings.PREF_ABC_AFTER_EMOJI, false))
        assertEquals(true, prefs.getBoolean(Settings.PREF_ABC_AFTER_CLIP, false))
        assertEquals(listOf(Settings.PREF_ABC_AFTER_EMOJI, Settings.PREF_ABC_AFTER_CLIP), readSwitchKeyboardAfter(prefs, options))
    }

    // the clipboard history view can't be opened without clipboard history
    @Test fun clipboardEntryOnlyWithClipboardHistory() {
        prefs.edit {
            putBoolean(Settings.PREF_ENABLE_CLIPBOARD_HISTORY, false)
            putBoolean(Settings.PREF_ABC_AFTER_CLIP, true)
        }
        val options = switchKeyboardAfterOptions(prefs)
        assertEquals(false, Settings.PREF_ABC_AFTER_CLIP in options)
        writeSwitchKeyboardAfter(prefs, options, emptyList())
        // a hidden switch keeps its value
        assertEquals(true, prefs.getBoolean(Settings.PREF_ABC_AFTER_CLIP, false))
    }
}
