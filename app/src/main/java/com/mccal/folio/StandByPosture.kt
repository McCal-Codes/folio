package com.mccal.folio

/**
 * Hard safety caps from docs/plan-standby-2.md §0 (WCAG 2.3.1 / A11Y-15).
 * No package or caller may shorten a fade below [MIN_FADE_MS], trigger a glow faster than
 * [MIN_GLOW_INTERVAL_MS], raise glow opacity above [MAX_GLOW_ALPHA], or shift more than
 * [MAX_BURN_IN_DP] from center.
 */
internal object StandBySafetyCaps {
    const val MIN_FADE_MS = 300
    const val ENTER_FADE_MS = 500
    const val EXIT_FADE_MS = 300
    const val VIEW_FADE_MS = 300
    const val GLOW_IN_MS = 600
    const val GLOW_HOLD_MS = 300
    const val GLOW_OUT_MS = 900
    const val MIN_GLOW_INTERVAL_MS = 10_000L
    const val MAX_GLOW_ALPHA = 0.45f
    const val MAX_BURN_IN_DP = 6
    const val BURN_IN_GLIDE_MS = 1_500
    const val MAX_NOTIF_DOTS = 5
}

/**
 * The five posture-aware layouts StandBy 2 arranges its shared dashboard modules into.
 *
 * - [BOOK]: inner foldable screen half-open with a vertical crease (Clock on leading half, Info/Cards on trailing half).
 * - [TABLETOP]: inner foldable screen half-open with a horizontal crease (Clock/Info on upper half [FoldRole.INFO],
 *   interactive controls/cards on lower half [FoldRole.CONTROLS], per ADP-13).
 * - [TENT]: cover screen sideways while propped as a tent.
 * - [LANDSCAPE]: flat phone or tablet/inner screen sideways on a charger/dock.
 * - [PORTRAIT]: upright window fallback (never stretches; stacks Clock above Info/Cards).
 */
internal enum class StandByPosture {
    BOOK,
    TABLETOP,
    TENT,
    LANDSCAPE,
    PORTRAIT;

    val isStacked: Boolean get() = this == TABLETOP || this == PORTRAIT
    val hasCrease: Boolean get() = this == BOOK || this == TABLETOP
}

/**
 * Pure snapshot of window, fold, and sensor signals used to classify [StandByPosture] deterministically
 * in both Home's [StandByOverlay] (which has WindowManager [Hinge]) and [StandByScreenSaver] (DreamService,
 * which may only have window dp, [hingeHalfway], and accelerometer gravity).
 */
internal data class StandByPostureSignals(
    val widthDp: Float,
    val heightDp: Float,
    val classScale: Float = 1f,
    val way: StandByWay? = null,
    /** From Jetpack WindowManager when running inside an Activity; null in DreamService or when flat. */
    val halfOpenVerticalCrease: Boolean? = null,
    val hingeSpansPx: List<IntRange> = emptyList(),
    /** True when the public hinge angle sensor is in the halfway band (30°..150°). */
    val hingeHalfway: Boolean = false,
    val tentState: Boolean? = null,
    /** True when accelerometer gravity is predominantly along the device X axis (sideways) vs Y/Z (tabletop/upright). */
    val sidewaysGravity: Boolean = false,
)

/**
 * Pure, deterministic posture resolver with safe fallbacks when WindowManager fold features are absent (ADP-15).
 */
internal fun resolveStandByPosture(s: StandByPostureSignals): StandByPosture {
    val regularWindow = fitsRegularHomeLayout(s.widthDp, s.heightDp, s.classScale)
    val onCover = !regularWindow
    val isTent = onCover && (s.way == StandByWay.TENT || (s.tentState ?: s.hingeHalfway))
    if (isTent && s.widthDp >= s.heightDp) return StandByPosture.TENT

    // 1. Explicit WindowManager Half-Open fold orientation (Home overlay)
    if (s.halfOpenVerticalCrease != null) {
        return if (s.halfOpenVerticalCrease) StandByPosture.BOOK else StandByPosture.TABLETOP
    }
    if (regularWindow && s.way == StandByWay.HALF_OPEN) {
        return if (s.hingeSpansPx.isNotEmpty() || s.sidewaysGravity) StandByPosture.BOOK else StandByPosture.TABLETOP
    }

    // 2. DreamService / fallback on inner foldable screen when WindowManager is unavailable but hinge is at ~90°
    if (regularWindow && s.hingeHalfway) {
        // On a half-folded inner screen without WindowManager: sideways gravity along X indicates book standing on edge;
        // otherwise tabletop resting on its bottom half.
        return if (s.sidewaysGravity) StandByPosture.BOOK else StandByPosture.TABLETOP
    }

    // 3. Flat / standard window geometry fallback (ADP-4, ADP-15)
    return if (s.widthDp >= s.heightDp) StandByPosture.LANDSCAPE else StandByPosture.PORTRAIT
}

