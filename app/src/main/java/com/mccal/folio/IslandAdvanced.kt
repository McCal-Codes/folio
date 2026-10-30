package com.mccal.folio

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Settings › Dynamic Island › Advanced island: color, corner radius, animation speed, haptics and gestures, each a short
 * list (no sliders), with Reset all at the top. It is reached only from the one row on the island page, behind the same
 * gate as Adjust island, and is never part of setup or What's new. Every change is saved at once ([IslandStyle]) and a
 * running island follows it; the guards for each option are in [IslandStyle].
 */
@Composable
internal fun IslandAdvancedPage() {
    val context = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    val values = remember(version) { IslandStyle.load(context) }
    var blocked by remember { mutableStateOf(false) }

    SettingsCard(null) {
        CardAction(stringResource(R.string.island_advanced_reset), enabled = !values.isDefault) {
            IslandStyle.reset(context); blocked = false; version++
        }
        CardNote(stringResource(R.string.island_advanced_reset_note))
    }

    SettingsCard(stringResource(R.string.island_color)) {
        ColorRow(values, onColor = { IslandStyle.saveColor(context, it); version++ })
        if (values.color == IslandColor.CUSTOM) CustomColorField(values, onColor = { IslandStyle.saveCustom(context, it); version++ })
        CardNote(stringResource(R.string.island_color_note))
        CardNote(stringResource(R.string.island_color_camera_note))
    }

    SettingsCard(null) {
        IosMenuRow(stringResource(R.string.island_corners), IslandCorners.entries.map { it to stringResource(it.label) }, values.corners,
            { IslandStyle.saveCorners(context, it); version++ }, tag = "island-corners")
        CardNote(stringResource(R.string.island_corners_note))
        IosMenuRow(stringResource(R.string.island_speed), IslandSpeed.entries.map { it to stringResource(it.label) }, values.speed,
            { IslandStyle.saveSpeed(context, it); version++ }, tag = "island-speed")
        CardNote(stringResource(R.string.island_speed_note))
        IosMenuRow(stringResource(R.string.island_haptics), IslandHaptics.entries.map { it to stringResource(it.label) }, values.haptics,
            { IslandStyle.saveHaptics(context, it); version++ }, tag = "island-haptics")
        CardNote(stringResource(R.string.island_haptics_note))
    }

    SettingsCard(stringResource(R.string.island_gestures)) {
        IslandGesture.entries.forEach { gesture ->
            IosMenuRow(stringResource(gesture.label), IslandStyle.choices(gesture).map { it to stringResource(it.label) }, values.gestures.getValue(gesture),
                { action -> if (IslandStyle.saveGesture(context, values.gestures, gesture, action)) { blocked = false; version++ } else blocked = true },
                tag = "island-gesture-${gesture.key}")
        }
        if (blocked) CardNote(stringResource(R.string.island_gesture_keep_dismiss), Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        CardNote(stringResource(R.string.island_gestures_note))
    }
}

/** Five round swatches: the four presets and Custom. Each is a 48 dp target; the chosen one wears a ring. */
@Composable
private fun ColorRow(values: IslandStyle.Values, onColor: (IslandColor) -> Unit) {
    val title = stringResource(R.string.island_color)
    Row(Modifier.fillMaxWidth().heightIn(min = FolioRow.ACTION.dp).settingsFocus(title), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), fontSize = FolioType.BODY.sp)
        IslandColor.entries.forEach { color ->
            val selected = values.color == color
            val name = stringResource(color.label)
            val description = stringResource(R.string.island_color_swatch, name)
            val fill = when {
                color == IslandColor.CUSTOM -> if (values.custom != null) Color(values.background) else MaterialTheme.colorScheme.surfaceVariant
                else -> Color(color.argb!!)
            }
            Box(Modifier.size(FolioTouch.MIN.dp).clip(CircleShape)
                .selectable(selected, role = Role.RadioButton) { onColor(color) }
                .semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
                Box(Modifier.size(FolioSpace.HUGE.dp).clip(CircleShape).background(fill)
                    .border(if (selected) FolioSpace.HAIR.dp else FolioSpace.HAIR.dp / 4, if (selected) FolioColors.Blue else Color.White.copy(alpha = .3f), CircleShape))
            }
        }
    }
}

/** A hex code for the Custom color; a valid one is saved as it is typed, and the page says when it had to be moved to stay readable. */
@Composable
private fun CustomColorField(values: IslandStyle.Values, onColor: (Int) -> Unit) {
    var text by remember { mutableStateOf(values.custom?.let(IslandStyle::toHex) ?: "") }
    val invalid = text.isNotBlank() && IslandStyle.parseHex(text) == null
    OutlinedTextField(
        text, { typed ->
            text = typed.filter { it != ' ' }.take(7)
            IslandStyle.parseHex(text)?.let(onColor)
        },
        Modifier.fillMaxWidth().padding(vertical = FolioSpace.SMALL.dp),
        label = { Text(stringResource(R.string.island_color_hex)) },
        placeholder = { Text(stringResource(R.string.island_color_hex_hint)) },
        singleLine = true, isError = invalid,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
    )
    if (invalid) CardNote(stringResource(R.string.island_color_hex_invalid), Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    else if (values.nudged) CardNote(stringResource(R.string.island_color_nudged), Modifier.semantics { liveRegion = LiveRegionMode.Polite })
}
