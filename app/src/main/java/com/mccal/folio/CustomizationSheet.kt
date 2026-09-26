package com.mccal.folio

import androidx.compose.ui.platform.LocalDensity
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.pluralStringResource
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
import androidx.compose.ui.semantics.heading
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
import kotlin.math.roundToInt
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Where Settings was when it closed, so opening it again picks up there, like iPhone Settings. The page itself is
 * kept by Home; this holds the rest. In memory only, so a restart opens at the top.
 */
/** A page to open Settings on, set by something outside Settings (the setup reminder on Home). */
internal object SettingsLink { var page: CustomizationPage? = null }

internal object SettingsMemory {
    var tweakId = ""
    var focusId = ""
    var bodyScroll = 0
    var sidebarScroll = 0
}

internal enum class CustomizationPage { OVERVIEW, SETUP, WALLPAPER, HOME, STATUS, GESTURES, FOLD, BACKUP, HELP, SIDE_KEY, LOCK, CREDITS, TWEAKS, TWEAK, MARKET, ADVANCED, NOTIFICATIONS, SEARCH, TODAY, ISLAND, PERMISSIONS, FOCUS, FOCUS_MODE, THEMES, COMING_SOON, TWEAK_LIBRARY, SOFTWARE_UPDATE, LIBRARY_TWEAK, ISLAND_APPS, SUPPORTER, SUPPORTERS;

    /** The page Back returns to: the nav bar button and the system Back gesture both use it. */
    val parent: CustomizationPage get() = when (this) {
        TWEAK, TWEAK_LIBRARY -> TWEAKS
        LIBRARY_TWEAK -> TWEAK_LIBRARY // a tweak opened from the Tweak Library goes back there
        FOCUS_MODE -> FOCUS
        ISLAND_APPS -> ISLAND
        else -> OVERVIEW
    }

    /** The top-level page this one lives under, highlighted in the split view's sidebar. */
    val root: CustomizationPage get() = if (parent == OVERVIEW) this else parent.root
}

