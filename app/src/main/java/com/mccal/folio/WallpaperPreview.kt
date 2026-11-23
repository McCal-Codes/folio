package com.mccal.folio

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/*
 * The full-screen wallpaper preview (FeatureGate.WALLPAPER_PREVIEW; Lab: wallpaper-apple). Tapping art stages it, and while the
 * gate is open the staged picture is shown here under your real Home with Cancel and Set at the bottom, where a thumb is. Only Set
 * writes, and both buttons go through the controller's own cancelPendingArt and setPendingArt, so this holds no state of its own.
 *
 * A Dialog, not an inline Box: on the Fold8 an inline full-screen Box is clipped by the window's insets.
 * Reduce Motion: a plain fade. Otherwise one 240 ms fade and scale on opening; nothing loops or flashes (WCAG 2.3.1).
 */

/** How long the preview takes to appear, once. */
private const val PREVIEW_MS = 240

/** The scale the preview grows from. */
private const val PREVIEW_FROM = .96f

/** The shape of one Home page as the Settings preview draws it (420 by 720), so a page can be fitted into a screen. */
private const val HOME_PAGE_ASPECT = 420f / 720f

/** Room kept under Home for the name, credit and buttons, so a wide screen's Home does not run under them. */
private const val WALLPAPER_CONTROLS_DP = 176

/** The same for the photo framing screen, which also has the Blur switch and a hint. */
private const val FRAME_CONTROLS_DP = 252

/** The line under a work's name: who made it and under what license, with whatever is blank left out. */
internal fun wallpaperCredit(artist: String, license: String): String =
    listOf(artist, license).map { it.trim() }.filter { it.isNotEmpty() }.joinToString(", ")

/**
 * The power-of-two fraction to decode at so the picture still covers a [targetWidth] x [targetHeight] screen when cropped to fill
 * it: both sides stay at least as large as the screen's, so nothing is scaled up. 1 when the source is already no bigger.
 */
internal fun previewSampleSize(sourceWidth: Int, sourceHeight: Int, targetWidth: Int, targetHeight: Int): Int {
    if (sourceWidth <= 0 || sourceHeight <= 0 || targetWidth <= 0 || targetHeight <= 0) return 1
    var sample = 1
    while (sourceWidth / (sample * 2) >= targetWidth && sourceHeight / (sample * 2) >= targetHeight) sample *= 2
    return sample
}

/** Decodes [id] for the full-screen preview at the screen's size, from the same asset or file Home reads. Call off the main thread. */
internal fun backgroundPreviewBitmap(context: android.content.Context, id: String, screenWidth: Int, screenHeight: Int): Bitmap? = runCatching {
    // Folio's own piece with colors changed is drawn from its recipe at the size that covers the screen.
    tweakedArtBitmap(context, id, maxOf(screenWidth, screenHeight))?.let { return@runCatching it }
    fun options(bounds: android.graphics.BitmapFactory.Options) = android.graphics.BitmapFactory.Options().apply {
        inSampleSize = previewSampleSize(bounds.outWidth, bounds.outHeight, screenWidth, screenHeight)
    }
    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    if (BackgroundLibrary.isBuiltIn(id)) {
        context.assets.open(BackgroundLibrary.assetPath(id)).use { android.graphics.BitmapFactory.decodeStream(it, null, bounds) }
        context.assets.open(BackgroundLibrary.assetPath(id)).use { android.graphics.BitmapFactory.decodeStream(it, null, options(bounds)) }
    } else {
        val path = BackgroundLibrary.artFile(context, id).absolutePath
        android.graphics.BitmapFactory.decodeFile(path, bounds)
        android.graphics.BitmapFactory.decodeFile(path, options(bounds))
    }
}.getOrNull()

