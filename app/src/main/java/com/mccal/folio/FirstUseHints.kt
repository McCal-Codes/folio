package com.mccal.folio

import android.content.Context

/**
 * Hints that teach in place, once: the first time Home is up after setup, and the first time the unfolded screen is.
 * Each is shown in Home's island, never twice, and never again once the person has done the thing it teaches (which
 * counts the same as having seen it). Kept in a file of their own so nothing else has to know.
 */
internal enum class FirstUseHint(val key: String) {
    /** Press and hold an icon to rearrange or edit Home. Done by starting to edit or opening an icon's menu. */
    HOLD("hold"),
    /** The unfolded screen is the same Home with more room. */
    UNFOLD("unfold");
}

internal object FirstUseHints {
    private const val ARMED = "armed"
    private fun prefs(context: Context) = context.getSharedPreferences("first_use_hints", Context.MODE_PRIVATE)

    /**
     * Setup just finished: the hints are now worth showing. Only someone who has just been through setup is new, so a
     * phone that updated from an older Folio never sees them.
     */
    fun arm(context: Context) { prefs(context).edit().putBoolean(ARMED, true).apply() }

    fun pending(context: Context, hint: FirstUseHint) = prefs(context).getBoolean(ARMED, false) && !prefs(context).getBoolean(hint.key, false)

    /** The person did the thing, or has seen the hint: it is not shown again. */
    fun done(context: Context, hint: FirstUseHint) { prefs(context).edit().putBoolean(hint.key, true).apply() }

    /** Shows [hint] in the island if it is still pending and the island is there to show it; marks it seen only when it was shown. */
    fun show(context: Context, hint: FirstUseHint) {
        if (!pending(context, hint)) return
        val text = context.getString(when (hint) { FirstUseHint.HOLD -> R.string.hint_hold; FirstUseHint.UNFOLD -> R.string.hint_unfold })
        if (IslandEvents.notice(context, text, toastFallback = false)) done(context, hint)
    }
}