internal enum class StandByClockLook { THIN, BOLD, ANALOG, NUMERALS, FLIP, WORLD, SOLAR, WORDS, STACK, NEON }
internal enum class StandByPlayerStyle { LEGACY_SLAB, CARD, COMPACT, OFF }
internal enum class StandByNotifMode { OFF, COUNT_ONLY, COLORS }
internal enum class StandByNightMode { SCHEDULE, ROOM_LIGHT, NEVER }

/**
 * The resolved StandBy configuration in force (`eff()` in docs/plan-standby-2.md).
 * When the `standby2` tweak package is not installed, switched off, or its gate is closed,
 * [resolveStandByConfig] returns [LEGACY_0_6_9] so StandBy behaves identically to Folio 0.6.9.
 */
internal data class StandByEffectiveConfig(
    val activePackage: Boolean = false,
    val clockLook: StandByClockLook = StandByClockLook.THIN,
    val playerStyle: StandByPlayerStyle = StandByPlayerStyle.LEGACY_SLAB,
    val upNext: Boolean = true,
    val timersBesideMedia: Boolean = false,
    val notifMode: StandByNotifMode = StandByNotifMode.OFF,
    val nightMode: StandByNightMode = StandByNightMode.SCHEDULE,
    val nightBrightnessPercent: Int = 100,
    val showChargingLine: Boolean = false,
    val tapEmptySpaceCloses: Boolean = true,
    val nightColor: String = "red",
    val offCharger: Boolean = false,
    val sunriseBeforeAlarm: Boolean = true,
) {
    companion object {
        val LEGACY_0_6_9 = StandByEffectiveConfig()
    }
}

internal fun resolveStandByConfig(
    gateOpen: Boolean,
    packageInstalled: Boolean,
    packageEnabled: Boolean,
    clockLook: StandByClockLook = StandByClockLook.THIN,
    playerStyle: StandByPlayerStyle = StandByPlayerStyle.CARD,
    upNext: Boolean = true,
    timersBesideMedia: Boolean = true,
    notifMode: StandByNotifMode = StandByNotifMode.COLORS,
    nightMode: StandByNightMode = StandByNightMode.SCHEDULE,
    nightBrightnessPercent: Int = 60,
    nightColor: String = "red",
    offCharger: Boolean = false,
    sunriseBeforeAlarm: Boolean = true,
): StandByEffectiveConfig {
    if (!gateOpen || !packageInstalled || !packageEnabled) return StandByEffectiveConfig.LEGACY_0_6_9
    return StandByEffectiveConfig(
        activePackage = true,
        clockLook = clockLook,
        playerStyle = playerStyle,
        upNext = upNext,
        timersBesideMedia = timersBesideMedia,
        notifMode = notifMode,
        nightMode = nightMode,
        nightBrightnessPercent = nightBrightnessPercent.coerceIn(0, 100),
        showChargingLine = true,
        tapEmptySpaceCloses = false,
        nightColor = nightColor,
        offCharger = offCharger,
        sunriseBeforeAlarm = sunriseBeforeAlarm,
    )
}

/**
 * Resolves [StandByEffectiveConfig] from [LauncherState] (including per-screen [FeatureScopes] and [TweakOptions]).
 */
