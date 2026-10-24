package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    @Test fun `sheets and menus are scenarios of their own, named in the report`() {
        val all = FrameHistogram().also { it.record(8_000_000L, false) }
        val s = PerfScenarios()
        s.begin(PerfScenario.SHEET); s.record(12_000_000L, false); s.end(PerfScenario.SHEET)
        s.begin(PerfScenario.MENU); s.record(9_000_000L, false); s.record(9_000_000L, false); s.end(PerfScenario.MENU)
        val text = PerfReport.build(header, listOf(sample(0, 0), sample(60_000, 3_000)), all, s)
        assertTrue(text, "Page or sheet sliding in: 1 frames" in text)
        assertTrue(text, "Menu or alert opening: 2 frames" in text)
    }

    @Test fun `moving an icon is a scenario of its own, named in the report`() {
        val all = FrameHistogram().also { it.record(8_000_000L, false) }
        val s = PerfScenarios()
        s.begin(PerfScenario.ICON_MOVE); repeat(3) { s.record(10_000_000L, false) }; s.end(PerfScenario.ICON_MOVE)
        val text = PerfReport.build(header, listOf(sample(0, 0), sample(60_000, 3_000)), all, s)
        assertTrue(text, "Moving an icon: 3 frames" in text)
    }

    /** A new sheet or menu entrance that forgets its scenario would be invisible in the report; this keeps them all counted. */
    @Test fun `every sheet and menu entrance names its scenario`() {
        val root = generateSequence(java.io.File("").absoluteFile) { it.parentFile }.first { java.io.File(it, "CHANGELOG.md").exists() }
        val calls = java.io.File(root, "app/src/main/java").walkTopDown().filter { it.extension == "kt" }.flatMap { f ->
            f.readLines().withIndex().filter { (_, l) -> "rememberEntrance(" in l && !l.trim().startsWith("*") && !l.trim().startsWith("//") && !l.contains("fun rememberEntrance") &&
                !l.contains("rememberEntrance(stiffness = spring.second") }
                .map { "${f.name}:${it.index + 1} ${it.value.trim()}" }
        }.toList()
        val missing = calls.filter { "scenario =" !in it }
        assertTrue("These entrances do not say what they are: $missing", missing.isEmpty() || missing.all { it.startsWith("FolioSheet.kt") && "rememberEntrance(spring" in it })
        assertTrue("There should be sheet and menu entrances to check: $calls", calls.size >= 4)
    }

    @Test fun `a scenario counts the time it was going on and Folio's CPU time in it, nesting included`() {
        var wall = 0L; var cpu = 0L
        val s = PerfScenarios({ wall }, { cpu })
        wall = 1_000; cpu = 100; s.begin(PerfScenario.HOME_SWIPE); s.begin(PerfScenario.HOME_SWIPE)
        wall = 3_000; cpu = 700; s.end(PerfScenario.HOME_SWIPE)
        wall = 4_000; cpu = 900
        assertEquals(PerfScenarios.Usage(3_000, 800, 0, 0, 0, 0), s.usage(PerfScenario.HOME_SWIPE)) // still going on: counted so far
        s.end(PerfScenario.HOME_SWIPE)
        wall = 9_000; cpu = 5_000
        assertEquals(3_000L, s.usage(PerfScenario.HOME_SWIPE).activeMs)
        s.begin(PerfScenario.HOME_SWIPE); wall = 9_500; cpu = 5_100; s.end(PerfScenario.HOME_SWIPE); s.end(PerfScenario.HOME_SWIPE)
        assertEquals(PerfScenarios.Usage(3_500, 900, 0, 0, 0, 0), s.usage(PerfScenario.HOME_SWIPE))
        assertEquals(0L, s.usage(PerfScenario.FOLD).activeMs)
    }

    @Test fun `without clocks a scenario records no time, and battery readings are counted only for scenarios going on`() {
        val s = PerfScenarios()
        s.begin(PerfScenario.FOLD); s.end(PerfScenario.FOLD)
        assertEquals(PerfScenarios.Usage(0, 0, 0, 0, 0, 0), s.usage(PerfScenario.FOLD))
        s.noteReading(-300_000, 310) // nothing going on
        s.begin(PerfScenario.FOLDER)
        s.noteReading(-300_000, 310); s.noteReading(-500_000, 330); s.noteReading(0, 0) // the last gave nothing
        s.end(PerfScenario.FOLDER)
        assertEquals(PerfScenarios.Usage(0, 0, 2, -400_000, 2, 320), s.usage(PerfScenario.FOLDER))
        assertEquals(0, s.usage(PerfScenario.FOLD).currentReadings)
    }

    @Test fun `the scenario usage line says what it cost, or that no battery reading fell inside it`() {
        assertNull(PerfReport.usageLine(PerfScenarios.Usage(0, 0, 0, 0, 0, 0)))
        val quiet = PerfReport.usageLine(PerfScenarios.Usage(4_000, 1_000, 0, 0, 0, 0))!!
        assertTrue(quiet, "Going on for 4.0 s, Folio CPU 1.0 s (25.0 % of one core)" in quiet)
        assertTrue(quiet, "no battery reading fell inside it" in quiet)
        val read = PerfReport.usageLine(PerfScenarios.Usage(8_000, 2_000, 2, -400_000, 2, 320))!!
        assertTrue(read, "2 battery readings inside it, average current -400 mA" in read)
        assertTrue(read, "temperature 32.0 C" in read)
    }

    @Test fun `the report shows each scenario's cost under its frame line`() {
        var wall = 0L; var cpu = 0L
        val all = FrameHistogram().also { it.record(8_000_000L, false) }
        val s = PerfScenarios({ wall }, { cpu })
        s.begin(PerfScenario.HOME_SWIPE); s.record(9_000_000L, false); wall = 2_000; cpu = 500; s.end(PerfScenario.HOME_SWIPE)
        val text = PerfReport.build(header, listOf(sample(0, 0), sample(60_000, 3_000)), all, s)
        assertTrue(text, Regex("""Home swipe: 1 frames.*\n    Going on for 2.0 s, Folio CPU 0.5 s \(25.0 % of one core\)""").containsMatchIn(text))
    }
}
