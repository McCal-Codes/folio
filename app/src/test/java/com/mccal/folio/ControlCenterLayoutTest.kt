package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ControlCenterLayoutTest {
    private val controls = CcControl.entries.map { it.name }

    private fun noOverlap(slots: List<CcSlot>, cols: Int): Boolean =
        slots.all { s -> ccFits(slots, s, cols, ccRowsUsed(slots)) }

    @Test fun `the default layout is the page Control Center draws today, at every width and control count`() {
        for (cols in listOf(4, 6, 8, 10, 12)) for (n in listOf(0, 3, 4, 5, 8, 13, 15)) for (focus in listOf(false, true)) {
            val chosen = controls.take(n)
            val slots = ccDefaultLayout(cols, chosen, focus)
            assertTrue("overlap or outside at $cols columns, $n controls", noOverlap(slots, cols))
            // controlCenterRows counts one more row than the grid has (the page strip's), so the grid has one less.
            // The small Focus bar leaves cells beside it, so the page can be shorter than the one drawn without the editor, never taller.
            if (!focus) assertEquals("rows at $cols columns, $n controls", controlCenterRows(cols, n, focus) - 1, ccRowsUsed(slots))
            else assertTrue("rows at $cols columns, $n controls, focus", ccRowsUsed(slots) <= controlCenterRows(cols, n, focus) - 1)
            assertEquals("every control is placed once", chosen.toSet(), slots.map { it.id }.toSet().intersect(chosen.toSet()))
            assertEquals(slots.size, slots.map { it.id }.toSet().size)
        }
    }

    @Test fun `four columns put the first four small controls beside the sliders, six or more put the sliders at the end of the top row`() {
        val four = ccDefaultLayout(4, controls.take(6), focus = true).associateBy { it.id }
        assertEquals(CcSlot(CC_FOCUS, 0, 2, 2, 1), four[CC_FOCUS])
        assertEquals(CcSlot(CC_BRIGHTNESS, 2, 2, 1, 2), four[CC_BRIGHTNESS])
        assertEquals(CcSlot(controls[0], 0, 3, 1, 1), four[controls[0]])
        assertEquals(CcSlot(controls[1], 1, 3, 1, 1), four[controls[1]])
        assertEquals(CcSlot(controls[3], 1, 4, 1, 1), four[controls[3]])
        assertEquals(CcSlot(controls[4], 2, 4, 1, 1), four[controls[4]])
        val six = ccDefaultLayout(6, controls.take(6), focus = false).associateBy { it.id }
        assertEquals(CcSlot(CC_MEDIA, 2, 0, 2, 2), six[CC_MEDIA])
        assertEquals(CcSlot(CC_VOLUME, 5, 0, 1, 2), six[CC_VOLUME])
        assertEquals(CcSlot(controls[0], 0, 2, 1, 1), six[controls[0]])
    }

    @Test fun `a control moves to a free place and not onto another control or off the grid`() {
        val slots = ccDefaultLayout(4, controls.take(6), focus = true)
        val rows = 8
        val moved = ccMove(slots, controls[0], 2, 7, 4, rows)
        assertEquals(CcSlot(controls[0], 2, 7, 1, 1), moved.first { it.id == controls[0] })
        assertEquals(slots.size, moved.size)
        // onto the brightness slider, off the right edge, below the bottom, and an unknown id: nothing changes
        assertSame(slots, ccMove(slots, controls[0], 2, 3, 4, rows))
        assertSame(slots, ccMove(slots, controls[0], 4, 7, 4, rows))
        assertSame(slots, ccMove(slots, controls[0], 0, 8, 4, rows))
        assertSame(slots, ccMove(slots, "nothing", 0, 7, 4, rows))
        // staying put is fine
        assertEquals(slots, ccMove(slots, controls[0], 0, 3, 4, rows))
    }

    @Test fun `a drop on a free place moves, a drop on a same-size control trades places`() {
        val slots = ccDefaultLayout(4, controls.take(6), focus = true)
        val rows = 8
        assertEquals(ccMove(slots, controls[0], 2, 7, 4, rows), ccDrop(slots, controls[0], 2, 7, 4, rows))
        val a = slots.first { it.id == controls[0] }
        val b = slots.first { it.id == controls[1] }
        val traded = ccDrop(slots, controls[0], b.col, b.row, 4, rows)
        assertEquals(b.col to b.row, traded.first { it.id == controls[0] }.let { it.col to it.row })
        assertEquals(a.col to a.row, traded.first { it.id == controls[1] }.let { it.col to it.row })
        assertEquals(slots.map { it.id }, traded.map { it.id })
        assertTrue(noOverlap(traded, 4))
        // dropping where it already is changes nothing
        assertEquals(slots, ccDrop(slots, controls[0], a.col, a.row, 4, rows))
    }

    @Test fun `a drop that cannot trade changes nothing`() {
        val slots = ccDefaultLayout(4, controls.take(6), focus = true)
        val rows = 8
        // onto a slider or the connectivity block: the small control would overlap its neighbors where the other one has to go
        assertSame(slots, ccDrop(slots, controls[0], 2, 3, 4, rows))
        assertSame(slots, ccDrop(slots, controls[0], 0, 0, 4, rows))
        // off the grid, and an unknown id
        assertSame(slots, ccDrop(slots, controls[0], 3, 8, 4, rows))
        assertSame(slots, ccDrop(slots, "nothing", 0, 7, 4, rows))
    }

    @Test fun `a wide control can trade places with two small ones only when both fit`() {
        // x is wide at (0, 6); y, z small at (0, 7) and (1, 7). Dropping y on x's right half is a place x covers, but x would land on top of z.
        val base = ccDefaultLayout(4, controls.take(2), focus = false)
        val slots = base + CcSlot("x", 0, 6, 2, 1) + CcSlot("y", 0, 7, 1, 1) + CcSlot("z", 1, 7, 1, 1)
        assertSame(slots, ccDrop(slots, "y", 0, 6, 4, 9))
        // a free row below lets the wide one go to the small one's place
        val roomy = base + CcSlot("x", 0, 6, 2, 1) + CcSlot("y", 0, 7, 1, 1)
        val traded = ccDrop(roomy, "y", 1, 6, 4, 9)
        assertEquals(CcSlot("y", 1, 6, 1, 1), traded.first { it.id == "y" })
        assertEquals(CcSlot("x", 0, 7, 2, 1), traded.first { it.id == "x" })
    }

    @Test fun `a control takes only the sizes it can, and only where they fit`() {
        val slots = ccDefaultLayout(4, controls.take(4), focus = false) + CcSlot("x", 0, 6, 1, 1)
        val rows = 8
        // a shortcut can be wide: its right neighbor is the other 2 by 2 control, so it cannot at (1, 3), but can on a free row
        assertSame(slots, ccResize(slots, controls[1], 2, 1, 4, rows))
        assertEquals(2, ccResize(slots, "x", 2, 1, 4, rows).first { it.id == "x" }.w)
        // not a size a shortcut has
        assertSame(slots, ccResize(slots, "x", 3, 1, 4, rows))
        assertSame(slots, ccResize(slots, "x", 1, 2, 4, rows))
        // a slider can be taller if the room below is free
        assertEquals(3, ccResize(slots, CC_BRIGHTNESS, 1, 3, 4, rows).first { it.id == CC_BRIGHTNESS }.h)
        assertSame(slots, ccResize(slots, CC_BRIGHTNESS, 1, 3, 4, 4))
        // at four columns the media module cannot be wider than the grid
        assertTrue(ccAllowedSizes(CC_MEDIA, 4).all { it.first <= 4 })
        assertTrue((6 to 2) in ccAllowedSizes(CC_MEDIA, 6))
    }

    @Test fun `a control that grows over small neighbors moves them to the first free places`() {
        val row = listOf(CcSlot("a", 0, 0, 1, 1), CcSlot("b", 1, 0, 1, 1), CcSlot("c", 2, 0, 1, 1), CcSlot("d", 3, 0, 1, 1))
        val grown = ccResize(row, "a", 2, 1, 4, 3)
        assertEquals(listOf("a", "b", "c", "d"), grown.map { it.id })
        assertEquals(CcSlot("a", 0, 0, 2, 1), grown[0])
        // b steps aside to the first free cell, and nothing else moves
        assertEquals(CcSlot("b", 0, 1, 1, 1), grown[1])
        assertEquals(row[2], grown[2]); assertEquals(row[3], grown[3])
    }

    @Test fun `growing is refused when the displaced controls have nowhere to go or a module is in the way`() {
        val row = listOf(CcSlot("a", 0, 0, 1, 1), CcSlot("b", 1, 0, 1, 1), CcSlot("c", 2, 0, 1, 1), CcSlot("d", 3, 0, 1, 1))
        // one row only: b has no free place
        assertSame(row, ccResize(row, "a", 2, 1, 4, 1))
        // a module beside it does not move aside
        val withModule = listOf(CcSlot("a", 0, 0, 1, 1), CcSlot(CC_MEDIA, 1, 0, 2, 2))
        assertSame(withModule, ccResize(withModule, "a", 2, 1, 4, 4))
        // a drag toward the bigger size snaps to it when the neighbors can step aside
        assertEquals(2 to 1, ccSnapSize(row, "a", 2f, 1f, 4, 3))
        assertEquals(1 to 1, ccSnapSize(row, "a", 2f, 1f, 4, 1))
    }

    @Test fun `a corner drag lands on the nearest size that fits`() {
        val slots = ccDefaultLayout(4, controls.take(4), focus = false) + CcSlot("x", 0, 6, 1, 1)
        // dragged to about two cells wide on a free row: wide
        assertEquals(2 to 1, ccSnapSize(slots, "x", 1.8f, 1.1f, 4, 8))
        // dragged little: it stays one cell
        assertEquals(1 to 1, ccSnapSize(slots, "x", 1.2f, 1.0f, 4, 8))
        // where the wide size is taken, it stays small however far it is dragged
        assertEquals(1 to 1, ccSnapSize(slots, controls[1], 2f, 1f, 4, 8))
        assertNull(ccSnapSize(slots, "nothing", 1f, 1f, 4, 8))
    }

    @Test fun `adding takes the first free place and says no when there is none`() {
        val slots = ccDefaultLayout(4, controls.take(2), focus = false)
        val added = ccAdd(slots, "extra", 4, 6)!!
        // the first free single cell, reading left to right, top to bottom, is under the two small controls
        assertEquals(CcSlot("extra", 0, 3, 1, 1), added.first { it.id == "extra" })
        // already there: nothing changes
        assertSame(added, ccAdd(added, "extra", 4, 6))
        // a grid with no free cell
        val full = listOf(CcSlot("a", 0, 0, 1, 1), CcSlot("b", 1, 0, 1, 1))
        assertNull(ccAdd(full, "c", 2, 1))
        // Now Playing starts at 2 by 2, which one taken cell in a 2 by 2 grid does not leave room for
        val gap = listOf(CcSlot("a", 0, 0, 1, 1))
        assertNull(ccAdd(gap, CC_MEDIA, 2, 2))
        assertEquals(CcSlot(CC_MEDIA, 0, 0, 2, 2), ccAdd(emptyList(), CC_MEDIA, 2, 2)!!.single())
    }

    @Test fun `removing frees the places and the free cells add up`() {
        val slots = ccDefaultLayout(4, controls.take(6), focus = true)
        val rows = 8
        val area = slots.sumOf { it.w * it.h }
        assertEquals(4 * rows - area, ccFreeCells(slots, 4, rows).size)
        val without = ccRemove(slots, controls[0])
        assertEquals(slots.size - 1, without.size)
        assertEquals(ccFreeCells(slots, 4, rows).size + 1, ccFreeCells(without, 4, rows).size)
        assertTrue(ccFreeCells(without, 4, rows).contains(0 to 3))
    }
}

