package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceStateTentTest {
    private fun state(id: Int, name: String) = "DeviceState{identifier=$id, name='$name', app_accessible=true, cancel_when_requester_not_on_top=false}"

    @Test fun `the states a Fold8 reports read as tent or not`() {
        assertEquals(true, DeviceStateTent.isTent(state(1, "TENT")))
        for ((id, name) in listOf(0 to "CLOSED", 3 to "OPENED", 2 to "HALF_OPENED", 4 to "CONCURRENT_INNER_DEFAULT", 5 to "CONCURRENT_OUTER_DEFAULT"))
            assertEquals(name, false, DeviceStateTent.isTent(state(id, name)))
    }

    @Test fun `the name decides, not the number, and only the exact word`() {
        assertEquals(true, DeviceStateTent.isTent(state(9, "tent")))
        assertEquals(false, DeviceStateTent.isTent(state(1, "TENT_MODE")))
        assertEquals(false, DeviceStateTent.isTent(state(1, "NOT_TENT")))
    }

    @Test fun `anything unreadable is can't tell`() {
        assertNull(DeviceStateTent.isTent(null))
        assertNull(DeviceStateTent.isTent("DeviceState{}"))
        assertNull(DeviceStateTent.isTent(42))
        assertNull(DeviceStateTent.isTent(""))
    }

    @Test fun `the callback reports state changes and ignores the rest`() {
        val seen = mutableListOf<Boolean?>()
        val me = Any()
        DeviceStateTent.invoke({ seen += it }, me, "onDeviceStateChanged", arrayOf(state(1, "TENT")))
        DeviceStateTent.invoke({ seen += it }, me, "onDeviceStateChanged", arrayOf(state(0, "CLOSED")))
        DeviceStateTent.invoke({ seen += it }, me, "onDeviceStateChanged", arrayOf<Any?>("nonsense"))
        DeviceStateTent.invoke({ seen += it }, me, "onDeviceStateChanged", null)
        DeviceStateTent.invoke({ seen += it }, me, "onSupportedStatesChanged", arrayOf(listOf(state(1, "TENT"))))
        assertEquals(listOf(true, false, null, null), seen)
    }

    @Test fun `the callback answers the Object methods a proxy must`() {
        val me = Any(); val other = Any()
        assertEquals(true, DeviceStateTent.invoke({}, me, "equals", arrayOf(me)))
        assertEquals(false, DeviceStateTent.invoke({}, me, "equals", arrayOf(other)))
        assertEquals(System.identityHashCode(me), DeviceStateTent.invoke({}, me, "hashCode", null))
        assertTrue(DeviceStateTent.invoke({}, me, "toString", null).toString().isNotEmpty())
        assertFalse(DeviceStateTent.invoke({}, me, "equals", null) == true)
    }
}
