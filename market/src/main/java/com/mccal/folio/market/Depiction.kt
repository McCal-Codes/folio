package com.mccal.folio.market

import org.json.JSONObject

/**
 * A package page (`depiction.json`, format v1): a list of blocks Folio draws with its own components.
 * Blocks from newer format versions are skipped, so an older Folio still shows the rest of the page.
 */
data class Depiction(val tint: Int?, val blocks: List<DepictionBlock>) {
    companion object {
        const val MAX_CHARS = 256 * 1024
        const val MAX_BLOCKS = 40
        const val MAX_MARKDOWN = 4000
        const val MAX_SCREENSHOTS = 10
        const val MAX_FEATURES = 12
        const val MAX_CHANGELOG = 50
        private val TINT = Regex("^#[0-9A-Fa-f]{6}\\z")
        private val DATE = Regex("^[0-9]{4}-[0-9]{2}-[0-9]{2}\\z")

        fun parse(text: String): ParseResult<Depiction> {
            val p = Problems()
            val json = parseStrictObject(text, MAX_CHARS, p) ?: return p.result { error("unreachable") }
            val f = Fields(json, "", p, setOf("\$schema", "format", "tint", "blocks"))
            f.anyString("\$schema")
            f.formatOne()
            val tint = f.string("tint", false, TINT, 7, "must be a color like #0A84FF")?.let { 0xFF000000.toInt() or it.substring(1).toInt(16) }
            val blocks = f.objects("blocks", true, maxItems = MAX_BLOCKS)?.mapNotNull { (index, block) -> readBlock(f, index, block) }
            return p.result { Depiction(tint, blocks!!) }
        }

        private fun readBlock(parent: Fields, index: Int, json: JSONObject): DepictionBlock? {
            val at = "${parent.where("blocks")}[$index]"
            val p = parent.problems
            val type = json.opt("type")
            if (type !is String) { p.errors += "$at.type is required and must be a string"; return null }
            fun fields(vararg keys: String) = parent.child(json, at, setOf("type", *keys))
            return when (type) {
                "hero" -> fields("image").string("image", true, SAFE_PATH, MAX_PATH, PATH_RULE)?.let(DepictionBlock::Hero)
                "screenshots" -> fields("images").strings("images", true, 1, MAX_SCREENSHOTS) { value, itemAt ->
                    parent.checkString(value, itemAt, SAFE_PATH, MAX_PATH, PATH_RULE)
                }?.let(DepictionBlock::Screenshots)
                "markdown" -> fields("text").text("text", true, MAX_MARKDOWN)?.let(DepictionBlock::Markdown)
                "featureList" -> readFeatures(fields("items"))?.let(DepictionBlock::FeatureList)
                "compatibility" -> {
                    val c = fields("screens", "notes", "testedOn", "fallback")
                    val screens = c.ids("screens", false, Screen::from, unique = false, unknownIsIgnored = true)
                    val notes = c.text("notes", false)
                    val testedOn = readTestedOn(c)
                    val fallback = c.text("fallback", false)
                    if (screens == null && c.has("screens")) null
                    else DepictionBlock.Compatibility(screens?.toSet() ?: Screen.entries.toSet(), notes, testedOn.orEmpty(), fallback)
                }
                "changelog" -> readChangelog(fields("entries"))?.let(DepictionBlock::Changelog)
                "link" -> {
                    val l = fields("title", "url")
                    val title = l.text("title", true)
                    val url = l.string("url", true, HTTPS_URL, MAX_URL, URL_RULE)
                    if (title != null && url != null) DepictionBlock.Link(title, url) else null
                }
                "donation" -> fields("url").string("url", true, HTTPS_URL, MAX_URL, URL_RULE)?.let(DepictionBlock::Donation)
                else -> null.also { p.ignored += "$at (${type.take(40)} block)" }
            }
        }

        private fun readFeatures(f: Fields): List<LocalizedText>? {
            val items = f.array("items", true, 1, MAX_FEATURES) ?: return null
            var ok = true
            val out = items.mapIndexedNotNull { i, item ->
                LocalizedText.read(item, MAX_TEXT) { f.problems.errors += "${f.where("items")}[$i] $it"; ok = false }
            }
            return if (ok) out else null
        }

        private fun readChangelog(f: Fields): List<ChangelogEntry>? {
            val items = f.objects("entries", true, maxItems = MAX_CHANGELOG) ?: return null
            val before = f.problems.errors.size
            val out = items.mapNotNull { (i, json) ->
                val e = f.child(json, "${f.where("entries")}[$i]", setOf("version", "date", "notes"))
                val version = PackageManifest.readVersion(e, "version")
                val date = e.string("date", false, DATE, 10, "must be a date like 2026-09-17")
                val notes = e.text("notes", true)
                if (version != null && notes != null) ChangelogEntry(version, date, notes) else null
            }
            return if (f.problems.errors.size == before) out else null
        }

        /** `testedOn` (ADR 0013): where the author tried it. Folio checks the shape, not the claim. */
        private fun readTestedOn(f: Fields): List<TestedOn>? {
            val items = f.objects("testedOn", false, maxItems = MAX_TESTED_ON) ?: return null
            return items.mapNotNull { (i, json) ->
                val t = f.child(json, "${f.where("testedOn")}[$i]", setOf("device", "android", "folio", "date"))
                val device = t.text("device", true, MAX_DEVICE)
                val android = t.long("android", true, PackageManifest.MIN_SDK.toLong()..PackageManifest.MAX_SDK.toLong())?.toInt()
                val folio = t.string("folio", true, maxLength = 40)?.let { raw ->
                    FolioVersion.parse(raw) ?: null.also { f.problems.errors += "${t.where("folio")} must look like 0.7.0" }
                }
                val date = t.string("date", true, DATE, 10, "must be a date like 2026-10-03")
                if (device != null && android != null && folio != null && date != null) TestedOn(device, android, folio, date) else null
            }
        }

        const val MAX_TESTED_ON = 10
        const val MAX_DEVICE = 60
        private const val PATH_RULE = "must be a relative path inside the package"
        private const val URL_RULE = "must be an https:// link"
    }
}

sealed interface DepictionBlock {
    data class Hero(val image: String) : DepictionBlock
    data class Screenshots(val images: List<String>) : DepictionBlock

    /** Folio's safe Markdown subset: paragraphs, bold, italic, lists and links. HTML and images are shown as text. */
    data class Markdown(val text: LocalizedText) : DepictionBlock
    data class FeatureList(val items: List<LocalizedText>) : DepictionBlock
    /**
     * [testedOn] is where the author tried it, and [fallback] what still works without the access it needs
     * (ADR 0013). Both are words for the page; nothing is decided from them.
     */
    data class Compatibility(
        val screens: Set<Screen>,
        val notes: LocalizedText?,
        val testedOn: List<TestedOn> = emptyList(),
        val fallback: LocalizedText? = null,
    ) : DepictionBlock
    data class Changelog(val entries: List<ChangelogEntry>) : DepictionBlock
    data class Link(val title: LocalizedText, val url: String) : DepictionBlock
    data class Donation(val url: String) : DepictionBlock
}

/** One phone the author tried a package on. [android] is the API level; [date] is `yyyy-mm-dd`. */
data class TestedOn(val device: LocalizedText, val android: Int, val folio: FolioVersion, val date: String)

/** One release in a package's changelog. [date] is `yyyy-mm-dd` as the author wrote it. */
data class ChangelogEntry(val version: DebVersion, val date: String?, val notes: LocalizedText)
