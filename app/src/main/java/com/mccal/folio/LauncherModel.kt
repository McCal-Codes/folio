package com.mccal.folio

import android.app.Application
import android.content.ComponentName
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.BreakIterator
import java.text.Collator

data class AppEntry(
    val id: String,
    val label: String,
    val icon: Bitmap,
    val component: ComponentName = ComponentName.unflattenFromString(parseProfileAppId(id)?.component ?: id)
        ?: ComponentName("", ""),
    val user: UserHandle = Process.myUserHandle(),
    val userSerial: Long = 0,
    val profileLabel: String = "Personal",
    val isWork: Boolean = false,
    val available: Boolean = true,
    /** The name Android reports, kept so a custom name can be changed or cleared without reloading apps. */
    val systemLabel: String = label,
) {
    val packageName: String get() = component.packageName
    /** A pinned shortcut (a website or app action someone added to Home) rather than an app. */
    val isShortcut: Boolean get() = component.className.startsWith(SHORTCUT_CLASS_PREFIX)
    val shortcutId: String? get() = component.className.takeIf { isShortcut }?.removePrefix(SHORTCUT_CLASS_PREFIX)
}

/** Pinned shortcuts live on Home as entries whose component names the owning app and "#shortcut:" plus the shortcut id. */
const val SHORTCUT_CLASS_PREFIX = "#shortcut:"

/** Custom names are capped so a renamed icon still reads as a label under it. */
const val MAX_APP_NAME = 40

/**
 * The first [max] characters of a name, cut where a reader would see a character rather than where the string
 * happens to have one. An emoji is two chars to Java and a family is eleven, so `take` alone can leave half of
 * one behind at the cap, which draws as a hollow box.
 *
 * The JDK's character breaks predate emoji joined with U+200D, so a cut inside a family lands between its people;
 * dropping a joiner left at the end turns that into the first of them standing alone, which at least reads.
 */
internal fun String.takeAppName(max: Int = MAX_APP_NAME): String {
    if (length <= max) return this
    val characters = BreakIterator.getCharacterInstance().also { it.setText(this) }
    // A cluster longer than the whole cap (sixty combining marks on one letter) has no break to fall back to, so
    // it is cut at the cap: a degenerate glyph stacked up the screen is worse than one cut mid-cluster.
    val end = characters.preceding(max + 1).takeIf { it > 0 }
        ?: characters.following(0).takeIf { it in 1..max } ?: minOf(max, length)
    return substring(0, end).trimEnd(JOINER)
}

/** Zero-width joiner: the thread between the people in a family emoji, and between the parts of many others. */
private const val JOINER = '\u200D'

/** Sets the custom name for [id]; a blank name clears it, so the app goes back to the name Android reports. */
fun editAppName(names: Map<String, String>, id: String, name: String): Map<String, String> {
    val trimmed = name.trim().takeAppName()
    return if (trimmed.isEmpty()) names - id else names + (id to trimmed)
}

/**
 * Custom names over the labels Android reports. Every screen reads [AppEntry.label], so renaming once here
 * covers Home, the dock, folders, the App Library and search.
 */
internal fun List<AppEntry>.withAppNames(names: Map<String, String>): List<AppEntry> {
    if (names.isEmpty() && none { it.label != it.systemLabel }) return this
    val collator = Collator.getInstance()
    return map { app -> (names[app.id] ?: app.systemLabel).let { if (it == app.label) app else app.copy(label = it) } }
        .sortedWith { a, b -> collator.compare(a.label, b.label) }
}

data class LauncherState(
    val apps: List<AppEntry> = emptyList(),
    val profiles: List<AppProfile> = emptyList(),
    val folders: List<FolderEntry> = emptyList(),
    val homeSlots: List<String?> = emptyList(),
    val leadingSlots: List<String?> = List(HOME_CELLS) { null },
    val editRevision: Int = 0,
    val canUndoEdit: Boolean = false,
    val dock: List<String?> = List(4) { null },
    val widgetPlacements: List<WidgetPlacement> = DEFAULT_WIDGET_PLACEMENTS,
    val widgetRestores: List<WidgetRestore> = emptyList(),
    val googleSearch: Boolean = true,
    val compact: LayoutPreset = LayoutPreset(),
    val expanded: LayoutPreset = LayoutPreset(),
    val labels: Boolean = true,
    val verticalStatus: Boolean = true,
    /** Mirror the side rail (dock, status, controls) to the left edge. */
    val leftHanded: Boolean = false,
    /** App ids kept out of All apps and app search (Home shortcuts are unaffected). */
    val hiddenApps: Set<String> = emptySet(),
    /** Show the vertical island (media and live progress) in the side rail. */
    val island: Boolean = true,
    /** Top pulls open Folio's iOS-style panels instead of the Android system shade. */
    val folioPanels: Boolean = true,
    val minPages: Int = 1,
    val statusStyle: StatusStyle = StatusStyle(),
    val foldEffect: Boolean = true,
    val foldIntensity: Float = 1f,
    /** Fold style: blur only (false) or iPhone Duo — still 1:1 right half plus blur (true). */
    val foldSnapshot: Boolean = false,
    val stayAwakeOnFold: Boolean = true,
    /** Blur of Home behind panels and Spotlight, 0…1. */
    val panelBlur: Float = 1f,
    val notificationClock: Boolean = true,
    val groupNotifications: Boolean = true,
    val dockEverywhere: Boolean = false,
    /** Buttons in Every App: a large Back / Home / Recents bar over other apps (Folio's accessibility service). */
    val buttonBar: Boolean = false,
    /** Bar height in dp (44 standard, 52 large, 60 extra large) and width as a share of the screen. */
    val buttonBarHeight: Float = 52f,
    val buttonBarWidth: Float = .5f,
    /** Android's Back · Home · Recents order instead of Samsung's Recents · Home · Back. */
    val buttonBarAndroidOrder: Boolean = false,
    val buttonBarLight: Boolean = false,
    /** Fades the bar when you haven't touched it for a moment. */
    val buttonBarFade: Boolean = true,
    val iconStyle: IconStyle = IconStyle.DEFAULT,
    val standBy: Boolean = true,
    /** Spotlight sections the user turned off (names of [SpotlightSection]). */
    val spotlightHidden: Set<String> = emptySet(),
    /** Engine for Enter in search: a [WebSearchTarget] name. */
    val searchEngine: String = "GOOGLE",
    /** Island system pop-ups the user turned off (IslandEventKind names). */
    val islandEventsOff: Set<String> = emptySet(),
    val libraryCategories: Boolean = true,
    /** The Personal and Work switch in the App Library (only shown with a work profile); off shows personal apps only. */
    val libraryWork: Boolean = true,
    val iconTint: Long = FolioColors.Value.Warning,
    val iconShape: IconShape = IconShape.DEFAULT,
    /** Package of the selected third-party icon pack, or null for app icons. */
    val iconPack: String? = null,
    val badgeStyle: BadgeStyle = BadgeStyle.DOT,
    val badgeColor: BadgeColor = BadgeColor.RED,
    val badgeLook: BadgeLook = BadgeLook.IOS,
    val badgeSize: BadgeSize = BadgeSize.STANDARD,
    /** Clear Badges When Opened: an app's badge goes quiet once you open it (see [BadgesWhenOpened]). */
    val badgesWhenOpened: Boolean = false,
    /** Badge counts already seen, by package name: the count that was showing when the app was last opened. */
    val badgesSeen: Map<String, Int> = emptyMap(),
    /** iOS "Search" capsule on Home in place of the page dots. */
    val searchPill: Boolean = true,
    /** Swipe down on Home (below the top edge) opens Spotlight. */
    /** Swipe down on Home: SPOTLIGHT, NOTIFICATIONS (Folio's or Android's, whichever the panels setting says) or OFF. */
    val swipeDownHome: String = "SPOTLIGHT",
    /** App for messaging contacts from Spotlight: null = default texting app, or OpenBubbles/BlueBubbles. */
    val messagesApp: String? = null,
    val messagesAvoidDouble: Boolean = true,
    val islandAlerts: Boolean = false,
    val islandAlertAppsOff: Set<String> = emptySet(),
    /** Control Center's small controls, in order (names of [CcControl]). */
    val ccControls: List<String> = CcControl.DEFAULTS,
    val ccSize: PanelSize = PanelSize.STANDARD,
    /** Unfolded: Control Center in the middle instead of under the right-hand pull. */
    val ccCentered: Boolean = false,
    /** Unfolded: iPad-style Notification Center (clock left, notifications right). */
    val ncSplit: Boolean = true,
    /** Smart Stacks: widget placement slot → extra widget ids stacked behind that placement's widget. */
    val widgetStacks: Map<Int, List<Int>> = emptyMap(),
    /** Smart Rotate: stacks move to the widget that matters now. */
    val stackRotate: Boolean = true,
    /** Live activities (music, calls, timers…) under the status in the side rail instead of at the camera. Off: the island stays on the camera. */
    val railActivities: Boolean = false,
    /** iOS "Newly Downloaded Apps": false = App Library only (Android's way), true = also add to Home. */
    val addNewAppsToHome: Boolean = false,
    /** Beta: save Home before big changes so they can be restored (Settings › Backup › Layout History). */
    val layoutHistory: Boolean = false,
    /** Beta: a dot under dock apps used in the last hour (needs Usage Access). */
    val dockRecentDots: Boolean = false,
    val focusModes: List<FocusMode> = DEFAULT_FOCUS_MODES,
    /** The Focus that's on, by id; null when none. */
    val activeFocus: String? = null,
    /** The page left of Home: "TODAY" (Folio's Today View) or "DISCOVER" (Google Discover). */
    val leftPage: String = "TODAY",
    val todayWidgets: List<TodayWidget> = DEFAULT_TODAY_WIDGETS,
    /** Unfolded: "PAGE" (swipe left of Home), "BESIDE" (always next to Home, iPad-style) or "OFF". */
    val todayUnfolded: String = "PAGE",
    /** Show Android's own home-screen wallpaper behind Folio (live wallpapers included) instead of Folio's background. */
    val systemWallpaper: Boolean = false,
    /** Text drawn on the wallpaper: "AUTO" follows the wallpaper, "LIGHT" white, "DARK" dark. */
    val homeInk: String = "AUTO",
    /** iOS-style tinted materials: Home's glass takes on the wallpaper's color. */
    val tintedGlass: Boolean = true,
    /** Wallpaper Tint for glass (Clear ↔ Tinted), 0–1; half is Folio's original tint. Used while [tintedGlass] is on. */
    val glassTint: Float = .5f,
    /** Accessibility › Reduce Transparency: glass becomes nearly solid (also follows Android's high contrast). */
    val reduceTransparency: Boolean = false,
    /** Black rounded corners over Home, like the iPhone Duo concept (issue #8). */
    val roundedCorners: Boolean = false,
    /** Dock along the bottom on phone-sized screens (the cover) in portrait, instead of on the Side Bar. */
    val cornerRadius: Float = 40f,
    /** Frost behind Home's widgets (0 = clear, 1 = solid). The Side Bar's is statusStyle.railGlass. */
    val widgetGlass: Float = .26f,
    val folderColumns: Int = 0,
    /** Tweaks the user has added from the Tweak Library; only these show in Settings › Tweaks. */
    val installedTweaks: Set<String> = emptySet(),
    val folderBackground: FolderBackground = FolderBackground.GLASS,
    val labelSize: LabelSize = LabelSize.STANDARD,
    val motionSpeed: MotionSpeed = MotionSpeed.STANDARD,
    /** How Home pages move as you swipe between them. [PageEffect.NONE] is the default and the flat swipe. */
    val pageEffect: PageEffect = PageEffect.NONE,
    /** Strength of the thin light outline around widgets and Side Bar capsules (0 = none). */
    val glassOutline: Float = .16f,
    /** Darken the wallpaper while Folio's dark appearance is on. */
    val dimWallpaperDark: Boolean = true,
    /**
     * iOS's legibility gradient: darken the top and bottom of the background so white text reads over a pale
     * wallpaper (A11Y-9). See [HomeScrim] for what it draws and how it composes with [dimWallpaperDark] and [homeInk].
     */
    val homeScrim: Boolean = true,
    /** Tinted icons use the wallpaper's color instead of [iconTint]. */
    val iconTintFromWallpaper: Boolean = false,
    /** Velvet-style: notification cards take on their app icon's color. */
    val tintNotifications: Boolean = false,
    /** ColorFlow-style: music cards and the island's sound bars take on the album art's color. */
    val tintMedia: Boolean = true,
    /** Harbor-style: dock icons magnify under your finger. */
    val dockMagnify: Boolean = false,
    /** Velox-style: swipe up on a Home app icon for its quick panel. */
    val appPanels: Boolean = true,
    /** Activator-style trigger → action (names of [FolioTrigger] → [FolioAction]). */
    val triggerActions: Map<String, String> = emptyMap(),
    val haptics: Boolean = true,
    /** iPhone-style cover shown when unlocking straight to Home. */
    val lockCover: Boolean = true,
    /** Per-screen overrides for tweaks: feature id → screen → "ON"/"OFF" (absent = follow the main switch). */
    val featureScopes: Map<String, Map<String, String>> = emptyMap(),
    /** Axon-style app icon row above notifications. */
    val notificationAppRow: Boolean = true,
    /** Drag along the Search pill or page dots to scrub pages. */
    val pageScrub: Boolean = true,
    /** Android wallpaper shifts slightly as pages change. */
    val wallpaperMotion: Boolean = true,
    /** Live Clock and Calendar icons. */
    val liveIcons: Boolean = true,
    /** Live Clock and Calendar look: AUTO (match the icons around them), LIGHT or DARK. */
    val liveIconLook: String = "AUTO",
    /** Optional tint per folder id (ARGB). */
    val folderColors: Map<String, Long> = emptyMap(),
    /** Icon Stacks: anchor app id → the apps that fan out when you swipe down on it. */
    val iconStacks: Map<String, List<String>> = emptyMap(),
    /** Custom app names by app id; apps without an entry keep the name Android reports. */
    val appNames: Map<String, String> = emptyMap(),
    /** Per-page looks by real Home page number (pages without an entry use Home's settings). */
    val pageStyles: Map<Int, PageStyle> = emptyMap(),
    val islandEverywhere: Boolean = false,
    /** The island steps aside for full-screen video and games (on), and for landscape apps (off). */
    val islandHideFullScreen: Boolean = true,
    val islandHideLandscape: Boolean = false,
    /** Home Screen & Dock › Layout › Rows: 0 = Automatic (More rows), 4 = always four app rows. */
    val homeRows: Int = 0,
    /** App rows that last fit the cover-class and the inner-class Home (0 = not measured yet); see [homeAppRows]. */
    val homeFitCompact: Int = 0,
    val homeFitExpanded: Int = 0,
    val loading: Boolean = true,
    val error: String? = null,
) {
    val order: List<String> get() = homeSlots.filterNotNull()
    val widgets: List<Int> get() = layout.widgets
    val layout: HomeLayout get() = HomeLayout(homeSlots, dock, widgetPlacements, folders, widgetRestores, leadingSlots, minPages)
    val homePages get() = layout.pageCount
    /** App rows every Home page shows, the same on both screens so pages don't change when you fold. */
    val homeAppRows: Int get() = effectiveHomeRows(homeRows, homeFitCompact, homeFitExpanded)
}

