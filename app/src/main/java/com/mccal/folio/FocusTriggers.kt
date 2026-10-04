package com.mccal.folio

/** How the screen is folded, as far as a Focus trigger cares. */
enum class FoldState(val key: String) {
    UNFOLDED("unfolded"), COVER("cover"), TENT("tent");
    companion object { fun parse(key: String?) = entries.firstOrNull { it.key == key } }
}

/**
 * What can turn a Focus on by itself besides its schedule. All of it is local and none needs a permission: how the
 * phone is folded, whether it is charging, and whether headphones or a speaker are connected.
 */
data class FocusTriggerSet(
    /** Turns the Focus on while the phone is in this fold state; null leaves folding out of it. */
    val fold: FoldState? = null,
    val charging: Boolean = false,
    val headphones: Boolean = false,
) {
    val any: Boolean get() = fold != null || charging || headphones
}

/** What the phone is doing right now. A null [fold] means unknown (a phone that doesn't fold, or not measured yet). */
data class FocusSignals(val fold: FoldState? = null, val charging: Boolean = false, val headphones: Boolean = false)

/** Why a Focus turned on by itself, for the line Home and Settings show ("Work · you unfolded"). */
enum class FocusReason(val key: String) { FOLD("fold"), CHARGING("charging"), HEADPHONES("headphones") }

/**
 * What Folio remembers between signal changes: the last signals it saw, the Focus it turned on by itself (so it only
 * turns that one off, never one you turned on by hand), and Focuses you turned off by hand while their trigger still
 * held (they stay off until the next change, as on iOS).
 */
data class FocusTriggerState(
    val signals: FocusSignals? = null,
    val byTrigger: String? = null,
    val reason: FocusReason? = null,
    val suppressed: Set<String> = emptySet(),
)

/** What to do after a change: the Focus that should be on now, and the state to keep. [changed] is false when nothing needs doing. */
data class FocusTriggerResult(val active: String?, val state: FocusTriggerState, val changed: Boolean)

/** Focus triggers as pure functions, unit-tested. The Android side only reads the signals and calls these. */
internal object FocusTriggers {
    /** The first Focus (in list order) whose trigger holds in [signals], with the reason. A Focus you turned off by hand is skipped. */
    fun wanted(modes: List<FocusMode>, signals: FocusSignals, suppressed: Set<String> = emptySet()): Pair<FocusMode, FocusReason>? {
        for (mode in modes) {
            if (mode.id in suppressed) continue
            reason(mode.triggers, signals)?.let { return mode to it }
        }
        return null
    }

    fun reason(triggers: FocusTriggerSet, signals: FocusSignals): FocusReason? = when {
        triggers.fold != null && signals.fold == triggers.fold -> FocusReason.FOLD
        triggers.charging && signals.charging -> FocusReason.CHARGING
        triggers.headphones && signals.headphones -> FocusReason.HEADPHONES
        else -> null
    }

    /**
     * The phone's signals changed. A Focus whose trigger now holds turns on; the Focus Folio turned on by itself turns off
     * when no trigger holds any more; a Focus you turned on by hand is never turned off here. Any change also clears the
     * "you turned it off" memory, because that only lasts until the next change.
     */
    fun onSignals(modes: List<FocusMode>, active: String?, state: FocusTriggerState, now: FocusSignals): FocusTriggerResult {
        if (state.signals == now) return FocusTriggerResult(active, state, false)
        val fresh = state.copy(signals = now, suppressed = emptySet())
        val hit = wanted(modes, now)
        return when {
            hit != null && hit.first.id != active -> FocusTriggerResult(hit.first.id, fresh.copy(byTrigger = hit.first.id, reason = hit.second), true)
            // Already on: if you turned it on yourself it stays yours, so it is not turned off when the trigger ends.
            hit != null -> FocusTriggerResult(active, fresh.copy(byTrigger = state.byTrigger, reason = if (state.byTrigger == active) hit.second else null), false)
            active != null && active == state.byTrigger -> FocusTriggerResult(null, fresh.copy(byTrigger = null, reason = null), true)
            else -> FocusTriggerResult(active, fresh.copy(byTrigger = null, reason = null), false)
        }
    }

    /**
     * The Focus was turned off by hand. If a trigger still holds for it, it stays off until the signals change, so
     * turning it off isn't undone a second later.
     */
    fun onTurnedOffByHand(modes: List<FocusMode>, state: FocusTriggerState, turnedOff: String?): FocusTriggerState {
        val signals = state.signals ?: return state.copy(byTrigger = null, reason = null)
        val holds = modes.firstOrNull { it.id == turnedOff }?.let { reason(it.triggers, signals) != null } == true
        return state.copy(byTrigger = null, reason = null, suppressed = if (holds && turnedOff != null) state.suppressed + turnedOff else state.suppressed)
    }

    /** A Focus was turned on by hand: it is no longer "turned on by a trigger", so a trigger ending won't turn it off. */
    fun onTurnedOnByHand(state: FocusTriggerState) = state.copy(byTrigger = null, reason = null)
}
