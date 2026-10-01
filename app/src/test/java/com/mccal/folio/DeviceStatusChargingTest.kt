package com.mccal.folio

import android.os.BatteryManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceStatusChargingTest {
    // Samsung's battery protection stops at 80% and Android says "not charging", with the cable still in.
    @Test fun `plugged in at a charge limit still counts as charging`() {
        assertTrue(isCharging(BatteryManager.BATTERY_STATUS_NOT_CHARGING, BatteryManager.BATTERY_PLUGGED_USB))
        assertTrue(isCharging(BatteryManager.BATTERY_STATUS_NOT_CHARGING, BatteryManager.BATTERY_PLUGGED_WIRELESS))
    }

    @Test fun `charging, full and unplugged read as they always did`() {
        assertTrue(isCharging(BatteryManager.BATTERY_STATUS_CHARGING, BatteryManager.BATTERY_PLUGGED_AC))
        assertTrue(isCharging(BatteryManager.BATTERY_STATUS_FULL, BatteryManager.BATTERY_PLUGGED_AC))
        assertFalse(isCharging(BatteryManager.BATTERY_STATUS_DISCHARGING, 0))
        assertFalse(isCharging(BatteryManager.BATTERY_STATUS_NOT_CHARGING, 0))
    }
}
