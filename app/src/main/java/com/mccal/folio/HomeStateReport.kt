package com.mccal.folio

/**
 * Where Home was when a report was made: which screen, which page, how many pages, which are hidden, what each screen
 * last rested on, whether a Focus is limiting the pages, and the profile. Reports about folding, the Page overview and
 * Focus depend on these and nothing else in a report says them. Counts and numbers only: no app names, no page names.
 *
 * Page numbers are what the person sees (counting from 1); [hidden] are real pages, which hidden ones are numbered among.
 */
internal data class HomeStateInfo(
    val screen: FolioScreen,
    /** The page the pager is on: -1 left of Home, 0 until [shownPages] a Home page, [shownPages] the App Library. */
    val position: Int,
    val shownPages: Int,
    val totalPages: Int,
    val hidden: Set<Int>,
    /** The page each screen last rested on, as a number among the pages showing; null before that screen was used. */
    val coverRest: Int?,
    val innerRest: Int?,
    val focusOn: Boolean,
    val focusLimitsPages: Boolean,
    val profile: String,
)

internal object HomeStateReport {
    fun showing(info: HomeStateInfo): String = when {
        info.position < 0 -> "the page left of Home"
        info.position < info.shownPages -> "Home page ${info.position + 1} of ${info.shownPages}"
        else -> "the App Library"
    }

    private fun pages(info: HomeStateInfo): String =
        "${info.totalPages}" + if (info.hidden.isEmpty()) "" else ", ${info.hidden.size} hidden (${info.hidden.sorted().joinToString { "${it + 1}" }})"

    private fun rested(info: HomeStateInfo): String =
        "cover ${info.coverRest?.let { "page ${it + 1}" } ?: "not used yet"}, inner ${info.innerRest?.let { "page ${it + 1}" } ?: "not used yet"}"

    private fun focus(info: HomeStateInfo): String = when {
        !info.focusOn -> "off"
        info.focusLimitsPages -> "on, limiting Home to ${info.shownPages} of ${info.totalPages} pages"
        else -> "on, every page showing"
    }

    /** The section in a full report. */
    fun text(info: HomeStateInfo?): String = if (info == null) "Home:\n  (not read yet)" else buildString {
        appendLine("Home:")
        appendLine("  Screen: ${info.screen.name.lowercase()}, showing ${showing(info)}")
        appendLine("  Pages: ${pages(info)}")
        appendLine("  Each screen last rested on: ${rested(info)}")
        appendLine("  Focus: ${focus(info)}")
        append("  Profile: ${info.profile}")
    }

    /** The same facts on one line, for the short summary that goes in a link. */
    fun line(info: HomeStateInfo?): String = if (info == null) "Home: not read yet" else
        "Home: ${info.screen.name.lowercase()}, ${showing(info)}; pages ${pages(info)}; rested ${rested(info)}; Focus ${focus(info)}; profile ${info.profile}"

    /** One installed Market package, as much as a report needs: no description, no author, no source address. */
    data class PackageLine(val id: String, val version: String, val enabled: Boolean, val origin: String)

    /** The packages Folio has installed, so a report can say what is changing Home. Capped, since a long list is no more use. */
    fun packages(list: List<PackageLine>, limit: Int = 30): String = buildString {
        append("Market packages:")
        if (list.isEmpty()) { append(" none"); return@buildString }
        list.sortedBy { it.id }.take(limit).forEach { append("\n  ${it.id} ${it.version} (${if (it.enabled) "on" else "off"}, ${it.origin})") }
        if (list.size > limit) append("\n  and ${list.size - limit} more")
    }
}
