package com.mccal.folio

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Software Update (like iOS Settings › General › Software Update): checks GitHub Releases for a newer Folio, downloads
 * the APK, verifies its SHA-256 and that it's signed with the same key as the installed app, then hands it to Android's
 * package installer. The only requests are to GitHub's public releases API and download server: about once a day unless
 * updates are set to Manual, and when you check. Folio Dev builds update from new builds instead, so this is off for them.
 */
internal object SoftwareUpdate {
    private const val LATEST = "https://api.github.com/repos/McCal-Codes/folio/releases/latest"
    // GitHub's "latest" skips pre-releases, so the beta channel reads the recent list and takes the newest.
    private const val RECENT = "https://api.github.com/repos/McCal-Codes/folio/releases?per_page=15"
    /**
     * Supporters' betas live in a private repository, which GitHub won't show to an app with no credentials. This
     * worker is asked instead: it checks the supporter code's signature — the same check Folio makes offline — and
     * only then reads the private releases with its own token. Empty until the worker is deployed, and then Beta
     * Updates simply reads the public pre-releases as before.
     */
    internal const val BETA_BROKER = ""
    private const val PREFS = "software_update"
    // Legacy switches (0.5.1–0.6.0), read once to carry a choice over to [Mode].
    private const val AUTO = "auto"
    private const val LAST_CHECK = "lastCheck"
    private const val AUTO_INSTALL = "autoInstall"
    private const val BETA = "beta"
    private const val NOTIFIED_VERSION = "notifiedVersion"
    private const val MODE = "mode"
    private const val AUTO_UPDATED_FROM = "autoUpdatedFrom"
    private const val CHANNEL = "software_update"
    private const val DAY_MS = 24L * 60 * 60 * 1000

    data class Release(val version: String, val apkUrl: String, val sumsUrl: String?, val notesUrl: String,
        /** The release notes from GitHub (Markdown), and the APK's size in bytes. */
        val notes: String = "", val size: Long = 0L, val prerelease: Boolean = false)

    /**
     * Like iOS Automatic Updates. Automatic (the default) checks daily, downloads, and installs when the phone is idle,
     * since installing restarts Home. Notify only tells you. Manual only checks when you ask.
     */
    enum class Mode(@androidx.annotation.StringRes val label: Int) { AUTOMATIC(R.string.automatic), NOTIFY(R.string.notify_me), MANUAL(R.string.manual) }

    sealed interface Status {
        data object Idle : Status
        data object Checking : Status
        data object UpToDate : Status
        data class Available(val release: Release) : Status
        data class Downloading(val release: Release, val fraction: Float? = null) : Status
        /** Downloaded and verified; installs when the phone is idle (or tonight), or now if you tap. */
        data class Ready(val release: Release, val tonight: Boolean) : Status
        data object Installing : Status
        data class Failed(val message: String) : Status
    }

    val status = MutableStateFlow<Status>(Status.Idle)