@Composable
internal fun CustomizationSheet(state: LauncherState, initiallyWide: Boolean, model: LauncherModel,
    isDefaultHome: Boolean, page: CustomizationPage, onPage: (CustomizationPage) -> Unit,
    onMakeDefault: () -> Unit, onClose: () -> Unit, onEditPins: () -> Unit, onWidget: (Int) -> Unit,
    onAddWidget: (Int) -> Unit, onRemoveWidget: (Int) -> Unit, onWallpaperPreview: () -> Unit,
    onExportLayout: () -> Unit, onImportLayout: () -> Unit, onSaveLayoutToFolder: (String) -> Unit = {},
    appearance: AppearanceState, onAppearanceMode: (AppearanceMode) -> Unit, onAppearanceAccent: (AccentChoice) -> Unit = {},
    onAppearanceManual: (String, Double, Double) -> Unit, onAppearanceDeviceLocation: () -> Unit,
    onAppearanceClear: () -> Unit, backgrounds: LauncherBackgroundController, homePage: Int = 0,
    onShadeSetup: () -> Unit = {},
    onOpenMarket: () -> Unit = {},
    onShowWelcome: () -> Unit = {},
    onShowWhatsNew: () -> Unit = {},
) {
    var wide by rememberSaveable { mutableStateOf(initiallyWide) }
    var tweakId by rememberSaveable { mutableStateOf(SettingsMemory.tweakId) }
    var focusId by rememberSaveable { mutableStateOf(SettingsMemory.focusId) }
    SideEffect { SettingsMemory.tweakId = tweakId; SettingsMemory.focusId = focusId }
    var settingsQuery by rememberSaveable { mutableStateOf("") }
    var namingBackup by remember { mutableStateOf(false) }
    val title = when (page) {
        CustomizationPage.OVERVIEW -> stringResource(R.string.folio)
        CustomizationPage.SETUP -> stringResource(R.string.setup_checklist)
        CustomizationPage.WALLPAPER -> stringResource(R.string.wallpaper_appearance)
        CustomizationPage.HOME -> stringResource(R.string.home_screen_dock)
        CustomizationPage.STATUS -> stringResource(R.string.icons_side_bar)
        CustomizationPage.GESTURES -> stringResource(R.string.gestures_actions)
        CustomizationPage.FOLD -> stringResource(R.string.fold_displays)
        CustomizationPage.BACKUP -> stringResource(R.string.backup)
        CustomizationPage.HELP -> stringResource(R.string.help)
        CustomizationPage.SIDE_KEY -> stringResource(R.string.side_key)
        CustomizationPage.LOCK -> stringResource(R.string.lock_cover)
        CustomizationPage.CREDITS -> stringResource(R.string.credits)
        CustomizationPage.SUPPORTERS -> stringResource(R.string.supporters)
        CustomizationPage.TWEAKS -> stringResource(R.string.tweaks)
        CustomizationPage.MARKET -> stringResource(R.string.market)
        CustomizationPage.FOCUS -> stringResource(R.string.focus)
        CustomizationPage.THEMES -> stringResource(R.string.themes)
        CustomizationPage.FOCUS_MODE -> state.focusModes.firstOrNull { it.id == focusId }?.name ?: stringResource(R.string.focus)
        CustomizationPage.TWEAK, CustomizationPage.LIBRARY_TWEAK -> TweakFeatures.firstOrNull { it.id == tweakId }?.name ?: stringResource(R.string.tweak)
        CustomizationPage.ADVANCED -> stringResource(R.string.advanced)
        CustomizationPage.NOTIFICATIONS -> stringResource(R.string.notifications_control_center)
        CustomizationPage.SEARCH -> stringResource(R.string.search_app_library)
        CustomizationPage.TODAY -> stringResource(R.string.today_view)
        CustomizationPage.ISLAND -> stringResource(R.string.dynamic_island)
        CustomizationPage.ISLAND_APPS -> stringResource(R.string.other_notifications)
        CustomizationPage.PERMISSIONS -> stringResource(R.string.privacy_permissions)
        CustomizationPage.COMING_SOON -> stringResource(R.string.roadmap)
        CustomizationPage.TWEAK_LIBRARY -> stringResource(R.string.tweak_library)
        CustomizationPage.SOFTWARE_UPDATE -> stringResource(R.string.software_update)
        CustomizationPage.SUPPORTER -> stringResource(R.string.supporter)
    }
    // Reopening Settings lands where it was, scrolled the same; opening another page starts at its top.
    val bodyScroll = rememberScrollState(SettingsMemory.bodyScroll)
    val sidebarScroll = rememberScrollState(SettingsMemory.sidebarScroll)
    var scrolledPage by remember { mutableStateOf(page) }
    LaunchedEffect(page) { if (page != scrolledPage) { scrolledPage = page; bodyScroll.scrollTo(0) } }
    DisposableEffect(Unit) { onDispose { SettingsMemory.bodyScroll = bodyScroll.value; SettingsMemory.sidebarScroll = sidebarScroll.value } }
    val setupSteps = rememberSetupSteps(isDefaultHome, onMakeDefault, onShadeSetup, state.messagesApp, model::setMessagesApp, state.systemWallpaper, model::setSystemWallpaper)
    val setupLeft = setupSteps.count { it.required && !it.done }
    val onBack = { onPage(page.parent) }
    val sheetContext = androidx.compose.ui.platform.LocalContext.current
    val nestedBackLabel = when (page.parent) { CustomizationPage.TWEAKS -> stringResource(R.string.tweaks); CustomizationPage.TWEAK_LIBRARY -> stringResource(R.string.tweak_library); CustomizationPage.FOCUS -> stringResource(R.string.focus); CustomizationPage.ISLAND -> stringResource(R.string.dynamic_island); else -> null }

    // The settings list. On the phone it's the first page; in the split view it's the sidebar, with the open page highlighted.
    val overviewRows: @Composable ColumnScope.(selected: CustomizationPage?, sidebar: Boolean) -> Unit = { selected, sidebar ->
                    SheetGroup {
                        TweakRow(Icons.Rounded.Wallpaper, FolioColors.Value.CyanLight, stringResource(R.string.wallpaper_appearance), "customization-wallpaper",
                            if (backgrounds.previewPending) stringResource(R.string.photo_ready) else null, selected = selected == CustomizationPage.WALLPAPER, chevron = !sidebar) { onPage(CustomizationPage.WALLPAPER) }
                        MenuDivider()
                        TweakRow(Icons.Rounded.GridView, FolioColors.Value.Blue, stringResource(R.string.home_screen_dock), "customization-home", selected = selected == CustomizationPage.HOME, chevron = !sidebar) { onPage(CustomizationPage.HOME) }
                        MenuDivider()
                        TweakRow(Icons.Rounded.Today, FolioColors.Value.Orange, stringResource(R.string.today_view), "customization-today", selected = selected == CustomizationPage.TODAY, chevron = !sidebar) { onPage(CustomizationPage.TODAY) }
                        MenuDivider()
                        TweakRow(Icons.Rounded.Palette, FolioColors.Value.Pink, stringResource(R.string.themes), "customization-themes",
                            FolioTheme.PRESETS.firstOrNull { state.looksLike(it) }?.name ?: stringResource(R.string.custom), selected = selected == CustomizationPage.THEMES, chevron = !sidebar) { onPage(CustomizationPage.THEMES) }
                        MenuDivider()
                        TweakRow(Icons.Rounded.Apps, FolioColors.Value.Indigo, stringResource(R.string.icons_side_bar), "customization-status", selected = selected == CustomizationPage.STATUS, chevron = !sidebar) { onPage(CustomizationPage.STATUS) }
                    }
                    SheetGroup {
                        TweakRow(Icons.Rounded.Circle, FolioColors.Value.SecondaryBackground, stringResource(R.string.dynamic_island), "customization-island", selected = selected == CustomizationPage.ISLAND, chevron = !sidebar) { onPage(CustomizationPage.ISLAND) }
                        MenuDivider()
                        TweakRow(Icons.Rounded.Notifications, FolioColors.Value.RedLight, stringResource(R.string.notifications_control_center), "customization-notifications", selected = selected == CustomizationPage.NOTIFICATIONS, chevron = !sidebar) { onPage(CustomizationPage.NOTIFICATIONS) }
                        MenuDivider()
                        TweakRow(Icons.Rounded.DarkMode, FolioColors.Value.Indigo, stringResource(R.string.focus), "customization-focus",
                            state.focusModes.firstOrNull { it.id == state.activeFocus }?.name, selected = selected == CustomizationPage.FOCUS, chevron = !sidebar) { onPage(CustomizationPage.FOCUS) }
                        MenuDivider()
                        TweakRow(Icons.Rounded.Search, FolioColors.Value.Gray, stringResource(R.string.search_app_library), "customization-search", selected = selected == CustomizationPage.SEARCH, chevron = !sidebar) { onPage(CustomizationPage.SEARCH) }
                        MenuDivider()
                        TweakRow(Icons.Rounded.Gesture, FolioColors.Value.Teal, stringResource(R.string.gestures_actions), "customization-gestures", selected = selected == CustomizationPage.GESTURES, chevron = !sidebar) { onPage(CustomizationPage.GESTURES) }
                    }
                    SheetGroup {
                        TweakRow(Icons.Rounded.TouchApp, FolioColors.Value.Orange, stringResource(R.string.side_key), "customization-side-key", selected = selected == CustomizationPage.SIDE_KEY, chevron = !sidebar) { onPage(CustomizationPage.SIDE_KEY) }
                        MenuDivider()
                        TweakRow(Icons.Rounded.Lock, FolioColors.Value.Green, stringResource(R.string.lock_cover), "customization-lock",
                            if (state.lockCover) stringResource(R.string.on) else stringResource(R.string.off), selected = selected == CustomizationPage.LOCK, chevron = !sidebar) { onPage(CustomizationPage.LOCK) }
                        MenuDivider()
                        TweakRow(Icons.Rounded.Devices, FolioColors.Value.Pink, stringResource(R.string.fold_displays), "customization-fold", selected = selected == CustomizationPage.FOLD, chevron = !sidebar) { onPage(CustomizationPage.FOLD) }
                    }
                    SheetGroup {
                        TweakRow(Icons.Rounded.AutoAwesome, FolioColors.Value.Purple, stringResource(R.string.tweaks), "customization-tweaks",
                            pluralStringResource(R.plurals.count_installed, state.installedTweaks.size, state.installedTweaks.size), selected = selected == CustomizationPage.TWEAKS, chevron = !sidebar) { onPage(CustomizationPage.TWEAKS) }
                        // The Market: Folio Dev shows it, and a supporter's code opens it (0.6.6).
                        if (MarketAccess.isOpen(sheetContext)) {
                            MenuDivider()
                            TweakRow(Icons.Rounded.Storefront, FolioColors.Value.Blue, stringResource(R.string.market), "customization-market",
                                selected = selected == CustomizationPage.MARKET, chevron = !sidebar) { onPage(CustomizationPage.MARKET) }
                        }
                    }
                    SheetGroup {
                        TweakRow(Icons.Rounded.Save, FolioColors.Value.Gray, stringResource(R.string.backup), "customization-backup", selected = selected == CustomizationPage.BACKUP, chevron = !sidebar) { onPage(CustomizationPage.BACKUP) }
                        MenuDivider()
                        TweakRow(Icons.Rounded.PanTool, FolioColors.Value.Blue, stringResource(R.string.privacy_permissions), "customization-permissions", selected = selected == CustomizationPage.PERMISSIONS, chevron = !sidebar) { onPage(CustomizationPage.PERMISSIONS) }
                        MenuDivider()
                        TweakRow(Icons.Rounded.Settings, FolioColors.Value.Gray, stringResource(R.string.advanced), "customization-advanced", selected = selected == CustomizationPage.ADVANCED, chevron = !sidebar) { onPage(CustomizationPage.ADVANCED) }
                    }
                    SheetGroup {
                        TweakRow(Icons.Rounded.HelpOutline, FolioColors.Value.Blue, stringResource(R.string.help), "customization-help", selected = selected == CustomizationPage.HELP, chevron = !sidebar) { onPage(CustomizationPage.HELP) }
                        MenuDivider()
                        TweakRow(Icons.Rounded.NewReleases, FolioColors.Value.Green, stringResource(R.string.what_s_new), "customization-whats-new", "v" + WhatsNew.currentVersion(androidx.compose.ui.platform.LocalContext.current), chevron = !sidebar) { onClose(); onShowWhatsNew() }
                        MenuDivider()
                        val updateStatus by SoftwareUpdate.status.collectAsState()
                        TweakRow(Icons.Rounded.SystemUpdate, FolioColors.Value.Gray, stringResource(R.string.software_update), "customization-software-update",
                            if (updateStatus is SoftwareUpdate.Status.Available) "1" else null, selected = selected == CustomizationPage.SOFTWARE_UPDATE, chevron = !sidebar) { onPage(CustomizationPage.SOFTWARE_UPDATE) }
                        MenuDivider()
                        TweakRow(Icons.Rounded.Map, FolioColors.Value.Indigo, stringResource(R.string.roadmap), "customization-coming-soon", selected = selected == CustomizationPage.COMING_SOON, chevron = !sidebar) { onPage(CustomizationPage.COMING_SOON) }
                        MenuDivider()
                        TweakRow(Icons.Rounded.Favorite, FolioColors.Value.Red, stringResource(R.string.credits), "customization-credits", selected = selected == CustomizationPage.CREDITS, chevron = !sidebar) { onPage(CustomizationPage.CREDITS) }
                        MenuDivider()
                        // The people who backed Folio, next to the people whose work it borrows from.
                        TweakRow(Icons.Rounded.Star, FolioColors.Value.Pink, stringResource(R.string.supporters), "customization-supporters", selected = selected == CustomizationPage.SUPPORTERS, chevron = !sidebar) { onPage(CustomizationPage.SUPPORTERS) }
                    }
                    SheetGroup {
                        val supportContext = androidx.compose.ui.platform.LocalContext.current
                        TweakRow(Icons.Rounded.LocalCafe, 0xFFFF5E5B, stringResource(R.string.support_folio), "customization-support", stringResource(R.string.ko_fi), chevron = !sidebar) {
                            runCatching { supportContext.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://ko-fi.com/mccal"))) }
                        }
                        if (Supporter.available(supportContext)) MenuDivider()
                        if (Supporter.available(supportContext)) TweakRow(Icons.Rounded.Redeem, FolioColors.Value.Purple, stringResource(R.string.supporter), "customization-supporter",
                            remember(supportContext) { Supporter.code(supportContext) }?.let { stringResource(R.string.code_added) },
                            selected = selected == CustomizationPage.SUPPORTER,
                            chevron = !sidebar) { onPage(CustomizationPage.SUPPORTER) }
                    }
                    CardNote(stringResource(R.string.folio_is_free_and_always_will_be_if_it_m), Modifier.padding(horizontal = FolioSpace.LARGE.dp))
    }
    // Home-app actions and the setup reminder: above the list on the phone, on Folio's own page in the split view.
    val overviewActions: @Composable ColumnScope.() -> Unit = {
                    if (!isDefaultHome || state.canUndoEdit) SheetGroup {
                        if (!isDefaultHome) IosActionRow(stringResource(R.string.set_as_home_app), "default-home-settings", onClick = onMakeDefault)
                        if (!isDefaultHome && state.canUndoEdit) MenuDivider()
                        if (state.canUndoEdit) IosActionRow(stringResource(R.string.undo_last_layout_change), onClick = { model.undoEdit(); onClose() })
                    }
                    if (setupLeft > 0) {
                        val required = setupSteps.count { it.required }
                        CustomizationDestination(Icons.Rounded.Checklist, stringResource(R.string.finish_setting_up_folio),
                            pluralStringResource(R.plurals.setup_steps_left_full, setupLeft, setupLeft), "customization-setup",
                            leading = { SetupRing(required - setupLeft, required, 40.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = .15f)) }) {
                            onPage(CustomizationPage.PERMISSIONS)
                        }
                    }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
    // iPad Settings / One UI on the unfolded screen: sidebar and page side by side, in either orientation.
    // Regular size class (both dimensions roomy), not a device check: the inner screen in either orientation.
    val fullWidth = maxWidth
    // The keyboard covers Settings; it doesn't make the window smaller (ADP-1, ADP-18). Measured out here, so that
    // tapping the search field can't turn the unfolded screen into a phone-sized one for as long as the keyboard is
    // up, take the sidebar away with it, and take the field you just tapped with the sidebar (#117).
    val keyboardDp = keyboardDpOverSheet()
    // With only one pane to spare (inside the Market beside its sidebar), Settings is the iPhone's: the list, and a
    // page pushed over it with Back.
    val split = settingsSplits(maxWidth.value, maxHeight.value, androidx.compose.ui.platform.LocalConfiguration.current.classScale,
        keyboardDp, LocalSettingsMaxColumns.current)
    // Takes the page to draw rather than reading the open one, so the split view can show a list and the thing you
    // picked from it side by side. Inside, `page` means "the page this column is drawing".
    val pageContent: @Composable ColumnScope.(CustomizationPage) -> Unit = { page ->
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
                    // The order is the one every well-regarded product uses, sourced in docs/mockups/wallpaper-page.html:
                    // the preview first because it is what the rest edits, then the picture, then the mode that dims it,
                    // then the colors, then the things drawn over it. App Icon lives on the Icons page. That is the
                    // iOS Wallpaper flow's shape rather than a Folio invention (DES-1), built from Folio's own rows,
                    // groups and cards (DES-9), with the preview standing in for iOS's chrome-on-preview.
                    //
                    // The preview shows the candidate when there is one, under Home's real chrome, which is what AOSP's
                    // small preview and iOS's Lock Screen preview both do. Art and photos apply the same way: a tap
                    // stages, Set writes.
                    // "Once" means once: leaving the page ends the offer as well as taking it.
                    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { backgrounds.offerTaken() } }
                    val candidate = backgrounds.pendingArtBitmap ?: backgrounds.previewBitmap
                    val staging = backgrounds.pendingArt != null || backgrounds.previewPending
                    if (!state.systemWallpaper) {
                        MiniHomePreview(candidate, state, 228.dp)
                        if (staging) SheetGroup {
                            IosActionRow(stringResource(R.string.set), "background-set",
                                enabled = candidate != null || backgrounds.pendingArt != null,
                                onClick = { if (backgrounds.previewPending) backgrounds.applyPreview() else backgrounds.setPendingArt() })
                            MenuDivider()
                            IosActionRow(stringResource(R.string.cancel), "background-cancel",
                                onClick = { if (backgrounds.previewPending) backgrounds.cancelPreview() else backgrounds.cancelPendingArt() })
                        } else if (backgrounds.justSet) SheetGroup {
                            // Once, after a Set, and behind Android's own preview: the phone's wallpaper cannot be read
                            // back at this targetSdk, so setting it cannot be undone from here.
                            IosActionRow(stringResource(R.string.also_set_as_phone_wallpaper), "wallpaper-preview",
                                onClick = { backgrounds.offerTaken(); onWallpaperPreview() })
                            CardNote(stringResource(R.string.set_as_phone_wallpaper_note))
                        }
                        if (backgrounds.loading) LinearProgressIndicator(Modifier.fillMaxWidth().testTag("background-loading"))
                    }
                    SettingsCard(stringResource(R.string.wallpaper)) {
                        IosSegmented(listOf(true to stringResource(R.string.android_wallpaper), false to stringResource(R.string.folio_background)),
                            state.systemWallpaper, { system -> model.setSystemWallpaper(system); wallpaperContext.asActivity()?.applyWallpaperWindow(system) },
                            Modifier.padding(vertical = FolioSpace.SNUG.dp), tag = "background-choice")
                        if (state.systemWallpaper) CardAction(stringResource(R.string.change_android_wallpaper), onClick = {
                            runCatching { wallpaperContext.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SET_WALLPAPER), wallpaperContext.getString(R.string.change_wallpaper))
                                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        }, modifier = Modifier.testTag("background-change-system"))
                        // Only Android's wallpaper drifts now, and the system moves it, so the switch lives with the
                        // wallpaper it moves. Folio's own picture has no spare width to slide (see DuneWallpaper).
                        if (state.systemWallpaper) SettingsSwitch(stringResource(R.string.wallpaper_moves_with_pages), state.wallpaperMotion, model::setWallpaperMotion, "wallpaper-motion-switch")
                        CardNote(if (state.systemWallpaper) stringResource(R.string.uses_the_same_wallpaper_as_your_phone_s)
                            else stringResource(R.string.wallpaper_group_note))
                    }
                    if (!state.systemWallpaper) {
                        BackgroundPicker(backgrounds)
                        (backgrounds.errorMessage ?: backgrounds.successMessage)?.let { message ->
                            TextButton(onClick = backgrounds::clearMessage, Modifier.fillMaxWidth().testTag("background-message")) { Text(message) }
                        }
                    }
                    AppearanceSettings(appearance, onAppearanceMode, onAppearanceManual, onAppearanceDeviceLocation,
                        onAppearanceClear, onAppearanceAccent) {
                        // A property of the mode, not of the text: One UI files "Dim wallpaper when Dark mode is on"
                        // right under its wallpaper and color entries, and that is where it reads correctly.
                        MenuDivider()
                        SettingsSwitch(stringResource(R.string.dark_appearance_dims_wallpaper), state.dimWallpaperDark, model::setDimWallpaperDark, "dim-wallpaper-switch")
                    }
                    SettingsCard(stringResource(R.string.text_on_home)) {
                        IosMenuRow(stringResource(R.string.text_color), listOf("AUTO" to stringResource(R.string.automatic), "LIGHT" to stringResource(R.string.light), "DARK" to stringResource(R.string.dark)), state.homeInk, model::setHomeInk, tag = "home-ink")
                        // Beside the text colour it exists for: the scrim is the other half of the same job, for a pale
                        // wallpaper that white text still has to read over.
                        SettingsSwitch(stringResource(R.string.darken_behind_text), state.homeScrim, model::setHomeScrim, "home-scrim-switch")
                        CardNote(stringResource(R.string.labels_status_page_dots_and_widget_text))
                    }
                    GlassCardSettings(state, model)
                    SettingsCard(stringResource(R.string.screen_corners)) {
                        SettingsSwitch(stringResource(R.string.rounded_corners), state.roundedCorners, model::setRoundedCorners, "rounded-corners-switch")
                        if (state.roundedCorners) CustomizationSlider(stringResource(R.string.size), stringResource(R.string.dp_value, state.cornerRadius.toInt()), state.cornerRadius, 16f..72f,
                            onChange = model::setCornerRadius)
                        CardNote(stringResource(R.string.draws_iphone_style_rounded_corners_over))
                    }
                }
                CustomizationPage.HOME -> {
                    HomeLayoutSettings(state, wide, { wide = it }, model, homePage, onEditPins, onWidget, onAddWidget, onRemoveWidget)
                    SettingsCard(stringResource(R.string.folders)) {
                        IosMenuRow(stringResource(R.string.columns), listOf(0 to stringResource(R.string.automatic), 3 to "3", 4 to "4"), state.folderColumns, model::setFolderColumns, tag = "folder-columns")
                        IosMenuRow(stringResource(R.string.background), FolderBackground.entries.map { it to stringResource(it.label) }, state.folderBackground, model::setFolderBackground, tag = "folder-background")
                    }
                    RecentDotsCard(state, model)
                }
                CustomizationPage.GESTURES, CustomizationPage.NOTIFICATIONS, CustomizationPage.SEARCH, CustomizationPage.TODAY -> {
                    if (page == CustomizationPage.GESTURES) SettingsCard(stringResource(R.string.gestures)) {
                        IosMenuRow(stringResource(R.string.animation_speed), MotionSpeed.entries.map { it to stringResource(it.label) }, state.motionSpeed, model::setMotionSpeed, tag = "motion-speed")
                        // Page Effects, for supporters until 0.6.8 (FeatureGate.PAGE_EFFECTS). None is the default.
                        val gestureContext = androidx.compose.ui.platform.LocalContext.current
                        val pageEffectsOpen = remember { FeatureGate.PAGE_EFFECTS.isOpen(gestureContext) }
                        if (pageEffectsOpen) {
                            IosMenuRow(stringResource(R.string.page_effects), PageEffect.entries.map { it to stringResource(it.label) },
                                state.pageEffect, model::setPageEffect, tag = "page-effect")
                            if (state.pageEffect != PageEffect.NONE) CardNote(stringResource(R.string.home_pages_turn_in_3d_as_you_swipe_off_w))
                        }
                        IosMenuRow(stringResource(R.string.swipe_down_on_home), listOf("SPOTLIGHT" to stringResource(R.string.spotlight), "NOTIFICATIONS" to stringResource(R.string.notification_center), "OFF" to stringResource(R.string.nothing)),
                            state.swipeDownHome, model::setSwipeDownHome, tag = "swipe-down-home")
                        SettingsSwitch(stringResource(R.string.drag_page_dots_to_flip_pages), state.pageScrub, model::setPageScrub, "page-scrub-switch")
                        SettingsSwitch(stringResource(R.string.haptic_feedback), state.haptics, model::setHaptics, "haptics-switch")
                        CardNote(stringResource(R.string.pull_down_from_the_top_left_for_notifica))
                    }
                    // One page for the panels: the on/off switch and, when on, their options.
                    if (page == CustomizationPage.NOTIFICATIONS) SettingsCard(stringResource(R.string.panels)) {
                        SettingsSwitch(stringResource(R.string.iphone_style_control_center_and_notifica), state.folioPanels, model::setFolioPanels, "folio-panels-switch")
                        if (state.folioPanels) {
                        CustomizationSlider(stringResource(R.string.background_blur), "${(state.panelBlur * 100).toInt()}%", state.panelBlur, 0f..1f) { model.setPanelBlur(it) }
                        SettingsSwitch(stringResource(R.string.big_clock_in_notification_center), state.notificationClock, model::setNotificationClock, "notification-clock-switch")
                        SettingsSwitch(stringResource(R.string.stack_notifications_by_app), state.groupNotifications, model::setGroupNotifications, "notification-group-switch")
                        SettingsSwitch(stringResource(R.string.unfolded_clock_beside_notifications), state.ncSplit, model::setNcSplit, "notification-split-switch")
                        IosMenuRow(stringResource(R.string.control_center_size), PanelSize.entries.map { it to stringResource(it.label) }, state.ccSize, model::setCcSize, tag = "cc-size")
                        SettingsSwitch(stringResource(R.string.unfolded_control_center_in_the_middle), state.ccCentered, model::setCcCentered, "cc-centered-switch")
                        CardNote(stringResource(R.string.tip_tap_at_the_top_of_control_center_to))
                        CardNote(stringResource(R.string.to_restyle_samsungs_own_pull_down_colors))
                        }
                    }
                    if (page == CustomizationPage.SEARCH) SettingsCard(stringResource(R.string.spotlight)) {
                        IosMenuRow(stringResource(R.string.search_with_enter), listOf(WebSearchTarget.GOOGLE.name to stringResource(R.string.google_no_ai), WebSearchTarget.DUCKDUCKGO.name to stringResource(R.string.duckduckgo)),
                            state.searchEngine, model::setSearchEngine, tag = "search-engine")
                        SpotlightSection.entries.forEach { section ->
                            SettingsSwitch(stringResource(section.title), section.name !in state.spotlightHidden,
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
                        CardNote(stringResource(R.string.pick_what_gestures_and_events_do_activat))
                        FolioTrigger.entries.forEach { trigger ->
                            val current = FolioAction.entries.firstOrNull { it.name == state.triggerActions[trigger.name] } ?: FolioAction.NONE
                            IosMenuRow(stringResource(trigger.label), FolioAction.entries.map { it to stringResource(it.label) }, current, { model.setTriggerAction(trigger, it) }, tag = "trigger-${trigger.name.lowercase()}")
                        }
                    }
                    if (page == CustomizationPage.TODAY) SettingsCard(stringResource(R.string.left_of_home)) {
                        // This used to ask for the screen to be rebuilt, through a cast that was always null in a
                        // sheet, so it never happened — and the pager has been following the page count from state
                        // on its own ever since. Restarting here would now throw you out of Settings to say so.
                        IosSegmented(listOf("TODAY" to stringResource(R.string.today_view), "DISCOVER" to stringResource(R.string.google_discover), "NONE" to stringResource(R.string.none)), state.leftPage,
                            { model.setLeftPage(it) }, Modifier.padding(vertical = FolioSpace.SNUG.dp), tag = "left-page")
                        CardNote(if (state.leftPage == "NONE") stringResource(R.string.nothing_to_the_left_of_home_swiping_righ)
                            else stringResource(R.string.today_view_is_iphone_s_widget_page_searc))
                        if (state.leftPage == "TODAY") {
                            IosMenuRow(stringResource(R.string.when_unfolded), listOf("PAGE" to stringResource(R.string.swipe_to_it), "BESIDE" to stringResource(R.string.beside_home), "OFF" to stringResource(R.string.none)),
                                state.todayUnfolded, model::setTodayUnfolded, tag = "today-unfolded")
                            CardNote(when (state.todayUnfolded) {
                                "BESIDE" -> stringResource(R.string.like_ipad_today_view_stays_on_the_left_o)
                                "OFF" -> stringResource(R.string.no_today_view_while_unfolded_it_s_still)
                                else -> stringResource(R.string.swipe_right_from_your_first_home_page_to)
                            })
                        }
                    }
                    if (page == CustomizationPage.SEARCH) SettingsCard(stringResource(R.string.app_library)) {
                        SettingsSwitch(stringResource(R.string.group_apps_into_categories), state.libraryCategories, model::setLibraryCategories, "library-categories-switch")
                        if (state.profiles.any { it.isWork }) SettingsSwitch(stringResource(R.string.work_apps), state.libraryWork, model::setLibraryWork, "library-work-switch")
                        HiddenAppsRow(state, model)
                        SettingsSwitch(stringResource(R.string.add_new_apps_to_home_screen), state.addNewAppsToHome, model::setAddNewAppsToHome, "add-new-apps-switch")
                        CardNote(stringResource(R.string.off_new_downloads_go_to_the_app_library))
                    }
                    if (page == CustomizationPage.SEARCH) SettingsCard(stringResource(R.string.search)) {
                        SettingsSwitch(stringResource(R.string.search_button_on_home), state.searchPill, model::setSearchPill, "search-pill-switch")
                        SettingsSwitch(stringResource(R.string.search_button_opens_the_google_app), state.googleSearch, model::setGoogleSearch, "google-search-switch")
                        CardNote(stringResource(R.string.when_off_the_search_button_opens_spotlig))
                    }
                }
                CustomizationPage.STATUS, CustomizationPage.ISLAND -> {
                    if (page == CustomizationPage.STATUS && !split) MiniHomePreview(backgrounds.previewBitmap, state, 210.dp)
                    val st = state.statusStyle
                    if (page == CustomizationPage.STATUS) AppIconCard(onChanged = { model.refresh() })
                    if (page == CustomizationPage.STATUS) SettingsCard(stringResource(R.string.app_icons)) {
                        val iconContext = androidx.compose.ui.platform.LocalContext.current
                        val packs = remember { IconPacks.installed(iconContext) }
                        IosMenuRow(stringResource(R.string.icon_pack), listOf<Pair<String?, String>>(null to stringResource(R.string.app_icons)) + packs.map { it.packageName to it.label },
                            state.iconPack, { IconPacks.clear(); model.setIconPack(it) }, tag = "icon-pack")
                        CardNote(stringResource(R.string.icon_packs_and_themes_are_made_by_indepe))
                        if (packs.isEmpty()) CardNote(stringResource(R.string.install_any_icon_pack_made_for_nova_styl))
                        CardAction(stringResource(R.string.refresh_icons), onClick = model::reloadIcons)
                        CardNote(stringResource(R.string.refresh_icons_note))
                        // Live Clock and Calendar: the app's own icon, or live icons that match the others, or always light/dark.
                        IosMenuRow(stringResource(R.string.clock_calendar), listOf("OFF" to stringResource(R.string.app_icons_2), "AUTO" to stringResource(R.string.live_automatic), "LIGHT" to stringResource(R.string.live_light), "DARK" to stringResource(R.string.live_dark)),
                            if (state.liveIcons) state.liveIconLook else "OFF",
                            { if (it == "OFF") model.setLiveIcons(false) else model.setLiveIconLook(it) }, tag = "live-icons-menu")
                        IosMenuRow(stringResource(R.string.shape), IconShape.entries.map { it to stringResource(it.label) }, state.iconShape, model::setIconShape, tag = "icon-shape")
                        IosMenuRow(stringResource(R.string.notification_badges), BadgeStyle.entries.map { it to stringResource(it.label) }, state.badgeStyle, model::setBadgeStyle, tag = "badge-style")
                        if (state.badgeStyle != BadgeStyle.OFF) {
                            IosMenuRow(stringResource(R.string.badge_color), BadgeColor.entries.map { it to stringResource(it.label) }, state.badgeColor, model::setBadgeColor, tag = "badge-color")
                            IosMenuRow(stringResource(R.string.badge_look), BadgeLook.entries.map { it to stringResource(it.label) }, state.badgeLook, model::setBadgeLook, tag = "badge-look")
                            IosMenuRow(stringResource(R.string.badge_size), BadgeSize.entries.map { it to stringResource(it.label) }, state.badgeSize, model::setBadgeSize, tag = "badge-size")
                            BadgePreviewRow(state)
                            // Clear Badges When Opened, for supporters until 0.6.8 (FeatureGate.BADGES_WHEN_OPENED).
                            val badgesWhenOpenedOpen = remember { FeatureGate.BADGES_WHEN_OPENED.isOpen(iconContext) }
                            if (badgesWhenOpenedOpen) {
                                SettingsSwitch(stringResource(R.string.clear_badges_when_opened), state.badgesWhenOpened,
                                    model::setBadgesWhenOpened, "badges-when-opened-switch")
                                CardNote(stringResource(R.string.a_badge_goes_away_when_you_open_the_app))
                            }
                        }
                        // iOS Home Screen customization: Default, Dark and Tinted side by side.
                        Text(stringResource(R.string.style), color = androidx.compose.ui.graphics.Color.White.copy(alpha = .6f), fontSize = FolioType.FOOTNOTE.sp, modifier = Modifier.padding(top = FolioSpace.SMALL.dp))
                        IosSegmented(IconStyle.entries.map { it to stringResource(it.label) }, state.iconStyle, { model.setIconStyle(it, state.iconTint) }, Modifier.padding(vertical = FolioSpace.TINY.dp), tag = "icon-style")
                        if (state.iconStyle == IconStyle.TINTED) Row(Modifier.fillMaxWidth().padding(vertical = FolioSpace.SNUG.dp), horizontalArrangement = Arrangement.spacedBy(FolioSpace.COMPACT.dp)) {
                            // Wallpaper color (follows the wallpaper when it changes)
                            val tone = LocalWallpaperTone.current
                            Box(Modifier.size(40.dp).clip(androidx.compose.foundation.shape.CircleShape)
                                .background(tone.primary?.let { androidx.compose.ui.graphics.Color(vividTint(it)) } ?: androidx.compose.ui.graphics.Color.Gray)
                                .then(if (state.iconTintFromWallpaper) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, androidx.compose.foundation.shape.CircleShape) else Modifier)
                                .clickable(role = androidx.compose.ui.semantics.Role.RadioButton) { model.setIconTintFromWallpaper(true) }
                                .description(R.string.wallpaper_color).semantics { selected = state.iconTintFromWallpaper },
                                contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.Wallpaper, null, tint = androidx.compose.ui.graphics.Color.Black.copy(alpha = .6f), modifier = Modifier.size(18.dp))
                            }
                            listOf(0xFFFFB340, FolioColors.Value.RedSoft, 0xFFFF7EB6, 0xFFBF8CFF, 0xFF64B5FF, 0xFF5EE0C4, 0xFF9BE15D, 0xFFE8E8E8).forEach { c ->
                                Box(Modifier.size(40.dp).clip(androidx.compose.foundation.shape.CircleShape).background(androidx.compose.ui.graphics.Color(c))
                                    .then(if (!state.iconTintFromWallpaper && state.iconTint == c) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, androidx.compose.foundation.shape.CircleShape) else Modifier)
                                    .clickable(role = androidx.compose.ui.semantics.Role.RadioButton) { model.setIconTintFromWallpaper(false); model.setIconStyle(IconStyle.TINTED, c) }
                                    .description(R.string.tint_color).semantics { selected = !state.iconTintFromWallpaper && state.iconTint == c })
                            }
                        }
                    }
                    if (page == CustomizationPage.STATUS) SettingsCard(stringResource(R.string.side_rail)) {
                        SettingsSwitch(stringResource(R.string.left_handed_layout_rail_on_the_left), state.leftHanded, model::setLeftHanded, "left-handed-switch")
                        SettingsSwitch(stringResource(R.string.show_app_names), state.labels, model::setLabels, "label-switch")
                        if (state.labels) IosMenuRow(stringResource(R.string.name_size), LabelSize.entries.map { it to stringResource(it.label) }, state.labelSize, model::setLabelSize, tag = "label-size")
                        CardNote(stringResource(R.string.frost_and_outline_are_in_wallpaper_appea))
                    }
                    if (page == CustomizationPage.STATUS) SettingsCard(stringResource(R.string.status)) {
                        SettingsSwitch(stringResource(R.string.show_status_in_the_rail), state.verticalStatus, model::setVerticalStatus, "status-switch")
                        if (state.verticalStatus) {
                            IosMenuRow(stringResource(R.string.icon_style), StatusGlyph.entries.map { it to stringResource(it.label) }, st.glyph, { model.setStatusStyle(st.copy(glyph = it)) }, tag = "status-glyph")
                            SettingsSwitch(stringResource(R.string.time), st.showTime, { model.setStatusStyle(st.copy(showTime = it)) }, "status-time")
                            SettingsSwitch(stringResource(R.string.date), st.showDate, { model.setStatusStyle(st.copy(showDate = it)) }, "status-date")
                            SettingsSwitch(stringResource(R.string.battery_percentage), st.showBatteryPercent, { model.setStatusStyle(st.copy(showBatteryPercent = it)) }, "status-percent")
                            CustomizationSlider(stringResource(R.string.status_spacing), when (st.spacing.roundToInt()) {
                                StatusStyle.COMPACT_SPACING.roundToInt() -> stringResource(R.string.compact); StatusStyle.STANDARD_SPACING.roundToInt() -> stringResource(R.string.standard)
                                else -> stringResource(R.string.dp_value, st.spacing.roundToInt()) }, st.spacing, 0f..16f, StatusStyle.STANDARD_SPACING, peek = true) { model.setStatusStyle(st.copy(spacing = it)) }
                            SettingsSwitch(stringResource(R.string.background), st.background, { model.setStatusStyle(st.copy(background = it)) }, "status-background")
                            SettingsSwitch(stringResource(R.string.silent_mode_icon), st.showSilent, { model.setStatusStyle(st.copy(showSilent = it)) }, "status-silent")
                            SettingsSwitch(stringResource(R.string.color_battery_when_charging_or_low), st.colorfulBattery, { model.setStatusStyle(st.copy(colorfulBattery = it)) }, "status-color")
                            CardNote(stringResource(R.string.status_colors_green_while_charging_orang))
                        }
                    }
                    if (page == CustomizationPage.ISLAND) SettingsCard(stringResource(R.string.in_every_app)) {
                        SettingsSwitch(stringResource(R.string.dock_handle_on_the_rail_edge), state.dockEverywhere, { on ->
                            model.setDockEverywhere(on); if (on && !SystemShadeAccessibilityService.isConnected()) onShadeSetup()
                        }, "dock-everywhere-switch")
                        SettingsSwitch(stringResource(R.string.dynamic_island), state.islandEverywhere, { on ->
                            model.setIslandEverywhere(on); if (on && !SystemShadeAccessibilityService.isConnected()) onShadeSetup()
                        }, "island-everywhere-switch")
                        if (state.islandEverywhere) {
                            // A pill over a full-screen film or game is in the way, so it steps aside by default.
                            SettingsSwitch(stringResource(R.string.hide_in_full_screen), state.islandHideFullScreen, model::setIslandHideFullScreen, "island-hide-full-screen-switch")
                            SettingsSwitch(stringResource(R.string.hide_in_landscape), state.islandHideLandscape, model::setIslandHideLandscape, "island-hide-landscape-switch")
                            CardNote(stringResource(R.string.the_island_comes_back_as_soon_as_the_app))
                        }
                        // For people who find the system's buttons too small, especially on the inner screen.
                        SettingsSwitch(stringResource(R.string.big_buttons), state.buttonBar, { on ->
                            model.setButtonBar(on); if (on && !SystemShadeAccessibilityService.isConnected()) onShadeSetup()
                        }, "button-bar-switch")
                        if (state.buttonBar) {
                            IosMenuRow(stringResource(R.string.size), listOf(44f to stringResource(R.string.standard), 52f to stringResource(R.string.large), 60f to stringResource(R.string.extra_large)), state.buttonBarHeight,
                                model::setButtonBarHeight, tag = "button-bar-size")
                            IosMenuRow(stringResource(R.string.width), listOf(.36f to stringResource(R.string.compact), .5f to stringResource(R.string.half_the_screen), .7f to stringResource(R.string.wide)), state.buttonBarWidth,
                                model::setButtonBarWidth, tag = "button-bar-width")
                            IosMenuRow(stringResource(R.string.order), listOf(false to stringResource(R.string.recents_home_back), true to stringResource(R.string.back_home_recents)), state.buttonBarAndroidOrder,
                                model::setButtonBarAndroidOrder, tag = "button-bar-order")
                            IosMenuRow(stringResource(R.string.look), listOf(false to stringResource(R.string.dark_glass), true to stringResource(R.string.light_glass)), state.buttonBarLight,
                                model::setButtonBarLight, tag = "button-bar-look")
                            SettingsSwitch(stringResource(R.string.fade_when_idle), state.buttonBarFade, model::setButtonBarFade, "button-bar-fade-switch")
                            val buttonContext = androidx.compose.ui.platform.LocalContext.current
                            CardAction(stringResource(R.string.put_the_buttons_back_at_the_bottom), onClick = { ButtonBarPosition.reset(buttonContext) })
                            CardNote(stringResource(R.string.long_press_and_drag_the_bar_to_move_it_u))
                            CardNote(stringResource(R.string.big_back_home_and_recents_buttons_float))
                            if (!gestureNavigation(androidx.compose.ui.platform.LocalContext.current))
                                CardNote(stringResource(R.string.android_s_three_buttons_are_on_so_you_ll))
                        }
                        CardNote(stringResource(R.string.uses_folios_accessibility_service_the_sa))
                    }
                    if (page == CustomizationPage.ISLAND) SettingsCard(stringResource(R.string.island)) {
                        val islandContext = androidx.compose.ui.platform.LocalContext.current
                        SettingsSwitch(stringResource(R.string.music_and_live_progress), state.island, { on ->
                            model.setIsland(on)
                            if (on && !IslandListenerService.hasAccess(islandContext))
                                runCatching { islandContext.startActivity(IslandListenerService.accessSettingsIntent(islandContext)) }
                        }, "island-switch")
                        if (state.island && state.verticalStatus) SettingsSwitch(stringResource(R.string.live_activities_under_the_status_bar), state.railActivities, model::setRailActivities, "rail-activities-switch")
                        if (state.island && !IslandListenerService.hasAccess(islandContext)) CardAction(stringResource(R.string.allow_notification_access), onClick = {
                            runCatching { islandContext.startActivity(IslandListenerService.accessSettingsIntent(islandContext)) }
                        })
                        if (state.island) {
                            CardAction(stringResource(R.string.put_the_island_back_at_the_camera), onClick = { IslandPosition.reset(islandContext) })
                            CardNote(stringResource(R.string.long_press_and_drag_the_island_to_move_i))
                        }
                    }
                    SettingsCard(stringResource(R.string.brief_pop_ups)) {
                        if (state.island) {
                            listOf("CHARGING" to stringResource(R.string.charging), "SILENT" to stringResource(R.string.silent_mode), "FOCUS" to stringResource(R.string.do_not_disturb), "BLUETOOTH" to stringResource(R.string.headphones_speakers), "MESSAGE" to stringResource(R.string.new_messages_with_quick_reply), "CALL" to stringResource(R.string.calls_answer_decline_end)).forEach { (kind, label) ->
                                SettingsSwitch(label, kind !in state.islandEventsOff, { model.setIslandEvent(kind, it) }, "island-event-${kind.lowercase()}")
                            }
                            if ("MESSAGE" !in state.islandEventsOff) MessageBannerSettings(state.messagesAvoidDouble, model::setMessagesAvoidDouble)
                            IslandAlertSettings(state.islandAlerts, state.islandAlertAppsOff, model::setIslandAlerts) { onPage(CustomizationPage.ISLAND_APPS) }
                        } else {
                            SettingsSwitch(stringResource(R.string.headphones_speakers), "BLUETOOTH" !in state.islandEventsOff, { model.setIslandEvent("BLUETOOTH", it) }, "island-event-bluetooth")
                            if (state.messagesAvoidDouble) {
                                // Apps that were switched to the island show no pop-up at all while the island is off.
                                CardNote(stringResource(R.string.message_pop_ups_come_from_the_island_whi))
                                MessageChannelList()
                            }
                        }
                        CardNote(stringResource(R.string.reads_only_music_calls_timers_navigation))
                    }
                }
                CustomizationPage.FOLD -> {
                    // What Folio has learned about how fast you fold (it adapts the animation to this).
                    val foldPrefs = androidx.compose.ui.platform.LocalContext.current.getSharedPreferences("folio", 0)
                    var learned by remember { mutableStateOf(foldPrefs.getFloat("fold_open_ms", 520f) to foldPrefs.getFloat("fold_close_ms", 650f)) }
                    SettingsCard(stringResource(R.string.your_fold_timing)) {
                        Text(stringResource(R.string.unfold_about_1_ms_fold_about_2_ms, learned.first.toInt(), learned.second.toInt()),
                            style = MaterialTheme.typography.bodyLarge)
                        CardAction(stringResource(R.string.reset_fold_timing), onClick = {
                            foldPrefs.edit().remove("fold_open_ms").remove("fold_close_ms").apply()
                            learned = 520f to 650f
                        })
                        CardNote(stringResource(R.string.learned_from_your_last_folds_and_used_to))
                    }
                    SettingsCard(stringResource(R.string.fold_animation)) {
                        SettingsSwitch(stringResource(R.string.fold_animation), state.foldEffect, model::setFoldEffect, "fold-effect-switch")
                        if (state.foldEffect) IosSegmented(listOf(false to stringResource(R.string.iphone_duo_fade), true to stringResource(R.string.screenshot_morph)),
                            state.foldSnapshot, model::setFoldSnapshot, Modifier.padding(vertical = FolioSpace.SNUG.dp), tag = "fold-style")
                        if (state.foldEffect && state.foldSnapshot) CardNote(stringResource(R.string.takes_a_quick_in_memory_snapshot_of_foli))
                        if (state.foldEffect) CustomizationSlider(stringResource(R.string.intensity), "${(state.foldIntensity * 100).toInt()}%",
                            state.foldIntensity, .3f..1.5f) { model.setFoldIntensity(it) }
                        if (state.foldEffect) FoldEffectPreview(state, backgrounds.previewBitmap)
                        CardNote(stringResource(R.string.your_fold_reports_only_a_few_hinge_posit))
                    }
                    SettingsCard(stringResource(R.string.standby)) {
                        SettingsSwitch(stringResource(R.string.show_standby_when_set_down_half_open), state.standBy, model::setStandBy, "standby-switch")
                        // The second way in is a supporter's until 0.6.8; the switch is hidden and, more to the point,
                        // StandBy doesn't look at the charger either while the gate is shut (REL-4a).
                        if (remember(sheetContext) { FeatureGate.STANDBY_CHARGING.isOpen(sheetContext) }) {
                            SettingsSwitch(stringResource(R.string.show_standby_while_charging), state.standByCharging,
                                model::setStandByCharging, "standby-charging-switch")
                            CardNote(stringResource(R.string.comes_on_half_a_minute_after_you_plug_th))
                        }
                        CardNote(stringResource(R.string.big_clock_date_next_alarm_battery_and_mu))
                    }
                    SettingsCard(stringResource(R.string.closing_from_home)) {
                        SettingsSwitch(stringResource(R.string.stay_awake_on_the_cover_screen), state.stayAwakeOnFold, model::setStayAwakeOnFold, "fold-awake-switch")
                        CardNote(stringResource(R.string.samsung_locks_the_phone_when_you_fold_on))
                    }
                }
                CustomizationPage.BACKUP -> {
                    if (namingBackup) BackupNameAlert(onCancel = { namingBackup = false }) { name -> namingBackup = false; onSaveLayoutToFolder(name) }
                    SheetGroup {
                        IosActionRow(stringResource(R.string.save_backup), "layout-save-folder", onClick = { namingBackup = true })
                        MenuDivider()
                        IosActionRow(stringResource(R.string.save_backup_to_files), "layout-export", onClick = onExportLayout)
                        MenuDivider()
                        IosActionRow(stringResource(R.string.restore_from_backup), "layout-import", onClick = onImportLayout)
                    }
                    // A backup carries Market packages too, but only a phone that has the Market is told so: the row
                    // that leads to it is hidden here as well, and naming a store the reader can't open explains
                    // nothing. What the backup actually saves doesn't change either way.
                    CardNote(
                        stringResource(
                            if (MarketAccess.isOpen(sheetContext)) R.string.save_the_current_home_layout_folders_wid_2
                            else R.string.save_the_current_home_layout_folders_wid,
                        ) + stringResource(R.string.restore_shows_a_review_before_changing_h),
                        Modifier.padding(horizontal = FolioSpace.TINY.dp),
                    )
                    LayoutHistoryCard(state, model, onClose)
                }
                CustomizationPage.SIDE_KEY -> SideKeyPage()
                CustomizationPage.LOCK -> {
                    SettingsCard(stringResource(R.string.lock_cover)) {
                        SettingsSwitch(stringResource(R.string.show_after_unlocking), state.lockCover, model::setLockCover, "lock-cover-switch")
                        CardNote(stringResource(R.string.android_doesn_t_let_apps_replace_the_rea))
                    }
                }
                CustomizationPage.CREDITS -> CreditsPage()
                CustomizationPage.SUPPORTERS -> SupportersPage()
                CustomizationPage.ISLAND_APPS -> IslandAlertApps(state.islandAlertAppsOff, state.messagesAvoidDouble, model::setIslandAlertApp)
                CustomizationPage.HELP -> {
                    // Getting help lives here rather than as more rows in the main list (fewer choices there).
                    val helpContext = androidx.compose.ui.platform.LocalContext.current
                    SheetGroup {
                        var askDiagnostics by remember { mutableStateOf(false) }
                        // Outside the dialog on purpose: closing the dialog is the first thing either button does, and
                        // a scope that went with it cancelled the report before it had been written.
                        val reportScope = rememberCoroutineScope()
                        TweakRow(Icons.Rounded.BugReport, FolioColors.Value.Red, stringResource(R.string.report_a_bug), "customization-report-bug") { askDiagnostics = true }
                        if (askDiagnostics) {
                            fun openForm() { runCatching { helpContext.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(BugReport.url(helpContext)))) } }
                            AlertDialog(onDismissRequest = { askDiagnostics = false },
                                title = { Text(stringResource(R.string.how_should_this_report_go)) },
                                text = { Text(stringResource(R.string.email_needs_no_account)) },
                                confirmButton = { TextButton(onClick = { askDiagnostics = false
                                    reportScope.launch { runCatching { helpContext.startActivity(Diagnostics.reportIntent(helpContext, email = true)) } } },
                                    modifier = Modifier.testTag("report-email")) { Text(stringResource(R.string.email_a_report)) } },
                                dismissButton = { TextButton(onClick = { askDiagnostics = false
                                    // Copied off the main thread first, so the form opens with the details ready to paste.
                                    reportScope.launch { Diagnostics.copy(helpContext); openForm() } },
                                    modifier = Modifier.testTag("report-copy-diagnostics")) { Text(stringResource(R.string.use_github_instead)) } })
                        }
                        MenuDivider()
                        TweakRow(Icons.Rounded.WavingHand, FolioColors.Value.Orange, stringResource(R.string.show_welcome_again), "customization-onboarding") { onClose(); onShowWelcome() }
                    }
                    CardNote(stringResource(R.string.report_a_bug_opens_github_in_your_browse), Modifier.padding(horizontal = FolioSpace.LARGE.dp))
                    LauncherHelp(
                        isDefaultHome = isDefaultHome,
                        onHomeSettings = onMakeDefault,
                        onAddWidget = { onAddWidget(homePage) },
                        onShadeSetup = onShadeSetup,
                    )
                }
                CustomizationPage.ADVANCED -> {
                    SettingsCard(stringResource(R.string.screenshot_mode)) {
                        val screenshot by ScreenshotMode.on.collectAsState()
                        SettingsSwitch(stringResource(R.string.screenshot_mode), screenshot, ScreenshotMode::set, "screenshot-mode-switch")
                        CardNote(stringResource(R.string.for_sharing_your_setup_folio_shows_9_41))
                    }
                    SettingsCard(stringResource(R.string.safe_mode)) {
                        CardNote(if (SafeMode.active) stringResource(R.string.folio_is_running_in_safe_mode_optional_f)
                            else stringResource(R.string.if_folio_closes_unexpectedly_twice_right))
                    }
                    CrashReportsCard()
                }
                CustomizationPage.MARKET -> {
                    Text(stringResource(R.string.market_page_intro),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = FolioSpace.TINY.dp))
                    SheetGroup { IosActionRow(stringResource(R.string.open_the_market), onClick = onOpenMarket) }
                    SheetGroupLabel(stringResource(R.string.featured_style))
                    val marketPrefs = remember(sheetContext) { rememberedMarketPrefs(sheetContext) }
                    var featuredStyle by remember { mutableStateOf(marketPrefs.featuredStyle) }
                    IosSegmented(
                        options = com.mccal.folio.market.FeaturedStyle.entries.map { it to stringResource(it.label) },
                        selected = featuredStyle,
                        onSelect = { chosen -> featuredStyle = chosen; marketPrefs.featuredStyle = chosen },
                        tag = "market-featured-style",
                    )
                    Text(stringResource(featuredStyle.description),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = FolioSpace.TINY.dp))
                    SheetGroup {
                        IosActionRow(stringResource(R.string.show_the_introduction_again)) { marketPrefs.introductionSeen = false }
                    }
                    // A code is redeemed in one place, Settings > Supporter, and this points at it rather than
                    // offering a second box that would take the same code for one feature only.
                    SheetGroupLabel(stringResource(R.string.early_access))
                    SheetGroup {
                        IosActionRow(stringResource(R.string.supporter)) { onPage(CustomizationPage.SUPPORTER) }
                    }
                    CardNote(stringResource(R.string.the_market_is_here_early_for_supporters))
                    SheetGroupLabel(stringResource(R.string.installing_apps))
                    var installApps by remember { mutableStateOf(marketPrefs.installApps) }
                    SheetGroup {
                        SwitchRow(
                            stringResource(R.string.installing_apps),
                            stringResource(if (installApps) R.string.folio_downloads_and_installs else R.string.open_play_f_droid_or_obtainium_instead),
                            installApps,
                        ) { installApps = it; marketPrefs.installApps = it }
                    }
                    CardNote(stringResource(R.string.some_listings_are_apps_of_their_own))
                    // The warning belongs on the switch, not buried in a sheet nobody reads twice.
                    if (installApps) CardNote(stringResource(R.string.turn_it_on_only_for_sources_you_would))
                    SheetGroupLabel(stringResource(R.string.market_refreshing_section))
                    var background by remember { mutableStateOf(marketPrefs.backgroundRefresh) }
                    var wifiOnly by remember { mutableStateOf(marketPrefs.refreshOnWifiOnly) }
                    SheetGroup {
                        SwitchRow(stringResource(R.string.refresh_in_the_background), stringResource(if (background) R.string.once_a_day else R.string.off), background) {
                            background = it
                            marketPrefs.backgroundRefresh = it
                            MarketRefreshJob.schedule(sheetContext)
                        }
                        if (background) {
                            MenuDivider()
                            SwitchRow(stringResource(R.string.only_on_wifi), stringResource(if (wifiOnly) R.string.never_uses_mobile_data else R.string.any_network), wifiOnly) {
                                wifiOnly = it
                                marketPrefs.refreshOnWifiOnly = it
                                MarketRefreshJob.schedule(sheetContext)
                            }
                        }
                    }
                    Text(stringResource(R.string.with_this_off_folio_only_goes_online),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = FolioSpace.TINY.dp))
                }
                CustomizationPage.TWEAKS -> {
                    CardNote(stringResource(R.string.features_inspired_by_ios_jailbreak_tweak), Modifier.padding(horizontal = FolioSpace.TINY.dp))
                    val tweaksContext = androidx.compose.ui.platform.LocalContext.current
                    val offered = remember { visibleTweaks(tweaksContext) }
                    val installed = offered.filter { it.id in state.installedTweaks }
                    if (installed.isNotEmpty()) SheetGroup {
                        installed.forEachIndexed { index, tweak ->
                            if (index > 0) MenuDivider()
                            TweakRow(tweak.icon, tweak.color, tweak.name, "tweak-${tweak.id}", if (tweak.get(state)) stringResource(R.string.on) else stringResource(R.string.off)) {
                                tweakId = tweak.id; onPage(CustomizationPage.TWEAK)
                            }
                        }
                    }
                    SheetGroup {
                        TweakRow(Icons.Rounded.Extension, FolioColors.Value.Purple, stringResource(R.string.tweak_library), "tweak-library",
                            (offered.size - installed.size).let { pluralStringResource(R.plurals.count_available, it, it) }) { onPage(CustomizationPage.TWEAK_LIBRARY) }
                    }
                }
                CustomizationPage.TWEAK_LIBRARY -> TweakLibraryPage(state, model) { tweakId = it.id; onPage(CustomizationPage.LIBRARY_TWEAK) }
                CustomizationPage.SOFTWARE_UPDATE -> SoftwareUpdatePage()
                CustomizationPage.SUPPORTER -> SupporterPage()
                CustomizationPage.PERMISSIONS -> PermissionsPage(isDefaultHome, onMakeDefault, onShadeSetup)
                CustomizationPage.THEMES -> ThemesPage(state, model, backgrounds.previewBitmap)
                CustomizationPage.FOCUS -> FocusListPage(state, model) { focusId = it; onPage(CustomizationPage.FOCUS_MODE) }
                CustomizationPage.FOCUS_MODE -> state.focusModes.firstOrNull { it.id == focusId }?.let { FocusModePage(it, state, model) }
                    ?: LaunchedEffect(Unit) { onPage(CustomizationPage.FOCUS) }
                CustomizationPage.TWEAK, CustomizationPage.LIBRARY_TWEAK -> TweakFeatures.firstOrNull { it.id == tweakId }?.let { tweak -> TweakPage(tweak, state, model) }
                    ?: LaunchedEffect(Unit) { onPage(CustomizationPage.TWEAKS) }
            }
    }
    if (!split) Column(Modifier.fillMaxSize().padding(horizontal = FolioSpace.LARGE.dp)) {
        SettingsNavBar(if (page == CustomizationPage.OVERVIEW) null else nestedBackLabel ?: stringResource(R.string.folio), onBack, onClose)
        if (page != CustomizationPage.OVERVIEW) SettingsLargeTitle(title)
        Column(Modifier.weight(1f).edgeFade(bodyScroll).verticalScroll(bodyScroll).padding(bottom = FolioSpace.XL.dp),
            verticalArrangement = Arrangement.spacedBy(FolioSpace.COMPACT.dp)) { pageContent(page) }
    } else {
        // Half folded with the fold running down the screen, the divider goes on the fold, so no row sits on the
        // crease (iPhone Duo: controls move away from the fold). Flat, it's a share of the width.
        val hinge = LocalHinge.current?.takeIf { it.active && it.vertical }
        val density = LocalDensity.current
        val onFold = hinge?.let { with(density) { it.startPx.toDp() } }?.takeIf { it >= 280.dp && it <= fullWidth * .7f }
        val columns = settingsColumns(
            maxWidth.value, maxHeight.value, androidx.compose.ui.platform.LocalConfiguration.current.classScale,
            nested = page.parent != CustomizationPage.OVERVIEW,
            onFold = onFold != null,
            keyboardDp = keyboardDp,
        ).coerceAtMost(LocalSettingsMaxColumns.current)
        val tiled = columns >= 2
        val threeColumns = columns == 3
        // Tiled it shares the width; as an overlay it can be a little wider so rows don't wrap. With three, the list
        // of settings gives up some width so the other two stay readable.
        val sidebarWidth = onFold
            ?: when {
                threeColumns -> (fullWidth * .28f).coerceIn(260.dp, 320.dp)
                tiled -> (fullWidth * .4f).coerceIn(280.dp, 380.dp)
                else -> minOf(360.dp, fullWidth * .6f)
            }
        val middleWidth = ((fullWidth - sidebarWidth) * .44f).coerceIn(280.dp, 380.dp)
        val middleScroll = rememberScrollState()
        var sidebarOpen by rememberSaveable { mutableStateOf(tiled || page == CustomizationPage.OVERVIEW) }
        var shownPage by remember { mutableStateOf(page) }
        // Opening a page slides the list away; coming back to the top brings it back, since the list is all that page has.
        SideEffect { if (page != shownPage) { shownPage = page; if (!tiled) sidebarOpen = page == CustomizationPage.OVERVIEW } }
        val sidebar: @Composable () -> Unit = {
            Column(Modifier.width(sidebarWidth).fillMaxHeight().edgeFade(sidebarScroll).verticalScroll(sidebarScroll)
                .padding(horizontal = FolioSpace.LARGE.dp).padding(top = 44.dp, bottom = FolioSpace.XL.dp).testTag("settings-sidebar"), verticalArrangement = Arrangement.spacedBy(FolioSpace.COMPACT.dp)) {
                SettingsLargeTitle(stringResource(R.string.folio))
                SettingsSearchField(settingsQuery) { settingsQuery = it }
                if (settingsQuery.isNotBlank()) SettingsSearchResults(settingsQuery, onOpen = { onPage(it) })
                else {
                    // Like the account card at the top of iPad Settings: Folio's own page.
                    SheetGroup { SidebarAppRow(selected = page == CustomizationPage.OVERVIEW, setupLeft) { onPage(CustomizationPage.OVERVIEW) } }
                    overviewRows(page.root, true)
                }
            }
        }
        val divider: @Composable () -> Unit = {
            Box(Modifier.fillMaxHeight().width(.5.dp).background(androidx.compose.ui.graphics.Color.White.copy(alpha = .14f)))
        }
        Row(Modifier.fillMaxSize()) {
            if (tiled) {
                sidebar()
                divider()
            }
            // The list you picked from stays where it was, and what you picked opens to the right of it - the way
            // Mail and Notes use an iPad's width. Only for a page that came from a list, so a column is never empty.
            if (threeColumns) {
                Column(Modifier.width(middleWidth).fillMaxHeight().padding(horizontal = FolioSpace.XL.dp)) {
                    Spacer(Modifier.height(44.dp))
                    nestedBackLabel?.let { SettingsLargeTitle(it) }
                    Column(Modifier.weight(1f).edgeFade(middleScroll).verticalScroll(middleScroll).padding(bottom = FolioSpace.XL.dp),
                        verticalArrangement = Arrangement.spacedBy(FolioSpace.COMPACT.dp)) { pageContent(page.parent) }
                }
                divider()
            }
            Column(Modifier.weight(1f).fillMaxHeight().padding(horizontal = FolioSpace.XL.dp)) {
                // With the list still on screen there's nothing for Back to reveal, so the bar keeps only Done. Without
                // it, every page but the first gets a way back, not only the sidebar button, which doesn't read as one.
                SettingsNavBar(if (threeColumns) null else nestedBackLabel
                    ?: if (!tiled && page != CustomizationPage.OVERVIEW) stringResource(R.string.folio) else null, onBack, onClose,
                    leading = if (tiled) null else ({ SidebarButton { sidebarOpen = !sidebarOpen } }))
                if (page != CustomizationPage.OVERVIEW) SettingsLargeTitle(title)
                Column(Modifier.weight(1f).edgeFade(bodyScroll).verticalScroll(bodyScroll).padding(bottom = FolioSpace.XL.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Column(Modifier.widthIn(max = 720.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(FolioSpace.COMPACT.dp)) {
                        // The space beside the list shows what the page changes, drawn from your real Home.
                        if (page == CustomizationPage.HOME && wide) UnfoldedHomePreview(backgrounds.previewBitmap, state, 200.dp)
                        else if (page == CustomizationPage.HOME || page == CustomizationPage.STATUS)
                            MiniHomePreview(backgrounds.previewBitmap, state, 240.dp)
                        pageContent(page)
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
                Box(Modifier.fillMaxHeight().background(FolioColors.SecondaryBackground)) { sidebar() }
            }
        }
    }
    }
}

/** The iPad/iPhone Duo sidebar toggle: shows or hides the settings list over the page. */
@Composable private fun SidebarButton(onClick: () -> Unit) {
    Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).clickable(onClickLabel = stringResource(R.string.show_or_hide_the_settings_list), onClick = onClick)
        .testTag("settings-sidebar-toggle"), contentAlignment = Alignment.Center) {
        Icon(Icons.Rounded.ViewSidebar, null, tint = LocalAccent.current.ink, modifier = Modifier.size(26.dp))
    }
}

