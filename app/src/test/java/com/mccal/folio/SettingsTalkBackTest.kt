package com.mccal.folio

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What TalkBack hears in Settings, from the beta.4 smoke test on the Fold8 (1 Oct 2026): the search field had no
 * name, every row on the phone said "Not selected", and the Home preview read out about 25 of its parts, a made-up
 * battery level among them, before General.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h900dp")
class SettingsTalkBackTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `an empty search field is named by its placeholder`() {
        compose.setContent { IosSearchField("", {}, "Search") }
        compose.onNode(hasSetTextAction()).assert(hasText("Search"))
    }

    @Test fun `once something is typed the field reads that, not the placeholder`() {
        compose.setContent { IosSearchField("spot", {}, "Search") }
        compose.onNode(hasSetTextAction()).assert(hasText("spot")).assert(!hasText("Search"))
    }

    @Test fun `a row on the phone is a place to go, not a choice`() {
        compose.setContent { TweakRow(Icons.Rounded.Settings, FolioColors.Value.Gray, "General", "row") {} }
        compose.onNodeWithTag("row").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Selected))
    }

    @Test fun `the sidebar marks the open page as a selected tab`() {
        compose.setContent {
            Column {
                TweakRow(Icons.Rounded.Settings, FolioColors.Value.Gray, "General", "open", selected = true, chevron = false) {}
                TweakRow(Icons.Rounded.Settings, FolioColors.Value.Gray, "Accessibility", "other", selected = false, chevron = false) {}
            }
        }
        compose.onNodeWithTag("open").assertIsSelected()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
        compose.onNodeWithTag("other").assertIsNotSelected()
    }

    @Test fun `the Home preview is one picture with a name`() {
        compose.setContent { MiniHomePreview(null, LauncherState(), 176.dp) }
        compose.onNodeWithTag("customization-home-preview").assert(hasContentDescription("Preview of Home"))
        compose.onAllNodes(hasClickAction()).assertCountEquals(0)
        compose.onAllNodesWithText("Search").assertCountEquals(0)
    }
}
