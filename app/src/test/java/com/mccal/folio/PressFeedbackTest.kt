package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PressFeedbackTest {
    @Test fun `an icon is full size and fully opaque at rest`() {
        assertEquals(1f, PressFeedback.scale(pressed = false, lifted = false, reduceMotion = false), 0f)
        assertEquals(1f, PressFeedback.alpha(pressed = false, lifted = false), 0f)
        assertEquals(0f, PressFeedback.shadowDp(lifted = false, reduceMotion = false), 0f)
    }

    @Test fun `a finger dips and dims it a little`() {
        assertEquals(PressFeedback.DIP, PressFeedback.scale(true, false, false), 0f)
        assertEquals(PressFeedback.DIP_ALPHA, PressFeedback.alpha(true, false), 0f)
        assertTrue(PressFeedback.DIP in .85f..0.95f && PressFeedback.DIP_ALPHA in .85f..1f)
    }

    @Test fun `an open menu lifts the icon over everything else, with a shadow and no dim`() {
        assertEquals(PressFeedback.LIFT, PressFeedback.scale(pressed = true, lifted = true, reduceMotion = false), 0f)
        assertEquals(1f, PressFeedback.alpha(pressed = true, lifted = true), 0f)
        assertTrue(PressFeedback.shadowDp(true, false) > 0f)
        assertTrue(PressFeedback.LIFT > 1f && PressFeedback.LIFT <= 1.1f)
    }

    @Test fun `Reduce Motion keeps the dim and drops the scale and the shadow`() {
        assertEquals(1f, PressFeedback.scale(true, false, true), 0f)
        assertEquals(1f, PressFeedback.scale(true, true, true), 0f)
        assertEquals(PressFeedback.DIP_ALPHA, PressFeedback.alpha(true, false), 0f)
        assertEquals(0f, PressFeedback.shadowDp(true, true), 0f)
    }

    @Test fun `the hold after a tap is short, so a failed launch never leaves an icon dipped`() {
        assertTrue(PressFeedback.HOLD_MS in 300L..1_000L)
    }

    @Test fun `with the motion pass off every icon keeps exactly the feel it had before`() {
        // App: dip to .88 and dim to .82. Dock: dip to .92. Folder: nothing. No lift, no shadow, any kind.
        assertEquals(.88f, PressFeedback.scale(true, false, false, PressFeedback.Kind.APP, v2 = false), 0f)
        assertEquals(.82f, PressFeedback.alpha(true, false, PressFeedback.Kind.APP, v2 = false), 0f)
        assertEquals(.92f, PressFeedback.scale(true, false, false, PressFeedback.Kind.DOCK, v2 = false), 0f)
        assertEquals(1f, PressFeedback.alpha(true, false, PressFeedback.Kind.DOCK, v2 = false), 0f)
        assertEquals(1f, PressFeedback.scale(true, false, false, PressFeedback.Kind.FOLDER, v2 = false), 0f)
        assertEquals(1f, PressFeedback.alpha(true, false, PressFeedback.Kind.FOLDER, v2 = false), 0f)
        PressFeedback.Kind.entries.forEach { k ->
            assertEquals("no lift without the pass", 1f, PressFeedback.scale(false, true, false, k, v2 = false), 0f)
            assertEquals(0f, PressFeedback.shadowDp(true, false, v2 = false), 0f)
            assertEquals("at rest", 1f, PressFeedback.scale(false, false, false, k, v2 = false), 0f)
        }
    }

    @Test fun `the old springs are kept with the pass off and the new one follows the finger`() {
        assertEquals(.55f to 1500f, PressFeedback.springFor(PressFeedback.Kind.APP, v2 = false))
        assertEquals(FolioMotion.Snap, PressFeedback.springFor(PressFeedback.Kind.DOCK, v2 = false))
        PressFeedback.Kind.entries.forEach { assertEquals(FolioMotion.Quick, PressFeedback.springFor(it, v2 = true)) }
    }
}
