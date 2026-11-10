package com.mccal.folio

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.time.LocalDate
import java.util.Base64

/**
 * A supporter whose code has run out can still get back in. These pin the whole renewal path: an expired code is
 * refused without side effects, a new code redeems over the dead one without removing anything first, the switches
 * come back on, and pasting the dead code again never hands out more time.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SupporterRenewalTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val pair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
    private val keys = listOf(Base64.getEncoder().encodeToString(pair.public.encoded))

    @Before fun clean() = Supporter.remove(context)

    /** The same shape `scripts/beta-code.py` mints. */
    private fun mint(scopeBits: Int, expires: LocalDate? = null, serial: Long = 7, months: Int = 0): String {
        val day = expires?.let { (it.toEpochDay() - LocalDate.of(2026, 1, 1).toEpochDay()).toInt() } ?: 0
        // A months code is version 2, with the months in the high half of the tier byte.
        val version: Byte = if (months > 0) 2 else 1
        val tierByte: Byte = if (months > 0) ((months shl 4) or 1).toByte() else 1
        val payload = byteArrayOf(version, scopeBits.toByte(), tierByte, (day shr 8).toByte(), day.toByte()) +
            ByteArray(4) { i -> (serial shr (24 - i * 8)).toByte() }
        val der = Signature.getInstance("SHA256withECDSA").run { initSign(pair.private); update(payload); sign() }
        var i = 2
        fun part(): ByteArray {
            val size = der[i + 1].toInt()
            val v = der.copyOfRange(i + 2, i + 2 + size).dropWhile { it == 0.toByte() }.toByteArray()
            i += 2 + size
            return ByteArray(32 - v.size) + v
        }
        val bytes = payload + part() + part()
        val alphabet = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
        var buffer = 0L; var bits = 0; val out = StringBuilder()
        for (b in bytes) {
            buffer = buffer shl 8 or (b.toLong() and 0xFF); bits += 8
            while (bits >= 5) { bits -= 5; out.append(alphabet[(buffer shr bits and 31).toInt()]) }
        }
        if (bits > 0) out.append(alphabet[(buffer shl (5 - bits) and 31).toInt()])
        return out.toString()
    }

    private val beta = 1 shl 0
    private val oct1 = LocalDate.of(2026, 10, 1)

    @Test fun `a code past its last day is refused, and nothing is stored or switched on`() {
        var channel: Boolean? = null; val source = mutableListOf<Boolean>()
        val result = Supporter.redeem(context, mint(beta, expires = oct1), oct1.plusDays(1), keys, { channel = it }, { source += it })
        assertTrue(result is BetaCodes.Result.Expired)
        assertNull(Supporter.storedText(context))
        assertFalse(Supporter.betaOn(context))
        assertNull("the update channel is left alone", channel)
        assertTrue("and no source is added", source.isEmpty())
    }

    @Test fun `a code redeemed on its last day works that day and not the next`() {
        assertTrue(Supporter.redeem(context, mint(beta, expires = oct1), oct1, keys, {}, {}) is BetaCodes.Result.Valid)
        assertNotNull(Supporter.code(context, oct1, keys))
        assertNull(Supporter.code(context, oct1.plusDays(1), keys))
    }

    @Test fun `a new code redeems over one that has run out, with no removing first`() {
        Supporter.redeem(context, mint(beta, expires = oct1, serial = 1), oct1.minusDays(10), keys, {}, {})
        assertNull("the old code has run out", Supporter.code(context, oct1.plusDays(5), keys))

        val renewed = Supporter.redeem(context, mint(beta, expires = oct1.plusMonths(3), serial = 2), oct1.plusDays(5), keys, {}, {})
        assertTrue(renewed is BetaCodes.Result.Valid)
        assertNotNull("the new code works", Supporter.code(context, oct1.plusDays(5), keys))
        assertEquals("and it is the new code that is stored", 2L, Supporter.code(context, oct1.plusDays(5), keys)?.serial)
    }

    @Test fun `renewing after a lapse switches the betas and the source back on`() {
        Supporter.redeem(context, mint(beta, expires = oct1, serial = 1), oct1.minusDays(10), keys, {}, {})
        // The person turned early access off, then the code ran out.
        Supporter.setBetaOn(context, false)
        var channel: Boolean? = null; val source = mutableListOf<Boolean>()
        Supporter.redeem(context, mint(beta, expires = oct1.plusMonths(3), serial = 2), oct1.plusDays(5), keys, { channel = it }, { source += it })
        // With no working code it counts as a first redemption, as a new supporter's would.
        assertTrue(Supporter.betaOn(context))
        assertEquals(true, channel)
        assertEquals(listOf(true), source)
        assertTrue(Supporter.has(context, BetaCodes.SCOPE_BETA, keys))
    }

    @Test fun `renewing while the old code still works keeps what the person chose`() {
        Supporter.redeem(context, mint(beta, expires = oct1.plusMonths(1), serial = 1), oct1, keys, {}, {})
        Supporter.setBetaOn(context, false)
        Supporter.redeem(context, mint(beta, expires = oct1.plusMonths(6), serial = 2), oct1.plusDays(3), keys, { error("channel touched") }, { error("source touched") })
        assertFalse("still off, because the person turned it off", Supporter.betaOn(context))
        assertEquals(2L, Supporter.code(context, oct1.plusDays(3), keys)?.serial)
    }

    @Test fun `a months code that has run out stays out when it is pasted again`() {
        val first = mint(beta, months = 1, serial = 5)
        assertTrue(Supporter.redeem(context, first, oct1, keys, {}, {}) is BetaCodes.Result.Valid)
        val later = oct1.plusDays(45)
        assertNull(Supporter.code(context, later, keys))
        // Pasting it again is refused: the window it started is remembered, not restarted.
        assertTrue(Supporter.redeem(context, first, later, keys, {}, {}) is BetaCodes.Result.Expired)
        assertNull(Supporter.code(context, later, keys))
    }

    @Test fun `a lapsed supporter who removes the code and renews is no worse off`() {
        Supporter.redeem(context, mint(beta, expires = oct1, serial = 1), oct1.minusDays(5), keys, {}, {})
        Supporter.remove(context) {}
        assertTrue(Supporter.redeem(context, mint(beta, expires = oct1.plusMonths(2), serial = 3), oct1.plusDays(2), keys, {}, {}) is BetaCodes.Result.Valid)
        assertNotNull(Supporter.code(context, oct1.plusDays(2), keys))
    }

    @Test fun `a code nobody signed is never a renewal`() {
        Supporter.redeem(context, mint(beta, expires = oct1, serial = 1), oct1.minusDays(5), keys, {}, {})
        val result = Supporter.redeem(context, "ABCDE-FGHJK", oct1.plusDays(5), keys, {}, {})
        assertFalse(result is BetaCodes.Result.Valid)
        assertNull(Supporter.code(context, oct1.plusDays(5), keys))
    }

    @Test fun `the thank-you badge outlives the code and a renewal does not move it`() {
        val scopeThanks = 1 shl 5
        Supporter.redeem(context, mint(beta or scopeThanks, expires = oct1, serial = 1), oct1.minusDays(20), keys, {}, {})
        val badge = Supporter.badgeSince(context)
        assertNotNull(badge)
        Supporter.redeem(context, mint(beta or scopeThanks, expires = oct1.plusMonths(3), serial = 2), oct1.plusDays(10), keys, {}, {})
        assertEquals(badge, Supporter.badgeSince(context))
    }
}
