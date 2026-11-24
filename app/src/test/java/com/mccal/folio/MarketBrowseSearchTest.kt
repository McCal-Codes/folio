package com.mccal.folio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Browse's search: what a typed query keeps. */
class MarketBrowseSearchTest {
    private fun hit(query: String) = marketMatches("Cabinet", "com.mccal.cabinet", "McCal", "tweaks", query)

    @Test fun `a blank query keeps everything`() {
        assertTrue(hit("")); assertTrue(hit("   "))
    }

    @Test fun `a word matches the name, id, author or section in any case`() {
        assertTrue(hit("cabin")); assertTrue(hit("CABINET")); assertTrue(hit("mccal.cab")); assertTrue(hit("mccal")); assertTrue(hit("tweak"))
    }

    @Test fun `every word typed has to be found`() {
        assertTrue(hit("cabinet tweaks")); assertFalse(hit("cabinet harborline"))
    }

    @Test fun `a package with no author or section is still found by its name`() {
        assertTrue(marketMatches("Palette", "com.mccal.palette", null, null, "pal"))
        assertFalse(marketMatches("Palette", "com.mccal.palette", null, null, "duet"))
    }
}