/** A row with a title, a line under it and a switch: the shape most Folio settings take. */
@Composable private fun SwitchRow(title: String, value: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = FolioSpace.COMFY.dp, vertical = FolioSpace.COMPACT.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = androidx.compose.ui.graphics.Color.White, fontSize = FolioType.BODY.sp)
            value?.let {
                Text(it, color = androidx.compose.ui.graphics.Color.White.copy(alpha = .55f), fontSize = FolioType.FOOTNOTE.sp)
            }
        }
        IosSwitch(checked, onChange, Modifier.testTag("switch-${title.lowercase().replace(' ', '-')}"))
    }
}

@Composable private fun SettingsNavBar(backLabel: String?, onBack: () -> Unit, onClose: () -> Unit, leading: (@Composable () -> Unit)? = null) {
    // iOS navigation bar: the sidebar button and "‹ Back" on the left, Done on the right.
    Box(Modifier.fillMaxWidth().heightIn(min = 44.dp)) {
        Row(Modifier.align(Alignment.CenterStart), verticalAlignment = Alignment.CenterVertically) {
            leading?.invoke()
            if (backLabel != null) Row(Modifier.clip(RoundedCornerShape(FolioRadius.CONTROL.dp))
                .clickable(onClick = onBack).padding(vertical = FolioSpace.SMALL.dp, horizontal = FolioSpace.HAIR.dp).testTag("customization-back"),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.ChevronLeft, null, tint = LocalAccent.current.ink, modifier = Modifier.size(28.dp))
                Text(backLabel, color = LocalAccent.current.ink, fontSize = FolioType.BODY.sp)
            }
        }
        Text(stringResource(R.string.done), color = LocalAccent.current.ink, fontSize = FolioType.BODY.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.CenterEnd).clip(RoundedCornerShape(FolioRadius.CONTROL.dp)).clickable(onClick = onClose)
                .padding(horizontal = FolioSpace.SMALL.dp, vertical = FolioSpace.SMALL.dp).description(R.string.close_customization))
    }
}

