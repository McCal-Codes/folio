package com.mccal.folio

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.withContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.mccal.folio.market.Capability
import com.mccal.folio.market.DepictionBlock
import com.mccal.folio.market.IndexPackage
import com.mccal.folio.market.InstallResult
import com.mccal.folio.market.InstalledPackage
import com.mccal.folio.market.FeaturedStyle
import com.mccal.folio.market.PackageManifest
import com.mccal.folio.market.PackagePermission
import com.mccal.folio.market.PackageSafety
import kotlinx.coroutines.launch
import com.mccal.folio.market.RefreshResult
import com.mccal.folio.market.RepoIndex
import com.mccal.folio.market.Source
import com.mccal.folio.market.Section

/** The Market's tabs. Adding a source over the network comes in Phase 6; Sources shows what Folio has today. */
internal enum class MarketTab(@androidx.annotation.StringRes val label: Int, val icon: ImageVector) {
    FEATURED(R.string.featured, Icons.Rounded.AutoAwesome),
    SOURCES(R.string.sources, Icons.Rounded.Public),
    PACKAGES(R.string.packages, Icons.Rounded.Storefront),
    INSTALLED(R.string.installed, Icons.Rounded.Download),
    SETTINGS(R.string.settings, Icons.Rounded.Settings),
}

/**
 * The Market: the packages Folio ships, what you have, and a page for each one.
 *
 * The layout follows the rest of Folio: one pane with a tab bar on a phone or a cover screen, and a list beside the
 * page when there's room ([fitsRegularHomeLayout]), so folding never loses your place.
 */