    /** Update work outlives the Settings page and the activity, and only one check or install runs at a time. */
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)
    private val busy = java.util.concurrent.atomic.AtomicBoolean(false)

    fun startCheck(context: Context) = launchExclusive { check(context.applicationContext) }
    fun startInstall(context: Context, release: Release) = launchExclusive { downloadAndInstall(context.applicationContext, release) }
    fun startCheckIfDue(context: Context) = launchExclusive { checkIfDue(context.applicationContext) }

    private fun launchExclusive(block: suspend () -> Unit) {
        if (!busy.compareAndSet(false, true)) return
        scope.launch { try { block() } finally { busy.set(false) } }
    }

    fun supported(context: Context) = context.packageName == FOLIO_CLASSES

    /** The chosen mode. Earlier versions stored separate switches; a choice made there carries over. */
    fun mode(context: Context): Mode {
        val prefs = context.getSharedPreferences(PREFS, 0)
        prefs.getString(MODE, null)?.let { saved -> Mode.entries.firstOrNull { it.name == saved }?.let { return it } }
        return modeFromLegacy(prefs.takeIf { it.contains(AUTO) }?.getBoolean(AUTO, false), prefs.getBoolean(AUTO_INSTALL, false))
    }
    internal fun modeFromLegacy(autoCheck: Boolean?, autoInstall: Boolean): Mode = when {
        autoCheck == null -> Mode.AUTOMATIC
        !autoCheck -> Mode.MANUAL
        autoInstall -> Mode.AUTOMATIC
        else -> Mode.NOTIFY
    }
    fun setMode(context: Context, mode: Mode) {
        context.getSharedPreferences(PREFS, 0).edit().putString(MODE, mode.name).apply()
        SoftwareUpdateJob.schedule(context)
    }
    fun lastChecked(context: Context): Long = context.getSharedPreferences(PREFS, 0).getLong(LAST_CHECK, 0L)

    /** Like iOS Beta Updates: also offer GitHub pre-releases. Leaving keeps the installed beta until a newer public release. */
    fun beta(context: Context) = context.getSharedPreferences(PREFS, 0).getBoolean(BETA, false)

    /**
     * Where to actually look for an update. Normally the switch above decides; a development build can point this at
     * one channel or the other without touching the switch, so the update path can be walked as a stranger sees it.
     */
    internal fun betaChannel(context: Context): Boolean = when (Dev.channel(context)) {
        Dev.Channel.STABLE -> false
        Dev.Channel.BETA -> true
        Dev.Channel.DEFAULT -> beta(context)
    }
    fun setBeta(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS, 0).edit().putBoolean(BETA, on).apply()
        status.value = Status.Idle
    }

    fun canPostNotifications(context: Context) = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** One notification per new version, in its own "Software updates" channel the user can mute in Android settings. */
    private fun postAvailable(context: Context, release: Release) {
        val prefs = context.getSharedPreferences(PREFS, 0)
        if (mode(context) == Mode.MANUAL || !canPostNotifications(context) || prefs.getString(NOTIFIED_VERSION, null) == release.version) return
        val manager = context.getSystemService(android.app.NotificationManager::class.java)
        createChannel(context, manager)
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java)
            .setAction(android.content.Intent.ACTION_APPLICATION_PREFERENCES).putExtra(EXTRA_OPEN_UPDATE, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = android.app.Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("Folio ${release.version} is available")
            .setContentText(context.getString(R.string.tap_to_see_what_s_new_and_install_it))
            .setContentIntent(open).setAutoCancel(true).build()
        runCatching { manager.notify(NOTIFICATION_ID, notification) }
        prefs.edit().putString(NOTIFIED_VERSION, release.version).apply()
    }

    /** "Tap to finish updating": used when the install needs a confirmation and Folio isn't on screen. False if it can't be posted. */
    fun postConfirm(context: Context, confirm: Intent): Boolean {
        if (!canPostNotifications(context)) return false
        val manager = context.getSystemService(android.app.NotificationManager::class.java)
        createChannel(context, manager)
        val tap = PendingIntent.getActivity(context, 1, confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        runCatching { manager.notify(NOTIFICATION_ID, android.app.Notification.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(context.getString(R.string.finish_updating_folio)).setContentText(context.getString(R.string.tap_to_install_the_update)).setContentIntent(tap).setAutoCancel(true).build()) }
        return true
    }

    const val EXTRA_OPEN_UPDATE = "folio_open_software_update"
    /** Set when the update notification is tapped, so Settings opens straight to Software Update. */
    @Volatile var openRequested = false
    private const val NOTIFICATION_ID = 4101

    /** After an automatic update: one quiet "Folio was updated" notification, opening What's New. */
    fun afterUpdate(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, 0)
        val from = prefs.getString(AUTO_UPDATED_FROM, null) ?: return
        val now = installedVersion(context)
        if (!isNewer(now, from)) return
        prefs.edit().remove(AUTO_UPDATED_FROM).apply()
        File(context.cacheDir, "updates").deleteRecursively()
        if (!canPostNotifications(context)) return
        val manager = context.getSystemService(android.app.NotificationManager::class.java)
        createChannel(context, manager)
        val open = PendingIntent.getActivity(context, 2, Intent(context, MainActivity::class.java)
            .setAction(android.content.Intent.ACTION_APPLICATION_PREFERENCES).putExtra(EXTRA_OPEN_UPDATE, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        runCatching { manager.notify(NOTIFICATION_ID, android.app.Notification.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("Folio was updated to $now").setContentText(context.getString(R.string.tap_to_see_what_s_new))
            .setContentIntent(open).setAutoCancel(true).build()) }
    }

    private fun createChannel(context: Context, manager: android.app.NotificationManager) =
        manager.createNotificationChannel(android.app.NotificationChannel(CHANNEL, "Software updates", android.app.NotificationManager.IMPORTANCE_DEFAULT)
            .apply { description = context.getString(R.string.new_versions_of_folio) })

    fun installedVersion(context: Context): String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "0"

    /** 1.10.0 is newer than 1.9.2: numeric comparison part by part. */
    /**
     * Semantic versions: numbers part by part, and a pre-release comes before its release
     * (0.7.0-beta.1 < 0.7.0-beta.2 < 0.7.0).
     */
    fun isNewer(candidate: String, installed: String): Boolean {
        fun split(v: String) = v.removePrefix("v").split('-', limit = 2).let { it[0].split('.').map { n -> n.toIntOrNull() ?: 0 } to it.getOrNull(1) }
        val (coreA, preA) = split(candidate); val (coreB, preB) = split(installed)
        for (i in 0 until maxOf(coreA.size, coreB.size)) {
            val x = coreA.getOrElse(i) { 0 }; val y = coreB.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        if (preA == null || preB == null) return preA == null && preB != null
        val a = preA.split('.'); val b = preB.split('.')
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrNull(i) ?: return false; val y = b.getOrNull(i) ?: return true
            val nx = x.toIntOrNull(); val ny = y.toIntOrNull()
            val order = if (nx != null && ny != null) nx.compareTo(ny) else x.compareTo(y)
            if (order != 0) return order > 0
        }
        return false
    }

    /**
     * Where to ask for betas, and what to send: the broker's address and the code, or null when there's no broker
     * deployed or no code that carries beta access.
     */
    internal fun betaSource(broker: String, code: String?): Pair<String, String>? {
        val address = broker.trim().trimEnd('/')
        val credential = code?.trim().orEmpty()
        if (address.isEmpty() || credential.isEmpty()) return null
        if (!address.startsWith("https://")) return null  // a code is a credential; it doesn't travel in the clear
        return "$address/beta/releases" to credential
    }

    /** A published release with an APK, or null. */
    private fun releaseOf(json: JSONObject): Release? {
        val assets = json.optJSONArray("assets") ?: return null
        fun asset(predicate: (String) -> Boolean) = (0 until assets.length()).map { assets.getJSONObject(it) }
            .firstOrNull { predicate(it.getString("name")) }?.getString("browser_download_url")
        val apk = asset { it.endsWith(".apk") } ?: return null
        val size = (0 until assets.length()).map { assets.getJSONObject(it) }.firstOrNull { it.getString("name").endsWith(".apk") }?.optLong("size") ?: 0L
        return Release(json.getString("tag_name").removePrefix("v"), apk, asset { it == "SHA256SUMS.txt" }, json.optString("html_url"),
            notes = json.optString("body").take(20_000), size = size, prerelease = json.optBoolean("prerelease"))
    }

    /** Called when Folio comes to the front and by the daily job: checks at most once a day unless Manual. */
    private suspend fun checkIfDue(context: Context) {
        SoftwareUpdateJob.schedule(context)
        if (!supported(context) || mode(context) == Mode.MANUAL) return
        val prefs = context.getSharedPreferences(PREFS, 0)
        if (System.currentTimeMillis() - prefs.getLong(LAST_CHECK, 0) < DAY_MS) return
        check(context)
        val available = (status.value as? Status.Available)?.release ?: return
        if (mode(context) == Mode.AUTOMATIC) { if (download(context, available)) SoftwareUpdateJob.scheduleInstall(context, tonight = false) }
        else postAvailable(context, available)
    }

    /** Run by the daily job, off the main thread. */
    internal suspend fun backgroundCheck(context: Context) {
        if (busy.compareAndSet(false, true)) try { checkIfDue(context) } finally { busy.set(false) }
    }

    /** Run by the install job when the phone is idle: installs a verified download. */
    internal suspend fun backgroundInstall(context: Context) {
        if (!busy.compareAndSet(false, true)) return
        try {
            val ready = readyApk(context) ?: return
            installReady(context, ready.first, ready.second)
        } finally { busy.set(false) }
    }

    /** Download now; install when the phone is idle and charging (like iOS "Update Tonight"). */
    fun startUpdateTonight(context: Context, release: Release) = launchExclusive {
        val app = context.applicationContext
        if (download(app, release)) { SoftwareUpdateJob.scheduleInstall(app, tonight = true); status.value = Status.Ready(release, tonight = true) }
    }

    private suspend fun check(context: Context) {
        if (!supported(context)) return
        status.value = Status.Checking
        context.getSharedPreferences(PREFS, 0).edit().putLong(LAST_CHECK, System.currentTimeMillis()).apply()
        status.value = withContext(Dispatchers.IO) {
            runCatching {
                val list = { text: String -> org.json.JSONArray(text).let { a -> (0 until a.length()).map(a::getJSONObject) } }
                // A supporter's beta comes from the broker; if it can't be reached, the public pre-releases still can.
                val brokered = betaSource(BETA_BROKER, Supporter.storedText(context)
                    ?.takeIf { betaChannel(context) && Supporter.has(context, BetaCodes.SCOPE_BETA) })
                val candidates = when {
                    brokered != null -> runCatching { list(get(brokered.first, brokered.second)) }.getOrElse { list(get(RECENT)) }
                    betaChannel(context) -> list(get(RECENT))
                    else -> listOf(JSONObject(get(LATEST)))
                }
                val newest = candidates.filter { !it.optBoolean("draft") }.mapNotNull(::releaseOf)
                    .reduceOrNull { a, b -> if (isNewer(b.version, a.version)) b else a } ?: error(context.getString(R.string.no_release_has_an_apk))
                if (!isNewer(newest.version, installedVersion(context))) Status.UpToDate
                else readyApk(context)?.takeIf { it.first.version == newest.version }?.let { Status.Ready(newest, tonight = false) }
                    ?: Status.Available(newest)
            }.getOrElse { Status.Failed(context.getString(R.string.couldn_t_check_for_updates_check_your_co)) }
        }
    }

    /** Downloads, verifies and installs [release] now. Android shows its own confirmation when it needs one. */
    private suspend fun downloadAndInstall(context: Context, release: Release) {
        if (!download(context, release)) return
        readyApk(context)?.let { installReady(context, it.first, it.second) }
    }

    private fun updatesDir(context: Context) = File(context.filesDir, "updates")

    /** Downloads and verifies [release] into the updates folder. False (with Failed status) if anything's wrong. */
    private suspend fun download(context: Context, release: Release): Boolean {
        readyApk(context)?.takeIf { it.first.version == release.version }?.let { status.value = Status.Ready(release, tonight = false); return true }
        status.value = Status.Downloading(release)
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val dir = updatesDir(context).apply { deleteRecursively(); mkdirs() }
                val apk = File(dir, "Folio-${release.version}.apk.part")
                download(release.apkUrl, apk, release.size) { status.value = Status.Downloading(release, it) }
                release.sumsUrl?.let { url ->
                    val expected = get(url).lines().firstOrNull { it.trim().endsWith(".apk") }?.substringBefore(' ')?.trim()
                    require(expected != null && expected.equals(sha256(apk), ignoreCase = true)) { context.getString(R.string.the_download_didn_t_match_its_checksum) }
                }
                require(sameSigner(context, apk)) { context.getString(R.string.the_update_isn_t_signed_with_folio_s_key) }
                apk.renameTo(File(dir, "Folio-${release.version}.apk"))
                File(dir, "release.json").writeText(JSONObject().put("version", release.version).put("notes", release.notes.take(4000))
                    .put("notesUrl", release.notesUrl).toString())
            }
        }
        return result.fold({ status.value = Status.Ready(release, tonight = false); true },
            { updatesDir(context).deleteRecursively(); status.value = Status.Failed(it.message ?: context.getString(R.string.the_update_couldn_t_be_downloaded)); false })
    }

    /** A verified download that's newer than what's installed, with its release info. */
    private fun readyApk(context: Context): Pair<Release, File>? = runCatching {
        val dir = updatesDir(context)
        val info = JSONObject(File(dir, "release.json").readText())
        val version = info.getString("version")
        val apk = File(dir, "Folio-$version.apk").takeIf { it.exists() } ?: return null
        if (!isNewer(version, installedVersion(context))) { dir.deleteRecursively(); return null }
        Release(version, "", null, info.optString("notesUrl"), info.optString("notes")) to apk
    }.getOrNull()

    fun installReadyNow(context: Context) = launchExclusive {
        val app = context.applicationContext
        readyApk(app)?.let { installReady(app, it.first, it.second) }
    }

    private suspend fun installReady(context: Context, release: Release, apk: File) {
        val result = withContext(Dispatchers.IO) {
            runCatching {
                require(sameSigner(context, apk)) { context.getString(R.string.the_update_isn_t_signed_with_folio_s_key) }
                context.getSharedPreferences(PREFS, 0).edit().putString(AUTO_UPDATED_FROM, installedVersion(context)).apply()
                install(context, apk)
            }
        }
        status.value = result.fold({ Status.Installing }, { Status.Failed(it.message ?: context.getString(R.string.the_update_couldn_t_be_installed)) })
        SoftwareUpdateJob.cancelInstall(context)
    }

    private fun get(url: String, code: String? = null): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.setRequestProperty("Accept", "application/vnd.github+json")
        c.setRequestProperty("User-Agent", "Folio")
        // The supporter code is the only credential the broker wants, and it goes in a header rather than the address.
        if (code != null) c.setRequestProperty("Authorization", "Bearer $code")
        c.connectTimeout = 10_000; c.readTimeout = 15_000
        return c.inputStream.bufferedReader().use { it.readText() }.also { c.disconnect() }
    }

    private fun download(url: String, target: File, expectedSize: Long, onProgress: (Float?) -> Unit) {
        val c = URL(url).openConnection() as HttpURLConnection
        c.setRequestProperty("User-Agent", "Folio")
        c.connectTimeout = 10_000; c.readTimeout = 60_000; c.instanceFollowRedirects = true
        try {
            val total = c.contentLengthLong.takeIf { it > 0 } ?: expectedSize
            var done = 0L; var reported = -1
            c.inputStream.use { input -> target.outputStream().use { out ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buffer); if (n < 0) break
                    out.write(buffer, 0, n); done += n
                    val percent = if (total > 0) (done * 100 / total).toInt() else -1
                    if (percent != reported) { reported = percent; onProgress(if (total > 0) done.toFloat() / total else null) }
                }
            } }
        } finally { c.disconnect() }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input -> val buf = ByteArray(64 * 1024); while (true) { val n = input.read(buf); if (n < 0) break; digest.update(buf, 0, n) } }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    @Suppress("DEPRECATION")
    private fun sameSigner(context: Context, apk: File): Boolean {
        val pm = context.packageManager
        val archive = pm.getPackageArchiveInfo(apk.path, PackageManager.GET_SIGNING_CERTIFICATES) ?: return false
        if (archive.packageName != context.packageName) return false
        val installed = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        val a = archive.signingInfo?.apkContentsSigners?.map { it.toCharsString() }?.toSet().orEmpty()
        val b = installed.signingInfo?.apkContentsSigners?.map { it.toCharsString() }?.toSet().orEmpty()
        return a.isNotEmpty() && a == b
    }

    private fun install(context: Context, apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            // Once Folio installed itself, Android 12+ can update it without asking again.
            setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val sessionId = installer.createSession(params)
        try {
            installer.openSession(sessionId).use { session ->
                apk.inputStream().use { input -> session.openWrite("folio.apk", 0, apk.length()).use { out -> input.copyTo(out); session.fsync(out) } }
                val intent = Intent(context, SoftwareUpdateReceiver::class.java)
                // Mutable so the installer can add its status extras; the intent is explicit to Folio's own receiver.
                val pending = PendingIntent.getBroadcast(context, sessionId, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
                session.commit(pending.intentSender)
            }
        } catch (error: Exception) {
            runCatching { installer.abandonSession(sessionId) }
            throw error
        }
    }
}

