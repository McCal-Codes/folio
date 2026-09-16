@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.mccal.folio

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Process
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import org.json.JSONObject

/** Set by MainActivity: while Folio's Home is showing, the everywhere overlays step aside. */
internal object FolioForeground { val visible = MutableStateFlow(false) }

/** Lifecycle for ComposeViews that live in accessibility overlay windows. */
private class OverlayOwner : LifecycleOwner, SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val saved = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = saved.savedStateRegistry
    fun start() { saved.performRestore(null); registry.currentState = Lifecycle.State.RESUMED }
    fun stop() { registry.currentState = Lifecycle.State.DESTROYED }
}

/**
 * Dock handle and Dynamic Island over every app, drawn from the shade-gesture accessibility service.
 * Windows are sized to their visible parts so the rest of the screen keeps working normally.
 */
internal class EverywhereOverlay(private val service: AccessibilityService) {
    private val wm = service.getSystemService(WindowManager::class.java)
    private val prefs = service.getSharedPreferences(SettingKeys.PREFS, 0)
    private val owner = OverlayOwner()
    private val density get() = service.resources.displayMetrics.density

    private var handle: View? = null
    private var dock: ComposeView? = null
    private var island: ComposeView? = null
    private val dockOpen = MutableStateFlow(false)

    private data class Settings(val dockEverywhere: Boolean, val islandEverywhere: Boolean, val leftHanded: Boolean, val dock: List<String>, val eventsOff: Set<String> = emptySet())
    private val settings = MutableStateFlow(readSettings())
    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key != SettingKeys.STATE) return@OnSharedPreferenceChangeListener
        val next = readSettings()
        if (next == settings.value) return@OnSharedPreferenceChangeListener // layout saves don't touch overlays
        val edgeMoved = next.leftHanded != settings.value.leftHanded
        settings.value = next
        if (edgeMoved) { removeHandle(); removeDock() }
        sync()
    }
    private var foregroundJob: kotlinx.coroutines.Job? = null
    private val scopeJob = kotlinx.coroutines.SupervisorJob()

    /** What the island would show right now; the window only exists while this is non-null. */
    private val islandContent = MutableStateFlow<IslandContent?>(null)

    fun start() {
        owner.start()
        IslandEvents.acquire(service)
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main.immediate + scopeJob)
        foregroundJob = scope.launch { FolioForeground.visible.collect { sync() } }
        scope.launch {
            kotlinx.coroutines.flow.combine(IslandListenerService.activity, IslandEvents.latest) { a, e ->
                a?.takeUnless { it is IslandActivity.Call && "CALL" in settings.value.eventsOff } to e }
                .collectLatest { (activity, eventPair) ->
                    val remaining = eventPair?.takeIf { it.first.kind !in settings.value.eventsOff }
                        ?.let { IslandEvents.showMs(it.first) - (System.currentTimeMillis() - it.second) } ?: 0L
                    islandContent.value = if (remaining > 0) IslandContent.Event(eventPair!!.first) else activity?.let { IslandContent.Live(it) }
                    sync()
                    if (remaining > 0) {
                        kotlinx.coroutines.delay(remaining)
                        islandContent.value = activity?.let { IslandContent.Live(it) }
                        sync()
                    }
                }
        }
        sync()
    }

    fun stop() {
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        scopeJob.cancel()
        removeHandle(); removeDock(); removeIsland()
        IslandEvents.release()
        owner.stop()
    }

    fun onConfigurationChanged() { removeHandle(); removeIsland(); sync() }

    private fun readSettings(): Settings = runCatching {
        val j = JSONObject(prefs.getString(SettingKeys.STATE, "{}") ?: "{}")
        val dockIds = j.optJSONArray(SettingKeys.DOCK)?.let { a -> (0 until a.length()).mapNotNull { a.optString(it).takeIf { s -> s.isNotBlank() && s != "null" } } }.orEmpty()
        val off = j.optJSONArray(SettingKeys.ISLAND_EVENTS_OFF)?.let { a -> (0 until a.length()).map(a::getString).toSet() }.orEmpty()
        Settings(j.optBoolean(SettingKeys.DOCK_EVERYWHERE, false), j.optBoolean(SettingKeys.ISLAND_EVERYWHERE, false), j.optBoolean(SettingKeys.LEFT_HANDED, false), dockIds, off)
    }.getOrDefault(Settings(false, false, false, emptyList()))

    private fun sync() {
        val s = settings.value.let { if (SafeMode.active) it.copy(dockEverywhere = false, islandEverywhere = false) else it }
        val home = FolioForeground.visible.value
        if (s.dockEverywhere && !home) addHandle(s.leftHanded) else { removeHandle(); removeDock() }
        val content = islandContent.value
        if (s.islandEverywhere && !home && content != null) { addIsland(); resizeIsland(content) } else removeIsland()
    }

    // ---- Dock handle -------------------------------------------------------------------------

    private fun addHandle(leftHanded: Boolean) {
        if (handle != null) return
        val view = object : View(service) {
            private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = 0x8CFFFFFF.toInt() }
            private var downX = 0f
            override fun onDraw(canvas: android.graphics.Canvas) {
                val w = 4 * density; val h = 56 * density
                val x = if (leftHanded) 3 * density else width - 3 * density - w
                canvas.drawRoundRect(x, (height - h) / 2, x + w, (height + h) / 2, w / 2, w / 2, paint)
            }
            override fun onTouchEvent(event: MotionEvent): Boolean {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> downX = event.rawX
                    MotionEvent.ACTION_MOVE -> if (kotlin.math.abs(event.rawX - downX) > 18 * density) { openDock(); return true }
                    MotionEvent.ACTION_UP -> { performClick(); openDock() }
                }
                return true
            }
            override fun performClick(): Boolean { super.performClick(); return true }
        }
        // As slim as can still be grabbed, so it barely overlaps the app or the back-gesture edge.
        val params = WindowManager.LayoutParams((14 * density).toInt(), (96 * density).toInt(),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT).apply {
            gravity = (if (leftHanded) Gravity.START else Gravity.END) or Gravity.CENTER_VERTICAL
            y = (-40 * density).toInt()
        }
        runCatching { wm.addView(view, params); handle = view }
    }

    private fun removeHandle() { handle?.let { runCatching { wm.removeView(it) } }; handle = null }

    private fun openDock() {
        if (dock != null) return
        val s = settings.value
        val apps = s.dock.mapNotNull { id -> resolveApp(id) }
        val view = ComposeView(service).apply {
            setViewTreeLifecycleOwner(owner); setViewTreeSavedStateRegistryOwner(owner)
            setContent {
                val open by dockOpen.collectAsState()
                LaunchedEffect(Unit) { dockOpen.value = true }
                Box(Modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, null) { closeDock() },
                    contentAlignment = if (s.leftHanded) Alignment.CenterStart else Alignment.CenterEnd) {
                    AnimatedVisibility(open, enter = fadeIn() + slideInHorizontally(spring(dampingRatio = .8f, stiffness = Spring.StiffnessMediumLow)) { if (s.leftHanded) -it else it },
                        exit = fadeOut() + slideOutHorizontally { if (s.leftHanded) -it else it }) {
                        Column(Modifier.padding(horizontal = 12.dp).width(72.dp).clip(RoundedCornerShape(30.dp))
                            .background(Color(0xFF1C1C1E).copy(alpha = .72f)).border(1.dp, Color.White.copy(alpha = .16f), RoundedCornerShape(30.dp))
                            .padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            apps.forEach { app ->
                                val hostView = androidx.compose.ui.platform.LocalView.current
                                Image(app.icon.asImageBitmap(), null, Modifier.size(50.dp).clip(RoundedCornerShape(13.dp))
                                    .combinedClickable(onClick = { closeDock(); launch(app) }, onLongClick = {
                                        // Drag beside the current app for split screen.
                                        if (startSplitDrag(hostView, service, app.component, app.user, "", app.icon)) closeDock()
                                    }))
                            }
                            if (apps.isNotEmpty()) HorizontalDivider(Modifier.width(40.dp), color = Color.White.copy(alpha = .2f))
                            Box(Modifier.size(50.dp).clip(RoundedCornerShape(13.dp)).background(Color.White.copy(alpha = .16f)).clickable {
                                closeDock(); service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                            }, contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Home, "Home", tint = Color.White) }
                        }
                    }
                }
            }
        }
        val params = WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_DIM_BEHIND,
            PixelFormat.TRANSLUCENT).apply { dimAmount = .18f }
        runCatching { wm.addView(view, params); dock = view }
    }

    private fun closeDock() {
        dockOpen.value = false
        dock?.postDelayed({ removeDock() }, 220)
    }

    private fun removeDock() { dock?.let { runCatching { wm.removeView(it) } }; dock = null; dockOpen.value = false }

    private data class DockApp(val component: ComponentName, val user: android.os.UserHandle, val icon: Bitmap)
    private val iconCache = android.util.LruCache<String, Bitmap>(16)

    private fun resolveApp(id: String): DockApp? {
        val identity = parseProfileAppId(id) ?: return null
        val component = ComponentName.unflattenFromString(identity.component) ?: return null
        val users = service.getSystemService(android.os.UserManager::class.java)
        val user = identity.userSerial?.let { runCatching { users.getUserForSerialNumber(it) }.getOrNull() } ?: Process.myUserHandle()
        val icon = iconCache.get(id) ?: runCatching {
            val apps = service.getSystemService(android.content.pm.LauncherApps::class.java)
            apps.getActivityList(component.packageName, user).firstOrNull { it.componentName == component }
                ?.getBadgedIcon(0)?.toBitmap(144, 144)
        }.getOrNull()?.also { iconCache.put(id, it) } ?: return null
        return DockApp(component, user, icon)
    }

    private fun launch(app: DockApp) {
        runCatching {
            val launcherApps = service.getSystemService(android.content.pm.LauncherApps::class.java)
            if (app.component.className.startsWith(SHORTCUT_CLASS_PREFIX))
                launcherApps.startShortcut(app.component.packageName, app.component.className.removePrefix(SHORTCUT_CLASS_PREFIX), null, null, app.user)
            else launcherApps.startMainActivity(app.component, app.user, null, null)
        }
    }

    // ---- Island ------------------------------------------------------------------------------

    private var islandParams: WindowManager.LayoutParams? = null
    private var geometry: IslandGeometry? = null

    private fun addIsland() {
        if (island != null) return
        val metrics = wm.currentWindowMetrics
        val cutout: Rect? = metrics.windowInsets.displayCutout?.boundingRects?.filter { !it.isEmpty }?.minByOrNull { it.top }
            ?: CameraArea.hiddenCamera(wm.defaultDisplay)
        val wide = isRegularSize(metrics.bounds.width() / density, metrics.bounds.height() / density, service.resources.configuration.classScale)
        val g = (IslandPosition.load(service, wide, metrics.bounds.width() > metrics.bounds.height())?.let { islandGeometryAt(it.xFraction * metrics.bounds.width(), it.topDp, metrics.bounds.width(), density) }
            ?: islandGeometry(cutout, metrics.bounds.width(), density)).also { geometry = it }
        val view = ComposeView(service).apply {
            setViewTreeLifecycleOwner(owner); setViewTreeSavedStateRegistryOwner(owner)
            setContent {
                val content = islandContent.collectAsState().value
                val w by animateDpAsState(content?.let { g.widthFor(it).dp } ?: 0.dp,
                    spring(dampingRatio = .72f, stiffness = Spring.StiffnessMediumLow), label = "overlay-island")
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (content != null && w > 1.dp) Box(Modifier.size(w, g.pillH.dp).clip(RoundedCornerShape((g.pillH / 2).dp)).background(Color.Black)
                        .clickable {
                            (content as? IslandContent.Live)?.let { IslandListenerService.open(service, it.activity) }
                            ((content as? IslandContent.Event)?.event as? IslandEvent.Message)?.let { IslandListenerService.openKey(service, it.key, it.packageName) }
                        }
                        .semantics { contentDescription = describe(content) }) {
                        IslandPillContent(content, g.camW.dp, g.pillH.dp)
                    }
                }
            }
        }
        val params = WindowManager.LayoutParams(1, (g.pillH * density).toInt(),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.START
            y = (g.top * density).toInt()
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        runCatching { wm.addView(view, params); island = view; islandParams = params }
    }

    /** Keep the window exactly as wide as the pill will be, so nothing around it blocks touches. */
    private fun resizeIsland(content: IslandContent) {
        val view = island ?: return
        val params = islandParams ?: return
        val g = geometry ?: return
        val widthPx = (g.widthFor(content) * density).toInt()
        if (params.width == widthPx) return
        params.width = widthPx
        params.x = (g.centerXPx - widthPx / 2f).toInt()
        runCatching { wm.updateViewLayout(view, params) }
    }

    private fun removeIsland() { island?.let { runCatching { wm.removeView(it) } }; island = null; islandParams = null }
}
