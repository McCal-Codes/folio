package com.mccal.folio

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.view.accessibility.AccessibilityEvent
import java.lang.ref.WeakReference
import kotlin.coroutines.resume
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Smart clock ink: the one thing this service can do is take a picture of the screen, so Folio can measure what is behind
 * the Big Clock when Android's own wallpaper is behind Home. Its own accessibility service, not the gestures service,
 * so turning it on is its own decision and the gestures service still cannot read the screen. It observes no events,
 * cannot inspect windows and cannot tap or type. Nothing here keeps the picture: see [SmartClockInk].
 */
class SmartInkService : AccessibilityService() {
    override fun onServiceConnected() {
        serviceInfo = serviceInfo.apply { eventTypes = 0 }
        instance = WeakReference(this)
        connected.value = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        release()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        release()
        super.onDestroy()
    }

    private fun release() {
        if (instance.get() === this) instance.clear()
        connected.value = false
    }

    companion object {
        private var instance = WeakReference<SmartInkService>(null)

        /** True while Android has the service on. Settings and the clock both follow it. */
        val connected = MutableStateFlow(false)

        /**
         * The screen right now, or null when the service is off or Android declines (it limits how often an app may take
         * one). The caller owns the bitmap and must recycle it as soon as it has measured what it needs.
         */
        internal suspend fun capture(displayId: Int): Bitmap? {
            val service = instance.get() ?: return null
            return suspendCancellableCoroutine { continuation ->
                service.takeScreenshot(displayId, { it.run() }, object : TakeScreenshotCallback {
                    override fun onSuccess(result: ScreenshotResult) {
                        val buffer = result.hardwareBuffer
                        val bitmap = try {
                            Bitmap.wrapHardwareBuffer(buffer, result.colorSpace)?.copy(Bitmap.Config.ARGB_8888, false)
                        } catch (_: Throwable) { null } finally { buffer.close() }
                        if (continuation.isActive) continuation.resume(bitmap) else bitmap?.recycle()
                    }
                    override fun onFailure(errorCode: Int) { if (continuation.isActive) continuation.resume(null) }
                })
            }
        }
    }
}
