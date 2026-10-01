package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Full-width Home: a bottom dock with no status Side Bar gives the page the width the Side Bar used to take. */
class FullWidthHomeTest {
    private val bottom = LayoutPreset(dockPlacement = DockPlacement.BOTTOM)

    @Test fun `on a phone the bar and the grid use the whole width once the Side Bar is gone`() {
        val withRail = homeGeometry(411f, 891f, bottom, true, statusHeight = 160f)
        val without = homeGeometry(411f, 891f, bottom, true, statusHeight = 0f, statusRail = false)
        assertTrue(withRail.horizontalDock && withRail.dockBesideRail)
        assertTrue(without.horizontalDock)
        assertFalse("nothing to sit beside", without.dockBesideRail)
        assertEquals(411f - 32f, without.gridWidth, .01f)
        assertTrue("the strip the Side Bar took is the grid's now", without.gridWidth > withRail.gridWidth + bottom.dockWidth - 1f)
        assertEquals(411f - 32f, without.dockBarRoom, .01f)
        assertTrue(without.iconSize >= withRail.iconSize)
    }

    @Test fun `with the Side Bar, or with a Side Bar dock, nothing changes`() {
        for ((w, h) in listOf(360f to 780f, 411f to 891f, 475f to 751f, 704f to 932f, 932f to 704f)) for (p in listOf(LayoutPreset(), bottom, LayoutPreset(dockPlacement = DockPlacement.SIDE))) {
            val rail = homeGeometry(w, h, p, true, statusHeight = 160f)
            assertEquals("$w x $h", rail, homeGeometry(w, h, p, true, statusHeight = 160f, statusRail = true))
        }
        val side = LayoutPreset(dockPlacement = DockPlacement.SIDE)
        assertEquals(homeGeometry(411f, 891f, side, true, statusHeight = 160f).gridWidth, homeGeometry(411f, 891f, side, true, statusHeight = 0f, statusRail = false).gridWidth, .01f)
    }

    @Test fun `the upright inner screen already used the whole width, so it does not move`() {
        val a = homeGeometry(704f, 932f, LayoutPreset(), true, statusHeight = 160f)
        val b = homeGeometry(704f, 932f, LayoutPreset(), true, statusHeight = 0f, statusRail = false)
        assertEquals(a.gridWidth, b.gridWidth, .01f)
        assertEquals(a.dockPitch, b.dockPitch, .01f)
    }
}
