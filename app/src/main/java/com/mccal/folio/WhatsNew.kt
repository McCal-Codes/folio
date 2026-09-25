package com.mccal.folio

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.semantics.semantics
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource

/** One version's notes from CHANGELOG.md: its number, date, and bullet points grouped by heading (Added, Changed, Fixed). */
data class ReleaseNotes(val version: String, val date: String?, val sections: List<Pair<String, List<String>>>)

/** A changelog line split into its bold title ("**More rows:** …") and the rest; [title] is null for plain lines. */
data class NoteItem(val title: String?, val detail: String)

internal object WhatsNew {
    /**
     * "**Title:** detail" → title and detail; other lines keep their text, markers and all, because What's New
     * renders them with [MarketText] now rather than showing them as typed.
     */
    fun split(item: String): NoteItem {
        val m = Regex("""^\*\*(.+?):?\*\*:?\s*(.*)$""").find(item.trim())
            ?: return NoteItem(null, item.trim())
        val detail = m.groupValues[2].trim().replaceFirstChar { it.uppercase() }
        return NoteItem(m.groupValues[1].trim().removeSuffix(":"), detail)
    }

    /** A symbol and color for a feature, chosen from words in its title (a star when nothing matches). */
    fun symbol(title: String): Pair<androidx.compose.ui.graphics.vector.ImageVector, Long> {
        val t = title.lowercase(java.util.Locale.ROOT)
        return when {
            "row" in t || "grid" in t -> Icons.Rounded.GridView to 0xFF0A84FF
            "slider" in t || "spacing" in t -> Icons.Rounded.Tune to 0xFF5E5CE6
            "position" in t || "dock" in t -> Icons.Rounded.Dock to 0xFF30D158
            "update" in t -> Icons.Rounded.SystemUpdate to 0xFFFF9F0A
            "clock" in t -> Icons.Rounded.Schedule to 0xFFFF375F
            "try" in t || "preview" in t -> Icons.Rounded.Home to 0xFF64D2FF
            "icon" in t -> Icons.Rounded.Apps to 0xFFBF5AF2
            "cover" in t || "fold" in t -> Icons.Rounded.Devices to 0xFF30B0C7
            "pull" in t || "library" in t || "notification" in t -> Icons.Rounded.SwipeDown to 0xFF32ADE6
            "badge" in t || "folder" in t -> Icons.Rounded.Folder to 0xFFFF453A
            "bug" in t || "report" in t -> Icons.Rounded.BugReport to 0xFFFF9F0A
            "roadmap" in t -> Icons.Rounded.Map to 0xFF5E5CE6
            "island" in t -> Icons.Rounded.Circle to 0xFF8E8E93
            "theme" in t || "look" in t -> Icons.Rounded.Palette to 0xFFFF375F
            else -> Icons.Rounded.AutoAwesome to 0xFFFFD60A
        }
    }

    private const val PREFS = "whats_new"
    private const val SEEN = "seen_version"

    /** Parses Keep a Changelog sections ("## [1.2.0] - date", "### Added", "- item"); stops at non-version headings. */
    fun parse(markdown: String): List<ReleaseNotes> {
        val releases = mutableListOf<ReleaseNotes>()
        var version: String? = null; var date: String? = null
        val sections = mutableListOf<Pair<String, MutableList<String>>>()
        fun flush() { version?.let { v -> releases += ReleaseNotes(v, date, sections.filter { it.second.isNotEmpty() }.map { it.first to it.second.toList() }) } }
        markdown.lineSequence().forEach { raw ->
            val line = raw.trimEnd()
            val heading = Regex("""^## \[(\d+\.\d+\.\d+[^\]]*)](?:\s*-\s*(.+))?""").find(line)
            when {
                heading != null -> { flush(); version = heading.groupValues[1]; date = heading.groupValues[2].ifBlank { null }; sections.clear() }
                line.startsWith("## ") -> { flush(); version = null; sections.clear() }
                version != null && line.startsWith("### ") -> sections += line.removePrefix("### ").trim() to mutableListOf()
                version != null && line.startsWith("- ") -> {
                    if (sections.isEmpty()) sections += "" to mutableListOf()
                    sections.last().second += line.removePrefix("- ").trim()
                }
            }
        }
        flush()
        return releases
    }

