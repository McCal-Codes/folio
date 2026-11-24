package com.mccal.folio

import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

/*
 * Android draws the phone's wallpaper behind Folio's window, so Folio's own blur and shade could not reach it: the icons went
 * soft while the picture stayed sharp. Only an app with All files access may read the wallpaper image (anything less is
 * refused with a security error, even for the home app), so this is optional and off until it is granted from Duet's settings.
 *
 * With it, a copy of the wallpaper is drawn under Folio's screen while a fold plays, over the half that moves, placed exactly
 * where Android places it (the picture scaled to cover the window and centered, then slid by the page offset when it is wider).
 * The fold's blur and shade then treat it like the rest of the screen. The still half, and the screen between folds, keep the
 * system's own wallpaper untouched. Nothing is kept, shown anywhere else, or sent.
 */

/** Where Android has slid a wider-than-the-screen wallpaper to, 0 (left) to 1 (right); centered when nothing moves it. */
internal object WallpaperPlacement {
    @Volatile var fraction = .5f
}

/** The largest side of the copy, in pixels: the fold blurs it almost at once, so more would only cost memory. */
private const val COPY_MAX_SIDE = 2048

/** Whether All files access is on for Folio right now. */
internal fun hasWallpaperAccess(): Boolean = Build.VERSION.SDK_INT >= 30 && Environment.isExternalStorageManager()

/** The system's screen for turning All files access on or off for Folio, or the general list where that one is missing. */
internal fun wallpaperAccessIntent(context: Context): Intent {
    val app = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}"))
    return if (app.resolveActivity(context.packageManager) != null) app else Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
}

/** Whether All files access is on, checked again each time the screen comes back (it is changed on a system screen). */
@Composable
internal fun rememberWallpaperAccess(): Boolean {
    var granted by remember { mutableStateOf(hasWallpaperAccess()) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) granted = hasWallpaperAccess() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return granted
}

/**
 * The phone's wallpaper as a picture, or null when [use] is false, the access is off, the wallpaper is a live one (there is no
 * picture to read) or it cannot be read. Loaded off the main thread, and again when the wallpaper changes.
 */
@Composable
internal fun rememberPhoneWallpaper(use: Boolean): ImageBitmap? {
    val context = LocalContext.current
    val access = rememberWallpaperAccess()
    var version by remember { mutableIntStateOf(0) }
    DisposableEffect(context, use) {
        val manager = runCatching { WallpaperManager.getInstance(context) }.getOrNull()
        val listener = WallpaperManager.OnColorsChangedListener { _, _ -> version++ }
        if (use && manager != null) runCatching { manager.addOnColorsChangedListener(listener, android.os.Handler(android.os.Looper.getMainLooper())) }
        onDispose { if (manager != null) runCatching { manager.removeOnColorsChangedListener(listener) } }
    }
    val image by produceState<ImageBitmap?>(null, use, access, version) {
        value = if (!use || !access) null else withContext(Dispatchers.IO) { loadPhoneWallpaper(context) }
    }
    return image
}

private fun loadPhoneWallpaper(context: Context): ImageBitmap? = runCatching {
    val manager = WallpaperManager.getInstance(context)
    if (manager.wallpaperInfo != null) return@runCatching null
    val source = (manager.getDrawable() as? BitmapDrawable)?.bitmap ?: return@runCatching null
    val scale = minOf(1f, COPY_MAX_SIDE / max(source.width, source.height).toFloat())
    val soft = if (source.config == Bitmap.Config.HARDWARE) source.copy(Bitmap.Config.ARGB_8888, false) else source
    val sized = if (scale < 1f) Bitmap.createScaledBitmap(soft, (soft.width * scale).roundToInt(), (soft.height * scale).roundToInt(), true) else soft
    sized.asImageBitmap()
}.getOrNull()

/** Draws [image] where Android has it, only over the half of the screen that moves (the other half is the system's own). */
internal fun DrawScope.drawPhoneWallpaper(image: ImageBitmap, geometry: FoldGeometry) {
    val w = size.width
    val h = size.height
    val s = max(w / image.width, h / image.height)
    val dw = image.width * s
    val dh = image.height * s
    val dx = -(dw - w) * (if (dw > w + 1f) WallpaperPlacement.fraction.coerceIn(0f, 1f) else .5f)
    val dy = -(dh - h) / 2f
    val hinge = geometry.hingePx
    val half = if (geometry.horizontal) {
        if (geometry.movingAfterHinge) floatArrayOf(0f, hinge, w, h) else floatArrayOf(0f, 0f, w, hinge)
    } else {
        if (geometry.movingAfterHinge) floatArrayOf(hinge, 0f, w, h) else floatArrayOf(0f, 0f, hinge, h)
    }
    clipRect(half[0], half[1], half[2], half[3]) {
        drawImage(image, dstOffset = IntOffset(dx.roundToInt(), dy.roundToInt()), dstSize = IntSize(dw.roundToInt(), dh.roundToInt()))
    }
}

/** Duet's setting for it: turns All files access on or off on the system's own screen, and says what it is for. */
@Composable
internal fun ColumnScope.PhoneWallpaperAccessRows() {
    val context = LocalContext.current
    val granted = rememberWallpaperAccess()
    IosActionRow(stringResource(if (granted) R.string.duet_wallpaper_manage else R.string.duet_wallpaper_allow), "duet-wallpaper-access",
        onClick = { runCatching { context.startActivity(wallpaperAccessIntent(context)) } })
    CardNote(stringResource(if (granted) R.string.duet_wallpaper_note_on else R.string.duet_wallpaper_note_off))
}
