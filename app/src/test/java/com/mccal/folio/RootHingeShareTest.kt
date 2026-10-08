package com.mccal.folio

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RootHingeShareTest {
    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun report(state: RootState = RootState.READY) = RootTestReport(RootTestReport.Outcome.MOVING, state, "su", "3.3.0:KernelSU", 124, 80, 0f, 179f, "")

    @Test fun `there is nothing to share before a test`() {
        RootHingeStore.clear(context)
        assertNull(RootHingeStore.shareIntent(context))
    }

    @Test fun `the report goes out as plain text and nothing else`() {
        RootHingeStore.clear(context)
        val text = report().text("SM-F971U", "17", "0.6.9")
        RootHingeStore.save(context, report(), text)
        val chooser = RootHingeStore.shareIntent(context)
        assertNotNull(chooser)
        assertEquals(Intent.ACTION_CHOOSER, chooser!!.action)
        val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals(Intent.ACTION_SEND, send.action)
        assertEquals("text/plain", send.type)
        assertEquals(text, send.getStringExtra(Intent.EXTRA_TEXT))
        assertEquals(context.getString(R.string.bridge_root_share_subject), send.getStringExtra(Intent.EXTRA_SUBJECT))
        // The earlier report-sharing fault came from a mail selector and a file stream in the chooser; this has neither.
        assertNull(send.selector)
        assertNull(send.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM))
        assertNull(send.clipData)
        assertNull(send.getStringArrayExtra(Intent.EXTRA_EMAIL))
    }

    @Test fun `the shared report holds nothing about the person`() {
        val text = report().text("SM-F971U", "17", "0.6.9")
        listOf("/data/", "base.apk", "@", "http").forEach { assertFalse(it, text.contains(it)) }
        assertTrue(text.contains("MOVING (READY)") && text.contains("KernelSU"))
    }

    @Test fun `the explanation is shown until it has been read once`() {
        RootHingeStore.clear(context)
        assertFalse(RootHingeStore.explained(context))
        RootHingeStore.setExplained(context)
        assertTrue(RootHingeStore.explained(context))
    }
}
