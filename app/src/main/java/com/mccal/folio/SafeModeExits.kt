package com.mccal.folio

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context

/** How a past Folio process ended, as Android recorded it. */
internal data class ExitRecord(val reason: Int, val timestampMs: Long)

/**
 * Which endings count toward Safe Mode. The crash handler already counts uncaught Java exceptions, so those are left
 * out here to avoid counting one crash twice; what it cannot see is a native crash, a freeze (ANR) and a process that
 * failed to start, and a loop of those never reached Safe Mode before.
 */
internal object SafeModeExits {
    private val COUNTED = setOf(ApplicationExitInfo.REASON_CRASH_NATIVE, ApplicationExitInfo.REASON_ANR, ApplicationExitInfo.REASON_INITIALIZATION_FAILURE)

    /** A failed start the process never got to write a marker for is only counted while it is this recent: older ones are history. */
    const val INIT_HORIZON_MS = 10 * 60_000L

    /**
     * The endings to add to the quick-crash count: a counted kind, newer than [seenUntilMs] (so none is counted twice
     * across starts), that ended within [windowMs] of the process starting at [previousStartMs]. With no recorded
     * start (the first run after this was added) nothing is counted for those, because there is nothing to measure against.
     * A process that failed to start ([ApplicationExitInfo.REASON_INITIALIZATION_FAILURE]) is the exception: it died before
     * it could write a start marker, so [previousStartMs] is an older, successful process and a time comparison against it
     * would throw away exactly the loop this is for. It counts if it is newer than [seenUntilMs] and within
     * [INIT_HORIZON_MS] of [nowMs].
     */
    fun quick(exits: List<ExitRecord>, previousStartMs: Long, seenUntilMs: Long, windowMs: Long, nowMs: Long = System.currentTimeMillis()): List<ExitRecord> =
        exits.filter { it.reason in COUNTED && it.timestampMs > seenUntilMs && when (it.reason) {
            ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> nowMs - it.timestampMs in 0..INIT_HORIZON_MS
            else -> previousStartMs > 0L && it.timestampMs - previousStartMs in 0 until windowMs
        } }

    /** Whether the process before this one, if it is a new ending, ended in a counted way, so Package Safe Mode may blame a package. */
    fun crashedLast(exits: List<ExitRecord>, seenUntilMs: Long): Boolean =
        exits.filter { it.timestampMs > seenUntilMs }.maxByOrNull { it.timestampMs }?.reason in COUNTED

    /** Android's record of the last few endings of this app, newest first; empty when it cannot be read. */
    fun read(context: Context): List<ExitRecord> = runCatching {
        context.getSystemService(ActivityManager::class.java).getHistoricalProcessExitReasons(context.packageName, 0, 5)
            .map { ExitRecord(it.reason, it.timestamp) }
    }.getOrDefault(emptyList())
}
