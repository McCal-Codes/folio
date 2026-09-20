package com.mccal.folio

import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.time.LocalDate

/**
 * Supporter codes. A code is a short signed ticket: Folio checks it against a public key built into the app, so
 * redeeming works offline, needs no account, and tells nobody that you supported. Only a code signed with McCal's
 * private key verifies, and Folio keeps the code itself and nothing else.
 *
 * Folio is open source, so this is a thank-you, not a lock: anyone can build the app themselves. The codes exist so
 * the official builds can give supporters their early access without asking who anyone is.
 */
internal object BetaCodes {
    /** What a code can unlock. The names are what feature code asks for, so they outlive any wording in Settings. */
    const val SCOPE_BETA = "beta"    // new features a release or two early
    const val SCOPE_LOOK = "look"    // personalization extras
    const val SCOPE_POWER = "power"  // power-user automation
    const val SCOPE_KEYS = "keys"    // Folio Keyboard extras
    const val SCOPE_DEV = "dev"      // the developer's own switches, and only in a development build

    // Appended, never reordered: a bit that already means something has to keep meaning it, or codes already handed
    // out would unlock the wrong thing.
    private val SCOPE_BITS = listOf(SCOPE_BETA, SCOPE_LOOK, SCOPE_POWER, SCOPE_KEYS, SCOPE_DEV)

    /** Day 0 of the expiry field, so two bytes cover well past any plan of mine. */
    private val EPOCH: Long = LocalDate.of(2026, 1, 1).toEpochDay()

    /**
     * Version 1 codes carry a fixed last day, decided when the code was minted. Version 2 adds the other kind: a
     * number of months that starts the day the code is redeemed, so a code from a pool minted months ago still gives
     * a full month. The months share the tier byte — months in the high nibble, tier in the low one — so a code is
     * the same length either way, and a version 1 code reads exactly as it always did.
     */
    private const val VERSION = 1
    private const val VERSION_MONTHS = 2
    private const val PAYLOAD = 9
    private const val SIGNATURE = 64

    /** Crockford's base32: no I, L, O or U, so a typed code can't be read wrong. */
    private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

    internal data class Code(val scopes: Set<String>, val tier: Int, val expiryDay: Int, val serial: Long,
        /** Months of access counted from the day the code is redeemed; 0 means it doesn't work that way. */
        val months: Int = 0) {
        /** The last day this code works on its own terms, or null when it never expires. */
        val expires: LocalDate? get() = expiryDay.takeIf { it > 0 }?.let { LocalDate.ofEpochDay(EPOCH + it) }

        /**
         * The last day this code works, given the day it was first redeemed here. A code can carry both a window and
         * a fixed last day; whichever comes first wins, so neither can be stretched by the other.
         */
        fun ends(redeemed: LocalDate?): LocalDate? {
            val window = redeemed?.takeIf { months > 0 }?.plusMonths(months.toLong())
            return listOfNotNull(expires, window).minOrNull()
        }

        fun expired(today: LocalDate = LocalDate.now(), redeemed: LocalDate? = null): Boolean =
            ends(redeemed)?.isBefore(today) == true
    }

    internal sealed interface Result {
        data class Valid(val code: Code) : Result
        data class Expired(val code: Code) : Result
        /** The text isn't a Folio code at all (wrong length, stray characters, a newer format). */
        data object Unreadable : Result
        /** Well-formed, but not signed by Folio: a typo, or a code from somewhere else. */
        data object NotOurs : Result
        /** Ours, but retired: a code that went around publicly, or one a refund took back. */
        data object Withdrawn : Result
    }

    fun verify(text: String, keys: List<String>, today: LocalDate = LocalDate.now(),
        withdrawn: Set<Long> = emptySet()): Result {
        val bytes = decode(text) ?: return Result.Unreadable
        val version = bytes.firstOrNull()?.toInt()
        if (bytes.size != PAYLOAD + SIGNATURE || (version != VERSION && version != VERSION_MONTHS)) {
            return Result.Unreadable
        }
        val payload = bytes.copyOfRange(0, PAYLOAD)
        val signature = derSignature(bytes.copyOfRange(PAYLOAD, bytes.size)) ?: return Result.Unreadable
        val signed = keys.any { key -> runCatching { verifyWith(key, payload, signature) }.getOrDefault(false) }
        if (!signed) return Result.NotOurs
        val scopes = SCOPE_BITS.filterIndexed { bit, _ -> payload[1].toInt() shr bit and 1 == 1 }.toSet()
        val tierByte = payload[2].toInt() and 0xFF
        val months = if (version == VERSION_MONTHS) tierByte shr 4 else 0
        val tier = if (version == VERSION_MONTHS) tierByte and 0x0F else tierByte
        val code = Code(scopes, tier,
            (payload[3].toInt() and 0xFF shl 8) or (payload[4].toInt() and 0xFF),
            payload.copyOfRange(5, 9).fold(0L) { total, b -> total shl 8 or (b.toLong() and 0xFF) }, months)
        return when {
            code.serial in withdrawn -> Result.Withdrawn
            code.expired(today) -> Result.Expired(code)
            else -> Result.Valid(code)
        }
    }

    private fun verifyWith(key: String, payload: ByteArray, signature: ByteArray): Boolean {
        val spec = X509EncodedKeySpec(java.util.Base64.getMimeDecoder().decode(key))
        val publicKey = KeyFactory.getInstance("EC").generatePublic(spec)
        return Signature.getInstance("SHA256withECDSA").run { initVerify(publicKey); update(payload); verify(signature) }
    }

    /** Groups, spaces and lower case are all fine: people copy codes out of email. */
    internal fun decode(text: String): ByteArray? {
        val clean = text.uppercase(java.util.Locale.ROOT).filter { it != '-' && !it.isWhitespace() }
            .map { if (it == 'I' || it == 'L') '1' else if (it == 'O') '0' else it }
        if (clean.isEmpty()) return null
        var buffer = 0L
        var bits = 0
        val out = java.io.ByteArrayOutputStream()
        for (c in clean) {
            val value = ALPHABET.indexOf(c).takeIf { it >= 0 } ?: return null
            buffer = buffer shl 5 or value.toLong()
            bits += 5
            if (bits >= 8) { bits -= 8; out.write((buffer shr bits and 0xFF).toInt()) }
        }
        return out.toByteArray()
    }

    /** Codes carry the raw r‖s pair; Java wants it wrapped as ASN.1 before it will look at it. */
    private fun derSignature(raw: ByteArray): ByteArray? {
        if (raw.size != SIGNATURE) return null
        val r = asn1Integer(raw.copyOfRange(0, 32))
        val s = asn1Integer(raw.copyOfRange(32, 64))
        return byteArrayOf(0x30, (r.size + s.size).toByte()) + r + s
    }

    private fun asn1Integer(value: ByteArray): ByteArray {
        var start = 0
        while (start < value.size - 1 && value[start] == 0.toByte()) start++
        val trimmed = value.copyOfRange(start, value.size)
        val body = if (trimmed[0].toInt() and 0x80 != 0) byteArrayOf(0) + trimmed else trimmed
        return byteArrayOf(0x02, body.size.toByte()) + body
    }

    /** How a code reads back to the person who typed it: FOLIO-XXXXX-XXXXX-… */
    internal fun group(text: String): String = text.uppercase(java.util.Locale.ROOT).filter { it.isLetterOrDigit() }
        .chunked(5).joinToString("-")
}
