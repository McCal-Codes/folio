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
        return try { runCatching { sampleClockRegion(shot, box) }.getOrNull() } finally { shot.recycle() }
    }
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
