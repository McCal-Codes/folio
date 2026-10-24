package com.mccal.folio

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** A release build has the no-op twin: the dev source is off, and the Market's client is the stock one. */
@RunWith(RobolectricTestRunner::class)
class DevSourceReleaseTest {
    @Test fun `the dev source is disabled`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        assertFalse(DevSource.enabled(context))
    }

    @Test fun `the client is a plain one`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val client = DevSource.httpClient(context, allowLocalhost = false)
        assertFalse(client.javaClass.name.contains("Dev"))
    }
}
