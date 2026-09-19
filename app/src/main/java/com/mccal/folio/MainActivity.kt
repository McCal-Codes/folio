package com.mccal.folio

import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.lifecycle.repeatOnLifecycle
import android.app.role.RoleManager
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.isImeVisible
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Bundle
import android.os.UserManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.viewModels
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.res.stringResource
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.doOnPreDraw
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.result.contract.ActivityResultContracts
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter

class MainActivity : ComponentActivity() {
    private val model: LauncherModel by viewModels()
    private lateinit var widgets: WidgetController
    internal lateinit var backups: BackupController
        private set
    internal lateinit var backgrounds: LauncherBackgroundController
        private set
    private val homeRequests = mutableIntStateOf(0)
    private val searchRequests = mutableIntStateOf(0)
    /** Opened from Android Settings (Home app gear / "Additional settings in the app"). */
    private val settingsRequests = mutableIntStateOf(0)
    private val defaultHome = mutableStateOf(false)
    private val showFirstRun = mutableStateOf(false)
    private val showWhatsNew = mutableStateOf(false)
    private val whatsNewRequested = mutableStateOf(false)
    /** A theme shared to Folio, waiting for Apply or Cancel. */
    private val sharedTheme = mutableStateOf<FolioTheme?>(null)
    private lateinit var setupExperience: SetupExperience
    private lateinit var status: DeviceStatusMonitor
    private lateinit var appearance: AppearanceStore
    private var appearanceLocationGeneration = 0
    private var appearancePermissionGeneration = -1
    private var appearanceLocationCancellation: CancellationSignal? = null
    private var timeReceiverRegistered = false
    private val timeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { appearance.refresh(systemDark()) }
    }
    private val locationPermission = activityResultRegistry.register("duo.appearance.location", this,
        ActivityResultContracts.RequestPermission(), permissionResult@{ granted ->
        if (appearancePermissionGeneration != appearanceLocationGeneration || isDestroyed) return@permissionResult
        appearancePermissionGeneration = -1
        if (granted) requestAppearanceLocation(keepPending = true)
        else finishAppearanceLocation(getString(R.string.location_permission_wasn_t_granted_using))
    })
    private var openingDiscover = false
    private var shadeSetupDialog: android.app.AlertDialog? = null
    private var returningFromShadeSettings = false
    private var shadeSetupOwnsExternalUi = false
    private var recreatingShadeSetup = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // The wallpaper theme must be chosen before the window exists (switching it later recreates the activity).
        if (usesSystemWallpaper(this)) setTheme(R.style.Theme_Duo_Wallpaper)
        super.onCreate(savedInstanceState)
        setupExperience = SetupExperience(this)
        Installs.start(this); NewApps.load(this)
        FocusScheduler.run(this)
        // USER_PRESENT is a protected system broadcast delivered to runtime receivers.
        androidx.core.content.ContextCompat.registerReceiver(this, unlockReceiver, android.content.IntentFilter(Intent.ACTION_USER_PRESENT),
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
        showFirstRun.value = setupExperience.entryDecision(SetupExperience.hadLauncherState(this)) ==
            SetupEntryDecision.SHOW
        showWhatsNew.value = savedInstanceState == null && WhatsNew.shouldShow(this, firstRun = showFirstRun.value)
        returningFromShadeSettings = savedInstanceState?.getBoolean(SHADE_SETTINGS_PENDING) == true
        val restoreShadeDialog = savedInstanceState?.getBoolean(SHADE_DIALOG_VISIBLE) == true
        appearance = AppearanceStore(this)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        widgets = WidgetController(this, model) { active ->
            LiveDiscover.setExternalResultPending(this, "main", "widget-setup", active)
        }.also { it.restore(savedInstanceState) }
        backups = BackupController(this, model, widgets) { active ->
            LiveDiscover.setExternalResultPending(this, "main", "layout-backup", active)
        }.also { it.restore() }
        backgrounds = LauncherBackgroundController(this) { active ->
            LiveDiscover.setExternalResultPending(this, "main", "launcher-background", active)
        }
        status = DeviceStatusMonitor(this).also { lifecycle.addObserver(it) }
        lifecycle.addObserver(IslandEvents.Observer(this))
        updateDefaultHome()
        if (savedInstanceState == null && intent.getStringExtra("duo_destination") == "search") searchRequests.intValue++
        if (savedInstanceState == null && opensSettings(intent)) { SoftwareUpdate.openRequested = intent.getBooleanExtra(SoftwareUpdate.EXTRA_OPEN_UPDATE, false); settingsRequests.intValue++ }
        intent.removeExtra("duo_destination")
        // A recreated activity (rotation, fold, process restart) keeps the pending alert; the launch intent is used once.
        if (savedInstanceState == null) takeSharedTheme(intent)
        else sharedTheme.value = savedInstanceState.getString(PENDING_THEME)?.let(FolioTheme::fromJson)
        setContent {
            val savedState = model.state.collectAsStateWithLifecycle().value
            val safeMode = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(SafeMode.active) }
            val solidGlass = savedState.reduceTransparency || rememberSystemHighContrast()
            val state = FocusPages.effective(if (safeMode.value) SafeMode.effective(savedState) else savedState)
                .let { if (solidGlass) it.withSolidGlass() else it }
            androidx.compose.runtime.LaunchedEffect(Unit) { kotlinx.coroutines.delay(31_000); SafeMode.markStable(this@MainActivity) }
            val safeAcknowledged = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
            if (safeMode.value && !safeAcknowledged.value) AlertDialog(onDismissRequest = {},
                title = { androidx.compose.material3.Text(getString(R.string.folio_started_in_safe_mode)) },
                text = { androidx.compose.material3.Text(stringResource(R.string.folio_closed_unexpectedly_twice_so_opt)) },
                confirmButton = { androidx.compose.material3.TextButton(onClick = { SafeMode.exit(this@MainActivity); safeMode.value = false }) {
                    androidx.compose.material3.Text(getString(R.string.restart_normally)) } },
                dismissButton = { androidx.compose.material3.TextButton(onClick = { safeAcknowledged.value = true }) {
                    androidx.compose.material3.Text(getString(R.string.continue_in_safe_mode)) } })
            val deviceStatus = ScreenshotMode.status(status.state.collectAsStateWithLifecycle().value, ScreenshotMode.on.collectAsStateWithLifecycle().value)
            // Folio shows its own status in the rail, so hide Android's status bar on Home (it
            // stays in apps, and a swipe from the very top edge reveals it briefly).
            androidx.compose.runtime.LaunchedEffect(state.verticalStatus) {
                androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
                    systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    if (state.verticalStatus) hide(androidx.core.view.WindowInsetsCompat.Type.statusBars())
                    else show(androidx.core.view.WindowInsetsCompat.Type.statusBars())
                }
            }
            val overlayOpen = topPanel.value != null || spotlightVisible.value || LauncherSheetsOpen.intValue > 0 ||
                // Only a Lock Cover that's actually drawn blurs Home (turning the setting off mid-way must not leave a blur).
                (lockCoverVisible.value && state.lockCover)
            MotionSpeed.current = state.motionSpeed
            // The trail for bug reports: what was open, and a heartbeat while Home is showing.
            val overlayName = listOfNotNull(topPanel.value?.name, getString(R.string.spotlight).takeIf { spotlightVisible.value },
                "sheet".takeIf { LauncherSheetsOpen.intValue > 0 }, getString(R.string.lock_cover).takeIf { lockCoverVisible.value && state.lockCover })
                .joinToString(" + ").ifEmpty { null }
            androidx.compose.runtime.LaunchedEffect(overlayName) { Diagnostics.event(overlayName?.let { "Open: $it" } ?: getString(R.string.nothing_open_over_home)) }
            val regular = androidx.compose.ui.platform.LocalConfiguration.current.fitsRegularHomeLayout()
            androidx.compose.runtime.LaunchedEffect(regular) { Diagnostics.event(if (regular) getString(R.string.unfolded_layout) else getString(R.string.folded_layout)) }
            androidx.compose.runtime.LaunchedEffect(Unit) {
                lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
                    while (true) { kotlinx.coroutines.delay(30_000); Diagnostics.checkpoint(this@MainActivity, visible = true) }
                }
            }
            val overlayProgress by rememberSettlingProgress(if (overlayOpen) 1f else 0f,
                MotionSpeed.spring(.86f, androidx.compose.animation.core.Spring.StiffnessMediumLow))
            val backdropBlurPx = with(androidx.compose.ui.platform.LocalDensity.current) { (state.panelBlur * 32).dp.toPx() }
            val backdropBlur = androidx.compose.runtime.remember(backdropBlurPx) {
                androidx.compose.ui.graphics.BlurEffect(backdropBlurPx, backdropBlurPx, androidx.compose.ui.graphics.TileMode.Clamp)
            }
            DuoTheme(appearance.state.dark) { val notificationItems = IslandListenerService.notifications.collectAsStateWithLifecycle().value
            val installSessions = Installs.active.collectAsStateWithLifecycle().value
            val installProgress = androidx.compose.runtime.remember(installSessions) { installSessions.values.associate { it.packageName to it.progress } }
            val newApps = NewApps.packages.collectAsStateWithLifecycle().value
            // The Discover host is a not-touchable window stacked above the keyboard; Android drops every key
            // tap "due to occlusion" while it exists. Remove it whenever a keyboard can be up.
            @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
            val imeUp = WindowInsets.isImeVisible ||
                WindowInsets.imeAnimationTarget.getBottom(androidx.compose.ui.platform.LocalDensity.current) > 0
            val typing = spotlightVisible.value || imeUp
            androidx.compose.runtime.DisposableEffect(typing) {
                if (typing) LiveDiscover.setExternalResultPending(this@MainActivity, "main", "keyboard", true)
                onDispose { if (typing) LiveDiscover.setExternalResultPending(this@MainActivity, "main", "keyboard", false) }
            }
            val clearedBadges = BadgeClears.cleared.collectAsStateWithLifecycle().value
            // Recent-app dots (Beta): refreshed every minute while Home is showing.
            val recentPackages by androidx.compose.runtime.produceState(emptySet<String>(), state.dockRecentDots) {
                if (!state.dockRecentDots) { value = emptySet(); return@produceState }
                lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
                    while (true) {
                        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { RecentUse.packages(this@MainActivity) }
                        kotlinx.coroutines.delay(60_000)
                    }
                }
            }
            val iconsAreDark by androidx.compose.runtime.produceState<Boolean?>(null, state.apps) {
                value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    iconsMostlyDark(state.apps.filter { LiveIcons.kind(this@MainActivity, it.packageName) == null && it.shortcutId == null }.map { it.icon })
                }
            }
            val badgeCounts = androidx.compose.runtime.remember(notificationItems, clearedBadges) { BadgeClears.counts(notificationItems, clearedBadges) }
            androidx.compose.runtime.SideEffect { latestNotifications = notificationItems }
            val wallpaperTone = rememberWallpaperTone(state.systemWallpaper)
            // Re-read on every resume so turning Remove animations on/off applies without restarting.
            val reduceMotionState = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(reduceMotionEnabled(this@MainActivity)) }
            androidx.lifecycle.compose.LifecycleResumeEffect(Unit) { reduceMotionState.value = reduceMotionEnabled(this@MainActivity); onPauseOrDispose { } }
            val reduceMotion = reduceMotionState.value
            val iconTint = if (state.iconTintFromWallpaper) wallpaperTone.primary?.let(::vividTint)?.toLong()?.and(0xFFFFFFFFL) ?: state.iconTint else state.iconTint
            androidx.compose.runtime.CompositionLocalProvider(
                LocalWallpaperTone provides wallpaperTone,
                LocalGlassLook provides GlassLook(state.widgetGlass, state.glassOutline),
                LocalSolidGlass provides solidGlass,
                LocalFolderLook provides FolderLook(state.folderColumns, state.folderBackground),
                LocalLabelSize provides state.labelSize,
                LocalReduceMotion provides reduceMotion,
                LocalHinge provides rememberHinge(this@MainActivity),
                // Tablets and desktop windows draw Folio proportionally larger instead of a phone-sized layout lost in a
                // big window; phones and foldables stay at exactly the system density (see uiScale).
                androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.platform.LocalDensity.current.let { d ->
                    val config = androidx.compose.ui.platform.LocalConfiguration.current
                    val scale = uiScale(config.screenWidthDp.toFloat(), config.screenHeightDp.toFloat(), config.classScale)
                    if (scale == 1f) d else androidx.compose.ui.unit.Density(d.density * scale, d.fontScale)
                },
                LocalTintOptions provides androidx.compose.ui.platform.LocalConfiguration.current.let { config ->
                    val screen = screenFor(config.fitsRegularHomeLayout())
                    TintOptions(FeatureScopes.on(state.featureScopes, "tintNotifications", state.tintNotifications, screen),
                        FeatureScopes.on(state.featureScopes, "tintMedia", state.tintMedia, screen),
                        FeatureScopes.on(state.featureScopes, "notificationAppRow", state.notificationAppRow, screen))
                },
                androidx.compose.ui.platform.LocalHapticFeedback provides (if (state.haptics) androidx.compose.ui.platform.LocalHapticFeedback.current else NoHaptics),
                LocalIconLook provides IconLook(state.iconStyle, androidx.compose.ui.graphics.Color(iconTint), state.iconShape, state.iconPack, state.badgeStyle, state.badgeColor, state.liveIcons, state.liveIconLook, state.badgeLook, state.badgeSize),
                LocalFocusLock provides FocusPages.lockingFocus(savedState)?.let { FocusLock(it, savedState.layout.pageCount) },
                LocalIconsAreDark provides iconsAreDark,
                LocalRecentPackages provides recentPackages,
                LocalBadgeCounts provides badgeCounts, LocalInstallProgress provides installProgress, LocalNewApps provides newApps, LocalFolderColors provides state.folderColors) { FoldTransitionHost(state.foldEffect && !reduceMotion, state.foldIntensity, state.stayAwakeOnFold, state.foldSnapshot, state.haptics) {
                // The launcher blurs behind every overlay with the same spring the overlay uses.
                androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize()
                    .graphicsLayer {
                    val p = overlayProgress
                    // Fixed radius while any overlay is showing: a constant blur is cached by the RenderThread,
                    // whereas animating the radius re-blurred the whole Home every frame (~14ms of GPU per
                    // frame). The overlay's scrim fades in over it, which hides the switch.
                    renderEffect = if (p > .02f && backdropBlurPx >= 2f && LauncherPagesOpen.intValue == 0 && SettingsPeek.value == null) backdropBlur else null
                }) {
                LauncherScreen(state, model, widgets, homeRequests.intValue,
                    onLaunch = { launchApp(it) }, onMakeDefault = ::makeDefault, onAppInfo = ::appInfo,
                    isDefaultHome = defaultHome.value, deviceStatus = deviceStatus, onStatusMode = ::setStatusMode, onWallpaperPreview = ::previewWallpaper,
                    onDiscover = ::openDiscover, searchRequests = searchRequests.intValue, settingsRequests = settingsRequests.intValue,
                    onLaunchFrom = ::launchApp, onGoogleSearch = ::openGoogleSearch,
                    appearance = appearance.state,
                    onAppearanceMode = { cancelAppearanceLocation(); appearance.setMode(it, systemDark()) },
                    onAppearanceManual = { place, lat, lon -> cancelAppearanceLocation(); appearance.setManual(place, lat, lon, systemDark()) },
                    onAppearanceDeviceLocation = ::useAppearanceLocation,
                    onAppearanceClear = { cancelAppearanceLocation(); appearance.clearLocation(systemDark()) },
                    showFirstRun = showFirstRun.value,
                    onFinishFirstRun = ::finishFirstRun,
                    onShadeSetup = ::showShadeSetup, onShowWelcome = { showFirstRun.value = true }, onShowWhatsNew = { whatsNewRequested.value = true })
                }
                StandByOverlay(rememberHalfOpenPose(this@MainActivity), state.standBy, blocked = overlayOpen, status = deviceStatus)
                LockCover(lockCoverVisible.value && state.lockCover) { lockCoverVisible.value = false }
                AudioDeviceCard("BLUETOOTH" !in state.islandEventsOff, blocked = overlayOpen)
                SetupReminderCard(defaultHome.value, blocked = overlayOpen || showFirstRun.value || !defaultHome.value, onMakeDefault = ::makeDefault,
                    onShadeSetup = ::showShadeSetup) { SettingsLink.page = CustomizationPage.PERMISSIONS; settingsRequests.intValue++ }
                sharedTheme.value?.let { theme ->
                    AlertDialog(onDismissRequest = { sharedTheme.value = null },
                        title = { androidx.compose.material3.Text(stringResource(R.string.apply_1, theme.name)) },
                        text = { androidx.compose.material3.Text(stringResource(R.string.this_changes_icons_badges_glass_text_on)) },
                        confirmButton = { androidx.compose.material3.TextButton(onClick = { model.applyTheme(theme); sharedTheme.value = null }) {
                            androidx.compose.material3.Text(getString(R.string.apply)) } },
                        dismissButton = { androidx.compose.material3.TextButton(onClick = { sharedTheme.value = null }) {
                            androidx.compose.material3.Text(getString(R.string.cancel)) } })
                }
                if (showWhatsNew.value || whatsNewRequested.value) WhatsNewSheet { showWhatsNew.value = false; whatsNewRequested.value = false; WhatsNew.markSeen(this@MainActivity) }
                // With live activities in the side rail, the camera island on Home keeps only its brief events.
                if (state.island) CutoutIsland(IslandListenerService.activity.collectAsStateWithLifecycle().value
                    ?.takeUnless { it is IslandActivity.Call && "CALL" in state.islandEventsOff }
                    ?.takeUnless { state.railActivities && state.verticalStatus && !overlayOpen }, state.islandEventsOff + "BLUETOOTH") {
                    IslandListenerService.open(this@MainActivity, it)
                }
                TopPanels(topPanel.value, { overlayProgress }, deviceStatus, onClose = { topPanel.value = null },
                    onSystemPanel = { openAndroidShade(it) }, showClock = state.notificationClock, grouped = state.groupNotifications,
                    ccControls = state.ccControls, onCcControls = model::setCcControls,
                    ccSize = state.ccSize, ccCentered = state.ccCentered, ncSplit = state.ncSplit,
                    focusModes = state.focusModes, activeFocus = state.activeFocus, onFocus = model::setFocus)
                SpotlightOverlay(spotlightVisible.value, { overlayProgress }, state, onClose = { spotlightVisible.value = false },
                    onLaunch = { launchApp(it) })
                // Last, so the corners sit above everything in Home's window.
                if (state.roundedCorners) RoundedScreenCorners(state.cornerRadius.dp)
            } } }
        }
        FoldRenderExperiment.attach(this)
        // Reassert the token after recreation (and after process restoration, where the
        // in-memory owner set is empty) before any external UI can uncover Discover.
        if (returningFromShadeSettings || restoreShadeDialog) ownShadeSetupExternally()
        if (restoreShadeDialog) window.decorView.post { if (!isFinishing && !isDestroyed) showShadeSetup() }
    }

    override fun onStart() {
        super.onStart(); widgets.host.startListening()
        if (!timeReceiverRegistered) {
            ContextCompat.registerReceiver(this, timeReceiver, IntentFilter().apply {
                addAction(Intent.ACTION_TIME_TICK); addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED); addAction(Intent.ACTION_DATE_CHANGED)
            }, ContextCompat.RECEIVER_NOT_EXPORTED)
            timeReceiverRegistered = true
        }
        appearance.refresh(systemDark())
    }
    override fun onStop() {
        closeOverlays() // never come back to a blurred Home
        if (timeReceiverRegistered) { unregisterReceiver(timeReceiver); timeReceiverRegistered = false }
        widgets.host.stopListening(); super.onStop()
    }
    override fun onDestroy() {
        runCatching { unregisterReceiver(unlockReceiver) }
        recreatingShadeSetup = isChangingConfigurations
        shadeSetupDialog?.dismiss()
        if (!isChangingConfigurations) releaseShadeSetupOwnership()
        cancelAppearanceLocation()
        super.onDestroy()
    }
    override fun onPause() {
        super.onPause()
        FolioForeground.visible.value = false
        Diagnostics.event(getString(R.string.home_hidden))
        Diagnostics.checkpoint(this, visible = false)
    }
    override fun onResume() {
        super.onResume()
        SoftwareUpdate.afterUpdate(this)
        SoftwareUpdate.startCheckIfDue(this)
        FolioForeground.visible.value = true
        Diagnostics.event("Home shown (${Diagnostics.screenSummary(this).substringBefore(',')})")
        Diagnostics.checkpoint(this, visible = true)
        FolioActions.home = java.lang.ref.WeakReference(this)
        model.syncFocus()
        // Unlock arrived just before Home resumed: show the cover now.
        if (unlockedAt > 0 && android.os.SystemClock.uptimeMillis() - unlockedAt < 2_000 && model.state.value.lockCover) lockCoverVisible.value = true
        unlockedAt = 0L
        if (SpotlightRequest.consume()) openSpotlight()
        FolioActions.pendingPanel?.let { FolioActions.pendingPanel = null; showPanel(it) }
        if (returningFromShadeSettings) {
            returningFromShadeSettings = false
            releaseShadeSetupOwnership()
        }
        val discover = DiscoverSession.host.get()
        if (discover != null) window.decorView.doOnPreDraw {
            it.postOnAnimation { if (DiscoverSession.host.get() === discover) DiscoverSession.dismiss() }
        }
        model.refresh(); appearance.refresh(systemDark()); updateDefaultHome()
        window.decorView.post {
            if (!isFinishing && !isDestroyed && !LiveDiscover.viewport.isEmpty)
                LiveDiscover.prepare(this, LiveDiscover.viewport, LiveDiscover.pageWidth)
        }
    }

    /** Folio's own iOS-style panels on Home; the Android shade when that setting is off. */
    internal val topPanel = androidx.compose.runtime.mutableStateOf<ShadePanel?>(null)
    /** The notifications on screen now, for Clear Badge. */
    internal var latestNotifications: List<NotificationItem> = emptyList()
    internal val spotlightVisible = androidx.compose.runtime.mutableStateOf(false)
    /** Lock Cover: shown when the phone is unlocked straight to Home. */
    private val lockCoverVisible = androidx.compose.runtime.mutableStateOf(false)
    private var unlockedAt = 0L
    private val unlockReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context, intent: Intent) {
            if (!model.state.value.lockCover) return
            unlockedAt = android.os.SystemClock.uptimeMillis()
            if (FolioForeground.visible.value) lockCoverVisible.value = true
        }
    }
    /** Opens Spotlight, Notification Center or Control Center (Folio's own panels when enabled). */
    internal fun showPanel(panel: ShadePanel) { if (panel == ShadePanel.SEARCH) openSpotlight() else openSystemShade(panel) }

    internal fun openSpotlight() { topPanel.value = null; spotlightVisible.value = true }
    private fun closeOverlays() { topPanel.value = null; spotlightVisible.value = false }

    /**
     * The Home button is the way out of anything. Setup is shown again next time (or from Settings › Help › Show Welcome
     * Again), so an overlay can never leave Home stuck behind it with no way back.
     */
    private fun closeEverything() { closeOverlays(); showFirstRun.value = false; lockCoverVisible.value = false }

    internal fun openSystemShade(panel: ShadePanel) {
        if (model.state.value.folioPanels) topPanel.value = panel else openAndroidShade(panel)
    }

    internal fun openAndroidShade(panel: ShadePanel) {
        when (SystemShadeAccessibilityService.open(this, panel)) {
            ShadeOpenResult.OPENED -> Unit
            ShadeOpenResult.SERVICE_DISABLED -> showShadeSetup()
            ShadeOpenResult.SERVICE_STARTING -> IslandEvents.notice(this, getString(R.string.folio_gestures_are_starting_swipe_down_a))
            ShadeOpenResult.ACTION_REJECTED -> IslandEvents.notice(this, getString(R.string.android_couldn_t_open_the_system_panel))
        }
    }

    private fun showShadeSetup() {
        if (shadeSetupDialog?.isShowing == true) return
        ownShadeSetupExternally()
        shadeSetupDialog = android.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.turn_on_folio_gestures))
            .setMessage("Android asks you to turn this on yourself:\n\n" +
                "1. Tap Open Settings, find “Folio gestures & overlays” (often under Installed apps) and turn it on.\n" +
                "2. If it's greyed out, or you see “App was denied access” or “Restricted setting”, tap App Info below, open the ⋮ menu " +
                "(top right), choose “Allow restricted settings” and confirm, then come back and turn it on. Android does this for apps " +
                "installed from a browser or file; it's a one-time step.\n\n" +
                getString(R.string.folio_uses_it_to_open_notification_cente) +
                getString(R.string.it_can_t_read_what_s_on_your_screen))
            .setNegativeButton(getString(R.string.not_now), null)
            .setNeutralButton(getString(R.string.app_info)) { _, _ ->
                returningFromShadeSettings = true
                runCatching { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:$packageName"))) }
                    .onFailure { returningFromShadeSettings = false; releaseShadeSetupOwnership() }
            }
            .setPositiveButton(getString(R.string.open_settings)) { _, _ ->
                returningFromShadeSettings = true
                val opened = runCatching { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }.isSuccess
                if (!opened) {
                    returningFromShadeSettings = false
                    releaseShadeSetupOwnership()
                    IslandEvents.notice(this, getString(R.string.accessibility_settings_are_unavailable))
                }
            }
            .also { dialog -> dialog.setOnDismissListener {
                shadeSetupDialog = null
                if (!returningFromShadeSettings && !recreatingShadeSetup) releaseShadeSetupOwnership()
            } }
            .show()
    }

    private fun finishFirstRun() {
        setupExperience.finish()
        showFirstRun.value = false
    }

    private fun ownShadeSetupExternally() {
        if (shadeSetupOwnsExternalUi) return
        shadeSetupOwnsExternalUi = true
        LiveDiscover.setExternalResultPending(this, "main", "shade-service-setup", true)
    }

    private fun releaseShadeSetupOwnership() {
        if (!shadeSetupOwnsExternalUi) return
        shadeSetupOwnsExternalUi = false
        LiveDiscover.setExternalResultPending(this, "main", "shade-service-setup", false)
    }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) setStatusMode(model.state.value.verticalStatus)
    }
    override fun onSaveInstanceState(outState: Bundle) {
        widgets.save(outState)
        outState.putBoolean(SHADE_DIALOG_VISIBLE, shadeSetupDialog?.isShowing == true && !returningFromShadeSettings)
        outState.putBoolean(SHADE_SETTINGS_PENDING, returningFromShadeSettings)
        sharedTheme.value?.let { outState.putString(PENDING_THEME, it.toJson().toString()) }
        super.onSaveInstanceState(outState)
    }
    /** Android's "Home app settings" gear, or Folio's own app icon (the FolioSettingsApp alias). Until Folio is the
     * Home app, its icon opens Home instead, as a preview you can leave with Back or the Home gesture. */
    private fun opensSettings(intent: Intent) = intent.action == Intent.ACTION_APPLICATION_PREFERENCES ||
        (fromAppIcon(intent) && defaultHome.value)
    private fun fromAppIcon(intent: Intent) = intent.component?.className?.startsWith("$FOLIO_CLASSES.${AppIconChoice.ALIAS_PREFIX}") == true

    /** Any app can start Home with this extra, so it's parsed again and only ever applied after the user taps Apply. */
    private fun takeSharedTheme(intent: Intent?) {
        val raw = intent?.getStringExtra(ThemeImportActivity.EXTRA_THEME) ?: return
        intent.removeExtra(ThemeImportActivity.EXTRA_THEME)
        sharedTheme.value = raw.takeIf { it.length <= ThemeImportActivity.MAX_BYTES }?.let(FolioTheme::fromJson)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        setIntent(intent)
        takeSharedTheme(intent)
        FoldRenderExperiment.onNewIntent(this, intent)
        updateDefaultHome()
        if (intent.getStringExtra("duo_destination") == "search") searchRequests.intValue++
        if (opensSettings(intent)) { SoftwareUpdate.openRequested = intent.getBooleanExtra(SoftwareUpdate.EXTRA_OPEN_UPDATE, false); settingsRequests.intValue++ }
        else if (intent.hasCategory(Intent.CATEGORY_HOME) || fromAppIcon(intent) || intent.getStringExtra("duo_destination") == "home") {
            closeEverything(); homeRequests.intValue++
        }
        intent.removeExtra("duo_destination")
    }

    @Deprecated("Widget configuration uses the platform host request-code API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (!widgets.onActivityResult(requestCode, resultCode)) super.onActivityResult(requestCode, resultCode, data)
    }

    private fun launchApp(app: AppEntry, bounds: android.graphics.Rect? = null) {
        RecentApps.record(this, app.id)
        NewApps.opened(this, app.packageName)
        try {
            val user = getSystemService(UserManager::class.java).getUserForSerialNumber(app.userSerial)
                ?: throw IllegalStateException(getString(R.string.profile_is_unavailable))
            val launcherApps = getSystemService(LauncherApps::class.java)
            val shortcut = app.shortcutId
            if (shortcut != null) launcherApps.startShortcut(app.packageName, shortcut, screenBounds(bounds), launchOptions(bounds), user)
            else launcherApps.startMainActivity(app.component, user, screenBounds(bounds), launchOptions(bounds))
        } catch (_: Exception) { IslandEvents.notice(this, getString(R.string.app_is_unavailable, app.label), app.icon); model.refresh() }
    }

    private fun screenBounds(bounds: android.graphics.Rect?): android.graphics.Rect? = bounds?.takeUnless { it.isEmpty }?.let {
        val location = IntArray(2); window.decorView.getLocationOnScreen(location)
        android.graphics.Rect(it).apply { offset(location[0], location[1]) }
    }
    private fun launchOptions(bounds: android.graphics.Rect?): Bundle? = bounds?.takeUnless { it.isEmpty }?.let {
        android.app.ActivityOptions.makeScaleUpAnimation(window.decorView, it.left, it.top, it.width(), it.height()).toBundle()
    }
    private fun openGoogleSearch(bounds: android.graphics.Rect?): Boolean = try {
        startActivity(googleSearchIntent().apply { sourceBounds = screenBounds(bounds) }, launchOptions(bounds))
        true
    } catch (_: android.content.ActivityNotFoundException) { false }
      catch (_: SecurityException) { false }

    private fun openDiscover() {
        if (DiscoverEmbedding.supported(this)) {
            if (openingDiscover) return
            openingDiscover = true
            // A very quick reopen can arrive before the previous return's deferred cleanup.
            // Finish that session before taking a new image, so it cannot invalidate this copy.
            DiscoverSession.dismiss()
            DiscoverMotion.capture(this) {
                openingDiscover = false
                if (!lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) return@capture
                DiscoverSession.apps = model.state.value.apps
                startActivity(Intent(this, DiscoverActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or Intent.FLAG_ACTIVITY_NO_ANIMATION))
            }
        }
        else showDiscoverFallback()
    }

    private fun showDiscoverFallback() {
        val google = packageManager.getLaunchIntentForPackage(DiscoverClient.GOOGLE_PACKAGE)
        android.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.discover_isn_t_available_here))
            .setMessage(getString(R.string.folio_can_t_place_the_discover_feed_besi))
            .setNegativeButton(getString(R.string.stay_on_home), null)
            .apply {
                if (google != null) setPositiveButton(getString(R.string.open_google)) { _, _ ->
                    runCatching { startActivity(google) }
                }
            }
            .show()
    }

    private fun makeDefault() {
        // Samsung may immediately cancel a role request; its Home settings is reliable.
        try { startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) }
        catch (_: android.content.ActivityNotFoundException) {
            val role = getSystemService(RoleManager::class.java)
            if (role.isRoleAvailable(RoleManager.ROLE_HOME)) startActivity(role.createRequestRoleIntent(RoleManager.ROLE_HOME))
            else startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
        }
    }

    private fun updateDefaultHome() {
        defaultHome.value = getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_HOME)
    }

    private fun systemDark() = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
        android.content.res.Configuration.UI_MODE_NIGHT_YES

    private fun useAppearanceLocation() {
        cancelAppearanceLocation()
        appearance.locationStatus(getString(R.string.waiting_for_approximate_device_location))
        LiveDiscover.setExternalResultPending(this, "main", "appearance-location", true)
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED)
            requestAppearanceLocation(keepPending = true)
        else {
            appearancePermissionGeneration = appearanceLocationGeneration
            runCatching { locationPermission.launch(android.Manifest.permission.ACCESS_COARSE_LOCATION) }
                .onFailure { finishAppearanceLocation(getString(R.string.location_permission_couldn_t_be_requeste)) }
        }
    }

    private fun requestAppearanceLocation(keepPending: Boolean = false) {
        if (!keepPending) LiveDiscover.setExternalResultPending(this, "main", "appearance-location", true)
        val generation = ++appearanceLocationGeneration
        val manager = getSystemService(LocationManager::class.java)
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            finishAppearanceLocation(getString(R.string.location_permission_isn_t_available_usin)); return
        }
        val cached = runCatching { manager.getProviders(true).mapNotNull { manager.getLastKnownLocation(it) }
            .maxByOrNull { it.time }?.takeIf { System.currentTimeMillis() - it.time <= 15 * 60_000 } }.getOrNull()
        if (cached != null) {
            if (generation == appearanceLocationGeneration) appearance.setDeviceLocation(cached.latitude, cached.longitude, systemDark())
            finishAppearanceLocation(null); return
        }
        val provider = runCatching { when {
            manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            manager.isProviderEnabled(LocationManager.PASSIVE_PROVIDER) -> LocationManager.PASSIVE_PROVIDER
            else -> null
        } }.getOrNull() ?: run { finishAppearanceLocation(getString(R.string.no_approximate_location_provider_is_avai)); return }
        val cancellation = CancellationSignal()
        appearanceLocationCancellation = cancellation
        window.decorView.postDelayed({
            if (generation == appearanceLocationGeneration && appearanceLocationCancellation === cancellation) {
                cancellation.cancel(); finishAppearanceLocation(getString(R.string.location_timed_out_using_the_system_them))
            }
        }, 10_000)
        runCatching { manager.getCurrentLocation(provider, cancellation, ContextCompat.getMainExecutor(this)) { location ->
            if (generation != appearanceLocationGeneration || isDestroyed) return@getCurrentLocation
            if (location != null) appearance.setDeviceLocation(location.latitude, location.longitude, systemDark())
            finishAppearanceLocation(if (location == null) getString(R.string.location_is_unavailable_using_the_system) else null)
        } }.onFailure { finishAppearanceLocation(getString(R.string.location_is_unavailable_using_the_system)) }
    }

    private fun cancelAppearanceLocation() {
        appearanceLocationGeneration++
        appearancePermissionGeneration = -1
        appearanceLocationCancellation?.cancel(); appearanceLocationCancellation = null
        if (::appearance.isInitialized) appearance.locationStatus(null)
        LiveDiscover.setExternalResultPending(this, "main", "appearance-location", false)
    }

    private fun finishAppearanceLocation(message: String?) {
        appearanceLocationGeneration++
        appearancePermissionGeneration = -1
        appearanceLocationCancellation = null
        appearance.locationStatus(message)
        LiveDiscover.setExternalResultPending(this, "main", "appearance-location", false)
    }

    /** The window's size in dp, to tell a fold or a resize from a change that leaves the layout alone. */
    private var lastWindowSize: Pair<Int, Int>? = null
        get() = field ?: (resources.configuration.screenWidthDp to resources.configuration.screenHeightDp).also { field = it }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        // A panel is drawn for the screen it opened on. Folding, unfolding or being resized leaves it laid out for a
        // screen that is no longer there, and on some phones it can't be dismissed at all (reported on r/GalaxyFold
        // from a Fold8 Ultra), so a real size change closes it and Home comes back clean.
        val size = newConfig.screenWidthDp to newConfig.screenHeightDp
        val was = lastWindowSize
        lastWindowSize = size
        // Only the panel: Spotlight lays itself out for the new screen, and closing it would throw away a search
        // somebody is halfway through typing.
        if (windowChangedShape(was, size)) topPanel.value = null
        // Folding, Display size, Smallest width or split screen can bring Android's status bar back over the Side Bar
        // status; hide it again once the new layout is in place.
        window.decorView.post { setStatusMode(model.state.value.verticalStatus) }
    }

    private fun setStatusMode(vertical: Boolean) {
        LiveDiscover.host.get()?.statusMode(vertical)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (vertical) controller.hide(WindowInsetsCompat.Type.statusBars()) else controller.show(WindowInsetsCompat.Type.statusBars())
    }

    private fun previewWallpaper() {
        try {
            startActivity(Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
                .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, ComponentName(this, DuneWallpaperService::class.java)))
        } catch (_: android.content.ActivityNotFoundException) {
            IslandEvents.notice(this, getString(R.string.the_system_wallpaper_preview_is_unavaila))
        }
    }

    private fun appInfo(app: AppEntry) {
        try {
            val user = getSystemService(UserManager::class.java).getUserForSerialNumber(app.userSerial)
                ?: throw IllegalStateException(getString(R.string.profile_is_unavailable))
            getSystemService(LauncherApps::class.java).startAppDetailsActivity(app.component, user, null, null)
        } catch (_: Exception) {
            IslandEvents.notice(this, getString(R.string.app_is_unavailable, app.label), app.icon)
            model.refresh()
        }
    }

    private companion object {
        const val SHADE_DIALOG_VISIBLE = "duo.shade.dialog_visible"
        const val SHADE_SETTINGS_PENDING = "duo.shade.settings_pending"
        const val PENDING_THEME = "folio.theme.pending"
    }
}
