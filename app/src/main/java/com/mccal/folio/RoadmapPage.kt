package com.mccal.folio

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Settings › General › Roadmap. The groups come from `roadmap.json` and the installed version ([Roadmap.group]): the
 * release in progress as Now, later releases as Next, the lists with a title as they are named, and what already
 * shipped folded under one row. Honest statuses, no dates; an item in the current beta says "In beta", never "Done".
 */
private enum class RoadmapPhase { Idle, Checking, Offline }

@Composable internal fun ComingSoonPage() {
    val context = LocalContext.current
    val version = remember { SoftwareUpdate.installedVersion(context) }
    var content by remember { mutableStateOf(Roadmap.local(context)) }
    var savedAt by remember { mutableStateOf(Roadmap.savedAt(context)) }
    var phase by remember { mutableStateOf(RoadmapPhase.Idle) }
    var earlierOpen by rememberSaveable { mutableStateOf(false) }
    var openRelease by rememberSaveable { mutableStateOf<String?>(null) }
    // Opening the Roadmap is when it checks GitHub for a newer one (at most every few hours).
    LaunchedEffect(Unit) {
        if (!Roadmap.isStale(context)) return@LaunchedEffect
        phase = RoadmapPhase.Checking
        when (val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Roadmap.refresh(context) }) {
            is Roadmap.Refresh.Updated -> { content = result.content; savedAt = Roadmap.savedAt(context); phase = RoadmapPhase.Idle }
            Roadmap.Refresh.Recent -> phase = RoadmapPhase.Idle
            Roadmap.Refresh.Failed -> phase = RoadmapPhase.Offline
        }
    }
    val groups = remember(content, version) { content?.let { Roadmap.group(it, version, SoftwareUpdate::isNewer) } }

    CardNote(content?.note ?: stringResource(R.string.where_folio_is_headed), Modifier.padding(horizontal = FolioSpace.TINY.dp))
    if (phase != RoadmapPhase.Idle) {
        Row(Modifier.padding(horizontal = FolioSpace.TINY.dp).semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FolioSpace.SMALL.dp)) {
            Icon(if (phase == RoadmapPhase.Checking) Icons.Rounded.Refresh else Icons.Rounded.CloudOff, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            // With nothing downloaded yet, the copy shown is the one that came with Folio, not one "saved on this phone".
            Text(stringResource(if (phase == RoadmapPhase.Checking) R.string.roadmap_checking else if (savedAt == null) R.string.roadmap_offline_bundled else R.string.roadmap_offline),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    groups?.now?.let { section ->
        RoadmapHeader(stringResource(R.string.roadmap_now), releaseTitle(section))
        RoadmapCard {
            // "Ready to try" is only true for someone on that release's beta; for everyone else the labels say enough.
            RoadmapProgress(section, show = section.release == Roadmap.releaseOf(version))
            section.items.forEach { RoadmapRow(it, showAll = true) }
        }
    }
    groups?.next?.forEachIndexed { index, section ->
        RoadmapHeader(if (index == 0) stringResource(R.string.roadmap_next) else null, releaseTitle(section))
        RoadmapCard { section.items.forEach { RoadmapRow(it, showAll = false) } }
    }
    groups?.titled?.forEach { section ->
        RoadmapHeader(section.title.orEmpty(), null)
        RoadmapCard { section.items.forEach { RoadmapRow(it, showAll = false) } }
    }
    if (groups != null && groups.shipped.isNotEmpty()) {
        RoadmapHeader(stringResource(R.string.roadmap_shipped), null)
        RoadmapCard {
            RoadmapFoldRow(stringResource(R.string.roadmap_earlier_releases), groups.shipped.size, earlierOpen,
                stringResource(if (earlierOpen) R.string.roadmap_hide_earlier else R.string.roadmap_show_earlier)) { earlierOpen = !earlierOpen }
            if (earlierOpen) groups.shipped.forEach { section ->
                val release = section.release.orEmpty()
                val open = openRelease == release
                RoadmapFoldRow(stringResource(R.string.folio_version, release), section.items.size, open, null) { openRelease = if (open) null else release }
                if (open) section.items.forEach { RoadmapRow(it, showAll = false, past = true) }
            }
        }
    }
    SheetGroup {
        IosActionRow(stringResource(R.string.suggest_a_feature), "coming-soon-suggest") {
            runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,
                android.net.Uri.parse(BugReport.NEW_ISSUE + "?template=feature_request.yml"))) }
        }
    }
    savedAt?.let { saved ->
        val ago = android.text.format.DateUtils.getRelativeTimeSpanString(saved, System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS).toString()
        Text(stringResource(if (phase == RoadmapPhase.Offline) R.string.roadmap_saved_copy else R.string.roadmap_updated, ago),
            Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

/** "Folio 0.6.9 · Foundation": the release, then its theme when the file gives one. */
@Composable private fun releaseTitle(section: Roadmap.Section): String {
    val release = stringResource(R.string.folio_version, section.release.orEmpty())
    return section.subtitle?.let { "$release · $it" } ?: release
}

/** A group's header: its name on the left and, for a release, which one on the right. Both read as one heading. */
@Composable private fun RoadmapHeader(left: String?, right: String?) {
    Row(Modifier.fillMaxWidth().padding(start = FolioSpace.LARGE.dp, end = FolioSpace.LARGE.dp, top = FolioSpace.COMPACT.dp)
        .semantics(mergeDescendants = true) { heading() }, horizontalArrangement = Arrangement.spacedBy(FolioSpace.MEDIUM.dp)) {
        // The name keeps the room it needs; a long release title takes what is left and wraps, never the other way round.
        if (!left.isNullOrEmpty()) Text(left.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (right != null) Text(right.uppercase(), Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = if (left.isNullOrEmpty()) androidx.compose.ui.text.style.TextAlign.Start else androidx.compose.ui.text.style.TextAlign.End)
        else Spacer(Modifier.weight(1f))
    }
}

@Composable private fun RoadmapCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    GroupedCard(MaterialTheme.colorScheme.surfaceContainerHigh, Color.White.copy(alpha = .12f), Modifier.fillMaxWidth(), content)
}

/** "8 of 11 ready to try" with a slim bar. Done and in-beta items count; a finished release says "All 11 done". */
@Composable private fun RoadmapProgress(section: Roadmap.Section, show: Boolean) {
    val (ready, total) = Roadmap.ready(section)
    val allDone = section.items.all { it.status == Roadmap.Status.DONE }
    if (!show && !allDone) return
    val text = if (allDone) stringResource(R.string.roadmap_all_done, total) else stringResource(R.string.roadmap_ready_to_try, ready, total)
    Row(Modifier.fillMaxWidth().padding(horizontal = FolioSpace.LARGE.dp, vertical = FolioSpace.MEDIUM.dp).semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FolioSpace.MEDIUM.dp)) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(Modifier.weight(1f).height(3.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Color.White.copy(alpha = .14f))) {
            Box(Modifier.fillMaxWidth(if (total == 0) 0f else ready.toFloat() / total).fillMaxHeight().background(Color.White.copy(alpha = .62f)))
        }
    }
}

