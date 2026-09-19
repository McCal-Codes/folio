package com.mccal.folio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A panel is drawn for the screen it opened on, so Folio closes it when the screen changes underneath it. This is the
 * rule that decides what counts as a change (see MainActivity.onConfigurationChanged).
 */
class WindowShapeTest {
    private val cover = 475 to 751
    private val inner = 932 to 648

    @Test fun `unfolding and folding count`() {
        assertTrue(windowChangedShape(cover, inner))
        assertTrue(windowChangedShape(inner, cover))
    }

    @Test fun `turning the phone counts`() {
        assertTrue(windowChangedShape(cover, cover.second to cover.first))
    }

    @Test fun `a resize into split screen counts`() {
        assertTrue(windowChangedShape(inner, 932 to 320))
    }

    @Test fun `system bars coming and going do not`() {
        assertFalse(windowChangedShape(cover, 475 to 745))
        assertFalse(windowChangedShape(cover, 471 to 751))
        assertFalse(windowChangedShape(cover, cover))
    }

    @Test fun `the first configuration after launch closes nothing`() {
        assertFalse(windowChangedShape(null, cover))
    }
}
