package com.mccal.folio

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import com.mccal.folio.market.DebVersion

/**
 * Updating an app of its own that is already on the phone: Keyd 0.2.0 installed, its source now listing 0.3.0.
 *
 * Until now the store only asked whether the app was there, so an app with a newer listing said Open for ever. This
 * reads the version Android has, compares it with the listing, and decides how the update may go in.
 *
 * **Versions compare as semantic versions**, because that is what an app's versionName is: `0.3.0-beta.3` comes
 * before `0.3.0`. The index's own order is dpkg's, where `-beta.3` would be a revision and sort *after* `0.3.0`, so
 * both sides are turned into dpkg's spelling of a pre-release first (the first `-` becomes `~`, and `+build` is
 * dropped) and then compared with [DebVersion]. The versionCode can't be used: the listing doesn't carry one, and
 * Keyd gives a beta the same code as its release.
 *
 * **Two refusals, whatever else is true.** The APK has to be the same app (its package name) signed by the same
 * key as the copy on the phone. Android would refuse a different key too, but only after its own screen had asked;
 * checking first means a source can't put a stranger's app in front of that screen under a familiar name.
 *
 * **Silent only when Folio put the app there.** Android lets an installer update without asking only an app it is
 * the installer of record for, and Folio only asks it to when that is so and the signer matches. Anything else goes
 * through Android's own confirmation, as a first install does.
 */
internal object MarketAppUpdate {

    /** An installed app, as much of it as an update needs. [signers] are the signing certificates, as strings. */
    data class OnPhone(val appId: String, val versionName: String?, val installer: String?, val signers: Set<String>)

    /** How an update may go in, or why it may not. */
    enum class Verdict {
        /** Folio installed it and the signer matches: Android updates it without asking. */
        SILENT,
        /** The same app and signer, but another installer put it there, so Android asks first. */
        ASK,
        /** The APK is some other app, or couldn't be read as one. */
        WRONG_APP,
        /** The APK isn't signed by the key the installed app is signed by. */
        WRONG_SIGNER,
    }

    /** A version in dpkg's order with semantic-version pre-releases, or null when it isn't one. */
    fun version(text: String?): DebVersion? {
        val trimmed = text?.trim()?.removePrefix("v")?.substringBefore('+')?.takeIf { it.isNotEmpty() } ?: return null
        return DebVersion.parse(trimmed.replaceFirst('-', '~'))
    }

    /**
     * True when [listing] is newer than [installed]. An installed version that can't be read is never "older": a
     * build Folio can't place is left alone rather than offered something that may be a step back.
     */
    fun isNewer(listing: DebVersion, installed: String?): Boolean {
        val have = version(installed) ?: return false
        val offered = version(listing.text) ?: return false
        return offered > have
    }

    /**
     * The installed app [listing] would update, or null when there is nothing to offer: it isn't an app of its own,
     * Android doesn't have it (or won't show it to Folio), the listing isn't newer, or the rules that keep a package
     * from being installed say no. Those are the same for an update as for a first install: a pulled listing, one
     * that takes the name of a package inside Folio, and one that needs a newer Folio are never offered.
     */
    fun offered(context: Context, listing: MarketEntry): OnPhone? {
        if (!MarketExternalApp.isExternal(listing.entry.manifest)) return null
        if (listing.revokedReason != null || listing.clash == MarketEntry.Impostor.BUILT_IN || listing.entry.needs.isNotEmpty()) {
            return null
        }
        val ids = listing.entry.manifest?.via.orEmpty().mapNotNull { it.appId }.distinct()
        return ids.firstNotNullOfOrNull { onPhone(context, it) }?.takeIf { isNewer(listing.entry.version, it.versionName) }
    }

    /** The app as Android has it, or null when Folio can't see it (no `<queries>` entry, or not installed). */
    fun onPhone(context: Context, appId: String): OnPhone? = runCatching {
        val pm = context.packageManager
        val info = pm.getPackageInfo(appId, PackageManager.GET_SIGNING_CERTIFICATES)
        OnPhone(
            appId = appId,
            versionName = info.versionName,
            installer = runCatching { pm.getInstallSourceInfo(appId).installingPackageName }.getOrNull(),
            signers = signers(info),
        )
    }.getOrNull()

    fun signers(info: PackageInfo?): Set<String> =
        info?.signingInfo?.apkContentsSigners?.map { it.toCharsString() }?.toSet().orEmpty()

    /**
     * What to do with an APK whose checksum already matched. [self] is Folio's own package name, which is Folio or
     * Folio Dev: each is the installer of record only for what it installed itself.
     */
    fun verdict(self: String, onPhone: OnPhone, archiveId: String?, archiveSigners: Set<String>): Verdict = when {
        archiveId == null || archiveId != onPhone.appId -> Verdict.WRONG_APP
        archiveSigners.isEmpty() || archiveSigners != onPhone.signers -> Verdict.WRONG_SIGNER
        onPhone.installer == self -> Verdict.SILENT
        else -> Verdict.ASK
    }

    /**
     * Reads the downloaded APK's package name and signers, from a copy in Folio's private cache that is deleted as
     * soon as it has been read. Android's archive reader wants a path, and nothing outside Folio can reach this one.
     */
    @Suppress("DEPRECATION")
    fun archive(context: Context, apk: ByteArray): PackageInfo? {
        val file = java.io.File(context.cacheDir, "market-update-check.apk")
        return try {
            file.writeBytes(apk)
            context.packageManager.getPackageArchiveInfo(file.path, PackageManager.GET_SIGNING_CERTIFICATES)
        } catch (_: Exception) {
            null
        } finally {
            file.delete()
        }
    }
}
