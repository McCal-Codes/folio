package com.mccal.folio

import android.content.Context
import android.content.SharedPreferences
import android.view.HapticFeedbackConstants
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlin.math.abs

/**
 * Hinge detents: feel the Fold click as it opens and closes, as if the hinge had stops. Soft ticks at 45, 90 and 135 degrees, a soft tick
 * as it closes, and a firmer click at flat. With a continuous hinge angle (the optional root feed) all of them play; with the public
 * sensor, which only reports 0, 90 and 180, Folio ticks once per position the Fold lands on. It replaces the single tick Folio plays
 * as the hinge passes halfway, so there is never a double tick at 90. Uses Folio's own haptics, so it is silent when Android's touch
 * feedback or Folio's Haptics switch is off. Mocked in the Lab (scene hinge-detents) on 8 Oct 2026.
 */
internal enum class DetentKind { TICK, CLICK }

/** One stop. The angle it is felt at is [centerDegrees] plus or minus [bandDegrees]: the stop plays when the hinge leaves that band on the other side. */
internal data class Detent(val centerDegrees: Float, val bandDegrees: Float, val kind: DetentKind, val labelDegrees: Int)

internal enum class DetentStrength(val id: Int, @StringRes val label: Int) {
    LIGHT(0, R.string.detent_light), MEDIUM(1, R.string.detent_medium), FIRM(2, R.string.detent_firm);
    companion object { fun of(id: Int) = entries.firstOrNull { it.id == id } ?: MEDIUM }
}

internal data class HingeDetentOptions(val on: Boolean = true, val strength: DetentStrength = DetentStrength.MEDIUM) {
    companion object {
        const val KEY_ON = "fold_detents"
        const val KEY_STRENGTH = "fold_detents_strength"
        fun read(prefs: SharedPreferences) = HingeDetentOptions(prefs.getBoolean(KEY_ON, true), DetentStrength.of(prefs.getInt(KEY_STRENGTH, DetentStrength.MEDIUM.id)))
        fun write(prefs: SharedPreferences, o: HingeDetentOptions) { prefs.edit().putBoolean(KEY_ON, o.on).putInt(KEY_STRENGTH, o.strength.id).apply() }
    }
}

/** The options as a live value: changing a switch in Settings is felt on the next fold. */
@Composable
internal fun rememberHingeDetentOptions(context: Context): State<HingeDetentOptions> {
    val prefs = remember { FoldMotionOptions.prefs(context) }
    val state = remember { mutableStateOf(HingeDetentOptions.read(prefs)) }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
            if (key == HingeDetentOptions.KEY_ON || key == HingeDetentOptions.KEY_STRENGTH) state.value = HingeDetentOptions.read(p)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return state
}

internal object HingeDetents {
    /** Interior stops sit on their own angle; closed and flat are set just inside the ends, where the sensor stops reporting. */
    val STOPS = listOf(
        Detent(4f, 2f, DetentKind.TICK, 0),
        Detent(45f, 3f, DetentKind.TICK, 45),
        Detent(90f, 3f, DetentKind.TICK, 90),
        Detent(135f, 3f, DetentKind.TICK, 135),
        Detent(176f, 2f, DetentKind.CLICK, 180),
    )
    /** The positions a stepped sensor reports, and the stop each one is felt as. */
    private val STEPS = listOf(0f to DetentKind.TICK, 90f to DetentKind.TICK, 180f to DetentKind.CLICK)
    /** Two stops closer together in time than this are one: a flick across the whole range plays one haptic, not five. */
    const val MIN_GAP_MS = 60L

    /** The stop felt when the hinge sits at [degrees] with a continuous sensor, or null between stops (used by the Settings preview). */
    fun feltAt(degrees: Float): Detent? = when {
        degrees <= 6f -> STOPS.first()
        degrees >= 173f -> STOPS.last()
        else -> STOPS.firstOrNull { it.kind == DetentKind.TICK && it.labelDegrees > 0 && abs(degrees - it.centerDegrees) <= 4f }
    }

