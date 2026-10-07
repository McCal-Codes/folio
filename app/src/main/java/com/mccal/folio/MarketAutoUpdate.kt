package com.mccal.folio

import android.content.Context
import com.mccal.folio.market.DebVersion
import com.mccal.folio.market.InstallResult
import com.mccal.folio.market.InstalledPackage
import java.io.File
import kotlinx.coroutines.sync.withLock

/**
 * Packages you installed from a source keep up to date by themselves (M1), in two halves so each does what it can:
 * the daily refresh downloads and checks a newer version and **stages** it, and an update goes in through the live app
 * (while Folio is running, or at its next start), with every check a normal install makes.
 *
 * Only an update that changes none of the settings the old version applied goes in on its own for now: new text,
 * pictures, a version number. One that would undo and re-apply settings waits for you to tap it, because an undo puts
 * back what the setting was when the old version went on, over anything you changed since.
 */
internal object MarketAutoUpdate {
    private const val DIR = "market-staged"

    /** One apply at a time: the daily job and the start-up check can both run it, and a staged file is installed once. */
    private val applying = kotlinx.coroutines.sync.Mutex()

    /**
     * The rule for an update that may install itself: it came from the source that installed it, it is newer, the
     * package is on and from a source (not a file), and nothing about the listing says stop.
     */
    fun isCandidate(
        origin: InstalledPackage.Origin, enabled: Boolean, installedSource: String?, installedVersion: DebVersion,
        listingSource: String, listingVersion: DebVersion, revoked: Boolean, impostor: Boolean, needsAnother: Boolean,
        /** The listing promises a size and a checksum. Without them nothing could be checked, so nothing installs by itself. */
        installable: Boolean = true,
    ): Boolean = origin == InstalledPackage.Origin.FOLIO_SOURCE && enabled && installedSource == listingSource &&
        listingVersion > installedVersion && !revoked && !impostor && !needsAnother && installable

    /** The newest newer listing for each installed package, from the source that installed it. */
    fun candidates(session: MarketSession): List<Pair<InstalledPackage, MarketEntry>> {
        val entries = session.entries()
        return session.installed().mapNotNull { installed ->
            entries.filter { it.id == installed.id }.filter { e ->
                isCandidate(installed.origin, installed.enabled, installed.sourceUrl, installed.version, e.source.url, e.entry.version,
                    e.revokedReason != null, e.clash != null, e.entry.needs.isNotEmpty(), e.entry.installable)
            }.maxByOrNull { it.entry.version }?.let { installed to it }
        }
    }

    internal fun stagedFile(context: Context, id: String, version: DebVersion) =
        File(File(context.cacheDir, DIR), "${"$id@$version".replace(Regex("[^A-Za-z0-9._@-]"), "_")}.pkg")

    /** Downloads and checks each candidate not yet staged; only ones that would not touch the person's settings are kept. Returns how many were staged. */
    suspend fun stage(context: Context, session: MarketSession): Int {
        var staged = 0
        for ((installed, entry) in candidates(session)) {
            // A package turned off on its own page is never staged; the notice for it still comes from candidates().
            if (!session.prefs.autoUpdateFor(installed.id)) continue
            val file = stagedFile(context, entry.id, entry.entry.version)
            if (file.exists()) continue
            val bytes = session.fetchBytes(entry) ?: continue
            if (!session.updateKeepsSettings(bytes, installed)) continue
            file.parentFile?.mkdirs()
            file.writeBytes(bytes)
            staged++
        }
        return staged
    }

    /**
     * Applies what is staged and still wanted, through [session] (built on the live launcher). A staged file that no
     * longer matches a candidate, or no longer matches the listing's checksum, is deleted unused. Returns the names updated.
     */
    suspend fun applyStaged(context: Context, session: MarketSession): List<String> = applying.withLock {
        // Safe Mode pauses optional work: the update may be the thing that is crashing.
        if (SafeMode.isOn(context)) return@withLock emptyList()
        val dir = File(context.cacheDir, DIR)
        if (!dir.isDirectory) return@withLock emptyList()
        // One install at a time, shared with the Get button; if one is running, the staged files wait for the next start.
        MarketWork.exclusive("auto-update") { applyAll(context, session, dir) } ?: emptyList()
    }

    private suspend fun applyAll(context: Context, session: MarketSession, dir: File): List<String> {
        val wanted = candidates(session).filter { (installed, _) -> session.prefs.autoUpdateFor(installed.id) }
        val updated = mutableListOf<String>()
        val keep = mutableSetOf<File>()
        for ((installed, entry) in wanted) {
            val file = stagedFile(context, entry.id, entry.entry.version)
            // Claimed by renaming, which is atomic, so even a second process could not install the same file twice.
            val claimed = File(file.path + ".claimed")
            if (!file.exists() || !file.renameTo(claimed)) continue
            keep += claimed
            val bytes = claimed.readBytes()
            val result = if (entry.entry.matches(bytes) && session.updateKeepsSettings(bytes, installed)) session.installStaged(entry, bytes) else null
            claimed.delete()
            when (result) {
                is InstallResult.Installed -> {
                    updated += result.installed.name; Diagnostics.autoUpdated(result.installed.id, result.installed.version)
                    result.replaced?.let { old -> session.autoUpdates.record(com.mccal.folio.market.AutoUpdate(result.installed.id, result.installed.name, old.version, result.installed.version, System.currentTimeMillis())) }
                }
                null -> Diagnostics.autoUpdateStale(entry.id)
                else -> Diagnostics.autoUpdateFailed(entry.id, result.javaClass.simpleName)
            }
        }
        dir.listFiles()?.filter { it !in keep && it.exists() && !it.name.endsWith(".claimed") }?.forEach { it.delete() }
        return updated
    }

    /** Whether this phone does automatic package updates at all: the gate, and the person's switch. */
    fun enabled(context: Context): Boolean =
        FeatureGate.MARKET_AUTO_UPDATE.isOpen(context) && rememberedMarketPrefs(context).autoUpdatePackages
}