    fun notes(context: Context): List<ReleaseNotes> =
        runCatching { context.assets.open("CHANGELOG.md").bufferedReader().use { parse(it.readText()) } }.getOrDefault(emptyList())

    fun currentVersion(context: Context): String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: ""

    /**
     * The release this build belongs to: [currentVersion] without a pre-release suffix.
     *
     * The changelog has a section per release, not per beta, and a beta shares its release's `versionCode` on
     * purpose so the release installs over it. Matching on the full name meant 0.7.0-beta.1 found no notes and
     * showed nobody what was new in it, which is most of what a beta is for. What is remembered as seen is still
     * the full name, so the release itself shows its notes again over the beta.
     */
    fun releaseVersion(context: Context): String = currentVersion(context).substringBefore('-')

    /**
     * Whether to show What's New now: only after an update to a version with notes, once. A first install records the
     * version without showing (the welcome covers it).
     */
    fun shouldShow(context: Context, firstRun: Boolean): Boolean {
        val prefs = context.getSharedPreferences(PREFS, 0)
        val current = currentVersion(context)
        val seen = prefs.getString(SEEN, null)
        if (seen == current) return false
        if (firstRun || seen == null && !context.getSharedPreferences(SettingKeys.PREFS, 0).contains(SettingKeys.STATE)) {
            markSeen(context); return false
        }
        return notes(context).any { it.version == releaseVersion(context) }
    }

    fun markSeen(context: Context) = context.getSharedPreferences(PREFS, 0).edit().putString(SEEN, currentVersion(context)).apply()
}

