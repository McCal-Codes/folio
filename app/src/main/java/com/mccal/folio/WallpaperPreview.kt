package com.mccal.folio

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.format.DateTimeFormatter

/*
 * The full-screen wallpaper preview (FeatureGate.WALLPAPER_PREVIEW; Lab: wallpaper-apple). Tapping art stages it, and while the
 * gate is open the staged picture is shown here on a drawn Home with Cancel and Set at the bottom, where a thumb is. Only Set
 * writes, and both buttons go through the controller's own cancelPendingArt and setPendingArt, so this holds no state of its own.
 *
 * A Dialog, not an inline Box: on the Fold8 an inline full-screen Box is clipped by the window's insets.
 * Reduce Motion: a plain fade. Otherwise one 240 ms fade and scale on opening; nothing loops or flashes (WCAG 2.3.1).
 */

/** How long the preview takes to appear, once. */
private const val PREVIEW_MS = 240

/** The scale the preview grows from. */
private const val PREVIEW_FROM = .96f

/** The drawn clock, a share of what Home's own clock is on a phone; a picture of the clock, not a size off the type scale. */
private const val DRAWN_CLOCK_SP = 72

/** A drawn icon: Home's icon size on a phone, as a stand-in rather than a setting. */
private const val DRAWN_ICON_DP = 56

private const val ICON_ROWS = 3
private const val ICON_COLUMNS = 4

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
internal fun WallpaperPreviewDialog(artId: String, thumbnail: Bitmap?, sharp: Bitmap?, onCancel: () -> Unit, onSet: () -> Unit) {
    val context = LocalContext.current
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

            Column(
                Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars).padding(horizontal = FolioSpace.XXL.dp, vertical = FolioSpace.LARGE.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                DrawnHome(Modifier.weight(1f))
                if (art != null) {
                    Text(art.title, color = Color.White, fontSize = FolioType.BODY.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                        maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = FolioSpace.MEDIUM.dp))
                    Text(wallpaperCredit(art.artist, art.license), color = Color.White.copy(alpha = .8f), fontSize = FolioType.FOOTNOTE.sp, textAlign = TextAlign.Center,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Row(Modifier.fillMaxWidth().padding(top = FolioSpace.LARGE.dp), horizontalArrangement = Arrangement.spacedBy(FolioSpace.MEDIUM.dp)) {
                    PreviewButton(stringResource(R.string.cancel), Color.White.copy(alpha = .22f), Color.White, "wallpaper-preview-cancel", onCancel, Modifier.weight(1f))
                    PreviewButton(stringResource(R.string.set), LocalAccent.current.fill, Color.White, "wallpaper-preview-set", onSet, Modifier.weight(1f))
                }
            }
        }
    }
}

/** Home as a picture: the clock, rows of icon stand-ins and the dock, so the art is judged under what will sit on it. */
@Composable
private fun DrawnHome(modifier: Modifier) {
    val pattern = if (android.text.format.DateFormat.is24HourFormat(LocalContext.current)) "HH:mm" else "h:mm"
    val stand = Color.White.copy(alpha = .55f)
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(currentTime().format(DateTimeFormatter.ofPattern(pattern)), color = Color.White, fontSize = DRAWN_CLOCK_SP.sp, fontWeight = FontWeight.Light,
            modifier = Modifier.padding(top = FolioSpace.HUGE.dp))
        Column(Modifier.padding(top = FolioSpace.HUGE.dp), verticalArrangement = Arrangement.spacedBy(FolioSpace.XXL.dp)) {
            repeat(ICON_ROWS) { IconRow(stand) }
        }
        Spacer(Modifier.weight(1f))
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(FolioRadius.RAIL.dp)).background(Color.White.copy(alpha = .25f)).padding(FolioSpace.MEDIUM.dp)) {
            IconRow(stand)
        }
    }
}

@Composable
private fun IconRow(color: Color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        repeat(ICON_COLUMNS) { Box(Modifier.size(DRAWN_ICON_DP.dp).clip(RoundedCornerShape(FolioRadius.GROUP.dp)).background(color)) }
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
