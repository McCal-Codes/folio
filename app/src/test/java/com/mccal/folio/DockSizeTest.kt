package com.mccal.folio

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Dock › Size: 100% is exactly the dock as it was, and bigger or smaller always fits the window it is in. */
class DockSizeTest {
    private fun geometry(width: Float, height: Float, size: Float, slots: Int = 4, placement: DockPlacement = DockPlacement.AUTOMATIC) =
        homeGeometry(width, height, LayoutPreset(dockSize = size, dockWidth = dockWidthForSize(size), dockPlacement = placement), true, statusHeight = 160f, dockSlots = slots)

    @Test fun `a hundred percent is the dock as it was`() {
        assertEquals(68f, dockWidthForSize(1f), 0f)
        for ((w, h) in listOf(360f to 780f, 412f to 915f, 704f to 932f, 800f to 1280f)) {
            assertEquals("$w x $h", homeGeometry(w, h, LayoutPreset(), true, statusHeight = 160f), geometry(w, h, 1f))
        }
    }

    @Test fun `smaller shrinks the dock's apps and larger grows them`() {
        val small = geometry(412f, 915f, .8f).dockIcon
        val normal = geometry(412f, 915f, 1f).dockIcon
        val large = geometry(412f, 915f, 1.4f).dockIcon
        assertTrue("$small < $normal", small < normal)
        assertTrue("$normal < $large", normal < large)
    }

    @Test fun `a larger dock never has apps bigger than its room`() {
        for ((w, h) in listOf(360f to 640f, 360f to 780f, 412f to 915f, 704f to 932f, 800f to 1280f)) {
            for (slots in 4..8) for (placement in DockPlacement.entries) {
                val g = geometry(w, h, MAX_DOCK_SIZE, slots, placement)
                val room = if (g.horizontalDock) minOf(g.dockBarHeight - 22f, (g.dockBarRoom - 32f - 16f) / slots - 22f) else minOf(dockWidthForSize(MAX_DOCK_SIZE) - 12f, g.dockRowHeight - 4f)
                val base = geometry(w, h, 1f, slots, placement).dockIcon
                assertTrue("$w x $h $slots $placement: ${g.dockIcon} within max(room $room, base $base)", g.dockIcon <= maxOf(room, base) + .01f)
            }
        }
    }

    @Test fun `a taller dock leaves room for fewer places, never under six`() {
        assertTrue(dockCapacity(1000f, 66f, 1.6f) < dockCapacity(1000f, 66f, 1f))
        assertEquals(6, dockCapacity(700f, 66f, 1.6f))
    }

    @Test fun `the size is kept in range and saved with the layout`() {
        assertEquals(MAX_DOCK_SIZE, LayoutPreset(dockSize = 9f).sanitized().dockSize, 0f)
        assertEquals(MIN_DOCK_SIZE, LayoutPreset(dockSize = 0f).sanitized().dockSize, 0f)
        assertEquals(1f, LayoutPreset(dockSize = Float.NaN).sanitized().dockSize, 0f)
        assertEquals(MAX_DOCK_WIDTH, LayoutPreset(dockWidth = 500f).sanitized().dockWidth, 0f)
        assertTrue("the widest dock for the biggest size is within the limit", dockWidthForSize(MAX_DOCK_SIZE) in 56f..MAX_DOCK_WIDTH)
    }
}

class DockCenteredTest {
    @Test fun `a centered dock has the same space above it as below it, on any screen`() {
        for ((w, h) in listOf(320f to 568f, 360f to 740f, 412f to 915f, 412f to 1100f, 800f to 1280f)) for (slots in 4..8) {
            val p = LayoutPreset(dockCentered = true, dockPlacement = DockPlacement.SIDE)
            val g = homeGeometry(w, h, p, true, statusHeight = 0f, dockSlots = slots)
            val above = g.dockTop - 8f
            val below = h - railControlsReserve(1, g.iconSize) - (g.dockTop + g.dockHeight)
            assertTrue("$w x $h $slots: above $above, below $below", kotlin.math.abs(above - below) <= 1f || g.dockHeight >= h - railControlsReserve(1, g.iconSize) - 8f - .5f)
        }
    }

