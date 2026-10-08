package com.mccal.folio

import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RootHingeFeedTest {
    private val apk = "/data/app/~~abc123==/com.mccal.folio.dev-xyz789==/base.apk"
    private val clock = AtomicLong()

    /** Plays [lines] one by one, then [then]; each Timeout moves the fake clock on by what was asked for. */
    private class Scripted(private val clock: AtomicLong, lines: List<SuLine>, private val then: SuLine, private val advanceMs: Long = 0) : SuProcess {
        private val queue = ArrayDeque(lines)
        @Volatile var closed = false
        override fun next(timeoutMs: Long): SuLine {
            if (closed) return SuLine.Eof
            val head = queue.removeFirstOrNull()
            if (head != null) { clock.addAndGet(advanceMs); return head }
            if (then == SuLine.Timeout) { clock.addAndGet(timeoutMs); Thread.sleep(1) }
            if (then == SuLine.Eof) clock.addAndGet(advanceMs)
            return then
        }
        override fun exitCode() = 0
        override fun errorText() = ""
        override fun close() { closed = true }
    }

    private fun t(s: String) = SuLine.Text(s)
    private fun reading(i: Int) = t("H ${(i + 1) * 20_000_000L} ${i * 4f}")

    private class Run(val samples: CopyOnWriteArrayList<HingeSample> = CopyOnWriteArrayList(), val lost: CopyOnWriteArrayList<String> = CopyOnWriteArrayList(), val done: CountDownLatch = CountDownLatch(1))

    private fun feed(run: Run, launcher: SuLauncher) = RootHingeFeed(launcher, apk, "su", { run.samples += it }, { run.lost += it; run.done.countDown() }, { clock.get() })

    @Test fun `readings reach the animation in order`() {
        val run = Run(); val p = Scripted(clock, listOf(t("R")) + (0 until 5).map(::reading), SuLine.Eof, advanceMs = 10)
        feed(run, { p }).start()
        assertTrue(run.done.await(5, TimeUnit.SECONDS))
        assertEquals(5, run.samples.size); assertEquals(listOf(0f, 4f, 8f, 12f, 16f), run.samples.map { it.angleDegrees })
        assertEquals(listOf("ended"), run.lost)
    }

    @Test fun `the command is the fixed one and carries the longest helper lifetime`() {
        val run = Run(); val seen = CopyOnWriteArrayList<List<String>>()
        feed(run, { c -> seen += c; Scripted(clock, listOf(t("R")), SuLine.Eof) }).start()
        assertTrue(run.done.await(5, TimeUnit.SECONDS))
        assertEquals(listOf("su", "-c", RootHingeRunner.shellCommand(apk, RootHingeHelper.MAX_SECONDS)!!), seen.first())
    }

    @Test fun `the helper saying it stopped is a loss with its reason`() {
        val run = Run()
        feed(run, { Scripted(clock, listOf(t("R"), t("E denied")), SuLine.Timeout) }).start()
        assertTrue(run.done.await(5, TimeUnit.SECONDS)); assertEquals(listOf("denied"), run.lost)
    }

    @Test fun `no su, or no answer in time, is a loss`() {
        val none = Run(); feed(none, { throw IOException("gone") }).start()
        assertTrue(none.done.await(5, TimeUnit.SECONDS)); assertEquals(listOf("no-su"), none.lost)
        val silent = Run(); feed(silent, { Scripted(clock, emptyList(), SuLine.Timeout) }).start()
        assertTrue(silent.done.await(5, TimeUnit.SECONDS)); assertEquals(listOf("no-answer"), silent.lost)
    }

    @Test fun `a helper that reaches the end of its lifetime is started again, not lost`() {
        val run = Run(); val starts = CopyOnWriteArrayList<Int>(); val restarted = CountDownLatch(2)
        val feed = feed(run) {
            starts += 1; restarted.countDown()
            // The first run lasts its whole lifetime (the fake clock moves on that far); the second just idles.
            if (starts.size == 1) Scripted(clock, listOf(t("R")), SuLine.Eof, advanceMs = RootHingeFeed.RESTART_AFTER_MS + 1)
            else Scripted(clock, listOf(t("R")), SuLine.Timeout)
        }
        feed.start()
        assertTrue(restarted.await(5, TimeUnit.SECONDS)); feed.stop()
        assertTrue(run.lost.isEmpty())
    }

    @Test fun `stopping closes the process and is never reported as a loss`() {
        val run = Run(); val p = Scripted(clock, listOf(t("R")), SuLine.Timeout)
        val feed = feed(run, { p }); feed.start()
        Thread.sleep(50); feed.stop(); Thread.sleep(100)
        assertTrue(p.closed); assertTrue(run.lost.isEmpty())
    }

    @Test fun `a bad app path never starts su`() {
        val run = Run()
        RootHingeFeed({ error("must not start") }, "/sdcard/evil.apk", "su", { run.samples += it }, { run.lost += it; run.done.countDown() }, { 0L }).start()
        assertTrue(run.done.await(5, TimeUnit.SECONDS)); assertEquals(listOf("path"), run.lost); assertFalse(run.samples.isNotEmpty())
    }
}
