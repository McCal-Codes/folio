package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** When a Ko-fi member's phone asks the worker for their next code, and what it does with the answer. */
class SupporterAutoRenewTest {
    private val today = LocalDate.of(2026, 11, 17)

    private fun active(daysLeft: Long?) = SupporterState.Active(today.plusDays(daysLeft ?: 0), daysLeft, serial = 1)

    @Test fun `it asks in the last week of a code, and not before`() {
        assertFalse(SupporterRenewal.due(active(30), 0L, today))
        assertFalse(SupporterRenewal.due(active(8), 0L, today))
        assertTrue(SupporterRenewal.due(active(7), 0L, today))
        assertTrue(SupporterRenewal.due(active(0), 0L, today))
    }

    @Test fun `it asks once a code has ended too, for a phone that was off`() {
        assertTrue(SupporterRenewal.due(SupporterState.Expired(today.minusDays(3), serial = 1), 0L, today))
    }

    @Test fun `a code that never ends, or no code, never asks`() {
        assertFalse(SupporterRenewal.due(SupporterState.Active(null, null, serial = 1), 0L, today))
        assertFalse(SupporterRenewal.due(SupporterState.None, 0L, today))
    }

    @Test fun `it asks at most once a day`() {
        assertFalse(SupporterRenewal.due(active(3), today.toEpochDay(), today))
        assertTrue(SupporterRenewal.due(active(3), today.minusDays(1).toEpochDay(), today))
    }

    @Test fun `only an https address is asked, because a code never travels in the clear`() {
        assertEquals("https://worker.example/renew", SupporterRenewal.address("https://worker.example/"))
        assertNull(SupporterRenewal.address("http://worker.example"))
        assertNull(SupporterRenewal.address(""))
    }

    @Test fun `a newer code in the answer is taken, the same one however it is written is not`() {
        val held = "ABCDE-FGHJK-MNPQR"
        assertEquals("STVWX-YZ012-34567", SupporterRenewal.newerCode("""{"code":"STVWX-YZ012-34567"}""", held))
        assertNull(SupporterRenewal.newerCode("""{"code":"abcde fghjk-mnpqr"}""", held))
    }

    private fun code(vararg scopes: String) = BetaCodes.Code(scopes.toSet(), tier = 1, expiryDay = 0, serial = 1, months = 2)

    @Test fun `a code that opens less never replaces one that works`() {
        val builder = code(BetaCodes.SCOPE_BETA, BetaCodes.SCOPE_KEYS)
        val plain = code(BetaCodes.SCOPE_BETA)
        assertFalse(SupporterRenewal.opensAtLeast(offered = plain, held = builder))
        assertTrue(SupporterRenewal.opensAtLeast(offered = builder, held = plain))
        assertTrue(SupporterRenewal.opensAtLeast(offered = builder, held = builder))
    }

    @Test fun `with nothing held, any genuine code may be taken`() {
        assertTrue(SupporterRenewal.opensAtLeast(offered = code(BetaCodes.SCOPE_BETA), held = null))
    }

    @Test fun `an answer that is not a code is ignored`() {
        assertNull(SupporterRenewal.newerCode("", "ABCDE"))
        assertNull(SupporterRenewal.newerCode("not json", "ABCDE"))
        assertNull(SupporterRenewal.newerCode("""{"code":""}""", "ABCDE"))
        assertNull(SupporterRenewal.newerCode("""{"other":"x"}""", "ABCDE"))
    }
}