internal fun resolveStandByConfig(
    state: LauncherState,
    gateOpen: Boolean,
    onCover: Boolean = false,
): StandByEffectiveConfig {
    val installed = STANDBY2_ID in state.installedTweaks
    val enabled = FeatureScopes.on(state.featureScopes, STANDBY2_ID, state.standBy2, screenFor(wide = !onCover))
    val opts = state.tweakOptions
    val clockLook = when (TweakOptions.value(opts, STANDBY2_ID, "clock")) {
        "bold" -> StandByClockLook.BOLD
        "analog" -> StandByClockLook.ANALOG
        "numerals" -> StandByClockLook.NUMERALS
        "flip" -> StandByClockLook.FLIP
        "world" -> StandByClockLook.WORLD
        "solar" -> StandByClockLook.SOLAR
        "words" -> StandByClockLook.WORDS
        "stack" -> StandByClockLook.STACK
        "neon" -> StandByClockLook.NEON
        else -> StandByClockLook.THIN
    }
    val playerStyle = when (TweakOptions.value(opts, STANDBY2_ID, "player")) {
        "compact" -> StandByPlayerStyle.COMPACT
        "off" -> StandByPlayerStyle.OFF
        else -> StandByPlayerStyle.CARD
    }
    val upNext = TweakOptions.on(opts, STANDBY2_ID, "upNext")
    val timers = TweakOptions.on(opts, STANDBY2_ID, "timers")
    val notifMode = when (TweakOptions.value(opts, STANDBY2_ID, "notif")) {
        "count" -> StandByNotifMode.COUNT_ONLY
        "off" -> StandByNotifMode.OFF
        else -> StandByNotifMode.COLORS
    }
    val nightMode = when (TweakOptions.value(opts, STANDBY2_ID, "nightMode")) {
        "room" -> StandByNightMode.ROOM_LIGHT
        "never" -> StandByNightMode.NEVER
        else -> StandByNightMode.SCHEDULE
    }
    val nightBrightness = TweakOptions.value(opts, STANDBY2_ID, "nightBrightness").toIntOrNull() ?: 60
    val nightColor = when (TweakOptions.value(opts, STANDBY2_ID, "nightColor")) {
        "amber" -> "amber"
        "blue" -> "blue"
        else -> "red"
    }
    val offCharger = TweakOptions.on(opts, STANDBY2_ID, "offCharger")
    val sunrise = TweakOptions.on(opts, STANDBY2_ID, "sunrise")
    return resolveStandByConfig(
        gateOpen = gateOpen,
        packageInstalled = installed,
        packageEnabled = enabled,
        clockLook = clockLook,
        playerStyle = playerStyle,
        upNext = upNext,
        timersBesideMedia = timers,
        notifMode = notifMode,
        nightMode = nightMode,
        nightBrightnessPercent = nightBrightness,
        nightColor = nightColor,
        offCharger = offCharger,
        sunriseBeforeAlarm = sunrise,
    )
}

/**
 * Pure ambient-light hysteresis tracker for [StandByNightMode.ROOM_LIGHT] (`docs/plan-standby-2.md` §5 Phase 5).
 *
 * Enters night when ambient light stays at or below [ENTER_DARK_LUX] (`2.0f` lux) for at least
 * [ENTER_DARK_DELAY_MS] (`2_000L` ms), and exits night when ambient light stays at or above
 * [EXIT_DARK_LUX] (`10.0f` lux) for at least [EXIT_DARK_DELAY_MS] (`3_000L` ms).
 * Readings in the deadband or brief spikes/shadows shorter than the delay keep the current state so a passing hand
 * or brief lamp does not flip Night mode.
 */
