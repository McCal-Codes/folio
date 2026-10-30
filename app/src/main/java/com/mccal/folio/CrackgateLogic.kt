package com.mccal.folio

import kotlin.random.Random

/*
 * Crackgate: a joke easter egg, a pretend Duo hinge fault drawn over the screen. This file is only the rules it follows, with no UI and
 * no Android classes. The rules are built so the joke can never flicker: it appears once, changes at most a few times, never closer
 * together than MIN_STEP_GAP_MS, never in red, never at all with reduce motion on, and clears itself.
 */

/** A shake counts only when the acceleration is above this many g. */
internal const val SHAKE_THRESHOLD_G = 2.7f

/** Two hard samples closer together than this are one jolt, not two shakes. */
internal const val SHAKE_GAP_MIN_MS = 180L

/** The second hard sample must come within this long of the first. */
internal const val SHAKE_WINDOW_MS = 1000L

/** How many separate hard samples make a shake gesture. */
internal const val SHAKES_NEEDED = 2

/** The fault clears itself after this long (10 minutes). */
internal const val AUTO_CLEAR_MS = 10 * 60 * 1000L

/** The most changes a fault goes through after it appears. */
internal const val MAX_GLITCH_STEPS = 4

/** No two changes are ever closer together than this. */
internal const val MIN_STEP_GAP_MS = 2500L

/** The shortest wait before a change. It is not below [MIN_STEP_GAP_MS], so that rule holds by construction. */
internal const val STEP_DELAY_MIN_MS = 2600L

/** The longest wait before a change. */
internal const val STEP_DELAY_MAX_MS = 6000L

/** A press has to be held this long to start it, so a plain tap never does. */
internal const val HOLD_TO_START_MS = 900L

/** A surprise appearance waits at least this many days after the last one. */
internal const val SURPRISE_MIN_GAP_DAYS = 1

/** The fixed thin height of the green line, as a percent of the screen height. */
internal const val LINE_GREEN_HEIGHT_PERCENT = 0.8f

/** The most the sampled roll can be; rolls run 0 to this. */
private const val ROLL_MAX = 99

/** Where the bright part of a bar starts, below the top of the bar, in percent. */
private const val BRIGHT_OFFSET_PERCENT = 7f

/**
 * Counts hard shakes of the phone. Feed it every accelerometer sample; [onSample] returns true once, when [SHAKES_NEEDED] hard samples
 * have come, each at least [SHAKE_GAP_MIN_MS] after the one before, the last within [SHAKE_WINDOW_MS] of the first. It then resets.
 */
internal class CrackgateShake {
    private var firstMs = 0L
    private var lastMs = 0L
    private var count = 0

    /** Forgets any shake in progress. */
    fun reset() {
        count = 0
        firstMs = 0L
        lastMs = 0L
    }

    /** Takes one sample at [timeMs] with [magnitudeG] in g. Returns true when the shake gesture is complete. */
    fun onSample(timeMs: Long, magnitudeG: Float): Boolean {
        if (magnitudeG <= SHAKE_THRESHOLD_G) return false
        if (count > 0 && timeMs < lastMs) reset() // the clock went backwards
        if (count > 0 && timeMs - lastMs < SHAKE_GAP_MIN_MS) return false // still the same jolt
        if (count == 0 || timeMs - firstMs > SHAKE_WINDOW_MS) {
            // Nothing in progress, or the first one is too old to count: this sample starts a new try.
            firstMs = timeMs
            lastMs = timeMs
            count = 1
        } else {
            lastMs = timeMs
            count++
        }
        if (count >= SHAKES_NEEDED) {
            reset()
            return true
        }
        return false
    }
}

/** The colors a fault is ever drawn in. [RED] exists only so a test can prove it is never used: red flashes are a seizure risk. */
internal enum class CrackgateColor { CYAN, GREEN, RED }

/**
 * The kinds of fault. [BAR_CYAN] is a bright cyan-white bar from the hinge to the right edge (as in the real photos), [BAR_CYAN_LEFT] is
 * its mirror, [LINE_GREEN] is a thin full-width green line (a reported bend-test fault) and [HAIRLINE_ONLY] is only the thin line down
 * the hinge. There is no red kind.
 */
internal enum class CrackgateKind { BAR_CYAN, BAR_CYAN_LEFT, LINE_GREEN, HAIRLINE_ONLY }

/** How likely each kind is, in percent; adds up to 100. */
internal val CRACKGATE_WEIGHTS: Map<CrackgateKind, Int> = linkedMapOf(
    CrackgateKind.BAR_CYAN to 60,
    CrackgateKind.BAR_CYAN_LEFT to 15,
    CrackgateKind.LINE_GREEN to 15,
    CrackgateKind.HAIRLINE_ONLY to 10,
)

/** The color each kind is drawn in. None is red. */
internal val CRACKGATE_COLOR_FAMILY: Map<CrackgateKind, CrackgateColor> = mapOf(
    CrackgateKind.BAR_CYAN to CrackgateColor.CYAN,
    CrackgateKind.BAR_CYAN_LEFT to CrackgateColor.CYAN,
    CrackgateKind.LINE_GREEN to CrackgateColor.GREEN,
    CrackgateKind.HAIRLINE_ONLY to CrackgateColor.CYAN,
)

