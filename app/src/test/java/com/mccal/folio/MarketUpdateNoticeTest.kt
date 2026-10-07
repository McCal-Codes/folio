package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketUpdateNoticeTest {
    private val keyd = PendingUpdate("com.mccal.keyd", "Keyd", "0.4.1")
    private val duet = PendingUpdate("com.mccal.folio.duet", "Duet", "1.2")

    @Test fun `the same updates give the same signature in any order`() {
        assertEquals(MarketUpdateNotice.signature(listOf(keyd, duet)), MarketUpdateNotice.signature(listOf(duet, keyd)))
    }

    @Test fun `a notice is posted for new versions, once`() {
        val sig = MarketUpdateNotice.signature(listOf(keyd))
        assertTrue(MarketUpdateNotice.shouldNotify(listOf(keyd), null))
        assertFalse(MarketUpdateNotice.shouldNotify(listOf(keyd), sig))
        // A newer version of the same package is news again; another package joining it is too.
        assertTrue(MarketUpdateNotice.shouldNotify(listOf(keyd.copy(version = "0.4.2")), sig))
        assertTrue(MarketUpdateNotice.shouldNotify(listOf(keyd, duet), sig))
    }

    @Test fun `nothing to say means no notice`() {
        assertFalse(MarketUpdateNotice.shouldNotify(emptyList(), null))
        assertFalse(MarketUpdateNotice.shouldNotify(emptyList(), "x"))
    }

    @Test fun `the names are the first three, then how many more`() {
        assertEquals("Keyd 0.4.1, Duet 1.2", MarketUpdateNotice.names(listOf(keyd, duet)) { "and $it more" })
        val five = (1..5).map { PendingUpdate("p$it", "P$it", "1") }
        assertEquals("P1 1, P2 1, P3 1, and 2 more", MarketUpdateNotice.names(five) { "and $it more" })
    }

    @Test fun `the update link parses and nothing else under market does`() {
        assertEquals(MarketLink.Updates, MarketLink.parse("folio://market/updates"))
        assertEquals(null, MarketLink.parse("folio://market/other"))
        assertEquals(null, MarketLink.parse("folio://market"))
    }
}
