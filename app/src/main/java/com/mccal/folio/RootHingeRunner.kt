package com.mccal.folio

import android.content.Context
import java.io.IOException
import kotlinx.coroutines.launch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** One line from the `su` process, or why there is none. */
internal sealed interface SuLine {
    data class Text(val line: String) : SuLine
    data object Timeout : SuLine
    data object Eof : SuLine
}

/** A running `su` process, as little as the test needs, so a test can stand in for a root manager. */
internal interface SuProcess {
    fun next(timeoutMs: Long): SuLine
    fun exitCode(): Int?
    fun errorText(): String
    fun close()
}

/** Starts `su`. Throws [IOException] when there is no such program, which is how a phone without root answers. */
internal fun interface SuLauncher {
    @Throws(IOException::class) fun start(command: List<String>): SuProcess
}

/** What the root test found. [state] is what Folio remembers; the rest is for the person and for a bug report. */
internal data class RootTestReport(
    val outcome: Outcome, val state: RootState, val suPath: String?, val rootManager: String?,
    val readings: Int, val distinctAngles: Int, val minDegrees: Float, val maxDegrees: Float, val detail: String,
) {
    enum class Outcome { MOVING, STILL, NO_ROOT, DENIED, NO_ANSWER, NO_SENSOR, FAILED }

    /** Plain text for a bug report: the result, which `su` answered, and the counts. Nothing about the person. */
    fun text(device: String, androidVersion: String, folioVersion: String): String = buildString {
        appendLine("Folio root hinge test")
        appendLine("Result: $outcome ($state)")
        appendLine("su: ${suPath ?: "none found"}   root manager: ${rootManager ?: "unknown"}")
        appendLine("Readings: $readings, different angles: $distinctAngles, range: $minDegrees to $maxDegrees")
        appendLine("Device: $device, Android $androidVersion, Folio $folioVersion")
        if (detail.isNotBlank()) appendLine("Detail: $detail")
    }
}

/**
 * The owner's one root test (ADR 0010): start the helper as root, wait for it to say it is ready, listen for a few seconds, and
 * report. Written against [SuLauncher] so the way KernelSU, Magisk and APatch each behave (a prompt that waits, a refusal, no
 * `su` at all) can be played back in tests without any of them installed. All three take `su -c <command>`.
 */
internal object RootHingeRunner {
    const val HELPER_CLASS = "com.mccal.folio.RootHingeHelper"
    const val READY_WAIT_MS = 20_000L
    const val LISTEN_MS = 12_000L
    /** The helper's own lifetime for a test: long enough for the wait and the listening, short enough to end by itself. */
    const val HELPER_SECONDS = 40

    /** Where a root manager may keep `su`, tried in this order. The first is found through PATH, which is what an app normally sees. */
    val SU_CANDIDATES = listOf("su", "/system/bin/su", "/system/xbin/su", "/sbin/su", "/debug_ramdisk/su", "/data/adb/ksu/bin/su", "/data/adb/magisk/su")

    private val OWN_APK = Regex("^/data/app/[A-Za-z0-9_~=/.\\-]+\\.apk$")

    /**
     * The command `su` runs, or null when [apkPath] is not an installed APK path. It is built only from a path Android gave Folio
     * for its own package and a number: no package, setting, file or network answer is in it.
     */
    fun shellCommand(apkPath: String, seconds: Int): String? {
        if (!OWN_APK.matches(apkPath) || ".." in apkPath) return null
        return "CLASSPATH=$apkPath app_process /system/bin $HELPER_CLASS ${seconds.coerceIn(1, 300)}"
    }

