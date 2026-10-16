// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.emoji

import android.content.Context
import android.util.AttributeSet
import android.widget.HorizontalScrollView
import helium314.keyboard.keyboard.Key
import helium314.keyboard.keyboard.Keyboard
import helium314.keyboard.keyboard.KeyboardElement
import helium314.keyboard.keyboard.KeyboardLayoutSet
import helium314.keyboard.keyboard.internal.KeyboardBuilder
import helium314.keyboard.keyboard.internal.KeyboardParams
import helium314.keyboard.keyboard.internal.keyboard_parser.EMOJI_HINT_LABEL
import helium314.keyboard.keyboard.internal.keyboard_parser.getCode
import helium314.keyboard.keyboard.internal.keyboard_parser.getEmojiKeyDimensions
import helium314.keyboard.keyboard.internal.keyboard_parser.getEmojiPopupSpec
import helium314.keyboard.keyboard.internal.keyboard_parser.loadEmojiDefaultVersionsAndPopupSpecs
import helium314.keyboard.latin.RichInputMethodSubtype
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.ResourceUtils
import helium314.keyboard.latin.utils.prefs

/**
 * A single row of emojis that scrolls horizontally: the results of the [EmojiSearch].
 * The emojis are keys of an [EmojiPageKeyboardView] like in the emoji view, so they look the same and offer the
 * same skin tone popups and descriptions on long press.
 */
class EmojiSearchResultsView(context: Context, attrs: AttributeSet?) : HorizontalScrollView(context, attrs) {
    private val keyboardView = EmojiPageKeyboardView(context, null)
    private var templateKeyboard: Keyboard? = null
    private lateinit var keyboardParams: KeyboardParams
    private var keyWidth = 0f
    private var keyHeight = 0f
    private var keyboardWidth = 0
    private var columnCount = 1

    /** the emojis shown, in this order */
    var emojis: List<String> = emptyList()
        private set

    /** height of the row, known after [prepare] */
    var rowHeight = 0
        private set

    init {
        isHorizontalScrollBarEnabled = false
        addView(keyboardView, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
    }

    fun setEmojiViewCallback(callback: EmojiViewCallback) = keyboardView.setEmojiViewCallback(callback)

    fun setHardwareAcceleratedDrawingEnabled(enabled: Boolean) = keyboardView.setHardwareAcceleratedDrawingEnabled(enabled)

    /** Sets up the emoji keys for the current settings and clears the row. Done whenever a search starts. */
    fun prepare() {
        keyboardWidth = ResourceUtils.getKeyboardWidth(context, Settings.getValues())
        val layoutSet = KeyboardLayoutSet.Builder(context, null, null).setSubtype(RichInputMethodSubtype.emojiSubtype)
            .setKeyboardGeometry(keyboardWidth, EmojiLayoutParams(resources).emojiKeyboardHeight).build()
        val template = layoutSet.getKeyboard(KeyboardElement.EMOJI_RECENTS)
        val builder = KeyboardBuilder(context, KeyboardParams())
        builder.load(template.mId)
        keyboardParams = builder.mParams
        val (width, height) = getEmojiKeyDimensions(keyboardParams, context)
        keyWidth = width
        keyHeight = height
        loadEmojiDefaultVersionsAndPopupSpecs(context)
        templateKeyboard = template
        val emptyRow = createRow(keyboardWidth)
        columnCount = emptyRow.occupiedColumnCount
        rowHeight = emptyRow.occupiedHeight + keyboardView.paddingTop + keyboardView.paddingBottom
        emojis = emptyList()
        keyboardView.setKeyboard(emptyRow)
        scrollTo(0, 0)
    }

    fun setEmojis(newEmojis: List<String>) {
        if (templateKeyboard == null) return
        emojis = newEmojis
        // the row gets wider than the keyboard if there are more emojis than fit, and can be scrolled then
        var width = keyboardWidth
        var row = createRow(width)
        while (row.occupiedColumnCount < newEmojis.size) {
            width += keyboardWidth / columnCount
            row = createRow(width)
        }
        newEmojis.forEach { emoji ->
            val popupSpec = getEmojiPopupSpec(emoji)
            val keyParams = Key.KeyParams(emoji, emoji.getCode(), if (popupSpec != null) EMOJI_HINT_LABEL else null, popupSpec,
                Key.LABEL_FLAGS_FONT_NORMAL, keyboardParams)
            keyParams.mAbsoluteWidth = keyWidth
            keyParams.mAbsoluteHeight = keyHeight
            row.addKeyLast(keyParams.createKey())
        }
        keyboardView.setKeyboard(row)
        keyboardView.requestLayout()
        scrollTo(0, 0)
    }

    private fun createRow(width: Int) = DynamicGridKeyboard.ofRowCount(context.prefs(), templateKeyboard, 1, false, width)
}
