package com.mccal.folio

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Automatic renewal contacts Folio's server about the code, so it is off until the person turns it on. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SupporterAutoRenewOptInTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Before fun clean() = Supporter.remove(context)

    @Test fun `it is off until the person turns it on`() {
        assertFalse(Supporter.autoRenewOn(context))
        Supporter.setAutoRenew(context, true)
        assertTrue(Supporter.autoRenewOn(context))
        Supporter.setAutoRenew(context, false)
        assertFalse(Supporter.autoRenewOn(context))
    }

    @Test fun `removing the code turns it off again`() {
        Supporter.setAutoRenew(context, true)
        Supporter.remove(context)
        assertFalse(Supporter.autoRenewOn(context))
    }

    @Test fun `with it off, nothing is asked even for a code that has ended`() {
        // No stored code either, but the switch is the first thing checked: off means the worker is never contacted.
        assertFalse(SupporterRenewal.check(context))
    }
}
