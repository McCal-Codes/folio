package com.mccal.folio

import androidx.compose.material.icons.rounded.LockClock
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.automirrored.rounded.EventNote
import androidx.compose.ui.res.stringResource
import android.appwidget.AppWidgetProviderInfo
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items
import android.content.Context
import android.content.pm.LauncherApps
import android.os.Process
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.widget.RemoteViews
import android.appwidget.AppWidgetManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.LinkedHashMap

internal data class WidgetCatalogEntry(
    val provider: AppWidgetProviderInfo,
    val providerLabel: String,
    val appLabel: String,
    val description: String,
    val userSerial: Long = 0,
    val profileLabel: String,
    val isWork: Boolean = false,
)

internal data class WidgetPickerSession(
    val provider: AppWidgetProviderInfo?,
    val slot: Int,
    val span: WidgetSpan,
    val pointer: Offset,
    val dragging: Boolean,
    val targetIndex: Int? = null,
    val candidate: WidgetPlacement? = null,
    val builtinId: Int? = null,
)

internal fun widgetCatalog(context: Context, providers: List<AppWidgetProviderInfo>, profile: AppProfile): List<WidgetCatalogEntry> {
    val pm = context.packageManager
    val launcherApps = context.getSystemService(LauncherApps::class.java)
    return providers.map { provider ->
        val packageName = provider.provider.packageName
        val appLabel = runCatching {
            launcherApps.getApplicationInfo(packageName, 0, provider.profile).loadLabel(pm).toString()
        }.recoverCatching {
            if (provider.profile != Process.myUserHandle()) throw it
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        }
            .getOrDefault(packageName)
        val providerLabel = provider.loadLabel(pm).toString()
        val description = runCatching { provider.loadDescription(context)?.toString().orEmpty() }.getOrDefault("")
        WidgetCatalogEntry(provider, providerLabel, appLabel, description, profile.userSerial, profile.label, profile.isWork)
    }.sortedWith(compareBy({ it.appLabel.lowercase() }, { it.providerLabel.lowercase() }))
}

private sealed interface CatalogPreview {
    data class Remote(val views: RemoteViews) : CatalogPreview
    data class Picture(val bitmap: Bitmap) : CatalogPreview
    data object Missing : CatalogPreview
}

private object WidgetPreviewCache {
    private const val MAX_BYTES = 16 * 1024 * 1024
    private val values = object : LinkedHashMap<String, CatalogPreview>(16, .75f, true) {}
    private var bytes = 0
    @Synchronized fun get(key: String) = values[key]
    @Synchronized fun put(key: String, value: CatalogPreview) {
        values.remove(key)?.let { bytes -= it.cost }
        values[key] = value; bytes += value.cost
        val iterator = values.entries.iterator()
        while (bytes > MAX_BYTES && iterator.hasNext()) { bytes -= iterator.next().value.cost; iterator.remove() }
    }
    private val CatalogPreview.cost get() = when (this) {
        is CatalogPreview.Picture -> bitmap.allocationByteCount
        is CatalogPreview.Remote -> 64 * 1024
        CatalogPreview.Missing -> 1
    }
}

private suspend fun loadWidgetPreview(context: Context, provider: AppWidgetProviderInfo, span: WidgetSpan): CatalogPreview {
    val density = context.resources.displayMetrics.densityDpi
    val key = "${provider.provider.flattenToString()}|${provider.profile.hashCode()}|$density|${span.width}x${span.height}"
    WidgetPreviewCache.get(key)?.let { return it }
    val loaded = withContext(Dispatchers.IO) {
        val manager = AppWidgetManager.getInstance(context)
        var remote: RemoteViews? = null
        if (android.os.Build.VERSION.SDK_INT >= 35 &&
            provider.generatedPreviewCategories and AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN != 0) {
            remote = runCatching { manager.getWidgetPreview(provider.provider, provider.profile,
                AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN) }.getOrNull()
        }
        if (remote == null && provider.previewLayout != 0)
            remote = runCatching { RemoteViews(provider.provider.packageName, provider.previewLayout) }.getOrNull()
        remote?.let(CatalogPreview::Remote) ?: run {
            val drawable = runCatching { provider.loadPreviewImage(context, density) }.getOrNull()
                ?: runCatching { provider.loadIcon(context, density) }.getOrNull()
            drawable
        }
    }
    // Drawable.draw may touch theme/view state. Convert it on Compose's UI thread; only resource
    // discovery and generated-preview IPC belong on the worker dispatcher.
    val result = when (loaded) {
        is CatalogPreview -> loaded
        is Drawable -> CatalogPreview.Picture(loaded.catalogBitmap())
        else -> CatalogPreview.Missing
    }
    WidgetPreviewCache.put(key, result)
    return result
}

