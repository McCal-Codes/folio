package com.mccal.folio

import androidx.core.content.edit
import kotlinx.coroutines.flow.conflate

import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import android.content.SharedPreferences
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

@Composable
internal fun DuneWallpaper(modifier: Modifier = Modifier) {
    val palette = LocalDuoPalette.current
    val context = LocalContext.current.applicationContext
    val revision = LauncherBackgroundCache.revision.intValue
    val initial = remember(revision) { LauncherBackgroundCache.bitmap?.takeUnless { it.isRecycled } }
    val photo = produceState(initialValue = initial, key1 = context, key2 = revision) {
        value = withContext(Dispatchers.IO) { loadLauncherBackground(context) }
    }.value
    Canvas(Modifier.fillMaxSize().then(modifier)) { drawLauncherBackground(photo?.asImageBitmap(), palette.dark) }
}

internal fun DrawScope.drawLauncherBackground(photo: ImageBitmap?, dark: Boolean = false) {
    if (photo == null || photo.width <= 0 || photo.height <= 0) {
        drawDunes(dark)
        return
    }
    val destinationWidth = size.width.toInt().coerceAtLeast(1)
    val destinationHeight = size.height.toInt().coerceAtLeast(1)
    val sourceAspect = photo.width.toFloat() / photo.height
    val destinationAspect = destinationWidth.toFloat() / destinationHeight
    val sourceWidth: Int
    val sourceHeight: Int
    if (sourceAspect > destinationAspect) {
        sourceHeight = photo.height
        sourceWidth = (sourceHeight * destinationAspect).toInt().coerceIn(1, photo.width)
    } else {
        sourceWidth = photo.width
        sourceHeight = (sourceWidth / destinationAspect).toInt().coerceIn(1, photo.height)
    }
    drawImage(
        image = photo,
        srcOffset = IntOffset((photo.width - sourceWidth) / 2, (photo.height - sourceHeight) / 2),
        srcSize = IntSize(sourceWidth, sourceHeight),
        dstSize = IntSize(destinationWidth, destinationHeight),
    )
}

internal fun DrawScope.drawDunes(dark: Boolean = false) {
        val w = size.width; val h = size.height
        drawRect(Brush.verticalGradient(if (dark) listOf(Color(0xFF132832), Color(0xFF263E49), Color(0xFF463F35))
            else listOf(Color(0xFF41687E), Color(0xFF94ADB5), Color(0xFFD8CEB6))))
        fun dune(y: Float, crest: Float, color: Color) {
            val path = Path().apply {
                moveTo(0f, h * y)
                cubicTo(w * .3f, h * (y - crest), w * .6f, h * (y + crest), w, h * (y - crest * .35f))
                lineTo(w, h); lineTo(0f, h); close()
            }
            drawPath(path, color)
        }
        dune(.57f, .17f, if (dark) Color(0xFF5B5040) else Color(0xFFC9B38E))
        dune(.72f, .12f, if (dark) Color(0xFF453D32) else Color(0xFFA49373))
        dune(.85f, .19f, if (dark) Color(0xFF302C26) else Color(0xFF84775F))
        for (n in 0..28) {
            val y = h * (.84f + n * .011f)
            val path = Path().apply {
                moveTo(0f, y)
                cubicTo(w * .35f, y - h * .17f, w * .65f, y + h * .05f, w, y - h * .06f)
            }
            drawPath(path, Color.White.copy(alpha = .045f), style = androidx.compose.ui.graphics.drawscope.Stroke(1.3f))
        }
}

