package com.mccal.folio

/**
 * Where a typed query can go. Google uses the "Web" filter (udm=14), which omits AI Overviews. [ai] marks the ones that
 * answer rather than list, which get their own icon; [chip] is whether a chip for it is always offered under a search
 * (the rest are only for Search with Enter, and show a chip while one of them is the one you chose).
 */
// The labels are brand names, so they stay English in every language (each line is marked english-only).
internal enum class WebSearchTarget(val label: String, private val prefix: String, val ai: Boolean = false, val chip: Boolean = true) {
    GOOGLE("Google", "https://www.google.com/search?udm=14&q="), // english-only
    DUCKDUCKGO("DuckDuckGo", "https://noai.duckduckgo.com/?q="), // english-only
    BING("Bing", "https://www.bing.com/search?q=", chip = false), // english-only
    BRAVE("Brave Search", "https://search.brave.com/search?q=", chip = false), // english-only
    ECOSIA("Ecosia", "https://www.ecosia.org/search?q=", chip = false), // english-only
    STARTPAGE("Startpage", "https://www.startpage.com/do/search?q=", chip = false), // english-only
    QWANT("Qwant", "https://www.qwant.com/?q=", chip = false), // english-only
    KAGI("Kagi", "https://kagi.com/search?q=", chip = false), // english-only
    CHATGPT("Ask ChatGPT", "https://chatgpt.com/?q=", ai = true), // english-only
    CLAUDE("Ask Claude", "https://claude.ai/new?q=", ai = true), // english-only
    PERPLEXITY("Perplexity", "https://www.perplexity.ai/search?q=", ai = true); // english-only

    /** The address for [query]. A string, so it can be checked without Android. */
    fun url(query: String): String = prefix + encodeSearch(query)

    fun uri(query: String): android.net.Uri = android.net.Uri.parse(url(query))

    companion object {
        /** The engines Search with Enter can use: every search engine, not the AI ones. */
        val enterEngines: List<WebSearchTarget> = entries.filter { !it.ai }

        /** The chips under a search: the usual ones, plus [engine] if it is one that is otherwise only for Enter. */
        fun chips(engine: String): List<WebSearchTarget> = entries.filter { it.chip || it.name == engine }
    }
}

/** What Search with Enter picks when the setting is [WebSearchTarget.name] or [CUSTOM_SEARCH]. */
internal const val CUSTOM_SEARCH = "CUSTOM"

/**
 * Your own search engine: [template] is a web address with %s (or {query}) where the search goes, like
 * https://example.com/search?q=%s. Only http and https, a real host and no sign-in in the address are accepted; anything
 * else is null, so a typo can never send a search somewhere Folio did not mean. The search is encoded the way a
 * browser would, so &, # and spaces cannot change the address.
 */
internal fun customSearchUrl(template: String, query: String): String? {
    val raw = template.trim()
    if (raw.isEmpty() || raw.length > 500) return null
    if (listOf("%s", "{query}").none { it in raw }) return null
    val encoded = encodeSearch(query)
    val filled = raw.replace("%s", encoded).replace("{query}", encoded)
    val uri = runCatching { java.net.URI(filled) }.getOrNull() ?: return null
    if (uri.scheme?.lowercase() !in setOf("http", "https") || uri.host.isNullOrBlank() || uri.userInfo != null) return null
    return filled
}

/** Whether [template] would work, for telling you while you type it. */
internal fun customSearchTemplateIsValid(template: String): Boolean = customSearchUrl(template, "test") != null

/** The address Search with Enter opens for [query]: your own engine if you chose it and it works, else the preset, else Google. */
internal fun enterSearchUrl(engine: String, customTemplate: String, query: String): String {
    if (engine == CUSTOM_SEARCH) customSearchUrl(customTemplate, query)?.let { return it }
    val target = runCatching { WebSearchTarget.valueOf(engine) }.getOrDefault(WebSearchTarget.GOOGLE)
    return target.url(query)
}

/**
 * [query], trimmed and percent-encoded the way android.net.Uri.encode does it (letters, digits and _-!.~'()* stay as
 * they are), so the same search makes the same address whether it is checked here or opened on the phone.
 */
internal fun encodeSearch(query: String): String = buildString {
    for (byte in query.trim().toByteArray(Charsets.UTF_8)) {
        val c = (byte.toInt() and 0xFF).toChar()
        if (c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c in "_-!.~'()*") append(c)
        else append('%').append("0123456789ABCDEF"[(byte.toInt() shr 4) and 0xF]).append("0123456789ABCDEF"[byte.toInt() and 0xF])
    }
}

internal fun openWebSearch(context: android.content.Context, target: WebSearchTarget, query: String) =
    openSearchUri(context, target.uri(query))

/** Search with Enter, with whichever engine is set. */
internal fun openEnterSearch(context: android.content.Context, state: LauncherState, query: String) =
    openSearchUri(context, android.net.Uri.parse(enterSearchUrl(state.searchEngine, state.searchCustomUrl, query)))

private fun openSearchUri(context: android.content.Context, uri: android.net.Uri) {
    // A plain https link: the matching app opens it if installed and verified, otherwise the browser.
    runCatching {
        context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, uri)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
