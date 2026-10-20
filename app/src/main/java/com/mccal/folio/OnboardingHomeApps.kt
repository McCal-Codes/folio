package com.mccal.folio

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The apps first setup offers for Home: this phone's own, personal profile, shown on Home and not hidden. */
internal fun onboardingApps(state: LauncherState): List<AppEntry> =
    state.apps.filter { it.available && !it.isWork && it.id !in state.hiddenApps && !it.isShortcut }.sortedBy { it.label.lowercase() }

/** What is ticked when the page first opens: what is already on Home, or the starter apps when Home is empty. */
internal fun initialHomePicks(state: LauncherState, apps: List<AppEntry>): Set<String> {
    val onHome = state.homeSlots.filterNotNull().filter { id -> apps.any { it.id == id } }
    return (if (onHome.isNotEmpty()) onHome else starterAppIds(apps.map { it.id to it.component.packageName })).toSet()
}

/** Puts exactly the ticked apps on Home. Apps that were already on Home and are unticked come off; nothing else is touched. */
internal fun applyHomePicks(model: LauncherModel, state: LauncherState, apps: List<AppEntry>, picked: Set<String>) {
    val onHome = state.homeSlots.filterNotNull().toSet()
    apps.forEach { app ->
        val want = app.id in picked
        if (want && app.id !in onHome) model.setPinned(app.id, true)
        else if (!want && app.id in onHome) model.setPinned(app.id, false)
    }
}

/**
 * Pick what goes on Home on one screen, with the layout shown live as you tap. Folio ticks one app per everyday job it
 * finds on this phone; everything else is a tap away under All apps. Nothing is applied until Continue.
 */
@Composable
internal fun HomeAppsPage(apps: List<AppEntry>, picked: Set<String>, onToggle: (String) -> Unit) {
    var showAll by rememberSaveable { mutableStateOf(false) }
    val suggested = remember(apps) { starterAppIds(apps.map { it.id to it.component.packageName }, max = STARTER_APP_GROUPS.size).toSet() }
    val shown = remember(apps, showAll, suggested, picked) {
        if (showAll) apps else apps.filter { it.id in suggested || it.id in picked }
    }
    val chosen = remember(apps, picked) { apps.filter { it.id in picked } }
    val rows = homeRowsFor(chosen.size)
    Column(Modifier.fillMaxWidth().padding(top = FolioSpace.XL.dp)) {
        // The live preview: Home's grid as it will be, icons as they are.
        val summary = if (chosen.isEmpty()) stringResource(R.string.home_apps_none)
            else pluralStringResource(R.plurals.home_apps_count, chosen.size, chosen.size) + " · " + pluralStringResource(R.plurals.home_rows_count, rows, rows)
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(FolioRadius.PANEL.dp)).background(Color.White.copy(alpha = .08f))
            .padding(FolioSpace.LARGE.dp).semantics(mergeDescendants = true) { contentDescription = summary }.testTag("home-apps-preview"),
            horizontalAlignment = Alignment.CenterHorizontally) {
            if (chosen.isNotEmpty()) Column(Modifier.widthIn(max = 260.dp), verticalArrangement = Arrangement.spacedBy(FolioSpace.COMPACT.dp)) {
                chosen.chunked(GRID_COLUMNS).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(FolioSpace.COMPACT.dp)) {
                        row.forEach { app -> Image(app.icon.asImageBitmap(), null, Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(FolioRadius.CARD.dp))) }
                        repeat(GRID_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            Text(summary, color = Color.White, fontSize = FolioType.SUBHEAD.sp, modifier = Modifier.padding(top = if (chosen.isEmpty()) 0.dp else FolioSpace.MEDIUM.dp))
        }
        SheetGroup(Modifier.padding(top = FolioSpace.LARGE.dp)) {
            shown.forEachIndexed { n, app ->
                if (n > 0) MenuDivider()
                val on = app.id in picked
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(on, role = Role.Checkbox, onValueChange = { onToggle(app.id) })
                    .padding(horizontal = FolioSpace.LARGE.dp).testTag("home-app-${app.component.packageName}"), verticalAlignment = Alignment.CenterVertically) {
                    Image(app.icon.asImageBitmap(), null, Modifier.size(32.dp).clip(RoundedCornerShape(FolioRadius.CONTROL.dp)))
                    Text(app.label, color = Color.White, fontSize = FolioType.BODY.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(horizontal = FolioSpace.MEDIUM.dp))
                    Icon(if (on) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, null,
                        tint = if (on) LocalAccent.current.ink else Color.White.copy(alpha = .4f))
                }
            }
        }
        if (!showAll && apps.size > shown.size) Text(stringResource(R.string.home_apps_show_all), color = LocalAccent.current.ink, fontSize = FolioType.BODY.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = FolioSpace.COMPACT.dp).clip(RoundedCornerShape(FolioRadius.CONTROL.dp)).clickable(role = Role.Button) { showAll = true }
                .heightIn(min = 48.dp).padding(FolioSpace.COMPACT.dp).testTag("home-apps-show-all"))
    }
}
