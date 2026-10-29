package com.mccal.folio

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
private data class CcSettingsSnapshot(val size: PanelSize, val centered: Boolean, val look: CcLook, val options: CcOptions)

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
        IosMenuRow(stringResource(R.string.control_center_look), CcLook.entries.map { it to stringResource(it.label) }, state.ccLook, model::setCcLook, tag = "cc-look")
        CardNote(stringResource(R.string.cc_look_note))
        // Side by side puts Control Center on the right itself, and a card sits under its own corner, so only Full screen can centre it.
        if (state.ncLayout == NcLayout.FULL_SCREEN) SettingsSwitch(stringResource(R.string.unfolded_control_center_in_the_middle), state.ccCentered, model::setCcCentered, "cc-centered-switch")
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
            undo = CcSettingsSnapshot(state.ccSize, state.ccCentered, state.ccLook, state.ccOptions)
            model.restoreControlCenter(PanelSize.STANDARD, false, CcLook.CLEAR, CcOptions())
        } else IosActionRow(stringResource(R.string.undo), "cc-reset-undo") {
            model.restoreControlCenter(before.size, before.centered, before.look, before.options)
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
