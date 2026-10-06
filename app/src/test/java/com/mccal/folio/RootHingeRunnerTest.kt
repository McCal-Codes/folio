package com.mccal.folio

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Plays back what a root manager's `su` does, on a clock the test controls, so no root manager has to be installed. */
class RootHingeRunnerTest {
    private class Clock { var ms = 0L }

    /** [events] are (milliseconds until it arrives, what arrives). When they run out, [then] is what the process does. */
    private class Scripted(
        private val clock: Clock, events: List<Pair<Long, SuLine>>, private val then: SuLine = SuLine.Timeout,
        private val code: Int? = 0, private val error: String = "",
    ) : SuProcess {
        private val queue = ArrayDeque(events.map { it.first to it.second })
        var closed = false
        override fun next(timeoutMs: Long): SuLine {
            val head = queue.firstOrNull() ?: run { clock.ms += timeoutMs; return then }
            if (head.first <= timeoutMs) { clock.ms += head.first; queue.removeFirst(); return head.second }
            clock.ms += timeoutMs; queue.removeFirst(); queue.addFirst(head.first - timeoutMs to head.second)
            return SuLine.Timeout
        }
        override fun exitCode() = code
        override fun errorText() = error
        override fun close() { closed = true }
    }

    private fun text(s: String) = SuLine.Text(s)
    private val apk = "/data/app/~~abc123==/com.mccal.folio.dev-xyz789==/base.apk"
    private fun readings(count: Int, everyMs: Long = 20) = (0 until count).map { everyMs to text("H ${(it + 1) * 20_000_000L} ${(it * 5f) % 180}") }

    private fun run(launcher: SuLauncher, clock: Clock, previous: RootState = RootState.UNKNOWN) =
        RootHingeRunner.run(launcher, apk, previous, now = { clock.ms })

    @Test fun `a working root manager is READY and moving the hinge shows it is continuous`() {
        val clock = Clock(); val started = mutableListOf<List<String>>()
        val report = run({ cmd ->
            started += cmd
            if (cmd.getOrNull(1) == "-v") Scripted(clock, listOf(0L to text("3.3.0:KernelSU"))) else Scripted(clock, listOf(300L to text("R")) + readings(60), then = SuLine.Eof)
        }, clock)
        assertEquals(RootTestReport.Outcome.MOVING, report.outcome); assertEquals(RootState.READY, report.state)
        assertTrue(report.readings > 30); assertTrue(report.distinctAngles >= RootHingeRunner.MIN_CONTINUOUS_ANGLES)
        assertEquals("3.3.0:KernelSU", report.rootManager); assertEquals("su", report.suPath)
        assertEquals(listOf("su", "-c", RootHingeRunner.shellCommand(apk, RootHingeRunner.HELPER_SECONDS)!!), started.first())
    }

    @Test fun `connected but not moved is READY and says to move the hinge`() {
        val clock = Clock()
        val report = run({ cmd -> if (cmd[1] == "-v") Scripted(clock, emptyList(), then = SuLine.Eof) else Scripted(clock, listOf(200L to text("R"))) }, clock)
        assertEquals(RootTestReport.Outcome.STILL, report.outcome); assertEquals(RootState.READY, report.state)
        assertEquals(0, report.readings); assertTrue(report.detail.contains("move the hinge"))
    }

    @Test fun `a prompt that waits for the owner, as Magisk shows, still counts when answered in time`() {
        val clock = Clock()
        val report = run({ cmd -> if (cmd[1] == "-v") Scripted(clock, listOf(0L to text("27.0:MAGISKSU"))) else Scripted(clock, listOf(9_000L to text("R"))) }, clock)
        assertEquals(RootState.READY, report.state); assertEquals("27.0:MAGISKSU", report.rootManager)
    }

    @Test fun `a prompt nobody answers ends as DENIED with a reason, and the process is closed`() {
        val clock = Clock(); lateinit var main: Scripted
        val report = run({ cmd -> if (cmd[1] == "-v") Scripted(clock, emptyList()) else Scripted(clock, emptyList()).also { main = it } }, clock)
        assertEquals(RootTestReport.Outcome.NO_ANSWER, report.outcome); assertEquals(RootState.DENIED, report.state)
        assertTrue(report.detail.contains("No answer")); assertTrue(main.closed)
    }

    @Test fun `su refusing is DENIED and carries what su said`() {
        val clock = Clock()
        val report = run({ cmd -> if (cmd[1] == "-v") Scripted(clock, emptyList(), then = SuLine.Eof) else Scripted(clock, emptyList(), then = SuLine.Eof, code = 1, error = "su: Permission denied\n") }, clock)
        assertEquals(RootTestReport.Outcome.DENIED, report.outcome); assertEquals(RootState.DENIED, report.state)
        assertTrue(report.detail.contains("Permission denied")); assertTrue(report.detail.contains("exit 1"))
    }

