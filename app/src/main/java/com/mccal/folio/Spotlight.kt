@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.mccal.folio

import androidx.compose.ui.res.stringResource
import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import androidx.compose.runtime.withFrameNanos
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.layout.layout
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

/** Spotlight sections that can be turned off in settings. */
internal enum class SpotlightSection(@androidx.annotation.StringRes val title: Int) { SUGGESTIONS(R.string.suggestions), CONTACTS(R.string.contacts), SETTINGS(R.string.settings), CALCULATOR(R.string.calculator), WEB(R.string.search_the_web_ask_ai) }

/** Remembers the last apps launched from Folio, for Spotlight suggestions. Stays on the device. */
internal object RecentApps {
    private const val KEY = "recent_apps"
    private const val LAUNCHES = "launch_log"
    private const val MAX_LAUNCHES = 300
    private const val HALF_LIFE_MS = 7 * 24 * 60 * 60 * 1000.0

    fun record(context: Context, id: String) {
        val prefs = context.getSharedPreferences("folio", 0)
        val list = (listOf(id) + (prefs.getString(KEY, "") ?: "").split('\n').filter { it.isNotBlank() && it != id }).take(12)
        val log = (listOf("$id|${System.currentTimeMillis()}") + (prefs.getString(LAUNCHES, "") ?: "").split('\n').filter { it.isNotBlank() }).take(MAX_LAUNCHES)
        prefs.edit().putString(KEY, list.joinToString("\n")).putString(LAUNCHES, log.joinToString("\n")).apply()
    }

    fun load(context: Context): List<String> =
        (context.getSharedPreferences("folio", 0).getString(KEY, "") ?: "").split('\n').filter { it.isNotBlank() }

    /** Frecency per app: each launch counts 1, halving every 7 days (idea credited to QuickLaunch). */
    fun frecency(context: Context, now: Long = System.currentTimeMillis()): Map<String, Double> =
        frecencyOf((context.getSharedPreferences("folio", 0).getString(LAUNCHES, "") ?: "").split('\n'), now)

    internal fun frecencyOf(lines: List<String>, now: Long): Map<String, Double> {
        val scores = HashMap<String, Double>()
        lines.forEach { line ->
            val cut = line.lastIndexOf('|'); if (cut <= 0) return@forEach
            val at = line.substring(cut + 1).toLongOrNull() ?: return@forEach
            val age = (now - at).coerceAtLeast(0)
            scores.merge(line.substring(0, cut), Math.pow(0.5, age / HALF_LIFE_MS), Double::plus)
        }
        return scores
    }
}

/** iOS-style Spotlight over Home: suggestions, apps, contacts, settings, calculator, web and AI. */
@Composable
internal fun SpotlightOverlay(visible: Boolean, progress: () -> Float, state: LauncherState, onClose: () -> Unit,
    onLaunch: (AppEntry) -> Unit) {
    BackHandler(visible) { onClose() }
    // Composed while visible or still animating out; every layer follows the one shared spring,
    // so scrim, content and the blurred Home behind always move together.
    // Composed while visible or animating out. (A pre-warmed hidden copy measured no faster and stole
    // keyboard focus from the visible one, so it was removed.)
    var composed by remember { mutableStateOf(false) }
    if (visible) composed = true
    LaunchedEffect(visible) {
        if (!visible) { snapshotFlow { progress() }.first { it <= .001f }; composed = false }
    }
    if (!composed) return
    val lift = with(androidx.compose.ui.platform.LocalDensity.current) { 24.dp.toPx() }
    Box(Modifier.fillMaxSize().graphicsLayer { alpha = progress() }.background(FolioGlass.scrim)
        .then(if (visible) Modifier.clickable(remember { MutableInteractionSource() }, null, onClick = onClose) else Modifier))
    Box(Modifier.fillMaxSize().graphicsLayer {
        val p = progress()
        alpha = p; translationY = -lift * (1f - p); scaleX = .96f + .04f * p; scaleY = scaleX
        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(.5f, 0f)
    }) { SpotlightContent(state, active = visible, onClose, onLaunch) }
}

