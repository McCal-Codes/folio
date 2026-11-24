package com.mccal.folio

/**
 * Stops a hinge sensor that cries "folded" while the phone is open from re-dimming Home and launching the fold bridge over and over.
 *
 * A real fold always ends with the inner display switching to the cover. So a closed reading that never leads to that switch
 * (the screen is still the inner one 1.8 s later, or the hinge reads open again without it ever switching) was a false alarm.
 * Each one mutes fold starts for a while, longer for each one in a row, and a real display switch clears the record. Seen on a
 * plugged-in Fold on 21 Nov 2026: the sensor read 0 and 180 in turn every few seconds while the device state stayed OPENED.
 */
internal class FalseFoldGuard(
    private val firstMuteMs: Long = FIRST_MUTE_MS,
    private val maxMuteMs: Long = MAX_MUTE_MS,
    private val forgetAfterMs: Long = FORGET_AFTER_MS,
) {
    private var strikes = 0
    private var lastAlarmAt = 0L
    private var mutedUntil = 0L

    /** Whether a fold that starts at [now] should be ignored. */
    fun muted(now: Long): Boolean = now < mutedUntil

    /** A closed reading came and went with the inner display still showing: mute folds for longer than last time. */
    fun falseAlarm(now: Long) {
        if (strikes > 0 && now - lastAlarmAt > forgetAfterMs) strikes = 0
        strikes++
        lastAlarmAt = now
        mutedUntil = now + minOf(maxMuteMs, firstMuteMs shl (strikes - 1).coerceAtMost(MAX_DOUBLINGS))
    }

    /** The display really switched, so the sensor can be trusted again. */
    fun realFold() { strikes = 0; mutedUntil = 0L }

    private companion object {
        const val FIRST_MUTE_MS = 10_000L
        const val MAX_MUTE_MS = 300_000L
        const val FORGET_AFTER_MS = 1_800_000L
        const val MAX_DOUBLINGS = 8
    }
}
