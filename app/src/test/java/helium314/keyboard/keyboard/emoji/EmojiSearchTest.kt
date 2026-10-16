// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.emoji

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.core.content.edit
import helium314.keyboard.ShadowInputMethodManager2
import helium314.keyboard.ShadowInputMethodService
import helium314.keyboard.ShadowProximityInfo
import helium314.keyboard.event.HapticEvent
import helium314.keyboard.keyboard.Key
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.keyboard.MainKeyboardView
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.NgramContext
import helium314.keyboard.latin.R
import helium314.keyboard.latin.RichInputMethodManager
import helium314.keyboard.latin.SuggestedWords.SuggestedWordInfo
import helium314.keyboard.latin.common.ComposedData
import helium314.keyboard.latin.common.Constants
import helium314.keyboard.latin.dictionary.Dictionary
import helium314.keyboard.latin.dictionary.DictionaryFactory
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.settings.SettingsValuesForSuggestion
import helium314.keyboard.latin.utils.DictionaryInfoUtils
import helium314.keyboard.latin.utils.dpToPx
import helium314.keyboard.latin.utils.prefs
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter
import java.io.File
import java.time.Duration
import java.util.Locale
import kotlin.math.abs
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Emoji search inside the keyboard: the emoji tab strip turns into a search field, typing goes into it,
 * and the emojis found are shown in a row above the keyboard. The emoji dictionary is replaced by
 * [FakeEmojiDictionary], as the real one needs the native library.
 */
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [
    ShadowInputMethodManager2::class,
    ShadowInputMethodService::class,
    ShadowProximityInfo::class,
    ShadowDictionaryFactory::class,
])
class EmojiSearchTest {
    private val latinIME = Robolectric.setupService(LatinIME::class.java)
    private val switcher = KeyboardSwitcher.getInstance()
    private val listener get() = latinIME.mKeyboardActionListener
    private lateinit var inputView: View

    @BeforeTest fun setUp() {
        // the search key is only there with an emoji dictionary for an enabled language
        val locale = RichInputMethodManager.getInstance().currentSubtype.locale
        File(DictionaryInfoUtils.getCacheDirectoryForLocale(locale, latinIME), "emoji_test.dict").createNewFile()
        RecentEmojis.clear()
        latinIME.prefs().edit { putBoolean(Settings.PREF_AUTO_CORRECTION, true) }
        ShadowInputMethodService.reset()
        ShadowInputMethodService.text = "Hi "
        ShadowInputMethodService.selectionStart = 3
        ShadowInputMethodService.selectionEnd = 3
        switcher.onCreateInputView(latinIME, true) // there needs to be a keyboard view for starting input
        val editorInfo = EditorInfo().apply { inputType = ShadowInputMethodService.currentInputType; initialSelStart = 3; initialSelEnd = 3 }
        latinIME.onStartInput(editorInfo, false)
        latinIME.onStartInputView(editorInfo, false)
        idle()
        inputView = switcher.onCreateInputView(latinIME, true)
        Robolectric.buildActivity(Activity::class.java).setup().get().setContentView(inputView)
        switcher.reloadMainKeyboard()
        switcher.setEmojiKeyboard()
        idle()
    }