@Composable private fun SettingsLargeTitle(title: String) = Text(title, color = androidx.compose.ui.graphics.Color.White, fontSize = 32.sp,
    fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = FolioSpace.TINY.dp, bottom = FolioSpace.SMALL.dp))

/** Sidebar header row: Folio's icon, name and setup state, like the account card in iPad Settings. */
@Composable private fun SidebarAppRow(selected: Boolean, setupLeft: Int, onClick: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val icon = remember { folioIconBitmap(context) }
    // iPad Settings: the selection is a rounded highlight inset from the group's edges, not a square band.
    Row(Modifier.fillMaxWidth().padding(FolioSpace.TINY.dp).clip(RoundedCornerShape(12.dp)).background(if (selected) LocalAccent.current.fill else androidx.compose.ui.graphics.Color.Transparent).clickable(onClick = onClick)
        .padding(horizontal = FolioSpace.COMFY.dp, vertical = FolioSpace.COMPACT.dp).testTag("settings-sidebar-folio"), verticalAlignment = Alignment.CenterVertically) {
        icon?.let { Image(it, null, Modifier.size(52.dp).clip(RoundedCornerShape(12.dp))) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.folio), color = androidx.compose.ui.graphics.Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text(if (setupLeft > 0) pluralStringResource(R.plurals.setup_steps_left, setupLeft, setupLeft) else stringResource(R.string.home_panels_and_tweaks),
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
        HelpTip(Icons.Rounded.Home, FolioColors.Value.Blue, stringResource(R.string.edit_home), stringResource(R.string.hold_an_app_for_its_menu_or_move_while_h))
        MenuDivider()
        HelpTip(Icons.Rounded.Widgets, FolioColors.Value.Indigo, stringResource(R.string.widgets_smart_stacks), stringResource(R.string.hold_a_widget_and_let_go_for_sizes_stack))
        MenuDivider()
        HelpTip(Icons.Rounded.SwipeDown, FolioColors.Value.RedLight, stringResource(R.string.notifications_control_center), stringResource(R.string.pull_down_from_the_top_left_or_top_right))
        MenuDivider()
        HelpTip(Icons.Rounded.Circle, FolioColors.Value.SecondaryBackground, stringResource(R.string.dynamic_island), stringResource(R.string.tap_it_for_details_hold_and_drag_to_move))
        MenuDivider()
        HelpTip(Icons.Rounded.Devices, FolioColors.Value.Pink, stringResource(R.string.folding), stringResource(R.string.folio_fades_between_screens_and_keeps_th))
    }
    SheetGroup {
        IosActionRow(stringResource(R.string.add_widget_to_this_page_2), "help-add-widget", onClick = onAddWidget)
    }
    // The person behind Folio, for anything a bug report doesn't cover.
    val helpContext = androidx.compose.ui.platform.LocalContext.current
    SheetGroup {
        IosActionRow(stringResource(R.string.email_the_developer), "help-contact-email") {
            runCatching { helpContext.startActivity(android.content.Intent(android.content.Intent.ACTION_SENDTO, android.net.Uri.parse("mailto:contact@mcc-cal.com"))
                .putExtra(android.content.Intent.EXTRA_SUBJECT, "Folio").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }
}

@Composable
private fun HelpTip(icon: ImageVector, color: Long, title: String, detail: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = FolioSpace.COMFY.dp, vertical = FolioSpace.COMPACT.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(30.dp).clip(RoundedCornerShape(7.dp)).background(androidx.compose.ui.graphics.Color(color)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = androidx.compose.ui.graphics.Color.White, fontSize = FolioType.BODY.sp)
            Text(detail, color = androidx.compose.ui.graphics.Color.White.copy(alpha = .55f), fontSize = 14.sp)
        }
    }
}




/** Tweak-style header: Folio's icon, name and version, like a jailbreak tweak's preference banner. */
@Composable private fun TweakBanner() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "" }
    Column(Modifier.fillMaxWidth().padding(top = FolioSpace.TINY.dp, bottom = FolioSpace.SNUG.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        // Folio's own launcher icon, like a tweak's preference banner.
        val icon = remember { folioIconBitmap(context) }
        if (icon != null) androidx.compose.foundation.Image(icon, null, Modifier.size(72.dp).clip(RoundedCornerShape(18.dp)))
        Text(stringResource(R.string.folio), color = androidx.compose.ui.graphics.Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = FolioSpace.SMALL.dp))
        Text(stringResource(R.string.iphone_duo_for_your_fold_v, version), color = androidx.compose.ui.graphics.Color.White.copy(alpha = .55f), fontSize = 14.sp)
    }
}

/** iOS Settings row: colored rounded icon square, title, optional value, chevron. */
@Composable private fun TweakRow(icon: ImageVector, color: Long, title: String, tag: String, value: String? = null,
    selected: Boolean = false, chevron: Boolean = true, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
        .then(if (selected) Modifier.padding(horizontal = 5.dp, vertical = FolioSpace.HAIR.dp).clip(RoundedCornerShape(FolioRadius.CONTROL.dp)).background(LocalAccent.current.fill) else Modifier)
        // The inset is taken back from the content padding, so the icon and title don't shift when selected.
        .clickable(onClick = onClick).padding(horizontal = if (selected) 9.dp else FolioSpace.COMFY.dp, vertical = if (selected) FolioSpace.SNUG.dp else FolioSpace.SMALL.dp).testTag(tag).semantics { this.selected = selected },
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(30.dp).clip(RoundedCornerShape(7.dp)).background(androidx.compose.ui.graphics.Color(color)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(12.dp))
        // A long title wraps to two lines rather than being cut off — iOS does the same in a narrow window or at a
        // large text size — and the value drops under it when there isn't room beside it.
        val config = androidx.compose.ui.platform.LocalConfiguration.current
        val tight = config.screenWidthDp < 360 || config.fontScale >= 1.3f
        val valueColor = androidx.compose.ui.graphics.Color.White.copy(alpha = if (selected) .85f else .5f)
        Column(Modifier.weight(1f)) {
            Text(
                title, color = androidx.compose.ui.graphics.Color.White, fontSize = FolioType.BODY.sp,
                maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            if (tight) value?.let { Text(it, color = valueColor, fontSize = FolioType.SUBHEAD.sp, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }
        }
        if (!tight) value?.let {
            Text(it, color = valueColor, fontSize = FolioType.BODY.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = FolioSpace.SMALL.dp))
        }
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
        SideKeyStep("1. Folio is your digital assistant", stringResource(R.string.settings_apps_default_apps_digital_assis), assistant) { open(AssistPickerActivity.settingsIntent()) }
        SideKeyStep("2. Hold the side key: Digital assistant", stringResource(R.string.side_button_press_and_hold_digital_assis), hold) { open(sideKeySettings(context)) }
        var holdTarget by remember { mutableStateOf(SideKeyHold.current(context)) }
        val holdOptions = remember(tick) { SideKeyHold.available(context) }
        IosMenuRow(stringResource(R.string.when_you_hold_it), holdOptions.map { it to it.label }, holdTarget,
            { holdTarget = it; SideKeyHold.set(context, it) }, tag = "side-key-hold")
        CardNote(stringResource(R.string.then_holding_the_side_key_opens_folio_s))
        CardNote(stringResource(R.string.still_nothing_choose_a_different_digital))
        // Good Lock's RegiStar can take over the side key before Android's assistant setting is used.
        val registar = remember(tick) { runCatching { context.packageManager.getPackageInfo("com.samsung.android.app.galaxyregistry", 0) }.isSuccess }
        if (registar) Text(stringResource(R.string.registar_good_lock_is_installed_if_it_h),
            style = MaterialTheme.typography.bodySmall, color = FolioColors.Orange)
    }
    SettingsCard(stringResource(R.string.double_press)) {
        SideKeyStep(stringResource(R.string.double_press_google_wallet), stringResource(R.string.side_button_double_press_open_app_wallet), wallet) { open(sideKeyDoublePressSettings(context) ?: sideKeySettings(context)) }
        CardNote(stringResource(R.string.like_double_clicking_the_side_button_for))
    }
}

@Composable private fun SideKeyStep(title: String, path: String, done: Boolean, onOpen: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title)
            CardNote(path)
        }
        if (done) Icon(Icons.Rounded.CheckCircle, stringResource(R.string.done), tint = FolioColors.Green)
        else TextButton(onClick = onOpen) { Text(stringResource(R.string.open)) }
    }
}