private fun Drawable.catalogBitmap(): Bitmap {
    val sourceWidth = intrinsicWidth.takeIf { it > 0 } ?: 144
    val sourceHeight = intrinsicHeight.takeIf { it > 0 } ?: 144
    val scale = minOf(1f, 720f / sourceWidth, 480f / sourceHeight)
    val w = (sourceWidth * scale).toInt().coerceAtLeast(1)
    val h = (sourceHeight * scale).toInt().coerceAtLeast(1)
    return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { bitmap ->
        val canvas = android.graphics.Canvas(bitmap)
        setBounds(0, 0, w, h)
        draw(canvas)
    }
}

@Composable
internal fun WidgetProviderPreview(entry: WidgetCatalogEntry, span: WidgetSpan, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val preview by produceState<CatalogPreview?>(null, entry.provider, span, context.resources.displayMetrics.densityDpi) {
        value = loadWidgetPreview(context, entry.provider, span)
    }
    Box(modifier.background(Color.White.copy(alpha = .1f)), contentAlignment = Alignment.Center) {
        when (val value = preview) {
            is CatalogPreview.Remote -> AndroidView(factory = { previewContext ->
                object : android.widget.FrameLayout(previewContext) {
                    override fun dispatchTouchEvent(event: android.view.MotionEvent?): Boolean = false
                }.apply {
                    importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                    isFocusable = false
                    isClickable = false
                    runCatching { addView(value.views.apply(previewContext, this)) }
                        .onFailure { addView(android.widget.TextView(previewContext).apply { text = entry.providerLabel }) }
                }
            }, modifier = Modifier.fillMaxSize().padding(6.dp))
            is CatalogPreview.Picture -> Image(value.bitmap.asImageBitmap(), null, Modifier.fillMaxSize().padding(8.dp),
                contentScale = ContentScale.Fit)
            CatalogPreview.Missing -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(entry.providerLabel, style = MaterialTheme.typography.bodySmall, color = Color.White)
                Text("${span.width} × ${span.height}", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = .6f))
            }
            null -> CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 3.dp, color = Color.White)
        }
    }
}

