package com.mccal.folio

/**
 * The page-level edits behind the Page overview: moving a Home page to a new place, hiding and showing pages, and
 * remapping everything that holds a page number so it follows the page instead of staying on the number.
 *
 * Pages are contiguous blocks of [HOME_CELLS] cells in [HomeLayout.slots], and a page's number is also stored in
 * [WidgetPlacement.page], `pageStyles`, a Focus's `pages` and `homePage`, the hidden set, and the page each screen
 * last rested on. A move is described once as an [order] (`order[new] = old`) and every holder is mapped through it.
 * Plain functions on plain data, so the rules are unit-tested without a phone.
 */
internal object HomePages {
    /** Hiding is shown and stored on the same page numbers as everything else; the last page left showing cannot be hidden. */
    const val MAX_PAGES = 20

    /** `order[newIndex] = oldIndex` after moving the page at [from] to place [to] (the others shift to make room). */
    fun moveOrder(count: Int, from: Int, to: Int): List<Int> {
        val order = (0 until count).toMutableList()
        if (from !in 0 until count || to !in 0 until count || from == to) return order
        order.add(to, order.removeAt(from))
        return order
    }

    /** True when [order] changes nothing. */
    fun isIdentity(order: List<Int>) = order.withIndex().all { (index, old) -> index == old }

    /** Where the page that was number [old] is now. A leading page (-1), or one past the pages there are, keeps its number. */
    fun remapPage(old: Int, order: List<Int>): Int = if (old < 0) old else order.indexOf(old).takeIf { it >= 0 } ?: old

    fun remapPages(pages: Set<Int>, order: List<Int>): Set<Int> = pages.mapTo(linkedSetOf()) { remapPage(it, order) }

    fun <T> remapPageMap(map: Map<Int, T>, order: List<Int>): Map<Int, T> = map.mapKeys { (page, _) -> remapPage(page, order) }

    /** A Focus's page set and opening page, followed to where those pages now are. */
    fun remapFocus(mode: FocusMode, order: List<Int>): FocusMode =
        mode.copy(pages = mode.pages?.let { remapPages(it, order) }, homePage = mode.homePage?.let { remapPage(it, order) })

    /**
     * [layout] with its pages in the new [order]: every page's cells and every widget placed on it move together. The
     * unfolded-only leading page, the dock, folders and widget slot numbers are not page-numbered and stay as they are. An
     * [order] of the wrong length (the layout and the picture of it disagree) changes nothing.
     */
    fun reorder(layout: HomeLayout, order: List<Int>): HomeLayout {
        if (order.size != layout.pageCount || isIdentity(order) || order.sorted() != order.indices.toList()) return layout
        val slots = order.flatMap { old -> List(HOME_CELLS) { local -> layout.slots.getOrNull(homeCellIndex(old, local)) } }
        val placements = layout.widgetPlacements.map { if (it.page >= 0) it.copy(page = remapPage(it.page, order)) else it }
        return layout.copy(slots = slots.dropLastWhile { it == null }, widgetPlacements = placements)
    }

    /** The real pages Home draws, in order: not the ones the person hid, and only the ones a Focus lists. Never empty. */
    fun shownPages(pageCount: Int, hidden: Set<Int>, focusPages: Set<Int>?): List<Int> {
        val all = (0 until pageCount).toList()
        val visible = all.filter { it !in hidden }.ifEmpty { all }
        // A Focus can narrow the pages that are showing but never bring a hidden one back.
        val focused = focusPages?.let { wanted -> visible.filter { it in wanted } }.orEmpty()
        return if (focusPages == null) visible else focused.ifEmpty { listOf(visible.first()) }
    }

    /** The hidden set kept to pages that exist, and never all of them. */
    fun sanitizeHidden(hidden: Set<Int>, pageCount: Int): Set<Int> =
        hidden.filter { it in 0 until pageCount }.toSet().let { if (it.size >= pageCount) emptySet() else it }

    /** [hidden] with [page] hidden or shown, or null when that is not allowed (the last page showing cannot be hidden). */
    fun withHidden(hidden: Set<Int>, pageCount: Int, page: Int, hide: Boolean): Set<Int>? {
        if (page !in 0 until pageCount) return null
        val current = sanitizeHidden(hidden, pageCount)
        if (!hide) return current - page
        if (page in current) return current
        return (current + page).takeIf { it.size < pageCount }
    }

    /**
     * Where a page index that was drawn on screen goes after the pages changed (a move, a hide or a show). [oldShown] and
     * [newShown] are the real pages Home drew before and after, and [order] is the move, if there was one. A page that is
     * no longer showing goes to the nearest one that is.
     */
    fun followShownIndex(index: Int, oldShown: List<Int>, order: List<Int>?, newShown: List<Int>): Int {
        if (newShown.isEmpty()) return 0
        val real = oldShown.getOrNull(index) ?: return index.coerceIn(0, newShown.lastIndex)
        val now = if (order == null) real else remapPage(real, order)
        newShown.indexOf(now).takeIf { it >= 0 }?.let { return it }
        return newShown.indices.minBy { kotlin.math.abs(newShown[it] - now) }
    }

    /** Hidden pages that still exist after the page count shrank (an empty last page removed). */
    fun afterPageCount(hidden: Set<Int>, pageCount: Int): Set<Int> = sanitizeHidden(hidden, pageCount)

    /** The page a TalkBack move left or right ends on, or null at either end. */
    fun stepTarget(page: Int, count: Int, toEnd: Boolean): Int? = (if (toEnd) page + 1 else page - 1).takeIf { it in 0 until count }
}

/** Everything besides the layout that a page move or hide changes, kept together so Undo can put it all back. */
internal data class PageSide(
    val pageStyles: Map<Int, PageStyle>,
    val hiddenPages: Set<Int>,
    val focusModes: List<FocusMode>,
)
