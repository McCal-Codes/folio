package com.mccal.folio

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Simple Mode turns some settings off and draws its own Home. Turning it off has to put back exactly what was there
 * (Pixel's Simple View does), including after Folio was killed or the phone restarted while Simple was on.
 */
class SimpleModeTest {
    @get:Rule val temp = TemporaryFolder()

    private val folder = "folder:223e4567-e89b-12d3-a456-426614174000"

    /** A Home someone arranged, and settings Simple changes all set away from Simple's values. */
    private val mine = LauncherState(
        homeSlots = listOf("com.a/.A", null, folder, "com.b/.B"), dock = listOf("com.phone/.P", null, "com.web/.W", null),
        widgetPlacements = listOf(WidgetPlacement(0, CLOCK_WIDGET, 0, 0, 0, 2, 2), WidgetPlacement(3, 42, 1, 2, 3, 2, 1)),
        folders = listOf(FolderEntry(folder, "Games", listOf("com.c/.C", "com.d/.D"))),
        widgetRestores = listOf(WidgetRestore(5, "com.x/.W", 0L, "Weather", "Personal", sourceScope = null)),
        leadingSlots = List(HOME_CELLS) { if (it == 1) "com.e/.E" else null }, minPages = 3,
        island = true, folioPanels = true, swipeDownHome = "SPOTLIGHT", islandEverywhere = true, dockEverywhere = true,
        standBy = true, standByCharging = true, standByTent = true, lockCover = true,
        pageEffect = PageEffect.CUBE, foldEffect = true, wallpaperMotion = true, addNewAppsToHome = true,
        haptics = false, searchEngine = "DUCKDUCKGO",
    )
    private val apps = listOf("com.phone/.P", "com.msg/.M", "com.cam/.C")

    @Test fun `turning Simple on changes only Simple's settings and starts its Home`() {
        val on = mine.turnedSimple(apps)
        assertEquals(ExperienceProfile.SIMPLE, on.profile())
        assertEquals(SimpleSetup(apps), on.simple)
        assertEquals(SimpleChanges.SIMPLE, SimpleChanges.of(on))
        assertFalse(on.island); assertFalse(on.folioPanels); assertFalse(on.lockCover); assertFalse(on.standBy)
        assertEquals(PageEffect.NONE, on.pageEffect)
        assertEquals("Home's layout is left alone", mine.layout, on.layout)
        assertEquals("settings Simple doesn't touch stay", mine.haptics, on.haptics)
        assertEquals(mine.searchEngine, on.searchEngine)
    }

    @Test fun `turning it off puts back exactly what was there`() {
        val restore = SimpleRestore.of(mine)
        val on = mine.turnedSimple(apps).copy(simple = SimpleSetup(apps.reversed(), SimpleStyle.GRID, SimpleTextSize.LARGEST))
        assertNotEquals(mine, on)
        assertEquals(mine, on.leftSimple(restore))
    }

    @Test fun `the saved copy survives Folio being killed while Simple is on`() {
        val file = temp.newFile("simple_restore.json").also { it.delete() }
        assertTrue(SimpleRestoreStore(file).save(SimpleRestore.of(mine)))
        val on = mine.turnedSimple(apps)
        // A new process: nothing in memory, only what is on disk. Both the Simple Home and the copy come back.
        val reloadedState = decodeLauncherState(savedWith(on.simple!!).toString(), legacyRaw = null)
        assertEquals(on.simple, reloadedState.simple)
        val reloaded = SimpleRestoreStore(file).load()
        assertEquals(SimpleRestore.of(mine), reloaded)
        assertEquals(mine, on.leftSimple(reloaded))
        SimpleRestoreStore(file).clear()
        assertNull(SimpleRestoreStore(file).load())
    }

    @Test fun `a fresh start knows Simple is on from the saved copy, before Home draws`() {
        val store = SimpleRestoreStore(java.io.File(temp.root, "simple_restore.json"))
        try {
            SimpleQuiet.start(store)
            assertFalse(SimpleQuiet.active)
            assertTrue(store.save(SimpleRestore.of(mine)))
            SimpleQuiet.start(store)
            assertTrue("notices stay quiet from the first moment", SimpleQuiet.active)
            store.clear()
            SimpleQuiet.start(store)
            assertFalse(SimpleQuiet.active)
        } finally { SimpleQuiet.active = false }
    }

    @Test fun `saving again replaces the old copy whole`() {
        val file = java.io.File(temp.root, "simple_restore.json")
        val store = SimpleRestoreStore(file)
        assertTrue(store.save(SimpleRestore.of(LauncherState())))
        assertTrue(store.save(SimpleRestore.of(mine)))
        assertEquals(SimpleRestore.of(mine), SimpleRestoreStore(file).load())
        assertFalse("no half-written file is left beside it", java.io.File(temp.root, "simple_restore.json.tmp").exists())
    }

    @Test fun `a damaged or missing copy is never half used`() {
        val file = temp.newFile("simple_restore.json")
        file.writeText("{\"version\":1,\"layout\":{},\"settings\":{\"island\":true}}")
        assertNull(SimpleRestoreStore(file).load())
        file.writeText("not json")
        assertNull(SimpleRestoreStore(file).load())
        // Without a copy, leaving still ends Simple, puts back the Folio profile and leaves Home as it is.
        val on = mine.turnedSimple(apps)
        val off = on.leftSimple(null)
        assertNull(off.simple)
        assertEquals(ExperienceProfile.FOLIO, off.profile())
        assertEquals(on.layout, off.layout)
    }

    @Test fun `an app removed from the phone while Simple was on stays gone`() {
        val restore = SimpleRestore.of(mine)
        // The refresh took the uninstalled app off the live Home meanwhile.
        val on = mine.turnedSimple(apps).copy(homeSlots = listOf("com.a/.A", null, folder))
        val off = on.leftSimple(restore)
        assertEquals(listOf("com.a/.A", null, folder), off.homeSlots)
        assertEquals(mine.dock, off.dock)
        assertEquals(SimpleChanges.of(mine), SimpleChanges.of(off))
        assertEquals("the Home from before Simple is kept in Layout History", mine.layout, restore.homeToKeep(off))
    }

    @Test fun `a backup restored while Simple is on loses nothing`() {
        val restore = SimpleRestore.of(mine)
        val on = mine.turnedSimple(apps, SimpleStyle.GRID)
        // A helper restores an older backup from All Settings: it brings its own Home and Side Bar, not Simple's
        // settings, and Simple's Home is untouched (applyImportedLayout copies the state).
        val backup = HomeLayout(slots = listOf("com.old/.O"), dock = listOf("com.phone/.P", null, null, null))
        val restored = on.copy(homeSlots = backup.slots, dock = backup.dock, widgetPlacements = emptyList(), folders = emptyList(),
            widgetRestores = emptyList(), leadingSlots = List(HOME_CELLS) { null }, minPages = 1, verticalStatus = false)
        assertEquals("Simple's Home and setup survive the restore", on.simple, restored.simple)
        val off = restored.leftSimple(restore)
        assertEquals("the restored backup is the Home after Simple", backup, off.layout)
        assertEquals("the backup's own Side Bar choice stays", false, off.verticalStatus)
        assertEquals("the settings Simple changed come back", SimpleChanges.of(mine), SimpleChanges.of(off))
        assertEquals("and the Home from before Simple goes to Layout History", mine.layout, restore.homeToKeep(off))
        assertNull("nothing to keep when Home didn't change", restore.homeToKeep(mine))
    }

    @Test fun `Simple Home holds six apps at most, each once`() {
        val many = (1..9).map { "com.app$it/.A" }
        assertEquals(many.take(SIMPLE_MAX_APPS), SimpleSetup(many + many).sanitized().apps)
        assertEquals(SIMPLE_MAX_APPS, LauncherState().turnedSimple(many).simple!!.apps.size)
    }

    @Test fun `its setup survives a save and an unknown value falls back to the default`() {
        val setup = SimpleSetup(apps, SimpleStyle.GRID, SimpleTextSize.LARGER)
        assertEquals(setup, SimpleSetup.fromJson(setup.toJson()))
        val odd = JSONObject().put("apps", JSONArray(apps)).put("style", "SPIRAL").put("textSize", "HUGE")
        assertEquals(SimpleSetup(apps), SimpleSetup.fromJson(odd))
        assertNull("a save from before Simple has it off", decodeLauncherState("{}", legacyRaw = null).simple)
    }

    @Test fun `List is the default Home style and the default text size is Folio's own standard`() {
        assertEquals(SimpleStyle.LIST, SimpleSetup().style)
        assertEquals(1f, SimpleSetup().textSize.scale, 0f)
    }

    /** The smallest save the decoder accepts, with Simple on. */
    private fun savedWith(setup: SimpleSetup): JSONObject {
        val empty = JSONArray().apply { repeat(HOME_CELLS) { put(JSONObject.NULL) } }
        return JSONObject().put("schema", STATE_SCHEMA)
            .put("pinned", JSONArray()).put("homeSlots", empty).put("leadingSlots", empty)
            .put("dock", JSONArray()).put("widgets", JSONArray()).put("folders", JSONArray())
            .put("simple", setup.toJson())
    }
}
