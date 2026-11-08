package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every tweak page dims what depends on its main switch the same way, from one rule. */
class TweakPageDimTest {
    @Test fun `dependent rows are full strength while the tweak is on`() {
        assertEquals(1f, tweakDependentAlpha(true), 0f)
    }

    @Test fun `dependent rows are dimmed but still legible while it is off`() {
        val off = tweakDependentAlpha(false)
        assertEquals(TWEAK_DIMMED_ALPHA, off, 0f)
        assertTrue(off < 1f && off >= .3f)
    }
}
