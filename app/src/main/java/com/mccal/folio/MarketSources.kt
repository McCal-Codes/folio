package com.mccal.folio

import com.mccal.folio.market.HttpClient
import com.mccal.folio.market.HttpResult
import com.mccal.folio.market.IndexPackage
import com.mccal.folio.market.InstallResult
import com.mccal.folio.market.PackageInstaller
import com.mccal.folio.market.RefreshResult
import com.mccal.folio.market.RepoClient
import com.mccal.folio.market.RevocationList
import com.mccal.folio.market.Source
import com.mccal.folio.market.SourceKey
import com.mccal.folio.market.SourceList
import com.mccal.folio.market.SourceSnapshot
import com.mccal.folio.market.normalizeSourceUrl
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A package in the store, with the source it came from. */
internal data class MarketEntry(
    val entry: IndexPackage,
    val source: Source,
    /** Why this package is turned off, from a revocation list. Null when it's fine. */
    val revokedReason: String? = null,
    /** True when the source that offers it isn't signed (a local one, during development). */
    val unsigned: Boolean = false,
    /**
     * Another source claiming the same package id.
     *
     * An id is the package's identity - settings, updates and Undo all hang off it - so two sources using one id
     * are two different things wearing the same name. Folio never picks a winner quietly: [Impostor.BUILT_IN] can't
     * be installed at all, and anything else is shown with both sources named.
     */
    val clash: Impostor? = null,
) {
    /**
     * This listing, not just its id: two sources can list one id, and a sheet that looked the id back up got whichever
     * source came first rather than the row that was tapped.
     */
    val listingKey: String get() = source.url + "\n" + entry.id

    enum class Impostor {
        /** It claims an id that belongs to a package inside Folio. There's no honest reason to do that. */
        BUILT_IN,

        /** Two sources the user added offer the same id. Which one they meant is theirs to say. */
        ANOTHER_SOURCE,
    }

    val id: String get() = entry.id
    val name: String get() = entry.manifest?.name?.english ?: entry.id
}

/**
 * Every package the store can show, from Folio's own index and from each source the user added.
 *
 * A package id is its identity - settings, updates and Undo all hang off it - so the same id from two places is two
 * different things wearing one name. Nothing is hidden and nothing is silently preferred: both are listed, and each
 * says who else is using the name. [MarketEntry.Impostor.BUILT_IN] is the one that can't be installed, because a
 * source claiming a name that belongs to a package inside Folio is claiming to be it.
 */
internal fun mergeEntries(
    builtIn: List<IndexPackage>,
    builtInSource: Source,
    revocations: RevocationList?,
    fromSources: List<Pair<Source, SourceSnapshot>>,
): List<MarketEntry> = buildList {
    val builtInIds = builtIn.map { it.id }.toSet()
    builtIn.forEach { add(MarketEntry(it, builtInSource, revocations?.reasonFor(it.id, it.version))) }

    val seen = fromSources.flatMap { (_, snapshot) -> snapshot.index.packages.map { it.id } }
        .groupingBy { it }.eachCount()

    for ((source, snapshot) in fromSources) {
        val unsigned = source.kind == Source.Kind.LOCAL_DEV
        snapshot.index.packages.forEach { entry ->
            val reason = snapshot.revokedReason(entry) ?: revocations?.reasonFor(entry.id, entry.version)
            val clash = when {
                entry.id in builtInIds -> MarketEntry.Impostor.BUILT_IN
                (seen[entry.id] ?: 0) > 1 -> MarketEntry.Impostor.ANOTHER_SOURCE
                else -> null
            }
            add(MarketEntry(entry, source, reason, unsigned, clash))
        }
    }
}

/** What happened the last time Folio asked a source for its list. */
internal data class SourceStatus(
    val source: Source,
    val snapshot: SourceSnapshot? = null,
    val failure: RefreshResult.Failed? = null,
    val refreshing: Boolean = false,
) {
    val packages: List<IndexPackage> get() = snapshot?.index?.packages.orEmpty()
}

/**
 * The sources the user added, and the packages they offer.
 *
 * Every network call happens off the main thread and through [RepoClient], so the signature, rollback, freshness and
 * hash checks are the same ones the tests cover. A source that fails keeps its last good list.
 */