@Composable
private fun SpotlightContent(state: LauncherState, active: Boolean, onClose: () -> Unit, onLaunch: (AppEntry) -> Unit) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val windowInfo = androidx.compose.ui.platform.LocalWindowInfo.current
    val view = androidx.compose.ui.platform.LocalView.current
    var fieldFocused by remember { mutableStateOf(false) }
    val imeVisible = WindowInsets.isImeVisible
    /** Focus the field and raise the keyboard, falling back to the window's IME controller. */
    suspend fun raiseKeyboard() {
        // Android ignores keyboard requests from a window without focus (e.g. right after the side-key
        // picker, a panel or a menu closes), so wait for it first.
        snapshotFlow { windowInfo.isWindowFocused }.first { it }
        withFrameNanos { }
        // Up to about 1.5 s: a busy first frame (just after Home starts) can leave the field unattached for a while.
        repeat(20) { attempt ->
            kotlinx.coroutines.delay(if (attempt == 0) 90 else 70)
            runCatching { focus.requestFocus() }
            if (fieldFocused) {
                keyboard?.show()
                kotlinx.coroutines.delay(260)
                if (!WindowInsetsHolderIme.visible(view)) runCatching {
                    context.asActivity()?.window?.let { w ->
                        androidx.core.view.WindowCompat.getInsetsController(w, view).show(androidx.core.view.WindowInsetsCompat.Type.ime())
                    }
                }
                return
            }
        }
    }
    LaunchedEffect(active) {
        // Keyboard only after Spotlight's first frames are on screen and the field is attached
        // (idea credited to QuickLaunch).
        if (active) raiseKeyboard() else { query = ""; focusManager.clearFocus(force = true) }
    }
    // Coming back to Spotlight (permission prompt, shade, app switch) brings the keyboard back.
    LaunchedEffect(active, windowInfo.isWindowFocused) {
        if (active && windowInfo.isWindowFocused && !imeVisible) {
            kotlinx.coroutines.delay(150)
            // If focus never landed (the window lost focus while Spotlight opened), try again from the start.
            if (fieldFocused) keyboard?.show() else raiseKeyboard()
        }
    }
    // Hiding the keyboard (Back, or the keyboard's own hide button) dismisses Spotlight too, like iOS: the keyboard is
    // Spotlight's input, so once it's gone the search is over. Only while Folio has window focus, so a permission
    // prompt or the notification shade doesn't close it.
    // isImeVisible alone can lag on some keyboards; the keyboard's target height is updated as soon as it starts moving.
    val imeTarget = WindowInsets.imeAnimationTarget.getBottom(androidx.compose.ui.platform.LocalDensity.current) > 0
    val imeState = androidx.compose.runtime.rememberUpdatedState(imeVisible || imeTarget)
    val configState = androidx.compose.runtime.rememberUpdatedState(LocalConfiguration.current.let { Triple(it.orientation, it.screenWidthDp, it.screenHeightDp) })
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        var wasUp = false
        snapshotFlow { imeState.value }.collectLatest { up ->
            if (up) { wasUp = true; return@collectLatest }
            if (!wasUp) return@collectLatest
            // The keyboard also drops for a moment when folding, rotating or switching to voice typing; only a
            // keyboard that stays down (and a window that kept focus and size) means the user dismissed it.
            val config = configState.value
            kotlinx.coroutines.delay(450)
            if (!imeState.value && windowInfo.isWindowFocused && configState.value == config) {
                wasUp = false; focusManager.clearFocus(force = true); onClose()
            }
        }
    }
    var frecency by remember { mutableStateOf(emptyMap<String, Double>()) }
    LaunchedEffect(active) { if (active) frecency = withContext(Dispatchers.IO) { RecentApps.frecency(context) } }
    val wide = LocalConfiguration.current.fitsRegularHomeLayout()

    val apps = remember(state.apps, state.hiddenApps) { state.apps.filter { it.id !in state.hiddenApps } }
    // Suggestions for this time of day first, then recent and dock apps to fill the row.
    val timely by produceState(emptyList<AppEntry>(), apps, active) { if (active) value = withContext(Dispatchers.IO) { Suggestions.forNow(context, apps) } }
    val recent = remember(apps, frecency, timely) {
        val byId = apps.associateBy { it.id }
        (timely + frecency.entries.sortedByDescending { it.value }.mapNotNull { byId[it.key] } +
            RecentApps.load(context).mapNotNull(byId::get) + state.dock.mapNotNull { it?.let(byId::get) }).distinctBy { it.id }.take(8)
    }
    val q = query.trim()
    fun shows(section: SpotlightSection) = section.name !in state.spotlightHidden
    val engine = runCatching { WebSearchTarget.valueOf(state.searchEngine) }.getOrDefault(WebSearchTarget.GOOGLE)
    val appHits = remember(q, apps, frecency) { if (q.isEmpty()) emptyList() else rankApps(apps, q, frecency) }
    val shortcuts = remember(context) { settingShortcuts(context) }
    val settingHits = remember(q, shortcuts) { if (q.length < 2) emptyList() else shortcuts.filter { it.matches(q) }.take(4) }
    val math = remember(q) { evaluateMath(q) }
    var contactsGranted by remember { mutableStateOf(context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) }
    val contactsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { contactsGranted = it }
    val contacts by produceState(emptyList<ContactHit>(), q, contactsGranted) {
        if (contactsGranted && q.length >= 2) kotlinx.coroutines.delay(150) // debounce typing
        value = if (contactsGranted && q.length >= 2) withContext(Dispatchers.IO) { queryContacts(context, q) } else emptyList()
    }
    val launch = { app: AppEntry -> RecentApps.record(context, app.id); onClose(); onLaunch(app) }
    val start = { intent: Intent -> onClose(); runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }; Unit }

    // imeAnimationTarget changes once per keyboard show/hide (not every animation frame), so results
    // re-layout a single time instead of on each frame of the keyboard sliding in.
    // Half folded, Spotlight moves off the hinge like iPhone Duo's system panels.
    FoldAvoidingBox(contentAlignment = Alignment.TopCenter) {
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.folioSafeTop).windowInsetsPadding(WindowInsets.imeAnimationTarget).padding(horizontal = FolioSpace.LARGE.dp).padding(top = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        // A readable column on big screens (iPad Spotlight floats at about this width) rather than stretching edge to edge.
        Column(Modifier.widthIn(max = 680.dp).fillMaxWidth().testTag("spotlight"),
            verticalArrangement = Arrangement.spacedBy(FolioSpace.COMFY.dp)) {
            // Search field
            val fieldScope = rememberCoroutineScope()
            // iOS: the search capsule with Cancel beside it.
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).background(SpotGlass)
                .border(FolioGlass.edge, RoundedCornerShape(18.dp))
                // The whole capsule is the tap target, not just the text line.
                .clickable(remember { MutableInteractionSource() }, null) { fieldScope.launch { raiseKeyboard() } }
                // Fixed height: the capsule doesn't grow or jump when the clear button appears.
                .heightIn(min = 52.dp).padding(start = FolioSpace.COMFY.dp, end = FolioSpace.TINY.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Search, null, tint = Color.White.copy(alpha = .75f), modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text(stringResource(R.string.search), color = Color.White.copy(alpha = .55f), fontSize = 18.sp)
                    BasicTextField(query, { query = it }, Modifier.fillMaxWidth().focusRequester(focus)
                        .onFocusChanged { fieldFocused = it.isFocused }.testTag("spotlight-field"),
                        singleLine = true, textStyle = TextStyle(color = Color.White, fontSize = 18.sp), cursorBrush = SolidColor(Color.White),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            when {
                                appHits.isNotEmpty() -> launch(appHits.first())
                                q.isNotEmpty() -> { onClose(); openWebSearch(context, engine, q) }
                            }
                        }))
                }
                if (query.isNotEmpty()) Box(Modifier.size(44.dp).clip(CircleShape).clickable { query = "" }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Cancel, "Clear", tint = Color.White.copy(alpha = .6f), modifier = Modifier.size(20.dp))
                }
            }
            Text(stringResource(R.string.cancel), color = Color.White, fontSize = FolioType.BODY.sp,
                modifier = Modifier.padding(start = FolioSpace.HAIR.dp).heightIn(min = 48.dp).clip(RoundedCornerShape(FolioRadius.CONTROL.dp))
                    .clickable { focusManager.clearFocus(force = true); keyboard?.hide(); onClose() }
                    .padding(horizontal = FolioSpace.MEDIUM.dp, vertical = FolioSpace.COMFY.dp).testTag("spotlight-cancel"))
            }

            val resultsState = androidx.compose.foundation.lazy.rememberLazyListState()
            LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false).edgeFade(resultsState), state = resultsState, verticalArrangement = Arrangement.spacedBy(FolioSpace.COMFY.dp),
                contentPadding = PaddingValues(bottom = 24.dp)) {
                if (q.isEmpty()) {
                    if (shows(SpotlightSection.SUGGESTIONS) && recent.isNotEmpty()) item("suggestions") {
                        Section(stringResource(R.string.suggestions)) { AppGrid(recent, launch, fullRows = true) }
                    }
                } else {
                    math?.takeIf { shows(SpotlightSection.CALCULATOR) }?.let { result -> item("math") {
                        Section(stringResource(R.string.calculator)) {
                            ResultRow(Icons.Rounded.Calculate, "= $result", q) {
                                val clip = context.getSystemService(android.content.ClipboardManager::class.java)
                                clip.setPrimaryClip(android.content.ClipData.newPlainText("Result", result))
                            }
                        }
                    } }
                    appHits.firstOrNull()?.let { top -> item("top") {
                        Section(stringResource(R.string.top_hit)) { TopHit(top) { launch(top) } }
                    } }
                    if (appHits.size > 1) item("apps") { Section(stringResource(R.string.apps)) { AppGrid(appHits.drop(1).take(8), launch) } }
                    if (shows(SpotlightSection.CONTACTS) && contacts.isNotEmpty()) item("contacts") {
                        Section(stringResource(R.string.contacts)) { contacts.forEach { c ->
                            ResultRow(Icons.Rounded.Person, c.name, c.address, trailing = c.address?.let { address -> {
                                // iPhone-style quick actions: message (default texting app or OpenBubbles) and call.
                                if (!address.contains('@')) SpotlightRoundAction(Icons.Rounded.Call, "Call ${c.name}") {
                                    start(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", address, null)))
                                }
                                SpotlightRoundAction(Icons.Rounded.ChatBubble, "Message ${c.name}") {
                                    Messaging.conversationIntent(context, state.messagesApp, address)?.let(start)
                                }
                            } }) { start(Intent(Intent.ACTION_VIEW, c.uri)) }
                        } }
                    } else if (shows(SpotlightSection.CONTACTS) && !contactsGranted && q.length >= 2) item("contacts-permission") {
                        Section(stringResource(R.string.contacts)) { ResultRow(Icons.Rounded.PersonSearch, stringResource(R.string.search_your_contacts), stringResource(R.string.allow_contacts_access)) {
                            contactsPermission.launch(Manifest.permission.READ_CONTACTS)
                        } }
                    }
                    if (shows(SpotlightSection.SETTINGS) && settingHits.isNotEmpty()) item("settings") {
                        Section(stringResource(R.string.settings)) { settingHits.forEach { s -> ResultRow(Icons.Rounded.Settings, s.title, stringResource(R.string.settings)) { start(Intent(s.action)) } } }
                    }
                    if (shows(SpotlightSection.WEB)) item("web") {
                        Section(stringResource(R.string.search_the_web_ask_ai)) {
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(FolioSpace.SMALL.dp)) {
                                WebSearchTarget.entries.forEach { target ->
                                    Row(Modifier.clip(RoundedCornerShape(50)).background(SpotGlass)
                                        .clickable { onClose(); openWebSearch(context, target, q) }
                                        .padding(horizontal = FolioSpace.COMFY.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(if (target.label.startsWith("Ask")) Icons.Rounded.AutoAwesome else Icons.Rounded.Public, null,
                                            tint = Color.White, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(target.label, color = Color.White, fontSize = 14.sp)
                                    }
                                }
                            }
                        }
                    }
                    if (math == null && appHits.isEmpty() && contacts.isEmpty() && settingHits.isEmpty()) item("none") {
                        Text(stringResource(R.string.no_results_on_this_phone), color = Color.White.copy(alpha = .6f), fontSize = FolioType.FOOTNOTE.sp,
                            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(FolioSpace.SMALL.dp)) {
        Text(title, color = FolioGlass.secondary, fontSize = FolioType.FOOTNOTE.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = FolioSpace.TINY.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(FolioRadius.GROUPED_CARD.dp)).background(SpotGlass)
            .border(FolioGlass.edge, RoundedCornerShape(FolioRadius.GROUPED_CARD.dp)).padding(FolioSpace.COMPACT.dp),
            verticalArrangement = Arrangement.spacedBy(FolioSpace.HAIR.dp), content = content)
    }
}

@Composable
private fun AppGrid(apps: List<AppEntry>, onLaunch: (AppEntry) -> Unit, fullRows: Boolean = false) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // As many ~84dp app cells as fit (four on a phone, up to eight on a wide column).
        val columns = evenColumnsOnHinge((maxWidth / 84.dp).toInt().coerceIn(4, 8), 4)
        // Suggestions show whole rows only, like iOS, so a wide column doesn't end in a lonely pair.
        val shown = if (fullRows && apps.size > columns) apps.take(apps.size / columns * columns) else apps
        Column(verticalArrangement = Arrangement.spacedBy(FolioSpace.SMALL.dp)) {
            shown.chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEach { app ->
                        val view = androidx.compose.ui.platform.LocalView.current
                        val ctx = LocalContext.current
                        Column(Modifier.weight(1f).clip(RoundedCornerShape(FolioRadius.CARD.dp))
                            .combinedClickable(onClick = { onLaunch(app) }, onLongClick = {
                                // Long-press to drag into split screen beside the app that's open.
                                startSplitDrag(view, ctx, app.component, app.user, app.label, app.icon)
                            }).padding(vertical = FolioSpace.SNUG.dp),
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            AppIcon(app, app.label, Modifier.size(52.dp).clip(RoundedCornerShape(13.dp)))
                            Text(app.label, color = Color.White, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = FolioSpace.TINY.dp, start = FolioSpace.HAIR.dp, end = FolioSpace.HAIR.dp))
                        }
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun TopHit(app: AppEntry, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(FolioRadius.CARD.dp)).clickable(onClick = onClick).padding(FolioSpace.SMALL.dp),
        verticalAlignment = Alignment.CenterVertically) {
        AppIcon(app, null, Modifier.size(56.dp).clip(RoundedCornerShape(FolioRadius.CARD.dp)))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(app.label, color = Color.White, fontSize = FolioType.BODY.sp, fontWeight = FontWeight.SemiBold)
            Text(if (app.profileLabel == stringResource(R.string.personal)) stringResource(R.string.application) else "${app.profileLabel} app", color = Color.White.copy(alpha = .6f), fontSize = FolioType.FOOTNOTE.sp)
        }
        Text(stringResource(R.string.open), color = Color.White, fontSize = FolioType.FOOTNOTE.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = .18f)).padding(horizontal = FolioSpace.COMFY.dp, vertical = FolioSpace.SNUG.dp))
    }
}

