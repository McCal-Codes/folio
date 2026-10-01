package com.mccal.folio

import android.app.Application
import android.view.accessibility.AccessibilityManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * How long the island shows a notice or a message. Its times were fixed, so someone who had asked Android for more
 * time to read (Accessibility › Time to take action) still had three and a half seconds (A11Y-19).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IslandTimeoutTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val notice = IslandEvent.Notice("Calendar is unavailable.")
    private val message = IslandEvent.Message("key", "com.example.chat", "Chat", "Sam", "On my way", null, null, canReply = true)

    @Test fun `the island keeps its own times when nobody asked for longer`() {
        assertEquals(IslandEvents.NOTICE_SHOW_MS, IslandEvents.showMs(context, notice))
        assertEquals(IslandEvents.MESSAGE_SHOW_MS, IslandEvents.showMs(context, message))
        assertEquals(IslandEvents.SHOW_MS, IslandEvents.showMs(context, IslandEvent.Charging(80)))
    }

    @Test fun `someone who asked Android for more time gets it`() {
        val a11y = shadowOf(context.getSystemService(AccessibilityManager::class.java))
        a11y.setNonInteractiveUiTimeout(20_000)
        a11y.setInteractiveUiTimeout(30_000)
        assertEquals(20_000L, IslandEvents.showMs(context, notice))
        // A message can be opened or answered, so it gets the time for things that need acting on.
        assertEquals(30_000L, IslandEvents.showMs(context, message))
    }
}
