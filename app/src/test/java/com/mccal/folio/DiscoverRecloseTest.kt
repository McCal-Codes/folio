package com.mccal.folio

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Home pressed from live Discover: Home settles on page 1 with the feed shut, and Android recreates Discover's host. The
 * new Google session could come back open. Folio ignored that report, so Google's window stayed over Home, the frame
 * painted Home on top of it as a tinted block, and the Spotlight pull landed on Google's window until a second Home.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DiscoverRecloseTest {
    private var reclosed = 0
    private val original = LiveDiscover.reclose

    @Before fun settleHomeWithTheFeedOpen() {
        LiveDiscover.reclose = { reclosed++ }
        LiveDiscover.allowNativeOpen = true
        LiveDiscover.nativePosition = 1f // Google last said the feed was open: Home was pressed from Discover.
        LiveDiscover.page(0f, scrolling = false) // Home settles on page 1, waiting for Google to confirm it shut.
    }

    @After fun restore() {
        LiveDiscover.reclose = original
        LiveDiscover.native(0f)
        LiveDiscover.nativePosition = 0f
    }

    @Test fun `a feed that comes back open over a settled Home is closed again`() {
        assertFalse(LiveDiscover.native(1f))
        assertEquals(1, reclosed)
    }

    @Test fun `a feed that is closing on its own is left to finish`() {
        LiveDiscover.native(.6f)
        LiveDiscover.native(.2f)
        assertEquals(0, reclosed)
        LiveDiscover.native(0f)
        assertEquals("the shut feed is confirmed and nothing more is asked", 0, reclosed)
    }

    @Test fun `once Google confirms the feed is shut, nothing is closed again`() {
        LiveDiscover.native(0f)
        assertEquals(0f, LiveDiscover.nativePosition)
        assertEquals(0, reclosed)
    }

    @Test fun `only a shut, settled Home with an open or reopening feed asks for a close`() {
        assertTrue(discoverShouldReclose(endpoint = 0f, previous = 1f, reported = 1f))
        assertTrue(discoverShouldReclose(endpoint = 0f, previous = .2f, reported = .5f))
        assertFalse("closing on its own", discoverShouldReclose(endpoint = 0f, previous = .5f, reported = .2f))
        assertFalse("already shut", discoverShouldReclose(endpoint = 0f, previous = 1f, reported = 0f))
        assertFalse("Home settled on Discover itself", discoverShouldReclose(endpoint = 1f, previous = 0f, reported = .5f))
    }
}