/** A static scene rendered on surface changes: no animation loop or background polling. */
class DuneWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = DuneEngine()

    inner class DuneEngine : Engine(), SharedPreferences.OnSharedPreferenceChangeListener {
        private val painter = CanvasDrawScope()
        private val appearance = AppearanceStore(this@DuneWallpaperService)
        private val appearancePrefs = getSharedPreferences("appearance", MODE_PRIVATE)
        private val backgroundPrefs = launcherBackgroundPreferences(this@DuneWallpaperService)
        private val loader = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        private var photo: ImageBitmap? = cachedLauncherBackground(this@DuneWallpaperService)?.asImageBitmap()
        private var photoLoad = 0
        private var photoLoading = false
        private var photoFailed = false
        private var visible = false
        private var timeReceiverRegistered = false
        private val timeReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (visible) { appearance.reloadFromPreferences(systemDark()); render(surfaceHolder) }
            }
        }
        private fun registerTimeReceiver() {
            if (timeReceiverRegistered) return
            ContextCompat.registerReceiver(this@DuneWallpaperService, timeReceiver, IntentFilter().apply {
                addAction(Intent.ACTION_TIME_TICK)
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
                addAction(Intent.ACTION_DATE_CHANGED)
                addAction(Intent.ACTION_CONFIGURATION_CHANGED)
            }, ContextCompat.RECEIVER_NOT_EXPORTED)
            timeReceiverRegistered = true
        }
        private fun unregisterTimeReceiver() {
            if (!timeReceiverRegistered) return
            unregisterReceiver(timeReceiver)
            timeReceiverRegistered = false
        }
        override fun onSurfaceCreated(holder: SurfaceHolder) { super.onSurfaceCreated(holder); appearance.reloadFromPreferences(systemDark()); render(holder) }
        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height); appearance.reloadFromPreferences(systemDark()); render(holder)
        }
        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            if (visible) {
                photoLoad++
                photoLoading = false
                photoFailed = false
                photo = cachedLauncherBackground(this@DuneWallpaperService)?.asImageBitmap()
                appearancePrefs.registerOnSharedPreferenceChangeListener(this)
                backgroundPrefs.registerOnSharedPreferenceChangeListener(this)
                registerTimeReceiver()
                appearance.reloadFromPreferences(systemDark()); render(surfaceHolder)
            } else {
                appearancePrefs.unregisterOnSharedPreferenceChangeListener(this)
                backgroundPrefs.unregisterOnSharedPreferenceChangeListener(this)
                unregisterTimeReceiver()
            }
        }
        override fun onDestroy() {
            photoLoad++
            loader.cancel()
            unregisterTimeReceiver()
            appearancePrefs.unregisterOnSharedPreferenceChangeListener(this)
            backgroundPrefs.unregisterOnSharedPreferenceChangeListener(this)
            super.onDestroy()
        }
        override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
            if (sharedPreferences === backgroundPrefs) {
                photoLoad++
                photo = cachedLauncherBackground(this@DuneWallpaperService)?.asImageBitmap()
                photoLoading = false
                photoFailed = false
            }
            if (visible) { appearance.reloadFromPreferences(systemDark()); render(surfaceHolder) }
        }
        private fun systemDark() = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        private fun render(holder: SurfaceHolder) {
            if (!holder.surface.isValid) return
            if (launcherBackgroundEnabled(this@DuneWallpaperService) && photo == null && !photoFailed) {
                if (photoLoading) return
                photoLoading = true
                val request = ++photoLoad
                loader.launch {
                    val loaded = withContext(Dispatchers.IO) { loadLauncherBackground(this@DuneWallpaperService) }
                    if (request == photoLoad) {
                        photoLoading = false
                        photo = loaded?.asImageBitmap()
                        photoFailed = loaded == null
                        if (visible) render(holder)
                    }
                }
                return
            }
            if (!launcherBackgroundEnabled(this@DuneWallpaperService)) { photo = null; photoFailed = false }
            val canvas = try { holder.lockCanvas() } catch (_: IllegalArgumentException) { null } ?: return
            try {
                painter.draw(Density(resources.displayMetrics.density), LayoutDirection.Ltr,
                    androidx.compose.ui.graphics.Canvas(canvas), Size(canvas.width.toFloat(), canvas.height.toFloat())) {
                        drawLauncherBackground(photo, appearance.state.dark)
                    }
            } finally { holder.unlockCanvasAndPost(canvas) }
        }
    }
}

/**
 * With Android's wallpaper behind Home, tell the wallpaper which page is showing so it can shift a little
 * as pages change (the parallax iPhone and most launchers have). Draws nothing.
 */
