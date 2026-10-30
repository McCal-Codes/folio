package com.mccal.folio

import androidx.annotation.StringRes
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.pow
import kotlin.math.roundToInt

/** The island's color: four presets and one the person typed. All are solid. */
internal enum class IslandColor(val key: String, @StringRes val label: Int, val argb: Int?) {
    BLACK("black", R.string.island_color_black, FolioIsland.Black.toInt()),
    GRAPHITE("graphite", R.string.island_color_graphite, FolioIsland.Graphite.toInt()),
    NAVY("navy", R.string.island_color_navy, FolioIsland.Navy.toInt()),
    PLUM("plum", R.string.island_color_plum, FolioIsland.Plum.toInt()),
    CUSTOM("custom", R.string.island_color_custom, null);

    companion object { fun of(key: String?): IslandColor = entries.firstOrNull { it.key == key } ?: BLACK }
}

/** How round the island's corners are. [share] is the part of a fully round pill's radius kept, and [card] the expanded card's radius in dp. */
internal enum class IslandCorners(val key: String, @StringRes val label: Int, val share: Float, val card: Float) {
    ROUND("round", R.string.island_corners_round, FolioIsland.CORNERS_ROUND, FolioIsland.CARD_ROUND),
    SOFT("soft", R.string.island_corners_soft, FolioIsland.CORNERS_SOFT, FolioIsland.CARD_SOFT),
    SQUARER("squarer", R.string.island_corners_squarer, FolioIsland.CORNERS_SQUARER, FolioIsland.CARD_SQUARER);

    companion object { fun of(key: String?): IslandCorners = entries.firstOrNull { it.key == key } ?: ROUND }
}

/** How fast the island's own motion runs, as a multiplier on its spring stiffness and the inverse of its fade lengths. */
internal enum class IslandSpeed(val key: String, @StringRes val label: Int, val multiplier: Float) {
    SLOWER("slower", R.string.island_speed_slower, FolioMotion.ISLAND_SLOWER),
    STANDARD("standard", R.string.standard, FolioMotion.ISLAND_STANDARD),
    FASTER("faster", R.string.island_speed_faster, FolioMotion.ISLAND_FASTER);

    companion object { fun of(key: String?): IslandSpeed = entries.firstOrNull { it.key == key } ?: STANDARD }
}

/** How strongly the island buzzes. Standard is what it did before the option existed. */
internal enum class IslandHaptics(val key: String, @StringRes val label: Int) {
    OFF("off", R.string.island_haptics_off),
    LIGHT("light", R.string.island_haptics_light),
    STANDARD("standard", R.string.standard);

    companion object { fun of(key: String?): IslandHaptics = entries.firstOrNull { it.key == key } ?: STANDARD }
}

/** A way of touching the island. */
internal enum class IslandGesture(val key: String, @StringRes val label: Int) {
    TAP("tap", R.string.island_gesture_tap),
    HOLD("hold", R.string.island_gesture_hold),
    SWIPE_UP("swipe_up", R.string.island_gesture_swipe_up),
    DOUBLE_TAP("double_tap", R.string.island_gesture_double_tap)
}

/** What a gesture can do. [MOVE] (pick the island up to move it) is only offered for Hold, where it is today's behavior. */
internal enum class IslandAction(val key: String, @StringRes val label: Int) {
    EXPAND("expand", R.string.island_action_expand),
    OPEN_APP("open_app", R.string.island_action_open_app),
    DISMISS("dismiss", R.string.island_action_dismiss),
    MOVE("move", R.string.island_action_move),
    NOTHING("nothing", R.string.island_action_nothing);

    companion object { fun of(key: String?): IslandAction? = entries.firstOrNull { it.key == key } }
}

internal typealias IslandGestureMap = Map<IslandGesture, IslandAction>

/**
 * Advanced island (Settings › Dynamic Island › Advanced island): color, corner radius, animation speed, haptics and
 * what each gesture does. Saved in the "folio" preferences, one value per option, and every save tells a running island
 * ([IslandLayoutChanges]) so it changes at once. Every default is what the island did before this existed, so nothing
 * changes for anyone who never opens the page. The functions in here are pure, so the rules (readable color, radius
 * floor, speed ceiling, at least one Dismiss) are unit tested.
 */
internal object IslandStyle {
    private const val PREFS = "folio"
    private const val COLOR = "island_style_color"
    private const val CUSTOM = "island_style_custom_color"
    private const val CORNERS = "island_style_corners"
    private const val SPEED = "island_style_speed"
    private const val HAPTICS = "island_style_haptics"
    private fun gestureKey(gesture: IslandGesture) = "island_style_gesture_${gesture.key}"

