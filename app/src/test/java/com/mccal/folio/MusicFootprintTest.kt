package com.mccal.folio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicFootprintTest {
    private val music = builtinWidgetConstraints(MUSIC_WIDGET)!!

    @Test fun aNarrowSlotIsTooSmallForTheMusicWidget() {
        // A 1 x 4 Suggestions widget replaced by Music used to keep its footprint, which Music cannot draw in.
        assertFalse(music.accepts(WidgetSpan(1, 4)))
        assertFalse(music.accepts(WidgetSpan(1, 1)))
        // Two columns of a 360 dp phone are about 165 dp: the Strip's art, title and controls need more than that.
        assertFalse(music.accepts(WidgetSpan(2, 1)))
    }

    @Test fun footprintsInsideTheLimitsAreKept() {
        assertTrue(music.accepts(WidgetSpan(3, 1)))
        assertTrue(music.accepts(WidgetSpan(4, 2)))
        assertTrue(music.accepts(music.preferred))
        assertTrue(music.accepts(music.maximum))
    }

    @Test fun tooBigIsRefusedToo() = assertFalse(music.accepts(WidgetSpan(GRID_COLUMNS + 1, 4)))
}
