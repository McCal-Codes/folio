package com.mccal.folio

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import android.content.Context
import com.mccal.folio.market.BuiltInSource
import com.mccal.folio.market.FileStore
import com.mccal.folio.market.FolioPackage
import com.mccal.folio.market.FolioVersion
import com.mccal.folio.market.IndexPackage
import com.mccal.folio.market.InstallResult
import com.mccal.folio.market.InstalledPackage
import com.mccal.folio.market.InstalledStore
import com.mccal.folio.market.MarketFeature
import com.mccal.folio.market.RepoClient
import com.mccal.folio.market.Source
import com.mccal.folio.market.SourceKey
import com.mccal.folio.market.SourceList
import com.mccal.folio.market.SourceStore
import com.mccal.folio.market.UrlHttpClient
import com.mccal.folio.market.AuthorTrust
import com.mccal.folio.market.MarketPrefs
import com.mccal.folio.market.PackageInstaller
import com.mccal.folio.market.PackageSafeMode
import com.mccal.folio.market.RepoIndex
import java.io.File

private typealias EarlyAuthor = AuthorTrust.Result

/**
 * Everything the Market needs on the phone, wired together: the packages Folio ships (read from assets), what's
 * installed (a folder under `filesDir`), and the installer that applies them through [MarketHost].
 *
 * Sources over the network come in Phase 6; until then the only source is Folio's own, which needs no network at all.
 */
