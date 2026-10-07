package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PerfMathTest {
    private fun sample(atMs: Long, cpuMs: Long = 0, battery: Int = 80, uah: Int = 0, plugged: Int = 0, charging: Long = 0,
                       frames: Long = 0, janky: Long = 0, front: Long = 0, screen: Long = 0, pss: Int = 0, folds: Int = 0) =
        PerfSample(atMs, cpuMs, pss, pss / 2, pss / 4, pss / 8, 40, battery, uah, -300_000, 3, plugged, 310, frames, janky, front, screen, charging, folds)

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
}