    /** What the island did before the options: black, round, standard speed and haptics, tap expands, hold picks up, swipe up puts a card away. */
    val defaultGestures: IslandGestureMap = mapOf(
        IslandGesture.TAP to IslandAction.EXPAND, IslandGesture.HOLD to IslandAction.MOVE,
        IslandGesture.SWIPE_UP to IslandAction.DISMISS, IslandGesture.DOUBLE_TAP to IslandAction.NOTHING,
    )

    /** All five options. [custom] is the opaque color the person typed, kept as typed; [background] is what is drawn. */
    data class Values(
        val color: IslandColor = IslandColor.BLACK,
        val custom: Int? = null,
        val corners: IslandCorners = IslandCorners.ROUND,
        val speed: IslandSpeed = IslandSpeed.STANDARD,
        val haptics: IslandHaptics = IslandHaptics.STANDARD,
        val gestures: IslandGestureMap = defaultGestures,
    ) {
        /** The island's solid color: the preset, or the typed color moved until its text is readable. */
        val background: Int get() = backgroundOf(color, custom)
        /** Whether the typed color had to be moved. */
        val nudged: Boolean get() = color == IslandColor.CUSTOM && custom != null && background != custom
        /** True while every option is at its default, so the island is drawn and handled exactly as before. */
        val isDefault: Boolean get() = this == Values()
    }

    /** What the island is drawn and handled with: the saved [Values] once the Advanced island gate is open, otherwise the defaults. */
    fun effective(saved: Values, gateOpen: Boolean): Values = if (gateOpen) saved else Values()

    /** [load] behind [FeatureGate.ISLAND_ADVANCED]: a shut gate means today's island exactly, even with values saved by an earlier build. */
    fun active(context: android.content.Context): Values =
        if (FeatureGate.ISLAND_ADVANCED.isOpen(context)) load(context) else Values()

    fun load(context: android.content.Context): Values {
        val prefs = context.getSharedPreferences(PREFS, 0)
        return Values(
            color = IslandColor.of(prefs.getString(COLOR, null)),
            custom = if (prefs.contains(CUSTOM)) prefs.getInt(CUSTOM, 0).let { opaque(it) } else null,
            corners = IslandCorners.of(prefs.getString(CORNERS, null)),
            speed = IslandSpeed.of(prefs.getString(SPEED, null)),
            haptics = IslandHaptics.of(prefs.getString(HAPTICS, null)),
            gestures = resolveGestures(IslandGesture.entries.mapNotNull { g -> IslandAction.of(prefs.getString(gestureKey(g), null))?.let { g to it } }.toMap()),
        )
    }

    private fun edit(context: android.content.Context, change: android.content.SharedPreferences.Editor.() -> Unit) {
        context.getSharedPreferences(PREFS, 0).edit().apply(change).apply()
        IslandLayoutChanges.changed()
    }

    fun saveColor(context: android.content.Context, color: IslandColor) = edit(context) { if (color == IslandColor.BLACK) remove(COLOR) else putString(COLOR, color.key) }
    /** Saves the typed color. Choosing Custom is [saveColor]; this keeps the number so the page can show what was typed. */
    fun saveCustom(context: android.content.Context, argb: Int) = edit(context) { putInt(CUSTOM, opaque(argb)); putString(COLOR, IslandColor.CUSTOM.key) }
    fun saveCorners(context: android.content.Context, corners: IslandCorners) = edit(context) { if (corners == IslandCorners.ROUND) remove(CORNERS) else putString(CORNERS, corners.key) }
    fun saveSpeed(context: android.content.Context, speed: IslandSpeed) = edit(context) { if (speed == IslandSpeed.STANDARD) remove(SPEED) else putString(SPEED, speed.key) }
    fun saveHaptics(context: android.content.Context, haptics: IslandHaptics) = edit(context) { if (haptics == IslandHaptics.STANDARD) remove(HAPTICS) else putString(HAPTICS, haptics.key) }
    /** Refused (returns false, nothing saved) when it would leave no gesture on Dismiss ([canSet]). */
    fun saveGesture(context: android.content.Context, current: IslandGestureMap, gesture: IslandGesture, action: IslandAction): Boolean {
        if (!canSet(current, gesture, action)) return false
        edit(context) { if (action == defaultGestures[gesture]) remove(gestureKey(gesture)) else putString(gestureKey(gesture), action.key) }
        return true
    }

