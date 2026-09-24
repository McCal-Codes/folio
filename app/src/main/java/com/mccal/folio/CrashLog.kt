package com.mccal.folio

import android.content.Context
import android.content.Intent
import android.os.Build
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Local crash reports, no analytics: the last few crashes are written to Folio's private storage and nothing
 * leaves the phone unless you share a report yourself (Settings › Help › Crash Reports).
 */
internal object CrashLog {
    private const val DIR = "crashes"
    private const val KEEP = 5

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        SafeMode.onStart(app)
        Diagnostics.onStart(app)
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { write(app, thread, error) }
            runCatching { SafeMode.onCrash(app) }
            previous?.uncaughtException(thread, error)
        }
    }

    internal fun dir(context: Context) = File(context.filesDir, DIR).apply { mkdirs() }

    /** Folio version, phone and screen: the lines every report starts with. */
    internal fun environment(context: Context): String {
        val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
        return "Folio: $version\n" +
            "Device: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, API ${Build.VERSION.SDK_INT})\n" +
            "Build: ${Diagnostics.buildDisplay()}\n" +
            "Screen: ${Diagnostics.screenSummary(context)}"
    }

    /** Writes one report file of [kind] and keeps only the newest few. */
    internal fun save(context: Context, kind: String, text: String) {
        val folder = dir(context)
        File(folder, "$kind-${System.currentTimeMillis()}.txt").writeText(text)
        reports(context).drop(KEEP).forEach { it.delete() }
    }

    internal fun report(context: Context, threadName: String, error: Throwable, now: LocalDateTime = LocalDateTime.now()): String =
        buildString {
            appendLine("Folio crash report")
            appendLine("Time: ${now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)}")
            appendLine(environment(context))
            appendLine("Thread: $threadName")
            appendLine()
            appendLine(error.stackTraceToString())
            Diagnostics.trailText().takeIf { it.isNotBlank() }?.let { appendLine("Before it:"); append(it) }
        }

    private fun write(context: Context, thread: Thread, error: Throwable) = save(context, "crash", report(context, thread.name, error))

    /** Newest first; only report files (not the saved trail). */
    fun reports(context: Context): List<File> = dir(context).listFiles()
        ?.filter { it.name.endsWith(".txt") && it.name.substringBefore('-') in KINDS }
        ?.sortedByDescending { it.name.substringAfter('-') }.orEmpty()

    private val KINDS = setOf("crash", "exit", "restart")

    fun clear(context: Context) { reports(context).forEach { it.delete() } }

    /** Share sheet with the report text, so you choose where it goes. */
    fun shareIntent(file: File): Intent = Intent.createChooser(
        Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT, "Folio crash report")
            .putExtra(Intent.EXTRA_TEXT, file.readText().take(60_000)), "Share crash report")
}

/**
 * Jailbreak-style Safe Mode: if Folio crashes twice within 30 seconds of starting, the next start pauses optional
 * features (without changing any settings) and asks whether to continue safely or restart normally.
 */
internal object SafeMode {
    private const val PREFS = "safe_mode"
    private const val QUICK_CRASHES = "quickCrashes"
    private const val WINDOW_MS = 30_000L
    private var startedAt = 0L
    @Volatile var active = false
        private set

    fun onStart(context: Context) {
        startedAt = android.os.SystemClock.elapsedRealtime()
        active = context.getSharedPreferences(PREFS, 0).getInt(QUICK_CRASHES, 0) >= 2
    }

    fun onCrash(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, 0)
        val quick = android.os.SystemClock.elapsedRealtime() - startedAt < WINDOW_MS
        prefs.edit().putInt(QUICK_CRASHES, if (quick) prefs.getInt(QUICK_CRASHES, 0) + 1 else 1).commit()
    }

    /** Folio ran a while without crashing: forget earlier quick crashes (called from Home). */
    fun markStable(context: Context) {
        if (android.os.SystemClock.elapsedRealtime() - startedAt < WINDOW_MS) return
        context.getSharedPreferences(PREFS, 0).edit().remove(QUICK_CRASHES).apply()
    }

    fun exit(context: Context) {
        context.getSharedPreferences(PREFS, 0).edit().remove(QUICK_CRASHES).commit()
        active = false
    }

    /** Settings as they apply while in Safe Mode; saved settings are untouched. */
    fun effective(state: LauncherState): LauncherState = if (!active) state else state.copy(
        appPanels = false, dockMagnify = false, tintNotifications = false, tintMedia = false, triggerActions = emptyMap(),
        foldEffect = false, lockCover = false, islandEverywhere = false, dockEverywhere = false, widgetStacks = state.widgetStacks,
        stackRotate = false, notificationAppRow = false, pageEffect = PageEffect.NONE)
}