class ControlCenterSavedLayoutTest {
    private val controls = CcControl.DEFAULTS

    private fun saved(cols: Int = 6) = CcSavedLayout(cols, ccDefaultLayout(cols, controls, focus = false))

    @Test fun `a module taken off stays off, is offered back, and returns to a free place`() {
        val full = ccDefaultLayout(6, controls, focus = true)
        val off = CcSavedLayout(6, ccRemove(ccRemove(full, CC_VOLUME), CC_FOCUS), focusOn = true)
        assertEquals(off, ccLayoutFromJson(ccLayoutToJson(off)))
        val drawn = ccLayoutFor(off, 6, controls, focus = true)
        assertEquals(listOf(CC_VOLUME, CC_FOCUS), ccMissingModules(drawn, focus = true))
        assertEquals(listOf(CC_VOLUME), ccMissingModules(drawn, focus = false).filter { it != CC_FOCUS })
        val back = ccAdd(drawn, CC_VOLUME, 6, 8)
        assertEquals(true, back != null && back.any { it.id == CC_VOLUME })
    }

    @Test fun `a saved layout survives writing and reading`() {
        val layout = saved()
        assertEquals(layout, ccLayoutFromJson(ccLayoutToJson(layout)))
    }

    @Test fun `nothing saved, a bad file, or a wrong width falls back to the default page`() {
        val default6 = ccDefaultLayout(6, controls, false)
        assertEquals(default6, ccLayoutFor(null, 6, controls, false))
        assertEquals(default6, ccLayoutFor(saved(4), 6, controls, false))
        assertNull(ccLayoutFromJson(null))
        assertNull(ccLayoutFromJson(org.json.JSONObject("{\"cols\":6}")))
    }

