package com.mccal.folio

import android.os.BatteryManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * #328: a Focus set to turn on while charging didn't. The regression list from the 10 Oct outside research
 * (docs/research-outside-experience-resolver-2026-10-10.md, section 3) and McCal's answers of the same day.
 */
class FocusChargingTest {
    private val work = FocusMode("work", "Work", 0, triggers = FocusTriggerSet(fold = FoldState.UNFOLDED))
    private val sleep = FocusMode("sleep", "Sleep", 0, triggers = FocusTriggerSet(charging = true))
    private val plain = FocusMode("dnd", "Do Not Disturb", 0)
    private val modes = listOf(plain, sleep, work)
    private val unplugged = FocusSignals(fold = FoldState.COVER, charging = false)
    private val plugged = FocusSignals(fold = FoldState.COVER, charging = true)

    @Test fun `plugged in counts as charging, also at a charge limit`() {
        // Samsung's battery protection holds at 80%: the battery reports not charging while the cable is in.
        assertTrue(isCharging(BatteryManager.BATTERY_STATUS_NOT_CHARGING, BatteryManager.BATTERY_PLUGGED_AC))
        assertTrue(isCharging(BatteryManager.BATTERY_STATUS_CHARGING, BatteryManager.BATTERY_PLUGGED_USB))
        assertTrue(isCharging(BatteryManager.BATTERY_STATUS_FULL, BatteryManager.BATTERY_PLUGGED_WIRELESS))
        assertFalse(isCharging(BatteryManager.BATTERY_STATUS_DISCHARGING, 0))
    }

    @Test fun `no Focus on, power connects, the charging Focus turns on`() {
        val r = FocusTriggers.onSignals(modes, null, FocusTriggerState(signals = unplugged), plugged)
        assertEquals("sleep", r.active)
        assertTrue(r.changed)
        assertEquals(FocusReason.CHARGING, r.state.reason)
    }

    @Test fun `the charging Focus turns off when power disconnects`() {
        val on = FocusTriggers.onSignals(modes, null, FocusTriggerState(signals = unplugged), plugged)
        val off = FocusTriggers.onSignals(modes, on.active, on.state, unplugged)
        assertNull(off.active)
        assertTrue(off.changed)
    }

    @Test fun `turned off by hand, it stays off until power changes, then follows it again`() {
        val on = FocusTriggers.onSignals(modes, null, FocusTriggerState(signals = unplugged), plugged)
        val offByHand = FocusTriggers.onTurnedOffByHand(modes, on.state, on.active)
        val unfolding = FocusTriggers.onSignals(modes, null, offByHand, plugged.copy(fold = FoldState.UNFOLDED))
        assertEquals("unfolding turns Work on, but Sleep stays off", "work", unfolding.active)
        val pulled = FocusTriggers.onSignals(modes, null, offByHand, unplugged)
        val back = FocusTriggers.onSignals(modes, pulled.active, pulled.state, plugged)
        assertEquals("plugging in again brings it back", "sleep", back.active)
    }

    @Test fun `a Focus chosen by hand stays on when power connects`() {
        val chosen = FocusTriggers.onTurnedOnByHand(FocusTriggerState(signals = unplugged))
        val r = FocusTriggers.onSignals(modes, "work", chosen, plugged)
        assertEquals("work", r.active)
        assertFalse(r.changed)
        assertNull(r.state.byTrigger)
    }

    @Test fun `a Focus its schedule turned on stays on when power connects`() {
        // A schedule turns a Focus on without a trigger, so byTrigger is empty, the same as by hand.
        val r = FocusTriggers.onSignals(modes, "dnd", FocusTriggerState(signals = unplugged), plugged)
        assertEquals("dnd", r.active)
        assertFalse(r.changed)
    }

    @Test fun `when two match, the higher one in the list wins`() {
        val both = FocusSignals(fold = FoldState.UNFOLDED, charging = true)
        assertEquals("sleep", FocusTriggers.wanted(modes, both)?.first?.id)
        assertEquals("work", FocusTriggers.wanted(listOf(plain, work, sleep), both)?.first?.id)
    }

    @Test fun `a trigger may hand over to a higher Focus it also turned on itself`() {
        val workOn = FocusTriggers.onSignals(modes, null, FocusTriggerState(signals = unplugged), unplugged.copy(fold = FoldState.UNFOLDED))
        val r = FocusTriggers.onSignals(modes, workOn.active, workOn.state, FocusSignals(fold = FoldState.UNFOLDED, charging = true))
        assertEquals("sleep", r.active)
        assertEquals(FocusReason.CHARGING, r.state.reason)
    }

    @Test fun `Folio restarting while already charging turns the charging Focus on`() {
        // A fresh start knows no earlier signals; the first reading is a change like any other.
        val r = FocusTriggers.onSignals(modes, null, FocusTriggerState(), plugged)
        assertEquals("sleep", r.active)
        assertTrue(r.changed)
    }

    @Test fun `the same reading twice changes nothing`() {
        val on = FocusTriggers.onSignals(modes, null, FocusTriggerState(signals = unplugged), plugged)
        val again = FocusTriggers.onSignals(modes, on.active, on.state, plugged)
        assertFalse(again.changed)
        assertEquals("sleep", again.active)
    }
}
