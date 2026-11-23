package com.mccal.folio

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A hinge sensor that reads 0 and 180 in turn while the phone stays open (seen on a plugged-in Fold, 21 Nov 2026, with the device
 * state OPENED throughout) must not keep dimming Home and launching the fold bridge.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FalseFoldGuardTest {
    @Test fun `each false alarm in a row mutes for longer, up to a cap`() {
        val g = FalseFoldGuard(firstMuteMs = 10_000, maxMuteMs = 40_000, forgetAfterMs = 1_000_000)
        assertFalse(g.muted(0))
        g.falseAlarm(0)
        assertTrue(g.muted(9_999)); assertFalse(g.muted(10_000))
        g.falseAlarm(10_000)
        assertTrue(g.muted(29_999)); assertFalse(g.muted(30_000))
        g.falseAlarm(30_000)
        assertTrue("capped at 40 s", g.muted(69_999)); assertFalse(g.muted(70_000))
    }

    @Test fun `a real display switch clears the record`() {
        val g = FalseFoldGuard(firstMuteMs = 10_000, maxMuteMs = 40_000, forgetAfterMs = 1_000_000)
        g.falseAlarm(0); g.falseAlarm(10_000)
        g.realFold()
        assertFalse(g.muted(10_001))
        g.falseAlarm(20_000)
        assertFalse("back to the shortest mute", g.muted(30_000))
    }

    @Test fun `a long quiet spell is forgiven`() {
        val g = FalseFoldGuard(firstMuteMs = 10_000, maxMuteMs = 300_000, forgetAfterMs = 60_000)
        g.falseAlarm(0); g.falseAlarm(10_000)
        g.falseAlarm(500_000)
        assertFalse("the first mute again, not the third", g.muted(510_000))
    }

    private fun timeline() = FoldTimeline(ApplicationProvider.getApplicationContext()).also {
        it.stayAwake = false
        it.expanded = true
    }

    /** Plays readings and samples the effect every 10 ms until [until]. */
    private fun FoldTimeline.play(readings: List<Pair<Long, Float>>, from: Long = 0, until: Long) {
        var next = 0
        for (t in from..until step 10L) {
            while (next < readings.size && readings[next].first <= t) { onAngle(readings[next].second, readings[next].first * 1_000_000L, readings[next].first); next++ }
            targetM(t)
        }
    }

    // A pulse of 180 each time, then 0 again, with nothing else changing: what the sensor log showed.
    private val glitch = listOf(0L to 180f, 200L to 0f, 3_000L to 180f, 3_400L to 0f, 6_000L to 180f, 6_400L to 0f)

    @Test fun `the first false fold still plays but the repeats are ignored`() {
        val fold = timeline()
        fold.play(glitch.take(2), until = 300)
        assertTrue("the first closed reading starts a fold", fold.closing)
        fold.play(emptyList(), from = 310, until = 2_900)
        assertFalse("it gives up while the screen is still the inner one", fold.closing)
        fold.play(glitch.drop(2), from = 2_910, until = 8_000)
        assertFalse("the next closed readings are muted", fold.closing)
        assertEquals("and nothing dims Home", 0f, fold.targetM(8_000), 1e-4f)
    }

    @Test fun `folds work again once the mute runs out`() {
        val fold = timeline()
        fold.play(glitch.take(2), until = 2_900)
        fold.play(listOf(12_000L to 180f, 12_200L to 0f), from = 2_910, until = 12_300)
        assertTrue("the mute is over, so a fold is a fold again", fold.closing)
    }

    @Test fun `a real fold switches the display and so is never muted`() {
        val fold = timeline()
        fold.play(glitch.take(2), until = 2_900)
        // The cover takes over, then the phone is opened again and folded again soon after.
        fold.expanded = false; fold.onDisplaySwitched(3_000)
        fold.expanded = true; fold.onDisplaySwitched(3_500)
        fold.play(listOf(4_000L to 180f, 4_200L to 0f), from = 3_510, until = 4_300)
        assertTrue("a switch clears the mute", fold.closing)
    }

    @Test fun `holding the phone half open is not a false alarm`() {
        val fold = timeline()
        fold.play(listOf(0L to 180f, 200L to 90f), until = 3_000)
        assertFalse("flex mode lets go", fold.closing)
        fold.play(listOf(3_500L to 180f, 3_700L to 0f), from = 3_010, until = 3_800)
        assertTrue("and the next fold is not muted", fold.closing)
    }
}
