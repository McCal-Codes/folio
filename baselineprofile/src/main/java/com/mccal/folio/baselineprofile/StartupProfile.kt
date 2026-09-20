package com.mccal.folio.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import org.junit.Rule
import org.junit.Test

/**
 * Records the code paths Folio runs on the way to a usable Home, so ART can compile them ahead of
 * time instead of interpreting them on the first launch after an install or an update. A launcher
 * pays that cost in front of the user every time the phone comes back to Home, which is why this
 * covers Home itself and the two surfaces a swipe away, not just the activity opening.
 */
class StartupProfile {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun homeAndFirstGestures() = rule.collect(packageName = targetPackage) {
        pressHome()
        startActivityAndWait()

        // Home has drawn. Let it settle before touching it: the wallpaper, the widgets and the
        // clock all arrive on their own coroutines.
        device.waitForIdle()

        // Up from the bottom edge opens the App Library; scroll it so the list and its icons are
        // compiled too, then come back.
        device.swipe(device.displayWidth / 2, device.displayHeight - 8, device.displayWidth / 2, device.displayHeight / 3, 12)
        device.waitForIdle()
        device.findObject(By.scrollable(true))?.scroll(Direction.DOWN, 1f)
        device.waitForIdle()
        device.pressBack()
        device.waitForIdle()
    }

    private val targetPackage: String
        get() = InstrumentationRegistry.getArguments().getString("targetAppId") ?: PROFILE_APP_ID

    private companion object {
        /**
         * The id the plugin's own build types carry (set in app/build.gradle.kts so a locally signed build can
         * install beside the real Folio). Asking the instrumentation for its target would name this test itself,
         * and the benchmark would then force-stop its own process.
         */
        const val PROFILE_APP_ID = "com.mccal.folio.profile"
    }
}
