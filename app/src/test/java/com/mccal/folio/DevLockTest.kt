package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DevLockTest {
    // A few iterations keep these fast; the stored record carries its own count, so the real 120,000 is a constant, not a test.
    private val fast = 1_000
    private fun record(pass: String = "correct horse") = DevLock.create(pass.toCharArray(), iterations = fast)
    private fun check(r: DevLockRecord?, pass: String, now: Long = 0L) = DevLock.check(r, pass.toCharArray(), now)

    @Test fun `a passphrase must be long enough and not blank`() {
        assertEquals(DevLock.Problem.TOO_SHORT, DevLock.problem("short".toCharArray()))
        assertEquals(DevLock.Problem.BLANK, DevLock.problem("         ".toCharArray()))
        assertNull(DevLock.problem("exactly 8".toCharArray()))
        assertNull(DevLock.problem("eight123".toCharArray()))
    }

    @Test fun `the right passphrase unlocks and the wrong one does not`() {
        val r = record()
        assertTrue(check(r, "correct horse") is DevLock.Result.Unlocked)
        assertTrue(check(r, "correct horsf") is DevLock.Result.Wrong)
        assertTrue(check(r, "") is DevLock.Result.Wrong)
    }

    @Test fun `nothing is stored but a salted hash`() {
        val a = record(); val b = record()
        assertNotEquals("two records of one passphrase differ by salt", a.salt.toList(), b.salt.toList())
        assertNotEquals(a.hash.toList(), b.hash.toList())
        assertFalse(String(a.hash, Charsets.ISO_8859_1).contains("correct horse"))
        assertEquals(32, a.hash.size)
    }

    @Test fun `no passphrase set says so`() {
        assertEquals(DevLock.Result.NotSet, check(null, "anything at all"))
    }

    @Test fun `five wrong tries are free then the wait doubles up to fifteen minutes`() {
        // Index is the number of wrong tries so far: the fifth is the first to start a wait.
        assertEquals(listOf(0L, 0L, 0L, 0L, 0L, 30_000L, 60_000L, 120_000L), (0..7).map { DevLock.waitAfter(it) })
        assertEquals(DevLock.MAX_WAIT_MS, DevLock.waitAfter(30))
        assertEquals(DevLock.MAX_WAIT_MS, DevLock.waitAfter(Int.MAX_VALUE))
    }

    @Test fun `repeated wrong tries start a wait and a right passphrase cannot skip it`() {
        var r: DevLockRecord = record()
        var now = 1_000_000L
        repeat(4) {
            val wrong = check(r, "nope nope nope", now) as DevLock.Result.Wrong
            assertEquals(0L, wrong.waitMs); r = wrong.record
        }
        val fifth = check(r, "nope nope nope", now) as DevLock.Result.Wrong
        assertEquals(30_000L, fifth.waitMs); assertEquals(0, fifth.triesBeforeWait)
        r = fifth.record
        // During the wait even the right passphrase is not looked at.
        val early = check(r, "correct horse", now + 10_000L)
        assertEquals(DevLock.Result.Waiting(20_000L), early)
        // After it, the right passphrase gets in and clears the count.
        val later = check(r, "correct horse", now + 30_000L) as DevLock.Result.Unlocked
        assertEquals(0, later.record.failures); assertEquals(0L, later.record.lockedUntilMs)
    }

    @Test fun `tries left before a wait counts down`() {
        var r: DevLockRecord = record()
        val left = (1..5).map { (check(r, "wrong wrong", 0L) as DevLock.Result.Wrong).also { w -> r = w.record }.triesBeforeWait }
        assertEquals(listOf(4, 3, 2, 1, 0), left)
    }

    @Test fun `a successful try clears earlier failures`() {
        var r: DevLockRecord = record()
        r = (check(r, "wrong wrong", 0L) as DevLock.Result.Wrong).record
        r = (check(r, "wrong wrong", 0L) as DevLock.Result.Wrong).record
        assertEquals(2, r.failures)
        assertEquals(0, (check(r, "correct horse", 0L) as DevLock.Result.Unlocked).record.failures)
    }

    @Test fun `creating a record from an unchecked passphrase is refused`() {
        val failed = runCatching { DevLock.create("short".toCharArray(), iterations = fast) }
        assertTrue(failed.isFailure)
    }
}
