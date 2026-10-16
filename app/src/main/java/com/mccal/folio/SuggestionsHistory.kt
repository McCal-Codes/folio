package com.mccal.folio

import android.content.Context

/**
 * Settings › Search & App Library › Clear Suggestions History.
 *
 * Folio only reads Android's usage stats (PACKAGE_USAGE_STATS) and cannot delete them, so "clear" works by a cutoff:
 * the time of the last clear, saved as a plain long in the `launcher` prefs beside the state (no schema change, an
 * older Folio ignores it). Suggestions, the App Library tile, the Suggestions widget and Smart Stack relevance ignore
 * any use at or before the cutoff. Folio's own launch log and recents list are emptied as well.
 */
internal object SuggestionsHistory {
    /** The uses that still count: strictly after [cutoff]. */
    fun <T> keepAfter(uses: List<Pair<T, Long>>, cutoff: Long): List<Pair<T, Long>> =
        if (cutoff <= 0L) uses else uses.filter { it.second > cutoff }

    /** The cutoff after a clear at [now]. Never moves back, so a clock set backward cannot bring old history back. */
    fun nextCutoff(now: Long, current: Long): Long = maxOf(now, current)

    fun cutoff(context: Context): Long =
        context.getSharedPreferences(SettingKeys.PREFS, 0).getLong(SettingKeys.SUGGESTIONS_CLEARED_AT, 0L)

    fun clear(context: Context, now: Long = System.currentTimeMillis()) {
        val prefs = context.getSharedPreferences(SettingKeys.PREFS, 0)
        prefs.edit().putLong(SettingKeys.SUGGESTIONS_CLEARED_AT, nextCutoff(now, prefs.getLong(SettingKeys.SUGGESTIONS_CLEARED_AT, 0L))).apply()
        RecentApps.clear(context)
    }
}
