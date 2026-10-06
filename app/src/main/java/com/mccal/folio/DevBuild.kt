package com.mccal.folio

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
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

    /** Text for an issue or a message: which build this is, with nothing about the person. */
    fun summary(info: DevBuildInfo): String = buildString {
        append("Folio Dev ").append(info.branch).append(' ').append(info.sha).append(if (info.dirty) " (uncommitted changes)" else "")
        append(", built ").append(info.builtAt)
    }
}

private const val RECENT_SHOWN = 5

/** Folio Dev's page is for the person who builds Folio, so its words stay English and are kept here, one per line, not in strings.xml. */
private object DevText {
    const val BADGE = "Folio Dev" // english-only
    const val WELCOME = "Welcome to Folio Dev" // english-only
    const val NEW_BUILD = "New build" // english-only
    const val THIS_BUILD = "This build" // english-only
    const val WELCOME_BODY = "This copy is for testing. It is separate from your Folio, with its own settings, and a new build installs over it without wiping them." // english-only
    const val REOPENED_BODY = "You are on this build now." // english-only
    const val NEW_BODY = "This copy of Folio Dev was just replaced. Here is what is on it." // english-only
    const val BRANCH = "Branch" // english-only
    const val COMMIT = "Commit" // english-only
    const val BUILT = "Built" // english-only
    const val REPLACED = "Replaced" // english-only
    const val RECENT = "Recent commits" // english-only
    const val TRY = "What to try" // english-only
    const val GOT_IT = "Got it" // english-only
    const val COPY = "Copy build info" // english-only
    const val CLIP_LABEL = "Folio Dev build" // english-only
    const val UNCOMMITTED = " + uncommitted changes" // english-only
    fun changed(n: Int) = "What changed ($n)" // english-only
    fun tried(done: Int, total: Int) = "$done of $total tried. Ticks stay on this phone and start over with the next build." // english-only
}

/** The page: which build, what changed, and the steps the person who made the build wrote (none is fine). */
@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
internal fun DevBuildSheet(info: DevBuildInfo, seen: DevBuildSeen, onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var ticks by remember { mutableStateOf(DevBuild.ticks(context, info.sha)) }
    val reopened = seen.sha == info.sha
    val changes = remember { if (reopened) info.commits.take(RECENT_SHOWN) else DevBuild.changesSince(info, seen.sha) }
    val first = seen.sha == null
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = FolioSpace.XXL.dp).testTag("dev-build"),
            verticalArrangement = Arrangement.spacedBy(FolioSpace.SMALL.dp)) {
            Text(DevText.BADGE, color = FolioColors.Cyan, style = MaterialTheme.typography.labelLarge)
            Text(if (first) DevText.WELCOME else if (reopened) DevText.THIS_BUILD else DevText.NEW_BUILD, style = MaterialTheme.typography.headlineMedium)
            Text(if (first) DevText.WELCOME_BODY else if (reopened) DevText.REOPENED_BODY else DevText.NEW_BODY, style = MaterialTheme.typography.bodyMedium)
            BuildLine(DevText.BRANCH, info.branch, "dev-build-branch")
            BuildLine(DevText.COMMIT, info.sha + if (info.dirty) DevText.UNCOMMITTED else "", "dev-build-sha")
            BuildLine(DevText.BUILT, info.builtAt.substringBefore('.').replace('T', ' '), "dev-build-time")
            if (seen.sha != null && !reopened) BuildLine(DevText.REPLACED, (seen.branch ?: "") + " " + seen.sha, "dev-build-replaced")
            if (changes.isNotEmpty()) {
                Text(if (reopened) DevText.RECENT else DevText.changed(changes.size), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = FolioSpace.SMALL.dp))
                changes.forEach { c ->
                    Column(Modifier.fillMaxWidth().padding(vertical = FolioSpace.TINY.dp)) {
                        Text(c.subject, style = MaterialTheme.typography.bodyMedium)
                        Text(c.sha, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (info.notes.isNotEmpty()) {
                Text(DevText.TRY, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = FolioSpace.SMALL.dp))
                info.notes.forEachIndexed { i, step ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = i in ticks, onCheckedChange = { on ->
                            ticks = if (on) ticks + i else ticks - i
                            DevBuild.setTicks(context, info.sha, ticks)
                        }, modifier = Modifier.semantics { contentDescription = step }.testTag("dev-build-step-$i"))
                        Text(step, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Text(DevText.tried(ticks.size, info.notes.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(Modifier.fillMaxWidth().padding(vertical = FolioSpace.MEDIUM.dp), horizontalArrangement = Arrangement.spacedBy(FolioSpace.SMALL.dp)) {
                androidx.compose.material3.Button(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp).testTag("dev-build-done")) { Text(DevText.GOT_IT) }
                TextButton(onClick = {
                    val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    clip.setPrimaryClip(android.content.ClipData.newPlainText(DevText.CLIP_LABEL, DevBuild.summary(info)))
                }, modifier = Modifier.heightIn(min = 48.dp).testTag("dev-build-copy")) { Text(DevText.COPY) }
            }
        }
    }
}

@Composable private fun BuildLine(label: String, value: String, tag: String) {
    Row(Modifier.fillMaxWidth().heightIn(min = 40.dp).testTag(tag), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
