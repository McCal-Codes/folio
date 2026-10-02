package com.mccal.folio

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Roadmap page, drawn from a saved copy written here so the test never reaches GitHub: the release in progress as
 * Now, a later release as Next with its theme, the titled lists, and shipped releases folded under one row.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h2000dp")
class RoadmapPageTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val release = SoftwareUpdate.installedVersion(context).substringBefore('-').split('.').take(3).joinToString(".")

    private fun save(vararg sections: String) {
        File(context.filesDir, "roadmap.json").writeText("""{"roadmap":1,"sections":[${sections.joinToString(",")}]}""")
    }
    private fun item(title: String, status: String, beta: Boolean = false) =
        """{"title":"$title","detail":"About $title","status":"$status"${if (beta) ""","beta":true""" else ""}}"""
    private fun page() = compose.setContent { Column(Modifier.verticalScroll(rememberScrollState())) { ComingSoonPage() } }

    @Before fun fresh() { File(context.filesDir, "roadmap.json").delete() }

    @Test fun `the release in progress, the next one and the lists are grouped, and an item in beta says so`() {
        save("""{"release":"$release","items":[${item("Beta thing", "building", beta = true)},${item("Still building", "building")}]}""",
            """{"release":"99.0.0","subtitle":"Theme","items":[${item("Future thing", "planned")}]}""",
            """{"title":"Later","items":[${item("Someday thing", "planned")}]}""",
            """{"release":"0.0.1","items":[${item("Old thing", "done")}]}""")
        page()
        compose.onNodeWithText("NOW").assertIsDisplayed()
        compose.onNodeWithText("FOLIO $release").assertIsDisplayed()
        compose.onNodeWithText("NEXT").assertIsDisplayed()
        compose.onNodeWithText("FOLIO 99.0.0 · THEME").assertIsDisplayed()
        compose.onNodeWithText("LATER").assertIsDisplayed()
        compose.onNodeWithText("In beta").assertIsDisplayed()
        compose.onNodeWithText("In progress").assertIsDisplayed()
        compose.onAllNodesWithText("Done").assertCountEquals(0)
        compose.onNodeWithText("1 of 2 ready to try").assertExists()
    }

    @Test fun `someone on another release is not told items in a beta are ready to try`() {
        save("""{"release":"0.0.1","items":[${item("Old thing", "done")}]}""",
            """{"release":"99.0.0","items":[${item("Beta thing", "building", beta = true)},${item("Other", "planned")}]}""")
        page()
        compose.onNodeWithText("In beta").assertIsDisplayed()
        compose.onNode(hasText("ready to try", substring = true)).assertDoesNotExist()
    }

    @Test fun `items are plain list rows, not buttons`() {
        save("""{"release":"$release","items":[${item("Beta thing", "building", beta = true)}]}""")
        page()
        compose.onNode(hasText("Beta thing") and hasClickAction()).assertDoesNotExist()
    }

    @Test fun `a release you are on that is finished moves to Shipped, and a finished newer one says all done`() {
        save("""{"release":"$release","items":[${item("Mine", "done")}]}""",
            """{"release":"99.0.0","items":[${item("One", "done")},${item("Two", "done")}]}""",
            """{"release":"0.0.1","items":[${item("Old thing", "done")}]}""")
        page()
        compose.onNodeWithText("All 2 done").assertExists()
        compose.onNodeWithText("Mine").assertDoesNotExist() // folded under Earlier releases
        compose.onNodeWithText("Earlier releases").assertHasClickAction()
        compose.onNodeWithText("Old thing").assertDoesNotExist()
        compose.onNodeWithText("Earlier releases").performClick()
        compose.onNodeWithText("Folio 0.0.1").performClick()
        compose.onNodeWithText("Old thing").assertExists()
        compose.onNodeWithText("Folio $release").performClick()
        compose.onNodeWithText("Mine").assertExists()
    }

    @Test fun `the page says when it was last updated`() {
        save("""{"release":"$release","items":[${item("One", "building")}]}""")
        page()
        compose.onNode(hasText("Updated", substring = true)).assertExists()
        compose.onNode(hasText("Can't reach", substring = true)).assertDoesNotExist()
    }
}
