// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.emoji

import android.app.Dialog
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.InputType
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ImageView
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.core.graphics.ColorUtils
import helium314.keyboard.event.HapticEvent
import helium314.keyboard.keyboard.Key
import helium314.keyboard.keyboard.KeyboardActionListener
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.keyboard.KeyboardTypeface
import helium314.keyboard.keyboard.internal.KeyboardIconsSet
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.keyboard.internal.keyboard_parser.getEmojiDefaultVersion
import helium314.keyboard.keyboard.internal.keyboard_parser.getEmojiNeutralVersion
import helium314.keyboard.latin.AudioAndHapticFeedbackManager
import helium314.keyboard.latin.R
import helium314.keyboard.latin.RichInputMethodManager
import helium314.keyboard.latin.SingleDictionaryFacilitator
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.common.StringUtils
import helium314.keyboard.latin.common.splitOnWhitespace
import helium314.keyboard.latin.dictionary.Dictionary
import helium314.keyboard.latin.dictionary.DictionaryFactory
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.DictionaryInfoUtils
import helium314.keyboard.latin.utils.ToolbarKey
import helium314.keyboard.latin.utils.dpToPx

/**
 * Emoji search inside the keyboard, started with the search key of the emoji view.
 *
 * While it is active, the emoji tab strip is replaced by a search field with a close button, the matching emojis are
 * shown in a row above the keyboard, and [KeyboardSwitcher] shows the alphabet keyboard. Its input goes into the search
 * field instead of the text field of the app (see KeyboardActionListenerImpl), and a picked emoji is entered into the
 * app's text field like any other emoji. Nothing leaves the keyboard window, so the keyboard is not reloaded.
 * Back closes the search like the close button, and not the keyboard.
 * The emojis are looked up in the emoji dictionary of the current language.
 */
