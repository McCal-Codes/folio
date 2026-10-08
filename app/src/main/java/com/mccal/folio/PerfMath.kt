package com.mccal.folio

import java.util.Locale
import java.util.concurrent.atomic.AtomicIntegerArray
import java.util.concurrent.atomic.AtomicLong

/**
 * The arithmetic behind the Performance log, kept free of Android classes so it can be tested on its own: CPU percent
 * from two readings, frame-time percentiles, the battery drain estimate, and the report text.
 */

/** One reading of Folio and the phone, taken by [PerfLog] about every five seconds. Times are since the log started. */
internal data class PerfSample(
    val atMs: Long,
    /** Folio's own CPU time so far (user plus system, all threads), in ms. */
    val cpuMs: Long,
    val pssKb: Int, val nativePssKb: Int, val javaPssKb: Int, val javaHeapKb: Int,
    val threads: Int,
    val batteryPercent: Int, val chargeUah: Int, val currentUa: Int, val status: Int, val plugged: Int, val tempTenthsC: Int,
    val frames: Long, val janky: Long,
    /** Cumulative ms Folio was in front, the screen was on, and the phone was charging; and folds seen so far. */
    val frontMs: Long, val screenOnMs: Long, val chargingMs: Long, val folds: Int,
)

internal object PerfMath {
    /** The longest a run lasts before it stops itself. */
    const val LIMIT_MS = 6 * 60 * 60_000L
    const val INTERVAL_MS = 5_000L
    /** Memory is the one costly reading (Android walks the page tables), so it is taken every sixth sample. */
    const val MEMORY_EVERY = 6
    /** A drain figure from less than this is mostly rounding in the battery level. */
    const val MIN_DRAIN_MS = 30 * 60_000L

    /** Whether this sample also reads memory. The first one always does, so a short run still has a figure. */
    fun readsMemory(tick: Int) = tick % MEMORY_EVERY == 0

    /** Whether a run that has gone on this long must stop now. */
    fun shouldStop(elapsedMs: Long) = elapsedMs >= LIMIT_MS

    /** CPU time as a percent of one core over the wall time between two readings (so 100 is one busy core, 800 all eight). */
    fun cpuPercent(cpuMsBefore: Long, cpuMsAfter: Long, wallMsBefore: Long, wallMsAfter: Long): Double {
        val wall = wallMsAfter - wallMsBefore
        if (wall <= 0) return 0.0
        return (cpuMsAfter - cpuMsBefore).coerceAtLeast(0) * 100.0 / wall
    }

    /** A frame is janky when it took longer than the frame's own deadline, or [fallbackNs] where the system gave none. */
    fun isJank(totalNs: Long, deadlineNs: Long, fallbackNs: Long) = totalNs > (if (deadlineNs > 0) deadlineNs else fallbackNs)

    fun percent(part: Long, whole: Long): Double = if (whole <= 0) 0.0 else part * 100.0 / whole

    /**
     * The rough drain over a run. Null when it can't mean anything: the phone was plugged in at any point, or the run
     * was shorter than [MIN_DRAIN_MS]. The percent comes from the battery level (whole percents, so coarse); the mAh
     * from the charge counter, which is finer but not on every phone (0 is passed through as "none").
     */
    fun drain(samples: List<PerfSample>): Drain? {
        if (samples.size < 2) return null
        val first = samples.first(); val last = samples.last()
        val ms = last.atMs - first.atMs
        if (ms < MIN_DRAIN_MS) return null
        if (samples.any { it.plugged != 0 || it.chargingMs > 0 }) return null
        val hours = ms / 3_600_000.0
        val percent = (first.batteryPercent - last.batteryPercent) / hours
        val mah = if (first.chargeUah > 0 && last.chargeUah > 0) (first.chargeUah - last.chargeUah) / 1000.0 / hours else null
        return Drain(percent, mah)
    }

    data class Drain(val percentPerHour: Double, val mahPerHour: Double?)

    fun fmt(value: Double, digits: Int = 1) = String.format(Locale.US, "%.${digits}f", value)
}

