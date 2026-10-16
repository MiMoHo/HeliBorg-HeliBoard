// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.core.content.edit
import helium314.keyboard.ShadowInputMethodService
import helium314.keyboard.event.Event
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.SuggestedWords.SuggestedWordInfo
import helium314.keyboard.latin.common.Constants
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.prefs
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.time.Duration
import kotlin.math.abs
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The space bar shows when a space is pending, i.e. when it will be inserted automatically before the next word
 * (phantom space after picking a suggestion, after punctuation or after gesture typing), see #1805.
 * Checked on the drawn space bar, so the tests cover the way from the input logic to what the user sees.
 */
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowInputMethodService::class, ShadowLooper::class])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PendingSpaceIndicatorTest {
    private val latinIME = Robolectric.setupService(LatinIME::class.java)
    private val keyboardSwitcher = KeyboardSwitcher.getInstance()
    private lateinit var spaceBarWithoutPendingSpace: IntArray

    init {
        keyboardSwitcher.onCreateInputView(latinIME, true)
        keyboardSwitcher.reloadMainKeyboard()
    }

    @BeforeTest fun reset() {
        latinIME.prefs().edit { clear() }
        ShadowInputMethodService.reset()
        setText("")
        spaceBarWithoutPendingSpace = drawSpaceBar()
    }

    @Test fun `picking a suggestion shows the pending space`() {
        pickSuggestion("hello")
        assertPendingSpaceShown(true)
    }

    @Test fun `no pending space if autospace after suggestion is off`() {
        latinIME.prefs().edit { putBoolean(Settings.PREF_AUTOSPACE_AFTER_SUGGESTION, false) }
        pickSuggestion("hello")
        assertPendingSpaceShown(false)
    }

    @Test fun `pending space goes away when the next word starts`() {
        pickSuggestion("hello")
        assertPendingSpaceShown(true)
        input('w')
        assertEquals("hello w", ShadowInputMethodService.text)
        assertPendingSpaceShown(false)
    }

    @Test fun `pending space stays after a comma until the next word`() {
        pickSuggestion("hello")
        input(',')
        assertEquals("hello,", ShadowInputMethodService.text)
        assertPendingSpaceShown(true)
        input('w')
        assertEquals("hello, w", ShadowInputMethodService.text)
        assertPendingSpaceShown(false)
    }

    @Test fun `pending space goes away when punctuation takes it`() {
        pickSuggestion("hello")
        assertPendingSpaceShown(true)
        input('(')
        assertEquals("hello (", ShadowInputMethodService.text)
        assertPendingSpaceShown(false)
    }

    @Test fun `pending space goes away on backspace`() {
        pickSuggestion("hello")
        assertPendingSpaceShown(true)
        latinIME.onEvent(Event.createSoftwareKeypressEvent(Event.NOT_A_CODE_POINT, KeyCode.DELETE, 0,
            Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE, false))
        assertPendingSpaceShown(false)
    }

    @Test fun `pending space goes away when the cursor moves`() {
        pickSuggestion("hello")
        assertPendingSpaceShown(true)
        setCursorPosition(2)
        assertPendingSpaceShown(false)
    }

    @Test fun `pending space goes away in a new text field`() {
        pickSuggestion("hello")
        assertPendingSpaceShown(true)
        setText("")
        assertPendingSpaceShown(false)
    }

    @Test fun `autospace after punctuation shows the pending space`() {
        latinIME.prefs().edit { putBoolean(Settings.PREF_AUTOSPACE_AFTER_PUNCTUATION, true) }
        "hello,".forEach { input(it) }
        assertPendingSpaceShown(true)
    }

    @Test fun `no pending space after punctuation if autospace after punctuation is off`() {
        "hello,".forEach { input(it) }
        assertPendingSpaceShown(false)
    }

    @Test fun `gesture typing shows the pending space`() {
        glideTypingInput("hello")
        assertPendingSpaceShown(true)
    }

    @Test fun `pending space replaces the language name with the centred symbol until the next word`() {
        // the space bar text is drawn in place of the language name
        showOnSpaceBar("English (US)")
        val nameWithoutPending = drawSpaceBar()
        assertFalse(nameWithoutPending.contentEquals(spaceBarWithoutPendingSpace), "space bar shows no name")
        pickSuggestion("hello")
        val pendingWithName = drawSpaceBar()
        input('w')
        assertEquals("hello w", ShadowInputMethodService.text)
        assertTrue(drawSpaceBar().contentEquals(nameWithoutPending), "space bar does not show the name again")

        showOnSpaceBar("")
        pickSuggestion("world")
        val pendingWithoutName = drawSpaceBar()
        assertSymbolCentred(pendingWithoutName)
        assertTrue(pendingWithName.contentEquals(pendingWithoutName), "space bar shows more than the centred symbol")
    }

    // ------- helper functions ---------

    private fun assertPendingSpaceShown(shown: Boolean) {
        val looksPending = !drawSpaceBar().contentEquals(spaceBarWithoutPendingSpace)
        assertEquals(shown, looksPending, if (shown) "space bar shows no pending space" else "space bar shows a pending space")
    }

    /** the pixels that differ from the space bar without pending space are centred horizontally */
    private fun assertSymbolCentred(pixels: IntArray) {
        val space = keyboardSwitcher.mainKeyboardView.keyboard!!.getKey(Constants.CODE_SPACE)!!
        val columns = pixels.indices.filter { pixels[it] != spaceBarWithoutPendingSpace[it] }.map { it % space.width }
        assertTrue(columns.isNotEmpty(), "space bar shows no pending space")
        val symbolCentre = (columns.min() + columns.max() + 1) / 2f
        val keyCentre = space.drawX - space.x + space.drawWidth / 2f
        assertTrue(abs(symbolCentre - keyCentre) <= 2, "symbol centre $symbolCentre is not at the key centre $keyCentre")
    }

    /** pixels of the space bar as drawn now */
    private fun drawSpaceBar(): IntArray {
        // run pending messages and let the language name on the space bar finish fading
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3))
        val view = keyboardSwitcher.mainKeyboardView
        view.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val space = view.keyboard!!.getKey(Constants.CODE_SPACE)!!
        val pixels = IntArray(space.width * space.height)
        bitmap.getPixels(pixels, 0, space.width, space.x + view.paddingLeft, space.y + view.paddingTop, space.width, space.height)
        return pixels
    }

    private fun showOnSpaceBar(text: String) {
        latinIME.prefs().edit { putString(Settings.PREF_SPACE_BAR_TEXT, text) }
        keyboardSwitcher.reloadMainKeyboard()
    }

    private fun input(char: Char) = latinIME.onEvent(Event.createEventForCodePointFromUnknownSource(char.code))

    // like selecting a suggestion from the strip
    private fun pickSuggestion(suggestion: String) =
        latinIME.pickSuggestionManually(SuggestedWordInfo(suggestion, "", 0, 0, null, 0, 0))

    private fun glideTypingInput(word: String) {
        val info = SuggestedWordInfo(word, "", 0, 0, null, 0, 0)
        val suggestedWords = SuggestedWords(ArrayList(listOf(info)), null, info, true, false, false, 0, 0)
        latinIME.mInputLogic.onUpdateTailBatchInputCompleted(Settings.getValues(), suggestedWords, keyboardSwitcher)
    }

    private fun setCursorPosition(position: Int) {
        val oldStart = ShadowInputMethodService.selectionStart
        val oldEnd = ShadowInputMethodService.selectionEnd
        ShadowInputMethodService.selectionStart = position
        ShadowInputMethodService.selectionEnd = position
        latinIME.onUpdateSelection(oldStart, oldEnd, position, position,
            ShadowInputMethodService.composingStart, ShadowInputMethodService.composingEnd)
    }

    // sets the text and starts input like in a new text field
    private fun setText(text: String) {
        ShadowInputMethodService.text = text
        ShadowInputMethodService.selectionStart = text.length
        ShadowInputMethodService.selectionEnd = text.length
        ShadowInputMethodService.composingStart = -1
        ShadowInputMethodService.composingEnd = -1
        val editorInfo = EditorInfo().apply { inputType = ShadowInputMethodService.currentInputType }
        latinIME.mHandler.onStartInput(editorInfo, false)
        latinIME.mHandler.onStartInputView(editorInfo, false)
        shadowOf(Looper.getMainLooper()).idle()
    }
}
