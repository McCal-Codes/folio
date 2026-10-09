package com.mccal.folio

import android.view.HapticFeedbackConstants
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HingeDetentsTest {
    /** Feeds a sweep of angles 20 ms apart and returns the stops that played, by the label angle. */
    private fun sweep(angles: List<Float>, continuous: Boolean = true, tracker: HingeDetents.Tracker = HingeDetents.Tracker()): List<Int> {
        var t = 0L
        return angles.mapNotNull { a -> t += 100; tracker.feed(a, continuous, t)?.labelDegrees }
    }
    private fun range(from: Int, to: Int) = if (from <= to) (from..to).map { it.toFloat() } else (from downTo to).map { it.toFloat() }

    @Test fun `opening slowly plays each stop once, in order, and a click at flat`() {
        val played = sweep(range(10, 179))
        assertEquals(listOf(45, 90, 135, 180), played)
        assertEquals(DetentKind.CLICK, HingeDetents.STOPS.last().kind)
    }

    @Test fun `closing slowly plays them again on the way down, ending with the tick as it closes`() {
        val played = sweep(range(179, 0))
        assertEquals(listOf(180, 135, 90, 45, 0), played)
    }

    @Test fun `closed from the start does not play anything until the hinge moves`() {
        assertEquals(emptyList<Int>(), sweep(listOf(0f, 0f, 1f, 2f, 3f)))
    }

    @Test fun `holding on a stop plays it once, and jitter at its edge does not repeat it`() {
        val jitter = listOf(80f, 85f, 89f, 90f, 91f, 92f, 93f, 94f, 93f, 94f, 95f, 94f, 93f, 94f, 93f, 94f)
        assertEquals(listOf(90), sweep(jitter))
    }

    @Test fun `going past a stop and back plays it twice, once each way`() {
        assertEquals(listOf(90, 90), sweep(range(80, 100) + range(100, 80)))
    }

    @Test fun `a flick across the whole range plays one haptic, not five`() {
        val tracker = HingeDetents.Tracker()
        assertNull(tracker.feed(5f, true, 0))
        val played = tracker.feed(179f, true, 10)
        assertEquals(180, played?.labelDegrees) // the click wins over the ticks it passed
        assertNull(tracker.feed(100f, true, 20)) // 60 ms rule: nothing right after
    }

    @Test fun `two readings closer than the gap play one haptic`() {
        val tracker = HingeDetents.Tracker()
        tracker.feed(40f, true, 0)
        assertEquals(45, tracker.feed(50f, true, 1_000)?.labelDegrees)
        assertNull(tracker.feed(95f, true, 1_000 + HingeDetents.MIN_GAP_MS - 1))
    }

    @Test fun `the public sensor plays once per position it lands on`() {
        val played = sweep(listOf(0f, 0f, 90f, 90f, 90f, 180f, 180f, 90f, 0f), continuous = false)
        assertEquals(listOf(90, 180, 90, 0), played)
    }

    @Test fun `the public sensor never plays the 45 and 135 stops`() {
        val played = sweep(listOf(0f, 90f, 180f, 90f, 0f), continuous = false)
        assertTrue(45 !in played && 135 !in played)
    }

    @Test fun `switching from the public sensor to a continuous one does not play a stop by itself`() {
        val tracker = HingeDetents.Tracker()
        tracker.feed(90f, false, 0)
        assertNull(tracker.feed(91f, true, 1_000))
    }

    @Test fun `what is felt at an angle follows the stops`() {
        assertEquals(90, HingeDetents.feltAt(91f)?.labelDegrees)
        assertEquals(0, HingeDetents.feltAt(2f)?.labelDegrees)
        assertEquals(180, HingeDetents.feltAt(178f)?.labelDegrees)
        assertNull(HingeDetents.feltAt(65f))
    }

    @Test fun `strength changes the haptic, and an old phone gets a plain tick`() {
        val tick = DetentKind.TICK; val click = DetentKind.CLICK
        assertEquals(HapticFeedbackConstants.CLOCK_TICK, HingeDetents.hapticFor(tick, DetentStrength.LIGHT, 34))
        assertEquals(HapticFeedbackConstants.SEGMENT_TICK, HingeDetents.hapticFor(tick, DetentStrength.MEDIUM, 34))
        assertEquals(HapticFeedbackConstants.CLOCK_TICK, HingeDetents.hapticFor(tick, DetentStrength.MEDIUM, 31))
        assertEquals(HapticFeedbackConstants.CONFIRM, HingeDetents.hapticFor(click, DetentKind.CLICK.let { DetentStrength.MEDIUM }, 34))
        assertNotEquals(HingeDetents.hapticFor(tick, DetentStrength.LIGHT, 34), HingeDetents.hapticFor(tick, DetentStrength.FIRM, 34))
        assertNotEquals(HingeDetents.hapticFor(click, DetentStrength.LIGHT, 34), HingeDetents.hapticFor(click, DetentStrength.FIRM, 34))
    }

    @Test fun `the options default to on and medium, and keep what was written`() {
        val prefs = FoldMotionOptions.prefs(ApplicationProvider.getApplicationContext())
        prefs.edit().clear().commit()
        assertEquals(HingeDetentOptions(), HingeDetentOptions.read(prefs))
        HingeDetentOptions.write(prefs, HingeDetentOptions(false, DetentStrength.FIRM))
        assertEquals(HingeDetentOptions(false, DetentStrength.FIRM), HingeDetentOptions.read(prefs))
        prefs.edit().putInt(HingeDetentOptions.KEY_STRENGTH, 99).commit()
        assertEquals(DetentStrength.MEDIUM, HingeDetentOptions.read(prefs).strength)
    }

    @Test fun `with detents on a stop replaces the single halfway tick, and off it is the old tick`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        fun timeline(on: Boolean) = FoldTimeline(context).also { it.stayAwake = false; it.expanded = true; it.detentsOn = on }
        fun run(t: FoldTimeline): Pair<Int, Int> {
            var halfway = 0; var stops = 0
            t.onHalfway = { halfway++ }; t.onDetent = { stops++ }
            var ms = 0L
            for (a in listOf(179f, 150f, 120f, 100f, 80f, 60f, 30f, 5f)) { ms += 100; t.onAngle(a, ms * 1_000_000L, ms) }
            return halfway to stops
        }
        val (halfwayOn, stopsOn) = run(timeline(true))
        assertEquals(0, halfwayOn); assertTrue("stops played: $stopsOn", stopsOn >= 3)
        val (halfwayOff, stopsOff) = run(timeline(false))
        assertEquals(1, halfwayOff); assertEquals(0, stopsOff)
    }
}
