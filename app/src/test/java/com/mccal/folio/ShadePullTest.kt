package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The pull down from the top edge of other apps: which panel, what counts as a pull, how thick the strip is, and when it exists. */
class ShadePullTest {
    @Test fun `the left half opens Notification Center and the right half Control Center`() {
        assertEquals(ShadePanel.NOTIFICATIONS, pullPanel(0f, 1080f))
        assertEquals(ShadePanel.NOTIFICATIONS, pullPanel(539.9f, 1080f))
        assertEquals(ShadePanel.QUICK_SETTINGS, pullPanel(540f, 1080f))
        assertEquals(ShadePanel.QUICK_SETTINGS, pullPanel(1079f, 1080f))
    }

    @Test fun `a pull is far enough down and more down than across by half`() {
        val slop = 8f
        assertTrue(isPullDown(0f, 13f, slop))
        assertTrue("a diagonal from the corner counts", isPullDown(30f, 40f, slop))
        assertFalse("not far enough", isPullDown(0f, 12f, slop))
        assertFalse("along the edge", isPullDown(80f, 30f, slop))
        assertFalse("upward", isPullDown(0f, -60f, slop))
        assertTrue("a quick swipe whose first event after the touch down is already 69 px down (the numbers from an emulator log, slop 21)", isPullDown(0f, 69.2f, 21f))
    }

    @Test fun `the strip is as tall as the status bar but between 24 and 48 dp`() {
        assertEquals(72, pullStripHeight(0, 3f))
        assertEquals(96, pullStripHeight(96, 3f))
        assertEquals(144, pullStripHeight(400, 3f))
        assertEquals(24, pullStripHeight(10, 1f))
    }

    @Test fun `the strip exists only when both switches are on and nothing else owns the top edge`() {
        assertTrue(pullStripWanted(true, true, true, false, false, false))
        assertFalse("pull off", pullStripWanted(false, true, true, false, false, false))
        assertFalse("panels over apps off", pullStripWanted(true, false, true, false, false, false))
        assertFalse("Folio's panels off", pullStripWanted(true, true, false, false, false, false))
        assertFalse("Safe Mode", pullStripWanted(true, true, true, true, false, false))
        assertFalse("Home is in front", pullStripWanted(true, true, true, false, true, false))
        assertFalse("a full screen app", pullStripWanted(true, true, true, false, false, true))
    }
}
