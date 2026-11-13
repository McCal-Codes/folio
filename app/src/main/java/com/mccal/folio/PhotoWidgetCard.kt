package com.mccal.folio

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

/** Longest side, in pixels, a photo is decoded to; never the full bitmap. */
private const val PHOTO_DECODE_PX = 1400

/** Decodes [uri] downsampled so its longest side is near [PHOTO_DECODE_PX]. Null when unreadable. */
private fun decodePhoto(context: Context, uri: Uri): Bitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= PHOTO_DECODE_PX) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
}.getOrNull()

/** Capture date from EXIF as a caption line, or null. */
private fun photoDateLine(context: Context, uri: Uri): String? = runCatching {
    context.contentResolver.openInputStream(uri)?.use { s ->
        val raw = android.media.ExifInterface(s).getAttribute(android.media.ExifInterface.TAG_DATETIME_ORIGINAL)
            ?: return@use null
        val millis = java.text.SimpleDateFormat("yyyy:MM:dd HH:mm:ss", java.util.Locale.US).parse(raw)?.time
        photoCaptionDate(millis) { DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it)) }
    }
}.getOrNull()

private fun canRead(context: Context, uri: String): Boolean =
    runCatching { context.contentResolver.openInputStream(Uri.parse(uri))?.close() != null }.getOrDefault(false)

/**
 * The Memories-style photo card: one picked photo, center-cropped, with a caption on a dark scrim and a play/pause
 * control that starts a slow cross-fade slideshow. The slideshow changes at most once every
 * [PHOTO_MIN_INTERVAL_SECONDS] seconds, fades rather than cuts, and does not run under Reduce Motion.
 */
@Composable
internal fun PhotoWidgetCard(onEdit: () -> Unit) {
    val context = LocalContext.current
    var state by remember { mutableStateOf(PhotoWidgetStore.load(context).withoutLost { canRead(context, it) }) }
    var playing by remember { mutableStateOf(false) }
    val reduce = LocalReduceMotion.current
    fun update(next: PhotoWidgetState) { state = next; PhotoWidgetStore.save(context, next) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(20)) { picked ->
        if (picked.isNotEmpty()) {
            picked.forEach { runCatching { context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
            update(state.copy(uris = picked.map { it.toString() }, index = 0))
        }
    }
    val pick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }

    val count = state.uris.size
    if (playing && !reduce && count > 1) {
        LaunchedEffect(state.uris, state.shuffle, state.intervalSeconds) {
            while (true) {
                delay(photoInterval(state.intervalSeconds) * 1000L)
                val next = if (state.shuffle) shuffledNextIndex(state.index, count) { (0 until it).random() } else nextPhotoIndex(state.index, count)
                update(state.copy(index = next))
            }
        }
    }

    val shape = RoundedCornerShape(FolioRadius.PANEL.dp)
    if (count == 0) {
        // Empty (or every grant lost, as after a restore on another phone): an Add photos card, never a blank one.
        Column(Modifier.fillMaxSize().clip(shape).background(FolioGlass.module).clickable(onClick = pick).padding(14.dp),
            verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.AddPhotoAlternate, null, tint = Color.White, modifier = Modifier.size(28.dp))
            Text(stringResource(R.string.add_photos), color = Color.White, fontSize = FolioType.SUBHEAD.sp, maxLines = 2)
        }
        return
    }

    val uri = Uri.parse(state.uris[state.index.coerceIn(0, count - 1)])
    val caption = if (state.showCaption) produceState<String?>(null, uri) { value = withContext(Dispatchers.IO) { photoDateLine(context, uri) } }.value else null
    val description = photoDescription(caption, state.index, count) { n, c -> context.getString(R.string.photo_n_of, n, c) }

    Box(Modifier.fillMaxSize().clip(shape).background(FolioGlass.module).clickable(onClick = onEdit)
        .semantics(mergeDescendants = true) { contentDescription = description }) {
        Crossfade(uri, animationSpec = if (reduce) snap() else tween(1200), label = "photoFade") { u ->
            val bitmap = produceState<Bitmap?>(null, u) { value = withContext(Dispatchers.IO) { decodePhoto(context, u) } }.value
            if (bitmap != null) Image(bitmap.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        // Dark scrim under the caption keeps white text above 4.5:1 on any photo.
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .72f))))
            .padding(start = 14.dp, end = 14.dp, top = 28.dp, bottom = 12.dp)) {
            if (caption != null) Text(caption, color = Color.White, fontSize = FolioType.FOOTNOTE.sp, maxLines = 1,
                modifier = Modifier.align(Alignment.CenterStart).padding(end = 52.dp))
        }
        if (count > 1 && !reduce) {
            val label = stringResource(if (playing) R.string.pause_slideshow else R.string.play_slideshow)
            Box(Modifier.align(Alignment.BottomEnd).padding(6.dp).size(FolioTouch.MIN.dp).clip(CircleShape)
                .background(Color.Black.copy(alpha = .45f)).clickable { playing = !playing }
                .semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
                Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = Color.White)
            }
        }
    }
}