@Composable
private fun ResultRow(icon: ImageVector, title: String, subtitle: String?, trailing: (@Composable RowScope.() -> Unit)? = null, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = FolioSpace.SMALL.dp, vertical = FolioSpace.COMPACT.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)).background(Color.White.copy(alpha = .14f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = FolioType.SUBHEAD.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            subtitle?.let { Text(it, color = Color.White.copy(alpha = .6f), fontSize = FolioType.GROUP_LABEL.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        trailing?.let { Row(horizontalArrangement = Arrangement.spacedBy(FolioSpace.SMALL.dp), content = it) }
    }
}

@Composable
private fun SpotlightRoundAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(Modifier.size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = .16f)).clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center) {
        Icon(icon, label, tint = Color.White, modifier = Modifier.size(18.dp))
    }
}

// ---------------------------------------------------------------------------------------------
// Search logic

/**
 * Prefix beats word-start beats substring beats initials ("gm" → Google Maps). An app you renamed is still
 * found by the name Android gives it, after everything matching the name you chose.
 */
internal fun rankApps(apps: List<AppEntry>, query: String, frecency: Map<String, Double> = emptyMap()): List<AppEntry> {
    val boost: (AppEntry) -> Double = { frecency[it.id] ?: 0.0 }
    val hits = rankByLabel(apps, query, boost) { it.label }
    val matched = hits.mapTo(mutableSetOf(), AppEntry::id)
    val renamed = apps.filter { it.label != it.systemLabel && it.id !in matched }
    return if (renamed.isEmpty()) hits else hits + rankByLabel(renamed, query, boost) { it.systemLabel }
}

