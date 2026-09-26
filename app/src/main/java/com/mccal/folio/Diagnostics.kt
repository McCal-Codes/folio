package com.mccal.folio

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.mccal.folio.market.InstallResult
import com.mccal.folio.market.PackageInstaller

/**
 * What happened before a problem, kept only on the phone (shared only if you choose to): Android's own record of why
 * Folio stopped (freezes, native crashes, being closed for memory), a note when the phone restarted while Folio was on
 * screen, and a short trail of recent events. No notifications, app names you haven't chosen, or anything you typed.
 */
internal object Diagnostics {
    private const val PREFS = "diagnostics"
    private const val LAST_EXIT = "lastExitTimestamp"
    private const val BOOT_COUNT = "bootCount"
    private const val LAST_SEEN = "lastSeenWallTime"
    private const val VISIBLE = "visible"
    private const val TRAIL_FILE = "trail.txt"
    private const val TRAIL_SIZE = 40
    /** A restart within this long of Folio last being on screen is worth a note. */
    private const val RESTART_WINDOW_MS = 2 * 60_000L
    private const val TRACE_LIMIT = 48_000

    private val trail = ArrayDeque<String>()
    private val time = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")

    /** Adds one line to the trail (short, no personal content). */
    @Synchronized fun event(what: String) {
        trail.addLast("${LocalDateTime.now().format(time)}  $what")
        while (trail.size > TRAIL_SIZE) trail.removeFirst()
    }

    @Synchronized fun trailText(): String = trail.joinToString("\n")

    /** Saves the trail and a heartbeat, so the next start can tell what came before a freeze or restart. */
    fun checkpoint(context: Context, visible: Boolean) {
        runCatching {
            File(CrashLog.dir(context), TRAIL_FILE).writeText(trailText())
            context.getSharedPreferences(PREFS, 0).edit().putLong(LAST_SEEN, System.currentTimeMillis())
                .putBoolean(VISIBLE, visible).putInt(BOOT_COUNT, bootCount(context)).apply()
        }
    }