@Composable
internal fun MarketScreen(
    session: MarketSession,
    installedTweaks: Set<String>,
    onClose: () -> Unit,
    /** Folio's own Settings, shown in the Settings tab. Without it the tab shows the Market's settings on their own. */
    settingsContent: (@Composable () -> Unit)? = null,
) {
    // Coming back from Play or Obtainium is how an external app arrives, and there is no other signal that it did:
    // Folio isn't told, it has to look again. This counts the returns, and the lookup is keyed on it.
    var returns by remember { mutableIntStateOf(MarketExternalApp.appsChanged()) }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycle) {
        val watcher = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) returns = MarketExternalApp.appsChanged()
        }
        lifecycle.lifecycle.addObserver(watcher)
        onDispose { lifecycle.lifecycle.removeObserver(watcher) }
    }

    // Saveable, so folding, rotating or leaving and coming back keeps the tab and the package that was open.
    var tab by rememberSaveable { mutableStateOf(MarketTab.FEATURED) }
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    /** The source whose page is open, by address. Folio's own uses [BUILT_IN_SOURCE_URL]. */
    var openSourceUrl by rememberSaveable { mutableStateOf<String?>(null) }
    var introducing by rememberSaveable { mutableStateOf(!session.prefs.introductionSeen) }
    var style by rememberSaveable { mutableStateOf(session.prefs.featuredStyle) }
    var confirming by rememberSaveable { mutableStateOf<String?>(null) }
    var addingSource by rememberSaveable { mutableStateOf(false) }
    var sourceUrl by rememberSaveable { mutableStateOf("") }
    var trusting by remember { mutableStateOf<RefreshResult.NeedsTrust?>(null) }
    var statuses by remember { mutableStateOf(emptyList<SourceStatus>()) }
    val scope = rememberCoroutineScope()
    var undo by remember { mutableStateOf<InstallResult.Installed?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    // Counts every message said, so the same words said twice still get their full time on screen: keyed on the text
    // alone, a second "Keyd updated" inherited what was left of the first one's timer.
    var said by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    // Re-read after every change, so the list always shows what's really installed. Reading means parsing the
    // bundled index and every cached source list, which is far too much to do while a frame is being drawn - so it
    // happens off the main thread and the screen fills in when it's done.
    var revision by rememberSaveable { mutableIntStateOf(0) }
    val index by produceState<RepoIndex?>(null, revision) { value = withContext(session.io) { session.index() } }
    val entries by produceState(emptyList<MarketEntry>(), revision, statuses) {
        value = withContext(session.io) { session.entries() }
    }
    val installed by produceState(emptyMap<String, InstalledPackage>(), revision) {
        value = withContext(session.io) { session.installed().associateBy { it.id } }
    }
    LaunchedEffect(revision) { statuses = withContext(session.io) { session.sources.cached() } }
    // Installing outlives this screen: the work can't be stopped halfway, so it's kept where Back can't reach it.
    val busyId = MarketWork.busyId

    val context = androidx.compose.ui.platform.LocalContext.current

    fun refresh() { revision++ }

    /**
     * Says something in the banner. Undo belongs to the install it came from, so any other message takes it away:
     * an Undo left over from an earlier install would remove a package the user is happy with.
     */
    fun say(text: String?) { message = text; undo = null; said++ }

    /** What the banner says about a finished install, and whether it can still be undone. */
    fun announce(name: String, result: InstallResult) {
        when (result) {
            is InstallResult.Installed -> { message = context.getString(R.string.text_1_s_is_on, result.installed.name); undo = result; said++ }
            is InstallResult.NeedsNewerFolio -> say(context.getString(R.string.text_1_s_needs_a_newer_folio, name))
            is InstallResult.Failed -> say(result.message)
        }
        refresh()
    }

    /** An external app whose "where from" sheet is open. */
    var choosing by rememberSaveable { mutableStateOf<String?>(null) }

    fun apply(entry: MarketEntry) {
        confirming = null
        // One at a time. Two installs at once would each write the list of what's installed from a copy read before
        // the other started, so one package would be applied to Home and forgotten, with no way left to remove it.
        MarketWork.install(entry.id, entry.name, context.getString(R.string.folio_couldn_t_finish_that_install)) { session.get(entry) }
    }

    val appContext = context.applicationContext

    /**
     * Get, for whichever kind of package this is: an app of its own goes to the sheet that says where it installs
     * from, or straight to the app when Android already has it. Everything else goes to the confirm sheet.
     */
    fun onExternalOrConfirm(entry: MarketEntry) {
        val manifest = entry.entry.manifest
        if (!MarketExternalApp.isExternal(manifest)) { confirming = entry.listingKey; return }
        val already = MarketExternalApp.installedAppId(appContext, manifest)
        if (already != null) MarketExternalApp.open(context, already, returns) else choosing = entry.listingKey
    }

    // What finished while nobody was looking. Closing the Market during a download used to lose the message and the
    // Undo that went with it; now the store picks them up when it opens.
    LaunchedEffect(MarketWork.finished) {
        MarketWork.taken()?.let { announce(it.name, it.result) }
    }

    /**
     * Folio's own install of an app: download, check the checksum, hand it to Android. It runs where an install
     * runs, so leaving the store doesn't stop it, and the ring in the button follows it like any other.
     */
    fun installApp(entry: MarketEntry) {
        if (MarketWork.busy) return
        MarketWork.run(entry.id) {
            MarketApkInstall.install(context, entry.name, entry.entry) { url, onProgress ->
                session.sources.fetch(entry.source, url, entry.entry.size ?: 0, onProgress)
            }
        }
    }

    /**
     * What became of an app Folio handed to Android. It arrives from a broadcast, not from [installApp], because
     * Android's install screen is a different app and the answer comes back after it - so the banner is driven
     * from here, and nothing is said while that screen is in front of everything anyway.
     */
    LaunchedEffect(Unit) {
        MarketApkInstall.status.collect { status ->
            when (status) {
                is MarketApkInstall.Status.Installed -> {
                    say(context.getString(R.string.text_1_s_is_installed, status.name))
                    // Ask Android again whether the app is there, so the row stops saying Get.
                    returns = MarketExternalApp.appsChanged()
                    MarketApkInstall.seen()
                }
                is MarketApkInstall.Status.Failed -> { say(status.message); MarketApkInstall.seen() }
                else -> Unit
            }
        }
    }

    /** Reads a source again now, whatever its refresh schedule says. A key change comes back as a sheet. */
    fun refreshSource(source: Source) {
        scope.launch {
            val result = session.sources.refresh(source.url, force = true)
            if (result is RefreshResult.NeedsTrust) trusting = result
            statuses = withContext(session.io) { session.sources.cached() }
            say(refreshMessage(context, source, result))
            refresh()
        }
    }

    /** Forgets a source: its list, its cache and its pinned key. What it installed stays. */
    fun forgetSource(source: Source) {
        scope.launch {
            // Deleting a source's cache and its pinned key is file work, not frame work.
            withContext(session.io) { session.sources.forget(source.url) }
            statuses = withContext(session.io) { session.sources.cached() }
            openSourceUrl = null
            say(context.getString(R.string.text_1_s_removed, source.label))
            refresh()
        }
    }

    /** Puts back a package Safe Mode turned off. Its changes go on again, so it runs off the main thread too. */
    fun tryAgain(id: String, name: String) {
        if (MarketWork.busy) return
        scope.launch {
            val back = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { session.enable(id) }
            say(context.getString(if (back) R.string.text_1_s_is_back_on else R.string.folio_couldn_t_put_1_s_back_on, name))
            refresh()
        }
    }

    fun remove(id: String, name: String) {
        if (MarketWork.busy) return
        scope.launch {
            val removed = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { session.remove(id) }
            if (removed) say(context.getString(R.string.text_1_s_removed, name))
            refresh()
        }
    }

    // Back unwinds one step at a time, in the order things were opened: the package first, then the source it
    // was listed on, then the store itself (handled by the launcher).
    BackHandler(enabled = openId != null || openSourceUrl != null) {
        if (openId != null) openId = null else openSourceUrl = null
    }

    // A .foliopkg someone opened: read it once, then the same confirm sheet as anything else. Keyed on the file
    // itself, so one shared while the Market is already open is read there and then.
    var importing by remember { mutableStateOf<Pair<ByteArray, com.mccal.folio.market.FolioPackage>?>(null) }
    LaunchedEffect(MarketImport.pending) {
        val bytes = MarketImport.pending
        MarketImport.pending = null
        if (bytes != null) {
            // Unzipped off the main thread, like every other read here: a big package froze Home before its sheet.
            when (val read = withContext(session.io) { session.read(bytes) }) {
                is com.mccal.folio.market.PackageInstaller.ReadResult.Ok -> importing = bytes to read.pkg
                is com.mccal.folio.market.PackageInstaller.ReadResult.NeedsNewerFolio ->
                    say(context.getString(R.string.that_package_needs_a_newer_folio))
                is com.mccal.folio.market.PackageInstaller.ReadResult.Failed -> say(read.message)
            }
        }
    }

    // A shared folio:// link opens straight on that package, once.
    LaunchedEffect(MarketLink.pending) {
        when (val link = MarketLink.pending) {
            is MarketLink.Package -> { tab = MarketTab.PACKAGES; openId = link.id }
            is MarketLink.Source -> {
                // What the format says a source link does: the Add Source sheet, filled in. The fingerprint still
                // has to be confirmed, so a link can't add a source by itself.
                tab = MarketTab.SOURCES
                sourceUrl = link.url
                addingSource = true
            }
            // A code is redeemed by the activity that received it; by the time the Market opens it's already done.
            null -> Unit
        }
        MarketLink.pending = null
    }

    if (introducing) {
        MarketIntroduction(
            style = style,
            onStyle = { chosen -> style = chosen; session.prefs.featuredStyle = chosen },
            onDone = { session.prefs.introductionSeen = true; introducing = false },
        )
        return
    }

    // Every row and page asks the same question about an external app, and re-asks it when Folio
    // comes back to the front.
    CompositionLocalProvider(LocalAppsChanged provides returns) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val regular = fitsRegularHomeLayout(maxWidth.value, maxHeight.value, LocalConfiguration.current.classScale)
        val split = regular && maxWidth.value >= 700f
        // Where the tabs go, the same rule the Mockup Lab draws: a sidebar once the window is as wide as the Fold8
        // inner screen (iPad), a rail on the long edge when the window is too short for a bar under it (the cover
        // screen rotated), and the bar itself everywhere else.
        val tabs = when {
            !regular && maxWidth > maxHeight -> TabPlacement.RAIL
            split && maxWidth.value >= 920f -> TabPlacement.SIDEBAR
            else -> TabPlacement.BOTTOM
        }
        // The list stays beside what it opened only when both still get a readable width; otherwise the package or
        // source pushes over the list with Back, as the App Store does on an iPad. Nothing open, the list has it all.
        val beside = split && marketListBeside(maxWidth.value, sidebar = tabs == TabPlacement.SIDEBAR)
        // The sidebar is already one of the window's panes, so the Settings tab gets what's left of them.
        val settingsMost = if (tabs == TabPlacement.SIDEBAR) settingsColumnsBesideSidebar(maxWidth.value) else 3
        val packages = entries.map { it.entry }
        val open = openId?.let { id -> entries.firstOrNull { it.id == id } }
        val folioBeta = remember(returns) { SoftwareUpdate.betaSourceReady(context) }
        val betaOpen = folioBeta && tab == MarketTab.SOURCES && open == null && openSourceUrl == FOLIO_BETA_SOURCE_URL
        // A source's page, when one is open and no package is open in front of it. Only on the Sources tab: the
        // same source seen from a package's "Show source" row lands here too, by way of that tab.
        val openSource = openSourceUrl
            ?.takeIf { tab == MarketTab.SOURCES && open == null }
            ?.let { url ->
                if (url == BUILT_IN_SOURCE_URL) session.builtIn to null
                else statuses.firstOrNull { it.source.url == url }?.let { it.source to it }
            }

        // Kept here rather than in the list, so coming Back from a package lands where the list was, per tab.
        val listState = rememberSaveable(tab, saver = LazyListState.Saver) { LazyListState() }
        Row(Modifier.fillMaxSize()) {
        if (tabs == TabPlacement.SIDEBAR) MarketSidebar(tab) { tab = it; openId = null; openSourceUrl = null }
        Column(Modifier.weight(1f)) {
            Row(Modifier.weight(1f)) {
                if (tab == MarketTab.SETTINGS && settingsContent != null) {
                    Box(Modifier.fillMaxSize()) {
                        CompositionLocalProvider(LocalSettingsMaxColumns provides settingsMost) { settingsContent() }
                    }
                } else if (beside || (open == null && openSource == null && !betaOpen)) {
                    val narrow = beside && (open != null || openSource != null || betaOpen)
                    BoxWithConstraints(if (narrow) Modifier.width(360.dp).fillMaxHeight() else Modifier.weight(1f).fillMaxHeight()) {
                        MarketList(
                            session = session,
                            tab = tab,
                            state = listState,
                            columns = if (narrow) 1 else marketListColumns(maxWidth.value),
                            index = index,
                            statuses = statuses,
                            localDevAllowed = session.localDevAllowed,
                            openSourceUrl = openSourceUrl,
                            onOpenSource = { openSourceUrl = it; openId = null },
                            onAddSource = { addingSource = true },
                            folioBeta = folioBeta,
                            onAddLocalDev = {
                                scope.launch {
                                    val result = session.sources.addLocalDev(DEFAULT_LOCAL_SOURCE)
                                    statuses = withContext(session.io) { session.sources.cached() }
                                    say(refreshMessage(context, Source(DEFAULT_LOCAL_SOURCE, kind = Source.Kind.LOCAL_DEV), result))
                                    refresh()
                                }
                            },
                            entries = entries,
                            installed = installed,
                            openId = openId,
                            busyId = busyId,
                            style = style,
                            onStyle = { chosen -> style = chosen; session.prefs.featuredStyle = chosen },
                            onIntroduce = { session.prefs.introductionSeen = false; introducing = true },
                            onOpen = { openId = it },
                            onGet = { onExternalOrConfirm(it) },
                            onRemove = { id, name -> remove(id, name) },
                        )
                    }
                }
                if (betaOpen) {
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        MarketFolioBetaPage(showBack = !beside, backTitle = stringResource(tab.label), onBack = { openSourceUrl = null })
                    }
                }
                if (openSource != null) {
                    val (sourceOpen, statusOpen) = openSource
                    val fromSource = entries.filter { it.source.url == sourceOpen.url }
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        MarketSourcePage(
                            name = if (sourceOpen.kind == Source.Kind.BUILT_IN) {
                                index?.name?.english ?: stringResource(R.string.folio)
                            } else {
                                statusOpen?.source?.label ?: sourceOpen.label
                            },
                            source = sourceOpen,
                            status = statusOpen,
                            packageCount = fromSource.size,
                            showBack = !beside,
                            backTitle = stringResource(tab.label),
                            onBack = { openSourceUrl = null },
                            onRefresh = { refreshSource(sourceOpen) },
                            onForget = { forgetSource(sourceOpen) },
                        ) {
                            SheetGroup {
                                for (entry in fromSource) {
                                    MarketRow(
                                        session = session,
                                        entry = entry,
                                        installed = installed[entry.id],
                                        busy = entry.id == busyId,
                                        selected = false,
                                        onOpen = { openId = entry.id },
                                        onGet = { onExternalOrConfirm(entry) },
                                        onRemove = { remove(entry.id, entry.name) },
                                    )
                                }
                            }
                        }
                    }
                }
                if (open != null) {
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        MarketPackagePage(
                            entry = open.entry,
                            session = session,
                            installed = installed[open.id],
                            revoked = open.revokedReason,
                            source = open.source,
                            showBack = !beside,
                            // Back from a package opened on a source's page goes to that page, so it says its name.
                            backTitle = openSourceUrl?.takeIf { tab == MarketTab.SOURCES }?.let { url ->
                                if (url == BUILT_IN_SOURCE_URL) index?.name?.english
                                else statuses.firstOrNull { it.source.url == url }?.source?.label
                            } ?: stringResource(tab.label),
                            onBack = { openId = null },
                            onGet = { onExternalOrConfirm(open) },
                            onRemove = { remove(open.id, open.name) },
                            onTryAgain = { tryAgain(open.id, open.name) },
                            onShare = { share(context, it) },
                            onReport = { report(context, index?.issuesUrl, it) },
                            onShowSource = {
                                openId = null
                                openSourceUrl = if (open.source.kind == Source.Kind.BUILT_IN) BUILT_IN_SOURCE_URL else open.source.url
                                tab = MarketTab.SOURCES
                            },
                        )
                    }
                }
            }
            // A plain message goes away by itself, as an iOS banner does; it used to stay until tapped or replaced,
            // so "Keyd updated" could sit over the page for as long as the store was open. One with Undo stays until
            // it's dismissed or replaced, because dismissing it is what ends the chance to undo. The time is
            // Android's recommended one, which is longer for someone using TalkBack or a longer timeout setting.
            val a11y = remember { context.getSystemService(android.view.accessibility.AccessibilityManager::class.java) }
            LaunchedEffect(message, undo, said) {
                if (message != null && undo == null) {
                    val wait = a11y?.getRecommendedTimeoutMillis(
                        MESSAGE_MILLIS, android.view.accessibility.AccessibilityManager.FLAG_CONTENT_TEXT,
                    ) ?: MESSAGE_MILLIS
                    kotlinx.coroutines.delay(wait.toLong())
                    message = null
                }
            }
            message?.let { text ->
                MarketMessage(
                    text = text,
                    undo = undo?.let { result ->
                        {
                            val undone = result
                            undo = null
                            message = null
                            scope.launch {
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { session.undo(undone) }
                                refresh()
                            }
                        }
                    },
                    onDismiss = { message = null },
                )
            }
            if (tabs == TabPlacement.BOTTOM) MarketTabs(tab) { tab = it; openId = null; openSourceUrl = null }
        }
        if (tabs == TabPlacement.RAIL) MarketRail(tab) { tab = it; openId = null; openSourceUrl = null }
        }
        importing?.let { (bytes, pkg) ->
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .6f)).clickable { importing = null }) {
                Box(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)).background(FolioColors.SecondaryBackground)
                        .clickable(enabled = false) {},
                ) {
                    // A file can carry its author's own signature now, so say which it is rather than assuming.
                    // Checking it hashes every file in the package and verifies a signature, so it happens once,
                    // off the main thread: in a composable it would run again on every redraw of the sheet, over
                    // as much as 20 MB.
                    val signing by produceState<com.mccal.folio.market.AuthorTrust.Result?>(null, pkg.id, pkg.version) {
                        value = withContext(session.io) { session.authorOf(pkg) }
                    }
                    MarketInstallSheet(
                        manifest = pkg.manifest,
                        origin = InstallOrigin(
                            line = when (signing) {
                                null -> stringResource(R.string.from_a_file_you_opened_checking_who_made)
                                is com.mccal.folio.market.AuthorTrust.Result.Signed,
                                is com.mccal.folio.market.AuthorTrust.Result.FirstTime,
                                -> stringResource(R.string.from_a_file_you_opened_signed_by_its)
                                else -> stringResource(R.string.from_a_file_you_opened)
                            },
                            checksum = null,
                            // Nothing is claimed while the check is still running: an empty warning for a moment
                            // is better than one that says the wrong thing and then corrects itself.
                            warning = when (signing) {
                                null, is com.mccal.folio.market.AuthorTrust.Result.Signed -> null
                                is com.mccal.folio.market.AuthorTrust.Result.FirstTime ->
                                    stringResource(R.string.folio_will_remember_this_developer_s_key)
                                else -> stringResource(R.string.text_1_s_only_open_packages_from_someone_you, signing!!.message)
                            },
                        ),
                        onGet = {
                            importing = null
                            MarketWork.install(pkg.manifest.id, pkg.manifest.name.english, context.getString(R.string.folio_couldn_t_finish_that_install)) { session.installFile(bytes) }
                        },
                        onCancel = { importing = null },
                    )
                }
            }
        }
        if (addingSource) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .6f)).clickable { addingSource = false }) {
                Box(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)).background(FolioColors.SecondaryBackground)
                        .clickable(enabled = false) {},
                ) {
                    MarketAddSourceSheet(
                        url = sourceUrl,
                        onUrl = { sourceUrl = it },
                        onNext = {
                            val typed = sourceUrl
                            addingSource = false
                            scope.launch {
                                when (val result = session.sources.inspect(typed)) {
                                    is RefreshResult.NeedsTrust -> trusting = result
                                    is RefreshResult.Failed -> say(result.message)
                                    else -> {
                                        statuses = withContext(session.io) { session.sources.cached() }
                                        say(context.getString(R.string.that_source_is_already_set_up))
                                    }
                                }
                            }
                        },
                        onCancel = { addingSource = false },
                    )
                }
            }
        }
        trusting?.let { request ->
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .6f))) {
                Box(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)).background(FolioColors.SecondaryBackground),
                ) {
                    MarketTrustSheet(
                        url = request.url,
                        key = request.key,
                        previous = request.previous,
                        onTrust = {
                            trusting = null
                            scope.launch {
                                val result = session.sources.trust(request.url, request.key)
                                statuses = withContext(session.io) { session.sources.cached() }
                                sourceUrl = ""
                                say(refreshMessage(context, Source(request.url), result))
                                refresh()
                            }
                        },
                        onCancel = { trusting = null },
                    )
                }
            }
        }
        choosing?.let { key ->
            entries.firstOrNull { it.listingKey == key }?.entry?.manifest?.let { manifest ->
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .6f)).clickable { choosing = null }) {
                    Box(
                        Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)).background(FolioColors.SecondaryBackground)
                            .clickable(enabled = false) {},
                    ) {
                        val listing = entries.first { it.listingKey == key }
                        MarketExternalSheet(
                            manifest = manifest,
                            signed = listing.source.kind != Source.Kind.LOCAL_DEV,
                            onInstallHere = if (
                                MarketApkInstall.canInstall(listing.source, listing.entry, session.prefs.installApps, listing.revokedReason != null)
                            ) {
                                { choosing = null; installApp(listing) }
                            } else {
                                null
                            },
                            onPick = { from ->
                                choosing = null
                                if (!MarketExternalApp.install(context, from)) {
                                    say(context.getString(R.string.folio_couldn_t_open_1_s_on_this_phone,
                                        context.getString(MarketExternalApp.label(from.store))))
                                }
                            },
                            onCancel = { choosing = null },
                        )
                    }
                }
            }
        }
        confirming?.let { key ->
            entries.firstOrNull { it.listingKey == key }?.let { entry ->
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .6f)).clickable { confirming = null }) {
                    Box(
                        Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)).background(FolioColors.SecondaryBackground)
                            .clickable(enabled = false) {},
                    ) {
                        MarketInstallSheet(
                            entry = entry.entry,
                            builtIn = entry.source.kind == Source.Kind.BUILT_IN,
                            onGet = { apply(entry) },
                            onCancel = { confirming = null },
                        )
                    }
                }
            }
        }
        if (index == null) {
            Text(
                stringResource(R.string.folio_couldn_t_read_its_own_packages),
                color = Color.White, modifier = Modifier.align(Alignment.Center).padding(32.dp),
            )
        }
    }
    }
}