/**
 * Frame times in half-millisecond bins, so counting one is a single increment: no list, no boxing, nothing the
 * garbage collector sees, which matters because it is called from the draw pipeline.
 */
internal class FrameHistogram {
    private val bins = AtomicIntegerArray(BINS + 1)
    private val total = AtomicLong()
    private val over = AtomicLong()

    fun record(totalNs: Long, janky: Boolean) {
        val bin = (totalNs / BIN_NS).toInt().coerceIn(0, BINS)
        bins.incrementAndGet(bin)
        total.incrementAndGet()
        if (janky) over.incrementAndGet()
    }

    val frames get() = total.get()
    val janky get() = over.get()

    /** The frame time (ms) at or below which [p] percent of frames fall; the top edge of its bin. */
    fun percentileMs(p: Double): Double {
        val n = total.get()
        if (n == 0L) return 0.0
        val target = Math.ceil(n * p / 100.0).toLong().coerceAtLeast(1)
        var seen = 0L
        for (i in 0..BINS) {
            seen += bins.get(i)
            if (seen >= target) return (i + 1) * BIN_NS / 1_000_000.0
        }
        return (BINS + 1) * BIN_NS / 1_000_000.0
    }

    companion object {
        const val BIN_NS = 500_000L
        /** Up to 100 ms; anything slower lands in the last bin. */
        const val BINS = 200
    }
}

/** What Folio is doing that a frame belongs to, so a report can say how smooth each thing was and not only the whole run. */
internal enum class PerfScenario { FOLD, HOME_SWIPE, FOLDER, WIDGET_RESIZE }

/**
 * Counts which scenarios are going on and gives each its own frame histogram. [begin] and [end] nest and may repeat, so two things at once,
 * or a stray [end], never leave a scenario stuck on. A frame counts toward every scenario active when it is drawn.
 */
internal class PerfScenarios {
    private val active = AtomicIntegerArray(PerfScenario.entries.size)
    private val histograms = Array(PerfScenario.entries.size) { FrameHistogram() }

    fun begin(s: PerfScenario) { active.incrementAndGet(s.ordinal) }
    fun end(s: PerfScenario) { active.updateAndGet(s.ordinal) { if (it > 0) it - 1 else 0 } }
    fun isActive(s: PerfScenario) = active.get(s.ordinal) > 0
    fun histogram(s: PerfScenario) = histograms[s.ordinal]

    fun record(totalNs: Long, janky: Boolean) {
        for (i in histograms.indices) if (active.get(i) > 0) histograms[i].record(totalNs, janky)
    }
}

/** Remembers how long each state held, so a report can say what was happening between two readings. */
internal class PerfContext(private val now: () -> Long, frontNow: Boolean = false, screenNow: Boolean = true, chargingNow: Boolean = false) {
    private var front = frontNow; private var screen = screenNow; private var charging = chargingNow
    private var since = now()
    private var frontMs = 0L; private var screenMs = 0L; private var chargingMs = 0L
    @Volatile var folds = 0; private set

    private fun settle() {
        val t = now(); val d = t - since; since = t
        if (front) frontMs += d
        if (screen) screenMs += d
        if (charging) chargingMs += d
    }

    @Synchronized fun setFront(on: Boolean) { settle(); front = on }
    @Synchronized fun setScreen(on: Boolean) { settle(); screen = on }
    @Synchronized fun setCharging(on: Boolean) { settle(); charging = on }
    @Synchronized fun noteFold() { folds++ }
    @Synchronized fun totals(): Triple<Long, Long, Long> { settle(); return Triple(frontMs, screenMs, chargingMs) }

    companion object {
        /** A change of smallest width this large is the phone folding or unfolding, not a rotation or a resize. */
        const val FOLD_DELTA_DP = 120
        fun isFold(smallestWidthBeforeDp: Int, smallestWidthAfterDp: Int) =
            smallestWidthBeforeDp > 0 && Math.abs(smallestWidthAfterDp - smallestWidthBeforeDp) >= FOLD_DELTA_DP
    }
}

internal data class PerfHeader(val version: String, val device: String, val android: String, val screen: String, val startedAt: String, val running: Boolean)

