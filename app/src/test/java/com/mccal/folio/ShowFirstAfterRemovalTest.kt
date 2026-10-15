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

/** W-4: Show First on a stack right after removing a widget keeps that removal's Undo and the removed widget's binding. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShowFirstAfterRemovalTest {
    private val context = RuntimeEnvironment.getApplication()
    private val prefs = context.getSharedPreferences(SettingKeys.PREFS, 0)
    private val store = ViewModelStore()

    @After fun clear() { store.clear(); FolioSettingsBridge.liveModel = null }

    private fun widget(slot: Int, id: Int, column: Int) =
        JSONObject().put("slot", slot).put("id", id).put("page", 0).put("column", column).put("row", 0).put("spanX", 2).put("spanY", 2)

    /** Slot 0 shows widget 41 with 42 behind it; slot 1 is widget 51, about to be removed. */
    private fun home(): LauncherModel {
        val empty = JSONArray().apply { repeat(HOME_CELLS) { put(JSONObject.NULL) } }
        val saved = JSONObject().put("schema", 9).put("pinned", JSONArray()).put("homeSlots", empty).put("leadingSlots", empty)
            .put("dock", JSONArray()).put("folders", JSONArray())
            .put("widgets", JSONArray().put(widget(0, 41, 0)).put(widget(1, 51, 2)))
            .put("widgetStacks", JSONObject().put("0", JSONArray().put(42)))
        prefs.edit().clear().putString(SettingKeys.STATE, saved.toString()).commit()
        return ViewModelProvider.create(store, ViewModelProvider.AndroidViewModelFactory(), MutableCreationExtras().apply {
            set(ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY, context)
        })[LauncherModel::class]
    }

    @Test fun `Show First after a removal keeps the Undo and the removed widget's binding`() {
        val model = home()
        assertTrue(model.removePlacement(DropTarget.Widget(1)))
        assertTrue(model.state.value.canUndoEdit)
        assertTrue(51 in model.retainedWidgetIds)

        assertTrue(model.showFirstInStack(0, 42))
        assertEquals(42, model.placement(0)?.id)
        assertTrue("Undo is still offered", model.state.value.canUndoEdit)
        assertTrue("the removed widget is still bound, so pruning keeps it", 51 in model.retainedWidgetIds)

        assertTrue(model.undoEdit())
        assertEquals(51, model.placement(1)?.id)
        assertEquals("the stack keeps the card shown first", 42, model.placement(0)?.id)
        assertEquals(listOf(41), model.state.value.widgetStacks[0])
    }

    @Test fun `Show First with nothing to undo still leaves no Undo`() {
        val model = home()
        assertTrue(model.showFirstInStack(0, 42))
        assertTrue(!model.state.value.canUndoEdit)
    }
}
