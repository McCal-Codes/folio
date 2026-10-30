package com.mccal.folio

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Crackgate rules: pure, and built so the joke can never flicker. */
class CrackgateLogicTest {
    // Shake

    @Test fun `two hard samples inside the window and the gap make a shake`() {
        val s = CrackgateShake()
        assertFalse(s.onSample(0, 3.5f))
        assertTrue(s.onSample(400, 3.5f))
    }

    @Test fun `one hard sample alone never fires`() {
        val s = CrackgateShake()
        assertFalse(s.onSample(0, 9f))
        assertFalse(s.onSample(50_000, 0.5f))
    }

    @Test fun `samples below the threshold change nothing`() {
        val s = CrackgateShake()
        assertFalse(s.onSample(0, 3.5f))
        assertFalse(s.onSample(300, SHAKE_THRESHOLD_G))
        assertFalse(s.onSample(400, 1f))
        assertTrue(s.onSample(500, 3.5f))
    }

    @Test fun `a second sample too close to the first is the same jolt`() {
        val s = CrackgateShake()
        assertFalse(s.onSample(0, 3.5f))
        assertFalse(s.onSample(SHAKE_GAP_MIN_MS - 1, 3.5f))
        assertTrue(s.onSample(SHAKE_GAP_MIN_MS, 3.5f))
    }

    @Test fun `a slow second sample does not count and starts a new try`() {
        val s = CrackgateShake()
        assertFalse(s.onSample(0, 3.5f))
        assertFalse(s.onSample(SHAKE_WINDOW_MS + 1, 3.5f))
        assertTrue(s.onSample(SHAKE_WINDOW_MS + 1 + SHAKE_GAP_MIN_MS, 3.5f))
    }

    @Test fun `the window edge still counts`() {
        val s = CrackgateShake()
        assertFalse(s.onSample(0, 3.5f))
        assertTrue(s.onSample(SHAKE_WINDOW_MS, 3.5f))
    }

    @Test fun `a shake resets so the next one needs two more samples`() {
        val s = CrackgateShake()
        s.onSample(0, 3.5f)
        assertTrue(s.onSample(400, 3.5f))
        assertFalse(s.onSample(800, 3.5f))
        assertTrue(s.onSample(1200, 3.5f))
    }

    @Test fun `reset forgets a shake in progress`() {
        val s = CrackgateShake()
        s.onSample(0, 3.5f)
        s.reset()
        assertFalse(s.onSample(400, 3.5f))
    }

    @Test fun `a clock that goes backwards does not fire`() {
        val s = CrackgateShake()
        assertFalse(s.onSample(10_000, 3.5f))
        assertFalse(s.onSample(100, 3.5f))
    }

    // Kinds and looks

    @Test fun `the weights add up to 100 and are 60 15 15 10`() {
        assertEquals(100, CRACKGATE_WEIGHTS.values.sum())
        assertEquals(listOf(60, 15, 15, 10), CRACKGATE_WEIGHTS.values.toList())
        assertEquals(CrackgateKind.entries.toSet(), CRACKGATE_WEIGHTS.keys)
    }

    @Test fun `every roll from 0 to 99 maps to a kind with the right counts`() {
        val counts = (0..99).groupingBy { crackgateKind(it) }.eachCount()
        assertEquals(60, counts[CrackgateKind.BAR_CYAN])
        assertEquals(15, counts[CrackgateKind.BAR_CYAN_LEFT])
        assertEquals(15, counts[CrackgateKind.LINE_GREEN])
        assertEquals(10, counts[CrackgateKind.HAIRLINE_ONLY])
    }

    @Test fun `a roll outside 0 to 99 is clamped`() {
        assertEquals(crackgateKind(0), crackgateKind(-50))
        assertEquals(crackgateKind(99), crackgateKind(1000))
    }

    @Test fun `no kind is red`() {
        assertEquals(CrackgateKind.entries.toSet(), CRACKGATE_COLOR_FAMILY.keys)
        CRACKGATE_COLOR_FAMILY.forEach { (kind, color) -> assertNotEquals("$kind must not be red", CrackgateColor.RED, color) }
    }

    @Test fun `looks stay in range over 2000 seeded randoms`() {
        val random = Random(42)
        repeat(2000) {
            val look = randomCrackgateLook(random)
            assertTrue(look.barTopPercent in 42f..68f)
            assertTrue(look.barEndGapPercent in 0f..8f)
            assertTrue(look.tiltDegrees >= -3.5f && look.tiltDegrees <= 0.5f)
            assertEquals(look.barTopPercent + 7f, look.brightFromPercent, 0f)
            if (look.kind == CrackgateKind.LINE_GREEN) {
                assertEquals(LINE_GREEN_HEIGHT_PERCENT, look.barHeightPercent, 0f)
            } else {
                assertTrue(look.barHeightPercent in 5f..8f)
            }
        }
    }

