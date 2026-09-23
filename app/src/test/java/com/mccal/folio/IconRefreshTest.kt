package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Test

/** #19: a theme that swaps icons (Theme Park) changes the configuration's assets sequence, and nothing else Folio watched. */
class IconRefreshTest {
    @Test fun `reads the assets sequence from the configuration's text`() {
        // As Configuration.toString prints it on Android 17, trimmed.
        assertEquals(42, assetsSequence("{1.0 310mcc260mnc [en_US] ldltr sw561dp w561dp h839dp 420dpi nrml long port finger -keyb/v/h -nav/h winConfig={ } s.8 fontWeightAdjustment=0 as.42}"))
    }

    @Test fun `is 0 when the configuration doesn't say`() {
        assertEquals(0, assetsSequence("{1.0 ?mcc?mnc [en_US] ldltr sw561dp}"))
    }

    @Test fun `isn't fooled by other fields that end in as`() {
        assertEquals(0, assetsSequence("{1.0 has.3 alias.7}"))
    }
}
