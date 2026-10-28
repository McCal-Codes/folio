package com.mccal.folio

import android.graphics.Bitmap
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 27 Oct, Fold 8 inner screen at 200% font: a folder's name on Home sat about 16 dp lower than an app's, under the Search pill and cut off.
 * Both labels follow the font to 130% (A11Y-13), but only the app's set its own line height; the folder's took the theme's, which is
 * taller and grows with the font, so the text slid down inside its cell. The folder's label now sits where an app's does.
 *
 * Robolectric measures text from a stand-in font, so the numbers are only trusted relative to each other: same size, same line, same place.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class HomeLabelLargeTextTest {
    @get:Rule val compose = createComposeRule()

    private val icon = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    private val app = AppEntry("com.example.camera/.Main", "Camera", icon)
    private val folder = FolderEntry("folder:11111111-1111-1111-1111-111111111111", "social", listOf(app.id, "com.example.other/.Main"))

    /** The top and bottom of the label under the tile's icon, in dp from the tile's top, at [fontScale]. */
    private fun labels(fontScale: Float): List<Pair<Float, Float>> {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                MaterialTheme {
                    Column(Modifier.width(80.dp)) {
                        AppTile(app, 56f, true, Modifier.testTag("app"), onClick = {}, onLongClick = {})
                        FolderTile(folder, mapOf(app.id to app), 56f, true, HomeDragState(), 0, Modifier.testTag("folder"), onClick = {})
                    }
                }
            }
        }
        fun of(tag: String, text: String): Pair<Float, Float> {
            val tile = compose.onNodeWithTag(tag, useUnmergedTree = true).getBoundsInRoot()
            val label = compose.onNodeWithText(text, useUnmergedTree = true).getBoundsInRoot()
            return (label.top - tile.top).value to (label.bottom - tile.top).value
        }
        return listOf(of("app", "Camera"), of("folder", "social"))
    }

    @Test fun `a folder's name sits where an app's does at 200 percent text`() {
        val (app, folder) = labels(2f)
        assertEquals("label top", app.first, folder.first, 1f)
        assertEquals("label bottom", app.second, folder.second, 1f)
    }

    @Test fun `and at the default size`() {
        val (app, folder) = labels(1f)
        assertEquals("label top", app.first, folder.first, 1f)
        assertTrue("a label has height", folder.second > folder.first)
    }
}
