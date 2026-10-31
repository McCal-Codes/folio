package com.mccal.folio

import androidx.compose.runtime.BroadcastFrameClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The swipe up that closes a panel follows the finger: how far it goes, how it thins, and that a pull never pushes the panel below its place. */
class PanelPullTest {
    @Test fun `a drag up pulls the panel and a drag down gives the pull back, never past rest`() {
        val pull = PanelPull()
        pull.drag(-120f)
        pull.drag(-30f)
        assertEquals(-150f, pull.offset, 0f)
        pull.drag(100f)
        assertEquals(-50f, pull.offset, 0f)
        pull.drag(500f)
        assertEquals(0f, pull.offset, 0f)
    }

    @Test fun `a panel opened again starts from rest`() {
        val pull = PanelPull()
        pull.drag(-300f)
        pull.reset()
        assertEquals(0f, pull.offset, 0f)
    }

    @Test fun `a new touch ends the spring back, so it never fights the finger for the offset`() {
        val pull = PanelPull()
        val scope = CoroutineScope(Dispatchers.Unconfined + BroadcastFrameClock())
        pull.drag(-200f)
        pull.release(scope, velocityY = 0f, thresholdPx = 1000f, flickPx = Float.MAX_VALUE, goBack = { false }, close = { error("a short pull must not close") })
        assertTrue(pull.springing)
        pull.drag(-10f)
        assertFalse(pull.springing)
        assertEquals(-210f, pull.offset, 0f)
        pull.release(scope, velocityY = 0f, thresholdPx = 1000f, flickPx = Float.MAX_VALUE, goBack = { false }, close = { error("a short pull must not close") })
        assertTrue(pull.springing)
        pull.reset()
        assertFalse(pull.springing)
        assertEquals(0f, pull.offset, 0f)
    }

    @Test fun `the panel thins as it is pulled but never vanishes under the finger`() {
        assertEquals(1f, PanelPull.fade(0f, 1000f), 0f)
        assertTrue(PanelPull.fade(-450f, 1000f) in .45f..0.55f)
        assertEquals(.15f, PanelPull.fade(-5000f, 1000f), 0f)
        assertEquals(1f, PanelPull.fade(-100f, 0f), 0f)
    }
}

/** The two small pull tabs stand in for the strip only where the strip cannot be: a full-screen app, with every other condition for the strip met. */
class PullTabRuleTest {
    @Test fun `tabs show only in a full-screen app with the strip's other conditions met and their switch on`() {
        val stripOtherwise = pullStripWanted(true, true, true, false, false, false)
        assertTrue(pullTabWanted(stripOtherwise, tabsOn = true, fullScreen = true))
        assertTrue(!pullTabWanted(stripOtherwise, tabsOn = true, fullScreen = false))
        assertTrue(!pullTabWanted(stripOtherwise, tabsOn = false, fullScreen = true))
    }

    @Test fun `no tabs when the pull itself is off, Folio is in front or Safe Mode is on`() {
        assertTrue(!pullTabWanted(pullStripWanted(false, true, true, false, false, false), true, true))
        assertTrue(!pullTabWanted(pullStripWanted(true, true, true, false, true, false), true, true))
        assertTrue(!pullTabWanted(pullStripWanted(true, true, true, true, false, false), true, true))
    }
}
