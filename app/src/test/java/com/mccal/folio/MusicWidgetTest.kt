package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicWidgetTest {
    @Test fun shortWidgetsAreTheStrip() {
        assertEquals(MusicShape.STRIP, musicShape(320f, 80f))
        assertEquals(MusicShape.STRIP, musicShape(320f, 109.9f))
        // Tall and wide does not rescue a short box: height decides first.
        assertEquals(MusicShape.STRIP, musicShape(400f, 100f))
    }

    @Test fun middleSizesAreTheCardNotAnEmptyPoster() {
        assertEquals(MusicShape.CARD, musicShape(320f, 110f))
        assertEquals(MusicShape.CARD, musicShape(320f, 179f))
        // Tall but narrow is a Card too: the Poster needs width for its art.
        assertEquals(MusicShape.CARD, musicShape(150f, 300f))
    }

    @Test fun roomyWidgetsAreThePoster() {
        assertEquals(MusicShape.POSTER, musicShape(160f, 180f))
        assertEquals(MusicShape.POSTER, musicShape(340f, 340f))
    }

    @Test fun timesRunMinutesAndSeconds() {
        assertEquals("0:00", formatMediaTime(0))
        assertEquals("1:05", formatMediaTime(65_000))
        assertEquals("12:09", formatMediaTime(729_000))
        // A position a hair past the end, or before the start, never shows a minus sign or a negative.
        assertEquals("0:00", formatMediaTime(-4_000))
    }

    @Test fun theWidgetCanBeResizedFromStripToPoster() {
        val limits = builtinWidgetConstraints(MUSIC_WIDGET)!!
        assertEquals(1, limits.minimum.height)
        assertTrue("minimum is short enough for the Strip", musicShape(limits.minimum.width * 80f, limits.minimum.height * 80f) == MusicShape.STRIP)
        assertEquals(4, limits.maximum.height)
        assertTrue(limits.canResizeHorizontally && limits.canResizeVertically)
    }
}