/** iOS Settings search field. */
/** A row that just says something: a title and a line under it, with no control. */
@Composable private fun SwitchlessRow(title: String, value: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = FolioSpace.LARGE.dp, vertical = FolioSpace.MEDIUM.dp)) {
        Text(title, color = androidx.compose.ui.graphics.Color.White, fontSize = FolioType.BODY.sp)
        Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable private fun SettingsSearchField(query: String, onQuery: (String) -> Unit) =
    IosSearchField(query, onQuery, stringResource(R.string.search), fieldModifier = Modifier.testTag("settings-search"))

/** Where each setting lives, for Settings search: its title as shown, and words people search for (settings_keywords_*). */
private val SettingsIndex: List<Triple<Int, Int, CustomizationPage>> = listOf(
    Triple(R.string.settings_background_wallpaper, R.string.settings_keywords_background_wallpaper, CustomizationPage.WALLPAPER),
    Triple(R.string.text_on_home, R.string.settings_keywords_text_on_home, CustomizationPage.WALLPAPER),
    Triple(R.string.settings_big_buttons_in_every_app, R.string.settings_keywords_big_buttons_in_every_app, CustomizationPage.ISLAND),
    Triple(R.string.settings_dock_and_status_position, R.string.settings_keywords_dock_and_status_position, CustomizationPage.HOME),
    Triple(R.string.settings_rounded_screen_corners, R.string.settings_keywords_rounded_screen_corners, CustomizationPage.WALLPAPER),
    Triple(R.string.glass, R.string.settings_keywords_glass, CustomizationPage.WALLPAPER),
    Triple(R.string.folders, R.string.settings_keywords_folders, CustomizationPage.HOME),
    Triple(R.string.software_update, R.string.settings_keywords_software_update, CustomizationPage.SOFTWARE_UPDATE),
    Triple(R.string.tweak_library, R.string.settings_keywords_tweak_library, CustomizationPage.TWEAK_LIBRARY),
    Triple(R.string.settings_animation_speed, R.string.settings_keywords_animation_speed, CustomizationPage.GESTURES),
    Triple(R.string.settings_app_name_size, R.string.settings_keywords_app_name_size, CustomizationPage.STATUS),
    Triple(R.string.tint_glass_with_wallpaper_color, R.string.settings_keywords_tint_glass_with_wallpaper_color, CustomizationPage.WALLPAPER),
    Triple(R.string.dark_appearance_dims_wallpaper, R.string.settings_keywords_dark_appearance_dims_wallpaper, CustomizationPage.WALLPAPER),
    Triple(R.string.darken_behind_text, R.string.settings_keywords_darken_behind_text, CustomizationPage.WALLPAPER),
    Triple(R.string.settings_appearance_light_dark_sunset, R.string.settings_keywords_appearance_light_dark_sunset, CustomizationPage.WALLPAPER),
    Triple(R.string.settings_grid_icon_size_dock, R.string.settings_keywords_grid_icon_size_dock, CustomizationPage.HOME),
    Triple(R.string.rows, R.string.settings_keywords_rows, CustomizationPage.HOME),
    Triple(R.string.space_between_columns, R.string.settings_keywords_space_between_columns, CustomizationPage.HOME),
    Triple(R.string.widget_size, R.string.settings_keywords_widget_size, CustomizationPage.HOME),
    Triple(R.string.space_between_dock_apps, R.string.settings_keywords_space_between_dock_apps, CustomizationPage.HOME),
    Triple(R.string.status_spacing, R.string.settings_keywords_status_spacing, CustomizationPage.STATUS),
    Triple(R.string.widgets, R.string.settings_keywords_widgets, CustomizationPage.HOME),
    Triple(R.string.settings_today_view_left_of_home, R.string.settings_keywords_today_view_left_of_home, CustomizationPage.TODAY),
    Triple(R.string.settings_icon_pack_shape_style, R.string.settings_keywords_icon_pack_shape_style, CustomizationPage.STATUS),
    Triple(R.string.notification_badges, R.string.settings_keywords_notification_badges, CustomizationPage.STATUS),
    Triple(R.string.settings_live_clock_and_calendar_icons, R.string.settings_keywords_live_clock_and_calendar_icons, CustomizationPage.STATUS),
    Triple(R.string.settings_side_bar_status_bar, R.string.settings_keywords_side_bar_status_bar, CustomizationPage.STATUS),
    Triple(R.string.dynamic_island, R.string.settings_keywords_dynamic_island, CustomizationPage.ISLAND),
    Triple(R.string.settings_other_notifications_in_the_islan, R.string.settings_keywords_other_notifications_in_the_islan, CustomizationPage.ISLAND_APPS),
    Triple(R.string.settings_island_and_dock_in_every_app, R.string.settings_keywords_island_and_dock_in_every_app, CustomizationPage.ISLAND),
    Triple(R.string.settings_hide_the_island_in_full_screen, R.string.settings_keywords_hide_the_island_in_full_screen, CustomizationPage.ISLAND),
    Triple(R.string.notification_center, R.string.settings_keywords_notification_center, CustomizationPage.NOTIFICATIONS),
    Triple(R.string.control_center, R.string.settings_keywords_control_center, CustomizationPage.NOTIFICATIONS),
    Triple(R.string.spotlight, R.string.settings_keywords_spotlight, CustomizationPage.SEARCH),
    Triple(R.string.app_library, R.string.settings_keywords_app_library, CustomizationPage.SEARCH),
    Triple(R.string.settings_search_button_swipe_down, R.string.settings_keywords_search_button_swipe_down, CustomizationPage.SEARCH),
    Triple(R.string.settings_page_dots_haptics, R.string.settings_keywords_page_dots_haptics, CustomizationPage.GESTURES),
    Triple(R.string.settings_gestures_pull_downs, R.string.settings_keywords_gestures_pull_downs, CustomizationPage.GESTURES),
    Triple(R.string.settings_swipe_down_on_home, R.string.settings_keywords_swipe_down_on_home, CustomizationPage.GESTURES),
    Triple(R.string.actions, R.string.settings_keywords_actions, CustomizationPage.GESTURES),
    Triple(R.string.side_key, R.string.settings_keywords_side_key, CustomizationPage.SIDE_KEY),
    Triple(R.string.lock_cover, R.string.settings_keywords_lock_cover, CustomizationPage.LOCK),
    Triple(R.string.fold_animation, R.string.settings_keywords_fold_animation, CustomizationPage.FOLD),
    Triple(R.string.standby, R.string.settings_keywords_standby, CustomizationPage.FOLD),
    Triple(R.string.themes, R.string.settings_keywords_themes, CustomizationPage.THEMES),
    Triple(R.string.focus, R.string.settings_keywords_focus, CustomizationPage.FOCUS),
    Triple(R.string.tweaks, R.string.settings_keywords_tweaks, CustomizationPage.TWEAKS),
    Triple(R.string.privacy_permissions, R.string.settings_keywords_privacy_permissions, CustomizationPage.PERMISSIONS),
    Triple(R.string.settings_safe_mode_crash_reports, R.string.settings_keywords_safe_mode_crash_reports, CustomizationPage.ADVANCED),
    Triple(R.string.screenshot_mode, R.string.settings_keywords_screenshot_mode, CustomizationPage.ADVANCED),
    Triple(R.string.settings_backup_restore, R.string.settings_keywords_backup_restore, CustomizationPage.BACKUP),
    Triple(R.string.settings_supporter_code, R.string.settings_keywords_supporter_code, CustomizationPage.SUPPORTER),
    Triple(R.string.roadmap, R.string.settings_keywords_roadmap, CustomizationPage.COMING_SOON),
    Triple(R.string.help, R.string.settings_keywords_help, CustomizationPage.HELP),
    Triple(R.string.credits, R.string.settings_keywords_credits, CustomizationPage.CREDITS),
    Triple(R.string.supporters, R.string.settings_keywords_supporters, CustomizationPage.SUPPORTERS),
)

internal fun settingsMatches(query: String, title: String, keywords: String): Boolean {
    val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
    val hay = "$title $keywords".lowercase()
    return words.isNotEmpty() && words.all { it in hay }
}

@Composable private fun SettingsSearchResults(query: String, onOpen: (CustomizationPage) -> Unit) {
    val resources = androidx.compose.ui.platform.LocalContext.current.resources
    val index = remember(androidx.compose.ui.platform.LocalConfiguration.current) {
        SettingsIndex.map { (title, keywords, page) -> Triple(resources.getString(title), resources.getString(keywords), page) }
    }
    val results = index.filter { (title, keywords) -> settingsMatches(query, title, keywords) }
    if (results.isEmpty()) Text(stringResource(R.string.no_results_for_1, query.trim()), color = androidx.compose.ui.graphics.Color.White.copy(alpha = .55f),
        modifier = Modifier.fillMaxWidth().padding(FolioSpace.XXL.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    else SheetGroup {
        results.forEachIndexed { index, (title, _, page) ->
            if (index > 0) MenuDivider()
            Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { onOpen(page) }.padding(horizontal = FolioSpace.LARGE.dp, vertical = FolioSpace.SMALL.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = androidx.compose.ui.graphics.Color.White, fontSize = FolioType.BODY.sp, modifier = Modifier.weight(1f))
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
        Perm(context.getString(R.string.home_app), context.getString(R.string.home_button_folding_gestures), isDefaultHome, onMakeDefault),
        Perm(context.getString(R.string.notification_access), context.getString(R.string.dynamic_island_notification_center_quick),
            IslandListenerService.hasAccess(context)) { open(IslandListenerService.accessSettingsIntent(context)) },
        Perm(context.getString(R.string.gestures_service_accessibility), context.getString(R.string.pull_down_panels_island_and_dock_in_othe),
            SystemShadeAccessibilityService.isConnected(), onShadeSetup),
        Perm(context.getString(R.string.do_not_disturb_access), context.getString(R.string.focus_control_center_actions),
            context.getSystemService(android.app.NotificationManager::class.java).isNotificationPolicyAccessGranted) {
            open(android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) },
        Perm(context.getString(R.string.modify_system_settings), context.getString(R.string.control_center_brightness_and_rotation_l), android.provider.Settings.System.canWrite(context)) {
            open(android.content.Intent(android.provider.Settings.ACTION_MANAGE_WRITE_SETTINGS, android.net.Uri.parse("package:${context.packageName}"))) },
        Perm(context.getString(R.string.usage_access_optional), context.getString(R.string.better_suggestions_counts_apps_you_open), Suggestions.hasUsageAccess(context)) {
            open(Suggestions.usageAccessIntent(context)) },
        Perm(context.getString(R.string.calendar_optional), context.getString(R.string.up_next_widget), UpNext.hasCalendar(context)) { open(appSettings) },
        Perm(context.getString(R.string.contacts), context.getString(R.string.spotlight_contact_search), context.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) == android.content.pm.PackageManager.PERMISSION_GRANTED) { open(appSettings) },
        Perm(context.getString(R.string.digital_assistant), context.getString(R.string.side_key_picker), AssistPickerActivity.isDefaultAssistant(context)) { open(AssistPickerActivity.settingsIntent()) },
    ) }
    CardNote(stringResource(R.string.everything_stays_on_your_phone_folio_has), Modifier.padding(horizontal = FolioSpace.TINY.dp))
    SheetGroup {
        perms.forEachIndexed { index, perm ->
            if (index > 0) MenuDivider()
            Row(Modifier.fillMaxWidth().clickable(onClick = perm.action).padding(horizontal = FolioSpace.LARGE.dp, vertical = FolioSpace.COMPACT.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(perm.name, color = androidx.compose.ui.graphics.Color.White, fontSize = FolioType.BODY.sp)
                    Text(stringResource(R.string.used_by_1, perm.usedBy), color = androidx.compose.ui.graphics.Color.White.copy(alpha = .55f), fontSize = FolioType.FOOTNOTE.sp)
                }
                Text(if (perm.allowed) stringResource(R.string.allowed) else "Off", color = if (perm.allowed) FolioColors.Green
                    else androidx.compose.ui.graphics.Color.White.copy(alpha = .5f), fontSize = FolioType.SUBHEAD.sp)
                Icon(Icons.Rounded.ChevronRight, null, tint = androidx.compose.ui.graphics.Color.White.copy(alpha = .3f))
            }
        }
        // Some banking apps refuse to run while any accessibility service is on, Folio's included (reported on
        // r/GalaxyFold, 18 Sep 2026). Nothing Folio can do from its side, so say so before someone is caught out.
        CardNote(stringResource(R.string.banking_apps_note))
    }
}

/** Tweak preference page: main switch first, per-screen overrides (dimmed when off), credit, reset. */
@Composable private fun TweakPage(tweak: TweakFeature, state: LauncherState, model: LauncherModel) {
    // Not installed yet: just what it does and Get, like a package page. Its settings appear once it's installed.
    if (tweak.id !in state.installedTweaks) {
        SettingsCard(tweak.name) {
            CardNote(stringResource(tweak.description))
            CardNote(stringResource(R.string.inspired_by_re_created_from_scratch_no_t, tweak.inspiredBy))
        }
        SheetGroup { IosActionRow(stringResource(R.string.get_tweak, tweak.name), "tweak-get-${tweak.id}") { model.installTweak(tweak) } }
        return
    }
    val on = tweak.get(state)
    SettingsCard(tweak.name) {
        SettingsSwitch(stringResource(R.string.enabled), on, { tweak.set(model, it) }, "tweak-enabled-${tweak.id}")
        CardNote(stringResource(tweak.description))
    }
    SettingsCard(stringResource(R.string.use_on)) {
        Column(Modifier.alpha(if (on) 1f else .4f)) {
            FolioScreen.entries.forEach { screen ->
                val value = FeatureScopes.value(state.featureScopes, tweak.id, screen)
                IosMenuRow(stringResource(screen.label), ScopeValue.entries.map { it to stringResource(it.label) }, value, { model.setFeatureScope(tweak.id, screen, it) }, enabled = on, tag = "scope-${tweak.id}-${screen.name.lowercase()}")
            }
        }
        CardNote(stringResource(R.string.default_follows_enabled_on_or_off_applie))
    }
    SettingsCard(stringResource(R.string.about)) {
        TextButton(onClick = { model.resetTweak(tweak) }) { Text(stringResource(R.string.reset_1, tweak.name)) }
        CardNote(stringResource(R.string.inspired_by_re_created_from_scratch_no_t, tweak.inspiredBy))
    }
    SheetGroup { IosActionRow(stringResource(R.string.remove_control, tweak.name), "tweak-remove-${tweak.id}", destructive = true) { model.removeTweak(tweak) } }
}

/** Themes (after SnowBoard): built-in looks with a live preview, plus saving and importing theme files. */
@Composable private fun ThemesPage(state: LauncherState, model: LauncherModel, stagedBitmap: android.graphics.Bitmap?) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    var undo by remember { mutableStateOf(model.themeUndo != null) }
    val open = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val theme = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)!!.use { it.readBytes().take(64_000).toByteArray().decodeToString() } }.getOrNull()?.let(FolioTheme::fromJson)
            }
            if (theme == null) message = context.getString(R.string.that_file_isn_t_a_folio_theme)
            else { model.applyTheme(theme); undo = true; message = context.getString(R.string.applied, theme.name) }
        }
    }
    MiniHomePreview(stagedBitmap, state, 220.dp)
    SheetGroup {
        FolioTheme.PRESETS.forEachIndexed { index, theme ->
            if (index > 0) MenuDivider()
            val current = state.looksLike(theme)
            Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { model.applyTheme(theme); undo = true; message = null }
                .padding(horizontal = FolioSpace.LARGE.dp).testTag("theme-${theme.name.lowercase()}"), verticalAlignment = Alignment.CenterVertically) {
                Text(theme.name, color = androidx.compose.ui.graphics.Color.White, fontSize = FolioType.BODY.sp, modifier = Modifier.weight(1f))
                if (current) Icon(Icons.Rounded.Check, null, tint = LocalAccent.current.ink, modifier = Modifier.size(20.dp))
            }
        }
    }
    CardNote(stringResource(R.string.a_theme_changes_icons_badges_glass_text), Modifier.padding(horizontal = FolioSpace.TINY.dp))
    SheetGroup {
        IosActionRow(stringResource(R.string.save_current_look_as_theme), "theme-save") {
            scope.launch {
                val name = FolioFiles.datedName("folio-theme")
                val saved = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    FolioFiles.save(context, name, "application/json", FolioTheme.of(state, context.getString(R.string.my_folio_theme)).toJson().toString(2).toByteArray())
                }
                message = if (saved != null) context.getString(R.string.saved_to_as, FolioFiles.displayPath, FolioFiles.displayName(context, saved) ?: name) else context.getString(R.string.the_theme_couldn_t_be_saved)
            }
        }
        MenuDivider()
        IosActionRow(stringResource(R.string.import_theme), "theme-import") { open.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
        if (undo) { MenuDivider(); IosActionRow(stringResource(R.string.undo_theme_change), "theme-undo") { model.undoTheme(); undo = false; message = null } }
    }
    message?.let { CardNote(it, Modifier.padding(horizontal = FolioSpace.TINY.dp)) }
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
            TweakRow(mode.icon(), mode.color, mode.name, "focus-${mode.id}", if (state.activeFocus == mode.id) stringResource(R.string.on) else if (mode.schedule != null) stringResource(R.string.scheduled) else null) { onOpen(mode.id) }
        }
    }
    CardNote(stringResource(R.string.focus_lets_you_silence_notifications_cha), Modifier.padding(horizontal = FolioSpace.TINY.dp))
    if (!access) {
        SheetGroup { IosActionRow(stringResource(R.string.allow_do_not_disturb_access), "focus-allow-access") {
            runCatching { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
        } }
        CardNote(stringResource(R.string.without_it_a_focus_still_changes_home_bu), Modifier.padding(horizontal = FolioSpace.TINY.dp))
    }
}

