package com.mccal.folio

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/*
 * Folio Dev's way to TEST the supporter beta and switch between the two apps. It is not the dev source: the beta comes
 * from the supporter worker with the person's own supporter code, through the same Software Update primitives the
 * daily Folio uses, and the dev code and key are never involved. It is kept apart from the dev channel on purpose:
 *   - the beta code goes only to SoftwareUpdate.BETA_BROKER, the dev code only to DevSource.WORKER;
 *   - the install target is com.mccal.folio (release-signed), so Android shows its own confirmation, and Folio asks first;
 *   - nothing here runs in the background, on start-up, or without a tap.
 */

/** A confirmation is a separate step: nothing installs over the daily Folio until [confirm] follows an [ask]. */
internal class DevBetaGate {
    private var pending: String? = null

    fun ask(version: String) { pending = version }

    /** The version that was asked about, exactly once; null when nothing was asked or it was already confirmed or cancelled. */
    fun confirm(): String? = pending.also { pending = null }

    fun cancel() { pending = null }

    val asking: String? get() = pending
}

/** The checks between a beta download and Android's installer. Pure, so each refusal is tested on its own. */
internal object DevBetaCheck {
    fun archive(
        archivePackage: String?, archiveSigners: Set<String>, installedSigners: Set<String>,
        archiveVersionCode: Long, installedVersionCode: Long, checksumMatches: Boolean,
    ): DevRefusal? = when {
        !checksumMatches -> DevRefusal.WRONG_HASH
        archivePackage != FOLIO_CLASSES -> DevRefusal.WRONG_PACKAGE
        archiveSigners.isEmpty() || archiveSigners != installedSigners -> DevRefusal.WRONG_SIGNER
        archiveVersionCode < installedVersionCode -> DevRefusal.OLDER_VERSION
        else -> null
    }
}

internal object DevBeta {
    sealed interface State {
        data object Idle : State
        data object NoCode : State
        data object Checking : State
        data class Found(val release: SoftwareUpdate.Release, val newer: Boolean) : State
        data class Asking(val release: SoftwareUpdate.Release) : State
        data class Downloading(val fraction: Float?) : State
        data object Installing : State
        data class Refused(val reason: DevRefusal) : State
        data object Failed : State
    }

    val state = MutableStateFlow<State>(State.Idle)
    internal val gate = DevBetaGate()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val busy = AtomicBoolean(false)

    private fun launchExclusive(block: () -> Unit) {
        if (!busy.compareAndSet(false, true)) return
        scope.launch { try { block() } finally { busy.set(false) } }
    }

    /** The daily Folio, or null when it is not installed (or this build cannot see it). */
    @Suppress("DEPRECATION")
    fun installedFolio(context: Context): PackageInfo? =
        runCatching { context.packageManager.getPackageInfo(FOLIO_CLASSES, PackageManager.GET_SIGNING_CERTIFICATES) }.getOrNull()

    /** Asks the real beta channel with the person's supporter code. A tap, never a schedule. */
    fun check(context: Context) = launchExclusive {
        val app = context.applicationContext
        if (!SoftwareUpdate.betaSourceReady(app)) { state.value = State.NoCode; return@launchExclusive }
        state.value = State.Checking
        state.value = runCatching {
            val release = SoftwareUpdate.newestRelease(app, beta = true)
            val installed = installedFolio(app)?.versionName
            State.Found(release, newer = installed == null || SoftwareUpdate.isNewer(release.version, installed))
        }.getOrElse { State.Failed }
    }

    /** Opens the confirmation. Nothing is downloaded or installed yet. */
    fun ask(release: SoftwareUpdate.Release) { gate.ask(release.version); state.value = State.Asking(release) }

    fun cancel() {
        gate.cancel()
        (state.value as? State.Asking)?.let { state.value = State.Found(it.release, newer = true) }
    }

    /** Runs only after [ask]: downloads, checks, and hands the file to Android's installer, which asks as well. */
    fun confirm(context: Context) {
        val asked = state.value as? State.Asking ?: return
        if (gate.confirm() != asked.release.version) return
        launchExclusive {
            val app = context.applicationContext
            state.value = runCatching { downloadAndInstall(app, asked.release) }.getOrElse { State.Failed }
        }
    }

    private fun downloadAndInstall(context: Context, release: SoftwareUpdate.Release): State {
        val installed = installedFolio(context) ?: return State.Failed
        val code = SoftwareUpdate.brokerCode(context)
        val dir = File(context.filesDir, "updates-dev-beta").apply { deleteRecursively(); mkdirs() }
        val part = File(dir, "Folio-${release.version.replace(Regex("[^A-Za-z0-9._-]"), "_")}.apk.part")
        try {
            // The beta code goes only to the beta worker's own addresses (downloadCredential), never to GitHub or the dev worker.
            SoftwareUpdate.download(release.apkUrl, part, release.size, SoftwareUpdate.downloadCredential(release.apkUrl, SoftwareUpdate.BETA_BROKER, code),
                maxBytes = if (release.size > 0) release.size else Long.MAX_VALUE) { state.value = State.Downloading(it) }
            val expected = release.sumsUrl?.let { url ->
                SoftwareUpdate.fetch(url, SoftwareUpdate.downloadCredential(url, SoftwareUpdate.BETA_BROKER, code))
                    .lines().firstOrNull { it.trim().endsWith(".apk") }?.substringBefore(' ')?.trim()
            }
            val sumsMatch = expected != null && expected.equals(SoftwareUpdate.sha256(part), ignoreCase = true)
            val pm = context.packageManager
            @Suppress("DEPRECATION")
            val archive = pm.getPackageArchiveInfo(part.path, PackageManager.GET_SIGNING_CERTIFICATES)
            fun signers(info: PackageInfo?) = info?.signingInfo?.apkContentsSigners?.map { it.toCharsString() }?.toSet().orEmpty()
            val refusal = DevBetaCheck.archive(archive?.packageName, signers(archive), signers(installed),
                archive?.longVersionCode ?: 0L, installed.longVersionCode, sumsMatch)
            if (refusal != null) { dir.deleteRecursively(); return State.Refused(refusal) }
            val apk = File(dir, "Folio-beta.apk")
            check(part.renameTo(apk))
            state.value = State.Installing
            SoftwareUpdate.install(context, apk, FOLIO_CLASSES)
            return State.Installing
        } catch (error: Exception) {
            dir.deleteRecursively()
            throw error
        }
    }

    /** A launcher intent for [packageName], or null when it is not installed (the button is then hidden). */
    fun openIntent(context: Context, packageName: String): Intent? {
        val pm = context.packageManager
        val intent = pm.getLaunchIntentForPackage(packageName)
            ?: Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(packageName).takeIf { pm.resolveActivity(it, 0) != null }
        return intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