/** The plain-text report: what Folio cost, in numbers a tester can paste into an issue. */
internal object PerfReport {
    /** English on purpose: this goes into a report McCal reads, like the rest of it. */
    private fun scenarioName(s: PerfScenario) = when (s) {
        PerfScenario.FOLD -> "Fold animation" // english-only
        PerfScenario.HOME_SWIPE -> "Home swipe" // english-only
        PerfScenario.FOLDER -> "Folder opening" // english-only
        PerfScenario.WIDGET_RESIZE -> "Widget resize" // english-only
    }

    fun build(header: PerfHeader, samples: List<PerfSample>, histogram: FrameHistogram, scenarios: PerfScenarios? = null): String {
        val sb = StringBuilder()
        sb.append("Folio performance report\n")
        sb.append("Folio ${header.version} on ${header.device} (Android ${header.android}), ${header.screen}\n")
        sb.append("Started ${header.startedAt}${if (header.running) " (still recording)" else ""}\n\n")
        if (samples.size < 2) {
            sb.append("Not enough readings yet. Leave the recording on for a few minutes.\n")
            return sb.toString()
        }
        val first = samples.first(); val last = samples.last()
        val spanMs = last.atMs - first.atMs
        val cpu = cpuSeries(samples)
        sb.append("Duration: ${duration(spanMs)} (${samples.size} readings, every ${PerfMath.INTERVAL_MS / 1000} s)\n\n")

        sb.append("CPU (Folio's own process, percent of one core; 100 = one core fully busy)\n")
        sb.append("  Average: ${PerfMath.fmt(PerfMath.cpuPercent(first.cpuMs, last.cpuMs, first.atMs, last.atMs))} %\n")
        sb.append("  Peak over one reading: ${PerfMath.fmt(cpu.maxOrNull() ?: 0.0)} %\n\n")

        sb.append("Battery (rough: whole-percent level, and only meaningful when not charging)\n")
        val drain = PerfMath.drain(samples)
        when {
            drain != null -> {
                sb.append("  Drain: about ${PerfMath.fmt(drain.percentPerHour)} % per hour")
                drain.mahPerHour?.let { sb.append(", ${PerfMath.fmt(it, 0)} mAh per hour from the charge counter") }
                sb.append("\n  This is the whole phone, not only Folio.\n")
            }
            samples.any { it.plugged != 0 || it.chargingMs > 0 } -> sb.append("  Drain: not estimated, the phone was charging during this run.\n")
            else -> sb.append("  Drain: not estimated, the run is shorter than ${PerfMath.MIN_DRAIN_MS / 60_000} minutes.\n")
        }
        sb.append("  Level ${first.batteryPercent} % to ${last.batteryPercent} %, temperature ${PerfMath.fmt(last.tempTenthsC / 10.0)} C, current now ${last.currentUa / 1000} mA (sign differs by phone)\n\n")

        sb.append("Frames (only while Folio was in front)\n")
        if (histogram.frames == 0L) sb.append("  None drawn: Folio was not in front.\n\n") else {
            sb.append("  Total: ${histogram.frames}, janky (past the frame deadline): ${histogram.janky} (${PerfMath.fmt(PerfMath.percent(histogram.janky, histogram.frames))} %)\n")
            sb.append("  Frame time p50 ${PerfMath.fmt(histogram.percentileMs(50.0))} ms, p95 ${PerfMath.fmt(histogram.percentileMs(95.0))} ms, p99 ${PerfMath.fmt(histogram.percentileMs(99.0))} ms\n\n")
        }

        if (scenarios != null && PerfScenario.entries.any { scenarios.histogram(it).frames > 0 }) {
            sb.append("Frames by what Folio was doing\n") // english-only
            for (sc in PerfScenario.entries) {
                val h = scenarios.histogram(sc)
                if (h.frames == 0L) continue
                sb.append("  ${scenarioName(sc)}: ${h.frames} frames, janky ${h.janky} (${PerfMath.fmt(PerfMath.percent(h.janky, h.frames))} %), p50 ${PerfMath.fmt(h.percentileMs(50.0))} ms, p95 ${PerfMath.fmt(h.percentileMs(95.0))} ms, p99 ${PerfMath.fmt(h.percentileMs(99.0))} ms\n") // english-only
            }
            sb.append("\n")
        }
        sb.append("Memory (Folio's own process)\n")
        val mem = samples.filter { it.pssKb > 0 }
        if (mem.isEmpty()) sb.append("  No reading.\n\n") else {
            val m = mem.last()
            sb.append("  Now: PSS ${m.pssKb / 1024} MB (native ${m.nativePssKb / 1024} MB, Java ${m.javaPssKb / 1024} MB), Java heap in use ${m.javaHeapKb / 1024} MB, ${m.threads} threads\n")
            sb.append("  Peak PSS: ${mem.maxOf { it.pssKb } / 1024} MB\n\n")
        }

        val frontPct = PerfMath.percent(last.frontMs - first.frontMs, spanMs)
        val screenPct = PerfMath.percent(last.screenOnMs - first.screenOnMs, spanMs)
        val chargePct = PerfMath.percent(last.chargingMs - first.chargingMs, spanMs)
        sb.append("Context: Folio in front ${PerfMath.fmt(frontPct, 0)} % of the run, screen on ${PerfMath.fmt(screenPct, 0)} %, charging ${PerfMath.fmt(chargePct, 0)} %, ")
        sb.append("fold or unfold seen ${last.folds - first.folds} times\n\n")

        sb.append("Per minute (cpu = percent of one core; front, screen = percent of that minute)\n")
        sb.append("min  cpu%  jank%  front%  screen%  batt%  PSS MB  folds\n")
        minutes(samples).forEachIndexed { i, row ->
            sb.append(String.format(Locale.US, "%3d  %4.1f  %5s  %6.0f  %7.0f  %5d  %6s  %5d\n", i + 1, row.cpu,
                if (row.frames == 0L) "-" else PerfMath.fmt(row.jank), row.front, row.screen, row.battery, if (row.pssMb > 0) row.pssMb.toString() else "-", row.folds))
        }
        sb.append("\nThese numbers are Folio's own view of itself. Android's own account is `dumpsys batterystats`; see tools/perf-capture.sh.\n")
        sb.append("Nothing in this report left the phone unless you shared it.\n")
        return sb.toString()
    }

