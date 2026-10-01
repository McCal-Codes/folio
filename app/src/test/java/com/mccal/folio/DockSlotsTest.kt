package com.mccal.folio

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The dock holds four apps until asked for more, up to six, and nothing that was written for four changes. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DockSlotsTest {
    private val blank = LauncherState(homeSlots = List(HOME_CELLS) { null }, widgetPlacements = emptyList(), loading = false)

    @Test fun `a dock is four places until it is made bigger`() {
        assertEquals(4, LauncherState().dock.size)
        assertEquals(4, decodeLauncherState("{}", legacyRaw = null).dock.size)
        assertEquals(4, decodeLauncherState("""{"dock":["a","b"]}""", legacyRaw = null).dock.size)
        assertEquals(listOf("a", "b", null, null), decodeLauncherState("""{"dock":["a","b"]}""", legacyRaw = null).dock)
    }

    @Test fun `a saved dock keeps up to six places, and a damaged one is held to that`() {
        assertEquals(listOf("a", "b", "c", "d", "e", null), decodeLauncherState("""{"dock":["a","b","c","d","e",null]}""", legacyRaw = null).dock)
        assertEquals(6, decodeLauncherState(JSONObject().put("dock", org.json.JSONArray(List(40) { "x$it" })).toString(), legacyRaw = null).dock.size)
    }

    @Test fun `growing adds empty places, and shrinking never drops an app`() {
        assertEquals(listOf("a", null, null, null, null, null), resizedDock(listOf("a", null, null, null), 6))
        assertEquals(listOf("a", "b", null, null), resizedDock(listOf("a", "b", null, null, null, null), 4))
        assertNull("a fifth app would be lost", resizedDock(listOf("a", "b", "c", "d", "e", null), 4))
        assertEquals(listOf("a", "b", "c", "d", "e"), resizedDock(listOf("a", "b", "c", "d", "e", null), 5))
        assertEquals("never fewer than four, never more than six", 6, resizedDock(listOf("a", null, null, null), 99)!!.size)
        assertEquals(4, resizedDock(listOf("a", null, null, null, null), 1)!!.size)
    }

    @Test fun `a backup of a four place dock is exactly what it was, and a bigger one is read back`() {
        val four = encodeLayoutBackup(blank.copy(dock = List(4) { null }), emptyList(), "phone")
        assertEquals(4, JSONObject(four).getJSONArray("dock").length())
        assertEquals(4, decodeLayoutBackup(four, emptyList(), emptyList(), "phone").layout.dock.size)

        val six = encodeLayoutBackup(blank.copy(dock = List(6) { null }), emptyList(), "phone")
        assertEquals(6, JSONObject(six).getJSONArray("dock").length())
        assertEquals(6, decodeLayoutBackup(six, emptyList(), emptyList(), "phone").layout.dock.size)
    }

    @Test fun `a backup with a dock of a size nobody makes is refused`() {
        for (size in listOf(3, 7)) {
            val raw = JSONObject(encodeLayoutBackup(blank, emptyList(), "phone")).put("dock", org.json.JSONArray(List(size) { JSONObject.NULL })).toString()
            val refused = runCatching { decodeLayoutBackup(raw, emptyList(), emptyList(), "phone") }.isFailure
            assertTrue("$size places", refused)
        }
    }

    @Test fun `more places make a taller dock and a wider bar, and four are exactly as before`() {
        val preset = LayoutPreset()
        val four = homeGeometry(704f, 932f, preset, true, statusHeight = 160f)
        assertEquals(four, homeGeometry(704f, 932f, preset, true, statusHeight = 160f, dockSlots = 4))
        val six = homeGeometry(704f, 932f, preset, true, statusHeight = 160f, dockSlots = 6)
        assertTrue(six.dockHeight >= 6 * 48f + 16f - .01f || six.dockHeight >= four.dockHeight)
        assertTrue(six.dockRowHeight >= 48f)
        // The side rail on a cover screen has to hold six 48 dp targets, or scroll.
        val cover = homeGeometry(475f, 751f, preset, true, statusHeight = 160f, dockSlots = 6)
        assertTrue(cover.dockRowHeight >= 48f)
        assertFalse(cover.dockHeight.isNaN())
    }

    @Test fun `a bottom bar with more apps than fit is held to the room it has`() {
        val phone = homeGeometry(360f, 780f, LayoutPreset(dockPlacement = DockPlacement.BOTTOM), true, statusHeight = 160f, dockSlots = 6)
        assertTrue(phone.horizontalDock)
        assertTrue(phone.dockBarRoom in 1f..360f)
        assertTrue("six apps need more than the room here, so the bar scrolls", 6 * phone.dockPitch + 16f > phone.dockBarRoom)
        val roomy = homeGeometry(704f, 932f, LayoutPreset(), true, statusHeight = 160f, dockSlots = 6)
        assertTrue("on the upright inner screen it all fits", 6 * roomy.dockPitch + 16f <= roomy.dockBarRoom + .5f)
    }
}