@Composable
internal fun WallpaperPreviewDialog(
    artId: String, thumbnail: Bitmap?, sharp: Bitmap?, state: LauncherState, onCancel: () -> Unit, onSet: () -> Unit,
    tweaks: WallpaperTweaks = WallpaperTweaks.NONE, onTweaks: ((WallpaperTweaks) -> Unit)? = null,
) {
    val context = LocalContext.current
    // Folio's own pieces can have their colors changed here; the panel opens under the name and Home's page shrinks to fit above it.
    val customizable = onTweaks != null && WallpaperRecipes.isCustomizable(artId)
    var customizing by remember { mutableStateOf(false) }
    val reduceMotion = LocalReduceMotion.current
    val art = produceState<Artwork?>(null, artId) { value = withContext(Dispatchers.IO) { BackgroundLibrary.find(context, artId) } }.value
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, tween(if (reduceMotion) FolioMotion.FADE_MS else PREVIEW_MS)) }
    val description = stringResource(R.string.wallpaper_preview)

    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(
            Modifier.fillMaxSize().background(Color.Black)
                .graphicsLayer {
                    alpha = appear.value
                    if (!reduceMotion) {
                        val s = PREVIEW_FROM + (1f - PREVIEW_FROM) * appear.value
                        scaleX = s; scaleY = s
                    }
                }
                .semantics { contentDescription = description }
                .testTag("wallpaper-preview-dialog"),
        ) {
            // The thumbnail shows until the sharp one is decoded (no blank frame), then one 150 ms cross-fade; a snap with Reduce Motion.
            val sharpAlpha by androidx.compose.animation.core.animateFloatAsState(
                if (sharp != null) 1f else 0f, if (reduceMotion) androidx.compose.animation.core.snap() else tween(FolioMotion.FADE_MS), label = "sharp")
            thumbnail?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            sharp?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize().graphicsLayer { alpha = sharpAlpha }, contentScale = ContentScale.Crop) }
            // Home has a scrim behind its text and so does this, so the bottom text and buttons read over a pale picture.
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .25f), Color.Transparent, Color.Black.copy(alpha = .6f)))))

            // Edge to edge like Apple's: Home fills the screen and the name and buttons float over its bottom edge.
            RealHome(state, thumbnail ?: sharp, (WALLPAPER_CONTROLS_DP + (if (customizable) 44 else 0) + (if (customizable && customizing) WALLPAPER_TWEAK_PANEL_DP else 0)).dp)
            Column(
                Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars).padding(horizontal = FolioSpace.XXL.dp, vertical = FolioSpace.LARGE.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.weight(1f))
                if (customizable && customizing && onTweaks != null) WallpaperTweakPanel(tweaks, onTweaks)
                if (art != null) {
                    Text(art.title, color = Color.White, fontSize = FolioType.BODY.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                        maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = FolioSpace.MEDIUM.dp))
                    Text(wallpaperCredit(art.artist, art.license), color = Color.White.copy(alpha = .8f), fontSize = FolioType.FOOTNOTE.sp, textAlign = TextAlign.Center,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (customizable) {
                    Text(
                        stringResource(if (customizing) R.string.done else R.string.customize), color = Color.White, fontSize = FolioType.FOOTNOTE.sp,
                        fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                        modifier = Modifier.defaultMinSize(minHeight = 44.dp).clickable(role = Role.Button) { customizing = !customizing }
                            .padding(horizontal = FolioSpace.LARGE.dp, vertical = FolioSpace.SMALL.dp).testTag("wallpaper-preview-customize"),
                    )
                }
                Row(Modifier.fillMaxWidth().padding(top = FolioSpace.LARGE.dp), horizontalArrangement = Arrangement.spacedBy(FolioSpace.MEDIUM.dp)) {
                    PreviewButton(stringResource(R.string.cancel), Color.White.copy(alpha = .22f), Color.White, "wallpaper-preview-cancel", onCancel, Modifier.weight(1f))
                    PreviewButton(stringResource(R.string.set), LocalAccent.current.fill, Color.White, "wallpaper-preview-set", onSet, Modifier.weight(1f))
                }
            }
        }
    }
}

/** A rectangle of a photo, in its own pixels: what the person framed. */
internal data class PhotoCrop(val left: Int, val top: Int, val width: Int, val height: Int) {
    fun fitsIn(bitmapWidth: Int, bitmapHeight: Int) =
        width > 0 && height > 0 && left >= 0 && top >= 0 && left + width <= bitmapWidth && top + height <= bitmapHeight
}

private const val MAX_FRAME_ZOOM = 5f

/** How far the picture may be moved at this zoom before an edge of it would show inside the screen. */
internal fun maxPan(container: Float, bitmap: Int, base: Float, zoom: Float) = ((bitmap * base * zoom - container) / 2f).coerceAtLeast(0f)

/**
 * The part of a photo showing on screen. The picture is first scaled to just cover the screen, then zoomed ([zoom], at least 1)
 * and moved ([offsetX], [offsetY] in screen pixels from its centered position), which is what the editor draws.
 */
