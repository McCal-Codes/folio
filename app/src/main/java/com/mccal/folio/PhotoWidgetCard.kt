package com.mccal.folio

import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

/** Longest side, in pixels, a photo is decoded to; never the full bitmap. */
private const val PHOTO_DECODE_PX = 1400

/**
 * Keeps live Discover's host closed while the photo picker is open. That host sits over Home as the resumed activity,
 * so Home stays paused and Android holds the picker's result until something resumes Home: the pick looked lost.
 * Closing it first, as the wallpaper picker does, lets the result reach Home the moment the picker closes.
 */
internal class PhotoPickHold(private val hold: (Boolean) -> Unit) {
    var pending = false
        private set

    /** Closes the host, then opens the picker; a picker that will not open lets the host come back. */
    fun launch(open: () -> Unit) {
        if (!pending) { pending = true; hold(true) }
        try { open() } catch (error: Exception) { release(); Diagnostics.caught("Photos widget: opening the photo picker", error) }
    }

    /** The picker answered (or the card left Home): Discover may start again. */
    fun release() { if (pending) { pending = false; hold(false) } }
}

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
 * Photos options, the back of the widget: change the photos, the date caption, shuffle and the slideshow speed (5, 10 or
 * 30 seconds). It fills the widget's own bounds and scrolls, so a small widget still reaches every option; Done turns it back.
 */
@Composable
private fun PhotoOptionsBack(state: PhotoWidgetState, onChange: (PhotoWidgetState) -> Unit, onChangePhotos: () -> Unit, onDismiss: () -> Unit) {
    val shape = RoundedCornerShape(FolioRadius.PANEL.dp)
    // The accent's light ink: the theme's primary is too dark to read on this card.
    val ink = LocalAccent.current.ink
    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides Color.White) {
        Column(Modifier.fillMaxSize().clip(shape).background(FolioColors.PopoverSurface).testTag("photo-options")
            .padding(horizontal = 14.dp, vertical = 8.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = FolioTouch.MIN.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.photo_options), Modifier.weight(1f).semantics { heading() }, fontWeight = FontWeight.SemiBold, maxLines = 1)
                androidx.compose.material3.TextButton(onDismiss, colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = ink)) { Text(stringResource(R.string.done)) }
            }
            // Fades at the edge that has more under it, so a small widget shows that its options scroll.
            Column(Modifier.weight(1f).fadingVerticalScroll()) {
                Row(Modifier.fillMaxWidth().heightIn(min = FolioTouch.MIN.dp).clickable(role = Role.Button, onClick = onChangePhotos),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.change_photos), color = ink)
                }
                @Composable fun switchRow(label: String, on: Boolean, set: (Boolean) -> Unit) = Row(
                    Modifier.fillMaxWidth().heightIn(min = FolioTouch.MIN.dp).toggleable(on, role = Role.Switch, onValueChange = set),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(label, Modifier.weight(1f)); androidx.compose.material3.Switch(on, null)
                }
                switchRow(stringResource(R.string.photo_show_date), state.showCaption) { onChange(state.copy(showCaption = it)) }
                switchRow(stringResource(R.string.photo_shuffle), state.shuffle) { onChange(state.copy(shuffle = it)) }
                Text(stringResource(R.string.photo_interval), Modifier.padding(top = 8.dp), fontSize = FolioType.FOOTNOTE.sp)
                PHOTO_INTERVALS.forEach { sec ->
                    Row(Modifier.fillMaxWidth().heightIn(min = FolioTouch.MIN.dp)
                        .selectable(state.intervalSeconds == sec, role = Role.RadioButton) { onChange(state.copy(intervalSeconds = sec)) },
                        verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.RadioButton(state.intervalSeconds == sec, null)
                        Text(stringResource(R.string.photo_seconds, sec), Modifier.padding(start = 12.dp))
                    }
                }
            }
        }
    }
}

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
    var showOptions by remember { mutableStateOf(false) }
    val reduce = LocalReduceMotion.current
    // The long-press menu's Customize and Remove this photo reach the card through PhotoWidgetBus.
    val optionsRequest by PhotoWidgetBus.optionsRequests.collectAsStateWithLifecycle()
    var seenRequest by remember { mutableIntStateOf(optionsRequest) }
    LaunchedEffect(optionsRequest) { if (optionsRequest != seenRequest) { seenRequest = optionsRequest; showOptions = true } }
    val changes by PhotoWidgetBus.changes.collectAsStateWithLifecycle()
    LaunchedEffect(changes) { if (changes > 0) state = PhotoWidgetStore.load(context) }
    fun update(next: PhotoWidgetState) { state = next; PhotoWidgetStore.save(context, next) }

    // Only Home's own activity hosts live Discover; anywhere else there is nothing to hold.
    val home = androidx.activity.compose.LocalActivity.current as? MainActivity
    val pickHold = remember(home) {
        PhotoPickHold { active -> home?.let { LiveDiscover.setExternalResultPending(it, "main", "photo-widget-picker", active) } }
    }
    DisposableEffect(pickHold) { onDispose { pickHold.release() } }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(20)) { picked ->
        pickHold.release()
        if (picked.isNotEmpty()) {
            picked.forEach { runCatching { context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
            update(state.copy(uris = picked.map { it.toString() }, index = 0))
        }
    }
    val pick = { pickHold.launch { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) } }

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

    // Tapping opens the options; while editing Home a tap is the usual widget-options tap instead.
    val editing = LocalHomeEdit.current.active
    // Back (or Done) turns the widget back to its photo.
    androidx.activity.compose.BackHandler(showOptions) { showOptions = false }
    // Edit Widget turns the widget over to its options (WidgetFlip); Reduce Motion cross-fades instead.
    WidgetFlip(showOptions, Modifier.fillMaxSize(),
        back = { PhotoOptionsBack(state, onChange = ::update, onChangePhotos = { showOptions = false; pick() }, onDismiss = { showOptions = false }) }) {
    Box(Modifier.fillMaxSize().clip(shape).background(FolioGlass.module).clickable { if (editing) onEdit() else showOptions = true }
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
}
