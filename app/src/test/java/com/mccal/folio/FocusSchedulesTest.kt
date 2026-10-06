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
