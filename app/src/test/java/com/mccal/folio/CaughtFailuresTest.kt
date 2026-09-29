package com.mccal.folio

import kotlin.coroutines.cancellation.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `caught { }` is `runCatching` that leaves a note in the Diagnostics trail. The note says where and what type of
 * failure, never the message (which can hold a path or a name), repeats share one line, and cancellation is never
 * swallowed.
 */
class CaughtFailuresTest {
    private fun lastLine() = Diagnostics.trailText().lines().last()

    @Test fun `a failure is noted by where and type, without its message`() {
        val result = caught("Test: reading a file") { error("/storage/emulated/0/Maya's photos/secret.json") }
        assertTrue(result.isFailure)
        assertTrue(lastLine(), lastLine().endsWith("Test: reading a file failed: IllegalStateException"))
        assertFalse(Diagnostics.trailText().contains("Maya"))
    }

    @Test fun `a success leaves no note`() {
        Diagnostics.event("Test: before")
        assertEquals(4, caught("Test: adding") { 2 + 2 }.getOrThrow())
        assertTrue(lastLine().endsWith("Test: before"))
    }

    @Test fun `the same failure again is counted on its line`() {
        Diagnostics.event("Test: start of repeats")
        repeat(3) { caught("Test: repeating") { throw IllegalArgumentException() } }
        val lines = Diagnostics.trailText().lines()
        assertTrue(lines.last(), lines.last().endsWith("Test: repeating failed: IllegalArgumentException (×3)"))
        assertTrue(lines[lines.size - 2].endsWith("Test: start of repeats"))
        // Anything else in between starts a new line.
        Diagnostics.event("Test: something else")
        caught("Test: repeating") { throw IllegalArgumentException() }
        assertTrue(lastLine().endsWith("Test: repeating failed: IllegalArgumentException"))
    }

    @Test(expected = CancellationException::class)
    fun `cancellation is rethrown, not swallowed`() {
        caught("Test: a coroutine being stopped") { throw CancellationException("stopped") }
    }

    // MarketWork's scope is never cancelled, so there a cancellation is a failure the user must still hear about.
    @Test fun `a caller that owns an uncancelled scope can treat cancellation as a failure`() {
        val result = caught("Test: market work", rethrowCancellation = false) { throw CancellationException("inside the work") }
        assertTrue(result.isFailure)
        assertTrue(lastLine(), lastLine().endsWith("Test: market work failed: CancellationException"))
    }
}
