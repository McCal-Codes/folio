package com.mccal.folio

import android.content.pm.PackageInfo
import android.content.pm.Signature
import java.security.MessageDigest

/**
 * Who a downloaded APK says it is: its package, its versionCode, and the keys it is signed with, read by Android's
 * own archive reader from the same bytes that go to the installer.
 *
 * Everything that decides is here and takes plain values, so it can be tested without a phone. [read] is the only
 * part that touches Android.
 *
 * **Signers are SHA-256 digests of the certificate**, lowercase hex: the number `apksigner verify --print-certs`
 * prints as "certificate SHA-256 digest", and the one a listing's `signerSha256` names.
 *
 * **Key rotation is allowed, the way Android allows it.** An APK signed with a rotated key (APK Signature Scheme v3)
 * carries the old keys it is allowed to replace, oldest first. Comparing only the current signers would call every
 * rotation a stranger, so a single-signer APK is judged by its lineage instead. An APK with several signers can't
 * rotate (Android ignores the lineage for those), so its signers have to match exactly.
 */
internal object ApkIdentity {

    /**
     * An APK's keys. [signers] are the current ones (`apkContentsSigners`); [history] is the lineage, oldest first and
     * the current key last (`signingCertificateHistory`), and is empty when [multiple].
     */
    data class Signing(val multiple: Boolean, val signers: Set<String>, val history: List<String> = emptyList()) {
        /** The key it is signed with now, for a single signer: the end of the lineage, or the one signer if there's none. */
        val newest: String? get() = if (multiple) null else history.lastOrNull() ?: signers.singleOrNull()
    }

    /** What the archive reader found. */
    data class Archive(val appId: String, val versionCode: Long, val signing: Signing)

    /** Why an APK is not the one that was listed. */
    enum class Refusal {
        /** Android couldn't read it as an app. */
        UNREADABLE,
        /** It is some other package. */
        WRONG_APP,
        /** Its versionCode is lower than the listing promised. */
        TOO_OLD,
        /** It isn't signed by a key the listing (or the copy on the phone) names. */
        WRONG_SIGNER,
    }

    /**
     * True when [apk] is signed by a key in [trusted].
     *
     * Several signers: the set must be exactly [trusted]. One signer: some key in its lineage must be listed, and the
     * newest must be. A listing that names only the old key therefore refuses a rotated APK until it names the new
     * one, which is the point: the source vouches for the key the app is signed with now.
     */
    fun signedBy(apk: Signing, trusted: Set<String>): Boolean {
        if (trusted.isEmpty() || apk.signers.isEmpty()) return false
        if (apk.multiple) return apk.signers == trusted
        val newest = apk.newest ?: return false
        val lineage = apk.history.ifEmpty { listOf(newest) }
        return lineage.any { it in trusted } && newest in trusted
    }

    /**
     * True when [apk] may replace an app signed as [installed]: Android's own rule, checked before its screen asks.
     * Several signers on either side must match exactly; otherwise the installed key must be in the APK's lineage,
     * so a rotated key that names the old one as its past is accepted.
     */
    fun descendsFrom(apk: Signing, installed: Signing): Boolean {
        if (apk.signers.isEmpty() || installed.signers.isEmpty()) return false
        if (apk.multiple || installed.multiple) return apk.multiple && installed.multiple && apk.signers == installed.signers
        val current = installed.newest ?: return false
        return current in apk.history.ifEmpty { apk.signers.toList() }
    }

    /**
     * Whether [archive] is the app a listing named: one of [appIds], at least [versionCode] when the listing gives one,
     * and signed by one of [signers]. Null when it is.
     */
    fun check(archive: Archive?, appIds: Set<String>, versionCode: Long?, signers: Set<String>): Refusal? = when {
        archive == null -> Refusal.UNREADABLE
        archive.appId !in appIds -> Refusal.WRONG_APP
        versionCode != null && archive.versionCode < versionCode -> Refusal.TOO_OLD
        !signedBy(archive.signing, signers) -> Refusal.WRONG_SIGNER
        else -> null
    }

    /** The keys in [info], read with `GET_SIGNING_CERTIFICATES`. Empty when Android gave none. */
    fun signing(info: PackageInfo?): Signing {
        val signingInfo = info?.signingInfo ?: return Signing(multiple = false, signers = emptySet())
        val multiple = signingInfo.hasMultipleSigners()
        val signers = signingInfo.apkContentsSigners.orEmpty().map(::digest).toSet()
        val history = if (multiple) emptyList() else signingInfo.signingCertificateHistory.orEmpty().map(::digest)
        return Signing(multiple, signers, history)
    }

    /** [info] as an [Archive], or null when Android couldn't read it. */
    fun read(info: PackageInfo?): Archive? {
        val appId = info?.packageName ?: return null
        return Archive(appId, info.longVersionCode, signing(info))
    }

    fun digest(cert: Signature): String = sha256Hex(cert.toByteArray())

    /** SHA-256 of [bytes], lowercase hex: the form a listing's `signerSha256` is written in. */
    fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
