package com.mccal.folio

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Package Safe Mode counts a crash only when the last run really crashed (S4 in the 5 Oct audit), once per process. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SafeModeCrashFlagTest {
    private val context get() = ApplicationProvider.getApplicationContext<android.app.Application>()

    @Test fun `a start after a crash says so once, and a clean start after that does not`() {
        SafeMode.onStart(context)
        SafeMode.onCrash(context)
        SafeMode.onStart(context)
        assertTrue(SafeMode.takeCrashedLastRun())
        assertFalse(SafeMode.takeCrashedLastRun())
        SafeMode.onStart(context)
        assertFalse(SafeMode.takeCrashedLastRun())
    }

    @Test fun `a fold or an unfold, asking again in the same process, never counts as a crash`() {
        SafeMode.onStart(context)
        assertFalse(SafeMode.takeCrashedLastRun())
        assertFalse(SafeMode.takeCrashedLastRun())
    }

    @Test fun `a process that ends before Home reads the flag does not use it up`() {
        SafeMode.onStart(context)
        SafeMode.onCrash(context)
        // A background job starts the process and it is killed before Home ever asks.
        SafeMode.onStart(context)
        // The next process, which does show Home, still sees the crash.
        SafeMode.onStart(context)
        assertTrue(SafeMode.takeCrashedLastRun())
        assertFalse(SafeMode.takeCrashedLastRun())
        SafeMode.onStart(context)
        assertFalse("and once it has been read, it is gone", SafeMode.takeCrashedLastRun())
    }
}
