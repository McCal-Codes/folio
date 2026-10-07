package com.mccal.folio.market

import org.json.JSONObject
import java.io.File

/**
 * Small string store. The app backs it with a folder; tests use [MemoryStore].
 *
 * [set] says whether the value is really stored. It can fail - a full disk, a file Android took away - and a caller
 * that has just changed the Home screen needs to know, because a change nobody recorded can't be undone.
 */
interface KeyValueStore {
    fun get(key: String): String?
    fun set(key: String, value: String?): Boolean
}

class MemoryStore : KeyValueStore {
    private val values = HashMap<String, String>()
    override fun get(key: String) = values[key]
    override fun set(key: String, value: String?): Boolean {
        if (value == null) values.remove(key) else values[key] = value
        return true
    }
}

/** One file per key inside [dir] (the app passes a folder under `filesDir`). Keys are hashed, so they're safe as names. */
class FileStore(private val dir: File) : KeyValueStore {
    override fun get(key: String): String? = file(key).takeIf { it.isFile }?.let {
        runCatching { it.readText() }.getOrNull()
    }

    /**
     * Writes beside the target and moves it into place, so a crash can't leave half a file behind - and, when the
     * move fails, leaves what was already there alone. The old version of this deleted the target first and tried
     * again, which turned one failed write into a lost value: for `source:<url>:state` that value is the pinned key,
     * and losing it quietly asks the user to trust the source all over again, exactly as a stolen key would.
     *
     * The temp file carries a unique name and the whole thing is serialised, so two writers can't use one another's.
     */
    @Synchronized
    override fun set(key: String, value: String?): Boolean {
        val target = file(key)
        if (value == null) return !target.exists() || target.delete()
        dir.mkdirs()
        val temp = File(dir, target.name + ".tmp." + java.util.UUID.randomUUID().toString().take(8))
        return runCatching {
            temp.writeText(value)
            java.nio.file.Files.move(
                temp.toPath(), target.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE,
            )
            true
        }.recoverCatching {
            // Some filesystems can't move atomically; a plain replace still never removes the old file first.
            java.nio.file.Files.move(temp.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            true
        }.also { temp.delete() }.getOrDefault(false)
    }

    private fun file(key: String) = File(dir, sha256Hex(key.toByteArray()).take(32))
}

/**
 * What Folio remembers about a source between refreshes: its pinned key, the newest entry it has accepted (rollback
 * protection), the cached index and its ETag.
 */
data class SourceState(
    val keyBase64: String? = null,
    val lastTimestamp: Long = 0,
    val lastRevokedTimestamp: Long = 0,
    val etag: String? = null,
    val lastRefresh: Long = 0,
) {
    val pinnedKey: SourceKey? get() = keyBase64?.let(SourceKey::parse)

    internal fun toJson(): String = JSONObject()
        .put("keyBase64", keyBase64)
        .put("lastTimestamp", lastTimestamp)
        .put("lastRevokedTimestamp", lastRevokedTimestamp)
        .put("etag", etag)
        .put("lastRefresh", lastRefresh)
        .toString()

    internal companion object {
        fun fromJson(text: String?): SourceState {
            val json = text?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return SourceState()
            return SourceState(
                keyBase64 = json.optString("keyBase64").takeIf { it.isNotEmpty() },
                lastTimestamp = json.optLong("lastTimestamp"),
                lastRevokedTimestamp = json.optLong("lastRevokedTimestamp"),
                etag = json.optString("etag").takeIf { it.isNotEmpty() },
                lastRefresh = json.optLong("lastRefresh"),
            )
        }
    }
}

/** Reads and writes each source's state and its cached files. */
class SourceStore(private val store: KeyValueStore) {
    fun state(url: String): SourceState = SourceState.fromJson(store.get(key(url, "state")))

    fun save(url: String, state: SourceState) = store.set(key(url, "state"), state.toJson())

    fun cached(url: String, name: String): String? = store.get(key(url, "file:$name"))

    fun cache(url: String, name: String, text: String?) = store.set(key(url, "file:$name"), text)

    /** Drops everything about a source, for Remove Source. */
    fun forget(url: String) {
        store.set(key(url, "state"), null)
        for (name in listOf("index", "entry", "revoked")) store.set(key(url, "file:$name"), null)
    }

    private fun key(url: String, part: String) = "source:${normalizeSourceUrl(url)}:$part"
}

/**
 * A source is identified by its base URL, with one trailing slash, so `…/repo` and `…/repo/` are the same source.
 * A GitHub repository address (`github.com/owner/repo`, with or without `https://`) is the repository's GitHub Pages
 * site, `https://owner.github.io/repo/`: Folio reads static files only and never calls GitHub's API. Anything else, a
 * path inside the repository included, is left exactly as typed.
 */
fun normalizeSourceUrl(url: String): String = githubPagesUrl(url) ?: (url.trim().trimEnd('/') + "/")

private val GITHUB_REPO = Regex("""^(?:https://)?(?:www\.)?github\.com/([A-Za-z0-9](?:[A-Za-z0-9-]{0,38}))/([A-Za-z0-9_.-]+?)(?:\.git)?/?$""", RegexOption.IGNORE_CASE)

/** The GitHub Pages site for a `github.com/owner/repo` address, or null for anything that isn't exactly that. */
fun githubPagesUrl(input: String): String? {
    val match = GITHUB_REPO.matchEntire(input.trim()) ?: return null
    val owner = match.groupValues[1].lowercase()
    val repo = match.groupValues[2]
    if (repo == "." || repo == ".." || repo.isEmpty()) return null
    // A repository named owner.github.io is the owner's own site, at the root.
    return if (repo.equals("$owner.github.io", ignoreCase = true)) "https://$owner.github.io/" else "https://$owner.github.io/$repo/"
}
