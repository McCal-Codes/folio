package com.mccal.folio

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Saved Home layouts load, and older ones upgrade without moving anything (decodeLauncherState). */
class LayoutLoadTest {
    /** A 0.6.0 (schema 8) save: 24-cell pages, an unfolded-only page, the old overflow widget at row 6. */
    internal fun schema8Fixture(): JSONObject = schema8()

    private fun schema8(): JSONObject {
        val slots = JSONArray()
        repeat(24) { slots.put(if (it == 8) "com.a/.A" else JSONObject.NULL) }  // page 0, row 2, column 0
        repeat(24) { slots.put(if (it == 13) "com.b/.B" else JSONObject.NULL) } // page 1, row 3, column 1
        val leading = JSONArray().apply { repeat(24) { put(if (it == 9) "com.c/.C" else JSONObject.NULL) } }
        val widgets = JSONArray()
            .put(JSONObject("""{"slot":0,"id":-2,"page":0,"column":0,"row":0,"spanX":2,"spanY":2}"""))
            .put(JSONObject("""{"slot":5,"id":42,"page":1,"column":0,"row":6,"spanX":4,"spanY":4}"""))
        return JSONObject().put("schema", 8).put("pinned", JSONArray()).put("homeSlots", slots).put("leadingSlots", leading)
            .put("dock", JSONArray().put("com.d/.D")).put("widgets", widgets).put("folders", JSONArray())
            .put("compact", JSONObject().put("iconSize", 60.0).put("rowGap", 12.0).put("dockWidth", 70.0).put("dockPosition", .5).put("dockAlignToGrid", false))
    }

    @Test fun `a 0_6_0 layout upgrades with every app and widget in the same place`() {
        val state = decodeLauncherState(schema8().toString(), legacyRaw = null)
        assertEquals("com.a/.A", state.homeSlots[homeCellIndex(0, 8)])
        assertEquals("com.b/.B", state.homeSlots[homeCellIndex(1, 13)])
        assertEquals(HOME_CELLS, state.leadingSlots.size)
        assertEquals("com.c/.C", state.leadingSlots[9])
        assertEquals("com.d/.D", state.dock[0])
        val overflow = state.widgetPlacements.first { it.slot == 5 }
        assertEquals(GRID_ROWS, overflow.row)
        assertEquals(0, state.widgetPlacements.first { it.slot == 0 }.row)
        // Per-screen settings keep their values, and new ones start at today's defaults.
        assertEquals(60f, state.compact.iconSize); assertEquals(12f, state.compact.rowGap); assertEquals(false, state.compact.dockAlignToGrid)
        assertEquals(DEFAULT_COLUMN_GAP, state.compact.columnGap); assertEquals(false, state.compact.pageTop)
        // Rows are the exception: Automatic is the default for a new Folio, but a Home that already exists keeps the
        // four rows it was arranged in until someone asks for more (McCal, 2026-09-20).
        assertEquals(BASE_APP_ROWS, state.homeRows)
    }

    @Test fun `an upgraded layout saved again loads the same`() {
        val first = decodeLauncherState(schema8().toString(), legacyRaw = null)
        // What a schema 9 save holds for the same layout.
        val slots = JSONArray().apply { first.homeSlots.forEach { put(it ?: JSONObject.NULL) } }
        val leading = JSONArray().apply { first.leadingSlots.forEach { put(it ?: JSONObject.NULL) } }
        val widgets = JSONArray().apply { first.widgetPlacements.forEach { w -> put(JSONObject().put("slot", w.slot).put("id", w.id)
            .put("page", w.page).put("column", w.column).put("row", w.row).put("spanX", w.spanX).put("spanY", w.spanY)) } }
        val saved = schema8().put("schema", STATE_SCHEMA).put("homeSlots", slots).put("leadingSlots", leading).put("widgets", widgets)
        val again = decodeLauncherState(saved.toString(), legacyRaw = null)
        assertEquals(first.homeSlots, again.homeSlots)
        assertEquals(first.leadingSlots, again.leadingSlots)
        assertEquals(first.widgetPlacements, again.widgetPlacements)
    }

    @Test fun `a damaged or newer save is refused instead of silently replaced`() {
        assertThrows(Exception::class.java) { decodeLauncherState("{not json", legacyRaw = null) }
        assertThrows(Exception::class.java) { decodeLauncherState(schema8().put("schema", STATE_SCHEMA + 1).toString(), legacyRaw = null) }
        // A schema 8 save missing its unfolded-only page is damaged, not empty.
        assertThrows(Exception::class.java) { decodeLauncherState(schema8().apply { remove("leadingSlots") }.toString(), legacyRaw = null) }
        // Two widgets can't share a slot.
        val clash = schema8().apply { getJSONArray("widgets").put(JSONObject("""{"slot":0,"id":-3,"page":0,"column":2,"row":0,"spanX":2,"spanY":2}""")) }
        assertThrows(Exception::class.java) { decodeLauncherState(clash.toString(), legacyRaw = null) }
    }

