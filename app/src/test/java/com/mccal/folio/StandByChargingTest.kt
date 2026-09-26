package com.mccal.folio

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.window.layout.FoldingFeature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The second way into StandBy: the charger, on any pose, so a phone that does not fold has it too.
 *
 * The decision is a pure function on purpose, because the alternative is a phone on a charger for half a minute per
 * case. What is checked here is the part that is easy to get wrong: which way in wins when both are true, that the
 * switch and the gate really stop the charger being looked at, and that unplugging ends it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StandByChargingTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    /** Folio as it is installed from a release: not the `.dev` build, and no supporter code redeemed. */
    private val plainBuild = object : ContextWrapper(context) {
        override fun getPackageName() = "com.mccal.folio"
    }

    @Test fun `the charger brings StandBy up with no fold at all`() {
        assertEquals(StandByEntry.CHARGING,
            standByEntry(pose = null, poseEnabled = true, charging = true, chargingEnabled = true))
    }

    @Test fun `the hinge wins when the phone is charging and half open at once`() {
        assertEquals("one StandBy, and the posture is the more specific fact", StandByEntry.POSE,
            standByEntry(FoldingFeature.Orientation.HORIZONTAL, poseEnabled = true, charging = true, chargingEnabled = true))
        // And it still comes up half open when the charger is switched off, as it always has.
        assertEquals(StandByEntry.POSE,
            standByEntry(FoldingFeature.Orientation.VERTICAL, poseEnabled = true, charging = false, chargingEnabled = false))
    }

    @Test fun `unplugging is the way out, without a touch`() {
        assertNull(standByEntry(pose = null, poseEnabled = true, charging = false, chargingEnabled = true))
    }

    @Test fun `the charger is not looked at while the switch is off`() {
        assertNull(standByEntry(pose = null, poseEnabled = true, charging = true, chargingEnabled = false))
    }

    @Test fun `a shut gate keeps the charger out of it, switch on or not`() {
        assertFalse("the gate is shut until 0.6.8", FeatureGate.STANDBY_CHARGING.open)
        Supporter.remove(plainBuild)
        assertFalse("a release build with no supporter code doesn't watch the charger",
            standByChargingOn(plainBuild, setting = true))
        assertNull(standByEntry(pose = null, poseEnabled = true, charging = true,
            chargingEnabled = standByChargingOn(plainBuild, setting = true)))
        assertFalse(standByChargingOn(plainBuild, setting = false))
    }

    @Test fun `the charger waits for the phone to be left alone, the hinge only for it to settle`() {
        assertEquals(2_500L, standByEnterDelayMs(StandByEntry.POSE))
        assertEquals("half a minute, the number Settings tells the user", 30_000L,
            standByEnterDelayMs(StandByEntry.CHARGING))
        assertTrue(standByEnterDelayMs(StandByEntry.CHARGING) > standByEnterDelayMs(StandByEntry.POSE))
    }

    @Test fun `the switch is saved, and a layout from before it starts off`() {
        val saved = LayoutLoadTest().schema8Fixture()
        assertFalse("nobody gets a screen saver they didn't ask for",
            decodeLauncherState(saved.toString(), legacyRaw = null).standByCharging)
        assertTrue(decodeLauncherState(saved.put("standByCharging", true).toString(), legacyRaw = null).standByCharging)
    }
}
