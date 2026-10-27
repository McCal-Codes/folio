package com.mccal.folio

import android.media.session.MediaSession
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * When notification access goes (or the service ends), nothing another app showed may stay in Folio: not the island
 * activity, not the Music widget's track (added in #467), not the notification list.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IslandListenerClearTest {
    private fun fill() {
        val session = MediaSession(ApplicationProvider.getApplicationContext(), "test")
        val media = IslandActivity.Media("com.example.music", "Song", "Artist", null, true, session.sessionToken)
        IslandListenerService.mediaMutable.value = media
        IslandListenerService.mutable.value = media
        IslandListenerService.notificationsMutable.value = listOf(
            NotificationItem("key", "com.example.chat", "Chat", null, "Someone", "Hello", 0L, true, null),
        )
    }

    private fun assertAllEmpty() {
        assertNull(IslandListenerService.mediaMutable.value)
        assertNull(IslandListenerService.mutable.value)
        assertTrue(IslandListenerService.notificationsMutable.value.isEmpty())
    }

    @Test fun `clearPublished empties the island, the Music widget track and the notifications`() {
        fill()
        IslandListenerService.clearPublished()
        assertAllEmpty()
    }

    @Test fun `losing notification access empties all three`() {
        fill()
        val service = Robolectric.buildService(IslandListenerService::class.java).create().get()
        service.onListenerDisconnected()
        assertAllEmpty()
        assertEquals(false, IslandListenerService.connected.value)
    }

    @Test fun `destroying the service empties all three`() {
        fill()
        val service = Robolectric.buildService(IslandListenerService::class.java).create().get()
        service.onDestroy()
        assertAllEmpty()
    }
}
