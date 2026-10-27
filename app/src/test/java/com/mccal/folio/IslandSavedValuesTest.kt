package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * BI-7 of the 27 Oct break-it test: a saved island position, size or nudge that is NaN, infinite or absurd (a damaged or
 * hand-edited preferences file) is read as "not set", so the island sits where the camera puts it instead of being laid
 * out at a NaN offset.
 */
class IslandSavedValuesTest {
    private val garbage = listOf("NaN,NaN", "NaN,5", "5,NaN", "Infinity,5", "5,-Infinity", "1e30,1e30", "-1e30,0", "abc", "", ",", "1,2,3", "0.5", "0.5,", ",12", "0x10,5")

    @Test fun `a good position is read`() {
        assertEquals(IslandPosition(0.5f, 40f), IslandPosition.parse("0.5,40"))
        assertEquals(IslandPosition(0f, 8f), IslandPosition.parse("0.0,8.0"))
        assertEquals(IslandPosition(1f, 300f), IslandPosition.parse("1.0,300"))
    }

    @Test fun `a bad position is not set`() {
        for (raw in garbage) assertNull("'$raw' should be unset", IslandPosition.parse(raw))
        assertNull(IslandPosition.parse(null))
        // A fraction outside the window and a top far off any screen are absurd too.
        assertNull(IslandPosition.parse("7,40"))
        assertNull(IslandPosition.parse("-0.5,40"))
        assertNull(IslandPosition.parse("0.5,-3"))
        assertNull(IslandPosition.parse("0.5,90000"))
    }

    @Test fun `clamping a position never gives NaN`() {
        val clamped = IslandPosition(Float.NaN, Float.NaN).clamped(1080, 2400, 2.625f, 34f)
        assertEquals(.5f, clamped.xFraction, 0f)
        assertEquals(false, clamped.topDp.isNaN())
        val far = IslandPosition(Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY).clamped(1080, 2400, 2.625f, 34f)
        assertEquals(1f, far.xFraction, 0f)
        assertEquals(false, far.topDp.isNaN() || far.topDp.isInfinite())
    }

    @Test fun `a good nudge is read and an out of range one is not set`() {
        assertEquals(IslandNudge(4f, -6f), IslandNudge.parse("4.0,-6.0"))
        assertEquals(IslandNudge(48f, -48f), IslandNudge.parse("48,-48"))
        for (raw in garbage) assertEquals("'$raw' should be unset", IslandNudge.None, IslandNudge.parse(raw))
        assertEquals(IslandNudge.None, IslandNudge.parse(null))
        assertEquals(IslandNudge.None, IslandNudge.parse("49,0"))
    }

    @Test fun `a size is read only when it is a sensible number of dp`() {
        assertEquals(6f, IslandSize.sane(6f), 0f)
        assertEquals(0f, IslandSize.sane(0f), 0f)
        assertEquals(0f, IslandSize.sane(-4f), 0f)
        assertEquals(0f, IslandSize.sane(Float.NaN), 0f)
        assertEquals(0f, IslandSize.sane(Float.POSITIVE_INFINITY), 0f)
        assertEquals(0f, IslandSize.sane(1e30f), 0f)
    }
}
