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
 * package installer. Nothing is sent anywhere until you check (or turn on automatic checks); the only request is to
 * GitHub's public releases API. Folio Dev builds update from Android Studio instead, so this is off for them.
 */
internal object SoftwareUpdate {
    private const val LATEST = "https://api.github.com/repos/McCal-Codes/folio/releases/latest"
    // GitHub's "latest" skips pre-releases, so the beta channel reads the recent list and takes the newest.
    private const val RECENT = "https://api.github.com/repos/McCal-Codes/folio/releases?per_page=15"
    private const val PREFS = "software_update"
    private const val AUTO = "auto"
    private const val LAST_CHECK = "lastCheck"
    private const val AUTO_INSTALL = "autoInstall"
    private const val NOTIFY = "notify"
    private const val BETA = "beta"
    private const val NOTIFIED_VERSION = "notifiedVersion"
    private const val CHANNEL = "software_update"
    private const val DAY_MS = 24L * 60 * 60 * 1000

    data class Release(val version: String, val apkUrl: String, val sumsUrl: String?, val notesUrl: String)

    sealed interface Status {
        data object Idle : Status
        data object Checking : Status
        data object UpToDate : Status
        data class Available(val release: Release) : Status
        data class Downloading(val release: Release) : Status
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
    fun autoCheck(context: Context) = context.getSharedPreferences(PREFS, 0).getBoolean(AUTO, false)
    fun setAutoCheck(context: Context, on: Boolean) = context.getSharedPreferences(PREFS, 0).edit().putBoolean(AUTO, on).apply()
    /** Like iOS "Install iOS Updates": after a daily check finds one, download and install it too. */
    /** Post a notification when a daily check finds an update (off until the user turns it on). */
    fun notify(context: Context) = context.getSharedPreferences(PREFS, 0).getBoolean(NOTIFY, false)
    fun setNotify(context: Context, on: Boolean) = context.getSharedPreferences(PREFS, 0).edit().putBoolean(NOTIFY, on).apply()

    /** Like iOS Beta Updates: also offer GitHub pre-releases. Leaving keeps the installed beta until a newer public release. */
    fun beta(context: Context) = context.getSharedPreferences(PREFS, 0).getBoolean(BETA, false)
    fun setBeta(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS, 0).edit().putBoolean(BETA, on).apply()
        status.value = Status.Idle
    }

    fun canPostNotifications(context: Context) = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** One notification per new version, in its own "Software updates" channel the user can mute in Android settings. */
    private fun postAvailable(context: Context, release: Release) {
        val prefs = context.getSharedPreferences(PREFS, 0)
        if (!notify(context) || !canPostNotifications(context) || prefs.getString(NOTIFIED_VERSION, null) == release.version) return
        val manager = context.getSystemService(android.app.NotificationManager::class.java)
        manager.createNotificationChannel(android.app.NotificationChannel(CHANNEL, "Software updates", android.app.NotificationManager.IMPORTANCE_DEFAULT)
            .apply { description = "When a new version of Folio is available" })
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java)
            .setAction(android.content.Intent.ACTION_APPLICATION_PREFERENCES).putExtra(EXTRA_OPEN_UPDATE, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = android.app.Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("Folio ${release.version} is available")
            .setContentText("Tap to see what's new and install it.")
            .setContentIntent(open).setAutoCancel(true).build()
        runCatching { manager.notify(NOTIFICATION_ID, notification) }
        prefs.edit().putString(NOTIFIED_VERSION, release.version).apply()
    }

