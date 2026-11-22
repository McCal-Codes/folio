package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class HomePagesTest {
    /** Three pages, one app on each, a widget on pages 0 and 2, the unfolded-only page with a widget, and the dock. */
    private fun layout(): HomeLayout {
        val slots = MutableList<String?>(3 * HOME_CELLS) { null }
        slots[homeCellIndex(0, 8)] = "p0"; slots[homeCellIndex(1, 8)] = "p1"; slots[homeCellIndex(2, 9)] = "p2"
        return HomeLayout(slots, listOf("dock", null, null, null),
            widgetPlacements = listOf(WidgetPlacement(0, CLOCK_WIDGET, 0, 0, 0, 2, 2), WidgetPlacement(1, DATE_WIDGET, 2, 0, 0, 2, 2),
                WidgetPlacement(2, DATE_WIDGET, -1, 0, 0, 2, 2)),
            leadingSlots = List(HOME_CELLS) { if (it == 8) "lead" else null }, minPages = 3)
    }

    @Test fun `moving a page gives the order of old numbers by new place`() {
        assertEquals(listOf(1, 2, 0), HomePages.moveOrder(3, 0, 2))
        assertEquals(listOf(2, 0, 1), HomePages.moveOrder(3, 2, 0))
        assertEquals(listOf(0, 2, 1, 3), HomePages.moveOrder(4, 1, 2))
        assertTrue(HomePages.isIdentity(HomePages.moveOrder(3, 1, 1)))
        // Out of range changes nothing.
        assertTrue(HomePages.isIdentity(HomePages.moveOrder(3, 0, 5)))
        assertTrue(HomePages.isIdentity(HomePages.moveOrder(3, -1, 1)))
    }

    @Test fun `reordering carries each page's apps and widgets with it and leaves the rest alone`() {
        val order = HomePages.moveOrder(3, 0, 2) // old 0 goes last
        val moved = HomePages.reorder(layout(), order)
        assertEquals(3, moved.pageCount)
        assertEquals("p1", moved.slots[homeCellIndex(0, 8)])
        assertEquals("p2", moved.slots[homeCellIndex(1, 9)])
        assertEquals("p0", moved.slots[homeCellIndex(2, 8)])
        // The clock was on page 0 and is now on page 2; the date was on page 2 and is now on page 1; the leading page stays.
        assertEquals(listOf(0 to 2, 1 to 1, 2 to -1), moved.widgetPlacements.map { it.slot to it.page })
        assertEquals(layout().dock, moved.dock)
        assertEquals(layout().leadingSlots, moved.leadingSlots)
        assertEquals(3, moved.minPages)
    }

    @Test fun `a move and its reverse give the original layout`() {
        val order = HomePages.moveOrder(3, 0, 2)
        val back = HomePages.reorder(HomePages.reorder(layout(), order), listOf(2, 0, 1))
        // Home trims empty trailing cells, so compare against the trimmed original.
        assertEquals(layout().copy(slots = layout().slots.dropLastWhile { it == null }), back)
    }

    @Test fun `an empty page in the middle keeps its place and a trailing empty page is kept by minPages`() {
        val slots = MutableList<String?>(HOME_CELLS) { null }.also { it[8] = "only" }
        val base = HomeLayout(slots, emptyList(), emptyList(), minPages = 3)
        val moved = HomePages.reorder(base, HomePages.moveOrder(3, 0, 2))
        assertEquals(3, moved.pageCount)
        assertEquals("only", moved.slots[homeCellIndex(2, 8)])
        // Empty trailing cells are trimmed; the page is still there because of minPages.
        assertEquals(homeCellIndex(2, 8) + 1, moved.slots.size)
    }

    @Test fun `moving an empty page to the end keeps the page count`() {
        // Two pages counted only by their cells: page 0 empty, page 1 has an app, minPages is still 1.
        val slots = MutableList<String?>(2 * HOME_CELLS) { null }.also { it[homeCellIndex(1, 8)] = "only" }
        val base = HomeLayout(slots, emptyList(), emptyList(), minPages = 1)
        assertEquals(2, base.pageCount)
        val moved = HomePages.reorder(base, HomePages.moveOrder(2, 0, 1))
        assertEquals(2, moved.pageCount)
        assertEquals("only", moved.slots[homeCellIndex(0, 8)])
    }

    @Test fun `a wrong sized or broken order leaves the layout as it was`() {
        assertEquals(layout(), HomePages.reorder(layout(), listOf(1, 0)))
        assertEquals(layout(), HomePages.reorder(layout(), listOf(0, 0, 1)))
        assertEquals(layout(), HomePages.reorder(layout(), listOf(0, 1, 2)))
    }

    @Test fun `page numbers follow the page`() {
        val order = HomePages.moveOrder(3, 0, 2) // [1, 2, 0]
        assertEquals(2, HomePages.remapPage(0, order))
        assertEquals(0, HomePages.remapPage(1, order))
        assertEquals(1, HomePages.remapPage(2, order))
        // The unfolded-only page and a stale number past the end do not move.
        assertEquals(-1, HomePages.remapPage(-1, order))
        assertEquals(7, HomePages.remapPage(7, order))
        assertEquals(setOf(2, 1), HomePages.remapPages(setOf(0, 2), order))
        val styles = mapOf(0 to PageStyle(iconScale = 1.2f), 2 to PageStyle(labels = false))
        assertEquals(mapOf(2 to PageStyle(iconScale = 1.2f), 1 to PageStyle(labels = false)), HomePages.remapPageMap(styles, order))
    }

    @Test fun `a Focus keeps showing the same pages and opening on the same one after a move`() {
        val work = DEFAULT_FOCUS_MODES.first { it.id == "work" }.copy(pages = setOf(0, 2), homePage = 2)
        val moved = HomePages.remapFocus(work, HomePages.moveOrder(3, 0, 2))
        assertEquals(setOf(2, 1), moved.pages)
        assertEquals(1, moved.homePage)
        // A Focus with no page choice stays that way.
        val all = DEFAULT_FOCUS_MODES.first { it.id == "dnd" }
        assertNull(HomePages.remapFocus(all, listOf(1, 0)).pages)
        assertNull(HomePages.remapFocus(all, listOf(1, 0)).homePage)
    }

    @Test fun `hiding takes a page out of what shows and showing puts it back`() {
        assertEquals(listOf(0, 2), HomePages.shownPages(3, setOf(1), null))
        assertEquals(listOf(0, 1, 2), HomePages.shownPages(3, emptySet(), null))
        // Stale numbers past the end are ignored.
        assertEquals(listOf(0, 1), HomePages.shownPages(2, setOf(5), null))
    }

    @Test fun `a Focus narrows the pages showing but cannot bring a hidden page back`() {
        assertEquals(listOf(2), HomePages.shownPages(3, setOf(0), setOf(0, 2)))
        assertEquals(listOf(0, 1, 2), HomePages.shownPages(3, emptySet(), setOf(0, 1, 2)))
        // A Focus that asks only for hidden or missing pages still shows one, the first that is showing.
        assertEquals(listOf(1), HomePages.shownPages(3, setOf(0), setOf(0, 9)))
    }

    @Test fun `the last page showing cannot be hidden and hiding every page is read as hiding none`() {
        assertNull(HomePages.withHidden(setOf(0, 1), 3, 2, hide = true))
        assertEquals(setOf(0), HomePages.withHidden(emptySet(), 3, 0, hide = true))
        assertNull(HomePages.withHidden(emptySet(), 1, 0, hide = true))
        assertEquals(setOf(1), HomePages.withHidden(setOf(0, 1), 3, 0, hide = false))
        assertNull(HomePages.withHidden(emptySet(), 3, 5, hide = true))
        assertEquals(emptySet<Int>(), HomePages.sanitizeHidden(setOf(0, 1, 2), 3))
        assertEquals(setOf(1), HomePages.sanitizeHidden(setOf(1, 8, -1), 3))
        // Showing a page that is not hidden changes nothing.
        assertEquals(setOf(1), HomePages.withHidden(setOf(1), 3, 0, hide = false))
    }

    @Test fun `the page a screen rested on follows a move`() {
        val shown = listOf(0, 1, 2)
        val order = HomePages.moveOrder(3, 0, 2)
        // Resting on page 0 (now last): the page index follows it to 2. Page 2 (now 1) goes to index 1.
        assertEquals(2, HomePages.followShownIndex(0, shown, order, shown))
        assertEquals(1, HomePages.followShownIndex(2, shown, order, shown))
    }

    @Test fun `the page a screen rested on follows a hide to the nearest page left`() {
        // Pages 0..3 showing, page 2 gets hidden: index 2 was real page 2, which is gone; the nearest showing page is 1 or 3.
        val before = listOf(0, 1, 2, 3)
        val after = listOf(0, 1, 3)
        assertEquals(0, HomePages.followShownIndex(0, before, null, after))
        assertEquals(2, HomePages.followShownIndex(3, before, null, after))
        val landed = HomePages.followShownIndex(2, before, null, after)
        assertTrue(landed == 1 || landed == 2)
        // Showing a page again moves later indexes along.
        assertEquals(3, HomePages.followShownIndex(2, after, null, before))
    }

    @Test fun `a remembered page that is not on the list stays inside the pages there are`() {
        assertEquals(2, HomePages.followShownIndex(9, listOf(0, 1), null, listOf(0, 1, 2)))
        assertEquals(0, HomePages.followShownIndex(0, emptyList(), null, listOf(4)))
    }

    @Test fun `TalkBack move steps stop at either end`() {
        assertEquals(1, HomePages.stepTarget(0, 3, toEnd = true))
        assertNull(HomePages.stepTarget(2, 3, toEnd = true))
        assertNull(HomePages.stepTarget(0, 3, toEnd = false))
        assertEquals(0, HomePages.stepTarget(1, 3, toEnd = false))
    }

    @Test fun `hiding pages leaves their apps in the layout`() {
        // Hiding is a setting beside the layout: nothing about the layout changes, so no app is lost.
        val layout = layout()
        val shown = HomePages.shownPages(layout.pageCount, setOf(1), null)
        val filtered = FocusPages.filter(layout, shown.toSet())
        assertEquals(2, filtered.pageCount)
        assertEquals(setOf("p0", "p2"), filtered.slots.filterNotNull().toSet())
        assertEquals(setOf("p0", "p1", "p2"), layout.slots.filterNotNull().toSet())
    }

    private fun state(hidden: Set<Int> = emptySet(), focus: FocusMode? = null): LauncherState {
        val l = layout()
        return LauncherState(homeSlots = l.slots, dock = l.dock, widgetPlacements = l.widgetPlacements, leadingSlots = l.leadingSlots, minPages = l.minPages,
            hiddenPages = hidden, focusModes = listOfNotNull(focus), activeFocus = focus?.id,
            pageStyles = mapOf(0 to PageStyle(iconScale = 1.2f), 2 to PageStyle(labels = false)), loading = false)
    }

    @Test fun `Home draws a filtered copy while a page is hidden, and the saved layout keeps every app`() {
        val s = state(hidden = setOf(1))
        assertTrue(FocusPages.isLocked(s))
        assertNull(FocusPages.lockingFocus(s))
        val shown = FocusPages.effective(s)
        assertEquals(2, shown.homePages)
        // Page 2 is page 1 on the filtered Home, with its page look and its widget.
        assertEquals("p2", shown.homeSlots[homeCellIndex(1, 9)])
        assertEquals(PageStyle(labels = false), shown.pageStyles[1])
        assertEquals(3, s.homePages)
        assertEquals(setOf("p0", "p1", "p2"), s.homeSlots.filterNotNull().toSet())
        // Nothing hidden: Home is the saved state untouched.
        val plain = state()
        assertSame(plain, FocusPages.effective(plain))
        assertEquals(null, FocusPages.lock(state()))
    }

    @Test fun `the lock names the Focus only when a Focus cuts pages beyond the hidden ones`() {
        val work = DEFAULT_FOCUS_MODES.first { it.id == "work" }.copy(pages = setOf(0, 2))
        // Page 1 hidden, and the Focus lists 0 and 2 only: the Focus takes nothing more away, so only hidden pages lock Home.
        assertNull(FocusPages.lock(state(hidden = setOf(1), focus = work))?.mode)
        assertEquals(listOf(0, 2), FocusPages.lock(state(hidden = setOf(1), focus = work))?.shown)
        // Nothing hidden: the Focus is the lock.
        assertEquals("work", FocusPages.lock(state(focus = work))?.mode?.id)
        // A Focus never brings a hidden page back.
        val all = work.copy(pages = setOf(0, 1, 2))
        assertEquals(listOf(0, 2), FocusPages.shown(state(hidden = setOf(1), focus = all)))
    }

    @Test fun `a Focus's opening page is found among the pages that show`() {
        val work = DEFAULT_FOCUS_MODES.first { it.id == "work" }.copy(pages = setOf(0, 2), homePage = 2)
        assertEquals(1, FocusPages.openPage(work, 3, listOf(0, 2)))
        // The opening page is hidden: the first page that shows.
        assertEquals(0, FocusPages.openPage(work, 3, listOf(0, 1)))
        assertNull(FocusPages.openPage(work.copy(homePage = null), 3, listOf(0, 2)))
    }

    @Test fun `new apps skip hidden pages`() {
        val cells = FocusPages.hiddenCells(state(hidden = setOf(1)))
        assertEquals(HOME_CELLS, cells.size)
        assertTrue(cells.all { homeCellPage(it) == 1 })
        assertTrue(FocusPages.hiddenCells(state()).isEmpty())
    }
}
