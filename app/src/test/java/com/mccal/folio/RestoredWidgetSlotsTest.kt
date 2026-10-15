package com.mccal.folio

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.MutableCreationExtras
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** W-1: a restored Layout Backup's widgets get their own slot numbers, so an old Smart Stack can't land on one of them. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RestoredWidgetSlotsTest {
    private val context = RuntimeEnvironment.getApplication()
    private val prefs = context.getSharedPreferences(SettingKeys.PREFS, 0)
    private val store = ViewModelStore()
    private val weather = "com.example.weather/.Widget"

    @After fun clear() { store.clear(); FolioSettingsBridge.liveModel = null }

    private fun widget(slot: Int, id: Int, column: Int) =
        JSONObject().put("slot", slot).put("id", id).put("page", 0).put("column", column).put("row", 0).put("spanX", 2).put("spanY", 2)

    /** Home now: a widget (id 41) with a Smart Stack behind it (id 42) in slot 0, and a white Big Clock in slot 1. */
    private fun home(
        widgets: JSONArray = JSONArray().put(widget(0, 41, 0)).put(widget(1, BIG_CLOCK_WIDGET, 2)),
        stacks: JSONObject = JSONObject().put("0", JSONArray().put(42)),
    ): LauncherModel {
        val empty = JSONArray().apply { repeat(HOME_CELLS) { put(JSONObject.NULL) } }
        val saved = JSONObject().put("schema", 9).put("pinned", JSONArray()).put("homeSlots", empty).put("leadingSlots", empty)
            .put("dock", JSONArray()).put("folders", JSONArray())
            .put("widgets", widgets)
            .put("widgetStacks", stacks)
            .put("bigClockStyles", JSONObject().put("1", JSONObject().put("mode", "WHITE")))
        prefs.edit().clear().putString(SettingKeys.STATE, saved.toString()).commit()
        return ViewModelProvider.create(store, ViewModelProvider.AndroidViewModelFactory(), MutableCreationExtras().apply {
            set(ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY, context)
        })[LauncherModel::class]
    }

    /** The backup: a different widget, waiting to be bound, in slot 0, and the Big Clock in slot 1 again. */
    private val backup = HomeLayout(List(HOME_CELLS) { null }, listOf(null, null, null, null),
        widgetPlacements = listOf(WidgetPlacement(0, NEEDS_BINDING_WIDGET, 0, 0, 0, 2, 2), WidgetPlacement(1, BIG_CLOCK_WIDGET, 0, 2, 0, 2, 2)),
        widgetRestores = listOf(WidgetRestore(0, weather, 0L, "Weather", "Personal")))

    @Test fun `a restore over overlapping slot numbers leaves each stack on its own widgets`() {
        val model = home()
        val defaults = LauncherState()
        assertTrue(model.applyImportedLayout(LayoutImportPreview(backup, emptyList(), emptyList(), appCount = 0, folderCount = 0,
            widgetCount = 2, compact = defaults.compact, expanded = defaults.expanded, labels = true, googleSearch = true, verticalStatus = true)))

        val state = model.state.value
        val restoredWeather = state.widgetPlacements.single { it.id == NEEDS_BINDING_WIDGET }
        val restoredClock = state.widgetPlacements.single { it.id == BIG_CLOCK_WIDGET }
        state.widgetPlacements.forEach { assertEquals("no old stack on ${it.slot}", listOf(it.id), model.stackCards(it.slot)) }
        assertTrue("restored widgets are on numbers the old Home never used", state.widgetPlacements.none { it.slot in setOf(0, 1) })
        assertEquals("the widget waiting to be bound still finds its provider", weather, state.layout.widgetRestore(restoredWeather.slot)?.providerComponent)
        assertEquals("the same Big Clock keeps its look", "WHITE", state.bigClockStyles[restoredClock.slot]?.mode)

        // Undo puts the old widget back with its stack.
        assertTrue(model.undoEdit())
        assertEquals(listOf(41, 42), model.stackCards(0))
    }

    @Test fun `a stack whose number an old overflow widget keeps comes back whole with Undo`() {
        // Home: widget 51 with 52 stacked behind it, in slot 5. The backup's pre-schema-6 overflow widget keeps slot 5.
        val model = home(JSONArray().put(widget(5, 51, 0)), JSONObject().put("5", JSONArray().put(52)))
        val overflow = WidgetPlacement(5, CLOCK_WIDGET, 1, 0, GRID_ROWS, GRID_COLUMNS, 4)
        val defaults = LauncherState()
        assertTrue(model.applyImportedLayout(LayoutImportPreview(HomeLayout(List(HOME_CELLS) { null }, listOf(null, null, null, null),
            widgetPlacements = listOf(overflow)), emptyList(), emptyList(), appCount = 0, folderCount = 0, widgetCount = 1,
            compact = defaults.compact, expanded = defaults.expanded, labels = true, googleSearch = true, verticalStatus = true)))

        assertEquals("the old stack isn't on the restored widget", listOf(CLOCK_WIDGET), model.stackCards(5))
        assertTrue("its widgets stay bound while Undo can bring them back", model.retainedWidgetIds.containsAll(setOf(51, 52)))

        assertTrue(model.undoEdit())
        assertEquals(listOf(51, 52), model.stackCards(5))
        assertTrue(model.retainedWidgetIds.containsAll(setOf(51, 52)))
    }

    @Test fun `new numbers keep their remainder by 3, and an old overflow widget keeps its number`() {
        val overflow = WidgetPlacement(5, INFO_WIDGET, 1, 0, GRID_ROWS, GRID_COLUMNS, 4)
        val today = WidgetPlacement(2, NEEDS_BINDING_WIDGET, -1, 0, 0, 4, 6)
        val layout = HomeLayout(List(HOME_CELLS) { null }, listOf(null, null, null, null), widgetPlacements = listOf(today, overflow),
            widgetRestores = listOf(WidgetRestore(2, weather, 0L, "Weather", "Personal")))

        val (renumbered, slots) = renumberRestoredWidgets(layout, firstFree = 4)

        // 5 has the right remainder but is the overflow widget's, so the Today widget moves on to 8.
        assertEquals(mapOf(2 to 8, 5 to 5), slots)
        assertEquals(listOf(5, 8), renumbered.widgetPlacements.map { it.slot })
        assertEquals(listOf(8), renumbered.widgetRestores.map { it.slot })
    }
}
