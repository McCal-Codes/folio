package com.mccal.folio

import android.media.session.PlaybackState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What counts as playing for the island. A track that stops to load is still a track you're listening to: treating
 * only STATE_PLAYING as live made the media card disappear and come back on every skip over a slow connection.
 */
class PlaybackLiveTest {
    @Test fun `playing, buffering and connecting are all live`() {
        assertTrue(IslandListenerService.playbackIsLive(PlaybackState.STATE_PLAYING))
        assertTrue(IslandListenerService.playbackIsLive(PlaybackState.STATE_BUFFERING))
        assertTrue(IslandListenerService.playbackIsLive(PlaybackState.STATE_CONNECTING))
    }

    @Test fun `a pause, a stop or no state at all is not live`() {
        assertFalse(IslandListenerService.playbackIsLive(PlaybackState.STATE_PAUSED))
        assertFalse(IslandListenerService.playbackIsLive(PlaybackState.STATE_STOPPED))
        assertFalse(IslandListenerService.playbackIsLive(PlaybackState.STATE_NONE))
        assertFalse(IslandListenerService.playbackIsLive(PlaybackState.STATE_ERROR))
        assertFalse(IslandListenerService.playbackIsLive(null))
    }
}
