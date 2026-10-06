package com.mccal.folio

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Build
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.json.JSONObject

/** One commit that went into a Folio Dev build. */
internal data class DevCommit(val sha: String, val subject: String)

/** What the build step wrote for this Folio Dev build (app/build.gradle.kts, generateDevBuildInfo). Commits are newest first. */
internal data class DevBuildInfo(
    val branch: String, val sha: String, val builtAt: String, val dirty: Boolean,
    val commits: List<DevCommit>, val notes: List<String>,
)

/** The build that was on the phone before this one, as remembered when its welcome page was closed. */
internal data class DevBuildSeen(val sha: String?, val branch: String?)

/**
 * Folio Dev's "welcome back" page (docs/release-0.6.9-plan.md, G4). What's New is keyed to the version name, and a local
 * build keeps the same one, so it never shows on a Folio Dev install. This is keyed to the commit instead: shown once per
 * build, only when the app id ends in `.dev`, and only when the build step left its file in the APK, which release
 * builds never get.
 */
internal object DevBuild {
    private const val PREFS = "dev_build"
    private const val SEEN_SHA = "seen_sha"
    private const val SEEN_BRANCH = "seen_branch"
    private const val TICKS = "ticks_"

    const val THIS_BUILD_LABEL = "This build" // english-only

    /** Bumped to show the page again from Settings › General › What's New. */
    val reopen = mutableIntStateOf(0)

    fun isDevApp(context: Context) = context.packageName.endsWith(".dev")

    fun parse(json: String): DevBuildInfo? = runCatching {
        val o = JSONObject(json)
        val commits = o.optJSONArray("commits")
        val notes = o.optJSONArray("notes")
        DevBuildInfo(
            branch = o.getString("branch"), sha = o.getString("sha"), builtAt = o.optString("builtAt"), dirty = o.optBoolean("dirty"),
            commits = (0 until (commits?.length() ?: 0)).map { commits!!.getJSONObject(it).let { c -> DevCommit(c.getString("sha"), c.getString("subject")) } },
            notes = (0 until (notes?.length() ?: 0)).map { notes!!.getString(it) },
        )
    }.getOrNull()

    fun load(context: Context): DevBuildInfo? = if (!isDevApp(context)) null
        else runCatching { context.assets.open("dev-build.json").bufferedReader().use { parse(it.readText()) } }.getOrNull()

    fun seen(context: Context): DevBuildSeen = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .let { DevBuildSeen(it.getString(SEEN_SHA, null), it.getString(SEEN_BRANCH, null)) }

    /** True the first time this build runs: the commit differs from the last one whose page was closed. */
    fun isNewBuild(info: DevBuildInfo, seen: DevBuildSeen) = seen.sha != info.sha

    fun shouldShow(context: Context): Boolean = load(context)?.let { isNewBuild(it, seen(context)) } == true

    fun markSeen(context: Context, info: DevBuildInfo) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(SEEN_SHA, info.sha).putString(SEEN_BRANCH, info.branch).apply()
    }

    /** The commits newer than [lastSha]; all of those the file lists when the last build is not among them (another branch, or older than 15). */
    fun changesSince(info: DevBuildInfo, lastSha: String?): List<DevCommit> {
        val at = info.commits.indexOfFirst { it.sha == lastSha }
        return if (lastSha == null || at < 0) info.commits else info.commits.take(at)
    }

    fun ticks(context: Context, sha: String): Set<Int> = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getStringSet(TICKS + sha, emptySet()).orEmpty().mapNotNull { it.toIntOrNull() }.toSet()

    fun setTicks(context: Context, sha: String, ticks: Set<Int>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putStringSet(TICKS + sha, ticks.map(Int::toString).toSet()).apply()
    }

    /** Which page this is: the first time Folio Dev has run here, a new build, or the same build opened again. */
    enum class Kind { FIRST, NEW, REOPENED }

    fun kind(info: DevBuildInfo, seen: DevBuildSeen) = when (seen.sha) { null -> Kind.FIRST; info.sha -> Kind.REOPENED; else -> Kind.NEW }

    /** Rows shown up front; the rest sit behind "N more changes". */
    const val LEAD_ROWS = 3
    const val RECENT_SHOWN = 5

    /** The commits to list: what is newer than the last build, or the latest few when the same build is opened again. */
    fun listed(info: DevBuildInfo, seen: DevBuildSeen): List<DevCommit> =
        if (kind(info, seen) == Kind.REOPENED) info.commits.take(RECENT_SHOWN) else changesSince(info, seen.sha)

    /** The build file's ISO time in the phone's own style ("Oct 6, 2026, 3:26 PM" in English); the raw text if it cannot be read. */
    fun shortTime(iso: String, locale: java.util.Locale = java.util.Locale.getDefault()): String = runCatching {
        java.time.OffsetDateTime.parse(iso).format(java.time.format.DateTimeFormatter.ofLocalizedDateTime(java.time.format.FormatStyle.MEDIUM, java.time.format.FormatStyle.SHORT).withLocale(locale))
    }.getOrDefault(iso)

    /** The quiet line under the rows: when it was built and, for a new build, which one it replaced. */
    fun footer(info: DevBuildInfo, seen: DevBuildSeen, locale: java.util.Locale = java.util.Locale.getDefault()): String {
        val built = "Built " + shortTime(info.builtAt, locale) + if (info.dirty) ", with uncommitted changes" else "" // english-only
        return if (kind(info, seen) == Kind.NEW) built + " · replaced " + (seen.branch ?: "") + " " + seen.sha else built // english-only
    }

    /** Text for an issue or a message: which build this is, with nothing about the person. */
    fun summary(info: DevBuildInfo): String = buildString {
        append("Folio Dev ").append(info.branch).append(' ').append(info.sha).append(if (info.dirty) " (uncommitted changes)" else "")
        append(", built ").append(info.builtAt)
    }
}