    /** The schema 8 fixture as this release saves it: 36-cell pages, so it decodes as a current save. */
    private fun currentSave(schema: Int): JSONObject {
        val first = decodeLauncherState(schema8().toString(), legacyRaw = null)
        val slots = JSONArray().apply { first.homeSlots.forEach { put(it ?: JSONObject.NULL) } }
        val leading = JSONArray().apply { first.leadingSlots.forEach { put(it ?: JSONObject.NULL) } }
        val widgets = JSONArray().apply { first.widgetPlacements.forEach { w -> put(JSONObject().put("slot", w.slot).put("id", w.id)
            .put("page", w.page).put("column", w.column).put("row", w.row).put("spanX", w.spanX).put("spanY", w.spanY)) } }
        return schema8().put("schema", schema).put("homeSlots", slots).put("leadingSlots", leading).put("widgets", widgets)
    }

    @Test fun `a schema 9 save from before Icon Actions loads with none, unchanged, and is written back as schema 9`() {
        val state = decodeLauncherState(currentSave(9).toString(), legacyRaw = null)
        assertTrue(state.iconActions.isEmpty())
        assertEquals("com.a/.A", state.homeSlots[homeCellIndex(0, 8)])
        assertEquals("com.d/.D", state.dock[0])
        // Nothing from schema 10 is in it, so an older Folio can still read what this one writes back.
        assertEquals(9, stateSchemaFor(state))
    }

    @Test fun `a schema 10 save keeps its icon actions, even one this build doesn't know, and a damaged gesture alone is dropped`() {
        val actions = JSONObject()
            .put("com.a/.A", JSONObject().put("up", JSONObject().put("id", "TORCH")).put("down", JSONObject().put("id", "future.action").put("a", JSONObject().put("x", "1"))))
            .put("com.d/.D", JSONObject().put("double", JSONObject().put("id", "")).put("up", JSONObject().put("id", "SPOTLIGHT")))
            .put("com.c/.C", JSONObject().put("double", "not an object"))
        val state = decodeLauncherState(currentSave(10).put("iconActions", actions).toString(), legacyRaw = null)
        assertEquals(IconActions(up = ActionRef("TORCH"), down = ActionRef("future.action", mapOf("x" to "1"))), state.iconActions["com.a/.A"])
        assertEquals(IconActions(up = ActionRef("SPOTLIGHT")), state.iconActions["com.d/.D"])
        assertNull("an icon with no gesture left has no entry", state.iconActions["com.c/.C"])
        assertEquals("the layout is untouched", "com.a/.A", state.homeSlots[homeCellIndex(0, 8)])
        assertEquals(10, stateSchemaFor(state))
    }

    @Test fun `an empty first launch gives a clean default Home`() {
        val state = decodeLauncherState("{}", legacyRaw = null)
        assertTrue(state.homeSlots.all { it == null })
        assertNull(state.error)
        // Only Duet: the fold animation was built in and on before it became a tweak.
        assertEquals(setOf(DUET_ID), state.installedTweaks)
    }
}

/** An update must not rearrange a Home that was already there. */
class UpgradeKeepsItsRowsTest {
    private fun schema8() = LayoutLoadTest().schema8Fixture()

    /** The same layout as a save this release would write: 36-cell pages, so decode accepts it. */
    private fun schema9(): JSONObject {
        val upgraded = decodeLauncherState(schema8().toString(), legacyRaw = null)
        val slots = JSONArray().apply { upgraded.homeSlots.forEach { put(it ?: JSONObject.NULL) } }
        val leading = JSONArray().apply { upgraded.leadingSlots.forEach { put(it ?: JSONObject.NULL) } }
        val widgets = JSONArray().apply { upgraded.widgetPlacements.forEach { w -> put(JSONObject().put("slot", w.slot).put("id", w.id)
            .put("page", w.page).put("column", w.column).put("row", w.row).put("spanX", w.spanX).put("spanY", w.spanY)) } }
        return schema8().put("schema", STATE_SCHEMA).put("homeSlots", slots).put("leadingSlots", leading).put("widgets", widgets)
    }

    @Test fun `a save from before this release comes back at four rows, not automatic`() {
        assertEquals(BASE_APP_ROWS, decodeLauncherState(schema8().toString(), legacyRaw = null).homeRows)
    }

    @Test fun `a save from this release keeps automatic`() {
        assertEquals(0, decodeLauncherState(schema9().toString(), legacyRaw = null).homeRows)
    }

    @Test fun `a choice that was made is kept either way`() {
        val chosen = schema9().put("homeRows", BASE_APP_ROWS).toString()
        assertEquals(BASE_APP_ROWS, decodeLauncherState(chosen, legacyRaw = null).homeRows)
    }
}