    private fun idle(millis: Long = 0) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis))

    private val editorText get() = ShadowInputMethodService.text
    private val searchBar get() = inputView.findViewById<View>(R.id.emoji_search_bar)
    private val searchField get() = inputView.findViewById<TextView>(R.id.emoji_search_field)
    private val resultsRow get() = (inputView.findViewById<ViewGroup>(R.id.emoji_search_results)).getChildAt(0) as EmojiPageKeyboardView
    private val shownResults get() = resultsRow.keyboard?.sortedKeys.orEmpty().map { it.label }

    private fun searchKey(): Key? = inputView.findViewById<MainKeyboardView>(R.id.bottom_row_keyboard)
        .keyboard?.sortedKeys?.firstOrNull { it.code == KeyCode.EMOJI_SEARCH }

    /** taps the search key of the emoji view, like a finger would */
    private fun tapSearchKey() {
        val key = assertNotNull(searchKey(), "search key in the emoji view")
        tap(inputView.findViewById(R.id.bottom_row_keyboard), key)
        idle()
    }

    private fun tap(view: View, key: Key) {
        val x = view.paddingLeft + key.x + key.width / 2f
        val y = view.paddingTop + key.y + key.height / 2f
        val down = SystemClock.uptimeMillis()
        listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP).forEach { action ->
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0)
            view.dispatchTouchEvent(event)
            event.recycle()
        }
        idle(100) // the emoji view enters an emoji a little after releasing it
    }

    /** types [text] on the keyboard, key by key */
    private fun type(text: String) = text.codePoints().forEach { pressKey(it) }

    private fun pressKey(code: Int) {
        listener.onPressKey(code, 0, 1, HapticEvent.KEY_PRESS)
        listener.onCodeInput(code, Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE, false)
        listener.onReleaseKey(code, false)
        idle()
    }

    /** The keyboard is shown because the app asked for it. Robolectric does not show the keyboard window, so this is set here. */
    private fun keyboardShownOnRequest() = ReflectionHelpers.setField(latinIME, "mShowInputRequested", true)

    /**
     * Back as InputMethodService gets it as key events: on Android 12 and older, from apps without predictive back,
     * and from the default back callback of InputMethodService. Returns whether the keyboard consumed it.
     */
    private fun pressBack(): Boolean {
        val state = KeyEvent.DispatcherState() // tracks the key from down to up, like the keyboard window does
        val down = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK).dispatch(latinIME, state, null)
        val up = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK).dispatch(latinIME, state, null)
        idle()
        return down && up
    }

    /**
     * Back on Android 13+ in an app with predictive back: the app calls the topmost back callback of the keyboard
     * window. If there is none but the default one of InputMethodService, which registers it with PRIORITY_SYSTEM
     * since Android 15, the app hides the keyboard with an animation instead, and the keyboard does not see the back
     * at all. Returns false then. Robolectric does not show the keyboard window, so the callbacks are still waiting in
     * the dispatcher of the window.
     */
    private fun backGesture(): Boolean {
        val callbacks = ReflectionHelpers.getField<List<android.util.Pair<OnBackInvokedCallback, Int>>>(
            latinIME.window.onBackInvokedDispatcher, "mCallbacks")
        val top = callbacks.withIndex().maxWithOrNull(compareBy({ it.value.second }, { it.index }))?.value
        if (top == null || top.second < OnBackInvokedDispatcher.PRIORITY_DEFAULT) return false
        top.first.onBackInvoked()
        idle()
        return true
    }

    /** HeliBoard opts in to predictive back in the manifest, which Robolectric does not read */
    private fun enablePredictiveBack() = ReflectionHelpers.callInstanceMethod<Unit>(latinIME.applicationInfo,
        "setEnableOnBackInvokedCallback", ClassParameter.from(Boolean::class.javaPrimitiveType, true))

    private fun drawSearchField(): Bitmap = Bitmap.createBitmap(searchField.width, searchField.height, Bitmap.Config.ARGB_8888)
        .also { searchField.draw(Canvas(it)) }

    /**
     * Asserts that the search field draws a caret in the text color at [offset] of the query, like a focused text field:
     * shown right away, hidden after half a second, and shown again after another half second.
     */
    private fun assertCaretBlinksAt(offset: Int) {
        val shown = drawSearchField()
        idle(500)
        val hidden = drawSearchField()
        idle(500)
        assertTrue(drawSearchField().sameAs(shown), "caret shown again")
        // the caret is where both drawings differ
        val caret = (0 until shown.width).flatMap { x -> (0 until shown.height).map { y -> x to y } }
            .filter { (x, y) -> shown.getPixel(x, y) != hidden.getPixel(x, y) }
        assertTrue(caret.isNotEmpty(), "caret blinks")
        val expectedX = searchField.compoundPaddingLeft + searchField.layout.getPrimaryHorizontal(offset)
        val xRange = caret.minOf { it.first }..caret.maxOf { it.first }
        val yRange = caret.minOf { it.second }..caret.maxOf { it.second }
        assertTrue(xRange.all { abs(it - expectedX) <= 2.dpToPx(latinIME.resources) }, "caret at $expectedX, drawn at $xRange")
        assertTrue(yRange.last - yRange.first + 1 >= searchField.textSize, "caret as high as the text: $yRange")
        assertTrue(caret.all { (x, y) -> shown.getPixel(x, y) == searchField.currentTextColor }, "caret in the text color")
    }

    private fun assertSearchClosed() {
        assertTrue(switcher.isShowingEmojiPalettes, "emoji view shown")
        assertEquals(View.VISIBLE, switcher.emojiTabStrip.visibility, "emoji tabs shown")
        assertEquals(View.GONE, searchBar?.visibility, "search field hidden")
        assertEquals("Hi ", editorText)
    }

    private fun assertSearchShown() {
        assertEquals(View.VISIBLE, searchBar?.visibility, "search field shown")
        assertEquals(View.GONE, switcher.emojiTabStrip.visibility, "emoji tabs hidden")
        assertFalse(switcher.isShowingEmojiPalettes, "emoji view hidden")
        assertTrue(switcher.keyboard!!.mId.element.isAlphabet, "alphabet keyboard shown")
    }

    @Test fun searchKeyShowsTheSearchInTheKeyboard() {
        tapSearchKey()
        assertNull(shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity, "no activity started")
        assertSearchShown()
        assertEquals(View.VISIBLE, inputView.findViewById<View>(R.id.emoji_search_results).visibility, "results row shown")
        assertEquals("", searchField.text.toString())
    }

    @Test fun typedLettersGoIntoTheSearchFieldAndNotIntoTheTextField() {
        tapSearchKey()
        type("cax")
        pressKey(KeyCode.DELETE)
        type("t f")
        assertEquals("Hi ", editorText)
        assertEquals("cat f", searchField.text.toString())
        assertSearchShown()
    }

    @Test fun resultsRowShowsTheEmojisFoundForTheQuery() {
        tapSearchKey()
        type("cat")
        assertEquals(listOf("🐱", "🐈"), shownResults)
        type(" face") // emojis matching both words come first
        assertEquals("🐱", shownResults.first())
        assertTrue("😀" in shownResults)
        repeat(" face".length + 3) { pressKey(KeyCode.DELETE) }
        assertEquals("", searchField.text.toString())
        assertEquals(emptyList(), shownResults)
    }

    @Test fun pickingAResultEntersTheEmojiIntoTheTextField() {
        tapSearchKey()
        type("cat")
        tap(resultsRow, resultsRow.keyboard!!.sortedKeys.first())
        assertEquals("Hi 🐱", editorText)
        assertEquals("🐱", RecentEmojis.get().firstOrNull())
        // the search stays, so more emojis can be picked
        assertSearchShown()
        assertEquals("cat", searchField.text.toString())
        tap(resultsRow, resultsRow.keyboard!!.sortedKeys[1])
        assertEquals("Hi 🐱🐈", editorText)
    }

    @Test fun enterPicksTheBestMatchAndReturnsToTheEmojiView() {
        tapSearchKey()
        type("heart")
        pressKey(Constants.CODE_ENTER)
        assertEquals("Hi ❤️", editorText)
        assertTrue(switcher.isShowingEmojiPalettes)
    }

    @Test fun closingReturnsToTheEmojiViewWithAnEmptySearch() {
        tapSearchKey()
        type("cat")
        inputView.findViewById<View>(R.id.emoji_search_close).performClick()
        idle()
        assertSearchClosed()

        tapSearchKey()
        assertEquals("", searchField.text.toString())
        assertEquals(emptyList(), shownResults)
    }

    @Test fun backClosesOnlyTheSearch() {
        keyboardShownOnRequest()
        tapSearchKey()
        type("cat")
        assertTrue(pressBack(), "back consumed")
        assertEquals(0, ShadowInputMethodService.hideRequests, "keyboard not hidden")
        assertSearchClosed()

        // without the search, back hides the keyboard as before
        assertTrue(pressBack(), "back consumed")
        assertEquals(1, ShadowInputMethodService.hideRequests, "keyboard hidden")
    }

    @Test fun backGestureClosesOnlyTheSearch() {
        enablePredictiveBack()
        tapSearchKey()
        type("cat")
        assertTrue(backGesture(), "back goes to the keyboard")
        assertEquals(0, ShadowInputMethodService.hideRequests, "keyboard not hidden")
        assertSearchClosed()

        assertFalse(backGesture(), "without the search, the app hides the keyboard on back as before")
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun searchFieldShowsABlinkingCaret() {
        tapSearchKey()
        assertCaretBlinksAt(0) // before the hint, like an empty text field
        type("cat")
        assertCaretBlinksAt(3)
        repeat(3) { pressKey(KeyCode.DELETE) }
        assertCaretBlinksAt(0)
    }

    @Test fun emojiKeyReturnsToTheEmojiViewWithAnEmptySearch() {
        tapSearchKey()
        type("cat")
        pressKey(KeyCode.EMOJI)
        assertTrue(switcher.isShowingEmojiPalettes, "emoji view shown")
        assertEquals(View.GONE, searchBar?.visibility, "search field hidden")
        tapSearchKey()
        assertEquals("", searchField.text.toString())
    }

    @Test fun hidingTheKeyboardEndsTheSearch() { // #2698
        tapSearchKey()
        type("cat")
        latinIME.onFinishInputView(false)
        idle()
        assertEquals(View.GONE, searchBar?.visibility, "search field hidden")
        assertTrue(switcher.keyboard!!.mId.element.isAlphabet, "alphabet keyboard")
        assertEquals(View.VISIBLE, switcher.mainKeyboardView.visibility, "alphabet keyboard shown")
        switcher.setEmojiKeyboard()
        idle()
        assertTrue(switcher.isShowingEmojiPalettes, "emoji view shown")
        assertEquals(View.VISIBLE, switcher.emojiTabStrip.visibility, "emoji tabs shown")
        assertEquals(View.GONE, searchBar?.visibility, "search field hidden")
    }

    @Test fun keyboardKeepsItsHeight() {
        val frame = inputView.findViewById<View>(R.id.main_keyboard_frame)
        val emojiViewHeight = frame.height
        assertTrue(emojiViewHeight > 0)
        tapSearchKey()
        assertSearchShown()
        assertEquals(emojiViewHeight.toFloat(), frame.height.toFloat(), 2f, "height of the keyboard while searching")
    }
}

