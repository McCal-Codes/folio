package com.mccal.folio

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Clear is white on a frosted tile, which vanishes on a light Home; there it draws dark instead (iOS adapts the same way). */
class ClearIconOnLightTest {
    @Test fun clearIsWhiteByDefaultAndDarkOnLight() {
        val dark = LivePalette.of(IconStyle.CLEAR, null)
        val light = LivePalette.of(IconStyle.CLEAR, null, onLight = true)
        assertEquals(Color.White, dark.calendarNumber)
        assertEquals(ClearInkOnLight, light.calendarNumber)
        assertNotEquals(dark.clockBackground, light.clockBackground)
        // The ink on a light Home must actually be dark, or the point is lost.
        assertTrue(light.calendarNumber.red < .2f && light.calendarNumber.alpha > .6f)
    }

    @Test fun otherStylesIgnoreTheLightFlag() {
        for (style in listOf(IconStyle.DEFAULT, IconStyle.DARK))
            assertEquals(style.name, LivePalette.of(style, null), LivePalette.of(style, null, onLight = true))
    }
}
