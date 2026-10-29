package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/** A widget whose app was disabled and enabled again must come back without leaving Home (the Maps "Widget unavailable" tile). */
class WidgetInfoRefreshTest {
    @Test fun `a missing info is looked up again and found once the app is back`() {
        var enabled = false
        val lookup = { if (enabled) "maps traffic" else null }
        var info = refreshedWidgetInfo(null, lookup)
        assertNull(info)
        info = refreshedWidgetInfo(info, lookup)
        assertNull("still disabled", info)
        enabled = true
        assertEquals("maps traffic", refreshedWidgetInfo(info, lookup))
    }

    @Test fun `a found info is kept and the lookup is not repeated`() {
        val found = Any()
        var calls = 0
        assertSame(found, refreshedWidgetInfo(found) { calls++; Any() })
        assertEquals(0, calls)
    }

    @Test fun `a lookup that throws counts as missing`() {
        assertNull(refreshedWidgetInfo<Any>(null) { throw IllegalStateException("provider died") })
    }
}
