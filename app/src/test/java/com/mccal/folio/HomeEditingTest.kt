package com.mccal.folio

import org.junit.Assert.*
import org.junit.Test

class HomeEditingTest {
    private val layout = HomeLayout(listOf("a", "b", "c"), listOf("d", "e", null, "a"))
    @Test fun `existing home app inserts forward and leaves the dock`() {
        val next = dropApp(layout, "a", DropTarget.Home(2))
        assertEquals(listOf("b", "c", "a"), next.slots)
        assertEquals(listOf("d", "e", null, null), next.dock)
        assertEquals(next, dropApp(next, "a", DropTarget.Home(2)))
    }

    @Test fun `existing home app inserts backward and rotates only intervening cells`() {
        val before = HomeLayout(listOf("a", "b", "c", "d", "e"), emptyList())
        val next = dropApp(before, "e", DropTarget.Home(1))
        assertEquals(listOf("a", "e", "b", "c", "d"), next.slots)
    }

    @Test fun `existing home app moved to an empty target leaves its source hole`() {
        val before = HomeLayout(listOf("a", "b", null, "c", "d"), emptyList())
        val next = dropApp(before, "a", DropTarget.Home(2))
        assertEquals(listOf(null, "b", "a", "c", "d"), next.slots)
    }

    @Test fun `existing move across rows preserves sparse cells within and outside its range`() {
        val before = HomeLayout(listOf("a", null, "b", "c", "d", null, "e", "f", "g", "h"), emptyList())
        val next = dropApp(before, "a", DropTarget.Home(8))
        assertEquals(listOf(null, "b", "c", "d", null, "e", "f", "g", "a", "h"), next.slots)
        assertEquals(before.slots.filterNotNull().toSet(), next.slots.filterNotNull().toSet())
    }
    @Test fun `move to new page retains empty cells and exact destination`() {
        val next = dropApp(layout, "a", DropTarget.Home(HOME_CELLS + 2))
        assertNull(next.slots[0])
        assertEquals("b", next.slots[1])
        assertEquals("a", next.slots[HOME_CELLS + 2])
        assertEquals(2, homePageCount(next.slots.size))
        assertEquals(layout.slots.filterNotNull().toSet(), next.slots.filterNotNull().toSet())
        assertNull(next.dock[3])
    }
    @Test fun `dock reorders existing shortcuts without ejecting one`() {
        val reordered = dropApp(HomeLayout(listOf("home", "d"), listOf("d", "e", "f", "a")), "d", DropTarget.Dock(2))
        assertEquals(listOf("e", "f", "d", "a"), reordered.dock)
        assertEquals(listOf("home"), reordered.slots)

        val movedToGap = dropApp(layout, "d", DropTarget.Dock(2))
        assertEquals(listOf(null, "e", "d", "a"), movedToGap.dock)
        assertEquals(layout.slots, movedToGap.slots)

        val movedBackward = dropApp(HomeLayout(listOf("home"), listOf("d", "e", "f", "a")), "a", DropTarget.Dock(1))
        assertEquals(listOf("d", "a", "e", "f"), movedBackward.dock)
        assertEquals(listOf("home"), movedBackward.slots)
    }
    @Test fun `moving dock shortcut onto home inserts it and clears dock`() {
        val next = dropApp(layout, "d", DropTarget.Home(1))
        assertEquals(listOf("a", "d", "b", "c"), next.slots)
        assertEquals(listOf(null, "e", null, "a"), next.dock)
    }

    @Test fun `new home app shifts only to the first vacancy`() {
        val before = HomeLayout(listOf("a", "b", null, "c", null, "d"), emptyList())
        val next = dropApp(before, "new", DropTarget.Home(0))
        assertEquals(listOf("new", "a", "b", "c", null, "d"), next.slots)
    }

    @Test fun `new home app wraps through rows and overflows a full page`() {
        val fullPage = (0 until 16).map(Int::toString)
        val next = dropApp(HomeLayout(fullPage, emptyList()), "new", DropTarget.Home(14))
        assertEquals(17, next.slots.size)
        assertEquals("new", next.slots[14])
        assertEquals("14", next.slots[15])
        assertEquals("15", next.slots[16])
        assertEquals(1, next.pageCount)
        assertEquals((fullPage + "new").toSet(), next.slots.filterNotNull().toSet())
    }

    @Test fun `empty home target accepts a new shortcut without shifting sparse cells`() {
        val before = HomeLayout(listOf("a", null, "b", null, "c"), emptyList())
        assertEquals(listOf("a", "new", "b", null, "c"), dropApp(before, "new", DropTarget.Home(1)).slots)
    }

    @Test fun `new dock shortcut prefers a later vacancy`() {
        val before = HomeLayout(listOf("home"), listOf("a", "b", "c", null, "d"))
        assertTrue(canPlaceInDock(before, "new"))
        val next = dropApp(before, "new", DropTarget.Dock(1))
        assertEquals(listOf("a", "new", "b", "c", "d"), next.dock)
        assertEquals(before.slots, next.slots)
        assertEquals(shortcuts(before) + "new", shortcuts(next))
    }

