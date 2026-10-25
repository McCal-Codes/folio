package com.mccal.folio

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay

/**
 * The provider info of a bound widget, looked up again whenever it was missing. Android answers null for a bound widget
 * while its app is disabled or being updated, and the answer is right again as soon as the app is back; keeping the
 * first null for as long as Home stays composed was why a Maps widget showed "Widget unavailable" after its app had been
 * disabled and re-enabled. A found info is kept as is, so a working widget is never rebuilt.
 */
internal fun <T : Any> refreshedWidgetInfo(cached: T?, lookup: () -> T?): T? =
    cached ?: runCatching(lookup).getOrNull()

/**
 * The info of widget [id] that heals itself: looked up again when Home comes back to the front (after enabling the app in
 * Settings, say) and, while it is missing, when a package changes, with a second look a moment later because the widget
 * service updates after the broadcast. Built-in cards (negative ids) have none.
 */
@Composable
internal fun rememberWidgetInfo(manager: AppWidgetManager, id: Int): AppWidgetProviderInfo? {
    val context = LocalContext.current
    fun look() = if (id < 0) null else manager.getAppWidgetInfo(id)
    var info by remember(id) { mutableStateOf(refreshedWidgetInfo(null, ::look)) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, id) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) info = refreshedWidgetInfo(info, ::look)
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val missing = id >= 0 && info == null
    var ping by remember(id) { mutableIntStateOf(0) }
    DisposableEffect(missing, id) {
        if (!missing) return@DisposableEffect onDispose {}
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) { ping++ }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_CHANGED); addAction(Intent.ACTION_PACKAGE_ADDED); addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        val registered = runCatching { context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED) }.isSuccess
        onDispose { if (registered) runCatching { context.unregisterReceiver(receiver) } }
    }
    LaunchedEffect(ping) {
        if (ping == 0) return@LaunchedEffect
        info = refreshedWidgetInfo(info, ::look)
        delay(1_500)
        info = refreshedWidgetInfo(info, ::look)
    }
    return info
}
