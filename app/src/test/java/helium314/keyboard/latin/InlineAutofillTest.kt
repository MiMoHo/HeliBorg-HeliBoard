// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin

import android.app.KeyguardManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.util.Size
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InlineSuggestion
import android.view.inputmethod.InlineSuggestionInfo
import android.view.inputmethod.InlineSuggestionsResponse
import android.widget.inline.InlinePresentationSpec
import androidx.core.content.edit
import androidx.core.view.isVisible
import helium314.keyboard.ShadowInputMethodService
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.suggestions.SuggestionStripView
import helium314.keyboard.latin.utils.InlineAutofillUtils
import helium314.keyboard.latin.utils.ToolbarMode
import helium314.keyboard.latin.utils.prefs
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Inline autofill suggestions are requested even when the toolbar settings hide suggestions. The strip
 * then shows them only while the autofill service provides them, and returns to the configured state after.
 */
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowInputMethodService::class])
class InlineAutofillTest {
    private val latinIME = Robolectric.setupService(LatinIME::class.java)
    private val switcher get() = KeyboardSwitcher.getInstance()
    private val stripContainer get() = switcher.stripContainer
    private val strip get() = stripContainer.findViewById<SuggestionStripView>(R.id.suggestion_strip_view)
    private val suggestionsStrip get() = strip.findViewById<ViewGroup>(R.id.suggestions_strip)
    private val toolbarContainer get() = strip.findViewById<View>(R.id.toolbar_container)

    @BeforeTest
    fun reset() {
        ShadowInputMethodService.reset()
        latinIME.prefs().edit { clear() }
    }

    @Test fun requestIsCreatedWhenTheStripIsHidden() {
        startInput(ToolbarMode.HIDDEN)
        assertNotNull(latinIME.onCreateInlineSuggestionsRequest(Bundle()))
    }

    @Test fun requestIsCreatedWhenTheStripShowsOnlyToolbarKeys() {
        startInput(ToolbarMode.TOOLBAR_KEYS)
        assertNotNull(latinIME.onCreateInlineSuggestionsRequest(Bundle()))
    }

    @Test fun noRequestWhileTheDeviceIsLocked() {
        shadowOf(latinIME.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).setIsDeviceLocked(true)
        startInput(ToolbarMode.EXPANDABLE)
        assertNull(latinIME.onCreateInlineSuggestionsRequest(Bundle()))
    }

    @Test fun hiddenStripAppearsOnlyWhileInlineSuggestionsArePresent() {
        startInput(ToolbarMode.HIDDEN)
        assertFalse(stripContainer.isVisible)

        assertTrue(latinIME.onInlineSuggestionsResponse(response(2)))
        assertTrue(stripContainer.isVisible)
        assertTrue(strip.isVisible)
        assertTrue(suggestionsStrip.isVisible)
        assertTrue(showsInlineSuggestions())

        assertFalse(latinIME.onInlineSuggestionsResponse(response(0)))
        assertFalse(stripContainer.isVisible)
        assertFalse(showsInlineSuggestions())
    }

    @Test fun hiddenStripStaysWhileTheKeyboardUpdatesItsSuggestions() {
        startInput(ToolbarMode.HIDDEN)
        latinIME.onInlineSuggestionsResponse(response(1))
        latinIME.setNeutralSuggestionStrip()
        switcher.reloadMainKeyboard() // like a shift state change
        assertTrue(stripContainer.isVisible)
        assertTrue(showsInlineSuggestions())
    }

    @Test fun hiddenStripIsHiddenAgainWhenInputFinishes() {
        startInput(ToolbarMode.HIDDEN)
        latinIME.onInlineSuggestionsResponse(response(1))
        assertTrue(stripContainer.isVisible)
        latinIME.onFinishInputView(true)
        assertFalse(stripContainer.isVisible)
        assertFalse(showsInlineSuggestions())
    }

    @Test fun hiddenStripIsHiddenAgainInTheNextField() {
        startInput(ToolbarMode.HIDDEN)
        latinIME.onInlineSuggestionsResponse(response(1))
        assertTrue(stripContainer.isVisible)
        latinIME.onStartInputView(passwordField(), false)
        assertFalse(stripContainer.isVisible)
        assertFalse(showsInlineSuggestions())
    }