    private fun cpuSeries(s: List<PerfSample>) = s.zipWithNext { a, b -> PerfMath.cpuPercent(a.cpuMs, b.cpuMs, a.atMs, b.atMs) }

    internal data class Minute(val cpu: Double, val jank: Double, val frames: Long, val front: Double, val screen: Double, val battery: Int, val pssMb: Int, val folds: Int)

    /** One row per started minute: the readings that end inside it are compared with the last one before it. */
    internal fun minutes(samples: List<PerfSample>): List<Minute> {
        val out = mutableListOf<Minute>()
        val start = samples.first().atMs
        var prev = samples.first()
        var bucket = 0
        val group = mutableListOf<PerfSample>()
        fun flush() {
            if (group.isEmpty()) return
            val last = group.last()
            val wall = last.atMs - prev.atMs
            out += Minute(PerfMath.cpuPercent(prev.cpuMs, last.cpuMs, prev.atMs, last.atMs),
                PerfMath.percent(last.janky - prev.janky, last.frames - prev.frames), last.frames - prev.frames,
                PerfMath.percent(last.frontMs - prev.frontMs, wall), PerfMath.percent(last.screenOnMs - prev.screenOnMs, wall),
                last.batteryPercent, group.lastOrNull { it.pssKb > 0 }?.pssKb?.div(1024) ?: 0, last.folds - prev.folds)
            prev = last; group.clear()
        }
        for (s in samples.drop(1)) {
            val b = ((s.atMs - start - 1) / 60_000L).toInt() // a reading at exactly 60 s closes minute 1
            if (b != bucket) { flush(); bucket = b }
            group += s
        }
        flush()
        return out
    }

    fun duration(ms: Long): String {
        val m = ms / 60_000; val s = ms / 1000 % 60
        return if (m >= 60) "${m / 60} h ${m % 60} min" else "$m min $s s"
    }
}
