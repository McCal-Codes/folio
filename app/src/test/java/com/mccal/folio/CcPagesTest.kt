package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The editor's pages (PNL-1: what does not fit goes to another page, never to a scroll). Pure rules: where pages break and how a control crosses. */
class CcPagesTest {
    private val cols = 4
    private fun c(id: String, col: Int, row: Int, w: Int = 1, h: Int = 1) = CcSlot(id, col, row, w, h)

    @Test fun `a layout that fits the window is one page`() {
        val slots = listOf(c("a", 0, 0), c("b", 1, 3))
        assertEquals(listOf(0), ccPageStarts(slots, 6, 4))
    }

    @Test fun `rows past the window go to the next page`() {
        val slots = listOf(c("a", 0, 0), c("b", 0, 5), c("c", 0, 6), c("d", 0, 11))
        assertEquals(listOf(0, 6), ccPageStarts(slots, 6, 12))
    }

    @Test fun `a page never cuts a tall control in two`() {
        // Rows 0..3 would be the first page; the slider on rows 3..5 stands across that edge, so the page ends above it.
        val slots = listOf(c("a", 0, 0), c("slider", 1, 3, 1, 3), c("b", 0, 6))
        val starts = ccPageStarts(slots, 4, 7)
        assertEquals(listOf(0, 3), starts)
        assertTrue(slots.none { it.row < starts[1] && it.bottom > starts[1] })
    }

    @Test fun `the editor's spare row opens a new page only when the last page is full`() {
        val full = (0 until 6).map { c("r$it", 0, it) }
        assertEquals(listOf(0), ccPageStarts(full, 6, 6))
        assertEquals(listOf(0, 6), ccPageStarts(full, 6, 7))
        val short = (0 until 3).map { c("r$it", 0, it) }
        assertEquals(listOf(0), ccPageStarts(short, 6, 4))
    }

    @Test fun `an empty stretch between controls is skipped`() {
        val slots = listOf(c("a", 0, 0), c("b", 0, 20))
        assertEquals(listOf(0, 20), ccPageStarts(slots, 6, 21))
    }

    @Test fun `with no clean place to break the page is cut at the window anyway`() {
        // Two tall controls offset by one row stand across every row boundary in a 3-row window.
        val slots = listOf(c("a", 0, 0, 1, 3), c("b", 1, 1, 1, 3), c("c", 2, 2, 1, 3))
        val starts = ccPageStarts(slots, 3, 5)
        assertEquals(0, starts.first())
        assertTrue(starts.zipWithNext().all { (a, b) -> b > a })
    }

    @Test fun `every control belongs to exactly one page`() {
        val slots = listOf(c("a", 0, 0), c("s", 2, 4, 1, 3), c("b", 0, 7), c("d", 3, 12), c("e", 1, 9, 2, 1))
        val starts = ccPageStarts(slots, 5, 14)
        val ends = starts.drop(1) + Int.MAX_VALUE
        val seen = starts.zip(ends).flatMap { (s, e) -> ccPageSlots(slots, s, e).map { it.id } }
        assertEquals(slots.map { it.id }.sorted(), seen.sorted())
    }

    @Test fun `a page's slots start at row 0 and merge back to the same layout`() {
        val slots = listOf(c("a", 0, 0), c("b", 1, 6), c("d", 2, 7))
        val page = ccPageSlots(slots, 6, 12)
        assertEquals(listOf(c("b", 1, 0), c("d", 2, 1)), page)
        assertEquals(slots, ccMergePage(slots, 6, 12, page))
    }

    @Test fun `a change on one page leaves the others alone and keeps the order`() {
        val slots = listOf(c("a", 0, 0), c("b", 1, 6), c("d", 2, 7))
        val moved = ccMergePage(slots, 6, 12, listOf(c("b", 3, 2), c("d", 2, 1)))
        assertEquals(listOf(c("a", 0, 0), c("b", 3, 8), c("d", 2, 7)), moved)
    }

    @Test fun `a control carried to the next page takes its first free place from the top`() {
        val slots = listOf(c("a", 0, 5), c("b", 0, 6), c("c", 1, 6))
        val next = ccCarry(slots, "a", cols, 6, 12, fromBottom = false)
        assertEquals(c("a", 2, 6), next?.first { it.id == "a" })
    }

    @Test fun `a control carried to the page before lands at its end`() {
        val slots = listOf(c("a", 0, 6), c("b", 0, 0), c("c", 3, 5))
        val prev = ccCarry(slots, "a", cols, 0, 6, fromBottom = true)
        assertEquals(c("a", 0, 5), prev?.first { it.id == "a" })
    }

    @Test fun `a tall control is carried only where it fits whole`() {
        val slots = listOf(c("s", 0, 3, 1, 3), c("x", 0, 6), c("y", 0, 7), c("z", 0, 8), c("w", 1, 6), c("v", 1, 7), c("u", 1, 8), c("t", 2, 6), c("r", 2, 7), c("q", 2, 8), c("p", 3, 6), c("o", 3, 7), c("n", 3, 8))
        // The next page is rows 6..9 and every cell of rows 6..8 is taken: no room for a 3-row slider.
        assertNull(ccCarry(slots, "s", cols, 6, 9, fromBottom = false))
        val roomy = ccCarry(slots, "s", cols, 6, 12, fromBottom = false)
        assertNotNull(roomy)
        assertEquals(9, roomy!!.first { it.id == "s" }.row)
    }

    @Test fun `a full page has no room and nothing moves`() {
        val full = (0 until 4).flatMap { col -> (6 until 8).map { row -> c("f$col$row", col, row) } } + c("a", 0, 0)
        assertNull(ccCarry(full, "a", cols, 6, 8, fromBottom = false))
    }

    @Test fun `a page holds a set number of rows for each size of screen`() {
        assertEquals(3, ccRowsForWindow(360f))   // a phone on its side
        assertEquals(4, ccRowsForWindow(704f))   // the unfolded Fold, wide
        assertEquals(5, ccRowsForWindow(751f))   // the Fold's cover
        assertEquals(5, ccRowsForWindow(932f))
        assertEquals(6, ccRowsForWindow(1100f))
        // Never fewer for a taller window, and the same for any height inside one size.
        var last = 0
        for (h in 200..1400 step 10) { val r = ccRowsForWindow(h.toFloat()); assertTrue("$h", r >= last); last = r }
        assertEquals(ccRowsForWindow(740f), ccRowsForWindow(900f))
    }
}
