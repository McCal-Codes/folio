package com.mccal.folio

import android.content.Context
import android.os.Build
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Help › Report a Bug: GitHub's bug form with the details a report needs already filled in. It only opens the
 * browser; nothing is sent until you submit the form yourself.
 *
 * The form has the phone, the Android version and the screen picked, and a short summary in Diagnostics (phone and screen,
 * where Home was, the last few events and the top of the latest crash), so a report arrives with the facts even when nobody
 * pastes the full file. The full file stays one tap away (Email a Report).
 */
internal object BugReport {
    const val NEW_ISSUE = "https://github.com/McCal-Codes/folio/issues/new"

    /** GitHub refuses a link of about 8,000 characters or more; the summary is cut to stay well under. */
    const val LINK_LIMIT = 7_000

    /** The last line of the summary, always kept: where the rest is. */
    const val FOOTER = "This is a short summary. The full report (log, widgets, hinge, wallpaper) is in Settings > Help > Report a Bug > Email a Report."

    /** The options of the form's Screen dropdown, word for word (a test keeps them equal to .github/ISSUE_TEMPLATE/bug_report.yml). */
    const val SCREEN_FOLDED = "Folded (cover screen)"
    const val SCREEN_UNFOLDED = "Unfolded"
    const val SCREEN_OTHER = "Phone, tablet or other (not a foldable)"

    /** The Screen option that is certain: a foldable is folded or unfolded, anything else is not a foldable. Half folded is the person's to pick. */
    fun screenChoice(foldable: Boolean, unfolded: Boolean): String = when {
        !foldable -> SCREEN_OTHER
        unfolded -> SCREEN_UNFOLDED
        else -> SCREEN_FOLDED
    }

    private fun enc(value: String) = URLEncoder.encode(value, Charsets.UTF_8.name())

    fun url(version: String?, manufacturer: String, model: String, androidRelease: String, sdk: Int, screen: String? = null, note: String? = null,
        screenChoice: String? = null, digest: List<String> = emptyList()): String {
        val phone = if (model.startsWith(manufacturer, ignoreCase = true)) model else "$manufacturer $model"
        val base = "$NEW_ISSUE?template=bug_report.yml&version=${enc(version ?: "")}&phone=${enc(phone.trim())}" +
            "&android=${enc("$androidRelease (API $sdk)")}" + (screenChoice?.let { "&screen=${enc(it)}" } ?: "")
        val head = (screen?.let { "Screen: $it\n" } ?: "") + (note?.let { "Test: $it\n" } ?: "")
        if (digest.isEmpty()) return base + head.takeIf { it.isNotEmpty() }?.let { "&diagnostics=${enc("$it\n")}" }.orEmpty()
        return base + "&diagnostics=${enc(fit(base, head, digest))}"
    }

    /**
     * [head] and as much of [sections] as keeps the link at or under [limit], in order, then the footer. A section that does not fit
     * whole is cut line by line, and nothing after it is added, so what is kept is always the start of what matters most.
     */
    internal fun fit(base: String, head: String, sections: List<String>, limit: Int = LINK_LIMIT): String {
        fun render(blocks: List<String>) = head + "\n" + blocks.joinToString("\n\n").let { if (it.isEmpty()) "" else "$it\n\n" } + FOOTER + "\n"
        fun fits(blocks: List<String>) = base.length + "&diagnostics=".length + enc(render(blocks)).length <= limit
        val kept = mutableListOf<String>()
        for (section in sections.filter { it.isNotBlank() }) {
            val lines = section.lines()
            val taken = (lines.size downTo 1).firstOrNull { fits(kept + lines.take(it).joinToString("\n")) } ?: break
            kept += lines.take(taken).joinToString("\n")
            if (taken < lines.size) break
        }
        return render(kept)
    }

    /** How many recent events the summary carries. */
    private const val EVENTS = 15

    /** The start of a saved crash report: what it was and when, then the first lines of the stack, which are what name the failure. */
    internal fun crashTop(report: String, stackLines: Int = 12): String {
        val lines = report.lines()
        val blank = lines.indexOfFirst { it.isBlank() }
        val head = listOfNotNull(lines.firstOrNull(), lines.firstOrNull { it.startsWith("Time:") }, lines.firstOrNull { it.startsWith("Thread:") })
        val stack = if (blank >= 0) lines.drop(blank + 1).filter { it.isNotBlank() }.take(stackLines) else emptyList()
        return (head + stack).joinToString("\n") { it.take(160) }
    }

    /** The summary, most useful first: the build and phone, how Folio is doing and where Home is, the last events, the latest crash. Reads a file, so off the main thread. */
    internal fun digestSections(context: Context): List<String> {
        val environment = runCatching { CrashLog.environment(context) }.getOrNull()?.lines()?.filterNot { it.startsWith("Screen: ") }?.joinToString("\n")
        val state = listOfNotNull(
            runCatching { Diagnostics.processReport() }.getOrNull(), Diagnostics.homeLine(), runCatching { Diagnostics.hingeSeen(context) }.getOrNull(),
        ).joinToString("\n")
        val events = Diagnostics.trailText().lines().filter { it.isNotBlank() }.takeLast(EVENTS)
        val crash = runCatching {
            CrashLog.reports(context).firstOrNull(Diagnostics::worthAsking)?.let { file ->
                "Latest crash or freeze:\n" + crashTop(file.useLines { it.take(40).joinToString("\n") })
            }
        }.getOrNull()
        return listOfNotNull(environment, state, if (events.isEmpty()) null else "Recent events:\n" + events.joinToString("\n") { it.take(160) }, crash)
    }

    private fun screenChoice(context: Context): String = screenChoice(
        foldable = context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_SENSOR_HINGE_ANGLE),
        unfolded = context.resources.configuration.fitsRegularHomeLayout(),
    )

    private fun version(context: Context) = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()

    fun url(context: Context, note: String? = null): String = url(
        version(context), Build.MANUFACTURER, Build.MODEL, Build.VERSION.RELEASE, Build.VERSION.SDK_INT, Diagnostics.screenSummary(context), note,
        screenChoice(context))

    /** [url] with the short summary included. Built off the main thread, as it reads the trail and a crash file. */
    suspend fun urlWithDigest(context: Context, note: String? = null): String = withContext(Dispatchers.IO) {
        url(version(context), Build.MANUFACTURER, Build.MODEL, Build.VERSION.RELEASE, Build.VERSION.SDK_INT, Diagnostics.screenSummary(context), note,
            screenChoice(context), digestSections(context))
    }
}
