package com.mccal.folio

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import com.mccal.folio.market.IndexPackage
import com.mccal.folio.market.Source
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Installing an app from the Market, the way Sileo does it: the store downloads, checks, and hands it over,
 * rather than sending you to somebody else's app and hoping you come back.
 *
 * **Off by default, and any source the user added may offer an app** (McCal, 2026-09-20). That is Cydia's and
 * Sileo's bargain, said plainly: you chose the source, so you chose what it may hand you.
 *
 * Four things still hold, and the setting's own words say so:
 *
 * - The index that named the app is **signed** by the source, and its key was pinned when it was added, so the
 *   listing is really that source's.
 * - The bytes must match the **checksum** the index promised, checked before the file is written anywhere
 *   Android can reach, so nothing can be swapped in on the way.
 * - **Android asks** before a first install, so the system's own install screen appears with the app's real name
 *   on it. An update to an app Folio itself installed, signed by the same key, is the one exception
 *   ([MarketAppUpdate]).
 * - It is **off until someone turns it on**.
 *
 * What is not protected, and cannot be: Folio can't tell whether the app itself is any good. An update has an
 * anchor, the key the copy on the phone is signed with, and Folio insists on it the way Software Update does; a
 * first install has none. The source vouches for it, and that is the whole of it.
 */
internal object MarketApkInstall {

    /**
     * Where an install has got to, for the store's banner.
     *
     * [Handed] is not the end of it: Android's install screen comes next, and whether the app arrived is only known
     * when [MarketInstallReceiver] hears back - which can be a while, and can be after the store was closed. So the
     * store follows this rather than the call that started the install.
     */
    sealed interface Status {
        data object Idle : Status
        data class Working(val name: String) : Status
        /**
         * Given to Android, which is now asking. Nothing to say here: its own screen is in front. [updating] when it
         * replaces a copy already on the phone, which may go in without a screen at all.
         */
        data class Handed(val name: String, val updating: Boolean = false) : Status
        /** Android installed it. [appId] is what it turned out to be, which Folio never told it for a first install. */
        data class Installed(val name: String, val appId: String?, val updated: Boolean = false) : Status
        data class Failed(val message: String) : Status
    }

    val status = MutableStateFlow<Status>(Status.Idle)

    /**
     * The store has said what happened, so it isn't said again the next time the store opens. An outcome nobody
     * has read stays, which is how an install that finished while the Market was closed still gets announced.
     */
    fun seen() {
        val now = status.value
        if (now is Status.Installed || now is Status.Failed) status.compareAndSet(now, Status.Idle)
    }

    /**
     * Whether Folio may install [entry] itself: the setting is on, and the listing has an APK with a checksum to
     * check it against. A listing with no checksum is never installed, whatever the setting says - there would
     * be nothing to compare the download with.
     */
    fun canInstall(source: Source, entry: IndexPackage, on: Boolean, revoked: Boolean = false): Boolean =
        // Not a local source: it's unsigned plain http, so any app on the phone listening on that port could name an
        // APK and its checksum. Not a pulled listing either, whatever the button showed.
        on && !revoked && entry.url != null && entry.sha256 != null &&
            source.kind != Source.Kind.BUILT_IN && source.kind != Source.Kind.LOCAL_DEV

