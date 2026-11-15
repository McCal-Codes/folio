package com.mccal.folio

import android.media.AudioManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CcValuesTest {
    @Test fun `the timeout steps up through the stops and wraps to the shortest`() {
        assertEquals(30_000, ccNextTimeout(15_000))
        assertEquals(600_000, ccNextTimeout(300_000))
        assertEquals(15_000, ccNextTimeout(600_000))
        // a timeout that is not a stop (set elsewhere) goes to the next stop above it, and a huge or unknown one starts over
        assertEquals(60_000, ccNextTimeout(45_000))
        assertEquals(15_000, ccNextTimeout(1_800_000))
        assertEquals(15_000, ccNextTimeout(0))
        assertEquals(15_000, ccNextTimeout(-1))
    }

    @Test fun `every stop comes round again after as many taps as there are stops`() {
        var t = CC_TIMEOUTS.first()
        repeat(CC_TIMEOUTS.size) { t = ccNextTimeout(t) }
        assertEquals(CC_TIMEOUTS.first(), t)
        assertTrue(CC_TIMEOUTS == CC_TIMEOUTS.sorted())
    }

    @Test fun `a timeout reads as seconds under a minute and minutes from one`() {
        assertEquals(15 to false, ccTimeoutLabel(15_000))
        assertEquals(30 to false, ccTimeoutLabel(30_000))
        assertEquals(1 to true, ccTimeoutLabel(60_000))
        assertEquals(10 to true, ccTimeoutLabel(600_000))
        assertEquals(0 to false, ccTimeoutLabel(0))
        assertEquals(0 to false, ccTimeoutLabel(-5))
        // "Never" is the largest Int on Android, and any hour or more reads the same
        assertEquals(-1 to false, ccTimeoutLabel(Int.MAX_VALUE))
        assertEquals(-1 to false, ccTimeoutLabel(3_600_000))
        assertEquals(59 to true, ccTimeoutLabel(3_599_000))
    }

    @Test fun `the ringer goes ring, vibrate, silent and round`() {
        assertEquals(AudioManager.RINGER_MODE_VIBRATE, ccNextRinger(AudioManager.RINGER_MODE_NORMAL, canSilent = true))
        assertEquals(AudioManager.RINGER_MODE_SILENT, ccNextRinger(AudioManager.RINGER_MODE_VIBRATE, canSilent = true))
        assertEquals(AudioManager.RINGER_MODE_NORMAL, ccNextRinger(AudioManager.RINGER_MODE_SILENT, canSilent = true))
    }

    @Test fun `without Do Not Disturb access the ringer skips silent`() {
        assertEquals(AudioManager.RINGER_MODE_VIBRATE, ccNextRinger(AudioManager.RINGER_MODE_NORMAL, canSilent = false))
        assertEquals(AudioManager.RINGER_MODE_NORMAL, ccNextRinger(AudioManager.RINGER_MODE_VIBRATE, canSilent = false))
        // a phone already silent (set elsewhere) can always be brought back
        assertEquals(AudioManager.RINGER_MODE_NORMAL, ccNextRinger(AudioManager.RINGER_MODE_SILENT, canSilent = false))
    }
}
