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
    private val process = OpenSu()
    private val launcher = SuLauncher { started.countDown(); process }
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

    @Test fun `after the timeline stops, a changed switch does nothing more`() {
        val t = running()
        t.stop()
        // The feed's own thread closes the helper once more as it ends; wait for it, or that late close lands after the reset below.
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (Thread.getAllStackTraces().keys.any { it.name == "folio-root-hinge" && it.isAlive } && System.nanoTime() < deadline) Thread.sleep(5)
        process.closed = false
        SystemBridge.setOff(context, true)
        settle()
        assertFalse("a stopped timeline still reacted to the switch", process.closed)
    }
}
