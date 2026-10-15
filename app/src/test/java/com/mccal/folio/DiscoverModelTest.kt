package com.mccal.folio

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.MutableCreationExtras
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** S-1: Discover's model only reads the saved Home, so it can't take Home's place for tiles and Focus or save over Home. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DiscoverModelTest {
    private val context = RuntimeEnvironment.getApplication()
    private val prefs = context.getSharedPreferences(SettingKeys.PREFS, 0)
    private val homeStore = ViewModelStore()
    private val discoverStore = ViewModelStore()
    private fun saved() = JSONObject(prefs.getString(SettingKeys.STATE, "{}")!!)
    private fun model(store: ViewModelStore, factory: ViewModelProvider.Factory) =
        ViewModelProvider.create(store, factory, MutableCreationExtras().apply {
            set(ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY, context)
        })[LauncherModel::class]

    @Before fun savedHome() {
        val empty = JSONArray().apply { repeat(HOME_CELLS) { put(JSONObject.NULL) } }
        val home = JSONObject().put("schema", 9).put("pinned", JSONArray()).put("homeSlots", empty).put("leadingSlots", empty)
            .put("dock", JSONArray()).put("widgets", JSONArray()).put("folders", JSONArray())
        prefs.edit().putString(SettingKeys.STATE, home.toString()).commit()
    }

    @After fun clear() { discoverStore.clear(); homeStore.clear(); FolioSettingsBridge.liveModel = null }

    @Test fun `a tile change made after a Discover visit is still there after Home's next save`() {
        val home = model(homeStore, ViewModelProvider.AndroidViewModelFactory())
        model(discoverStore, LauncherModel.readOnlyFactory)
        discoverStore.clear() // Discover closes

        // The Island tile, then any later change on Home that saves.
        FolioSettingsBridge.set(context, SettingKeys.ISLAND_EVERYWHERE, true) { model, value -> model.setIslandEverywhere(value) }
        home.setDockEverywhere(true)

        assertTrue(saved().getBoolean(SettingKeys.ISLAND_EVERYWHERE))
        assertTrue(saved().getBoolean(SettingKeys.DOCK_EVERYWHERE))
        assertSame("Home stays the model tiles and Focus go through", home, FolioSettingsBridge.liveModel?.get())
    }

    @Test fun `Discover's model never saves`() {
        val before = prefs.getString(SettingKeys.STATE, null)
        val discover = model(discoverStore, LauncherModel.readOnlyFactory)
        discover.setStandBy(false)
        discover.flushPending()
        discoverStore.clear()
        assertEquals(before, prefs.getString(SettingKeys.STATE, null))
        assertEquals(null, FolioSettingsBridge.liveModel?.get())
    }
}
