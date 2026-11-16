package com.mccal.folio

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import org.hamcrest.CoreMatchers.containsString
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SpecialWidgetReplacementIntegrationTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules = org.junit.rules.RuleChain.outerRule(WithoutNativeFeed()).around(compose)
    private fun model() = ViewModelProvider(compose.activity)[LauncherModel::class.java]
    private fun controller() = MainActivity::class.java.getDeclaredField("widgets").apply { isAccessible = true }
        .get(compose.activity) as WidgetController

    private fun ready() {
        compose.waitUntil(15_000) { !model().state.value.loading }
        val density = compose.activity.resources.displayMetrics.density
        check(compose.activity.windowManager.currentWindowMetrics.bounds.width() / density >= 650f) {
            "Special leading-panel replacement requires inner-display geometry"
        }
    }

    private fun openReplacement(slot: Int, provider: android.appwidget.AppWidgetProviderInfo, locked: Boolean) {
        compose.onNodeWithTag("widget-slot-$slot", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.OnLongClick)
        compose.onNodeWithText("Replace").performClick()
        compose.onNodeWithTag("widget-catalog-search").performTextInput(
            provider.loadLabel(compose.activity.packageManager).toString())
        val tag = "widget-provider-${provider.provider.flattenToString()}"
        compose.onNodeWithTag(tag).performScrollTo().performClick()
        compose.continueToPlacement()
        if (locked) {
            compose.onNodeWithTag("widget-replacement-locked").assertIsDisplayed().assertTextContains("Replace here")
            compose.onNodeWithContentDescription("Previous home page").assertDoesNotExist()
            compose.onNodeWithContentDescription("Next home page").assertDoesNotExist()
        } else {
            compose.onNodeWithTag("widget-replacement-locked").assertDoesNotExist()
            compose.onNodeWithContentDescription("Previous home page").assertExists().assertIsNotEnabled()
            compose.onNodeWithContentDescription("Next home page").assertExists().assertIsEnabled()
        }
        compose.onNodeWithTag("widget-placement-preview").assertIsDisplayed()
    }

    private fun exerciseReplacement(special: WidgetPlacement,
        provider: android.appwidget.AppWidgetProviderInfo, idsBefore: Set<Int>) {
        val ordinaryHomeBefore = model().state.value.homeSlots
        val leadingHomeBefore = model().state.value.leadingSlots
        val locked = special.row + special.spanY > GRID_ROWS
        openReplacement(special.slot, provider, locked)
        compose.onNodeWithTag("widget-placement-cancel").performClick()
        assertEquals(special, model().placement(special.slot))
        assertEquals(ordinaryHomeBefore, model().state.value.homeSlots)
        assertEquals(leadingHomeBefore, model().state.value.leadingSlots)
        assertEquals(idsBefore, controller().host.appWidgetIds.toSet())

        openReplacement(special.slot, provider, locked)
        compose.onNodeWithTag("widget-placement-apply").assertIsEnabled().performClick()
        compose.waitUntil(10_000) {
            model().placement(special.slot)?.id?.let { it >= 0 && it != special.id } == true &&
                controller().pendingPlacement == null
        }
        val replacement = requireNotNull(model().placement(special.slot))
        assertEquals(listOf(special.page, special.column, special.row, special.spanX, special.spanY),
            listOf(replacement.page, replacement.column, replacement.row, replacement.spanX, replacement.spanY))
        assertEquals(ordinaryHomeBefore, model().state.value.homeSlots)
        assertEquals(leadingHomeBefore, model().state.value.leadingSlots)
        assertEquals(provider.provider, controller().manager.getAppWidgetInfo(replacement.id)?.provider)
        onView(withText(containsString("Fixture widget is live"))).check(matches(isDisplayed()))

        compose.runOnIdle { assertTrue(model().undoEdit()) }
        assertEquals(special, model().placement(special.slot))
        assertEquals(ordinaryHomeBefore, model().state.value.homeSlots)
        assertEquals(leadingHomeBefore, model().state.value.leadingSlots)
    }

    @Test fun leadingGridReplacementCancelCommitAndUndoKeepExactFootprint() {
        ready()
        val before = model().state.value.layout
        val idsBefore = controller().host.appWidgetIds.toSet()
        try {
            val special = model().state.value.widgetPlacements.firstOrNull { it.page == -1 }
                ?: WidgetPlacement(model().nextWidgetSlot(), INFO_WIDGET, -1, 0, 0, 4, 6).also {
                    compose.runOnIdle { assertTrue(model().placeWidget(it)) }
                }
            val provider = requireNotNull(controller().personalProviders().firstOrNull {
                it.provider.packageName == FolioTestPackages.test &&
                    it.provider.className.endsWith("OptionalConfigWidgetProvider")
            })

            exerciseReplacement(special, provider, idsBefore)
        } finally {
            compose.runOnIdle { model().restoreLayout(before) }
            controller().host.appWidgetIds.filter { it !in idsBefore }.forEach(controller().host::deleteAppWidgetId)
            assertEquals(before, model().state.value.layout)
            assertEquals(idsBefore, controller().host.appWidgetIds.toSet())
        }
    }

    @Test fun legacyOverflowReplacementCancelCommitAndUndoKeepExactFootprint() {
        ready()
        val before = model().state.value.layout
        val idsBefore = controller().host.appWidgetIds.toSet()
        try {
            val provider = requireNotNull(controller().personalProviders().firstOrNull {
                it.provider.packageName == FolioTestPackages.test &&
                    it.provider.className.endsWith("OptionalConfigWidgetProvider")
            })
            val anchorApp = model().state.value.apps.first { it.available && it.id !in before.dock }
            val slots = before.slots.toMutableList().apply {
                while (size <= HOME_CELLS) add(null)
                this[HOME_CELLS] = anchorApp.id
            }
            val overflow = WidgetPlacement(5, CLOCK_WIDGET, 1, 0, GRID_ROWS, 4, 4)
            val fixture = before.copy(slots = slots,
                widgetPlacements = before.widgetPlacements.filterNot { it.slot == overflow.slot } + overflow)
            compose.runOnIdle { model().restoreLayout(fixture) }
            compose.onNodeWithContentDescription("Home page 2").performClick()
            compose.onNodeWithTag("widget-slot-${overflow.slot}", useUnmergedTree = true).performScrollTo()

            exerciseReplacement(overflow, provider, idsBefore)
        } finally {
            compose.runOnIdle { model().restoreLayout(before) }
            controller().host.appWidgetIds.filter { it !in idsBefore }.forEach(controller().host::deleteAppWidgetId)
            assertEquals(before, model().state.value.layout)
            assertEquals(idsBefore, controller().host.appWidgetIds.toSet())
        }
    }
}
