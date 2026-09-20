package com.mccal.folio

import androidx.compose.runtime.saveable.SaverScope
import org.junit.Assert.assertEquals
import org.junit.Test

/** What the widget picker was asked for has to survive a fold, and come back in the right fields. */
class WidgetRequestTest {
    private fun roundTrip(request: WidgetRequest): WidgetRequest {
        val saved = with(WidgetRequest.Saver) { SaverScope { true }.save(request) }
        return WidgetRequest.Saver.restore(requireNotNull(saved))!!
    }

    @Test fun `a request for one app's widget at an exact cell comes back whole`() {
        val back = roundTrip(WidgetRequest(slot = 4, targetIndex = 17, exactTarget = true,
            packageName = "com.example.weather", profileSerial = 10L))
        assertEquals(4, back.slot)
        assertEquals(17, back.targetIndex)
        assertEquals(true, back.exactTarget)
        assertEquals("com.example.weather", back.packageName)
        assertEquals(10L, back.profileSerial)
        assertEquals(null, back.stackSlot)
        assertEquals(false, back.toToday)
    }

    @Test fun `adding to a stack and adding to Today stay told apart`() {
        assertEquals(9, roundTrip(WidgetRequest(stackSlot = 9)).stackSlot)
        assertEquals(null, roundTrip(WidgetRequest(stackSlot = 9)).toToday.takeIf { it })
        assertEquals(true, roundTrip(WidgetRequest(toToday = true)).toToday)
        assertEquals(null, roundTrip(WidgetRequest(toToday = true)).stackSlot)
    }

    @Test fun `showing every app again keeps the spot it was going to`() {
        val request = WidgetRequest(slot = 3, targetIndex = 12, exactTarget = true,
            packageName = "com.example.weather", profileSerial = 10L)
        request.anyApp()
        assertEquals(null, request.packageName)
        assertEquals(null, request.profileSerial)
        assertEquals(false, request.exactTarget)
        // The spot is still the one the picker was opened for.
        assertEquals(3, request.slot)
        assertEquals(12, request.targetIndex)
    }
}
