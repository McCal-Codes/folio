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
    data class Item(val icon: String, val color: Long, val title: String, val detail: String, val status: Status)
    /** [release] is set for a version's own section ("Folio 0.6.1"); otherwise [title] names it ("Next"). */
    data class Section(val title: String?, val release: String?, val items: List<Item>)
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
                Section(title, release, (0 until minOf(items.length(), 30)).mapNotNull { i ->
                    val item = items.getJSONObject(i)
                    val status = runCatching { Status.valueOf(item.getString("status").uppercase(java.util.Locale.ROOT)) }.getOrNull() ?: return@mapNotNull null
                    val itemTitle = item.optString("title").trim().takeIf { it.isNotEmpty() }?.take(60) ?: return@mapNotNull null
                    Item(item.optString("icon").take(24), color(item.optString("color")), itemTitle,
                        item.optString("detail").trim().take(240), status)
                }).takeIf { it.items.isNotEmpty() }
            }).takeIf { it.sections.isNotEmpty() }
    }.getOrNull()

    private fun color(hex: String): Long =
        hex.removePrefix("#").takeIf { it.length == 6 }?.toLongOrNull(16)?.let { 0xFF000000 or it } ?: FolioColors.Value.Gray

    /** The newest copy on the phone: the last good download, else the one shipped with Folio. */
    fun local(context: Context): Content? =
        runCatching { File(context.filesDir, CACHE).takeIf { it.exists() }?.readText()?.let(::parse) }.getOrNull()
            ?: runCatching { context.assets.open(ASSET).bufferedReader().use { it.readText() }.let(::parse) }.getOrNull()

    /** Fetches the latest roadmap if the saved one is old. Returns it, or null when nothing new was saved. Call off the main thread. */
    fun refresh(context: Context, now: Long = System.currentTimeMillis()): Content? {
        val cache = File(context.filesDir, CACHE)
        if (cache.exists() && now - cache.lastModified() < REFRESH_MS) return null
        return runCatching {
            val c = URL(URL).openConnection() as HttpURLConnection
            c.setRequestProperty("User-Agent", "Folio")
            c.connectTimeout = 8_000; c.readTimeout = 10_000; c.useCaches = false
            try {
                if (c.responseCode != 200) return null
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
                if (bytes.size > MAX_BYTES) return null
                val raw = String(bytes, Charsets.UTF_8)
                parse(raw)?.also { cache.writeText(raw) }
            } finally { c.disconnect() }
        }.getOrNull()
    }
}