/** Label ranking used by Spotlight; shorter labels win ties. Pure, so it's unit-tested. */
internal fun <T> rankByLabel(items: List<T>, query: String, boost: (T) -> Double = { 0.0 }, label: (T) -> String): List<T> {
    val q = query.lowercase()
    return items.mapNotNull { item ->
        val text = label(item).lowercase()
        // A Chinese name also answers to its pinyin, joined ("weixin") and by initials ("wx"), like iOS.
        val pinyin = Pinyin.syllables(text)
        val score = listOfNotNull(labelScore(text, text.split(' ', '-', '.', '_').filter { it.isNotEmpty() }, q),
            pinyin.takeIf { it.isNotEmpty() }?.let { labelScore(it.joinToString(""), it, q) }).minOrNull()
        score?.let { Triple(it, item, label(item).length) }
    }.sortedWith(compareBy<Triple<Int, T, Int>>({ it.first }, { -boost(it.second) }, { it.third })).map { it.second }
}

private fun labelScore(text: String, words: List<String>, q: String): Int? = when {
    text == q -> 0
    text.startsWith(q) -> 1
    words.any { it.startsWith(q) } -> 2
    text.contains(q) -> 3
    words.joinToString("") { it.take(1) }.startsWith(q) -> 4
    isSubsequence(q, text) -> 5 // fuzzy: letters in order ("spfy" → Spotify)
    else -> null
}

