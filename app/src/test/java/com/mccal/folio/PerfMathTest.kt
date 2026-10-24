package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PerfMathTest {
    private fun sample(atMs: Long, cpuMs: Long = 0, battery: Int = 80, uah: Int = 0, plugged: Int = 0, charging: Long = 0,
                       frames: Long = 0, janky: Long = 0, front: Long = 0, screen: Long = 0, pss: Int = 0, folds: Int = 0,
                       temp: Int = 310, thermal: Int = -1, powerSave: Int = -1, hz: Int = 0, features: Int = 0) =
        PerfSample(atMs, cpuMs, pss, pss / 2, pss / 4, pss / 8, 40, battery, uah, -300_000, 3, plugged, temp, frames, janky, front, screen, charging, folds, thermal, powerSave, hz, features)

    @Test fun `cpu percent is process time over wall time`() {
        assertEquals(10.0, PerfMath.cpuPercent(1_000, 1_500, 10_000, 15_000), 1e-9)
        assertEquals(250.0, PerfMath.cpuPercent(0, 12_500, 0, 5_000), 1e-9) // more than one core
    }

    @Test fun `cpu percent survives a zero or backwards interval and a counter that went down`() {
        assertEquals(0.0, PerfMath.cpuPercent(0, 100, 5_000, 5_000), 0.0)
        assertEquals(0.0, PerfMath.cpuPercent(0, 100, 6_000, 5_000), 0.0)
        assertEquals(0.0, PerfMath.cpuPercent(500, 400, 0, 5_000), 0.0)
    }

    @Test fun `a frame is janky past its own deadline, or the fallback when there is none`() {
        assertTrue(PerfMath.isJank(20_000_000, 16_000_000, 8_000_000))
        assertFalse(PerfMath.isJank(12_000_000, 16_000_000, 8_000_000))
        assertTrue(PerfMath.isJank(12_000_000, 0, 8_333_333))
        assertFalse(PerfMath.isJank(8_000_000, 0, 8_333_333))
    }

    @Test fun `percentiles come from the histogram`() {
        val h = FrameHistogram()
        repeat(90) { h.record(4_000_000, false) }  // 4 ms
        repeat(9) { h.record(12_000_000, false) }  // 12 ms
        h.record(60_000_000, true)                 // 60 ms
        assertEquals(100, h.frames)
        assertEquals(1, h.janky)
        assertEquals(4.5, h.percentileMs(50.0), 1e-9)
        assertEquals(12.5, h.percentileMs(95.0), 1e-9)
        assertEquals(12.5, h.percentileMs(99.0), 1e-9)
        assertEquals(60.5, h.percentileMs(100.0), 1e-9)
    }

    @Test fun `an empty histogram reports zero and a very slow frame lands in the last bin`() {
        val h = FrameHistogram()
        assertEquals(0.0, h.percentileMs(99.0), 0.0)
        h.record(900_000_000, true)
        assertEquals((FrameHistogram.BINS + 1) * 0.5, h.percentileMs(50.0), 1e-9)
    }

    @Test fun `drain needs an hour's worth of unplugged readings`() {
        val hour = 3_600_000L
        val d = PerfMath.drain(listOf(sample(0, battery = 80, uah = 4_000_000), sample(hour, battery = 77, uah = 3_880_000)))
        assertNotNull(d)
        assertEquals(3.0, d!!.percentPerHour, 1e-9)
        assertEquals(120.0, d.mahPerHour!!, 1e-9)
    }

    @Test fun `drain is left out when charging, too short, or too few readings`() {
        val hour = 3_600_000L
        assertNull(PerfMath.drain(listOf(sample(0), sample(hour, plugged = 2))))
        assertNull(PerfMath.drain(listOf(sample(0), sample(hour, charging = 60_000))))
        assertNull(PerfMath.drain(listOf(sample(0), sample(10 * 60_000))))
        assertNull(PerfMath.drain(listOf(sample(0))))
    }

    @Test fun `drain has no mAh when the phone has no charge counter`() {
        val d = PerfMath.drain(listOf(sample(0, battery = 50), sample(2 * 3_600_000L, battery = 40)))
        assertEquals(5.0, d!!.percentPerHour, 1e-9)
        assertNull(d.mahPerHour)
    }

    @Test fun `the log stops itself after six hours and reads memory every sixth time`() {
        assertFalse(PerfMath.shouldStop(6 * 3_600_000L - 1))
        assertTrue(PerfMath.shouldStop(6 * 3_600_000L))
        assertEquals(listOf(0, 6, 12), (0..12).filter(PerfMath::readsMemory))
    }

    @Test fun `context adds up how long each state held`() {
        var t = 0L
        val c = PerfContext({ t }, frontNow = false, screenNow = true, chargingNow = false)
        t = 1_000; c.setFront(true)
        t = 4_000; c.setScreen(false)
        t = 6_000; c.setFront(false); c.setCharging(true)
        t = 10_000
        val (front, screen, charging) = c.totals()
        assertEquals(5_000, front)
        assertEquals(4_000, screen)
        assertEquals(4_000, charging)
    }

    @Test fun `a big change of smallest width is a fold, a small one is not`() {
        assertTrue(PerfContext.isFold(411, 600))
        assertTrue(PerfContext.isFold(600, 411))
        assertFalse(PerfContext.isFold(411, 411))
        assertFalse(PerfContext.isFold(0, 600))
    }

    private val header = PerfHeader("0.6.9", "samsung SM-F971U", "17", "folded 411x891 dp", "2026-10-07 09:00", running = false)

    @Test fun `the report says too little when there are under two readings`() {
        assertTrue(PerfReport.build(header, listOf(sample(0)), FrameHistogram()).contains("Not enough readings"))
    }

    @Test fun `the report has the sections a tester needs and no em dashes`() {
        val h = FrameHistogram().apply { repeat(10) { record(5_000_000, false) }; record(30_000_000, true) }
        val s = (0..40).map { i -> sample(i * 5_000L, cpuMs = i * 250L, battery = 80, frames = 11, janky = 1, front = i * 2_500L, screen = i * 5_000L, pss = 200_000 + i, folds = if (i > 20) 1 else 0) }
        val text = PerfReport.build(header, s, h)
        listOf("Folio performance report", "Duration: 3 min 20 s", "Average: 5.0 %", "Peak over one reading: 5.0 %", "Drain: not estimated, the run is shorter",
            "Total: 11, janky", "p50", "p95", "p99", "Peak PSS", "Folio in front 50 %", "fold or unfold seen 1 times", "Per minute", "batterystats").forEach {
            assertTrue("missing: $it\n$text", text.contains(it))
        }
        assertFalse(text.contains('—'))
    }

    @Test fun `per minute rows split the run at whole minutes`() {
        val s = (0..24).map { i -> sample(i * 5_000L, cpuMs = i * 500L, frames = i * 10L, janky = i.toLong(), front = i * 5_000L, screen = i * 5_000L) }
        val rows = PerfReport.minutes(s)
        assertEquals(2, rows.size) // 0 to 120 s
        assertEquals(10.0, rows[0].cpu, 1e-9)
        assertEquals(10.0, rows[0].jank, 1e-9)
        assertEquals(100.0, rows[0].front, 1e-9)
    }

    @Test fun `duration reads in minutes then hours`() {
        assertEquals("2 min 5 s", PerfReport.duration(125_000))
        assertEquals("1 h 30 min", PerfReport.duration(90 * 60_000L))
    }

    @Test fun `span gives first, last, lowest and highest, and nothing for no readings`() {
        val sp = PerfMath.span(listOf(31.0, 35.5, 30.0, 33.0))!!
        assertEquals(31.0, sp.first, 0.0); assertEquals(33.0, sp.last, 0.0)
        assertEquals(30.0, sp.min, 0.0); assertEquals(35.5, sp.max, 0.0)
        assertEquals(2.0, sp.change, 1e-9)
        assertNull(PerfMath.span(emptyList()))
    }

    @Test fun `charge used comes from the first and last counters the phone gave`() {
        val s = listOf(sample(0, uah = 0), sample(5_000, uah = 4_000_000), sample(10_000, uah = 3_990_000), sample(15_000, uah = 0))
        assertEquals(10.0, PerfMath.chargeUsedMah(s)!!, 1e-9)
        assertEquals(-5.0, PerfMath.chargeUsedMah(listOf(sample(0, uah = 3_000_000), sample(1, uah = 3_005_000)))!!, 1e-9) // charging
        assertNull(PerfMath.chargeUsedMah(listOf(sample(0), sample(5_000))))
        assertNull(PerfMath.chargeUsedMah(listOf(sample(0, uah = 3_000_000), sample(5_000))))
    }

    @Test fun `share is the percent of readings that pass, and zero for none`() {
        val s = listOf(sample(0, features = PerfFeature.SMART_INK), sample(5_000), sample(10_000, features = PerfFeature.SMART_INK), sample(15_000))
        assertEquals(50.0, PerfMath.share(s) { it.features and PerfFeature.SMART_INK != 0 }, 1e-9)
        assertEquals(0.0, PerfMath.share(s) { it.features and PerfFeature.ISLAND_LISTENER != 0 }, 0.0)
        assertEquals(0.0, PerfMath.share(emptyList()) { true }, 0.0)
    }

    @Test fun `thermal status has a word for each level and Unavailable for the phone that gives none`() {
        assertEquals("None", PerfMath.thermalName(0))
        assertEquals("Severe", PerfMath.thermalName(3))
        assertEquals("Shutdown", PerfMath.thermalName(6))
        assertEquals("Unavailable", PerfMath.thermalName(-1))
        assertEquals("Unavailable", PerfMath.thermalName(9))
    }

    @Test fun `what changed compares the first reading with the last and says Unavailable for what the phone did not report`() {
        val s = listOf(
            sample(0, cpuMs = 0, battery = 80, uah = 4_000_000, temp = 300, thermal = 0, powerSave = 0, hz = 120, pss = 200_000, features = PerfFeature.GESTURES_SERVICE),
            sample(60_000, cpuMs = 6_000, battery = 79, uah = 3_980_000, temp = 335, thermal = 1, powerSave = 1, hz = 60, pss = 220_000,
                features = PerfFeature.GESTURES_SERVICE or PerfFeature.SMART_INK))
        val text = PerfReport.whatChanged(s).joinToString("\n")
        listOf("Battery level: 80 % to 79 % (-1)", "Charge counter: 4000 mAh to 3980 mAh (-20 mAh)", "Temperature: 30.0 C to 33.5 C (+3.5 C)",
            "Thermal status: None to Light", "Battery saver: off to on", "Display refresh rate: 120 Hz to 60 Hz (-60)",
            "Folio CPU time: 6.0 s", "Smart clock ink service went on").forEach { assertTrue("$it\n$text", it in text) }
        assertTrue(text, "Folio memory (PSS): 195 MB to 214 MB" in text)
        assertFalse("Gestures service did not change", "Gestures service (also hosts panels and the island over other apps) went" in text)
        val bare = PerfReport.whatChanged(listOf(sample(0, battery = -1, temp = 0), sample(5_000, battery = -1, temp = 0))).joinToString("\n")
        listOf("Battery level: Unavailable", "Charge counter: Unavailable", "Temperature: Unavailable", "Thermal status: Unavailable", "Battery saver: Unavailable",
            "Display refresh rate: Unavailable", "Folio memory (PSS): Unavailable", "Folio services: none switched on or off").forEach { assertTrue("$it\n$bare", it in bare) }
    }

    @Test fun `phone state reports heat, thermal peak, battery saver, refresh rates, screen time and service shares`() {
        val s = listOf(
            sample(0, temp = 300, thermal = 0, powerSave = 0, hz = 120, screen = 0, features = PerfFeature.ISLAND_LISTENER),
            sample(5_000, temp = 340, thermal = 2, powerSave = 1, hz = 60, screen = 5_000, features = PerfFeature.ISLAND_LISTENER),
            sample(10_000, temp = 320, thermal = 0, powerSave = 1, hz = 60, screen = 5_000, features = 0),
            sample(15_000, temp = 310, thermal = 0, powerSave = 0, hz = 120, screen = 5_000, features = 0))
        val text = PerfReport.deviceState(s).joinToString("\n")
        listOf("lowest 30.0 C, highest 34.0 C, +1.0 C from first to last", "highest Moderate, above None for 25 % of readings", "on for 50 % of readings",
            "60 to 120 Hz", "Screen: on 0 min 5 s, off 0 min 10 s", "Dynamic Island (notification listener connected): 50 %",
            "Smart clock ink service: 0 %").forEach { assertTrue("$it\n$text", it in text) }
        val none = PerfReport.deviceState(listOf(sample(0, temp = 0), sample(5_000, temp = 0))).joinToString("\n")
        listOf("Battery temperature: Unavailable", "Thermal status: Unavailable", "Battery saver: Unavailable", "Display refresh rate: Unavailable").forEach { assertTrue("$it\n$none", it in none) }
    }

    @Test fun `the full report carries the new sections and no em dashes`() {
        val s = (0..4).map { sample(it * 5_000L, cpuMs = it * 100L, thermal = 0, powerSave = 0, hz = 120) }
        val text = PerfReport.build(header, s, FrameHistogram())
        listOf("What changed, first reading to last", "Phone state during the run", "Folio services, share of readings they were on").forEach { assertTrue("$it\n$text", it in text) }
        assertFalse(text.contains('\u2014'))
    }
}
