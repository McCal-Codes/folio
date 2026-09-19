package com.mccal.folio

import android.content.Context
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.WindowInsets
import android.view.WindowMetrics
import androidx.window.WindowSdkExtensions
import androidx.window.embedding.ActivityEmbeddingController
import java.lang.reflect.Proxy
import org.json.JSONObject

/** Optional adapter to the device's Window Extensions 8+ activity-stack API.
 * AndroidX 1.5 exposes this only internally, so resolve the public extension interface at runtime
 * and retain the stable split-host fallback. No hidden Android framework API or permission is used.
 * This owns only Duo's overlay stack calculator; it never embeds another app's activity.
 */
internal object DiscoverBounds {
    private const val TAG = "duo-discover-padded"
    private const val LIVE_DISCOVER_MIN_SDK = 37
    private var overlayKey: String? = null
    @Volatile private var measuredViewport: Rect? = null
    val available get() = overlayKey != null
    fun resetViewport() { measuredViewport = null }
    /** A mismatch here is why the feed can come back the wrong size after a fold; the trace says which size won. */
    fun matchesViewport(width: Int, height: Int): Boolean = !available || measuredViewport?.let {
        kotlin.math.abs(it.width() - width) <= 2 && kotlin.math.abs(it.height() - height) <= 2
    } == true
    @android.annotation.SuppressLint("RequiresWindowSdk") // Available only after the extension 8 check.
    fun updateViewport(context: Context, rect: Rect) {
        if (!available || rect == measuredViewport || rect.width() <= 0 || rect.height() <= 0) return
        measuredViewport = Rect(rect)
        ActivityEmbeddingController.getInstance(context).invalidateVisibleActivityStacks()
    }

    fun dockWidth(context: Context, widthDp: Float): Float = runCatching {
        JSONObject(context.getSharedPreferences("launcher", 0).getString("state", "{}") ?: "{}")
            .optJSONObject(if (widthDp * context.resources.configuration.classScale >= 650f) "expanded" else "compact")?.optDouble("dockWidth", 68.0)?.toFloat()
    }.getOrNull()?.coerceIn(56f, 84f) ?: 68f

    fun initialize(context: Context) {
        if (WindowSdkExtensions.getInstance().extensionVersion < 8) return
        // Discover hosted beside Home has only been verified on Android 17. On Android 16 (Galaxy Z Fold7, issue #12)
        // its frame painted stale copies of Home, so older versions use the plain full-screen Discover page.
        if (android.os.Build.VERSION.SDK_INT < LIVE_DISCOVER_MIN_SDK) return
        try {
            val extensions = Class.forName("androidx.window.extensions.WindowExtensionsProvider")
                .getMethod("getWindowExtensions").invoke(null)
            val component = Class.forName("androidx.window.extensions.WindowExtensions")
                .getMethod("getActivityEmbeddingComponent").invoke(extensions)
            val componentType = Class.forName("androidx.window.extensions.embedding.ActivityEmbeddingComponent")
            val functionType = Class.forName("androidx.window.extensions.core.util.function.Function")
            val paramsType = Class.forName("androidx.window.extensions.embedding.ActivityStackAttributesCalculatorParams")
            val parentType = Class.forName("androidx.window.extensions.embedding.ParentContainerInfo")
            val builderType = Class.forName("androidx.window.extensions.embedding.ActivityStackAttributes\$Builder")
            val constructor = builderType.getConstructor()
            val setBounds = builderType.getMethod("setRelativeBounds", Rect::class.java)
            val build = builderType.getMethod("build")
            val getParent = paramsType.getMethod("getParentContainerInfo")
            val getMetrics = parentType.getMethod("getWindowMetrics")
            val getConfig = parentType.getMethod("getConfiguration")
            val key = Class.forName("androidx.window.extensions.embedding.ActivityEmbeddingOptionsProperties")
                .getField("KEY_OVERLAY_TAG").get(null) as String
            val calculator = Proxy.newProxyInstance(functionType.classLoader, arrayOf(functionType)) { proxy, method, args ->
                when (method.name) {
                    "apply" -> {
                        val rect = measuredViewport?.let(::Rect) ?: runCatching {
                            val parent = getParent.invoke(args!![0])
                            val metrics = getMetrics.invoke(parent) as WindowMetrics
                            val configuration = getConfig.invoke(parent) as Configuration
                            val d = configuration.densityDpi / 160f
                            val dock = dockWidth(context, metrics.bounds.width() / d)
                            val vertical = runCatching { JSONObject(context.getSharedPreferences("launcher", 0).getString("state", "{}") ?: "{}").optBoolean("verticalStatus", true) }.getOrDefault(true)
                            val types = WindowInsets.Type.displayCutout() or WindowInsets.Type.navigationBars() or
                                (if (vertical) 0 else WindowInsets.Type.statusBars())
                            val insets = metrics.windowInsets.getInsetsIgnoringVisibility(types)
                            // Outer glass starts at 16dp; Google's real viewport is inset another
                            // 16dp. Its own native text padding remains completely unobscured.
                            val left = (insets.left + 32*d).toInt().coerceIn(0, metrics.bounds.width()-1)
                            val top = (insets.top + 32*d).toInt().coerceIn(0, metrics.bounds.height()-1)
                            Rect(left, top,
                                (metrics.bounds.width() - insets.right - (dock + 44)*d).toInt().coerceAtLeast(left+1),
                                (metrics.bounds.height() - insets.bottom - 32*d).toInt().coerceAtLeast(top+1))
                        }.getOrElse {
                            Log.w("DuoDiscover", "Padded bounds unavailable", it)
                            // Fail closed, leaving the launcher's back arrow accessible.
                            Rect(0, 0, 1, 1)
                        }
                        val builder = constructor.newInstance()
                        setBounds.invoke(builder, rect)
                        build.invoke(builder)
                    }
                    "toString" -> "Duo Discover bounds calculator"
                    "hashCode" -> System.identityHashCode(proxy)
                    "equals" -> proxy === args?.getOrNull(0)
                    else -> null
                }
            }
            componentType.getMethod("setActivityStackAttributesCalculator", functionType).invoke(component, calculator)
            overlayKey = key
            DiscoverClient.trace { "embedding ready" }
        } catch (e: ReflectiveOperationException) {
            Log.i("DuoDiscover", "Using split host; padded extension unavailable", e)
        } catch (e: LinkageError) {
            Log.i("DuoDiscover", "Using split host; extension differs on this device", e)
        } catch (e: RuntimeException) {
            Log.i("DuoDiscover", "Using split host; extension rejected setup", e)
        }
    }
    fun launchOptions(suppressOverlayAnimation: Boolean = false): Bundle? = overlayKey?.let { key -> Bundle().apply {
        putString(key, TAG)
        // Extensions 8–10's overlay implementation derives its TaskFragment animations solely
        // from this alignment hint. An unknown value keeps the explicit bounds but resolves
        // its open/close resources to 0. This narrow compatibility path is live-host-only;
        // newer extension versions retain their normal alignment animation as a fallback.
        val extension = WindowSdkExtensions.getInstance().extensionVersion
        val suppressForAuditedExtension = suppressOverlayAnimation && extension in 8..10
        putInt("androidx.window.embedding.ActivityStackAlignment",
            if (suppressForAuditedExtension) -2 else 0)
    } }
}
