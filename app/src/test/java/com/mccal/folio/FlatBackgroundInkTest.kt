package com.mccal.folio

import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** F03 (27 Oct sweep): with no picture chosen, Automatic ink must read against the flat surface Home draws, in both appearances. */
class FlatBackgroundInkTest {
    @Test fun `Automatic ink on the flat surface is dark in light appearance and white in dark appearance`() {
        assertTrue(homeInkFor("AUTO", flatBackgroundTone(darkAppearance = false).prefersDarkText).dark)
        assertFalse(homeInkFor("AUTO", flatBackgroundTone(darkAppearance = true).prefersDarkText).dark)
    }

    @Test fun `the ink Automatic picks has at least 4_5 to 1 contrast with the surface in both appearances`() {
        for ((dark, surface) in listOf(false to FLAT_BACKGROUND_LIGHT, true to FLAT_BACKGROUND_DARK)) {
            val ink = homeInkFor("AUTO", flatBackgroundTone(dark).prefersDarkText)
            val ratio = contrastRatio(ink.primary.luminance(), surface.luminance())
            assertTrue("dark appearance=$dark: ${ink.primary} on $surface is $ratio to 1", ratio >= 4.5f)
        }
    }

    @Test fun `hand-set ink is left alone, and an unknown tone still means white`() {
        assertEquals(true, homeInkFor("DARK", false).dark)
        assertEquals(false, homeInkFor("LIGHT", true).dark)
        assertEquals(false, homeInkFor("AUTO", WallpaperTone().prefersDarkText).dark)
    }
}
