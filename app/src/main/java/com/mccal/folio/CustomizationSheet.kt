package com.mccal.folio

import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

internal enum class CustomizationPage { OVERVIEW, SETUP, WALLPAPER, HOME, STATUS, GESTURES, FOLD, BACKUP, HELP, SIDE_KEY, LOCK, CREDITS, TWEAKS, TWEAK, ADVANCED, NOTIFICATIONS, SEARCH, TODAY, ISLAND, PERMISSIONS, FOCUS, FOCUS_MODE, THEMES, COMING_SOON }

@Composable
internal fun CustomizationSheet(state: LauncherState, initiallyWide: Boolean, model: LauncherModel,
    isDefaultHome: Boolean, page: CustomizationPage, onPage: (CustomizationPage) -> Unit,
    onMakeDefault: () -> Unit, onClose: () -> Unit, onEditPins: () -> Unit, onWidget: (Int) -> Unit,
    onAddWidget: (Int) -> Unit, onRemoveWidget: (Int) -> Unit, onWallpaperPreview: () -> Unit,
    onExportLayout: () -> Unit, onImportLayout: () -> Unit,
    appearance: AppearanceState, onAppearanceMode: (AppearanceMode) -> Unit,
    onAppearanceManual: (String, Double, Double) -> Unit, onAppearanceDeviceLocation: () -> Unit,
    onAppearanceClear: () -> Unit, backgrounds: LauncherBackgroundController, homePage: Int = 0,
    onShadeSetup: () -> Unit = {},
    onShowWelcome: () -> Unit = {},
    onShowWhatsNew: () -> Unit = {},
) {
    var wide by rememberSaveable { mutableStateOf(initiallyWide) }
    var tweakId by rememberSaveable { mutableStateOf("") }
    var focusId by rememberSaveable { mutableStateOf("") }
    var settingsQuery by rememberSaveable { mutableStateOf("") }
    val title = when (page) {
        CustomizationPage.OVERVIEW -> "Folio"
        CustomizationPage.SETUP -> "Setup Checklist"
        CustomizationPage.WALLPAPER -> "Wallpaper & Appearance"
        CustomizationPage.HOME -> "Home Screen & Dock"
        CustomizationPage.STATUS -> "Icons & Side Bar"
        CustomizationPage.GESTURES -> "Gestures & Actions"
        CustomizationPage.FOLD -> "Fold & Displays"
        CustomizationPage.BACKUP -> "Backup"
        CustomizationPage.HELP -> "Help"
        CustomizationPage.SIDE_KEY -> "Side Key"
        CustomizationPage.LOCK -> "Lock Cover"
        CustomizationPage.CREDITS -> "Credits"
        CustomizationPage.TWEAKS -> "Tweaks"
        CustomizationPage.FOCUS -> "Focus"
        CustomizationPage.THEMES -> "Themes"
        CustomizationPage.FOCUS_MODE -> state.focusModes.firstOrNull { it.id == focusId }?.name ?: "Focus"
        CustomizationPage.TWEAK -> TweakFeatures.firstOrNull { it.id == tweakId }?.name ?: "Tweak"
        CustomizationPage.ADVANCED -> "Advanced"
        CustomizationPage.NOTIFICATIONS -> "Notifications & Control Center"
        CustomizationPage.SEARCH -> "Search & App Library"
        CustomizationPage.TODAY -> "Today View"
        CustomizationPage.ISLAND -> "Dynamic Island"
        CustomizationPage.PERMISSIONS -> "Privacy & Permissions"
        CustomizationPage.COMING_SOON -> "Coming Soon"
    }
    val bodyScroll = rememberScrollState()
    LaunchedEffect(page) { bodyScroll.scrollTo(0) }
    val setupSteps = rememberSetupSteps(isDefaultHome, onMakeDefault, onShadeSetup, state.messagesApp, model::setMessagesApp, state.systemWallpaper, model::setSystemWallpaper)
    val setupLeft = setupSteps.count { it.required && !it.done }
    val onBack = { onPage(when (page) { CustomizationPage.TWEAK -> CustomizationPage.TWEAKS; CustomizationPage.FOCUS_MODE -> CustomizationPage.FOCUS; else -> CustomizationPage.OVERVIEW }) }

    // The settings list. On the phone it's the first page; in the split view it's the sidebar, with the open page highlighted.
    val overviewRows: @Composable ColumnScope.(selected: CustomizationPage?, sidebar: Boolean) -> Unit = { selected, sidebar ->
                    SheetGroup {
                        TweakRow(Icons.Rounded.Wallpaper, 0xFF32ADE6, "Wallpaper & Appearance", "customization-wallpaper",
                            if (backgrounds.previewPending) "Photo ready" else null, selected = selected == CustomizationPage.WALLPAPER, chevron = !sidebar) { onPage(CustomizationPage.WALLPAPER) }
                        MenuDivider()
                        TweakRow(Icons.Rounded.GridView, 0xFF0A84FF, "Home Screen & Dock", "customization-home", selected = selected == CustomizationPage.HOME, chevron = !sidebar) { onPage(CustomizationPage.HOME) }
                        MenuDivider()
                        TweakRow(Icons.Rounded.Today, 0xFFFF9F0A, "Today View", "customization-today", selected = selected == CustomizationPage.TODAY, chevron = !sidebar) { onPage(CustomizationPage.TODAY) }
                        MenuDivider()
                        TweakRow(Icons.Rounded.Palette, 0xFFFF375F, "Themes", "customization-themes",
                            FolioTheme.PRESETS.firstOrNull { state.looksLike(it) }?.name ?: "Custom", selected = selected == CustomizationPage.THEMES, chevron = !sidebar) { onPage(CustomizationPage.THEMES) }
                        MenuDivider()
                        TweakRow(Icons.Rounded.Apps, 0xFF5E5CE6, "Icons & Side Bar", "customization-status", selected = selected == CustomizationPage.STATUS, chevron = !sidebar) { onPage(CustomizationPage.STATUS) }
                    }
                    SheetGroup {
                        TweakRow(Icons.Rounded.Circle, 0xFF1C1C1E, "Dynamic Island", "customization-island", selected = selected == CustomizationPage.ISLAND, chevron = !sidebar) { onPage(CustomizationPage.ISLAND) }
                        MenuDivider()
                        TweakRow(Icons.Rounded.Notifications, 0xFFFF3B30, "Notifications & Control Center", "customization-notifications", selected = selected == CustomizationPage.NOTIFICATIONS, chevron = !sidebar) { onPage(CustomizationPage.NOTIFICATIONS) }
                        MenuDivider()
                        TweakRow(Icons.Rounded.DarkMode, 0xFF5E5CE6, "Focus", "customization-focus",
                            state.focusModes.firstOrNull { it.id == state.activeFocus }?.name, selected = selected == CustomizationPage.FOCUS, chevron = !sidebar) { onPage(CustomizationPage.FOCUS) }
                        MenuDivider()
                        TweakRow(Icons.Rounded.Search, 0xFF8E8E93, "Search & App Library", "customization-search", selected = selected == CustomizationPage.SEARCH, chevron = !sidebar) { onPage(CustomizationPage.SEARCH) }
                        MenuDivider()
                        TweakRow(Icons.Rounded.Gesture, 0xFF30B0C7, "Gestures & Actions", "customization-gestures", selected = selected == CustomizationPage.GESTURES, chevron = !sidebar) { onPage(CustomizationPage.GESTURES) }
                    }
                    SheetGroup {
                        TweakRow(Icons.Rounded.TouchApp, 0xFFFF9F0A, "Side Key", "customization-side-key", selected = selected == CustomizationPage.SIDE_KEY, chevron = !sidebar) { onPage(CustomizationPage.SIDE_KEY) }
                        MenuDivider()
                        TweakRow(Icons.Rounded.Lock, 0xFF30D158, "Lock Cover", "customization-lock",
                            if (state.lockCover) "On" else "Off", selected = selected == CustomizationPage.LOCK, chevron = !sidebar) { onPage(CustomizationPage.LOCK) }
                        MenuDivider()
                        TweakRow(Icons.Rounded.Devices, 0xFFFF375F, "Fold & Displays", "customization-fold", selected = selected == CustomizationPage.FOLD, chevron = !sidebar) { onPage(CustomizationPage.FOLD) }
                    }
                    SheetGroup {
                        TweakRow(Icons.Rounded.AutoAwesome, 0xFFBF5AF2, "Tweaks", "customization-tweaks",
                            "${TweakFeatures.count { it.get(state) }} on", selected = selected == CustomizationPage.TWEAKS, chevron = !sidebar) { onPage(CustomizationPage.TWEAKS) }
                    }
                    SheetGroup {
                        TweakRow(Icons.Rounded.Save, 0xFF8E8E93, "Backup", "customization-backup", selected = selected == CustomizationPage.BACKUP, chevron = !sidebar) { onPage(CustomizationPage.BACKUP) }
                        MenuDivider()
                        TweakRow(Icons.Rounded.PanTool, 0xFF0A84FF, "Privacy & Permissions", "customization-permissions", selected = selected == CustomizationPage.PERMISSIONS, chevron = !sidebar) { onPage(CustomizationPage.PERMISSIONS) }
                        MenuDivider()
                        TweakRow(Icons.Rounded.Settings, 0xFF8E8E93, "Advanced", "customization-advanced", selected = selected == CustomizationPage.ADVANCED, chevron = !sidebar) { onPage(CustomizationPage.ADVANCED) }
                    }
                    SheetGroup {
                        TweakRow(Icons.Rounded.HelpOutline, 0xFF0A84FF, "Help", "customization-help", selected = selected == CustomizationPage.HELP, chevron = !sidebar) { onPage(CustomizationPage.HELP) }
                        MenuDivider()
                        TweakRow(Icons.Rounded.NewReleases, 0xFF30D158, "What's New", "customization-whats-new", "v" + WhatsNew.currentVersion(androidx.compose.ui.platform.LocalContext.current), chevron = !sidebar) { onClose(); onShowWhatsNew() }
                        MenuDivider()
                        TweakRow(Icons.Rounded.Upcoming, 0xFF5E5CE6, "Coming Soon", "customization-coming-soon", selected = selected == CustomizationPage.COMING_SOON, chevron = !sidebar) { onPage(CustomizationPage.COMING_SOON) }
                        MenuDivider()
                        val bugContext = androidx.compose.ui.platform.LocalContext.current
                        TweakRow(Icons.Rounded.BugReport, 0xFFFF453A, "Report a Bug", "customization-report-bug", chevron = !sidebar) {
                            runCatching { bugContext.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(BugReport.url(bugContext)))) }
                        }
                        MenuDivider()
                        TweakRow(Icons.Rounded.WavingHand, 0xFFFF9F0A, "Show Welcome Again", "customization-onboarding", chevron = !sidebar) { onClose(); onShowWelcome() }
                        MenuDivider()
                        TweakRow(Icons.Rounded.Favorite, 0xFFFF453A, "Credits", "customization-credits", selected = selected == CustomizationPage.CREDITS, chevron = !sidebar) { onPage(CustomizationPage.CREDITS) }
                    }
                    Text("Report a Bug opens GitHub in your browser with your Folio version and phone filled in. Nothing is sent until you submit it.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
    }
    // Home-app actions and the setup reminder: above the list on the phone, on Folio's own page in the split view.
    val overviewActions: @Composable ColumnScope.() -> Unit = {
                    if (!isDefaultHome || state.canUndoEdit) SheetGroup {
                        if (!isDefaultHome) IosActionRow(stringResource(R.string.set_as_home_app), "default-home-settings", onClick = onMakeDefault)
                        if (!isDefaultHome && state.canUndoEdit) MenuDivider()
                        if (state.canUndoEdit) IosActionRow(stringResource(R.string.undo_last_layout_change), onClick = { model.undoEdit(); onClose() })
                    }
                    if (setupLeft > 0) CustomizationDestination(Icons.Rounded.Checklist, "Finish setting up Folio",
                        "$setupLeft step${if (setupLeft > 1) "s" else ""} left for the full experience", "customization-setup") { onPage(CustomizationPage.PERMISSIONS) }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
    // iPad Settings / One UI on the unfolded screen: sidebar and page side by side, in either orientation.
    // Regular size class (both dimensions roomy), not a device check: the inner screen in either orientation.
    val fullWidth = maxWidth
    val split = isRegularSize(maxWidth.value, maxHeight.value, androidx.compose.ui.platform.LocalConfiguration.current.classScale)
    val pageContent: @Composable ColumnScope.() -> Unit = {
            when (page) {
                CustomizationPage.OVERVIEW -> if (split) {
                    TweakBanner()
                    overviewActions()
                    MiniHomePreview(backgrounds.previewBitmap, state, 260.dp)
                } else {
                    // Like iOS Settings: the header gets out of the way while searching.
                    if (settingsQuery.isBlank()) TweakBanner()
                    SettingsSearchField(settingsQuery) { settingsQuery = it }
                    if (settingsQuery.isNotBlank()) SettingsSearchResults(settingsQuery, onOpen = { settingsQuery = ""; onPage(it) })
                    else {
                        overviewActions()
                        MiniHomePreview(backgrounds.previewBitmap, state, 176.dp)
                        overviewRows(null, false)
                        }
                }
                // The old Setup Checklist lives on in Privacy & Permissions (one list of everything Folio can use).
                CustomizationPage.SETUP -> PermissionsPage(isDefaultHome, onMakeDefault, onShadeSetup)
                CustomizationPage.COMING_SOON -> ComingSoonPage()
                CustomizationPage.WALLPAPER -> {
                    val wallpaperContext = androidx.compose.ui.platform.LocalContext.current
                    AppIconCard(onChanged = { model.refresh() })
                    SettingsCard(stringResource(R.string.background)) {
                        IosSegmented(listOf(true to stringResource(R.string.android_wallpaper), false to stringResource(R.string.folio_background)),
                            state.systemWallpaper, { system -> model.setSystemWallpaper(system); (wallpaperContext as? android.app.Activity)?.recreate() },
                            Modifier.padding(vertical = 6.dp), tag = "background-choice")
                        Text(if (state.systemWallpaper) "Uses the same wallpaper as your phone’s home screen (including live wallpapers), so it matches what you had in Samsung’s or another launcher."
                            else "Folio’s dunes or a photo you choose, only behind Folio.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (state.systemWallpaper) TextButton(onClick = {
                            runCatching { wallpaperContext.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SET_WALLPAPER), "Change wallpaper")
                                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        }, modifier = Modifier.testTag("background-change-system")) { Text(stringResource(R.string.change_android_wallpaper)) }
                    }
                    GlassCardSettings(state, model)
                    SettingsCard(stringResource(R.string.text_on_home)) {
                        IosMenuRow("Text Color", listOf("AUTO" to "Automatic", "LIGHT" to "Light", "DARK" to "Dark"), state.homeInk, model::setHomeInk, tag = "home-ink")
                        Text(stringResource(R.string.labels_status_page_dots_and_widget_text),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        SettingsSwitch(stringResource(R.string.dark_appearance_dims_wallpaper), state.dimWallpaperDark, model::setDimWallpaperDark, "dim-wallpaper-switch")
                        if (state.systemWallpaper) SettingsSwitch(stringResource(R.string.wallpaper_moves_with_pages), state.wallpaperMotion, model::setWallpaperMotion, "wallpaper-motion-switch")
                    }
                    if (!state.systemWallpaper) {
                    MiniHomePreview(backgrounds.previewBitmap, state, 228.dp)
                    SheetGroupLabel(stringResource(R.string.launcher_background))
                    SheetGroup {
                        IosActionRow(if (backgrounds.previewPending) "Choose a Different Photo…" else "Choose a Photo…", "background-choose",
                            enabled = !backgrounds.loading, onClick = backgrounds::choosePhoto)
                        if (backgrounds.previewPending) {
                            MenuDivider()
                            IosActionRow(stringResource(R.string.apply), "background-preview-apply", enabled = backgrounds.previewBitmap != null, onClick = backgrounds::applyPreview)
                            MenuDivider()
                            IosActionRow(stringResource(R.string.cancel), "background-preview-cancel", onClick = backgrounds::cancelPreview)
                        }
                        if (backgrounds.photoSelected && !backgrounds.previewPending) {
                            MenuDivider()
                            IosActionRow(stringResource(R.string.reset_to_default_dunes), "background-reset", destructive = true, onClick = backgrounds::reset)
                        }
                        MenuDivider()
                        IosActionRow(stringResource(R.string.preview_as_phone_wallpaper), "wallpaper-preview", onClick = onWallpaperPreview)
                    }
                    if (backgrounds.loading) LinearProgressIndicator(Modifier.fillMaxWidth().testTag("background-loading"))
                    Text(stringResource(R.string.changes_the_image_behind_folio_s_home_sc) + " " + stringResource(R.string.opens_android_s_preview_to_use_folio_s_b),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
                    (backgrounds.errorMessage ?: backgrounds.successMessage)?.let { message ->
                        TextButton(onClick = backgrounds::clearMessage, Modifier.fillMaxWidth().testTag("background-message")) { Text(message) }
                    }
                    }
                    AppearanceSettings(appearance, onAppearanceMode, onAppearanceManual, onAppearanceDeviceLocation, onAppearanceClear)
                }
                CustomizationPage.HOME -> {
                    HomeLayoutSettings(state, wide, { wide = it }, model, homePage, onEditPins, onWidget, onAddWidget, onRemoveWidget)
                    RecentDotsCard(state, model)
                }
                CustomizationPage.GESTURES, CustomizationPage.NOTIFICATIONS, CustomizationPage.SEARCH, CustomizationPage.TODAY -> {
                    if (page == CustomizationPage.GESTURES) SettingsCard(stringResource(R.string.gestures)) {
                        Text(stringResource(R.string.pull_down_from_the_top_left_for_notifica),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        SettingsSwitch(stringResource(R.string.drag_page_dots_to_flip_pages), state.pageScrub, model::setPageScrub, "page-scrub-switch")
                        SettingsSwitch(stringResource(R.string.haptic_feedback), state.haptics, model::setHaptics, "haptics-switch")
                    }
                    // One page for the panels: the on/off switch and, when on, their options.
                    if (page == CustomizationPage.NOTIFICATIONS) SettingsCard(stringResource(R.string.panels)) {
                        SettingsSwitch(stringResource(R.string.iphone_style_control_center_and_notifica), state.folioPanels, model::setFolioPanels, "folio-panels-switch")
                        if (state.folioPanels) {
                        CustomizationSlider("Background blur", "${(state.panelBlur * 100).toInt()}%", state.panelBlur, 0f..1f) { model.setPanelBlur(it) }
                        SettingsSwitch(stringResource(R.string.big_clock_in_notification_center), state.notificationClock, model::setNotificationClock, "notification-clock-switch")
                        SettingsSwitch(stringResource(R.string.stack_notifications_by_app), state.groupNotifications, model::setGroupNotifications, "notification-group-switch")
                        SettingsSwitch(stringResource(R.string.unfolded_clock_beside_notifications), state.ncSplit, model::setNcSplit, "notification-split-switch")
                        IosMenuRow(stringResource(R.string.control_center_size), PanelSize.entries.map { it to it.label }, state.ccSize, model::setCcSize, tag = "cc-size")
                        SettingsSwitch(stringResource(R.string.unfolded_control_center_in_the_middle), state.ccCentered, model::setCcCentered, "cc-centered-switch")
                        Text(stringResource(R.string.tip_tap_at_the_top_of_control_center_to),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(R.string.to_restyle_samsungs_own_pull_down_colors),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (page == CustomizationPage.SEARCH) SettingsCard(stringResource(R.string.spotlight)) {
                        IosMenuRow(stringResource(R.string.search_with_enter), listOf(WebSearchTarget.GOOGLE.name to "Google (no AI)", WebSearchTarget.DUCKDUCKGO.name to "DuckDuckGo"),
                            state.searchEngine, model::setSearchEngine, tag = "search-engine")
                        SpotlightSection.entries.forEach { section ->
                            SettingsSwitch(section.title, section.name !in state.spotlightHidden,
                                { model.setSpotlightSection(section.name, it) }, "spotlight-${section.name.lowercase()}")
                        }
                        val messageContext = androidx.compose.ui.platform.LocalContext.current
                        val iMessageApps = remember { Messaging.iMessageApps.filter { Messaging.installed(messageContext, it.first) } }
                        if (iMessageApps.isNotEmpty()) {
                            IosMenuRow(stringResource(R.string.message_contacts_with), listOf<Pair<String?, String>>(null to stringResource(R.string.texting_app)) + iMessageApps.map { it.first to it.second },
                                state.messagesApp, model::setMessagesApp, tag = "messages-app")
                        }
                    }
                    if (page == CustomizationPage.GESTURES) SettingsCard(stringResource(R.string.actions)) {
                        Text(stringResource(R.string.pick_what_gestures_and_events_do_activat),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FolioTrigger.entries.forEach { trigger ->
                            val current = FolioAction.entries.firstOrNull { it.name == state.triggerActions[trigger.name] } ?: FolioAction.NONE
                            IosMenuRow(trigger.label, FolioAction.entries.map { it to it.label }, current, { model.setTriggerAction(trigger, it) }, tag = "trigger-${trigger.name.lowercase()}")
                        }
                    }
                    if (page == CustomizationPage.TODAY) SettingsCard(stringResource(R.string.left_of_home)) {
                        val leftContext = androidx.compose.ui.platform.LocalContext.current
                        // The Home pager's page count changes with this; rebuild the screen once.
                        IosSegmented(listOf("TODAY" to "Today View", "DISCOVER" to "Google Discover"), state.leftPage,
                            { model.setLeftPage(it); (leftContext as? android.app.Activity)?.recreate() }, Modifier.padding(vertical = 6.dp), tag = "left-page")
                        Text(stringResource(R.string.today_view_is_iphone_s_widget_page_searc),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (state.leftPage == "TODAY") {
                            IosMenuRow(stringResource(R.string.when_unfolded), listOf("PAGE" to "Swipe to It", "BESIDE" to "Beside Home", "OFF" to "Off"),
                                state.todayUnfolded, model::setTodayUnfolded, tag = "today-unfolded")
                            Text(when (state.todayUnfolded) {
                                "BESIDE" -> "Like iPad: Today View stays on the left of the open screen, next to your first Home page. It takes the place of the unfolded-only page."
                                "OFF" -> "No Today View while unfolded; it's still there on the cover screen."
                                else -> "Swipe right from your first Home page to open it, folded or unfolded."
                            }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (page == CustomizationPage.SEARCH) SettingsCard(stringResource(R.string.app_library)) {
                        SettingsSwitch(stringResource(R.string.group_apps_into_categories), state.libraryCategories, model::setLibraryCategories, "library-categories-switch")
                        SettingsSwitch("Add New Apps to Home Screen", state.addNewAppsToHome, model::setAddNewAppsToHome, "add-new-apps-switch")
                        Text("Off, new downloads go to the App Library only. Either way they show a blue dot until you open them.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (page == CustomizationPage.SEARCH) SettingsCard(stringResource(R.string.search)) {
                        SettingsSwitch(stringResource(R.string.search_button_on_home), state.searchPill, model::setSearchPill, "search-pill-switch")
                        SettingsSwitch(stringResource(R.string.swipe_down_on_home_for_spotlight), state.swipeDownSearch, model::setSwipeDownSearch, "swipe-search-switch")
                        SettingsSwitch(stringResource(R.string.search_button_opens_the_google_app), state.googleSearch, model::setGoogleSearch, "google-search-switch")
                        Text(stringResource(R.string.when_off_the_search_button_opens_spotlig),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                CustomizationPage.STATUS, CustomizationPage.ISLAND -> {
                    if (page == CustomizationPage.STATUS && !split) MiniHomePreview(backgrounds.previewBitmap, state, 210.dp)
                    val st = state.statusStyle
                    if (page == CustomizationPage.STATUS) SettingsCard(stringResource(R.string.app_icons)) {
                        val iconContext = androidx.compose.ui.platform.LocalContext.current
                        val packs = remember { IconPacks.installed(iconContext) }
                        IosMenuRow(stringResource(R.string.icon_pack), listOf<Pair<String?, String>>(null to stringResource(R.string.app_icons)) + packs.map { it.packageName to it.label },
                            state.iconPack, { IconPacks.clear(); model.setIconPack(it) }, tag = "icon-pack")
                        if (packs.isEmpty()) Text(stringResource(R.string.install_any_icon_pack_made_for_nova_styl),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        // Live Clock and Calendar: the app's own icon, or live icons that match the others, or always light/dark.
                        IosMenuRow("Clock & Calendar", listOf("OFF" to "App Icons", "AUTO" to "Live, Automatic", "LIGHT" to "Live, Light", "DARK" to "Live, Dark"),
                            if (state.liveIcons) state.liveIconLook else "OFF",
                            { if (it == "OFF") model.setLiveIcons(false) else model.setLiveIconLook(it) }, tag = "live-icons-menu")
                        IosMenuRow(stringResource(R.string.shape), IconShape.entries.map { it to it.label }, state.iconShape, model::setIconShape, tag = "icon-shape")
                        IosMenuRow(stringResource(R.string.notification_badges), BadgeStyle.entries.map { it to it.label }, state.badgeStyle, model::setBadgeStyle, tag = "badge-style")
                        if (state.badgeStyle != BadgeStyle.OFF) IosMenuRow(stringResource(R.string.badge_color), BadgeColor.entries.map { it to it.label }, state.badgeColor, model::setBadgeColor, tag = "badge-color")
                        // iOS Home Screen customization: Default, Dark and Tinted side by side.
                        Text(stringResource(R.string.style), color = androidx.compose.ui.graphics.Color.White.copy(alpha = .6f), fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                        IosSegmented(IconStyle.entries.map { it to it.label }, state.iconStyle, { model.setIconStyle(it, state.iconTint) }, Modifier.padding(vertical = 4.dp), tag = "icon-style")
                        if (state.iconStyle == IconStyle.TINTED) Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            // Wallpaper color (follows the wallpaper when it changes)
                            val tone = LocalWallpaperTone.current
                            Box(Modifier.size(40.dp).clip(androidx.compose.foundation.shape.CircleShape)
                                .background(tone.primary?.let { androidx.compose.ui.graphics.Color(vividTint(it)) } ?: androidx.compose.ui.graphics.Color.Gray)
                                .then(if (state.iconTintFromWallpaper) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, androidx.compose.foundation.shape.CircleShape) else Modifier)
                                .clickable(role = androidx.compose.ui.semantics.Role.RadioButton) { model.setIconTintFromWallpaper(true) }
                                .semantics { contentDescription = "Wallpaper color"; selected = state.iconTintFromWallpaper },
                                contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.Wallpaper, null, tint = androidx.compose.ui.graphics.Color.Black.copy(alpha = .6f), modifier = Modifier.size(18.dp))
                            }
                            listOf(0xFFFFB340, 0xFFFF6961, 0xFFFF7EB6, 0xFFBF8CFF, 0xFF64B5FF, 0xFF5EE0C4, 0xFF9BE15D, 0xFFE8E8E8).forEach { c ->
                                Box(Modifier.size(40.dp).clip(androidx.compose.foundation.shape.CircleShape).background(androidx.compose.ui.graphics.Color(c))
                                    .then(if (!state.iconTintFromWallpaper && state.iconTint == c) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, androidx.compose.foundation.shape.CircleShape) else Modifier)
                                    .clickable(role = androidx.compose.ui.semantics.Role.RadioButton) { model.setIconTintFromWallpaper(false); model.setIconStyle(IconStyle.TINTED, c) }
                                    .semantics { contentDescription = "Tint color"; selected = !state.iconTintFromWallpaper && state.iconTint == c })
                            }
                        }
                    }
                    if (page == CustomizationPage.STATUS) SettingsCard(stringResource(R.string.side_rail)) {
                        SettingsSwitch(stringResource(R.string.left_handed_layout_rail_on_the_left), state.leftHanded, model::setLeftHanded, "left-handed-switch")
                        SettingsSwitch(stringResource(R.string.show_app_names), state.labels, model::setLabels, "label-switch")
                        Text("Frost and outline are in Wallpaper & Appearance › Glass.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (page == CustomizationPage.STATUS) SettingsCard(stringResource(R.string.status)) {
                        SettingsSwitch(stringResource(R.string.show_status_in_the_rail), state.verticalStatus, model::setVerticalStatus, "status-switch")
                        if (state.verticalStatus) {
                            IosMenuRow(stringResource(R.string.icon_style), StatusGlyph.entries.map { it to it.label }, st.glyph, { model.setStatusStyle(st.copy(glyph = it)) }, tag = "status-glyph")
                            SettingsSwitch(stringResource(R.string.time), st.showTime, { model.setStatusStyle(st.copy(showTime = it)) }, "status-time")
                            SettingsSwitch(stringResource(R.string.date), st.showDate, { model.setStatusStyle(st.copy(showDate = it)) }, "status-date")
                            SettingsSwitch(stringResource(R.string.battery_percentage), st.showBatteryPercent, { model.setStatusStyle(st.copy(showBatteryPercent = it)) }, "status-percent")
                            SettingsSwitch(stringResource(R.string.color_battery_when_charging_or_low), st.colorfulBattery, { model.setStatusStyle(st.copy(colorfulBattery = it)) }, "status-color")
                        }
                    }
                    if (page == CustomizationPage.ISLAND) SettingsCard(stringResource(R.string.in_every_app)) {
                        SettingsSwitch(stringResource(R.string.dock_handle_on_the_rail_edge), state.dockEverywhere, { on ->
                            model.setDockEverywhere(on); if (on && !SystemShadeAccessibilityService.isConnected()) onShadeSetup()
                        }, "dock-everywhere-switch")
                        SettingsSwitch(stringResource(R.string.dynamic_island), state.islandEverywhere, { on ->
                            model.setIslandEverywhere(on); if (on && !SystemShadeAccessibilityService.isConnected()) onShadeSetup()
                        }, "island-everywhere-switch")
                        Text(stringResource(R.string.uses_folios_accessibility_service_the_sa),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (page == CustomizationPage.ISLAND) SettingsCard(stringResource(R.string.island)) {
                        val islandContext = androidx.compose.ui.platform.LocalContext.current
                        SettingsSwitch(stringResource(R.string.music_and_live_progress), state.island, { on ->
                            model.setIsland(on)
                            if (on && !IslandListenerService.hasAccess(islandContext))
                                runCatching { islandContext.startActivity(IslandListenerService.accessSettingsIntent(islandContext)) }
                        }, "island-switch")
                        if (state.island && state.verticalStatus) SettingsSwitch("Live Activities Under the Status Bar", state.railActivities, model::setRailActivities, "rail-activities-switch")
                        if (state.island && !IslandListenerService.hasAccess(islandContext)) TextButton(onClick = {
                            runCatching { islandContext.startActivity(IslandListenerService.accessSettingsIntent(islandContext)) }
                        }) { Text(stringResource(R.string.allow_notification_access)) }
                        if (state.island) {
                            Text(stringResource(R.string.long_press_and_drag_the_island_to_move_i),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            TextButton(onClick = { IslandPosition.reset(islandContext) }) { Text(stringResource(R.string.put_the_island_back_at_the_camera)) }
                            Text(stringResource(R.string.brief_pop_ups), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
                            listOf("CHARGING" to "Charging", "SILENT" to "Silent mode", "FOCUS" to "Do Not Disturb", "BLUETOOTH" to "Bluetooth devices", "MESSAGE" to "New messages (with quick reply)", "CALL" to "Calls (answer, decline, end)").forEach { (kind, label) ->
                                SettingsSwitch(label, kind !in state.islandEventsOff, { model.setIslandEvent(kind, it) }, "island-event-${kind.lowercase()}")
                            }
                            if ("MESSAGE" !in state.islandEventsOff) MessageBannerSettings(state.messagesAvoidDouble, model::setMessagesAvoidDouble)
                        } else if (state.messagesAvoidDouble) {
                            // Apps that were switched to the island show no pop-up at all while the island is off.
                            Text("The island is off, so message pop-ups only come from Android. Apps below that use the island won’t pop up until you switch them back:",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            MessageChannelList()
                        }
                        Text(stringResource(R.string.reads_only_music_calls_timers_navigation),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                CustomizationPage.FOLD -> {
                    // What Folio has learned about how fast you fold (it adapts the animation to this).
                    val foldPrefs = androidx.compose.ui.platform.LocalContext.current.getSharedPreferences("folio", 0)
                    var learned by remember { mutableStateOf(foldPrefs.getFloat("fold_open_ms", 520f) to foldPrefs.getFloat("fold_close_ms", 650f)) }
                    SettingsCard(stringResource(R.string.your_fold_timing)) {
                        Text("Unfold: about ${learned.first.toInt()} ms · Fold: about ${learned.second.toInt()} ms",
                            style = MaterialTheme.typography.bodyLarge)
                        Text(stringResource(R.string.learned_from_your_last_folds_and_used_to),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = {
                            foldPrefs.edit().remove("fold_open_ms").remove("fold_close_ms").apply()
                            learned = 520f to 650f
                        }) { Text(stringResource(R.string.reset_fold_timing)) }
                    }
                    SettingsCard(stringResource(R.string.fold_animation)) {
                        SettingsSwitch(stringResource(R.string.fold_animation), state.foldEffect, model::setFoldEffect, "fold-effect-switch")
                        if (state.foldEffect) IosSegmented(listOf(false to stringResource(R.string.iphone_duo_fade), true to stringResource(R.string.screenshot_morph)),
                            state.foldSnapshot, model::setFoldSnapshot, Modifier.padding(vertical = 6.dp), tag = "fold-style")
                        if (state.foldEffect && state.foldSnapshot) Text(stringResource(R.string.takes_a_quick_in_memory_snapshot_of_foli),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (state.foldEffect) CustomizationSlider("Intensity", "${(state.foldIntensity * 100).toInt()}%",
                            state.foldIntensity, .3f..1.5f) { model.setFoldIntensity(it) }
                        Text(stringResource(R.string.your_fold_reports_only_a_few_hinge_posit),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    SettingsCard(stringResource(R.string.standby)) {
                        SettingsSwitch(stringResource(R.string.show_standby_when_set_down_half_open), state.standBy, model::setStandBy, "standby-switch")
                        Text(stringResource(R.string.big_clock_date_next_alarm_battery_and_mu),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    SettingsCard(stringResource(R.string.closing_from_home)) {
                        SettingsSwitch(stringResource(R.string.stay_awake_on_the_cover_screen), state.stayAwakeOnFold, model::setStayAwakeOnFold, "fold-awake-switch")
                        Text(stringResource(R.string.samsung_locks_the_phone_when_you_fold_on),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                CustomizationPage.BACKUP -> {
                    SheetGroup {
                        IosActionRow("Save Backup…", "layout-export", onClick = onExportLayout)
                        MenuDivider()
                        IosActionRow("Restore from Backup…", "layout-import", onClick = onImportLayout)
                    }
                    Text(stringResource(R.string.save_the_current_home_layout_folders_wid) + " " + stringResource(R.string.restore_shows_a_review_before_changing_h),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
                    LayoutHistoryCard(state, model, onClose)
                }
                CustomizationPage.SIDE_KEY -> SideKeyPage()
                CustomizationPage.LOCK -> {
                    SettingsCard(stringResource(R.string.lock_cover)) {
                        SettingsSwitch(stringResource(R.string.show_after_unlocking), state.lockCover, model::setLockCover, "lock-cover-switch")
                        Text(stringResource(R.string.android_doesn_t_let_apps_replace_the_rea),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                CustomizationPage.CREDITS -> CreditsPage()
                CustomizationPage.HELP -> {
                    LauncherHelp(
                        isDefaultHome = isDefaultHome,
                        onHomeSettings = onMakeDefault,
                        onAddWidget = { onAddWidget(homePage) },
                        onShadeSetup = onShadeSetup,
                    )
                }
                CustomizationPage.ADVANCED -> {
                    SettingsCard("Safe Mode") {
                        Text(if (SafeMode.active) "Folio is running in Safe Mode: optional features are paused for this session."
                            else "If Folio closes unexpectedly twice right after starting, it starts in Safe Mode with optional features paused. Your settings are never changed.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    CrashReportsCard()
                }
                CustomizationPage.TWEAKS -> {
                    Text("Features inspired by iOS jailbreak tweaks, re-created for Folio. Each can be turned on or off, or set per screen.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
                    SheetGroup {
                        TweakFeatures.forEachIndexed { index, tweak ->
                            if (index > 0) MenuDivider()
                            TweakRow(tweak.icon, tweak.color, tweak.name, "tweak-${tweak.id}", if (tweak.get(state)) "On" else "Off") {
                                tweakId = tweak.id; onPage(CustomizationPage.TWEAK)
                            }
                        }
                    }
                }
                CustomizationPage.PERMISSIONS -> PermissionsPage(isDefaultHome, onMakeDefault, onShadeSetup)
                CustomizationPage.THEMES -> ThemesPage(state, model, backgrounds.previewBitmap)
                CustomizationPage.FOCUS -> FocusListPage(state, model) { focusId = it; onPage(CustomizationPage.FOCUS_MODE) }
                CustomizationPage.FOCUS_MODE -> state.focusModes.firstOrNull { it.id == focusId }?.let { FocusModePage(it, state, model) }
                    ?: LaunchedEffect(Unit) { onPage(CustomizationPage.FOCUS) }
                CustomizationPage.TWEAK -> TweakFeatures.firstOrNull { it.id == tweakId }?.let { tweak -> TweakPage(tweak, state, model) }
                    ?: LaunchedEffect(Unit) { onPage(CustomizationPage.TWEAKS) }
            }
    }
    if (!split) Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        SettingsNavBar(if (page == CustomizationPage.OVERVIEW) null else if (page == CustomizationPage.TWEAK) "Tweaks" else if (page == CustomizationPage.FOCUS_MODE) "Focus" else stringResource(R.string.folio), onBack, onClose)
        if (page != CustomizationPage.OVERVIEW) SettingsLargeTitle(title)
        Column(Modifier.weight(1f).verticalScroll(bodyScroll).padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp), content = pageContent)
    } else {
        // Split arrangement by shape, not device: wider than tall, sidebar and page are tiled; taller than wide,
        // the page gets the width and the sidebar opens over it from the sidebar button (iPhone Duo split views).
        val tiled = maxWidth > maxHeight
        // Tiled it shares the width; as an overlay it can be a little wider so rows don't wrap.
        val sidebarWidth = if (tiled) (fullWidth * .4f).coerceIn(280.dp, 380.dp) else minOf(360.dp, fullWidth * .6f)
        var sidebarOpen by rememberSaveable { mutableStateOf(true) }
        var shownPage by remember { mutableStateOf(page) }
        SideEffect { if (page != shownPage) { shownPage = page; if (!tiled) sidebarOpen = false } }
        val sidebar: @Composable () -> Unit = {
            val sidebarScroll = rememberScrollState()
            Column(Modifier.width(sidebarWidth).fillMaxHeight().verticalScroll(sidebarScroll)
                .padding(horizontal = 16.dp).padding(top = 44.dp, bottom = 20.dp).testTag("settings-sidebar"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SettingsLargeTitle(stringResource(R.string.folio))
                SettingsSearchField(settingsQuery) { settingsQuery = it }
                if (settingsQuery.isNotBlank()) SettingsSearchResults(settingsQuery, onOpen = { onPage(it) })
                else {
                    // Like the account card at the top of iPad Settings: Folio's own page.
                    SheetGroup { SidebarAppRow(selected = page == CustomizationPage.OVERVIEW, setupLeft) { onPage(CustomizationPage.OVERVIEW) } }
                    overviewRows(when (page) { CustomizationPage.TWEAK -> CustomizationPage.TWEAKS; CustomizationPage.FOCUS_MODE -> CustomizationPage.FOCUS; else -> page }, true)
                }
            }
        }
        Row(Modifier.fillMaxSize()) {
            if (tiled) {
                sidebar()
                Box(Modifier.fillMaxHeight().width(.5.dp).background(androidx.compose.ui.graphics.Color.White.copy(alpha = .14f)))
            }
            Column(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 20.dp)) {
                SettingsNavBar(if (page == CustomizationPage.TWEAK) "Tweaks" else if (page == CustomizationPage.FOCUS_MODE) "Focus" else null, onBack, onClose,
                    leading = if (tiled) null else ({ SidebarButton { sidebarOpen = !sidebarOpen } }))
                if (page != CustomizationPage.OVERVIEW) SettingsLargeTitle(title)
                Column(Modifier.weight(1f).verticalScroll(bodyScroll).padding(bottom = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Column(Modifier.widthIn(max = 720.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        // The space beside the list shows what the page changes, drawn from your real Home.
                        if (page == CustomizationPage.HOME || page == CustomizationPage.STATUS)
                            MiniHomePreview(backgrounds.previewBitmap, state, 240.dp, iconScale = (if (wide) state.expanded else state.compact).iconSize / 66f)
                        pageContent()
                    }
                }
            }
        }
        if (!tiled) {
            val reduceMotion = LocalReduceMotion.current
            androidx.compose.animation.AnimatedVisibility(sidebarOpen, enter = androidx.compose.animation.fadeIn(), exit = androidx.compose.animation.fadeOut()) {
                Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black.copy(alpha = .4f))
                    .clickable(remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, null) { sidebarOpen = false })
            }
            androidx.compose.animation.AnimatedVisibility(sidebarOpen,
                enter = if (reduceMotion) androidx.compose.animation.fadeIn() else androidx.compose.animation.slideInHorizontally { -it },
                exit = if (reduceMotion) androidx.compose.animation.fadeOut() else androidx.compose.animation.slideOutHorizontally { -it }) {
                Box(Modifier.fillMaxHeight().background(androidx.compose.ui.graphics.Color(0xFF1C1C1E))) { sidebar() }
            }
        }
    }
    }
}

/** The iPad/iPhone Duo sidebar toggle: shows or hides the settings list over the page. */
@Composable private fun SidebarButton(onClick: () -> Unit) {
    Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).clickable(onClickLabel = "Show or hide the settings list", onClick = onClick)
        .testTag("settings-sidebar-toggle"), contentAlignment = Alignment.Center) {
        Icon(Icons.Rounded.ViewSidebar, null, tint = IosBlue, modifier = Modifier.size(26.dp))
    }
}

@Composable private fun SettingsNavBar(backLabel: String?, onBack: () -> Unit, onClose: () -> Unit, leading: (@Composable () -> Unit)? = null) {
    // iOS navigation bar: "‹ Back" on sub-pages (or the sidebar button), Done on the right.
    Box(Modifier.fillMaxWidth().heightIn(min = 44.dp)) {
        if (backLabel == null && leading != null) Box(Modifier.align(Alignment.CenterStart)) { leading() }
        if (backLabel != null) Row(Modifier.align(Alignment.CenterStart).clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onBack).padding(vertical = 8.dp, horizontal = 2.dp).testTag("customization-back"),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.ChevronLeft, null, tint = IosBlue, modifier = Modifier.size(28.dp))
            Text(backLabel, color = IosBlue, fontSize = 17.sp)
        }
        Text(stringResource(R.string.done), color = IosBlue, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.CenterEnd).clip(RoundedCornerShape(10.dp)).clickable(onClick = onClose)
                .padding(horizontal = 8.dp, vertical = 8.dp).semantics { contentDescription = "Close customization" })
    }
}

@Composable private fun SettingsLargeTitle(title: String) = Text(title, color = androidx.compose.ui.graphics.Color.White, fontSize = 32.sp,
    fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))

/** Sidebar header row: Folio's icon, name and setup state, like the account card in iPad Settings. */
@Composable private fun SidebarAppRow(selected: Boolean, setupLeft: Int, onClick: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val icon = remember { folioIconBitmap(context) }
    // iPad Settings: the selection is a rounded highlight inset from the group's edges, not a square band.
    Row(Modifier.fillMaxWidth().padding(4.dp).clip(RoundedCornerShape(12.dp)).background(if (selected) IosBlue else androidx.compose.ui.graphics.Color.Transparent).clickable(onClick = onClick)
        .padding(horizontal = 14.dp, vertical = 10.dp).testTag("settings-sidebar-folio"), verticalAlignment = Alignment.CenterVertically) {
        icon?.let { Image(it, null, Modifier.size(52.dp).clip(RoundedCornerShape(12.dp))) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.folio), color = androidx.compose.ui.graphics.Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text(if (setupLeft > 0) "$setupLeft setup step${if (setupLeft > 1) "s" else ""} left" else "Home, panels and tweaks",
                color = androidx.compose.ui.graphics.Color.White.copy(alpha = if (selected) .85f else .55f), fontSize = 14.sp)
        }
    }
}

@Composable
private fun LauncherHelp(
    isDefaultHome: Boolean,
    onHomeSettings: () -> Unit,
    onAddWidget: () -> Unit,
    onShadeSetup: () -> Unit,
) {
    SheetGroup {
        HelpTip(Icons.Rounded.Home, 0xFF0A84FF, "Edit Home", "Hold an app for its menu, or move while holding to start jiggle mode. Long-press empty space for widgets and pages.")
        MenuDivider()
        HelpTip(Icons.Rounded.Widgets, 0xFF5E5CE6, "Widgets & Smart Stacks", "Hold a widget and let go for sizes, stacks and Smart Rotate. Swipe a stack up or down.")
        MenuDivider()
        HelpTip(Icons.Rounded.SwipeDown, 0xFFFF3B30, "Notifications & Control Center", "Pull down from the top left or top right. Swipe down lower on Home for Spotlight.")
        MenuDivider()
        HelpTip(Icons.Rounded.Circle, 0xFF1C1C1E, "Dynamic Island", "Tap it for details, hold and drag to move it. On the inner screen, drag it onto the camera once.")
        MenuDivider()
        HelpTip(Icons.Rounded.Devices, 0xFFFF375F, "Folding", "Folio fades between screens and keeps the cover awake when you fold from Home.")
    }
    SheetGroup {
        IosActionRow("Add Widget to This Page", "help-add-widget", onClick = onAddWidget)
    }
    // The person behind Folio, for anything a bug report doesn't cover.
    val helpContext = androidx.compose.ui.platform.LocalContext.current
    SheetGroup {
        IosActionRow("Email the Developer", "help-contact-email") {
            runCatching { helpContext.startActivity(android.content.Intent(android.content.Intent.ACTION_SENDTO, android.net.Uri.parse("mailto:contact@mcc-cal.com"))
                .putExtra(android.content.Intent.EXTRA_SUBJECT, "Folio").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
        MenuDivider()
        IosActionRow("Buy Me a Coffee", "help-ko-fi") {
            runCatching { helpContext.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://ko-fi.com/mccal"))
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }
}

@Composable
private fun HelpTip(icon: ImageVector, color: Long, title: String, detail: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(30.dp).clip(RoundedCornerShape(7.dp)).background(androidx.compose.ui.graphics.Color(color)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = androidx.compose.ui.graphics.Color.White, fontSize = 17.sp)
            Text(detail, color = androidx.compose.ui.graphics.Color.White.copy(alpha = .55f), fontSize = 14.sp)
        }
    }
}



private val IosBlue = androidx.compose.ui.graphics.Color(0xFF0A84FF)

/** Tweak-style header: Folio's icon, name and version, like a jailbreak tweak's preference banner. */
@Composable private fun TweakBanner() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "" }
    Column(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        // Folio's own launcher icon, like a tweak's preference banner.
        val icon = remember { folioIconBitmap(context) }
        if (icon != null) androidx.compose.foundation.Image(icon, null, Modifier.size(72.dp).clip(RoundedCornerShape(18.dp)))
        Text(stringResource(R.string.folio), color = androidx.compose.ui.graphics.Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
        Text("iPhone Duo for your Fold · v$version", color = androidx.compose.ui.graphics.Color.White.copy(alpha = .55f), fontSize = 14.sp)
    }
}

/** iOS Settings row: colored rounded icon square, title, optional value, chevron. */
@Composable private fun TweakRow(icon: ImageVector, color: Long, title: String, tag: String, value: String? = null,
    selected: Boolean = false, chevron: Boolean = true, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
        .then(if (selected) Modifier.padding(horizontal = 5.dp, vertical = 2.dp).clip(RoundedCornerShape(10.dp)).background(IosBlue) else Modifier)
        // The inset is taken back from the content padding, so the icon and title don't shift when selected.
        .clickable(onClick = onClick).padding(horizontal = if (selected) 9.dp else 14.dp, vertical = if (selected) 6.dp else 8.dp).testTag(tag).semantics { this.selected = selected },
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(30.dp).clip(RoundedCornerShape(7.dp)).background(androidx.compose.ui.graphics.Color(color)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(title, color = androidx.compose.ui.graphics.Color.White, fontSize = 17.sp, modifier = Modifier.weight(1f))
        value?.let { Text(it, color = androidx.compose.ui.graphics.Color.White.copy(alpha = if (selected) .85f else .5f), fontSize = 17.sp) }
        if (chevron) Icon(Icons.Rounded.ChevronRight, null, tint = androidx.compose.ui.graphics.Color.White.copy(alpha = .3f))
    }
}

/** Guided side key setup: hold → Folio's assistant picker, double press → Google Wallet. Samsung doesn't let apps change these, so each row checks and opens the right screen. */
@Composable private fun SideKeyPage() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) { lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) { tick++ } }
    fun open(intent: android.content.Intent?) { intent?.let { runCatching { context.startActivity(it.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) } } }
    val assistant = remember(tick) { AssistPickerActivity.isDefaultAssistant(context) }
    val hold = remember(tick) { sideKeyHoldIsAssistant(context) }
    val wallet = remember(tick) { sideKeyDoublePressIsWallet(context) }
    SettingsCard(stringResource(R.string.press_and_hold)) {
        SideKeyStep("1. Folio is your digital assistant", "Settings › Apps › Default apps › Digital assistant app › Folio", assistant) { open(AssistPickerActivity.settingsIntent()) }
        SideKeyStep("2. Hold the side key: Digital assistant", "Side button › Press and hold › Digital assistant", hold) { open(sideKeySettings(context)) }
        Text(stringResource(R.string.then_holding_the_side_key_opens_folio_s),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Still nothing? Choose a different digital assistant, then Folio again, so Android picks up Folio's assistant service.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        // Good Lock's RegiStar can take over the side key before Android's assistant setting is used.
        val registar = remember(tick) { runCatching { context.packageManager.getPackageInfo("com.samsung.android.app.galaxyregistry", 0) }.isSuccess }
        if (registar) Text("RegiStar (Good Lock) is installed. If it has its own side key action, it runs instead: set RegiStar's Press and hold to Digital assistant, or turn that action off.",
            style = MaterialTheme.typography.bodySmall, color = androidx.compose.ui.graphics.Color(0xFFFF9F0A))
    }
    SettingsCard(stringResource(R.string.double_press)) {
        SideKeyStep("Double press: Google Wallet", "Side button › Double press › Open app › Wallet", wallet) { open(sideKeyDoublePressSettings(context) ?: sideKeySettings(context)) }
        Text(stringResource(R.string.like_double_clicking_the_side_button_for),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable private fun SideKeyStep(title: String, path: String, done: Boolean, onOpen: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title)
            Text(path, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (done) Icon(Icons.Rounded.CheckCircle, "Done", tint = androidx.compose.ui.graphics.Color(0xFF30D158))
        else TextButton(onClick = onOpen) { Text(stringResource(R.string.open)) }
    }
}

/** iOS Settings search field. */
@Composable private fun SettingsSearchField(query: String, onQuery: (String) -> Unit) =
    IosSearchField(query, onQuery, "Search", fieldModifier = Modifier.testTag("settings-search"))

/** Where each setting lives, for Settings search (titles as shown, plus words people search for). */
private val SettingsIndex: List<Triple<String, String, CustomizationPage>> = listOf(
    Triple("Background & wallpaper", "wallpaper photo dunes android image", CustomizationPage.WALLPAPER),
    Triple("Text on Home", "light dark ink labels legibility", CustomizationPage.WALLPAPER),
    Triple("Glass", "glass frost blur outline border transparency widgets side bar tint", CustomizationPage.WALLPAPER),
    Triple("Tint glass with wallpaper color", "glass tint frost blur", CustomizationPage.WALLPAPER),
    Triple("Dark appearance dims wallpaper", "dim dark mode night", CustomizationPage.WALLPAPER),
    Triple("Appearance (light, dark, sunset)", "theme dark light sunrise", CustomizationPage.WALLPAPER),
    Triple("Grid, icon size & dock", "layout rows columns spacing icons dock", CustomizationPage.HOME),
    Triple("Widgets", "widget stack smart", CustomizationPage.HOME),
    Triple("Today View / Left of Home", "today discover google widgets page beside", CustomizationPage.TODAY),
    Triple("Icon pack, shape & style", "icons pack squircle circle tinted dark", CustomizationPage.STATUS),
    Triple("Notification badges", "badge dot count color soft", CustomizationPage.STATUS),
    Triple("Live Clock and Calendar icons", "live clock calendar", CustomizationPage.STATUS),
    Triple("Side Bar & Status Bar", "side bar rail status bar battery wifi time left-handed labels app names", CustomizationPage.STATUS),
    Triple("Dynamic Island", "island pill camera pop-ups calls messages charging bluetooth", CustomizationPage.ISLAND),
    Triple("Island and dock in every app", "overlay everywhere other apps handle", CustomizationPage.ISLAND),
    Triple("Notification Center", "notifications clock stack group split blur panels iphone style", CustomizationPage.NOTIFICATIONS),
    Triple("Control Center", "control center size centered toggles", CustomizationPage.NOTIFICATIONS),
    Triple("Spotlight", "search engine google duckduckgo contacts calculator sections messages openbubbles", CustomizationPage.SEARCH),
    Triple("App Library", "library categories hidden apps", CustomizationPage.SEARCH),
    Triple("Search button & swipe down", "search pill swipe google", CustomizationPage.SEARCH),
    Triple("Page dots & haptics", "page dots scrub drag flip haptics vibration feedback", CustomizationPage.GESTURES),
    Triple("Gestures & pull-downs", "gesture pull down swipe", CustomizationPage.GESTURES),
    Triple("Actions", "activator double tap two finger charging bluetooth headphones trigger", CustomizationPage.GESTURES),
    Triple("Side Key", "side key assistant chatgpt claude wallet double press hold", CustomizationPage.SIDE_KEY),
    Triple("Lock Cover", "lock screen unlock cover clock", CustomizationPage.LOCK),
    Triple("Fold animation", "fold unfold animation blur fade duo timing", CustomizationPage.FOLD),
    Triple("StandBy", "standby tent half open clock", CustomizationPage.FOLD),
    Triple("Themes", "theme look snowboard icon style tint shape badges glass import export", CustomizationPage.THEMES),
    Triple("Focus", "focus do not disturb dnd sleep work personal silence quiet grayscale", CustomizationPage.FOCUS),
    Triple("Tweaks", "tweak jailbreak velox harbor axon velvet colorflow panels magnification tint album", CustomizationPage.TWEAKS),
    Triple("Privacy & Permissions", "privacy permissions setup checklist home app notification access accessibility gestures contacts bluetooth", CustomizationPage.PERMISSIONS),
    Triple("Safe Mode & crash reports", "safe mode crash report bug", CustomizationPage.ADVANCED),
    Triple("Backup & restore", "backup restore export import layout", CustomizationPage.BACKUP),
    Triple("Coming Soon", "coming soon roadmap planned future features lock designer keyboard", CustomizationPage.COMING_SOON),
    Triple("Credits", "credits thanks duolauncher jakesgoodapps license", CustomizationPage.CREDITS),
)

internal fun settingsMatches(query: String, title: String, keywords: String): Boolean {
    val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
    val hay = "$title $keywords".lowercase()
    return words.isNotEmpty() && words.all { it in hay }
}

@Composable private fun SettingsSearchResults(query: String, onOpen: (CustomizationPage) -> Unit) {
    val results = SettingsIndex.filter { (title, keywords) -> settingsMatches(query, title, keywords) }
    if (results.isEmpty()) Text("No Results for “${query.trim()}”", color = androidx.compose.ui.graphics.Color.White.copy(alpha = .55f),
        modifier = Modifier.fillMaxWidth().padding(24.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    else SheetGroup {
        results.forEachIndexed { index, (title, _, page) ->
            if (index > 0) MenuDivider()
            Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { onOpen(page) }.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = androidx.compose.ui.graphics.Color.White, fontSize = 17.sp, modifier = Modifier.weight(1f))
                Icon(Icons.Rounded.ChevronRight, null, tint = androidx.compose.ui.graphics.Color.White.copy(alpha = .3f))
            }
        }
    }
}

/** Every permission Folio can use, whether it's allowed, and which features rely on it (shared ownership). */
@Composable private fun PermissionsPage(isDefaultHome: Boolean, onMakeDefault: () -> Unit, onShadeSetup: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) { lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) { tick++ } }
    fun open(intent: android.content.Intent) { runCatching { context.startActivity(intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) } }
    val appSettings = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${context.packageName}"))
    data class Perm(val name: String, val usedBy: String, val allowed: Boolean, val action: () -> Unit)
    val perms = remember(tick, isDefaultHome) { listOf(
        Perm("Home app", "Home button, folding, gestures", isDefaultHome, onMakeDefault),
        Perm("Notification access", "Dynamic Island, Notification Center, quick reply, badges, app panels, Lock Cover",
            IslandListenerService.hasAccess(context)) { open(IslandListenerService.accessSettingsIntent(context)) },
        Perm("Gestures service (accessibility)", "Pull-down panels, island and dock in other apps, Lock Screen and Screenshot actions",
            SystemShadeAccessibilityService.isConnected(), onShadeSetup),
        Perm("Do Not Disturb access", "Focus, Control Center, Actions",
            context.getSystemService(android.app.NotificationManager::class.java).isNotificationPolicyAccessGranted) {
            open(android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) },
        Perm("Modify system settings", "Control Center brightness and rotation lock", android.provider.Settings.System.canWrite(context)) {
            open(android.content.Intent(android.provider.Settings.ACTION_MANAGE_WRITE_SETTINGS, android.net.Uri.parse("package:${context.packageName}"))) },
        Perm("Usage access (optional)", "Better Suggestions: counts apps you open from anywhere, not just from Folio", Suggestions.hasUsageAccess(context)) {
            open(Suggestions.usageAccessIntent(context)) },
        Perm("Calendar (optional)", "Up Next widget", UpNext.hasCalendar(context)) { open(appSettings) },
        Perm("Contacts", "Spotlight contact search", context.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) == android.content.pm.PackageManager.PERMISSION_GRANTED) { open(appSettings) },
        Perm("Nearby devices (Bluetooth)", "Device names in the Dynamic Island", context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) == android.content.pm.PackageManager.PERMISSION_GRANTED) { open(appSettings) },
        Perm("Digital assistant", "Side key picker", AssistPickerActivity.isDefaultAssistant(context)) { open(AssistPickerActivity.settingsIntent()) },
    ) }
    Text("Everything stays on your phone. Folio has no ads or analytics, and nothing is sent anywhere unless you share a crash report.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
    SheetGroup {
        perms.forEachIndexed { index, perm ->
            if (index > 0) MenuDivider()
            Row(Modifier.fillMaxWidth().clickable(onClick = perm.action).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(perm.name, color = androidx.compose.ui.graphics.Color.White, fontSize = 17.sp)
                    Text("Used by: ${perm.usedBy}", color = androidx.compose.ui.graphics.Color.White.copy(alpha = .55f), fontSize = 13.sp)
                }
                Text(if (perm.allowed) "Allowed" else "Off", color = if (perm.allowed) androidx.compose.ui.graphics.Color(0xFF30D158)
                    else androidx.compose.ui.graphics.Color.White.copy(alpha = .5f), fontSize = 15.sp)
                Icon(Icons.Rounded.ChevronRight, null, tint = androidx.compose.ui.graphics.Color.White.copy(alpha = .3f))
            }
        }
    }
}

/** Tweak preference page: main switch first, per-screen overrides (dimmed when off), credit, reset. */
@Composable private fun TweakPage(tweak: TweakFeature, state: LauncherState, model: LauncherModel) {
    val on = tweak.get(state)
    SettingsCard(tweak.name) {
        SettingsSwitch("Enabled", on, { tweak.set(model, it) }, "tweak-enabled-${tweak.id}")
        Text(tweak.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    SettingsCard("Use On") {
        Column(Modifier.alpha(if (on) 1f else .4f)) {
            FolioScreen.entries.forEach { screen ->
                val value = FeatureScopes.value(state.featureScopes, tweak.id, screen)
                IosMenuRow(screen.label, ScopeValue.entries.map { it to it.label }, value, { model.setFeatureScope(tweak.id, screen, it) }, enabled = on, tag = "scope-${tweak.id}-${screen.name.lowercase()}")
            }
        }
        Text("Default follows Enabled. On or Off applies only to that screen.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    SettingsCard("About") {
        Text("Inspired by ${tweak.inspiredBy}. Re-created from scratch; no tweak code is included.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = { model.resetTweak(tweak) }) { Text("Reset ${tweak.name}") }
    }
}

/** Themes (after SnowBoard): built-in looks with a live preview, plus saving and importing theme files. */
@Composable private fun ThemesPage(state: LauncherState, model: LauncherModel, stagedBitmap: android.graphics.Bitmap?) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    var undo by remember { mutableStateOf(model.themeUndo != null) }
    val save = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val ok = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching {
                context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(FolioTheme.of(state, "My Folio Theme").toJson().toString(2).toByteArray()) }
            }.isSuccess }
            message = if (ok) "Theme saved." else "The theme couldn't be saved."
        }
    }
    val open = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val theme = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)!!.use { it.readBytes().take(64_000).toByteArray().decodeToString() } }.getOrNull()?.let(FolioTheme::fromJson)
            }
            if (theme == null) message = "That file isn't a Folio theme."
            else { model.applyTheme(theme); undo = true; message = "Applied ${theme.name}." }
        }
    }
    MiniHomePreview(stagedBitmap, state, 220.dp)
    SheetGroup {
        FolioTheme.PRESETS.forEachIndexed { index, theme ->
            if (index > 0) MenuDivider()
            val current = state.looksLike(theme)
            Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { model.applyTheme(theme); undo = true; message = null }
                .padding(horizontal = 16.dp).testTag("theme-${theme.name.lowercase()}"), verticalAlignment = Alignment.CenterVertically) {
                Text(theme.name, color = androidx.compose.ui.graphics.Color.White, fontSize = 17.sp, modifier = Modifier.weight(1f))
                if (current) Icon(Icons.Rounded.Check, null, tint = IosBlue, modifier = Modifier.size(20.dp))
            }
        }
    }
    Text("A theme changes icons, badges, glass, text on Home and the status bar. Your apps, pages and widgets stay as they are.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
    SheetGroup {
        IosActionRow("Save Current Look as Theme…", "theme-save") { save.launch("folio-theme.json") }
        MenuDivider()
        IosActionRow("Import Theme…", "theme-import") { open.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
        if (undo) { MenuDivider(); IosActionRow("Undo Theme Change", "theme-undo") { model.undoTheme(); undo = false; message = null } }
    }
    message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp)) }
}

/** iOS Settings › Focus: the list of Focuses, with the one that's on. */
@Composable private fun FocusListPage(state: LauncherState, model: LauncherModel, onOpen: (String) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var access by remember { mutableStateOf(FocusController.hasAccess(context)) }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) { lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) { access = FocusController.hasAccess(context) } }
    SheetGroup {
        state.focusModes.forEachIndexed { index, mode ->
            if (index > 0) MenuDivider()
            TweakRow(mode.icon(), mode.color, mode.name, "focus-${mode.id}", if (state.activeFocus == mode.id) "On" else if (mode.schedule != null) "Scheduled" else null) { onOpen(mode.id) }
        }
    }
    Text("Focus lets you silence notifications, change how the phone looks and bring Home to the page you need. Turn one on here or from Control Center.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
    if (!access) {
        SheetGroup { IosActionRow("Allow Do Not Disturb Access…", "focus-allow-access") {
            runCatching { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
        } }
        Text("Without it, a Focus still changes Home but can't silence notifications or change the look.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
    }
}

@Composable private fun FocusModePage(mode: FocusMode, state: LauncherState, model: LauncherModel) {
    val on = state.activeFocus == mode.id
    SheetGroup {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(30.dp).clip(RoundedCornerShape(7.dp)).background(androidx.compose.ui.graphics.Color(mode.color)), contentAlignment = Alignment.Center) {
                Icon(mode.icon(), null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(19.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text(if (on) "On" else "Off", color = androidx.compose.ui.graphics.Color.White, fontSize = 17.sp, modifier = Modifier.weight(1f))
            IosSwitch(on, { model.setFocus(if (it) mode.id else null) }, Modifier.testTag("focus-switch-${mode.id}"))
        }
    }
    SettingsCard("Schedule") {
        val context = androidx.compose.ui.platform.LocalContext.current
        val schedule = mode.schedule
        SettingsSwitch("Turn On Automatically", schedule != null, { on ->
            model.updateFocusMode(mode.copy(schedule = if (!on) null else when (mode.id) {
                "sleep" -> FocusSchedule(22 * 60, 7 * 60)
                "work" -> FocusSchedule(9 * 60, 17 * 60, setOf(1, 2, 3, 4, 5))
                else -> FocusSchedule(9 * 60, 17 * 60)
            }))
        }, "focus-schedule")
        if (schedule != null) {
            val is24 = android.text.format.DateFormat.is24HourFormat(context)
            fun label(minute: Int) = java.time.LocalTime.of(minute / 60, minute % 60).format(java.time.format.DateTimeFormatter.ofPattern(if (is24) "HH:mm" else "h:mm a"))
            fun pick(minute: Int, onPicked: (Int) -> Unit) = android.app.TimePickerDialog(context, android.R.style.Theme_DeviceDefault_Dialog_Alert,
                { _, h, m -> onPicked(h * 60 + m) }, minute / 60, minute % 60, is24).show()
            listOf("From" to schedule.startMinute, "To" to schedule.endMinute).forEach { (name, minute) ->
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(10.dp)).clickable {
                    pick(minute) { picked -> model.updateFocusMode(mode.copy(schedule = if (name == "From") schedule.copy(startMinute = picked) else schedule.copy(endMinute = picked))) }
                }.testTag("focus-schedule-${name.lowercase()}"), verticalAlignment = Alignment.CenterVertically) {
                    Text(name, Modifier.weight(1f))
                    Text(label(minute), color = IosBlue, fontSize = 17.sp)
                }
            }
            // iOS day picker: one letter per day, filled when the schedule runs that day.
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                java.time.DayOfWeek.entries.forEach { day ->
                    val on = day.value in schedule.days
                    Box(Modifier.size(38.dp).clip(androidx.compose.foundation.shape.CircleShape)
                        .background(if (on) androidx.compose.ui.graphics.Color(mode.color) else androidx.compose.ui.graphics.Color.White.copy(alpha = .1f))
                        .clickable(onClickLabel = day.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault())) {
                            val days = if (on) schedule.days - day.value else schedule.days + day.value
                            if (days.isNotEmpty()) model.updateFocusMode(mode.copy(schedule = schedule.copy(days = days)))
                        }, contentAlignment = Alignment.Center) {
                        Text(day.getDisplayName(java.time.format.TextStyle.NARROW, java.util.Locale.getDefault()),
                            color = androidx.compose.ui.graphics.Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Text("${mode.name} turns on and off at these times. Turning it off early keeps it off until the next time it starts.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    SettingsCard("Notifications") {
        SettingsSwitch("Silence Notifications", mode.silence, { model.updateFocusMode(mode.copy(silence = it)) }, "focus-silence")
        Text("Calls and people allowed in Android's Do Not Disturb settings still come through.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    SettingsCard("Home Screen") {
        // The real page count: while a Focus hides pages, Home's own state is the filtered copy.
        val real by model.state.collectAsState()
        val pages = real.layout.pageCount
        Text("Show Pages", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IosChip(mode.pages == null, { model.updateFocusMode(mode.copy(pages = null)) }, label = { Text("All") })
            repeat(pages) { page ->
                val shown = mode.pages?.contains(page) == true
                IosChip(shown, {
                    val next = (mode.pages ?: emptySet()).let { if (shown) it - page else it + page }
                    model.updateFocusMode(mode.copy(pages = next.ifEmpty { null }))
                }, label = { Text("Page ${page + 1}") }, modifier = Modifier.testTag("focus-page-$page"))
            }
        }
        Text("Only these pages show while ${mode.name} is on. Editing Home is paused until it ends, so nothing moves on the hidden pages.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        IosMenuRow("Open On", listOf<Pair<Int?, String>>(null to "Any Page") + (0 until pages).filter { mode.pages == null || it in mode.pages }.map { it to "Page ${it + 1}" },
            mode.homePage, { model.updateFocusMode(mode.copy(homePage = it)) }, tag = "focus-open-on")
        Text("Home opens on this page while ${mode.name} is on.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (android.os.Build.VERSION.SDK_INT >= 35) SettingsCard("Look") {
        SettingsSwitch("Dim Wallpaper", mode.dimWallpaper, { model.updateFocusMode(mode.copy(dimWallpaper = it)) }, "focus-dim")
        SettingsSwitch("Dark Appearance", mode.darkTheme, { model.updateFocusMode(mode.copy(darkTheme = it)) }, "focus-dark")
        SettingsSwitch("Grayscale", mode.grayscale, { model.updateFocusMode(mode.copy(grayscale = it)) }, "focus-gray")
        Text("Android applies these while the Focus is on and puts things back when it ends.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable private fun CrashReportsCard() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var reports by remember { mutableStateOf(CrashLog.reports(context)) }
    SettingsCard(stringResource(R.string.crash_reports)) {
        Text(if (reports.isEmpty()) "No crashes recorded." else "${reports.size} saved on this phone. Nothing is sent unless you share it.",
            style = MaterialTheme.typography.bodyMedium)
        reports.firstOrNull()?.let { latest ->
            Text(latest.readLines().take(5).joinToString("\n"), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { runCatching { context.startActivity(CrashLog.shareIntent(latest).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) } }) {
                    Text(stringResource(R.string.share_latest))
                }
                TextButton(onClick = { CrashLog.clear(context); reports = emptyList() }) { Text(stringResource(R.string.clear)) }
            }
        }
    }
}

@Composable private fun CreditsPage() {
    val credits = listOf(
        "DuoLauncher" to "jakesgoodapps (github.com/jakesgoodapps/DuoLauncher) · MIT · Folio’s starting codebase: iPhone Duo-style Home layouts for both screens, widgets, folders, work profile, wallpapers and Discover",
        "iphone-duo" to "chuspeeism · MIT · fold blur and darkening model",
        "iPhone Duo on Galaxy Z Fold 8 demo" to "u/moomanjohnny · screenshot + shader idea (no code)",
        "QuickLaunch" to "AhmedTheGeek · Spotlight ideas (no code)",
        "Velox" to "Phillip Tennen · app panels idea",
        "Activator" to "Ryan Petrich · gestures and events idea",
        "Axon" to "Nepeta · notification app row idea",
        "Velvet" to "NoisyFlake & HiMyNameisUbik · tinted notifications idea",
        "ColorFlow" to "David Goldman · album art colors idea",
        "Harbor" to "Evan Swick · dock magnification idea",
        "SnowBoard" to "SparkDev · themes idea (no code)",
        "Apex" to "Sticktron · Icon Stacks idea (no code)",
        "Icon Restore" to "Layout History idea (no code)",
        "Lynx 2" to "recent-app dots idea (no code)",
        "ColorBadges" to "badges that match the app idea (no code)",
        "Barrel" to "Page Effects idea, coming soon (no code)",
        "Contributor Covenant 3.0" to "Organization for Ethical Source · CC BY-SA 4.0 · the project's code of conduct",
    )
    SettingsCard(stringResource(R.string.thanks_to)) {
        credits.forEach { (name, detail) ->
            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Text(name)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    Text(stringResource(R.string.tweak_ideas_were_re_created_from_scratch),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
}

@Composable private fun CustomizationDestination(icon: ImageVector, title: String, detail: String, tag: String, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).testTag(tag),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .52f), shape = RoundedCornerShape(20.dp)) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleMedium); Text(detail,
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Icon(Icons.Rounded.ChevronRight, null)
        }
    }
}

/**
 * Live preview of Home built from real data only: your Home and dock apps (with the current icon shape, pack,
 * tint, badges and live icons), your text, glass and dimming settings, and your background. Android's wallpaper
 * image can't be read by apps, so in that mode the preview uses the wallpaper's own reported colors and says so.
 */
@Composable private fun MiniHomePreview(stagedBitmap: android.graphics.Bitmap?, state: LauncherState,
    previewHeight: androidx.compose.ui.unit.Dp, @Suppress("UNUSED_PARAMETER") iconScale: Float = 1f) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val backgroundRevision = LauncherBackgroundCache.revision.intValue
    val committedBitmap = remember(backgroundRevision) { cachedLauncherBackground(context) }
    val bitmap = stagedBitmap ?: committedBitmap
    val apps = remember(state.apps) { state.apps.associateBy { it.id } }
    val tone = LocalWallpaperTone.current
    val ink = homeInkFor(state.homeInk, tone.prefersDarkText)
    val basePalette = LocalDuoPalette.current
    val glass = if (state.tintedGlass) tintedGlass(basePalette.glass, tone.primary) else basePalette.glass
    // Home page 1 drawn at a real cover-screen size with Folio's own layout math and parts (widget cards, icons,
    // status rail, dock, search pill), then scaled down, so the preview matches Home instead of approximating it.
    val refW = 420f; val refH = 720f
    val geometry = homeGeometry(refW, refH, state.compact, state.labels, statusHeight = if (state.verticalStatus) 180f else 0f, labelHeight = 20f)
    val placements = state.widgetPlacements.filter { it.page == 0 }
    val cells = HomeCellLayout.forPage(geometry, placements.map { it.row to it.spanY })
    val (iconSize, labels) = (state.pageStyles[0] ?: PageStyle()).apply(geometry, state.labels)
    val scale = previewHeight.value / refH
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.height(previewHeight).width(previewHeight * (refW / refH))
            .clip(RoundedCornerShape(26.dp * (previewHeight.value / 260f)))
            .border(1.5.dp, androidx.compose.ui.graphics.Color.White.copy(alpha = .18f), RoundedCornerShape(26.dp * (previewHeight.value / 260f)))
            .testTag("customization-home-preview"), contentAlignment = Alignment.Center) {
            Box(Modifier.requiredSize(refW.dp, refH.dp).graphicsLayer { scaleX = scale; scaleY = scale }) {
                if (state.systemWallpaper) Box(Modifier.matchParentSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(
                    androidx.compose.ui.graphics.Color(tone.primary ?: 0xFF5A6B78.toInt()), androidx.compose.ui.graphics.Color(tone.secondary ?: tone.primary ?: 0xFF2E3A42.toInt())))))
                else {
                    DuneWallpaper()
                    bitmap?.let { Image(it.asImageBitmap(), null, Modifier.matchParentSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop) }
                }
                if (state.dimWallpaperDark && basePalette.dark) Box(Modifier.matchParentSize().background(androidx.compose.ui.graphics.Color.Black.copy(alpha = .3f)))
                CompositionLocalProvider(LocalHomeInk provides ink, LocalDuoPalette provides basePalette.copy(glass = glass)) {
                    Box(Modifier.offset(x = 16.dp, y = geometry.contentTop.dp).width(geometry.gridWidth.dp).height((cells.height(GRID_ROWS)).dp)) {
                        placements.forEach { w ->
                            Box(Modifier.offset(x = (cells.x(w.column, w.row) + 5f).dp, y = cells.y(w.row).dp)
                                .size((geometry.cellWidth * w.spanX - 10f).dp, (cells.spanHeight(w.row, w.spanY) - 18f).coerceAtLeast(48f).dp)) {
                                if (w.id < 0) BuiltinWidgetCard(w.id, w.slot) {}
                                else Box(Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)).background(glass.copy(alpha = LocalGlassLook.current.widget)), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Rounded.Widgets, null, tint = ink.secondary, modifier = Modifier.size(32.dp))
                                }
                            }
                        }
                        repeat(HOME_CELLS) { local ->
                            val app = state.homeSlots.getOrNull(local)?.let(apps::get) ?: return@repeat
                            val row = local / GRID_COLUMNS
                            Column(Modifier.offset(x = cells.x(local % GRID_COLUMNS, row).dp, y = cells.y(row).dp).width(geometry.cellWidth.dp),
                                horizontalAlignment = Alignment.CenterHorizontally) {
                                AppIcon(app, null, Modifier.size(iconSize.dp), shape = RoundedCornerShape((iconSize * .24f).dp))
                                if (labels) Text(app.label, color = ink.primary, fontSize = 11.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(top = 4.dp, start = 2.dp, end = 2.dp),
                                    style = androidx.compose.ui.text.TextStyle(shadow = ink.labelShadow))
                            }
                        }
                    }
                    if (state.verticalStatus) StatusRail(DeviceStatus(battery = 80, wifiConnected = true, wifiLevel = 4, cellularLevel = 4),
                        Modifier.align(Alignment.TopEnd).padding(end = 12.dp).offset(y = geometry.contentTop.dp).width(state.compact.dockWidth.dp),
                        iconSize = dockIconSize(iconSize).dp, style = state.statusStyle)
                    Column(Modifier.align(Alignment.TopEnd).padding(end = 12.dp).offset(y = geometry.dockTop.dp).width(state.compact.dockWidth.dp)
                        .height(geometry.dockHeight.dp).background(glass.copy(alpha = state.statusStyle.railGlass), RoundedCornerShape(30.dp))
                        .border(1.dp, LocalGlassLook.current.outlineColor, RoundedCornerShape(30.dp)).padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        state.dock.forEach { id ->
                            Box(Modifier.fillMaxWidth().height(geometry.dockRowHeight.dp), contentAlignment = Alignment.Center) {
                                id?.let(apps::get)?.let { AppIcon(it, null, Modifier.size(dockIconSize(iconSize).dp), shape = RoundedCornerShape(11.dp)) }
                            }
                        }
                    }
                    if (state.searchPill) Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp, end = (state.compact.dockWidth + 24f).dp)) {
                        HomeSearchPill {}
                    }
                }
            }
        }
        if (state.systemWallpaper) Text(stringResource(R.string.colors_from_your_android_wallpaper_apps),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable private fun HomeLayoutSettings(state: LauncherState, wide: Boolean, onWide: (Boolean) -> Unit,
    model: LauncherModel, homePage: Int, onEditPins: () -> Unit, onWidget: (Int) -> Unit,
    onAddWidget: (Int) -> Unit, onRemoveWidget: (Int) -> Unit) {
    val p = if (wide) state.expanded else state.compact
    IosSegmented(listOf(false to stringResource(R.string.cover), true to stringResource(R.string.inner)), wide, onWide, Modifier.padding(vertical = 4.dp), tag = "layout-screen")
    var confirmIPhone by remember { mutableStateOf(false) }
    SheetGroup {
        IosActionRow(stringResource(R.string.choose_home_apps), onClick = onEditPins)
        MenuDivider()
        IosActionRow("Arrange Like iPhone…", "arrange-like-iphone", onClick = { confirmIPhone = true })
    }
    if (confirmIPhone) AlertDialog(onDismissRequest = { confirmIPhone = false },
        title = { Text("Arrange Like iPhone?") },
        text = { Text("Your first Home page and dock get iPhone’s layout (FaceTime, Calendar, Photos, Camera… with Phone, Safari, Messages and Music in the dock) using the matching apps on this phone. Apps already there move to your next page. You can undo this.") },
        confirmButton = { TextButton(onClick = { confirmIPhone = false; model.arrangeLikeIPhone() }) { Text("Arrange") } },
        dismissButton = { TextButton(onClick = { confirmIPhone = false }) { Text(stringResource(R.string.cancel)) } })
    CustomizationSlider("App icon size", "${p.iconSize.toInt()} dp", p.iconSize, 40f..68f) { model.setPreset(wide, p.copy(iconSize = it)) }
    CustomizationSlider("Space between rows", "${p.rowGap.toInt()} dp", p.rowGap, 0f..28f) { model.setPreset(wide, p.copy(rowGap = it)) }
    CustomizationSlider("Dock width", "${p.dockWidth.toInt()} dp", p.dockWidth, 56f..84f) { model.setPreset(wide, p.copy(dockWidth = it)) }
    SettingsSwitch(stringResource(R.string.align_dock_with_app_rows), p.dockAlignToGrid, { model.setPreset(wide, p.copy(dockAlignToGrid = it)) })
    if (!p.dockAlignToGrid) CustomizationSlider("Dock height on screen", "${(p.dockPosition * 100).toInt()}%", p.dockPosition, .25f.. .75f) { model.setPreset(wide, p.copy(dockPosition = it)) }
    SheetGroup { IosActionRow(stringResource(R.string.reset_this_layout), destructive = true, onClick = { model.setPreset(wide, LayoutPreset()) }) }
    // Per-page looks (after Atria): each page can have its own icon size and labels.
    SheetGroupLabel("Pages")
    // Real pages and styles (a Focus hiding pages renumbers the state Home draws), collected so the chips update.
    val real by model.state.collectAsState()
    val realPages = real.layout.pageCount
    SheetGroup {
        repeat(realPages) { page ->
            if (page > 0) MenuDivider()
            val style = real.pageStyles[page] ?: PageStyle()
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).testTag("page-style-$page"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Page ${page + 1}", color = androidx.compose.ui.graphics.Color.White, fontSize = 17.sp, modifier = Modifier.weight(1f))
                    if (page == homePage) Text("Showing", color = androidx.compose.ui.graphics.Color.White.copy(alpha = .5f), fontSize = 13.sp)
                }
                IosSegmented(PageStyle.SIZES.map { it.second to it.first }, style.iconScale, { model.setPageStyle(page, style.copy(iconScale = it)) }, tag = "page-size-$page")
                IosMenuRow("Labels", listOf<Pair<Boolean?, String>>(null to "Same as Home", true to "Show", false to "Hide"), style.labels,
                    { model.setPageStyle(page, style.copy(labels = it)) }, tag = "page-labels-$page")
            }
        }
    }
    Text("Changes how icons look on one page. Widgets, the grid and the dock stay the same on every page.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
    SheetGroupLabel("Widgets · Page ${homePage + 1}")
    SheetGroup {
        state.widgetPlacements.filter { it.page == homePage || (wide && it.page == -1) }.forEach { placement ->
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (placement.page == -1) "Unfolded-only page" else "${placement.spanX} × ${placement.spanY} widget · row ${placement.row + 1}", Modifier.weight(1f),
                    color = androidx.compose.ui.graphics.Color.White, fontSize = 17.sp)
                TextButton(onClick = { onWidget(placement.slot) }) { Text(stringResource(R.string.replace)) }
                IconButton(onClick = { onRemoveWidget(placement.slot) }, modifier = Modifier.semantics { contentDescription = if (placement.page == -1) "Remove widget from Unfolded-only page" else "Remove widget" }) {
                    Icon(Icons.Rounded.RemoveCircle, null, tint = androidx.compose.ui.graphics.Color(0xFFFF453A)) }
            }
            MenuDivider()
        }
        IosActionRow(stringResource(R.string.add_widget_to_this_page), onClick = { onAddWidget(homePage) })
    }
}

/** Keeps Android's pop-up and Folio's island message card from showing for the same message. */
@Composable private fun MessageBannerSettings(avoidDouble: Boolean, onAvoidDouble: (Boolean) -> Unit) {
    SettingsSwitch(stringResource(R.string.dont_double_up_with_android_pop_ups), avoidDouble, onAvoidDouble, "messages-avoid-double-switch")
    Text(if (avoidDouble) "Messages that Android already pops up are left to Android. Turn off Android\u2019s pop-up for an app below and its messages use the island instead (sound, badges and the notification list stay the same)."
        else "The island shows every new message, even when Android also shows its own pop-up.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (!avoidDouble) return
    MessageChannelList()
}

/** Messaging apps Folio has seen, and whether each one pops up through Android or the island. */
@Composable private fun MessageChannelList() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val channels by IslandListenerService.messageChannels.collectAsState()
    val list = channels.values.sortedWith(compareBy({ !it.popsUp }, { it.appLabel }))
    if (list.isEmpty()) Text(stringResource(R.string.messaging_apps_appear_here_after_they_po),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    list.forEach { channel ->
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(channel.appLabel, style = MaterialTheme.typography.bodyLarge)
                Text(listOfNotNull(channel.channelName, if (channel.popsUp) "Android pop-up" else "Island").joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // Both ways lead to the app's own notification settings: turn Android's pop-up off to use the island,
            // or back on to get Android's pop-up again (the only way back once an app was switched over).
            TextButton(onClick = { runCatching { context.startActivity(channel.settingsIntent()) } }) {
                Text(if (channel.popsUp) stringResource(R.string.use_island) else "Use Android")
            }
        }
    }
}

@Composable private fun SettingsSwitch(label: String, checked: Boolean, onChecked: (Boolean) -> Unit, tag: String? = null) {
    // One accessible element for TalkBack ("label, switch, on"); the whole row toggles.
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f)); IosSwitch(checked, onChecked, Modifier.then(if (tag != null) Modifier.testTag(tag) else Modifier))
    }
}

@Composable private fun CustomizationSlider(label: String, valueLabel: String, value: Float,
    range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column { Row { Text(label, Modifier.weight(1f)); Text(valueLabel, color = androidx.compose.ui.graphics.Color.White.copy(alpha = .6f)) }
        IosSlider(value, onChange, valueRange = range, modifier = Modifier.semantics { contentDescription = label }) }
}

@Composable private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, top = 6.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp), content = content)
    }
}

/** Folio's icon from its adaptive layers, so it gets an iOS rounded square rather than the device's icon mask. */
internal fun folioIconBitmap(context: android.content.Context, size: Int = 216): androidx.compose.ui.graphics.ImageBitmap? = runCatching {
    val adaptive = context.getDrawable(AppIconChoice.current(context).mipmap) as android.graphics.drawable.AdaptiveIconDrawable
    android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888).also { bitmap ->
        val canvas = android.graphics.Canvas(bitmap)
        listOfNotNull(adaptive.background, adaptive.foreground).forEach { layer ->
            layer.setBounds(-size / 4, -size / 4, size * 5 / 4, size * 5 / 4); layer.draw(canvas)
        }
    }.asImageBitmap()
}.getOrNull()

/** What's planned, from Folio's roadmap: a short, honest list (no dates), plus a way to suggest something. */
@Composable private fun ComingSoonPage() {
    val context = androidx.compose.ui.platform.LocalContext.current
    Text("Features being worked on for future updates. Plans can change, and Android doesn't allow everything iOS does.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
    SettingsCard("Coming Soon") {
        ComingSoonRow(Icons.Rounded.History, 0xFF8E8E93, "Layout History", "Go back to how Home looked before a big change, like a theme or Arrange Like iPhone.")
        MenuDivider()
        ComingSoonRow(Icons.Rounded.NotificationsActive, 0xFFFF3B30, "Notification Rules", "Choose per app where notifications show: Notification Center, the island or badges.")
        MenuDivider()
        ComingSoonRow(Icons.Rounded.Folder, 0xFF0A84FF, "Folder Options", "Bigger folder grids and different ways to open a folder.")
        MenuDivider()
        ComingSoonRow(Icons.Rounded.Dock, 0xFF30D158, "Dock Drawer", "Swipe in on the dock for recent apps and Now Playing.")
        MenuDivider()
        ComingSoonRow(Icons.Rounded.DarkMode, 0xFF5E5CE6, "Deeper Focus", "Hide badges and suggestions while a Focus is on.")
        MenuDivider()
        ComingSoonRow(Icons.Rounded.Schedule, 0xFFFF9F0A, "Complications", "A second time zone, sunset and your next alarm on the Lock Cover and StandBy.")
        MenuDivider()
        ComingSoonRow(Icons.Rounded.ViewCarousel, 0xFF32ADE6, "Page Effects", "3D effects when you swipe between Home pages, like a cube or a wheel. Inspired by Barrel.")
        MenuDivider()
        ComingSoonRow(Icons.Rounded.TouchApp, 0xFF30D158, "Back Tap", "Double or triple tap the back of your phone to open Spotlight, Control Center or an app. Inspired by RegiStar.")
    }
    SettingsCard("Further Out") {
        ComingSoonRow(Icons.Rounded.Brush, 0xFFFF375F, "Lock Designer", "Design your own Lock Cover and StandBy, with layouts for folded, unfolded and half folded.")
        MenuDivider()
        ComingSoonRow(Icons.Rounded.Keyboard, 0xFF8E8E93, "Folio Keyboard", "An iOS-style keyboard, with ideas from jailbreak keyboard tweaks.")
        MenuDivider()
        ComingSoonRow(Icons.Rounded.Extension, 0xFFBF5AF2, "Folio Tweaks", "Install tweaks made by the community, if it can be done safely.")
    }
    SheetGroup {
        IosActionRow("Suggest a Feature", "coming-soon-suggest") {
            runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,
                android.net.Uri.parse(BugReport.NEW_ISSUE + "?template=feature_request.yml"))) }
        }
    }
}

@Composable private fun ComingSoonRow(icon: ImageVector, color: Long, title: String, detail: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp).semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.Top) {
        Box(Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(androidx.compose.ui.graphics.Color(color)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = androidx.compose.ui.graphics.Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(detail, color = androidx.compose.ui.graphics.Color.White.copy(alpha = .62f), fontSize = 14.sp, lineHeight = 19.sp)
        }
    }
}