/** iOS-style "What's New": the version's changes grouped under their headings, with a Continue button. */
@androidx.compose.runtime.Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
internal fun WhatsNewSheet(onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val version = androidx.compose.runtime.remember { WhatsNew.releaseVersion(context) }
    val notes = androidx.compose.runtime.remember { WhatsNew.notes(context) }
    val release = notes.firstOrNull { it.version == version } ?: notes.firstOrNull()
    val older = notes.filter { it != release }
    var expanded by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(setOf<String>()) }
    var showAllFeatures by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    var fixesOpen by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.fillMaxWidth().fillMaxHeight(.9f).padding(horizontal = FolioSpace.XXL.dp).testTag("whats-new")) {
            val listState = androidx.compose.foundation.lazy.rememberLazyListState()
            androidx.compose.foundation.lazy.LazyColumn(androidx.compose.ui.Modifier.weight(1f).edgeFade(listState), state = listState, verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(FolioSpace.COMPACT.dp)) {
                item {
                    androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.fillMaxWidth().padding(top = FolioSpace.MEDIUM.dp, bottom = FolioSpace.SMALL.dp),
                        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                        folioIconBitmap(context)?.let { androidx.compose.foundation.Image(it, null, androidx.compose.ui.Modifier.size(72.dp)
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(18.dp))) }
                        androidx.compose.material3.Text(stringResource(R.string.what_s_new_in_folio), color = androidx.compose.ui.graphics.Color.White, fontSize = 30.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = androidx.compose.ui.Modifier.padding(top = FolioSpace.COMFY.dp))
                        release?.let {
                            androidx.compose.foundation.layout.Row(androidx.compose.ui.Modifier.padding(top = FolioSpace.SMALL.dp),
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                androidx.compose.material3.Text(stringResource(R.string.version_1, it.version), color = FolioColors.Cyan,
                                    fontSize = FolioType.FOOTNOTE.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                                    modifier = androidx.compose.ui.Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(FolioRadius.CONTROL.dp))
                                        .background(FolioColors.Cyan.copy(alpha = .16f)).padding(horizontal = FolioSpace.COMPACT.dp, vertical = FolioSpace.TINY.dp))
                                it.date?.takeIf { d -> !d.equals("Unreleased", true) }?.let { d -> androidx.compose.material3.Text(d,
                                    color = androidx.compose.ui.graphics.Color.White.copy(alpha = .55f), fontSize = 14.sp,
                                    modifier = androidx.compose.ui.Modifier.padding(start = FolioSpace.SMALL.dp)) }
                            }
                        }
                    }
                }
                // Like a tweak's depiction in Sileo: new features first, each with a soft icon, a title and a line about it;
                // everything else (changes and fixes) folds into one group.
                val sections = release?.sections.orEmpty()
                val features = sections.filter { it.first.equals("Added", true) }.flatMap { it.second }.map(WhatsNew::split)
                val others = sections.filterNot { it.first.equals("Added", true) }.flatMap { it.second }.map(WhatsNew::split)
                val shownFeatures = if (showAllFeatures) features else features.take(FEATURES_SHOWN)
                shownFeatures.forEachIndexed { index, note -> item(key = "feature-$index") { FeatureRow(note) } }
                if (features.size > shownFeatures.size) item(key = "more-features") {
                    SheetGroup {
                        DisclosureRow("${features.size - shownFeatures.size} more new features", open = false, tag = "whats-new-more") { showAllFeatures = true }
                    }
                }
                if (others.isNotEmpty()) item(key = "fixes") {
                    SheetGroup {
                        DisclosureRow("Fixes and Improvements", open = fixesOpen, count = others.size, tag = "whats-new-fixes") { fixesOpen = !fixesOpen }
                        if (fixesOpen) others.forEach { note ->
                            MenuDivider()
                            androidx.compose.material3.Text(MarketText.inline(note.title?.let { "**$it:** ${note.detail}" } ?: note.detail, LocalAccent.current.ink),
                                color = androidx.compose.ui.graphics.Color.White.copy(alpha = .85f), fontSize = FolioType.SUBHEAD.sp,
                                modifier = androidx.compose.ui.Modifier.fillMaxWidth().padding(horizontal = FolioSpace.LARGE.dp, vertical = FolioSpace.COMPACT.dp))
                        }
                    }
                }
                // Version History: every earlier release, collapsed like iOS disclosure rows.
                if (older.isNotEmpty()) item { androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.padding(top = FolioSpace.MEDIUM.dp)) { SheetGroupLabel("Version History") } }
                older.forEach { notes ->
                    item(key = "history-${notes.version}") {
                        val open = notes.version in expanded
                        SheetGroup {
                            androidx.compose.foundation.layout.Row(androidx.compose.ui.Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                .clickable { expanded = if (open) expanded - notes.version else expanded + notes.version }
                                .padding(horizontal = FolioSpace.LARGE.dp).testTag("history-${notes.version}"), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                androidx.compose.material3.Text(stringResource(R.string.version_1, notes.version), color = androidx.compose.ui.graphics.Color.White, fontSize = 16.sp,
                                    modifier = androidx.compose.ui.Modifier.weight(1f))
                                notes.date?.let { androidx.compose.material3.Text(it, color = androidx.compose.ui.graphics.Color.White.copy(alpha = .55f), fontSize = FolioType.SUBHEAD.sp) }
                                androidx.compose.material3.Icon(if (open) androidx.compose.material.icons.Icons.Rounded.ExpandLess else androidx.compose.material.icons.Icons.Rounded.ExpandMore,
                                    null, tint = androidx.compose.ui.graphics.Color.White.copy(alpha = .4f), modifier = androidx.compose.ui.Modifier.padding(start = FolioSpace.SMALL.dp).size(20.dp))
                            }
                            if (open) notes.sections.forEach { (heading, items) ->
                                MenuDivider()
                                if (heading.isNotEmpty()) androidx.compose.material3.Text(heading.uppercase(), color = androidx.compose.ui.graphics.Color.White.copy(alpha = .5f),
                                    fontSize = FolioType.GROUP_LABEL.sp, modifier = androidx.compose.ui.Modifier.padding(start = FolioSpace.LARGE.dp, top = FolioSpace.COMPACT.dp))
                                items.forEach { text ->
                                    androidx.compose.material3.Text(MarketText.inline("• $text", LocalAccent.current.ink), color = androidx.compose.ui.graphics.Color.White.copy(alpha = .85f), fontSize = FolioType.SUBHEAD.sp,
                                        modifier = androidx.compose.ui.Modifier.fillMaxWidth().padding(horizontal = FolioSpace.LARGE.dp, vertical = FolioSpace.SNUG.dp))
                                }
                            }
                        }
                    }
                }
            }
            androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxWidth().padding(vertical = FolioSpace.LARGE.dp).heightIn(min = 52.dp)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(FolioRadius.CARD.dp)).background(LocalAccent.current.fill)
                .clickable(onClick = onDismiss).testTag("whats-new-continue"), contentAlignment = androidx.compose.ui.Alignment.Center) {
                androidx.compose.material3.Text(stringResource(R.string.continue_choice), color = androidx.compose.ui.graphics.Color.White, fontSize = FolioType.BODY.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
            }
        }
    }
}

