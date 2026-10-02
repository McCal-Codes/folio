package com.mccal.folio.market

/**
 * The three files a source publishes: the signed `entry.json`, the `index.json` it pins, and the signed `revoked.json`.
 * See docs/sdk/format-v1.md; the schemas are `entry.schema.json`, `index.schema.json` and `revoked.schema.json`.
 */
data class SourceEntry(val keyId: String, val timestamp: Long, val maxAge: Long, val index: FileRef) {
    /** The moment this entry stops being fresh; after it, Folio shows "Couldn't refresh" and keeps the cached index. */
    val staleAfter: Long get() = timestamp + maxAge

    companion object {
        const val MAX_CHARS = 4 * 1024
        const val MAX_INDEX_BYTES = 8 * 1024 * 1024
        val KEY_ID = Regex("^[0-9A-F]{16}\\z")
        private val TIMESTAMPS = 0L..4102444800L // up to 2100-01-01, matching the schema

        fun parse(text: String): ParseResult<SourceEntry> {
            val p = Problems()
            val json = parseStrictObject(text, MAX_CHARS, p) ?: return p.result { error("unreachable") }
            val f = Fields(json, "", p, setOf("\$schema", "format", "keyId", "timestamp", "maxAge", "index"))
            f.anyString("\$schema")
            f.formatOne()
            val keyId = f.string("keyId", true, KEY_ID, 16, "must be 16 uppercase hex characters")
            val timestamp = f.long("timestamp", true, TIMESTAMPS)
            // At least an hour, at most 30 days: a source can't freeze its index for longer than that.
            val maxAge = f.long("maxAge", true, 3600L..2592000L)
            val index = f.obj("index", true, setOf("path", "sha256", "size"))?.let(FileRef::read)
            return p.result { SourceEntry(keyId!!, timestamp!!, maxAge!!, index!!) }
        }
    }
}

/** A file pinned by path, hash and size, so a swapped file is refused before it's parsed. */
data class FileRef(val path: String, val sha256: String, val size: Int) {
    companion object {
        val SHA256 = Regex("^[0-9a-f]{64}\\z")

        internal fun read(f: Fields): FileRef? {
            val path = f.string("path", true, SAFE_PATH, MAX_PATH, "must be a relative path inside the source")
            val sha256 = f.string("sha256", true, SHA256, 64, "must be 64 lowercase hex characters")
            val size = f.long("size", true, 1L..SourceEntry.MAX_INDEX_BYTES.toLong())
            return if (path != null && sha256 != null && size != null) FileRef(path, sha256, size.toInt()) else null
        }
    }
}

/** A source's package list. Duplicated ids are kept as they come: a source may offer several versions of a package. */
data class RepoIndex(
    val name: LocalizedText,
    val description: LocalizedText?,
    val icon: String?,
    val issuesUrl: String?,
    val featured: List<FeaturedItem>,
    val packages: List<IndexPackage>,
) {
    companion object {
        const val MAX_CHARS = 8 * 1024 * 1024
        const val MAX_PACKAGES = 5000
        const val MAX_FEATURED = 12

        fun parse(text: String): ParseResult<RepoIndex> {
            val p = Problems()
            val json = parseStrictObject(text, MAX_CHARS, p) ?: return p.result { error("unreachable") }
            val f = Fields(json, "", p, setOf("\$schema", "format", "name", "description", "icon", "issuesUrl", "featured", "packages"))
            f.anyString("\$schema")
            f.formatOne()
            val name = f.text("name", true)
            val description = f.text("description", false)
            val icon = f.string("icon", false, SAFE_PATH, MAX_PATH, "must be a relative path inside the source")
            val issuesUrl = f.string("issuesUrl", false, HTTPS_URL, MAX_URL, "must be an https:// link")
            val featured = f.objects("featured", false, maxItems = MAX_FEATURED)?.map { (i, item) ->
                val e = f.child(item, "${f.where("featured")}[$i]", setOf("package", "label", "image"))
                FeaturedItem(
                    packageId = e.string("package", true, PackageManifest.ID, PackageManifest.MAX_ID, "must be a package id"),
                    label = e.text("label", false),
                    image = e.string("image", false, SAFE_PATH, MAX_PATH, "must be a relative path inside the source"),
                )
            }
            val packages = f.objects("packages", true, maxItems = MAX_PACKAGES)?.mapNotNull { (i, item) ->
                IndexPackage.read(f, i, item)
            }
            return p.result {
                RepoIndex(name!!, description, icon, issuesUrl, featured.orEmpty().map { it.copy(packageId = it.packageId!!) }, packages!!)
            }
        }
    }
}

