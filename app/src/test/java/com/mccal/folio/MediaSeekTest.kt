package com.mccal.folio

import android.media.session.PlaybackState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The progress line on Control Center's Now Playing tile and the Music widget: where a finger seeks to, and which players allow it. */
class MediaSeekTest {
    @Test fun `a finger points to the same share of the track as of the line`() {
        assertEquals(0L, mediaSeekTarget(0f, 200f, 240_000L))
        assertEquals(120_000L, mediaSeekTarget(100f, 200f, 240_000L))
        assertEquals(240_000L, mediaSeekTarget(200f, 200f, 240_000L))
    }

    @Test fun `a finger past either end stays inside the track`() {
        assertEquals(0L, mediaSeekTarget(-30f, 200f, 240_000L))
        assertEquals(240_000L, mediaSeekTarget(900f, 200f, 240_000L))
    }

    @Test fun `no width or no length seeks to the start`() {
        assertEquals(0L, mediaSeekTarget(50f, 0f, 240_000L))
        assertEquals(0L, mediaSeekTarget(50f, 200f, 0L))
    }

    @Test fun `only a player that offers seek is seeked`() {
        assertTrue(mediaCanSeek(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_SEEK_TO))
        assertFalse(mediaCanSeek(PlaybackState.ACTION_PLAY))
        assertFalse(mediaCanSeek(null))
    }
}