@Composable private fun FocusModePage(mode: FocusMode, state: LauncherState, model: LauncherModel) {
    val on = state.activeFocus == mode.id
    SheetGroup {
        Row(Modifier.fillMaxWidth().padding(horizontal = FolioSpace.COMFY.dp, vertical = FolioSpace.SMALL.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(30.dp).clip(RoundedCornerShape(7.dp)).background(androidx.compose.ui.graphics.Color(mode.color)), contentAlignment = Alignment.Center) {
                Icon(mode.icon(), null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(19.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text(if (on) "On" else "Off", color = androidx.compose.ui.graphics.Color.White, fontSize = FolioType.BODY.sp, modifier = Modifier.weight(1f))
            IosSwitch(on, { model.setFocus(if (it) mode.id else null) }, Modifier.testTag("focus-switch-${mode.id}"))
        }
    }
    SettingsCard(stringResource(R.string.schedule)) {
        val context = androidx.compose.ui.platform.LocalContext.current
        val schedule = mode.schedule
        SettingsSwitch(stringResource(R.string.turn_on_automatically), schedule != null, { on ->
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
            // The row knows which end it sets by its own key, not by its label, which changes with the language.
            listOf(Triple("from", stringResource(R.string.from), schedule.startMinute),
                Triple("to", stringResource(R.string.to), schedule.endMinute)).forEach { (key, name, minute) ->
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(FolioRadius.CONTROL.dp)).clickable {
                    pick(minute) { picked -> model.updateFocusMode(mode.copy(schedule = if (key == "from") schedule.copy(startMinute = picked) else schedule.copy(endMinute = picked))) }
                }.testTag("focus-schedule-$key"), verticalAlignment = Alignment.CenterVertically) {
                    Text(name, Modifier.weight(1f))
                    Text(label(minute), color = LocalAccent.current.ink, fontSize = FolioType.BODY.sp)
                }
            }
            // iOS day picker: one letter per day, filled when the schedule runs that day.
            Row(Modifier.fillMaxWidth().padding(vertical = FolioSpace.SNUG.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                java.time.DayOfWeek.entries.forEach { day ->
                    val on = day.value in schedule.days
                    Box(Modifier.size(38.dp).clip(androidx.compose.foundation.shape.CircleShape)
                        .background(if (on) androidx.compose.ui.graphics.Color(mode.color) else androidx.compose.ui.graphics.Color.White.copy(alpha = .1f))
                        .clickable(onClickLabel = day.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault())) {
                            val days = if (on) schedule.days - day.value else schedule.days + day.value
                            if (days.isNotEmpty()) model.updateFocusMode(mode.copy(schedule = schedule.copy(days = days)))
                        }, contentAlignment = Alignment.Center) {
                        Text(day.getDisplayName(java.time.format.TextStyle.NARROW, java.util.Locale.getDefault()),
                            color = androidx.compose.ui.graphics.Color.White, fontSize = FolioType.SUBHEAD.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            CardNote(stringResource(R.string.focus_schedule_note, mode.name))
        }
    }
    SettingsCard(stringResource(R.string.notifications_title)) {
        SettingsSwitch(stringResource(R.string.silence_notifications), mode.silence, { model.updateFocusMode(mode.copy(silence = it)) }, "focus-silence")
        CardNote(stringResource(R.string.calls_and_people_allowed_in_android_s_do))
    }
    SettingsCard(stringResource(R.string.home_screen)) {
        // The real page count: while a Focus hides pages, Home's own state is the filtered copy.
        val real by model.state.collectAsState()
        val pages = real.layout.pageCount
        Text(stringResource(R.string.show_pages), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = FolioSpace.TINY.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(FolioSpace.SMALL.dp)) {
            IosChip(mode.pages == null, { model.updateFocusMode(mode.copy(pages = null)) }, label = { Text(stringResource(R.string.all)) })
            repeat(pages) { page ->
                val shown = mode.pages?.contains(page) == true
                IosChip(shown, {
                    val next = (mode.pages ?: emptySet()).let { if (shown) it - page else it + page }
                    model.updateFocusMode(mode.copy(pages = next.ifEmpty { null }))
                }, label = { Text(stringResource(R.string.page_1, page + 1)) }, modifier = Modifier.testTag("focus-page-$page"))
            }
        }
        CardNote(stringResource(R.string.only_these_pages_show_while_is_on_editin, mode.name))
        IosMenuRow(stringResource(R.string.open_on), listOf<Pair<Int?, String>>(null to stringResource(R.string.any_page)) + (0 until pages).filter { mode.pages == null || it in mode.pages }.map { it to stringResource(R.string.page_number, it + 1) },
            mode.homePage, { model.updateFocusMode(mode.copy(homePage = it)) }, tag = "focus-open-on")
        CardNote(stringResource(R.string.home_opens_on_this_page_while_is_on, mode.name))
    }
    if (android.os.Build.VERSION.SDK_INT >= 35) SettingsCard(stringResource(R.string.look)) {
        SettingsSwitch(stringResource(R.string.dim_wallpaper), mode.dimWallpaper, { model.updateFocusMode(mode.copy(dimWallpaper = it)) }, "focus-dim")
        SettingsSwitch(stringResource(R.string.dark_appearance), mode.darkTheme, { model.updateFocusMode(mode.copy(darkTheme = it)) }, "focus-dark")
        SettingsSwitch(stringResource(R.string.grayscale), mode.grayscale, { model.updateFocusMode(mode.copy(grayscale = it)) }, "focus-gray")
        CardNote(stringResource(R.string.android_applies_these_while_the_focus_is))
    }
}

@Composable private fun CrashReportsCard() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var reports by remember { mutableStateOf(CrashLog.reports(context)) }
    SettingsCard(stringResource(R.string.crash_reports)) {
        Text(if (reports.isEmpty()) stringResource(R.string.no_problems_recorded) else pluralStringResource(R.plurals.reports_saved, reports.size, reports.size),
            style = MaterialTheme.typography.bodyMedium)
        reports.firstOrNull()?.let { latest ->
            CardNote(latest.readLines().take(5).joinToString("\n"))
            CardAction(stringResource(R.string.share_latest), Modifier.fillMaxWidth(), onClick = { runCatching { context.startActivity(CrashLog.shareIntent(latest).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) } })
            CardAction(stringResource(R.string.clear), Modifier.fillMaxWidth(), destructive = true, onClick = { CrashLog.clear(context); reports = emptyList() })
        }
        val shareScope = rememberCoroutineScope()
        CardAction(stringResource(R.string.share_diagnostics), onClick = {
            shareScope.launch { runCatching { context.startActivity(Diagnostics.reportIntent(context, email = false)) } }
        }, modifier = Modifier.testTag("share-diagnostics"))
        CardNote(stringResource(R.string.diagnostics_file_note))
    }
}

@Composable private fun CreditsPage() {
    val credits = listOf(
        stringResource(R.string.duolauncher) to "jakesgoodapps (github.com/jakesgoodapps/DuoLauncher) · MIT · Folio’s starting codebase: iPhone Duo-style Home layouts for both screens, widgets, folders, work profile, wallpapers and Discover",
        "iphone-duo" to "chuspeeism · MIT · fold blur and darkening model",
        "iPhone Duo on Galaxy Z Fold 8 demo" to "u/moomanjohnny · screenshot + shader idea (no code)",
        stringResource(R.string.quicklaunch) to stringResource(R.string.ahmedthegeek_spotlight_ideas_no_code),
        stringResource(R.string.foldfx) to "u/FixHour8452 · fold transition ideas: halfway haptic tick, light sweep, slight scale, following a smooth hinge angle (no code)",
        stringResource(R.string.zfoldduo) to "nnnnnnn0090 · MIT · showed a Fold7/Fold8 can read Samsung's internal hinge angle without root; research for Enhanced Fold Tracking",
        stringResource(R.string.velox) to stringResource(R.string.phillip_tennen_app_panels_idea),
        stringResource(R.string.activator) to stringResource(R.string.ryan_petrich_gestures_and_events_idea),
        stringResource(R.string.axon) to stringResource(R.string.nepeta_notification_app_row_idea),
        stringResource(R.string.velvet) to stringResource(R.string.noisyflake_himynameisubik_tinted_notific),
        stringResource(R.string.colorflow) to stringResource(R.string.david_goldman_album_art_colors_idea),
        stringResource(R.string.harbor) to stringResource(R.string.evan_swick_dock_magnification_idea),
        stringResource(R.string.snowboard) to stringResource(R.string.sparkdev_themes_idea_no_code),
        stringResource(R.string.apex) to stringResource(R.string.sticktron_icon_stacks_idea_no_code),
        stringResource(R.string.icon_restore) to stringResource(R.string.layout_history_idea_no_code),
        stringResource(R.string.lynx_2) to "recent-app dots idea (no code)",
        stringResource(R.string.colorbadges) to "badges that match the app idea (no code)",
        stringResource(R.string.barrel) to stringResource(R.string.page_effects_idea_coming_soon_no_code),
        stringResource(R.string.contributor_covenant_3_0) to stringResource(R.string.organization_for_ethical_source_cc_by_sa),
    )
    SettingsCard(stringResource(R.string.thanks_to)) {
        credits.forEach { (name, detail) ->
            Column(Modifier.fillMaxWidth().padding(vertical = FolioSpace.SNUG.dp)) {
                Text(name)
                CardNote(detail)
            }
        }
    }
    CardNote(stringResource(R.string.tweak_ideas_were_re_created_from_scratch), Modifier.padding(horizontal = FolioSpace.TINY.dp))
}

@Composable private fun CustomizationDestination(icon: ImageVector, title: String, detail: String, tag: String,
    leading: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).testTag(tag),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .52f), shape = RoundedCornerShape(FolioRadius.GROUPED_CARD.dp)) {
        Row(Modifier.padding(horizontal = FolioSpace.LARGE.dp, vertical = FolioSpace.COMPACT.dp), verticalAlignment = Alignment.CenterVertically) {
            if (leading != null) leading() else Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleMedium); CardNote(detail) }
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
    previewHeight: androidx.compose.ui.unit.Dp, framed: Boolean = true,
    /** The Side Bar (status, dock and search): off for the second page of an unfolded preview, which has one Side Bar. */
    sideBar: Boolean = true,
    /** The cover's layout by default; the inner screen's for an unfolded preview. */
    preset: LayoutPreset = state.compact) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val backgroundRevision = LauncherBackgroundCache.revision.intValue
    val committedBitmap = remember(backgroundRevision) { cachedLauncherBackground(context) }
    val bitmap = stagedBitmap ?: committedBitmap
    val apps = remember(state.apps) { state.apps.associateBy { it.id } }
    val tone = LocalWallpaperTone.current
    val ink = homeInkFor(state.homeInk, tone.prefersDarkText)
    val basePalette = LocalDuoPalette.current
    val glass = if (state.glassTintAmount > 0f) tintedGlass(basePalette.glass, tone.primary, state.glassTintAmount) else basePalette.glass
    // Home page 1 drawn at a real cover-screen size with Folio's own layout math and parts (widget cards, icons,
    // status rail, dock, search pill), then scaled down, so the preview matches Home instead of approximating it.
    val refW = 420f; val refH = 720f
    val geometry = homeGeometry(refW, refH, preset, state.labels, statusHeight = if (state.verticalStatus) 180f else 0f, labelHeight = 20f,
        appRows = state.homeAppRows)
    val placements = state.widgetPlacements.filter { it.page == 0 }
    val shownRows = shownHomeRows(state.homeAppRows, state.homeSlots.take(HOME_CELLS), placements)
    val cells = HomeCellLayout.forPage(geometry, placements.map { it.row to it.spanY })
    val (iconSize, labels) = (state.pageStyles[0] ?: PageStyle()).apply(geometry, state.labels)
    val scale = previewHeight.value / refH
    val left = state.leftHanded
    val railAlign = if (left) Alignment.TopStart else Alignment.TopEnd
    val railEdge = if (left) Modifier.padding(start = FolioSpace.MEDIUM.dp) else Modifier.padding(end = FolioSpace.MEDIUM.dp)
    // A thin black bezel with the screen's own corners inside it, so the preview reads as the phone, not a card.
    val corner = 26.dp * (previewHeight.value / 260f)
    val bezel = previewHeight * .022f
    Column(if (framed) Modifier.fillMaxWidth() else Modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(if (!framed) Modifier else Modifier.clip(RoundedCornerShape(corner + bezel)).background(androidx.compose.ui.graphics.Color(0xFF0B0B0C))
            .border(1.dp, androidx.compose.ui.graphics.Color.White.copy(alpha = .2f), RoundedCornerShape(corner + bezel)).padding(bezel)) {
        Box(Modifier.height(previewHeight).width(previewHeight * (refW / refH))
            .clip(RoundedCornerShape(if (framed) corner else 0.dp))
            .testTag("customization-home-preview"), contentAlignment = Alignment.Center) {
            Box(Modifier.requiredSize(refW.dp, refH.dp).graphicsLayer { scaleX = scale; scaleY = scale }) {
                if (state.systemWallpaper) Box(Modifier.matchParentSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(
                    androidx.compose.ui.graphics.Color(tone.primary ?: 0xFF5A6B78.toInt()), androidx.compose.ui.graphics.Color(tone.secondary ?: tone.primary ?: 0xFF2E3A42.toInt())))))
                else {
                    DuneWallpaper()
                    bitmap?.let { Image(it.asImageBitmap(), null, Modifier.matchParentSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop) }
                }
                val previewDim = if (state.dimWallpaperDark && basePalette.dark) .3f else 0f
                if (previewDim > 0f) Box(Modifier.matchParentSize().background(androidx.compose.ui.graphics.Color.Black.copy(alpha = previewDim)))
                // Drawn over the background rather than inside it, because the preview lays the chosen photo over the
                // background; on Home the same scrim is baked into the background's cached layer.
                HomeScrim.of(state.homeScrim, ink.dark, previewDim).let { if (it.draws) Box(Modifier.matchParentSize().homeScrim(it)) }
                CompositionLocalProvider(LocalHomeInk provides ink, LocalDuoPalette provides basePalette.copy(glass = glass)) {
                    Box(Modifier.offset(x = (if (left) refW - 16f - geometry.gridWidth else 16f).dp, y = geometry.contentTop.dp).width(geometry.gridWidth.dp).height((cells.height(shownRows)).dp)) {
                        placements.forEach { w ->
                            Box(Modifier.offset(x = (cells.x(w.column, w.row) + 5f).dp, y = cells.y(w.row).dp)
                                .size((geometry.cellWidth * w.spanX - 10f).dp, (cells.spanHeight(w.row, w.spanY) - 18f).coerceAtLeast(48f).dp)) {
                                if (w.id < 0) BuiltinWidgetCard(w.id, w.slot) {}
                                else Box(Modifier.fillMaxSize().clip(RoundedCornerShape(FolioRadius.PANEL.dp)).background(glass.copy(alpha = LocalGlassLook.current.widget)), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Rounded.Widgets, null, tint = ink.secondary, modifier = Modifier.size(32.dp))
                                }
                            }
                        }
                        repeat(shownRows * GRID_COLUMNS) { local ->
                            val id = state.homeSlots.getOrNull(local) ?: return@repeat
                            val app = apps[id]
                            val folder = if (app == null) state.folders.firstOrNull { it.id == id } ?: return@repeat else null
                            val row = local / GRID_COLUMNS
                            Column(Modifier.offset(x = cells.x(local % GRID_COLUMNS, row).dp, y = cells.y(row).dp).width(geometry.cellWidth.dp),
                                horizontalAlignment = Alignment.CenterHorizontally) {
                                if (app != null) AppIcon(app, null, Modifier.size(iconSize.dp), shape = RoundedCornerShape((iconSize * .24f).dp))
                                else if (folder != null) PreviewFolder(folder, apps, iconSize)
                                if (labels) Text(app?.label ?: folder?.title.orEmpty(), color = ink.primary, fontSize = LocalLabelSize.current.sp.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(top = FolioSpace.TINY.dp, start = FolioSpace.HAIR.dp, end = FolioSpace.HAIR.dp),
                                    style = androidx.compose.ui.text.TextStyle(shadow = ink.labelShadow))
                            }
                        }
                    }
                    if (sideBar && state.verticalStatus) StatusRail(DeviceStatus(battery = 80, wifiConnected = true, wifiLevel = 4, cellularLevel = 4),
                        Modifier.align(railAlign).then(railEdge).offset(y = geometry.statusTop.dp).width(preset.dockWidth.dp),
                        iconSize = dockIconSize(iconSize).dp, style = state.statusStyle)
                    if (sideBar && geometry.dockBesideRail) Box(Modifier.align(if (left) Alignment.BottomEnd else Alignment.BottomStart)
                        .width((refW - preset.dockWidth - 28f).dp).padding(bottom = 58.dp), contentAlignment = Alignment.Center) { Row(Modifier
                        .height(geometry.dockBarHeight.dp).background(glass.copy(alpha = state.statusStyle.railGlass), RoundedCornerShape(30.dp))
                        .border(1.dp, LocalGlassLook.current.outlineColor, RoundedCornerShape(30.dp)).padding(horizontal = FolioSpace.SMALL.dp), verticalAlignment = Alignment.CenterVertically) {
                        state.dock.forEach { id ->
                            Box(Modifier.width(geometry.dockPitch.dp), contentAlignment = Alignment.Center) {
                                id?.let(apps::get)?.let { AppIcon(it, null, Modifier.size(dockIconSize(iconSize).dp), shape = RoundedCornerShape(11.dp)) }
                            }
                        }
                    } }
                    else if (sideBar) Column(Modifier.align(railAlign).then(railEdge).offset(y = geometry.dockTop.dp).width(preset.dockWidth.dp)
                        .height(geometry.dockHeight.dp).background(glass.copy(alpha = state.statusStyle.railGlass), RoundedCornerShape(30.dp))
                        .border(1.dp, LocalGlassLook.current.outlineColor, RoundedCornerShape(30.dp)).padding(vertical = FolioSpace.SMALL.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        state.dock.forEach { id ->
                            Box(Modifier.fillMaxWidth().height(geometry.dockRowHeight.dp), contentAlignment = Alignment.Center) {
                                id?.let(apps::get)?.let { AppIcon(it, null, Modifier.size(dockIconSize(iconSize).dp), shape = RoundedCornerShape(11.dp)) }
                            }
                        }
                    }
                    if (sideBar && state.searchPill) Box(Modifier.align(Alignment.BottomCenter).padding(bottom = FolioSpace.COMFY.dp)
                        .then(if (left) Modifier.padding(start = (preset.dockWidth + 24f).dp) else Modifier.padding(end = (preset.dockWidth + 24f).dp))) {
                        HomeSearchPill {}
                    }
                }
            }
        }
        }
        if (framed && state.systemWallpaper) CardNote(stringResource(R.string.colors_from_your_android_wallpaper_apps), Modifier.padding(top = FolioSpace.SNUG.dp))
    }
}

/** Drag to see the fold effect at your Intensity without folding: two Home pages side by side, like the open screen. */
@Composable private fun FoldEffectPreview(state: LauncherState, bitmap: android.graphics.Bitmap?) {
    var fold by rememberSaveable { mutableFloatStateOf(.5f) }
    val corner = 16.dp
    val bezel = 5.dp
    Box(Modifier.fillMaxWidth().padding(top = FolioSpace.SMALL.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.clip(RoundedCornerShape(corner + bezel)).background(androidx.compose.ui.graphics.Color(0xFF0B0B0C))
            .border(1.dp, androidx.compose.ui.graphics.Color.White.copy(alpha = .2f), RoundedCornerShape(corner + bezel)).padding(bezel)
            .clearedDescription(R.string.fold_effect_preview)) {
            Row(Modifier.clip(RoundedCornerShape(corner)).foldPreviewEffect { fold * state.foldIntensity }) {
                // Two Home pages with one Side Bar, on the right (on the left in left-handed layouts), like the open Fold.
                MiniHomePreview(bitmap, state, 150.dp, framed = false, sideBar = state.leftHanded)
                MiniHomePreview(bitmap, state, 150.dp, framed = false, sideBar = !state.leftHanded)
            }
        }
    }
    // Slider end to end is the hinge from open (180°) to half folded (90°), where the effect peaks.
    CustomizationSlider(stringResource(R.string.preview), if (fold < .01f) stringResource(R.string.open) else "${(180 - 90 * fold).toInt()}°", fold, 0f..1f) { fold = it }
}

/**
 * Hidden apps, like iOS: kept out of the App Library, and listed here only after unlocking with a fingerprint, face or
 * the phone's PIN (straight away on a phone with no screen lock, where there's nothing to ask).
 */
@Composable private fun HiddenAppsRow(state: LauncherState, model: LauncherModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val hidden = state.apps.filter { it.id in state.hiddenApps }.sortedBy { it.label.lowercase() }
    var unlocked by remember { mutableStateOf(false) }
    if (hidden.isEmpty()) return
    if (!unlocked) {
        IosActionRow(stringResource(R.string.hidden_apps_count, hidden.size), "hidden-apps") {
            val authenticators = android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_WEAK or
                android.hardware.biometrics.BiometricManager.Authenticators.DEVICE_CREDENTIAL
            runCatching {
                android.hardware.biometrics.BiometricPrompt.Builder(context).setTitle(context.getString(R.string.hidden_apps_title))
                    .setSubtitle(context.getString(R.string.unlock_to_see_the_apps_you_ve_hidden)).setAllowedAuthenticators(authenticators).build()
                    .authenticate(android.os.CancellationSignal(), context.mainExecutor,
                        object : android.hardware.biometrics.BiometricPrompt.AuthenticationCallback() {
                            override fun onAuthenticationSucceeded(result: android.hardware.biometrics.BiometricPrompt.AuthenticationResult) { unlocked = true }
                            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                                if (errorCode == android.hardware.biometrics.BiometricPrompt.BIOMETRIC_ERROR_NO_DEVICE_CREDENTIAL) unlocked = true
                            }
                        })
            }
        }
        return
    }
    Text(stringResource(R.string.hidden_apps), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = FolioSpace.SNUG.dp))
    hidden.forEach { app ->
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            AppIcon(app, null, Modifier.size(32.dp), shape = RoundedCornerShape(8.dp))
            Spacer(Modifier.width(12.dp))
            Text(app.label, Modifier.weight(1f))
            TextButton(onClick = { model.setHidden(app.id, false) }) { Text(stringResource(R.string.unhide)) }
        }
    }
}

/** A folder as Home draws it (first four apps on glass or the folder's color), without Home's drag and launch hooks. */
@Composable private fun PreviewFolder(folder: FolderEntry, apps: Map<String, AppEntry>, size: Float) {
    val shape = RoundedCornerShape((size * .24f).dp)
    val tint = LocalFolderColors.current[folder.id]?.let { androidx.compose.ui.graphics.Color(it) }
    Box(Modifier.size(size.dp).clip(shape).background(tint?.copy(alpha = .78f) ?: Glass.copy(alpha = .72f))
        .border(1.dp, androidx.compose.ui.graphics.Color.White.copy(alpha = .55f), shape)) {
        folder.appIds.take(4).forEachIndexed { index, id ->
            apps[id]?.let { app ->
                AppIcon(app, null, Modifier.align(when (index) {
                    0 -> Alignment.TopStart; 1 -> Alignment.TopEnd; 2 -> Alignment.BottomStart; else -> Alignment.BottomEnd
                }).padding(5.dp).size((size * .38f).dp).clip(RoundedCornerShape(6.dp)))
            }
        }
    }
}

@Composable private fun HomeLayoutSettings(state: LauncherState, wide: Boolean, onWide: (Boolean) -> Unit,
    model: LauncherModel, homePage: Int, onEditPins: () -> Unit, onWidget: (Int) -> Unit,
    onAddWidget: (Int) -> Unit, onRemoveWidget: (Int) -> Unit) {
    val p = if (wide) state.expanded else state.compact
    IosSegmented(listOf(false to stringResource(R.string.cover), true to stringResource(R.string.inner)), wide, onWide, Modifier.padding(vertical = FolioSpace.TINY.dp), tag = "layout-screen")
    var confirmIPhone by remember { mutableStateOf(false) }
    SheetGroup {
        IosActionRow(stringResource(R.string.choose_home_apps), onClick = onEditPins)
        MenuDivider()
        IosActionRow(stringResource(R.string.arrange_like_iphone), "arrange-like-iphone", onClick = { confirmIPhone = true })
    }
    if (confirmIPhone) AlertDialog(onDismissRequest = { confirmIPhone = false },
        title = { Text(stringResource(R.string.arrange_like_iphone_2)) },
        text = { Text(stringResource(R.string.your_first_home_page_and_dock_get_iphon)) },
        confirmButton = { TextButton(onClick = { confirmIPhone = false; model.arrangeLikeIPhone() }) { Text(stringResource(R.string.arrange)) } },
        dismissButton = { TextButton(onClick = { confirmIPhone = false }) { Text(stringResource(R.string.cancel)) } })
    SettingsCard(stringResource(R.string.layout)) {
        val d = LayoutPreset()
        CustomizationSlider(stringResource(R.string.app_icon_size), stringResource(R.string.dp_value, p.iconSize.toInt()), p.iconSize, 40f..68f, d.iconSize, peek = true) { model.setPreset(wide, p.copy(iconSize = it)) }
        CustomizationSlider(stringResource(R.string.space_between_rows), stringResource(R.string.dp_value, p.rowGap.toInt()), p.rowGap, 0f..28f, d.rowGap, peek = true) { model.setPreset(wide, p.copy(rowGap = it)) }
        CustomizationSlider(stringResource(R.string.space_between_columns), stringResource(R.string.dp_value, p.columnGap.toInt()), p.columnGap, 8f..40f, d.columnGap, peek = true) { model.setPreset(wide, p.copy(columnGap = it)) }
        CustomizationSlider(stringResource(R.string.widget_size), "${(p.widgetScale * 100).roundToInt()}%", p.widgetScale, .8f..1.25f, d.widgetScale, peek = true) { model.setPreset(wide, p.copy(widgetScale = it)) }
        // Automatic says which number it landed on, so the count is never a mystery.
        IosMenuRow(stringResource(R.string.rows), listOf(0 to stringResource(R.string.automatic_rows_count, state.homeAppRows), 4 to "4"), state.homeRows, model::setHomeRows, tag = "home-rows")
        CardNote(stringResource(R.string.automatic_adds_up_to_3_more_rows_of_apps))
        val rowsContext = androidx.compose.ui.platform.LocalContext.current
        CardNote(automaticRowsNote(state, rowsContext.strings()))
    }
    SettingsCard(stringResource(R.string.position)) {
        IosMenuRow(stringResource(R.string.dock), listOf(DockPlacement.AUTOMATIC to stringResource(R.string.automatic), DockPlacement.SIDE to stringResource(R.string.side_rail), DockPlacement.BOTTOM to stringResource(R.string.bottom)),
            p.dockPlacement, { model.setPreset(wide, p.copy(dockPlacement = it)) }, tag = "dock-placement")
        IosMenuRow(stringResource(R.string.apps), listOf(false to stringResource(R.string.centered), true to "Top"), p.pageTop,
            { model.setPreset(wide, p.copy(pageTop = it)) }, tag = "page-position")
        IosMenuRow(stringResource(R.string.status), listOf(true to stringResource(R.string.level_with_apps), false to stringResource(R.string.custom)), p.statusAlignToGrid,
            { model.setPreset(wide, p.copy(statusAlignToGrid = it)) }, tag = "status-position")
        if (!p.statusAlignToGrid) CustomizationSlider(stringResource(R.string.status_height), if (p.statusPosition < .01f) "Top" else "${(p.statusPosition * 100).toInt()}%",
            p.statusPosition, 0f..1f, peek = true) { model.setPreset(wide, p.copy(statusPosition = it)) }
        CardNote(when (p.dockPlacement) {
            DockPlacement.AUTOMATIC -> if (wide) stringResource(R.string.the_dock_stays_on_the_side_bar_and_moves) else stringResource(R.string.the_dock_stays_on_the_side_bar)
            DockPlacement.SIDE -> stringResource(R.string.the_dock_stays_on_the_side_bar_even_when)
            DockPlacement.BOTTOM -> if (wide) stringResource(R.string.the_dock_sits_along_the_bottom_under_you) else stringResource(R.string.the_dock_sits_along_the_bottom_in_landsc)
        } + if (!p.statusAlignToGrid) " The dock always stays below the status." else "")
    }
    SettingsCard(stringResource(R.string.side_rail)) {
        CustomizationSlider(stringResource(R.string.width), stringResource(R.string.dp_value, p.dockWidth.toInt()), p.dockWidth, 56f..84f, LayoutPreset().dockWidth, peek = true) { model.setPreset(wide, p.copy(dockWidth = it)) }
        CustomizationSlider(stringResource(R.string.space_between_dock_apps), stringResource(R.string.dp_value, p.dockSpacing.toInt()), p.dockSpacing, 0f..24f, 0f, peek = true) { model.setPreset(wide, p.copy(dockSpacing = it)) }
        if (p.dockPlacement != DockPlacement.BOTTOM) SettingsSwitch(stringResource(R.string.align_dock_with_app_rows), p.dockAlignToGrid, { model.setPreset(wide, p.copy(dockAlignToGrid = it)) })
        if (!p.dockAlignToGrid && p.dockPlacement != DockPlacement.BOTTOM) CustomizationSlider(stringResource(R.string.dock_height), "${(p.dockPosition * 100).toInt()}%", p.dockPosition, 0f..1f,
            LayoutPreset().dockPosition, peek = true) { model.setPreset(wide, p.copy(dockPosition = it)) }
    }
    SheetGroup { IosActionRow(stringResource(R.string.reset_this_layout), destructive = true, onClick = { model.setPreset(wide, LayoutPreset()) }) }
    // Per-page looks (after Atria): each page can have its own icon size and labels.
    SheetGroupLabel(stringResource(R.string.pages))
    // Real pages and styles (a Focus hiding pages renumbers the state Home draws), collected so the chips update.
    val real by model.state.collectAsState()
    val realPages = real.layout.pageCount
    SheetGroup {
        repeat(realPages) { page ->
            if (page > 0) MenuDivider()
            val style = real.pageStyles[page] ?: PageStyle()
            Column(Modifier.fillMaxWidth().padding(horizontal = FolioSpace.LARGE.dp, vertical = FolioSpace.COMPACT.dp).testTag("page-style-$page"), verticalArrangement = Arrangement.spacedBy(FolioSpace.SMALL.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.page_1, page + 1), color = androidx.compose.ui.graphics.Color.White, fontSize = FolioType.BODY.sp, modifier = Modifier.weight(1f))
                    if (page == homePage) Text(stringResource(R.string.showing), color = androidx.compose.ui.graphics.Color.White.copy(alpha = .5f), fontSize = FolioType.FOOTNOTE.sp)
                }
                IosSegmented(PageStyle.SIZES.map { it.second to it.first }, style.iconScale, { model.setPageStyle(page, style.copy(iconScale = it)) }, tag = "page-size-$page")
                IosMenuRow(stringResource(R.string.labels), listOf<Pair<Boolean?, String>>(null to stringResource(R.string.same_as_home), true to stringResource(R.string.show), false to stringResource(R.string.hide)), style.labels,
                    { model.setPageStyle(page, style.copy(labels = it)) }, tag = "page-labels-$page")
            }
        }
    }
    CardNote(stringResource(R.string.changes_how_icons_look_on_one_page_widge), Modifier.padding(horizontal = FolioSpace.TINY.dp))
    SheetGroupLabel(stringResource(R.string.widgets_page_number, homePage + 1))
    SheetGroup {
        state.widgetPlacements.filter { it.page == homePage || (wide && it.page == -1) }.forEach { placement ->
            val removeLabel = stringResource(if (placement.page == -1) R.string.remove_widget_from_unfolded_only_page else R.string.remove_widget)
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = FolioSpace.LARGE.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (placement.page == -1) stringResource(R.string.unfolded_only_page) else stringResource(R.string.widget_size_row, placement.spanX, placement.spanY, placement.row + 1), Modifier.weight(1f),
                    color = androidx.compose.ui.graphics.Color.White, fontSize = FolioType.BODY.sp)
                TextButton(onClick = { onWidget(placement.slot) }) { Text(stringResource(R.string.replace)) }
                IconButton(onClick = { onRemoveWidget(placement.slot) }, modifier = Modifier.semantics { contentDescription = removeLabel }) {
                    Icon(Icons.Rounded.RemoveCircle, null, tint = FolioColors.Red) }
            }
            MenuDivider()
        }
        IosActionRow(stringResource(R.string.add_widget_to_this_page), onClick = { onAddWidget(homePage) })
    }
}