@Composable
private fun MarketTabs(selected: MarketTab, onSelect: (MarketTab) -> Unit) {
    Row(Modifier.fillMaxWidth().background(FolioColors.SecondaryBackground).padding(vertical = 6.dp)) {
        for (tab in MarketTab.entries) {
            Column(
                Modifier.weight(1f).marketTab(tab, onSelect).padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) { MarketTabIcon(tab, tab == selected); MarketTabLabel(tab, tab == selected) }
        }
    }
}

/** Where the tabs sit: under the content, up the trailing edge, or as a labelled sidebar on a big screen. */
private enum class TabPlacement { BOTTOM, RAIL, SIDEBAR }

/**
 * The tabs on the long edge, for a window too short for a bar underneath (the Fold8 cover screen rotated). The
 * content keeps the height it has, which is the scarce direction there.
 */
@Composable
private fun MarketRail(selected: MarketTab, onSelect: (MarketTab) -> Unit) {
    Column(
        Modifier.width(76.dp).fillMaxHeight().background(FolioColors.SecondaryBackground).padding(vertical = 8.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        for (tab in MarketTab.entries) {
            Column(
                Modifier.fillMaxWidth().marketTab(tab, onSelect).padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) { MarketTabIcon(tab, tab == selected); MarketTabLabel(tab, tab == selected) }
        }
    }
}

/**
 * A labelled sidebar instead of a tab bar, the way iPad Settings and the App Store use the width they have. The
 * selected row is a rounded highlight, inset from the edges like the sidebar rows in Folio's own Settings.
 */
@Composable
private fun MarketSidebar(selected: MarketTab, onSelect: (MarketTab) -> Unit) {
    Column(
        Modifier.width(180.dp).fillMaxHeight().background(FolioColors.SecondaryBackground)
            .windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 8.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            stringResource(R.string.market), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 10.dp),
        )
        for (tab in MarketTab.entries) {
            val on = tab == selected
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    .background(if (on) FolioColors.Blue else Color.Transparent)
                    .marketTab(tab, onSelect).padding(horizontal = 12.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    tab.icon, contentDescription = null, modifier = Modifier.size(20.dp),
                    tint = if (on) Color.White else Color.White.copy(alpha = .55f),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(tab.label), color = if (on) Color.White else Color.White.copy(alpha = .85f), fontSize = 15.sp,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** One tab is one control wherever it's drawn, so the tag and the spoken label don't depend on the layout. */
@Composable
private fun Modifier.marketTab(tab: MarketTab, onSelect: (MarketTab) -> Unit) =
    clickable(onClickLabel = stringResource(tab.label)) { onSelect(tab) }.testTag("market-tab-${tab.name.lowercase()}")

@Composable
private fun MarketTabIcon(tab: MarketTab, on: Boolean) = Icon(
    tab.icon, contentDescription = null, modifier = Modifier.size(22.dp),
    tint = if (on) FolioColors.Blue else Color.White.copy(alpha = .55f),
)

@Composable
private fun MarketTabLabel(tab: MarketTab, on: Boolean) = Text(
    stringResource(tab.label), color = if (on) FolioColors.Blue else Color.White.copy(alpha = .55f), fontSize = 11.sp,
    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
)

@Composable
private fun MarketList(
    session: MarketSession,
    tab: MarketTab,
    state: LazyListState,
    columns: Int,
    index: RepoIndex?,
    statuses: List<SourceStatus>,
    localDevAllowed: Boolean,
    openSourceUrl: String?,
    onOpenSource: (String) -> Unit,
    onAddSource: () -> Unit,
    onAddLocalDev: () -> Unit,
    folioBeta: Boolean,
    entries: List<MarketEntry>,
    installed: Map<String, InstalledPackage>,
    openId: String?,
    busyId: String?,
    style: FeaturedStyle,
    onStyle: (FeaturedStyle) -> Unit,
    onIntroduce: () -> Unit,
    onOpen: (String) -> Unit,
    onGet: (MarketEntry) -> Unit,
    onRemove: (String, String) -> Unit,
) {
    // A package is an update when a source offers a higher version than the one installed.
    val updates = entries.filter { entry ->
        installed[entry.id]?.let { entry.entry.version > it.version } == true
    }
    val shown = when (tab) {
        MarketTab.FEATURED, MarketTab.PACKAGES -> entries
        MarketTab.INSTALLED -> entries.filter { it.id in installed } - updates.toSet()
        MarketTab.SOURCES, MarketTab.SETTINGS -> emptyList()
    }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), state = state, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        item {
            Column(Modifier.padding(top = 12.dp, bottom = 4.dp)) {
                if (tab == MarketTab.FEATURED) Text(stringResource(R.string.welcome_to_folio), color = Color.White.copy(alpha = .55f), fontSize = 13.sp)
                Text(stringResource(tab.label), color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (tab == MarketTab.FEATURED && index != null) {
            item(key = "featured") {
                MarketFeatured(
                    featured = index.featured,
                    // Folio's own packages only. The banners come from Folio's index, so what they point at has to
                    // come from there too, or a source could put itself in Folio's window by reusing an id.
                    packages = index.packages,
                    calm = style == FeaturedStyle.CALM,
                    onOpen = onOpen,
                )
            }
        }
        if (tab == MarketTab.SOURCES) {
            item(key = "sources") {
                MarketSourcesTab(
                    builtInName = index?.name?.english ?: stringResource(R.string.folio),
                    builtInCount = entries.count { it.source.kind == Source.Kind.BUILT_IN },
                    statuses = statuses,
                    localDevAllowed = localDevAllowed,
                    openUrl = openSourceUrl,
                    onOpen = onOpenSource,
                    onAdd = onAddSource,
                    onAddLocalDev = onAddLocalDev,
                    folioBeta = folioBeta,
                )
            }
        }
        if (tab == MarketTab.SETTINGS) {
            item(key = "settings") { MarketOwnSettings(style = style, onStyle = onStyle, onIntroduce = onIntroduce) }
        }
        if (tab == MarketTab.INSTALLED && updates.isNotEmpty()) {
            item(key = "updates-label") { SheetGroupLabel("Updates") }
            item(key = "updates") {
                SheetGroup(Modifier.padding(bottom = 10.dp)) {
                    for (entry in updates) {
                        MarketRow(
                            session = session,
                            entry = entry,
                            installed = installed[entry.id],
                            busy = entry.id == busyId,
                            update = true,
                            selected = entry.id == openId,
                            onOpen = { onOpen(entry.id) },
                            onGet = { onGet(entry) },
                            onRemove = { onRemove(entry.id, entry.name) },
                        )
                    }
                }
            }
        }
        if (shown.isEmpty() && tab == MarketTab.INSTALLED && updates.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.nothing_yet_themes_and_tweaks_you_get),
                    color = Color.White.copy(alpha = .55f), modifier = Modifier.padding(vertical = 24.dp),
                )
            }
        }
        for (section in Section.entries) {
            val inSection = shown.filter { it.entry.manifest?.section == section }
            if (inSection.isEmpty()) continue
            item(key = "label-${section.id}") { SheetGroupLabel(section.id.replaceFirstChar(Char::uppercase)) }
            item(key = "group-${section.id}") {
                SheetGroup(Modifier.padding(bottom = 10.dp)) {
                    // Row by row, left to right, so reading order and TalkBack's order stay the list's order.
                    for (pair in inSection.chunked(columns)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            for (entry in pair) {
                                Box(Modifier.weight(1f)) {
                                    MarketRow(
                                        session = session,
                                        entry = entry,
                                        installed = installed[entry.id],
                                        busy = entry.id == busyId,
                                        selected = entry.id == openId,
                                        onOpen = { onOpen(entry.id) },
                                        onGet = { onGet(entry) },
                                        onRemove = { onRemove(entry.id, entry.name) },
                                    )
                                }
                            }
                            // An odd last row keeps its half, so rows line up down both columns.
                            repeat(columns - pair.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MarketRow(
    session: MarketSession,
    entry: MarketEntry,
    installed: InstalledPackage?,
    busy: Boolean,
    selected: Boolean,
    onOpen: () -> Unit,
    onGet: () -> Unit,
    onRemove: () -> Unit,
    update: Boolean = false,
) {
    val name = entry.name
    val author = entry.entry.manifest?.author?.name?.english.orEmpty()
    val openLabel = stringResource(R.string.open_1_s, name)
    Row(
        Modifier.fillMaxWidth()
            .background(if (selected) Color.White.copy(alpha = .06f) else Color.Transparent)
            .clickable(onClickLabel = openLabel, onClick = onOpen)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PackageIcon(session, entry, 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, color = Color.White, fontSize = 16.sp)
            Text(
                when {
                    installed?.enabled == false -> stringResource(R.string.turned_off_after_a_crash)
                    entry.revokedReason != null -> entry.revokedReason
                    update -> "${installed?.version} → ${entry.entry.version}"
                    // A package from somewhere other than Folio says where it came from.
                    entry.source.kind != Source.Kind.BUILT_IN -> "$author · ${entry.source.label}"
                    else -> author
                },
                color = when {
                    installed?.enabled == false || entry.revokedReason != null -> Color(0xFFFFB340)
                    else -> Color.White.copy(alpha = .55f)
                },
                fontSize = 13.sp,
            )
            if (entry.unsigned) {
                Text(stringResource(R.string.unsigned), color = Color(0xFFFFB340), fontSize = 12.sp)
            }
            when (entry.clash) {
                MarketEntry.Impostor.BUILT_IN ->
                    Text(stringResource(R.string.claims_a_folio_package_s_name), color = FolioColors.Red, fontSize = 12.sp)
                MarketEntry.Impostor.ANOTHER_SOURCE ->
                    Text(stringResource(R.string.another_source_offers_this_name_too), color = Color(0xFFFFB340), fontSize = 12.sp)
                null -> Unit
            }
        }
        when {
            busy -> InstallProgress(MarketWork.progress, words = false, name = name)
            // A revoked package can be removed but never installed again - including as an update, which is how a
            // pulled package used to slip back in.
            entry.revokedReason != null && installed != null -> MarketActionButton(R.string.remove, name, onRemove)
            entry.revokedReason != null -> Text(stringResource(R.string.unavailable), color = Color.White.copy(alpha = .55f), fontSize = 13.sp)
            // Nothing can be installed under a name that belongs to a package inside Folio.
            entry.clash == MarketEntry.Impostor.BUILT_IN ->
                Text(stringResource(R.string.refused), color = FolioColors.Red, fontSize = 13.sp)
            entry.entry.needs.isNotEmpty() -> Text(stringResource(R.string.needs_a_newer_folio), color = Color.White.copy(alpha = .55f), fontSize = 13.sp)
            update -> MarketActionButton(R.string.update, name, onGet)
            // An app of its own isn't installed by Folio, so what it offers is Get until Android has it, then Open.
            MarketExternalApp.isExternal(entry.entry.manifest) ->
                MarketActionButton(
                    if (externalAppId(entry.entry.manifest) != null) R.string.open else R.string.get, name, onGet,
                )
            installed != null -> MarketActionButton(R.string.remove, name, onRemove)
            else -> MarketActionButton(R.string.get, name, onGet)
        }
    }
}

/**
 * The package name of an external app that is on this phone, or null.
 *
 * Asked of Android rather than remembered, because the app can arrive or go while Folio is open - the person
 * leaves for Play and comes back - and a remembered answer would be wrong exactly then. It's a cheap lookup, and
 * it is only asked for a package that says it is an app.
 */
@Composable
private fun externalAppId(manifest: com.mccal.folio.market.PackageManifest?): String? {
    val context = androidx.compose.ui.platform.LocalContext.current
    val resumed = LocalAppsChanged.current
    return remember(manifest, resumed) { MarketExternalApp.installedAppId(context, manifest, resumed) }
}

/** Bumped when Folio comes back to the front, so "is it installed yet" is asked again after a trip to Play. */
internal val LocalAppsChanged = androidx.compose.runtime.compositionLocalOf { 0 }

/**
 * The Market's own settings, for when it's shown without the launcher's Settings behind it (the tests, and any future
 * place the store stands alone). Inside Folio, the Settings tab shows Folio's real Settings instead.
 */
@Composable
private fun MarketOwnSettings(style: FeaturedStyle, onStyle: (FeaturedStyle) -> Unit, onIntroduce: () -> Unit) {
    Column {
        SheetGroupLabel(stringResource(R.string.featured_style))
        IosSegmented(
            options = FeaturedStyle.entries.map { it to stringResource(it.label) },
            selected = style,
            onSelect = onStyle,
            modifier = Modifier.padding(vertical = 8.dp),
            tag = "market-featured-style",
        )
        Text(
            stringResource(style.description),
            color = Color.White.copy(alpha = .55f), fontSize = 13.sp, modifier = Modifier.padding(bottom = 12.dp),
        )
        SheetGroup(Modifier.padding(bottom = 10.dp)) {
            IosActionRow(stringResource(R.string.show_the_introduction_again), onClick = onIntroduce)
        }
        val context = androidx.compose.ui.platform.LocalContext.current
        SheetGroup(Modifier.padding(bottom = 16.dp)) {
            IosActionRow(stringResource(R.string.open_folio_settings)) {
                runCatching {
                    context.startActivity(
                        android.content.Intent(android.content.Intent.ACTION_APPLICATION_PREFERENCES)
                            .setPackage(context.packageName)
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            }
        }
    }
}

/** The Get / Remove pill. Its name says which package it belongs to, so a screen reader hears more than "Get". */
@Composable
private fun MarketActionButton(@androidx.annotation.StringRes label: Int, name: String, onClick: () -> Unit) {
    // A semantics block isn't a composable scope, so the spoken name is read before the modifier chain.
    val described = stringResource(R.string.text_1_s_2_s, stringResource(label), name)
    Text(
        stringResource(label),
        color = FolioColors.Blue,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            // 44 dp tall, so it's a comfortable target rather than just big enough to see.
            .heightIn(min = 44.dp)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .semantics { contentDescription = described },
    )
}

@Composable
private fun MarketPackagePage(
    entry: IndexPackage,
    session: MarketSession,
    installed: InstalledPackage?,
    revoked: String?,
    source: Source,
    showBack: Boolean,
    backTitle: String? = null,
    onBack: () -> Unit,
    onGet: () -> Unit,
    onRemove: () -> Unit,
    onTryAgain: () -> Unit,
    onShare: (IndexPackage) -> Unit,
    onReport: (IndexPackage) -> Unit,
    onShowSource: () -> Unit,
) {
    // Keyed on the version too: after an update the page has to read the new package's own text and images, not the
    // ones it read before. Reading them means parsing every bundled package, so it happens off the main thread.
    val pkg by produceState<com.mccal.folio.market.FolioPackage?>(null, entry.id, entry.version) {
        value = withContext(session.io) { session.read(entry.id) }
    }
    // A package from a source keeps its page on the source, named by the manifest's `depiction`. It used to be read
    // only from a package already on the phone, so nothing from a source ever showed a screenshot, and an app -
    // which is never a package on the phone - never could. It is only words and pictures, drawn from the same closed
    // set of blocks as Folio's own; what the safety sheet says still comes from the signed index alone.
    val sourcePage by produceState<com.mccal.folio.market.Depiction?>(null, source.url, entry.id, entry.version) {
        val path = entry.manifest?.depiction
        value = if (source.kind == Source.Kind.BUILT_IN || path == null) null else withContext(session.io) {
            session.sources.fetch(source, path, com.mccal.folio.market.Depiction.MAX_CHARS) { _, _ -> }
                ?.decodeToString()
                ?.let { (com.mccal.folio.market.Depiction.parse(it) as? com.mccal.folio.market.ParseResult.Ok)?.value }
        }
    }
    val name = entry.manifest?.name?.english ?: entry.id
    val backLabel = stringResource(R.string.back)
    val external = MarketExternalApp.isExternal(entry.manifest)
    val onPhone = if (external) externalAppId(entry.manifest) else null
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        if (showBack) {
            Row(Modifier.fillMaxWidth().clickable(onClickLabel = backLabel, onClick = onBack).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.ChevronLeft, contentDescription = null, tint = FolioColors.Blue, modifier = Modifier.size(18.dp))
                // Where Back goes, as on iPhone ("‹ Packages"); TalkBack still hears "Back" as the action.
                Text(backTitle ?: backLabel, color = FolioColors.Blue, fontSize = 16.sp)
            }
        }
        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            PackageIcon(session, MarketEntry(entry, source), 64.dp)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(name, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                entry.manifest?.author?.name?.english?.let { Text(it, color = Color.White.copy(alpha = .55f), fontSize = 14.sp) }
                // The author said AI helped make it (the manifest's aiAssisted). A plain tag, not a warning: it's
                // how the package was made, the way the author line says who made it.
                if (entry.manifest?.aiAssisted != null) AiAssistedTag(Modifier.padding(top = 4.dp))
            }
        }

        if (installed?.enabled == false) {
            SheetGroup(Modifier.padding(top = 12.dp)) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = Color(0xFFFFB340), modifier = Modifier.size(20.dp))
                    Column(Modifier.padding(start = 10.dp)) {
                        Text(stringResource(R.string.turned_off_after_a_crash), color = Color.White, fontSize = 15.sp)
                        Text(
                            installed.disabledReason ?: stringResource(R.string.your_settings_are_kept),
                            color = Color.White.copy(alpha = .55f), fontSize = 13.sp,
                        )
                        // Safe Mode took its changes off Home. This puts them back, for a crash that wasn't its
                        // fault; Remove, below, is the other way out.
                        Text(
                            stringResource(R.string.try_again),
                            color = FolioColors.Blue, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 8.dp).clip(RoundedCornerShape(12.dp))
                                .clickable(onClick = onTryAgain).heightIn(min = 44.dp)
                                .padding(vertical = 11.dp).testTag("package-try-again"),
                        )
                    }
                }
            }
        }

        Row(Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            when {
                // Pulled by its source. Removing what's already on is still allowed; getting it is not.
                revoked != null && installed == null -> Column(Modifier.testTag("package-unavailable")) {
                    Text(stringResource(R.string.unavailable), color = Color(0xFFFFB340), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.its_source_pulled_it_1_s, revoked), color = Color.White.copy(alpha = .55f), fontSize = 13.sp)
                }
                MarketWork.busyId == entry.id -> InstallProgress(MarketWork.progress, words = true, name = name)
                external -> MarketActionButton(if (onPhone != null) R.string.open else R.string.get, name, onGet)
                installed != null -> MarketActionButton(R.string.remove, name, onRemove)
                else -> MarketActionButton(R.string.get, name, onGet)
            }
            Spacer(Modifier.width(8.dp))
            // The version beside the button. "Built in" belongs to Folio's own packages; a listing from a source
            // that isn't installed yet was being called built in too, which is the one thing it certainly isn't.
            Text(
                when {
                    installed != null -> stringResource(R.string.version_1, installed.version)
                    source.kind == Source.Kind.BUILT_IN -> stringResource(R.string.built_in)
                    else -> stringResource(R.string.version_1, entry.version.text)
                },
                color = Color.White.copy(alpha = .55f), fontSize = 13.sp,
            )
        }

        entry.manifest?.description?.english?.let {
            Text(it, color = Color.White, fontSize = 15.sp, modifier = Modifier.padding(bottom = 12.dp))
        }

        // The page the author wrote: Folio draws each block itself, and skips any it doesn't know.
        val blocks = (pkg?.depiction ?: sourcePage)?.blocks.orEmpty()
        if (blocks.none { it is DepictionBlock.Hero || it is DepictionBlock.Screenshots }) NoScreenshots()
        blocks.forEach { block ->
            when (block) {
                is DepictionBlock.Hero -> MarketImage(session, source, block.image, Modifier.fillMaxWidth().height(160.dp))
                is DepictionBlock.Screenshots -> Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    block.images.forEach { MarketScreenshot(session, source, it, height = 260.dp) }
                }
                // The subset format-v1 promises authors: paragraphs, bold, italic, lists and links, nothing else.
                is DepictionBlock.Markdown -> Column(Modifier.padding(bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MarketText.paragraphs(block.text.english).forEach { para ->
                        Text(
                            if (para.bullet) buildAnnotatedString { append("·  "); append(para.text) } else para.text,
                            color = Color.White.copy(alpha = .85f), fontSize = 15.sp, lineHeight = 21.sp,
                            modifier = if (para.bullet) Modifier.padding(start = 4.dp) else Modifier,
                        )
                    }
                }
                is DepictionBlock.FeatureList -> Column(Modifier.padding(bottom = 10.dp)) {
                    block.items.forEach { Text("· ${it.english}", color = Color.White.copy(alpha = .85f), fontSize = 15.sp) }
                }
                is DepictionBlock.Changelog -> Column(Modifier.padding(bottom = 10.dp)) {
                    SheetGroupLabel(stringResource(R.string.what_s_new))
                    block.entries.forEach { Text("${it.version} — ${it.notes.english}", color = Color.White.copy(alpha = .7f), fontSize = 14.sp) }
                }
                else -> Unit
            }
        }

        // What a package can't reach is worked out from the Folio permissions it asks for. An app of its own asks
        // Folio for nothing and gets everything Android grants it - a keyboard sees what you type - so the list
        // would be a promise Folio has no way to keep. It says what is true instead.
        if (external) {
            SheetGroupLabel(stringResource(R.string.an_app_of_its_own))
            SheetGroup(Modifier.padding(bottom = 12.dp)) {
                Text(
                    stringResource(R.string.folio_can_t_see_inside_an_app),
                    color = Color.White.copy(alpha = .7f), fontSize = 14.sp, modifier = Modifier.padding(14.dp),
                )
            }
        }
        val safety = entry.manifest?.takeUnless { external }?.let { PackageSafety.of(it) }
        safety?.let {
            SheetGroupLabel(stringResource(R.string.what_it_can_t_reach))
            SheetGroup(Modifier.padding(bottom = 12.dp)) {
                Column(Modifier.padding(14.dp)) {
                    it.cannotAccess.forEach { line -> Text(line, color = Color.White.copy(alpha = .7f), fontSize = 14.sp) }
                }
            }
        }

        SheetGroupLabel(stringResource(R.string.information))
        SheetGroup(Modifier.padding(bottom = 12.dp)) {
            Column(Modifier.padding(14.dp)) {
                // The source's name leads to the source, the way it does in Cydia and Sileo: a package is listed
                // somewhere, and that somewhere has the rest of what it offers.
                val sourceLine = stringResource(R.string.source_1_s, source.label)
                Text(
                    sourceLine,
                    color = Color(0xFF6CB4FF), fontSize = 14.sp,
                    modifier = Modifier.clickable(onClickLabel = sourceLine, onClick = onShowSource)
                        .heightIn(min = 44.dp).padding(vertical = 12.dp).testTag("package-show-source"),
                )
                // Provenance, or the honest absence of it. A package from a source with no provenance was being
                // called "Built into Folio", which said the opposite of where it actually came from.
                val built = entry.provenance
                Text(
                    when {
                        built != null -> stringResource(R.string.built_from_1_s_2_s, built.repo, built.commit)
                        source.kind == Source.Kind.BUILT_IN -> stringResource(R.string.built_into_folio)
                        else -> stringResource(R.string.its_source_didn_t_say_how_it_was_built)
                    },
                    color = Color.White.copy(alpha = .55f), fontSize = 13.sp,
                )
                installed?.let { Text(stringResource(R.string.installed_1_s, it.version), color = Color.White.copy(alpha = .55f), fontSize = 13.sp) }
                entry.manifest?.aiAssisted?.let { ai ->
                    Text(
                        stringResource(R.string.made_with_ai_1_s, ai.tools.joinToString()),
                        color = Color.White.copy(alpha = .85f), fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp),
                    )
                    ai.note?.let { Text(it.english, color = Color.White.copy(alpha = .55f), fontSize = 13.sp) }
                }
                if (external) {
                    // Where it comes from, and whether Android has it - the two things the store can honestly say
                    // about an app it doesn't install.
                    val stores = entry.manifest?.via.orEmpty()
                        .map { stringResource(MarketExternalApp.label(it.store)) }.joinToString()
                    Text(
                        stringResource(R.string.get_it_on_1_s, stores),
                        color = Color.White.copy(alpha = .85f), fontSize = 14.sp,
                    )
                    Text(
                        stringResource(R.string.on_this_phone_1_s, onPhone ?: stringResource(R.string.not_yet)),
                        color = Color.White.copy(alpha = .55f), fontSize = 13.sp,
                    )
                }
            }
        }
        SheetGroup(Modifier.padding(bottom = 24.dp)) {
            IosActionRow(stringResource(R.string.share)) { onShare(entry) }
            MenuDivider()
            IosActionRow(stringResource(R.string.report_a_package), destructive = true) { onReport(entry) }
        }

        // The privacy label comes from the manifest's permissions, never from anything the author wrote - which is
        // exactly why an app doesn't get one: its manifest asks Folio for nothing, and "No data collected" under a
        // keyboard would be Folio vouching for something it can't see.
        if (external) return@Column
        SheetGroupLabel(stringResource(if (entry.manifest?.permissions.isNullOrEmpty()) R.string.no_data_collected else R.string.what_this_package_changes))
        SheetGroup(Modifier.padding(bottom = 24.dp)) {
            val lines = entry.manifest?.permissions.orEmpty().mapNotNull(PackagePermission::label)
            if (lines.isEmpty()) {
                Text(stringResource(R.string.changes_appearance_only), color = Color.White.copy(alpha = .7f), fontSize = 14.sp, modifier = Modifier.padding(14.dp))
            } else {
                Column(Modifier.padding(14.dp)) {
                    lines.forEach { Text(it, color = Color.White.copy(alpha = .85f), fontSize = 14.sp) }
                }
            }
        }
    }
}

