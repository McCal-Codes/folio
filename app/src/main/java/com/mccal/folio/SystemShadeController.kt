package com.mccal.folio

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import java.lang.ref.WeakReference

/** iOS 27–style zones for a downward swipe on Home: notifications, search/assistant, controls. */
internal enum class ShadePanel { NOTIFICATIONS, SEARCH, QUICK_SETTINGS;
    companion object {
        /** The shortcut's panel from its name; Search is Folio's own and has no system panel, so only these two open one. */
        fun fromShortcut(name: String?): ShadePanel? = entries.firstOrNull { it != SEARCH && it.name == name }
    }
}

internal enum class ShadeOpenResult { OPENED, SERVICE_DISABLED, SERVICE_STARTING, ACTION_REJECTED }

/**
 * The platform exposes shade expansion to third-party apps only as accessibility global actions.
 * This service deliberately observes no events and cannot inspect windows or inject gestures.
 */
class SystemShadeAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        // The declaration lets Settings describe the service. Once bound, unsubscribe from
        // even our own package's events: global actions do not require event delivery.
        serviceInfo = serviceInfo.apply { eventTypes = 0 }
        instance = WeakReference(this)
        overlay = EverywhereOverlay(this).also { it.start() }
    }
    private var overlay: EverywhereOverlay? = null

    /**
     * One screenshot for the blur behind Folio's panels, shrunk and blurred at once on a worker thread ([FrostBlur]); the full picture is dropped
     * and nothing is saved or sent. [onResult] gets it on the main thread, or null when the phone refuses (a second picture too soon, a secure window
     * that comes out black is still a picture). It must be taken before the panel's own window exists, or the panel would be in it.
     */
    private fun snapshot(onResult: (android.graphics.Bitmap?) -> Unit) {
        val main = android.os.Handler(android.os.Looper.getMainLooper())
        val deliver = { bitmap: android.graphics.Bitmap? -> main.post { onResult(bitmap) }; Unit }
        runCatching {
            val displayId = runCatching { display?.displayId }.getOrNull() ?: android.view.Display.DEFAULT_DISPLAY
            takeScreenshot(displayId, backdropWorker, object : TakeScreenshotCallback {
                override fun onSuccess(result: ScreenshotResult) {
                    val blurred = runCatching {
                        val hardware = android.graphics.Bitmap.wrapHardwareBuffer(result.hardwareBuffer, result.colorSpace)
                        val soft = hardware?.copy(android.graphics.Bitmap.Config.ARGB_8888, false)
                        hardware?.recycle()
                        soft?.let { FrostBlur.blurred(it).also { _ -> it.recycle() } }
                    }.getOrNull()
                    runCatching { result.hardwareBuffer.close() }
                    deliver(blurred)
                }
                override fun onFailure(errorCode: Int) { deliver(null) }
            })
        }.onFailure { deliver(null) }
    }
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        overlay?.onConfigurationChanged()
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit
    override fun onDestroy() {
        overlay?.stop(); overlay = null
        if (instance.get() === this) instance.clear()
        super.onDestroy()
    }

    companion object {
        private var instance = WeakReference<SystemShadeAccessibilityService>(null)
        internal fun isConnected() = instance.get() != null
        private val backdropWorker: java.util.concurrent.Executor by lazy { java.util.concurrent.Executors.newSingleThreadExecutor() }

        /** Whether a screenshot can be asked for now: the service is running (Android 11 and later; Folio's minimum is higher). */
        internal fun canSnapshot(): Boolean = instance.get() != null

        /** See [snapshot]. With no service the answer is null at once. */
        internal fun takeBackdrop(onResult: (android.graphics.Bitmap?) -> Unit) { instance.get()?.snapshot(onResult) ?: onResult(null) }

        /** Screenshot, lock screen, power menu… through the gestures service; false when it isn't running. */
        internal fun global(action: Int): Boolean = instance.get()?.performGlobalAction(action) == true

        /**
         * Folio's own panel over the open app, when the switch for it is on and the phone is unlocked. False means nothing was drawn and
         * the caller should open the phone's shade ([open]); Home's own "Android notifications" button always uses [open].
         */
        internal fun openFolio(panel: ShadePanel): Boolean = instance.get()?.overlay?.openShade(panel) == true

        internal fun open(context: Context, panel: ShadePanel): ShadeOpenResult {
            val service = instance.get()
            if (service != null) {
                val action = when (panel) {
                    ShadePanel.NOTIFICATIONS -> GLOBAL_ACTION_NOTIFICATIONS
                    ShadePanel.QUICK_SETTINGS -> GLOBAL_ACTION_QUICK_SETTINGS
                    ShadePanel.SEARCH -> return ShadeOpenResult.ACTION_REJECTED // handled in-app
                }
                return if (service.performGlobalAction(action)) ShadeOpenResult.OPENED
                else ShadeOpenResult.ACTION_REJECTED
            }
            return if (isEnabled(context)) ShadeOpenResult.SERVICE_STARTING
            else ShadeOpenResult.SERVICE_DISABLED
        }

        private fun isEnabled(context: Context): Boolean {
            val component = ComponentName(context, SystemShadeAccessibilityService::class.java)
            val manager = context.getSystemService(AccessibilityManager::class.java)
            return manager.getEnabledAccessibilityServiceList(
                android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK
            ).any {
                val service = it.resolveInfo?.serviceInfo ?: return@any false
                ComponentName(service.packageName, service.name) == component
            }
        }
    }
}
