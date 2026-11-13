package com.mccal.folio

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Cards the newest release shows before the rest fold into "More in <version>", so older releases and What's New stay near the top. */
private const val FEATURED_CARDS = 5

/** A tip: the changelog's feature (its title and detail), with the symbol and two colors its title picks, for a card in Apple's Tips style. */
private data class Tip(val note: NoteItem, val icon: ImageVector, val top: Color, val bottom: Color) {
    val title: String get() = note.title ?: WhatsNew.firstSentence(note.detail)
}

private fun tipOf(note: NoteItem): Tip {
    val (icon, color) = WhatsNew.symbol(note.title ?: note.detail)
    val base = Color(color)
    return Tip(note, icon, base, lerp(base, Color.Black, .5f))
}

/**
 * Settings > General > Tips, laid out like Apple's Tips app: one featured tip as a large colored card, the rest of the release as
 * cards with a colored header, each opening to the whole tip, and earlier releases as collections you open. Every card is a
 * feature from CHANGELOG.md, the list What's New shows once after an update, so a feature added there appears here with no
 * second list to keep. What's New and, on Folio Dev, This build are one tap away at the foot.
 */
@Composable
internal fun TipsPage(onShowWhatsNew: () -> Unit, onShowBuild: (() -> Unit)?) {
    val context = LocalContext.current
    val releases = remember { WhatsNew.notes(context).map { r -> r to r.sections.filter { it.first.equals("Added", true) }.flatMap { it.second }.map { tipOf(WhatsNew.split(it)) } }.filter { it.second.isNotEmpty() } }
    var expanded by remember { mutableStateOf(setOf<String>()) }
    var open by remember { mutableStateOf<Tip?>(null) }
    Column(Modifier.padding(horizontal = FolioSpace.LARGE.dp), verticalArrangement = Arrangement.spacedBy(FolioSpace.MEDIUM.dp)) {
        if (releases.isEmpty()) CardNote(stringResource(R.string.tips_none))
        releases.forEachIndexed { index, (release, tips) ->
            if (index == 0) {
                TipHero(tips.first(), stringResource(R.string.tips_new_in, release.version)) { open = tips.first() }
                tips.drop(1).take(FEATURED_CARDS).forEach { tip -> TipCard(tip) { open = tip } }
                val rest = tips.drop(1 + FEATURED_CARDS)
                if (rest.isNotEmpty()) {
                    val key = "more-" + release.version
                    val isOpen = key in expanded
                    SheetGroup {
                        DisclosureRow(stringResource(R.string.tips_more_in, release.version), open = isOpen, count = rest.size, tag = "tips-more-${release.version}") {
                            expanded = if (isOpen) expanded - key else expanded + key
                        }
                    }
                    if (isOpen) rest.forEach { tip -> TipCard(tip) { open = tip } }
                }
            } else {
                val isOpen = release.version in expanded
                SheetGroup {
                    DisclosureRow(stringResource(R.string.tips_new_in, release.version), open = isOpen, count = tips.size, tag = "tips-release-${release.version}") {
                        expanded = if (isOpen) expanded - release.version else expanded + release.version
                    }
                }
                if (isOpen) tips.forEach { tip -> TipCard(tip) { open = tip } }
            }
        }
        SheetGroup {
            TweakRow(Icons.Rounded.NewReleases, FolioColors.Value.Green, stringResource(R.string.what_s_new), "tips-whats-new", "v" + WhatsNew.currentVersion(context)) { onShowWhatsNew() }
            if (onShowBuild != null) {
                MenuDivider()
                TweakRow(Icons.Rounded.Build, FolioColors.Value.Orange, DevBuild.THIS_BUILD_LABEL, "tips-dev-build", DevBuild.load(context)?.let { DevBuild.buildLabel(it) ?: it.sha }) { onShowBuild() }
            }
        }
        CardNote(stringResource(R.string.tips_footer))
    }
    open?.let { TipSheet(it) { open = null } }
}