    /**
     * Downloads the APK, checks it against the checksum the index promised, and hands it to Android.
     *
     * The bytes are checked before the file is written anywhere Android can reach, so a source that serves
     * something other than what it listed never gets as far as the install screen.
     *
     * With [update], this replaces the copy already on the phone, and two more things must hold first: the APK is
     * that app, and it is signed by the same key ([MarketAppUpdate.verdict]). When Folio is also that app's installer
     * of record, Android is asked to update it without a screen.
     */
    suspend fun install(
        context: Context,
        name: String,
        entry: IndexPackage,
        update: MarketAppUpdate.OnPhone? = null,
        fetch: suspend (String, (Long, Long) -> Unit) -> ByteArray?,
    ): Boolean {
        val url = entry.url ?: return fail(context, R.string.that_listing_has_no_app_to_download)
        val expected = entry.sha256 ?: return fail(context, R.string.that_listing_has_no_app_to_download)
        status.value = Status.Working(name)
        val bytes = fetch(url) { read, total -> MarketWork.downloaded(read, total) }
            ?: return fail(context, R.string.folio_couldn_t_download_that_app)
        // Hashing and copying up to 20 MB happen off the main thread: MarketWork runs on it, and only the fetch moved.
        val matches = withContext(Dispatchers.Default) { sha256(bytes).equals(expected, ignoreCase = true) }
        // The checksum already covers the size; this is the listing's own number, checked in its own right.
        if (!matches || (entry.size != null && bytes.size != entry.size)) {
            return fail(context, R.string.that_app_didn_t_match_what_its_source)
        }
        val silent = update?.let { onPhone ->
            val archive = withContext(Dispatchers.IO) { MarketAppUpdate.archive(context, bytes) }
            when (MarketAppUpdate.verdict(context.packageName, onPhone, archive?.packageName, MarketAppUpdate.signers(archive))) {
                MarketAppUpdate.Verdict.SILENT -> true
                MarketAppUpdate.Verdict.ASK -> false
                MarketAppUpdate.Verdict.WRONG_APP -> return fail(context, R.string.that_update_is_a_different_app)
                MarketAppUpdate.Verdict.WRONG_SIGNER ->
                    return fail(context, context.getString(R.string.that_update_isn_t_signed_by_the_same, name))
            }
        } ?: false
        MarketWork.applying()
        // Runs inside MarketWork, whose scope nothing cancels: every failure here is one to tell the user about.
        return caught("Market: writing an app for Android", rethrowCancellation = false) {
            withContext(Dispatchers.IO) { hand(context, bytes, update?.appId, silent) }
            status.value = Status.Handed(name, updating = update != null)
            true
        }.getOrElse {
            fail(context, R.string.folio_couldn_t_hand_that_app_to_android)
        }
    }

    private fun sha256(bytes: ByteArray) =
        java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun fail(context: Context, message: Int): Boolean = fail(context, context.getString(message))

    private fun fail(context: Context, message: String): Boolean {
        status.value = Status.Failed(message)
        return false
    }

    /**
     * Written straight into the install session: the checked bytes, with no copy on disk anyone could swap.
     *
     * A first install names no package and asks: this is somebody else's app, and Folio does not get to say which
     * package these bytes claim to be. An update names [appId], so Android refuses bytes that turn out to be any
     * other app, and asks only when [silent] is false.
     */
    private fun hand(context: Context, apk: ByteArray, appId: String? = null, silent: Boolean = false) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            if (appId != null) {
                setAppPackageName(appId)
                setRequireUserAction(
                    if (silent) PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED
                    else PackageInstaller.SessionParams.USER_ACTION_REQUIRED,
                )
            }
        }
        val sessionId = installer.createSession(params)
        try {
            installer.openSession(sessionId).use { session ->
                session.openWrite("package.apk", 0, apk.size.toLong()).use { out ->
                    out.write(apk)
                    session.fsync(out)
                }
                val pending = PendingIntent.getBroadcast(
                    context, sessionId, Intent(context, MarketInstallReceiver::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                )
                session.commit(pending.intentSender)
            }
        } catch (error: Exception) {
            runCatching { installer.abandonSession(sessionId) }
            throw error
        }
    }
}

/** Android's answer to an install Folio handed over: the confirm screen, then whether it worked. */
class MarketInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION") val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                caught("Market: showing Android's install prompt") { context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
            PackageInstaller.STATUS_SUCCESS -> {
                val handed = MarketApkInstall.status.value as? MarketApkInstall.Status.Handed
                MarketApkInstall.status.value = MarketApkInstall.Status.Installed(
                    name = handed?.name ?: intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME).orEmpty(),
                    appId = intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME),
                    updated = handed?.updating == true,
                )
            }
            else -> MarketApkInstall.status.value = MarketApkInstall.Status.Failed(
                intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                    ?.let { context.getString(R.string.android_didn_t_install_it_1_s, it) }
                    ?: context.getString(R.string.android_didn_t_install_that_app),
            )
        }
    }
}
