// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.clipboard

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import helium314.keyboard.ShadowInputMethodService
import helium314.keyboard.latin.LatinIME
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowInputMethodService::class, ShadowLooper::class], qualifiers = "w384dp-h896dp-450dpi")
class ClipboardLayoutParamsTest {
    // clipboard entries sit in a grid, the space between two rows should match the space between two columns
    @Test fun gapBetweenRowsEqualsGapBetweenColumns() = assertEqualGaps()

    @Config(qualifiers = "+land")
    @Test fun gapBetweenRowsEqualsGapBetweenColumnsInLandscape() = assertEqualGaps()

    private fun assertEqualGaps() {
        val latinIME = Robolectric.setupService(LatinIME::class.java)
        val entry = View(latinIME).apply { layoutParams = RecyclerView.LayoutParams(0, 0) }
        ClipboardLayoutParams(latinIME).setItemProperties(entry)
        val margins = entry.layoutParams as RecyclerView.LayoutParams
        // neighbouring entries are separated by the margins of both
        val gapBetweenColumns = margins.marginEnd + margins.marginStart
        val gapBetweenRows = margins.bottomMargin + margins.topMargin
        assertEquals(gapBetweenColumns, gapBetweenRows, "gap between rows of clipboard entries")
    }
}
