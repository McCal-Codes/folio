package com.mccal.folio

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap

/**
 * "Add to Home Screen" when an app asks to pin one of its widgets (Android's pin request goes to the Home app).
 * An iOS-style card over the app shows the widget's preview; Add places it in the first free spot on Home.
 *
 * **Trusting the request.** This activity is exported, because Android delivers pin requests through it, so any app
 * can also start it with an intent it made itself. A request read back by `getPinItemRequest` carries the binder that
 * answers `isValid()` and `accept()`, and a forged one answers yes to both while naming whatever widget or shortcut it
 * likes. So nothing is placed on the request's word. A widget goes on Home only once Android reports the id it
 * accepted as bound to that same provider, which only a real request can do; a shortcut only once `LauncherApps`
 * lists it as pinned. A provider that isn't installed is turned away before the card is even shown.
 */
class PinWidgetActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val launcherApps = getSystemService(LauncherApps::class.java)
        val request = runCatching { launcherApps.getPinItemRequest(intent) }.getOrNull()?.takeIf { it.isValid }
        if (request == null) { finish(); return }
        if (request.requestType == LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT) { showShortcut(request); return }
        val provider = request.getAppWidgetProviderInfo(this)
        if (provider == null || !PinTrust.installed(provider.provider,
                runCatching { AppWidgetManager.getInstance(this).getInstalledProvidersForProfile(provider.profile).map { it.provider } }.getOrDefault(emptyList()))
        ) {
            Diagnostics.pinRefused(provider?.provider?.packageName, PinTrust.NOT_INSTALLED)
            finish(); return
        }
        val label = provider.loadLabel(packageManager)
        val app = runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(provider.provider.packageName, 0)).toString() }.getOrDefault(label)
        val preview = runCatching { provider.loadPreviewImage(this, resources.displayMetrics.densityDpi)?.toBitmap() }.getOrNull()
            ?: runCatching { provider.loadIcon(this, resources.displayMetrics.densityDpi)?.toBitmap() }.getOrNull()
        setContent {
            var error by remember { mutableStateOf<String?>(null) }
            PinCard(onCancel = ::finish, error = error, addLabel = stringResource(R.string.add_to_home_screen), tag = "pin-widget", onAdd = { error = add(request, provider) ?: run { finish(); null } }) {
                preview?.let { Image(it.asImageBitmap(), null, Modifier.heightIn(max = 180.dp).clip(RoundedCornerShape(FolioRadius.GROUPED_CARD.dp))) }
                Text(label, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                Text(if (label != app) app else stringResource(R.string.widget), color = Color.White.copy(alpha = .6f), fontSize = FolioType.SUBHEAD.sp)
            }
        }
    }

    /** A website or app shortcut: the same card with its icon; Add pins it and puts it in the first free spot on Home. */
    private fun showShortcut(request: LauncherApps.PinItemRequest) {
        val info = request.shortcutInfo ?: run { finish(); return }
        val label = (info.shortLabel ?: info.longLabel ?: this@PinWidgetActivity.getString(R.string.shortcut)).toString()
        val app = runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(info.`package`, 0)).toString() }.getOrDefault("")
        val icon = runCatching { getSystemService(LauncherApps::class.java).getShortcutIconDrawable(info, resources.displayMetrics.densityDpi)?.toBitmap(192, 192) }.getOrNull()
        setContent {
            var error by remember { mutableStateOf<String?>(null) }
            PinCard(onCancel = ::finish, error = error, addLabel = stringResource(R.string.add_to_home_screen), tag = "pin-shortcut", onAdd = {
                val model = FolioSettingsBridge.liveModel?.get()
                when {
                    model == null -> error = this@PinWidgetActivity.getString(R.string.open_folio_once_then_try_again)
                    runCatching { request.accept() }.getOrDefault(false) && pinned(info) -> { model.placePinnedShortcut(info.`package`, info.id); finish() }
                    else -> {
                        Diagnostics.pinRefused(info.`package`, PinTrust.NOT_PINNED)
                        error = this@PinWidgetActivity.getString(R.string.the_shortcut_couldn_t_be_added)
                    }
                }
            }) {
                icon?.let { Image(it.asImageBitmap(), null, Modifier.size(72.dp).clip(RoundedCornerShape(18.dp))) }
                Text(label, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                if (app.isNotEmpty()) Text(app, color = Color.White.copy(alpha = .6f), fontSize = FolioType.SUBHEAD.sp)
            }
        }
    }

    /** Whether Android now lists [info] as pinned for Folio, which is what a real request's accept() does. */
    private fun pinned(info: ShortcutInfo): Boolean = runCatching {
        val query = LauncherApps.ShortcutQuery().setPackage(info.`package`).setShortcutIds(listOf(info.id))
            .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)
        getSystemService(LauncherApps::class.java).getShortcuts(query, info.userHandle).orEmpty().any { it.id == info.id && it.isPinned }
    }.getOrDefault(false)

    /** Binds and places the widget; returns a message when it can't. */
    private fun add(request: LauncherApps.PinItemRequest, provider: android.appwidget.AppWidgetProviderInfo): String? {
        val model = FolioSettingsBridge.liveModel?.get() ?: return this@PinWidgetActivity.getString(R.string.open_folio_once_then_try_again)
        FocusPages.lockingFocus(model.state.value)?.let { return this@PinWidgetActivity.getString(R.string.turn_off_to_add_to_home_screen, it.name) }
        val layout = model.state.value.layout
        val appRows = model.state.value.homeAppRows
        val density = resources.displayMetrics.density
        // Folio's usual Home pitch: about 90dp columns, widget-height top rows, app rows below.
        val grid = WidgetGridSizing(GRID_COLUMNS, visibleHomeRows(appRows), 90f, 88f, 97f, 10f, 18f, topRowHeightDp = 97f, appRowHeightDp = 88f)
        val span = widgetSpanConstraints(WidgetProviderSizing(
            minWidthDp = provider.minWidth / density, minHeightDp = provider.minHeight / density,
            minResizeWidthDp = provider.minResizeWidth / density, minResizeHeightDp = provider.minResizeHeight / density,
            maxResizeWidthDp = provider.maxResizeWidth / density, maxResizeHeightDp = provider.maxResizeHeight / density,
            targetCellWidth = provider.targetCellWidth, targetCellHeight = provider.targetCellHeight,
            horizontalPaddingDp = 0f, verticalPaddingDp = 0f, resizeMode = provider.resizeMode), grid)?.preferred ?: WidgetSpan(2, 2)
        val (page, index) = firstFreeWidgetSpot(layout, span.width, span.height, appRows = appRows) ?: return this@PinWidgetActivity.getString(R.string.there_s_no_room_on_home_for_this_widget)
        val host = AppWidgetHost(this, 1024)
        val id = host.allocateAppWidgetId()
        val accepted = runCatching { request.accept(Bundle().apply { putInt(AppWidgetManager.EXTRA_APPWIDGET_ID, id) }) }.getOrDefault(false)
        // Android's word, not the request's: only a real request leaves this id bound to the provider it named.
        val bound = runCatching { AppWidgetManager.getInstance(this).getAppWidgetInfo(id)?.provider }.getOrNull()
        val trusted = accepted && PinTrust.bound(provider.provider, bound)
        if (accepted && !trusted) Diagnostics.pinRefused(provider.provider.packageName, PinTrust.NOT_BOUND)
        val local = homeCellLocal(index)
        val placed = trusted && model.placeWidget(WidgetPlacement(model.nextWidgetSlot(), id, page, local % GRID_COLUMNS, local / GRID_COLUMNS, span.width, span.height))
        if (!placed) { host.deleteAppWidgetId(id); return this@PinWidgetActivity.getString(R.string.the_widget_couldn_t_be_added) }
        return null
    }
}

