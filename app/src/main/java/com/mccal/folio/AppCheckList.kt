package com.mccal.folio

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A search field over a list of apps, each with a check: the one list behind choosing the apps in an Icon Stack and in
 * a folder. [apps] is already narrowed to what [query] matches; [enabled] false dims a row and ignores taps (a full stack).
 */
@Composable
internal fun AppCheckList(apps: List<AppEntry>, query: String, onQuery: (String) -> Unit, checked: (AppEntry) -> Boolean,
    enabled: (AppEntry) -> Boolean, checkedLabel: String, uncheckedLabel: String, onToggle: (AppEntry) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier) {
        IosSearchField(query, onQuery, "Search apps", Modifier.padding(vertical = FolioSpace.MEDIUM.dp))
        val listState = androidx.compose.foundation.lazy.rememberLazyListState()
        LazyColumn(Modifier.weight(1f).edgeFade(listState), state = listState) {
            items(apps, key = { it.id }) { app ->
                val on = checked(app)
                val usable = enabled(app)
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(enabled = usable) { onToggle(app) }
                    .graphicsLayer { alpha = if (usable) 1f else .4f }.padding(vertical = FolioSpace.SNUG.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(app, null, Modifier.size(40.dp), shape = RoundedCornerShape(FolioRadius.CONTROL.dp), badge = false)
                    Text(app.label, color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f).padding(start = FolioSpace.MEDIUM.dp))
                    Icon(if (on) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, if (on) checkedLabel else uncheckedLabel,
                        tint = if (on) LocalAccent.current.ink else Color.White.copy(alpha = .35f), modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

/** Choose several apps for a folder at once: tick any number, then Done puts them all in as one change. */
@Composable
internal fun FolderAppsEditor(folder: FolderEntry, apps: List<AppEntry>, onDone: (List<String>) -> Unit) {
    var query by remember { mutableStateOf("") }
    val picked = remember(folder.id) { mutableStateListOf<String>() }
    val shown = remember(apps, query) { apps.filter { appMatches(it, query.trim()) } }
    Column(Modifier.fillMaxWidth().fillMaxHeight(.85f).padding(horizontal = FolioSpace.LARGE.dp).testTag("folder-apps-editor")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (folder.title.isBlank()) stringResource(R.string.add_apps) else stringResource(R.string.add_apps_to_1, folder.title),
                    color = Color.White, fontSize = FolioType.TITLE.sp, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.n_selected, picked.size), color = Color.White.copy(alpha = .6f), fontSize = FolioType.FOOTNOTE.sp)
            }
            Text(stringResource(R.string.done), color = LocalAccent.current.ink, fontSize = FolioType.BODY.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(FolioRadius.CONTROL.dp)).clickable { onDone(picked.toList()) }
                    .padding(FolioSpace.SMALL.dp).testTag("folder-apps-done"))
        }
        AppCheckList(shown, query, { query = it }, checked = { it.id in folder.appIds || it.id in picked }, enabled = { it.id !in folder.appIds },
            checkedLabel = stringResource(R.string.app_selected), uncheckedLabel = stringResource(R.string.app_not_selected),
            onToggle = { if (it.id in picked) picked.remove(it.id) else picked.add(it.id) }, modifier = Modifier.weight(1f))
    }
}
