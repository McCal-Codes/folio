package com.mccal.folio

import androidx.compose.ui.graphics.Color

/**
 * Folio's shared colors: Apple's dark-appearance system colors, named once so screens, themes and Market packages all
 * point at the same values. Use these instead of writing hex colors in a screen.
 */
internal object FolioColors {
    val Blue = Color(0xFF0A84FF)
    /**
     * Blue deep enough for white text to clear 4.5:1 (4.93): filled buttons. iOS puts white on [Blue] itself, which
     * measures 3.65 and fails WCAG AA for text this size, so Folio's filled buttons sit a shade deeper (A11Y-9).
     */
    val BlueDeep = Color(0xFF0A6FD6)
    /** Blue for text and links on a dark surface, where [Blue] is too dark to read (6.19:1 on a sheet). */
    val BlueOnDark = Color(0xFF6CB4FF)
    val Red = Color(0xFFFF453A)
    /** The light-appearance red (destructive text on light surfaces, badges). */
    val RedLight = Color(0xFFFF3B30)
    /** Destructive text on dark menus and sheets: [Red] measures 3.33 on a menu's grey, this one 4.97 (A11Y-9). */
    val RedOnDark = Color(0xFFFF8A80)
    val Green = Color(0xFF30D158)
    /** The light-appearance green (iOS systemGreen): switch tracks and confirmations on light surfaces. */
    val GreenLight = Color(0xFF34C759)
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
 * The colour Folio uses for the thing you can act on: buttons, switches, selection, links. Two roles, because one
 * colour can't do both jobs on a dark surface: [fill] sits behind white text, [ink] is the accent as text.
 */
internal data class FolioAccent(val fill: Color, val ink: Color)

/** What Settings offers under Accent. Folio's own teal is the default; Apple's blue is there for whoever wants it. */
enum class AccentChoice(val id: String) { FOLIO_TEAL("teal"), APPLE_BLUE("blue") }

internal object FolioAccents {
    /** Folio's app icon, which ships in teal (#173D43 to #2E5E66), and the soft icon's mint for text. */
    val Teal = FolioAccent(fill = Color(0xFF2A6A70), ink = Color(0xFF6DB7B4))
    /** iOS's blue, deep enough for white text to pass AA. */
    val Blue = FolioAccent(fill = FolioColors.BlueDeep, ink = FolioColors.BlueOnDark)
    fun of(choice: AccentChoice) = when (choice) {
        AccentChoice.FOLIO_TEAL -> Teal
        AccentChoice.APPLE_BLUE -> Blue
    }
}

/** The accent in force, from Settings › Wallpaper & Appearance › Accent. */
internal val LocalAccent = androidx.compose.runtime.staticCompositionLocalOf { FolioAccents.Teal }

/**
 * Folio's spacing scale, in dp. These are the gaps Folio already uses; a size off the scale needs a reason in a
 * comment beside it. See docs/standards/design.md (DES-6).
 */
internal object FolioSpace {
    const val HAIR = 2
    const val TINY = 4
    const val SNUG = 6
    const val SMALL = 8
    const val MEDIUM = 12
    const val LARGE = 16
    const val XL = 20
    const val XXL = 24
    const val HUGE = 32
}

/** Folio's corner radii, in dp. Inner corners are concentric: outer radius minus the padding between them (DES-8). */
internal object FolioRadius {
    /** Small controls: chips, search fields, inline buttons. */
    const val CONTROL = 10
    /** Menus, alerts, form sheets and cards. */
    const val CARD = 14
    /** Groups inside a sheet. */
    const val GROUP = 16
    /** Grouped settings cards. */
    const val GROUPED_CARD = 20
    /** Panels and large surfaces. */
    const val PANEL = 24
    /** The top corners of a bottom sheet. */
    const val SHEET_TOP = 28
}

/**
 * Folio's type scale, in sp, following iOS's text styles. Home app labels come from [LabelSize] instead, because they
 * follow the user's icon size.
 */
internal object FolioType {
    const val TITLE = 28
    const val BODY = 17
    const val SUBHEAD = 15
    const val FOOTNOTE = 13
    /** Uppercase group labels, SemiBold with .4sp tracking. */
    const val GROUP_LABEL = 12
}

/** Row heights, in dp: iOS's list metrics, and never below the 48dp touch target (A11Y-1). */
internal object FolioRow {
    const val ACTION = 48
    const val NAV = 52
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
