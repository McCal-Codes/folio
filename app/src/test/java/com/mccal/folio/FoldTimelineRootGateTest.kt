package com.mccal.folio

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The root hinge feed must stop the moment the owner's switches say it may not run: "Allow system access" off (the kill switch),
 * or the root options turned off. It is checked once at start, so without a listener a running feed would carry on until Home
 * closed. A fake `su` that stays open stands in for the root helper; it records whether the feed closed it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FoldTimelineRootGateTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    /** An `su` that answers ready and then waits, like a helper that is running and has nothing to say yet. */
    private class OpenSu : SuProcess {
        @Volatile var closed = false
        private var sentReady = false
        override fun next(timeoutMs: Long): SuLine {
            if (closed) return SuLine.Eof
            if (!sentReady) { sentReady = true; return SuLine.Text("R") }
            Thread.sleep(5)
            return SuLine.Timeout
        }
        override fun exitCode() = 0
        override fun errorText() = ""
        override fun close() { closed = true }
    }

    private val started = CountDownLatch(1)
    /** Every helper the timeline started, in order: a restart is a second one. */
    private val processes = java.util.concurrent.CopyOnWriteArrayList<OpenSu>()
    private val process get() = processes.first()
    private val launcher = SuLauncher { OpenSu().also { processes += it; started.countDown() } }
    private var timeline: FoldTimeline? = null

    @Before fun root() {
        // The feed refuses an app file that is not under /data/app, so Robolectric's is replaced by a real-looking one.
        context.applicationInfo.sourceDir = "/data/app/~~abc123==/com.mccal.folio.dev-xyz789==/base.apk"
        listOf("system_bridge", "root_hinge", "folio").forEach { context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit() }
        context.getSharedPreferences("root_hinge", Context.MODE_PRIVATE).edit()
            .putString("state", RootState.READY.name).putString("su", RootHingeRunner.SU_CANDIDATES.first())
            .putBoolean("advanced", true).putBoolean("use", true).commit()
    }

    @After fun stop() { timeline?.stop() }

    private fun running(): FoldTimeline {
        val t = FoldTimeline(context, launcher).also { timeline = it }
        t.start()
        assertTrue("the root feed never started", started.await(5, TimeUnit.SECONDS))
        assertFalse("the feed should be running", process.closed)
        return t
    }

    private fun settle() = shadowOf(android.os.Looper.getMainLooper()).idle()

    /** The feed starts on its own thread, so a test waits a moment for the launcher to be called. */
    private fun waitFor(check: () -> Boolean): Boolean {
        val end = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < end) { if (check()) return true; Thread.sleep(10) }
        return false
    }

    @Test fun `turning off system access stops the root feed at once`() {
        val t = running()
        SystemBridge.setOff(context, true)
        settle()
        assertTrue("the root helper kept running after the kill switch", process.closed)
        assertTrue("the fold animation was not told to carry on without it", t.wake.tryReceive().isSuccess)
    }

    @Test fun `turning off the root options stops the root feed`() {
        running()
        RootHingeStore.setUseInFold(context, false)
        settle()
        assertTrue(process.closed)
    }

    @Test fun `turning off advanced options stops the root feed`() {
        running()
        RootHingeStore.setAdvanced(context, false)
        settle()
        assertTrue(process.closed)
    }

    @Test fun `an unrelated setting leaves the root feed running`() {
        running()
        context.getSharedPreferences("root_hinge", Context.MODE_PRIVATE).edit().putString("report", "x").commit()
        context.getSharedPreferences("system_bridge", Context.MODE_PRIVATE).edit().putBoolean("something_else", true).commit()
        settle()
        assertFalse("the feed was stopped by a setting that does not matter", process.closed)
    }

    @Test fun `turning system access back on starts the root feed again`() {
        running()
        SystemBridge.setOff(context, true); settle()
        assertTrue(process.closed)
        SystemBridge.setOff(context, false); settle()
        assertTrue("the feed was not started again", waitFor { processes.size == 2 })
        assertFalse("the new helper should be running", processes[1].closed)
    }

    @Test fun `turning on the root options while Home is open starts the feed`() {
        context.getSharedPreferences("root_hinge", Context.MODE_PRIVATE).edit().putBoolean("use", false).commit()
        val t = FoldTimeline(context, launcher).also { timeline = it }
        t.start()
        assertTrue("the feed started while the option was off", processes.isEmpty())
        RootHingeStore.setUseInFold(context, true); settle()
        assertTrue("the feed did not start when the option was turned on", waitFor { processes.size == 1 })
    }

    @Test fun `a change that leaves the gate open does not start a second feed`() {
        running()
        context.getSharedPreferences("root_hinge", Context.MODE_PRIVATE).edit().putString("report", "x").commit()
        settle()
        Thread.sleep(100)
        assertTrue("a second helper was started", processes.size == 1)
    }

    @Test fun `the feed does not start again while system access is still off`() {
        running()
        SystemBridge.setOff(context, true); settle()
        RootHingeStore.setUseInFold(context, false); settle()
        RootHingeStore.setUseInFold(context, true); settle()
        Thread.sleep(100)
        assertTrue("the feed was started while system access is off", processes.size == 1)
    }

    @Test fun `after the timeline stops, a changed switch does nothing more`() {
        val t = running()
        t.stop()
        process.closed = false
        SystemBridge.setOff(context, true)
        settle()
        assertFalse("a stopped timeline still reacted to the switch", process.closed)
    }
}