    @Test fun `the same seed gives the same look`() {
        assertEquals(randomCrackgateLook(Random(7)), randomCrackgateLook(Random(7)))
    }

    // Glitch plan

    @Test fun `the plan is empty for reduce motion and for not glitchy`() {
        repeat(50) { seed ->
            assertTrue(planCrackgateGlitches(Random(seed), reduceMotion = true, glitchy = true).isEmpty())
            assertTrue(planCrackgateGlitches(Random(seed), reduceMotion = false, glitchy = false).isEmpty())
            assertTrue(planCrackgateGlitches(Random(seed), reduceMotion = true, glitchy = false).isEmpty())
        }
    }

    @Test fun `over 500 seeded plans the steps are few slow and never repeat`() {
        repeat(500) { seed ->
            val plan = planCrackgateGlitches(Random(seed), reduceMotion = false, glitchy = true)
            assertTrue("1 to $MAX_GLITCH_STEPS steps", plan.size in 1..MAX_GLITCH_STEPS)
            plan.forEach { assertTrue("delay ${it.delayMs}", it.delayMs in STEP_DELAY_MIN_MS..STEP_DELAY_MAX_MS) }
            plan.zipWithNext().forEach { (a, b) -> assertNotEquals(a.step, b.step) }
        }
    }

    @Test fun `the plans use every step and every length across seeds`() {
        val plans = (0 until 500).map { planCrackgateGlitches(Random(it), reduceMotion = false, glitchy = true) }
        assertEquals((1..MAX_GLITCH_STEPS).toSet(), plans.map { it.size }.toSet())
        assertEquals(CrackgateStep.entries.toSet(), plans.flatten().map { it.step }.toSet())
    }

    @Test fun `the shortest possible wait is not under the minimum gap`() {
        assertTrue(STEP_DELAY_MIN_MS >= MIN_STEP_GAP_MS)
        assertTrue(STEP_DELAY_MAX_MS >= STEP_DELAY_MIN_MS)
    }

    @Test fun `changes per second are far under the flash limit`() {
        assertTrue(crackgateChangesPerSecondAtMost() < 0.5)
        assertTrue(crackgateChangesPerSecondAtMost() < 3.0)
    }

    // Auto clear

    @Test fun `the fault clears itself after ten minutes`() {
        assertFalse(crackgateShouldClear(1000, 1000))
        assertFalse(crackgateShouldClear(1000, 1000 + AUTO_CLEAR_MS - 1))
        assertTrue(crackgateShouldClear(1000, 1000 + AUTO_CLEAR_MS))
    }

    @Test fun `a clock that went backwards clears it`() {
        assertTrue(crackgateShouldClear(5000, 4999))
    }

    // Surprise

    private fun surprise(
        enabled: Boolean = true,
        surpriseOn: Boolean = true,
        onHome: Boolean = true,
        recording: Boolean = false,
        onCall: Boolean = false,
        last: Long? = null,
        today: Long = 20_000,
        roll: Int = 0,
        oneInN: Int = 1,
    ) = crackgateMaySurprise(enabled, surpriseOn, onHome, recording, onCall, last, today, roll, oneInN)

    @Test fun `a surprise is allowed when everything lines up`() {
        assertTrue(surprise())
        assertTrue(surprise(last = 19_999))
    }

    @Test fun `each condition on its own stops a surprise`() {
        assertFalse(surprise(enabled = false))
        assertFalse(surprise(surpriseOn = false))
        assertFalse(surprise(onHome = false))
        assertFalse(surprise(recording = true))
        assertFalse(surprise(onCall = true))
        assertFalse(surprise(last = 20_000))
        assertFalse(surprise(last = 20_000L - SURPRISE_MIN_GAP_DAYS + 1))
        assertFalse(surprise(roll = 1, oneInN = 5))
    }

    @Test fun `the dice pass only on a multiple of N and N below 1 counts as 1`() {
        assertTrue(surprise(roll = 10, oneInN = 5))
        assertFalse(surprise(roll = 11, oneInN = 5))
        assertTrue(surprise(roll = 7, oneInN = 0))
        assertTrue(surprise(roll = 7, oneInN = -3))
    }

    // Hold to start

    @Test fun `only a long press starts it`() {
        assertFalse(crackgateHoldStarts(0))
        assertFalse(crackgateHoldStarts(120))
        assertFalse(crackgateHoldStarts(HOLD_TO_START_MS - 1))
        assertTrue(crackgateHoldStarts(HOLD_TO_START_MS))
        assertTrue(crackgateHoldStarts(HOLD_TO_START_MS + 500))
    }
}
