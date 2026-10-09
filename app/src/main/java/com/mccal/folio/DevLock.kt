package com.mccal.folio

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * The passphrase behind Folio Dev's Developer settings (docs/release-0.6.9-plan.md, dev login). Pure logic, no Android:
 * the screen and the preferences file come later, and this is what they will call.
 *
 * The passphrase is kept as a salted PBKDF2 hash, never as text, and only on the phone. It is a convenience gate against a
 * stray tap, not a security boundary: Folio Dev is debuggable, so anyone with adb can edit its preferences. Nothing here
 * is for release builds.
 */
internal data class DevLockRecord(
    val salt: ByteArray,
    val hash: ByteArray,
    val iterations: Int,
    /** Wrong tries in a row. */
    val failures: Int = 0,
    /** Epoch milliseconds before which no try is looked at, or 0. */
    val lockedUntilMs: Long = 0L,
) {
    // A ByteArray in a data class compares by identity; compare by content so two equal records are equal.
    override fun equals(other: Any?) = other is DevLockRecord && salt.contentEquals(other.salt) && hash.contentEquals(other.hash) &&
        iterations == other.iterations && failures == other.failures && lockedUntilMs == other.lockedUntilMs
    override fun hashCode() = 31 * (31 * salt.contentHashCode() + hash.contentHashCode()) + iterations
}

internal object DevLock {
    const val MIN_LENGTH = 8
    const val ITERATIONS = 120_000
    private const val SALT_BYTES = 16
    private const val KEY_BITS = 256

    /** Wrong tries allowed before the first wait. */
    const val FREE_TRIES = 5
    const val FIRST_WAIT_MS = 30_000L
    const val MAX_WAIT_MS = 15 * 60_000L

    /** How long Unlocked lasts when Settings stays open. Kept in memory by the caller, never saved. */
    const val UNLOCK_MS = 15 * 60_000L

    enum class Problem { TOO_SHORT, BLANK }

    /** Why [passphrase] can't be used, or null when it can. Whitespace around it counts as part of it, but it can't be all whitespace. */
    fun problem(passphrase: CharArray): Problem? = when {
        passphrase.all { it.isWhitespace() } -> Problem.BLANK
        passphrase.size < MIN_LENGTH -> Problem.TOO_SHORT
        else -> null
    }

    fun create(passphrase: CharArray, random: SecureRandom = SecureRandom(), iterations: Int = ITERATIONS): DevLockRecord {
        require(problem(passphrase) == null) { "the passphrase was not checked with problem() first" }
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        return DevLockRecord(salt, derive(passphrase, salt, iterations), iterations)
    }

    sealed interface Result {
        /** The passphrase matched. [record] has the failure count cleared. */
        data class Unlocked(val record: DevLockRecord) : Result
        /** It did not match. [record] carries the new failure count and any wait. */
        data class Wrong(val record: DevLockRecord, val triesBeforeWait: Int, val waitMs: Long) : Result
        /** Too many wrong tries: this try was not looked at, so a right passphrase does not get in early. */
        data class Waiting(val retryAfterMs: Long) : Result
        /** No passphrase has been set. */
        data object NotSet : Result
    }

    /** The wait after [failures] wrong tries in a row: none for the first five, then 30 s, doubling, at most 15 min. */
    fun waitAfter(failures: Int): Long {
        if (failures < FREE_TRIES) return 0L
        val doublings = (failures - FREE_TRIES).coerceAtMost(10)
        return (FIRST_WAIT_MS shl doublings).coerceAtMost(MAX_WAIT_MS)
    }

    fun check(record: DevLockRecord?, passphrase: CharArray, nowMs: Long): Result {
        if (record == null) return Result.NotSet
        if (nowMs < record.lockedUntilMs) return Result.Waiting(record.lockedUntilMs - nowMs)
        val attempt = derive(passphrase, record.salt, record.iterations)
        if (MessageDigest.isEqual(attempt, record.hash)) return Result.Unlocked(record.copy(failures = 0, lockedUntilMs = 0L))
        val failures = record.failures + 1
        val wait = waitAfter(failures)
        val next = record.copy(failures = failures, lockedUntilMs = if (wait > 0) nowMs + wait else 0L)
        return Result.Wrong(next, (FREE_TRIES - failures).coerceAtLeast(0), wait)
    }

    private fun derive(passphrase: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(passphrase, salt, iterations, KEY_BITS)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}
