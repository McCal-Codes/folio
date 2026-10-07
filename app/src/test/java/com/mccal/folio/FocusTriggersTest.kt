package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusTriggersTest {
    private val work = FocusMode("work", "Work", 0, triggers = FocusTriggerSet(fold = FoldState.UNFOLDED))
    private val sleep = FocusMode("sleep", "Sleep", 0, triggers = FocusTriggerSet(charging = true))
    private val music = FocusMode("personal", "Personal", 0, triggers = FocusTriggerSet(headphones = true))
    private val plain = FocusMode("dnd", "Do Not Disturb", 0)
    private val modes = listOf(plain, work, sleep, music)
    private val unfolded = FocusSignals(fold = FoldState.UNFOLDED)
    private val cover = FocusSignals(fold = FoldState.COVER)

    @Test fun `a trigger set with nothing chosen turns nothing on`() {
        assertFalse(FocusTriggerSet().any)
        assertNull(FocusTriggers.wanted(listOf(plain), unfolded))
    }

    @Test fun `unfolding turns the Focus on and says why`() {
        val r = FocusTriggers.onSignals(modes, null, FocusTriggerState(signals = cover), unfolded)
        assertEquals("work", r.active)
        assertTrue(r.changed)
        assertEquals("work", r.state.byTrigger)
        assertEquals(FocusReason.FOLD, r.state.reason)
    }

    @Test fun `folding back turns off only the Focus the trigger turned on`() {
        val on = FocusTriggers.onSignals(modes, null, FocusTriggerState(signals = cover), unfolded)
        val off = FocusTriggers.onSignals(modes, on.active, on.state, cover)
        assertNull(off.active)
        assertTrue(off.changed)
        assertNull(off.state.byTrigger)
    }

    @Test fun `a Focus you turned on yourself is left on when the trigger ends`() {
        val state = FocusTriggers.onTurnedOnByHand(FocusTriggerState(signals = unfolded))
        // Work is on by hand while unfolded, then the phone is folded: it stays.
        val r = FocusTriggers.onSignals(modes, "work", state, cover)
        assertEquals("work", r.active)
        assertFalse(r.changed)
    }

    @Test fun `turning it off by hand sticks until the next change`() {
        val on = FocusTriggers.onSignals(modes, null, FocusTriggerState(signals = cover), unfolded)
        val off = FocusTriggers.onTurnedOffByHand(modes, on.state, "work")
        assertEquals(setOf("work"), off.suppressed.keys)
        // Same signals again (no change): nothing happens.
        assertFalse(FocusTriggers.onSignals(modes, null, off, unfolded).changed)
        // Folded then unfolded: a change clears the memory, so it turns on again.
        val folded = FocusTriggers.onSignals(modes, null, off, cover)
        assertTrue(folded.state.suppressed.isEmpty())
        assertEquals("work", FocusTriggers.onSignals(modes, null, folded.state, unfolded).active)
    }

    @Test fun `an unrelated change does not bring back a Focus you turned off`() {
        val on = FocusTriggers.onSignals(modes, null, FocusTriggerState(signals = cover), unfolded)
        val off = FocusTriggers.onTurnedOffByHand(modes, on.state, "work")
        // Still unfolded, but now charging: Work's trigger reads only the fold, which has not changed. Sleep (charging) does turn on.
        val charging = FocusTriggers.onSignals(modes, null, off, unfolded.copy(charging = true))
        assertEquals("sleep", charging.active)
        assertEquals(setOf("work"), charging.state.suppressed.keys)
        // Folding changes what Work reads, so the memory goes and it can come back.
        val folded = FocusTriggers.onSignals(modes, "sleep", charging.state, cover.copy(charging = true))
        assertTrue(folded.state.suppressed.isEmpty())
    }

    @Test fun `turning off a Focus whose trigger does not hold remembers nothing`() {
        val state = FocusTriggerState(signals = cover)
        assertTrue(FocusTriggers.onTurnedOffByHand(modes, state, "work").suppressed.isEmpty())
    }

    @Test fun `charging and headphones are independent triggers`() {
        assertEquals(FocusReason.CHARGING, FocusTriggers.reason(sleep.triggers, FocusSignals(charging = true)))
        assertEquals(FocusReason.HEADPHONES, FocusTriggers.reason(music.triggers, FocusSignals(headphones = true)))
        assertNull(FocusTriggers.reason(sleep.triggers, FocusSignals(headphones = true)))
    }

    @Test fun `when two Focuses want to be on the first in the list wins`() {
        val both = FocusSignals(fold = FoldState.UNFOLDED, charging = true)
        assertEquals("work", FocusTriggers.wanted(modes, both)?.first?.id)
        assertEquals("sleep", FocusTriggers.wanted(modes, both, suppressed = setOf("work"))?.first?.id)
    }

    @Test fun `an unknown fold state never matches a fold trigger`() {
        assertNull(FocusTriggers.reason(work.triggers, FocusSignals(fold = null)))
    }

    @Test fun `triggers are saved and read back, and old saves read as none`() {
        val saved = DEFAULT_FOCUS_MODES.map { if (it.id == "work") it.copy(triggers = FocusTriggerSet(FoldState.TENT, true, true)) else it }
        val back = focusModesFromJson(focusModesToJson(saved))
        assertEquals(FocusTriggerSet(FoldState.TENT, true, true), back.first { it.id == "work" }.triggers)
        assertEquals(FocusTriggerSet(), back.first { it.id == "sleep" }.triggers)
        // A save from before triggers existed has no "triggers" key.
        val old = org.json.JSONArray().put(org.json.JSONObject().put("id", "work").put("name", "Work").put("color", 0).put("silence", true))
        assertEquals(FocusTriggerSet(), focusModesFromJson(old).first { it.id == "work" }.triggers)
    }

    @Test fun `fold state is read from the window and only for a phone that folds`() {
        assertEquals(FoldState.UNFOLDED, foldStateOf(hasFold = true, halfOpened = false, foldable = true))
        assertEquals(FoldState.TENT, foldStateOf(hasFold = true, halfOpened = true, foldable = true))
        assertEquals(FoldState.COVER, foldStateOf(hasFold = false, halfOpened = false, foldable = true))
        assertNull(foldStateOf(hasFold = false, halfOpened = false, foldable = false))
    }

    @Test fun `headphones are headsets and speakers, not the phone's own speaker`() {
        assertTrue(isHeadphoneType(android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP))
        assertTrue(isHeadphoneType(android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET))
        assertFalse(isHeadphoneType(android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER))
    }
}