/** iOS-style alternate app icons: tap one to use it for Folio's app entry. */
@Composable private fun AppIconCard(onChanged: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var current by remember { mutableStateOf(AppIconChoice.current(context)) }
    SettingsCard("App Icon") {
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            AppIconChoice.entries.forEach { choice ->
                val bitmap = remember(choice) { runCatching {
                    val adaptive = context.getDrawable(choice.mipmap) as android.graphics.drawable.AdaptiveIconDrawable
                    android.graphics.Bitmap.createBitmap(180, 180, android.graphics.Bitmap.Config.ARGB_8888).also { b ->
                        val canvas = android.graphics.Canvas(b)
                        listOfNotNull(adaptive.background, adaptive.foreground).forEach { it.setBounds(-45, -45, 225, 225); it.draw(canvas) }
                    }.asImageBitmap()
                }.getOrNull() }
                val selected = choice == current
                Column(Modifier.clip(RoundedCornerShape(16.dp)).clickable {
                    if (!selected) { AppIconChoice.set(context, choice); current = choice; onChanged() }
                }.padding(6.dp).semantics { this.selected = selected; contentDescription = "${choice.label} app icon" }.testTag("app-icon-${choice.name.lowercase()}"),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(64.dp).then(if (selected) Modifier.border(2.5.dp, IosBlue, RoundedCornerShape(18.dp)).padding(4.dp) else Modifier.padding(4.dp))) {
                        bitmap?.let { androidx.compose.foundation.Image(it, null, Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp))) }
                    }
                    Text(choice.label, color = if (selected) IosBlue else androidx.compose.ui.graphics.Color.White, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
        Text("Changes Folio's icon in the App Library and other launchers.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Beta label beside a title, like TestFlight features. */
@Composable private fun BetaTag() {
    Text("BETA", color = androidx.compose.ui.graphics.Color(0xFFFF9F0A), fontSize = 11.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 8.dp).border(1.dp, androidx.compose.ui.graphics.Color(0xFFFF9F0A), RoundedCornerShape(5.dp))
            .padding(horizontal = 5.dp, vertical = 1.dp))
}

/** Layout History (Beta): automatic snapshots of Home before big changes, each restorable. */
@Composable private fun LayoutHistoryCard(state: LauncherState, model: LauncherModel, onClose: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) { LayoutHistory.load(context) }
    val snapshots by LayoutHistory.snapshots.collectAsState()
    var confirm by remember { mutableStateOf<LayoutSnapshot?>(null) }
    SettingsCard("Layout History") {
        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) { Text("Save Home Before Big Changes"); BetaTag() }
            IosSwitch(state.layoutHistory, model::setLayoutHistory, Modifier.testTag("layout-history-switch"))
        }
        Text("Before restoring a backup, Arrange Like iPhone or an older layout, Folio saves your Home here so you can go back. The last ${LayoutHistory.MAX} are kept on this phone.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (state.layoutHistory) {
            TextButton(onClick = { model.saveLayoutSnapshot("Saved by you", force = true) }, modifier = Modifier.testTag("layout-history-save")) { Text("Save Current Layout") }
            snapshots.forEach { snapshot ->
                MenuDivider()
                Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(snapshot.reason, fontSize = 16.sp)
                        Text(java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT).format(java.util.Date(snapshot.time)),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = { confirm = snapshot }) { Text("Restore") }
                }
            }
        }
    }
    confirm?.let { snapshot ->
        AlertDialog(onDismissRequest = { confirm = null },
            title = { Text("Restore This Layout?") },
            text = { Text("Home goes back to how it was (${snapshot.reason.lowercase()}). Your current layout is saved first, and apps you've since removed stay removed.") },
            confirmButton = { TextButton(onClick = { model.restoreLayoutSnapshot(snapshot); confirm = null; onClose() }) { Text("Restore") } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } })
    }
}

