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

    // Two columns of a 360 dp phone leave about 165 dp: art, a title and two 48 dp buttons need 230.
    @Test fun aNarrowStripKeepsItsTitleAndPlayButton() {
        assertEquals(MusicStripPlan(art = true, play = true, next = true), musicStripPlan(246f))
        assertEquals(MusicStripPlan(art = true, play = true, next = true), musicStripPlan(230f))
        assertEquals(MusicStripPlan(art = true, play = true, next = false), musicStripPlan(229f))
        assertEquals(MusicStripPlan(art = false, play = true, next = false), musicStripPlan(165f))
        // Whatever the width, play and pause stay, and what is drawn always leaves the title at least its least width.
        for (w in 100..400) {
            val plan = musicStripPlan(w.toFloat())
            assertTrue(plan.play)
            val used = 20f + 48f + (if (plan.art) 50f else 0f) + (if (plan.next) 48f else 0f)
            if (w >= 132) assertTrue("title at $w dp", w - used >= 64f)
        }
    }

    @Test fun theMinimumFootprintFitsAPhonesWidth() {
        // The narrowest Home grid column a phone has is about 80 dp; the minimum span must leave the Strip its full row.
        val minimum = builtinWidgetConstraints(MUSIC_WIDGET)!!.minimum
        assertTrue(minimum.width * 80f + (minimum.width - 1) * 4f >= 230f)
    }

    private fun linear(scale: Float): (Float) -> Float = { sp -> sp * scale }

    @Test fun textRowsGrowWithTheFontScale() {
        assertTrue(musicLineDp(FolioType.SUBHEAD, linear(2f)) > 1.9f * musicLineDp(FolioType.SUBHEAD, linear(1f)))
        assertEquals(FolioType.SUBHEAD * MUSIC_LINE, musicLineDp(FolioType.SUBHEAD, linear(1f)), 0.001f)
    }

    @Test fun aLargeFontTakesItsRoomFromTheArtAndThenTheExtrasNotTheTitleOrControls() {
        // A 150 dp Card: at the normal font the art is its 32 dp floor or more with everything shown.
        val normal = musicCardPlan(150f, 64f, FolioType.SUBHEAD, FolioType.FOOTNOTE, true, true, 8f, 48f, linear(1f))
        assertTrue(normal.subtitle && normal.scrubber)
        assertTrue(normal.artDp >= MUSIC_ART_MIN_DP)
        // At 200 percent the same height cannot hold the extras and the art: the scrubber goes first, then the artist, and the art keeps its floor.
        val large = musicCardPlan(150f, 64f, FolioType.SUBHEAD, FolioType.FOOTNOTE, true, true, 8f, 48f, linear(2f))
        assertTrue("the scrubber is dropped", !large.scrubber)
        assertTrue(large.artDp >= MUSIC_ART_MIN_DP)
        // One step of room less than that keeps the artist: only the scrubber goes.
        val some = musicCardPlan(190f, 64f, FolioType.SUBHEAD, FolioType.FOOTNOTE, true, true, 8f, 48f, linear(2f))
        assertTrue(some.subtitle && !some.scrubber)
        // Whatever is shown fits: title, shown extras, controls, gaps and the art add up to no more than the height, unless even the title and controls overflow.
        val used = large.artDp + musicLineDp(FolioType.SUBHEAD, linear(2f)) + 48f + 8f +
            (if (large.subtitle) musicLineDp(FolioType.FOOTNOTE, linear(2f)) else 0f) + (if (large.scrubber) musicScrubberDp(linear(2f)) else 0f)
        assertTrue("used $used of 150", used <= 150f + 0.01f)
    }

    @Test fun theArtNeverGoesOutsideItsLimits() {
        for (h in listOf(80f, 120f, 200f, 400f)) for (scale in listOf(1f, 1.3f, 2f)) {
            val plan = musicCardPlan(h, 64f, FolioType.SUBHEAD, FolioType.FOOTNOTE, true, true, 8f, 48f, linear(scale))
            assertTrue("$h at $scale: ${plan.artDp}", plan.artDp in MUSIC_ART_MIN_DP..64f)
        }
    }
}
