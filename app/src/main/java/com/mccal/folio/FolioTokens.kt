package com.mccal.folio

import androidx.compose.ui.graphics.Color

/**
 * Folio's shared colors: Apple's dark-appearance system colors, named once so screens, themes and Market packages all
 * point at the same values. Use these instead of writing hex colors in a screen.
 */
internal object FolioColors {
    /**
     * The palette as ARGB numbers, for the places that hold a colour as a value rather than a `Color`: a tweak's
     * tint, a Focus mode, a saved layout's icon tint. One source of truth, so a number and a colour can't drift.
     */
    object Value {
        const val Blue = 0xFF0A84FFL
        const val BlueDeep = 0xFF0A6FD6L
        const val BlueOnDark = 0xFF6CB4FFL
        const val Red = 0xFFFF453AL
        const val RedLight = 0xFFFF3B30L
        const val RedOnDark = 0xFFFF8A80L
        const val RedSoft = 0xFFFF6961L
        const val Green = 0xFF30D158L
        const val GreenLight = 0xFF34C759L
        const val Orange = 0xFFFF9F0AL
        const val Yellow = 0xFFFFD60AL
        const val Indigo = 0xFF5E5CE6L
        const val Purple = 0xFFBF5AF2L
        const val Pink = 0xFFFF375FL
        const val Cyan = 0xFF64D2FFL
        const val CyanLight = 0xFF32ADE6L
        const val Teal = 0xFF30B0C7L
        const val Gray = 0xFF8E8E93L
        const val Warning = 0xFFFFB340L
        const val SecondaryBackground = 0xFF1C1C1EL
        const val SheetSurface = 0xFF2C2C2EL
        const val MenuSurface = 0xFF3A3A3CL
        const val LightBackground = 0xFFF2F2F7L
    }

    val Blue = Color(Value.Blue)
    /**
     * Blue deep enough for white text to clear 4.5:1 (4.93): filled buttons. iOS puts white on [Blue] itself, which
     * measures 3.65 and fails WCAG AA for text this size, so Folio's filled buttons sit a shade deeper (A11Y-9).
     */
    val BlueDeep = Color(Value.BlueDeep)
    /** Blue for text and links on a dark surface, where [Blue] is too dark to read (6.19:1 on a sheet). */
    val BlueOnDark = Color(Value.BlueOnDark)
    val Red = Color(Value.Red)
    /** The light-appearance red (destructive text on light surfaces, badges). */
    val RedLight = Color(Value.RedLight)
    /** Destructive text on dark menus and sheets: [Red] measures 3.33 on a menu's grey, this one 4.97 (A11Y-9). */
    val RedOnDark = Color(Value.RedOnDark)
    /** A softer red, for a state that is wrong rather than destructive: a source that failed, a paused profile. */
    val RedSoft = Color(Value.RedSoft)
    val Green = Color(Value.Green)
    /** The light-appearance green (iOS systemGreen): switch tracks and confirmations on light surfaces. */
    val GreenLight = Color(Value.GreenLight)
    val Orange = Color(Value.Orange)
    val Yellow = Color(Value.Yellow)
    val Indigo = Color(Value.Indigo)
    val Purple = Color(Value.Purple)
    val Pink = Color(Value.Pink)
    val Cyan = Color(Value.Cyan)
    /** The light-appearance cyan (iOS systemCyan). */
    val CyanLight = Color(Value.CyanLight)
    /** iOS systemTeal. Folio's accent teal is its own colour ([FolioAccents.Teal]); this is the system one. */
    val Teal = Color(Value.Teal)
    val Gray = Color(Value.Gray)
    /**
     * Amber for something that needs attention but isn't destructive: a package Safe Mode turned off, an unsigned
     * file, a setting that is about to change. Status colours never follow the accent (see [LocalAccent]).
     */
    val Warning = Color(Value.Warning)
    /** Grouped background and cards in dark appearance. */
    val SecondaryBackground = Color(Value.SecondaryBackground)
    /** The grey a sheet, an alert and a grouped card sit on. */
    val SheetSurface = Color(Value.SheetSurface)
    /** The grey a pop-up menu sits on, a step lighter than a sheet. */
    val MenuSurface = Color(Value.MenuSurface)
    /** iOS's grouped background in light appearance, for the few Folio surfaces that follow the system theme. */
    val LightBackground = Color(Value.LightBackground)
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
    /** A list row's vertical inset, and the gap either side of a chevron. */
    const val COMPACT = 10
    const val MEDIUM = 12
    /** A sidebar or library row's horizontal inset: 16 crowds a 44 dp icon beside it. */
    const val COMFY = 14
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