/** "Earlier releases" and each past release: a count and a chevron, opened in place. */
@Composable private fun RoadmapFoldRow(title: String, count: Int, open: Boolean, onClickLabel: String?, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = FolioRow.ACTION.dp).clickable(role = Role.Button, onClickLabel = onClickLabel, onClick = onClick)
        .padding(horizontal = FolioSpace.LARGE.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), color = Color.White, fontSize = FolioType.BODY.sp)
        Text(count.toString(), color = Color.White.copy(alpha = .55f), fontSize = FolioType.BODY.sp)
        Spacer(Modifier.width(FolioSpace.TINY.dp))
        Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = Color.White.copy(alpha = .3f))
    }
}

/**
 * One item: its icon, title and detail, and one quiet status. A list row, not a button: nothing happens on a tap, so it
 * carries no click and no focus stop. On a narrow window the status moves under the text instead of squeezing it.
 */
@Composable private fun RoadmapRow(item: Roadmap.Item, showAll: Boolean, past: Boolean = false) {
    val status: String? = when {
        past -> null
        item.status == Roadmap.Status.DONE -> if (showAll) stringResource(R.string.roadmap_done) else null
        item.status == Roadmap.Status.BUILDING -> stringResource(if (item.beta) R.string.roadmap_in_beta else R.string.in_progress)
        showAll && item.status == Roadmap.Status.PLANNED -> stringResource(R.string.planned)
        else -> null
    }
    val done = status != null && item.status == Roadmap.Status.DONE
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val narrow = maxWidth < 420.dp
        Row(Modifier.fillMaxWidth().padding(horizontal = FolioSpace.LARGE.dp, vertical = FolioSpace.MEDIUM.dp).semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.Top) {
            Box(Modifier.size(30.dp).clip(RoundedCornerShape(FolioRadius.CONTROL.dp)).background(Color(item.color)), contentAlignment = Alignment.Center) {
                Icon(roadmapIcon(item.icon), null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(FolioSpace.MEDIUM.dp))
            Column(Modifier.weight(1f)) {
                Text(item.title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                if (item.detail.isNotEmpty()) Text(item.detail, color = Color.White.copy(alpha = .62f), fontSize = 14.sp, lineHeight = 19.sp)
                if (status != null && narrow) RoadmapStatusLabel(status, done, Modifier.padding(top = FolioSpace.TINY.dp))
            }
            if (status != null && !narrow) RoadmapStatusLabel(status, done, Modifier.padding(start = FolioSpace.MEDIUM.dp).align(Alignment.CenterVertically))
        }
    }
}

/** "Done" is green with a check, so color is never the only cue; every other status is plain secondary text. */
@Composable private fun RoadmapStatusLabel(text: String, done: Boolean, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FolioSpace.TINY.dp)) {
        if (done) Icon(Icons.Rounded.CheckCircle, null, tint = Color(FolioColors.Value.Green), modifier = Modifier.size(18.dp))
        Text(text, color = if (done) Color(FolioColors.Value.Green) else Color.White.copy(alpha = .62f), fontSize = 13.sp, maxLines = 1, softWrap = false)
    }
}