    @Test fun `new dock shortcut uses an earlier vacancy when no later one exists`() {
        val before = HomeLayout(listOf("new"), listOf("a", null, "b", "c", "d"))
        val next = dropApp(before, "new", DropTarget.Dock(3))
        assertEquals(listOf("a", "b", "c", "new", "d"), next.dock)
        assertEquals(emptyList<String?>(), next.slots)
        assertEquals(shortcuts(before), shortcuts(next))
    }

    @Test fun `full dock rejects a home newcomer without changing either surface`() {
        // Six is as big as the dock gets; a dock with fewer would grow to take the app.
        val before = HomeLayout(listOf("left", "new", "right"), listOf("a", "b", "c", "d", "e", "f"))
        val next = dropApp(before, "new", DropTarget.Dock(1))
        assertFalse(canPlaceInDock(before, "new"))
        assertSame(before, next)
        assertEquals(shortcuts(before), shortcuts(next))
    }

    @Test fun `home to empty dock moves without leaving a duplicate`() {
        val before = HomeLayout(listOf("a", "moving"), listOf("x", null, "y"))
        val next = dropApp(before, "moving", DropTarget.Dock(1))
        assertEquals(listOf("a"), next.slots)
        assertEquals(listOf("x", "moving", "y"), next.dock)
        assertEquals(1, (next.slots + next.dock).count { it == "moving" })
        assertEquals(shortcuts(before), shortcuts(next))
    }

    @Test fun `full dock rejects a library newcomer and keeps every shortcut`() {
        val before = HomeLayout(listOf("home"), listOf("a", "b", "c", "d", "e", "f"))
        val next = dropApp(before, "new", DropTarget.Dock(0))
        assertSame(before, next)
        assertEquals(shortcuts(before), shortcuts(next))
    }

    @Test fun `preview is pure and committing it again is idempotent`() {
        val before = HomeLayout(listOf("left", "new", "right"), listOf("a", null, "c", "d"))
        val preview = dropApp(before, "new", DropTarget.Dock(1))
        assertEquals(listOf("left", "new", "right"), before.slots)
        assertEquals(listOf("a", null, "c", "d"), before.dock)
        assertEquals(preview, dropApp(preview, "new", DropTarget.Dock(1)))
    }

    @Test fun `moving a dock shortcut home opens space for a new dock app`() {
        val before = HomeLayout(listOf("home"), listOf("a", "b", "c", "d"))
        val cleared = dropApp(before, "b", DropTarget.Home(1))
        assertEquals(listOf("home", "b"), cleared.slots)
        assertEquals(listOf("a", null, "c", "d"), cleared.dock)
        assertTrue(canPlaceInDock(cleared, "new"))
        val added = dropApp(cleared, "new", DropTarget.Dock(1))
        assertEquals(listOf("a", "new", "c", "d"), added.dock)
        assertEquals(cleared.slots, added.slots)
        assertEquals(shortcuts(before) + "new", shortcuts(added))
    }

    @Test fun `editing one shortcut preserves unrelated legacy duplicates`() {
        val before = HomeLayout(listOf("legacy", "legacy", "new"), listOf("a", null, "c", "d"))
        val next = dropApp(before, "new", DropTarget.Dock(1))
        assertEquals(listOf("legacy", "legacy"), next.slots)
        assertEquals(listOf("a", "new", "c", "d"), next.dock)
    }
    @Test fun `removal refresh and normalization keep intentional gaps`() {
        assertEquals(listOf(null, "b", "c"), pinHomeApp(layout.slots, "a", false))
        assertEquals(listOf("d", "b", "c"), pinHomeApp(listOf(null, "b", "c"), "d", true))
        assertEquals(listOf("a", null, "c"), reconcileHomeSlots(layout.slots, setOf("a", "c", "new")))
        assertEquals(listOf("a", null, null, "b"), normalizeHomeSlots(listOf("a", "a", null, "b", null)))
        assertEquals(emptyList<String?>(), normalizeHomeSlots(listOf(null, null)))
    }
    @Test fun `invalid drop leaves layout unchanged`() {
        assertEquals(layout, dropApp(layout, "a", DropTarget.Home(-HOME_CELLS - 1)))
        assertEquals(layout, dropApp(layout, "a", DropTarget.Home(2 * HOME_CELLS)))
        // One past the end is the open place a dock that can grow offers; two past it is nowhere.
        assertEquals(layout, dropApp(layout, "a", DropTarget.Dock(layout.dock.size + 1)))
        assertEquals(layout, dropApp(layout, " ", DropTarget.Dock(0)))
        assertEquals(layout, dropApp(layout, "a", DropTarget.Library("a")))
        assertFalse(canPlaceInDock(layout.copy(dock = listOf("a", "b")), ""))
        assertTrue(canPlaceInDock(layout.copy(dock = listOf("a", "b")), "a"))
    }

    private fun shortcuts(layout: HomeLayout) = (layout.slots + layout.dock).filterNotNull().toSet()
}
