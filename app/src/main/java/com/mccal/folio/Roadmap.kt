package com.mccal.folio

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

/**
 * The Roadmap in Settings, kept in one file in Folio's repository (app/src/main/assets/roadmap.json). A copy ships in
 * the app; opening the Roadmap fetches the latest from GitHub at most every few hours. Only a plain request for that
 * file is made, and a bad or oversized file is ignored in favor of the last good one.
 */
internal object Roadmap {
    const val URL = "https://raw.githubusercontent.com/McCal-Codes/folio/main/app/src/main/assets/roadmap.json"
    private const val ASSET = "roadmap.json"
    private const val CACHE = "roadmap.json"
    private const val MAX_BYTES = 64 * 1024
    private const val REFRESH_MS = 6 * 60 * 60 * 1000L

    enum class Status { DONE, BUILDING, PLANNED, EXPLORING }
    /** [beta] marks a [Status.BUILDING] item that is already in the current beta: "In beta" on the page, never "Done" before the stable. */
    data class Item(val icon: String, val color: Long, val title: String, val detail: String, val status: Status, val beta: Boolean = false)
    /**
     * [release] is set for a version's own section ("Folio 0.6.1"); otherwise [title] names it ("Next"). [subtitle] is an
     * optional theme for a release ("Foundation"); a Folio that predates it ignores the field.
     */
    data class Section(val title: String?, val release: String?, val items: List<Item>, val subtitle: String? = null)
    data class Content(val note: String?, val sections: List<Section>)

    /** Reads a roadmap file; null if it isn't a valid one. Unknown statuses and bad colors are skipped or defaulted. */
    fun parse(raw: String): Content? = runCatching {
        if (raw.length > MAX_BYTES) return null
        val json = JSONObject(raw)
        if (json.optInt("roadmap", 0) != 1) return null
        val sections = json.getJSONArray("sections")
        Content(json.optString("note").takeIf { it.isNotBlank() }?.take(300),
            (0 until minOf(sections.length(), 12)).mapNotNull { index ->
                val section = sections.getJSONObject(index)
                val release = section.optString("release").takeIf { Regex("""\d+\.\d+\.\d+(-[\w.]+)?""").matches(it) }
                val title = section.optString("title").takeIf { it.isNotBlank() }?.take(40)
                if (release == null && title == null) return@mapNotNull null
                val items = section.getJSONArray("items")
                val subtitle = section.optString("subtitle").trim().takeIf { it.isNotEmpty() }?.take(40)
                Section(title, release, (0 until minOf(items.length(), 30)).mapNotNull { i ->
                    val item = items.getJSONObject(i)
                    val status = runCatching { Status.valueOf(item.getString("status").uppercase(java.util.Locale.ROOT)) }.getOrNull() ?: return@mapNotNull null
                    val itemTitle = item.optString("title").trim().takeIf { it.isNotEmpty() }?.take(60) ?: return@mapNotNull null
                    Item(item.optString("icon").take(24), color(item.optString("color")), itemTitle,
                        item.optString("detail").trim().take(240), status, item.optBoolean("beta", false))
                }, subtitle).takeIf { it.items.isNotEmpty() }
            }).takeIf { it.sections.isNotEmpty() }
    }.getOrNull()

    private fun color(hex: String): Long =
        hex.removePrefix("#").takeIf { it.length == 6 }?.toLongOrNull(16)?.let { 0xFF000000 or it } ?: FolioColors.Value.Gray

    /** The newest copy on the phone: the last good download, else the one shipped with Folio. */
    fun local(context: Context): Content? =
        runCatching { File(context.filesDir, CACHE).takeIf { it.exists() }?.readText()?.let(::parse) }.getOrNull()
            ?: runCatching { context.assets.open(ASSET).bufferedReader().use { it.readText() }.let(::parse) }.getOrNull()

    /** What opening the Roadmap found out: the saved copy is recent, a newer one was saved, or GitHub couldn't be read. */
    sealed interface Refresh {
        data object Recent : Refresh
        data class Updated(val content: Content) : Refresh
        data object Failed : Refresh
    }

    /** When the last good download was saved, or null while the copy shipped with Folio is the only one. */
    fun savedAt(context: Context): Long? = File(context.filesDir, CACHE).takeIf { it.exists() }?.lastModified()

    /** True when the saved copy is old enough that opening the Roadmap checks GitHub. */
    fun isStale(context: Context, now: Long = System.currentTimeMillis()): Boolean {
        val saved = savedAt(context)
        return saved == null || now - saved >= REFRESH_MS
    }

    /** Fetches the latest roadmap if the saved one is old. Call off the main thread. */
    fun refresh(context: Context, now: Long = System.currentTimeMillis()): Refresh {
        val cache = File(context.filesDir, CACHE)
        if (!isStale(context, now)) return Refresh.Recent
        return runCatching {
            val c = URL(URL).openConnection() as HttpURLConnection
            c.setRequestProperty("User-Agent", "Folio")
            c.connectTimeout = 8_000; c.readTimeout = 10_000; c.useCaches = false
            try {
                if (c.responseCode != 200) return Refresh.Failed
                // Read at most one byte past the limit (readNBytes needs Android 13).
                val bytes = c.inputStream.use { input ->
                    val out = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (out.size() <= MAX_BYTES) {
                        val n = input.read(buffer, 0, minOf(buffer.size, MAX_BYTES + 1 - out.size()))
                        if (n < 0) break
                        out.write(buffer, 0, n)
                    }
                    out.toByteArray()
                }
                if (bytes.size > MAX_BYTES) return Refresh.Failed
                val raw = String(bytes, Charsets.UTF_8)
                parse(raw)?.let { Refresh.Updated(it).also { _ -> cache.writeText(raw) } } ?: Refresh.Failed
            } finally { c.disconnect() }
        }.getOrDefault(Refresh.Failed)
    }

    /** The page's groups: what is in progress, what comes after, what is already out, and the titled lists. */
    data class Groups(val now: Section?, val next: List<Section>, val shipped: List<Section>, val titled: List<Section>)

    /**
     * Sorts a roadmap by the version that is installed, so the file needs no "current" marker. [now] is the installed
     * release while it still has open items, else the first release after it; older releases are [shipped], the rest
     * of the newer ones are [next], and sections without a version are [titled] ("Later", "Exploring"). A hotfix
     * (0.6.7.1) and a beta (0.6.8-beta.5) belong to their release's section. [isNewer] is SoftwareUpdate's comparison.
     */
    fun group(content: Content, installed: String, isNewer: (candidate: String, installed: String) -> Boolean): Groups {
        val thisRelease = installed.substringBefore('-').split('.').take(3).joinToString(".")
        val releases = content.sections.filter { it.release != null }
        val own = releases.firstOrNull { it.release == thisRelease }
        val newer = releases.filter { it !== own && isNewer(it.release!!, installed) }
        val older = releases.filter { it !== own && it !in newer }
        val ownIsOpen = own != null && own.items.any { it.status != Status.DONE }
        val now = if (ownIsOpen) own else newer.firstOrNull()
        val shipped = (older + listOfNotNull(own.takeIf { !ownIsOpen })).sortedWith { a, b ->
            when { isNewer(a.release!!, b.release!!) -> -1; isNewer(b.release!!, a.release!!) -> 1; else -> 0 }
        }
        return Groups(now, newer.filter { it !== now }, shipped, content.sections.filter { it.release == null })
    }

    /** Items ready to try (done, or in the current beta) out of all of them. */
    fun ready(section: Section): Pair<Int, Int> =
        section.items.count { it.status == Status.DONE || it.beta } to section.items.size
}