/**
 * Automatic: the fewest rows any of this device's screens has room for (screens not measured yet don't count, and
 * nothing measured means four). A fixed setting is used as is.
 */
fun effectiveHomeRows(setting: Int, fitCompact: Int, fitExpanded: Int): Int =
    (if (setting > 0) setting else listOf(fitCompact, fitExpanded).filter { it > 0 }.minOrNull() ?: BASE_APP_ROWS)
        .coerceIn(BASE_APP_ROWS, MAX_APP_ROWS)

/** Saved-state schema. 9: 36-cell Home pages (More rows); 6–8 had 24. */
const val STATE_SCHEMA = 9

/**
 * The schema that brought More rows. A save older than this was arranged in four rows, and keeps them; anything
 * from this schema on already had Automatic. Pinned to 9 rather than following [STATE_SCHEMA], or the next schema
 * bump would start treating 0.6.5 saves as old and take their Automatic rows away.
 */
const val MORE_ROWS_SCHEMA = 9

class LauncherModel(application: Application) : AndroidViewModel(application) {
    private data class RefreshedApps(val entries: List<AppEntry>, val profiles: List<AppProfile>,
        val authoritativeProfiles: Set<Long>, val removedProfiles: Set<Long>)
    private data class UndoImportSettings(val compact: LayoutPreset, val expanded: LayoutPreset, val labels: Boolean,
        val googleSearch: Boolean, val verticalStatus: Boolean)
    private val prefs = application.getSharedPreferences("launcher", 0)
    private val launcherApps = application.getSystemService(LauncherApps::class.java)
    private val userManager = application.getSystemService(UserManager::class.java)
    private val appCatalogPrefs = application.getSharedPreferences("app_catalog", 0)
    private val legacyRaw = prefs.getString("state", null)
    private val sourceSchema = runCatching { JSONObject(legacyRaw ?: "{}").optInt("schema", 1) }.getOrDefault(1)
    private var needsMigration = sourceSchema < 2
    private var statePayloadInvalid = false
    private val mutable = MutableStateFlow(load())
    val state = mutable.asStateFlow()
    private var undoLayout: Pair<HomeLayout, HomeLayout>? = null
    private var undoImportSettings: UndoImportSettings? = null
    private var refreshing = false
    private var refreshPending = false
    private val invalidatedPackages = mutableSetOf<Pair<Long, String>>()
    private val removedPackages = mutableSetOf<Pair<Long, String>>()
    private val unavailablePackages = mutableSetOf<Pair<Long, String>>()
    /** New downloads waiting for the refresh that brings their apps in (for "Add to Home Screen"). */
    private val addedPackages = mutableSetOf<String>()
    /** Shortcuts just accepted from a pin request, to put on Home once the refresh brings them in (package, shortcut id). */
    private val pendingShortcuts = mutableSetOf<Pair<String, String>>()

    /** After Android pins a shortcut for Folio, refresh and place it in the first free spot on Home. */
    fun placePinnedShortcut(packageName: String, shortcutId: String) {
        pendingShortcuts += packageName to shortcutId
        refresh(packageName)
    }

