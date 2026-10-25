package com.mccal.folio

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager

/**
 * A thin strip over the top edge of other apps that turns a pull down into Folio's panel: the left half asks for Notification Center and
 * the right half for Control Center ([onPull] decides what that opens, Folio's own or the phone's). The strip sits where the status bar
 * is, so it takes the touches there; it draws nothing and tells TalkBack nothing. The rules (which half, what a pull is, how tall the
 * strip is, when it exists) are the pure functions in ShadeLayout.kt; this class only holds the window.
 */
internal class PullStrip(private val service: AccessibilityService, private val wm: WindowManager, private val onPull: (ShadePanel) -> Unit) {
    private var view: View? = null
    private val density get() = service.resources.displayMetrics.density

    fun add() {
        if (view != null) return
        val slop = ViewConfiguration.get(service).scaledTouchSlop.toFloat()
        val strip = object : View(service) {
            private var downX = 0f
            private var downY = 0f
            private var fired = false
            override fun onTouchEvent(event: MotionEvent): Boolean {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> { downX = event.x; downY = event.y; fired = false }
                    // Fired on the way down, not on release: the system cancels a touch that goes about 25 dp, so a later trigger never fires.
                    MotionEvent.ACTION_MOVE -> if (!fired && isPullDown(event.x - downX, event.y - downY, slop)) {
                        fired = true
                        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        onPull(pullPanel(downX, width.toFloat()))
                    }
                }
                return true
            }
        }.apply { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }
        val statusBar = runCatching { wm.currentWindowMetrics.windowInsets.getInsets(android.view.WindowInsets.Type.statusBars()).top }.getOrDefault(0)
        val params = WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT, pullStripHeight(statusBar, density),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.START
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        if (runCatching { wm.addView(strip, params) }.isSuccess) view = strip
    }

    fun remove() {
        view?.let { runCatching { wm.removeView(it) } }
        view = null
    }
}
