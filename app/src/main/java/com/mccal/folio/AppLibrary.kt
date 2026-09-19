@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.mccal.folio

import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.boundsInWindow

/** Incremented to focus the All apps search field (e.g. after a middle swipe-down on Home). */
internal val librarySearchFocusRequests = mutableIntStateOf(0)

@Composable
internal fun AppLibrary(
    state: LauncherState, query: String, onQuery: (String) -> Unit,
    onLaunch: (AppEntry) -> Unit, onPin: (String, Boolean) -> Unit, onActions: (AppEntry) -> Unit,
    modifier: Modifier = Modifier, editing: Boolean = false,
    drag: HomeDragState? = null, page: Int? = null,
    onLaunchFrom: (AppEntry, android.graphics.Rect?) -> Unit = { app, _ -> onLaunch(app) },
    onTurnOnWork: (Long) -> Unit = {},
) {
    val glass = !editing
    val palette = LocalDuoPalette.current
    val ink = if (glass) Ink else MaterialTheme.colorScheme.onSurface
    val pinned = remember(state.homeSlots, state.leadingSlots) {
        (state.homeSlots.asSequence() + state.leadingSlots.asSequence()).filterNotNull().toSet()
    }
    val hasWork = state.profiles.any { it.isWork } || state.apps.any { it.isWork }
    // With Work turned off in Settings the switch goes away and only personal apps are listed.
    val workSwitch = hasWork && state.libraryWork
    var showWork by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val searchFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focusRequest = librarySearchFocusRequests.intValue
    LaunchedEffect(focusRequest) {
        if (focusRequest > 0 && !editing) {
            kotlinx.coroutines.delay(280) // let the page settle first
            runCatching { searchFocus.requestFocus(); keyboard?.show() }
        }
    }
    val listState = rememberLazyListState()
    val selectedProfile = if (showWork) state.profiles.firstOrNull { it.isWork } else state.profiles.firstOrNull { it.isPersonal }
    LaunchedEffect(showWork, selectedProfile?.available, selectedProfile?.quiet) {
        listState.scrollToItem(0)
    }
    // Hidden apps stay out of the App Library entirely, like iOS; they're listed (after unlocking) in Settings.
    val visibleApps = remember(state.apps, query, showWork, workSwitch, hasWork, state.hiddenApps, editing) {
        val text = query.trim()
        // A renamed app answers to both names here, the same as in Spotlight.
        state.apps.filter { (if (workSwitch) it.isWork == showWork else !(hasWork && it.isWork)) &&
            (it.label.contains(text, true) || it.systemLabel.contains(text, true)) &&
            (editing || it.id !in state.hiddenApps) }
    }
    // iOS-style App Library: category tiles while browsing; the A–Z list for search, hidden and editing.
    var openCategory by remember { mutableStateOf<LibraryCategory?>(null) }
    val browsing = state.libraryCategories && !editing && query.isBlank()
    val categorized by produceState(emptyMap<LibraryCategory, List<AppEntry>>(), visibleApps, browsing) {
        if (!browsing) return@produceState
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val pm = context.packageManager
            val byId = visibleApps.associateBy { it.id }
            val suggestions = RecentApps.load(context).mapNotNull(byId::get).take(8)
            val grouped = visibleApps.groupBy { LibraryCategory.of(pm, it.component.packageName) }
                .mapValues { (_, apps) -> apps.sortedBy { it.label.lowercase() } }
            buildMap {
                if (suggestions.isNotEmpty()) put(LibraryCategory.SUGGESTIONS, suggestions)
                grouped.entries.sortedWith(compareBy({ it.key == LibraryCategory.OTHER }, { -it.value.size })).forEach { put(it.key, it.value) }
            }
        }
    }
    val groups = remember(visibleApps) {
        visibleApps.groupBy {
            it.label.firstOrNull()?.takeIf(Char::isLetter)?.uppercaseChar()?.toString() ?: "#"
        }
    }
    // Like iOS, a category opens as an expanded folder over the library instead of replacing it.
    openCategory?.takeIf { browsing }?.let { category ->
        CategoryFolder(stringResource(category.title), categorized[category].orEmpty(), onDismiss = { openCategory = null },
            onLaunch = { openCategory = null; onLaunchFrom(it, null) }, onActions = { openCategory = null; onActions(it) })
    }
    Surface(modifier, shape = RoundedCornerShape(24.dp),
        color = if (glass) Glass.copy(alpha = .48f) else MaterialTheme.colorScheme.surface,
        contentColor = ink,
        border = if (glass) BorderStroke(1.dp, Color.White.copy(alpha = .38f)) else null) {
        Column(Modifier.background(Brush.verticalGradient(if (glass)
            listOf(Color.White.copy(alpha = .09f), Color.Transparent) else listOf(Color.Transparent, Color.Transparent)))
            .padding(horizontal = 16.dp).padding(top = 18.dp)) {
            // iOS App Library has no title bar, just its search field; choosing Home apps keeps a title and count.
            if (editing) Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.choose_home_apps_title), Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.pinned_1, pinned.size), color = ink, fontSize = 12.sp)
            }
            if (workSwitch) Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IosChip(selected = !showWork, onClick = { showWork = false }, label = { Text(stringResource(R.string.personal)) })
                IosChip(selected = showWork, onClick = { showWork = true }, label = { Text(stringResource(R.string.work)) })
            }
            IosSearchField(query, onQuery, if (editing) "Search apps" else "App Library", Modifier.padding(vertical = 12.dp),
                fieldModifier = (if (editing) Modifier else Modifier.focusRequester(searchFocus)).testTag(if (editing) "pin-search" else "library-search"),
                ink = ink, onSearch = {
                    if (!editing && query.isNotBlank()) openWebSearch(context,
                        runCatching { WebSearchTarget.valueOf(state.searchEngine) }.getOrDefault(WebSearchTarget.GOOGLE), query)
                })
            var libraryWidth by remember { mutableStateOf(360.dp) }
            val density = androidx.compose.ui.platform.LocalDensity.current
            LazyColumn(Modifier.weight(1f).edgeFade(listState).onSizeChanged { libraryWidth = with(density) { it.width.toDp() } }.testTag("all-apps-list"), state = listState,
                contentPadding = PaddingValues(bottom = 12.dp)) {
                if (showWork && selectedProfile?.available == false) item("work-paused") {
                    Column(Modifier.fillMaxWidth().padding(vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (selectedProfile.quiet) "Work apps are paused" else "Work profile is unavailable")
                        if (selectedProfile.quiet) Button(onClick = { onTurnOnWork(selectedProfile.userSerial) },
                            Modifier.padding(top = 10.dp).testTag("turn-on-work")) { Text(stringResource(R.string.turn_on_work_apps)) }
                    }
                }
                if (!editing && query.isBlank()) item("downloading") { DownloadingApps(ink) }
                if (!editing && query.isNotBlank()) item("web-search") {
                    WebSearchRow(query) { openWebSearch(context, it, query) }
                }
                if (browsing && categorized.isNotEmpty()) {
                    // Tiles stay iPhone-sized: more columns on the wide inner screen instead of giant tiles.
                    val columns = libraryColumns(libraryWidth.value)
                    items(categorized.entries.toList().chunked(columns), key = { row -> "cat-" + row.first().key.name }) { row ->
                        Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            row.forEach { (cat, apps) ->
                                CategoryCard(stringResource(cat.title), apps, Modifier.weight(1f), labelColor = ink, onLaunch = { onLaunchFrom(it, null) }) { openCategory = cat }
                            }
                            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                } else if (groups.isEmpty()) item { Text(if (state.loading) "Loading apps…" else "No apps found", Modifier.padding(vertical = 20.dp)) }
                if (!(browsing && categorized.isNotEmpty())) groups.forEach { (letter, entries) ->
                    stickyHeader(key = "heading-$letter") {
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            // An opaque small chip prevents text from showing through the sticky letter.
                            Box(Modifier.size(width = 32.dp, height = 28.dp).background(
                                if (glass) (if (palette.dark) Color(0xFF314852) else Color(0xFFB7CBD3))
                                else MaterialTheme.colorScheme.surfaceContainer,
                                RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                                Text(letter, color = ink, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            }
                            if (glass) HorizontalDivider(Modifier.weight(1f).padding(start = 10.dp), color = Color.White.copy(alpha = .24f))
                        }
                    }
                    items(entries, key = { it.id }) { app ->
                        val isPinned = app.id in pinned
                        val launchBounds = remember { android.graphics.Rect() }
                        val dragModifier = if (drag != null) Modifier.dropRegion(drag, DropTarget.Library(app.id), app.id, page) else Modifier
                        val click = { if (editing) onPin(app.id, !isPinned) else onLaunchFrom(app, launchBounds) }
                        Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).then(dragModifier).clip(RoundedCornerShape(14.dp)).testTag("library-app-${app.id}")
                            .then(if (drag == null) Modifier.combinedClickable(onClick = click, onLongClick = { onActions(app) })
                                else Modifier.clickable(onClick = click).semantics { onLongClick("App options") { onActions(app); true } })
                            .padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            AppIcon(app, null, Modifier.size(40.dp)
                                .onGloballyPositioned { launchBounds.set(it.boundsInWindow().toAndroidBounds()) }.clip(RoundedCornerShape(10.dp)))
                            Row(Modifier.weight(1f).padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                NewAppDot(app.packageName, 7.dp)
                                Text(app.label, maxLines = 2, fontSize = 14.sp)
                            }
                            if (editing) IconButton(onClick = { onPin(app.id, !isPinned) }, Modifier.testTag("pin-${app.id}")) {
                                // iOS selection: filled blue check when on Home, empty ring when not.
                                Icon(if (isPinned) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                                    if (isPinned) "Remove ${app.label} from home" else "Pin ${app.label} to home",
                                    tint = if (isPinned) FolioColors.Blue else ink.copy(alpha = .35f),
                                    modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WebSearchRow(query: String, onSearch: (WebSearchTarget) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Text(stringResource(R.string.search_1_with, query.trim()), fontSize = 12.sp, color = Ink.copy(alpha = .75f),
            modifier = Modifier.padding(bottom = 6.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WebSearchTarget.entries.forEach { target ->
                AssistChip(onClick = { onSearch(target) }, label = { Text(target.label) },
                    leadingIcon = { Icon(if (target.label.startsWith("Ask")) Icons.Rounded.AutoAwesome else Icons.Rounded.Public,
                        null, Modifier.size(16.dp)) },
                    modifier = Modifier.testTag("web-search-${target.name.lowercase()}"))
            }
        }
    }
}