    /** Deletes a pinned shortcut (iOS "Delete Bookmark"): unpins it in Android, which removes it from Home and the App Library. */
    fun deleteShortcut(app: AppEntry) {
        val id = app.shortcutId ?: return
        runCatching {
            val remaining = launcherApps.getShortcuts(LauncherApps.ShortcutQuery().setPackage(app.packageName)
                .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED), app.user)?.map { it.id }.orEmpty() - id
            launcherApps.pinShortcuts(app.packageName, remaining, app.user)
        }
        setPinned(app.id, false)
        removedPackages += app.userSerial to app.packageName
        refresh(app.packageName, app.user)
    }
    // Accessed only in the serialized IO refresh. Returning Home reuses existing bitmaps.
    private val iconCache = mutableMapOf<String, AppEntry>()
    private var iconConfiguration = ""
    /** Set by [reloadIcons]; the refresh that follows drops every cached icon. */
    @Volatile private var iconsStale = false
    internal var completedRefreshes = 0
        private set
    private val callback = object : LauncherApps.Callback() {
        override fun onPackageAdded(packageName: String, user: UserHandle) {
            // A package Folio didn't know is a new download (not an update or a profile coming back).
            if (mutable.value.apps.none { it.packageName == packageName }) {
                NewApps.mark(getApplication(), packageName)
                addedPackages += packageName
            }
            refresh(packageName, user)
        }
        override fun onPackageRemoved(packageName: String, user: UserHandle) {
            removedPackages += userManager.getSerialNumberForUser(user) to packageName
            refresh(packageName, user)
        }
        override fun onPackageChanged(packageName: String, user: UserHandle) = refresh(packageName, user)
        // Apps update their dynamic shortcuts often; only pinned ones matter to Home.
        override fun onShortcutsChanged(packageName: String, shortcuts: MutableList<android.content.pm.ShortcutInfo>, user: UserHandle) {
            if (shortcuts.any { it.isPinned } || mutable.value.apps.any { it.isShortcut && it.packageName == packageName }) refresh(packageName, user)
        }
        override fun onPackagesAvailable(packages: Array<out String>, user: android.os.UserHandle, replacing: Boolean) {
            packages.forEach {
                val key = userManager.getSerialNumberForUser(user) to it
                invalidatedPackages += key; unavailablePackages -= key
            }; refresh()
        }
        override fun onPackagesUnavailable(packages: Array<out String>, user: android.os.UserHandle, replacing: Boolean) {
            // A package update temporarily hides activities; don't erase its pins or dock slot.
            packages.forEach {
                val key = userManager.getSerialNumberForUser(user) to it
                invalidatedPackages += key; unavailablePackages += key
            }; refresh()
        }
    }

    init { launcherApps.registerCallback(callback); refresh(); FolioSettingsBridge.liveModel = java.lang.ref.WeakReference(this) }

    /** Settings › Refresh Icons: for a theme app that changes icons without telling launchers (#19). */
    fun reloadIcons() { iconsStale = true; IconPacks.clear(); refresh() }

    fun refresh(invalidatedPackage: String? = null, user: UserHandle = Process.myUserHandle()) {
        invalidatedPackage?.let { invalidatedPackages += userManager.getSerialNumberForUser(user) to it }
        if (refreshing) { refreshPending = true; return }
        refreshing = true
        val invalidated = invalidatedPackages.toSet()
        val removed = removedPackages.toSet()
        val temporarilyUnavailable = unavailablePackages.toSet()
        invalidatedPackages.clear()
        removedPackages.clear()
        val resources = getApplication<Application>().resources
        val configuration = resources.configuration.let { "${it.densityDpi}|${it.locales.toLanguageTags()}|${it.uiMode}|${assetsSequence(it.toString())}" }
        viewModelScope.launch {
            try {
                val apps = withContext(Dispatchers.IO) {
                    if (configuration != iconConfiguration || iconsStale) { iconCache.clear(); iconConfiguration = configuration; iconsStale = false }
                    iconCache.keys.removeAll { key -> parseProfileAppId(key)?.let { identity ->
                        val serial = identity.userSerial ?: userManager.getSerialNumberForUser(Process.myUserHandle())
                        serial to (ComponentName.unflattenFromString(identity.component)?.packageName ?: "") in invalidated
                    } == true }
                    val collator = Collator.getInstance()
                    val application = getApplication<Application>()
                    val personal = Process.myUserHandle()
                    val personalSerial = userManager.getSerialNumberForUser(personal)
                    val associatedSerials = userManager.userProfiles.mapTo(mutableSetOf(), userManager::getSerialNumberForUser)
                    val handles = launcherApps.profiles
                        .filter { profile ->
                            val serial = userManager.getSerialNumberForUser(profile)
                            serial in associatedSerials && (serial == personalSerial || isSupportedWorkProfile(launcherApps, profile))
                        }
                        .distinctBy(userManager::getSerialNumberForUser)
                    val profiles = handles.map { profile ->
                        val serial = userManager.getSerialNumberForUser(profile)
                        val isPersonal = serial == personalSerial
                        val quiet = !isPersonal && runCatching { userManager.isQuietModeEnabled(profile) }.getOrDefault(false)
                        val unlocked = runCatching { userManager.isUserUnlocked(profile) }.getOrDefault(isPersonal)
                        AppProfile(serial, if (isPersonal) "Personal" else "Work", isPersonal, !isPersonal,
                            quiet, unlocked, !quiet && unlocked)
                    }
                    val cachedBeforeProfiles = loadCachedApps().filterNot { entry -> entry.userSerial to entry.packageName in removed }
                    val removedProfileSerials = removedAssociatedProfileSerials(
                        cachedBeforeProfiles.filter(AppEntry::isWork).mapTo(mutableSetOf(), AppEntry::userSerial), associatedSerials)
                    val cached = cachedBeforeProfiles.filterNot { it.isWork && it.userSerial in removedProfileSerials }
                    val authoritativeProfiles = mutableSetOf<Long>()
                    authoritativeProfiles += removedProfileSerials
                    val liveApps = handles.flatMap { profile ->
                        val serial = userManager.getSerialNumberForUser(profile)
                        val descriptor = profiles.first { it.userSerial == serial }
                        val activityList = if (descriptor.available) runCatching { launcherApps.getActivityList(null, profile) }.getOrNull() else null
                        if (activityList == null) emptyList() else activityList.also { authoritativeProfiles += serial }.mapNotNull { info ->
                            // Folio lists itself only as its Settings app, like iOS Settings in the App Library.
                            if (info.componentName.packageName == application.packageName && !info.componentName.className.startsWith("$FOLIO_CLASSES.${AppIconChoice.ALIAS_PREFIX}")) return@mapNotNull null
                            val component = info.componentName
                            // Every alternate icon is its own component; they share one id so switching icons keeps
                            // Folio's place on Home, in the dock and in folders.
                            val idComponent = if (component.packageName == application.packageName)
                                ComponentName(application.packageName, "$FOLIO_CLASSES.${AppIconChoice.TEAL.alias}") else component
                            val id = profileAppId(idComponent.flattenToString(), serial, personalSerial)
                            val label = info.label.toString()
                            iconCache[id]?.takeIf { it.label == label && it.available && it.component == component } ?: run {
                                val icon = runCatching { info.getBadgedIcon(0) }.getOrElse { application.packageManager.defaultActivityIcon }
                                AppEntry(id, label, launcherIcon(icon), component, profile, serial, descriptor.label,
                                    descriptor.isWork, available = true).also { iconCache[id] = it }
                            }
                        }
                    }
                    // Pinned shortcuts (e.g. Chrome's "Add to Home screen"). Only the default Home app may read them;
                    // if the query fails, the shortcuts Folio already knows stay put rather than vanish from Home.
                    val shortcuts = handles.flatMap { profile ->
                        val serial = userManager.getSerialNumberForUser(profile)
                        val descriptor = profiles.first { it.userSerial == serial }
                        if (!descriptor.available) return@flatMap emptyList()
                        val pinned = runCatching { launcherApps.getShortcuts(LauncherApps.ShortcutQuery()
                            .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED), profile) }.getOrNull()
                            ?: return@flatMap cached.filter { it.isShortcut && it.userSerial == serial }.map { it.copy(available = true) }
                        pinned.filter { it.isEnabled && it.`package` != application.packageName }.map { info ->
                            val component = ComponentName(info.`package`, SHORTCUT_CLASS_PREFIX + info.id)
                            val id = profileAppId(component.flattenToString(), serial, personalSerial)
                            val label = (info.shortLabel ?: info.longLabel ?: "Shortcut").toString()
                            iconCache[id]?.takeIf { it.label == label && it.available } ?: run {
                                val icon = runCatching { launcherApps.getShortcutBadgedIconDrawable(info, resources.displayMetrics.densityDpi) }.getOrNull()
                                    ?: application.packageManager.defaultActivityIcon
                                AppEntry(id, label, launcherIcon(icon), component, profile, serial, descriptor.label, descriptor.isWork, available = true)
                                    .also { iconCache[id] = it }
                            }
                        }
                    }
                    val live = liveApps + shortcuts
                    val liveIds = live.mapTo(mutableSetOf(), AppEntry::id)
                    val profileBySerial = profiles.associateBy(AppProfile::userSerial)
                    val unavailable = cached.filter { it.id !in liveIds }.mapNotNull { cachedEntry ->
                        val profile = profileBySerial[cachedEntry.userSerial]
                        val key = cachedEntry.userSerial to cachedEntry.packageName
                        // A successful profile query is authoritative except while Android explicitly
                        // reports a package unavailable (for example during an update).
                        if (cachedEntry.userSerial in authoritativeProfiles && key !in temporarilyUnavailable) null
                        else cachedEntry.copy(user = profile?.let { p -> handles.firstOrNull { userManager.getSerialNumberForUser(it) == p.userSerial } } ?: personal,
                            profileLabel = profile?.label ?: cachedEntry.profileLabel, available = false)
                    }
                    val entries = (live + unavailable).distinctBy(AppEntry::id)
                        .sortedWith { a, b -> collator.compare(a.label, b.label) }
                    saveCachedApps(entries)
                    // Spell Chinese names in pinyin here, off the main thread, so search and the A–Z list don't wait.
                    entries.forEach { Pinyin.syllables(it.label) }
                    iconCache.keys.retainAll(entries.map { it.id }.toSet())
                    RefreshedApps(entries, (profiles + unavailable.map { AppProfile(it.userSerial, it.profileLabel, false, true,
                        quiet = true, unlocked = false, available = false) }).distinctBy(AppProfile::userSerial),
                        authoritativeProfiles.toSet(), removedProfileSerials)
                }
                mutable.update { old ->
                    val entries = apps.entries.withAppNames(old.appNames)
                    val profiles = apps.profiles
                    val dock = if (!prefs.getBoolean("initialized", false)) initialDock(entries) else old.dock
                    val installed = entries.map { it.id }
                    val legacyPins = if (needsMigration) migrateHomePins(old.order, installed, suggestedPins(entries, dock))
                        else old.homeSlots
                    val pins = if (sourceSchema < 6 && needsMigration) migrateSchema5Apps(legacyPins) else legacyPins
                    val availableIds = entries.mapTo(mutableSetOf(), AppEntry::id)
                    val authoritative = apps.authoritativeProfiles
                    val removedIds = removedAppIds(old.homeSlots.filterNotNull() + old.leadingSlots.filterNotNull() +
                        old.dock.filterNotNull() + old.folders.flatMap { it.appIds } + old.iconStacks.keys + old.iconStacks.values.flatten() +
                        old.appNames.keys, availableIds,
                        authoritative, temporarilyUnavailable, removed, userManager.getSerialNumberForUser(Process.myUserHandle()),
                        apps.removedProfiles)
                    // iOS "Add to Home Screen": a newly downloaded app also goes to the first free spot on Home.
                    val oldIds = old.apps.mapTo(mutableSetOf(), AppEntry::id)
                    val arrivals = entries.filter { it.packageName in addedPackages && it.id !in oldIds && !it.isWork && !it.isShortcut }
                    val newShortcuts = entries.filter { app -> app.isShortcut && pendingShortcuts.any { it.first == app.packageName && it.second == app.shortcutId } }
                    pendingShortcuts.removeAll { p -> newShortcuts.any { it.packageName == p.first && it.shortcutId == p.second } }
                    addedPackages.removeAll(arrivals.map { it.packageName }.toSet())
                    val homeArrivals = (if (old.addNewAppsToHome) arrivals else emptyList()) + newShortcuts
                    // While a Focus hides Home pages, new arrivals go to a page that's showing, not one you can't see.
                    val hiddenCells = FocusPages.lockingFocus(old)?.pages?.let { shown ->
                        (0 until old.layout.pageCount).filter { it !in shown }.flatMap { page -> (0 until HOME_CELLS).map { homeCellIndex(page, it) } }
                    }.orEmpty()
                    val blockedForArrivals = (old.widgetPlacements.filter { it.page >= 0 }.flatMap { it.coveredIndices() } + hiddenCells).toSet()
                    val withArrivals = homeArrivals.fold(pins) { slots, app ->
                        if (app.id in old.dock || app.id in old.leadingSlots || old.folders.any { app.id in it.appIds }) slots
                        else pinHomeApp(slots, app.id, true, blockedForArrivals, old.homeAppRows)
                    }
                    val validPins = withArrivals.map { it?.takeUnless(removedIds::contains) }
                    val validDock = dock.map { it?.takeUnless(removedIds::contains) }
                    val reconciled = reconcileFolders(HomeLayout(validPins, validDock, old.widgetPlacements, old.folders,
                        old.widgetRestores, old.leadingSlots, old.minPages), removedIds)
                    old.copy(apps = entries, profiles = profiles, homeSlots = reconciled.slots, leadingSlots = reconciled.leadingSlots,
                        dock = reconciled.dock, folders = reconciled.folders,
                        iconStacks = IconStacks.prune(old.iconStacks, old.iconStacks.keys + old.iconStacks.values.flatten() - removedIds),
                        appNames = old.appNames - removedIds,
                        canUndoEdit = old.canUndoEdit && old.layout == reconciled, loading = false,
                        error = if (statePayloadInvalid) old.error else null)
                }
                if (needsMigration && legacyRaw != null && !prefs.contains("state_v1_backup"))
                    prefs.edit().putString("state_v1_backup", legacyRaw).apply()
                needsMigration = false
                persist()
                completedRefreshes++
            } catch (_: Exception) {
                mutable.update { it.copy(loading = false, error = getApplication<Application>().getString(R.string.apps_could_not_be_loaded_tap_to_retry)) }
            } finally {
                refreshing = false
                if (refreshPending) { refreshPending = false; refresh() }
            }
        }
    }

    private fun loadCachedApps(): List<AppEntry> = runCatching {
        val application = getApplication<Application>()
        val personal = Process.myUserHandle()
        val array = JSONArray(appCatalogPrefs.getString("apps", "[]"))
        List(array.length()) { index ->
            val item = array.getJSONObject(index)
            val id = item.getString("id")
            val identity = parseProfileAppId(id) ?: error("Invalid cached app identity")
            val component = ComponentName.unflattenFromString(identity.component) ?: error("Invalid cached component")
            val serial = item.getLong("serial").takeIf { it >= 0 } ?: error("Invalid cached profile")
            val user = userManager.getUserForSerialNumber(serial) ?: personal
            val isWork = item.optBoolean("work", identity.userSerial != null)
            val baseIcon = application.packageManager.defaultActivityIcon
            val icon = runCatching { application.packageManager.getUserBadgedIcon(baseIcon, user) }.getOrDefault(baseIcon)
            AppEntry(id, item.getString("label"), launcherIcon(icon), component, user, serial,
                item.optString("profile", if (isWork) "Work" else "Personal"), isWork, available = false)
        }
    }.getOrDefault(emptyList())

    private fun saveCachedApps(apps: List<AppEntry>) {
        val array = JSONArray().also { result -> apps.forEach { app -> result.put(JSONObject()
            .put("id", app.id).put("label", app.label).put("serial", app.userSerial)
            .put("profile", app.profileLabel).put("work", app.isWork)) } }
        appCatalogPrefs.edit().putString("apps", array.toString()).apply()
    }

    private fun initialDock(apps: List<AppEntry>): List<String?> {
        val packages = listOf(
            listOf("com.samsung.android.dialer", "com.google.android.dialer"),
            listOf("com.android.chrome", "com.sec.android.app.sbrowser"),
            listOf("com.google.android.apps.messaging", "com.samsung.android.messaging"),
            listOf("com.spotify.music", "com.google.android.apps.youtube.music"),
        )
        return packages.map { choices -> choices.firstNotNullOfOrNull { pkg -> apps.firstOrNull { !it.isWork && it.packageName == pkg }?.id } }
    }

    private fun suggestedPins(apps: List<AppEntry>, dock: List<String?>): List<String> {
        val groups = listOf(
            listOf("com.samsung.android.calendar", "com.google.android.calendar"),
            listOf("com.sec.android.app.camera", "com.android.camera2", "com.google.android.GoogleCamera"),
            listOf("com.sec.android.gallery3d", "com.google.android.apps.photos"),
            listOf("com.sec.android.app.clockpackage", "com.google.android.deskclock"),
            listOf("com.google.android.gm", "com.samsung.android.email.provider"),
            listOf("com.google.android.apps.maps"),
            listOf("com.samsung.android.app.notes", "com.google.android.keep"),
            listOf("com.sec.android.app.myfiles", "com.google.android.documentsui"),
            listOf("com.android.settings"),
            listOf("com.sec.android.app.popupcalculator", "com.google.android.calculator"),
            listOf("com.android.vending"), listOf("com.google.android.youtube"),
            listOf("com.samsung.android.app.contacts", "com.google.android.contacts"),
            listOf("com.google.android.apps.docs"), listOf("com.google.android.apps.walletnfcrel"),
            listOf("com.sec.android.app.shealth"),
        )
        return groups.mapNotNull { choices -> choices.firstNotNullOfOrNull { pkg ->
            apps.firstOrNull { !it.isWork && it.packageName == pkg && it.id !in dock }?.id
        } }.distinct()
    }

    fun setPinned(id: String, pinned: Boolean) {
        if (statePayloadInvalid) return
        if (mutable.value.folders.any { id in it.appIds }) return
        mutable.update { old ->
            val enable = pinned && old.apps.any { it.id == id }
            old.copy(homeSlots = if (enable && id in old.leadingSlots) old.homeSlots else pinHomeApp(old.homeSlots, id,
                enable, old.widgetPlacements.flatMapTo(mutableSetOf()) { it.coveredIndices() }.filterTo(mutableSetOf()) { it >= 0 }, old.homeAppRows),
                leadingSlots = if (enable) old.leadingSlots else old.leadingSlots.map { it?.takeUnless(id::equals) },
                canUndoEdit = false)
        }
        persist()
    }

    fun turnOnWork(userSerial: Long): Boolean = runCatching {
        val user = userManager.getUserForSerialNumber(userSerial) ?: return false
        if (userManager.getSerialNumberForUser(Process.myUserHandle()) == userSerial || user !in launcherApps.profiles) return false
        userManager.requestQuietModeEnabled(false, user).also { refresh() }
    }.getOrDefault(false)

    fun setDock(slot: Int, id: String?) {
        if (statePayloadInvalid) return
        if (slot !in mutable.value.dock.indices) return
        if (id != null && (isReservedFolderId(id) || mutable.value.folders.any { id in it.appIds })) return
        mutable.update { old -> old.copy(canUndoEdit = false,
            homeSlots = if (id == null) old.homeSlots else old.homeSlots.map { it?.takeUnless(id::equals) }.dropLastWhile { it == null },
            leadingSlots = if (id == null) old.leadingSlots else old.leadingSlots.map { it?.takeUnless(id::equals) },
            dock = old.dock.mapIndexed { index, value ->
                when { index == slot -> id; value == id && id != null -> null; else -> value }
            }) }
        persist()
    }

    fun move(id: String, offset: Int) {
        val old = mutable.value
        val from = old.layout.indexOfShortcut(id) ?: return
        val page = homeCellPage(from)
        // Steps over cells in rows Home doesn't show.
        var target = from + offset
        val step = if (offset < 0) -1 else 1
        val range = if (page == -1) homeCellIndex(-1, 0)..homeCellIndex(-1, HOME_CELLS - 1) else 0..maxOf(old.homeSlots.lastIndex, from)
        while (target in range && target != from && !homeCellShown(target, old.homeAppRows)) target += step
        applyDrop(id, DropTarget.Home(target.coerceIn(range)))
    }

    fun applyDrop(id: String, target: DropTarget): Boolean {
        val old = mutable.value
        val folder = old.layout.folder(id)
        if (old.apps.none { it.id == id } && folder == null) return false
        if (target is DropTarget.Folder) return folder == null && addAppToFolder(target.id, id)
        if (folder != null && target !is DropTarget.Home) return false
        return commitLayout(dropApp(old.layout, id, target, old.homeAppRows))
    }

    fun createFolder(firstAppId: String, secondAppId: String, targetIndex: Int, title: String = "Folder"): String? {
        val installed = mutable.value.apps.mapTo(mutableSetOf(), AppEntry::id)
        if (firstAppId !in installed || secondAppId !in installed) return null
        val id = newFolderId()
        return id.takeIf { commitLayout(com.mccal.folio.createFolder(mutable.value.layout, FolderEntry(id, title, emptyList()),
            firstAppId, secondAppId, targetIndex)) }
    }
    fun renameFolder(folderId: String, title: String) = commitLayout(com.mccal.folio.renameFolder(mutable.value.layout, folderId, title))
    fun addAppToFolder(folderId: String, appId: String, index: Int? = null): Boolean {
        if (mutable.value.apps.none { it.id == appId }) return false
        return commitLayout(com.mccal.folio.addAppToFolder(mutable.value.layout, folderId, appId, index))
    }
    fun removeAppFromFolder(folderId: String, appId: String, target: DropTarget) =
        commitLayout(com.mccal.folio.removeAppFromFolder(mutable.value.layout, folderId, appId, target, mutable.value.homeAppRows))
    fun moveFolderApp(folderId: String, appId: String, index: Int) =
        commitLayout(com.mccal.folio.moveFolderApp(mutable.value.layout, folderId, appId, index))
    fun folder(id: String) = mutable.value.layout.folder(id)

    /**
     * [before] is the Home the user had when they chose to restore. It differs from the current state when the Market
     * has already taken this phone's packages off, and it is what Layout History and Undo should give back.
     */
    fun applyImportedLayout(preview: LayoutImportPreview, before: LauncherState? = null): Boolean {
        if (statePayloadInvalid) return false
        val old = mutable.value
        val kept = before ?: old
        if (old.layout == preview.layout && old.compact == preview.compact && old.expanded == preview.expanded &&
            old.labels == preview.labels && old.googleSearch == preview.googleSearch && old.verticalStatus == preview.verticalStatus &&
            old.appNames + preview.appNames == old.appNames) return false
        if (old.layoutHistory && !old.loading) LayoutHistory.add(getApplication(), "Before restoring a backup", kept.layout)
        undoLayout = kept.layout to preview.layout
        undoImportSettings = UndoImportSettings(kept.compact, kept.expanded, kept.labels, kept.googleSearch, kept.verticalStatus)
        // A restored name replaces the one on this phone; names this backup says nothing about are left alone.
        val names = old.appNames + preview.appNames
        mutable.value = old.copy(appNames = names, apps = old.apps.withAppNames(names),
            homeSlots = preview.layout.slots, leadingSlots = preview.layout.leadingSlots, dock = preview.layout.dock,
            widgetPlacements = preview.layout.widgetPlacements, folders = preview.layout.folders,
            widgetRestores = preview.layout.widgetRestores, compact = preview.compact, expanded = preview.expanded,
            widgetStacks = WidgetStacks.prune(old.widgetStacks, old.widgetPlacements.map { it.slot }.toSet()),
            labels = preview.labels, googleSearch = preview.googleSearch, verticalStatus = preview.verticalStatus,
            editRevision = old.editRevision + 1, canUndoEdit = true)
        persist()
        return true
    }

    fun moveWidget(from: Int, to: Int): Boolean {
        val target = mutable.value.layout.placement(to) ?: return false
        return moveWidgetTo(from, target.page * HOME_CELLS + target.row * GRID_COLUMNS + target.column)
    }
    fun moveWidgetTo(slot: Int, index: Int) = commitLayout(moveWidget(mutable.value.layout, slot, index))
    fun resizeWidget(slot: Int, spanX: Int, spanY: Int) = commitLayout(resizeWidget(mutable.value.layout, slot, spanX, spanY))
    fun placeWidget(placement: WidgetPlacement) = commitLayout(placeWidget(mutable.value.layout, placement))
    fun placement(slot: Int) = mutable.value.layout.placement(slot)
    // Never reuse a slot number that a (possibly undoable) stack still refers to.
    fun nextWidgetSlot() = maxOf(mutable.value.widgetPlacements.maxOfOrNull { it.slot } ?: -1,
        mutable.value.widgetStacks.keys.maxOrNull() ?: -1) + 1

    fun stackCards(slot: Int): List<Int> = mutable.value.layout.placement(slot)
        ?.let { WidgetStacks.cards(it.id, mutable.value.widgetStacks[slot]) }.orEmpty()

    /** Adds a bound (or built-in) widget behind the widget at [slot]. */
    fun addToStack(slot: Int, id: Int): Boolean {
        if (statePayloadInvalid) return false
        val placement = mutable.value.layout.placement(slot) ?: return false
        mutable.update { it.copy(widgetStacks = it.widgetStacks + (slot to WidgetStacks.add(it.widgetStacks[slot], placement.id, id))) }
        persist()
        return true
    }

    fun removeFromStack(slot: Int, id: Int) = rearrangeStack(slot) { primary, extras -> WidgetStacks.remove(primary, extras, id) }
    fun showFirstInStack(slot: Int, id: Int) = rearrangeStack(slot) { primary, extras -> WidgetStacks.showFirst(primary, extras, id) }

    private fun rearrangeStack(slot: Int, change: (Int, List<Int>) -> Pair<Int, List<Int>>?): Boolean {
        if (statePayloadInvalid) return false
        val old = mutable.value
        val placement = old.layout.placement(slot) ?: return false
        val (primary, extras) = change(placement.id, old.widgetStacks[slot].orEmpty()) ?: return false
        // Changing which widget a placement shows isn't undoable (like replacing a widget); clearing undo keeps
        // retained ids and the stack map consistent.
        undoLayout = null
        undoImportSettings = null
        mutable.value = old.copy(
            widgetPlacements = old.widgetPlacements.map { if (it.slot == slot) it.copy(id = primary) else it },
            widgetStacks = if (extras.isEmpty()) old.widgetStacks - slot else old.widgetStacks + (slot to extras),
            canUndoEdit = false, editRevision = old.editRevision + 1)
        persist()
        return true
    }

    fun setStackRotate(value: Boolean) = updateSettings(soon = false) { it.copy(stackRotate = value) }
    fun setRailActivities(value: Boolean) = updateSettings(soon = false) { it.copy(railActivities = value) }
    /** The look before the last theme was applied, so Undo can put it back. */
    var themeUndo: FolioTheme? = null
        private set
    fun applyTheme(theme: FolioTheme) {
        themeUndo = FolioTheme.of(mutable.value, "Previous")
        val packs = IconPacks.installed(getApplication()).mapTo(mutableSetOf()) { it.packageName }
        updateSettings(soon = false) { it.withTheme(theme, packs) }
    }
    fun undoTheme() { themeUndo?.let { previous -> themeUndo = null; val packs = IconPacks.installed(getApplication()).mapTo(mutableSetOf()) { it.packageName }
        updateSettings(soon = false) { it.withTheme(previous, packs) } } }
    fun setPageStyle(page: Int, style: PageStyle) = updateSettings(soon = false) {
        it.copy(pageStyles = if (style.isDefault) it.pageStyles - page else it.pageStyles + (page to style))
    }
    fun toggleStackApp(anchor: String, app: String) = updateSettings(soon = false) { it.copy(iconStacks = IconStacks.toggle(it.iconStacks, anchor, app)) }
    fun setAddNewAppsToHome(value: Boolean) = updateSettings(soon = false) { it.copy(addNewAppsToHome = value) }
    fun setLayoutHistory(value: Boolean) = updateSettings(soon = false) { it.copy(layoutHistory = value) }
    fun setDockRecentDots(value: Boolean) = updateSettings(soon = false) { it.copy(dockRecentDots = value) }

    /** Saves the current Home to Layout History when it's on (or always, when [force] is set). */
    fun saveLayoutSnapshot(reason: String, force: Boolean = false) {
        val s = mutable.value
        if ((s.layoutHistory || force) && !s.loading && !statePayloadInvalid) LayoutHistory.add(getApplication(), reason, s.layout)
    }

    /**
     * Restores a Layout History snapshot: the current Home is saved first, apps that are no longer installed and
     * widgets Android no longer has are left out, and Undo is offered like any other layout change.
     */
    fun restoreLayoutSnapshot(snapshot: LayoutSnapshot): Boolean {
        if (statePayloadInvalid || mutable.value.loading || FocusPages.lockingFocus(mutable.value) != null) return false
        saveLayoutSnapshot("Before restoring", force = true)
        val old = mutable.value
        val before = snapshot.layout
        val manager = android.appwidget.AppWidgetManager.getInstance(getApplication())
        val widgets = before.widgetPlacements.filter { it.id < 0 || runCatching { manager.getAppWidgetInfo(it.id) != null }.getOrDefault(false) }
        // Same rules as a refresh: only apps from profiles Android can see right now count as removed (a paused work
        // profile's apps stay), and folders are dissolved properly, so a restored layout always loads again.
        val savedIds = before.slots.filterNotNull() + before.leadingSlots.filterNotNull() + before.dock.filterNotNull() + before.folders.flatMap { it.appIds }
        val removed = removedAppIds(savedIds, old.apps.mapTo(mutableSetOf(), AppEntry::id),
            old.profiles.filter { it.available }.mapTo(mutableSetOf(), AppProfile::userSerial), emptySet(), emptySet(),
            userManager.getSerialNumberForUser(Process.myUserHandle()))
        val next = reconcileFolders(before.copy(widgetPlacements = widgets), removed)
        if (old.layout == next) return false
        undoLayout = old.layout to next
        undoImportSettings = null
        mutable.value = old.copy(homeSlots = next.slots, leadingSlots = next.leadingSlots, dock = next.dock,
            widgetPlacements = next.widgetPlacements, folders = next.folders, widgetRestores = next.widgetRestores, minPages = next.minPages,
            // Only stacks of the restored widgets: a snapshot widget landing in a reused slot mustn't inherit another stack.
            widgetStacks = WidgetStacks.prune(old.widgetStacks, next.widgetPlacements.map { it.slot }.toSet()),
            editRevision = old.editRevision + 1, canUndoEdit = true)
        persist()
        return true
    }
    /** Turns a Focus on (or all off with null) and applies it to Android. */
    fun setFocus(id: String?) {
        updateSettings(soon = false) { it.copy(activeFocus = id?.takeIf { f -> it.focusModes.any { m -> m.id == f } }) }
        val state = mutable.value
        FocusController.apply(getApplication(), state.focusModes, state.focusModes.firstOrNull { it.id == state.activeFocus })
    }
    fun updateFocusMode(mode: FocusMode) {
        updateSettings(soon = false) { it.copy(focusModes = FocusModes.update(it.focusModes, mode)) }
        FocusScheduler.schedule(getApplication(), mutable.value.focusModes)
        if (mutable.value.activeFocus == mode.id) FocusController.apply(getApplication(), mutable.value.focusModes, mode)
    }
    /** Clears the Focus if its Do Not Disturb rule was turned off in Android (Quick Settings, a schedule…). */
    fun syncFocus() {
        val state = mutable.value
        val active = state.focusModes.firstOrNull { it.id == state.activeFocus } ?: return
        if (FocusController.isOnInAndroid(getApplication(), active) == false) updateSettings(soon = false) { it.copy(activeFocus = null) }
    }
    fun setLeftPage(value: String) = updateSettings(soon = false) { it.copy(leftPage = value) }
    fun setTodayUnfolded(value: String) = updateSettings(soon = false) { it.copy(todayUnfolded = value) }
    fun setSystemWallpaper(value: Boolean) = updateSettings(soon = false) { it.copy(systemWallpaper = value) }
    fun setHomeInk(value: String) = updateSettings(soon = false) { it.copy(homeInk = value) }
    fun setTintedGlass(value: Boolean) = updateSettings(soon = false) { it.copy(tintedGlass = value) }
    /** One slider for both: all the way to Clear turns tinting off. */
    fun setGlassTint(value: Float) = updateSettings(soon = true) { it.copy(tintedGlass = value > .01f, glassTint = if (value > .01f) value.coerceIn(0f, 1f) else it.glassTint) }
    fun setReduceTransparency(value: Boolean) = updateSettings(soon = false) { it.copy(reduceTransparency = value) }
    fun setRoundedCorners(value: Boolean) = updateSettings(soon = false) { it.copy(roundedCorners = value) }
    fun setCornerRadius(value: Float) = updateSettings(soon = true) { it.copy(cornerRadius = value.coerceIn(16f, 72f)) }
    fun setWidgetGlass(value: Float) = updateSettings(soon = true) { it.copy(widgetGlass = value.coerceIn(0f, 1f)) }
    fun setFolderColumns(value: Int) = updateSettings(soon = false) { it.copy(folderColumns = value.takeIf { v -> v in setOf(0, 3, 4) } ?: 0) }
    fun setFolderBackground(value: FolderBackground) = updateSettings(soon = false) { it.copy(folderBackground = value) }
    fun setLabelSize(value: LabelSize) = updateSettings(soon = false) { it.copy(labelSize = value) }
    fun setMotionSpeed(value: MotionSpeed) = updateSettings(soon = false) { it.copy(motionSpeed = value) }
    fun setPageEffect(value: PageEffect) = updateSettings(soon = false) { it.copy(pageEffect = value) }
    fun setGlassOutline(value: Float) = updateSettings(soon = true) { it.copy(glassOutline = value.coerceIn(0f, 1f)) }
    /** One tap for the whole glass look: widgets and Side Bar together. */
    fun setGlassPreset(frost: Float) = updateSettings(soon = false) {
        it.copy(widgetGlass = frost, statusStyle = it.statusStyle.copy(railGlass = frost)) }
    fun setDimWallpaperDark(value: Boolean) = updateSettings(soon = false) { it.copy(dimWallpaperDark = value) }
    fun setHomeScrim(value: Boolean) = updateSettings(soon = false) { it.copy(homeScrim = value) }
    fun setIconTintFromWallpaper(value: Boolean) = updateSettings(soon = false) { it.copy(iconTintFromWallpaper = value) }
    fun setTintNotifications(value: Boolean) = updateSettings(soon = false) { it.copy(tintNotifications = value) }
    fun setTintMedia(value: Boolean) = updateSettings(soon = false) { it.copy(tintMedia = value) }
    fun setDockMagnify(value: Boolean) = updateSettings(soon = false) { it.copy(dockMagnify = value) }
    fun setAppPanels(value: Boolean) = updateSettings(soon = false) { it.copy(appPanels = value) }
    fun setHaptics(value: Boolean) = updateSettings(soon = false) { it.copy(haptics = value) }
    fun setLockCover(value: Boolean) = updateSettings(soon = false) { it.copy(lockCover = value) }
    fun setFeatureScope(id: String, screen: FolioScreen, value: ScopeValue) =
        updateSettings(soon = false) { it.copy(featureScopes = FeatureScopes.set(it.featureScopes, id, screen, value)) }
    /** Tweak Library "Get": adds the tweak to Settings and turns it on. */
    internal fun installTweak(feature: TweakFeature) {
        updateSettings(soon = false) { it.copy(installedTweaks = it.installedTweaks + feature.id) }
        feature.set(this, true)
    }

    /** "Remove": turns the tweak off, clears its per-screen settings and takes it out of Settings. */
    internal fun removeTweak(feature: TweakFeature) {
        feature.set(this, false)
        updateSettings(soon = false) { it.copy(installedTweaks = it.installedTweaks - feature.id, featureScopes = it.featureScopes - feature.id) }
    }

    internal fun resetTweak(feature: TweakFeature) {
        feature.set(this, feature.default)
        updateSettings(soon = false) { it.copy(featureScopes = it.featureScopes - feature.id) }
    }
    fun setNotificationAppRow(value: Boolean) = updateSettings(soon = false) { it.copy(notificationAppRow = value) }
    fun setPageScrub(value: Boolean) = updateSettings(soon = false) { it.copy(pageScrub = value) }
    fun setWallpaperMotion(value: Boolean) = updateSettings(soon = false) { it.copy(wallpaperMotion = value) }
    fun setLiveIcons(value: Boolean) = updateSettings(soon = false) { it.copy(liveIcons = value) }
    fun setLiveIconLook(value: String) = updateSettings(soon = false) { it.copy(liveIcons = true, liveIconLook = value) }
    fun setTriggerAction(trigger: FolioTrigger, action: FolioAction) = updateSettings(soon = false) {
        it.copy(triggerActions = if (action == FolioAction.NONE) it.triggerActions - trigger.name else it.triggerActions + (trigger.name to action.name))
    }
    fun addTodayWidget(id: Int, size: TodaySize): Boolean {
        if (statePayloadInvalid) return false
        updateSettings(soon = false) { it.copy(todayWidgets = TodayWidgets.add(it.todayWidgets, TodayWidget(id, size))) }
        return true
    }
    fun removeTodayWidget(id: Int) = updateSettings(soon = false) { it.copy(todayWidgets = TodayWidgets.remove(it.todayWidgets, id)) }
    fun moveTodayWidget(id: Int, delta: Int) = updateSettings(soon = false) { it.copy(todayWidgets = TodayWidgets.move(it.todayWidgets, id, delta)) }
    fun removePlacement(source: DropTarget) = commitLayout(removePlacement(mutable.value.layout, source))
    /** Arranges Home's first page and dock like iPhone's with the matching installed apps (undoable). */
    fun arrangeLikeIPhone(): Boolean {
        saveLayoutSnapshot("Before Arrange Like iPhone")
        val state = mutable.value
        return commitLayout(arrangeLikeIPhone(state.layout, resolveIPhoneApps(getApplication(), state.apps, state.messagesApp), state.homeAppRows))
    }
    private fun commitLayout(next: HomeLayout): Boolean {
        if (statePayloadInvalid) return false
        // A Focus hiding Home pages locks editing: Home shows a filtered copy, so its positions aren't the real ones.
        if (FocusPages.lockingFocus(mutable.value) != null) return false
        val old = mutable.value
        if (old.layout == next) return false
        undoLayout = old.layout to next
        undoImportSettings = null
        mutable.value = old.copy(homeSlots = next.slots, leadingSlots = next.leadingSlots, dock = next.dock,
            widgetPlacements = next.widgetPlacements, folders = next.folders,
            widgetRestores = next.widgetRestores,
            // Keep stacks of the undoable previous layout too, so undoing a removal brings the whole stack back.
            widgetStacks = WidgetStacks.prune(old.widgetStacks, (next.widgetPlacements + old.widgetPlacements).map { it.slot }.toSet()),
            editRevision = old.editRevision + 1, canUndoEdit = true)
        persist()
        return true
    }

    fun undoEdit(): Boolean {
        val (before, after) = undoLayout ?: return false
        val old = mutable.value
        if (!old.canUndoEdit || old.layout != after) return false
        val installed = (old.apps.map { it.id } + before.folders.map { it.id }).toSet()
        val settings = undoImportSettings
        mutable.value = old.copy(homeSlots = reconcileHomeSlots(before.slots, installed),
            leadingSlots = before.leadingSlots.map { it?.takeIf(installed::contains) },
            dock = before.dock.map { it?.takeIf(installed::contains) }, widgetPlacements = before.widgetPlacements, folders = before.folders,
            widgetStacks = WidgetStacks.prune(old.widgetStacks, before.widgetPlacements.map { it.slot }.toSet()),
            widgetRestores = before.widgetRestores, compact = settings?.compact ?: old.compact,
            expanded = settings?.expanded ?: old.expanded, labels = settings?.labels ?: old.labels,
            googleSearch = settings?.googleSearch ?: old.googleSearch, verticalStatus = settings?.verticalStatus ?: old.verticalStatus,
            canUndoEdit = false,
            editRevision = old.editRevision + 1)
        undoLayout = null
        undoImportSettings = null
        persist()
        return true
    }
    fun setLabels(value: Boolean) { if (statePayloadInvalid) return; undoLayout = null; undoImportSettings = null; mutable.update { it.copy(labels = value, canUndoEdit = false) }; persist() }
    /** Adds an empty Home page after the last one; returns its index. */
    fun addPage(): Int { if (statePayloadInvalid || FocusPages.lockingFocus(state.value) != null) return -1; val next = state.value.homePages
        undoLayout = null; mutable.update { it.copy(minPages = (next + 1).coerceAtMost(20), canUndoEdit = false) }; persist(); return next }
    /** Removes the last page when it has no apps or widgets. */
    fun removeLastEmptyPage(): Boolean { if (statePayloadInvalid || FocusPages.lockingFocus(state.value) != null) return false; val s = state.value
        if (s.homePages <= 1 || s.layout.contentPageCount >= s.homePages) return false
        undoLayout = null; mutable.update { it.copy(minPages = maxOf(1, it.homePages - 1), canUndoEdit = false) }; persist(); return true }
    fun setSpotlightSection(section: String, visible: Boolean) = updateSettings(soon = false) {
        it.copy(spotlightHidden = if (visible) it.spotlightHidden - section else it.spotlightHidden + section) }
    fun setSearchEngine(engine: String) = updateSettings(soon = false) { it.copy(searchEngine = engine) }
    fun setIslandEvent(kind: String, enabled: Boolean) = updateSettings(soon = false) {
        it.copy(islandEventsOff = if (enabled) it.islandEventsOff - kind else it.islandEventsOff + kind) }
    fun setLibraryCategories(value: Boolean) = updateSettings(soon = false) { it.copy(libraryCategories = value) }
    fun setLibraryWork(value: Boolean) = updateSettings(soon = false) { it.copy(libraryWork = value) }
    fun setFoldSnapshot(value: Boolean) = updateSettings(soon = false) { it.copy(foldSnapshot = value) }
    fun setStandBy(value: Boolean) = updateSettings(soon = false) { it.copy(standBy = value) }
    fun setIconStyle(style: IconStyle, tint: Long) = updateSettings(soon = false) { it.copy(iconStyle = style, iconTint = tint) }
    fun setIconShape(shape: IconShape) = updateSettings(soon = false) { it.copy(iconShape = shape) }
    fun setIconPack(pack: String?) = updateSettings(soon = false) { it.copy(iconPack = pack) }
    fun setBadgeStyle(style: BadgeStyle) = updateSettings(soon = false) { it.copy(badgeStyle = style) }
    fun setBadgeColor(color: BadgeColor) = updateSettings(soon = false) { it.copy(badgeColor = color) }
    fun setBadgeLook(look: BadgeLook) = updateSettings(soon = false) { it.copy(badgeLook = look) }
    fun setBadgeSize(size: BadgeSize) = updateSettings(soon = false) { it.copy(badgeSize = size) }
    /** Clear Badges When Opened; turning it off forgets the counts it remembered, so every badge comes back. */
    fun setBadgesWhenOpened(value: Boolean) = updateSettings(soon = false) {
        it.copy(badgesWhenOpened = value, badgesSeen = if (value) it.badgesSeen else emptyMap()) }
    /** An app was opened: the [count] showing on it now is seen, so its badge stays away until a new notification. */
    fun noteBadgeSeen(packageName: String, count: Int) = updateSettings(soon = true) {
        it.copy(badgesSeen = if (count <= 0) it.badgesSeen - packageName else it.badgesSeen + (packageName to count)) }
    /** Keeps the seen counts in step with the notifications showing now (see [BadgesWhenOpened.trimmed]). */
    fun trimBadgesSeen(counts: Map<String, Int>) {
        val seen = mutable.value.badgesSeen
        if (seen.isEmpty()) return
        val trimmed = BadgesWhenOpened.trimmed(counts, seen)
        if (trimmed != seen) updateSettings(soon = true) { it.copy(badgesSeen = trimmed) }
    }
    fun setSearchPill(value: Boolean) = updateSettings(soon = false) { it.copy(searchPill = value) }
    fun setSwipeDownHome(value: String) = updateSettings(soon = false) { it.copy(swipeDownHome = value) }
    fun setMessagesApp(pkg: String?) = updateSettings(soon = false) { it.copy(messagesApp = pkg) }
    fun setMessagesAvoidDouble(value: Boolean) = updateSettings(soon = false) { it.copy(messagesAvoidDouble = value) }
    fun setIslandAlerts(value: Boolean) = updateSettings(soon = false) { it.copy(islandAlerts = value) }
    fun setIslandAlertApp(packageName: String, enabled: Boolean) = updateSettings(soon = false) {
        it.copy(islandAlertAppsOff = if (enabled) it.islandAlertAppsOff - packageName else it.islandAlertAppsOff + packageName) }
    fun setCcControls(controls: List<String>) = updateSettings(soon = false) { it.copy(ccControls = controls.distinct()) }
    fun setCcSize(size: PanelSize) = updateSettings(soon = false) { it.copy(ccSize = size) }
    fun setCcCentered(value: Boolean) = updateSettings(soon = false) { it.copy(ccCentered = value) }
    fun setNcSplit(value: Boolean) = updateSettings(soon = false) { it.copy(ncSplit = value) }
    fun setFolderColor(folderId: String, color: Long?) = updateSettings(soon = false) {
        it.copy(folderColors = if (color == null) it.folderColors - folderId else it.folderColors + (folderId to color)) }
    fun setButtonBar(value: Boolean) = updateSettings(soon = false) { it.copy(buttonBar = value) }
    fun setButtonBarHeight(value: Float) = updateSettings(soon = false) { it.copy(buttonBarHeight = value.coerceIn(44f, 60f)) }
    fun setButtonBarWidth(value: Float) = updateSettings(soon = false) { it.copy(buttonBarWidth = value.coerceIn(.3f, .8f)) }
    fun setButtonBarAndroidOrder(value: Boolean) = updateSettings(soon = false) { it.copy(buttonBarAndroidOrder = value) }
    fun setButtonBarLight(value: Boolean) = updateSettings(soon = false) { it.copy(buttonBarLight = value) }
    fun setButtonBarFade(value: Boolean) = updateSettings(soon = false) { it.copy(buttonBarFade = value) }

    fun setDockEverywhere(value: Boolean) = updateSettings(soon = false) { it.copy(dockEverywhere = value) }
    fun setIslandEverywhere(value: Boolean) = updateSettings(soon = false) { it.copy(islandEverywhere = value) }
    fun setIslandHideFullScreen(value: Boolean) = updateSettings(soon = false) { it.copy(islandHideFullScreen = value) }
    fun setIslandHideLandscape(value: Boolean) = updateSettings(soon = false) { it.copy(islandHideLandscape = value) }
    fun setHomeRows(value: Int) = updateSettings(soon = false) { it.copy(homeRows = if (value == BASE_APP_ROWS) value else 0) }
    /** Remembers how many app rows fit this screen class's full-screen Home (see [effectiveHomeRows]). */
    fun recordHomeFit(expanded: Boolean, rows: Int) {
        val value = rows.coerceIn(BASE_APP_ROWS, MAX_APP_ROWS)
        val old = mutable.value
        if ((if (expanded) old.homeFitExpanded else old.homeFitCompact) == value) return
        updateSettings(soon = false) { if (expanded) it.copy(homeFitExpanded = value) else it.copy(homeFitCompact = value) }
    }
    fun setPanelBlur(value: Float) = updateSettings(soon = true) { it.copy(panelBlur = value) }
    fun setNotificationClock(value: Boolean) = updateSettings(soon = false) { it.copy(notificationClock = value) }
    fun setGroupNotifications(value: Boolean) = updateSettings(soon = false) { it.copy(groupNotifications = value) }
    fun setFoldEffect(value: Boolean) = updateSettings(soon = false) { it.copy(foldEffect = value) }
    fun setFoldIntensity(value: Float) = updateSettings(soon = true) { it.copy(foldIntensity = value) }
    fun setStayAwakeOnFold(value: Boolean) = updateSettings(soon = false) { it.copy(stayAwakeOnFold = value) }
    fun setStatusStyle(style: StatusStyle) = updateSettings(soon = true) { it.copy(statusStyle = style) }
    fun setFolioPanels(value: Boolean) = updateSettings(soon = false) { it.copy(folioPanels = value) }
    fun setIsland(value: Boolean) = updateSettings(soon = false) { it.copy(island = value) }
    fun setHidden(id: String, hidden: Boolean) = updateSettings(soon = false) { it.copy(hiddenApps = if (hidden) it.hiddenApps + id else it.hiddenApps - id) }
    /** Renames one app everywhere it appears; a blank name puts the name Android reports back. */
    fun renameApp(id: String, name: String) = updateSettings(soon = false) { s ->
        val names = editAppName(s.appNames, id, name)
        s.copy(appNames = names, apps = s.apps.withAppNames(names))
    }
    fun setLeftHanded(value: Boolean) = updateSettings(soon = false) { it.copy(leftHanded = value) }
    fun setVerticalStatus(value: Boolean) { if (statePayloadInvalid) return; undoLayout = null; undoImportSettings = null; mutable.update { it.copy(verticalStatus = value, canUndoEdit = false) }; persist() }
    fun setGoogleSearch(value: Boolean) { if (statePayloadInvalid) return; undoLayout = null; undoImportSettings = null; mutable.update { it.copy(googleSearch = value, canUndoEdit = false) }; persist() }
    fun setPreset(expanded: Boolean, value: LayoutPreset) {
        if (statePayloadInvalid) return
        undoLayout = null; undoImportSettings = null
        mutable.update { if (expanded) it.copy(expanded = value.sanitized(), canUndoEdit = false) else it.copy(compact = value.sanitized(), canUndoEdit = false) }
        // Home updates on every slider step; the write waits until the slider rests.
        persistSoon()
    }
    val retainedWidgetIds: Set<Int> get() {
        val state = mutable.value
        val undoPlacements = if (state.canUndoEdit) undoLayout?.first?.widgetPlacements.orEmpty() else emptyList()
        val placements = state.widgetPlacements + undoPlacements
        // Stacked widgets are retained exactly as long as their placement (or its undoable copy) exists.
        return (placements.map { it.id }.filter { it >= 0 } + WidgetStacks.retained(state.widgetStacks, placements.map { it.slot }.toSet()) +
            TodayWidgets.retained(state.todayWidgets)).toSet()
    }
    val canPruneWidgetIds get() = !statePayloadInvalid
    fun setWidget(slot: Int, id: Int) {
        if (statePayloadInvalid) return
        val old = mutable.value
        val existing = old.layout.placement(slot)
        val next = when {
            id == EMPTY_WIDGET -> removePlacement(old.layout, DropTarget.Widget(slot))
            existing != null -> old.layout.copy(widgetPlacements = old.widgetPlacements.map { if (it.slot == slot) it.copy(id = id) else it },
                widgetRestores = old.widgetRestores.filterNot { it.slot == slot })
            else -> placeWidget(old.layout, migrateSchema5Widgets(List(slot) { EMPTY_WIDGET } + id).single())
        }
        mutable.update { it.copy(widgetPlacements = next.widgetPlacements, widgetRestores = next.widgetRestores,
            canUndoEdit = false, editRevision = it.editRevision + 1) }
        undoLayout = null
        undoImportSettings = null
        persist()
    }

    internal fun restoreLayout(layout: HomeLayout) {
        if (statePayloadInvalid) return
        val installed = (mutable.value.apps.map { it.id } + layout.folders.map { it.id }).toSet()
        mutable.update { it.copy(homeSlots = reconcileHomeSlots(layout.slots, installed),
            leadingSlots = layout.slotsForPage(-1).map { id -> id?.takeIf { it in installed || isFolderId(it) } },
            dock = layout.dock.map { id -> id?.takeIf { it in installed || isFolderId(it) } }, widgetPlacements = layout.widgetPlacements,
            folders = layout.folders, widgetRestores = layout.widgetRestores, widgetStacks = emptyMap(),
            canUndoEdit = false, editRevision = it.editRevision + 1) }
        undoLayout = null
        undoImportSettings = null
        persist()
    }

    /** Appearance/behaviour settings: no-op while the saved state is unreadable, then saved. */
    private inline fun updateSettings(soon: Boolean, change: (LauncherState) -> LauncherState) {
        if (statePayloadInvalid) return
        mutable.update(change)
        if (soon) persistSoon() else persist()
    }

    private var persistJob: kotlinx.coroutines.Job? = null
    /** Coalesces rapid changes (sliders) into one write shortly after they stop. */
    private fun persistSoon() {
        persistJob?.cancel()
        persistJob = viewModelScope.launch { kotlinx.coroutines.delay(300); persist() }
    }

    /** Whether the saved Home layout couldn't be read (it's kept untouched and editing is paused until the user decides). */
    val layoutDamaged get() = statePayloadInvalid

    /** Starts over with a default Home after a damaged save; the damaged copy stays as `state_damaged_backup`. */
    fun resetDamagedLayout() {
        if (!statePayloadInvalid) return
        // Once, like the schema backups beside it: a second damaged layout must not write over the first one,
        // which may be the only copy of a Home someone spent an evening arranging.
        if (!prefs.contains("state_damaged_backup")) {
            prefs.getString("state", null)?.let { prefs.edit().putString("state_damaged_backup", it).apply() }
        }
        statePayloadInvalid = false
        mutable.update { it.copy(error = null) }
        persist()
        refresh()
    }

    private fun persist() {
        if (needsMigration || statePayloadInvalid) return
        val s = mutable.value
        fun preset(p: LayoutPreset) = JSONObject().put("iconSize", p.iconSize).put("rowGap", p.rowGap)
            .put("dockWidth", p.dockWidth).put("dockPosition", p.dockPosition).put("dockAlignToGrid", p.dockAlignToGrid)
            .put("dockPlacement", p.dockPlacement.name).put("statusAlignToGrid", p.statusAlignToGrid).put("statusPosition", p.statusPosition)
            .put("columnGap", p.columnGap).put("dockSpacing", p.dockSpacing).put("widgetScale", p.widgetScale).put("pageTop", p.pageTop)
        val widgets = JSONArray().also { array -> s.widgetPlacements.forEach { w -> array.put(JSONObject()
            .put("slot", w.slot).put("id", w.id).put("page", w.page).put("column", w.column).put("row", w.row)
            .put("spanX", w.spanX).put("spanY", w.spanY)) } }
        val folders = JSONArray().also { array -> s.folders.forEach { folder -> array.put(JSONObject()
            .put("id", folder.id).put("title", folder.title).put("apps", JSONArray(folder.appIds))) } }
        val restores = JSONArray().also { array -> s.widgetRestores.forEach { restore -> array.put(JSONObject()
            .put("slot", restore.slot).put("provider", restore.providerComponent).put("userSerial", restore.userSerial)
            .put("title", restore.title).put("profileLabel", restore.profileLabel).put("work", restore.isWork)
            .put("sourceScope", restore.sourceScope)) } }
        val data = JSONObject().put("schema", STATE_SCHEMA).put("pinned", JSONArray(s.order)).put("homeSlots", JSONArray(s.homeSlots))
            .put("leadingSlots", JSONArray(s.leadingSlots)).put("dock", JSONArray(s.dock))
            .put("widgets", widgets).put("labels", s.labels)
            .put("folders", folders)
            .put("restores", restores)
            .put("googleSearch", s.googleSearch)
            .put("verticalStatus", s.verticalStatus)
            .put(SettingKeys.LEFT_HANDED, s.leftHanded)
            .put("hiddenApps", JSONArray(s.hiddenApps.toList()))
            .put("island", s.island)
            .put("folioPanels", s.folioPanels)
            .put("minPages", s.minPages)
            .put("statusStyle", s.statusStyle.toJson())
            .put("foldEffect", s.foldEffect).put("foldSnapshot", s.foldSnapshot).put("foldIntensity", s.foldIntensity.toDouble()).put("stayAwakeOnFold", s.stayAwakeOnFold)
            .put("panelBlur", s.panelBlur.toDouble()).put("notificationClock", s.notificationClock).put("groupNotifications", s.groupNotifications)
            .put("standBy", s.standBy).put("spotlightHidden", JSONArray(s.spotlightHidden.toList())).put("searchEngine", s.searchEngine)
            .put(SettingKeys.ISLAND_EVENTS_OFF, JSONArray(s.islandEventsOff.toList())).put("libraryCategories", s.libraryCategories).put("libraryWork", s.libraryWork).put("iconStyle", s.iconStyle.name).put("iconTint", s.iconTint)
            .put("iconShape", s.iconShape.name).put("iconPack", s.iconPack ?: JSONObject.NULL).put("badgeStyle", s.badgeStyle.name).put("badgeColor", s.badgeColor.name).put("badgeLook", s.badgeLook.name).put("badgeSize", s.badgeSize.name).put("badgesWhenOpened", s.badgesWhenOpened)
            .put("badgesSeen", JSONObject().apply { s.badgesSeen.forEach { (pkg, count) -> put(pkg, count) } }).put("searchPill", s.searchPill).put("swipeDownHome", s.swipeDownHome).put("messagesApp", s.messagesApp ?: JSONObject.NULL).put(SettingKeys.MESSAGES_AVOID_DOUBLE, s.messagesAvoidDouble)
            .put(SettingKeys.ISLAND_ALERTS, s.islandAlerts).put(SettingKeys.ISLAND_ALERT_APPS_OFF, JSONArray(s.islandAlertAppsOff.toList())).put("ccControls", JSONArray(s.ccControls))
            .put("ccSize", s.ccSize.name).put("ccCentered", s.ccCentered).put("ncSplit", s.ncSplit)
            .put("widgetStacks", JSONObject().apply { s.widgetStacks.forEach { (slot, ids) -> put(slot.toString(), JSONArray(ids)) } })
            .put("stackRotate", s.stackRotate).put("railActivitiesUnderStatus", s.railActivities).put("addNewAppsToHome", s.addNewAppsToHome)
            .put("layoutHistory", s.layoutHistory).put("dockRecentDots", s.dockRecentDots)
            .put("installedTweaks", JSONArray(s.installedTweaks.toList()))
            .put("folderColumns", s.folderColumns).put("folderBackground", s.folderBackground.name)
            .put("labelSize", s.labelSize.name).put("motionSpeed", s.motionSpeed.name).put("pageEffect", s.pageEffect.name)
            .put("widgetGlass", s.widgetGlass.toDouble()).put("glassOutline", s.glassOutline.toDouble())
            .put("focusModes", focusModesToJson(s.focusModes))
            .put("activeFocus", s.activeFocus ?: "").put("leftPage", s.leftPage).put("todayUnfolded", s.todayUnfolded).put("systemWallpaper", s.systemWallpaper).put("homeInk", s.homeInk).put("tintedGlass", s.tintedGlass).put("glassTint", s.glassTint.toDouble()).put("reduceTransparency", s.reduceTransparency)
            .put("roundedCorners", s.roundedCorners).put("cornerRadius", s.cornerRadius.toDouble())
            .put("dimWallpaperDark", s.dimWallpaperDark).put("homeScrim", s.homeScrim).put("iconTintFromWallpaper", s.iconTintFromWallpaper)
            .put("tintNotifications", s.tintNotifications).put("tintMedia", s.tintMedia).put("dockMagnify", s.dockMagnify).put("appPanels", s.appPanels).put("haptics", s.haptics).put("lockCover", s.lockCover)
            .put("featureScopes", JSONObject().apply { s.featureScopes.forEach { (id, m) -> put(id, JSONObject(m as Map<*, *>)) } }).put("notificationAppRow", s.notificationAppRow)
            .put("pageScrub", s.pageScrub).put("wallpaperMotion", s.wallpaperMotion).put("liveIcons", s.liveIcons).put("liveIconLook", s.liveIconLook)
            .put("triggerActions", JSONObject().apply { s.triggerActions.forEach { (k, v) -> put(k, v) } })
            .put("todayWidgets", JSONArray().apply { s.todayWidgets.forEach { put(JSONObject().put("id", it.id).put("size", it.size.name)) } })
            .put("folderColors", JSONObject().apply { s.folderColors.forEach { (id, c) -> put(id, c) } })
            .put("iconStacks", JSONObject().apply { s.iconStacks.forEach { (id, apps) -> put(id, JSONArray(apps)) } })
            .put("appNames", JSONObject().apply { s.appNames.forEach { (id, name) -> put(id, name) } })
            .put("pageStyles", JSONObject().apply { s.pageStyles.forEach { (page, style) -> put(page.toString(), JSONObject().put("scale", style.iconScale.toDouble())
                .apply { style.labels?.let { put("labels", it) } }) } })
            .put(SettingKeys.DOCK_EVERYWHERE, s.dockEverywhere).put(SettingKeys.ISLAND_EVERYWHERE, s.islandEverywhere)
            .put(SettingKeys.ISLAND_HIDE_FULL_SCREEN, s.islandHideFullScreen).put(SettingKeys.ISLAND_HIDE_LANDSCAPE, s.islandHideLandscape)
            .put(SettingKeys.BUTTON_BAR, s.buttonBar).put(SettingKeys.BUTTON_BAR_HEIGHT, s.buttonBarHeight.toDouble())
            .put(SettingKeys.BUTTON_BAR_WIDTH, s.buttonBarWidth.toDouble()).put(SettingKeys.BUTTON_BAR_ANDROID_ORDER, s.buttonBarAndroidOrder)
            .put(SettingKeys.BUTTON_BAR_LIGHT, s.buttonBarLight).put(SettingKeys.BUTTON_BAR_FADE, s.buttonBarFade)
            .put("compact", preset(s.compact)).put("expanded", preset(s.expanded))
            .put("homeRows", s.homeRows).put("homeFitCompact", s.homeFitCompact).put("homeFitExpanded", s.homeFitExpanded)
        val editor = prefs.edit()
        if (legacyRaw != null && sourceSchema == 2 && !prefs.contains("state_v2_backup"))
            editor.putString("state_v2_backup", legacyRaw)
        if (legacyRaw != null && sourceSchema < 4 && !prefs.contains("state_v3_backup"))
            editor.putString("state_v3_backup", legacyRaw)
        if (legacyRaw != null && sourceSchema < 5 && !prefs.contains("state_v4_backup"))
            editor.putString("state_v4_backup", legacyRaw)
        if (legacyRaw != null && sourceSchema < 6 && !prefs.contains("state_v5_backup"))
            editor.putString("state_v5_backup", legacyRaw)
        if (legacyRaw != null && sourceSchema < 7 && !prefs.contains("state_v6_backup"))
            editor.putString("state_v6_backup", legacyRaw)
        if (legacyRaw != null && sourceSchema < 8 && !prefs.contains("state_v7_backup"))
            editor.putString("state_v7_backup", legacyRaw)
        if (legacyRaw != null && sourceSchema < 9 && !prefs.contains("state_v8_backup"))
            editor.putString("state_v8_backup", legacyRaw)
        editor.putString("state", data.toString()).putBoolean("initialized", true)
            // Kept beside the state so a restore or theme import that changes it chooses the right window next time.
            .putBoolean(SettingKeys.SYSTEM_WALLPAPER, s.systemWallpaper).apply()
    }

    private fun load(): LauncherState = runCatching {
        decodeLauncherState(prefs.getString("state", "{}") ?: "{}", legacyRaw)
    }.getOrElse {
        statePayloadInvalid = legacyRaw != null
        LauncherState(loading = false, error = "Saved Home layout could not be read; it was left unchanged.")
    }

    override fun onCleared() {
        launcherApps.unregisterCallback(callback)
        if (FolioSettingsBridge.liveModel?.get() === this) FolioSettingsBridge.liveModel = null
    }
}

