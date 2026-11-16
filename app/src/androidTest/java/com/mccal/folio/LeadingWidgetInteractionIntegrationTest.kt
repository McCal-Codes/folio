package com.mccal.folio

import android.appwidget.AppWidgetManager
import android.os.ParcelFileDescriptor
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.hamcrest.CoreMatchers.containsString
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real provider and native-view acceptance for the unfolded-only Home grid. */
@RunWith(AndroidJUnit4::class)
class LeadingWidgetInteractionIntegrationTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules = org.junit.rules.RuleChain.outerRule(WithoutNativeFeed()).around(compose)
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    private fun model() = ViewModelProvider(compose.activity)[LauncherModel::class.java]
    private fun controller() = MainActivity::class.java.getDeclaredField("widgets").apply { isAccessible = true }
        .get(compose.activity) as WidgetController
    private fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(
        instrumentation.uiAutomation.executeShellCommand(command)
    ).bufferedReader().use { it.readText().trim() }
    private fun ready() {
        check(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        compose.waitUntil(15_000) { !model().state.value.loading }
    }
    private fun provider() = controller().personalProviders().single {
        it.provider.packageName == FolioTestPackages.test &&
            it.provider.className.endsWith("OptionalConfigWidgetProvider")
    }
    private fun providerTag() = "widget-provider-${provider().provider.flattenToString()}"
    private fun root() = compose.onNodeWithTag("launcher-root")
    private fun center(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.center

    private fun openCatalogAt(index: Int) {
        compose.onNodeWithTag("home-cell-$index").performTouchInput { longClick() }
        compose.onNodeWithText("Add widget").performClick()
        compose.onNodeWithTag("visual-widget-picker").assertIsDisplayed()
    }

    private fun placeOptionalAt(index: Int): WidgetPlacement {
        val slot = model().nextWidgetSlot()
        openCatalogAt(index)
        compose.onNodeWithTag("widget-catalog-search").performTextInput("Instant Conditions")
        compose.onNodeWithTag(providerTag()).performScrollTo().performClick()
        compose.continueToPlacement()
        compose.onNodeWithTag("widget-placement-preview").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Ready to place"))
        compose.onNodeWithTag("widget-placement-apply").assertIsEnabled().performClick()
        compose.waitUntil(10_000) { model().placement(slot)?.id?.let { it >= 0 } == true }
        return requireNotNull(model().placement(slot))
    }

    @Test fun addTwoMoveHeldWidgetThenFoldAndUnfoldPreservesLeadingBindings() {
        ready()
        val before = model().state.value.layout
        val idsBefore = controller().host.appWidgetIds.toSet()
        val originalSize = Regex("Override size: (\\d+x\\d+)").find(shell("wm size"))?.groupValues?.get(1)
        val originalDensity = Regex("Override density: (\\d+)").find(shell("wm density"))?.groupValues?.get(1)
        val originalFont = shell("settings get system font_scale").takeUnless { it == "null" }
        var pointerHeld = false
        var primaryFailure: Throwable? = null
        try {
            // Clock152 occupies the complete historical leading panel on the repaired baseline.
            // Keep its real ID bound and move every existing leading placement as a group to a
            // new blank normal page for the duration of this test.
            val sparePage = before.pageCount
            val fixture = before.copy(
                leadingSlots = List(HOME_CELLS) { null },
                widgetPlacements = before.widgetPlacements.map {
                    if (it.page == -1) it.copy(page = sparePage) else it
                },
            )
            compose.runOnIdle { model().restoreLayout(fixture) }
            assertEquals("Relocating the baseline must retain every real widget binding",
                idsBefore, controller().host.appWidgetIds.toSet())

            shell("wm density 420")
            shell("settings put system font_scale 1.08")
            shell("wm size 2448x1848")
            compose.waitUntil(15_000) {
                compose.activity.windowManager.currentWindowMetrics.bounds.let { it.width() == 2448 && it.height() == 1848 }
            }
            compose.onNodeWithTag("expanded-leading-home").assertIsDisplayed()
            compose.onNodeWithTag("home-cell--24").assertIsDisplayed()
            compose.onNodeWithTag("home-cell--1").assertIsDisplayed()

            val first = placeOptionalAt(-24)
            assertEquals(WidgetPlacement(first.slot, first.id, -1, 0, 0, 2, 2), first)
            assertEquals(provider().provider, AppWidgetManager.getInstance(compose.activity).getAppWidgetInfo(first.id)?.provider)
            compose.onNodeWithTag("widget-slot-${first.slot}").assertIsDisplayed()
            onView(withText(containsString("Fixture widget is live"))).check(matches(isDisplayed()))

            val second = placeOptionalAt(-12)
            assertEquals(WidgetPlacement(second.slot, second.id, -1, 0, 3, 2, 2), second)
            assertNotEquals(first.id, second.id)
            compose.onNodeWithTag("widget-slot-${first.slot}").assertIsDisplayed()
            compose.onNodeWithTag("widget-slot-${second.slot}").assertIsDisplayed()
            assertEquals(idsBefore + setOf(first.id, second.id), controller().host.appWidgetIds.toSet())

            // Hold the real Android widget view, move it across the Fold's left grid, and
            // release only after the live preview has recomposed over the destination.
            val source = compose.onNodeWithTag("widget-slot-${first.slot}").fetchSemanticsNode().boundsInRoot
            val grab = Offset(source.left + source.width * .2f, source.top + source.height * .2f)
            root().performTouchInput {
                down(grab); advanceEventTime(700); moveTo(grab + Offset(4f, 0f))
            }
            pointerHeld = true
            val destination = center("home-cell--22")
            root().performTouchInput { moveTo(destination, 350) }
            compose.waitForIdle()
            assertTrue(compose.onNodeWithTag("widget-slot-${first.slot}")
                .fetchSemanticsNode().boundsInRoot.contains(destination))
            root().performTouchInput { up() }
            pointerHeld = false
            compose.waitForIdle()
            val moved = WidgetPlacement(first.slot, first.id, -1, 2, 0, 2, 2)
            assertEquals(moved, model().placement(first.slot))
            assertEquals(provider().provider, controller().manager.getAppWidgetInfo(first.id)?.provider)

            shell("wm size 1248x1972")
            compose.waitUntil(15_000) { compose.activity.windowManager.currentWindowMetrics.bounds.width() == 1248 }
            compose.onNodeWithTag("expanded-leading-home").assertDoesNotExist()
            compose.onNodeWithTag("widget-slot-${first.slot}").assertDoesNotExist()
            compose.onNodeWithTag("widget-slot-${second.slot}").assertDoesNotExist()
            assertEquals(moved, model().placement(first.slot))
            assertEquals(second, model().placement(second.slot))
            assertTrue(setOf(first.id, second.id).all { it in controller().host.appWidgetIds })

            shell("wm size 2448x1848")
            compose.waitUntil(15_000) { compose.activity.windowManager.currentWindowMetrics.bounds.width() == 2448 }
            compose.onNodeWithTag("expanded-leading-home").assertIsDisplayed()
            compose.onNodeWithTag("widget-slot-${first.slot}").assertIsDisplayed()
            compose.onNodeWithTag("widget-slot-${second.slot}").assertIsDisplayed()
            assertEquals(moved, model().placement(first.slot))
            assertEquals(second, model().placement(second.slot))
            assertEquals(idsBefore + setOf(first.id, second.id), controller().host.appWidgetIds.toSet())
        } catch (failure: Throwable) {
            primaryFailure = failure
            throw failure
        } finally {
            var cleanupFailure: Throwable? = null
            fun cleanup(block: () -> Unit) = try { block() } catch (failure: Throwable) {
                if (cleanupFailure == null) cleanupFailure = failure else cleanupFailure?.addSuppressed(failure)
            }
            if (pointerHeld) cleanup { root().performTouchInput { cancel() } }
            cleanup { shell("wm size ${originalSize ?: "reset"}") }
            cleanup { shell("wm density ${originalDensity ?: "reset"}") }
            cleanup {
                if (originalFont == null) shell("settings delete system font_scale")
                else shell("settings put system font_scale $originalFont")
            }
            cleanup { compose.waitForIdle(); ready() }
            cleanup { compose.runOnIdle { model().restoreLayout(before) } }
            cleanup {
                controller().host.appWidgetIds.filter { it !in idsBefore }.forEach(controller().host::deleteAppWidgetId)
                assertEquals("Every baseline ID, including Clock152, must survive", idsBefore,
                    controller().host.appWidgetIds.toSet())
                assertEquals(before, model().state.value.layout)
            }
            cleanupFailure?.let { if (primaryFailure != null) primaryFailure?.addSuppressed(it) else throw it }
        }
    }

    @Test fun heldNativeWidgetAndAppMoveAcrossScreenOneAndLeadingInBothDirections() {
        ready()
        val before = model().state.value.layout
        val idsBefore = controller().host.appWidgetIds.toSet()
        val originalSize = Regex("Override size: (\\d+x\\d+)").find(shell("wm size"))?.groupValues?.get(1)
        var testId: Int? = null
        var pointerHeld = false
        var primaryFailure: Throwable? = null
        try {
            val chosenApp = model().state.value.apps.first {
                it.available && it.id !in before.dock && before.folders.none { folder -> it.id in folder.appIds }
            }
            val slot = model().nextWidgetSlot()
            val id = controller().host.allocateAppWidgetId()
            testId = id
            assertTrue("Fixture provider must bind through the real AppWidgetManager",
                controller().manager.bindAppWidgetIdIfAllowed(id, provider().profile, provider().provider, null))
            val fixture = before.copy(
                slots = MutableList<String?>(HOME_CELLS) { null }.apply { this[23] = chosenApp.id },
                leadingSlots = List(HOME_CELLS) { null },
                widgetPlacements = before.widgetPlacements.map {
                    if (it.page == -1) it.copy(page = before.pageCount) else it
                } + WidgetPlacement(slot, id, 0, 0, 2, 2, 2),
            )
            compose.runOnIdle { model().restoreLayout(fixture) }
            shell("wm size 2448x1848")
            compose.waitUntil(15_000) { compose.activity.windowManager.currentWindowMetrics.bounds.width() == 2448 }
            compose.onNodeWithTag("expanded-leading-home").assertIsDisplayed()

            fun drag(sourceTag: String, destinationTag: String) {
                val source = compose.onNodeWithTag(sourceTag).fetchSemanticsNode().boundsInRoot
                val grab = Offset(source.left + source.width * .2f, source.top + source.height * .2f)
                root().performTouchInput {
                    down(grab); advanceEventTime(700); moveTo(grab + Offset(4f, 0f))
                }
                pointerHeld = true
                root().performTouchInput { moveTo(center(destinationTag), 400) }
                compose.waitForIdle()
                root().performTouchInput { up() }
                pointerHeld = false
                compose.waitForIdle()
            }

            drag("widget-slot-$slot", "home-cell--24")
            val leadingWidget = WidgetPlacement(slot, id, -1, 0, 0, 2, 2)
            assertEquals(leadingWidget, model().placement(slot))
            assertEquals(provider().provider, controller().manager.getAppWidgetInfo(id)?.provider)
            drag("widget-slot-$slot", "home-cell-8")
            assertEquals(WidgetPlacement(slot, id, 0, 0, 2, 2, 2), model().placement(slot))
            assertEquals(provider().provider, controller().manager.getAppWidgetInfo(id)?.provider)

            drag("home-cell-23", "home-cell--1")
            assertEquals(chosenApp.id, model().state.value.layout.slotAt(-1))
            assertNull(model().state.value.layout.slotAt(23))
            drag("home-cell--1", "home-cell-23")
            assertEquals(chosenApp.id, model().state.value.layout.slotAt(23))
            assertNull(model().state.value.layout.slotAt(-1))
            assertEquals(id, model().placement(slot)?.id)
            assertTrue(id in controller().host.appWidgetIds)
        } catch (failure: Throwable) {
            primaryFailure = failure
            throw failure
        } finally {
            var cleanupFailure: Throwable? = null
            fun cleanup(block: () -> Unit) = try { block() } catch (failure: Throwable) {
                if (cleanupFailure == null) cleanupFailure = failure else cleanupFailure?.addSuppressed(failure)
            }
            if (pointerHeld) cleanup { root().performTouchInput { cancel() } }
            cleanup { shell("wm size ${originalSize ?: "reset"}") }
            cleanup { compose.waitForIdle(); ready(); compose.runOnIdle { model().restoreLayout(before) } }
            cleanup {
                testId?.takeIf { it !in idsBefore && it in controller().host.appWidgetIds }
                    ?.let(controller().host::deleteAppWidgetId)
                assertEquals(idsBefore, controller().host.appWidgetIds.toSet())
                assertEquals(before, model().state.value.layout)
            }
            cleanupFailure?.let { if (primaryFailure != null) primaryFailure?.addSuppressed(it) else throw it }
        }
    }

    @Test fun foldingLeadingCatalogDraftCancelsWithoutBindingOrFallbackTarget() {
        ready()
        val before = model().state.value.layout
        val idsBefore = controller().host.appWidgetIds.toSet()
        val originalSize = Regex("Override size: (\\d+x\\d+)").find(shell("wm size"))?.groupValues?.get(1)
        var primaryFailure: Throwable? = null
        try {
            // Give both surfaces known empty targets while preserving every placement and ID
            // on separate temporary normal pages.
            val fixture = before.copy(
                slots = emptyList(),
                leadingSlots = List(HOME_CELLS) { null },
                widgetPlacements = before.widgetPlacements.mapIndexed { index, placement ->
                    placement.copy(page = before.pageCount + index)
                },
            )
            compose.runOnIdle { model().restoreLayout(fixture) }
            shell("wm size 2448x1848")
            compose.waitUntil(15_000) { compose.activity.windowManager.currentWindowMetrics.bounds.width() == 2448 }

            openCatalogAt(-24)
            compose.onNodeWithTag("widget-catalog-search").performTextInput("Instant Conditions")
            compose.onNodeWithTag(providerTag()).performScrollTo().performClick()
            compose.continueToPlacement()
            compose.onNodeWithTag("widget-placement-mode").assertExists()
            compose.onNodeWithTag("widget-placement-preview").assertIsDisplayed()
            assertEquals(idsBefore, controller().host.appWidgetIds.toSet())
            assertEquals(fixture, model().state.value.layout)

            shell("wm size 1248x1972")
            compose.waitUntil(15_000) { compose.activity.windowManager.currentWindowMetrics.bounds.width() == 1248 }
            compose.onNodeWithTag("widget-placement-mode").assertDoesNotExist()
            compose.onNodeWithTag("visual-widget-picker").assertDoesNotExist()
            compose.onNodeWithTag("widget-placement-preview").assertDoesNotExist()
            assertNull(controller().pendingPlacement)
            assertEquals(idsBefore, controller().host.appWidgetIds.toSet())
            assertEquals(fixture, model().state.value.layout)

            // Unfolding does not resurrect the signed target. A fresh request on Screen 1
            // must open a normal picker and remain unbound until the user chooses Place.
            shell("wm size 2448x1848")
            compose.waitUntil(15_000) { compose.activity.windowManager.currentWindowMetrics.bounds.width() == 2448 }
            openCatalogAt(0)
            compose.onNodeWithTag("visual-widget-picker").assertIsDisplayed()
            compose.onNodeWithTag("widget-catalog-search").performTextInput("Instant Conditions")
            compose.onNodeWithTag(providerTag()).performScrollTo().performClick()
            compose.continueToPlacement()
            compose.onNodeWithTag("widget-placement-preview").assertIsDisplayed()
            assertTrue("Fresh normal-page request must target Screen 1 cell 0",
                compose.onNodeWithTag("widget-placement-preview").fetchSemanticsNode().boundsInRoot.contains(center("home-cell-0")))
            assertEquals(idsBefore, controller().host.appWidgetIds.toSet())
            assertEquals(fixture, model().state.value.layout)
            compose.onNodeWithTag("widget-placement-cancel").performClick()
        } catch (failure: Throwable) {
            primaryFailure = failure
            throw failure
        } finally {
            var cleanupFailure: Throwable? = null
            fun cleanup(block: () -> Unit) = try { block() } catch (failure: Throwable) {
                if (cleanupFailure == null) cleanupFailure = failure else cleanupFailure?.addSuppressed(failure)
            }
            cleanup { shell("wm size ${originalSize ?: "reset"}") }
            cleanup { compose.waitForIdle(); ready(); compose.runOnIdle { model().restoreLayout(before) } }
            cleanup {
                controller().host.appWidgetIds.filter { it !in idsBefore }.forEach(controller().host::deleteAppWidgetId)
                assertEquals(idsBefore, controller().host.appWidgetIds.toSet())
                assertEquals(before, model().state.value.layout)
            }
            cleanupFailure?.let { if (primaryFailure != null) primaryFailure?.addSuppressed(it) else throw it }
        }
    }
}