/** "AI-assisted", beside the developer on a package page. */
@Composable
internal fun AiAssistedTag(modifier: Modifier = Modifier) {
    Text(
        stringResource(R.string.ai_assisted),
        color = Color.White.copy(alpha = .7f), fontSize = 11.sp,
        modifier = modifier.border(0.5.dp, Color.White.copy(alpha = .3f), RoundedCornerShape(50))
            .padding(horizontal = 7.dp, vertical = 1.dp).testTag("package-ai-assisted"),
    )
}

/** The "… is on · Undo" line, the same shape as Folio's other undo messages. */
@Composable
private fun MarketMessage(text: String, undo: (() -> Unit)?, onDismiss: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(14.dp)).background(Color(0xFF2C2C2E))
            .clickable(onClickLabel = stringResource(R.string.dismiss), onClick = onDismiss)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
        if (undo != null) {
            Text(stringResource(R.string.undo), color = FolioColors.Blue, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable(onClick = undo))
        }
    }
}

/** How long a plain message stays, before Android lengthens it for anyone who needs longer. */
private const val MESSAGE_MILLIS = 4_000

/** What Folio can do today, for the "Needs a newer Folio" check. Kept next to the screen that shows it. */
internal val MARKET_CAPABILITIES: Set<Capability> = MarketHost(NoLauncher).capabilities