    @Test fun `a layout with an unknown id, a repeat, an overlap, a bad size or a place outside the grid is refused whole`() {
        fun read(vararg slots: CcSlot) = ccLayoutFromJson(ccLayoutToJson(CcSavedLayout(6, slots.toList())))
        val ok = CcSlot(CcControl.entries.first().name, 0, 0, 1, 1)
        assertEquals(CcSavedLayout(6, listOf(ok)), read(ok))
        assertNull(read(ok.copy(id = "nope")))
        assertNull(read(ok, ok))
        assertNull(read(ok, ok.copy(id = CcControl.entries[1].name)))
        assertNull(read(ok.copy(w = 3)))
        assertNull(read(ok.copy(col = 6)))
        assertNull(read(ok.copy(row = -1)))
    }

    @Test fun `a saved place is kept, a control turned off leaves it, and one turned on takes the first free place`() {
        val layout = saved()
        val first = controls.first()
        val moved = ccMove(layout.slots, first, 5, 6, 6, 8)
        assertEquals(5 to 6, moved.first { it.id == first }.let { it.col to it.row })
        val kept = ccLayoutFor(CcSavedLayout(6, moved), 6, controls, false)
        assertEquals(moved.first { it.id == first }, kept.first { it.id == first })
        val fewer = ccLayoutFor(CcSavedLayout(6, moved), 6, controls - first, false)
        assertTrue(fewer.none { it.id == first })
        val extra = CcControl.entries.map { it.name }.first { it !in controls }
        val more = ccLayoutFor(layout, 6, controls + extra, false)
        assertTrue(more.any { it.id == extra })
        assertEquals(more.size, more.map { it.id }.toSet().size)
    }

