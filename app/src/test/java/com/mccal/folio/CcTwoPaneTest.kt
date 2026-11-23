package com.mccal.folio

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CcTwoPaneTest {
    private fun crease(from: Float, to: Float = from) = from..to

    @Test fun `a container that is too narrow has no split`() {
        assertNull(ccTwoPaneSplit(CC_TWO_PANE_MIN_WIDTH_DP - 1f))
        assertNull(ccTwoPaneSplit(412f))
        assertNull(ccTwoPaneSplit(320f, listOf(crease(160f))))
    }

    @Test fun `the class scale decides, not the raw dp (ADP-2)`() {
        // The same 700 dp becomes 560 when the Display size is raised by 0.8, so it is a phone-sized window and gets no split.
        assertNull(ccTwoPaneSplit(700f, classScale = .8f))
        assertNotNull(ccTwoPaneSplit(700f, classScale = 1f))
    }

    @Test fun `with no hinge the list takes its share between its least and most`() {
        val s = ccTwoPaneSplit(1200f)!!
        assertFalse(s.onHinge)
        assertEquals(CC_TWO_PANE_LIST_MAX_DP, s.paneWidthDp, 0f) // 36 percent of 1200 is past the most
        assertEquals(1200f - CC_TWO_PANE_LIST_MAX_DP - CC_TWO_PANE_GAP_DP, s.panelWidthDp, .01f)
        assertEquals(s.panelWidthDp + CC_TWO_PANE_GAP_DP, s.paneStartDp, .01f)
        assertEquals(900f * CC_TWO_PANE_LIST_SHARE, ccTwoPaneSplit(900f)!!.paneWidthDp, .01f)
        // 36 percent of 700 is under the least, so the least is kept; and the panel still has its four columns.
        assertEquals(CC_TWO_PANE_LIST_MIN_DP, ccTwoPaneSplit(700f)!!.paneWidthDp, 0f)
    }

    @Test fun `the two panes never overlap and always fit in the container`() {
        var width = CC_TWO_PANE_MIN_WIDTH_DP
        while (width <= 2400f) {
            ccTwoPaneSplit(width)?.let { s ->
                assertTrue("overlap at $width", s.panelWidthDp <= s.paneStartDp)
                assertTrue("outside at $width", s.paneStartDp + s.paneWidthDp <= width + .01f)
                assertTrue(s.panelWidthDp >= CC_TWO_PANE_PANEL_MIN_DP)
                assertTrue(s.paneWidthDp in CC_TWO_PANE_LIST_MIN_DP..CC_TWO_PANE_LIST_MAX_DP)
            }
            width += 8f
        }
    }

    @Test fun `a hinge in the middle is the divider and no control can straddle it`() {
        // An unfolded Fold: 840 dp wide, the crease in the middle with no width of its own.
        val s = ccTwoPaneSplit(840f, listOf(crease(420f)))!!
        assertTrue(s.onHinge)
        assertEquals(420f - CC_TWO_PANE_HINGE_MARGIN_DP, s.panelWidthDp, .01f)
        assertEquals(420f + CC_TWO_PANE_HINGE_MARGIN_DP, s.paneStartDp, .01f)
        assertTrue("the panel stops short of the crease", s.panelWidthDp < 420f)
        assertTrue("the list starts past it", s.paneStartDp > 420f)
    }

    @Test fun `a hinge with a width of its own keeps clear space on both sides`() {
        val s = ccTwoPaneSplit(900f, listOf(crease(440f, 460f)))!!
        assertEquals(440f - CC_TWO_PANE_HINGE_MARGIN_DP, s.panelWidthDp, .01f)
        assertEquals(460f + CC_TWO_PANE_HINGE_MARGIN_DP, s.paneStartDp, .01f)
    }

    @Test fun `a hinge that leaves either side too small gives no split rather than a share across the crease`() {
        assertNull("panel too small", ccTwoPaneSplit(900f, listOf(crease(200f))))
        assertNull("list too small", ccTwoPaneSplit(900f, listOf(crease(780f))))
    }

    @Test fun `a hinge at the container's edge is the half-open book's own edge and is ignored`() {
        // A half-open book is already confined to one half by FoldAvoidingBox, so its crease lies at the edge of what is left.
        val s = ccTwoPaneSplit(900f, listOf(crease(-6f, 0f), crease(900f)))
        assertNotNull(s)
        assertFalse(s!!.onHinge)
    }

    @Test fun `with two hinges the panel is the first segment and the list is held in the second`() {
        // A tri-fold: panels of about 400 dp. The list stays between the creases, so it is never drawn across the second.
        val s = ccTwoPaneSplit(1200f, listOf(crease(400f), crease(800f)))!!
        assertTrue(s.onHinge)
        assertEquals(400f - CC_TWO_PANE_HINGE_MARGIN_DP, s.panelWidthDp, .01f)
        assertTrue(s.paneStartDp + s.paneWidthDp <= 800f - CC_TWO_PANE_HINGE_MARGIN_DP + .01f)
    }

    @Test fun `on a hinge the list is capped at its most`() {
        val s = ccTwoPaneSplit(1400f, listOf(crease(500f)))!!
        assertEquals(CC_TWO_PANE_LIST_MAX_DP, s.paneWidthDp, 0f)
    }

    @Test fun `hinge spans are measured from the container's start edge`() {
        val hinge = Hinge(active = false, vertical = true, startPx = 1000, endPx = 1000)
        // 2.0 px per dp, the container's left edge at 32 px and 1800 px wide: the crease is at (1000 - 32) / 2 dp.
        val spans = ccHingeSpansDp(hinge, 32f, 1800f, rtl = false, density = 2f)
        assertEquals(1, spans.size)
        assertEquals(484f, spans[0].start, .01f)
    }

    @Test fun `right to left mirrors the hinge so the panel is still on the start side`() {
        val hinge = Hinge(active = false, vertical = true, startPx = 1000, endPx = 1000)
        val spans = ccHingeSpansDp(hinge, 0f, 1800f, rtl = true, density = 2f)
        assertEquals((1800f - 1000f) / 2f, spans[0].start, .01f)
        assertTrue(spans[0].start <= spans[0].endInclusive)
    }

    @Test fun `a horizontal hinge is not along the width, so it never sets the divider`() {
        val tabletop = Hinge(active = true, vertical = false, startPx = 900, endPx = 940)
        assertTrue(ccHingeSpansDp(tabletop, 0f, 1800f, rtl = false, density = 2f).isEmpty())
        assertTrue(ccHingeSpansDp(null, 0f, 1800f, rtl = false, density = 2f).isEmpty())
    }

    @Test fun `the sections keep the gallery's order and leave out an empty one`() {
        fun item(id: String, module: Boolean = false) = CcGalleryItem(id, id, Icons.Rounded.Add, module)
        val sections = ccGallerySections(listOf(item("CAMERA"), item("HOTSPOT"), item(CC_MEDIA, module = true), item("MYSTERY")))
        assertEquals(listOf(R.string.cc_gallery_modules, CcGalleryGroup.CONNECTIVITY.label, CcGalleryGroup.TOOLS.label, R.string.cc_gallery_controls), sections.map { it.label })
        assertEquals(listOf("MYSTERY"), sections.last().items.map { it.id })
        assertTrue(ccGallerySections(emptyList()).isEmpty())
    }

    @Test fun `every item lands in exactly one section`() {
        val all = CcControl.entries.map { CcGalleryItem(it.name, it.name, Icons.Rounded.Add, false) }
        val placed = ccGallerySections(all).flatMap { it.items }
        assertEquals(all.map { it.id }.sorted(), placed.map { it.id }.sorted())
    }

    @Test fun aPanelThatMustKeepMoreColumnsTakesMoreOfTheWindowOrThereIsNoSplit() {
        // 672 dp has room for a 376 dp panel, but six columns at the touch size need more than that.
        assertNotNull(ccTwoPaneSplit(672f))
        assertNull(ccTwoPaneSplit(672f, panelMinDp = controlCenterMinPanelDp(6, 90f)))
        assertNotNull(ccTwoPaneSplit(900f, panelMinDp = controlCenterMinPanelDp(6, 90f)))
        // A hinge splits the same way: a panel before the crease that is too narrow for its columns is no split.
        assertNull(ccTwoPaneSplit(900f, listOf(380f..380f), panelMinDp = controlCenterMinPanelDp(6, 90f)))
    }

    @Test fun theLeastPanelNeverGoesBelowTheFixedMinimum() {
        assertEquals(ccTwoPaneSplit(900f)?.panelWidthDp, ccTwoPaneSplit(900f, panelMinDp = 10f)?.panelWidthDp)
        assertNull(ccTwoPaneSplit(900f, listOf(300f..300f), panelMinDp = 10f))
    }
}