/** Folio Dev's page is for the person who builds Folio, so its words stay English and are kept here, one per line, not in strings.xml. */
private object DevText {
    const val WELCOME = "Welcome to Folio Dev" // english-only
    const val NEW_BUILD = "New build" // english-only
    const val THIS_BUILD = "This build" // english-only
    const val WELCOME_BODY = "The amber copy of Folio for testing." // english-only
    const val REOPENED_BODY = "You are on this build now." // english-only
    const val NEW_BODY = "Folio Dev was just replaced. Here is what is new on this phone." // english-only
    const val TRY = "What to try" // english-only
    const val CONTINUE = "Continue" // english-only
    const val ALL_DONE = "All done" // english-only
    const val DONE = "Done" // english-only
    const val COPY = "Copy build info" // english-only
    const val CLIP_LABEL = "Folio Dev build" // english-only
    fun more(n: Int) = "$n more changes" // english-only
    fun tried(done: Int, total: Int) = "$done of $total" // english-only
    val first = listOf(
        "Its own copy" to "Separate from your Folio, with its own Home and settings.", // english-only
        "Updates keep your Home" to "A new build installs over it without wiping anything.", // english-only
        "Shown once per build" to "Each new build says what changed and what to try.", // english-only
    )
}

/** The page, in the style of What's New: one icon, a title, the build, three rows, the steps to try if there are any, and Continue. */
@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
internal fun DevBuildSheet(info: DevBuildInfo, seen: DevBuildSeen, onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val kind = DevBuild.kind(info, seen)
    var ticks by remember { mutableStateOf(DevBuild.ticks(context, info.sha)) }
    var moreOpen by remember { mutableStateOf(false) }
    val changes = remember { DevBuild.listed(info, seen) }
    val white = androidx.compose.ui.graphics.Color.White
    val amber = androidx.compose.ui.graphics.Color(FolioColors.Value.Orange)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.9f).padding(horizontal = FolioSpace.XXL.dp).testTag("dev-build")) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Column(Modifier.fillMaxWidth().padding(top = FolioSpace.MEDIUM.dp, bottom = FolioSpace.SMALL.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    androidx.compose.foundation.layout.Box(Modifier.size(72.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(FolioRadius.GROUPED_CARD.dp)).background(amber), contentAlignment = Alignment.Center) {
                        androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Rounded.Build, null, tint = white, modifier = Modifier.size(36.dp))
                    }
                    Text(when (kind) { DevBuild.Kind.FIRST -> DevText.WELCOME; DevBuild.Kind.NEW -> DevText.NEW_BUILD; DevBuild.Kind.REOPENED -> DevText.THIS_BUILD },
                        color = white, fontSize = FolioType.TITLE.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(top = FolioSpace.COMFY.dp))
                    Text(info.branch + " · " + info.sha, color = FolioColors.Cyan, fontSize = FolioType.FOOTNOTE.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                        modifier = Modifier.padding(top = FolioSpace.SMALL.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(FolioRadius.CONTROL.dp))
                            .background(FolioColors.Cyan.copy(alpha = .16f)).padding(horizontal = FolioSpace.COMPACT.dp, vertical = FolioSpace.TINY.dp).testTag("dev-build-sha"))
                    Text(when (kind) { DevBuild.Kind.FIRST -> DevText.WELCOME_BODY; DevBuild.Kind.NEW -> DevText.NEW_BODY; DevBuild.Kind.REOPENED -> DevText.REOPENED_BODY },
                        color = white.copy(alpha = .8f), fontSize = FolioType.SUBHEAD.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(top = FolioSpace.COMPACT.dp))
                }
                if (kind == DevBuild.Kind.FIRST) DevText.first.forEach { (title, detail) -> DevRow(androidx.compose.material.icons.Icons.Rounded.AutoAwesome, amber, title, detail) }
                else {
                    changes.take(DevBuild.LEAD_ROWS).forEach { c -> WhatsNew.symbol(c.subject).let { (icon, color) -> DevRow(icon, androidx.compose.ui.graphics.Color(color), c.subject, c.sha) } }
                    val rest = changes.drop(DevBuild.LEAD_ROWS)
                    if (rest.isNotEmpty() && !moreOpen) SheetGroup(Modifier.padding(top = FolioSpace.MEDIUM.dp)) {
                        androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { moreOpen = true }.padding(horizontal = FolioSpace.LARGE.dp)
                            .testTag("dev-build-more"), verticalAlignment = Alignment.CenterVertically) {
                            Text(DevText.more(rest.size), color = FolioColors.Cyan, fontSize = FolioType.BODY.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                        }
                    }
                    if (moreOpen) rest.forEach { c -> WhatsNew.symbol(c.subject).let { (icon, color) -> DevRow(icon, androidx.compose.ui.graphics.Color(color), c.subject, c.sha) } }
                }
                if (kind != DevBuild.Kind.FIRST && info.notes.isNotEmpty()) {
                    SheetGroupLabel(DevText.TRY + " · " + DevText.tried(ticks.size, info.notes.size))
                    SheetGroup {
                        info.notes.forEachIndexed { i, step ->
                            if (i > 0) MenuDivider()
                            Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = FolioSpace.SMALL.dp), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = i in ticks, onCheckedChange = { on ->
                                    ticks = if (on) ticks + i else ticks - i
                                    DevBuild.setTicks(context, info.sha, ticks)
                                }, modifier = Modifier.semantics { contentDescription = step }.testTag("dev-build-step-$i"))
                                Text(step, color = white, fontSize = FolioType.BODY.sp)
                            }
                        }
                    }
                }
                Text(DevBuild.footer(info, seen), color = white.copy(alpha = .6f), fontSize = FolioType.GROUP_LABEL.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = FolioSpace.LARGE.dp, bottom = FolioSpace.MEDIUM.dp).testTag("dev-build-footer"))
            }
            val allDone = info.notes.isNotEmpty() && ticks.size == info.notes.size
            androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().padding(top = FolioSpace.SMALL.dp).heightIn(min = 52.dp)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(FolioRadius.CARD.dp)).background(LocalAccent.current.fill)
                .clickable(onClick = onDismiss).testTag("dev-build-done"), contentAlignment = Alignment.Center) {
                Text(if (allDone) DevText.ALL_DONE else if (kind == DevBuild.Kind.REOPENED) DevText.DONE else DevText.CONTINUE, color = white, fontSize = FolioType.BODY.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
            }
            TextButton(onClick = {
                val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clip.setPrimaryClip(android.content.ClipData.newPlainText(DevText.CLIP_LABEL, DevBuild.summary(info)))
            }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("dev-build-copy")) { Text(DevText.COPY, color = FolioColors.Cyan) }
        }
    }
}

/** One row like What's New's: a soft icon, a bold line and a smaller one under it. */
@Composable private fun DevRow(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: androidx.compose.ui.graphics.Color, title: String, detail: String) {
    Row(Modifier.fillMaxWidth().padding(top = FolioSpace.COMFY.dp).semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.Top) {
        androidx.compose.foundation.layout.Box(Modifier.size(44.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(FolioRadius.CARD.dp)).background(tint.copy(alpha = .18f)), contentAlignment = Alignment.Center) {
            androidx.compose.material3.Icon(icon, null, tint = tint, modifier = Modifier.size(24.dp))
        }
        Column(Modifier.padding(start = FolioSpace.COMFY.dp).weight(1f)) {
            Text(title, color = androidx.compose.ui.graphics.Color.White, fontSize = FolioType.BODY.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
            Text(detail, color = androidx.compose.ui.graphics.Color.White.copy(alpha = .68f), fontSize = FolioType.SUBHEAD.sp, modifier = Modifier.padding(top = FolioSpace.HAIR.dp))
        }
    }
}
