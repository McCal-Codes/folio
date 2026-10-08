package com.mccal.folio

import android.os.BatteryManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The charging island comes up when the cable goes in, whatever the battery says at a charge limit. */
class ChargingIslandTest {
    private val usb = BatteryManager.BATTERY_PLUGGED_USB

    @Test fun `plugging in at a charge limit starts charging`() {
        // Samsung's battery protection holds the battery, so Android says "not charging" with the cable in.
        assertTrue(startsCharging(wasCharging = false, status = BatteryManager.BATTERY_STATUS_NOT_CHARGING, plugged = usb))
    }

    @Test fun `plugging in normally starts charging`() {
        assertTrue(startsCharging(wasCharging = false, status = BatteryManager.BATTERY_STATUS_CHARGING, plugged = usb))
    }

    @Test fun `the battery topping back up at the limit isn't a new plug-in`() {
        assertFalse(startsCharging(wasCharging = true, status = BatteryManager.BATTERY_STATUS_CHARGING, plugged = usb))
    }

    @Test fun `the first reading is the state it was already in`() {
        assertFalse(startsCharging(wasCharging = null, status = BatteryManager.BATTERY_STATUS_CHARGING, plugged = usb))
    }

    @Test fun `running on battery doesn't start charging`() {
        assertFalse(startsCharging(wasCharging = false, status = BatteryManager.BATTERY_STATUS_DISCHARGING, plugged = 0))
    }
}