internal class MarketSources(
    private val client: RepoClient,
    private val list: SourceList,
    private val http: HttpClient,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    /**
     * The revocation list Folio ships with. A source listed there is refused before Folio calls it at all, which is
     * the only way to disown a source that has been taken over between releases.
     */
    private val knownRevocations: () -> RevocationList? = { null },
) {
    fun sources(): List<Source> = list.added()

    /** The cached list for each source, without asking the network. */
    fun cached(): List<SourceStatus> = sources().map { source ->
        SourceStatus(source, client.cachedSnapshot(source.url))
    }

    /** Reads a source's key so its fingerprint can be shown before anything is trusted. */
    suspend fun inspect(url: String): RefreshResult = withContext(io) {
        val base = normalizeSourceUrl(url)
        if (base.startsWith("http://")) return@withContext client.refreshLocalDev(base)
        client.readKey(base)
    }

    /** Pins a key the user has confirmed, adds the source, and reads its list. */
    suspend fun trust(url: String, key: SourceKey, kind: Source.Kind = Source.Kind.ADDED): RefreshResult = withContext(io) {
        val base = normalizeSourceUrl(url)
        client.trust(base, key)
        list.add(Source(base, kind = kind, addedAt = System.currentTimeMillis() / 1000))
        refresh(base, force = true)
    }

    /** True when this source is already in the list, whoever put it there. */
    fun has(url: String): Boolean = sources().any { it.url == normalizeSourceUrl(url) }

    /** Adds a local source for development. Unsigned, so only Folio Dev can use it at all. */
    suspend fun addLocalDev(url: String): RefreshResult = withContext(io) {
        val base = normalizeSourceUrl(url)
        val result = client.refreshLocalDev(base)
        if (result is RefreshResult.Updated) {
            list.add(Source(base, kind = Source.Kind.LOCAL_DEV, addedAt = System.currentTimeMillis() / 1000))
            list.rename(base, result.snapshot.index.name.english)
        }
        result
    }

    suspend fun refresh(url: String, force: Boolean): RefreshResult = withContext(io) {
        // One spelling for the rest of this function. RepoClient and SourceList each normalize again at their own
        // door, so this changes nothing today; it's here so the lookup and the writes can't drift apart later.
        val base = normalizeSourceUrl(url)
        val source = sources().firstOrNull { it.url == base }
        val result = if (source?.kind == Source.Kind.LOCAL_DEV) client.refreshLocalDev(base)
        else client.refresh(base, force, knownRevocations())
        val name = (result as? RefreshResult.Updated)?.snapshot?.index?.name?.english
            ?: (result as? RefreshResult.Unchanged)?.snapshot?.index?.name?.english
        name?.let { list.rename(base, it) }
        result
    }

    /** Asks every source, one at a time so a slow one doesn't hold the others up in parallel connections. */
    suspend fun refreshAll(force: Boolean): Map<String, RefreshResult> = withContext(io) {
        sources().associate { it.url to refresh(it.url, force) }
    }

    fun forget(url: String) {
        client.forget(url)
        list.remove(url)
    }

    /**
     * Downloads a package and installs it: size and checksum are checked against the index entry before anything is
     * opened, and [PackageInstaller] does the rest.
     */
    /**
     * Fetches a file a source listed, with no opinion about what it is: an APK the Market installs goes through
     * the same client, the same byte cap and the same rules as everything else. [maxBytes] is what the index
     * promised, so a source can't grow a download after the fact.
     */
    /**
     * True when this source's signed list is past its `maxAge` (T3). It can still be browsed offline, but nothing is
     * installed from it: a host that simply stops answering would otherwise keep a frozen list, and every revocation
     * it never delivered, installable for ever.
     */
    fun isStale(source: Source): Boolean = stale(source)

    private fun stale(source: Source): Boolean {
        if (source.kind == Source.Kind.BUILT_IN || source.kind == Source.Kind.LOCAL_DEV) return false
        return client.isStale(source.url)
    }

    suspend fun fetch(source: Source, url: String, maxBytes: Int, onProgress: (Long, Long) -> Unit): ByteArray? =
        withContext(io) {
            if (stale(source)) return@withContext null
            val full = if (url.startsWith("https://")) url else source.url + url
            (http.get(full, maxBytes, onProgress) as? HttpResult.Body)?.bytes
        }

    /**
     * Downloads a listing's bytes without applying anything, for staging an update: null when the list is too old, there
     * is nowhere to download from, the download fails, or it is not the size and checksum the source promised.
     */
    suspend fun fetchPackage(entry: IndexPackage, source: Source): ByteArray? = withContext(io) {
        if (stale(source)) return@withContext null
        // A checksum is required too: staging and installing on its own with nothing to check against is not allowed.
        if (entry.sha256 == null) return@withContext null
        val url = entry.url ?: return@withContext null
        val size = entry.size ?: return@withContext null
        val full = if (url.startsWith("https://")) url else source.url + url
        (http.get(full, size, { _, _ -> }) as? HttpResult.Body)?.bytes?.takeIf { entry.matches(it) }
    }

    suspend fun download(
        entry: IndexPackage,
        source: Source,
        installer: PackageInstaller,
        /** Bytes so far and the length the source declared, for the ring in the Get button. */
        onProgress: (Long, Long) -> Unit = { _, _ -> },
        onApplying: () -> Unit = {},
    ): InstallResult = withContext(io) {
        if (stale(source)) {
            return@withContext InstallResult.Failed(InstallResult.Reason.ARCHIVE, "that source's list is too old to install from, so refresh it first")
        }
        val url = entry.url ?: return@withContext InstallResult.Failed(InstallResult.Reason.ARCHIVE, "that package has nowhere to download from")
        val size = entry.size ?: return@withContext InstallResult.Failed(InstallResult.Reason.SIZE, "that package didn't say how big it is")
        val full = if (url.startsWith("https://")) url else source.url + url
        when (val result = http.get(full, size, onProgress)) {
            is HttpResult.Body -> {
                onApplying()
                installer.install(
                    result.bytes,
                    expected = entry,
                    origin = com.mccal.folio.market.InstalledPackage.Origin.FOLIO_SOURCE,
                    sourceUrl = source.url,
                )
            }
            is HttpResult.TooLarge -> InstallResult.Failed(InstallResult.Reason.SIZE, "that download is bigger than the source said")
            is HttpResult.NotModified -> InstallResult.Failed(InstallResult.Reason.ARCHIVE, "the source answered oddly")
            is HttpResult.Failed -> InstallResult.Failed(InstallResult.Reason.ARCHIVE, result.message)
        }
    }
}
