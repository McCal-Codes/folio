package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetFlipTest {
    @Test fun `the front turns to edge on and the back arrives from the same side`() {
        assertEquals(0f, WidgetFlip.rotationY(0f), 0f)
        assertEquals(90f, WidgetFlip.rotationY(.4999f), .1f)
        assertEquals(-90f, WidgetFlip.rotationY(.5f), 0f)
        assertEquals(0f, WidgetFlip.rotationY(1f), 0f)
    }

    @Test fun `the turn never goes past either face`() {
        assertEquals(0f, WidgetFlip.rotationY(-.2f), 0f)
        // A spring that overshoots must not tip the back past flat.
        assertEquals(0f, WidgetFlip.rotationY(1.04f), 0f)
    }

    @Test fun `the back shows only after the halfway point`() {
        assertFalse(WidgetFlip.showsBack(0f))
        assertFalse(WidgetFlip.showsBack(.49f))
        assertTrue(WidgetFlip.showsBack(.5f))
        assertTrue(WidgetFlip.showsBack(1.1f))
    }

    @Test fun `reduce motion cross fades with no turn`() {
        assertEquals(1f, WidgetFlip.fade(0f), 0f)
        assertEquals(.5f, WidgetFlip.fade(.5f), 0f)
        assertEquals(0f, WidgetFlip.fade(1f), 0f)
        assertEquals(0f, WidgetFlip.fade(2f), 0f)
    }

    @Test fun `the camera sits three widget widths away in the unit the layer reads`() {
        // 72 px per inch: a 720 px wide widget puts the camera 2160 px, 30 inches, away.
        assertEquals(30f, WidgetFlip.cameraDistance(720f), .001f)
    }
}
