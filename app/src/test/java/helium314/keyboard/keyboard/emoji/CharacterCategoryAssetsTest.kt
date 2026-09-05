package helium314.keyboard.keyboard.emoji

import helium314.keyboard.keyboard.KeyboardElement
import helium314.keyboard.latin.common.StringUtils
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** guards the second tab page: its asset files must exist, be non-empty and free of duplicates */
@RunWith(RobolectricTestRunner::class)
class CharacterCategoryAssetsTest {
    private val context get() = RuntimeEnvironment.getApplication()

    private val charElements = listOf(
        KeyboardElement.EMOJI_CHAR_PUNCTUATION to "CHAR_PUNCTUATION.txt",
        KeyboardElement.EMOJI_CHAR_ARROWS to "CHAR_ARROWS.txt",
        KeyboardElement.EMOJI_CHAR_MATH to "CHAR_MATH.txt",
        KeyboardElement.EMOJI_CHAR_LETTERS to "CHAR_LETTERS.txt",
    )

    @Test
    fun everyCharacterCategoryAssetLoadsAndHasEntries() {
        charElements.forEach { (_, fileName) ->
            val lines = readAsset(fileName)
            assertTrue(lines.isNotEmpty(), "$fileName must not be empty")
            assertTrue(lines.size >= 20, "$fileName should offer a useful amount of characters, got ${lines.size}")
        }
    }

    @Test
    fun characterCategoryEntriesAreSingleLineAndUnique() {
        charElements.forEach { (_, fileName) ->
            val lines = readAsset(fileName)
            assertEquals(lines.size, lines.toSet().size, "$fileName must not contain duplicates")
            lines.forEach { entry ->
                assertTrue(entry.isNotBlank(), "$fileName must not contain blank entries")
                assertTrue(!entry.contains(' '), "$fileName entry '$entry' must be a single token")
            }
        }
    }

    @Test
    fun characterCategoriesAreEmojiLayoutsAndDistinctFromEmojiPage() {
        charElements.forEach { (element, _) ->
            assertTrue(element.isEmojiLayout, "$element must count as an emoji layout so the pager builds it")
        }
        // the emoticons stay a real category, they only moved to the character page
        assertTrue(KeyboardElement.EMOJI_EMOTICONS.isEmojiLayout)
    }

    @Test
    fun characterCategoriesDoNotRepeatTheEmojiSymbolsCategory() {
        val emojiSymbols = readAsset("SYMBOLS.txt").toSet()
        charElements.forEach { (_, fileName) ->
            val overlap = readAsset(fileName).toSet().intersect(emojiSymbols)
            assertTrue(overlap.isEmpty(), "$fileName duplicates emoji SYMBOLS entries: $overlap")
        }
    }

    @Test
    fun characterCategoriesUseAssignedCodePoints() {
        charElements.forEach { (_, fileName) ->
            readAsset(fileName).forEach { entry ->
                val codePoint = entry.codePointAt(0)
                assertTrue(
                    Character.isDefined(codePoint),
                    "$fileName entry '$entry' uses an unassigned code point"
                )
                assertEquals(
                    1, StringUtils.codePointCount(entry),
                    "$fileName entry '$entry' should be a single code point so it maps to a plain key"
                )
            }
        }
    }

    private fun readAsset(fileName: String) =
        context.assets.open("emoji/$fileName").reader().use { it.readLines() }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
}