@Composable
internal fun VisualWidgetPicker(
    entries: List<WidgetCatalogEntry>?,
    profiles: List<AppProfile>,
    selectedProfile: AppProfile,
    onSelectProfile: (AppProfile) -> Unit,
    onTurnOnWork: (Long) -> Unit,
    hiddenForDrag: Boolean,
    footprint: (AppWidgetProviderInfo) -> WidgetSpan?,
    onBack: () -> Unit,
    onTap: (AppWidgetProviderInfo) -> Unit,
    onBuiltin: (Int) -> Unit,
    onDragStart: (AppWidgetProviderInfo, Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDrop: () -> Unit,
    onCancelDrag: () -> Unit,
) {
    val widgetPanelLabel = stringResource(R.string.widget_panel_title)
    val suggestionsLabel = stringResource(R.string.suggestions)
    val bigClockLabel = stringResource(R.string.big_clock)
    val upNextLabel = stringResource(R.string.up_next)
    val dateLabel = stringResource(R.string.date)
    val clockLabel = stringResource(R.string.clock)
    val folioLabel = stringResource(R.string.folio)
    var query by rememberSaveable { mutableStateOf("") }
    val latestDragStart by rememberUpdatedState(onDragStart)
    val latestDrag by rememberUpdatedState(onDrag)
    val latestDrop by rememberUpdatedState(onDrop)
    val latestCancelDrag by rememberUpdatedState(onCancelDrag)
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val words = query.trim().lowercase()
    val filtered = remember(entries, words) { entries.orEmpty().filter { entry ->
        words.isEmpty() || listOf(entry.appLabel, entry.providerLabel, entry.description,
            entry.provider.provider.packageName).any { it.lowercase().contains(words) }
    } }
    // iOS widget gallery: dark glass, large title, search capsule, grid of preview cards grouped by app.
    val ink = Color.White
    val secondary = Color.White.copy(alpha = .6f)
    Surface(Modifier.fillMaxSize().alpha(if (hiddenForDrag) 0f else 1f)
        .then(if (hiddenForDrag) Modifier.clearAndSetSemantics { }.focusProperties { canFocus = false } else Modifier)
        .testTag("visual-widget-picker"),
        color = Color(0xFF111114).copy(alpha = .97f), contentColor = ink) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.folioSafeTop).navigationBarsPadding().padding(horizontal = 18.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.widgets), color = ink, fontSize = 32.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Box(Modifier.size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = .14f)).clickable(onClickLabel = stringResource(R.string.close), onClick = onBack),
                    contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Close, stringResource(R.string.back), tint = ink, modifier = Modifier.size(20.dp)) }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = .12f))
                .padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Search, null, tint = secondary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text(stringResource(R.string.search_widgets), color = secondary, fontSize = 17.sp)
                    androidx.compose.foundation.text.BasicTextField(query, { query = it }, Modifier.fillMaxWidth().testTag("widget-catalog-search"),
                        singleLine = true, textStyle = androidx.compose.ui.text.TextStyle(color = ink, fontSize = 17.sp),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(ink))
                }
            }
            if (profiles.any { it.isWork }) Row(Modifier.fillMaxWidth().padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                profiles.forEach { profile ->
                    val selected = profile.userSerial == selectedProfile.userSerial
                    Text(profile.label, color = if (selected) Color.Black else ink, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                        modifier = Modifier.clip(CircleShape).background(if (selected) Color.White else Color.White.copy(alpha = .14f))
                            .clickable { onSelectProfile(profile) }.padding(horizontal = 14.dp, vertical = 8.dp))
                }
            }
            if (!selectedProfile.available || !selectedProfile.unlocked || selectedProfile.quiet) {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (selectedProfile.quiet) "${selectedProfile.label} apps are paused"
                        else "${selectedProfile.label} profile is unavailable", color = secondary)
                    if (selectedProfile.isWork) FolioButton(stringResource(R.string.turn_on), { onTurnOnWork(selectedProfile.userSerial) },
                        Modifier.padding(top = 12.dp))
                }
            }
            val catalogState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
            LazyVerticalGrid(GridCells.Adaptive(168.dp), Modifier.fillMaxSize().edgeFade(catalogState).testTag("widget-catalog-list"), state = catalogState,
                horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 24.dp)) {
                fun header(key: String, title: String) = item(key, span = { GridItemSpan(maxLineSpan) }) {
                    Text(title, color = ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp, start = 2.dp))
                }
                if (entries == null) item("catalog-loading", span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.TopCenter) { CircularProgressIndicator(color = ink) }
                }
                if (words.isEmpty() && selectedProfile.isPersonal) {
                    header("duo-widgets", folioLabel)
                    items(listOf(Triple(CLOCK_WIDGET, clockLabel, Icons.Rounded.Schedule), Triple(DATE_WIDGET, dateLabel, Icons.Rounded.CalendarToday),
                        Triple(UP_NEXT_WIDGET, upNextLabel, androidx.compose.material.icons.Icons.AutoMirrored.Rounded.EventNote), Triple(BIG_CLOCK_WIDGET, bigClockLabel, Icons.Rounded.LockClock), Triple(SUGGESTIONS_WIDGET, suggestionsLabel, androidx.compose.material.icons.Icons.Rounded.AutoAwesome),
                        Triple(INFO_WIDGET, widgetPanelLabel, Icons.Rounded.Widgets)), key = { "builtin-${it.first}" }) { (id, label, icon) ->
                        GalleryCard(label, stringResource(R.string.folio), stringResource(if (id == BIG_CLOCK_WIDGET) R.string.wide else R.string.small), Modifier.testTag("widget-builtin-$id")
                            .clickable { focusManager.clearFocus(); keyboard?.hide(); onBuiltin(id) }) {
                            Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(22.dp))
                                .background(Color.White.copy(alpha = .1f)), contentAlignment = Alignment.Center) {
                                Icon(icon, null, tint = ink, modifier = Modifier.size(44.dp))
                            }
                        }
                    }
                }
                filtered.groupBy { it.appLabel }.forEach { (app, group) ->
                    header("header-$app", app)
                    items(group, key = { it.provider.provider.flattenToString() }) { entry ->
                        val span = footprint(entry.provider)
                        var origin by remember { mutableStateOf(Offset.Zero) }
                        val sizeName = span?.let { when { it.width <= 2 && it.height <= 2 -> stringResource(R.string.small); it.height <= 2 -> stringResource(R.string.medium); else -> stringResource(R.string.large) } }
                        GalleryCard(entry.providerLabel, if (entry.isWork) "${entry.appLabel} · ${entry.profileLabel}" else entry.appLabel,
                            span?.let { "$sizeName · ${it.width} × ${it.height}" } ?: stringResource(R.string.doesn_t_fit_this_layout),
                            Modifier.testTag("widget-provider-${entry.provider.provider.flattenToString()}${if (entry.isWork) "-profile-${entry.userSerial}" else ""}")
                                .onGloballyPositioned { origin = it.boundsInRoot().topLeft }
                                .pointerInput(entry.provider) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = { point ->
                                            focusManager.clearFocus(); keyboard?.hide()
                                            latestDragStart(entry.provider, origin + point)
                                        },
                                        onDrag = { change, _ -> change.consume(); latestDrag(origin + change.position) },
                                        onDragEnd = { latestDrop() }, onDragCancel = { latestCancelDrag() })
                                }.clickable(enabled = span != null, onClick = {
                                    focusManager.clearFocus(); keyboard?.hide(); onTap(entry.provider)
                                }),
                            dimmed = span == null) {
                            val ratio = span?.let { it.width.toFloat() / it.height } ?: 1f
                            val previewTag = "widget-preview-${entry.provider.provider.flattenToString()}${if (entry.isWork) "-profile-${entry.userSerial}" else ""}"
                            Box(Modifier.fillMaxWidth().aspectRatio(ratio.coerceIn(.7f, 2.2f)).clip(RoundedCornerShape(22.dp)).testTag(previewTag)) {
                                WidgetProviderPreview(entry, span ?: WidgetSpan(2, 2), Modifier.fillMaxSize())
                            }
                        }
                    }
                }
                if (entries != null && filtered.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(stringResource(R.string.no_widgets_found), color = secondary, modifier = Modifier.padding(20.dp))
                }
            }
        }
    }
}

/** One widget in the gallery: preview on top, name and app underneath (iOS style). */
@Composable
private fun GalleryCard(title: String, subtitle: String, detail: String, modifier: Modifier, dimmed: Boolean = false,
    preview: @Composable () -> Unit) {
    Column(modifier.alpha(if (dimmed) .45f else 1f).clip(RoundedCornerShape(24.dp)).background(Color.White.copy(alpha = .06f)).padding(10.dp)) {
        preview()
        Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.dp, start = 2.dp))
        Text(subtitle, color = Color.White.copy(alpha = .6f), fontSize = 13.sp, maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.padding(start = 2.dp))
        Text(detail, color = Color.White.copy(alpha = .45f), fontSize = 12.sp, maxLines = 1, modifier = Modifier.padding(start = 2.dp, bottom = 2.dp))
    }
}