    @Test fun `no su anywhere is NO_ROOT and every place was tried`() {
        val tried = mutableListOf<String>()
        val report = RootHingeRunner.run({ cmd -> tried += cmd[0]; throw IOException("not found") }, apk, now = { 0L })
        assertEquals(RootState.NO_ROOT, report.state); assertEquals(RootHingeRunner.SU_CANDIDATES, tried)
    }

    @Test fun `su found only outside PATH is used, which is how some APatch and older setups look`() {
        val clock = Clock()
        val report = run({ cmd ->
            if (cmd[0] == "su") throw IOException("not in PATH")
            if (cmd[1] == "-v") Scripted(clock, emptyList(), then = SuLine.Eof) else Scripted(clock, listOf(100L to text("R")))
        }, clock)
        assertEquals("/system/bin/su", report.suPath); assertEquals(RootState.READY, report.state)
    }

    @Test fun `a phone with no Samsung sensor, or one that refuses even root, is NO_SENSOR`() {
        for (word in listOf("no-sensor", "denied")) {
            val clock = Clock()
            val report = run({ cmd -> if (cmd[1] == "-v") Scripted(clock, emptyList(), then = SuLine.Eof) else Scripted(clock, listOf(100L to text("E $word"))) }, clock)
            assertEquals(word, RootState.NO_SENSOR, report.state)
        }
    }

    @Test fun `a helper that dies after it worked once is LOST, and on a first test is just DENIED`() {
        fun test(previous: RootState): RootState {
            val clock = Clock()
            return run({ cmd -> if (cmd[1] == "-v") Scripted(clock, emptyList(), then = SuLine.Eof) else Scripted(clock, listOf(100L to text("E died"))) }, clock, previous).state
        }
        assertEquals(RootState.LOST, test(RootState.READY)); assertEquals(RootState.DENIED, test(RootState.UNKNOWN))
    }

    @Test fun `noise before the helper's first line is ignored`() {
        val clock = Clock()
        val report = run({ cmd -> if (cmd[1] == "-v") Scripted(clock, emptyList(), then = SuLine.Eof) else Scripted(clock, listOf(10L to text("Welcome to the root shell"), 10L to text("R"))) }, clock)
        assertEquals(RootState.READY, report.state)
    }

    @Test fun `the command is built from Folio's own app path and a number, and nothing else`() {
        assertNotNull(RootHingeRunner.shellCommand(apk, 40))
        assertEquals("CLASSPATH=$apk app_process /system/bin com.mccal.folio.RootHingeHelper 40", RootHingeRunner.shellCommand(apk, 40))
        assertTrue(RootHingeRunner.shellCommand(apk, 9999)!!.endsWith(" 300")); assertTrue(RootHingeRunner.shellCommand(apk, -5)!!.endsWith(" 1"))
        listOf("", "base.apk", "/data/local/tmp/x.apk", "/data/app/x.apk; reboot", "/data/app/a b/base.apk", "/data/app/a'b/base.apk", "/data/app/\$(id)/base.apk",
            "/data/app/../../system/x.apk", "/data/app/x/base.apk\nrm", "/data/app/x/base.jar", "/data/app/x/base.apk`id`", "/data/app/x|y/base.apk")
            .forEach { assertNull("'$it'", RootHingeRunner.shellCommand(it, 40)) }
    }

    @Test fun `a bad app path never starts su at all`() {
        val report = RootHingeRunner.run({ error("must not start") }, "/sdcard/evil.apk", now = { 0L })
        assertEquals(RootTestReport.Outcome.FAILED, report.outcome)
    }

    @Test fun `the report text names the result and su and holds nothing about the person`() {
        val clock = Clock()
        val report = run({ cmd -> if (cmd[1] == "-v") Scripted(clock, listOf(0L to text("3.3.0:KernelSU"))) else Scripted(clock, listOf(100L to text("R")) + readings(60)) }, clock)
        val t = report.text("SM-F971U", "17", "0.6.9")
        assertTrue(t.contains("Result: MOVING (READY)")); assertTrue(t.contains("su: su")); assertTrue(t.contains("KernelSU")); assertTrue(t.contains("SM-F971U"))
        assertFalse(t.contains(apk))
    }

    @Test fun `the helper prints readings the app parses, and keeps its lifetime in range`() {
        val line = RootHingeHelper.reading(123_456_789L, 142.5f)
        assertEquals("H 123456789 142.50", line)
        assertEquals(RootHingeProtocol.Message.Reading(HingeSample(142.5f, 123_456_789L)), RootHingeProtocol.parse(line))
        assertEquals(1, RootHingeHelper.lifetimeSeconds(emptyArray())); assertEquals(1, RootHingeHelper.lifetimeSeconds(arrayOf("x")))
        assertEquals(1, RootHingeHelper.lifetimeSeconds(arrayOf("0"))); assertEquals(40, RootHingeHelper.lifetimeSeconds(arrayOf("40")))
        assertEquals(300, RootHingeHelper.lifetimeSeconds(arrayOf("99999")))
    }
}