/** A banner on Featured. [packageId] is null only while a broken entry is being reported. */
data class FeaturedItem(val packageId: String?, val label: LocalizedText?, val image: String?)

/** Where a package was built from, so its page can show "Built from repo @ commit". */
data class Provenance(val repo: String, val commit: String, val workflow: String?)

/**
 * One package in an index: the copy of its manifest, plus where to download it.
 *
 * [manifest] is null when this Folio can't use the package ([needs] says why, for "Needs a newer Folio"); the entry is
 * still listed so the store can show it instead of silently hiding it.
 */
data class IndexPackage(
    val id: String,
    val version: DebVersion,
    val url: String?,
    val sha256: String?,
    val size: Int?,
    val provenance: Provenance?,
    val manifest: PackageManifest?,
    val needs: List<String> = emptyList(),
    /** The author's own signature over this package's id, version and bytes, when they published one. */
    val signedBy: AuthorSignature? = null,
) {
    /** True when Folio has everything it needs to download and install this package. */
    val installable: Boolean get() = manifest != null && url != null && sha256 != null && size != null

    companion object {
        const val MAX_PACKAGE_BYTES = 20 * 1024 * 1024

        /** Why a listed package can't be used here: it names something this Folio doesn't have. */
        const val NEEDS_NEWER_FOLIO = "a newer Folio"
        private val RELATIVE_OR_HTTPS = Regex("^(https://\\S+|(?!/)(?!.*\\.\\.)[A-Za-z0-9._/-]+)\\z")
        private val REPO = Regex("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+\\z")
        private val COMMIT = Regex("^[0-9a-f]{7,40}\\z")

        internal fun read(parent: Fields, index: Int, json: org.json.JSONObject): IndexPackage? {
            val at = "${parent.where("packages")}[$index]"
            val f = parent.child(json, at, setOf("id", "version", "url", "sha256", "size", "provenance", "manifest", "signedBy"))
            val p = parent.problems
            val id = f.string("id", true, PackageManifest.ID, PackageManifest.MAX_ID, "must be a package id")
            val version = PackageManifest.readVersion(f, "version")
            val url = f.string("url", false, RELATIVE_OR_HTTPS, MAX_URL, "must be an https:// link or a relative path")
            val sha256 = f.string("sha256", false, FileRef.SHA256, 64, "must be 64 lowercase hex characters")
            val size = f.long("size", false, 1L..MAX_PACKAGE_BYTES.toLong())
            if (f.has("url") != f.has("sha256") || f.has("url") != f.has("size")) {
                p.errors += "$at needs url, sha256 and size together, or none of them"
            }
            val provenance = f.obj("provenance", false, setOf("repo", "commit", "workflow"))?.let { pr ->
                val repo = pr.string("repo", true, REPO, 200, "must look like owner/repo")
                val commit = pr.string("commit", true, COMMIT, 40, "must be a commit hash")
                val workflow = pr.string("workflow", false, maxLength = 200)
                if (repo != null && commit != null) Provenance(repo, commit, workflow) else null
            }
            // The copy of the manifest is parsed with the same reader as the real file inside the package.
            val embedded = if (!f.has("manifest")) null.also { p.errors += "$at.manifest is required" } else {
                when (val parsed = PackageManifest.parse(json.optJSONObject("manifest")?.toString() ?: "")) {
                    is ParseResult.Ok -> {
                        parsed.ignored.forEach { p.ignored += "$at.manifest.$it" }
                        parsed.value
                    }
                    is ParseResult.Unsupported -> null.also { p.ignored += "$at needs a newer Folio (${parsed.needs.joinToString()})" }
                    is ParseResult.Invalid -> null.also { parsed.errors.take(3).forEach { e -> p.errors += "$at.manifest: $e" } }
                }
            }
            if (id != null && version != null && embedded != null) {
                if (embedded.id != id) p.errors += "$at.id doesn't match the manifest's id"
                if (embedded.version != version) p.errors += "$at.version doesn't match the manifest's version"
            }
            if (id == null || version == null) return null
            val needs = when {
                embedded == null && f.has("manifest") -> listOf(NEEDS_NEWER_FOLIO)
                // A kind this Folio reads but won't install: the listing says so instead of offering Get.
                embedded != null && embedded.kinds.any { it.reserved } -> embedded.kinds.filter { it.reserved }.map { it.id }
                else -> emptyList()
            }
            val signedBy = AuthorSignature.read(f, at)
            if (signedBy != null && sha256 == null) {
                p.errors += "$at.signedBy needs the package's sha256 to sign over"
            }
            return IndexPackage(id, version, url, sha256, size?.toInt(), provenance, embedded, needs, signedBy)
        }
    }
}