/** Render adaptive layers through our rounded-square mask, preserving original app artwork. */
private fun launcherIcon(drawable: Drawable): Bitmap {
    if (drawable !is AdaptiveIconDrawable) return drawable.toBitmap(144, 144)
    val bitmap = Bitmap.createBitmap(144, 144, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.clipPath(Path().apply { addRoundRect(0f, 0f, 144f, 144f, 34f, 34f, Path.Direction.CW) })
    drawable.setBounds(0, 0, 144, 144)
    drawable.background?.draw(canvas)
    drawable.foreground?.draw(canvas)
    return bitmap
}

/**
 * Reads Folio's saved state (any schema it knows), upgrading older layouts. Throws when the saved text is damaged, so
 * the caller can keep it untouched and tell the user. [legacyRaw] is the pre-schema-2 payload, when there was one.
 */
internal fun decodeLauncherState(raw: String, legacyRaw: String?): LauncherState {
    val j = JSONObject(raw)
    fun preset(key: String, default: LayoutPreset): LayoutPreset {
        val p = j.optJSONObject(key) ?: return default
        val loaded = LayoutPreset(p.optDouble("iconSize", default.iconSize.toDouble()).toFloat(),
            p.optDouble("rowGap", default.rowGap.toDouble()).toFloat(),
            p.optDouble("dockWidth", default.dockWidth.toDouble()).toFloat(),
            p.optDouble("dockPosition", default.dockPosition.toDouble()).toFloat(),
            p.optBoolean("dockAlignToGrid", true), DockPlacement.parse(p.optString("dockPlacement")),
            // 0.6.5 development builds saved "statusTop" (the top of the screen).
            p.optBoolean("statusAlignToGrid", !p.optBoolean("statusTop", false)),
            p.optDouble("statusPosition", 0.0).toFloat(),
            p.optDouble("columnGap", DEFAULT_COLUMN_GAP.toDouble()).toFloat(), p.optDouble("dockSpacing", 0.0).toFloat(),
            p.optDouble("widgetScale", 1.0).toFloat(), p.optBoolean("pageTop", false)).sanitized()
        return upgradePreset(loaded, j.optInt("schema", 1), key == "expanded")
    }
    val order = j.optJSONArray(if (j.optInt("schema", 1) >= 2) "pinned" else "order") ?: JSONArray()
    val cells = j.optJSONArray("homeSlots").takeIf { j.optInt("schema", 1) >= 4 } ?: order
    val schema = j.optInt("schema", 1)
    require(schema <= STATE_SCHEMA) { "Unsupported saved-state schema $schema" }
    // Schemas 6–8 saved 24-cell pages; the same cells keep their place on 36-cell pages (More rows).
    val legacyGrid = schema in 6..8
    val rawSlots = List(cells.length()) { cells.optString(it).takeIf { id -> id.isNotBlank() && id != "null" } }
        .let { if (legacyGrid) migrateLegacyHomeSlots(it) else it }
    val legacySlots = normalizeHomeSlots(rawSlots)
    val rawLeadingSlots = if (schema >= 8) {
        val leading = j.optJSONArray("leadingSlots") ?: error("Schema 8 requires a leading slot array")
        val size = if (legacyGrid) LEGACY_HOME_CELLS else HOME_CELLS
        require(leading.length() == size)
        migrateLegacyLeadingSlots(List(size) { leading.optString(it).takeIf { id -> id.isNotBlank() && id != "null" } })
    } else List(HOME_CELLS) { null }
    val loadedDock = List(4) { j.optJSONArray("dock")?.optString(it)?.takeIf { it.isNotBlank() && it != "null" } }
    val widgetArray = j.optJSONArray("widgets")
    val placements = if (schema >= 6) {
        require(widgetArray != null) { "Schema $schema requires a widget placement array" }
        fun strictInt(objectValue: JSONObject, key: String): Int {
            val number = objectValue.get(key) as? Number ?: error("$key must be an integer")
            val value = number.toDouble()
            require(value.isFinite() && value % 1.0 == 0.0 && value >= Int.MIN_VALUE && value <= Int.MAX_VALUE) {
                "$key must be a finite integer"
            }
            return value.toInt()
        }
        List(widgetArray.length()) { index ->
            val w = widgetArray.getJSONObject(index)
            WidgetPlacement(strictInt(w, "slot"), strictInt(w, "id"), strictInt(w, "page"), strictInt(w, "column"), strictInt(w, "row"),
                strictInt(w, "spanX"), strictInt(w, "spanY")).let { if (legacyGrid) migrateLegacyWidgetPlacement(it) else it }
        }.also { loaded ->
            require(loaded.map { it.slot }.distinct().size == loaded.size) { "Widget placement slots must be unique" }
            loaded.forEach { placement ->
                val baseGeometry = placement.slot >= 0 && placement.id != EMPTY_WIDGET && placement.page >= -1 &&
                    placement.column >= 0 && placement.row >= 0 && placement.spanX in 1..GRID_COLUMNS &&
                    placement.spanY in 1..GRID_ROWS && placement.column + placement.spanX <= GRID_COLUMNS
                val insideGrid = placement.row + placement.spanY <= GRID_ROWS
                val migratedOverflow = placement.page > 0 && placement.slot / 3 == placement.page && placement.slot % 3 == 2 &&
                    placement.column == 0 && placement.row == GRID_ROWS && placement.spanX == GRID_COLUMNS && placement.spanY == 4
                require(baseGeometry && (insideGrid || migratedOverflow)) { "Invalid widget placement" }
            }
        }
    } else {
        val ids = if (schema < 5) List(3) { index ->
            (widgetArray?.optInt(index, -1) ?: -1).let {
                if (it < 0) listOf(CLOCK_WIDGET, DATE_WIDGET, INFO_WIDGET)[index] else it
            }
        } else List(widgetArray?.length() ?: 0) { widgetArray!!.optInt(it, EMPTY_WIDGET) }
        migrateSchema5Widgets(ids)
    }.filterNot { placement -> schema < 8 && placement == WidgetPlacement(2, INFO_WIDGET, -1, 0, 0, 4, 6) }
    val folders = if (schema >= 7) {
        val array = j.optJSONArray("folders") ?: error("Schema 7 requires a folder array")
        List(array.length()) { index ->
            val item = array.getJSONObject(index)
            val apps = item.getJSONArray("apps")
            FolderEntry(item.getString("id"), item.getString("title"), List(apps.length()) { apps.getString(it) })
        }.also { loaded ->
            require(loaded.map(FolderEntry::id).distinct().size == loaded.size)
            require(loaded.flatMap(FolderEntry::appIds).distinct().size == loaded.sumOf { it.appIds.size })
            loaded.forEach { folder ->
                require(isFolderId(folder.id) && folder.title.isNotBlank() && folder.appIds.size >= 2)
                require(folder.appIds.none { it.isBlank() || isReservedFolderId(it) })
            }
            val children = loaded.flatMapTo(mutableSetOf(), FolderEntry::appIds)
            val folderIds = loaded.mapTo(mutableSetOf(), FolderEntry::id)
            val rawFolderRefs = (rawSlots + rawLeadingSlots).filterNotNull().filter(::isReservedFolderId)
            require(rawFolderRefs.all(::isFolderId))
            require(rawFolderRefs.size == folderIds.size && rawFolderRefs.toSet() == folderIds)
            require((rawSlots + rawLeadingSlots).none { it in children } &&
                loadedDock.none { it in children || (it != null && isReservedFolderId(it)) })
        }
    } else emptyList()
    if (schema >= 8) {
        val leadingIds = rawLeadingSlots.filterNotNull()
        require(leadingIds.distinct().size == leadingIds.size) {
            "An unfolded-only shortcut appears more than once"
        }
        val leadingApps = leadingIds.filterNot(::isReservedFolderId)
        val otherApps = rawSlots.filterNotNull().filterNot(::isReservedFolderId) +
            loadedDock.filterNotNull() + folders.flatMap(FolderEntry::appIds)
        require(leadingApps.none { it in otherApps }) {
            "An unfolded-only app shortcut appears on another surface"
        }
        val occupiedLeadingCells = rawLeadingSlots.indices
            .filterTo(mutableSetOf()) { rawLeadingSlots[it] != null }
            .mapTo(mutableSetOf()) { homeCellIndex(-1, it) }
        require(placements.filter { it.page == -1 }.none { placement ->
            placement.coveredIndices().any { it in occupiedLeadingCells }
        }) { "An unfolded-only shortcut overlaps a widget" }
    }
    val restores = if (schema >= 7) {
        val array = j.optJSONArray("restores") ?: JSONArray()
        List(array.length()) { index ->
            val item = array.getJSONObject(index)
            WidgetRestore(item.getInt("slot"), item.getString("provider"), item.getLong("userSerial"),
                item.getString("title"), item.getString("profileLabel"), item.optBoolean("work", false),
                item.optString("sourceScope").takeIf { it.isNotBlank() && it != "null" })
        }.also { loaded ->
            require(loaded.map(WidgetRestore::slot).distinct().size == loaded.size)
            loaded.forEach { restore ->
                require(restore.slot >= 0 && restore.userSerial >= 0 && restore.title.isNotBlank() &&
                    restore.profileLabel.isNotBlank() && ComponentName.unflattenFromString(restore.providerComponent) != null)
            }
            require(placements.filter { it.id == NEEDS_BINDING_WIDGET }.map { it.slot }.toSet() == loaded.map { it.slot }.toSet())
        }
    } else emptyList()
    return LauncherState(homeSlots = if (schema in 2..5) migrateSchema5Apps(legacySlots) else legacySlots,
        leadingSlots = rawLeadingSlots,
        dock = loadedDock,
        widgetPlacements = placements, folders = folders, widgetRestores = restores,
        googleSearch = j.optBoolean("googleSearch", true),
        labels = j.optBoolean("labels", true), compact = preset("compact", LayoutPreset()),
        expanded = preset("expanded", LayoutPreset()), verticalStatus = j.optBoolean("verticalStatus", true),
        leftHanded = j.optBoolean("leftHanded", false),
        hiddenApps = j.optJSONArray("hiddenApps")?.let { a -> (0 until a.length()).map(a::getString).toSet() } ?: emptySet(),
        island = j.optBoolean("island", true),
        folioPanels = j.optBoolean("folioPanels", true),
        minPages = j.optInt("minPages", 1).coerceIn(1, 20),
        statusStyle = StatusStyle.fromJson(j.optJSONObject("statusStyle")),
        foldEffect = j.optBoolean("foldEffect", true), foldSnapshot = j.optBoolean("foldSnapshot", false), foldIntensity = j.optDouble("foldIntensity", 1.0).toFloat().coerceIn(.3f, 1.5f),
        stayAwakeOnFold = j.optBoolean("stayAwakeOnFold", true),
        panelBlur = j.optDouble("panelBlur", 1.0).toFloat().coerceIn(0f, 1f), notificationClock = j.optBoolean("notificationClock", true),
        groupNotifications = j.optBoolean("groupNotifications", true),
        standBy = j.optBoolean("standBy", true),
        spotlightHidden = j.optJSONArray("spotlightHidden")?.let { a -> (0 until a.length()).map(a::getString).toSet() } ?: emptySet(),
        searchEngine = j.optString("searchEngine", "GOOGLE"),
        islandEventsOff = j.optJSONArray(SettingKeys.ISLAND_EVENTS_OFF)?.let { a -> (0 until a.length()).map(a::getString).toSet() } ?: emptySet(),
        libraryCategories = j.optBoolean("libraryCategories", true),
        libraryWork = j.optBoolean("libraryWork", true),
        iconStyle = runCatching { IconStyle.valueOf(j.optString("iconStyle")) }.getOrDefault(IconStyle.DEFAULT),
        iconTint = j.optLong("iconTint", FolioColors.Value.Warning),
        iconShape = runCatching { IconShape.valueOf(j.optString("iconShape")) }.getOrDefault(IconShape.DEFAULT),
        iconPack = j.optString("iconPack").takeIf { it.isNotBlank() && it != "null" },
        badgeStyle = runCatching { BadgeStyle.valueOf(j.optString("badgeStyle")) }.getOrDefault(BadgeStyle.DOT),
        badgeColor = runCatching { BadgeColor.valueOf(j.optString("badgeColor")) }.getOrDefault(BadgeColor.RED),
        badgeLook = runCatching { BadgeLook.valueOf(j.optString("badgeLook")) }.getOrDefault(BadgeLook.IOS),
        badgeSize = runCatching { BadgeSize.valueOf(j.optString("badgeSize")) }.getOrDefault(BadgeSize.STANDARD),
        badgesWhenOpened = j.optBoolean("badgesWhenOpened", false),
        badgesSeen = j.optJSONObject("badgesSeen")?.let { o -> o.keys().asSequence().associateWith { o.getInt(it) }.filterValues { it > 0 } } ?: emptyMap(),
        searchPill = j.optBoolean("searchPill", true),
        // Up to 0.6.0 this was a switch for Spotlight alone.
        swipeDownHome = j.optString("swipeDownHome", "").ifBlank { if (j.optBoolean("swipeDownSearch", true)) "SPOTLIGHT" else "OFF" },
        messagesApp = j.optString("messagesApp").takeIf { it.isNotBlank() && it != "null" },
        messagesAvoidDouble = j.optBoolean(SettingKeys.MESSAGES_AVOID_DOUBLE, true),
        islandAlerts = j.optBoolean(SettingKeys.ISLAND_ALERTS, false),
        islandAlertAppsOff = j.optJSONArray(SettingKeys.ISLAND_ALERT_APPS_OFF)?.let { a -> (0 until a.length()).map(a::getString).toSet() } ?: emptySet(),
        ccControls = j.optJSONArray("ccControls")?.let { a -> (0 until a.length()).map(a::getString).filter { name -> CcControl.entries.any { it.name == name } } }
            ?: CcControl.DEFAULTS,
        ccSize = runCatching { PanelSize.valueOf(j.optString("ccSize")) }.getOrDefault(PanelSize.STANDARD),
        ccCentered = j.optBoolean("ccCentered", false), ncSplit = j.optBoolean("ncSplit", true),
        widgetStacks = j.optJSONObject("widgetStacks")?.let { o -> o.keys().asSequence().mapNotNull { key ->
            val ids = o.optJSONArray(key) ?: return@mapNotNull null
            key.toIntOrNull()?.let { slot -> slot to (0 until ids.length()).map(ids::getInt) }
        }.toMap() } ?: emptyMap(),
        stackRotate = j.optBoolean("stackRotate", true),
        railActivities = j.optBoolean("railActivitiesUnderStatus", false),
        addNewAppsToHome = j.optBoolean("addNewAppsToHome", false),
        layoutHistory = j.optBoolean("layoutHistory", false), dockRecentDots = j.optBoolean("dockRecentDots", false),
        folderColumns = j.optInt("folderColumns", 0).takeIf { it in setOf(0, 3, 4) } ?: 0,
        folderBackground = runCatching { FolderBackground.valueOf(j.optString("folderBackground")) }.getOrDefault(FolderBackground.GLASS),
        labelSize = runCatching { LabelSize.valueOf(j.optString("labelSize")) }.getOrDefault(LabelSize.STANDARD),
        motionSpeed = runCatching { MotionSpeed.valueOf(j.optString("motionSpeed")) }.getOrDefault(MotionSpeed.STANDARD),
        // A save from before Page Effects, and anything unrecognised, reads as the flat swipe.
        pageEffect = PageEffect.of(j.optString("pageEffect")),
        widgetGlass = j.optDouble("widgetGlass", .26).toFloat().coerceIn(0f, 1f), glassOutline = j.optDouble("glassOutline", .16).toFloat().coerceIn(0f, 1f),
        focusModes = focusModesFromJson(j.optJSONArray("focusModes")),
        activeFocus = j.optString("activeFocus").takeIf { it.isNotEmpty() },
        leftPage = j.optString("leftPage", "TODAY").takeIf { it in setOf("TODAY", "DISCOVER", "NONE") } ?: "TODAY",
        todayUnfolded = j.optString("todayUnfolded", "PAGE").takeIf { it in setOf("PAGE", "BESIDE", "OFF") } ?: "PAGE",
        systemWallpaper = j.optBoolean("systemWallpaper", false),
        homeInk = j.optString("homeInk", "AUTO").takeIf { it in setOf("AUTO", "LIGHT", "DARK") } ?: "AUTO",
        tintedGlass = j.optBoolean("tintedGlass", true),
        glassTint = j.optDouble("glassTint", .5).toFloat().takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: .5f,
        reduceTransparency = j.optBoolean("reduceTransparency", false),
        roundedCorners = j.optBoolean("roundedCorners", false), cornerRadius = j.optDouble("cornerRadius", 40.0).toFloat().coerceIn(16f, 72f), dimWallpaperDark = j.optBoolean("dimWallpaperDark", true),
        homeScrim = j.optBoolean("homeScrim", true),
        iconTintFromWallpaper = j.optBoolean("iconTintFromWallpaper", false),
        tintNotifications = j.optBoolean("tintNotifications", false), tintMedia = j.optBoolean("tintMedia", true),
        dockMagnify = j.optBoolean("dockMagnify", false), appPanels = j.optBoolean("appPanels", true), haptics = j.optBoolean("haptics", true), lockCover = j.optBoolean("lockCover", true),
        featureScopes = j.optJSONObject("featureScopes")?.let { o -> o.keys().asSequence().associateWith { id ->
            o.optJSONObject(id)?.let { inner -> inner.keys().asSequence().associateWith { inner.getString(it) } }.orEmpty()
        } } ?: emptyMap(),
        notificationAppRow = j.optBoolean("notificationAppRow", true), pageScrub = j.optBoolean("pageScrub", true),
        wallpaperMotion = j.optBoolean("wallpaperMotion", true), liveIcons = j.optBoolean("liveIcons", true),
        liveIconLook = j.optString("liveIconLook", "AUTO").takeIf { it in setOf("AUTO", "LIGHT", "DARK") } ?: "AUTO",
        triggerActions = j.optJSONObject("triggerActions")?.let { o -> o.keys().asSequence().associateWith { o.getString(it) } } ?: emptyMap(),
        todayWidgets = j.optJSONArray("todayWidgets")?.let { a -> (0 until a.length()).mapNotNull { i ->
            a.optJSONObject(i)?.let { o -> runCatching { TodayWidget(o.getInt("id"), TodaySize.valueOf(o.getString("size"))) }.getOrNull() }
        } } ?: DEFAULT_TODAY_WIDGETS,
        folderColors = j.optJSONObject("folderColors")?.let { o -> o.keys().asSequence().associateWith { o.getLong(it) } } ?: emptyMap(),
        pageStyles = j.optJSONObject("pageStyles")?.let { o -> o.keys().asSequence().mapNotNull { key ->
            val page = key.toIntOrNull()?.takeIf { it >= 0 } ?: return@mapNotNull null
            val style = o.optJSONObject(key) ?: return@mapNotNull null
            page to PageStyle(style.optDouble("scale", 1.0).toFloat().coerceIn(.7f, 1.3f), if (style.has("labels")) style.optBoolean("labels") else null)
        }.toMap() } ?: emptyMap(),
        // Held to the same shape as a name typed into the rename field: settings written by an older build, or a
        // file someone edited by hand, shouldn't be able to hang a paragraph under an icon.
        appNames = j.optJSONObject("appNames")?.let { o -> o.keys().asSequence()
            .associateWith { o.optString(it).trim().takeAppName() }
            .filterValues(String::isNotBlank) } ?: emptyMap(),
        iconStacks = j.optJSONObject("iconStacks")?.let { o -> o.keys().asSequence().associateWith { key ->
            o.optJSONArray(key)?.let { a -> (0 until a.length()).mapNotNull { a.optString(it).takeIf(String::isNotBlank) } }.orEmpty()
        }.filterValues { it.isNotEmpty() } } ?: emptyMap(),
        dockEverywhere = j.optBoolean("dockEverywhere", false), islandEverywhere = j.optBoolean("islandEverywhere", false),
        islandHideFullScreen = j.optBoolean(SettingKeys.ISLAND_HIDE_FULL_SCREEN, true),
        islandHideLandscape = j.optBoolean(SettingKeys.ISLAND_HIDE_LANDSCAPE, false),
        buttonBar = j.optBoolean(SettingKeys.BUTTON_BAR, false),
        buttonBarHeight = j.optDouble(SettingKeys.BUTTON_BAR_HEIGHT, 52.0).toFloat().coerceIn(44f, 60f),
        buttonBarWidth = j.optDouble(SettingKeys.BUTTON_BAR_WIDTH, .5).toFloat().coerceIn(.3f, .8f),
        buttonBarAndroidOrder = j.optBoolean(SettingKeys.BUTTON_BAR_ANDROID_ORDER, false),
        buttonBarLight = j.optBoolean(SettingKeys.BUTTON_BAR_LIGHT, false),
        buttonBarFade = j.optBoolean(SettingKeys.BUTTON_BAR_FADE, true),
        // Automatic is the new default, but it must not rearrange a Home that already exists: a save written before
        // this release comes back fixed at the four rows it was drawn with, and Settings › Home Screen & Dock offers
        // Automatic to anyone who wants the taller screens filled.
        homeRows = j.optInt("homeRows", if (j.optInt("schema", 1) in 1 until MORE_ROWS_SCHEMA) BASE_APP_ROWS else 0)
            .takeIf { it == BASE_APP_ROWS } ?: 0,
        homeFitCompact = j.optInt("homeFitCompact", 0).takeIf { it in BASE_APP_ROWS..MAX_APP_ROWS } ?: 0,
        homeFitExpanded = j.optInt("homeFitExpanded", 0).takeIf { it in BASE_APP_ROWS..MAX_APP_ROWS } ?: 0)
        .let { st ->
            val saved = j.optJSONArray("installedTweaks")
            when {
                saved != null -> st.copy(installedTweaks = (0 until saved.length()).mapNotNull { saved.optString(it).takeIf(String::isNotBlank) }.toSet())
                // Updating from before the Tweak Library: every tweak that's on counts as installed, so nothing changes.
                legacyRaw != null -> st.copy(installedTweaks = TweakFeatures.filter { it.get(st) || it.id in st.featureScopes }.mapTo(mutableSetOf()) { it.id })
                // A new install starts clean: tweaks are added from the Tweak Library when wanted.
                else -> st.copy(installedTweaks = emptySet(), appPanels = false, dockMagnify = false, notificationAppRow = false,
                    tintNotifications = false, tintMedia = false)
            }
        }
}

/** How strongly glass takes the wallpaper color: none when tinting is off; half the slider is the original .28. */
val LauncherState.glassTintAmount: Float get() = if (tintedGlass) .56f * glassTint else 0f

/** Reduce Transparency: nearly solid widgets, Side Bar and dock, with a clearer edge (the saved values stay as they are). */
fun LauncherState.withSolidGlass(): LauncherState =
    copy(widgetGlass = maxOf(widgetGlass, .9f), glassOutline = maxOf(glassOutline, .45f), statusStyle = statusStyle.copy(railGlass = maxOf(statusStyle.railGlass, .9f)))

/**
 * Android counts changes to installed themes and overlays (Samsung's Theme Park among them) in the configuration's
 * assets sequence. It isn't public API, but the configuration prints it as "as.N", so a theme that swaps icons without
 * a package change still clears the icon cache, while folding and rotating don't. 0 when it isn't there.
 */
internal fun assetsSequence(configuration: String): Int =
    Regex("""\bas\.(\d+)""").find(configuration)?.groupValues?.get(1)?.toIntOrNull() ?: 0
