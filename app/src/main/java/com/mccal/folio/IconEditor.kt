package com.mccal.folio

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * One app's own icon look: its style and shape, each either like the other icons or its own, with the icon shown as it
 * will be. Reset puts it back to following the launcher. The choice is applied when the icon is drawn.
 */
@Composable
internal fun AppIconEditor(app: AppEntry, current: AppIconOverride, onChange: (AppIconOverride) -> Unit, onDone: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = FolioSpace.LARGE.dp).padding(bottom = FolioSpace.XL.dp).verticalScroll(rememberScrollState()).testTag("icon-editor")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // The icon as it will look, whatever the launcher's look is.
            CompositionLocalProvider(LocalAppIconStyles provides mapOf(app.id to current)) {
                AppIcon(app, null, Modifier.size(56.dp), shape = RoundedCornerShape(14.dp), badge = false)
            }
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.edit_icon_for_1, app.label), color = Color.White, fontSize = FolioType.TITLE.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.done), color = LocalAccent.current.ink, fontSize = FolioType.BODY.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(FolioRadius.CONTROL.dp)).clickable(onClick = onDone).padding(FolioSpace.SMALL.dp).testTag("icon-editor-done"))
        }
        SheetGroupLabel(stringResource(R.string.style))
        ChipRow {
            IosChip(current.style == null, { onChange(current.copy(style = null)) }, { Text(stringResource(R.string.like_other_icons)) }, Modifier.testTag("icon-style-follow"))
            IconStyle.entries.forEach { style ->
                IosChip(current.style == style, { onChange(current.copy(style = style)) }, { Text(stringResource(style.label)) }, Modifier.testTag("icon-style-${style.name.lowercase()}"))
            }
        }
        SheetGroupLabel(stringResource(R.string.shape))
        ChipRow {
            IosChip(current.shape == null, { onChange(current.copy(shape = null)) }, { Text(stringResource(R.string.like_other_icons)) }, Modifier.testTag("icon-shape-follow"))
            IconShape.entries.forEach { shape ->
                IosChip(current.shape == shape, { onChange(current.copy(shape = shape)) }, { Text(stringResource(shape.label)) }, Modifier.testTag("icon-shape-${shape.name.lowercase()}"))
            }
        }
        // Your own picture: Android's photo picker (it hands over only the one picture), cropped to a square from its middle.
        SheetGroupLabel(stringResource(R.string.your_picture))
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) scope.launch { AppIconPictures.save(context, app.id, uri)?.let { stamp -> onChange(current.copy(picture = stamp)) } }
        }
        ChipRow {
            IosChip(false, { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                { Text(stringResource(if (current.hasPicture) R.string.change_picture else R.string.choose_picture)) }, Modifier.testTag("icon-picture-choose"))
            if (current.hasPicture) IosChip(false, { onChange(current.copy(picture = 0L)) }, { Text(stringResource(R.string.remove_picture)) }, Modifier.testTag("icon-picture-remove"))
        }
        CardNote(stringResource(R.string.edit_icon_picture_note))
        CardNote(stringResource(R.string.edit_icon_note))
        if (!current.isDefault) IosActionRow(stringResource(R.string.reset_icon), destructive = true, onClick = { onChange(AppIconOverride()) })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    FlowRow(Modifier.fillMaxWidth().padding(top = FolioSpace.SNUG.dp, bottom = FolioSpace.MEDIUM.dp), horizontalArrangement = Arrangement.spacedBy(FolioSpace.SMALL.dp),
        verticalArrangement = Arrangement.spacedBy(FolioSpace.SMALL.dp)) { content() }
}