/** Android's installer reports back here; when it needs the user's OK, its confirmation screen is shown. */
class SoftwareUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION") val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                // Android may block starting a screen while Folio isn't in front, so then ask with a notification.
                if (FolioForeground.visible.value) runCatching { context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                else if (!SoftwareUpdate.postConfirm(context, confirm))
                    SoftwareUpdate.status.value = SoftwareUpdate.Status.Failed(context.getString(R.string.the_update_is_downloaded_open_software_u))
            }
            PackageInstaller.STATUS_SUCCESS -> Unit
            else -> SoftwareUpdate.status.value = SoftwareUpdate.Status.Failed(
                intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)?.let { "The update wasn't installed: $it" } ?: "The update wasn't installed.")
        }
    }
}

/**
 * Background work for Software Update, through JobScheduler so Android picks a good moment: a daily check when there's
 * a network, and installing a downloaded update when the phone is idle (and charging, for Update Tonight).
 */
class SoftwareUpdateJob : android.app.job.JobService() {
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)

    override fun onStartJob(params: android.app.job.JobParameters): Boolean {
        scope.launch {
            try {
                if (params.jobId == INSTALL) SoftwareUpdate.backgroundInstall(applicationContext) else SoftwareUpdate.backgroundCheck(applicationContext)
            } finally { jobFinished(params, false) }
        }
        return true
    }

    override fun onStopJob(params: android.app.job.JobParameters) = true

    companion object {
        private const val CHECK = 4102
        private const val INSTALL = 4103

        private fun scheduler(context: Context) = context.getSystemService(android.app.job.JobScheduler::class.java)
        private fun component(context: Context) = android.content.ComponentName(context, SoftwareUpdateJob::class.java)

        /** The daily check, unless updates are Manual or this build doesn't use them. */
        fun schedule(context: Context) {
            val jobs = scheduler(context) ?: return
            if (!SoftwareUpdate.supported(context) || SoftwareUpdate.mode(context) == SoftwareUpdate.Mode.MANUAL) {
                jobs.cancel(CHECK); jobs.cancel(INSTALL); return
            }
            if (jobs.getPendingJob(CHECK) != null) return
            runCatching { jobs.schedule(android.app.job.JobInfo.Builder(CHECK, component(context))
                .setRequiredNetworkType(android.app.job.JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(24L * 60 * 60 * 1000, 6L * 60 * 60 * 1000).build()) }
        }

        /** Installs the downloaded update once the phone is idle (and charging, when [tonight]). */
        fun scheduleInstall(context: Context, tonight: Boolean) {
            runCatching { scheduler(context)?.schedule(android.app.job.JobInfo.Builder(INSTALL, component(context))
                .setRequiresDeviceIdle(true).setRequiresCharging(tonight).build()) }
        }

        fun cancelInstall(context: Context) { scheduler(context)?.cancel(INSTALL) }
    }
}