internal fun photoCrop(containerWidth: Float, containerHeight: Float, bitmapWidth: Int, bitmapHeight: Int,
    zoom: Float, offsetX: Float, offsetY: Float): PhotoCrop {
    val base = maxOf(containerWidth / bitmapWidth, containerHeight / bitmapHeight)
    val scale = base * zoom.coerceAtLeast(1f)
    val visibleWidth = (containerWidth / scale).coerceAtMost(bitmapWidth.toFloat())
    val visibleHeight = (containerHeight / scale).coerceAtMost(bitmapHeight.toFloat())
    val centerX = bitmapWidth / 2f - offsetX / scale
    val centerY = bitmapHeight / 2f - offsetY / scale
    val left = (centerX - visibleWidth / 2f).coerceIn(0f, bitmapWidth - visibleWidth)
    val top = (centerY - visibleHeight / 2f).coerceIn(0f, bitmapHeight - visibleHeight)
    val width = visibleWidth.roundToInt().coerceIn(1, bitmapWidth)
    val height = visibleHeight.roundToInt().coerceIn(1, bitmapHeight)
    return PhotoCrop(left.roundToInt().coerceIn(0, bitmapWidth - width), top.roundToInt().coerceIn(0, bitmapHeight - height), width, height)
}

/**
 * Apple's Add Wallpaper step for a photo: the picture fills the screen under a drawn Home, pinch to zoom and drag to frame
 * it, then Set keeps what you framed. Cancel leaves the wallpaper as it was.
 */
@Composable
internal fun PhotoFrameDialog(photo: Bitmap, state: LauncherState, onCancel: () -> Unit, onSet: (PhotoCrop, Boolean) -> Unit) {
    val reduceMotion = LocalReduceMotion.current
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, tween(if (reduceMotion) FolioMotion.FADE_MS else PREVIEW_MS)) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var area by remember { mutableStateOf(IntSize.Zero) }
    var blur by remember { mutableStateOf(false) }
    // The blurred copy is made the first time Blur is switched on, off the main thread, and kept for switching back and forth.
    val soft by produceState<Bitmap?>(null, photo, blur) { if (blur && value == null) value = withContext(Dispatchers.Default) { photo.blurredSmall() } }
    // Sharp picture first, the blurred one fading in over it: one soft cross-fade, a snap with Reduce Motion.
    val softAlpha by androidx.compose.animation.core.animateFloatAsState(if (blur && soft != null) 1f else 0f,
        if (reduceMotion) androidx.compose.animation.core.snap() else FolioMotion.spring<Float>(FolioMotion.Firm), label = "wallpaper blur")
    val description = stringResource(R.string.wallpaper_preview)
    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black).graphicsLayer { alpha = appear.value }
            .semantics { contentDescription = description }.testTag("wallpaper-frame-dialog")) {
            val image = remember(photo) { photo.asImageBitmap() }
            // The whole picture is drawn at its covering size (zoom 1) and moved from the middle, which is what photoCrop reads back.
            Canvas(Modifier.fillMaxSize().onSizeChanged { area = it }
                    .pointerInput(photo) {
                        detectTransformGestures { centroid, pan, gestureZoom, _ ->
                            val width = area.width.toFloat(); val height = area.height.toFloat()
                            if (width <= 0f || height <= 0f) return@detectTransformGestures
                            val base = maxOf(width / photo.width, height / photo.height)
                            val next = (zoom * gestureZoom).coerceIn(1f, MAX_FRAME_ZOOM)
                            val ratio = next / zoom
                            // Zoom about the fingers, not the middle, then follow them.
                            val about = Offset(centroid.x - width / 2f, centroid.y - height / 2f)
                            val moved = (offset - about) * ratio + about + pan
                            val limitX = maxPan(width, photo.width, base, next); val limitY = maxPan(height, photo.height, base, next)
                            zoom = next
                            offset = Offset(moved.x.coerceIn(-limitX, limitX), moved.y.coerceIn(-limitY, limitY))
                        }
                    }
            ) {
                val scale = maxOf(this.size.width / photo.width, this.size.height / photo.height) * zoom
                val width = photo.width * scale; val height = photo.height * scale
                val place = IntOffset(((this.size.width - width) / 2f + offset.x).roundToInt(), ((this.size.height - height) / 2f + offset.y).roundToInt())
                val placed = IntSize(width.roundToInt(), height.roundToInt())
                drawImage(image, dstOffset = place, dstSize = placed, filterQuality = FilterQuality.Medium)
                if (softAlpha > 0f) soft?.let { drawImage(it.asImageBitmap(), dstOffset = place, dstSize = placed, alpha = softAlpha, filterQuality = FilterQuality.Medium) }
            }
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .25f), Color.Transparent, Color.Black.copy(alpha = .6f)))))
            RealHome(state, photo, FRAME_CONTROLS_DP.dp)
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars).padding(horizontal = FolioSpace.XXL.dp, vertical = FolioSpace.LARGE.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.weight(1f))
                BlurSwitch(blur) { blur = it }
                Text(stringResource(R.string.wallpaper_frame_hint), color = Color.White.copy(alpha = .85f), fontSize = FolioType.FOOTNOTE.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = FolioSpace.MEDIUM.dp))
                Row(Modifier.fillMaxWidth().padding(top = FolioSpace.LARGE.dp), horizontalArrangement = Arrangement.spacedBy(FolioSpace.MEDIUM.dp)) {
                    PreviewButton(stringResource(R.string.cancel), Color.White.copy(alpha = .22f), Color.White, "wallpaper-frame-cancel", onCancel, Modifier.weight(1f))
                    PreviewButton(stringResource(R.string.set), LocalAccent.current.fill, Color.White, "wallpaper-frame-set",
                        { onSet(photoCrop(area.width.toFloat(), area.height.toFloat(), photo.width, photo.height, zoom, offset.x, offset.y), blur) }, Modifier.weight(1f))
                }
            }
        }
    }
}