private object NoLauncher : MarketLauncher {
    override val state = LauncherState()
    override fun installTweak(feature: TweakFeature) = Unit
    override fun removeTweak(feature: TweakFeature) = Unit
    override fun setFeatureScope(id: String, screen: FolioScreen, value: ScopeValue) = Unit
    override fun applyTheme(theme: FolioTheme) = Unit
}

/**
 * Which sheet the Folio app icon opens. The Market holds Settings as a tab, so the icon opens the Market once it
 * exists — but anything that asked for a particular Settings page (a permission prompt, an update) still gets
 * Settings, because that's what it asked for.
 */
internal fun sheetForAppIcon(linkedPage: CustomizationPage?, currentPage: CustomizationPage, marketEnabled: Boolean): String =
    if (linkedPage == null && currentPage == CustomizationPage.OVERVIEW && marketEnabled) "market" else "settings"

/**
 * A picture a package shows. Folio's own packages are in the APK, so their bytes are decoded straight from assets; a
 * package from a source names a path on that source's host, which [MarketImages] fetches through Folio's own client.
 */
@Composable
private fun MarketImage(session: MarketSession, source: Source, path: String, modifier: Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val url = remember(source.url, path) { MarketImages.urlFor(source, path) }
    val bundled = remember(path, url) {
        if (url != null) null else MarketImages.bundled(session.source::asset, path)
    }
    Box(modifier.padding(bottom = 10.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = .06f))) {
        when {
            bundled != null -> androidx.compose.foundation.Image(
                bitmap = bundled,
                contentDescription = null, // the page's text says what it is; the picture repeats it
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            url != null -> coil3.compose.AsyncImage(
                model = url,
                imageLoader = MarketImages.loader(context),
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * One screenshot, at its own shape. They used to be cropped into a fixed 150 by 260 frame, which only suits a
 * screenshot of a whole phone screen: a keyboard is wider than it is tall, and cropping it lost the keys at both
 * edges. The height is fixed so a row of them lines up, and the width follows the picture, as App Store listings do.
 */
@Composable
private fun MarketScreenshot(session: MarketSession, source: Source, path: String, height: androidx.compose.ui.unit.Dp) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val url = remember(source.url, path) { MarketImages.urlFor(source, path) }
    val bundled = remember(path, url) { if (url != null) null else MarketImages.bundled(session.source::asset, path) }
    val painter = when {
        bundled != null -> remember(bundled) { androidx.compose.ui.graphics.painter.BitmapPainter(bundled) }
        url != null -> coil3.compose.rememberAsyncImagePainter(url, imageLoader = MarketImages.loader(context))
        else -> null
    }
    val size = painter?.intrinsicSize
    // Until the picture arrives there is no shape to follow, so it holds a phone-shaped space rather than none.
    val ratio = if (size != null && size != androidx.compose.ui.geometry.Size.Unspecified && size.width > 0f && size.height > 0f) size.width / size.height else 150f / 260f
    Box(
        Modifier.padding(bottom = 10.dp).height(height).then(Modifier.width(height * ratio))
            .clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = .06f)),
    ) {
        painter?.let {
            androidx.compose.foundation.Image(
                painter = it,
                contentDescription = null, // the page's text says what it is; the picture repeats it
                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * A package's icon: the one it ships if it has one, and otherwise a tile in its section's colour with its first
 * letter. Every row has one either way, so the list doesn't change shape depending on who published what.
 */
@Composable
private fun PackageIcon(session: MarketSession, entry: MarketEntry, size: androidx.compose.ui.unit.Dp) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val icon = entry.entry.manifest?.icon
    val url = remember(entry.source.url, icon) { icon?.let { MarketImages.urlFor(entry.source, it) } }
    // Folio's own icons are in the APK; a source's are on its host, and Coil fetches them.
    val bundled = remember(icon, url) {
        if (icon == null || url != null) null else MarketImages.bundled(session.source::asset, icon)
    }
    val tint = sectionColor(entry.entry.manifest?.section)
    val letter: @Composable () -> Unit = {
        Text(
            entry.name.take(1).uppercase(), color = Color.White,
            fontSize = (size.value * .42f).sp, fontWeight = FontWeight.SemiBold,
        )
    }
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size / 4.5f)).background(tint),
        contentAlignment = Alignment.Center,
    ) {
        when {
            bundled != null -> androidx.compose.foundation.Image(
                bitmap = bundled, contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize(),
            )
            // The letter while it loads and if it never does: an icon that failed used to leave a blank square.
            url != null -> coil3.compose.SubcomposeAsyncImage(
                model = url, imageLoader = MarketImages.loader(context), contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                loading = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { letter() } },
                error = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { letter() } },
            )
            else -> letter()
        }
    }
}

