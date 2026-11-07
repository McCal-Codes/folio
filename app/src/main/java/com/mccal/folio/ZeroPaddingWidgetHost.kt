package com.mccal.folio

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.os.Bundle
import android.util.SizeF
import android.view.MotionEvent
import androidx.compose.ui.geometry.Offset

internal class ZeroPaddingWidgetHost(context: Context, hostId: Int) : AppWidgetHost(context, hostId) {
    override fun onCreateView(context: Context, appWidgetId: Int,
        appWidget: AppWidgetProviderInfo): AppWidgetHostView =
        ZeroPaddingWidgetHostView(context)
}

private class ZeroPaddingWidgetHostView(context: Context) : AppWidgetHostView(context) {
    private var providerOwnsVerticalGesture = false
    private val publishCurrentSize = Runnable {
        if (!isAttachedToWindow || appWidgetId < 0 || width <= 0 || height <= 0) return@Runnable
        val density = resources.displayMetrics.density
        publishExactWidgetSize(this, width / density, height / density)
    }

    init {
        addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            if (right - left > 0 && bottom - top > 0 && (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop)) {
                scheduleSizePublication()
            }
        }
    }

    override fun setAppWidget(appWidgetId: Int, info: AppWidgetProviderInfo?) {
        super.setAppWidget(appWidgetId, info)
        setPadding(0, 0, 0, 0)
        scheduleSizePublication()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        scheduleSizePublication()
    }

    override fun onSizeChanged(widthPx: Int, heightPx: Int, oldWidthPx: Int, oldHeightPx: Int) {
        super.onSizeChanged(widthPx, heightPx, oldWidthPx, oldHeightPx)
        if (appWidgetId < 0 || widthPx <= 0 || heightPx <= 0) return
        // Avoid provider IPC inside a View/Compose layout callback. Coalesce size publication
        // until after the current layout pass finishes.
        scheduleSizePublication()
    }

    override fun onDetachedFromWindow() {
        if (providerOwnsVerticalGesture) parent?.requestDisallowInterceptTouchEvent(false)
        providerOwnsVerticalGesture = false
        removeCallbacks(publishCurrentSize)
        super.onDetachedFromWindow()
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // Compose's Home column is vertically scrollable too. Give a provider's
                // ListView/ScrollView the uninterrupted native stream when DOWN hit that
                // content. The common pager observes Initial events before AndroidView, so
                // a horizontal swipe can still claim its axis and cancel this stream.
                providerOwnsVerticalGesture = nativeWidgetConsumesVerticalGesture(
                    this,
                    Offset(event.rawX, event.rawY),
                )
                if (providerOwnsVerticalGesture) parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (providerOwnsVerticalGesture) parent?.requestDisallowInterceptTouchEvent(false)
                providerOwnsVerticalGesture = false
            }
        }
        return super.dispatchTouchEvent(event)
    }

    private fun scheduleSizePublication() {
        removeCallbacks(publishCurrentSize)
        post(publishCurrentSize)
    }
}

internal fun exactWidgetSizeOptions(widthDp: Float, heightDp: Float) = Bundle().apply {
    val width = widthDp.coerceAtLeast(1f)
    val height = heightDp.coerceAtLeast(1f)
    putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, width.toInt())
    putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, width.toInt())
    putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, height.toInt())
    putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, height.toInt())
    putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, arrayListOf(SizeF(width, height)))
}

private fun publishExactWidgetSize(view: AppWidgetHostView, widthDp: Float, heightDp: Float) {
    if (view.appWidgetId < 0) return
    val options = exactWidgetSizeOptions(widthDp, heightDp)
    val current = runCatching { AppWidgetManager.getInstance(view.context).getAppWidgetOptions(view.appWidgetId) }.getOrNull() ?: return
    val sameBounds = listOf(
        AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,
        AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH,
        AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,
        AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,
    ).all { current.getInt(it) == options.getInt(it) }
    val currentSizes = current.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)
    val nextSizes = options.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)
    if (!sameBounds || currentSizes != nextSizes) runCatching { view.updateAppWidgetOptions(options) }
}
