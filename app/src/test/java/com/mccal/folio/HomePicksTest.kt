package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomePicksTest {
    private val starters = { listOf("s1", "s2") }

    @Test fun firstRunIgnoresTheLayoutFolioSeededAndOffersTheStarterSet() =
        assertEquals(listOf("s1", "s2"), homePicksFor(true, listOf("a", "b", "c"), starters))

    @Test fun aReplayKeepsWhatIsOnHome() =
        assertEquals(listOf("a", "b"), homePicksFor(false, listOf("a", "b"), starters))

    @Test fun anEmptyHomeGetsTheStarterSetEitherWay() {
        assertEquals(listOf("s1", "s2"), homePicksFor(false, emptyList(), starters))
        assertEquals(listOf("s1", "s2"), homePicksFor(true, emptyList(), starters))
    }

    @Test fun appsComeOffHomeBeforeNewOnesGoOn() {
        val changes = homePickChanges(onHome = setOf("a", "b", "c", "d"), appIds = listOf("a", "b", "c", "d", "e", "f"), picked = setOf("b", "e", "f"))
        assertEquals(listOf("a" to false, "c" to false, "d" to false, "e" to true, "f" to true), changes)
    }

    @Test fun newPicksFillTheFirstFreeCellsInsteadOfLandingAfterCellsThatAreAboutToOpen() {
        // Folio's own first suggestions sit in cells 8..15 under the widget rows; the starter set keeps two of them and adds two.
        val seeded = List(8) { null } + listOf("cal", "cam", "photos", "clock", "gmail", "maps", "notes", "settings")
        val blocked = (0 until 8).toSet()
        var slots: List<String?> = seeded
        val changes = homePickChanges(onHome = seeded.filterNotNull().toSet(), appIds = listOf("cal", "cam", "chrome", "clock", "gmail", "maps", "messages", "notes", "photos", "settings"),
            picked = setOf("cal", "cam", "photos", "maps", "settings", "chrome", "messages"))
        changes.forEach { (id, pinned) -> slots = pinHomeApp(slots, id, pinned, blocked, appRows = 4) }
        // Both new apps took cells inside the seeded block (the first two that opened), none sits past it.
        assertTrue(slots.indexOf("chrome") in 8..15)
        assertTrue(slots.indexOf("messages") in 8..15)
        assertEquals(slots.lastIndex, slots.indexOfLast { it != null })
        assertTrue("last app at ${slots.lastIndex}", slots.lastIndex <= 15)
    }
}