    @Test fun `Focus follows its switch`() {
        val withFocus = CcSavedLayout(6, ccDefaultLayout(6, controls, focus = true))
        assertTrue(ccLayoutFor(withFocus, 6, controls, focus = false).none { it.id == CC_FOCUS })
        assertTrue(ccLayoutFor(saved(), 6, controls, focus = true).any { it.id == CC_FOCUS })
    }
}

class CcSlotGridMathTest {
    @Test fun `a drag lands on the nearest whole cell and under half a cell goes back`() {
        assertEquals(2 to 3, ccDragTarget(1, 3, 100f, 0f, 100f))
        assertEquals(1 to 3, ccDragTarget(1, 3, 49f, -49f, 100f))
        assertEquals(0 to 1, ccDragTarget(1, 2, -90f, -110f, 100f))
        assertEquals(1 to 1, ccDragTarget(1, 1, 500f, 500f, 0f))
    }

    @Test fun `a corner drag reports the size in cells it reached`() {
        assertEquals(3f to 2.5f, ccDragSize(2, 2, 100f, 50f, 100f))
    }

    @Test fun `the next size skips ones that do not fit and is null when there is none`() {
        val focus = CcSlot(CC_FOCUS, 0, 0, 4, 1)
        assertEquals(2 to 1, ccNextSize(listOf(focus), CC_FOCUS, 4, 4))
        val blocked = listOf(focus, CcSlot(CcControl.entries.first().name, 0, 1, 1, 1))
        // 2 by 1 fits, 2 by 2 would hit the control below.
        assertEquals(2 to 1, ccNextSize(blocked, CC_FOCUS, 4, 4))
        val pinned = CcSlot(CC_BRIGHTNESS, 0, 0, 1, 2)
        assertEquals(1 to 3, ccNextSize(listOf(pinned), CC_BRIGHTNESS, 4, 3))
        assertNull(ccNextSize(listOf(pinned), CC_BRIGHTNESS, 4, 2))
        assertNull(ccNextSize(listOf(CcSlot(CC_CONNECTIVITY, 0, 0, 2, 2)), CC_CONNECTIVITY, 4, 4))
    }
}

