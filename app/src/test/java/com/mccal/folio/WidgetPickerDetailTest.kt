package com.mccal.folio

import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.performScrollToNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The size switch and the page map's snapping are plain arithmetic: a size a widget does not accept is never offered or reached. */
class WidgetPickerDetailLogicTest {
    private val free = WidgetSpanConstraints(WidgetSpan(2, 2), WidgetSpan(1, 1), WidgetSpan(4, 4), canResizeHorizontally = true, canResizeVertically = true)
    private val fixed = WidgetSpanConstraints(WidgetSpan(2, 2), WidgetSpan(2, 2), WidgetSpan(2, 2), canResizeHorizontally = false, canResizeVertically = false)

    @Test fun `a free widget offers its own limits and Apple's three sizes, smallest first`() {
        assertEquals(listOf(WidgetSpan(1, 1), WidgetSpan(2, 2), WidgetSpan(4, 2), WidgetSpan(4, 4)), widgetSizeOptions(free, WidgetSpan(2, 2)))
    }

    @Test fun `a widget that cannot be resized offers only its own size`() {
        assertEquals(listOf(WidgetSpan(2, 2)), widgetSizeOptions(fixed, WidgetSpan(2, 2)))
    }

    @Test fun `a widget with no limits of its own offers the size it starts at`() {
        assertEquals(listOf(WidgetSpan(4, 2)), widgetSizeOptions(null, WidgetSpan(4, 2)))
    }

    @Test fun `a narrow widget is never offered a size beyond its limits`() {
        val strip = WidgetSpanConstraints(WidgetSpan(4, 1), WidgetSpan(3, 1), WidgetSpan(4, 2), canResizeHorizontally = true, canResizeVertically = true)
        val options = widgetSizeOptions(strip, WidgetSpan(4, 1))
        assertTrue(options.all { strip.accepts(it) })
        assertEquals(WidgetSpan(3, 1), options.first())
    }

    @Test fun `there are never more than five sizes`() {
        assertTrue(widgetSizeOptions(free, WidgetSpan(2, 2)).size <= 5)
    }

    @Test fun `sizes have the same three names as the gallery`() {
        assertEquals(R.string.small, widgetSizeNameRes(WidgetSpan(2, 2)))
        assertEquals(R.string.medium, widgetSizeNameRes(WidgetSpan(4, 2)))
        assertEquals(R.string.large, widgetSizeNameRes(WidgetSpan(4, 4)))
    }

    @Test fun `a moved widget stays on the page`() {
        assertEquals(3 to 5, mapClampAnchor(9, 9, WidgetSpan(1, 1), 6))
        assertEquals(2 to 4, mapClampAnchor(3, 5, WidgetSpan(2, 2), 6))
        assertEquals(0 to 0, mapClampAnchor(-3, -1, WidgetSpan(4, 2), 6))
    }

    @Test fun `dragging the corner snaps to the nearest size the widget accepts`() {
        assertEquals(WidgetSpan(4, 2), mapResize(free, 0, 0, 3, 1, 6))
        assertEquals(WidgetSpan(1, 1), mapResize(free, 0, 0, 0, 0, 6))
        // The corner cannot pass the page's edge or the widget's own maximum.
        assertEquals(WidgetSpan(2, 3), mapResize(free, 2, 3, 9, 9, 6))
        assertEquals(WidgetSpan(4, 4), mapResize(free, 0, 0, 3, 9, 6))
    }

    @Test fun `a widget at its minimum cannot be dragged smaller`() {
        val medium = WidgetSpanConstraints(WidgetSpan(4, 2), WidgetSpan(2, 2), WidgetSpan(4, 2), canResizeHorizontally = true, canResizeVertically = false)
        assertEquals(WidgetSpan(2, 2), mapResize(medium, 0, 0, 0, 0, 6))
    }

    @Test fun `Folio's own widgets start at their own size`() {
        assertEquals(WidgetSpan(4, 2), builtinDefaultSpan(BIG_CLOCK_WIDGET))
        assertEquals(WidgetSpan(2, 2), builtinDefaultSpan(CLOCK_WIDGET))
        assertEquals(WidgetSpan(4, 1), builtinDefaultSpan(SEARCH_WIDGET))
    }
}

