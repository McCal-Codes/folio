package com.mccal.folio

import android.app.ApplicationExitInfo
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** S5 in the 5 Oct audit: native crashes, freezes and failed starts count toward Safe Mode too. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SafeModeExitsTest {
    private val native = ApplicationExitInfo.REASON_CRASH_NATIVE
    private val anr = ApplicationExitInfo.REASON_ANR
    private val java = ApplicationExitInfo.REASON_CRASH
    private val context get() = ApplicationProvider.getApplicationContext<android.app.Application>()

    @Test fun `a native crash or a freeze soon after the start counts, a Java crash does not (the handler counts it)`() {
        val exits = listOf(ExitRecord(native, 10_000), ExitRecord(anr, 12_000), ExitRecord(java, 14_000))
        assertEquals(listOf(native, anr), SafeModeExits.quick(exits, previousStartMs = 5_000, seenUntilMs = 0, windowMs = 30_000).map { it.reason })
    }

    @Test fun `an ending after the 30 second window, or already counted, or with no recorded start, does not count`() {
        assertTrue(SafeModeExits.quick(listOf(ExitRecord(native, 40_000)), 5_000, 0, 30_000).isEmpty())
        assertTrue(SafeModeExits.quick(listOf(ExitRecord(native, 10_000)), 5_000, seenUntilMs = 10_000, windowMs = 30_000).isEmpty())
        assertTrue(SafeModeExits.quick(listOf(ExitRecord(native, 10_000)), previousStartMs = 0, seenUntilMs = 0, windowMs = 30_000).isEmpty())
    }

    @Test fun `the newest unseen ending decides whether the last run crashed`() {
        assertTrue(SafeModeExits.crashedLast(listOf(ExitRecord(native, 20_000), ExitRecord(ApplicationExitInfo.REASON_USER_REQUESTED, 10_000)), 0))
        assertFalse(SafeModeExits.crashedLast(listOf(ExitRecord(ApplicationExitInfo.REASON_USER_REQUESTED, 20_000), ExitRecord(native, 10_000)), 0))
        assertFalse(SafeModeExits.crashedLast(listOf(ExitRecord(native, 20_000)), seenUntilMs = 20_000))
    }

    @Test fun `two native crashes in a row at the start put Folio in Safe Mode on the next start`() {
        SafeMode.onStart(context) { emptyList() }
        val first = ExitRecord(native, System.currentTimeMillis() + 5_000)
        SafeMode.onStart(context) { listOf(first) }
        assertFalse("one is not enough", SafeMode.active)
        val second = ExitRecord(anr, System.currentTimeMillis() + 6_000)
        SafeMode.onStart(context) { listOf(second, first) }
        assertTrue(SafeMode.active)
        assertTrue(SafeMode.takeCrashedLastRun())
    }

    @Test fun `the same ending is never counted at two starts`() {
        SafeMode.onStart(context) { emptyList() }
        val one = ExitRecord(native, System.currentTimeMillis() + 5_000)
        SafeMode.onStart(context) { listOf(one) }
        SafeMode.onStart(context) { listOf(one) }
        assertFalse(SafeMode.active)
    }

    @Test fun `a native crash Android recorded survives a process that never reaches Home`() {
        val exits = listOf(ExitRecord(native, 10_000))
        // The first start after the crash: a background job, killed before Home exists, never asks.
        SafeMode.onStart(context) { exits }
        // The next start, with the same record already seen: Home still learns the last run crashed.
        SafeMode.onStart(context) { exits }
        assertTrue(SafeMode.takeCrashedLastRun())
        assertFalse(SafeMode.takeCrashedLastRun())
        SafeMode.onStart(context) { exits }
        assertFalse("and it is gone once Home has read it", SafeMode.takeCrashedLastRun())
    }

    @Test fun `a failed start counts without an earlier start marker, once, and only while it is recent`() {
        val init = ApplicationExitInfo.REASON_INITIALIZATION_FAILURE
        val now = 1_000_000L
        val recent = listOf(ExitRecord(init, now - 20_000), ExitRecord(init, now - 40_000))
        assertEquals("two quick failures, though the last recorded start is long before and older", 2,
            SafeModeExits.quick(recent, previousStartMs = 5_000, seenUntilMs = 0, windowMs = 30_000, nowMs = now).size)
        assertEquals("no recorded start at all still counts them", 2,
            SafeModeExits.quick(recent, previousStartMs = 0, seenUntilMs = 0, windowMs = 30_000, nowMs = now).size)
        assertEquals("one already counted is not counted again", 1,
            SafeModeExits.quick(recent, previousStartMs = 5_000, seenUntilMs = now - 30_000, windowMs = 30_000, nowMs = now).size)
        assertEquals("an old failure is history", 0,
            SafeModeExits.quick(listOf(ExitRecord(init, now - 3_600_000)), previousStartMs = 5_000, seenUntilMs = 0, windowMs = 30_000, nowMs = now).size)
        assertEquals("and a native crash still needs an earlier start to measure against", 0,
            SafeModeExits.quick(listOf(ExitRecord(native, now - 1_000)), previousStartMs = 0, seenUntilMs = 0, windowMs = 30_000, nowMs = now).size)
    }
}
