package com.mccal.folio

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.window.Dialog
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * BI-2 of the 27 Oct break-it test: Compose builds a Dialog's window again when the density changes (Display size in
 * Android's settings), and the Market and Settings live in one, so the tab and page chosen inside were thrown away and the
 * Market came back on Featured. [FolioDialog] keeps what a page saved across that rebuild.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class DialogKeepsPageTest {
    @get:Rule val compose = createComposeRule()

    private var density by mutableStateOf(1f)

    /** A page with a saveable "tab", like the Market's, inside whichever dialog [host] is. */
    private fun setPage(host: @androidx.compose.runtime.Composable (@androidx.compose.runtime.Composable () -> Unit) -> Unit) {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density)) {
                host {
                    var tab by rememberSaveable { mutableStateOf("Featured") }
                    Column {
                        Text(tab, Modifier.testTag("tab"))
                        Text("go", Modifier.testTag("go").clickable { tab = "Settings" })
                    }
                }
            }
        }
    }

    @Test fun `a plain dialog loses its tab when the density changes`() {
        setPage { content -> Dialog(onDismissRequest = {}) { content() } }
        compose.onNodeWithTag("go").performClick()
        compose.onNodeWithTag("tab").assertTextEquals("Settings")
        density = 1.2f
        compose.waitForIdle()
        // This is the bug: kept here so that if Compose ever stops rebuilding the window, the wrapper can go.
        compose.onNodeWithTag("tab").assertTextEquals("Featured")
    }

    @Test fun `the Folio dialog keeps its tab when the density changes`() {
        setPage { content -> FolioDialog(onDismissRequest = {}) { content() } }
        compose.onNodeWithTag("go").performClick()
        compose.onNodeWithTag("tab").assertTextEquals("Settings")
        density = 1.2f
        compose.waitForIdle()
        compose.onNodeWithTag("tab").assertTextEquals("Settings")
        density = 1f
        compose.waitForIdle()
        compose.onNodeWithTag("tab").assertTextEquals("Settings")
    }
}