/**
 * The App Store's filling ring, where the Get button was.
 *
 * Determinate while bytes are arriving and the source said how big the file is; a turning ring otherwise, because
 * a bar that sits at an invented percentage is worse than one that admits it doesn't know. [words] adds roughly how
 * long is left, which only fits on a package's page - see [MarketProgress] for why "roughly".
 */
/** [MarketProgress.Wording] in the phone's language. */
@Composable
private fun marketProgressWords(wording: MarketProgress.Wording): String = when (wording) {
    MarketProgress.Wording.Applying -> stringResource(R.string.progress_applying)
    MarketProgress.Wording.NearlyDone -> stringResource(R.string.progress_nearly_done)
    MarketProgress.Wording.AFewSeconds -> stringResource(R.string.progress_a_few_seconds_left)
    is MarketProgress.Wording.Seconds ->
        pluralStringResource(R.plurals.progress_about_seconds_left, wording.seconds.toInt(), wording.seconds.toInt())
    is MarketProgress.Wording.Minutes ->
        pluralStringResource(R.plurals.progress_about_minutes_left, wording.minutes, wording.minutes)
    is MarketProgress.Wording.Megabytes -> stringResource(R.string.progress_megabytes, wording.soFar, wording.total)
}

@Composable
private fun InstallProgress(progress: MarketProgress?, words: Boolean, name: String) {
    val fraction = progress?.fraction
    val doing = stringResource(
        if (progress?.phase == MarketProgress.Phase.APPLYING) R.string.applying_1_s else R.string.installing_1_s, name,
    )
    Row(
        Modifier.padding(horizontal = 10.dp).semantics {
            // A list can have a ring in it with nothing else to read, so the ring says which package it belongs to.
            contentDescription = doing
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Only on the page. In a row the line squeezes the package's name, which the lab's checks caught on a
        // small phone; the App Store shows a bare ring in a list for the same reason.
        if (words) {
            progress?.wording?.let { marketProgressWords(it) }?.let {
                // It wraps rather than being cut short: at 200% text "About 20 seconds left" is wider than the
                // column, and half a sentence about how long is left is worse than two lines of it.
                Text(
                    it, color = Color.White.copy(alpha = .55f), fontSize = 13.sp,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(8.dp))
            }
        }
        Box(Modifier.size(26.dp), contentAlignment = Alignment.Center) {
            if (fraction != null) {
                androidx.compose.material3.CircularProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.size(26.dp),
                    color = FolioColors.Blue,
                    trackColor = Color.White.copy(alpha = .16f),
                    strokeWidth = 3.dp,
                )
            } else {
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.size(26.dp),
                    color = FolioColors.Blue,
                    trackColor = Color.White.copy(alpha = .16f),
                    strokeWidth = 3.dp,
                )
            }
        }
    }
}