internal fun isSubsequence(needle: String, hay: String): Boolean {
    if (needle.length < 2) return false
    var i = 0
    for (c in hay) if (i < needle.length && c == needle[i]) i++
    return i == needle.length
}

/** Evaluates simple arithmetic like "12*(3+4)/2". Returns null when the query isn't math. */
internal fun evaluateMath(input: String): String? {
    val text = input.replace('×', '*').replace('÷', '/').replace(" ", "")
    if (text.length < 3 || !text.any { it in "+-*/^%" } || !text.all { it.isDigit() || it in "+-*/^%.()" }) return null
    // Phone numbers and dates ("555-1234", "2026-09-12") aren't sums.
    if (Regex("""^\d+(-\d+)+$""").matches(text)) return null
    return runCatching {
        val parser = MathParser(text)
        val value = parser.expression()
        if (!parser.done || value.isNaN() || value.isInfinite()) null
        else if (value == Math.rint(value) && kotlin.math.abs(value) < 1e15) value.toLong().toString()
        else String.format(java.util.Locale.ROOT, "%.6f", value).trimEnd('0').trimEnd('.')
    }.getOrNull()
}

private class MathParser(private val text: String) {
    private var i = 0
    val done get() = i == text.length
    private fun peek() = text.getOrNull(i)
    fun expression(): Double {
        var v = term()
        while (peek() == '+' || peek() == '-') { val op = text[i++]; val r = term(); v = if (op == '+') v + r else v - r }
        return v
    }
    private fun term(): Double {
        var v = power()
        while (peek() == '*' || peek() == '/' || peek() == '%') {
            val op = text[i++]; val r = power()
            v = when (op) { '*' -> v * r; '/' -> v / r; else -> v % r }
        }
        return v
    }
    private fun power(): Double { val b = unary(); return if (peek() == '^') { i++; Math.pow(b, power()) } else b }
    private fun unary(): Double {
        if (peek() == '-') { i++; return -unary() }
        if (peek() == '(') { i++; val v = expression(); require(peek() == ')'); i++; return v }
        val start = i
        while (peek()?.let { it.isDigit() || it == '.' } == true) i++
        return text.substring(start, i).toDouble()
    }
}

