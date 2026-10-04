package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestionsLayoutTest {
    @Test fun `icons never grow past a Home icon`() {
        for ((w, h) in listOf(80f to 62f, 320f to 62f, 80f to 260f, 320f to 380f, 200f to 200f)) {
            assertTrue("$w x $h", suggestionsLayout(w, h, 66f).icon <= 66f)
        }
    }

    @Test fun `a row, a column and the whole grid show what fits`() {
        assertEquals(4, suggestionsLayout(320f, 70f, 54f).columns)
        assertEquals(1, suggestionsLayout(320f, 70f, 54f).rows)
        assertEquals(1, suggestionsLayout(70f, 380f, 54f).columns)
        assertEquals(4, suggestionsLayout(70f, 380f, 54f).rows)
        val big = suggestionsLayout(320f, 380f, 54f)
        assertEquals(4 to 4, big.columns to big.rows)
    }

    @Test fun `one cell still shows one icon, smaller if it must`() {
        val tiny = suggestionsLayout(60f, 52f, 66f)
        assertEquals(1, tiny.columns)
        assertEquals(1, tiny.rows)
        assertTrue(tiny.icon >= 28f)
    }

    @Test fun `a bigger Home icon never makes more icons than fit`() {
        val a = suggestionsLayout(320f, 380f, 48f)
        val b = suggestionsLayout(320f, 380f, 72f)
        assertTrue(b.columns <= a.columns && b.rows <= a.rows)
    }

    @Test fun `Suggestions goes from one cell to the whole grid and nothing else changes`() {
        val c = builtinWidgetConstraints(SUGGESTIONS_WIDGET)!!
        assertEquals(WidgetSpan(1, 1), c.minimum)
        assertEquals(WidgetSpan(GRID_COLUMNS, 4), c.maximum)
        assertTrue(c.canResizeHorizontally && c.canResizeVertically)
        assertNull(builtinWidgetConstraints(CLOCK_WIDGET))
        assertNotNull(builtinWidgetConstraints(SUGGESTIONS_WIDGET))
    }
}
