package com.mccal.folio

import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Runtime coverage for preservation guarantees that depend on Android JSON and model state. */
class LayoutBackupIntegrationTest {
    val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules = org.junit.rules.RuleChain.outerRule(WithoutNativeFeed()).around(compose)

    private fun model() = ViewModelProvider(compose.activity)[LauncherModel::class.java]
    private fun ready() = compose.waitUntil(15_000) { !model().state.value.loading }

    @Test fun unresolvedWorkIdentityAndWidgetScopeSurviveReexport() {
        ready()
        val icon = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        // Production app IDs use ComponentName.flattenToString(), whose class name is canonical.
        val component = "com.example.work/com.example.work.Main"
        val workId = profileAppId(component, 42, 0)
        val app = AppEntry(workId, "Work app", icon, android.content.ComponentName.unflattenFromString(component)!!,
            userSerial = 42, profileLabel = "Work", isWork = true)
        val restore = WidgetRestore(8, "com.example.work/com.example.work.Widget", 42, "Work widget", "Work",
            isWork = true, sourceScope = "origin-scope")
        val placement = WidgetPlacement(8, NEEDS_BINDING_WIDGET, 0, 0, 0, 2, 2)
        val exported = encodeLayoutBackup(LauncherState(apps = listOf(app), profiles = listOf(
            AppProfile(0, "Personal", true, false, false, true, true),
            AppProfile(42, "Work", false, true, false, true, true)),
            homeSlots = listOf(null, null, workId), widgetPlacements = listOf(placement),
            widgetRestores = listOf(restore), loading = false), emptyList(), "origin-scope")

        val same = decodeLayoutBackup(exported, listOf(app), listOf(
            AppProfile(0, "Personal", true, false, false, true, true),
            AppProfile(42, "Work", false, true, false, true, true)), "origin-scope")
        assertEquals(workId, same.layout.slots[2])
        assertTrue(same.profileIssues.isEmpty())

        val foreign = decodeLayoutBackup(exported, emptyList(), listOf(
            AppProfile(0, "Personal", true, false, false, true, true)), "destination-scope")
        assertNull(foreign.layout.slots.getOrNull(2))
        assertEquals("origin-scope", foreign.layout.widgetRestores.single().sourceScope)
        assertTrue(foreign.profileIssues.isNotEmpty())
        val reexport = encodeLayoutBackup(LauncherState(homeSlots = foreign.layout.slots,
            dock = foreign.layout.dock, folders = foreign.layout.folders,
            widgetPlacements = foreign.layout.widgetPlacements, widgetRestores = foreign.layout.widgetRestores,
            compact = foreign.compact, expanded = foreign.expanded, labels = foreign.labels,
            googleSearch = foreign.googleSearch, verticalStatus = foreign.verticalStatus, loading = false),
            emptyList(), "destination-scope")
        assertEquals("origin-scope", JSONObject(reexport).getJSONArray("widgets").getJSONObject(0).getString("sourceScope"))
        icon.recycle()
    }

    @Test fun importedLayoutAndSettingsUndoAtomicallyAndRetainOldWidgetBindings() {
        ready()
        val before = model().state.value
        val oldWidgetIds = before.widgetPlacements.map { it.id }.filter { it >= 0 }.toSet()
        val preview = LayoutImportPreview(before.layout.copy(widgetPlacements = emptyList(), widgetRestores = emptyList()),
            emptyList(), emptyList(), before.homeSlots.filterNotNull().size, before.folders.size, 0,
            before.compact, before.expanded, labels = !before.labels, googleSearch = !before.googleSearch,
            verticalStatus = !before.verticalStatus)
        try {
            compose.runOnIdle { assertTrue(model().applyImportedLayout(preview)) }
            assertTrue(oldWidgetIds.all { it in model().retainedWidgetIds })
            compose.runOnIdle { assertTrue(model().undoEdit()) }
            assertEquals(before.layout, model().state.value.layout)
            assertEquals(before.labels, model().state.value.labels)
            assertEquals(before.googleSearch, model().state.value.googleSearch)
            assertEquals(before.verticalStatus, model().state.value.verticalStatus)
        } finally {
            compose.runOnIdle {
                if (model().state.value.canUndoEdit) model().undoEdit()
                model().restoreLayout(before.layout)
                model().setLabels(before.labels)
                model().setGoogleSearch(before.googleSearch)
                model().setVerticalStatus(before.verticalStatus)
            }
        }
    }

    @Test fun corruptBackupDecodeLeavesPersistedLauncherStateUntouched() {
        ready()
        val prefs = compose.activity.getSharedPreferences("launcher", 0)
        val before = prefs.getString("state", null)
        assertThrows(Exception::class.java) {
            decodeLayoutBackup("{\"version\":1,\"sourceScope\":\"x\",\"apps\":[]}",
                model().state.value.apps, model().state.value.profiles, "x")
        }
        assertEquals(before, prefs.getString("state", null))
        assertFalse(model().state.value.loading)
    }
}