internal class RoomLightTracker(
    initialNight: Boolean = false,
    private val enterDarkLux: Float = ENTER_DARK_LUX,
    private val exitDarkLux: Float = EXIT_DARK_LUX,
    private val enterDelayMs: Long = ENTER_DARK_DELAY_MS,
    private val exitDelayMs: Long = EXIT_DARK_DELAY_MS,
) {
    var isNight: Boolean = initialNight
        private set
    private var pendingSinceMs: Long = -1L
    private var pendingTargetNight: Boolean? = null

    fun onReading(lux: Float, timeMs: Long): Boolean {
        val wantNight = when {
            !isNight && lux <= enterDarkLux -> true
            isNight && lux >= exitDarkLux -> false
            else -> null
        }
        if (wantNight == null) {
            pendingSinceMs = -1L
            pendingTargetNight = null
            return isNight
        }
        if (pendingTargetNight != wantNight || pendingSinceMs < 0L) {
            pendingTargetNight = wantNight
            pendingSinceMs = timeMs
            return isNight
        }
        val requiredDelay = if (wantNight) enterDelayMs else exitDelayMs
        if (timeMs - pendingSinceMs >= requiredDelay) {
            isNight = wantNight
            pendingSinceMs = -1L
            pendingTargetNight = null
        }
        return isNight
    }

    companion object {
        const val ENTER_DARK_LUX = 2.0f
        const val EXIT_DARK_LUX = 10.0f
        const val ENTER_DARK_DELAY_MS = 2_000L
        const val EXIT_DARK_DELAY_MS = 3_000L
    }
}

/**
 * Pure night-mode resolver combining [StandByNightMode], the schedule rule (`22:00..06:00`), and the optional
 * [RoomLightTracker] reading ([roomDark], or `null` when no light sensor is present).
 */
internal fun resolveStandByNight(
    mode: StandByNightMode,
    scheduleNight: Boolean,
    roomDark: Boolean? = null,
): Boolean = when (mode) {
    StandByNightMode.NEVER -> false
    StandByNightMode.SCHEDULE -> scheduleNight
    StandByNightMode.ROOM_LIGHT -> roomDark ?: scheduleNight
}

/**
 * Whether [actions] from `PlaybackState.getActions()` offers the transport action [flag]
 * (e.g. `ACTION_SKIP_TO_PREVIOUS` or `ACTION_SKIP_TO_NEXT`).
 */
internal fun mediaSkipOffered(actions: Long, flag: Long): Boolean = (actions and flag) != 0L

/**
 * Formats an active [IslandActivity.Timer] for StandBy 2 (`MM:SS` under 1 hour, `H:MM:SS` at or above 1 hour).
 * Clamps negative countdowns to `00:00`.
 */
internal fun formatStandByTimer(baseMs: Long, nowMs: Long, countDown: Boolean): String {
    val deltaMs = (if (countDown) baseMs - nowMs else nowMs - baseMs).coerceAtLeast(0L)
    val totalSec = deltaMs / 1_000L
    val hours = totalSec / 3_600L
    val minutes = (totalSec % 3_600L) / 60L
    val seconds = totalSec % 60L
    return if (hours > 0L) {
        String.format(java.util.Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(java.util.Locale.US, "%02d:%02d", minutes, seconds)
    }
}

/**
 * Formats a positive `BatteryManager.computeChargeTimeRemaining()` millisecond duration into `"1 h 20 min"` / `"45 min"`,
 * or returns `null` when unknown (`<= 0L`).
 */
internal fun formatStandByChargeDuration(remainingMs: Long): String? {
    if (remainingMs <= 0L) return null
    val totalMinutes = ((remainingMs + 59_999L) / 60_000L).coerceAtLeast(1L)
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return when {
        hours > 0L && minutes > 0L -> "$hours h $minutes min" // english-only
        hours > 0L -> "$hours h" // english-only
        else -> "$minutes min" // english-only
    }
}

/**
 * Resolves the StandBy 2 charging status line (`"Full in 1 h 20 min"` or `"On hold at 80%"`),
 * or `null` when unplugged, when [showChargingLine] is false, or when Android returns `-1` (`null`).
 */
internal fun formatStandByChargeLine(
    showChargingLine: Boolean,
    charging: Boolean,
    battery: Int?,
    chargeFullInMs: Long?,
    chargeOnHold: Boolean,
    onHoldFormat: (Int) -> String,
    fullInFormat: (String) -> String,
): String? {
    if (!showChargingLine || !charging) return null
    if (chargeOnHold && battery != null) return onHoldFormat(battery)
    val duration = chargeFullInMs?.let(::formatStandByChargeDuration) ?: return null
    return fullInFormat(duration)
}

/**
 * 4x5 ColorMatrix mapping RGB luminance into StandBy's night hue (Red, Amber, or Blue),
 * so album art on the StandBy 2 player card matches the selected night hue in a dark room.
 */
internal fun nightArtColorMatrix(nightColor: String = "red"): FloatArray {
    val lumR = 0.165f
    val lumG = 0.558f
    val lumB = 0.056f
    return when (nightColor) {
        "amber" -> floatArrayOf(
            lumR,         lumG,         lumB,         0f, 0f,
            lumR * 0.58f, lumG * 0.58f, lumB * 0.58f, 0f, 0f,
            0f,           0f,           0f,           0f, 0f,
            0f,           0f,           0f,           1f, 0f,
        )
        "blue" -> floatArrayOf(
            0f,           0f,           0f,           0f, 0f,
            lumR * 0.35f, lumG * 0.35f, lumB * 0.35f, 0f, 0f,
            lumR,         lumG,         lumB,         0f, 0f,
            0f,           0f,           0f,           1f, 0f,
        )
        else -> floatArrayOf(
            lumR, lumG, lumB, 0f, 0f,
            0f,   0f,   0f,   0f, 0f,
            0f,   0f,   0f,   0f, 0f,
            0f,   0f,   0f,   1f, 0f,
        )
    }
}

/**
 * Relative luminance of an sRGB color in `0.0..1.0` per WCAG 2.x.
 * On pure `#000000` (`L = 0`), a `3:1` non-text contrast ratio (WCAG 1.4.11) requires `(L + 0.05) / 0.05 >= 3.0`,
 * i.e. `L >= 0.10`.
 */
internal fun standByRelativeLuminance(argb: Int): Double {
    fun channel(c: Int): Double {
        val s = (c and 255) / 255.0
        return if (s <= 0.03928) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4)
    }
    val r = channel(argb ushr 16)
    val g = channel(argb ushr 8)
    val b = channel(argb)
    return 0.2126 * r + 0.7152 * g + 0.0722 * b
}