    private fun bootCount(context: Context) =
        runCatching { Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT) }.getOrDefault(-1)

    /** Called once when Folio starts: turns anything Android recorded since last time into local reports. */
    fun onStart(context: Context) {
        runCatching { recordExits(context) }
        runCatching { recordRestart(context) }
        event("Folio started")
    }

    private fun previousTrail(context: Context) =
        runCatching { File(CrashLog.dir(context), TRAIL_FILE).readText() }.getOrNull()?.takeIf { it.isNotBlank() }

    private fun recordExits(context: Context) {
        val am = context.getSystemService(ActivityManager::class.java) ?: return
        val prefs = context.getSharedPreferences(PREFS, 0)
        val seen = prefs.getLong(LAST_EXIT, 0L)
        val exits = am.getHistoricalProcessExitReasons(context.packageName, 0, 5).filter { it.timestamp > seen }
        if (exits.isEmpty()) return
        prefs.edit().putLong(LAST_EXIT, exits.maxOf { it.timestamp }).apply()
        // Java crashes are already written by the crash handler, with their stack trace.
        exits.filter { it.reason in WORTH_REPORTING }.forEach { exit ->
            // Only a freeze's trace, which is text. A native crash's record is a binary tombstone that can hold pieces
            // of whatever Folio had in memory: unreadable, and not something to put in a report someone emails.
            val trace = if (exit.reason == ApplicationExitInfo.REASON_ANR)
                runCatching { exit.traceInputStream?.bufferedReader()?.use { it.readText().take(TRACE_LIMIT) } }.getOrNull() else null
            CrashLog.save(context, "exit", buildString {
                appendLine("Folio ${label(exit.reason)}")
                appendLine("When: ${format(exit.timestamp)}")
                appendLine(CrashLog.environment(context))
                appendLine("Reason: ${exit.description ?: label(exit.reason)} (status ${exit.status}, importance ${exit.importance})")
                appendLine("Memory: ${exit.pss / 1024} MB PSS, ${exit.rss / 1024} MB RSS")
                previousTrail(context)?.let { appendLine(); appendLine("Before it:"); appendLine(it) }
                trace?.let { appendLine(); appendLine("Android's trace:"); append(it) }
            })
        }
    }

    private fun recordRestart(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, 0)
        val lastBoot = prefs.getInt(BOOT_COUNT, -1)
        val boot = bootCount(context)
        if (lastBoot < 0 || boot < 0 || boot == lastBoot) return
        val bootedAt = System.currentTimeMillis() - android.os.SystemClock.elapsedRealtime()
        val lastSeen = prefs.getLong(LAST_SEEN, 0L)
        // Only when Folio was on screen shortly before the phone went down: that's the kind of restart a stuck
        // screen leads to. Ordinary restarts hours later aren't recorded.
        if (!prefs.getBoolean(VISIBLE, false) || lastSeen <= 0 || bootedAt - lastSeen !in 0..RESTART_WINDOW_MS) return
        CrashLog.save(context, "restart", buildString {
            appendLine("Phone restarted while Folio was on screen")
            appendLine("Folio last seen: ${format(lastSeen)}; phone started again: ${format(bootedAt)}")
            appendLine(CrashLog.environment(context))
            appendLine("Android doesn't tell apps why a phone restarted. If you restarted it because Folio stopped responding, please say so in your report.")
            previousTrail(context)?.let { appendLine(); appendLine("Before it:"); append(it) }
        })
    }

    private fun format(epochMs: Long) = LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMs), ZoneId.systemDefault())
        .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

    private val WORTH_REPORTING = setOf(ApplicationExitInfo.REASON_ANR, ApplicationExitInfo.REASON_CRASH_NATIVE,
        ApplicationExitInfo.REASON_LOW_MEMORY, ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE,
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE, ApplicationExitInfo.REASON_SIGNALED)

    internal fun label(reason: Int) = when (reason) {
        ApplicationExitInfo.REASON_ANR -> "stopped responding (freeze)"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "native crash"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "was closed by Android to free memory"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "was closed for using too many resources"
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "failed to start"
        ApplicationExitInfo.REASON_SIGNALED -> "was stopped by the system"
        else -> "stopped (reason $reason)"
    }

    /** Screen and setup facts that explain most layout and drawing bugs (no personal data). */
    fun screenSummary(context: Context): String {
        val config = context.resources.configuration
        val metrics = context.resources.displayMetrics
        val state = runCatching {
            org.json.JSONObject(context.getSharedPreferences(SettingKeys.PREFS, 0).getString(SettingKeys.STATE, "{}") ?: "{}")
        }.getOrNull()
        val scale = runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) }.getOrDefault(1f)
        return listOf(
            "${if (config.fitsRegularHomeLayout()) "unfolded" else "folded"} ${config.screenWidthDp}×${config.screenHeightDp} dp",
            "smallest width ${config.smallestScreenWidthDp} dp",
            "density ${metrics.densityDpi} (default ${android.util.DisplayMetrics.DENSITY_DEVICE_STABLE})",
            "font ${config.fontScale}×",
            "animations ${scale}×",
            "left page ${state?.optString("leftPage", "TODAY") ?: "?"}",
            "wallpaper ${if (state?.optBoolean("systemWallpaper", false) == true) "Android" else "Folio"}",
            "fold effect ${if (state?.optBoolean("foldEffect", true) != false) "on" else "off"}",
            "page effect ${state?.optString("pageEffect")?.ifBlank { PageEffect.NONE.name } ?: "?"}",
            "safe mode ${if (SafeMode.active) "on" else "off"}",
        ).joinToString(", ")
    }

    fun buildDisplay(): String = "${Build.DISPLAY} (${Build.HARDWARE}, ${Build.SOC_MODEL})"

    /**
     * Everything useful for a bug report in one text: phone and screen, the latest reports, the recent trail and
     * Folio's own log lines (apps can only read their own). Built on demand, shown to you before it goes anywhere.
     */
    fun bundle(context: Context): String = buildString {
        appendLine("Folio diagnostics (${format(System.currentTimeMillis())})")
        appendLine(CrashLog.environment(context))
        appendLine()
        appendLine("Recent events:")
        appendLine(trailText().ifBlank { "(none)" })
        CrashLog.reports(context).take(3).forEach { appendLine(); appendLine("---- ${it.name}"); appendLine(it.readText().take(12_000)) }
        appendLine()
        appendLine("---- Folio log")
        append(ownLog().takeLast(20_000))
    }.take(60_000)

    private fun ownLog(): String = runCatching {
        val process = ProcessBuilder("logcat", "-d", "-t", "400", "-v", "time", "--pid", android.os.Process.myPid().toString())
            .redirectErrorStream(true).start()
        process.inputStream.bufferedReader().use { it.readText() }.also { process.destroy() }
    }.getOrDefault("(log unavailable)")

    /**
     * The bundle, built where it can take its time. It runs logcat and reads report files, and doing that on the
     * main thread from a tap is what froze Folio on a slow phone, usually just when it was already misbehaving.
     */
    suspend fun bundleOffMain(context: Context): String = withContext(Dispatchers.IO) { bundle(context) }

    /** Where an emailed report goes: no GitHub account needed, and nothing passes through a server of Folio's. */
    const val SUPPORT_EMAIL = "contact@mcc-cal.com"

    /**
     * The report as a file, handed to whatever app the person picks. An email app attaches it, so it arrives as one
     * readable file instead of pages of pasted text, and they can open it before anything is sent. [email] addresses
     * it to [SUPPORT_EMAIL]; without it, the share sheet leaves the choice of where entirely to them.
     */
    suspend fun reportIntent(context: Context, email: Boolean): Intent = withContext(Dispatchers.IO) {
        // Old reports are cleared, but not one a mail app may still be reading: only those more than ten minutes old,
        // and every report gets a name of its own, so two in the same minute don't overwrite each other.
        val dir = File(context.cacheDir, "reports").apply { mkdirs() }
        val now = System.currentTimeMillis()
        dir.listFiles()?.filter { now - it.lastModified() > 10 * 60_000 }?.forEach { it.delete() }
        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss"))
        val file = File(dir, "folio-report-$stamp-${(1000..9999).random()}.txt").apply { writeText(bundle(context)) }
        val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.reports", file)
        val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
        val send = Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.folio_bug_report_1, version))
            .putExtra(Intent.EXTRA_TEXT, context.getString(R.string.report_email_body))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        // The chooser only passes read access on if the file is also in clipData.
        send.clipData = ClipData.newRawUri("", uri)
        if (email) {
            send.putExtra(Intent.EXTRA_EMAIL, arrayOf(SUPPORT_EMAIL))
            send.selector = Intent(Intent.ACTION_SENDTO, android.net.Uri.parse("mailto:"))
        }
        Intent.createChooser(send, context.getString(if (email) R.string.email_a_report else R.string.share_diagnostics))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    suspend fun copy(context: Context) {
        val text = bundleOffMain(context)
        context.getSystemService(android.content.ClipboardManager::class.java)
            ?.setPrimaryClip(ClipData.newPlainText("Folio diagnostics", text))
    }

    private const val ASKED = "report_asked_name"

    /**
     * Worth asking about: a crash, a freeze, or a native crash. Not Android closing Folio to free memory, which a
     * launcher in the background has happen to it routinely, and which telling someone "Folio closed unexpectedly"
     * about would only alarm them.
     */
    internal fun worthAsking(report: File): Boolean = when (report.name.substringBefore('-')) {
        "crash" -> true
        "exit" -> runCatching { report.useLines { it.firstOrNull() } }.getOrNull()?.let { first ->
            first == "Folio ${label(ApplicationExitInfo.REASON_ANR)}" || first == "Folio ${label(ApplicationExitInfo.REASON_CRASH_NATIVE)}"
        } == true
        else -> false
    }

    /**
     * The newest crash or freeze nobody has been asked about yet, or null. It remembers the report it last offered,
     * not a time, so a clock that is wrong in either direction can't hide one or offer one twice. The first time this
     * runs it only takes note of what is already there, so someone updating Folio isn't greeted by a report from
     * weeks ago.
     */
    fun unaskedFailure(context: Context): File? {
        val prefs = context.getSharedPreferences(PREFS, 0)
        val newest = CrashLog.reports(context).firstOrNull(::worthAsking)
        if (!prefs.contains(ASKED)) { prefs.edit().putString(ASKED, newest?.name.orEmpty()).apply(); return null }
        return newest?.takeIf { it.name != prefs.getString(ASKED, "") }
    }

    /** Asked about [report], whatever the answer: it is never offered again. */
    fun markAsked(context: Context, report: File) {
        context.getSharedPreferences(PREFS, 0).edit().putString(ASKED, report.name).apply()
    }

    // The Market and the background, in the trail. The words are here, in the one file McCal reads rather than people,
    // so callers hand over facts and a bug report reads the same in every language. Ids, versions and sizes only:
    // never a picture, a path, or what a package contains.

    /** A package fetched and applied, or why not. [id] is null for a file that didn't install, whose id is not known. */
    fun marketGot(id: String?, result: InstallResult) = event("Market get ${id ?: "from a file"}: ${describe(result)}")

    fun marketRemoved(id: String, ok: Boolean) = event("Market remove $id: ${if (ok) "removed" else "nothing changed"}")

    fun marketUndone(id: String, ok: Boolean) = event("Market undo $id: ${if (ok) "put back" else "couldn't put back"}")

    fun marketTurnedOff(id: String, ok: Boolean, bySafeMode: Boolean) =
        event("Market ${if (bySafeMode) "Safe Mode turned off" else "turn off"} $id: ${if (ok) "off" else "nothing changed"}")

    fun marketTurnedOn(id: String, ok: Boolean) = event("Market Try Again $id: ${if (ok) "on" else "still off"}")

    fun marketRestored(restore: PackageInstaller.Restore?) = event(
        if (restore == null) "Market backup: no packages"
        else "Market backup: ${restore.on.size} on, ${restore.off.size} off, ${restore.failed.size} failed" +
            restore.failed.joinToString(prefix = " (", postfix = ")") { it.id }.takeIf { restore.failed.isNotEmpty() }.orEmpty(),
    )

    private fun describe(result: InstallResult) = when (result) {
        is InstallResult.Installed -> "installed ${result.installed.version.text}" +
            (result.replaced?.let { " over ${it.version.text}" } ?: "")
        is InstallResult.NeedsNewerFolio -> "needs a newer Folio (${result.missing.joinToString()})"
        is InstallResult.Failed -> "failed, ${result.reason.name}: ${result.message}"
    }

    /** A wallpaper package's picture went on. [shown] is whether it is behind Home now (see ArtSelection). */
    fun artOn(id: String, fresh: Boolean, shown: Boolean) =
        event("Wallpaper $id on (${if (fresh) "new" else "again"}), ${if (shown) "behind Home" else "user's choice kept"}")

    /** A wallpaper package's picture came off. [putBack] is whether the earlier background went back behind Home. */
    fun artOff(id: String, putBack: Boolean) =
        event("Wallpaper $id off, ${if (putBack) "earlier background put back" else "user's choice kept"}")

    /** Why a wallpaper couldn't go on. The installer shows people one general message, so the reason is kept here. */
    fun artRefused(id: String, why: String?) = event("Wallpaper $id refused: ${why ?: "no reason given"}")

    fun artPruned(ids: List<String>) { if (ids.isNotEmpty()) event("Wallpaper art cleared: ${ids.joinToString()}") }

    fun backgroundSet(choice: BackgroundChoice) = event("Background set: ${choice.save()}")

    /** Home asked for its background and got nothing back from the decoder. */
    fun backgroundUnreadable(choice: BackgroundChoice, width: Int = 0, height: Int = 0) =
        event("Background ${choice.save()} couldn't be decoded" + if (width > 0) " (${width}x$height)" else "")

    /** The background was bigger than Folio draws and was decoded smaller. Expected never to happen. */
    fun backgroundSampled(choice: BackgroundChoice, width: Int, height: Int, sample: Int) =
        event("Background ${choice.save()} is ${width}x$height, decoded at 1/$sample")

    /** A pin request turned away. [why] is one of [PinTrust]'s reasons; the package is the one the request named. */
    fun pinRefused(packageName: String?, why: String) = event("Pin request from ${packageName ?: "an unknown app"} refused: $why")
}
