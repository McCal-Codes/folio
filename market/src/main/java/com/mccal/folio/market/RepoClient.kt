package com.mccal.folio.market

import org.json.JSONObject
/** What a source looks like after a successful refresh. */
data class SourceSnapshot(
    val url: String,
    val entry: SourceEntry,
    val index: RepoIndex,
    val revocation: RevocationList?,
    val fetchedAt: Long,
    /** Entries this Folio skipped: newer fields, newer blocks, packages that need a newer Folio. */
    val notes: List<String> = emptyList(),
) {
    /** The reason a package is turned off, from this source's revocation list. */
    fun revokedReason(pkg: IndexPackage): String? = revocation?.reasonFor(pkg.id, pkg.version)

    val usablePackages: List<IndexPackage> get() = index.packages.filter { revokedReason(it) == null }
}

sealed interface RefreshResult {
    data class Updated(val snapshot: SourceSnapshot) : RefreshResult

    /** Nothing changed: the source answered 304, the index still matches its hash, or it's too soon to ask again. */
    data class Unchanged(val snapshot: SourceSnapshot) : RefreshResult

    /**
     * The user has to look at the key before Folio will use this source: either it's new, or the key changed, which is
     * what a stolen-key attack looks like (T4, T5). [previous] is the pinned key when one is being replaced.
     */
    data class NeedsTrust(val url: String, val key: SourceKey, val previous: SourceKey?) : RefreshResult

    /** [cached] is the copy Folio keeps showing, so a failed refresh never empties the store. */
    data class Failed(val reason: Reason, val message: String, val cached: SourceSnapshot? = null) : RefreshResult

    enum class Reason { INSECURE, NETWORK, MISSING, KEY_MISMATCH, SIGNATURE, ROLLBACK, EXPIRED, SIZE, HASH, PARSE, REVOKED }
}

/**
 * Fetches and checks a source's files (Phase 2). The order is the one in the threat model: signature, then rollback and
 * freshness, then size and hash, then parsing. Nothing is trusted before its signature is checked, and a failed refresh
 * keeps the cached copy.
 *
 * Folio only ever reads static files, never a repository API, which is what keeps it inside GitHub's rate limits (60
 * requests an hour per address). One refresh makes at most five requests: entry, its signature, the index, and the
 * revocation list with its signature.
 */
