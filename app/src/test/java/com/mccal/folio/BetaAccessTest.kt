package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Supporter page and Software Update say the same thing about betas, in the order the fixes come. */
class BetaAccessTest {
    private val none = SoftwareUpdate.CodeStanding.NONE
    private val bad = SoftwareUpdate.CodeStanding.INVALID
    private val ended = SoftwareUpdate.CodeStanding.ENDED
    private val ok = SoftwareUpdate.CodeStanding.WORKING

    @Test fun `every standing has its own answer`() {
        assertEquals(BetaAccess.NO_CODE, betaAccess(none, false, false, false))
        assertEquals(BetaAccess.CODE_INVALID, betaAccess(bad, true, true, true))
        assertEquals(BetaAccess.CODE_ENDED, betaAccess(ended, true, true, true))
        assertEquals(BetaAccess.NO_BETA_SCOPE, betaAccess(ok, false, true, true))
        assertEquals(BetaAccess.EARLY_ACCESS_OFF, betaAccess(ok, true, false, true))
        assertEquals(BetaAccess.UPDATES_OFF, betaAccess(ok, true, true, false))
        assertEquals(BetaAccess.READY, betaAccess(ok, true, true, true))
    }

    @Test fun `the code comes before its switches, and early access before beta updates`() {
        assertEquals(BetaAccess.CODE_ENDED, betaAccess(ended, true, false, false))
        assertEquals(BetaAccess.NO_BETA_SCOPE, betaAccess(ok, false, false, false))
        assertEquals(BetaAccess.EARLY_ACCESS_OFF, betaAccess(ok, true, false, false))
    }

    @Test fun `it agrees with the note Software Update shows for the same phone`() {
        // Whenever the update check leaves betas out for a reason on this phone, the status is not READY, and the other way round.
        for (standing in SoftwareUpdate.CodeStanding.entries) for (early in listOf(true, false)) for (updates in listOf(true, false)) {
            val note = SoftwareUpdate.betaNoteFor(standing, early, updates, lookupFailed = false)
            val access = betaAccess(standing, hasBetaScope = true, earlyAccess = early, betaUpdates = updates)
            if (standing == SoftwareUpdate.CodeStanding.NONE) assertEquals(BetaAccess.NO_CODE, access)
            else assertEquals("$standing $early $updates", note == null, access == BetaAccess.READY)
        }
    }

    @Test fun `only a missing or ended code sends someone to Ko-fi`() {
        assertEquals(setOf(BetaAccess.NO_CODE, BetaAccess.CODE_ENDED), BetaAccess.entries.filter { it.needsKofi }.toSet())
        assertFalse(BetaAccess.READY.needsKofi)
        assertTrue(BetaAccess.entries.all { it.short != it.long })
    }
}
