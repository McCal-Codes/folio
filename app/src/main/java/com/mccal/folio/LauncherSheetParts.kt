@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.mccal.folio

import androidx.compose.ui.res.stringResource
import android.appwidget.AppWidgetProviderInfo
import android.os.UserManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextStyle
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
internal fun AppPicker(apps: List<AppEntry>, dockSlot: Int?, onSelect: (AppEntry) -> Unit, onClear: () -> Unit,
    onLongClick: (AppEntry) -> Unit, canSelect: (AppEntry) -> Boolean = { true }, blockedHint: String? = null) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(apps, query) { apps.filter { it.label.contains(query.trim(), ignoreCase = true) } }
    Column(Modifier.fillMaxWidth().fillMaxHeight(.88f).padding(horizontal = FolioSpace.XL.dp).imePadding()) {
        Text(if (dockSlot == null) "Your Apps" else "Dock Position ${dockSlot + 1}", color = Color.White, fontSize = FolioType.TITLE.sp, fontWeight = FontWeight.Bold)
        IosSearchField(query, { query = it }, stringResource(R.string.search_apps), Modifier.padding(vertical = FolioSpace.MEDIUM.dp), fieldModifier = Modifier.testTag("search-field"))
        if (dockSlot != null) SheetGroup(Modifier.padding(bottom = FolioSpace.SMALL.dp)) { IosActionRow(stringResource(R.string.leave_this_position_empty), destructive = true, onClick = onClear) }
        if (blockedHint != null) Text(blockedHint, color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = FolioSpace.SMALL.dp).testTag("dock-full-guidance"))
        val listState = androidx.compose.foundation.lazy.rememberLazyListState()
        LazyColumn(Modifier.weight(1f).edgeFade(listState), state = listState) {
            if (filtered.isEmpty()) item { Text(stringResource(R.string.no_apps_found), Modifier.padding(vertical = FolioSpace.XXL.dp)) }
            items(filtered, key = { it.id }) { app ->
                val enabled = canSelect(app)
                Row(Modifier.fillMaxWidth().testTag("picker-app-${app.id}")
                    .combinedClickable(enabled = enabled, onClick = { onSelect(app) }, onLongClick = { onLongClick(app) })
                    .alpha(if (enabled) 1f else .45f)
                    .padding(vertical = FolioSpace.COMPACT.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(app, null, Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)))
                    Text(app.label, Modifier.padding(start = FolioSpace.LARGE.dp).weight(1f), maxLines = 2)
                    if (dockSlot != null && enabled) Icon(Icons.Rounded.AddCircle, "Choose ${app.label}", tint = LocalAccent.current.ink)
                }
            }
        }
    }
}


/** iOS grouped-list section header inside Folio's dark sheets. */
@Composable
internal fun SheetGroupLabel(text: String) {
    Text(text.uppercase(), color = Color.White.copy(alpha = .55f), fontSize = FolioType.GROUP_LABEL.sp, fontWeight = FontWeight.SemiBold,
        letterSpacing = .4.sp, modifier = Modifier.padding(start = FolioSpace.LARGE.dp, top = FolioSpace.COMPACT.dp))
}

/** iOS inset grouped list: rounded dark card holding rows separated by thin dividers. */
@Composable
internal fun SheetGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(FolioRadius.GROUP.dp)).background(Color(0xFF2C2C2E)), content = content)
}

/** Side-rail placement; the rail sits on the right unless the left-handed layout is on. */
internal fun railTop(leftHanded: Boolean) = if (leftHanded) Alignment.TopStart else Alignment.TopEnd
internal fun railBottom(leftHanded: Boolean) = if (leftHanded) Alignment.BottomStart else Alignment.BottomEnd
internal fun Modifier.railEdge(leftHanded: Boolean, gap: androidx.compose.ui.unit.Dp) =
    padding(start = if (leftHanded) gap else 0.dp, end = if (leftHanded) 0.dp else gap)