/** Recent-app dots (Beta): needs Usage Access, asked for right here when it's turned on. */
@Composable private fun RecentDotsCard(state: LauncherState, model: LauncherModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    SettingsCard("Dock") {
        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) { Text("Recent App Dots"); BetaTag() }
            IosSwitch(state.dockRecentDots, { on ->
                model.setDockRecentDots(on)
                if (on && !Suggestions.hasUsageAccess(context)) runCatching {
                    context.startActivity(Suggestions.usageAccessIntent(context).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }, Modifier.testTag("dock-recent-dots-switch"))
        }
        Text("A small dot beside dock apps you've used in the last hour. Android doesn't tell launchers which apps are running, so this uses Usage Access.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Settings › Wallpaper & Appearance › Glass: one style menu for most people, sliders to fine-tune. */
@Composable private fun GlassCardSettings(state: LauncherState, model: LauncherModel) {
    val presets = listOf("CLEAR" to .08f, "LIGHT" to .16f, "FROSTED" to .26f, "SOLID" to .55f)
    val rail = state.statusStyle.railGlass
    val current = presets.firstOrNull { (_, v) -> kotlin.math.abs(state.widgetGlass - v) < .005f && kotlin.math.abs(rail - v) < .005f }?.first ?: "CUSTOM"
    SettingsCard("Glass") {
        IosMenuRow("Style", listOf("CLEAR" to "Clear", "LIGHT" to "Light", "FROSTED" to "Frosted", "SOLID" to "Solid") +
            (if (current == "CUSTOM") listOf("CUSTOM" to "Custom") else emptyList()), current,
            { key -> presets.firstOrNull { it.first == key }?.let { model.setGlassPreset(it.second) } }, tag = "glass-style")
        CustomizationSlider("Widgets", "${(state.widgetGlass * 100).toInt()}%", state.widgetGlass, 0f..0.8f, model::setWidgetGlass)
        CustomizationSlider("Side Bar", "${(rail * 100).toInt()}%", rail, 0f..0.8f) { model.setStatusStyle(state.statusStyle.copy(railGlass = it)) }
        CustomizationSlider("Outline", if (state.glassOutline < .01f) "Off" else "${(state.glassOutline * 100).toInt()}%", state.glassOutline, 0f..0.5f, model::setGlassOutline)
        SettingsSwitch(stringResource(R.string.tint_glass_with_wallpaper_color), state.tintedGlass, model::setTintedGlass, "tinted-glass-switch")
        Text("Frost is how see-through widgets and the Side Bar are; the outline is the thin light edge around them.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
