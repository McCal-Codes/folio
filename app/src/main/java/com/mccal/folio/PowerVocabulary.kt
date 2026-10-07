package com.mccal.folio

/**
 * The words Folio uses to say what an action can do and what it needs (docs/plan-power-tools.md, ADR 0008). They carry
 * no behavior: nothing here asks Android for anything. They give the Capability Viewer, the Inspector and, later, the
 * Capability Broker one shared vocabulary, so a feature asks "can this action run" and never "is Shizuku running".
 */

/** Every privilege tier in ADR 0008, A0 to A5. Folio uses only A0 to A2 today (see [CapabilityTier]); a row only says what is true now. */
internal enum class PrivilegeTier(val code: String) {
    STANDARD("A0"), NOTIFICATIONS("A1"), ACCESSIBILITY("A2"), SHIZUKU("A3"), ROOT("A4"), HOOKS("A5");

    /** Whether Folio has any code that uses this tier. A3 to A5 are decisions on paper, not features. */
    val inUse get() = ordinal <= ACCESSIBILITY.ordinal
}

/**
 * How much an operation can hurt, independent of how much access it needs: reading a root-only file is "observe",
 * restarting System UI is "disruptive". Ordered from least to most, so a comparison says which asks for more care.
 */
internal enum class OperationRisk {
    /** Reads only. Nothing to undo. */
    OBSERVE,
    /** Undoes itself or can be undone automatically. */
    REVERSIBLE,
    /** Persists, and Folio knows how to put it back. */
    STATEFUL,
    /** Can interrupt apps or the system while it runs. */
    DISRUPTIVE,
    /** Could make Android unstable. */
    CRITICAL,
    /** Whether it works is not guaranteed on this phone. */
    EXPERIMENTAL;

    /** Disruptive and above is explained and confirmed before it runs. */
    val confirmFirst get() = this >= DISRUPTIVE
    /** Critical changes are applied for a short time and kept only if the person says so. */
    val timedKeep get() = this == CRITICAL
    /** Whether there is a previous state to restore: reads have none, everything else must. */
    val needsRollback get() = this != OBSERVE
}

/** Whether an operation is expected to work on this phone. Ordered from best to worst. */
internal enum class CapabilityStatus {
    SUPPORTED, LIKELY, EXPERIMENTAL, UNSUPPORTED, BLOCKED;

    /** It may run: tested, probably fine, or worth trying with a warning. */
    val runs get() = this <= EXPERIMENTAL
    /** It runs, but the person is told it is not guaranteed. */
    val warns get() = this == EXPERIMENTAL
}

/** The state of one way of getting access (a permission, a service, a bridge). Losing access is a normal state, never an error. */
internal enum class BackendStatus {
    AVAILABLE, NOT_GRANTED, LOST, UNSUPPORTED;

    companion object {
        /** The state for a permission or service that is [granted] right now, on a phone that [supported] it. */
        fun of(granted: Boolean, supported: Boolean = true) = when {
            !supported -> UNSUPPORTED
            granted -> AVAILABLE
            else -> NOT_GRANTED
        }
    }
}
