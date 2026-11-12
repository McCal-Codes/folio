package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CcGalleryCardTest {
    private val w = 360f
    private val m = 24f

    @Test fun `it stands beside the panel on the right when there is room`() {
        val p = ccGalleryCardPlacement(1600f, 1000f, floatArrayOf(100f, 80f, 700f, 600f), w, 500f, m)
        assertEquals(700f + m, p.x, 0f)
        assertEquals(80f, p.y, 0f)
    }

    @Test fun `it takes the left side when the right has no room`() {
        val p = ccGalleryCardPlacement(1000f, 1000f, floatArrayOf(500f, 80f, 960f, 600f), w, 500f, m)
        assertEquals(500f - m - w, p.x, 0f)
    }

    @Test fun `with no room on either side it stands low, centered on the panel and capped`() {
        val p = ccGalleryCardPlacement(900f, 1000f, floatArrayOf(200f, 100f, 700f, 600f), w, 800f, m)
        assertEquals((200f + 700f) / 2f - w / 2f, p.x, 0f)
        assertEquals(1000f * CC_GALLERY_LOW_CARD_HEIGHT, p.maxHeight, 0f)
        assertEquals(1000f - m - 1000f * CC_GALLERY_LOW_CARD_HEIGHT, p.y, 0f)
    }

    @Test fun `it never leaves the window`() {
        for (panel in listOf(floatArrayOf(0f, 0f, 200f, 300f), floatArrayOf(700f, 900f, 900f, 1000f), floatArrayOf(300f, 0f, 600f, 1000f))) {
            val p = ccGalleryCardPlacement(900f, 1000f, panel, w, 400f, m)
            assertTrue(p.x >= m && p.x + w <= 900f - m + .5f)
            assertTrue(p.y >= m && p.y + minOf(400f, p.maxHeight) <= 1000f - m + .5f)
        }
    }
}
