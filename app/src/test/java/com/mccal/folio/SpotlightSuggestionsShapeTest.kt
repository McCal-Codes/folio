package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * How Spotlight draws its suggestions while nothing is typed, by the room above the keyboard. A flip's cover screen (748x720 at
 * 320 dpi is 360 dp tall) leaves about 112 dp above a keyboard, and the suggestions card used to be drawn there with its heading
 * and the top of a card, the rest under the keys.
 */
class SpotlightSuggestionsShapeTest {
    @Test
    fun `a flip cover screen with a keyboard shows the field alone`() {
        assertEquals(SuggestionsShape.NONE, suggestionsShape(112f))
    }

    @Test
    fun `room for the field and one row shows a single row of icons`() {
        assertEquals(SuggestionsShape.STRIP, suggestionsShape(SPOTLIGHT_STRIP_ABOVE_KEYBOARD_DP))
        assertEquals(SuggestionsShape.STRIP, suggestionsShape(200f))
        assertEquals(SuggestionsShape.STRIP, suggestionsShape(319f))
    }

    @Test
    fun `just under the strip's room still shows nothing, not half a row`() {
        assertEquals(SuggestionsShape.NONE, suggestionsShape(SPOTLIGHT_STRIP_ABOVE_KEYBOARD_DP - 1f))
    }

    @Test
    fun `a regular phone keeps the whole card`() {
        assertEquals(SuggestionsShape.FULL, suggestionsShape(320f))
        assertEquals(SuggestionsShape.FULL, suggestionsShape(480f))
        assertEquals(SuggestionsShape.FULL, suggestionsShape(900f))
    }
}