internal class MarketSession(
    context: Context,
    launcher: MarketLauncher,
    /** Where reading, unpacking and applying happen. A test replaces it so it doesn't have to wait on a thread. */
    internal val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val appContext = context.applicationContext

    val source = BuiltInSource(
        read = { path -> runCatching { appContext.assets.open(path).use { it.readBytes() } }.getOrNull() },
        list = { path -> runCatching { appContext.assets.list(path)?.toList().orEmpty() }.getOrDefault(emptyList()) },
    )

    private val files = FileStore(File(appContext.filesDir, "market"))

    /** How Featured looks, and whether the introduction has been seen. */
    val prefs = MarketPrefs(files)

    private val store = InstalledStore(files)
    private val safeMode = PackageSafeMode(files)
    private val installer = PackageInstaller(
        store, MarketHost(launcher), safeMode,
        folioVersion = FolioVersion.fromAppVersion(WhatsNew.currentVersion(context)),
    )

    /** Whether this build can read an unsigned source served from the phone: Folio Dev only. */
    val localDevAllowed = MarketFeature.isDevBuild(appContext.packageName)

    /** Sources the user added. Folio Dev can also point at a source served from the phone (unsigned, localhost only). */
    val sources = MarketSources(
        client = RepoClient(
            // Folio Dev, and only Folio Dev, may read a source served off this phone in the clear.
            http = UrlHttpClient(allowLocalhost = localDevAllowed),
            store = SourceStore(files),
            allowLocalDev = localDevAllowed,
        ),
        list = SourceList(files),
        http = UrlHttpClient(allowLocalhost = localDevAllowed),
        io = io,
        knownRevocations = { source.revocations() },
    )

    /** The package list, or null when the bundled files are unreadable, which only a broken build can cause. */
    fun index(): RepoIndex? = source.index()

    /** Folio's own source, as a [Source], so built-in packages carry a source like any other. */
    val builtIn = Source("folio://built-in/", name = appContext.getString(R.string.folio), kind = Source.Kind.BUILT_IN)

    /**
     * Every package the store can show: Folio's own first, then each source the user added, from its cached list.
     * Revoked packages keep their place with the reason, so nothing disappears without an explanation.
     */
    fun entries(): List<MarketEntry> = mergeEntries(
        builtIn = index()?.packages.orEmpty(),
        builtInSource = builtIn,
        // Folio's own revocation list rules everywhere: it can pull one of Folio's packages, and it can pull a
        // package offered by a source that hasn't admitted it yet.
        revocations = source.revocations(),
        fromSources = sources.cached().mapNotNull { status -> status.snapshot?.let { status.source to it } },
    )

    /**
     * Downloads and installs a package. A package the source has pulled is refused here as well as in the store, so
     * there is no screen that can install one, and reading, unpacking and applying always happen off the main thread.
     */
    suspend fun get(entry: MarketEntry): InstallResult = fetch(entry).also { Diagnostics.marketGot(entry.entry.id, it) }

    private suspend fun fetch(entry: MarketEntry): InstallResult = when {
        entry.revokedReason != null ->
            InstallResult.Failed(
                InstallResult.Reason.REVOKED,
                appContext.getString(R.string.text_1_s_was_pulled_by_its_source_2_s, entry.name, entry.revokedReason),
            )
        // A source claiming an id that belongs to a package inside Folio is claiming to be that package.
        entry.clash == MarketEntry.Impostor.BUILT_IN -> InstallResult.Failed(
            InstallResult.Reason.MISMATCH,
            appContext.getString(R.string.text_1_s_offers_this_under_a_name, entry.source.label),
        )
        entry.source.kind == Source.Kind.BUILT_IN -> withContext(io) { get(entry.entry) }
        else -> sources.download(
            entry.entry, entry.source, installer,
            onProgress = { bytes, total -> MarketWork.downloaded(bytes, total) },
            onApplying = { MarketWork.applying() },
        )
    }

    fun installed(): List<InstalledPackage> = store.installed()

    fun installed(id: String): InstalledPackage? = store.find(id)

    /** The page and payload for a package, without applying anything: what the package page shows. */
    fun read(id: String): FolioPackage? {
        val files = source.filesFor(id) ?: return null
        return (installer.readFiles(files) as? PackageInstaller.ReadResult.Ok)?.pkg
    }

    fun get(entry: IndexPackage): InstallResult {
        val files = source.filesFor(entry.id) ?: return InstallResult.Failed(InstallResult.Reason.ARCHIVE, appContext.getString(R.string.folio_couldn_t_find_that_package))
        return installer.installBuiltIn(files)
    }

    /** Who signed a package that arrived as a file, for the confirm sheet. Checks nothing else. */
    fun authorOf(pkg: FolioPackage): EarlyAuthor = AuthorTrust(files).checkFiles(pkg.id, pkg.version, pkg.files)

    /** Reads a `.foliopkg` someone opened, without applying it: the confirm sheet shows what's inside. */
    fun read(bytes: ByteArray): PackageInstaller.ReadResult = installer.read(bytes)

    /** Installs a file someone opened. It's recorded as coming from a file, not from a source. */
    suspend fun installFile(bytes: ByteArray): InstallResult =
        installFromFile(bytes).also { Diagnostics.marketGot((it as? InstallResult.Installed)?.installed?.id, it) }

    private suspend fun installFromFile(bytes: ByteArray): InstallResult = withContext(io) {
        // A file gets the same two refusals a source's listing does, before its signature can pin a key to the id.
        val pkg = (installer.read(bytes) as? PackageInstaller.ReadResult.Ok)?.pkg
        if (pkg != null) {
            if (source.filesFor(pkg.id) != null) return@withContext InstallResult.Failed(
                InstallResult.Reason.MISMATCH, appContext.getString(R.string.that_file_uses_a_name_that_belongs),
            )
            val revoked = sources.cached().firstNotNullOfOrNull { it.snapshot?.revocation?.reasonFor(pkg.id, pkg.version) }
            if (revoked != null) return@withContext InstallResult.Failed(
                // The same words as a pulled package from a source's list, so the two refusals read alike.
                InstallResult.Reason.REVOKED,
                appContext.getString(R.string.text_1_s_was_pulled_by_its_source_2_s, pkg.manifest.name.english, revoked),
            )
        }
        installer.install(bytes, origin = InstalledPackage.Origin.FILE)
    }

    fun remove(id: String): Boolean = installer.remove(id).also {
        Diagnostics.marketRemoved(id, it)
        if (it) pruneArt()
    }

    /**
     * Clears wallpaper art whose package has gone.
     *
     * The host deliberately leaves a picture on disk when a package's changes are put back, because Undo, Safe Mode
     * and Remove all go through that one path and only the last means gone. The installed list is what tells them
     * apart, and it is known here rather than there.
     */
    private fun pruneArt() {
        Diagnostics.artPruned(BackgroundLibrary.prune(appContext, store.installed().map { it.id }.toSet()))
    }

    // Undo removes through the installer directly, past remove() above, so it prunes for itself.
    fun undo(result: InstallResult.Installed): Boolean = installer.undo(result).also {
        Diagnostics.marketUndone(result.installed.id, it)
        if (it) pruneArt()
    }

    /**
     * Called when Folio starts after a crash: if a package was being applied, it's turned off rather than left to
     * break Home again. Returns the package that was turned off, so the store can explain itself.
     */
    fun noteCrash(): InstalledPackage? {
        val id = safeMode.noteCrash() ?: return null
        val off = installer.disable(id, appContext.getString(R.string.folio_stopped_twice_just_after_this_package))
        Diagnostics.marketTurnedOff(id, off, bySafeMode = true)
        return store.find(id)
    }

    /** Turns a package off the way Safe Mode does: its changes come off Home and its record stays. */
    fun disable(id: String, reason: String): Boolean =
        installer.disable(id, reason).also { Diagnostics.marketTurnedOff(id, it, bySafeMode = false) }

    /** Try Again, after Safe Mode turned a package off: its changes go back on. */
    fun enable(id: String): Boolean = installer.enable(id).also { Diagnostics.marketTurnedOn(id, it) }

    /** What a layout backup carries about packages, or null when this phone has none to carry. */
    fun exportPackages(): String? = store.installed().takeIf { it.isNotEmpty() }?.let { store.export() }

    /** How many packages a backup carries, for the restore sheet to say so before anything is touched. */
    fun countPackages(text: String?): Int = text?.let { store.readBackup(it)?.records?.size } ?: 0

    /**
     * Puts a backup's packages back, with the launcher's own restore run in the middle - see
     * [PackageInstaller.restoreBackup] for why the order matters. [putLayoutBack] always runs, including when there
     * are no packages to put back or Folio can't read the ones there are; null is returned in both of those cases.
     */
    fun restorePackages(text: String?, offReason: String, putLayoutBack: () -> Unit): PackageInstaller.Restore? {
        if (text == null) {
            putLayoutBack()
            return null
        }
        return installer.restoreBackup(text, offReason, putLayoutBack).also(Diagnostics::marketRestored)
    }
}