/** stands in for an emoji dictionary: finds emojis by the start of the words in their names */
private class FakeEmojiDictionary(locale: Locale) : Dictionary(TYPE_EMOJI, locale) {
    private val emojis = listOf("🐱" to "cat face", "🐈" to "cat", "😀" to "grinning face", "❤️" to "red heart")

    override fun getSuggestions(composedData: ComposedData, ngramContext: NgramContext, proximityInfoHandle: Long,
        settingsValuesForSuggestion: SettingsValuesForSuggestion, sessionId: Int, weightForLocale: Float,
        inOutWeightOfLangModelVsSpatialModel: FloatArray
    ): ArrayList<SuggestedWordInfo> {
        val typed = composedData.mTypedWord.lowercase()
        return ArrayList(emojis.filter { (_, name) -> name.split(" ").any { it.startsWith(typed) } }
            .mapIndexed { i, (emoji, _) ->
                SuggestedWordInfo(emoji, "", 1000000 - i, SuggestedWordInfo.KIND_CORRECTION, this,
                    SuggestedWordInfo.NOT_AN_INDEX, SuggestedWordInfo.NOT_A_CONFIDENCE)
            })
    }

    override fun isInDictionary(word: String) = false
}

@Implements(DictionaryFactory::class)
class ShadowDictionaryFactory {
    companion object {
        @Implementation
        @JvmStatic
        fun getDictionary(file: File, locale: Locale): Dictionary? =
            if (file.name.startsWith("emoji_")) FakeEmojiDictionary(locale) else null
    }
}
