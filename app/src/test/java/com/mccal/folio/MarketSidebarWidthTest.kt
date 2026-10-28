package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** BI-3 and the 27 Oct Fold 8 photo: the Market sidebar was a fixed 180 dp and its labels broke mid-word at 200% text ("Feature" / "d"). */
class MarketSidebarWidthTest {
    @Test fun `the default size and smaller keep the 180 dp sidebar`() {
        assertEquals(180f, marketSidebarWidthDp(1f), 0f)
        assertEquals(180f, marketSidebarWidthDp(.85f), 0f)
    }

    @Test fun `the sidebar grows with the labels and stops where they stop`() {
        assertEquals(180f * 1.15f, marketSidebarWidthDp(1.15f), 0.001f)
        assertEquals(234f, marketSidebarWidthDp(CHROME_TEXT_CAP), 0.001f)
        for (scale in listOf(1.5f, 2f, 3f)) assertEquals("past the cap at $scale", 234f, marketSidebarWidthDp(scale), 0.001f)
    }

    @Test fun `a bad font scale changes nothing`() {
        assertEquals(180f, marketSidebarWidthDp(Float.NaN), 0f)
        assertEquals(180f, marketSidebarWidthDp(0f), 0f)
    }

    /**
     * The widest tab label, at the capped size, in the widest sidebar text: the sidebar's room (width less its padding, the
     * icon and the gap) holds it with room to spare, using the average glyph at .6 em as a generous bound. The sidebar is
     * wider than that only from [THREE_PANES_DP] in the same way; the narrowest window with a sidebar is 920 dp.
     */
    @Test fun `the widest label fits the sidebar at 200 percent with the cap`() {
        val labelSp = FolioType.SUBHEAD * CHROME_TEXT_CAP
        val room = marketSidebarWidthDp(2f) - 2f * FolioSpace.SMALL - 2f * FolioSpace.MEDIUM - 20f - 10f
        val widest = "Installed".length * labelSp * .6f
        assertTrue("label $widest dp in $room dp", widest <= room)
        assertTrue("the sidebar leaves a 920 dp window most of its width", marketSidebarWidthDp(3f) < 920f / 3f)
    }
}