class RepoClient(
    private val http: HttpClient,
    private val store: SourceStore,
    /**
     * Whether [refreshLocalDev] works at all. Folio Dev passes true so someone building a package can serve it from
     * the phone; the release build passes false, and then there is no code path that reads an unsigned index.
     */
    private val allowLocalDev: Boolean = false,
    /**
     * Whether a plain-http source address is acceptable. Always false unless the caller says otherwise, and only Folio
     * Dev's debug source does: it admits one loopback address for the stub, nothing else. Signatures are checked
     * exactly as for https; this only changes which addresses may be asked.
     */
    private val allowInsecure: (String) -> Boolean = { false },
    private val clock: () -> Long = { System.currentTimeMillis() / 1000 },
) {
    /**
     * Reads a source's key so the user can check its fingerprint before adding it. Also used when a source's key
     * changes, to show the old and new fingerprints side by side.
     */
    fun readKey(url: String): RefreshResult {
        val base = normalizeSourceUrl(url)
        if (!base.startsWith("https://") && !allowInsecure(base)) return RefreshResult.Failed(RefreshResult.Reason.INSECURE, "sources must use https")
        val key = when (val result = http.get(base + KEY_FILE, SourceKey.MAX_KEY_CHARS)) {
            is HttpResult.Body -> SourceKey.parse(result.bytes.decodeToString())
                ?: return RefreshResult.Failed(RefreshResult.Reason.PARSE, "that source's key file isn't a P-256 key")
            is HttpResult.NotModified, is HttpResult.TooLarge -> return RefreshResult.Failed(RefreshResult.Reason.NETWORK, "the source answered oddly")
            is HttpResult.Failed -> return RefreshResult.Failed(
                if (result.missing) RefreshResult.Reason.MISSING else RefreshResult.Reason.NETWORK,
                if (result.missing) "that address doesn't have a Folio source" else result.message,
            )
        }
        val pinned = store.state(base).pinnedKey
        // The key Folio already trusts: nothing to confirm. Asking again showed "this source changed its key" beside two
        // identical fingerprints, and confirming it reset the rollback floors below.
        if (pinned != null && pinned.spki.contentEquals(key.spki)) return refresh(base)
        return RefreshResult.NeedsTrust(base, key, pinned)
    }

    /** Pins [key] after the user has confirmed its fingerprint. */
    fun trust(url: String, key: SourceKey) {
        val base = normalizeSourceUrl(url)
        val state = store.state(base)
        // Trusting the same key again changes nothing: resetting the floors would let an older signed entry, index or
        // revocation list back in.
        if (state.pinnedKey?.spki?.contentEquals(key.spki) == true) return
        // A new key starts its own rollback history, so an old signed entry can't be replayed under it.
        store.save(base, state.copy(keyBase64 = key.base64, lastTimestamp = 0, lastRevokedTimestamp = 0, etag = null))
    }

    fun forget(url: String) = store.forget(normalizeSourceUrl(url))

    /**
     * Checks the source for a newer index. [force] skips the every-six-hours limit (pull to refresh), and
     * [knownRevocations] is the revocation list from a source the user already trusts, which can disown this one.
     */
    fun refresh(url: String, force: Boolean = false, knownRevocations: RevocationList? = null): RefreshResult {
        val base = normalizeSourceUrl(url)
        if (!base.startsWith("https://") && !allowInsecure(base)) return RefreshResult.Failed(RefreshResult.Reason.INSECURE, "sources must use https")
        knownRevocations?.reasonForSource(base)?.let {
            return RefreshResult.Failed(RefreshResult.Reason.REVOKED, it)
        }
        val state = store.state(base)
        val pinned = state.pinnedKey ?: return readKey(base)
        val cached = cachedSnapshot(base, state)
        if (!force && cached != null && clock() - state.lastRefresh < MIN_REFRESH_SECONDS) {
            return RefreshResult.Unchanged(cached)
        }

        // 1. The entry and its detached signature, checked before anything in them is believed.
        val entryBytes = when (val result = http.get(base + ENTRY_FILE, SourceEntry.MAX_CHARS)) {
            is HttpResult.Body -> result.bytes
            is HttpResult.TooLarge -> return RefreshResult.Failed(RefreshResult.Reason.SIZE, "that source's entry file is too big", cached)
            is HttpResult.NotModified -> return RefreshResult.Failed(RefreshResult.Reason.NETWORK, "the source answered oddly", cached)
            is HttpResult.Failed -> return RefreshResult.Failed(
                if (result.missing) RefreshResult.Reason.MISSING else RefreshResult.Reason.NETWORK, result.message, cached,
            )
        }
        val signature = when (val result = http.get(base + ENTRY_FILE + SIGNATURE_SUFFIX, SourceKey.MAX_SIGNATURE_CHARS)) {
            is HttpResult.Body -> result.bytes.decodeToString()
            else -> return RefreshResult.Failed(RefreshResult.Reason.SIGNATURE, "that source isn't signed", cached)
        }
        if (!pinned.verifies(entryBytes, signature)) {
            return RefreshResult.Failed(RefreshResult.Reason.SIGNATURE, "the source's signature didn't match", cached)
        }
        val entry = when (val parsed = SourceEntry.parse(entryBytes.decodeToString())) {
            is ParseResult.Ok -> parsed.value
            is ParseResult.Unsupported -> return RefreshResult.Failed(RefreshResult.Reason.PARSE, "that source needs a newer Folio", cached)
            is ParseResult.Invalid -> return RefreshResult.Failed(RefreshResult.Reason.PARSE, parsed.errors.first(), cached)
        }
        if (entry.keyId != pinned.keyId) {
            // Signed with the pinned key but naming another one: refuse rather than guess which key is meant.
            return RefreshResult.Failed(RefreshResult.Reason.KEY_MISMATCH, "that source's key id doesn't match the one Folio pinned", cached)
        }

        // 2. Rollback and freshness.
        if (entry.timestamp < state.lastTimestamp) {
            return RefreshResult.Failed(RefreshResult.Reason.ROLLBACK, "the source served an older list than before", cached)
        }
        val now = clock()
        if (now > entry.staleAfter) {
            return RefreshResult.Failed(RefreshResult.Reason.EXPIRED, "that source hasn't been updated in too long", cached)
        }

        // 3. The index: reuse the cached copy when it already matches the hash the entry pins.
        val cachedIndex = store.cached(base, "index")
        // The ETag only means "you already have this file", so it's only true once the file really is cached. Saving
        // it beside the download instead left a source stuck for good when a later step failed: the next refresh
        // asked with the new ETag, got 304, and reported the tamper warning for ever.
        var downloadedEtag: String? = null
        val indexText = if (cachedIndex != null && sha256Hex(cachedIndex.toByteArray()) == entry.index.sha256) {
            cachedIndex
        } else {
            when (val result = http.get(base + entry.index.path, entry.index.size, state.etag)) {
                is HttpResult.NotModified ->
                    // The source says nothing changed, but its entry pins a hash the cached copy doesn't have.
                    return RefreshResult.Failed(RefreshResult.Reason.HASH, "that source's list doesn't match what it signed", cached)
                is HttpResult.TooLarge ->
                    // Only the signed number of bytes is ever read, so a swollen list is stopped before it's in memory.
                    return RefreshResult.Failed(RefreshResult.Reason.SIZE, "that source's list isn't the size it signed", cached)
                is HttpResult.Failed -> return RefreshResult.Failed(RefreshResult.Reason.NETWORK, result.message, cached)
                is HttpResult.Body -> {
                    if (result.bytes.size != entry.index.size) {
                        return RefreshResult.Failed(RefreshResult.Reason.SIZE, "that source's list isn't the size it signed", cached)
                    }
                    if (sha256Hex(result.bytes) != entry.index.sha256) {
                        return RefreshResult.Failed(RefreshResult.Reason.HASH, "that source's list doesn't match what it signed", cached)
                    }
                    downloadedEtag = result.etag
                    // Strict, because the list is cached as text and re-hashed from it: a lossy decode would never match
                    // the signed hash again and the source would show the tamper warning on every later refresh.
                    runCatching { result.bytes.decodeToString(throwOnInvalidSequence = true) }.getOrNull()
                        ?: return RefreshResult.Failed(RefreshResult.Reason.PARSE, "that source's list isn't UTF-8 text", cached)
                }
            }
        }
        val parsedIndex = RepoIndex.parse(indexText)
        val index = when (parsedIndex) {
            is ParseResult.Ok -> parsedIndex.value
            is ParseResult.Unsupported -> return RefreshResult.Failed(RefreshResult.Reason.PARSE, "that source needs a newer Folio", cached)
            is ParseResult.Invalid -> return RefreshResult.Failed(RefreshResult.Reason.PARSE, parsedIndex.errors.first(), cached)
        }

        // 4. The source's own revocation list, if it publishes one.
        val revocation = readRevocations(base, pinned, state)
        revocation?.reasonForSource(base)?.let {
            return RefreshResult.Failed(RefreshResult.Reason.REVOKED, it, cached)
        }

        store.cache(base, "index", indexText)
        store.cache(base, "entry", entryBytes.decodeToString())
        store.save(
            base,
            store.state(base).copy(
                lastTimestamp = entry.timestamp,
                lastRevokedTimestamp = revocation?.timestamp ?: state.lastRevokedTimestamp,
                lastRefresh = now,
                etag = downloadedEtag ?: store.state(base).etag,
            ),
        )
        val snapshot = SourceSnapshot(base, entry, index, revocation, now, (parsedIndex as ParseResult.Ok).ignored)
        return if (indexText == cachedIndex) RefreshResult.Unchanged(snapshot) else RefreshResult.Updated(snapshot)
    }

    /**
     * Reads an unsigned index straight off a source on this phone, for someone building a package
     * (`folio-pkg serve` plus `adb reverse`). No signature, no key, no rollback or freshness checks — none of them
     * mean anything when the source is a file on your own desk.
     *
     * Only Folio Dev can do this: the release build is built with [allowLocalDev] false, and only `localhost` is
     * allowed even then, so this can never reach a source on the internet.
     */
    fun refreshLocalDev(url: String): RefreshResult {
        if (!allowLocalDev) return RefreshResult.Failed(RefreshResult.Reason.INSECURE, "local sources need Folio Dev")
        val base = normalizeSourceUrl(url)
        if (!isLocal(base)) return RefreshResult.Failed(RefreshResult.Reason.INSECURE, "a local source has to be on this phone")
        val bytes = when (val result = http.get(base + "index.json", RepoIndex.MAX_CHARS)) {
            is HttpResult.Body -> result.bytes
            is HttpResult.TooLarge -> return RefreshResult.Failed(RefreshResult.Reason.SIZE, "that list is too big")
            // Say what actually went wrong. "Nothing is being served there" was said for every failure, including
            // the build refusing plain HTTP, which sent someone looking at their server for an hour.
            is HttpResult.Failed -> return RefreshResult.Failed(RefreshResult.Reason.NETWORK, result.message)
            else -> return RefreshResult.Failed(RefreshResult.Reason.NETWORK, "nothing is being served at that address")
        }
        val index = when (val parsed = RepoIndex.parse(bytes.decodeToString())) {
            is ParseResult.Ok -> parsed.value
            is ParseResult.Unsupported -> return RefreshResult.Failed(RefreshResult.Reason.PARSE, "that list needs a newer Folio")
            is ParseResult.Invalid -> return RefreshResult.Failed(RefreshResult.Reason.PARSE, parsed.errors.first())
        }
        val now = clock()
        val entry = SourceEntry("0".repeat(16), now, MIN_LOCAL_MAX_AGE, FileRef("index.json", sha256Hex(bytes), bytes.size))
        store.cache(base, "index", bytes.decodeToString())
        // The made-up entry is cached beside the index, because [cachedSnapshot] needs both and will hand back
        // nothing without it. Without this the refresh says "updated" and the source then has no packages at
        // all: not in the list, not in Packages, no count beside its name.
        store.cache(base, "entry", localEntryJson(entry))
        store.save(base, store.state(base).copy(lastRefresh = now))
        return RefreshResult.Updated(SourceSnapshot(base, entry, index, revocation = null, fetchedAt = now, notes = listOf(UNSIGNED_NOTE)))
    }

    /** A local source has no `entry.json` to fetch, so Folio writes the one it made in the same shape. */
    private fun localEntryJson(entry: SourceEntry) = JSONObject()
        .put("format", 1)
        .put("keyId", entry.keyId)
        .put("timestamp", entry.timestamp)
        .put("maxAge", entry.maxAge)
        .put("index", JSONObject().put("path", entry.index.path).put("sha256", entry.index.sha256).put("size", entry.index.size))
        .toString()

    /** True when the signed list Folio has for this source is past its `maxAge` (T3). */
    fun isStale(url: String): Boolean {
        val signed = cachedSnapshot(url)?.entry ?: return false
        return clock() > signed.staleAfter
    }

    /** The last good copy, so the store keeps working offline and a failed refresh changes nothing. */
    fun cachedSnapshot(url: String): SourceSnapshot? {
        val base = normalizeSourceUrl(url)
        return cachedSnapshot(base, store.state(base))
    }

    private fun cachedSnapshot(base: String, state: SourceState): SourceSnapshot? {
        val indexText = store.cached(base, "index") ?: return null
        val index = (RepoIndex.parse(indexText) as? ParseResult.Ok)?.value ?: return null
        // The entry that was accepted with this index, kept so an offline snapshot reports what was really signed.
        val entry = store.cached(base, "entry")?.let { (SourceEntry.parse(it) as? ParseResult.Ok)?.value } ?: return null
        val revocation = store.cached(base, "revoked")?.let { (RevocationList.parse(it) as? ParseResult.Ok)?.value }
        return SourceSnapshot(base, entry, index, revocation, state.lastRefresh)
    }

    /**
     * The newest revocation list Folio has for this source. A missing, broken, unsigned or older copy never replaces
     * the one already stored, so a host can't un-revoke a package by withholding the file or serving an old one.
     */
    private fun readRevocations(base: String, key: SourceKey, state: SourceState): RevocationList? {
        val stored = store.cached(base, "revoked")?.let { (RevocationList.parse(it) as? ParseResult.Ok)?.value }
        val body = http.get(base + REVOKED_FILE, RevocationList.MAX_CHARS)
        val bytes = (body as? HttpResult.Body)?.bytes ?: return stored
        val signature = (http.get(base + REVOKED_FILE + SIGNATURE_SUFFIX, SourceKey.MAX_SIGNATURE_CHARS) as? HttpResult.Body)
            ?.bytes?.decodeToString() ?: return stored
        if (!key.verifies(bytes, signature)) return stored
        val list = (RevocationList.parse(bytes.decodeToString()) as? ParseResult.Ok)?.value ?: return stored
        if (list.timestamp < state.lastRevokedTimestamp) return stored
        store.cache(base, "revoked", bytes.decodeToString())
        return list
    }

    companion object {
        const val ENTRY_FILE = "entry.json"
        const val KEY_FILE = "key.pub"
        const val REVOKED_FILE = "revoked.json"
        const val SIGNATURE_SUFFIX = ".sig"

        /** Background refreshes wait six hours; pull to refresh passes `force`. */
        const val MIN_REFRESH_SECONDS = 6 * 60 * 60L

        /** What a local source's packages are labelled with, so nothing unsigned is ever mistaken for signed. */
        const val UNSIGNED_NOTE = "Unsigned: served from this phone"
        private const val MIN_LOCAL_MAX_AGE = 3600L

        /** Only the phone itself. `adb reverse` puts the desktop's server here. */
        internal fun isLocal(url: String): Boolean =
            url.startsWith("http://localhost:") || url.startsWith("http://127.0.0.1:") || url.startsWith("https://localhost:")
    }
}
