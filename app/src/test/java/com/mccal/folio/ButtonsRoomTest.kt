package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What Folio tells a keyboard about its Big Buttons. The answer decides whether someone's keyboard covers them, so
 * it is worth being exact about when it is zero.
 */
class ButtonsRoomTest {

    @Test fun `off means no room`() {
        assertEquals(0f, buttonsRoomDp("""{"buttonBar":false,"buttonBarHeight":52}""", 0f))
    }

    @Test fun `on and at the bottom asks for its height`() {
        assertEquals(52f, buttonsRoomDp("""{"buttonBar":true,"buttonBarHeight":52}""", 0f))
        assertEquals(60f, buttonsRoomDp("""{"buttonBar":true,"buttonBarHeight":60}""", 0f))
    }

    @Test fun `a height outside what Folio allows is brought back inside it`() {
        assertEquals(60f, buttonsRoomDp("""{"buttonBar":true,"buttonBarHeight":900}""", 0f))
        assertEquals(44f, buttonsRoomDp("""{"buttonBar":true,"buttonBarHeight":2}""", 0f))
    }

    @Test fun `lifted out of the way asks for nothing`() {
        // Dragging the buttons up is how someone moves them off a keyboard; reserving room would undo that.
        assertEquals(0f, buttonsRoomDp("""{"buttonBar":true,"buttonBarHeight":52}""", 120f))
    }

    @Test fun `a nudge is not a lift`() {
        assertEquals(52f, buttonsRoomDp("""{"buttonBar":true,"buttonBarHeight":52}""", 4f))
    }

    @Test fun `nonsense settings ask for nothing rather than crashing a keyboard`() {
        assertEquals(0f, buttonsRoomDp("not json", 0f))
        assertEquals(0f, buttonsRoomDp("{}", 0f))
    }
}