/**
 * Nudges [argb] lighter if needed so its relative luminance on pure black reaches at least `0.10` (`3:1` WCAG 1.4.11
 * non-text contrast), keeping full opacity.
 */
internal fun nudgeStandByColorOnBlack(argb: Int): Int {
    val opaque = argb or (255 shl 24)
    if (standByRelativeLuminance(opaque) >= 0.10) return opaque
    val r0 = (opaque ushr 16) and 255
    val g0 = (opaque ushr 8) and 255
    val b0 = opaque and 255
    for (step in 1..25) {
        val t = step / 25.0
        val r = (r0 + ((255 - r0) * t).toInt()).coerceIn(0, 255)
        val g = (g0 + ((255 - g0) * t).toInt()).coerceIn(0, 255)
        val b = (b0 + ((255 - b0) * t).toInt()).coerceIn(0, 255)
        val candidate = (255 shl 24) or (r shl 16) or (g shl 8) or b
        if (standByRelativeLuminance(candidate) >= 0.10) return candidate
    }
    return FolioColors.Value.Blue.toInt()
}

/**
 * Samples a representative accent color from an icon's ARGB pixels when `Notification.color` is unset (`0`),
 * preferring saturated opaque pixels and falling back to [FolioColors.Value.Blue].
 */
internal fun sampleStandByIconColor(pixels: IntArray): Int {
    var bestColor: Int? = null
    var bestScore = -1
    for (px in pixels) {
        val a = (px ushr 24) and 255
        if (a < 160) continue
        val r = (px ushr 16) and 255
        val g = (px ushr 8) and 255
        val b = px and 255
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val sat = max - min
        if (max < 36) continue // skip near-black
        if (min > 235 && sat < 16) continue // skip pure white background
        val score = sat * 2 + max
        if (score > bestScore) {
            bestScore = score
            bestColor = (255 shl 24) or (r shl 16) or (g shl 8) or b
        }
    }
    return nudgeStandByColorOnBlack(bestColor ?: FolioColors.Value.Blue.toInt())
}

/**
 * Pure input candidate for StandBy 2 notification dots, count, and edge glow.
 */
internal data class StandByNotifCandidate(
    val key: String,
    val packageName: String,
    val postTime: Long,
    val color: Int? = null,
    val ongoing: Boolean = false,
    val importance: Int = 3,
    val canShowBadge: Boolean = true,
    val visibility: Int = 1,
    val silenced: Boolean = false,
)

