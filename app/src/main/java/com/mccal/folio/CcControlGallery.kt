package com.mccal.folio

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One thing the gallery can add to Control Center: a control, or a module (connectivity, Now Playing, a slider, Focus) that was taken off. */
internal data class CcGalleryItem(val id: String, val label: String, val icon: ImageVector, val module: Boolean)

/** The items whose name has [query] in it, ignoring case and the spaces around it; every item for a blank query. */
internal fun ccGalleryFilter(items: List<CcGalleryItem>, query: String): List<CcGalleryItem> {
    val q = query.trim()
    return if (q.isEmpty()) items else items.filter { it.label.contains(q, ignoreCase = true) }
}

/**
 * The sheet Control Center's edit mode opens to add a control (as iOS's Add a Control): a search field, then the modules that are off and
 * the controls that are not on the page, each a round button with its name under it. A tap adds that item and the sheet stays open to add
 * more; it closes itself once there is nothing left to add. Nothing here decides where an item goes: [onAdd] does.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CcControlGallery(items: List<CcGalleryItem>, onAdd: (CcGalleryItem) -> Unit, onDismiss: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val shown = ccGalleryFilter(items, query)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = FolioSpace.LARGE.dp).padding(bottom = FolioSpace.LARGE.dp)) {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(FolioRadius.CARD.dp)).background(Color.White.copy(alpha = .1f)).padding(horizontal = FolioSpace.COMPACT.dp, vertical = FolioSpace.SNUG.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FolioSpace.SNUG.dp)) {
                Icon(Icons.Rounded.Search, null, tint = FolioGlass.secondary, modifier = Modifier.size(20.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text(stringResource(R.string.search_controls), color = FolioGlass.secondary, fontSize = FolioType.BODY.sp)
                    BasicTextField(query, { query = it }, singleLine = true, cursorBrush = SolidColor(Color.White),
                        textStyle = TextStyle(color = Color.White, fontSize = FolioType.BODY.sp), keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "" })
                }
            }
            if (shown.isEmpty()) {
                Text(stringResource(if (items.isEmpty()) R.string.all_controls_added else R.string.no_controls_found), color = FolioGlass.secondary, fontSize = FolioType.FOOTNOTE.sp,
                    modifier = Modifier.padding(vertical = FolioSpace.LARGE.dp))
            } else LazyVerticalGrid(GridCells.Adaptive(88.dp), Modifier.heightIn(max = 520.dp).padding(top = FolioSpace.COMPACT.dp),
                horizontalArrangement = Arrangement.spacedBy(FolioSpace.COMPACT.dp), verticalArrangement = Arrangement.spacedBy(FolioSpace.COMPACT.dp)) {
                val modules = shown.filter { it.module }
                val controls = shown.filter { !it.module }
                if (modules.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) { GalleryHeading(stringResource(R.string.cc_gallery_modules)) }
                    items(modules, key = { it.id }) { GalleryTile(it, onAdd) }
                }
                if (controls.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) { GalleryHeading(stringResource(R.string.cc_gallery_controls)) }
                    items(controls, key = { it.id }) { GalleryTile(it, onAdd) }
                }
            }
        }
    }
}

@Composable
private fun GalleryHeading(text: String) =
    Text(text, color = FolioGlass.secondary, fontSize = FolioType.FOOTNOTE.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = FolioSpace.TINY.dp))

@Composable
private fun GalleryTile(item: CcGalleryItem, onAdd: (CcGalleryItem) -> Unit) {
    val add = stringResource(R.string.add_control, item.label)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(FolioRadius.CARD.dp)).clickable { onAdd(item) }.semantics { role = Role.Button; contentDescription = add }.padding(vertical = FolioSpace.TINY.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(64.dp).clip(CircleShape).background(Color.White.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
            Icon(item.icon, null, tint = Color.White, modifier = Modifier.size(28.dp))
            Box(Modifier.align(Alignment.TopEnd).size(20.dp).clip(CircleShape).background(FolioColors.Green), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Add, null, tint = Color.White, modifier = Modifier.size(14.dp))
            }
        }
        Text(item.label, color = Color.White, fontSize = FolioType.GROUP_LABEL.sp, maxLines = 2, lineHeight = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = FolioSpace.TINY.dp))
    }
}