/** The featured tip: a tall card in the tip's color, its release over the title, like the big card that opens Apple's Tips. */
@Composable
private fun TipHero(tip: Tip, caption: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(FolioRadius.GROUPED_CARD.dp))
        .background(Brush.linearGradient(listOf(tip.top, tip.bottom)))
        .clickable(role = Role.Button, onClick = onClick).heightIn(min = 220.dp).padding(FolioSpace.XL.dp).testTag("tips-hero"),
        verticalArrangement = Arrangement.SpaceBetween) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(Color.White.copy(alpha = .22f)), contentAlignment = Alignment.Center) {
            Icon(tip.icon, null, tint = Color.White, modifier = Modifier.size(28.dp))
        }
        Column(Modifier.padding(top = FolioSpace.XL.dp), verticalArrangement = Arrangement.spacedBy(FolioSpace.SMALL.dp)) {
            Text(caption.uppercase(), color = Color.White.copy(alpha = .8f), fontSize = FolioType.GROUP_LABEL.sp, fontWeight = FontWeight.SemiBold, letterSpacing = .6.sp)
            Text(tip.title, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold, lineHeight = 32.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text(MarketText.inline(WhatsNew.firstSentence(tip.note.detail), Color.White), color = Color.White.copy(alpha = .92f), fontSize = FolioType.SUBHEAD.sp,
                maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** One tip: a colored header with its symbol, then the title and the first sentence. Tapping opens the whole tip. */
@Composable
private fun TipCard(tip: Tip, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(FolioRadius.GROUPED_CARD.dp)).background(FolioColors.SheetSurface)
        .clickable(role = Role.Button, onClick = onClick).testTag("tips-card")) {
        Box(Modifier.fillMaxWidth().heightIn(min = 96.dp).background(Brush.linearGradient(listOf(tip.top, tip.bottom))), contentAlignment = Alignment.Center) {
            Icon(tip.icon, null, tint = Color.White, modifier = Modifier.size(40.dp))
        }
        Column(Modifier.padding(FolioSpace.LARGE.dp), verticalArrangement = Arrangement.spacedBy(FolioSpace.TINY.dp)) {
            Text(tip.title, color = Color.White, fontSize = FolioType.BODY.sp, fontWeight = FontWeight.SemiBold, maxLines = if (tip.note.title == null) 4 else 2, overflow = TextOverflow.Ellipsis)
            // An older entry has no bold title, so its title is already its first sentence: the line under it would say the same thing twice.
            if (tip.note.title != null) Text(MarketText.inline(WhatsNew.firstSentence(tip.note.detail), LocalAccent.current.ink), color = Color.White.copy(alpha = .78f), fontSize = FolioType.SUBHEAD.sp,
                maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** The whole tip in a sheet: its colored banner, the title and everything the changelog says, then Done. */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun TipSheet(tip: Tip, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = FolioSpace.XXL.dp).padding(bottom = FolioSpace.LARGE.dp).testTag("tips-sheet"),
            verticalArrangement = Arrangement.spacedBy(FolioSpace.MEDIUM.dp)) {
            Box(Modifier.fillMaxWidth().heightIn(min = 140.dp).clip(RoundedCornerShape(FolioRadius.GROUPED_CARD.dp))
                .background(Brush.linearGradient(listOf(tip.top, tip.bottom))), contentAlignment = Alignment.Center) {
                Icon(tip.icon, null, tint = Color.White, modifier = Modifier.size(56.dp))
            }
            Text(tip.title, color = Color.White, fontSize = FolioType.TITLE.sp, fontWeight = FontWeight.Bold)
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                Text(MarketText.inline(tip.note.detail, LocalAccent.current.ink), color = Color.White.copy(alpha = .85f), fontSize = FolioType.CALLOUT.sp, lineHeight = 22.sp)
            }
            FolioButton(stringResource(R.string.done), onDismiss, modifier = Modifier.fillMaxWidth().testTag("tips-done"))
        }
    }
}
