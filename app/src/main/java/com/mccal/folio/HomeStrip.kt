package com.mccal.folio

import androidx.annotation.StringRes

/**
 * What sits above the dock at rest: the Search button (the default, like iOS), the page dots, or nothing. The dots
 * come back for a moment while you swipe, drag the strip or edit Home, whichever is chosen, so no way to reach a page
 * is lost. Before this there was only a switch for the Search button; its old value is still written beside this one,
 * so going back to an older Folio keeps Home as it was.
 */
enum class HomeStrip(val key: String, @StringRes val label: Int) {
    SEARCH("search", R.string.home_strip_search),
    DOTS("dots", R.string.home_strip_dots),
    NOTHING("nothing", R.string.home_strip_nothing);

    companion object {
        /** The saved choice; a state saved before this existed only knew whether the Search button was on. */
        fun read(saved: String?, legacySearchPill: Boolean): HomeStrip =
            entries.firstOrNull { it.key == saved } ?: if (legacySearchPill) SEARCH else DOTS
    }
}

/** Whether the Search button is what sits above the dock (it also decides if the rail shows its own search control). */
val LauncherState.searchPill get() = homeStrip == HomeStrip.SEARCH

/** What the strip draws this moment. */
internal enum class StripView { PILL, DOTS, EMPTY }

/**
 * [idle] is true when Home is at rest on a page: not editing, dragging, scrubbing or mid swipe. Away from rest the dots
 * show. A phone that chose Nothing but can't use it (a supporter code that ran out while the option was still in
 * beta) shows the dots rather than a strip with no way to tell where you are.
 */
internal fun stripView(strip: HomeStrip, idle: Boolean, nothingOpen: Boolean): StripView {
    val chosen = if (strip == HomeStrip.NOTHING && !nothingOpen) HomeStrip.DOTS else strip
    return when {
        !idle -> StripView.DOTS
        chosen == HomeStrip.SEARCH -> StripView.PILL
        chosen == HomeStrip.NOTHING -> StripView.EMPTY
        else -> StripView.DOTS
    }
}
