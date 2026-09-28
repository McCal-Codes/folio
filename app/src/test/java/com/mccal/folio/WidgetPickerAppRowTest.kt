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

    private fun show() = compose.setContent {
        VisualWidgetPicker(entries, listOf(personal), personal, {}, {}, hiddenForDrag = false, footprint = { WidgetSpan(2, 2) },
            onBack = {}, onTap = {}, onBuiltin = {}, onDragStart = { _, _ -> }, onDrag = {}, onDrop = {}, onCancelDrag = {})
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
}
