package com.mccal.folio

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Settings › Gestures & Actions › Icon Actions: every icon that has an action, opening the same editor as Edit Icon, and
 * Reset All with an Undo while the page is open. An icon whose app is gone is left out; its actions go with the app.
 */
@Composable
internal fun IconActionsSettingsPage(apps: List<AppEntry>, actions: Map<String, IconActions>,
    onSet: (String, IconActions) -> Unit, onReplaceAll: (Map<String, IconActions>) -> Unit) {
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    // What Reset All cleared, so it can be put back until the page closes.
    var beforeReset by remember { mutableStateOf<Map<String, IconActions>?>(null) }
    val byId = remember(apps) { apps.associateBy { it.id } }
    editing?.let { id -> byId[id]?.let { app ->
        IconActionsEditor(app, actions[id] ?: IconActions(), apps, { onSet(id, it) }, onBack = { editing = null },
            backLabel = stringResource(R.string.icon_actions_title))
        return
    } }
    val withActions = actions.keys.mapNotNull { byId[it] }.sortedBy { it.label.lowercase() }
    SettingsCard(null) {
        if (withActions.isEmpty()) Text(stringResource(R.string.icon_actions_none_yet), color = Color.White.copy(alpha = .7f), fontSize = FolioType.BODY.sp,
            modifier = Modifier.padding(vertical = FolioSpace.MEDIUM.dp).testTag("icon-actions-none"))
        withActions.forEach { app -> key(app.id) {
            val rows = iconActionMenuRows(actions[app.id]) {}
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button) { editing = app.id }.testTag("icon-actions-app-${app.id}"),
                verticalAlignment = Alignment.CenterVertically) {
                AppIcon(app, null, Modifier.size(32.dp), shape = RoundedCornerShape(FolioRadius.CONTROL.dp), badge = false)
                Column(Modifier.weight(1f).padding(start = FolioSpace.MEDIUM.dp, top = FolioSpace.SNUG.dp, bottom = FolioSpace.SNUG.dp)) {
                    Text(app.label, color = Color.White, fontSize = FolioType.BODY.sp)
                    rows.forEach { (line, _) -> Text(line, color = Color.White.copy(alpha = .7f), fontSize = FolioType.FOOTNOTE.sp) }
                }
                Icon(Icons.Rounded.ChevronRight, null, tint = Color.White.copy(alpha = .3f), modifier = Modifier.mirroredForRtl())
            }
        } }
    }
    CardNote(stringResource(R.string.icon_actions_settings_note))
    beforeReset?.let { previous ->
        IosActionRow(stringResource(R.string.icon_actions_undo_reset), tag = "icon-actions-undo-reset", onClick = { onReplaceAll(previous); beforeReset = null })
    }
    if (actions.isNotEmpty()) IosActionRow(stringResource(R.string.icon_actions_reset_all), tag = "icon-actions-reset-all", destructive = true, onClick = {
        beforeReset = actions; onReplaceAll(emptyMap())
    })
}