/** The picker's second step: a tap on a card opens the widget large, Add commits, and the page map says whether it fits. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h900dp")
class WidgetPickerDetailTest {
    @get:Rule val compose = createComposeRule()

    private val personal = AppProfile(0, "Personal", isPersonal = true, isWork = false, quiet = false, unlocked = true, available = true)

    private fun entry(pkg: String, app: String, name: String, description: String = "") = WidgetCatalogEntry(
        AppWidgetProviderInfo().apply {
            provider = ComponentName(pkg, "$pkg.$name")
            AppWidgetProviderInfo::class.java.getField("providerInfo").set(this, android.content.pm.ActivityInfo().apply {
                packageName = pkg; this.name = "$pkg.$name"
                applicationInfo = android.content.pm.ApplicationInfo().apply { packageName = pkg }
            })
        }, name, app, description, 0, "Personal")

    private val alarm = entry("com.clock", "Clock", "Alarm", "Wake up on time")
    private val limits = WidgetSpanConstraints(WidgetSpan(2, 2), WidgetSpan(2, 1), WidgetSpan(4, 2), canResizeHorizontally = true, canResizeVertically = true)

    /** An empty page of six rows with the first row's cells taken; [room] says whether a widget of any size fits. */
    private fun placement(room: Boolean = true, added: MutableList<Triple<Int, Int, WidgetSpan>>? = null) = PickerPlacement(
        0, 1, 8,
        rows = { 6 }, taken = { setOf(0, 1) },
        fits = { index, span -> room && homeCellLocal(index) / GRID_COLUMNS + span.height <= 6 && homeCellLocal(index) % GRID_COLUMNS + span.width <= GRID_COLUMNS },
        contentSize = { span, _ -> WidgetContentSize(span.width * 80f, span.height * 80f) },
        freeOnPage = { page, _ -> if (room) homeCellIndex(page, 8) else null },
        firstFree = { _, page -> if (room) homeCellIndex(page, 8) else null })

    private val taps = mutableListOf<String>()
    private val added = mutableListOf<Triple<Int, Int, WidgetSpan>>()

    private fun show(placement: PickerPlacement? = placement()) = compose.setContent {
        VisualWidgetPicker(listOf(alarm), listOf(personal), personal, {}, {}, hiddenForDrag = false, footprint = { WidgetSpan(2, 2) },
            onBack = {}, onTap = { taps += "tap" }, onBuiltin = { taps += "builtin-$it" }, onDragStart = { _, _ -> }, onDrag = {}, onDrop = {}, onCancelDrag = {},
            constraintsOf = { limits }, placement = placement,
            onAddAt = { provider, builtin, index, span -> added += Triple(if (provider != null) 1 else 0, index, span); builtin?.let { taps += "added-builtin-$it" } })
    }

    private fun openAlarm() {
        compose.onNodeWithTag("widget-provider-com.clock/com.clock.Alarm", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("widget-detail", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun `a tap opens the widget large and adds nothing yet`() {
        show()
        openAlarm()
        compose.onNodeWithTag("widget-detail-description", useUnmergedTree = true).assertIsDisplayed()
        assertTrue(taps.isEmpty() && added.isEmpty())
    }

    @Test fun `Add puts the widget at the cell and size the page map ended on`() {
        show()
        openAlarm()
        compose.onNodeWithTag("widget-detail-size-4x2", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("widget-detail-add", useUnmergedTree = true).performClick()
        assertEquals(listOf(Triple(1, 8, WidgetSpan(4, 2))), added)
        assertTrue(taps.isEmpty())
    }

    @Test fun `the size switch shows only sizes the widget accepts`() {
        show()
        openAlarm()
        compose.onAllNodes(hasTestTag("widget-detail-size-4x4"), useUnmergedTree = true).assertCountEquals(0)
        compose.onNodeWithTag("widget-detail-size-2x1", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun `when nothing fits the page map says so and Add is off`() {
        show(placement(room = false))
        openAlarm()
        compose.onNodeWithTag("widget-detail-add", useUnmergedTree = true).assertIsNotEnabled()
        compose.onNodeWithTag("widget-detail-fit", useUnmergedTree = true).assertIsDisplayed()
        assertTrue(added.isEmpty())
    }

    @Test fun `when the page it was opened for is full the map starts on the next one and says so`() {
        show(PickerPlacement(0, 1, 8,
            rows = { 6 }, taken = { setOf() },
            fits = { index, span -> homeCellPage(index) == 1 && homeCellLocal(index) / GRID_COLUMNS + span.height <= 6 },
            contentSize = { span, _ -> WidgetContentSize(span.width * 80f, span.height * 80f) },
            freeOnPage = { page, _ -> if (page == 1) homeCellIndex(1, 0) else null },
            firstFree = { _, _ -> homeCellIndex(1, 0) }))
        openAlarm()
        compose.onNodeWithTag("widget-detail-fit", useUnmergedTree = true).assertTextContains("Page 1 has no room at this size, so this starts on Page 2.")
        compose.onNodeWithTag("widget-detail-add", useUnmergedTree = true).assertIsEnabled()
    }

    @Test fun `a screen reader is told where the widget is and hears the result of a refused drop`() {
        show()
        openAlarm()
        compose.onNodeWithTag("widget-detail-map", useUnmergedTree = true)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Column 1, row 3, 2 wide, 2 tall"))
        compose.onNodeWithTag("widget-detail-fit", useUnmergedTree = true)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
    }

    @Test fun `without a page to draw, Add falls back to the widget's own placement`() {
        show(placement = null)
        openAlarm()
        compose.onAllNodes(hasTestTag("widget-detail-map"), useUnmergedTree = true).assertCountEquals(0)
        compose.onNodeWithTag("widget-detail-add", useUnmergedTree = true).assertIsEnabled().performClick()
        assertEquals(listOf("tap"), taps)
    }

    @Test fun `back returns to the gallery`() {
        show()
        openAlarm()
        compose.onNodeWithTag("widget-detail-back", useUnmergedTree = true).performClick()
        compose.onAllNodes(hasTestTag("widget-detail"), useUnmergedTree = true).assertCountEquals(0)
        compose.onNodeWithTag("widget-catalog-list", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun `one of Folio's own widgets opens the same step and adds through the page map`() {
        show()
        compose.onNodeWithTag("widget-builtin-$CLOCK_WIDGET", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("widget-detail", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("widget-detail-add", useUnmergedTree = true).performClick()
        assertEquals(listOf("added-builtin-$CLOCK_WIDGET"), taps)
        assertEquals(1, added.size)
    }
}