    fun run(
        launcher: SuLauncher, apkPath: String, previous: RootState = RootState.UNKNOWN,
        now: () -> Long, readyWaitMs: Long = READY_WAIT_MS, listenMs: Long = LISTEN_MS,
    ): RootTestReport {
        val command = shellCommand(apkPath, HELPER_SECONDS)
            ?: return report(RootTestReport.Outcome.FAILED, previous, null, null, detail = "Folio's own app file was not where Android said.")
        var process: SuProcess? = null
        var su: String? = null
        for (candidate in SU_CANDIDATES) {
            try { process = launcher.start(listOf(candidate, "-c", command)); su = candidate; break } catch (_: IOException) { /* not here */ }
        }
        if (process == null) return report(RootTestReport.Outcome.NO_ROOT, previous, null, null, detail = "No su program was found.")
        try {
            val deadline = now() + readyWaitMs
            var ready = false
            while (!ready) {
                val left = deadline - now()
                if (left <= 0) return report(RootTestReport.Outcome.NO_ANSWER, previous, su, version(launcher, su), detail = "No answer in ${readyWaitMs / 1000} seconds. A root prompt may be waiting.")
                when (val line = process.next(minOf(left, 1_000L))) {
                    SuLine.Timeout -> Unit
                    SuLine.Eof -> {
                        val code = process.exitCode()
                        val why = process.errorText().lineSequence().firstOrNull { it.isNotBlank() }?.take(120).orEmpty()
                        return report(if (code != null && code != 0 && refusedRoot(why)) RootTestReport.Outcome.DENIED else RootTestReport.Outcome.FAILED, previous, su, version(launcher, su),
                            detail = "su ended (exit ${code ?: "?"})${if (why.isNotEmpty()) ": $why" else ""}")
                    }
                    is SuLine.Text -> when (val message = RootHingeProtocol.parse(line.line)) {
                        RootHingeProtocol.Message.Ready -> ready = true
                        is RootHingeProtocol.Message.Reading -> ready = true
                        is RootHingeProtocol.Message.Stopped -> return report(
                            when (message.reason) { "no-sensor", "denied" -> RootTestReport.Outcome.NO_SENSOR; else -> RootTestReport.Outcome.FAILED },
                            previous, su, version(launcher, su), detail = "The helper stopped: ${message.reason}.")
                        null -> Unit // shell noise, such as a banner
                    }
                }
            }
            val gate = RootHingeProtocol.Gate()
            val angles = HashSet<Int>()
            var count = 0
            var low = Float.MAX_VALUE
            var high = -1f
            val end = now() + listenMs
            while (now() < end) {
                val line = process.next(minOf(end - now(), 500L))
                if (line == SuLine.Eof) break
                val message = (line as? SuLine.Text)?.let { RootHingeProtocol.parse(it.line) }
                if (message is RootHingeProtocol.Message.Stopped) break
                if (message is RootHingeProtocol.Message.Reading && gate.accept(message.sample)) {
                    count++
                    angles += (message.sample.angleDegrees * 2).toInt()
                    low = minOf(low, message.sample.angleDegrees); high = maxOf(high, message.sample.angleDegrees)
                }
            }
            val continuous = angles.size >= MIN_CONTINUOUS_ANGLES
            return RootTestReport(
                if (continuous) RootTestReport.Outcome.MOVING else RootTestReport.Outcome.STILL, RootState.READY, su, version(launcher, su),
                count, angles.size, if (count == 0) 0f else low, if (count == 0) 0f else high,
                if (continuous) "" else "Connected, but too few readings to call it continuous: move the hinge while the test runs.",
            )
        } finally {
            process.close()
        }
    }

    /**
     * Whether what `su` printed to stderr is a refusal. A root manager says so in words ("Permission denied", "not allowed"); a
     * process that failed after getting root (a missing class, a crash) does not, and calling that "not allowed" would send the
     * person to their root manager for a fault that is Folio's. Nothing printed at all counts as a refusal: some managers say nothing.
     */
    fun refusedRoot(stderr: String): Boolean =
        stderr.isBlank() || Regex("denied|not allowed|permission|unauthori[sz]ed|not granted|refused|forbidden", RegexOption.IGNORE_CASE).containsMatchIn(stderr)

    /** Different angles (counted in half degrees) that make a feed continuous rather than stepped; the public sensor gives three. */
    const val MIN_CONTINUOUS_ANGLES = 8

    private fun report(outcome: RootTestReport.Outcome, previous: RootState, su: String?, manager: String?, detail: String): RootTestReport {
        val state = when (outcome) {
            RootTestReport.Outcome.NO_ROOT -> RootState.NO_ROOT
            RootTestReport.Outcome.NO_SENSOR -> RootState.NO_SENSOR
            RootTestReport.Outcome.DENIED, RootTestReport.Outcome.NO_ANSWER -> RootState.DENIED
            // Worked before and now does not: say so, so the person knows it stopped rather than never worked.
            RootTestReport.Outcome.FAILED -> if (previous == RootState.READY) RootState.LOST else RootState.DENIED
            else -> RootState.READY
        }
        return RootTestReport(outcome, state, su, manager, 0, 0, 0f, 0f, detail)
    }

    /** What `su -v` says, such as "27.0:MAGISKSU" or "3.3.0:KernelSU", for a bug report. Best effort, a second or two at most. */
    private fun version(launcher: SuLauncher, su: String?): String? {
        if (su == null) return null
        return runCatching {
            val p = launcher.start(listOf(su, "-v"))
            try { ((p.next(1_500L) as? SuLine.Text)?.line?.trim())?.takeIf { it.length in 1..60 && it.all { c -> c.isLetterOrDigit() || c in ".:- _" } } } finally { p.close() }
        }.getOrNull()
    }
}

