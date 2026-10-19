package com.mccal.folio

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** Setup's question, full screen over Settings. Back and Cancel call [onCancel] and change nothing; only Continue calls [onContinue]. */
@Composable
internal fun SimpleSetupDialog(initial: String, onCancel: () -> Unit, onContinue: (String) -> Unit) =
    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        SimpleSetup(initial, onCancel, onContinue)
    }

@Composable
internal fun SimpleSetup(initial: String, onCancel: () -> Unit, onContinue: (String) -> Unit) {
    var chosen by remember { mutableStateOf(if (initial == SIMPLE_HELPING) SIMPLE_HELPING else SIMPLE_ME) }
    BackHandler(onBack = onCancel)
    SimpleScreen {
        Text(stringResource(R.string.simple_setup_title), color = Ink, fontSize = FolioSimpleType.TITLE.sp, fontWeight = FontWeight.SemiBold)
        ChoiceCard(chosen == SIMPLE_ME, stringResource(R.string.simple_for_me), stringResource(R.string.simple_for_me_detail)) { chosen = SIMPLE_ME }
        ChoiceCard(chosen == SIMPLE_HELPING, stringResource(R.string.simple_for_helping), stringResource(R.string.simple_for_helping_detail)) { chosen = SIMPLE_HELPING }
        if (chosen == SIMPLE_HELPING) Text(stringResource(R.string.simple_helping_note), color = Ink, fontSize = FolioSimpleType.HINT.sp)
        BigButton(stringResource(R.string.simple_continue), filled = true) { onContinue(chosen) }
        BigButton(stringResource(R.string.cancel), filled = false, onClick = onCancel)
    }
}

/**
 * Where the people are changed: the list in order, each with Move up, Move down and Remove (buttons rather than
 * dragging, so TalkBack and a shaky finger both work), Add while there is room, and who the phone is for.
 */
@Composable
internal fun SimplePeopleEditor(state: LauncherState, model: LauncherModel, onAdd: () -> Unit, onDone: () -> Unit) {
    var removing by remember { mutableStateOf<Int?>(null) }
    val people = state.simpleFavorites
    BackHandler(onBack = onDone)
    SimpleScreen {
        Text(stringResource(R.string.simple_edit_people), color = Ink, fontSize = FolioSimpleType.TITLE.sp, fontWeight = FontWeight.SemiBold)
        people.forEachIndexed { index, person ->
            val label = person.name.ifBlank { person.phone }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(FolioRadius.PANEL.dp)).background(Glass).padding(FolioSpace.MEDIUM.dp),
                verticalArrangement = Arrangement.spacedBy(FolioSpace.SMALL.dp)) {
                Text(label, color = Ink, fontSize = FolioSimpleType.LABEL.sp, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(FolioSpace.SMALL.dp)) {
                    RoundButton(Icons.Rounded.KeyboardArrowUp, stringResource(R.string.simple_move_up, label), index > 0) { model.setSimpleFavorites(people.movedUp(index)) }
                    RoundButton(Icons.Rounded.KeyboardArrowDown, stringResource(R.string.simple_move_down, label), index < people.lastIndex) { model.setSimpleFavorites(people.movedDown(index)) }
                    RoundButton(Icons.Rounded.Close, stringResource(R.string.simple_remove, label), true) { removing = index }
                }
            }
        }
        if (people.size < SIMPLE_MAX_FAVORITES) BigButton(stringResource(R.string.simple_add_people), filled = false, icon = Icons.Rounded.Add, onClick = onAdd)
        SectionLabel(stringResource(R.string.simple_who_for))
        ChoiceCard(state.simpleFor == SIMPLE_ME, stringResource(R.string.simple_for_me), stringResource(R.string.simple_for_me_detail)) { model.setSimpleFor(SIMPLE_ME) }
        ChoiceCard(state.simpleFor == SIMPLE_HELPING, stringResource(R.string.simple_for_helping), stringResource(R.string.simple_for_helping_detail)) { model.setSimpleFor(SIMPLE_HELPING) }
        BigButton(stringResource(R.string.simple_done), filled = true, onClick = onDone)
    }
    removing?.let { index ->
        val label = people.getOrNull(index)?.let { it.name.ifBlank { it.phone } }
        if (label == null) removing = null
        else AlertDialog(onDismissRequest = { removing = null },
            title = { Text(stringResource(R.string.simple_remove_title, label)) },
            confirmButton = { TextButton(onClick = { removing = null; model.setSimpleFavorites(people.removedAt(index)) }, modifier = Modifier.heightIn(min = FolioTouch.MIN.dp)) {
                Text(stringResource(R.string.simple_remove_confirm), fontSize = FolioSimpleType.HINT.sp) } },
            dismissButton = { TextButton(onClick = { removing = null }, modifier = Modifier.heightIn(min = FolioTouch.MIN.dp)) {
                Text(stringResource(R.string.simple_leave_stay), fontSize = FolioSimpleType.HINT.sp) } })
    }
}

@Composable
private fun SimpleScreen(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val palette = LocalDuoPalette.current
    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(palette.backgroundTop, palette.backgroundBottom)))
        .statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = FolioSpace.LARGE.dp, vertical = FolioSpace.XL.dp),
        verticalArrangement = Arrangement.spacedBy(FolioSpace.LARGE.dp), content = content)
}

/** One of two answers: the whole card is the button, and "selected" is said as well as drawn. */
@Composable
private fun ChoiceCard(selected: Boolean, title: String, detail: String, onClick: () -> Unit) {
    val accent = LocalAccent.current.fill
    Column(Modifier.fillMaxWidth().heightIn(min = 120.dp).clip(RoundedCornerShape(FolioRadius.PANEL.dp)).background(Glass)
        .border(if (selected) 3.dp else 0.dp, if (selected) accent else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(FolioRadius.PANEL.dp))
        .semantics { this.selected = selected }
        .clickable(role = Role.RadioButton, onClick = onClick).padding(FolioSpace.LARGE.dp),
        verticalArrangement = Arrangement.spacedBy(FolioSpace.SMALL.dp)) {
        Text(title, color = Ink, fontSize = FolioSimpleType.LABEL.sp, fontWeight = FontWeight.SemiBold)
        Text(detail, color = Ink, fontSize = FolioSimpleType.HINT.sp)
    }
}

@Composable
private fun BigButton(text: String, filled: Boolean, icon: ImageVector? = null, onClick: () -> Unit) {
    val accent = LocalAccent.current
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).clip(RoundedCornerShape(FolioRadius.PANEL.dp))
        .background(if (filled) accent.fill else Glass).clickable(role = Role.Button, onClick = onClick).padding(FolioSpace.MEDIUM.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        icon?.let { Icon(it, null, tint = Ink, modifier = Modifier.size(32.dp).padding(end = FolioSpace.SMALL.dp)) }
        Text(text, color = if (filled) androidx.compose.ui.graphics.Color.White else Ink, fontSize = FolioSimpleType.LABEL.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** An icon-only button 56 dp square, named for what it does to whom ("Move Mom up"). A disabled one is dimmed and says nothing. */
@Composable
private fun RoundButton(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    Column(Modifier.size(56.dp).clip(RoundedCornerShape(FolioRadius.GROUP.dp)).background(Glass)
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick).semantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(icon, null, tint = if (enabled) Ink else Ink.copy(alpha = .3f), modifier = Modifier.size(32.dp))
    }
}