/** iOS's Blur switch: a pill that is lit while the picture is soft, so icons stand out on a busy photo. */
@Composable
private fun BlurSwitch(on: Boolean, onChange: (Boolean) -> Unit) {
    val label = stringResource(R.string.wallpaper_blur)
    Box(Modifier.padding(top = FolioSpace.MEDIUM.dp).defaultMinSize(minHeight = FolioTouch.MIN.dp).clip(RoundedCornerShape(FolioRadius.RAIL.dp))
        .background(if (on) Color.White.copy(alpha = .92f) else Color.White.copy(alpha = .22f))
        .toggleable(value = on, role = Role.Switch, onValueChange = onChange).semantics { contentDescription = label }.testTag("wallpaper-frame-blur"),
        contentAlignment = Alignment.Center) {
        Text(label, color = if (on) Color.Black.copy(alpha = .85f) else Color.White, fontSize = FolioType.BODY.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = FolioSpace.XXL.dp, vertical = FolioSpace.COMPACT.dp))
    }
}

/**
 * Home as it will be: your own icons, widgets, folders, status and dock, drawn by the same code as the Settings preview,
 * over the picture this screen already draws, so the art is judged under what will really sit on it. A phone-shaped
 * screen shows one page; a wide one (the unfolded Fold) shows two pages and one Side Bar, as the open Fold does.
 */
@Composable
private fun RealHome(state: LauncherState, picture: Bitmap?, reserve: androidx.compose.ui.unit.Dp) {
    // Automatic ink follows the picture, as it does on Home once the picture is set (the same WallpaperColors read), so
    // dark text shows over a pale picture here too instead of the current wallpaper's white.
    val current = LocalWallpaperTone.current
    val tone by produceState(current, picture) {
        val source = picture?.takeUnless { it.isRecycled } ?: return@produceState
        value = withContext(Dispatchers.Default) { runCatching { android.app.WallpaperColors.fromBitmap(source).tone() }.getOrDefault(current) }
    }
    // Home's own darkening and "Darken behind text" shade, from the same code, over the whole screen: white labels on a pale
    // picture are only readable on Home because of it.
    val ink = homeInkFor(state.homeInk, tone.prefersDarkText)
    val dim = if (state.dimWallpaperDark && LocalDuoPalette.current.dark) .3f else 0f
    val scrim = HomeScrim.of(state.homeScrim, ink.dark, dim)
    CompositionLocalProvider(LocalWallpaperTone provides tone) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // Open or closed is the screen's shape, not the room left above the controls: with a panel open that room is wider than
            // tall even on a phone, and a phone's Home must stay one page.
            val open = maxWidth > maxHeight
            if (dim > 0f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dim)))
            if (scrim.draws) Box(Modifier.fillMaxSize().homeScrim(scrim))
            BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).padding(bottom = reserve), contentAlignment = Alignment.TopCenter) {
                val pages = if (open) 2 else 1
                // Home is drawn on a cover-sized page; fit as many of them as the room allows without cropping or stretching one.
                val height = minOf(maxHeight, maxWidth / (HOME_PAGE_ASPECT * pages))
                Row {
                    if (open) {
                        MiniHomePreview(null, state, height, framed = false, sideBar = state.leftHanded, preset = state.expanded, background = false)
                        MiniHomePreview(null, state, height, framed = false, sideBar = !state.leftHanded, preset = state.expanded, background = false)
                    } else {
                        MiniHomePreview(null, state, height, framed = false, background = false)
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewButton(label: String, fill: Color, ink: Color, tag: String, onClick: () -> Unit, modifier: Modifier) {
    Box(
        modifier.defaultMinSize(minHeight = FolioTouch.MIN.dp).clip(RoundedCornerShape(FolioRadius.RAIL.dp)).background(fill)
            .clickable(onClick = onClick).semantics { role = Role.Button; contentDescription = label }.testTag(tag),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = ink, fontSize = FolioType.BODY.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = FolioSpace.COMPACT.dp)) }
}