/** iOS system colours, one per section, so a package's tile says what kind of thing it is before you read it. */
private fun sectionColor(section: com.mccal.folio.market.Section?): Color = when (section) {
    com.mccal.folio.market.Section.THEMES -> FolioColors.Indigo
    com.mccal.folio.market.Section.TWEAKS -> FolioColors.Blue
    com.mccal.folio.market.Section.LAYOUTS -> FolioColors.Green
    com.mccal.folio.market.Section.WALLPAPERS -> FolioColors.Orange
    com.mccal.folio.market.Section.SCRIPTS -> FolioColors.Pink
    null -> FolioColors.Gray
}

/**
 * What a package page shows where its pictures would be. A gap reads as something that failed to load, so Folio says
 * it plainly - and the line is aimed at whoever published the package as much as at the person reading it.
 */
@Composable
private fun NoScreenshots() {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = .05f)).padding(16.dp).testTag("package-no-screenshots"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SadFolio(46.dp)
        Spacer(Modifier.width(14.dp))
        Column {
            Text(stringResource(R.string.no_screenshots), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(
                stringResource(R.string.its_publisher_hasn_t_shown_what_it_looks),
                color = Color.White.copy(alpha = .55f), fontSize = 13.sp,
            )
        }
    }
}

/** Folio's mark, a little sad: the same page with a folded corner, drawn with a small frown. */
@Composable
private fun SadFolio(size: androidx.compose.ui.unit.Dp) {
    val face = Color.White.copy(alpha = .5f)
    androidx.compose.foundation.Canvas(Modifier.size(size)) {
        val w = this.size.width
        val u = w / 54f
        drawRoundRect(
            color = Color.White.copy(alpha = .10f),
            topLeft = androidx.compose.ui.geometry.Offset(7 * u, 4 * u),
            size = androidx.compose.ui.geometry.Size(40 * u, 46 * u),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(9 * u),
        )
        drawCircle(face, radius = 2.6f * u, center = androidx.compose.ui.geometry.Offset(21 * u, 26 * u))
        drawCircle(face, radius = 2.6f * u, center = androidx.compose.ui.geometry.Offset(33 * u, 26 * u))
        // A frown: an arc opening upwards, which is the same curve as a smile turned over.
        drawArc(
            color = face,
            startAngle = 200f, sweepAngle = 140f, useCenter = false,
            topLeft = androidx.compose.ui.geometry.Offset(20 * u, 36 * u),
            size = androidx.compose.ui.geometry.Size(14 * u, 10 * u),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.4f * u, cap = androidx.compose.ui.graphics.StrokeCap.Round),
        )
    }
}

