package com.mccal.folio

import android.app.Activity
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.animation.ValueAnimator
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/** A non-interactive visual frame above the Google window. Activity bounds constrain the feed;
 * this only paints the rounded perimeter, and never intercepts or forwards its touch events. */
internal class DiscoverFrame(private val activity: Activity, private val verticalStatus: Boolean, private val live: Boolean = false) {
    private var view: View? = null
    private var manager: WindowManager? = null
    var liveProgress = 0f
        set(value) { field = value; view?.postInvalidateOnAnimation() }
    private var coverAlpha = 1f
    private var revealAnimator: ValueAnimator? = null
    private var lastTraceBucket = Int.MIN_VALUE
    private val backgroundChanged: () -> Unit = ::invalidate
    var fullSize: Size = Size.Zero
        set(value) { field = value; view?.invalidate() }
    var origin: Offset = Offset.Zero
        set(value) { field = value; view?.invalidate() }
    fun invalidate() { view?.postInvalidateOnAnimation() }
    fun reveal() {
        // Add after Google's first visible callback: both windows use DRAWN_APPLICATION,
        // so creating the frame during binding would put it underneath the native feed.
        show()
        if (live) { coverAlpha = 0f; return }
        // The same rule LocalReduceMotion follows, from the same helper: this is a View, so it can't read the local.
        if (reduceMotionEnabled(activity)) {
            coverAlpha = 0f; invalidate(); return
        }
        if (coverAlpha <= 0f || revealAnimator != null) return
        revealAnimator = ValueAnimator.ofFloat(coverAlpha, 0f).apply {
            startDelay = 50
            duration = 160
            addUpdateListener { coverAlpha = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    fun show() {
        if (view != null || activity.isDestroyed) return
        val frame = object : View(activity) {
            private val painter = CanvasDrawScope()
            private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0x66ffffff; style = Paint.Style.STROKE; strokeWidth = resources.displayMetrics.density
            }
            private val cover = Paint()
            private val clipPath = Path() // reused every frame (no allocation while drawing)
            override fun onAttachedToWindow() {
                super.onAttachedToWindow()
                windowInsetsController?.apply {
                    systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    if (verticalStatus) hide(WindowInsets.Type.statusBars())
                }
            }
            override fun onDraw(canvas: Canvas) {
                val d = resources.displayMetrics.density
                if (live) {
                    // Keep Home over a late native frame until Google confirms dismissal.
                    if (liveProgress <= 0f && LiveDiscover.nativePosition <= 0f) return
                    val right = minOf(width * liveProgress, LiveDiscover.progress * LiveDiscover.pageWidth -
                        (LiveDiscover.pageWidth - LiveDiscover.viewport.width())).coerceAtLeast(0f)
                    val left = right - width
                    if (DuoMotionTrace.enabled) {
                        val bucket = (liveProgress.coerceIn(0f, 1f) * 20f).toInt()
                        if (bucket != lastTraceBucket || liveProgress == 0f || liveProgress == 1f) {
                            lastTraceBucket = bucket
                            val frameLocation = IntArray(2).also(::getLocationOnScreen)
                            val decorLocation = IntArray(2).also(activity.window.decorView::getLocationOnScreen)
                            val matrix = Matrix().also(canvas::getMatrix)
                            val values = FloatArray(9).also(matrix::getValues)
                            val windowBounds = activity.windowManager.currentWindowMetrics.bounds
                            val layer = LiveDiscover.homeLayer
                            DuoMotionTrace.event("discover_frame_draw",
                                "live=$liveProgress compose=${LiveDiscover.progress} native=${LiveDiscover.nativePosition} " +
                                    "clip=[$left,$right] frame=${width}x$height frameScreen=${frameLocation.contentToString()} " +
                                    "decorScreen=${decorLocation.contentToString()} window=$windowBounds viewport=${LiveDiscover.viewport} " +
                                    "full=${LiveDiscover.fullSize} pagerOrigin=${LiveDiscover.pagerOrigin} " +
                                    "layer=${layer?.size} matrix=${values.contentToString()}")
                        }
                    }
                    val shape = clipPath.apply { reset(); addRoundRect(left, 0f, right, height.toFloat(), 16*d, 16*d, Path.Direction.CW) }
                    val save = canvas.save()
                    canvas.clipOutPath(shape)
                    val layer = LiveDiscover.homeLayer
                    if (layer != null && !layer.isReleased) {
                        canvas.translate(-LiveDiscover.viewport.left.toFloat(), -LiveDiscover.viewport.top.toFloat())
                        painter.draw(Density(d), LayoutDirection.Ltr, androidx.compose.ui.graphics.Canvas(canvas),
                            LiveDiscover.fullSize) {
                            drawLauncherBackground(LauncherBackgroundCache.bitmap?.asImageBitmap(), DuoAppearanceRuntime.dark)
                            translate(LiveDiscover.pagerOrigin.x, LiveDiscover.pagerOrigin.y) { drawLayer(layer) }
                        }
                    }
                    canvas.restoreToCount(save)
                    return
                }
                val insets = rootWindowInsets?.getInsets(WindowInsets.Type.systemBars())
                val path = clipPath.apply {
                    reset()
                    if (DiscoverBounds.available) addRoundRect(0f, 0f, width.toFloat(), height.toFloat(), 16*d, 16*d, Path.Direction.CW)
                    else addRoundRect(2*d, (insets?.top ?: 0) + 12*d, width - 2*d,
                        height - (insets?.bottom ?: 0) - 12*d, 26*d, 26*d, Path.Direction.CW)
                }
                path.offset(-(1f - DiscoverMotion.progress.floatValue) * width, 0f)
                val saved = canvas.save()
                canvas.clipOutPath(path)
                canvas.translate(-origin.x, -origin.y)
                painter.draw(Density(d), LayoutDirection.Ltr, androidx.compose.ui.graphics.Canvas(canvas),
                    if (fullSize == Size.Zero) Size(width.toFloat(), height.toFloat()) else fullSize) {
                        drawLauncherBackground(LauncherBackgroundCache.bitmap?.asImageBitmap(), DuoAppearanceRuntime.dark)
                    }
                if (DiscoverBounds.available) canvas.drawColor(
                    if (DuoAppearanceRuntime.dark) 0xeb263a43.toInt() else 0xebe8eff2.toInt())
                canvas.restoreToCount(saved)
                canvas.drawPath(path, border)
                if (coverAlpha > 0f) {
                    cover.color = if (DuoAppearanceRuntime.dark) 0xff263a43.toInt() else 0xffe8eff2.toInt()
                    cover.alpha = (coverAlpha * 255).toInt()
                    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), cover)
                }
                DiscoverMotion.drawHome(canvas, origin.x, origin.y, fullSize.width, fullSize.height)
            }
        }
        val attrs = WindowManager.LayoutParams(-1, -1, WindowManager.LayoutParams.TYPE_DRAWN_APPLICATION,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS,
            PixelFormat.TRANSLUCENT).apply {
            token = activity.window.attributes.token
            title = "DuoDiscoverFrame"
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            setFitInsetsTypes(0)
        }
        val wm = activity.createWindowContext(WindowManager.LayoutParams.TYPE_DRAWN_APPLICATION, null)
            .getSystemService(WindowManager::class.java)
        // Frame failure should leave a usable rectangular feed, not crash the launcher.
        if (runCatching { wm.addView(frame, attrs) }.isSuccess) {
            manager = wm; view = frame; LauncherBackgroundCache.listen(backgroundChanged)
        }
    }
    fun hide() {
        revealAnimator?.cancel(); revealAnimator = null; coverAlpha = 1f
        LauncherBackgroundCache.forget(backgroundChanged)
        view?.let { runCatching { manager?.removeViewImmediate(it) } }; view = null; manager = null
    }
}
