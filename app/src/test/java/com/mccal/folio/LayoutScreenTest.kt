package com.mccal.folio

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The upright inner screen can have its own layout, and until it does every window gets exactly the layout it always did. */
class LayoutScreenTest {
    private val sizes = org.json.JSONObject(javaClass.getResource("/screen-matrix.json")!!.readText()).getJSONArray("devices").let { list ->
        (0 until list.length()).flatMap { i ->
            val d = list.getJSONObject(i); val w = d.getDouble("width").toFloat(); val h = d.getDouble("height").toFloat()
            listOf(w to h, h to w, maxOf(w, h) / 2f to minOf(w, h))
        }
    } + listOf(649f to 700f, 650f to 559f, 650f to 560f, 700f to 700f, 704f to 932f, 932f to 704f)

    @Test fun `with no layout of its own every window gets the layout it always did`() {
        val state = LauncherState(compact = LayoutPreset(iconSize = 50f), expanded = LayoutPreset(iconSize = 60f))
        for ((w, h) in sizes) for (scale in listOf(1f, 1.2f)) {
            val before = if (w * scale >= EXPANDED_HOME_MIN_WIDTH_DP && h * scale >= HOME_REGULAR_MIN_HEIGHT_DP) state.expanded else state.compact
            assertEquals("$w x $h at $scale", before, state.presetFor(layoutScreenFor(w, h, scale)))
        }
    }

    @Test fun `an upright inner screen is the one taller than wide, among the expanded windows`() {
        assertEquals(LayoutScreen.INNER_UPRIGHT, layoutScreenFor(704f, 932f))
        assertEquals(LayoutScreen.INNER, layoutScreenFor(932f, 704f))
        assertEquals(LayoutScreen.INNER, layoutScreenFor(700f, 700f))
        assertEquals("the cover stays the cover however it is held", LayoutScreen.COVER, layoutScreenFor(475f, 751f))
        assertEquals(LayoutScreen.COVER, layoutScreenFor(649f, 700f))
    }

    @Test fun `once it has one, only the upright inner screen uses it`() {
        val mine = LayoutPreset(iconSize = 44f)
        val state = LauncherState(compact = LayoutPreset(iconSize = 50f), expanded = LayoutPreset(iconSize = 60f), portrait = mine)
        assertEquals(mine, state.presetFor(LayoutScreen.INNER_UPRIGHT))
        assertEquals(state.expanded, state.presetFor(LayoutScreen.INNER))
        assertEquals(state.compact, state.presetFor(LayoutScreen.COVER))
    }

    @Test fun `it is saved only when it exists, and an older save has none`() {
        assertNull(decodeLauncherState("{}", legacyRaw = null).portrait)
        val saved = JSONObject().put("expanded", JSONObject().put("iconSize", 60.0).put("rowGap", 12.0).put("dockWidth", 70.0).put("dockPosition", .5))
            .put("portrait", JSONObject().put("iconSize", 44.0).put("rowGap", 10.0).put("dockWidth", 70.0).put("dockPosition", .5))
        val state = decodeLauncherState(saved.toString(), legacyRaw = null)
        assertEquals(44f, state.portrait?.iconSize)
        assertEquals(60f, state.expanded.iconSize)
        assertNotEquals(state.expanded, state.portrait)
    }

    @Test fun `a damaged upright layout is clamped like any other, not trusted`() {
        val state = decodeLauncherState(JSONObject().put("portrait", JSONObject().put("iconSize", 500.0)).toString(), legacyRaw = null)
        assertTrue(state.portrait!!.iconSize <= 68f)
    }
}