/** Icons a roadmap file can name; anything else shows a star. */
private fun roadmapIcon(name: String): ImageVector = when (name) {
    "bug" -> Icons.Rounded.BugReport; "tune" -> Icons.Rounded.Tune; "folder" -> Icons.Rounded.Folder; "apps" -> Icons.Rounded.Apps
    "clock" -> Icons.Rounded.Schedule; "badge" -> Icons.Rounded.Notifications; "notifications" -> Icons.Rounded.NotificationsActive
    "extension" -> Icons.Rounded.Extension; "update" -> Icons.Rounded.SystemUpdate; "circle" -> Icons.Rounded.Circle
    "rings" -> Icons.Rounded.DonutLarge; "corner" -> Icons.Rounded.RoundedCorner; "headphones" -> Icons.Rounded.Headphones
    "weather" -> Icons.Rounded.WbSunny; "store" -> Icons.Rounded.Storefront; "grid" -> Icons.Rounded.GridView
    "history" -> Icons.Rounded.History; "tap" -> Icons.Rounded.TouchApp; "pages" -> Icons.Rounded.ViewCarousel
    "dock" -> Icons.Rounded.Dock; "lock" -> Icons.Rounded.Lock; "news" -> Icons.Rounded.Newspaper; "brush" -> Icons.Rounded.Brush
    "sensor" -> Icons.Rounded.Sensors; "keyboard" -> Icons.Rounded.Keyboard; "palette" -> Icons.Rounded.Palette
    "redeem" -> Icons.Rounded.Redeem; "language" -> Icons.Rounded.Translate; "search" -> Icons.Rounded.Search
    "speed" -> Icons.Rounded.Speed; "accessibility" -> Icons.Rounded.Accessibility; "globe" -> Icons.Rounded.Public
    "person" -> Icons.Rounded.Person; "video" -> Icons.Rounded.Videocam
    else -> Icons.Rounded.Star
}
