package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WifiNameTest {
    @Test fun `the quotes Android puts around a name are dropped`() = assertEquals("Home", wifiNameOrNull("\"Home\""))

    @Test fun `a name without quotes is kept`() = assertEquals("Cafe 5G", wifiNameOrNull("Cafe 5G"))

    @Test fun `the placeholder for a hidden name is no name`() = assertNull(wifiNameOrNull("<unknown ssid>"))

    @Test fun `nothing and blank are no name`() {
        assertNull(wifiNameOrNull(null))
        assertNull(wifiNameOrNull("  "))
        assertNull(wifiNameOrNull("\"\""))
    }
}
