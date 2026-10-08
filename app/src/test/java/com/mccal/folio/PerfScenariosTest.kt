package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PerfScenariosTest {
    private val header = PerfHeader("0.6.9", "samsung SM-F971U", "17", "unfolded 932x704 dp", "2026-10-08 09:00", running = false)
    private fun sample(atMs: Long, cpuMs: Long) = PerfSample(atMs, cpuMs, 0, 0, 0, 0, 40, 80, 0, -300_000, 3, 0, 310, 0, 0, 0, 0, 0, 0)

    @Test fun `a frame counts only toward the scenarios going on when it is drawn`() {
        val s = PerfScenarios()
        s.record(8_000_000L, false)
        assertEquals(0L, s.histogram(PerfScenario.HOME_SWIPE).frames)
        s.begin(PerfScenario.HOME_SWIPE)
        s.record(9_000_000L, false); s.record(30_000_000L, true)
        s.end(PerfScenario.HOME_SWIPE)
        s.record(8_000_000L, false)
        assertEquals(2L, s.histogram(PerfScenario.HOME_SWIPE).frames)
        assertEquals(1L, s.histogram(PerfScenario.HOME_SWIPE).janky)
        assertEquals(0L, s.histogram(PerfScenario.FOLD).frames)
    }

    @Test fun `two things at once both count, and a stray end never leaves one stuck on`() {
        val s = PerfScenarios()
        s.begin(PerfScenario.FOLD); s.begin(PerfScenario.HOME_SWIPE)
        s.record(9_000_000L, false)
        assertEquals(1L, s.histogram(PerfScenario.FOLD).frames); assertEquals(1L, s.histogram(PerfScenario.HOME_SWIPE).frames)
        s.end(PerfScenario.FOLD); s.end(PerfScenario.FOLD); s.end(PerfScenario.FOLD)
        assertFalse(s.isActive(PerfScenario.FOLD))
        s.begin(PerfScenario.FOLD)
        assertTrue("a stray end before did not push the count below zero", s.isActive(PerfScenario.FOLD))
        s.end(PerfScenario.FOLD)
        assertFalse(s.isActive(PerfScenario.FOLD))
    }

    @Test fun `the same scenario nested stays on until every start has ended`() {
        val s = PerfScenarios()
        s.begin(PerfScenario.FOLDER); s.begin(PerfScenario.FOLDER)
        s.end(PerfScenario.FOLDER)
        assertTrue(s.isActive(PerfScenario.FOLDER))
        s.end(PerfScenario.FOLDER)
        assertFalse(s.isActive(PerfScenario.FOLDER))
    }

    @Test fun `the report lists each scenario that drew frames, with its own percentiles, and skips the rest`() {
        val all = FrameHistogram().also { it.record(8_000_000L, false) }
        val s = PerfScenarios()
        s.begin(PerfScenario.FOLD)
        repeat(98) { s.record(9_000_000L, false) }
        s.record(40_000_000L, true); s.record(60_000_000L, true)
        s.end(PerfScenario.FOLD)
        val text = PerfReport.build(header, listOf(sample(0, 0), sample(60_000, 3_000)), all, s)
        assertTrue(text, "Frames by what Folio was doing" in text)
        assertTrue(text, Regex("""Fold animation: 100 frames, janky 2 \(2(\.0)? %\)""").containsMatchIn(text))
        assertTrue("p99 shows the slow frames the average hides", Regex("""Fold animation:.*p99 (4[0-9]|5[0-9]|60)(\.\d)? ms""").containsMatchIn(text))
        assertFalse("a scenario with no frames is not listed", "Home swipe:" in text)
    }

    @Test fun `with no scenario frames the report has no scenario section, and without scenarios it is as before`() {
        val all = FrameHistogram().also { it.record(8_000_000L, false) }
        val samples = listOf(sample(0, 0), sample(60_000, 3_000))
        val without = PerfReport.build(header, samples, all)
        assertEquals(without, PerfReport.build(header, samples, all, PerfScenarios()))
        assertFalse("Frames by what Folio was doing" in without)
    }
}
