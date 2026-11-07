package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Where overlays go on folds with any number of hinges (Screen Coverage v2: postures and tri-folds). */
class FoldSafeSpanTest {
    private fun clear(span: IntRange, hinges: List<IntRange>) = hinges.none { h -> span.first < h.last && span.last > h.first }

    @Test fun `no hinge uses the whole window`() {
        assertEquals(0..1000, foldSafeSpan(emptyList(), 1000, trailing = true))
    }

    @Test fun `book fold uses the trailing half and a table uses the bottom or top half`() {
        val hinge = listOf(990..1010)
        assertEquals(1010..2000, foldSafeSpan(hinge, 2000, trailing = true))
        assertEquals(0..990, foldSafeSpan(hinge, 2000, trailing = false))
    }

    @Test fun `tri-fold overlays sit on one panel and never cross either hinge`() {
        val hinges = listOf(890..910, 1790..1810)
        for (trailing in listOf(true, false)) {
            val span = foldSafeSpan(hinges, 2700, trailing)
            assertTrue(clear(span, hinges))
            assertTrue(span.last - span.first >= 2700 / 4)
        }
        assertEquals(1810..2700, foldSafeSpan(hinges, 2700, trailing = true))
        // Hinges given out of order are handled the same.
        assertEquals(1810..2700, foldSafeSpan(hinges.reversed(), 2700, trailing = true))
    }

    @Test fun `a sliver of a panel is skipped for a usable one`() {
        // Hinge near the trailing edge: the sliver after it is too small, so the big panel is used.
        assertEquals(0..1800, foldSafeSpan(listOf(1800..1820), 2000, trailing = true))
    }

    @Test fun `a hinge at the edge or covering everything still gives a region`() {
        assertEquals(20..1000, foldSafeSpan(listOf(0..20), 1000, trailing = true))
        val all = foldSafeSpan(listOf(0..1000), 1000, trailing = true)
        assertTrue(all.first >= 0 && all.last <= 1000)
    }

    @Test fun `every hinge position across a window leaves a clear region`() {
        for (extent in listOf(1248, 2448, 2700, 3600)) for (count in 1..2) {
            var pos = 0
            while (pos < extent - 40) {
                val hinges = (0 until count).map { i -> (pos + i * extent / 3).coerceAtMost(extent - 20).let { it..it + 20 } }
                for (trailing in listOf(true, false)) {
                    val span = foldSafeSpan(hinges, extent, trailing)
                    assertTrue("$extent $hinges $trailing -> $span", span.first >= 0 && span.last <= extent && span.last >= span.first)
                    assertTrue("$extent $hinges $trailing -> $span", clear(span, hinges))
                }
                pos += 37
            }
        }
    }

    @Test fun `unfolded Home stays on its side of a book fold`() {
        // Pixel Fold inner: without the fold rule the Home half would start left of the middle.
        val flat = homeGeometry(841f, 701f, LayoutPreset(), true, statusHeight = 160f)
        val folded = homeGeometry(841f, 701f, LayoutPreset(), true, statusHeight = 160f, foldAtCenter = true)
        assertTrue(flat.homeWidth > 841f / 2f)
        assertTrue(folded.homeWidth <= 841f / 2f && folded.expanded && folded.iconSize >= 48f)
        // The Galaxy Z Fold8 is wide enough that nothing changes.
        assertEquals(homeGeometry(932f, 704f, LayoutPreset(), true, statusHeight = 160f),
            homeGeometry(932f, 704f, LayoutPreset(), true, statusHeight = 160f, foldAtCenter = true))
    }

    @Test fun `DUO_PANE_GAP brings the left pane closer when flat and separates the halves when partly folded`() {
        val g = homeGeometry(932f, 704f, LayoutPreset(), true, statusHeight = 160f, foldAtCenter = true)
        val rightGridStart = 932f - g.homeWidth + 16f
        val oldLeftOrigin = expandedLeftPaneOrigin(932f, g.homeWidth, g.gridWidth, duoPaneGap = false, bookFolded = false)
        val flatLeftOrigin = expandedLeftPaneOrigin(932f, g.homeWidth, g.gridWidth, duoPaneGap = true, bookFolded = false)
        val foldedLeftOrigin = expandedLeftPaneOrigin(932f, g.homeWidth, g.gridWidth, duoPaneGap = true, bookFolded = true)
        // Flat with the gate open: the left grid ends DUO_FLAT_PANE_GAP_DP before the right grid starts, and still left of the middle crease.
        val flatLeftGridEnd = flatLeftOrigin + 16f + g.gridWidth
        assertEquals(DUO_FLAT_PANE_GAP_DP, rightGridStart - flatLeftGridEnd, 0.01f)
        assertTrue(flatLeftGridEnd <= 932f / 2f)
        assertTrue(flatLeftOrigin > oldLeftOrigin)
        // Partly folded: the left pane springs back to the middle of the left half, separating the two halves across the crease.
        assertEquals(oldLeftOrigin, foldedLeftOrigin, 0.001f)
        assertTrue(rightGridStart - (foldedLeftOrigin + 16f + g.gridWidth) > DUO_FLAT_PANE_GAP_DP + 30f)
    }
}
