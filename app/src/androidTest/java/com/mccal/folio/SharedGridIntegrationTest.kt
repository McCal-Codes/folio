package com.mccal.folio

import android.app.Application
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.SharedPreferences
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.pressBack
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** End-to-end acceptance coverage for the shared app/widget coordinate space. */
class SharedGridIntegrationTest {
    val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules = org.junit.rules.RuleChain.outerRule(WithoutNativeFeed()).around(compose)

    private fun model() = ViewModelProvider(compose.activity)[LauncherModel::class.java]
    private fun ready() = compose.waitUntil(15_000) { !model().state.value.loading }
    private fun root() = compose.onNodeWithTag("launcher-root")
    private fun center(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.center
    private fun begin(tag: String) {
        val start = center(tag)
        root().performTouchInput { down(start); advanceEventTime(700); moveTo(start + Offset(4f, 0f)) }
        compose.waitForIdle()
    }
    private fun drop(tag: String) {
        root().performTouchInput { moveTo(center(tag), 300); up() }
        compose.waitForIdle()
    }
    private fun undo() {
        compose.openHomeCustomization()
        compose.onNodeWithText("Undo last layout change").performClick()
        compose.waitForIdle()
    }

    @Test fun emptySpaceShowsTheFullCatalogAndAppActionsFilterProvidersByPackage() {
        ready(); val before = model().state.value.layout
        val manager = AppWidgetManager.getInstance(compose.activity)
        val hostIdsBefore = (MainActivity::class.java.getDeclaredField("widgets").apply { isAccessible = true }
            .get(compose.activity) as WidgetController).host.appWidgetIds.toSet()
        try {
            compose.runOnIdle { model().removePlacement(DropTarget.Widget(0)) }
            val catalogBaseline = model().state.value.layout
            compose.onNodeWithTag("home-cell-0").performTouchInput { longClick() }
            compose.onNodeWithText("Add to Home").assertIsDisplayed()
            compose.onNodeWithTag("empty-space-widgets").performClick()
            compose.onNodeWithTag("visual-widget-picker").assertIsDisplayed()
            compose.onNodeWithText("Widgets").assertIsDisplayed()
            compose.onNode(hasText("Search widgets") and hasSetTextAction()).assertIsDisplayed()
            compose.onNodeWithTag("widget-builtin-$CLOCK_WIDGET").assertIsDisplayed()
                .assertTextContains("Clock")
            val allProviders = manager.installedProviders.filter { it.profile == android.os.Process.myUserHandle() }
            assertTrue("Emulator fixture needs an Android widget provider", allProviders.isNotEmpty())
            val visibleProvider = allProviders.first { it.provider.className.endsWith("AnalogAppWidgetProvider") }
            val visibleProviderTag = "widget-provider-${visibleProvider.provider.flattenToString()}"
            compose.onNodeWithTag("widget-catalog-list", useUnmergedTree = true)
                .performScrollToNode(hasTestTag(visibleProviderTag))
            compose.onNodeWithTag(visibleProviderTag, useUnmergedTree = true).assertIsDisplayed()
            compose.onNodeWithTag(visibleProviderTag, useUnmergedTree = true).performClick()
            compose.continueToPlacement()
            compose.onNodeWithTag("widget-placement-mode").assertIsDisplayed()
            pressBack()
            compose.onNodeWithTag("visual-widget-picker").assertIsDisplayed()
            compose.onNodeWithTag("widget-placement-mode").assertDoesNotExist()
            pressBack()
            compose.onNodeWithTag("visual-widget-picker").assertDoesNotExist()
            assertEquals(catalogBaseline, model().state.value.layout)
            assertEquals(hostIdsBefore, (MainActivity::class.java.getDeclaredField("widgets").apply { isAccessible = true }
                .get(compose.activity) as WidgetController).host.appWidgetIds.toSet())

            val provider = allProviders.firstOrNull { candidate ->
                model().state.value.apps.any { ComponentName.unflattenFromString(it.id)?.packageName == candidate.provider.packageName }
            }
            assertNotNull("Fixture needs a launchable app that also exposes widgets", provider)
            val packageProvider = requireNotNull(provider)
            val app = model().state.value.apps.first {
                ComponentName.unflattenFromString(it.id)?.packageName == packageProvider.provider.packageName
            }
            compose.runOnIdle { model().applyDrop(app.id, DropTarget.Home(8)) }
            compose.onNodeWithTag("home-app-${app.id}").performTouchInput { longClick() }
            compose.onNodeWithText("Widgets").performClick()
            compose.onNodeWithTag("visual-widget-picker").assertIsDisplayed()
            manager.installedProviders.filter { it.profile == android.os.Process.myUserHandle() }.forEach { candidate ->
                val tag = "widget-provider-${candidate.provider.flattenToString()}"
                if (candidate.provider.packageName == packageProvider.provider.packageName) {
                    compose.onNodeWithTag("widget-catalog-list", useUnmergedTree = true)
                        .performScrollToNode(hasTestTag(tag))
                    compose.onNodeWithTag(tag, useUnmergedTree = true).assertExists()
                } else compose.onNodeWithTag(tag, useUnmergedTree = true).assertDoesNotExist()
            }
        } finally { compose.runOnIdle { model().restoreLayout(before) } }
    }

    @Test fun appUsesAFreedTopCellAndWidgetCollisionRejectsThenUndoRestoresMove() {
        ready(); val before = model().state.value.layout
        try {
            val firstPageIds = before.slots.take(HOME_CELLS).filterNotNull().toSet()
            val app = model().state.value.apps.first { it.id in firstPageIds }
            compose.runOnIdle { model().removePlacement(DropTarget.Widget(0)) }
            val freed = model().state.value.layout
            val source = freed.slots.indexOf(app.id)
            begin("home-cell-$source"); drop("home-cell-0")
            assertEquals(app.id, model().state.value.homeSlots[0])
            undo()
            assertEquals(freed, model().state.value.layout)

            val first = freed.placement(1) ?: error("Fixture needs the second migrated widget")
            begin("widget-slot-${first.slot}"); drop("home-cell-$source")
            assertEquals("An app collision must reject the widget move", freed, model().state.value.layout)
            compose.runOnIdle { model().applyDrop(app.id, DropTarget.Home(12)) }
            val movable = model().state.value.layout
            begin("widget-slot-${first.slot}"); drop("home-cell-0")
            assertEquals(first.copy(column = 0, row = 0), model().placement(first.slot))
            undo()
            assertEquals(movable, model().state.value.layout)
        } finally { compose.runOnIdle { model().restoreLayout(before) } }
    }

    @Test fun realWidgetResizesReportsSizeSurvivesRecreationAndRemoveUndoKeepsBinding() {
        check(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        ready(); val before = model().state.value.layout
        val host = AppWidgetHost(compose.activity, 1024)
        val manager = AppWidgetManager.getInstance(compose.activity)
        val provider = manager.installedProviders.first { it.provider.className.endsWith("AnalogAppWidgetProvider") }
        val id = host.allocateAppWidgetId()
        val slot = model().nextWidgetSlot()
        try {
            assertTrue(manager.bindAppWidgetIdIfAllowed(id, provider.provider))
            compose.runOnIdle { model().placeWidget(WidgetPlacement(slot, id, 1, 0, 0, 1, 2)) }
            compose.onNodeWithContentDescription("Home page 2").performClick()
            compose.waitForIdle()
            compose.onNodeWithTag("widget-slot-$slot").performTouchInput { longClick() }
            compose.onNodeWithText("Resize on Home").assertIsDisplayed()
            pressBack()
            compose.onNodeWithText("Resize on Home").assertDoesNotExist()
            compose.onNodeWithTag("widget-slot-$slot").performTouchInput { longClick() }
            compose.onNodeWithText("Resize on Home").assertIsDisplayed()
            compose.onNodeWithText("Resize on Home").performClick()
            compose.onNodeWithTag("widget-resize-preview-$slot").assertIsDisplayed()
            compose.onNodeWithTag("widget-resize-handle-$slot").performTouchInput {
                down(center); moveBy(Offset(width * 2f, 0f), 400); up()
            }
            compose.onNodeWithText("Apply").performClick()
            val resized = requireNotNull(model().placement(slot))
            assertTrue("Dragging the handle increases the widget width", resized.spanX > 1)
            val options = manager.getAppWidgetOptions(id)
            assertTrue(options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) > 0)

            compose.activityRule.scenario.recreate(); ready()
            assertEquals(resized, model().placement(slot))
            assertEquals(provider.provider, manager.getAppWidgetInfo(id).provider)
            begin("widget-slot-$slot")
            root().performTouchInput { moveBy(Offset(60f, 40f), 200) }
            drop("remove-drop-target")
            assertNull(model().placement(slot))
            undo()
            assertEquals(id, model().placement(slot)?.id)
            assertEquals(provider.provider, manager.getAppWidgetInfo(id).provider)
        } finally {
            compose.runOnIdle { model().restoreLayout(before) }
            host.deleteAppWidgetId(id)
        }
    }

    @Test fun nativeWidgetUsesTheExactEmptyAnchorInsteadOfSearchingElsewhere() {
        check(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        ready(); val before = model().state.value.layout
        val manager = AppWidgetManager.getInstance(compose.activity)
        val controller = MainActivity::class.java.getDeclaredField("widgets").apply { isAccessible = true }
            .get(compose.activity) as WidgetController
        val provider = manager.installedProviders.filter {
            it.configure == null && it.profile == android.os.Process.myUserHandle() &&
                it.provider.packageName == "com.google.android.deskclock"
        }.minBy { it.minWidth.toLong() * it.minHeight }
        val idsBefore = controller.host.appWidgetIds.toSet()
        val slot = model().nextWidgetSlot()
        var createdId = -1
        try {
            val pageAnchor = model().state.value.apps.first { it.id !in before.dock }.id
            compose.runOnIdle { model().applyDrop(pageAnchor, DropTarget.Home(47)) }
            compose.onNodeWithContentDescription("Home page 2").performClick()
            compose.waitForIdle()
            compose.onNodeWithTag("home-cell-24").performTouchInput { longClick() }
            compose.onNodeWithTag("empty-space-widgets").performClick()
            compose.onNode(hasText("Search widgets") and hasSetTextAction())
                .performTextInput(provider.loadLabel(compose.activity.packageManager).toString())
            compose.onNodeWithTag("widget-provider-${provider.provider.flattenToString()}").performScrollTo().performClick()
            compose.continueToPlacement()
            compose.onNodeWithTag("widget-placement-preview").assertIsDisplayed().assert(
                SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "Ready to place"))
            compose.onNodeWithTag("widget-placement-apply").performClick()
            compose.waitUntil(10_000) { model().placement(slot)?.id?.let { it >= 0 } == true }
            val placed = requireNotNull(model().placement(slot))
            createdId = placed.id
            assertEquals("The requested cell remains the widget's anchor", 24,
                placed.page * HOME_CELLS + placed.row * GRID_COLUMNS + placed.column)
            assertEquals(provider.provider, manager.getAppWidgetInfo(placed.id)?.provider)
        } finally {
            compose.runOnIdle { model().restoreLayout(before) }
            if (createdId >= 0 && createdId !in idsBefore) controller.host.deleteAppWidgetId(createdId)
            compose.waitUntil(5_000) { controller.host.appWidgetIds.toSet() == idsBefore }
            assertEquals(idsBefore, controller.host.appWidgetIds.toSet())
        }
    }

    @Test fun cancelingFirstRowResizeKeepsTheLayoutAndPagerAliveAcrossRecreation() {
        ready(); val before = model().state.value.layout
        try {
            val pageAnchor = model().state.value.apps.first { it.id !in before.dock }.id
            compose.runOnIdle { model().applyDrop(pageAnchor, DropTarget.Home(HOME_CELLS)) }
            val arranged = model().state.value.layout
            val widget = requireNotNull(arranged.placement(0))
            compose.onNodeWithTag("widget-slot-${widget.slot}").performTouchInput { longClick() }
            compose.onNodeWithText("Apply").assertIsDisplayed()
            compose.onNodeWithText("Resize on Home").performClick()
            compose.onNodeWithTag("widget-resize-preview-${widget.slot}").assertIsDisplayed()
            compose.onNode(hasText("Cancel") and hasClickAction() and
                !hasContentDescription("Cancel widget setup")).performClick()
            assertEquals(arranged, model().state.value.layout)

            compose.onNodeWithTag("app-pager").performTouchInput { swipeLeft() }
            compose.onNodeWithTag("app-pager").assert(
                SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "Home page 2 of 2"))
            compose.activityRule.scenario.recreate(); ready()
            compose.onNodeWithTag("app-pager").performTouchInput { swipeRight() }
            compose.onNodeWithTag("app-pager").assert(
                SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "Home page 1 of 2"))
            assertEquals(arranged, model().state.value.layout)
        } finally { compose.runOnIdle { model().restoreLayout(before) } }
    }

    @Test fun settingsManagesAWidgetByItsCurrentPageAndStableSlot() {
        ready(); val before = model().state.value.layout
        try {
            val moved = requireNotNull(before.placement(0)).copy(page = 1, column = 0, row = 0)
            compose.runOnIdle { assertTrue(model().placeWidget(moved)) }
            compose.onNodeWithContentDescription("Home page 2").performClick()
            compose.waitForIdle()
            compose.openHomeCustomization()
            compose.onNodeWithTag("customization-home").performClick()
            compose.onNodeWithText("Widgets · Page 2").performScrollTo().assertIsDisplayed()
            compose.onNodeWithContentDescription("Remove widget", useUnmergedTree = true)
                .performScrollTo().assertIsDisplayed().performClick()
            compose.waitUntil(4_000) { model().placement(moved.slot) == null }
            assertNull("Settings removes the placement displayed on the current page", model().placement(moved.slot))
            assertNotNull("A different stable slot on page 1 is untouched", model().placement(1))
            compose.runOnIdle { assertTrue(model().undoEdit()) }
            assertEquals(moved, model().placement(moved.slot))
        } finally { compose.runOnIdle { model().restoreLayout(before) } }
    }

    @Test fun folderAnchorAndMembersSurviveAuthoritativeRefreshAndActivityRecreation() {
        ready(); val before = model().state.value.layout
        try {
            val apps = model().state.value.apps.filter { it.id !in before.dock }.take(2)
            assertEquals(2, apps.size)
            compose.runOnIdle {
                assertTrue(model().applyDrop(apps[0].id, DropTarget.Home(24)))
                assertTrue(model().applyDrop(apps[1].id, DropTarget.Home(25)))
            }
            val folderId = compose.runOnIdle {
                requireNotNull(model().createFolder(apps[0].id, apps[1].id, 24, "Weather"))
            }
            val arranged = model().state.value.layout
            assertEquals(folderId, arranged.slots[24])
            assertEquals(listOf(apps[0].id, apps[1].id), arranged.folder(folderId)?.appIds)

            compose.runOnIdle { model().refresh() }
            compose.waitUntil(15_000) { !model().state.value.loading }
            assertEquals("Refresh must retain the folder as a Home entity", folderId, model().state.value.homeSlots[24])
            assertEquals(arranged.folder(folderId), model().state.value.layout.folder(folderId))

            compose.activityRule.scenario.recreate(); ready()
            assertEquals(folderId, model().state.value.homeSlots[24])
            assertEquals(arranged.folder(folderId), model().state.value.layout.folder(folderId))
        } finally { compose.runOnIdle { model().restoreLayout(before) } }
    }

    @Test fun schema5MigratesBacksUpAndRoundTripsWithExactCoordinates() {
        ready()
        val main = model()
        val before = main.state.value.layout
        val prefs = compose.activity.getSharedPreferences("launcher", 0)
        val original = prefs.all.toMap()
        val apps = main.state.value.apps.filter { it.id !in main.state.value.dock }.take(17)
        assertEquals("Migration fixture needs 17 installed apps", 17, apps.size)
        val legacySlots = JSONArray().apply { apps.forEach { put(it.id) } }
        val legacy = JSONObject().put("schema", 5).put("pinned", JSONArray(apps.map { it.id }))
            .put("homeSlots", legacySlots).put("dock", JSONArray(before.dock))
            .put("widgets", JSONArray(listOf(CLOCK_WIDGET, DATE_WIDGET, 9)))
            .put("labels", false).put("googleSearch", false).put("verticalStatus", false).toString()
        var firstStore: ViewModelStore? = null
        var secondStore: ViewModelStore? = null
        try {
            prefs.edit().putString("state", legacy).remove("state_v5_backup").remove("state_v6_backup").remove("state_v7_backup")
                .putBoolean("initialized", true).commit()
            val app = ApplicationProvider.getApplicationContext<Application>()
            val migrationStore = ViewModelStore()
            firstStore = migrationStore
            val factory = ViewModelProvider.AndroidViewModelFactory.getInstance(app)
            lateinit var migrated: LauncherModel
            compose.runOnIdle { migrated = ViewModelProvider(migrationStore, factory)["schema5-migration", LauncherModel::class.java] }
            compose.waitUntil(15_000) { !migrated.state.value.loading && JSONObject(prefs.getString("state", "{}")!!).optInt("schema") == 8 }
            val expectedSlots = normalizeHomeSlots(migrateSchema5Apps(apps.map { it.id }))
            assertEquals(expectedSlots, migrated.state.value.homeSlots)
            assertEquals(listOf(
                WidgetPlacement(0, CLOCK_WIDGET, 0, 0, 0, 2, 2),
                WidgetPlacement(1, DATE_WIDGET, 0, 2, 0, 2, 2),
                WidgetPlacement(2, 9, -1, 0, 0, 4, 6),
            ), migrated.state.value.widgetPlacements)
            assertTrue(migrated.state.value.folders.isEmpty())
            assertEquals(legacy, prefs.getString("state_v5_backup", null))
            assertEquals(legacy, prefs.getString("state_v6_backup", null))
            assertEquals(legacy, prefs.getString("state_v7_backup", null))
            assertEquals(List(HOME_CELLS) { null }, migrated.state.value.leadingSlots)
            val migratedLayout = migrated.state.value.layout
            firstStore.clear(); firstStore = null

            val roundTripStore = ViewModelStore()
            secondStore = roundTripStore
            lateinit var roundTripped: LauncherModel
            compose.runOnIdle { roundTripped = ViewModelProvider(roundTripStore, factory)["schema8-roundtrip", LauncherModel::class.java] }
            compose.waitUntil(15_000) { !roundTripped.state.value.loading }
            assertEquals(migratedLayout, roundTripped.state.value.layout)
            assertEquals(9, roundTripped.placement(2)?.id)
        } finally {
            firstStore?.clear(); secondStore?.clear()
            compose.runOnIdle { main.restoreLayout(before) }
            restorePreferences(prefs, original)
        }
    }

    @Test fun schema7MigrationCreatesEmptyLeadingGridRemovesOnlyExactDemoAndRetainsRealLeadingWidget() {
        ready()
        val prefs = compose.activity.getSharedPreferences("launcher", 0)
        val original = prefs.all.toMap()
        val main = model()
        val before = main.state.value.layout
        val controller = MainActivity::class.java.getDeclaredField("widgets").apply { isAccessible = true }
            .get(compose.activity) as WidgetController
        val retainedIdOrNull = controller.host.appWidgetIds.firstOrNull { controller.manager.getAppWidgetInfo(it) != null }
        assumeTrue("Migration fixture needs an actual bound provider widget", retainedIdOrNull != null)
        val retainedId = requireNotNull(retainedIdOrNull)
        val retainedProvider = requireNotNull(controller.manager.getAppWidgetInfo(retainedId)).provider
        val app = main.state.value.apps.first { it.id !in before.dock }
        val widgets = JSONArray()
            .put(JSONObject().put("slot", 2).put("id", INFO_WIDGET).put("page", -1).put("column", 0)
                .put("row", 0).put("spanX", 4).put("spanY", 6))
            .put(JSONObject().put("slot", 8).put("id", retainedId).put("page", -1).put("column", 1)
                .put("row", 1).put("spanX", 2).put("spanY", 2))
            .put(JSONObject().put("slot", 9).put("id", DATE_WIDGET).put("page", -1).put("column", 3)
                .put("row", 5).put("spanX", 1).put("spanY", 1))
        val legacy = JSONObject().put("schema", 7).put("pinned", JSONArray(listOf(app.id)))
            .put("homeSlots", JSONArray(listOf(app.id))).put("dock", JSONArray(before.dock))
            .put("widgets", widgets).put("folders", JSONArray()).put("restores", JSONArray())
            .put("labels", false).put("googleSearch", false).put("verticalStatus", false)
            .put("compact", JSONObject().put("iconSize", 61).put("rowGap", 7).put("dockWidth", 67)
                .put("dockPosition", .44).put("dockAlignToGrid", false))
            .put("expanded", JSONObject().put("iconSize", 62).put("rowGap", 9).put("dockWidth", 69)
                .put("dockPosition", .55).put("dockAlignToGrid", true)).toString()
        var store: ViewModelStore? = null
        var reloadStore: ViewModelStore? = null
        try {
            prefs.edit().putString("state", legacy).remove("state_v7_backup").putBoolean("initialized", true).commit()
            val application = ApplicationProvider.getApplicationContext<Application>()
            store = ViewModelStore()
            val factory = ViewModelProvider.AndroidViewModelFactory.getInstance(application)
            lateinit var migrated: LauncherModel
            compose.runOnIdle { migrated = ViewModelProvider(store!!, factory)["schema7-leading", LauncherModel::class.java] }
            compose.waitUntil(15_000) { !migrated.state.value.loading &&
                JSONObject(prefs.getString("state", "{}")!!).optInt("schema") == 8 }
            assertEquals(List(HOME_CELLS) { null }, migrated.state.value.leadingSlots)
            assertNull(migrated.placement(2))
            assertEquals(retainedId, migrated.placement(8)?.id)
            assertEquals(WidgetPlacement(9, DATE_WIDGET, -1, 3, 5, 1, 1), migrated.placement(9))
            assertTrue(retainedId in migrated.retainedWidgetIds)
            assertEquals(listOf(app.id), migrated.state.value.homeSlots)
            assertEquals(before.dock, migrated.state.value.dock)
            assertFalse(migrated.state.value.labels)
            assertFalse(migrated.state.value.googleSearch)
            assertFalse(migrated.state.value.verticalStatus)
            assertEquals(legacy, prefs.getString("state_v7_backup", null))
            assertEquals(retainedProvider, controller.manager.getAppWidgetInfo(retainedId)?.provider)

            val migratedLayout = migrated.state.value.layout
            val migratedCompact = migrated.state.value.compact
            val migratedExpanded = migrated.state.value.expanded
            compose.runOnIdle { store?.clear(); store = null }
            reloadStore = ViewModelStore()
            lateinit var reloaded: LauncherModel
            compose.runOnIdle { reloaded = ViewModelProvider(reloadStore!!, factory)["schema8-leading-reload", LauncherModel::class.java] }
            compose.waitUntil(15_000) { !reloaded.state.value.loading }
            assertEquals(migratedLayout, reloaded.state.value.layout)
            assertEquals(migratedCompact, reloaded.state.value.compact)
            assertEquals(migratedExpanded, reloaded.state.value.expanded)
            assertEquals(retainedProvider, controller.manager.getAppWidgetInfo(retainedId)?.provider)
        } finally {
            compose.runOnIdle { store?.clear(); reloadStore?.clear() }
            compose.runOnIdle { main.restoreLayout(before) }
            restorePreferences(prefs, original)
            assertEquals(retainedProvider, controller.manager.getAppWidgetInfo(retainedId)?.provider)
        }
    }

    @Test fun widgetControllerRestoresOnlyCompleteTransactionsAndNeverDeletesRetainedIds() {
        ready()
        val field = MainActivity::class.java.getDeclaredField("widgets").apply { isAccessible = true }
        val controller = field.get(compose.activity) as WidgetController
        val retainedId = model().state.value.widgetPlacements.first { it.id >= 0 }.id
        val retainedProvider = controller.manager.getAppWidgetInfo(retainedId)?.provider
        fun placement(slot: Int, id: Int) = intArrayOf(slot, id, 1, 0, 0, 2, 2)
        fun savedAfter(bundle: android.os.Bundle): android.os.Bundle {
            controller.restore(bundle)
            return android.os.Bundle().also(controller::save)
        }
        fun assertCleared(saved: android.os.Bundle) {
            assertEquals(-1, saved.getInt("pendingWidget", -1))
            assertFalse(saved.containsKey("pendingWidgetPlacement"))
            assertFalse(saved.containsKey("pendingWidgetOriginal"))
        }

        val missingPlacementId = controller.host.allocateAppWidgetId()
        assertCleared(savedAfter(android.os.Bundle().apply { putInt("pendingWidget", missingPlacementId) }))
        assertFalse(missingPlacementId in controller.host.appWidgetIds)

        val malformedPlacementId = controller.host.allocateAppWidgetId()
        assertCleared(savedAfter(android.os.Bundle().apply {
            putInt("pendingWidget", malformedPlacementId)
            putIntArray("pendingWidgetPlacement", intArrayOf(1, 2, 3))
        }))
        assertFalse(malformedPlacementId in controller.host.appWidgetIds)

        assertCleared(savedAfter(android.os.Bundle().apply { putInt("pendingWidget", retainedId) }))
        assertTrue("Malformed restore must retain Home's real widget ID", retainedId in controller.host.appWidgetIds)
        assertEquals(retainedProvider, controller.manager.getAppWidgetInfo(retainedId)?.provider)

        val mismatchId = controller.host.allocateAppWidgetId()
        assertCleared(savedAfter(android.os.Bundle().apply {
            putInt("pendingWidget", mismatchId)
            putIntArray("pendingWidgetPlacement", placement(80, mismatchId + 1))
        }))
        assertFalse(mismatchId in controller.host.appWidgetIds)

        assertCleared(savedAfter(android.os.Bundle().apply {
            putIntArray("pendingWidgetPlacement", placement(81, 81))
        }))

        val newId = controller.host.allocateAppWidgetId()
        val profile = requireNotNull(controller.manager.getAppWidgetInfo(retainedId)).profile
        assertTrue(controller.manager.bindAppWidgetIdIfAllowed(newId, profile, requireNotNull(retainedProvider), null))
        val legitimateNew = savedAfter(android.os.Bundle().apply {
            putInt("pendingWidget", newId)
            putIntArray("pendingWidgetPlacement", placement(82, newId))
            putString("pendingWidgetProvider", requireNotNull(retainedProvider).flattenToString())
            putLong("pendingWidgetProfileSerial", compose.activity.getSystemService(android.os.UserManager::class.java)
                .getSerialNumberForUser(profile))
            putString("pendingWidgetStatus", WidgetSetupStatus.CONFIGURING.name)
        })
        assertEquals(newId, legitimateNew.getInt("pendingWidget"))
        assertArrayEquals(placement(82, newId), legitimateNew.getIntArray("pendingWidgetPlacement"))
        assertFalse(legitimateNew.containsKey("pendingWidgetOriginal"))
        controller.restore(null)
        val restoredNew = android.os.Bundle().also(controller::save)
        assertEquals(newId, restoredNew.getInt("pendingWidget"))
        assertTrue(newId in controller.host.appWidgetIds)
        controller.cancelPendingSetup()
        assertFalse(newId in controller.host.appWidgetIds)

        val original = model().state.value.widgetPlacements.first()
        val replacementId = controller.host.allocateAppWidgetId()
        assertTrue(controller.manager.bindAppWidgetIdIfAllowed(replacementId, profile,
            requireNotNull(retainedProvider), null))
        val legitimateReplacement = savedAfter(android.os.Bundle().apply {
            putInt("pendingWidget", replacementId)
            putIntArray("pendingWidgetPlacement", placement(original.slot, replacementId))
            putIntArray("pendingWidgetOriginal", intArrayOf(original.slot, original.id, original.page,
                original.column, original.row, original.spanX, original.spanY))
            putString("pendingWidgetProvider", requireNotNull(retainedProvider).flattenToString())
            putLong("pendingWidgetProfileSerial", compose.activity.getSystemService(android.os.UserManager::class.java)
                .getSerialNumberForUser(profile))
            putString("pendingWidgetStatus", WidgetSetupStatus.CONFIGURING.name)
        })
        assertEquals(replacementId, legitimateReplacement.getInt("pendingWidget"))
        assertTrue(legitimateReplacement.containsKey("pendingWidgetOriginal"))
        controller.restore(null)
        assertEquals(replacementId, android.os.Bundle().also(controller::save).getInt("pendingWidget"))
        controller.cancelPendingSetup()
        assertFalse(replacementId in controller.host.appWidgetIds)

        val malformedOriginalId = controller.host.allocateAppWidgetId()
        assertTrue(controller.manager.bindAppWidgetIdIfAllowed(malformedOriginalId, profile,
            requireNotNull(retainedProvider), null))
        assertCleared(savedAfter(android.os.Bundle().apply {
            putInt("pendingWidget", malformedOriginalId)
            putIntArray("pendingWidgetPlacement", placement(original.slot, malformedOriginalId))
            putIntArray("pendingWidgetOriginal", intArrayOf(original.slot, original.id))
            putString("pendingWidgetProvider", requireNotNull(retainedProvider).flattenToString())
            putLong("pendingWidgetProfileSerial", compose.activity.getSystemService(android.os.UserManager::class.java)
                .getSerialNumberForUser(profile))
            putString("pendingWidgetStatus", WidgetSetupStatus.CONFIGURING.name)
        }))
        assertFalse(malformedOriginalId in controller.host.appWidgetIds)
        assertEquals(retainedProvider, controller.manager.getAppWidgetInfo(retainedId)?.provider)
    }

    @Test fun malformedSavedRecordsFailClosedWhileSchema6UpgradesAndSchema7FoldersRoundTrip() {
        ready()
        val prefs = compose.activity.getSharedPreferences("launcher", 0)
        val original = prefs.all.toMap()
        val app = ApplicationProvider.getApplicationContext<Application>()
        val factory = ViewModelProvider.AndroidViewModelFactory.getInstance(app)
        fun base(): JSONObject = JSONObject().put("schema", 6).put("pinned", JSONArray())
            .put("homeSlots", JSONArray()).put("dock", JSONArray(listOf(null, null, null, null)))
        fun widget(slot: Int = 0, id: Int = CLOCK_WIDGET, page: Int = 0, column: Int = 0,
            row: Int = 0, spanX: Int = 2, spanY: Int = 2) = JSONObject()
            .put("slot", slot).put("id", id).put("page", page).put("column", column).put("row", row)
            .put("spanX", spanX).put("spanY", spanY)
        fun leading(vararg entries: Pair<Int, String>): JSONArray = JSONArray(
            MutableList<Any?>(HOME_CELLS) { JSONObject.NULL }.apply {
                entries.forEach { (index, id) -> this[index] = id }
            })
        fun load(raw: String, key: String): Pair<LauncherModel, ViewModelStore> {
            val store = ViewModelStore()
            lateinit var candidate: LauncherModel
            compose.runOnIdle {
                assertTrue(prefs.edit().putString("state", raw).putBoolean("initialized", true).commit())
                candidate = ViewModelProvider(store, factory).get(key, LauncherModel::class.java)
            }
            return candidate to store
        }

        val invalid = listOf(
            base(),
            base().put("widgets", "not-an-array"),
            base().put("widgets", JSONArray().put("not-an-object")),
            base().put("widgets", JSONArray().put(widget()).put(widget())),
            base().put("widgets", JSONArray().put(widget(id = EMPTY_WIDGET))),
            base().put("widgets", JSONArray().put(widget(slot = -1))),
            base().put("widgets", JSONArray().put(widget(page = -2))),
            base().put("widgets", JSONArray().put(widget(spanX = 0))),
            base().put("widgets", JSONArray().put(widget(spanY = 7))),
            base().put("widgets", JSONArray().put(widget(column = 3, spanX = 2))),
            base().put("widgets", JSONArray().put(widget(row = 5, spanY = 2))),
            base().put("widgets", JSONArray().put(widget(slot = 4, page = 1, row = 6, spanX = 4, spanY = 4))),
            base().put("widgets", JSONArray().put(widget(slot = 5, page = 0, row = 6, spanX = 4, spanY = 4))),
            base().put("widgets", JSONArray().put(widget(slot = 5, page = 1, column = 1, row = 6, spanX = 3, spanY = 4))),
            base().put("schema", 9).put("widgets", JSONArray()),
            base().put("schema", 8).put("widgets", JSONArray()).put("folders", JSONArray()).put("leadingSlots", JSONArray()),
            base().put("schema", 8).put("widgets", JSONArray()).put("folders", JSONArray())
                .put("leadingSlots", leading(0 to "duplicate", 1 to "duplicate")),
            base().put("schema", 8).put("widgets", JSONArray()).put("folders", JSONArray())
                .put("homeSlots", JSONArray().put("cross-surface"))
                .put("leadingSlots", leading(0 to "cross-surface")),
            base().put("schema", 8).put("widgets", JSONArray()).put("folders", JSONArray())
                .put("dock", JSONArray(listOf("cross-dock", null, null, null)))
                .put("leadingSlots", leading(0 to "cross-dock")),
            base().put("schema", 8).put("widgets", JSONArray().put(widget(page = -1, spanX = 2, spanY = 2)))
                .put("folders", JSONArray()).put("leadingSlots", leading(0 to "widget-overlap")),
            base().put("schema", 7).put("widgets", JSONArray()),
            base().put("schema", 7).put("widgets", JSONArray()).put("folders", "not-an-array"),
            base().put("schema", 7).put("widgets", JSONArray()).put("folders", JSONArray().put("not-an-object")),
            base().put("schema", 7).put("widgets", JSONArray()).put("homeSlots", JSONArray().put("folder:not-a-uuid"))
                .put("folders", JSONArray().put(JSONObject().put("id", "folder:not-a-uuid").put("title", "Bad")
                    .put("apps", JSONArray(listOf("one", "two"))))),
        ).map(JSONObject::toString)
        try {
            invalid.forEachIndexed { index, raw ->
                val (candidate, store) = load(raw, "invalid-schema6-$index")
                try {
                    assertNotNull("Case $index must expose a load error", candidate.state.value.error)
                    assertFalse("Case $index must disable widget-ID pruning", candidate.canPruneWidgetIds)
                    assertEquals("Case $index must not rewrite the raw payload", raw, prefs.getString("state", null))
                } finally { store.clear() }
            }

            val positive = listOf(
                base().put("widgets", JSONArray()) to emptyList(),
                base().put("widgets", JSONArray().put(widget(slot = 2, id = 9, page = -1, spanX = 4, spanY = 6))) to
                    listOf(WidgetPlacement(2, 9, -1, 0, 0, 4, 6)),
                base().put("widgets", JSONArray().put(widget(slot = 5, id = 9, page = 1, row = 6, spanX = 4, spanY = 4))) to
                    listOf(WidgetPlacement(5, 9, 1, 0, 6, 4, 4)),
            )
            positive.forEachIndexed { index, (json, placements) ->
                if (index == 0) prefs.edit().remove("state_v6_backup").commit()
                val raw = json.toString()
                val (candidate, store) = load(raw, "valid-schema6-$index")
                try {
                    compose.waitUntil(15_000) { !candidate.state.value.loading &&
                        JSONObject(prefs.getString("state", "{}")!!).optInt("schema") == 8 }
                    assertNull(candidate.state.value.error)
                    assertTrue(candidate.canPruneWidgetIds)
                    assertEquals(placements, candidate.state.value.widgetPlacements)
                    assertTrue(candidate.state.value.folders.isEmpty())
                    assertEquals(0, JSONObject(prefs.getString("state", "{}")!!).getJSONArray("folders").length())
                    if (index == 0) assertEquals(raw, prefs.getString("state_v6_backup", null))
                } finally { store.clear() }
            }

            val installed = model().state.value.apps.take(2).map { it.id }
            assertEquals(2, installed.size)
            val folderId = "folder:00000000-0000-0000-0000-000000000007"
            val folderJson = base().put("schema", 7).put("widgets", JSONArray())
                .put("homeSlots", JSONArray().put(folderId)).put("folders", JSONArray().put(JSONObject()
                    .put("id", folderId).put("title", "Weather").put("apps", JSONArray(installed))))
            val (folderModel, folderStore) = load(folderJson.toString(), "valid-schema7-folder")
            try {
                compose.waitUntil(15_000) { !folderModel.state.value.loading }
                assertNull(folderModel.state.value.error)
                assertEquals(listOf(FolderEntry(folderId, "Weather", installed)), folderModel.state.value.folders)
                assertEquals(folderId, folderModel.state.value.homeSlots.first())
            } finally { folderStore.clear() }
        } finally { restorePreferences(prefs, original) }
    }

    private fun restorePreferences(prefs: SharedPreferences, values: Map<String, *>) {
        val editor = prefs.edit().clear()
        values.forEach { (key, value) -> when (value) {
            is String -> editor.putString(key, value)
            is Boolean -> editor.putBoolean(key, value)
            is Int -> editor.putInt(key, value)
            is Long -> editor.putLong(key, value)
            is Float -> editor.putFloat(key, value)
            is Set<*> -> @Suppress("UNCHECKED_CAST") editor.putStringSet(key, value as Set<String>)
        } }
        assertTrue("Original launcher preferences restore synchronously", editor.commit())
    }
}