private const val FEATURES_SHOWN = 6

/** A new feature: a soft tinted icon (the symbol in color on a pale tile), its title and what it does. */
@androidx.compose.runtime.Composable
private fun FeatureRow(note: NoteItem) {
    val (symbol, color) = WhatsNew.symbol(note.title ?: note.detail)
    val tint = androidx.compose.ui.graphics.Color(color)
    androidx.compose.foundation.layout.Row(androidx.compose.ui.Modifier.fillMaxWidth().padding(vertical = FolioSpace.SNUG.dp)
        .semantics(mergeDescendants = true) {}, verticalAlignment = androidx.compose.ui.Alignment.Top) {
        androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.size(44.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
            .background(tint.copy(alpha = .18f)), contentAlignment = androidx.compose.ui.Alignment.Center) {
            androidx.compose.material3.Icon(symbol, null, tint = tint, modifier = androidx.compose.ui.Modifier.size(24.dp))
        }
        androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.padding(start = FolioSpace.COMFY.dp).weight(1f)) {
            note.title?.let { androidx.compose.material3.Text(it, color = androidx.compose.ui.graphics.Color.White, fontSize = FolioType.BODY.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold) }
            androidx.compose.material3.Text(MarketText.inline(note.detail, LocalAccent.current.ink), color = androidx.compose.ui.graphics.Color.White.copy(alpha = .68f), fontSize = FolioType.SUBHEAD.sp,
                modifier = androidx.compose.ui.Modifier.padding(top = FolioSpace.HAIR.dp))
        }
    }
}

/** A row that opens more below it, with an optional count, like an iOS disclosure row. */
@androidx.compose.runtime.Composable
private fun DisclosureRow(label: String, open: Boolean, count: Int? = null, tag: String, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Row(androidx.compose.ui.Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onClick)
        .padding(horizontal = FolioSpace.LARGE.dp).testTag(tag), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        androidx.compose.material3.Text(label, color = androidx.compose.ui.graphics.Color.White, fontSize = 16.sp,
            modifier = androidx.compose.ui.Modifier.weight(1f))
        count?.let { androidx.compose.material3.Text("$it", color = androidx.compose.ui.graphics.Color.White.copy(alpha = .55f), fontSize = FolioType.SUBHEAD.sp) }
        androidx.compose.material3.Icon(if (open) androidx.compose.material.icons.Icons.Rounded.ExpandLess else androidx.compose.material.icons.Icons.Rounded.ExpandMore,
            null, tint = androidx.compose.ui.graphics.Color.White.copy(alpha = .4f), modifier = androidx.compose.ui.Modifier.padding(start = FolioSpace.SMALL.dp).size(20.dp))
    }
}