/** Picks a kind from [roll], 0 to 99 (outside that is clamped), by [CRACKGATE_WEIGHTS]. */
internal fun crackgateKind(roll: Int): CrackgateKind {
    val r = roll.coerceIn(0, ROLL_MAX)
    var upTo = 0
    for ((kind, weight) in CRACKGATE_WEIGHTS) {
        upTo += weight
        if (r < upTo) return kind
    }
    return CrackgateKind.BAR_CYAN
}

/** One drawn fault: the kind plus where and how big, all in percent of the screen (degrees for the tilt). */
internal data class CrackgateLook(
    val kind: CrackgateKind,
    val barTopPercent: Float,
    val barEndGapPercent: Float,
    val tiltDegrees: Float,
    val brightFromPercent: Float,
    val barHeightPercent: Float,
)

private const val BAR_TOP_MIN = 42
private const val BAR_TOP_MAX = 68
private const val END_GAP_MAX = 8
private const val TILT_MIN = -3.5f
private const val TILT_SPAN = 4.0f
private const val BAR_HEIGHT_MIN = 5
private const val BAR_HEIGHT_MAX = 8

/** Draws a random look. The same seeded [random] always gives the same look. */
internal fun randomCrackgateLook(random: Random): CrackgateLook {
    val kind = crackgateKind(random.nextInt(ROLL_MAX + 1))
    val top = random.nextInt(BAR_TOP_MIN, BAR_TOP_MAX + 1).toFloat()
    val endGap = random.nextInt(0, END_GAP_MAX + 1).toFloat()
    val tilt = TILT_MIN + random.nextFloat() * TILT_SPAN
    val barHeight = random.nextInt(BAR_HEIGHT_MIN, BAR_HEIGHT_MAX + 1).toFloat()
    return CrackgateLook(
        kind = kind,
        barTopPercent = top,
        barEndGapPercent = endGap,
        tiltDegrees = tilt,
        brightFromPercent = top + BRIGHT_OFFSET_PERCENT,
        barHeightPercent = if (kind == CrackgateKind.LINE_GREEN) LINE_GREEN_HEIGHT_PERCENT else barHeight,
    )
}

/** The small changes a fault can go through while it is up. */
internal enum class CrackgateStep { BAR_HEIGHT, BAR_LENGTH, HAIRLINE_BRIGHT, ICON_STRIP }

/** One planned change: wait [delayMs] after the previous change (or after appearing), then do [step]. */
internal data class CrackgateGlitch(val delayMs: Long, val step: CrackgateStep)

/**
 * Plans the changes after the fault appears. Empty when [reduceMotion] is on or [glitchy] is off. Otherwise 1 to [MAX_GLITCH_STEPS]
 * changes, each waiting [STEP_DELAY_MIN_MS] to [STEP_DELAY_MAX_MS], so none are closer than [MIN_STEP_GAP_MS], and never the same
 * step twice in a row.
 */
internal fun planCrackgateGlitches(random: Random, reduceMotion: Boolean, glitchy: Boolean): List<CrackgateGlitch> {
    if (reduceMotion || !glitchy) return emptyList()
    val steps = CrackgateStep.entries
    val count = random.nextInt(1, MAX_GLITCH_STEPS + 1)
    val plan = ArrayList<CrackgateGlitch>(count)
    var previous: CrackgateStep? = null
    repeat(count) {
        val choices = steps.filter { it != previous }
        val step = choices[random.nextInt(choices.size)]
        plan += CrackgateGlitch(random.nextLong(STEP_DELAY_MIN_MS, STEP_DELAY_MAX_MS + 1), step)
        previous = step
    }
    return plan
}

/** The most changes per second the plan can ever make. Far under three a second (WCAG 2.3.1). */
internal fun crackgateChangesPerSecondAtMost(): Double = 1000.0 / MIN_STEP_GAP_MS

/** True once the fault has been up for [AUTO_CLEAR_MS], or when the clock went backwards. */
internal fun crackgateShouldClear(startedAtMs: Long, nowMs: Long): Boolean =
    nowMs < startedAtMs || nowMs - startedAtMs >= AUTO_CLEAR_MS

/**
 * Whether the fault may appear by itself (a surprise). Only when the egg and the surprise are both on, on Home, not recording or on a
 * call, at least [SURPRISE_MIN_GAP_DAYS] after the last time, and the dice ([roll] one in [oneInN]) say so. [oneInN] below 1 counts as 1.
 */
internal fun crackgateMaySurprise(
    enabled: Boolean,
    surpriseOn: Boolean,
    onHome: Boolean,
    recording: Boolean,
    onCall: Boolean,
    lastShownEpochDay: Long?,
    todayEpochDay: Long,
    roll: Int,
    oneInN: Int,
): Boolean {
    if (!enabled || !surpriseOn || !onHome || recording || onCall) return false
    if (lastShownEpochDay != null && todayEpochDay - lastShownEpochDay < SURPRISE_MIN_GAP_DAYS) return false
    return roll % oneInN.coerceAtLeast(1) == 0
}

/** True once a press has been held [HOLD_TO_START_MS]. A plain tap does not start it. */
internal fun crackgateHoldStarts(heldMs: Long): Boolean = heldMs >= HOLD_TO_START_MS
