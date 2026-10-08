package com.mccal.folio

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The widget gallery's ✕ had two names for TalkBack: Close as what a double tap does, and Back as the button. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h900dp")
class WidgetGalleryCloseTest {
    @get:Rule val compose = createComposeRule()

    private val personal = AppProfile(0, "Personal", isPersonal = true, isWork = false, quiet = false, unlocked = true, available = true)

    @Test fun `the close button is called Close, and nothing is called Back`() {
        compose.setContent {
            VisualWidgetPicker(emptyList(), listOf(personal), personal, {}, {}, hiddenForDrag = false, footprint = { WidgetSpan(2, 2) },
                onBack = {}, onTap = {}, onBuiltin = {}, onDragStart = { _, _ -> }, onDrag = {}, onDrop = {}, onCancelDrag = {})
        }
        compose.onAllNodesWithContentDescription("Close").assertCountEquals(1)
        compose.onAllNodesWithContentDescription("Back").assertCountEquals(0)
    }
}