/** Keeps Android's pop-up and Folio's island message card from showing for the same message. */
@Composable private fun MessageBannerSettings(avoidDouble: Boolean, onAvoidDouble: (Boolean) -> Unit) {
    SettingsSwitch(stringResource(R.string.dont_double_up_with_android_pop_ups), avoidDouble, onAvoidDouble, "messages-avoid-double-switch")
    CardNote(if (avoidDouble) stringResource(R.string.messages_that_android_already_pops_up_ar)
        else stringResource(R.string.the_island_shows_every_new_message_even))
    if (!avoidDouble) return
    MessageChannelList()
}

/** Brief pop-ups › Other notifications: any app's new notifications in the island, like messages. Off until turned on. */
@Composable private fun IslandAlertSettings(on: Boolean, appsOff: Set<String>, onOn: (Boolean) -> Unit, onChooseApps: () -> Unit) {
    SettingsSwitch(stringResource(R.string.other_notifications_2), on, onOn, "island-alerts-switch")
    if (on) {
        val channels by IslandListenerService.messageChannels.collectAsState()
        val seen = channels.values.filter { !it.isMessage }.map { it.packageName }.toSet() + appsOff
        IosNavRow(stringResource(R.string.apps), if (seen.isEmpty()) null else stringResource(R.string.count_of_total, (seen - appsOff).size, seen.size), onChooseApps, "island-alert-apps")
    }
    CardNote(if (on) stringResource(R.string.new_notifications_from_the_apps_you_choo)
        else stringResource(R.string.show_new_notifications_from_other_apps_i))
}

/** Dynamic Island › Other Notifications: which apps' notifications pop up in the island (apps appear once they've sent one). */
@Composable private fun IslandAlertApps(appsOff: Set<String>, avoidDouble: Boolean, onApp: (String, Boolean) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val channels by IslandListenerService.messageChannels.collectAsState()
    val seen = channels.values.filter { !it.isMessage }.groupBy { it.packageName }
    val pm = context.packageManager
    fun label(pkg: String) = seen[pkg]?.first()?.appLabel
        ?: runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
    val apps = remember(seen.keys, appsOff) { (seen.keys + appsOff).map { it to label(it) }.sortedBy { it.second.lowercase() } }
    SettingsCard(stringResource(R.string.show_in_the_island)) {
        if (apps.isEmpty()) Text(stringResource(R.string.apps_show_up_here_after_they_send_a_noti),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = FolioSpace.MEDIUM.dp))
        apps.forEach { (pkg, name) -> key(pkg) {
            val enabled = pkg !in appsOff
            // With "don't double up" on, a channel Android pops up itself stays with Android; offer the same way over.
            val android = seen[pkg].orEmpty().firstOrNull { it.popsUp }?.takeIf { avoidDouble && enabled }
            val icon = remember(pkg) { runCatching { pm.getApplicationIcon(pkg).toBitmap(84, 84).asImageBitmap() }.getOrNull() }
            Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
                icon?.let { Image(it, null, Modifier.size(29.dp).clip(RoundedCornerShape(7.dp))) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    if (android != null) Text(stringResource(R.string.android_shows_its_own_pop_up), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (android != null) CardAction(stringResource(R.string.use_island), onClick = { runCatching { context.startActivity(android.settingsIntent()) } })
                IosSwitch(enabled, { onApp(pkg, it) }, Modifier.testTag("island-alert-$pkg"))
            }
        } }
    }
    CardNote(if (avoidDouble) stringResource(R.string.apps_that_android_already_pops_up_stay_w)
        else stringResource(R.string.the_island_shows_these_apps_new_notifica), Modifier.padding(horizontal = FolioSpace.LARGE.dp))
}

/** Messaging apps Folio has seen, and whether each one pops up through Android or the island. */
@Composable private fun MessageChannelList() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val channels by IslandListenerService.messageChannels.collectAsState()
    val list = channels.values.filter { it.isMessage }.sortedWith(compareBy({ !it.popsUp }, { it.appLabel }))
    if (list.isEmpty()) CardNote(stringResource(R.string.messaging_apps_appear_here_after_they_po))
    list.forEach { channel ->
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(channel.appLabel, style = MaterialTheme.typography.bodyLarge)
                CardNote(listOfNotNull(channel.channelName, if (channel.popsUp) stringResource(R.string.android_pop_up) else stringResource(R.string.island)).joinToString(" · "))
            }
            // Both ways lead to the app's own notification settings: turn Android's pop-up off to use the island,
            // or back on to get Android's pop-up again (the only way back once an app was switched over).
            TextButton(onClick = { runCatching { context.startActivity(channel.settingsIntent()) } }) {
                Text(if (channel.popsUp) stringResource(R.string.use_island) else stringResource(R.string.android_popup_title))
            }
        }
    }
}

@Composable internal fun SettingsSwitch(label: String, checked: Boolean, onChecked: (Boolean) -> Unit, tag: String? = null) {
    // One accessible element for TalkBack ("label, switch, on"); the whole row toggles.
    Row(Modifier.fillMaxWidth().heightIn(min = 50.dp).semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f).padding(end = FolioSpace.MEDIUM.dp, top = FolioSpace.SNUG.dp, bottom = FolioSpace.SNUG.dp), fontSize = FolioType.BODY.sp); IosSwitch(checked, onChecked, Modifier.then(if (tag != null) Modifier.testTag(tag) else Modifier))
    }
}

/**
 * [peek]: a Home layout slider, so Settings fades while it's dragged by touch (TalkBack and keys change it without a
 * press, so they don't). [default]: a light tick as the value crosses it.
 */
@Composable private fun CustomizationSlider(label: String, valueLabel: String, value: Float,
    range: ClosedFloatingPointRange<Float>, default: Float? = null, peek: Boolean = false, onChange: (Float) -> Unit) {
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val dragged by interaction.collectIsDraggedAsState()
    val pressed by interaction.collectIsPressedAsState()
    val touching = peek && (dragged || pressed)
    var bounds by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
    val span = range.endInclusive - range.start
    if (touching) SideEffect {
        SettingsPeek.value = PeekSlider(label, valueLabel, if (span > 0f) ((value - range.start) / span).coerceIn(0f, 1f) else 0f, bounds)
    }
    DisposableEffect(touching) { onDispose { if (touching) SettingsPeek.value = null } }
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val change: (Float) -> Unit = { next ->
        if (default != null && next != value && kotlin.math.sign(next - default) != kotlin.math.sign(value - default))
            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.SegmentTick)
        onChange(next)
    }
    Column(Modifier.padding(top = FolioSpace.COMPACT.dp, bottom = FolioSpace.HAIR.dp).onGloballyPositioned { bounds = it.boundsInWindow() }) {
        Row { Text(label, Modifier.weight(1f), fontSize = FolioType.BODY.sp); Text(valueLabel, fontSize = FolioType.BODY.sp, color = androidx.compose.ui.graphics.Color.White.copy(alpha = .6f)) }
        IosSlider(value, change, valueRange = range, modifier = Modifier.semantics { contentDescription = label }, interactionSource = interaction) }
}

@Composable internal fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(FolioSpace.SNUG.dp)) {
        Text(title.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = FolioSpace.LARGE.dp, top = FolioSpace.COMPACT.dp).semantics { heading() })
        GroupedCard(MaterialTheme.colorScheme.surfaceContainerHigh, androidx.compose.ui.graphics.Color.White.copy(alpha = .12f),
            Modifier.fillMaxWidth(), content)
    }
}

/** The open Fold for the Inner tab: two Home pages with one Side Bar, laid out with the inner screen's settings. */
@Composable private fun UnfoldedHomePreview(bitmap: android.graphics.Bitmap?, state: LauncherState, height: androidx.compose.ui.unit.Dp) {
    val corner = 18.dp
    val bezel = 5.dp
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Row(Modifier.clip(RoundedCornerShape(corner + bezel)).background(androidx.compose.ui.graphics.Color(0xFF0B0B0C))
            .border(1.dp, androidx.compose.ui.graphics.Color.White.copy(alpha = .2f), RoundedCornerShape(corner + bezel)).padding(bezel)
            .clip(RoundedCornerShape(corner)).clearedDescription(R.string.preview_of_home_on_the_inner_screen)) {
            MiniHomePreview(bitmap, state, height, framed = false, sideBar = state.leftHanded, preset = state.expanded)
            MiniHomePreview(bitmap, state, height, framed = false, sideBar = !state.leftHanded, preset = state.expanded)
        }
    }
}

/** Folio's current app icon as its screens show it. */
internal fun folioIconBitmap(context: android.content.Context, size: Int = 216): androidx.compose.ui.graphics.ImageBitmap? =
    AppIconChoice.current(context).artwork(context, size)

/**
 * Roadmap: what shipped in this version, what's next, later, and ideas being explored. Honest statuses, no dates.
 * Everything here comes from Folio's plan; it changes as feedback comes in.
 */
private enum class RoadmapStatus(@androidx.annotation.StringRes val label: Int, val color: Long) {
    DONE(R.string.in_this_update, FolioColors.Value.Green), BUILDING(R.string.in_progress, FolioColors.Value.Blue),
    PLANNED(R.string.planned, FolioColors.Value.Orange), EXPLORING(R.string.exploring, FolioColors.Value.Purple)
}
private data class RoadmapItem(val icon: ImageVector, val color: Long, val title: String, val detail: String, val status: RoadmapStatus,
    /** Overrides the status label, e.g. "Coming in 0.6.1" for a release that isn't installed yet. */
    val label: String? = null)

/** Icons a roadmap file can name; anything else shows a star. */
private fun roadmapIcon(name: String): ImageVector = when (name) {
    "bug" -> Icons.Rounded.BugReport; "tune" -> Icons.Rounded.Tune; "folder" -> Icons.Rounded.Folder; "apps" -> Icons.Rounded.Apps
    "clock" -> Icons.Rounded.Schedule; "badge" -> Icons.Rounded.Notifications; "notifications" -> Icons.Rounded.NotificationsActive
    "extension" -> Icons.Rounded.Extension; "update" -> Icons.Rounded.SystemUpdate; "circle" -> Icons.Rounded.Circle
    "rings" -> Icons.Rounded.DonutLarge; "corner" -> Icons.Rounded.RoundedCorner; "headphones" -> Icons.Rounded.Headphones
    "weather" -> Icons.Rounded.WbSunny; "store" -> Icons.Rounded.Storefront; "grid" -> Icons.Rounded.GridView
    "history" -> Icons.Rounded.History; "tap" -> Icons.Rounded.TouchApp; "pages" -> Icons.Rounded.ViewCarousel
    "dock" -> Icons.Rounded.Dock; "lock" -> Icons.Rounded.Lock; "news" -> Icons.Rounded.Newspaper; "brush" -> Icons.Rounded.Brush
    "sensor" -> Icons.Rounded.Sensors; "keyboard" -> Icons.Rounded.Keyboard; "palette" -> Icons.Rounded.Palette
    "redeem" -> Icons.Rounded.Redeem; "language" -> Icons.Rounded.Translate; "search" -> Icons.Rounded.Search
    "speed" -> Icons.Rounded.Speed; "accessibility" -> Icons.Rounded.Accessibility; "globe" -> Icons.Rounded.Public
    "person" -> Icons.Rounded.Person; "video" -> Icons.Rounded.Videocam
    else -> Icons.Rounded.Star
}

@Composable private fun ComingSoonPage() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val version = remember { SoftwareUpdate.installedVersion(context) }
    var content by remember { mutableStateOf(Roadmap.local(context)) }
    // Opening the Roadmap is when it checks GitHub for a newer one (at most every few hours).
    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Roadmap.refresh(context) }?.let { content = it }
    }
    val sections = content?.sections.orEmpty().map { section ->
        val release = section.release
        val shipped = release == null || !SoftwareUpdate.isNewer(release, version)
        val title = release?.let { stringResource(if (shipped) R.string.folio_version else R.string.folio_version_coming, it) } ?: section.title.orEmpty()
        title to section.items.map { item ->
            val status = RoadmapStatus.valueOf(item.status.name)
            RoadmapItem(roadmapIcon(item.icon), item.color, item.title, item.detail, status,
                label = when {
                    release == null || status != RoadmapStatus.DONE -> null
                    release == version -> null
                    shipped -> stringResource(R.string.released)
                    else -> stringResource(R.string.coming_in, release)
                })
        }
    }
    CardNote(content?.note ?: stringResource(R.string.where_folio_is_headed), Modifier.padding(horizontal = FolioSpace.TINY.dp))
    sections.forEach { (title, items) ->
        SettingsCard(title) {
            items.forEachIndexed { index, item ->
                RoadmapRow(item, last = index == items.lastIndex)
            }
        }
    }
    SheetGroup {
        IosActionRow(stringResource(R.string.suggest_a_feature), "coming-soon-suggest") {
            runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,
                android.net.Uri.parse(BugReport.NEW_ISSUE + "?template=feature_request.yml"))) }
        }
    }
}

/** One roadmap item: a timeline dot and line on the left, the item's icon, text and a status label. */
@Composable private fun RoadmapRow(item: RoadmapItem, last: Boolean) {
    val statusColor = androidx.compose.ui.graphics.Color(item.status.color)
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.Top) {
        Box(Modifier.width(18.dp).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
            if (!last) Box(Modifier.padding(top = 22.dp).width(2.dp).fillMaxHeight().background(statusColor.copy(alpha = .3f)))
            Box(Modifier.padding(top = FolioSpace.LARGE.dp).size(10.dp).clip(androidx.compose.foundation.shape.CircleShape).background(statusColor))
        }
        Row(Modifier.weight(1f).padding(start = FolioSpace.SMALL.dp, top = FolioSpace.COMPACT.dp, bottom = FolioSpace.COMPACT.dp, end = FolioSpace.SNUG.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(androidx.compose.ui.graphics.Color(item.color)), contentAlignment = Alignment.Center) {
                Icon(item.icon, null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.title, color = androidx.compose.ui.graphics.Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f, fill = false))
                    Text(item.label ?: stringResource(item.status.label), color = statusColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false,
                        modifier = Modifier.padding(start = FolioSpace.SMALL.dp).clip(RoundedCornerShape(50)).background(statusColor.copy(alpha = .16f)).padding(horizontal = 7.dp, vertical = FolioSpace.HAIR.dp))
                }
                Text(item.detail, color = androidx.compose.ui.graphics.Color.White.copy(alpha = .62f), fontSize = 14.sp, lineHeight = 19.sp)
            }
        }
    }
}


/** iOS-style alternate app icons: tap one to use it for Folio's app entry. */
@Composable private fun AppIconCard(onChanged: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var current by remember { mutableStateOf(AppIconChoice.current(context)) }
    SettingsCard(stringResource(R.string.app_icon)) {
        Row(Modifier.fillMaxWidth().padding(vertical = FolioSpace.SNUG.dp), horizontalArrangement = Arrangement.spacedBy(FolioSpace.XL.dp)) {
            AppIconChoice.entries.forEach { choice ->
                val choiceName = stringResource(choice.label)
                val bitmap = remember(choice) { choice.artwork(context, 180) }
                val selected = choice == current
                Column(Modifier.clip(RoundedCornerShape(FolioRadius.GROUP.dp)).clickable {
                    if (!selected) { AppIconChoice.set(context, choice); current = choice; onChanged() }
                }.padding(FolioSpace.SNUG.dp).semantics { this.selected = selected; contentDescription = choiceName + " app icon" }.testTag("app-icon-${choice.name.lowercase()}"),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(64.dp).then(if (selected) Modifier.border(2.5.dp, LocalAccent.current.ink, RoundedCornerShape(18.dp)).padding(FolioSpace.TINY.dp) else Modifier.padding(FolioSpace.TINY.dp))) {
                        bitmap?.let { androidx.compose.foundation.Image(it, null, Modifier.fillMaxSize().clip(RoundedCornerShape(FolioRadius.CARD.dp))) }
                    }
                    Text(stringResource(choice.label), color = if (selected) LocalAccent.current.ink else androidx.compose.ui.graphics.Color.White, fontSize = FolioType.FOOTNOTE.sp, modifier = Modifier.padding(top = FolioSpace.SNUG.dp))
                }
            }
        }
        CardNote(stringResource(R.string.changes_folio_s_icon_in_the_app_library))
    }
}