/** The checks a pin request has to pass, apart from the Android calls that feed them, so they can be tested. */
internal object PinTrust {
    const val NOT_INSTALLED = "the widget it names isn't installed"
    const val NOT_BOUND = "Android didn't bind the widget it accepted"
    const val NOT_PINNED = "Android doesn't list the shortcut as pinned"

    /** The widget a request names is one Android has installed for that profile. */
    fun <T> installed(provider: T, installed: List<T>) = provider in installed

    /** The id the request accepted is bound, and to the provider the request named rather than another. */
    fun <T : Any> bound(requested: T, bound: T?) = bound != null && bound == requested
}

/** The Add to Home Screen card: what's being added, then Add and Cancel. Tapping outside cancels. */
@Composable
private fun PinCard(onCancel: () -> Unit, error: String?, addLabel: String, tag: String, onAdd: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .45f)).clickable(onClick = onCancel), contentAlignment = Alignment.BottomCenter) {
        Column(Modifier.padding(FolioSpace.LARGE.dp).widthIn(max = 420.dp).fillMaxWidth().clip(RoundedCornerShape(FolioRadius.SHEET_TOP.dp)).background(FolioColors.SecondaryBackground)
            .pointerInput(Unit) { detectTapGestures() }.padding(FolioSpace.XL.dp).testTag("$tag-card"),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(FolioSpace.MEDIUM.dp)) {
            content()
            error?.let { Text(it, color = FolioColors.Red, fontSize = 14.sp, textAlign = TextAlign.Center) }
            Box(Modifier.fillMaxWidth().heightIn(min = 50.dp).clip(RoundedCornerShape(FolioRadius.CARD.dp)).background(LocalAccent.current.fill)
                .clickable(onClick = onAdd).testTag("$tag-add"), contentAlignment = Alignment.Center) {
                Text(addLabel, color = Color.White, fontSize = FolioType.BODY.sp, fontWeight = FontWeight.SemiBold)
            }
            Text(stringResource(R.string.cancel), color = LocalAccent.current.ink, fontSize = FolioType.BODY.sp, modifier = Modifier.clickable(onClick = onCancel).padding(FolioSpace.SMALL.dp))
        }
    }
}
