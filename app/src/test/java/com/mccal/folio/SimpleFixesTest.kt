package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the emulator run of the Simple Mode preview found. */
class SimpleFixesTest {
    /** A pretend font: every character is as wide as the size. */
    private val width: (String, Float) -> Int = { text, sp -> (text.length * sp).toInt() }

    @Test fun `a settings request opens Settings once, not again when Home draws anew`() {
        val seen = RequestLatch()
        assertFalse("no request yet", seen.take(0))
        assertTrue(seen.take(1))
        assertFalse("Home drawn again after Simple, same request", seen.take(1))
        assertTrue("a new request still opens it", seen.take(2))
    }

    @Test fun `words never split, they get a step smaller down to 20 sp`() {
        // "Messages" is 8 characters: 8 x 24 = 192 does not fit 180; 8 x 22 = 176 does.
        assertEquals(22f, simpleFitSize("Messages", 24f, 180, oneLineFirst = false, width), 0f)
        assertEquals("fits already", 24f, simpleFitSize("Call", 24f, 180, oneLineFirst = false, width), 0f)
        assertEquals("wrapping between words is fine", 24f, simpleFitSize("Leave Simple Mode", 24f, 200, oneLineFirst = false, width), 0f)
        assertEquals("never under 20 sp", 20f, simpleFitSize("Messagesxxxxxxxx", 24f, 100, oneLineFirst = false, width), 0f)
        assertEquals("Chinese breaks between characters", 24f, simpleFitSize("退出简易模式", 24f, 60, oneLineFirst = false, width), 0f)
    }

    @Test fun `a button takes a step smaller before it wraps`() {
        // "Move Down" is 9 characters: 9 x 22 = 198 is over 190; 9 x 20 = 180 fits on one line.
        assertEquals(20f, simpleFitSize("Move Down", 22f, 190, oneLineFirst = true, width), 0f)
        assertEquals("Move Up fits as it is", 22f, simpleFitSize("Move Up", 22f, 190, oneLineFirst = true, width), 0f)
    }

    @Test fun `Helper Settings stays open across a fold, a turn, a font size or dark mode`() {
        assertFalse("recreated for a configuration change: stays unlocked", relocksOnStop(changingConfigurations = true))
        assertTrue("Folio left the screen: locks", relocksOnStop(changingConfigurations = false))
        assertFalse("a recreated Folio counts Home presses from zero", relocksOnHomeRequest(request = 0, seen = 3))
        assertFalse("the same press seen again", relocksOnHomeRequest(request = 3, seen = 3))
        assertTrue("a new press of Home locks", relocksOnHomeRequest(request = 4, seen = 3))
        assertTrue(SimpleScreen.EDIT.unlocked); assertFalse(SimpleScreen.HOME.unlocked); assertFalse(SimpleScreen.MORE.unlocked)
        assertEquals(5 * 60_000L, SIMPLE_IDLE_RELOCK_MS)
    }

    @Test fun `Big Grid shows as the List when fewer than four tiles would fit`() {
        assertTrue("Call and More Apps only", simpleGridFallsBack(SimpleStyle.GRID, shown = 1, total = 6))
        assertTrue(simpleGridFallsBack(SimpleStyle.GRID, shown = 2, total = 6))
        assertFalse("four tiles with More Apps", simpleGridFallsBack(SimpleStyle.GRID, shown = 3, total = 6))
        assertFalse("everything fits", simpleGridFallsBack(SimpleStyle.GRID, shown = 2, total = 2))
        assertFalse(simpleGridFallsBack(SimpleStyle.LIST, shown = 1, total = 6))
    }

    @Test fun `Folio is never offered as an app on Simple Home`() {
        assertTrue(isFolio("com.mccal.folio", "com.mccal.folio.dev"))
        assertTrue(isFolio("com.mccal.folio.dev", "com.mccal.folio"))
        assertFalse(isFolio("com.google.android.dialer", "com.mccal.folio"))
    }
}