/** A signed list of packages (and sources) to turn off, with the reason to show the user. */
data class RevocationList(val timestamp: Long, val packages: List<RevokedPackage>, val sources: List<RevokedSource>) {
    /** The reason [id] at [version] is revoked, or null when it isn't. `"*"` in the list means every version. */
    fun reasonFor(id: String, version: DebVersion): String? = packages.firstOrNull { entry ->
        entry.id == id && entry.versions.any { it == null || DebVersion.parse(it)?.equals(version) == true }
    }?.reason

    /**
     * Why a source was pulled. The address is compared with and without its last slash: Folio stores every source
     * address ending in one, and nothing tells a publisher their list has to spell it that way.
     */
    fun reasonForSource(url: String): String? {
        val wanted = url.trimEnd('/')
        return sources.firstOrNull { it.url.trimEnd('/').equals(wanted, ignoreCase = true) }?.reason
    }

    companion object {
        const val MAX_CHARS = 1024 * 1024
        const val MAX_ENTRIES = 5000
        const val MAX_VERSIONS = 64
        private val VERSION_OR_ANY = Regex("^(\\*|([0-9]+:)?[0-9][A-Za-z0-9.+~]*(-[A-Za-z0-9.+~]+)?)\\z")

        fun parse(text: String): ParseResult<RevocationList> {
            val p = Problems()
            val json = parseStrictObject(text, MAX_CHARS, p) ?: return p.result { error("unreachable") }
            val f = Fields(json, "", p, setOf("\$schema", "format", "timestamp", "packages", "sources"))
            f.anyString("\$schema")
            f.formatOne()
            val timestamp = f.long("timestamp", true, 0L..4102444800L)
            val packages = f.objects("packages", true, maxItems = MAX_ENTRIES)?.map { (i, item) ->
                val e = f.child(item, "${f.where("packages")}[$i]", setOf("id", "versions", "reason"))
                val id = e.string("id", true, PackageManifest.ID, PackageManifest.MAX_ID, "must be a package id")
                // null stands for "*": every version of this package.
                val versions = e.strings("versions", true, minItems = 1, maxItems = MAX_VERSIONS) { value, itemAt ->
                    e.checkString(value, itemAt, VERSION_OR_ANY, DebVersion.MAX_LENGTH, "must be a version or *")
                }?.map { if (it == "*") null else it }
                val reason = e.string("reason", true, maxLength = 200)
                RevokedPackage(id.orEmpty(), versions.orEmpty(), reason.orEmpty())
            }
            val sources = f.objects("sources", false, maxItems = MAX_ENTRIES)?.map { (i, item) ->
                val e = f.child(item, "${f.where("sources")}[$i]", setOf("url", "reason"))
                RevokedSource(
                    url = e.string("url", true, HTTPS_URL, MAX_URL, "must be an https:// link").orEmpty(),
                    reason = e.string("reason", true, maxLength = 200).orEmpty(),
                )
            }
            return p.result { RevocationList(timestamp!!, packages!!, sources.orEmpty()) }
        }
    }
}

/** [versions] holds the revoked versions; a null entry means every version. */
data class RevokedPackage(val id: String, val versions: List<String?>, val reason: String)

data class RevokedSource(val url: String, val reason: String)
