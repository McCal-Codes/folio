package com.mccal.folio

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.provider.Settings
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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
            val trace = if (exit.reason == ApplicationExitInfo.REASON_ANR || exit.reason == ApplicationExitInfo.REASON_CRASH_NATIVE)
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

    fun shareIntent(context: Context): android.content.Intent = android.content.Intent.createChooser(
        android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain")
            .putExtra(android.content.Intent.EXTRA_SUBJECT, "Folio diagnostics")
            .putExtra(android.content.Intent.EXTRA_TEXT, bundle(context)), "Share diagnostics")

    fun copy(context: Context) {
        context.getSystemService(android.content.ClipboardManager::class.java)
            ?.setPrimaryClip(android.content.ClipData.newPlainText("Folio diagnostics", bundle(context)))
    }
}
