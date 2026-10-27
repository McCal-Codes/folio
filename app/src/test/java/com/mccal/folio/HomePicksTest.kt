package com.mccal.folio

import org.junit.Assert.assertEquals
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

    // F02 (27 Oct sweep): Folio's own first-refresh suggestions fill cells 0..12 on the emulator; setup's starter set differs.
    private val seeded = listOf("calendar", "camera", "photos", "clock", "gmail", "maps", "keep", "files", "settings", "calc", "play", "youtube", "contacts")
    private val offered = listOf("calendar", "camera", "chrome", "clock", "contacts", "files", "gmail", "keep", "maps", "messages", "phone", "photos", "play", "settings", "youtube", "calc")
    private val starterSet = setOf("calendar", "camera", "chrome", "maps", "messages", "phone", "photos", "settings")

    @Test fun firstRunPacksTheTickedAppsInThePreviewOrderWithNoGap() {
        val slots = homeSlotsForPicks(seeded, offered, starterSet, emptySet(), MAX_APP_ROWS, firstRun = true)
        assertEquals(listOf("calendar", "camera", "chrome", "maps", "messages", "phone", "photos", "settings"), slots)
    }

    @Test fun firstRunSkipsCellsAWidgetCovers() {
        // A widget over the first two rows: the apps start at cell 8.
        val slots = homeSlotsForPicks(seeded, offered, starterSet, (0 until 8).toSet(), MAX_APP_ROWS, firstRun = true)
        assertEquals(List(8) { null } + listOf("calendar", "camera", "chrome", "maps", "messages", "phone", "photos", "settings"), slots)
    }

    @Test fun firstRunKeepsAnAppSetupDidNotOffer() {
        val slots = homeSlotsForPicks(seeded + "other", offered, starterSet, emptySet(), MAX_APP_ROWS, firstRun = true)
        assertEquals("other", slots.last())
        assertEquals(9, slots.size)
    }

    @Test fun aReplayLeavesTheAppsWhereThePersonPutThemAndFillsTheFreedCellsFirst() {
        val own = listOf("calendar", null, "chrome", null, null, "settings", "clock")
        val slots = homeSlotsForPicks(own, offered, setOf("calendar", "chrome", "settings", "maps", "phone"), emptySet(), MAX_APP_ROWS, firstRun = false)
        // Clock (offered, unticked) came off; the two new apps took the first free cells, 1 and 3; nothing else moved.
        assertEquals(listOf("calendar", "maps", "chrome", "phone", null, "settings"), slots)
    }

    @Test fun anAppInTheSetOfOfferedButUntickedComesOffEvenWhenNothingIsAdded() {
        assertEquals(listOf("calendar"), homeSlotsForPicks(listOf("calendar", "clock"), offered, setOf("calendar"), emptySet(), MAX_APP_ROWS, firstRun = false))
    }
}
