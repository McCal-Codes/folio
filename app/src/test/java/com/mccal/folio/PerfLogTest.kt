package com.mccal.folio

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Starting and stopping the recorder on a (simulated) phone: it takes a reading at both ends and gives a report. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PerfLogTest {
    @Test fun `a run reads once at the start and once at the stop, then has a report`() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        assertFalse(PerfLog.status.value.running)
        PerfLog.start(app)
        assertTrue(PerfLog.status.value.running)
        // The sampling thread's first reading; let it run.
        shadowOf(android.os.Looper.getMainLooper()).idle()
        Thread.sleep(300)
        PerfLog.stop()
        val status = PerfLog.status.value
        assertFalse(status.running)
        assertTrue("readings: ${status.readings}", status.readings >= 2)
        assertTrue(status.hasReport)
        val text = PerfLog.reportText()
        assertTrue(text.startsWith("Folio performance report"))
        assertTrue(text.contains("Per minute"))
    }

    @Test fun `starting twice does not start a second run`() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        PerfLog.start(app); PerfLog.start(app)
        PerfLog.stop(); PerfLog.stop()
        assertEquals(false, PerfLog.status.value.running)
    }

    @Test fun `sharing is plain text only, with no file, stream or selector`() {
        val chooser = PerfLog.shareIntent("report", "Share Report")
        assertEquals(android.content.Intent.ACTION_CHOOSER, chooser.action)
        @Suppress("DEPRECATION") val send = chooser.getParcelableExtra<android.content.Intent>(android.content.Intent.EXTRA_INTENT)!!
        assertEquals(android.content.Intent.ACTION_SEND, send.action)
        assertEquals("text/plain", send.type)
        assertEquals("report", send.getStringExtra(android.content.Intent.EXTRA_TEXT))
        assertEquals(null, send.selector)
        assertEquals(null, send.clipData)
        assertFalse(send.hasExtra(android.content.Intent.EXTRA_STREAM))
        assertEquals(setOf(android.content.Intent.EXTRA_TEXT), send.extras!!.keySet())
    }
}
