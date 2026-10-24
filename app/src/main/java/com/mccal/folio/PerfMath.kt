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
    /** Android's thermal status (0 none to 6 shutdown), or -1 where the phone gives none. */
    val thermal: Int = -1,
    /** Battery saver: 1 on, 0 off, -1 not read. */
    val powerSave: Int = -1,
    /** The display's current mode rate in Hz, or 0 where it could not be read. */
    val refreshHz: Int = 0,
    /** Which of Folio's own services were on, as [PerfFeature] bits. */
    val features: Int = 0,
)

/** Folio's own services and switches worth knowing about when judging battery: one bit each in [PerfSample.features]. */
internal object PerfFeature {
    const val ISLAND_LISTENER = 1
    const val GESTURES_SERVICE = 2
    const val SMART_INK = 4
    const val PRIVATE_ENABLED = 8
    const val PRIVATE_ACTIVE = 16

    /** English on purpose, like the rest of the report. */
    val names = listOf(
        ISLAND_LISTENER to "Dynamic Island (notification listener connected)", // english-only
        GESTURES_SERVICE to "Gestures service (also hosts panels and the island over other apps)", // english-only
        SMART_INK to "Smart clock ink service", // english-only
        PRIVATE_ENABLED to "Private While Recording switched on", // english-only
        PRIVATE_ACTIVE to "Private view showing (a recording was seen)", // english-only
    )
}

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

    /** The first, last, lowest and highest of a series; null when the series is empty (nothing was reported). */
    data class Span(val first: Double, val last: Double, val min: Double, val max: Double) {
        val change get() = last - first
    }

    fun span(values: List<Double>): Span? =
        if (values.isEmpty()) null else Span(values.first(), values.last(), values.min(), values.max())

    /**
     * How much the charge counter fell between the first and last readings that have one, in mAh (positive = the
     * battery gave charge, negative = it took charge). Null when the phone gives no counter or fewer than two readings have it.
     */
    fun chargeUsedMah(samples: List<PerfSample>): Double? {
        val have = samples.filter { it.chargeUah > 0 }
        if (have.size < 2) return null
        return (have.first().chargeUah - have.last().chargeUah) / 1000.0
    }

    /** The percent of readings for which [test] holds; 0 for no readings. Readings are evenly spaced, so this is also a share of time. */
    fun share(samples: List<PerfSample>, test: (PerfSample) -> Boolean): Double =
        percent(samples.count(test).toLong(), samples.size.toLong())

    /** PowerManager's thermal status as a word. -1 is how this log marks a phone that gave none. */
    fun thermalName(status: Int): String = when (status) {
        0 -> "None" // english-only
        1 -> "Light" // english-only
        2 -> "Moderate" // english-only
        3 -> "Severe" // english-only
        4 -> "Critical" // english-only
        5 -> "Emergency" // english-only
        6 -> "Shutdown" // english-only
        else -> UNAVAILABLE
    }

    const val UNAVAILABLE = "Unavailable" // english-only

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
internal enum class PerfScenario { FOLD, HOME_SWIPE, FOLDER, WIDGET_RESIZE, SHEET, MENU, ICON_MOVE }

/**
 * Counts which scenarios are going on and gives each its own frame histogram. [begin] and [end] nest and may repeat, so two things at once,
 * or a stray [end], never leave a scenario stuck on. A frame counts toward every scenario active when it is drawn.
 */
internal class PerfScenarios(private val wallMs: (() -> Long)? = null, private val cpuMs: (() -> Long)? = null) {
    private val n = PerfScenario.entries.size
    private val active = AtomicIntegerArray(n)
    private val histograms = Array(n) { FrameHistogram() }

    // Touched only by begin, end and the sampling thread, under the lock; never by the per-frame path.
    private val lock = Any()
    private val startWall = LongArray(n); private val startCpu = LongArray(n)
    private val doneWall = LongArray(n); private val doneCpu = LongArray(n)
    private val currentCount = IntArray(n); private val currentSum = LongArray(n)
    private val tempCount = IntArray(n); private val tempSum = LongArray(n)

