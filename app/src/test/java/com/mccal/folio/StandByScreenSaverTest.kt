package com.mccal.folio

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs

/** The rules StandBy's screen saver runs by, without a phone. */
class StandByScreenSaverRulesTest {
    @Test fun `only the laptop pose off the charger holds the screen on`() {
        assertTrue(standByHoldsScreen(StandByWay.HALF_OPEN, charging = false))
        // On a charger the screen follows its timeout, so the phone locks (and the screen saver can take over).
        assertFalse(standByHoldsScreen(StandByWay.HALF_OPEN, charging = true))
        assertFalse(standByHoldsScreen(StandByWay.CHARGING, charging = true))
        assertFalse(standByHoldsScreen(StandByWay.TENT, charging = false))
        assertFalse(standByHoldsScreen(StandByWay.TENT, charging = true))
        assertFalse(standByHoldsScreen(null, charging = false))
    }

    @Test fun `the chosen screen saver is Folio's only when it's on and names this package`() {
        val ours = "com.mccal.folio/com.mccal.folio.StandByScreenSaver"
        assertTrue(screenSaverIsFolio(1, ours, "com.mccal.folio"))
        assertTrue(screenSaverIsFolio(1, "com.android.dreams.basic/.Colors, $ours", "com.mccal.folio"))
        assertFalse(screenSaverIsFolio(0, ours, "com.mccal.folio"))
        assertFalse(screenSaverIsFolio(1, null, "com.mccal.folio"))
        assertFalse(screenSaverIsFolio(1, "com.android.dreams.basic/com.android.dreams.basic.Colors", "com.mccal.folio"))
        // Folio and Folio Dev sit side by side: one's choice isn't the other's.
        assertFalse(screenSaverIsFolio(1, "com.mccal.folio.dev/com.mccal.folio.StandByScreenSaver", "com.mccal.folio"))
        assertFalse(screenSaverIsFolio(1, ours, "com.mccal.folio.dev"))
    }

    @Test fun `a swipe up leaves only from the bottom and only once it has gone far enough`() {
        val height = 1000f
        assertTrue(isSwipeUpToLeave(startY = 950f, y = 850f, height = height, minTravel = 80f))
        assertFalse(isSwipeUpToLeave(startY = 950f, y = 900f, height = height, minTravel = 80f))
        assertFalse(isSwipeUpToLeave(startY = 500f, y = 100f, height = height, minTravel = 80f)) // a swipe in the middle
        assertFalse(isSwipeUpToLeave(startY = 950f, y = 1000f, height = height, minTravel = 80f)) // down, not up
    }

    @Test fun `the clock moves every minute and stays within 6 dp`() {
        val minute = 60_000L
        val shifts = (0 until 16).map { burnInShift(it * minute) }
        assertTrue(shifts.all { (x, y) -> abs(x) <= 6 && abs(y) <= 6 })
        assertTrue(shifts.zipWithNext().all { (a, b) -> a != b })
        assertEquals(burnInShift(0), burnInShift(minute * 8)) // a fixed walk, the same for a given minute
        assertEquals(burnInShift(5 * minute), burnInShift(5 * minute + 59_000))
    }

    /** Readings every 60 ms, as SENSOR_DELAY_UI gives them. */
    private fun PickUpDetector.feed(from: Long, until: Long, x: Float, y: Float, z: Float): Boolean {
        var t = from
        var picked = false
        while (t < until) { picked = add(x, y, z, t) || picked; t += 60 }
        return picked
    }

    @Test fun `resting on its side doesn't leave`() {
        val detector = PickUpDetector()
        assertFalse(detector.feed(0, 5_000, -9.8f, .1f, .2f))
    }

    @Test fun `a tap's jolt doesn't leave`() {
        val detector = PickUpDetector()
        assertFalse(detector.feed(0, 2_000, -9.8f, 0f, 0f))
        assertFalse(detector.add(-9.8f, 2.5f, 3f, 2_000)) // one hard reading from a tap
        assertFalse(detector.feed(2_060, 3_000, -9.8f, 0f, 0f))
    }

    @Test fun `turning it upright leaves`() {
        val detector = PickUpDetector()
        assertFalse(detector.feed(0, 2_000, -9.8f, 0f, 0f))
        assertTrue(detector.feed(2_000, 2_500, 0f, 9.8f, 0f))
    }

    @Test fun `picking it up off a table leaves`() {
        val detector = PickUpDetector()
        assertFalse(detector.feed(0, 2_000, 0f, 0f, 9.8f))
        assertTrue(detector.feed(2_000, 2_500, 0f, 8.5f, 4.9f)) // tilted 60° toward you
    }

    @Test fun `nothing counts before it has settled`() {
        val detector = PickUpDetector()
        assertFalse(detector.feed(0, 400, 0f, 9.8f, 0f))
        assertFalse(detector.feed(400, 900, -9.8f, 0f, 0f))
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StandByScreenSaverTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    /** The first test build crashed here: it reached for a context while being constructed, and took Home down with it. */
    @Test fun `the screen saver is built and created without a crash`() {
        Robolectric.buildService(StandByScreenSaver::class.java).create().destroy()
    }

    @Test fun `Android lists it only while StandBy's charger ways are open`() {
        val component = ComponentName(context, StandByScreenSaver::class.java)
        syncStandByScreenSaver(context, open = true)
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_ENABLED, context.packageManager.getComponentEnabledSetting(component))
        syncStandByScreenSaver(context, open = false)
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, context.packageManager.getComponentEnabledSetting(component))
    }

    @Test fun `Settings reads whether Folio StandBy is the chosen screen saver`() {
        val resolver = context.contentResolver
        Settings.Secure.putInt(resolver, "screensaver_enabled", 1)
        Settings.Secure.putString(resolver, "screensaver_components", "${context.packageName}/${StandByScreenSaver::class.java.name}")
        assertTrue(folioScreenSaverChosen(context))
        Settings.Secure.putInt(resolver, "screensaver_enabled", 0)
        assertFalse(folioScreenSaverChosen(context))
    }
}