    /** Clears every option, so the island goes back to how it was. */
    fun reset(context: android.content.Context) = edit(context) {
        remove(COLOR); remove(CUSTOM); remove(CORNERS); remove(SPEED); remove(HAPTICS)
        IslandGesture.entries.forEach { remove(gestureKey(it)) }
    }

    // ---- Color ------------------------------------------------------------------------------------------------------

    private val OPAQUE = FolioIsland.Black.toInt()
    private val WHITE = FolioIsland.White.toInt()
    private fun opaque(argb: Int) = argb or OPAQUE

    fun backgroundOf(color: IslandColor, custom: Int?): Int = when (color) {
        IslandColor.CUSTOM -> readableBackground(custom ?: IslandColor.BLACK.argb!!)
        else -> color.argb!!
    }

    /**
     * Whether the saved color is drawn at all. The island stays solid black while it wraps a real camera hole, because any
     * lighter color would show as a ring around the lens. Moved off the camera, or over a camera that is not a reported
     * cutout (the Fold's inner screen has its camera under the display), the color applies.
     */
    fun colorApplies(wrapsRealCamera: Boolean) = !wrapsRealCamera

    /**
     * Same, for the island as it is right now. An open card is much larger than the lens and covers it, so no ring can
     * show and the saved color may be drawn even over a real camera. Only the resting pill stays black there.
     */
    fun colorApplies(wrapsRealCamera: Boolean, open: Boolean) = open || colorApplies(wrapsRealCamera)

    /**
     * Whether the island is drawn around a camera hole that Android reports: it was not moved ([custom] null), the
     * reported cutout is on the top edge, and there is one.
     */
    fun wrapsRealCamera(custom: IslandPosition?, camera: IntArray?, reportedCutout: Boolean, windowWidthPx: Int, windowHeightPx: Int): Boolean =
        custom == null && reportedCutout && camera != null && cameraOnTopEdge(camera, windowWidthPx, windowHeightPx)

