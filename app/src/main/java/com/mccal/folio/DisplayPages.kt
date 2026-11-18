package com.mccal.folio

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue

/**
 * The Home page each screen was last resting on, so folding or unfolding goes back to where you were on that screen
 * instead of carrying one page across. A screen that has no page yet (the other screen has not been used since Folio
 * started) has none, and Home keeps the page it is on.
 */
internal data class HomePageMemory(val cover: Int = NONE, val inner: Int = NONE) {
    fun page(screen: FolioScreen): Int? = (if (screen == FolioScreen.COVER) cover else inner).takeIf { it >= 0 }

    fun with(screen: FolioScreen, page: Int): HomePageMemory =
        if (screen == FolioScreen.COVER) copy(cover = page) else copy(inner = page)

    companion object {
        const val NONE = -1
        val Saver = listSaver<HomePageMemory, Int>(save = { listOf(it.cover, it.inner) }, restore = { HomePageMemory(it[0], it[1]) })
    }
}

/**
 * Which Home page to show after the screen changed (a fold or an unfold), or null to leave Home where it is.
 *
 * Only a Home page is swapped for the new screen's own: the App Library, the page left of Home and the temporary page a
 * drag adds all sit at or past the Home pages (or before them) and stay put, so a library that was open stays open. A page
 * that no longer exists (Home pages were removed since) is kept inside the pages there are.
 */
internal fun pageAfterScreenChange(settledPage: Int, homePages: Int, remembered: Int?): Int? {
    if (homePages <= 0 || settledPage !in 0 until homePages || remembered == null) return null
    return remembered.coerceIn(0, homePages - 1).takeIf { it != settledPage }
}

/**
 * Where the App Library was left, so a fold or an unfold (which builds the library again for the other layout) shows the
 * same Work or Personal list, the same open category and the same scroll. Cleared when Home leaves the library, so
 * the next visit starts at the top as it always did.
 */
internal class LibraryPlace {
    var work by mutableStateOf(false)
    var category by mutableStateOf<LibraryCategory?>(null)
    var index = 0
    var offset = 0
    /** What the scroll was last reset for (list, search words): the same again after a rebuild keeps the place. */
    var scrollKey: Any? = null

    fun clear() { work = false; category = null; index = 0; offset = 0; scrollKey = null }
}
