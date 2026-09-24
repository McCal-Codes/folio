package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Folio has to lay out on any Android window, not just the Galaxy Z Fold it's developed on. */
class ScreenMatrixTest {
    private data class Screen(val name: String, val width: Float, val height: Float)

    /**
     * Real screen sizes in dp, not guesses, shared with the Mockup Lab: app/src/test/resources/screen-matrix.json.
     * Most come from the device definitions Android Studio ships in the SDK (pixels × 160 / density); the Galaxy Z
     * Fold8 screens were measured with adb (2448×1848 and 1248×1972 px at 420 dpi). Split-screen entries are half of a
     * listed screen.
     */
    private val devices = org.json.JSONObject(javaClass.getResource("/screen-matrix.json")!!.readText()).getJSONArray("devices").let { list ->
        (0 until list.length()).map { i -> list.getJSONObject(i).let { Screen(it.getString("name"), it.getDouble("width").toFloat(), it.getDouble("height").toFloat()) } }
    }

    @Test fun `the shared device list is complete`() {
        assertEquals(25, devices.size)
        assertTrue(devices.any { it.name == "Galaxy Z Fold8 inner" && it.width == 932f && it.height == 704f })
        // Estimated from Samsung's specs at 420 dpi; the file says so beside each one.
        assertTrue(devices.any { it.name == "Galaxy Z TriFold main (estimated)" && it.width == 823f && it.height == 603f })
    }

    /** Each device in both orientations, plus split-screen halves of the bigger ones. */
    private val screens = devices.flatMap { d ->
        listOf(d, Screen("${d.name} rotated", d.height, d.width)) +
            (if (maxOf(d.width, d.height) >= 800f) listOf(Screen("${d.name} split half", maxOf(d.width, d.height) / 2f, minOf(d.width, d.height))) else emptyList())
    }

    @Test fun `phones and foldables draw at the system density`() {
        for (s in devices.filter { maxOf(it.width, it.height) < 1000f }) {
            assertEquals(s.name, 1f, uiScale(s.width, s.height))
        }
    }

    @Test fun `big screens scale up but never past the cap`() {
        val tablet = uiScale(1280f, 800f)
        assertTrue(tablet > 1.05f)
        assertEquals(tablet, uiScale(800f, 1280f))
        assertEquals(1.45f, uiScale(1920f, 1080f))
        for (s in screens) {
            val scale = uiScale(s.width, s.height)
            assertTrue(s.name, scale in 1f..1.45f)
            // Scaling never turns a regular window compact: the layout family matches the real window.
            assertEquals(s.name, fitsRegularHomeLayout(s.width, s.height), fitsRegularHomeLayout(s.width / scale, s.height / scale))
        }
    }

    /**
     * #117: tapping the search field in Settings opened the keyboard, the keyboard took 300 dp off the window, the
     * window stopped counting as a regular one, and the sidebar the field lived in was torn down mid-tap, which took
     * the focus and put the keyboard away again.
     */
    @Test fun `the keyboard never changes which Settings layout a window gets`() {
        // Galaxy Z Fold8 inner with a keyboard up: 300 dp of its 704 covered, 404 left to draw in.
        assertTrue(settingsSplits(932f, 404f, keyboardDp = 300f))
        assertEquals(2, settingsColumns(932f, 404f, keyboardDp = 300f))
        // Without measuring the keyboard out, the same window reads as a phone's.
        assertFalse(settingsSplits(932f, 404f))
        assertEquals(1, settingsColumns(932f, 404f))
        // A phone window doesn't gain a sidebar because a keyboard opened, either.
        assertFalse(settingsSplits(411f, 500f, keyboardDp = 391f))
        assertEquals(1, settingsColumns(411f, 500f, keyboardDp = 391f))
        // Every screen in the matrix, with a keyboard of any usual size over it.
        for (s in screens) for (keyboard in listOf(0f, 120f, 240f, 360f)) {
            val left = (s.height - keyboard).coerceAtLeast(0f)
            val tag = "${s.name} under ${keyboard.toInt()} dp of keyboard"
            assertEquals(tag, settingsSplits(s.width, s.height), settingsSplits(s.width, left, keyboardDp = keyboard))
            assertEquals(tag, settingsColumns(s.width, s.height), settingsColumns(s.width, left, keyboardDp = keyboard))
        }
    }

    @Test fun `Home fits every window without cropping or overlapping`() {
        for (s in screens) for (labels in listOf(true, false)) {
            val scale = uiScale(s.width, s.height)
            val w = s.width / scale; val h = s.height / scale
            val status = if (fitsRegularHomeLayout(w, h)) 180f else 0f
            val g = homeGeometry(w, h, LayoutPreset(), labels, statusHeight = status)
            val tag = "${s.name} (${w.toInt()}×${h.toInt()})"
            assertTrue("$tag rows ${g.rowHeight}", g.rowHeight >= 48f)
            assertTrue("$tag dock rows", g.dockRowHeight >= 48f)
            assertTrue("$tag icon ${g.iconSize}", g.iconSize >= 32f)
            assertTrue("$tag home width", g.homeWidth <= w)
            if (!g.horizontalDock) assertTrue("$tag grid ${g.gridWidth} + dock beside it", g.gridWidth + LayoutPreset().sanitized().dockWidth <= g.homeWidth)
            else assertTrue("$tag grid", g.gridWidth <= w)
            assertTrue("$tag dock under the top", g.dockTop >= 8f)
            if (h >= 400f) assertTrue("$tag dock ${g.dockTop}+${g.dockHeight} inside $h", g.dockTop + g.dockHeight <= h)
            // Two Home panels only when there's room for both; tall roomy windows get the bottom dock bar.
            assertFalse("$tag expanded and bar", g.expanded && g.horizontalDock)
            if (g.expanded) assertTrue(tag, w >= 650f && w > h)
        }
    }
}
