package com.mccal.folio

import androidx.compose.ui.geometry.Rect
import kotlin.math.max

/**
 * Reads what is behind the Big Clock when Android's own wallpaper is behind Home, whose pixels no app can read any other
 * way. A picture of the screen is taken only while Smart clock ink is on (the service is), only while Folio's Home is in
 * front, at most once every two seconds; the part under the clock is measured straight away and the picture is thrown
 * away. Only the numbers in the sample are kept, and nothing is saved or sent.
 */
internal object SmartClockInk {
    private const val MIN_INTERVAL_MS = 2_000L
    private var lastAtMs = 0L

    /** [box] is the clock's box as fractions of the window, which is the screen on Home. Null when nothing could be measured. */
    suspend fun sample(box: Rect, displayId: Int): ClockInkSample? {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastAtMs < MIN_INTERVAL_MS) return null
        lastAtMs = now
        val shot = SmartInkService.capture(displayId) ?: return null
        return try {
            runCatching {
                // The picture has the clock on it, so the clock's own digits are never measured: the wallpaper just above and just
                // below it is, and the stricter of the two decides (docs/audits/2026-10-08-smart-ink-capture-research.md, section 3).
                val (above, below) = wallpaperBesideClock(box)
                listOfNotNull(above, below).map { sampleClockRegion(shot, it) }.reduceOrNull(::strictest)
            }.getOrNull()
        } finally { shot.recycle() }
    }
}

/** Strips of the same width right above and right below [box] (fractions of the window), half its height each; null when there is no room. */
internal fun wallpaperBesideClock(box: Rect): Pair<Rect?, Rect?> {
    val h = box.bottom - box.top
    val above = Rect(box.left, (box.top - h * .5f).coerceAtLeast(0f), box.right, box.top).takeIf { it.bottom - it.top > .01f }
    val below = Rect(box.left, box.bottom, box.right, (box.bottom + h * .5f).coerceAtMost(1f)).takeIf { it.bottom - it.top > .01f }
    return above to below
}

/** The harder case of two samples: the brightest bright end, the darkest dark end, the average of the rest. */
internal fun strictest(first: ClockInkSample, second: ClockInkSample): ClockInkSample {
    fun RegionStats.with(o: RegionStats) = RegionStats((mean + o.mean) / 2f, minOf(p15, o.p15), maxOf(p85, o.p85), (this.r + o.r) / 2f, (this.g + o.g) / 2f, (this.b + o.b) / 2f)
    return first.copy(under = first.under.with(second.under))
}

/**
 * The picture is of Home with the clock drawn on it, so the ink chosen from one picture changes the next one. To stop a
 * near tie from flipping back and forth every few seconds, a new sample that would switch the ink is only taken when the
 * other ink wins by [FLIP_MARGIN] times the contrast the current ink would still have. A sample that keeps the same
 * ink always replaces the old one.
 */
internal const val FLIP_MARGIN = 1.5f

internal fun keepOrSwap(previous: ClockInkSample?, next: ClockInkSample): ClockInkSample {
    if (previous == null) return next
    val (previousDark, _) = pickInk(previous.under)
    val (nextDark, nextBest) = pickInk(next.under)
    if (previousDark == nextDark) return next
    // What the previous ink would still hold against the new picture.
    val keptRatio = if (previousDark) contrastRatio(.006f, next.under.p15) else contrastRatio(1f, next.under.p85)
    return if (nextBest >= keptRatio * FLIP_MARGIN) next else previous
}