/** Shares a `folio://package/<id>` link, which opens the package on another phone that has Folio. */
private fun share(context: android.content.Context, entry: IndexPackage) {
    val name = entry.manifest?.name?.english ?: entry.id
    runCatching {
        context.startActivity(
            android.content.Intent.createChooser(
                android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(android.content.Intent.EXTRA_TEXT, context.getString(R.string.text_1_s_for_folio_folio_package_2_s, name, entry.id)),
                context.getString(R.string.share_1_s, name),
            ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

/**
 * Opens the source's issue form with the package already filled in, so a report carries what a maintainer needs:
 * which package, which version, and the checksum of the file that was installed.
 */
private fun report(context: android.content.Context, issuesUrl: String?, entry: IndexPackage) {
    val url = issuesUrl ?: "https://github.com/McCal-Codes/folio/issues/new"
    val name = entry.manifest?.name?.english ?: entry.id
    val body = buildString {
        append("Package: ").append(entry.id).append('\n')
        append("Version: ").append(entry.version).append('\n')
        entry.sha256?.let { append("Checksum: ").append(it).append('\n') }
        entry.provenance?.let { append("Built from: ").append(it.repo).append(" @ ").append(it.commit).append('\n') }
        append("\nWhat's wrong:\n")
    }
    val full = url + (if ('?' in url) "&" else "?") +
        "title=" + android.net.Uri.encode("Report: $name") + "&body=" + android.net.Uri.encode(body)
    runCatching {
        context.startActivity(
            android.content.Intent(android.content.Intent.ACTION_VIEW, full.toUri())
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

/** A `folio://` link someone shared. Anything else is ignored rather than guessed at. */
internal sealed interface MarketLink {
    data class Package(val id: String) : MarketLink
    data class Source(val url: String) : MarketLink

    // A supporter's code is a link too, but it belongs to Settings rather than the store: see RedeemActivity.

    companion object {
        /** What the Market should open, or null when this isn't a link Folio knows. */
        fun parse(uri: String?): MarketLink? {
            val text = uri?.trim() ?: return null
            if (!text.startsWith("folio://")) return null
            val rest = text.removePrefix("folio://")
            val host = rest.substringBefore('/')
            val value = rest.substringAfter('/', "").substringBefore('?').substringBefore('#')
            if (value.isEmpty()) return null
            return when (host) {
                "package" -> Package(value).takeIf { PackageManifest.ID.containsMatchIn(it.id) }
                // The url is encoded, because it carries its own slashes.
                "source" -> android.net.Uri.decode(value).let { url -> Source(url).takeIf { url.startsWith("https://") } }
                else -> null
            }
        }

        /** Where the Market is asked to go before it opens, set by the activity that received the link. */
        var pending: MarketLink? by mutableStateOf(null)
    }
}
