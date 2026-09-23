package com.mccal.folio

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
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
 * The lasting Supporter badge: $15 or more mints a code carrying [BetaCodes.SCOPE_THANKS], and redeeming it writes
 * down the month. It is a thank-you rather than access, so it has to outlive the code itself: the month running out,
 * Remove Code, and a later code without the scope all leave it alone, and it never moves to a newer month.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SupporterBadgeTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val pair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
    private val keys = listOf(Base64.getEncoder().encodeToString(pair.public.encoded))
    private val beta = 1 shl 0
    private val thanks = 1 shl 5

    @Before fun clean() {
        Supporter.remove(context)
        context.getSharedPreferences("folio", 0).edit().clear().apply()
    }

    private fun mint(scopeBits: Int, serial: Long = 7, months: Int = 1): String {
        val version: Byte = 2
        val tierByte: Byte = ((months shl 4) or 1).toByte()
        val payload = byteArrayOf(version, scopeBits.toByte(), tierByte, 0, 0) +
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

    private fun redeem(code: String, day: LocalDate) =
        Supporter.redeem(context, code, today = day, keys = keys, onBetaChannel = {}, onSupporterSource = {})

    @Test fun `a code without the thanks scope earns no badge`() {
        assertTrue(redeem(mint(beta), LocalDate.of(2026, 9, 22)) is BetaCodes.Result.Valid)
        assertNull(Supporter.badgeSince(context))
    }

    @Test fun `a thanks code writes down the month it was redeemed`() {
        assertTrue(redeem(mint(beta or thanks), LocalDate.of(2026, 9, 22)) is BetaCodes.Result.Valid)
        assertEquals("2026-09", Supporter.badgeSince(context))
    }

    @Test fun `the badge outlives the code, and its month never moves`() {
        redeem(mint(beta or thanks, serial = 1), LocalDate.of(2026, 9, 22))
        Supporter.remove(context)
        assertEquals("2026-09", Supporter.badgeSince(context))
        // A second thanks code, months later, is still the same supporter: the badge keeps the first month.
        redeem(mint(beta or thanks, serial = 2), LocalDate.of(2027, 3, 4))
        assertEquals("2026-09", Supporter.badgeSince(context))
        // And a plain code afterwards never takes it away.
        redeem(mint(beta, serial = 3), LocalDate.of(2027, 4, 1))
        assertEquals("2026-09", Supporter.badgeSince(context))
    }

    @Test fun `the badge month is written the reader's way`() {
        assertEquals("September 2026", monthYearLabel("2026-09"))
        assertEquals("nonsense", monthYearLabel("nonsense"))
    }
}
