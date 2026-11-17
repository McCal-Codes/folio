package com.mccal.folio

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * iOS's Customize for Home: a short sheet over Home with the icon looks (Default, Dark, Clear, Tinted) drawn on one of your own
 * apps, so you pick by seeing it. The change applies at once and Home stays visible above the sheet. The same choice, and the rest
 * of the icon options, are still in Folio's settings.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun CustomizeHomeSheet(
    sample: AppEntry?, style: IconStyle, tint: Long, tintFromWallpaper: Boolean,
    onStyle: (IconStyle) -> Unit, onTintFromWallpaper: () -> Unit, onTint: (Long) -> Unit, onDone: () -> Unit,
) {
    // Folio's own sheet (FolioSheet.kt) for the dark surface, but with Home left sharp, so the new look shows on it as you tap.
    ModalBottomSheet(onDismissRequest = onDone, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), keepHomeSharp = true) {
        Column(Modifier.fillMaxWidth().padding(horizontal = FolioSpace.LARGE.dp).padding(bottom = FolioSpace.XL.dp)
            .verticalScroll(rememberScrollState()).testTag("customize-home-sheet")) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.customize), color = Color.White, fontSize = FolioType.TITLE.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f))
                Text(stringResource(R.string.done), color = LocalAccent.current.ink, fontSize = FolioType.BODY.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(FolioRadius.CONTROL.dp)).heightIn(min = 48.dp)
                        .clickable(role = Role.Button, onClick = onDone).padding(horizontal = FolioSpace.SMALL.dp)
                        .wrapContentHeight(Alignment.CenterVertically).testTag("customize-home-done"))
            }
            SheetGroupLabel(stringResource(R.string.icon_style))
            // A row of four at normal size; at a large text size the tiles drop onto a second row instead of clipping their names.
            FlowRow(Modifier.fillMaxWidth().padding(top = FolioSpace.SNUG.dp).selectableGroup(), horizontalArrangement = Arrangement.SpaceEvenly,
                verticalArrangement = Arrangement.spacedBy(FolioSpace.TINY.dp)) {
                IconStyle.entries.forEach { option ->
                    StyleTile(option, option == style, sample, { onStyle(option) }, Modifier.widthIn(min = 76.dp))
                }
            }
            if (style == IconStyle.TINTED) IconTintSwatches(tintFromWallpaper, tint, onTintFromWallpaper, onTint)
        }
    }
}

/** One look, drawn on a real app, with its name under it. The picked one is ringed and read out as selected. */
@Composable
private fun StyleTile(style: IconStyle, selected: Boolean, sample: AppEntry?, onClick: () -> Unit, modifier: Modifier) {
    val accent = LocalAccent.current.ink
    val frame = RoundedCornerShape(FolioRadius.CONTROL.dp)
    Column(modifier.clip(frame).selectable(selected, role = Role.RadioButton, onClick = onClick)
        .padding(vertical = FolioSpace.SMALL.dp).testTag("customize-style-${style.name.lowercase()}"),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(72.dp).clip(frame).background(Color.White.copy(alpha = .08f))
            .then(if (selected) Modifier.border(3.dp, accent, frame) else Modifier), contentAlignment = Alignment.Center) {
            if (sample != null) CompositionLocalProvider(LocalAppIconStyles provides mapOf(sample.id to AppIconOverride(style = style))) {
                AppIcon(sample, null, Modifier.size(56.dp), shape = RoundedCornerShape(FolioRadius.CARD.dp), badge = false)
            }
        }
        Text(stringResource(style.label), color = Color.White.copy(alpha = if (selected) 1f else .6f), fontSize = FolioType.FOOTNOTE.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, softWrap = false, modifier = Modifier.padding(top = FolioSpace.TINY.dp))
    }
}

/**
 * The color Tinted icons take: the wallpaper's own (it follows when the wallpaper changes), or one of eight. Shared by
 * Customize and the settings page, so both offer the same choices.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun IconTintSwatches(fromWallpaper: Boolean, tint: Long, onWallpaper: () -> Unit, onColor: (Long) -> Unit) {
    val ring = MaterialTheme.colorScheme.onSurface
    FlowRow(Modifier.fillMaxWidth().padding(vertical = FolioSpace.SNUG.dp).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(FolioSpace.COMPACT.dp),
        verticalArrangement = Arrangement.spacedBy(FolioSpace.COMPACT.dp)) {
        val tone = LocalWallpaperTone.current
        Box(Modifier.size(40.dp).clip(CircleShape)
            .background(wallpaperIconTint(tone)?.let { Color(it) } ?: Color.Gray)
            .then(if (fromWallpaper) Modifier.border(3.dp, ring, CircleShape) else Modifier)
            .clickable(role = Role.RadioButton, onClick = onWallpaper)
            .description(R.string.wallpaper_color).semantics { selected = fromWallpaper },
            contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Wallpaper, null, tint = Color.Black.copy(alpha = .6f), modifier = Modifier.size(18.dp))
        }
        listOf(FolioColors.Value.Warning, FolioColors.Value.RedSoft, 0xFFFF7EB6, 0xFFBF8CFF, 0xFF64B5FF, 0xFF5EE0C4, 0xFF9BE15D, 0xFFE8E8E8).forEach { c ->
            Box(Modifier.size(40.dp).clip(CircleShape).background(Color(c))
                .then(if (!fromWallpaper && tint == c) Modifier.border(3.dp, ring, CircleShape) else Modifier)
                .clickable(role = Role.RadioButton) { onColor(c) }
                .description(R.string.tint_color).semantics { selected = !fromWallpaper && tint == c })
        }
    }
}
