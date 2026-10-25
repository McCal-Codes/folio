package com.mccal.folio.market

import org.json.JSONArray
import org.json.JSONObject

/** A source Folio knows about. The built-in one always exists; the rest are ones the user chose to add. */
data class Source(
    val url: String,
    /** The name from the source's own index, once Folio has read it. */
    val name: String? = null,
    val kind: Kind = Kind.ADDED,
    val addedAt: Long = 0,
) {
    enum class Kind(val id: String) {
        /** Folio's own packages, inside the app: no network, no signature to check. */
        BUILT_IN("built-in"),

        /** A source the user added, signed, with its key pinned the first time. */
        ADDED("added"),

        /**
         * Folio's supporter source, added by redeeming a code rather than by hand.
         *
         * Its key ships in the app, so adding it is not trust-on-first-use: there is no fingerprint to confirm
         * because Folio already knows which key is right, and a source that answers with another one fails the
         * signature like any other. That matters, because a source that appears without being asked for is
         * exactly where showing a fingerprint would be skipped.
         */
        SUPPORTER("supporter"),

        /**
         * Folio Community Tweaks (folio-tweaks), Folio's own tweaks source. Its key ships in the app like the
         * supporter source's, so it is never trusted on first use, and it can be removed like any source. A reader
         * that doesn't know this kind treats it as an added source, which is only less trusted.
         */
        FIRST_PARTY("first-party"),

        /**
         * A source served from the phone itself while someone builds a package (`folio-pkg serve` plus
         * `adb reverse`). Unsigned, http, and only in Folio Dev.
         */
        LOCAL_DEV("local-dev"),
        ;

        companion object {
            fun from(id: String?) = entries.firstOrNull { it.id == id } ?: ADDED
        }
    }

    val label: String get() = name ?: url.removePrefix("https://").removePrefix("http://").trimEnd('/')
}

/** The sources Folio has, in the order they were added. The built-in one is always first and can't be removed. */
class SourceList(private val store: KeyValueStore) {
    fun added(): List<Source> {
        val text = store.get(KEY) ?: return emptyList()
        val array = runCatching { JSONArray(text) }.getOrNull() ?: return emptyList()
        return (0 until array.length()).mapNotNull { i ->
            val json = array.optJSONObject(i) ?: return@mapNotNull null
            val url = json.optString("url").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            Source(
                url = normalizeSourceUrl(url),
                name = json.optString("name").takeIf { it.isNotEmpty() },
                kind = Source.Kind.from(json.optString("kind")),
                addedAt = json.optLong("addedAt"),
            )
        }
    }

    /** Adds a source, or updates the name of one that's already there. Refuses anything that isn't https. */
    fun add(source: Source): Boolean {
        val url = normalizeSourceUrl(source.url)
        if (!url.startsWith("https://") && source.kind != Source.Kind.LOCAL_DEV) return false
        val existing = added().filterNot { it.url == url }
        write(existing + source.copy(url = url))
        return true
    }

    fun remove(url: String) = write(added().filterNot { it.url == normalizeSourceUrl(url) })

    /** Records the name a source gave for itself, so the list reads well before it's opened. */
    fun rename(url: String, name: String?) {
        val target = normalizeSourceUrl(url)
        write(added().map { if (it.url == target) it.copy(name = name) else it })
    }

    private fun write(sources: List<Source>) {
        val array = JSONArray()
        for (source in sources) {
            array.put(
                JSONObject().put("url", source.url).put("name", source.name)
                    .put("kind", source.kind.id).put("addedAt", source.addedAt),
            )
        }
        store.set(KEY, array.toString())
    }

    private companion object {
        const val KEY = "market:sources"
    }
}