class CcSideTest {
    @Test fun `the side is the middle when centered, left when left handed, and the right otherwise`() {
        assertEquals(CcSide.RIGHT, ccSideOf(centered = false, left = false))
        assertEquals(CcSide.MIDDLE, ccSideOf(centered = true, left = false))
        assertEquals(CcSide.LEFT, ccSideOf(centered = false, left = true))
        assertEquals(CcSide.MIDDLE, ccSideOf(centered = true, left = true))
    }
}

class CcGalleryFilterTest {
    private val icon = androidx.compose.ui.graphics.vector.ImageVector.Builder("t", androidx.compose.ui.unit.Dp(1f), androidx.compose.ui.unit.Dp(1f), 1f, 1f).build()
    private val items = listOf(
        CcGalleryItem("DND", "Do Not Disturb", icon, false),
        CcGalleryItem("CAMERA", "Camera", icon, false),
        CcGalleryItem(CC_VOLUME, "Volume", icon, true),
    )

    @Test fun `a blank search shows everything and a word finds names that hold it, ignoring case`() {
        assertEquals(items, ccGalleryFilter(items, "  "))
        assertEquals(listOf("CAMERA"), ccGalleryFilter(items, "cAm").map { it.id })
        assertEquals(listOf("DND"), ccGalleryFilter(items, " disturb ").map { it.id })
        assertEquals(emptyList<CcGalleryItem>(), ccGalleryFilter(items, "zzz"))
    }
}

class CcHoldCardTest {
    @Test fun `the card's number is the slider as a whole percent, held between 0 and 100`() {
        assertEquals("0%", ccPercentLabel(0f))
        assertEquals("40%", ccPercentLabel(.4f))
        assertEquals("100%", ccPercentLabel(1f))
        assertEquals("0%", ccPercentLabel(-.3f))
        assertEquals("100%", ccPercentLabel(1.7f))
    }
}