/** Beta label beside a title, like TestFlight features. */
@Composable private fun BetaTag() {
    Text(stringResource(R.string.beta), color = FolioColors.Orange, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false,
        modifier = Modifier.padding(start = FolioSpace.SMALL.dp).border(1.dp, FolioColors.Orange, RoundedCornerShape(5.dp))
            .padding(horizontal = 5.dp, vertical = 1.dp))
}

/** Layout History (Beta): automatic snapshots of Home before big changes, each restorable. */
@Composable private fun LayoutHistoryCard(state: LauncherState, model: LauncherModel, onClose: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) { LayoutHistory.load(context) }
    val snapshots by LayoutHistory.snapshots.collectAsState()
    var confirm by remember { mutableStateOf<LayoutSnapshot?>(null) }
    SettingsCard(stringResource(R.string.layout_history)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) { Text(stringResource(R.string.save_home_before_big_changes), Modifier.weight(1f, fill = false)); BetaTag() }
            IosSwitch(state.layoutHistory, model::setLayoutHistory, Modifier.testTag("layout-history-switch"))
        }
        if (state.layoutHistory) {
            // Read here rather than in the click: the name is what the snapshot is called in the list below it.
            val savedByYou = stringResource(R.string.saved_by_you)
            CardAction(stringResource(R.string.save_current_layout), onClick = { model.saveLayoutSnapshot(savedByYou, force = true) }, modifier = Modifier.testTag("layout-history-save"))
            snapshots.forEach { snapshot ->
                // Merged, like the switch row above: without it every saved layout offers a button TalkBack reads as
                // just "Restore", and there is no way to hear which layout it would put back. Merged, the row is one
                // thing to land on - "Before restoring a backup, 19 Sep 2026 8:14 PM, Restore" - carrying the button's
                // own action, and it is a bigger target besides.
                Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).semantics(mergeDescendants = true) {},
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(snapshot.reason, fontSize = 16.sp)
                        CardNote(java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT).format(java.util.Date(snapshot.time)))
                    }
                    CardAction(stringResource(R.string.restore), onClick = { confirm = snapshot })
                }
            }
        }
        CardNote(stringResource(R.string.before_restoring_a_backup_arrange_like_i, LayoutHistory.MAX))
    }
    confirm?.let { snapshot ->
        AlertDialog(onDismissRequest = { confirm = null },
            title = { Text(stringResource(R.string.restore_this_layout)) },
            text = { Text(stringResource(R.string.home_goes_back_to_how_it_was_1, snapshot.reason.lowercase())) },
            confirmButton = { TextButton(onClick = { model.restoreLayoutSnapshot(snapshot); confirm = null; onClose() }) { Text(stringResource(R.string.restore)) } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text(stringResource(R.string.cancel)) } })
    }
}

/** Recent-app dots (Beta): needs Usage Access, asked for right here when it's turned on. */
@Composable private fun RecentDotsCard(state: LauncherState, model: LauncherModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    SettingsCard(stringResource(R.string.dock)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) { Text(stringResource(R.string.recent_app_dots), Modifier.weight(1f, fill = false)); BetaTag() }
            IosSwitch(state.dockRecentDots, { on ->
                model.setDockRecentDots(on)
                if (on && !Suggestions.hasUsageAccess(context)) runCatching {
                    context.startActivity(Suggestions.usageAccessIntent(context).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }, Modifier.testTag("dock-recent-dots-switch"))
        }
        CardNote(stringResource(R.string.a_small_dot_beside_dock_apps_you_ve_used))
    }
}

/** Settings › Wallpaper & Appearance › Glass: one style menu for most people, sliders to fine-tune. */
@Composable private fun GlassCardSettings(state: LauncherState, model: LauncherModel) {
    val presets = listOf("CLEAR" to .08f, "LIGHT" to .16f, "FROSTED" to .26f, "SOLID" to .55f)
    val rail = state.statusStyle.railGlass
    val current = presets.firstOrNull { (_, v) -> kotlin.math.abs(state.widgetGlass - v) < .005f && kotlin.math.abs(rail - v) < .005f }?.first ?: "CUSTOM"
    val solid = LocalSolidGlass.current
    SettingsCard(stringResource(R.string.glass)) {
        if (!solid) IosMenuRow(stringResource(R.string.style), listOf("CLEAR" to stringResource(R.string.clear), "LIGHT" to stringResource(R.string.light), "FROSTED" to stringResource(R.string.frosted), "SOLID" to stringResource(R.string.solid)) +
            (if (current == "CUSTOM") listOf("CUSTOM" to stringResource(R.string.custom)) else emptyList()), current,
            { key -> presets.firstOrNull { it.first == key }?.let { model.setGlassPreset(it.second) } }, tag = "glass-style")
        if (!solid) {
            CustomizationSlider(stringResource(R.string.widgets), "${(state.widgetGlass * 100).toInt()}%", state.widgetGlass, 0f..0.8f, onChange = model::setWidgetGlass)
            CustomizationSlider(stringResource(R.string.side_rail), "${(rail * 100).toInt()}%", rail, 0f..0.8f) { model.setStatusStyle(state.statusStyle.copy(railGlass = it)) }
            CustomizationSlider(stringResource(R.string.outline), if (state.glassOutline < .01f) stringResource(R.string.off) else "${(state.glassOutline * 100).toInt()}%", state.glassOutline, 0f..0.5f, onChange = model::setGlassOutline)
        }
        // Clear ↔ Tinted, like iOS: all the way left is clear glass, the middle is Folio's usual wallpaper tint.
        val tint = if (state.tintedGlass) state.glassTint else 0f
        CustomizationSlider(stringResource(R.string.wallpaper_tint), when { tint < .01f -> stringResource(R.string.clear); tint > .99f -> stringResource(R.string.tinted); else -> "${(tint * 100).toInt()}%" },
            tint, 0f..1f, default = .5f, peek = true, onChange = model::setGlassTint)
        SettingsSwitch(stringResource(R.string.reduce_transparency), state.reduceTransparency, model::setReduceTransparency, "reduce-transparency-switch")
        CardNote(if (solid && !state.reduceTransparency) stringResource(R.string.glass_is_nearly_solid_because_android_s)
            else if (solid) stringResource(R.string.widgets_the_side_bar_and_the_dock_are_ne)
            else stringResource(R.string.frost_is_how_see_through_widgets_and_the))
    }
}

/** A live sample of the badge settings on a plain icon, so each change shows right away. */
@Composable private fun BadgePreviewRow(state: LauncherState) {
    val look = LocalIconLook.current
    Row(Modifier.fillMaxWidth().padding(vertical = FolioSpace.SMALL.dp).clearedDescription(R.string.badge_preview),
        horizontalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally)) {
        listOf(1, 12).forEach { count ->
            Box(Modifier.size(56.dp)) {
                Box(Modifier.fillMaxSize().clip(RoundedCornerShape(FolioRadius.CARD.dp)).background(FolioColors.MenuSurface))
                val color = look.badgeColor.fixed?.let { androidx.compose.ui.graphics.Color(it) }
                    ?: if (look.badgeColor == BadgeColor.SOFT) androidx.compose.ui.graphics.Color(0xFFE5E5EA) else FolioColors.RedLight
                IconBadge(count, state.badgeStyle, color, state.badgeLook, state.badgeSize.scale)
            }
        }
    }
}

/** iOS-style "Save Backup" alert with a name field; the file lands in Download/Folio. */
@Composable private fun BackupNameAlert(onCancel: () -> Unit, onSave: (String) -> Unit) {
    val backupContext = androidx.compose.ui.platform.LocalContext.current
    var name by remember { mutableStateOf(backupContext.getString(R.string.default_backup_name, java.time.LocalDate.now().toString())) }
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    AlertDialog(onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.save_backup_2)) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.saved_to_1, FolioFiles.displayPath), fontSize = FolioType.FOOTNOTE.sp)
                androidx.compose.foundation.text.BasicTextField(name, { name = it.take(60) },
                    Modifier.padding(top = FolioSpace.MEDIUM.dp).fillMaxWidth().clip(RoundedCornerShape(8.dp))
                        .background(androidx.compose.ui.graphics.Color.White.copy(alpha = .1f)).padding(horizontal = FolioSpace.COMPACT.dp, vertical = FolioSpace.SMALL.dp)
                        .focusRequester(focus).testTag("backup-name"),
                    singleLine = true, textStyle = androidx.compose.ui.text.TextStyle(color = androidx.compose.ui.graphics.Color.White, fontSize = FolioType.SUBHEAD.sp),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(androidx.compose.ui.graphics.Color.White),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { onSave(name) }))
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) } })
}

/** Tweak Library: every built-in tweak as a package, Sileo-style. Get adds it to Settings › Tweaks and turns it on. */
@Composable private fun TweakLibraryPage(state: LauncherState, model: LauncherModel, onOpen: (TweakFeature) -> Unit) {
    CardNote(stringResource(R.string.built_into_folio_and_off_until_you_get_t), Modifier.padding(horizontal = FolioSpace.TINY.dp))
    SheetGroup {
        val libraryContext = androidx.compose.ui.platform.LocalContext.current
        remember { visibleTweaks(libraryContext) }.forEachIndexed { index, tweak ->
            if (index > 0) MenuDivider()
            val installed = tweak.id in state.installedTweaks
            Row(Modifier.fillMaxWidth().clickable(role = androidx.compose.ui.semantics.Role.Button, onClickLabel = stringResource(R.string.show_details)) { onOpen(tweak) }
                .padding(horizontal = FolioSpace.COMFY.dp, vertical = FolioSpace.COMPACT.dp).testTag("library-tweak-${tweak.id}"),
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(FolioRadius.CONTROL.dp)).background(androidx.compose.ui.graphics.Color(tweak.color)), contentAlignment = Alignment.Center) {
                    Icon(tweak.icon, null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(tweak.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.inspired_by_1, tweak.inspiredBy.substringBefore(" by ")), fontSize = FolioType.FOOTNOTE.sp, color = androidx.compose.ui.graphics.Color.White.copy(alpha = .6f), maxLines = 1)
                }
                // Sileo's pill: Get in blue; once installed it reads Open and goes to the tweak's settings.
                val actionLabel = stringResource(if (installed) R.string.open_tweak else R.string.get_tweak, tweak.name)
                Text(stringResource(if (installed) R.string.open else R.string.get), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                    color = if (installed) LocalAccent.current.ink else androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier.minimumInteractiveComponentSize().clip(RoundedCornerShape(50)).background(if (installed) androidx.compose.ui.graphics.Color.White.copy(alpha = .12f) else LocalAccent.current.fill)
                        .clickable(role = androidx.compose.ui.semantics.Role.Button) { if (installed) onOpen(tweak) else model.installTweak(tweak) }.padding(horizontal = FolioSpace.LARGE.dp, vertical = FolioSpace.SNUG.dp)
                        .semantics { contentDescription = actionLabel })
            }
        }
    }
}

/** Settings › Software Update, laid out like iOS: the version, one clear action, and automatic updates. */
@Composable private fun SoftwareUpdatePage() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val status by SoftwareUpdate.status.collectAsState()
    val installed = remember { SoftwareUpdate.installedVersion(context) }
    val supported = SoftwareUpdate.supported(context)
    // Like iOS: the page checks when you open it, unless a check already ran this session.
    LaunchedEffect(Unit) { if (supported && status == SoftwareUpdate.Status.Idle) SoftwareUpdate.startCheck(context) }
    val icon = remember { folioIconBitmap(context) }
    SheetGroup {
        Row(Modifier.fillMaxWidth().padding(FolioSpace.LARGE.dp), verticalAlignment = Alignment.CenterVertically) {
            icon?.let { Image(it, null, Modifier.size(56.dp).clip(RoundedCornerShape(13.dp))) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.folio_version, installed), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(when {
                    !supported -> stringResource(R.string.folio_dev_a_test_build_it_updates_from_n)
                    status == SoftwareUpdate.Status.Checking -> stringResource(R.string.checking_for_updates)
                    status == SoftwareUpdate.Status.UpToDate -> stringResource(R.string.folio_is_up_to_date)
                    status is SoftwareUpdate.Status.Failed -> (status as SoftwareUpdate.Status.Failed).message
                    else -> SoftwareUpdate.lastChecked(context).takeIf { it > 0 }?.let {
                        val ago = if (System.currentTimeMillis() - it < 60_000) stringResource(R.string.just_now)
                            else android.text.format.DateUtils.getRelativeTimeSpanString(it, System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS)
                        stringResource(R.string.last_checked, ago)
                    }
                        ?: stringResource(R.string.updates_come_from_folio_s_github_release)
                }, style = MaterialTheme.typography.bodySmall,
                    color = if (status is SoftwareUpdate.Status.Failed) FolioColors.Red else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (!supported) return
    val release = when (val s = status) {
        is SoftwareUpdate.Status.Available -> s.release
        is SoftwareUpdate.Status.Downloading -> s.release
        is SoftwareUpdate.Status.Ready -> s.release
        else -> null
    }
    if (release != null) UpdateCard(release, status)
    else SheetGroup {
        IosActionRow(stringResource(R.string.check_for_updates), "update-check", enabled = status !is SoftwareUpdate.Status.Checking && status != SoftwareUpdate.Status.Installing) {
            SoftwareUpdate.startCheck(context)
        }
    }
    var mode by remember { mutableStateOf(SoftwareUpdate.mode(context)) }
    val notifyPermission = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { }
    var beta by remember { mutableStateOf(SoftwareUpdate.beta(context)) }
    SettingsCard(stringResource(R.string.updates)) {
        IosMenuRow(stringResource(R.string.automatic_updates), SoftwareUpdate.Mode.entries.map { it to stringResource(it.label) }, mode, {
            mode = it; SoftwareUpdate.setMode(context, it)
            if (it != SoftwareUpdate.Mode.MANUAL && !SoftwareUpdate.canPostNotifications(context))
                notifyPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }, tag = "update-mode")
        CardNote(when (mode) {
            SoftwareUpdate.Mode.AUTOMATIC -> stringResource(R.string.folio_checks_once_a_day_downloads_new_ve)
            SoftwareUpdate.Mode.NOTIFY -> stringResource(R.string.folio_checks_once_a_day_and_sends_a_noti)
            SoftwareUpdate.Mode.MANUAL -> stringResource(R.string.folio_only_checks_when_you_open_this_pag)
        })
        IosMenuRow(stringResource(R.string.beta_updates), listOf(false to stringResource(R.string.off), true to stringResource(R.string.folio_beta)), beta, { beta = it; SoftwareUpdate.setBeta(context, it) }, tag = "update-beta")
        CardNote(if (beta) stringResource(R.string.you_ll_get_folio_betas_as_well_as_public)
            else stringResource(R.string.turn_on_to_try_new_features_before_they))
        // A supporter's code turns this on by itself, so say where it came from rather than leaving them to wonder.
        if (beta && Supporter.has(context, BetaCodes.SCOPE_BETA)) CardNote(stringResource(R.string.a_supporter_code_turned_this_on_you_can))
    }
    CardNote(stringResource(R.string.every_update_is_checked_against_its_publ), Modifier.padding(horizontal = FolioSpace.LARGE.dp))
}

/** The available update, like iOS's: version, size, the release notes, progress, and Update Now / Update Tonight. */
@Composable internal fun UpdateCard(release: SoftwareUpdate.Release, status: SoftwareUpdate.Status) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var expanded by remember(release.version) { mutableStateOf(false) }
    val notes = remember(release.notes) { releaseNoteLines(release.notes) }
    SheetGroup {
        Column(Modifier.fillMaxWidth().padding(FolioSpace.LARGE.dp), verticalArrangement = Arrangement.spacedBy(FolioSpace.COMPACT.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val icon = remember { folioIconBitmap(context) }
                icon?.let { Image(it, null, Modifier.size(44.dp).clip(RoundedCornerShape(FolioRadius.CONTROL.dp))) }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(stringResource(if (release.prerelease) R.string.folio_version_beta else R.string.folio_version, release.version), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    CardNote(listOfNotNull(stringResource(R.string.mccal_codes), release.size.takeIf { it > 0 }?.let { android.text.format.Formatter.formatShortFileSize(context, it) })
                        .joinToString(" · "))
                }
            }
            if (notes.isNotEmpty()) {
                (if (expanded) notes else notes.take(6)).forEach { line -> Text(line, style = MaterialTheme.typography.bodyMedium) }
                // A beta's notes have no page to open (its repository is private), so once they're expanded there's no link.
                if ((!expanded && notes.size > 6) || release.notesUrl.isNotBlank()) Text(if (!expanded && notes.size > 6) stringResource(R.string.more) else stringResource(R.string.full_release_notes),
                    color = LocalAccent.current.ink, style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable {
                        if (!expanded && notes.size > 6) expanded = true
                        else runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(release.notesUrl))) }
                    }.padding(vertical = FolioSpace.TINY.dp))
            }
            when (status) {
                is SoftwareUpdate.Status.Downloading -> {
                    val fraction = status.fraction
                    if (fraction != null) LinearProgressIndicator({ fraction }, Modifier.fillMaxWidth())
                    else LinearProgressIndicator(Modifier.fillMaxWidth())
                    CardNote(stringResource(R.string.downloading) + (fraction?.let { " · ${(it * 100).toInt()}%" } ?: "…"))
                }
                is SoftwareUpdate.Status.Ready -> CardNote(if (status.tonight) stringResource(R.string.downloaded_and_verified_it_installs_toni)
                    else stringResource(R.string.downloaded_and_verified_it_installs_when))
                else -> Unit
            }
            if (status !is SoftwareUpdate.Status.Downloading) Row(horizontalArrangement = Arrangement.spacedBy(FolioSpace.COMPACT.dp)) {
                FolioButton(stringResource(R.string.update_now), {
                    if (status is SoftwareUpdate.Status.Ready) SoftwareUpdate.installReadyNow(context) else SoftwareUpdate.startInstall(context, release)
                }, Modifier.weight(1f), tag = "update-install")
                if (!(status is SoftwareUpdate.Status.Ready && status.tonight)) FolioButton(stringResource(R.string.update_tonight),
                    { SoftwareUpdate.startUpdateTonight(context, release) }, Modifier.weight(1f), style = FolioButtonStyle.TONAL, tag = "update-tonight")
            }
            CardNote(stringResource(R.string.updating_restarts_home_for_a_moment))
        }
    }
}

/** GitHub release notes as plain lines: headings and emphasis markers dropped, bullets kept. */
internal fun releaseNoteLines(markdown: String): List<String> = markdown.lines()
    .map { it.trim() }
    // Folio's notes open with install and verify steps; the part worth reading here starts at "What's new".
    .let { lines -> lines.indexOfFirst { Regex("""^#+\s*What.s new""", RegexOption.IGNORE_CASE).containsMatchIn(it) }
        .takeIf { it >= 0 }?.let { lines.drop(it + 1) } ?: lines }
    .filter { it.isNotEmpty() && !it.startsWith("![") && !it.startsWith("<") && !it.startsWith("```") && !it.startsWith("> ") }
    .map { line ->
        line.removePrefix("### ").removePrefix("## ").removePrefix("# ")
            .replace(Regex("""\*\*|__|`"""), "")
            .replace(Regex("""\[([^\]]+)]\([^)]+\)"""), "$1")
            .let { if (it.startsWith("- ") || it.startsWith("* ")) "• " + it.drop(2) else it }
    }

/** Why Automatic landed on this many rows: which screens Folio has measured, and which one sets the limit. */
internal fun automaticRowsNote(state: LauncherState, strings: Strings): String {
    val cover = state.homeFitCompact
    val inner = state.homeFitExpanded
    return when {
        state.homeRows > 0 -> strings.plural(R.plurals.rows_note_fixed, state.homeRows, state.homeRows)
        cover == 0 && inner == 0 -> strings.get(R.string.rows_note_unmeasured)
        inner == 0 -> strings.get(R.string.rows_note_cover_only, cover)
        cover == 0 -> strings.get(R.string.rows_note_inner_only, inner)
        cover == inner && cover < MAX_APP_ROWS -> strings.plural(R.plurals.rows_note_same, cover, cover)
        cover == inner -> strings.plural(R.plurals.rows_note_same_max, cover, cover)
        else -> strings.get(R.string.rows_note_different, cover, inner, minOf(cover, inner))
    }
}

/** A content description from strings.xml; semantics blocks can't read resources themselves. */
@Composable internal fun Modifier.description(@androidx.annotation.StringRes id: Int): Modifier =
    stringResource(id).let { text -> semantics { contentDescription = text } }

/** Like [description], replacing whatever the children would have said. */
@Composable internal fun Modifier.clearedDescription(@androidx.annotation.StringRes id: Int): Modifier =
    stringResource(id).let { text -> clearAndSetSemantics { contentDescription = text } }
