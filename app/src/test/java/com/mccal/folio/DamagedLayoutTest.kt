package com.mccal.folio

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.MutableCreationExtras
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** S-2: the damaged-layout notice's Restore really restores, and no backup is made of the stand-in Home. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DamagedLayoutTest {
    private val context = RuntimeEnvironment.getApplication()
    private val prefs = context.getSharedPreferences(SettingKeys.PREFS, 0)
    private val store = ViewModelStore()
    /** A save cut off part way, as a full disk or a crash mid-write leaves it. */
    private val damaged = """{"schema":9,"pinned":[],"homeSlots":["""
    private val mail = "com.example.mail/.Main"
    private val layout = HomeLayout(List(HOME_CELLS) { null }, listOf(mail, null, null, null))
    private val defaults = LauncherState()
    private val backup = LayoutImportPreview(layout, missingApps = emptyList(), profileIssues = emptyList(), appCount = 1,
        folderCount = 0, widgetCount = 0, compact = defaults.compact, expanded = defaults.expanded,
        labels = false, googleSearch = true, verticalStatus = true)
    private lateinit var model: LauncherModel

    @Before fun damagedHome() {
        prefs.edit().clear().putString(SettingKeys.STATE, damaged).commit()
        model = ViewModelProvider.create(store, ViewModelProvider.AndroidViewModelFactory(), MutableCreationExtras().apply {
            set(ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY, context)
        })[LauncherModel::class]
        assertTrue(model.layoutDamaged)
    }

    @After fun clear() { store.clear(); FolioSettingsBridge.liveModel = null }

    private fun saved() = JSONObject(prefs.getString(SettingKeys.STATE, "{}")!!)

    @Test fun `Restore on a damaged layout applies the backup, keeps the damaged copy and clears the notice`() {
        assertTrue(model.applyImportedLayout(backup))

        assertFalse(model.layoutDamaged)
        assertNull(model.state.value.error)
        assertEquals(damaged, prefs.getString("state_damaged_backup", null))
        assertEquals(mail, saved().getJSONArray("dock").getString(0))
        assertFalse("the backup's own settings came with it", saved().getBoolean("labels"))
        assertFalse("the stand-in Home is nothing to undo to", model.state.value.canUndoEdit)
    }

    @Test fun `an earlier layout from Layout History restores a damaged layout too`() {
        assertTrue(model.restoreLayoutSnapshot(LayoutSnapshot(0L, "Saved by you", layout)))

        assertFalse(model.layoutDamaged)
        assertEquals(damaged, prefs.getString("state_damaged_backup", null))
        assertEquals(mail, saved().getJSONArray("dock").getString(0))
    }

    @Test fun `no Layout Backup is made while the layout is damaged`() {
        assertNull("the stand-in Home would export as an empty one", model.stateForBackup())
        assertEquals(damaged, prefs.getString(SettingKeys.STATE, null))

        model.applyImportedLayout(backup)
        assertNotNull(model.stateForBackup())
    }
}