/** The Market's settings, for screens that only need those (Settings › Market) rather than the whole session. */
internal fun rememberedMarketPrefs(context: Context): MarketPrefs =
    MarketPrefs(FileStore(File(context.applicationContext.filesDir, "market")))

/** Where `folio-pkg serve` plus `adb reverse tcp:8787 tcp:8787` puts a source being written. */
internal const val DEFAULT_LOCAL_SOURCE = "http://localhost:8787/"

/**
 * The supporter source: Keyd's repository publishes its own releases and, beside them, a signed Folio source that
 * lists Keyd as an `externalApp` package.
 *
 * Redeeming a code adds it, so a supporter doesn't have to be handed an address to paste. That only works safely
 * because [SUPPORTER_SOURCE_KEY] ships in the app: adding a source normally shows its key fingerprint and waits
 * for the user, which is what stops someone impersonating a source (T4), and a source that arrives on its own has
 * nobody to ask. With the key already known there is nothing to confirm, and a host answering with a different
 * key fails the signature like any other.
 *
 * An empty key would mean the source is never added - the same rule as `BetaKeys.SUPPORTER` - and it stayed empty
 * until the real key existed, because a placeholder would let anyone publish as Folio's supporter source.
 */
internal const val SUPPORTER_SOURCE = "https://mccal-codes.github.io/folio-keyd/"

// The source's public key (2026-09-21). The private half signs the source in folio-keyd's publishing workflow and
// is nowhere else in any repository.
internal const val SUPPORTER_SOURCE_KEY =
    "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEOn02K6Fhi4Gto1Kb/q7I/iBZd4C0jqD/Y9yI9TyvRXBUy0qx3PqntQIUcFbw2bicxQOgdCLGS1rPevg+ninbOA=="

/**
 * Whether this phone sees the Market: Folio Dev or a supporter's code. Everything the Market hands out is also in
 * Settings, so a stable-release user isn't missing a feature — only the store that lists them.
 */
internal object MarketAccess {
    /**
     * A supporter's code opens the Market through the same scope that opens every other early feature, and through
     * the same beta switch: one code, one page to redeem it on, one switch to step back off the betas.
     *
     * Beta Updates on its own no longer opens it. That switch is about which builds you get, and the store is what
     * a supporter gets for supporting.
     */
    fun isOpen(context: Context): Boolean = FeatureGate.MARKET.isOpen(context)

    /**
     * Adds the supporter source, once, when a code is redeemed. Nothing happens without a key built in, and
     * nothing happens twice: a source already in the list is left exactly as it is, name and all.
     *
     * The reading happens in the background and its failure doesn't matter here - the source is in the list
     * either way, and the store refreshes it the next time it's opened.
     */
    fun addSupporterSource(context: Context): Boolean {
        val key = SourceKey.parse(SUPPORTER_SOURCE_KEY) ?: return false
        val app = context.applicationContext
        MarketWork.background {
            val session = MarketSession(app, ReadOnlyMarketLauncher)
            if (!session.sources.has(SUPPORTER_SOURCE)) {
                session.sources.trust(SUPPORTER_SOURCE, key, Source.Kind.SUPPORTER)
            }
        }
        return true
    }

    /** Removing the code removes the source with it. What it listed and installed stays; only the list goes. */
    fun forgetSupporterSource(context: Context) {
        if (SUPPORTER_SOURCE_KEY.isEmpty()) return
        val app = context.applicationContext
        MarketWork.background {
            MarketSession(app, ReadOnlyMarketLauncher).sources.forget(SUPPORTER_SOURCE)
        }
    }
}

/** Adding or forgetting a source changes no setting and applies no package, and with this it can't. */
private object ReadOnlyMarketLauncher : MarketLauncher {
    override val state = LauncherState()
    override fun installTweak(feature: TweakFeature) = Unit
    override fun removeTweak(feature: TweakFeature) = Unit
    override fun setFeatureScope(id: String, screen: FolioScreen, value: ScopeValue) = Unit
    override fun applyTheme(theme: FolioTheme) = Unit

    // Not silently ignored like the rest: applying is supposed to return what it replaced, and a snapshot invented
    // by a launcher that changed nothing would tell Undo to put back something that was never taken away.
    override fun applyArtBackground(art: Artwork, bytes: ByteArray, sha256: String): String =
        error("this launcher only adds and forgets sources")

    override fun restoreArtBackground(artId: String, snapshot: String) = Unit
}
