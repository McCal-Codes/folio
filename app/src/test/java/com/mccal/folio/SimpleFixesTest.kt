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

    @Test fun `Folio is never offered as an app on Simple Home`() {
        assertTrue(isFolio("com.mccal.folio", "com.mccal.folio.dev"))
        assertTrue(isFolio("com.mccal.folio.dev", "com.mccal.folio"))
        assertFalse(isFolio("com.google.android.dialer", "com.mccal.folio"))
    }
}
