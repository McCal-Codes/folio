package com.mccal.folio

import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.time.LocalDate
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Supporter codes verify on the phone, offline, and only when McCal signed them. */
class BetaCodesTest {
    private val keys = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
    private val publicKey = listOf(Base64.getEncoder().encodeToString(keys.public.encoded))
    private val today = LocalDate.of(2026, 9, 17)

    /** The same code `scripts/beta-code.py` mints: a 9-byte ticket and a raw r‖s signature, in Crockford base32. */
    private fun mint(scopeBits: Int, tier: Int = 1, expires: LocalDate? = null, serial: Long = 42,
        months: Int = 0, version: Int = if (months > 0) 2 else 1): String {
        val day = expires?.let { it.toEpochDay() - LocalDate.of(2026, 1, 1).toEpochDay() }?.toInt() ?: 0
        val tierByte = if (months > 0) months shl 4 or tier else tier
        val payload = byteArrayOf(version.toByte(), scopeBits.toByte(), tierByte.toByte(), (day shr 8).toByte(), day.toByte()) +
            ByteArray(4) { i -> (serial shr (24 - i * 8)).toByte() }
        val der = Signature.getInstance("SHA256withECDSA").run { initSign(keys.private); update(payload); sign() }
        return base32(payload + raw(der))
    }

    /** ASN.1 back to the pair of 32-byte numbers a code carries. */
    private fun raw(der: ByteArray): ByteArray {
        var i = 2
        fun next(): ByteArray {
            val size = der[i + 1].toInt()
            val value = der.copyOfRange(i + 2, i + 2 + size).dropWhile { it == 0.toByte() }.toByteArray()
            i += 2 + size
            return ByteArray(32 - value.size) + value
        }
        return next() + next()
    }

    private fun base32(data: ByteArray): String {
        val alphabet = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
        var buffer = 0L
        var bits = 0
        val out = StringBuilder()
        for (byte in data) {
            buffer = buffer shl 8 or (byte.toLong() and 0xFF)
            bits += 8
            while (bits >= 5) { bits -= 5; out.append(alphabet[(buffer shr bits and 31).toInt()]) }
        }
        if (bits > 0) out.append(alphabet[(buffer shl (5 - bits) and 31).toInt()])
        return out.toString()
    }

    @Test fun `a signed code unlocks what it says`() {
        val result = BetaCodes.verify(mint(0b0101, tier = 2), publicKey, today)
        val code = (result as BetaCodes.Result.Valid).code
        assertEquals(setOf(BetaCodes.SCOPE_BETA, BetaCodes.SCOPE_POWER), code.scopes)
        assertEquals(2, code.tier)
        assertEquals(42L, code.serial)
        assertEquals(null, code.expires)
    }

    @Test fun `a code typed in groups or lower case still works`() {
        val code = mint(0b0001)
        assertTrue(BetaCodes.verify(BetaCodes.group(code).lowercase(), publicKey, today) is BetaCodes.Result.Valid)
        assertTrue(BetaCodes.verify(code.chunked(4).joinToString(" "), publicKey, today) is BetaCodes.Result.Valid)
    }

    @Test fun `an expiry date is kept and then runs out`() {
        val code = mint(0b0001, expires = LocalDate.of(2026, 10, 1))
        assertEquals(LocalDate.of(2026, 10, 1), (BetaCodes.verify(code, publicKey, today) as BetaCodes.Result.Valid).code.expires)
        assertTrue(BetaCodes.verify(code, publicKey, LocalDate.of(2026, 10, 2)) is BetaCodes.Result.Expired)
    }

    @Test fun `a months code counts from the day it was redeemed`() {
        val code = (BetaCodes.verify(mint(0b1001, tier = 3, months = 2), publicKey, today) as BetaCodes.Result.Valid).code
        assertEquals(2, code.months)
        assertEquals(3, code.tier)
        assertEquals(null, code.expires)
        // Nothing runs out until the phone says when it started: a pool minted months ago still gives its full time.
        assertEquals(null, code.ends(null))
        assertEquals(LocalDate.of(2026, 11, 17), code.ends(today))
        assertTrue(code.expired(LocalDate.of(2026, 11, 18), today))
        assertTrue(!code.expired(LocalDate.of(2026, 11, 17), today))
    }

    @Test fun `months and a fixed date, whichever comes first`() {
        val code = (BetaCodes.verify(mint(0b0001, months = 6, expires = LocalDate.of(2026, 10, 1)), publicKey, today)
            as BetaCodes.Result.Valid).code
        assertEquals(LocalDate.of(2026, 10, 1), code.ends(today))
        // And the fixed date still ends it on its own, with no redemption date in hand.
        assertTrue(BetaCodes.verify(mint(0b0001, months = 6, expires = LocalDate.of(2026, 10, 1)), publicKey,
            LocalDate.of(2026, 10, 2)) is BetaCodes.Result.Expired)
    }

    @Test fun `a code from a newer Folio isn't read as an old one`() {
        assertEquals(BetaCodes.Result.Unreadable, BetaCodes.verify(mint(0b0001, version = 3), publicKey, today))
    }

    @Test fun `the developer scope is its own bit, and doesn't disturb the others`() {
        val dev = (BetaCodes.verify(mint(0b10000), publicKey, today) as BetaCodes.Result.Valid).code
        assertEquals(setOf(BetaCodes.SCOPE_DEV), dev.scopes)
        // Every scope at once, to prove bit 4 didn't move the ones that were already spoken for.
        val all = (BetaCodes.verify(mint(0b11111), publicKey, today) as BetaCodes.Result.Valid).code
        assertEquals(setOf(BetaCodes.SCOPE_BETA, BetaCodes.SCOPE_LOOK, BetaCodes.SCOPE_POWER,
            BetaCodes.SCOPE_KEYS, BetaCodes.SCOPE_DEV), all.scopes)
        // A supporter's code carries no developer scope.
        val supporter = (BetaCodes.verify(mint(0b1111), publicKey, today) as BetaCodes.Result.Valid).code
        assertEquals(false, BetaCodes.SCOPE_DEV in supporter.scopes)
    }

    @Test fun `a withdrawn code stops working`() {
        val code = mint(0b0001, serial = 7)
        assertTrue(BetaCodes.verify(code, publicKey, today) is BetaCodes.Result.Valid)
        assertEquals(BetaCodes.Result.Withdrawn, BetaCodes.verify(code, publicKey, today, withdrawn = setOf(7L)))
        // Only that one: someone else's code is untouched.
        assertTrue(BetaCodes.verify(mint(0b0001, serial = 8), publicKey, today, withdrawn = setOf(7L)) is BetaCodes.Result.Valid)
    }

    @Test fun `codes nobody signed, or someone else signed, are refused`() {
        val other = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
        assertEquals(BetaCodes.Result.NotOurs,
            BetaCodes.verify(mint(0b1111), listOf(Base64.getEncoder().encodeToString(other.public.encoded)), today))
        // One character changed is a code we didn't sign, never a different set of features. The change has to land
        // in the middle: base32's last character carries padding bits, and flipping those decodes to the same bytes.
        val tampered = mint(0b0001).let { code ->
            val at = code.length / 2
            code.take(at) + (if (code[at] == 'Z') '0' else 'Z') + code.drop(at + 1)
        }
        assertTrue(BetaCodes.verify(tampered, publicKey, today) is BetaCodes.Result.NotOurs)
        assertEquals(BetaCodes.Result.Unreadable, BetaCodes.verify("FOLIO-12345", publicKey, today))
        assertEquals(BetaCodes.Result.Unreadable, BetaCodes.verify("", publicKey, today))
    }
}
