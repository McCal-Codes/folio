package com.mccal.folio

import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasTestTag
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The widget picker shows one row per app while browsing, and one app at a time from the row of app icons.
 *
 * On the Fold8 805 widgets are installed and 430 come from one pack; grouped by app and uncapped, that pack was the
 * wall in front of Calendar and Clock (update-map, "Widget picker, cleaner").
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h900dp")
class WidgetPickerAppRowTest {
    @get:Rule val compose = createComposeRule()

    private val personal = AppProfile(0, "Personal", isPersonal = true, isWork = false, quiet = false, unlocked = true, available = true)

    private fun entry(pkg: String, app: String, name: String) = WidgetCatalogEntry(
        AppWidgetProviderInfo().apply {
            provider = ComponentName(pkg, "$pkg.$name")
            // Hidden from the public SDK, and what the preview reads first; a real provider always has it.
            AppWidgetProviderInfo::class.java.getField("providerInfo").set(this, android.content.pm.ActivityInfo().apply {
                packageName = pkg; this.name = "$pkg.$name"
                applicationInfo = android.content.pm.ApplicationInfo().apply { packageName = pkg }
            })
        }, name, app, "", 0, "Personal")

    // "Pack" has ten widgets, "Clock" two: at 400 dp wide the grid has two columns.
    private val entries = (1..10).map { entry("com.pack", "Pack", "W$it") } + listOf(entry("com.clock", "Clock", "Alarm"), entry("com.clock", "Clock", "World"))

    private fun show(destination: String? = null) = compose.setContent {
        VisualWidgetPicker(entries, listOf(personal), personal, {}, {}, hiddenForDrag = false, footprint = { WidgetSpan(2, 2) },
            onBack = {}, onTap = {}, onBuiltin = {}, onDragStart = { _, _ -> }, onDrag = {}, onDrop = {}, onCancelDrag = {}, destination = destination)
    }

    /** Folio's own widgets come first, so an app's group can be below the fold; a lazy grid only builds what's shown. */
    private fun scrollTo(tag: String) = compose.onNodeWithTag("widget-catalog-list", useUnmergedTree = true)
        .performScrollToNode(hasTestTag(tag))

    private fun packCards() = compose.onAllNodes(androidx.compose.ui.test.SemanticsMatcher("a Pack widget card") {
        it.config.getOrElseNullable(androidx.compose.ui.semantics.SemanticsProperties.TestTag) { null }?.startsWith("widget-provider-com.pack/") == true
    }, useUnmergedTree = true)

    @Test fun `browsing shows one row of a big app, and a way to the rest`() {
        show()
        scrollTo("widget-show-all-Pack")
        compose.onNodeWithTag("widget-show-all-Pack", useUnmergedTree = true).assertIsDisplayed()
        packCards().assertCountEquals(2)
    }

    @Test fun `show all opens that app's whole list`() {
        show()
        scrollTo("widget-show-all-Pack")
        compose.onNodeWithTag("widget-show-all-Pack", useUnmergedTree = true).performClick()
        compose.onAllNodes(hasTestTag("widget-show-all-Pack"), useUnmergedTree = true).assertCountEquals(0)
    }

    @Test fun `choosing an app in the row shows only that app`() {
        show()
        compose.onNodeWithTag("widget-app-Clock", useUnmergedTree = true).performClick()
        packCards().assertCountEquals(0)
        compose.onNodeWithText("Alarm").assertIsDisplayed()
        compose.onNodeWithText("World").assertIsDisplayed()
    }

    @Test fun `Folio's own widgets are capped like any app's, and have their own place in the row`() {
        show()
        compose.onNodeWithTag("widget-show-all-folio", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("widget-app-folio", useUnmergedTree = true).performClick()
        compose.onAllNodes(hasTestTag("widget-show-all-folio"), useUnmergedTree = true).assertCountEquals(0)
        packCards().assertCountEquals(0)
    }

    @Test fun `the picker says where a widget will go, and says nothing when it does not know`() {
        show(destination = "Adding to Home, page 2")
        compose.onNodeWithText("Adding to Home, page 2").assertIsDisplayed()
    }

    @Test fun `no destination line without a destination`() {
        show()
        compose.onAllNodes(hasTestTag("widget-picker-destination"), useUnmergedTree = true).assertCountEquals(0)
    }

    @Test fun `on a phone the apps are a row above the gallery, not a list beside it`() {
        show()
        compose.onNodeWithTag("widget-app-row", useUnmergedTree = true).assertIsDisplayed()
        compose.onAllNodes(hasTestTag("widget-app-list"), useUnmergedTree = true).assertCountEquals(0)
    }
}

/** Unfolded (and any window from 720 dp wide) the apps stand in a list beside the gallery, with a way back to all of them. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w840dp-h900dp")
class WidgetPickerWideTest {
    @get:Rule val compose = createComposeRule()

    private val personal = AppProfile(0, "Personal", isPersonal = true, isWork = false, quiet = false, unlocked = true, available = true)

    private fun entry(pkg: String, app: String, name: String) = WidgetCatalogEntry(
        AppWidgetProviderInfo().apply {
            provider = ComponentName(pkg, "$pkg.$name")
            AppWidgetProviderInfo::class.java.getField("providerInfo").set(this, android.content.pm.ActivityInfo().apply {
                packageName = pkg; this.name = "$pkg.$name"
                applicationInfo = android.content.pm.ApplicationInfo().apply { packageName = pkg }
            })
        }, name, app, "", 0, "Personal")

    private val entries = listOf(entry("com.clock", "Clock", "Alarm"), entry("com.clock", "Clock", "World"), entry("com.mail", "Mail", "Inbox"))

    private fun show() = compose.setContent {
        VisualWidgetPicker(entries, listOf(personal), personal, {}, {}, hiddenForDrag = false, footprint = { WidgetSpan(2, 2) },
            onBack = {}, onTap = {}, onBuiltin = {}, onDragStart = { _, _ -> }, onDrag = {}, onDrop = {}, onCancelDrag = {})
    }

    @Test fun `a wide window lists the apps beside the gallery`() {
        show()
        compose.onNodeWithTag("widget-app-list", useUnmergedTree = true).assertIsDisplayed()
        compose.onAllNodes(hasTestTag("widget-app-row"), useUnmergedTree = true).assertCountEquals(0)
    }

    @Test fun `choosing an app in the list narrows the gallery, and All widgets brings the rest back`() {
        show()
        compose.onNodeWithTag("widget-app-Mail", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Inbox").assertIsDisplayed()
        compose.onAllNodes(hasTestTag("widget-provider-com.clock/com.clock.Alarm"), useUnmergedTree = true).assertCountEquals(0)
        compose.onNodeWithTag("widget-app-all", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Alarm").assertIsDisplayed()
    }
}
