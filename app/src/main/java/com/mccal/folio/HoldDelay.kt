package com.mccal.folio

import androidx.compose.ui.platform.ViewConfiguration

/**
 * How long a finger stays down before Home counts it as a hold (the menu on an app, picking an icon up). Standard is
 * Android's own delay, which Samsung's Touch and hold delay in Accessibility already changes, so Standard follows it;
 * the other two scale it, never past [MIN_MS]..[MAX_MS], so a hold can't fire on a tap or take longer than a second.
 */
enum class HoldDelay(@androidx.annotation.StringRes val label: Int, val factor: Float) {
    SHORTER(R.string.hold_shorter, .7f), STANDARD(R.string.standard, 1f), LONGER(R.string.hold_longer, 1.6f);

    fun millis(base: Long): Long = if (this == STANDARD) base else (base * factor).toLong().coerceIn(MIN_MS, MAX_MS)

    companion object {
        const val MIN_MS = 250L
        const val MAX_MS = 1200L
    }
}

/** [base] with only the hold delay changed, so taps, slop and everything else stay the platform's. */
internal class HoldViewConfiguration(private val base: ViewConfiguration, private val delay: HoldDelay) : ViewConfiguration by base {
    override val longPressTimeoutMillis: Long get() = delay.millis(base.longPressTimeoutMillis)
}
