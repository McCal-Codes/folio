package com.mccal.folio

import androidx.test.core.app.ApplicationProvider
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RootSettingsGrantTest {
    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val pkg = "com.mccal.folio.dev"

    private class Fake(private val lines: List<SuLine>, private val code: Int?, private val error: String = "") : SuProcess {
        private val queue = ArrayDeque(lines)
        var closed = false
        override fun next(timeoutMs: Long): SuLine = queue.removeFirstOrNull() ?: SuLine.Eof
        override fun exitCode() = code
        override fun errorText() = error
        override fun close() { closed = true }
    }

    @Test fun `su runs pm for the one permission and nothing else`() {
        assertEquals("pm grant $pkg android.permission.WRITE_SECURE_SETTINGS", RootSettingsGrantRunner.command(pkg, grant = true))
        assertEquals("pm revoke $pkg android.permission.WRITE_SECURE_SETTINGS", RootSettingsGrantRunner.command(pkg, grant = false))
        listOf("", "a b.c", "x; reboot", "com.mccal.folio && id", "\$(id).a").forEach { assertNull("'$it'", RootSettingsGrantRunner.command(it, true)) }
    }

    @Test fun `a grant that pm accepts is done, and the process is closed`() {
        val started = mutableListOf<List<String>>(); val p = Fake(emptyList(), code = 0)
        val out = RootSettingsGrantRunner.run({ started += it; p }, "su", pkg, grant = true, now = { 0L })
        assertEquals(RootSettingsGrantRunner.Outcome.DONE, out); assertTrue(p.closed)
        assertEquals(listOf("su", "-c", "pm grant $pkg android.permission.WRITE_SECURE_SETTINGS"), started.single())
    }

    @Test fun `output from pm is dropped and a clean exit still counts`() {
        val p = Fake(listOf(SuLine.Text("Success"), SuLine.Timeout), code = 0)
        assertEquals(RootSettingsGrantRunner.Outcome.DONE, RootSettingsGrantRunner.run({ p }, "su", pkg, true, now = { 0L }))
    }

    @Test fun `a refusal from the root manager is denied, any other failure is failed`() {
        assertEquals(RootSettingsGrantRunner.Outcome.DENIED, RootSettingsGrantRunner.run({ Fake(emptyList(), 1, "su: Permission denied") }, "su", pkg, true, now = { 0L }))
        assertEquals(RootSettingsGrantRunner.Outcome.FAILED, RootSettingsGrantRunner.run({ Fake(emptyList(), 255, "Exception occurred while executing 'grant'") }, "su", pkg, true, now = { 0L }))
        assertEquals(RootSettingsGrantRunner.Outcome.FAILED, RootSettingsGrantRunner.run({ Fake(emptyList(), null) }, "su", pkg, true, now = { 0L }).let { if (it == RootSettingsGrantRunner.Outcome.DONE) it else RootSettingsGrantRunner.Outcome.FAILED })
    }

    @Test fun `no su program and a bad package never get as far as a command`() {
        assertEquals(RootSettingsGrantRunner.Outcome.FAILED, RootSettingsGrantRunner.run({ throw IOException("gone") }, "su", pkg, true, now = { 0L }))
        assertEquals(RootSettingsGrantRunner.Outcome.FAILED, RootSettingsGrantRunner.run({ error("must not start") }, "su", "x; id", true, now = { 0L }))
    }

    @Test fun `a process that never finishes times out as failed`() {
        var t = 0L
        val p = object : SuProcess { override fun next(timeoutMs: Long): SuLine { t += timeoutMs; return SuLine.Timeout }; override fun exitCode() = null; override fun errorText() = ""; override fun close() {} }
        assertEquals(RootSettingsGrantRunner.Outcome.FAILED, RootSettingsGrantRunner.run({ p }, "su", pkg, true, now = { t }, waitMs = 5_000L))
    }

    @Test fun `advanced options and the root approval are off until the owner turns them on`() {
        RootHingeStore.clear(context)
        assertFalse(RootHingeStore.advanced(context)); assertFalse(RootHingeStore.allowRootGrant(context)); assertFalse(RootSettingsGrantTester.allowed(context))
        RootHingeStore.setAdvanced(context, true); RootHingeStore.setAllowRootGrant(context, true)
        assertFalse("root has not been tested", RootSettingsGrantTester.allowed(context))
    }

    @Test fun `the root grant needs all three, advanced on and the approval and root that works`() {
        RootHingeStore.clear(context)
        val ready = RootTestReport(RootTestReport.Outcome.MOVING, RootState.READY, "su", "3.3.0:KernelSU", 124, 80, 0f, 179f, "")
        RootHingeStore.save(context, ready, ready.text("SM-F971U", "17", "0.6.9"))
        RootHingeStore.setAllowRootGrant(context, true)
        assertFalse("advanced is off", RootSettingsGrantTester.allowed(context))
        RootHingeStore.setAdvanced(context, true)
        assertTrue(RootSettingsGrantTester.allowed(context))
        RootHingeStore.setAllowRootGrant(context, false)
        assertFalse("the approval is off", RootSettingsGrantTester.allowed(context))
    }

    @Test fun `a root test that fails takes both approvals back`() {
        RootHingeStore.clear(context)
        val ready = RootTestReport(RootTestReport.Outcome.MOVING, RootState.READY, "su", null, 1, 1, 0f, 0f, "")
        RootHingeStore.save(context, ready, "ok"); RootHingeStore.setAdvanced(context, true); RootHingeStore.setAllowRootGrant(context, true); RootHingeStore.setUseInFold(context, true)
        val denied = RootTestReport(RootTestReport.Outcome.DENIED, RootState.DENIED, "su", null, 0, 0, 0f, 0f, "")
        RootHingeStore.save(context, denied, "no")
        assertFalse(RootHingeStore.allowRootGrant(context)); assertFalse(RootHingeStore.useInFold(context))
        assertTrue("advanced options stay on: that is the owner's own choice", RootHingeStore.advanced(context))
    }
}