/**
 * Pure output summary for StandBy 2 notification dots, count, and rate-limited edge glow:
 * - [totalCount]: total matching notifications since StandBy became active (`"%1$d new"`).
 * - [dotColors]: at most [StandBySafetyCaps.MAX_NOTIF_DOTS] (`5`) unique app colors ordered newest-first.
 * - [overflowApps]: count of additional unique apps beyond 5 (`"+%1$d"`).
 * - [newestGlowKey]: signature (`key@postTime`) of the newest notification for one-shot glow deduplication.
 * - [newestGlowColor]: resolved ARGB color for the edge glow.
 * - [newestGlowSilenced]: whether the newest notification is silenced by Do Not Disturb / visual suppression.
 */
internal data class StandByNotifSummary(
    val totalCount: Int = 0,
    val dotColors: List<Int> = emptyList(),
    val overflowApps: Int = 0,
    val newestGlowKey: String? = null,
    val newestGlowColor: Int? = null,
    val newestGlowSilenced: Boolean = true,
)

/**
 * Pure resolver for StandBy 2 notification count, app-colored dots (capped at 5 + `+N`), and lock-screen privacy.
 */
internal fun resolveStandByNotifications(
    items: List<StandByNotifCandidate>,
    sessionStartMs: Long,
    mode: StandByNotifMode,
    locked: Boolean = false,
    lockScreenShowNotifs: Boolean = true,
): StandByNotifSummary {
    if (mode == StandByNotifMode.OFF) return StandByNotifSummary()
    if (locked && !lockScreenShowNotifs) return StandByNotifSummary()

    val matching = items.filter { item ->
        item.postTime >= sessionStartMs &&
            !item.ongoing &&
            (item.importance == IMPORTANCE_UNKNOWN || item.importance >= 3) &&
            item.canShowBadge &&
            (!locked || item.visibility != -1)
    }
    if (matching.isEmpty()) return StandByNotifSummary()

    if (mode == StandByNotifMode.COUNT_ONLY) {
        return StandByNotifSummary(totalCount = matching.size)
    }

    val byPackage = matching
        .groupBy { it.packageName }
        .entries
        .sortedByDescending { (_, list) -> list.maxOf { it.postTime } }

    val fallbackColor = FolioColors.Value.Blue.toInt()
    val dotColors = byPackage
        .take(StandBySafetyCaps.MAX_NOTIF_DOTS)
        .map { (_, list) ->
            val newestForPkg = list.maxBy { it.postTime }
            nudgeStandByColorOnBlack(newestForPkg.color ?: fallbackColor)
        }
    val overflow = (byPackage.size - StandBySafetyCaps.MAX_NOTIF_DOTS).coerceAtLeast(0)
    val newest = matching.maxBy { it.postTime }
    return StandByNotifSummary(
        totalCount = matching.size,
        dotColors = dotColors,
        overflowApps = overflow,
        newestGlowKey = "${newest.key}@${newest.postTime}",
        newestGlowColor = nudgeStandByColorOnBlack(newest.color ?: fallbackColor),
        newestGlowSilenced = newest.silenced,
    )
}

/**
 * Pure rate-limiter and motion/DND gate for the StandBy 2 notification edge glow (`docs/plan-standby-2.md` §0 & §5 Phase 6).
 * Enforces [StandBySafetyCaps.MIN_GLOW_INTERVAL_MS] (`10_000L`), suppresses under `LocalReduceMotion`, and respects
 * Do Not Disturb / visual suppression ([silenced]).
 */
internal fun shouldTriggerStandByGlow(
    mode: StandByNotifMode,
    reduceMotion: Boolean,
    silenced: Boolean,
    nowElapsedMs: Long,
    lastGlowElapsedMs: Long,
): Boolean {
    if (mode != StandByNotifMode.COLORS || reduceMotion || silenced) return false
    return lastGlowElapsedMs < 0L || (nowElapsedMs - lastGlowElapsedMs) >= StandBySafetyCaps.MIN_GLOW_INTERVAL_MS
}


