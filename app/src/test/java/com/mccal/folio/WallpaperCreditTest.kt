package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Test

class WallpaperCreditTest {
    @Test fun `the credit names the artist and the license`() {
        assertEquals("Utagawa Hiroshige, Public domain", wallpaperCredit("Utagawa Hiroshige", "Public domain"))
    }

    @Test fun `the preview decodes the largest fraction that still covers the screen`() {
        assertEquals(1, previewSampleSize(2448, 3264, 1080, 2400))
        assertEquals(2, previewSampleSize(4000, 5000, 1080, 2400))
        assertEquals(1, previewSampleSize(800, 600, 1080, 2400))
        assertEquals(1, previewSampleSize(0, 0, 1080, 2400))
    }

    @Test fun `a blank part is left out`() {
        assertEquals("Public domain", wallpaperCredit("  ", "Public domain"))
        assertEquals("Utagawa Hiroshige", wallpaperCredit("Utagawa Hiroshige", ""))
        assertEquals("", wallpaperCredit("", ""))
    }
}
