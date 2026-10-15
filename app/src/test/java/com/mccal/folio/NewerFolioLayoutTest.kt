package com.mccal.folio

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.MutableCreationExtras
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** S-3 in the beta 4 audit: a layout saved by a newer Folio is not "damaged", and Start Fresh is not offered for it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NewerFolioLayoutTest {
    @get:Rule val compose = createComposeRule()
    private val context = RuntimeEnvironment.getApplication()
    private val prefs = context.getSharedPreferences(SettingKeys.PREFS, 0)
    private val store = ViewModelStore()

    @After fun clear() { store.clear(); FolioSettingsBridge.liveModel = null }

    private fun modelWith(saved: String): LauncherModel {
        prefs.edit().clear().putString(SettingKeys.STATE, saved).commit()
        return ViewModelProvider.create(store, ViewModelProvider.AndroidViewModelFactory(), MutableCreationExtras().apply {
            set(ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY, context)
        })[LauncherModel::class]
    }

    @Test fun `a save from a newer Folio is held back and says so`() {
        val saved = """{"schema":${STATE_SCHEMA + 1},"pinned":[],"homeSlots":[]}"""
        val model = modelWith(saved)
        assertTrue(model.layoutDamaged)
        assertTrue(model.layoutFromNewerFolio)
        assertEquals(context.getString(R.string.home_layout_saved_by_newer_folio_short), model.state.value.error)
        assertEquals("kept untouched", saved, prefs.getString(SettingKeys.STATE, null))
    }

    @Test fun `a save cut off part way is still damaged, not newer`() {
        val model = modelWith("""{"schema":9,"pinned":[],"homeSlots":[""")
        assertTrue(model.layoutDamaged)
        assertFalse(model.layoutFromNewerFolio)
        assertEquals(context.getString(R.string.saved_home_layout_could_not_be_read), model.state.value.error)
    }

    @Test fun `the newer-Folio notice offers only Not Now and Restore`() {
        compose.setContent { DamagedLayoutDialog(newerFolio = true, onRestore = {}, onNotNow = {}, onStartFresh = {}) }
        compose.onNodeWithText(context.getString(R.string.home_layout_saved_by_newer_folio)).assertExists()
        compose.onNodeWithText(context.getString(R.string.restore_2)).assertExists()
        compose.onNodeWithText(context.getString(R.string.not_now)).assertExists()
        compose.onNodeWithText(context.getString(R.string.start_fresh)).assertDoesNotExist()
    }

    @Test fun `the damaged notice still offers Start Fresh`() {
        compose.setContent { DamagedLayoutDialog(newerFolio = false, onRestore = {}, onNotNow = {}, onStartFresh = {}) }
        compose.onNodeWithText(context.getString(R.string.your_home_layout_couldn_t_be_loaded)).assertExists()
        compose.onNodeWithText(context.getString(R.string.start_fresh)).assertExists()
    }
}