/** The real thing: runs `su` with [ProcessBuilder] and reads its output on a thread, so a prompt that waits does not block the app. */
internal object ProcessSuLauncher : SuLauncher {
    override fun start(command: List<String>): SuProcess {
        val process = ProcessBuilder(command).start()
        val queue = LinkedBlockingQueue<Any>()
        val eof = Any()
        Thread {
            runCatching { process.inputStream.bufferedReader().forEachLine { queue.put(it) } }
            queue.put(eof)
        }.apply { isDaemon = true }.start()
        return object : SuProcess {
            private var ended = false
            override fun next(timeoutMs: Long): SuLine {
                if (ended) return SuLine.Eof
                val item = queue.poll(timeoutMs, TimeUnit.MILLISECONDS) ?: return SuLine.Timeout
                if (item === eof) { ended = true; return SuLine.Eof }
                return SuLine.Text(item as String)
            }
            override fun exitCode(): Int? = runCatching { process.waitFor(500, TimeUnit.MILLISECONDS); process.exitValue() }.getOrNull()
            override fun errorText(): String = runCatching { process.errorStream.bufferedReader().readText().take(400) }.getOrDefault("")
            override fun close() {
                runCatching { process.outputStream.close() } // the helper reads this: closing it tells it to stop
                process.destroy()
                runCatching { if (!process.waitFor(1, TimeUnit.SECONDS)) process.destroyForcibly() }
            }
        }
    }
}

/** What the last root test found, kept on the phone so the System Bridge page and the broker agree. */
internal object RootHingeStore {
    private const val PREFS = "root_hinge"

    fun state(context: Context): RootState = runCatching {
        RootState.valueOf(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("state", null) ?: return RootState.UNKNOWN)
    }.getOrDefault(RootState.UNKNOWN)

    fun lastReport(context: Context): String? = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("report", null)

    fun save(context: Context, report: RootTestReport, text: String) {
        val edit = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("state", report.state.name).putString("report", text)
        // Only a path that worked is remembered, so the live feed never has to look for su again.
        if (report.state == RootState.READY && report.suPath != null) edit.putString("su", report.suPath) else edit.remove("su")
        // A root that is not ready cannot feed the animation, and a fresh grant starts with the feed off again.
        if (report.state != RootState.READY) edit.putBoolean("use", false)
        edit.apply()
    }

    fun suPath(context: Context): String? = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("su", null)?.takeIf { it in RootHingeRunner.SU_CANDIDATES }

    /** Whether the owner switched the live feed on for the fold animation. Off until they do. */
    fun useInFold(context: Context): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("use", false)
    fun setUseInFold(context: Context, on: Boolean) { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("use", on).apply() }

    /** The feed stopped by itself: remember it, so the page says so and the animation uses the public sensor from now on. */
    fun markLost(context: Context) {
        if (state(context) == RootState.READY) context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("state", RootState.LOST.name).apply()
    }

    /** Forgets the result: back to "not tested". */
    fun clear(context: Context) { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply() }

    /** The test, on the real `su`: [RootHingeRunner.run] with Folio's own APK path, saved. */
    fun test(context: Context): Pair<RootTestReport, String> {
        val report = RootHingeRunner.run(ProcessSuLauncher, context.applicationInfo.sourceDir, state(context), now = { android.os.SystemClock.elapsedRealtime() })
        val text = report.text(android.os.Build.MODEL, android.os.Build.VERSION.RELEASE, WhatsNew.currentVersion(context))
        save(context, report, text)
        return report to text
    }
}

/**
 * Runs the owner's root test where a screen change cannot cancel it. Opening or closing the Fold swaps displays and rebuilds Folio's
 * screen, and a test tied to that screen stopped halfway. This holder belongs to the app process: the page only watches [status].
 */
internal object RootHingeTester {
    sealed interface Status {
        data object Idle : Status
        data object Running : Status
        data class Done(val report: RootTestReport) : Status
    }

    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
    private val _status = kotlinx.coroutines.flow.MutableStateFlow<Status>(Status.Idle)
    val status: kotlinx.coroutines.flow.StateFlow<Status> = _status

    /** Starts the test unless one is already running; the result lands in [status] whichever screen is showing. */
    fun start(context: Context) {
        if (!_status.compareAndSet(_status.value.takeIf { it !is Status.Running } ?: return, Status.Running)) return
        val app = context.applicationContext
        scope.launch {
            _status.value = try { Status.Done(RootHingeStore.test(app).first) } catch (e: Exception) { Status.Idle }
        }
    }

    /** Clears a shown result, so a page opened later starts clean. */
    fun dismiss() { if (_status.value is Status.Done) _status.value = Status.Idle }
}
