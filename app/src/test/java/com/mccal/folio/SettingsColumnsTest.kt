package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * How many columns Settings shows, on the windows Folio actually meets. The rule is about the window, never the
 * device: 700 dp for two columns, as the Market uses, and [THREE_PANES_DP] (1200) for three.
 */
class SettingsColumnsTest {
    private fun columns(w: Float, h: Float, nested: Boolean = false, onFold: Boolean = false) =
        settingsColumns(w, h, nested = nested, onFold = onFold)

    @Test fun `a phone and a cover screen show one page at a time`() {
        assertEquals(1, columns(411f, 891f))          // Pixel 9
        assertEquals(1, columns(475f, 751f))          // Fold8 cover
        assertEquals(1, columns(751f, 475f))          // Fold8 cover, rotated: too short to be regular
        assertEquals(1, columns(411f, 891f, nested = true))
    }

    @Test fun `a window too narrow for two readable columns keeps the list over the page`() {
        assertEquals(1, columns(610f, 925f))          // 7.4" rollable
        assertEquals(1, columns(600f, 960f, nested = true))
    }

    @Test fun `the list sits beside the page in either orientation, which is what iPad Settings does`() {
        assertEquals(2, columns(932f, 704f))          // Fold8 inner
        assertEquals(2, columns(852f, 883f))          // Pixel 9 Pro Fold inner, held upright
        assertEquals(2, columns(800f, 1280f))         // tablet, portrait
        assertEquals(2, columns(1280f, 800f))         // tablet, landscape
    }

    @Test fun `a page opened from a list pushes inside the page column, as iPad Settings does`() {
        assertEquals(2, columns(932f, 704f, nested = true))    // Fold8 inner: the tweak replaces Tweaks, with Back
        assertEquals(2, columns(852f, 883f, nested = true))
        assertEquals(2, columns(838f, 945f, nested = true))    // 8" fold-out
        assertEquals(2, columns(1199f, 800f, nested = true))
    }

    @Test fun `a window of 1200 dp or more keeps the list beside the page it opened`() {
        assertEquals(3, columns(1200f, 800f, nested = true))
        assertEquals(3, columns(1280f, 800f, nested = true))   // tablet, landscape
        assertEquals(3, columns(1920f, 1080f, nested = true))  // desktop
        assertEquals(2, columns(1280f, 800f))                  // a top-level page has nothing to sit beside
    }

    @Test fun `half folded, the fold keeps the divider and there is no third column`() {
        assertEquals(2, columns(932f, 704f, nested = true, onFold = true))
        assertEquals(2, columns(1280f, 800f, nested = true, onFold = true))
    }
}
