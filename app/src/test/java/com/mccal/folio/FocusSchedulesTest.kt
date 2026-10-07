package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class FocusSchedulesTest {
    // 2026-09-14 is a Monday.
    private fun at(day: Int, hour: Int, minute: Int = 0) = LocalDateTime.of(2026, 9, 14 + day, hour, minute)
    private val sleep = DEFAULT_FOCUS_MODES.first { it.id == "sleep" }.copy(schedule = FocusSchedule(22 * 60, 7 * 60, setOf(1, 2, 3, 4, 5)))
    private val work = DEFAULT_FOCUS_MODES.first { it.id == "work" }.copy(schedule = FocusSchedule(9 * 60, 17 * 60, setOf(1, 2, 3, 4, 5)))

    @Test fun `windows cross midnight and belong to the day they start`() {
        assertTrue(sleep.schedule!!.covers(at(0, 23)))      // Monday night
        assertTrue(sleep.schedule!!.covers(at(1, 6, 30)))   // Tuesday early morning, from Monday
        assertFalse(sleep.schedule!!.covers(at(0, 6, 30)))  // Monday early morning: Sunday isn't scheduled
        assertTrue(sleep.schedule!!.covers(at(5, 6, 30)))   // Saturday morning, from Friday night
        assertFalse(sleep.schedule!!.covers(at(5, 23)))     // Saturday night isn't scheduled
        assertFalse(work.schedule!!.covers(at(0, 17)))      // ends exactly at 17:00
    }

    @Test fun `boundaries turn scheduled focuses on and off but leave manual ones alone`() {
        val modes = listOf(sleep, work) + DEFAULT_FOCUS_MODES.filter { it.id != "sleep" && it.id != "work" }
        assertEquals("work", FocusSchedules.activeAt(modes, null, at(0, 9), at(0, 8, 59)))
        assertNull(FocusSchedules.activeAt(modes, "work", at(0, 17), at(0, 16, 59)))
        assertEquals("personal", FocusSchedules.activeAt(modes, "personal", at(0, 17), at(0, 16, 59)))
        // Turned on by hand outside its window (a Saturday): a boundary doesn't turn it off.
        assertEquals("work", FocusSchedules.activeAt(modes, "work", at(5, 17), at(5, 16, 59)))
        // A scheduled Focus starting takes over from one that's on.
        assertEquals("sleep", FocusSchedules.activeAt(modes, "work", at(0, 22), at(0, 21, 59)))
        assertEquals(at(0, 9), FocusSchedules.nextBoundary(modes, at(0, 8)))
        assertEquals(at(0, 17), FocusSchedules.nextBoundary(modes, at(0, 9)))
        assertNull(FocusSchedules.nextBoundary(DEFAULT_FOCUS_MODES, at(0, 9)))
    }
}

class FocusScheduleFixesTest {
    private fun at(h: Int, m: Int = 0, day: Int = 5) = java.time.LocalDateTime.of(2026, 10, day, h, m)
    private val sleep = FocusMode("sleep", "Sleep", 0, schedule = FocusSchedule(22 * 60, 7 * 60))

    @Test fun `a scheduled Focus you turned off stays off for the rest of its window`() {
        val dismissed = mapOf("sleep" to FocusSchedules.windowEnd(sleep.schedule!!, at(23)))
        // 23:30, nothing on, still inside the window: it must not switch itself back on.
        org.junit.Assert.assertNull(FocusSchedules.activeAt(listOf(sleep), null, at(23, 30), at(23, 0), dismissed))
        // Without the memory it would, which was the bug.
        org.junit.Assert.assertEquals("sleep", FocusSchedules.activeAt(listOf(sleep), null, at(23, 30), at(23, 0)))
    }

    @Test fun `the next night's window turns it on again`() {
        val dismissed = mapOf("sleep" to FocusSchedules.windowEnd(sleep.schedule!!, at(23)))
        org.junit.Assert.assertEquals("sleep", FocusSchedules.activeAt(listOf(sleep), null, at(22, 30, day = 6), at(22, 0, day = 6), dismissed))
    }

    @Test fun `a window ends at its end time, tomorrow when that has passed today`() {
        org.junit.Assert.assertEquals(at(7, 0, day = 6), FocusSchedules.windowEnd(sleep.schedule!!, at(23)))
        org.junit.Assert.assertEquals(at(7, 0, day = 5), FocusSchedules.windowEnd(sleep.schedule!!, at(3)))
    }

    @Test fun `an alarm in the repeated hour when the clocks go back is not in the past`() {
        val zone = java.time.ZoneId.of("America/New_York")
        // 1 November 2026, 01:10 on the second pass (EST): 06:10 UTC. A 01:30 boundary has two instants; the first (EDT) is 05:30 UTC, past.
        val now = java.time.Instant.parse("2026-11-01T06:10:00Z")
        val at = FocusSchedules.alarmAt(java.time.LocalDateTime.of(2026, 11, 1, 1, 30), now, zone)
        org.junit.Assert.assertEquals(java.time.Instant.parse("2026-11-01T06:30:00Z"), at)
        org.junit.Assert.assertTrue(at.isAfter(now))
    }

    @Test fun `an ordinary alarm is the time asked for`() {
        val zone = java.time.ZoneId.of("America/New_York")
        val now = java.time.Instant.parse("2026-10-05T10:00:00Z")
        org.junit.Assert.assertEquals(java.time.Instant.parse("2026-10-05T21:00:00Z"), FocusSchedules.alarmAt(java.time.LocalDateTime.of(2026, 10, 5, 17, 0), now, zone))
    }
}
