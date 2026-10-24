package com.mccal.folio

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The swap where Home collects its state: off unless Screenshot Mode is on and a rule is set, and the real apps come back after. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ScreenshotDisguiseSeamTest {
    @get:Rule val compose = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()
    private fun app(pkg: String, label: String) = AppEntry("$pkg/.Main", label, Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888))
    private val camera = app("com.android.camera", "Camera")
    private val reddit = app("com.reddit.frontpage", "Reddit")
    private val state = LauncherState(apps = listOf(camera, reddit), loading = false)

    private fun install(pkg: String, system: Boolean) = shadowOf(context.packageManager).installPackage(PackageInfo().apply {
        packageName = pkg
        applicationInfo = ApplicationInfo().apply { packageName = pkg; flags = if (system) ApplicationInfo.FLAG_SYSTEM else 0 }
    })

    @Before fun setUp() {
        install(camera.packageName, system = true)
        install(reddit.packageName, system = false)
    }

    @After fun tearDown() {
        ScreenshotMode.set(false)
        PrivateRecording.setEnabled(context, false)
        ScreenshotSwap.set(context, DisguiseRules())
    }

    @Test fun `the system flags come from the package manager`() {
        assertEquals(setOf(camera.packageName), systemPackages(context, setOf(camera.packageName, reddit.packageName, "com.not.installed")))
    }

    @Test fun `Home draws the swap only while Screenshot Mode is on, and the real apps after`() {
        var shown: LauncherState? = null
        compose.setContent { shown = rememberDisguised(state) }
        compose.waitForIdle()
        assertSame("no rules, mode off: the state itself", state, shown)

        ScreenshotSwap.set(context, DisguiseRules(chosen = setOf(reddit.id)))
        compose.waitForIdle()
        assertSame("a rule but the mode off: still the state itself", state, shown)

        ScreenshotMode.set(true)
        compose.waitUntil(5_000) { shown?.apps?.any { it.iconFrom != null } == true }
        val swapped = shown!!.apps.single { it.id == reddit.id }
        assertEquals("Camera", swapped.label)
        assertEquals(reddit.component, swapped.component)

        ScreenshotMode.set(false)
        compose.waitUntil(5_000) { shown === state }
    }

    @Test fun `the private view swaps on its own, without Screenshot Mode, and nothing with no apps chosen`() {
        var shown: LauncherState? = null
        compose.setContent { shown = rememberDisguised(state) }
        PrivateRecording.setEnabled(context, true)
        PrivateRecording.report(true)
        compose.waitForIdle()
        assertSame("recording but no apps chosen: nothing swapped", state, shown)

        ScreenshotSwap.set(context, DisguiseRules(chosen = setOf(reddit.id)))
        compose.waitUntil(5_000) { shown?.apps?.any { it.iconFrom != null } == true }
        assertEquals("Camera", shown!!.apps.single { it.id == reddit.id }.label)
        assertFalse("Screenshot Mode is untouched, so clocks keep the real time", ScreenshotMode.on.value)

        PrivateRecording.setEnabled(context, false)
        compose.waitUntil(5_000) { shown === state }
    }
}
