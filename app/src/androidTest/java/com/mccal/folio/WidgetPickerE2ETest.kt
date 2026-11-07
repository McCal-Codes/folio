package com.mccal.folio

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry
import org.hamcrest.CoreMatchers.containsString
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Public AppWidgetManager coverage backed by providers installed in the instrumentation APK. */
class WidgetPickerE2ETest {
    val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules = org.junit.rules.RuleChain.outerRule(WithoutNativeFeed()).around(compose)

    private fun model() = ViewModelProvider(compose.activity)[LauncherModel::class.java]
    private fun controller() = MainActivity::class.java.getDeclaredField("widgets").apply { isAccessible = true }
        .get(compose.activity) as WidgetController
    private fun ready() = compose.waitUntil(15_000) { !model().state.value.loading }
    private fun provider(simpleName: String) = controller().personalProviders().single {
        it.provider.packageName == FolioTestPackages.test && it.provider.className.endsWith(simpleName)
    }
    private fun providerTag(simpleName: String) = "widget-provider-${provider(simpleName).provider.flattenToString()}"
    private fun previewTag(simpleName: String) = "widget-preview-${provider(simpleName).provider.flattenToString()}"

    private fun clickExternalActivity(text: String) {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        var target: android.view.accessibility.AccessibilityNodeInfo? = null
        compose.waitUntil(10_000) {
            fun find(node: android.view.accessibility.AccessibilityNodeInfo?): android.view.accessibility.AccessibilityNodeInfo? {
                if (node == null) return null
                if (node.text?.toString() == text || node.contentDescription?.toString() == text) return node
                for (index in 0 until node.childCount) find(node.getChild(index))?.let { return it }
                return null
            }
            target = automation.windows.firstNotNullOfOrNull { find(it.root) }
            target?.isVisibleToUser == true
        }
        var clickable = target
        while (clickable != null && !clickable!!.isClickable) clickable = clickable!!.parent
        assertTrue("Could not click external activity action $text",
            requireNotNull(clickable).performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK))
    }

    private fun injectTouch(action: Int, position: Offset, downTime: Long) {
        val event = android.view.MotionEvent.obtain(
            downTime, android.os.SystemClock.uptimeMillis(), action, position.x, position.y, 0)
        try {
            event.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
            check(InstrumentationRegistry.getInstrumentation().uiAutomation.injectInputEvent(event, false)) {
                "Global input injection rejected action=$action at $position"
            }
        } finally { event.recycle() }
    }

    private fun prepareEmptySecondPage() {
        val layout = model().state.value.layout
        val app = model().state.value.apps.first { it.id !in layout.dock }
        val lastVisibleCell = HOME_CELLS + visibleHomeRows(model().state.value.homeAppRows) * GRID_COLUMNS - 1
        compose.runOnIdle { assertTrue(model().applyDrop(app.id, DropTarget.Home(lastVisibleCell))) }
        compose.waitForIdle()
        // The resting strip can show Search instead of page dots. Use the ordinary paging gesture.
        compose.onNodeWithTag("app-pager").performTouchInput { swipeLeft() }
        compose.waitUntil(5_000) {
            compose.onNodeWithTag("app-pager").fetchSemanticsNode()
                .config[androidx.compose.ui.semantics.SemanticsProperties.StateDescription] == "Home page 2 of 2"
        }
        compose.waitForIdle()
        assertNull(model().state.value.homeSlots[HOME_CELLS])
    }

    private fun openCatalogAt(index: Int = HOME_CELLS) {
        if (compose.onAllNodesWithTag("jiggle-bar").fetchSemanticsNodes().isEmpty()) {
            compose.onNodeWithTag("home-cell-$index").performTouchInput { longClick() }
        }
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.add_widget)).performClick()
        compose.onNodeWithTag("visual-widget-picker").assertIsDisplayed()
        compose.onNodeWithTag("widget-catalog-search").performTextClearance()
    }

    private fun placeByTap(simpleName: String) {
        compose.onNodeWithTag("widget-catalog-search").performTextClearance()
        compose.onNodeWithTag("widget-catalog-search").performTextInput(
            provider(simpleName).loadLabel(compose.activity.packageManager).toString())
        compose.onNodeWithTag(providerTag(simpleName), useUnmergedTree = true).performScrollTo().performClick()
        compose.onNodeWithTag("widget-placement-preview")
            .assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "Ready to place"))
        compose.onNodeWithTag("widget-placement-apply").assertIsEnabled().performClick()
    }

    private fun cleanup(before: HomeLayout, idsBefore: Set<Int>) {
        compose.runOnIdle { model().restoreLayout(before) }
        controller().host.appWidgetIds.filter { it !in idsBefore }.forEach(controller().host::deleteAppWidgetId)
        assertEquals("The exact baseline widget bindings must survive", idsBefore, controller().host.appWidgetIds.toSet())
    }

    @Test fun catalogUsesAppAndWidgetNamesAndPreviewOrCanceledPlacementNeverBinds() {
        ready()
        val before = model().state.value.layout
        val idsBefore = controller().host.appWidgetIds.toSet()
        try {
            prepareEmptySecondPage()
            openCatalogAt()

            assertEquals("Widget Fixture Application", widgetCatalog(
                compose.activity,
                listOf(provider("ModernTargetWidgetProvider")),
                model().state.value.profiles.single { it.isPersonal }).single().appLabel)
            compose.onNodeWithTag("widget-catalog-search").performTextInput("Widget Fixture Application")
            compose.onNodeWithTag("widget-catalog-list")
                .performScrollToNode(hasTestTag(providerTag("ModernTargetWidgetProvider")))
            compose.onNodeWithTag(providerTag("ModernTargetWidgetProvider"), useUnmergedTree = true)
                .assertIsDisplayed()
            assertEquals("Rendering catalog previews must not allocate a host ID",
                idsBefore, controller().host.appWidgetIds.toSet())

            compose.onNodeWithTag("widget-catalog-search").performTextClearance()
            compose.onNodeWithTag("widget-catalog-search").performTextInput("Forecast Canvas")
            closeSoftKeyboard()
            compose.onNodeWithTag(providerTag("ModernTargetWidgetProvider"), useUnmergedTree = true)
                .performScrollTo().assertIsDisplayed().assertIsEnabled()
            compose.waitUntil(10_000) {
                runCatching { compose.onNodeWithTag(previewTag("ModernTargetWidgetProvider"), useUnmergedTree = true).fetchSemanticsNode() }.isSuccess
            }
            compose.onNodeWithTag("widget-catalog-list", useUnmergedTree = true)
                .performScrollToNode(hasTestTag(previewTag("ModernTargetWidgetProvider")))
            compose.onNodeWithTag(previewTag("ModernTargetWidgetProvider"), useUnmergedTree = true).assertIsDisplayed()
            val previewBounds = compose.onNodeWithTag(previewTag("ModernTargetWidgetProvider"), useUnmergedTree = true)
                .getUnclippedBoundsInRoot()
            val previewHeight = previewBounds.bottom - previewBounds.top
            val maxPreviewHeight = 240.dp
            assertTrue("Picker preview must remain bounded so its widget title and action stay reachable",
                previewHeight <= maxPreviewHeight)
            assertEquals("A 4 × 2 provider preview must retain its footprint aspect ratio",
                2f, (previewBounds.right - previewBounds.left).value / previewHeight.value, .08f)
            compose.onNodeWithText("Forecast Canvas 4 by 2").assertIsDisplayed()
            compose.onNodeWithText("${compose.activity.getString(R.string.medium)} · 4 × 2")
                .performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag(providerTag("LegacyResizableWidgetProvider"), useUnmergedTree = true)
                .assertDoesNotExist()

            compose.onNodeWithTag(providerTag("ModernTargetWidgetProvider"), useUnmergedTree = true)
                .performScrollTo().performClick()
            compose.onNodeWithTag("widget-placement-preview").assertIsDisplayed()
            compose.onNodeWithTag("widget-placement-cancel").performClick()
            assertEquals("Canceling before drop must not bind or allocate",
                idsBefore, controller().host.appWidgetIds.toSet())

            openCatalogAt()
            compose.onNodeWithTag("widget-catalog-search").performTextInput("Flexible Legacy Strip")
            compose.onNodeWithTag("widget-catalog-list", useUnmergedTree = true)
                .performScrollToNode(hasTestTag(providerTag("LegacyResizableWidgetProvider")))
            compose.onNodeWithTag(providerTag("LegacyResizableWidgetProvider"), useUnmergedTree = true)
                .assertIsDisplayed().assertIsEnabled()
            compose.onNodeWithText("Doesn’t fit this layout").assertDoesNotExist()
        } finally { cleanup(before, idsBefore) }
    }

    @Test fun requiredConfigurationCancelDeletesPendingAndSuccessCommitsRenderedWidget() {
        ready()
        val before = model().state.value.layout
        val idsBefore = controller().host.appWidgetIds.toSet()
        val manager = AppWidgetManager.getInstance(compose.activity)
        var primaryFailure: Throwable? = null
        try {
            prepareEmptySecondPage()
            val slot = model().nextWidgetSlot()
            openCatalogAt()
            placeByTap("RequiredConfigWidgetProvider")
            compose.waitUntil(5_000) { controller().pendingPlacement?.slot == slot }
            val canceledId = requireNotNull(controller().pendingPlacement).id
            assertTrue(canceledId in controller().host.appWidgetIds)
            clickExternalActivity("Cancel fixture configuration")
            compose.waitUntil(5_000) { controller().pendingPlacement == null }
            assertNull(model().placement(slot))
            assertFalse("Canceled configuration must delete its allocated ID", canceledId in controller().host.appWidgetIds)

            openCatalogAt()
            placeByTap("RequiredConfigWidgetProvider")
            compose.waitUntil(5_000) { controller().pendingPlacement?.slot == slot }
            val committedId = requireNotNull(controller().pendingPlacement).id
            clickExternalActivity("Use configured widget")
            compose.waitUntil(10_000) { model().placement(slot)?.id == committedId }
            val placed = requireNotNull(model().placement(slot))
            assertEquals(provider("RequiredConfigWidgetProvider").provider,
                manager.getAppWidgetInfo(committedId)?.provider)
            assertNull(controller().pendingPlacement)
            onView(withText(containsString("Configured fixture is live"))).check(matches(isDisplayed()))
            val options = manager.getAppWidgetOptions(committedId)
            assertTrue(options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) > 0)
            assertTrue(options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT) > 0)

            compose.activityRule.scenario.recreate(); ready()
            assertEquals(placed, model().placement(slot))
            assertEquals(provider("RequiredConfigWidgetProvider").provider,
                manager.getAppWidgetInfo(committedId)?.provider)
            onView(withText(containsString("Configured fixture is live"))).check(matches(isDisplayed()))
        } catch (failure: Throwable) {
            primaryFailure = failure
            throw failure
        } finally {
            try { cleanup(before, idsBefore) }
            catch (cleanupFailure: Throwable) {
                primaryFailure?.addSuppressed(cleanupFailure) ?: throw cleanupFailure
            }
        }
    }

    @Test fun optionalReconfigurableProviderSkipsConfigurationAndCommitsAtExactAnchor() {
        ready()
        val before = model().state.value.layout
        val idsBefore = controller().host.appWidgetIds.toSet()
        try {
            prepareEmptySecondPage()
            val slot = model().nextWidgetSlot()
            openCatalogAt()
            placeByTap("OptionalConfigWidgetProvider")
            compose.waitUntil(10_000) { model().placement(slot)?.id?.let { it >= 0 } == true }
            val placed = requireNotNull(model().placement(slot))
            assertEquals(HOME_CELLS, placed.page * HOME_CELLS + placed.row * GRID_COLUMNS + placed.column)
            assertNull("Optional + reconfigurable must not leave a configuration transaction", controller().pendingPlacement)
            assertEquals(provider("OptionalConfigWidgetProvider").provider,
                controller().manager.getAppWidgetInfo(placed.id)?.provider)
            compose.onNodeWithText("Configure fixture widget").assertDoesNotExist()

            compose.runOnIdle { assertTrue(controller().reconfigure(placed.id)) }
            clickExternalActivity("Cancel fixture configuration")
            compose.waitUntil(5_000) { controller().reconfigureWidgetId == null }
            assertEquals(placed, model().placement(slot))
            assertTrue(placed.id in controller().host.appWidgetIds)

            compose.runOnIdle { assertTrue(controller().reconfigure(placed.id)) }
            clickExternalActivity("Use configured widget")
            compose.waitUntil(5_000) { controller().reconfigureWidgetId == null }
            assertEquals(placed, model().placement(slot))
            assertTrue(placed.id in controller().host.appWidgetIds)
        } finally { cleanup(before, idsBefore) }
    }

    @Test fun cancelingPlacementOnTemporaryPageReturnsToLastPersistedHome() {
        ready()
        val before = model().state.value.layout
        val idsBefore = controller().host.appWidgetIds.toSet()
        try {
            prepareEmptySecondPage()
            val fixture = model().state.value.layout
            openCatalogAt()
            compose.onNodeWithTag("widget-catalog-search").performTextInput("Instant Conditions")
            compose.onNodeWithTag(providerTag("OptionalConfigWidgetProvider"), useUnmergedTree = true)
                .performScrollTo().performClick()
            compose.onNodeWithContentDescription("Next home page").performClick()
            compose.waitUntil(5_000) {
                runCatching { compose.onNode(SemanticsMatcher.expectValue(
                    androidx.compose.ui.semantics.SemanticsProperties.StateDescription,
                    "Home page 3 of 3")).fetchSemanticsNode() }.isSuccess
            }
            compose.onNodeWithTag("widget-placement-cancel").performClick()
            compose.onNode(SemanticsMatcher.expectValue(
                androidx.compose.ui.semantics.SemanticsProperties.StateDescription,
                "Home page 2 of 2")).assertExists()
            val librarySearch = compose.onAllNodesWithTag("library-search").fetchSemanticsNodes()
            if (librarySearch.isNotEmpty()) compose.onNodeWithTag("library-search").assertIsNotFocused()
            assertEquals(fixture, model().state.value.layout)
            assertEquals(idsBefore, controller().host.appWidgetIds.toSet())
        } finally { cleanup(before, idsBefore) }
    }

    @Test fun heldProviderTurnsToABlankPageAndDropsAtTheChosenAnchorOnly() {
        ready()
        val before = model().state.value.layout
        val idsBefore = controller().host.appWidgetIds.toSet()
        try {
            prepareEmptySecondPage()
            openCatalogAt()
            compose.onNodeWithTag("widget-catalog-search").performTextInput("Instant Conditions")
            val slot = model().nextWidgetSlot()
            val card = compose.onNodeWithTag(providerTag("OptionalConfigWidgetProvider"), useUnmergedTree = true)
            card.performScrollTo()
            closeSoftKeyboard()
            compose.waitForIdle()
            val windowLocation = IntArray(2).also {
                compose.activity.findViewById<android.view.View>(android.R.id.content).getLocationOnScreen(it)
            }
            val screenOffset = Offset(windowLocation[0].toFloat(), windowLocation[1].toFloat())
            val cardBounds = card.fetchSemanticsNode().boundsInRoot
            val start = Offset(cardBounds.center.x,
                cardBounds.top + 40f * compose.activity.resources.displayMetrics.density) + screenOffset
            val root = compose.onNodeWithTag("launcher-root")
            val right = root.fetchSemanticsNode().boundsInRoot.right + screenOffset.x - 3f
            val destination = compose.onNodeWithTag("home-cell-$HOME_CELLS").fetchSemanticsNode().boundsInRoot.center + screenOffset
            val edge = Offset(right, start.y)
            val outcome = java.util.concurrent.atomic.AtomicReference<Result<Unit>>()
            val stage = java.util.concurrent.atomic.AtomicInteger(0)
            val continueGesture = java.util.concurrent.atomic.AtomicBoolean(false)
            val continueFromEdge = java.util.concurrent.atomic.AtomicBoolean(false)
            val allowRelease = java.util.concurrent.atomic.AtomicBoolean(false)
            val gestureThread = Thread {
                outcome.set(runCatching {
                    val downTime = android.os.SystemClock.uptimeMillis()
                    injectTouch(android.view.MotionEvent.ACTION_DOWN, start, downTime)
                    android.os.SystemClock.sleep(700)
                    stage.set(1)
                    while (!continueGesture.get()) android.os.SystemClock.sleep(10)
                    injectTouch(android.view.MotionEvent.ACTION_MOVE, start + Offset(8f, 0f), downTime)
                    // Give Compose/InputDispatcher a frame to publish the first drag
                    // event before the full-window edge move; back-to-back MOVE events
                    // can be coalesced while the picker recomposes into placement mode.
                    android.os.SystemClock.sleep(100)
                    injectTouch(android.view.MotionEvent.ACTION_MOVE, edge, downTime)
                    stage.set(2)
                    while (!continueFromEdge.get()) android.os.SystemClock.sleep(10)
                    injectTouch(android.view.MotionEvent.ACTION_MOVE, destination, downTime)
                    stage.set(3)
                    while (!allowRelease.get()) android.os.SystemClock.sleep(10)
                    injectTouch(android.view.MotionEvent.ACTION_UP, destination, downTime)
                })
            }.apply { start() }
            val gestureDeadline = android.os.SystemClock.uptimeMillis() + 10_000
            while (stage.get() < 1 && android.os.SystemClock.uptimeMillis() < gestureDeadline) {
                compose.mainClock.advanceTimeByFrame()
                android.os.SystemClock.sleep(16)
            }
            val started = runCatching {
                compose.onNodeWithTag("widget-placement-mode").assertExists(
                    "A visible-card hold must enter widget placement mode before moving")
            }
            continueGesture.set(true)
            if (started.isFailure) {
                continueFromEdge.set(true)
                allowRelease.set(true)
                gestureThread.join(5_000)
                started.getOrThrow()
            }
            while (stage.get() < 2 && android.os.SystemClock.uptimeMillis() < gestureDeadline) {
                compose.mainClock.advanceTimeByFrame()
                android.os.SystemClock.sleep(16)
            }
            // First publish the MOVE at the edge and let the edge LaunchedEffect start.
            // Advancing one large interval immediately can launch its 650 ms delay only
            // on the final frame, leaving no virtual time for the delay itself.
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            repeat(20) {
                if (runCatching { compose.onNode(SemanticsMatcher.expectValue(
                        androidx.compose.ui.semantics.SemanticsProperties.StateDescription,
                        "Home page 3 of 3")).fetchSemanticsNode() }.isSuccess) return@repeat
                compose.mainClock.advanceTimeBy(50)
                compose.waitForIdle()
            }
            val turned = runCatching {
                compose.onNode(SemanticsMatcher.expectValue(
                    androidx.compose.ui.semantics.SemanticsProperties.StateDescription,
                    "Home page 3 of 3")).assertExists()
            }
            java.io.File(compose.activity.filesDir, "held-widget-at-edge-semantics.txt")
                .writeText(compose.onRoot(useUnmergedTree = true).printToString(Int.MAX_VALUE))
            java.io.File(compose.activity.filesDir, "held-widget-at-edge.png").outputStream().use { output ->
                InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                    ?.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output)
            }
            continueFromEdge.set(true)
            if (turned.isFailure) {
                allowRelease.set(true)
                gestureThread.join(5_000)
                turned.getOrThrow()
            }
            while (stage.get() < 3 && android.os.SystemClock.uptimeMillis() < gestureDeadline) {
                compose.mainClock.advanceTimeByFrame()
                android.os.SystemClock.sleep(16)
            }
            compose.waitUntil(5_000) {
                runCatching { compose.onNodeWithTag("widget-placement-preview")
                    .assert(SemanticsMatcher.expectValue(
                        androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "Ready to place")) }.isSuccess
            }
            allowRelease.set(true)
            while (gestureThread.isAlive && android.os.SystemClock.uptimeMillis() < gestureDeadline) {
                compose.mainClock.advanceTimeByFrame()
                android.os.SystemClock.sleep(16)
            }
            assertFalse("Held provider gesture must terminate", gestureThread.isAlive)
            requireNotNull(outcome.get()).getOrThrow()
            val evidence = java.io.File(compose.activity.filesDir, "held-widget-after-gesture.png")
            evidence.outputStream().use { output ->
                InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                    ?.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output)
            }
            java.io.File(compose.activity.filesDir, "held-widget-after-gesture-semantics.txt")
                .writeText(compose.onRoot(useUnmergedTree = true).printToString(Int.MAX_VALUE))
            compose.onNode(SemanticsMatcher.expectValue(
                androidx.compose.ui.semantics.SemanticsProperties.StateDescription,
                "Home page 3 of 3")).assertExists("Held provider must turn to the new blank page before release")
            compose.waitUntil(10_000) { model().placement(slot)?.id?.let { it >= 0 } == true }
            val placed = requireNotNull(model().placement(slot))
            assertEquals("The held provider must use the selected blank-page anchor", 2 * HOME_CELLS,
                placed.page * HOME_CELLS + placed.row * GRID_COLUMNS + placed.column)
        } finally { cleanup(before, idsBefore) }
    }
}