class EmojiSearch(
    private val searchBar: View,
    private val resultsView: EmojiSearchResultsView,
    /** the window of the keyboard, for handling back */
    private val keyboardWindow: Dialog,
    private val keyboardActionListener: KeyboardActionListener,
    private val listener: Listener,
) {
    interface Listener {
        /** [emoji] was picked from the results */
        fun onEmojiSearchResultPicked(emoji: String)
        /** the close button or back was pressed */
        fun onEmojiSearchCloseRequested()
    }

    private val context: Context = searchBar.context
    private val field: EmojiSearchField = searchBar.findViewById(R.id.emoji_search_field)
    private val query = StringBuilder()

    // Back closes the search like the close button, and not the keyboard. Since Android 15, the app hides the keyboard
    // on back without asking it, unless the keyboard window has a back callback of its own. Older Android versions and
    // apps without predictive back send back as key events, see LatinIME.onKeyDown.
    private val backCallback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        OnBackInvokedCallback { listener.onEmojiSearchCloseRequested() } else null

    var isActive = false
        private set

    /** the text typed into the search field */
    val queryText get() = query.toString()

    /** the emojis found for [queryText], best match first */
    val results get() = resultsView.emojis

    /** height of the row of results, valid while the search is active */
    val resultsHeight get() = resultsView.rowHeight

    init {
        val colors = Settings.getValues().mColors
        colors.setBackground(searchBar, ColorType.STRIP_BACKGROUND)
        val textColor = colors.get(ColorType.EMOJI_SEARCH_TEXT)
        val fieldColor = colors.get(ColorType.EMOJI_SEARCH_BACKGROUND)
        field.background = GradientDrawable().apply {
            cornerRadius = 20.dpToPx(context.resources).toFloat()
            setColor(fieldColor)
        }
        field.setTextColor(textColor)
        field.setHintTextColor(ColorUtils.blendARGB(fieldColor, textColor, 0.5f))
        KeyboardTypeface.customTypeface()?.let { field.typeface = it }
        KeyboardIconsSet.instance.getNewDrawable(KeyboardIconsSet.NAME_SEARCH_KEY, context)?.let { icon ->
            val size = (field.textSize * 1.3f).toInt()
            icon.setBounds(0, 0, size, size)
            colors.setColor(icon, ColorType.EMOJI_SEARCH_TEXT)
            field.setCompoundDrawablesRelative(icon, null, null, null)
        }

        val closeButton = searchBar.findViewById<ImageView>(R.id.emoji_search_close)
        closeButton.setImageDrawable(KeyboardIconsSet.instance.getNewDrawable(ToolbarKey.CLOSE_HISTORY.name, context))
        colors.setColor(closeButton, ColorType.EMOJI_CATEGORY)
        closeButton.setOnClickListener {
            AudioAndHapticFeedbackManager.getInstance().performHapticAndAudioFeedback(KeyCode.NOT_SPECIFIED, it, HapticEvent.KEY_PRESS)
            listener.onEmojiSearchCloseRequested()
        }

        resultsView.setEmojiViewCallback(object : EmojiViewCallback {
            override fun onPressKey(key: Key) = keyboardActionListener.onPressKey(key.code, 0, 1, HapticEvent.KEY_PRESS)

            override fun onReleaseKey(key: Key) {
                val emoji = if (key.code == KeyCode.MULTIPLE_CODE_POINTS) key.outputText
                    else StringUtils.newSingleCodePointString(key.code)
                if (emoji != null) listener.onEmojiSearchResultPicked(emoji)
                keyboardActionListener.onReleaseKey(key.code, false)
            }

            override fun getDescription(emoji: String): String? = if (Settings.getValues().mShowEmojiDescriptions)
                dictionaryFacilitator?.getWordProperty(getEmojiNeutralVersion(emoji))?.let {
                    if (it.mHasShortcuts) it.mShortcutTargets[0]?.mWord else null
                } else null

            override fun onRemoveRecentsKey(key: Key?) {}
        })
    }

    /** Shows the empty search field and the row for the results */
    fun start() {
        isActive = true
        query.setLength(0)
        resultsView.prepare()
        showQuery()
        searchBar.visibility = View.VISIBLE
        resultsView.visibility = View.VISIBLE
        setBackClosesSearch(true)
    }

    /** Hides search field and results, and forgets the query, so the next search starts empty */
    fun stop() {
        isActive = false
        query.setLength(0)
        showQuery()
        resultsView.setEmojis(emptyList())
        searchBar.visibility = View.GONE
        resultsView.visibility = View.GONE
        setBackClosesSearch(false)
    }

    /** Adds [text] typed on the keyboard to the query */
    fun addText(text: String) {
        query.append(text)
        showQuery()
        search()
    }

    /** Removes the last character of the query, like backspace */
    fun deleteLastCharacter() {
        if (query.isEmpty()) return
        query.setLength(query.length - Character.charCount(query.codePointBefore(query.length)))
        showQuery()
        search()
    }

    private fun setBackClosesSearch(closes: Boolean) {
        if (backCallback == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val dispatcher = keyboardWindow.onBackInvokedDispatcher
        if (closes) dispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, backCallback)
        else dispatcher.unregisterOnBackInvokedCallback(backCallback)
    }

    private fun showQuery() {
        field.text = query
        field.setCaretShown(isActive)
    }

    private fun search() {
        resultsView.setEmojis(if (query.isBlank()) emptyList() else findEmojis(context, query.toString()))
    }

    companion object {
        /** what the keyboard used for typing the query is made for: plain text, and done ends the search */
        @JvmField
        val EDITOR_INFO = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_DONE
        }

        private var dictionaryFacilitator: SingleDictionaryFacilitator? = null

        /**
         * Emojis in the emoji dictionary of the current language that match the words in [text], in any order,
         * best match first. Partial and slightly misspelled words match too.
         */
        @JvmStatic
        fun findEmojis(context: Context, text: String): List<String> {
            initDictionaryFacilitator(context)
            val facilitator = dictionaryFacilitator ?: return emptyList()
            // avoid a crash in SingleDictionaryFacilitator.getSuggestions
            if (KeyboardSwitcher.getInstance().keyboard == null) return emptyList()
            return facilitator.getSuggestions(text.splitOnWhitespace()).filter { it.isEmoji }
                .map { getEmojiDefaultVersion(it.word) }
        }

        @JvmStatic
        fun closeDictionaryFacilitator() {
            dictionaryFacilitator?.closeDictionaries()
            dictionaryFacilitator = null
        }

        private fun initDictionaryFacilitator(context: Context) {
            val locale = RichInputMethodManager.getInstance().currentSubtype.locale
            if (dictionaryFacilitator?.isForLocale(locale) != true) {
                dictionaryFacilitator?.closeDictionaries()
                dictionaryFacilitator = DictionaryInfoUtils.getCachedDictForLocaleAndType(locale, Dictionary.TYPE_EMOJI, context)
                    ?.let { DictionaryFactory.getDictionary(it, locale) }?.let { SingleDictionaryFacilitator(it) }
            }
        }
    }
}
