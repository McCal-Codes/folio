package com.mccal.folio

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class ScreenshotSwapPageTest {
    @get:Rule val compose = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()
    private fun app(pkg: String, label: String) = AppEntry("$pkg/.Main", label, Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888))
    private val camera = app("com.android.camera", "Camera")
    private val reddit = app("com.reddit.frontpage", "Reddit")

    private fun install(pkg: String, system: Boolean) = shadowOf(context.packageManager).installPackage(PackageInfo().apply {
        packageName = pkg
        applicationInfo = ApplicationInfo().apply { packageName = pkg; flags = if (system) ApplicationInfo.FLAG_SYSTEM else 0 }
    })

    @Before fun setUp() {
        install(camera.packageName, system = true)
        install(reddit.packageName, system = false)
        ScreenshotSwap.set(context, DisguiseRules())
        compose.setContent { Column { ScreenshotSwapPage(listOf(camera, reddit)) } }
        compose.waitUntil(5_000) { runCatching { compose.onNodeWithTag("screenshot-swap-${reddit.id}").assertIsEnabled() }.isSuccess }
    }

    @After fun tearDown() { ScreenshotSwap.set(context, DisguiseRules()) }

    @Test fun `choosing an app saves it, and a stock app can't be chosen`() {
        compose.onNodeWithTag("screenshot-swap-${reddit.id}").performClick()
        compose.waitForIdle()
        assertEquals(setOf(reddit.id), ScreenshotSwap.rules.value.chosen)
        compose.onNodeWithTag("screenshot-swap-${camera.id}").assertIsNotEnabled()
        compose.onNodeWithText(context.getString(R.string.screenshot_swap_system_app)).assertExists()
    }

    @Test fun `all third-party apps is saved and locks the list`() {
        compose.onNodeWithTag("screenshot-swap-all", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        assertTrue(ScreenshotSwap.rules.value.allThirdParty)
        compose.onNodeWithTag("screenshot-swap-${reddit.id}").assertIsNotEnabled()
    }
}