@Composable
internal fun SystemWallpaperParallax(pager: androidx.compose.foundation.pager.PagerState) {
    val view = androidx.compose.ui.platform.LocalView.current
    val manager = androidx.compose.runtime.remember(view) { android.app.WallpaperManager.getInstance(view.context) }
    androidx.compose.runtime.LaunchedEffect(pager, view) {
        // The offset call is a binder round-trip: keep it off the UI thread and drop stale positions.
        androidx.compose.runtime.snapshotFlow { pager.currentPage + pager.currentPageOffsetFraction to pager.pageCount }
            .conflate()
            .collect { (position, count) ->
                val token = view.windowToken ?: return@collect
                val steps = (count - 1).coerceAtLeast(1)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    runCatching {
                        manager.setWallpaperOffsetSteps(1f / steps, 0f)
                        manager.setWallpaperOffsets(token, (position / steps).coerceIn(0f, 1f), .5f)
                    }
                }
            }
    }
}

/**
 * Switches Home between Android's wallpaper and Folio's own background.
 *
 * Turning it on needs the window itself, not just a flag on it: `android:windowShowWallpaper` belongs to the theme
 * the window was built from, and a window built opaque shows the wallpaper only through the relayout before covering
 * it again, which looked like the wallpaper flashing up and vanishing. The setting is saved here, at once, and
 * [MainActivity] picks the wallpaper theme from it, so starting the screen again is what actually turns it on.
 *
 * Turning it off needs no restart: an opaque background over the same window hides the wallpaper, as it always did.
 */
/**
 * The activity behind a context. Compose inside a sheet or dialog hands out a wrapper rather than the activity, so
 * `context as? Activity` there is null and whatever it guarded silently never happens.
 */
internal tailrec fun android.content.Context.asActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is android.content.ContextWrapper -> baseContext.asActivity()
    else -> null
}

internal fun android.app.Activity.applyWallpaperWindow(system: Boolean) {
    // Saved here and on its own before the screen starts again: the full state save can be held back (a first run
    // still loading its apps), and the new window is built from what's saved. apply() is enough, since the new screen
    // runs in this process and reads the same preferences in memory.
    getSharedPreferences(SettingKeys.PREFS, 0).edit { putBoolean(SettingKeys.SYSTEM_WALLPAPER, system) }
    if (system && !showsWallpaper) { startAgain(); return }
    val background = if (system) android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
        else obtainStyledAttributes(R.style.Theme_Duo, intArrayOf(android.R.attr.windowBackground)).let { it.getDrawable(0).also { _ -> it.recycle() } }
    window.setBackgroundDrawable(background)
    if (system) window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
    else window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
}

/**
 * A new instance of this screen, with a new window. Not `recreate()`: Android keeps the old window across it, and a
 * window first made opaque stays opaque to the screen even after it asks for the wallpaper, so the wallpaper never
 * showed and every swipe piled onto the last frame (#12, #35). The same intent, so Settings opens again on top.
 */
private fun android.app.Activity.startAgain() {
    startActivity(android.content.Intent(intent).addFlags(
        android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK))
    finish()
}

/** Whether this window actually shows Android's wallpaper behind it, whatever the setting says. */
internal val android.app.Activity.showsWallpaper: Boolean
    get() = window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER != 0

/** Whether Home shows Android's wallpaper (read straight from saved state: needed before the activity's window exists). */
internal fun usesSystemWallpaper(context: android.content.Context): Boolean = runCatching {
    val prefs = context.getSharedPreferences(SettingKeys.PREFS, 0)
    if (prefs.contains(SettingKeys.SYSTEM_WALLPAPER)) return@runCatching prefs.getBoolean(SettingKeys.SYSTEM_WALLPAPER, false)
    org.json.JSONObject(prefs.getString(SettingKeys.STATE, "{}") ?: "{}").optBoolean("systemWallpaper", false)
}.getOrDefault(false)

/** Once per process: the window is rebuilt at most once to match the setting, so a mismatch can never loop. */
internal object WallpaperWindowRepair { var tried = false }
