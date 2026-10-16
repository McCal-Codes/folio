package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** When the opened island card stays open or is put away (IM-9, IM-10). */
class IslandCardRulesTest {
    @Test fun `a card the user opened closes when an event takes the island over`() {
        assertFalse(expandedAfterTakeover(expanded = true, takeover = true))
    }

    @Test fun `a closed card stays closed after a takeover and an open card stays open without one`() {
        assertFalse(expandedAfterTakeover(expanded = false, takeover = true))
        assertTrue(expandedAfterTakeover(expanded = true, takeover = false))
        assertFalse(expandedAfterTakeover(expanded = false, takeover = false))
    }

    @Test fun `a tap outside closes only a card the user opened`() {
        assertTrue(islandCardTapOutsideCloses(expanded = true, hasLive = true, message = false, notice = false, ringing = false))
        assertFalse(islandCardTapOutsideCloses(expanded = false, hasLive = true, message = false, notice = false, ringing = false))
        assertFalse(islandCardTapOutsideCloses(expanded = true, hasLive = false, message = false, notice = false, ringing = false))
    }

    @Test fun `a message, a notice or a ringing call is not closed by a tap outside`() {
        assertFalse(islandCardTapOutsideCloses(expanded = true, hasLive = true, message = true, notice = false, ringing = false))
        assertFalse(islandCardTapOutsideCloses(expanded = true, hasLive = true, message = false, notice = true, ringing = false))
        assertFalse(islandCardTapOutsideCloses(expanded = true, hasLive = true, message = false, notice = false, ringing = true))
    }

    @Test fun `the touch extension is 14 dp`() {
        assertEquals(14, ISLAND_TOUCH_EXTRA)
    }
}