    /** What one scenario cost: time it was going on, Folio's CPU time in that time, and the battery readings that fell inside it. */
    data class Usage(val activeMs: Long, val cpuMs: Long, val currentReadings: Int, val avgCurrentUa: Long, val tempReadings: Int, val avgTempTenthsC: Long)

    fun begin(s: PerfScenario) {
        synchronized(lock) {
            if (active.getAndIncrement(s.ordinal) == 0 && wallMs != null) { startWall[s.ordinal] = wallMs.invoke(); startCpu[s.ordinal] = cpuMs?.invoke() ?: 0 }
        }
    }

    fun end(s: PerfScenario) {
        synchronized(lock) {
            if (active.get(s.ordinal) <= 0) return
            if (active.decrementAndGet(s.ordinal) == 0 && wallMs != null) {
                doneWall[s.ordinal] += wallMs.invoke() - startWall[s.ordinal]
                doneCpu[s.ordinal] += ((cpuMs?.invoke() ?: 0) - startCpu[s.ordinal]).coerceAtLeast(0)
            }
        }
    }

    fun isActive(s: PerfScenario) = active.get(s.ordinal) > 0
    fun histogram(s: PerfScenario) = histograms[s.ordinal]

    fun record(totalNs: Long, janky: Boolean) {
        for (i in histograms.indices) if (active.get(i) > 0) histograms[i].record(totalNs, janky)
    }

    /** One battery reading (0 means the phone gave none), counted for every scenario going on right now. */
    fun noteReading(currentUa: Int, tempTenthsC: Int) {
        synchronized(lock) {
            for (i in 0 until n) if (active.get(i) > 0) {
                if (currentUa != 0) { currentCount[i]++; currentSum[i] += currentUa }
                if (tempTenthsC > 0) { tempCount[i]++; tempSum[i] += tempTenthsC }
            }
        }
    }

