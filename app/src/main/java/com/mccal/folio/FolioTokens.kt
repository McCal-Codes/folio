package com.mccal.folio

import androidx.compose.ui.graphics.Color

/**
 * Folio's shared colors: Apple's dark-appearance system colors, named once so screens, themes and Market packages all
 * point at the same values. Use these instead of writing hex colors in a screen.
 */
internal object FolioColors {
    val Blue = Color(0xFF0A84FF)
    val Red = Color(0xFFFF453A)
    /** The light-appearance red (destructive text on light surfaces, badges). */
    val RedLight = Color(0xFFFF3B30)
    val Green = Color(0xFF30D158)
    val Orange = Color(0xFFFF9F0A)
    val Yellow = Color(0xFFFFD60A)
    val Indigo = Color(0xFF5E5CE6)
    val Purple = Color(0xFFBF5AF2)
    val Pink = Color(0xFFFF375F)
    val Cyan = Color(0xFF64D2FF)
    val Gray = Color(0xFF8E8E93)
    /** Grouped background and cards in dark appearance. */
    val SecondaryBackground = Color(0xFF1C1C1E)
    /** Supporting text on dark glass and sheets. */
    val SecondaryLabel = Color.White.copy(alpha = .6f)
}

/**
 * Folio's shared springs as (damping, stiffness) pairs. Always run them through [MotionSpeed.spring] so they follow
 * Settings › Gestures › Animation Speed; Reduce Motion is handled by the callers.
 */
internal object FolioMotion {
    /** How long the Gauge takes to sweep to a new battery level: long enough to read as movement, short enough to ignore. */
    const val GAUGE_MS = 650

    /** Sheets and panels settling into place. */
    val Settle = .86f to 420f
    /** Quick responses to a touch (buttons, toggles, closing). */
    val Quick = .8f to 700f
    /** Firm, no-bounce snaps (dismissals). */
    val Firm = 1f to 700f
    fun <T> spring(pair: Pair<Float, Float>) = MotionSpeed.spring<T>(pair.first, pair.second)
}