    @Test fun `centering is off unless asked for, and is saved with the layout`() {
        assertEquals(false, LayoutPreset().dockCentered)
        assertEquals(homeGeometry(412f, 915f, LayoutPreset(), true, statusHeight = 160f),
            homeGeometry(412f, 915f, LayoutPreset(dockCentered = false), true, statusHeight = 160f))
    }
}

class DockShownWholeTest {
    @Test fun `the side dock shows only the places that fit whole, on every window`() {
        for ((w, h) in listOf(320f to 568f, 360f to 640f, 360f to 740f, 412f to 915f, 800f to 1280f)) for (slots in 4..8) for (size in listOf(.8f, 1f, 1.6f)) {
            val p = LayoutPreset(dockSize = size, dockWidth = dockWidthForSize(size), dockPlacement = DockPlacement.SIDE)
            val g = homeGeometry(w, h, p, true, statusHeight = 160f, dockSlots = slots)
            val shown = g.dockShownSlots
            assertTrue("$w x $h $slots: shows $shown", shown in MIN_DOCK_SLOTS..maxOf(slots, MIN_DOCK_SLOTS))
            assertTrue("$w x $h $slots size $size: $shown places need ${shown * g.dockRowHeight + 16f}, dock is ${g.dockHeight}", shown * g.dockRowHeight + 16f <= g.dockHeight + .5f || shown == MIN_DOCK_SLOTS)
            assertTrue("$w x $h: the dock ends inside the window", g.dockTop + g.dockHeight <= h + .5f)
        }
    }

    @Test fun `a window with room shows every place, and a short one shows fewer`() {
        val side = LayoutPreset(dockPlacement = DockPlacement.SIDE)
        assertEquals(8, homeGeometry(412f, 1100f, side, true, statusHeight = 160f, dockSlots = 8).dockShownSlots)
        assertTrue(homeGeometry(320f, 568f, side, true, statusHeight = 160f, dockSlots = 8).dockShownSlots < 8)
    }

    @Test fun `a side bar too short for four places ends on a whole place`() {
        val side = LayoutPreset(dockPlacement = DockPlacement.SIDE)
        for (h in listOf(300f, 320f, 360f, 400f)) {
            val g = homeGeometry(780f, h, side, true, statusHeight = 160f)
            val places = (g.dockHeight - 16f) / 48f
            assertEquals("$h: ${g.dockHeight}", Math.round(places).toFloat(), places, .01f)
        }
    }
}

/** Fit more dock apps is opt in: the default dock never goes below 48 dp a place, the compact one may go to 40 dp and shrinks its icons. */
class DockCompactTest {
    private fun geometry(compact: Boolean, height: Float = 640f) =
        homeGeometry(412f, height, LayoutPreset(dockCompact = compact, dockPlacement = DockPlacement.SIDE), true, statusHeight = 160f)

    @Test fun `it is off by default`() {
        assertEquals(false, LayoutPreset().dockCompact)
    }

    @Test fun `off, no place is shorter than 48 dp`() {
        for (h in listOf(560f, 640f, 740f, 915f)) assertTrue("$h", geometry(false, h).dockRowHeight >= STANDARD_DOCK_PITCH)
    }

    @Test fun `on, no place is shorter than 40 dp and none shows fewer than off`() {
        for (h in listOf(560f, 640f, 740f, 915f)) {
            val on = geometry(true, h); val off = geometry(false, h)
            assertTrue("$h", on.dockRowHeight >= COMPACT_DOCK_PITCH)
            assertTrue("$h", on.dockShownSlots >= off.dockShownSlots)
        }
    }

    @Test fun `on, a place never crowds its icon`() {
        for (h in listOf(560f, 640f, 740f)) {
            val g = geometry(true, h)
            assertTrue("$h ${g.dockIcon} ${g.dockRowHeight}", g.dockIcon <= g.dockRowHeight - 4f + 0.01f || g.dockIcon <= 24f)
        }
    }

    @Test fun `a tall screen looks the same on or off`() {
        assertEquals(geometry(false, 915f).dockShownSlots, geometry(true, 915f).dockShownSlots)
    }
}
