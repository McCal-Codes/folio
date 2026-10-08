package com.mccal.folio.market

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketPrefsTest {
    @Test fun `the carousel is the default, and a choice sticks`() {
        val prefs = MarketPrefs(MemoryStore())
        assertEquals(FeaturedStyle.CAROUSEL, prefs.featuredStyle)
        assertTrue(!prefs.introductionSeen)
        prefs.featuredStyle = FeaturedStyle.CALM
        prefs.introductionSeen = true
        assertEquals(FeaturedStyle.CALM, prefs.featuredStyle)
        assertTrue(prefs.introductionSeen)
    }

    @Test fun `an unreadable value falls back to the carousel rather than failing`() {
        val store = MemoryStore()
        store.set("market:featured-style", "hologram")
        assertEquals(FeaturedStyle.CAROUSEL, MarketPrefs(store).featuredStyle)
    }

    @Test fun `the choice survives a restart`() {
        val dir = java.nio.file.Files.createTempDirectory("folio-prefs").toFile().also { it.deleteOnExit() }
        MarketPrefs(FileStore(dir)).featuredStyle = FeaturedStyle.CALM
        assertEquals(FeaturedStyle.CALM, MarketPrefs(FileStore(dir)).featuredStyle)
        assertTrue("the choice is written to a file", dir.isDirectory && dir.list()!!.isNotEmpty())
    }

    @Test fun `nothing goes online by itself until someone says so`() {
        val prefs = MarketPrefs(MemoryStore())
        assertTrue("background refresh starts off", !prefs.backgroundRefresh)
        assertTrue("so do automatic updates", !prefs.autoUpdatePackages)
        assertTrue("and update notices", !prefs.notifyUpdates)
        assertTrue("when it is turned on it waits for Wi-Fi, never spending mobile data", prefs.refreshOnWifiOnly)
        prefs.backgroundRefresh = true
        prefs.refreshOnWifiOnly = false
        assertTrue(prefs.backgroundRefresh && !prefs.refreshOnWifiOnly)
        prefs.backgroundRefresh = false
        assertTrue(!prefs.backgroundRefresh)
    }

    @Test fun `the first-use question is asked once`() {
        val store = MemoryStore()
        assertTrue(!MarketPrefs(store).updatesQuestionSeen)
        MarketPrefs(store).updatesQuestionSeen = true
        assertTrue("remembered, so it is never asked again", MarketPrefs(store).updatesQuestionSeen)
        assertTrue("and answering it turns nothing on by itself", !MarketPrefs(store).backgroundRefresh)
    }
}

class SkippedUpdatesTest {
    @Test fun `an update you undid is remembered and cleared when emptied`() {
        val prefs = MarketPrefs(MemoryStore())
        org.junit.Assert.assertTrue(prefs.skippedUpdates.isEmpty())
        prefs.skippedUpdates = setOf("a@1.1.0", "b@2.0.0")
        org.junit.Assert.assertEquals(setOf("a@1.1.0", "b@2.0.0"), prefs.skippedUpdates)
        prefs.skippedUpdates = emptySet()
        org.junit.Assert.assertTrue(prefs.skippedUpdates.isEmpty())
    }
}
