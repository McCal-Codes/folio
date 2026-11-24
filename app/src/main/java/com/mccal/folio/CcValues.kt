package com.mccal.folio

import android.media.AudioManager

/**
 * The rules behind Control Center's value tiles (Sound mode, Screen timeout): a tap steps a value to the next one, and the tile shows where it
 * is. Pure, so the steps are tested without a phone. How a tile that holds a value is drawn is [CcValue]; what it reads and writes is [DeviceControls].
 */

/** The secure setting Android keeps Night Light's on state in. Readable by anyone; writing it needs WRITE_SECURE_SETTINGS. */
internal const val NIGHT_LIGHT_KEY = "night_display_activated"

/** The screen timeouts a tap steps through, in ms: the same stops Android's own Display settings offers, shortest first. */
internal val CC_TIMEOUTS = listOf(15_000, 30_000, 60_000, 120_000, 300_000, 600_000)

/** The timeout after [currentMs]; the shortest again once the longest is reached or the current one is not a stop and is longer than all. */
internal fun ccNextTimeout(currentMs: Int): Int = CC_TIMEOUTS.firstOrNull { it > currentMs } ?: CC_TIMEOUTS.first()

/** From this long a timeout reads as Never: Android stores "never" as the largest Int (35,791 minutes), and no real stop is an hour or more. */
internal const val CC_TIMEOUT_NEVER_MS = 3_600_000

/** A timeout as a number and whether it counts minutes (30 s is 30 and false, 2 min is 2 and true); 0 for a timeout that is not known, -1 for Never. */
internal fun ccTimeoutLabel(ms: Int): Pair<Int, Boolean> = when {
    ms <= 0 -> 0 to false
    ms >= CC_TIMEOUT_NEVER_MS -> -1 to false
    ms < 60_000 -> (ms / 1000) to false
    else -> (ms / 60_000) to true
}

/**
 * The ringer after [current]: Ring, then Vibrate, then Silent, then Ring again. Silent is skipped (Vibrate goes straight back to Ring) where
 * [canSilent] is false, because Android switches Do Not Disturb on for it and refuses an app that has no access to that.
 */
internal fun ccNextRinger(current: Int, canSilent: Boolean): Int = when (current) {
    AudioManager.RINGER_MODE_NORMAL -> AudioManager.RINGER_MODE_VIBRATE
    AudioManager.RINGER_MODE_VIBRATE -> if (canSilent) AudioManager.RINGER_MODE_SILENT else AudioManager.RINGER_MODE_NORMAL
    else -> AudioManager.RINGER_MODE_NORMAL
}
