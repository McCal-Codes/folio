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
}
