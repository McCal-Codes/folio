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

    /**
     * The endings to add to the quick-crash count: a counted kind, newer than [seenUntilMs] (so none is counted twice
     * across starts), that ended within [windowMs] of the process starting at [previousStartMs]. With no recorded
     * start (the first run after this was added) nothing is counted, because there is nothing to measure against.
     */
    fun quick(exits: List<ExitRecord>, previousStartMs: Long, seenUntilMs: Long, windowMs: Long): List<ExitRecord> =
        if (previousStartMs <= 0L) emptyList()
        else exits.filter { it.reason in COUNTED && it.timestampMs > seenUntilMs && it.timestampMs - previousStartMs in 0 until windowMs }

    /** Whether the process before this one, if it is a new ending, ended in a counted way, so Package Safe Mode may blame a package. */
    fun crashedLast(exits: List<ExitRecord>, seenUntilMs: Long): Boolean =
        exits.filter { it.timestampMs > seenUntilMs }.maxByOrNull { it.timestampMs }?.reason in COUNTED

    /** Android's record of the last few endings of this app, newest first; empty when it cannot be read. */
    fun read(context: Context): List<ExitRecord> = runCatching {
        context.getSystemService(ActivityManager::class.java).getHistoricalProcessExitReasons(context.packageName, 0, 5)
            .map { ExitRecord(it.reason, it.timestamp) }
    }.getOrDefault(emptyList())
}