    @Test fun hiddenStripIsHiddenAgainWhenClosed() {
        startInput(ToolbarMode.HIDDEN)
        latinIME.onInlineSuggestionsResponse(response(1))
        assertTrue(stripContainer.isVisible)
        latinIME.removeExternalSuggestions() // close button next to the inline suggestions
        assertFalse(stripContainer.isVisible)
        assertFalse(showsInlineSuggestions())
    }

    @Test fun toolbarKeysComeBackAfterInlineSuggestions() {
        startInput(ToolbarMode.TOOLBAR_KEYS)
        assertTrue(toolbarContainer.isVisible)
        assertFalse(suggestionsStrip.isVisible)

        assertTrue(latinIME.onInlineSuggestionsResponse(response(1)))
        assertFalse(toolbarContainer.isVisible)
        assertTrue(suggestionsStrip.isVisible)
        assertTrue(showsInlineSuggestions())

        latinIME.setNeutralSuggestionStrip() // the keyboard's own suggestions must not replace them
        assertFalse(toolbarContainer.isVisible)
        assertTrue(showsInlineSuggestions())

        latinIME.onInlineSuggestionsResponse(response(0))
        assertTrue(stripContainer.isVisible)
        assertTrue(toolbarContainer.isVisible)
        assertFalse(suggestionsStrip.isVisible)
        assertFalse(showsInlineSuggestions())
    }

    @Test fun clipboardSuggestionDoesNotReplaceInlineSuggestions() {
        startInput(ToolbarMode.TOOLBAR_KEYS)
        copyToClipboard("copied")
        latinIME.onInlineSuggestionsResponse(response(1))
        latinIME.setNeutralSuggestionStrip()
        assertTrue(showsInlineSuggestions())

        latinIME.onInlineSuggestionsResponse(response(0))
        assertTrue(latinIME.tryShowClipboardSuggestion()) // the clip is shown again once they are gone
    }

    @Test fun stripWithSuggestionsShowsInlineSuggestionsAsBefore() {
        startInput(ToolbarMode.SUGGESTION_STRIP)
        assertTrue(latinIME.onInlineSuggestionsResponse(response(1)))
        assertTrue(stripContainer.isVisible)
        assertTrue(showsInlineSuggestions())

        latinIME.onInlineSuggestionsResponse(response(0))
        assertTrue(stripContainer.isVisible)
    }

    // ------- helpers ---------

    private fun passwordField() = EditorInfo().apply {
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
    }

    private fun startInput(mode: ToolbarMode) {
        latinIME.prefs().edit { putString(Settings.PREF_TOOLBAR_MODE, mode.name) }
        latinIME.setInputView(latinIME.onCreateInputView())
        latinIME.onStartInputView(passwordField(), false)
        ShadowLooper.idleMainLooper()
    }

    private fun copyToClipboard(text: String) {
        val clipboard = latinIME.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("", text))
        // a clip counts as recent only shortly after copying, but Robolectric stamps it with its own clock
        val description = clipboard.primaryClip!!.description
        description.javaClass.getMethod("setTimestamp", Long::class.javaPrimitiveType).invoke(description, System.currentTimeMillis())
    }

    private fun showsInlineSuggestions() = suggestionsStrip.isVisible && containsInlineView(suggestionsStrip)

    private fun containsInlineView(group: ViewGroup): Boolean = (0 until group.childCount).any {
        val child = group.getChildAt(it)
        child is InlineAutofillUtils.InlineContentClipView || (child is ViewGroup && containsInlineView(child))
    }

    // the factory methods are hidden test API, the classes can't be built otherwise
    private fun response(count: Int): InlineSuggestionsResponse {
        val spec = InlinePresentationSpec.Builder(Size(100, 50), Size(740, 50)).build()
        val newInfo = InlineSuggestionInfo::class.java.getMethod("newInlineSuggestionInfo", InlinePresentationSpec::class.java,
            String::class.java, Array<String>::class.java, String::class.java, Boolean::class.javaPrimitiveType)
        val newSuggestion = InlineSuggestion::class.java.getMethod("newInlineSuggestion", InlineSuggestionInfo::class.java)
        val suggestions = List(count) {
            val info = newInfo.invoke(null, spec, InlineSuggestionInfo.SOURCE_AUTOFILL, null, InlineSuggestionInfo.TYPE_SUGGESTION, false)
            newSuggestion.invoke(null, info) as InlineSuggestion
        }
        return InlineSuggestionsResponse::class.java.getMethod("newInlineSuggestionsResponse", List::class.java)
            .invoke(null, suggestions) as InlineSuggestionsResponse
    }
}