    /** "#RRGGBB", "RRGGBB", "#RGB" or "RGB" as an opaque color, or null when it is not one. */
    fun parseHex(text: String): Int? {
        val digits = text.trim().removePrefix("#")
        if (digits.length != 6 && digits.length != 3) return null
        if (!digits.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return null
        val full = if (digits.length == 3) digits.map { "$it$it" }.joinToString("") else digits
        return opaque(full.toLong(16).toInt())
    }

    fun toHex(argb: Int): String = "%06X".format(argb and 0xFFFFFF)

    private fun channel(value: Int): Double = (value / 255.0).let { if (it <= .03928) it / 12.92 else ((it + .055) / 1.055).pow(2.4) }

    /** WCAG relative luminance of a color's red, green and blue. */
    fun luminance(argb: Int): Double =
        .2126 * channel(argb shr 16 and 0xFF) + .7152 * channel(argb shr 8 and 0xFF) + .0722 * channel(argb and 0xFF)

    /** WCAG contrast ratio, 1 to 21. */
    fun contrast(a: Int, b: Int): Double {
        val la = luminance(a); val lb = luminance(b)
        return (maxOf(la, lb) + .05) / (minOf(la, lb) + .05)
    }

    /** White or black, whichever reads better on [background]; white when they tie. */
    fun inkFor(background: Int): Int = if (contrast(WHITE, background) >= contrast(OPAQUE, background)) WHITE else OPAQUE

    /** [foreground] at [alpha] drawn over [background]. */
    fun blend(foreground: Int, background: Int, alpha: Float): Int {
        fun mix(shift: Int) = (((foreground shr shift and 0xFF) * alpha) + ((background shr shift and 0xFF) * (1f - alpha))).roundToInt().coerceIn(0, 255)
        return OPAQUE or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
    }

    /** Contrast of the island's secondary text (the best ink at [FolioIsland.SECONDARY_TEXT_ALPHA]) against [background]. */
    fun secondaryTextContrast(background: Int): Double =
        contrast(blend(inkFor(background), background, FolioIsland.SECONDARY_TEXT_ALPHA), background)

    /**
     * [argb] made opaque, and moved lighter or darker (toward the end its ink comes from) one step at a time until its
     * text reaches [minContrast]. A color that already reads well comes back as it was, apart from being made opaque.
     */
    fun readableBackground(argb: Int, minContrast: Double = FolioIsland.TEXT_MIN_CONTRAST): Int {
        val start = opaque(argb)
        if (secondaryTextContrast(start) >= minContrast) return start
        val (h, s, l) = toHsl(start)
        val darker = inkFor(start) == WHITE
        var next = l
        while (true) {
            next = if (darker) maxOf(0f, next - FolioIsland.NUDGE_STEP) else minOf(1f, next + FolioIsland.NUDGE_STEP)
            val candidate = fromHsl(h, s, next)
            if (secondaryTextContrast(candidate) >= minContrast || next <= 0f || next >= 1f) return candidate
        }
    }

    private fun toHsl(argb: Int): Triple<Float, Float, Float> {
        val r = (argb shr 16 and 0xFF) / 255f; val g = (argb shr 8 and 0xFF) / 255f; val b = (argb and 0xFF) / 255f
        val max = maxOf(r, g, b); val min = minOf(r, g, b)
        val l = (max + min) / 2f
        if (max == min) return Triple(0f, 0f, l)
        val d = max - min
        val s = if (l > .5f) d / (2f - max - min) else d / (max + min)
        val h = when (max) { r -> (g - b) / d + (if (g < b) 6f else 0f); g -> (b - r) / d + 2f; else -> (r - g) / d + 4f } / 6f
        return Triple(h, s, l)
    }

    private fun fromHsl(h: Float, s: Float, l: Float): Int {
        fun channel(t0: Float): Int {
            val t = if (t0 < 0f) t0 + 1f else if (t0 > 1f) t0 - 1f else t0
            val q = if (l < .5f) l * (1f + s) else l + s - l * s
            val p = 2f * l - q
            val v = when { t < 1f / 6f -> p + (q - p) * 6f * t; t < .5f -> q; t < 2f / 3f -> p + (q - p) * (2f / 3f - t) * 6f; else -> p }
            return (v * 255f).roundToInt().coerceIn(0, 255)
        }
        if (s == 0f) return OPAQUE or ((l * 255f).roundToInt().coerceIn(0, 255) * 0x010101)
        return OPAQUE or (channel(h + 1f / 3f) shl 16) or (channel(h) shl 8) or channel(h - 1f / 3f)
    }

    // ---- Corner radius ----------------------------------------------------------------------------------------------

    /**
     * The compact pill's corner radius, in dp. Round is half the height, as before. A squarer step keeps at least the
     * camera hole's own curve ([holeRadiusDp], zero when the island is not around a camera), so a corner never cuts closer
     * to the lens than the hole's own edge does, and never goes past fully round.
     */
    fun pillRadius(corners: IslandCorners, pillHeightDp: Float, holeRadiusDp: Float): Float {
        val round = pillHeightDp / 2f
        if (corners == IslandCorners.ROUND) return round
        return (round * corners.share).coerceAtLeast(minOf(holeRadiusDp, round)).coerceAtMost(round)
    }

    // ---- Animation speed --------------------------------------------------------------------------------------------

    /**
     * The multiplier for the island's own springs and fades. [animationSpeed] is Settings › Gestures › Animation Speed's
     * factor, which already scales every spring, so the product of the two is capped at [FolioMotion.ISLAND_FASTEST]:
     * however both are set, a change is never quick enough to read as a flicker. Standard is always exactly 1.
     */
    fun speedMultiplier(speed: IslandSpeed, animationSpeed: Float): Float = when (speed) {
        IslandSpeed.STANDARD -> FolioMotion.ISLAND_STANDARD
        IslandSpeed.SLOWER -> FolioMotion.ISLAND_SLOWER
        IslandSpeed.FASTER -> minOf(FolioMotion.ISLAND_FASTER, maxOf(1f, FolioMotion.ISLAND_FASTEST / animationSpeed.coerceAtLeast(.01f)))
    }

    /** A duration shortened or lengthened by [multiplier]; never below [floorMs] (or the duration itself, when that is shorter already). */
    fun scaledMs(ms: Int, multiplier: Float, floorMs: Int = FolioMotion.ISLAND_MIN_FADE_MS): Int =
        if (multiplier == 1f) ms else (ms / multiplier).roundToInt().coerceAtLeast(minOf(ms, floorMs))

    // ---- Haptics ----------------------------------------------------------------------------------------------------

    /** What the island buzzes at: nothing at all when the system's touch feedback is off, whatever was chosen. */
    fun effectiveHaptics(level: IslandHaptics, systemTouchFeedbackOn: Boolean): IslandHaptics =
        if (systemTouchFeedbackOn) level else IslandHaptics.OFF

    /** Android's Touch feedback switch (Settings › Sound and vibration). On unless it was switched off. */
    fun systemTouchFeedbackOn(context: android.content.Context): Boolean =
        runCatching { android.provider.Settings.System.getInt(context.contentResolver, android.provider.Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) != 0 }.getOrDefault(true)

    // ---- Gestures ---------------------------------------------------------------------------------------------------

    /** What a gesture may be set to. Pick up to move is only for Hold. */
    fun choices(gesture: IslandGesture): List<IslandAction> =
        if (gesture == IslandGesture.HOLD) listOf(IslandAction.MOVE, IslandAction.EXPAND, IslandAction.OPEN_APP, IslandAction.DISMISS, IslandAction.NOTHING)
        else listOf(IslandAction.EXPAND, IslandAction.OPEN_APP, IslandAction.DISMISS, IslandAction.NOTHING)

    /**
     * A full, valid map from whatever was saved: missing gestures take their default, an action a gesture may not have
     * takes its default too, and when nothing is left on Dismiss, Swipe up goes back to it, so a card can always be put away.
     */
    fun resolveGestures(saved: IslandGestureMap): IslandGestureMap {
        val map = IslandGesture.entries.associateWith { g -> saved[g]?.takeIf { it in choices(g) } ?: defaultGestures.getValue(g) }
        return if (IslandAction.DISMISS in map.values) map else map + (IslandGesture.SWIPE_UP to IslandAction.DISMISS)
    }

    /** Whether [gesture] may be set to [action] without leaving no gesture on Dismiss. */
    fun canSet(current: IslandGestureMap, gesture: IslandGesture, action: IslandAction): Boolean =
        action in choices(gesture) && IslandAction.DISMISS in (current + (gesture to action)).values

    /** True when every gesture is at its default, so the island keeps the touch handling it always had. */
    fun gesturesAreDefault(gestures: IslandGestureMap): Boolean = resolveGestures(gestures) == defaultGestures

    /** The action a tap does on the island over other apps, which has no card to expand: Expand opens the app, as a tap always did there. */
    fun overlayAction(action: IslandAction): IslandAction = if (action == IslandAction.EXPAND) IslandAction.OPEN_APP else action
}

/** Text and icons on the island: white, or black on a light custom color ([IslandStyle.inkFor]). */
internal val LocalIslandInk = compositionLocalOf { Color.White }

/** The Advanced island options in force for the island being drawn. */
internal val LocalIslandStyle = compositionLocalOf { IslandStyle.Values() }

/** Does [type] unless the options or the system's touch feedback say not to; Light is the gentlest tick. */
internal fun HapticFeedback.island(level: IslandHaptics, systemOn: Boolean, standard: HapticFeedbackType) {
    when (IslandStyle.effectiveHaptics(level, systemOn)) {
        IslandHaptics.OFF -> Unit
        IslandHaptics.LIGHT -> performHapticFeedback(HapticFeedbackType.TextHandleMove)
        IslandHaptics.STANDARD -> performHapticFeedback(standard)
    }
}

/**
 * Touch handling for an island whose gestures are not all at their defaults. A tap waits the system's double-tap timeout
 * only while Double tap is set to something (it is how the platform tells the two apart); with it left at Nothing a tap
 * is immediate. Hold is only handled here when it is set to an action (pick up to move stays with the island's own drag),
 * and a swipe up only when it is set to open or expand (putting a card away is the cards' own swipe).
 */
internal fun Modifier.islandGestureInput(gestures: IslandGestureMap, onGesture: (IslandGesture, IslandAction) -> Unit): Modifier = composed {
    val latest by rememberUpdatedState(onGesture)
    val tap = gestures.getValue(IslandGesture.TAP)
    val double = gestures.getValue(IslandGesture.DOUBLE_TAP)
    val hold = gestures.getValue(IslandGesture.HOLD)
    val swipe = gestures.getValue(IslandGesture.SWIPE_UP)
    val reach = with(LocalDensity.current) { FolioIsland.SWIPE_UP_DP.dp.toPx() }
    val taps = Modifier.pointerInput(tap, double, hold) {
        detectTapGestures(
            onTap = { latest(IslandGesture.TAP, tap) },
            onDoubleTap = if (double != IslandAction.NOTHING) ({ latest(IslandGesture.DOUBLE_TAP, double) }) else null,
            onLongPress = if (hold != IslandAction.MOVE && hold != IslandAction.NOTHING) ({ latest(IslandGesture.HOLD, hold) }) else null,
        )
    }
    val swipes = if (swipe == IslandAction.EXPAND || swipe == IslandAction.OPEN_APP) Modifier.pointerInput(swipe) {
        var travel = 0f
        detectVerticalDragGestures(
            onDragStart = { travel = 0f },
            onDragEnd = { if (travel < -reach) latest(IslandGesture.SWIPE_UP, swipe) },
        ) { change, dy -> change.consume(); travel += dy }
    } else Modifier
    this.then(taps).then(swipes)
}