    /** Time and CPU include a scenario still going on. All zero when the log has no clock (nothing was started). */
    fun usage(s: PerfScenario): Usage = synchronized(lock) {
        val i = s.ordinal
        var wall = doneWall[i]; var cpu = doneCpu[i]
        if (wallMs != null && active.get(i) > 0) { wall += wallMs.invoke() - startWall[i]; cpu += ((cpuMs?.invoke() ?: 0) - startCpu[i]).coerceAtLeast(0) }
        Usage(wall, cpu, currentCount[i], if (currentCount[i] > 0) currentSum[i] / currentCount[i] else 0,
            tempCount[i], if (tempCount[i] > 0) tempSum[i] / tempCount[i] else 0)
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
        PerfScenario.SHEET -> "Page or sheet sliding in" // english-only
        PerfScenario.MENU -> "Menu or alert opening" // english-only
        PerfScenario.ICON_MOVE -> "Moving an icon" // english-only
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
        sb.append("What changed, first reading to last\n") // english-only
        whatChanged(samples).forEach { sb.append("  ").append(it).append('\n') }
        sb.append('\n')

        sb.append("CPU (Folio's own process, percent of one core; 100 = one core fully busy)\n")
        sb.append("  Average: ${PerfMath.fmt(PerfMath.cpuPercent(first.cpuMs, last.cpuMs, first.atMs, last.atMs))} %\n")
        sb.append("  Peak over one reading: ${PerfMath.fmt(cpu.maxOrNull() ?: 0.0)} %\n\n")

        sb.append("Battery (rough: whole-percent level, and only meaningful when not charging)\n")
        val drain = PerfMath.drain(samples)
        when {
            drain != null -> {
                sb.append("  Drain: about ${PerfMath.fmt(drain.percentPerHour)} % per hour") // english-only
                drain.mahPerHour?.let { sb.append(", ${PerfMath.fmt(it, 0)} mAh per hour from the charge counter") } // english-only
                sb.append("\n  This is the whole phone, not only Folio.\n")
            }
            samples.any { it.plugged != 0 || it.chargingMs > 0 } -> sb.append("  Drain: not estimated, the phone was charging during this run.\n")
            else -> sb.append("  Drain: not estimated, the run is shorter than ${PerfMath.MIN_DRAIN_MS / 60_000} minutes.\n")
        }
        sb.append("  Level ${first.batteryPercent} % to ${last.batteryPercent} %, temperature ${PerfMath.fmt(last.tempTenthsC / 10.0)} C, current now ${last.currentUa / 1000} mA (sign differs by phone)\n\n")

        sb.append("Phone state during the run (as the phone reported it)\n") // english-only
        deviceState(samples).forEach { sb.append("  ").append(it).append('\n') }
        sb.append('\n')

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
                usageLine(scenarios.usage(sc))?.let { sb.append("    ").append(it).append('\n') }
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

    private fun signed(v: Double, digits: Int = 1) = (if (v >= 0) "+" else "") + PerfMath.fmt(v, digits)

    /** What one scenario cost, or null when the log had no clock for it. */
    internal fun usageLine(u: PerfScenarios.Usage): String? {
        if (u.activeMs <= 0) return null
        val battery = if (u.currentReadings == 0 && u.tempReadings == 0) "no battery reading fell inside it (readings are ${PerfMath.INTERVAL_MS / 1000} s apart)" // english-only
        else "${u.currentReadings} battery readings inside it" + // english-only
            (if (u.currentReadings > 0) ", average current ${u.avgCurrentUa / 1000} mA (sign differs by phone)" else "") + // english-only
            (if (u.tempReadings > 0) ", temperature ${PerfMath.fmt(u.avgTempTenthsC / 10.0)} C" else "") // english-only
        return "Going on for ${PerfMath.fmt(u.activeMs / 1000.0)} s, Folio CPU ${PerfMath.fmt(u.cpuMs / 1000.0)} s " + // english-only
            "(${PerfMath.fmt(PerfMath.percent(u.cpuMs, u.activeMs))} % of one core); $battery" // english-only
    }

    /** The first reading against the last, one line per thing, saying Unavailable where the phone gave nothing. Facts only. */
    internal fun whatChanged(samples: List<PerfSample>): List<String> {
        val first = samples.first(); val last = samples.last()
        val out = mutableListOf<String>()
        fun ints(label: String, a: Int, b: Int, ok: (Int) -> Boolean, unit: String) {
            out += if (ok(a) && ok(b)) "$label: $a$unit to $b$unit (${signed((b - a).toDouble(), 0)})" else "$label: ${PerfMath.UNAVAILABLE}" // english-only
        }
        ints("Battery level", first.batteryPercent, last.batteryPercent, { it >= 0 }, " %") // english-only
        val mah = PerfMath.chargeUsedMah(samples)
        val counters = samples.filter { it.chargeUah > 0 }
        out += if (mah == null) "Charge counter: ${PerfMath.UNAVAILABLE}" // english-only
        else "Charge counter: ${PerfMath.fmt(counters.first().chargeUah / 1000.0, 0)} mAh to ${PerfMath.fmt(counters.last().chargeUah / 1000.0, 0)} mAh (${signed(-mah, 0)} mAh)" // english-only
        val temps = PerfMath.span(samples.filter { it.tempTenthsC > 0 }.map { it.tempTenthsC / 10.0 })
        out += if (temps == null) "Temperature: ${PerfMath.UNAVAILABLE}" // english-only
        else "Temperature: ${PerfMath.fmt(temps.first)} C to ${PerfMath.fmt(temps.last)} C (${signed(temps.change)} C)" // english-only
        val th = samples.filter { it.thermal >= 0 }
        out += if (th.isEmpty()) "Thermal status: ${PerfMath.UNAVAILABLE}" else "Thermal status: ${PerfMath.thermalName(th.first().thermal)} to ${PerfMath.thermalName(th.last().thermal)}" // english-only
        fun plug(s: PerfSample) = if (s.plugged != 0) "plugged in" else "on battery" // english-only
        out += "Power: ${plug(first)} to ${plug(last)}" // english-only
        val ps = samples.filter { it.powerSave >= 0 }
        fun onOff(v: Int) = if (v == 1) "on" else "off" // english-only
        out += if (ps.isEmpty()) "Battery saver: ${PerfMath.UNAVAILABLE}" else "Battery saver: ${onOff(ps.first().powerSave)} to ${onOff(ps.last().powerSave)}" // english-only
        ints("Display refresh rate", first.refreshHz, last.refreshHz, { it > 0 }, " Hz") // english-only
        val pss = samples.filter { it.pssKb > 0 }
        out += if (pss.isEmpty()) "Folio memory (PSS): ${PerfMath.UNAVAILABLE}" else "Folio memory (PSS): ${pss.first().pssKb / 1024} MB to ${pss.last().pssKb / 1024} MB" // english-only
        ints("Folio threads", first.threads, last.threads, { it > 0 }, "") // english-only
        out += "Folio CPU time: ${PerfMath.fmt((last.cpuMs - first.cpuMs).coerceAtLeast(0) / 1000.0)} s over the run" // english-only
        val flipped = PerfFeature.names.filter { (bit, _) -> (first.features and bit) != (last.features and bit) }
        out += if (flipped.isEmpty()) "Folio services: none switched on or off" // english-only
        else flipped.joinToString("; ") { (bit, name) -> "$name went ${if (last.features and bit != 0) "on" else "off"}" } // english-only
        return out
    }

    /** Readings that describe the phone around Folio: heat, power, display and which of Folio's services were running. */
    internal fun deviceState(samples: List<PerfSample>): List<String> {
        val first = samples.first(); val last = samples.last()
        val out = mutableListOf<String>()
        val temps = PerfMath.span(samples.filter { it.tempTenthsC > 0 }.map { it.tempTenthsC / 10.0 })
        out += if (temps == null) "Battery temperature: ${PerfMath.UNAVAILABLE}" // english-only
        else "Battery temperature: lowest ${PerfMath.fmt(temps.min)} C, highest ${PerfMath.fmt(temps.max)} C, ${signed(temps.change)} C from first to last" // english-only
        val th = samples.filter { it.thermal >= 0 }
        out += if (th.isEmpty()) "Thermal status: ${PerfMath.UNAVAILABLE}" // english-only
        else "Thermal status: highest ${PerfMath.thermalName(th.maxOf { it.thermal })}, above None for ${PerfMath.fmt(PerfMath.share(th) { it.thermal > 0 }, 0)} % of readings" // english-only
        val ps = samples.filter { it.powerSave >= 0 }
        out += if (ps.isEmpty()) "Battery saver: ${PerfMath.UNAVAILABLE}" else "Battery saver: on for ${PerfMath.fmt(PerfMath.share(ps) { it.powerSave == 1 }, 0)} % of readings" // english-only
        val hz = samples.filter { it.refreshHz > 0 }.map { it.refreshHz }
        out += if (hz.isEmpty()) "Display refresh rate: ${PerfMath.UNAVAILABLE}" // english-only
        else "Display refresh rate: ${hz.min()} to ${hz.max()} Hz (the display's mode, not the instant rate an adaptive panel is drawing at)" // english-only
        val spanMs = last.atMs - first.atMs
        val onMs = (last.screenOnMs - first.screenOnMs).coerceIn(0, spanMs)
        out += "Screen: on ${duration(onMs)}, off ${duration(spanMs - onMs)}" // english-only
        out += "Folio services, share of readings they were on:" // english-only
        PerfFeature.names.forEach { (bit, name) -> out += "  $name: ${PerfMath.fmt(PerfMath.share(samples) { it.features and bit != 0 }, 0)} %" } // english-only
        out += "A service being on is not the same as it drawing something; Android reports whether it is connected." // english-only
        return out
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
        return if (m >= 60) "${m / 60} h ${m % 60} min" else "$m min $s s" // english-only
    }
}
