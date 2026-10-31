package com.mccal.folio

import android.accessibilityservice.AccessibilityService
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager

/**
 * Two small tabs on the top edge of an app that has hidden the status bar (a video, a game, a reader). In those apps Android keeps the swipe from the top
 * edge for its own bars, so the pull strip ([PullStrip]) stays away; the tabs are the second way in, a pull with a mark to start it from. The left one
 * asks for Notification Center and the right one for Control Center ([onPull] decides what that opens, as it does for the strip). They are still: nothing
 * about them moves or fades, they take only the touches on their own small area, and they tell TalkBack nothing, as the strip does.
 */
internal class PullTabs(private val service: AccessibilityService, private val wm: WindowManager, private val onPull: (ShadePanel) -> Unit) {
    private val views = mutableListOf<View>()
    val showing get() = views.isNotEmpty()
    private val density get() = service.resources.displayMetrics.density

    fun add() {
        if (views.isNotEmpty()) return
        addTab(ShadePanel.NOTIFICATIONS, Gravity.START)
        addTab(ShadePanel.QUICK_SETTINGS, Gravity.END)
    }

    private fun addTab(panel: ShadePanel, side: Int) {
        val slop = ViewConfiguration.get(service).scaledTouchSlop.toFloat()
        val d = density
        val tab = object : View(service) {
            private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.argb(153, 255, 255, 255) }
            private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.argb(102, 0, 0, 0); style = Paint.Style.STROKE; strokeWidth = d }
            private val pill = RectF()
            private var downX = 0f
            private var downY = 0f
            private var fired = false
            override fun onDraw(canvas: Canvas) {
                // A slim pill at the top of the touch area, white with a faint dark rim so it reads on a bright picture and a dark one.
                val w = PILL_WIDTH_DP * d; val h = PILL_HEIGHT_DP * d
                pill.set((width - w) / 2f, PILL_TOP_DP * d, (width + w) / 2f, PILL_TOP_DP * d + h)
                canvas.drawRoundRect(pill, h / 2f, h / 2f, fill)
                canvas.drawRoundRect(pill, h / 2f, h / 2f, edge)
            }
            override fun onTouchEvent(event: MotionEvent): Boolean {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> { downX = event.x; downY = event.y; fired = false }
                    // Fired on the way down, as the strip does, so a quick pull the system cancels still counts.
                    MotionEvent.ACTION_MOVE, MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> if (!fired && isPullDown(event.x - downX, event.y - downY, slop)) {
                        fired = true
                        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        onPull(panel)
                    }
                }
                return true
            }
        }.apply { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }
        val params = WindowManager.LayoutParams((TAB_WIDTH_DP * d).toInt(), (TAB_HEIGHT_DP * d).toInt(),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or side
            x = (TAB_EDGE_GAP_DP * d).toInt()
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        if (runCatching { wm.addView(tab, params) }.isSuccess) views += tab
    }

    fun remove() {
        views.forEach { v -> runCatching { wm.removeView(v) } }
        views.clear()
    }

    private companion object {
        /** The touch area is a little wider than the mark, so a thumb finds it; it is as tall as the strip's minimum. */
        const val TAB_WIDTH_DP = 72f
        const val TAB_HEIGHT_DP = 28f
        const val TAB_EDGE_GAP_DP = 24f
        const val PILL_WIDTH_DP = 36f
        const val PILL_HEIGHT_DP = 5f
        const val PILL_TOP_DP = 6f
    }
}