private data class ContactHit(val name: String, val address: String?, val uri: Uri)

private fun queryContacts(context: Context, query: String): List<ContactHit> = runCatching {
    val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_FILTER_URI, Uri.encode(query))
    context.contentResolver.query(uri, arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.LOOKUP_KEY,
        ContactsContract.Contacts.DISPLAY_NAME_PRIMARY), null, null, null)?.use { c ->
        buildList {
            while (c.moveToNext() && size < 5) {
                val id = c.getLong(0)
                add(ContactHit(c.getString(2) ?: continue, contactAddress(context, id),
                    ContactsContract.Contacts.getLookupUri(id, c.getString(1)) ?: ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, id)))
            }
        }
    } ?: emptyList()
}.getOrDefault(emptyList())

/** The contact's primary phone number, else their first email (for Message / Call buttons). */
private fun contactAddress(context: Context, contactId: Long): String? {
    fun first(uri: Uri, column: String, contactColumn: String, primary: String) = runCatching {
        context.contentResolver.query(uri, arrayOf(column), "$contactColumn = ?", arrayOf(contactId.toString()), "$primary DESC")
            ?.use { if (it.moveToFirst()) it.getString(0) else null }
    }.getOrNull()
    return first(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, ContactsContract.CommonDataKinds.Phone.NUMBER,
        ContactsContract.CommonDataKinds.Phone.CONTACT_ID, ContactsContract.CommonDataKinds.Phone.IS_PRIMARY)
        ?: first(ContactsContract.CommonDataKinds.Email.CONTENT_URI, ContactsContract.CommonDataKinds.Email.ADDRESS,
            ContactsContract.CommonDataKinds.Email.CONTACT_ID, ContactsContract.CommonDataKinds.Email.IS_PRIMARY)
}