    /** The android.view.HapticFeedbackConstants value for a stop, so the strength setting maps to what the phone can do. */
    fun hapticFor(kind: DetentKind, strength: DetentStrength, sdk: Int): Int = when (kind) {
        DetentKind.TICK -> when (strength) {
            DetentStrength.LIGHT -> HapticFeedbackConstants.CLOCK_TICK
            DetentStrength.MEDIUM -> if (sdk >= 34) HapticFeedbackConstants.SEGMENT_TICK else HapticFeedbackConstants.CLOCK_TICK
            DetentStrength.FIRM -> HapticFeedbackConstants.CONTEXT_CLICK
        }
        DetentKind.CLICK -> when (strength) {
            DetentStrength.LIGHT -> HapticFeedbackConstants.CONTEXT_CLICK
            DetentStrength.MEDIUM -> if (sdk >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.CONTEXT_CLICK
            DetentStrength.FIRM -> HapticFeedbackConstants.LONG_PRESS
        }
    }

    /**
     * Reads hinge readings and says when a stop was passed. A continuous sensor flips each stop's side only once the hinge is clear of its
     * band, so a hand holding the Fold on a stop, or jitter at its edge, plays it once. A stepped sensor plays once per position it lands on.
     */
    class Tracker {
        private var sides: IntArray? = null
        private var step: Int? = null
        private var lastAtMs: Long? = null

        fun reset() { sides = null; step = null }

        /** At most one stop per reading: the firm click if any was passed, otherwise the stop nearest the hinge. */
        fun feed(degrees: Float, continuous: Boolean, nowMs: Long): Detent? {
            val found = if (continuous) continuousStop(degrees) else steppedStop(degrees)
            if (found == null) return null
            if (lastAtMs?.let { nowMs - it < MIN_GAP_MS } == true) return null
            lastAtMs = nowMs
            return found
        }

        private fun continuousStop(degrees: Float): Detent? {
            step = null
            val previous = sides
            val current = IntArray(STOPS.size) { i ->
                val s = STOPS[i]
                when {
                    degrees > s.centerDegrees + s.bandDegrees -> 1
                    degrees < s.centerDegrees - s.bandDegrees -> -1
                    else -> previous?.get(i) ?: if (degrees >= s.centerDegrees) 1 else -1
                }
            }
            sides = current
            if (previous == null) return null
            val flipped = STOPS.indices.filter { previous[it] != current[it] }.map { STOPS[it] }
            return flipped.firstOrNull { it.kind == DetentKind.CLICK } ?: flipped.minByOrNull { abs(it.centerDegrees - degrees) }
        }

        private fun steppedStop(degrees: Float): Detent? {
            sides = null
            val nearest = STEPS.indices.minByOrNull { abs(STEPS[it].first - degrees) } ?: return null
            val previous = step
            step = nearest
            if (previous == null || previous == nearest) return null
            return if (STEPS[nearest].second == DetentKind.CLICK) STOPS.last() else STOPS.first { it.labelDegrees == STEPS[nearest].first.toInt() }
        }
    }
}

/** Fold effect › Hinge detents: the switch, the strength, and a preview that says what is felt at an angle. */
@Composable
internal fun HingeDetentsCard(foliohaptics: Boolean) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val options by rememberHingeDetentOptions(context)
    val prefs = remember { FoldMotionOptions.prefs(context) }
    val onRoot = remember { RootHingeStore.useInFold(context) && hingeSource(SystemBridge.broker(context)) == HingeSource.ROOT_HELPER }
    val active = options.on && foliohaptics
    var angle by remember { androidx.compose.runtime.mutableFloatStateOf(100f) }
    SettingsCard(androidx.compose.ui.res.stringResource(R.string.hinge_detents)) {
        SettingsSwitch(androidx.compose.ui.res.stringResource(R.string.hinge_detents), options.on, { HingeDetentOptions.write(prefs, options.copy(on = it)) }, "hinge-detents-switch")
        CardNote(androidx.compose.ui.res.stringResource(R.string.hinge_detents_note))
        IosSegmented(DetentStrength.entries.map { it to androidx.compose.ui.res.stringResource(it.label) }, options.strength,
            { if (active) HingeDetentOptions.write(prefs, options.copy(strength = it)) }, Modifier.padding(vertical = FolioSpace.SNUG.dp), tag = "hinge-detents-strength")
        val degrees = angle.toInt()
        val felt = if (!onRoot) HingeDetents.feltAt(angle)?.takeIf { it.labelDegrees == 0 || it.labelDegrees == 180 || it.labelDegrees == 90 } else HingeDetents.feltAt(angle)
        androidx.compose.material3.Slider(angle, { angle = it }, valueRange = 0f..180f, enabled = active,
            modifier = Modifier.semantics { contentDescription = context.getString(R.string.detent_angle, degrees) }.testTag("hinge-detents-angle"))
        androidx.compose.material3.Text(
            when {
                !active -> androidx.compose.ui.res.stringResource(R.string.detent_none)
                felt == null -> androidx.compose.ui.res.stringResource(R.string.detent_none)
                felt.kind == DetentKind.CLICK -> androidx.compose.ui.res.stringResource(R.string.detent_felt_flat)
                felt.labelDegrees == 0 -> androidx.compose.ui.res.stringResource(R.string.detent_felt_closed)
                else -> androidx.compose.ui.res.stringResource(R.string.detent_felt_tick, felt.labelDegrees)
            },
            Modifier.padding(horizontal = FolioSpace.MEDIUM.dp, vertical = FolioSpace.SNUG.dp).testTag("hinge-detents-felt"), fontSize = FolioType.BODY.sp)
        CardNote(androidx.compose.ui.res.stringResource(
            if (!foliohaptics) R.string.detent_haptics_off else if (onRoot) R.string.detent_way_root else R.string.detent_way_steps))
        CardNote(androidx.compose.ui.res.stringResource(R.string.detent_footer))
    }
}
