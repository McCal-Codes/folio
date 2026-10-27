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

/** The ids of apps whose name another app in [apps] also uses (ignoring case and spaces at the ends), so a row can say which is which. */
internal fun idsWithSharedLabel(apps: List<Pair<String, String>>): Set<String> {
    val byName = apps.groupBy { it.second.trim().lowercase() }
    return byName.values.filter { it.size > 1 }.flatMap { group -> group.map { it.first } }.toSet()
}

/**
 * What is ticked when the page first opens. On the first run Home has only been filled by Folio's own first-refresh
 * suggestions (up to 16 apps), which nobody chose, so the starter set is offered instead. On a replay from Settings Home is
 * the person's own, so what is on it is what is ticked, or the starter set when it is empty.
 */
internal fun initialHomePicks(state: LauncherState, apps: List<AppEntry>, firstRun: Boolean = false): Set<String> =
    homePicksFor(firstRun, state.homeSlots.filterNotNull().filter { id -> apps.any { it.id == id } }) {
        starterAppIds(apps.map { it.id to it.component.packageName })
    }.toSet()

internal fun homePicksFor(firstRun: Boolean, onHome: List<String>, starters: () -> List<String>): List<String> =
    if (!firstRun && onHome.isNotEmpty()) onHome else starters()

/**
 * Home's slots once setup applies [picked], from the [offered] apps in the order the page lists them. Apps that were on Home, were
 * offered and are not ticked come off first, so the cells they free are the first places new apps go; nothing else is touched.
 * On the first run nobody has arranged Home yet (it only holds Folio's own suggestions), so the ticked apps are packed from the
 * first free cell in the order the page's preview shows them: no empty cell inside the page and no app stranded after a gap
 * (27 Oct sweep, F02: Chrome alone in row 4 under an empty row).
 */
internal fun homeSlotsForPicks(slots: List<String?>, offered: List<String>, picked: Set<String>, blocked: Set<Int>, appRows: Int, firstRun: Boolean): List<String?> {
    val offeredSet = offered.toSet()
    var result = normalizeHomeSlots(slots.map { if (it != null && it in offeredSet && it !in picked) null else it })
    if (firstRun) {
        val others = result.filterNotNull().filter { it !in offeredSet }
        return (offered.filter { it in picked } + others).fold(emptyList()) { packed, id -> pinHomeApp(packed, id, true, blocked, appRows) }
    }
    offered.filter { it in picked && it !in result }.forEach { result = pinHomeApp(result, it, true, blocked, appRows) }
    return result
}

/** Puts exactly the ticked apps on Home (see [homeSlotsForPicks]). Apps that were already on Home and are unticked come off. */
internal fun applyHomePicks(model: LauncherModel, apps: List<AppEntry>, picked: Set<String>, firstRun: Boolean) =
    model.applyHomePicks(apps.map { it.id }, picked, firstRun)

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
    val sameName = remember(apps) { idsWithSharedLabel(apps.map { it.id to it.label }) }
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
                    Column(Modifier.weight(1f).padding(horizontal = FolioSpace.MEDIUM.dp)) {
                        Text(app.label, color = Color.White, fontSize = FolioType.BODY.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        // Two apps with one name (Samsung's and Google's Calendar) can't be told apart by the name alone.
                        if (app.id in sameName) Text(app.component.packageName, color = Color.White.copy(alpha = .6f), fontSize = FolioType.FOOTNOTE.sp,
                            maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
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
