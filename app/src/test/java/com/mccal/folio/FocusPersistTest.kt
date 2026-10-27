package com.mccal.folio

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A-13 (a Focus turned off by hand stays off after a restart) and A-17 (Home-only label without Do Not Disturb access). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FocusPersistTest {
    private val context get() = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val sleep = FocusMode("sleep", "Sleep", 0, triggers = FocusTriggerSet(charging = true, fold = FoldState.TENT))
    private val modes = listOf(sleep)
    private val plugged = FocusSignals(fold = FoldState.COVER, charging = true)

    @Test fun `the turned-off memory survives being written and read back`() {
        val map = mapOf("sleep" to TriggerSlice(FoldState.TENT, charging = true, headphones = false), "work" to TriggerSlice(null, false, true))
        assertEquals(map, FocusTriggers.decodeSuppressed(FocusTriggers.encodeSuppressed(map)))
        assertTrue(FocusTriggers.decodeSuppressed(null).isEmpty())
        assertTrue(FocusTriggers.decodeSuppressed("not json").isEmpty())
    }

    @Test fun `a Focus turned off by hand stays off after the process restarts while its trigger still holds`() {
        val on = FocusTriggers.onSignals(modes, null, FocusTriggerState(signals = plugged.copy(charging = false)), plugged)
        assertEquals("sleep", on.active)
        val off = FocusTriggers.onTurnedOffByHand(modes, on.state, on.active)
        // The process ends: only what was written down comes back, and the last signals are not among it.
        val restarted = FocusTriggerState(suppressed = FocusTriggers.decodeSuppressed(FocusTriggers.encodeSuppressed(off.suppressed)))
        val again = FocusTriggers.onSignals(modes, null, restarted, plugged)
        assertNull("the first reading after the restart does not turn it back on", again.active)
        // It is free again once its signals change and the trigger comes back.
        val unplugged = FocusTriggers.onSignals(modes, null, again.state, plugged.copy(charging = false))
        assertEquals("sleep", FocusTriggers.onSignals(modes, null, unplugged.state, plugged).active)
    }

    @Test fun `an on Focus without Do Not Disturb access says Home only`() {
        assertEquals("On", focusRowStatus(context, sleep, true, null, access = true))
        assertEquals("On (Home only)", focusRowStatus(context, sleep, true, null, access = false))
        assertTrue(focusRowStatus(context, sleep, true, FocusReason.CHARGING, access = false)!!.endsWith("(Home only)"))
        assertNull(focusRowStatus(context, sleep, false, null, access = false))
    }
}