private data class SettingShortcut(val title: String, val action: String, val keywords: List<String>) {
    fun matches(q: String) = title.contains(q, true) || keywords.any { it.startsWith(q, true) }
}

private fun settingShortcuts(context: android.content.Context) = listOf(
    SettingShortcut(context.getString(R.string.wi_fi), Settings.ACTION_WIFI_SETTINGS, listOf("wifi", "wireless", "internet", "network")),
    SettingShortcut(context.getString(R.string.bluetooth), Settings.ACTION_BLUETOOTH_SETTINGS, listOf("bluetooth", "pair", "headphones")),
    SettingShortcut(context.getString(R.string.mobile_network), Settings.ACTION_NETWORK_OPERATOR_SETTINGS, listOf("cellular", "mobile", "data", "sim")),
    SettingShortcut(context.getString(R.string.airplane_mode), Settings.ACTION_AIRPLANE_MODE_SETTINGS, listOf("airplane", "flight")),
    SettingShortcut(context.getString(R.string.display_brightness), Settings.ACTION_DISPLAY_SETTINGS, listOf("display", "brightness", "screen", "dark")),
    SettingShortcut(context.getString(R.string.sounds_vibration), Settings.ACTION_SOUND_SETTINGS, listOf("sound", "volume", "ringtone", "vibration")),
    SettingShortcut("Notifications", if (android.os.Build.VERSION.SDK_INT >= 33) Settings.ACTION_ALL_APPS_NOTIFICATION_SETTINGS else Settings.ACTION_SETTINGS, listOf("notification", "alerts")),
    SettingShortcut(context.getString(R.string.battery), Intent.ACTION_POWER_USAGE_SUMMARY, listOf("battery", "power", "charging")),
    SettingShortcut(context.getString(R.string.apps), Settings.ACTION_APPLICATION_SETTINGS, listOf("apps", "applications", "uninstall")),
    SettingShortcut(context.getString(R.string.default_apps), Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS, listOf("default", "home app", "browser", "assistant")),
    SettingShortcut(context.getString(R.string.storage), Settings.ACTION_INTERNAL_STORAGE_SETTINGS, listOf("storage", "space")),
    SettingShortcut(context.getString(R.string.location), Settings.ACTION_LOCATION_SOURCE_SETTINGS, listOf("location", "gps")),
    SettingShortcut(context.getString(R.string.security_privacy), Settings.ACTION_SECURITY_SETTINGS, listOf("security", "privacy", "lock", "password", "fingerprint")),
    SettingShortcut(context.getString(R.string.accessibility), Settings.ACTION_ACCESSIBILITY_SETTINGS, listOf("accessibility")),
    SettingShortcut(context.getString(R.string.date_time), Settings.ACTION_DATE_SETTINGS, listOf("date", "time", "clock")),
    SettingShortcut(context.getString(R.string.language_keyboard), Settings.ACTION_LOCALE_SETTINGS, listOf("language", "keyboard")),
    SettingShortcut(context.getString(R.string.do_not_disturb), Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS, listOf("dnd", "do not disturb", "focus")),
    SettingShortcut(context.getString(R.string.nfc_payments), Settings.ACTION_NFC_SETTINGS, listOf("nfc", "pay", "wallet", "contactless")),
    SettingShortcut(context.getString(R.string.developer_options), Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS, listOf("developer", "usb debugging")),
    SettingShortcut(context.getString(R.string.about_phone), Settings.ACTION_DEVICE_INFO_SETTINGS, listOf("about", "phone", "software", "version")),
    SettingShortcut(context.getString(R.string.settings), Settings.ACTION_SETTINGS, listOf("settings", "preferences")),
)

private val SpotGlass = FolioGlass.card

/** Whether the IME is actually on screen for [view]'s window. */
private object WindowInsetsHolderIme {
    fun visible(view: android.view.View): Boolean =
        androidx.core.view.ViewCompat.getRootWindowInsets(view)?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) == true
}
