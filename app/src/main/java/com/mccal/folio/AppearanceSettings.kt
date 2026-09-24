package com.mccal.folio

import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check

@Composable
internal fun AppearanceSettings(state: AppearanceState, onMode: (AppearanceMode) -> Unit,
    onManual: (String, Double, Double) -> Unit, onDeviceLocation: () -> Unit, onClear: () -> Unit,
    onAccent: (AccentChoice) -> Unit = {}) {
    var place by remember(state.place) { mutableStateOf(state.place) }
    var latitude by remember(state.latitude) { mutableStateOf(state.latitude?.toString().orEmpty()) }
    var longitude by remember(state.longitude) { mutableStateOf(state.longitude?.toString().orEmpty()) }
    var inputError by remember { mutableStateOf<String?>(null) }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("appearance-settings")) {
        SheetGroupLabel(stringResource(R.string.appearance))
        // iOS selection list: one checkmark row per choice.
        SheetGroup {
            AppearanceMode.entries.forEachIndexed { i, mode ->
                if (i > 0) MenuDivider()
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(state.mode == mode, role = Role.RadioButton) { onMode(mode) }
                    .padding(horizontal = 16.dp).testTag("appearance-${mode.name.lowercase()}"), verticalAlignment = Alignment.CenterVertically) {
                    Text(when (mode) {
                        AppearanceMode.LIGHT -> "Light"; AppearanceMode.DARK -> "Dark"; AppearanceMode.SYSTEM -> "Follow System"
                        AppearanceMode.SUNRISE_SUNSET -> "Sunset to Sunrise"
                    }, Modifier.weight(1f), color = Color.White, fontSize = 17.sp)
                    if (state.mode == mode) Icon(Icons.Rounded.Check, null, tint = LocalAccent.current.ink, modifier = Modifier.size(20.dp))
                }
            }
        }
        // The colour Folio uses for what you can act on. Folio's own teal by default; Apple's blue for whoever
        // prefers it. Status colours (Wi-Fi, battery, an alert's red) don't follow it.
        SheetGroupLabel(stringResource(R.string.accent))
        SheetGroup {
            AccentChoice.entries.forEachIndexed { i, choice ->
                if (i > 0) MenuDivider()
                val swatch = FolioAccents.of(choice)
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(state.accent == choice, role = Role.RadioButton) { onAccent(choice) }
                    .padding(horizontal = 16.dp).testTag("accent-${choice.id}"), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(22.dp).clip(androidx.compose.foundation.shape.CircleShape).background(swatch.fill))
                    Text(when (choice) {
                        AccentChoice.FOLIO_TEAL -> stringResource(R.string.folio_teal)
                        AccentChoice.APPLE_BLUE -> stringResource(R.string.classic_blue)
                    }, Modifier.weight(1f).padding(start = 12.dp), color = Color.White, fontSize = 17.sp)
                    if (state.accent == choice) Icon(Icons.Rounded.Check, null, tint = LocalAccent.current.ink, modifier = Modifier.size(20.dp))
                }
            }
        }
        if (state.mode == AppearanceMode.SUNRISE_SUNSET) {
            state.fallback?.let { Text(it, color = FolioColors.Red, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp)) }
            SheetGroupLabel("Location")
            SheetGroup {
                InlineField(stringResource(R.string.place_name), place, { place = it }, "appearance-place")
                MenuDivider()
                InlineField("Latitude", latitude, { latitude = it }, "appearance-latitude", "−90 to 90", number = true)
                MenuDivider()
                InlineField("Longitude", longitude, { longitude = it }, "appearance-longitude", "−180 to 180", number = true)
                MenuDivider()
                IosActionRow(stringResource(R.string.use_this_place), "appearance-save-place") {
                    focusManager.clearFocus(); keyboard?.hide()
                    val lat = latitude.toDoubleOrNull(); val lon = longitude.toDoubleOrNull()
                    if (lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0) {
                        inputError = null; onManual(place, lat, lon)
                    } else inputError = "Enter a latitude from −90 to 90 and longitude from −180 to 180."
                }
            }
            AppearanceFeedback(inputError, FolioColors.Red, "appearance-manual-status")
            SheetGroup {
                IosActionRow(stringResource(R.string.use_device_location), "appearance-device-location") {
                    focusManager.clearFocus(); keyboard?.hide(); onDeviceLocation()
                }
                if (state.latitude != null) {
                    MenuDivider()
                    IosActionRow(stringResource(R.string.clear_location), destructive = true, onClick = onClear)
                }
            }
            AppearanceFeedback(state.locationStatus, Color.White.copy(alpha = .55f), "appearance-location-status")
        }
    }
}


/** iOS Settings text row: label on the left, editable value on the right. */
@Composable
private fun InlineField(label: String, value: String, onValue: (String) -> Unit, tag: String, hint: String = "", number: Boolean = false) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, fontSize = 17.sp)
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            if (value.isEmpty()) Text(hint, color = Color.White.copy(alpha = .3f), fontSize = 17.sp)
            BasicTextField(value, onValue, Modifier.fillMaxWidth().testTag(tag), singleLine = true,
                textStyle = TextStyle(color = Color.White.copy(alpha = .6f), fontSize = 17.sp, textAlign = TextAlign.End),
                cursorBrush = SolidColor(LocalAccent.current.ink),
                keyboardOptions = if (number) KeyboardOptions(keyboardType = KeyboardType.Decimal) else KeyboardOptions.Default)
        }
    }
}

@Composable
private fun AppearanceFeedback(message: String?, color: androidx.compose.ui.graphics.Color, tag: String) {
    val bringIntoView = remember { BringIntoViewRequester() }
    LaunchedEffect(message) { if (message != null) bringIntoView.bringIntoView() }
    message?.let {
        Text(it, color = color, modifier = Modifier.bringIntoViewRequester(bringIntoView)
            .semantics { liveRegion = LiveRegionMode.Polite }.testTag(tag))
    }
}
