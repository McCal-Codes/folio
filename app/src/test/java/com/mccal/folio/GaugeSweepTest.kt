package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Test

/** The Gauge's ring: the left half fills first, from the top down, then the right half from the bottom up. */
class GaugeSweepTest {
    private val side = gaugeSweeps(1f).first  // one half's full sweep, whatever the angle is set to

    @Test fun `an empty battery fills neither half`() {
        assertEquals(0f to 0f, gaugeSweeps(0f))
    }

    @Test fun `half a charge is exactly the left half`() {
        assertEquals(side to 0f, gaugeSweeps(.5f))
    }

    @Test fun `a full charge fills both halves`() {
        assertEquals(side to side, gaugeSweeps(1f))
    }

    @Test fun `a quarter fills half of the left half`() {
        assertEquals(side / 2f, gaugeSweeps(.25f).first, .001f)
        assertEquals(0f, gaugeSweeps(.25f).second, .001f)
    }

    @Test fun `three quarters fills the left half and half of the right`() {
        assertEquals(side, gaugeSweeps(.75f).first, .001f)
        assertEquals(side / 2f, gaugeSweeps(.75f).second, .001f)
    }

    @Test fun `a level outside 0 to 1 is held at the ends, never drawn past the ring`() {
        assertEquals(0f to 0f, gaugeSweeps(-.5f))
        assertEquals(side to side, gaugeSweeps(1.4f))
    }
}

/** The reading inside the Gauge has to fit whether the battery reads 1, 10 or 100. */
class GaugeReadingTest {
    @Test fun `a full charge takes a smaller size than a shorter reading`() {
        assertEquals(gaugeReadingSize(1), gaugeReadingSize(2), .0001f)
        org.junit.Assert.assertTrue(gaugeReadingSize(3) < gaugeReadingSize(2))
    }

    @Test fun `every reading fits across the mark with room to spare`() {
        for (digits in 1..3) {
            val width = gaugeReadingWidth(digits)
            org.junit.Assert.assertTrue("$digits digits come out $width wide", width <= .92f)
        }
    }

    @Test fun `the reading never gets so small it stops being readable`() {
        for (digits in 1..3) org.junit.Assert.assertTrue(gaugeReadingSize(digits) >= .20f)
    }
}

/** The break at the top of the Gauge's ring opens for the reading and closes when there isn't one. */
class GaugeRingTest {
    @Test fun `a longer reading opens a wider break`() {
        val one = gaugeRing(1, showsReading = true)
        val three = gaugeRing(3, showsReading = true)
        org.junit.Assert.assertTrue("100 needs more room than 9", three.topGap > one.topGap)
    }

    @Test fun `no reading means no break at the top`() {
        val closed = gaugeRing(3, showsReading = false)
        assertEquals(0f, closed.topGap, .001f)
        // One continuous track then, minus only the gap the dots sit in.
        assertEquals(360f, closed.side * 2f + 66f, .001f)
    }

    @Test fun `the two arcs always meet the breaks, whatever the reading`() {
        for (digits in 1..3) {
            val ring = gaugeRing(digits, showsReading = true)
            // Left arc, top break, right arc and bottom break make up the whole circle.
            assertEquals(360f, ring.side * 2f + ring.topGap + 66f, .001f)
            // The top break is centred on 12 o'clock.
            assertEquals(270f, ring.leftStart + ring.side + ring.topGap / 2f, .001f)
        }
    }

    @Test fun `the break stays within sensible limits`() {
        for (digits in 1..3) {
            val gap = gaugeRing(digits, showsReading = true).topGap
            org.junit.Assert.assertTrue("$digits digits gave $gap degrees", gap in 60f..130f)
        }
    }
}
