package com.mccal.folio

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.delay

/** What Reset Control Center changed, kept for a few seconds so Undo can put it back. */
/** Where Control Center sits on a wide window: under the top right corner (as before), in the middle, or under the top left for a left handed grip. */
internal enum class CcSide(val label: Int) { RIGHT(R.string.cc_side_right), MIDDLE(R.string.cc_side_middle), LEFT(R.string.cc_side_left) }

internal fun ccSideOf(centered: Boolean, left: Boolean): CcSide = if (centered) CcSide.MIDDLE else if (left) CcSide.LEFT else CcSide.RIGHT

/**
 * The side choice as three small screens, each with the panel drawn where it would sit, so the difference is plain before it is picked.
 * A tap applies it at once like every row here; TalkBack reads each as one choice in a group.
 */
@Composable
internal fun CcSidePicker(selected: CcSide, enabled: Boolean = true, onSelect: (CcSide) -> Unit) {
    val ink = LocalContentColor.current.copy(alpha = if (enabled) LocalContentColor.current.alpha else .4f)
    Column(Modifier.fillMaxWidth().padding(vertical = FolioSpace.SNUG.dp), verticalArrangement = Arrangement.spacedBy(FolioSpace.SMALL.dp)) {
        Text(stringResource(R.string.control_center_side), fontSize = FolioType.BODY.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            CcSide.entries.forEach { side ->
                val on = side == selected
                val name = stringResource(side.label)
                Column(Modifier.heightIn(min = FolioTouch.MIN.dp).selectable(on, enabled = enabled, role = androidx.compose.ui.semantics.Role.RadioButton) { onSelect(side) }
                    .semantics(mergeDescendants = true) {}.padding(FolioSpace.TINY.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(FolioSpace.TINY.dp)) {
                    // A wide window, and the panel as the block under the top corner it opens from.
                    Box(Modifier.size(88.dp, 64.dp).clip(RoundedCornerShape(10.dp)).background(ink.copy(alpha = .08f))
                        .border(if (on) 2.dp else 1.dp, if (on) FolioColors.Blue else ink.copy(alpha = .2f), RoundedCornerShape(10.dp)).padding(6.dp),
                        contentAlignment = when (side) { CcSide.RIGHT -> Alignment.TopEnd; CcSide.MIDDLE -> Alignment.TopCenter; CcSide.LEFT -> Alignment.TopStart }) {
                        Box(Modifier.size(36.dp, 46.dp).clip(RoundedCornerShape(6.dp)).background(if (on) FolioColors.Blue else ink.copy(alpha = .45f)))
                    }
                    Text(name, fontSize = FolioType.FOOTNOTE.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
    }
}

/**
 * The look choice as three small panels, each with four tiles drawn the way that look draws them over a colorful backdrop (Clear is see-through,
 * Frosted smoky, Solid opaque), so the difference shows before it is picked. A tap applies it at once; TalkBack reads each as one choice.
 */
@Composable
internal fun CcLookPicker(selected: CcLook, onSelect: (CcLook) -> Unit) {
    val ink = LocalContentColor.current
    Column(Modifier.fillMaxWidth().padding(vertical = FolioSpace.SNUG.dp).testTag("cc-look"), verticalArrangement = Arrangement.spacedBy(FolioSpace.SMALL.dp)) {
        Text(stringResource(R.string.control_center_look), fontSize = FolioType.BODY.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            CcLook.entries.forEach { look ->
                val on = look == selected
                val tile = when (look) { CcLook.CLEAR -> Color.White.copy(alpha = .16f); CcLook.FROSTED -> FolioGlass.frostedTile; CcLook.SOLID -> FolioColors.MenuSurface }
                val edge = if (look == CcLook.CLEAR) Color.White.copy(alpha = .5f) else Color.Transparent
                Column(Modifier.heightIn(min = FolioTouch.MIN.dp).selectable(on, role = androidx.compose.ui.semantics.Role.RadioButton) { onSelect(look) }
                    .semantics(mergeDescendants = true) {}.padding(FolioSpace.TINY.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(FolioSpace.TINY.dp)) {
                    Box(Modifier.size(88.dp, 64.dp).clip(RoundedCornerShape(FolioRadius.CONTROL.dp))
                        .background(Brush.linearGradient(listOf(FolioColors.Blue, FolioColors.Purple, FolioColors.Orange)))
                        .border(if (on) 2.dp else 1.dp, if (on) FolioColors.Blue else ink.copy(alpha = .2f), RoundedCornerShape(FolioRadius.CONTROL.dp)).padding(8.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            repeat(2) {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    repeat(2) { Box(Modifier.size(width = 32.dp, height = 18.dp).clip(RoundedCornerShape(FolioRadius.CONTROL.dp)).background(tile).border(1.dp, edge, RoundedCornerShape(FolioRadius.CONTROL.dp))) }
                                }
                            }
                        }
                    }
                    Text(stringResource(look.label), fontSize = FolioType.FOOTNOTE.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
    }
}

/**
 * "Compact panel on wide screens" and, under it, the side choice. On a wide window Control Center fills the window by default, so there
 * is nowhere to move it; compact makes it a panel of about 520 dp that Right, Middle and Left place under the top corner. The side choice
 * stays on screen but dimmed until compact is on, so what turns it on is plain.
 */
@Composable
internal fun CcSideChoice(state: LauncherState, model: LauncherModel) {
    SettingsSwitch(stringResource(R.string.cc_rail), state.ccOptions.rail, { model.setCcOptions(state.ccOptions.copy(rail = it)) }, "cc-rail-switch")
    CardNote(stringResource(R.string.cc_rail_note))
    SettingsSwitch(stringResource(R.string.cc_compact_panel), state.ccOptions.compact, { model.setCcOptions(state.ccOptions.copy(compact = it)) }, "cc-compact-switch")
    CardNote(stringResource(R.string.cc_compact_note))
    CcSidePicker(ccSideOf(state.ccCentered, state.ccLeft), enabled = state.ccOptions.compact, onSelect = model::setCcSide)
}

private data class CcSettingsSnapshot(val left: Boolean, val size: PanelSize, val centered: Boolean, val look: CcLook, val options: CcOptions, val layout: CcSavedLayout?)

/**
 * The Control Center part of Settings > Notifications & Control Center, in short cards (FeatureGate.CC_SETTINGS_V2): the size and look
 * that were always here, then Pages, Sliders, Controls and Reset. Every row applies at once, as the rest of Settings does.
 * Reset puts size, look, "in the middle" and the options back and leaves the chosen controls alone; Undo is offered for
 * [CC_RESET_UNDO_MS].
 */
@Composable
internal fun ControlCenterSettingsCards(state: LauncherState, model: LauncherModel, onEditControlCenter: () -> Unit) {
    val options = state.ccOptions
    fun set(next: CcOptions) = model.setCcOptions(next)
    var undo by remember { mutableStateOf<CcSettingsSnapshot?>(null) }
    LaunchedEffect(undo) { if (undo != null) { delay(CC_RESET_UNDO_MS); undo = null } }

    SettingsCard(stringResource(R.string.control_center)) {
        IosMenuRow(stringResource(R.string.control_center_size), PanelSize.entries.map { it to stringResource(it.label) }, state.ccSize, model::setCcSize, tag = "cc-size")
        CcSizeNote()
        CcLookPicker(state.ccLook, model::setCcLook)
        CardNote(stringResource(R.string.cc_look_note))
        // Side by side puts Control Center on the right itself, and a card sits under its own corner, so only Full screen can centre it.
        if (state.ncLayout == NcLayout.FULL_SCREEN) CcSideChoice(state, model)
        CardNote(stringResource(R.string.to_restyle_samsungs_own_pull_down_colors))
    }
    SettingsCard(stringResource(R.string.cc_pages_title)) {
        SettingsSwitch(stringResource(R.string.cc_media_page), options.mediaPage, { set(options.copy(mediaPage = it)) }, "cc-media-page-switch")
        SettingsSwitch(stringResource(R.string.cc_connectivity_page), options.connectivityPage, { set(options.copy(connectivityPage = it)) }, "cc-connectivity-page-switch")
        IosMenuRow(stringResource(R.string.cc_open_on),
            listOf(CcOpenOn.CONTROLS to stringResource(R.string.cc_page_controls), CcOpenOn.LAST to stringResource(R.string.cc_open_on_last)),
            options.openOn, { set(options.copy(openOn = it)) }, tag = "cc-open-on")
        CardNote(stringResource(R.string.cc_pages_note))
    }
    // The network's name needs precise location from Android (it can reveal where the phone is); Folio only reads the name for the rail and the Connectivity page.
    val nameContext = androidx.compose.ui.platform.LocalContext.current
    var nameAllowed by remember { mutableStateOf(androidx.core.content.ContextCompat.checkSelfPermission(nameContext, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED) }
    val askName = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { nameAllowed = it }
    if (!nameAllowed) SettingsCard(null) {
        IosActionRow(stringResource(R.string.cc_wifi_name), "cc-wifi-name") { runCatching { askName.launch(android.Manifest.permission.ACCESS_FINE_LOCATION) } }
        CardNote(stringResource(R.string.cc_wifi_name_note))
    }
    SettingsCard(stringResource(R.string.cc_sliders_title)) {
        SettingsSwitch(stringResource(R.string.cc_slider_ticks), options.sliderTicks, { set(options.copy(sliderTicks = it)) }, "cc-slider-ticks-switch")
        CardNote(stringResource(R.string.cc_sliders_note))
    }
    SettingsCard(stringResource(R.string.cc_page_controls)) {
        IosNavRow(stringResource(R.string.edit_controls), stringResource(R.string.cc_controls_count, state.ccControls.size, CcControl.entries.size), onEditControlCenter, tag = "cc-edit-controls")
        CardNote(stringResource(R.string.cc_edit_controls_note))
        SettingsSwitch(stringResource(R.string.cc_close_after), options.closeAfterToggle, { set(options.copy(closeAfterToggle = it)) }, "cc-close-after-switch")
        CardNote(stringResource(R.string.cc_close_after_note))
    }
    SettingsCard(null) {
        val before = undo
        // Undo stands in the Reset row's own place, so it is where the finger was and never below the fold; the note under it says what happened.
        if (before == null) IosActionRow(stringResource(R.string.cc_reset), "cc-reset", destructive = true) {
            undo = CcSettingsSnapshot(state.ccLeft, state.ccSize, state.ccCentered, state.ccLook, state.ccOptions, state.ccLayout)
            model.restoreControlCenter(PanelSize.STANDARD, false, CcLook.CLEAR, CcOptions())
        } else IosActionRow(stringResource(R.string.undo), "cc-reset-undo") {
            model.restoreControlCenter(before.size, before.centered, before.look, before.options, before.layout, before.left)
            undo = null
        }
        CardNote(stringResource(if (before == null) R.string.cc_reset_note else R.string.cc_reset_done),
            if (before == null) Modifier else Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    }
}

/** Under the size row on a narrow window: the sizes only differ where there is width to spend, and a phone's panel already fills it. */
@Composable
internal fun CcSizeNote() {
    if (!androidx.compose.ui.platform.LocalConfiguration.current.fitsRegularHomeLayout()) CardNote(stringResource(R.string.cc_size_note))
}