    /** "Tap to finish updating": used when the install needs a confirmation and Folio isn't on screen. False if it can't be posted. */
    fun postConfirm(context: Context, confirm: Intent): Boolean {
        if (!canPostNotifications(context)) return false
        val manager = context.getSystemService(android.app.NotificationManager::class.java)
        manager.createNotificationChannel(android.app.NotificationChannel(CHANNEL, "Software updates", android.app.NotificationManager.IMPORTANCE_DEFAULT))
        val tap = PendingIntent.getActivity(context, 1, confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        runCatching { manager.notify(NOTIFICATION_ID, android.app.Notification.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("Finish updating Folio").setContentText("Tap to install the update.").setContentIntent(tap).setAutoCancel(true).build()) }
        return true
    }

    const val EXTRA_OPEN_UPDATE = "folio_open_software_update"
    /** Set when the update notification is tapped, so Settings opens straight to Software Update. */
    @Volatile var openRequested = false
    private const val NOTIFICATION_ID = 4101

    fun autoInstall(context: Context) = context.getSharedPreferences(PREFS, 0).getBoolean(AUTO_INSTALL, false)
    fun setAutoInstall(context: Context, on: Boolean) = context.getSharedPreferences(PREFS, 0).edit().putBoolean(AUTO_INSTALL, on).apply()

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

    /** A published release with an APK, or null. */
    private fun releaseOf(json: JSONObject): Release? {
        val assets = json.optJSONArray("assets") ?: return null
        fun asset(predicate: (String) -> Boolean) = (0 until assets.length()).map { assets.getJSONObject(it) }
            .firstOrNull { predicate(it.getString("name")) }?.getString("browser_download_url")
        val apk = asset { it.endsWith(".apk") } ?: return null
        return Release(json.getString("tag_name").removePrefix("v"), apk, asset { it == "SHA256SUMS.txt" }, json.optString("html_url"))
    }

    /** Called when Folio comes to the front: checks at most once a day, only if automatic checks are on. */
    private suspend fun checkIfDue(context: Context) {
        if (!supported(context) || !autoCheck(context)) return
        val prefs = context.getSharedPreferences(PREFS, 0)
        if (System.currentTimeMillis() - prefs.getLong(LAST_CHECK, 0) < DAY_MS) return
        // Recorded before the request, so being offline doesn't retry on every trip Home.
        prefs.edit().putLong(LAST_CHECK, System.currentTimeMillis()).apply()
        check(context)
        val available = (status.value as? Status.Available)?.release ?: return
        if (autoInstall(context)) downloadAndInstall(context, available) else postAvailable(context, available)
    }

    private suspend fun check(context: Context) {
        if (!supported(context)) return
        status.value = Status.Checking
        status.value = withContext(Dispatchers.IO) {
            runCatching {
                val candidates = if (beta(context)) org.json.JSONArray(get(RECENT)).let { list -> (0 until list.length()).map(list::getJSONObject) }
                    else listOf(JSONObject(get(LATEST)))
                val newest = candidates.filter { !it.optBoolean("draft") }.mapNotNull(::releaseOf)
                    .reduceOrNull { a, b -> if (isNewer(b.version, a.version)) b else a } ?: error("No release has an APK.")
                if (isNewer(newest.version, installedVersion(context))) Status.Available(newest) else Status.UpToDate
            }.getOrElse { Status.Failed("Couldn't check for updates. Check your connection and try again.") }
        }
    }

    /** Downloads, verifies and installs [release]. Android shows its own confirmation when it needs one. */
    private suspend fun downloadAndInstall(context: Context, release: Release) {
        status.value = Status.Downloading(release)
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val dir = File(context.cacheDir, "updates").apply { deleteRecursively(); mkdirs() }
                val apk = File(dir, "Folio-${release.version}.apk")
                download(release.apkUrl, apk)
                release.sumsUrl?.let { url ->
                    val expected = get(url).lines().firstOrNull { it.trim().endsWith(".apk") }?.substringBefore(' ')?.trim()
                    require(expected != null && expected.equals(sha256(apk), ignoreCase = true)) { "The download didn't match its checksum." }
                }
                require(sameSigner(context, apk)) { "The update isn't signed with Folio's key, so it wasn't installed." }
                install(context, apk)
            }
        }
        status.value = result.fold({ Status.Installing }, { Status.Failed(it.message ?: "The update couldn't be installed.") })
    }

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.setRequestProperty("Accept", "application/vnd.github+json")
        c.setRequestProperty("User-Agent", "Folio")
        c.connectTimeout = 10_000; c.readTimeout = 15_000
        return c.inputStream.bufferedReader().use { it.readText() }.also { c.disconnect() }
    }

    private fun download(url: String, target: File) {
        val c = URL(url).openConnection() as HttpURLConnection
        c.setRequestProperty("User-Agent", "Folio")
        c.connectTimeout = 10_000; c.readTimeout = 60_000; c.instanceFollowRedirects = true
        c.inputStream.use { input -> target.outputStream().use { input.copyTo(it) } }
        c.disconnect()
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
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
                    SoftwareUpdate.status.value = SoftwareUpdate.Status.Failed("The update is downloaded. Open Software Update and tap Install to finish.")
            }
            PackageInstaller.STATUS_SUCCESS -> Unit
            else -> SoftwareUpdate.status.value = SoftwareUpdate.Status.Failed(
                intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)?.let { "The update wasn't installed: $it" } ?: "The update wasn't installed.")
        }
    }
}
