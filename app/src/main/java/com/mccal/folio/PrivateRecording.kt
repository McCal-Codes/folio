package com.mccal.folio

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Whether Folio's private view is on: the person turned Private While Recording on ([enabled]) and Android says Folio's
 * windows are in a screen recording or share ([recording], Android 15+). Kept in memory only, so a crash can never
 * leave Home in the private view; [enabled] is the one thing saved.
 *
 * Android reports only visible or not visible, and may flip quickly (a recorder pausing, a cast reconnecting). The
 * private view goes on at once, since a late switch would show what it is meant to hide, and goes off only after
 * [offDelayMs] of staying not visible, so a quick flip never makes Home flap between the two.
 */
internal class PrivateViewState(private val scope: CoroutineScope, private val offDelayMs: Long = OFF_DELAY_MS) {
    private val recordingMutable = MutableStateFlow(false)
    private val enabledMutable = MutableStateFlow(false)
    private val onMutable = MutableStateFlow(false)
    private val startedMutable = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private var offJob: Job? = null

    /** What Android last said, after the delay above. */
    val recording: StateFlow<Boolean> = recordingMutable.asStateFlow()
    val enabled: StateFlow<Boolean> = enabledMutable.asStateFlow()
    /** The private view is showing. */
    val on: StateFlow<Boolean> = onMutable.asStateFlow()
    /** One item each time the private view goes on (not when it goes off), for the island notice. */
    val started: SharedFlow<Unit> = startedMutable.asSharedFlow()

    /** What each listening source (the activity, the overlay service) last said; a source that is gone is not here. */
    private val sources = mutableMapOf<String, Boolean>()

    /**
     * One source's answer. The private view is on if ANY source says Folio's windows are visible in a recording, so
     * one source stopping or saying no never clears what another still reports. Reporting registers the source.
     */
    @Synchronized fun report(visible: Boolean, source: String = ACTIVITY) {
        sources[source] = visible
        settle()
    }

    /** A source stopped listening. With none left nothing can say Folio is recorded, so the state clears at once. */
    @Synchronized fun detach(source: String) {
        if (sources.remove(source) == null) return
        if (sources.isEmpty()) { offJob?.cancel(); offJob = null; recordingMutable.value = false; refresh() } else settle()
    }

    private fun settle() {
        if (sources.values.any { it }) {
            offJob?.cancel(); offJob = null
            recordingMutable.value = true
        } else if (recordingMutable.value && offJob == null) {
            offJob = scope.launch { delay(offDelayMs); settleOff() }
        }
        refresh()
    }

    @Synchronized private fun settleOff() { offJob = null; if (sources.values.none { it }) recordingMutable.value = false; refresh() }

    @Synchronized fun setEnabled(value: Boolean) { enabledMutable.value = value; refresh() }

    private fun refresh() {
        val next = enabledMutable.value && recordingMutable.value
        if (next == onMutable.value) return
        onMutable.value = next
        if (next) startedMutable.tryEmit(Unit)
    }

    companion object { const val OFF_DELAY_MS = 1_000L; const val ACTIVITY = "activity"; const val OVERLAY = "overlay" }
}

/**
 * Private While Recording (Settings › General › Advanced). While Folio's windows are in a screen recording or share,
 * Home hides what a stranger watching should not see: Swap Apps applies on both screens, the island and Notification
 * Center show the app and the word Notification instead of the message, badge numbers become dots, and Hidden Apps
 * relocks. It is separate from [ScreenshotMode]: clocks keep the real time, and nothing else about Screenshot Mode
 * changes. The switch is a plain boolean beside the launcher state (like the system wallpaper flag), not a key in
 * `launcher/state`, so no schema changes and every older Folio ignores it.
 */
internal object PrivateRecording {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val state = PrivateViewState(scope)
    @Volatile private var loaded = false

    val on: StateFlow<Boolean> get() = state.on
    val enabled: StateFlow<Boolean> get() = state.enabled
    val started: SharedFlow<Unit> get() = state.started

    /** Android 15 added the recording callback; below it the feature has nothing to listen to. */
    val supported: Boolean get() = android.os.Build.VERSION.SDK_INT >= 35

    /** Swapped apps by package with their stand-ins, kept up to date by Home for the island and Notification Center. */
    val standIns = MutableStateFlow<Map<String, AppEntry>>(emptyMap())

    /** The word for a notification's text, in the person's language. Set from a context when Folio starts. */
    // Only a fallback until load() replaces it with the translated string at startup.
    @Volatile var notificationWord: String = "Notification" // english-only

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(SettingKeys.PREFS, Context.MODE_PRIVATE)

    fun load(context: Context) {
        if (!loaded) synchronized(this) {
            if (!loaded) { state.setEnabled(FeatureGate.PRIVATE_RECORDING.isOpen(context) && prefs(context).getBoolean(SettingKeys.PRIVATE_WHILE_RECORDING, false)); loaded = true }
        }
        notificationWord = context.getString(R.string.private_notification_word)
    }

    fun setEnabled(context: Context, value: Boolean) {
        load(context)
        state.setEnabled(value)
        prefs(context).edit().putBoolean(SettingKeys.PRIVATE_WHILE_RECORDING, value).apply()
    }

    /** From the window manager's callback: whether Folio's windows are in a recording now. */
    fun report(visible: Boolean, source: String = PrivateViewState.ACTIVITY) = state.report(visible, source)
    fun detach(source: String) = state.detach(source)

    /** [source], or [transform]ed while the private view is on. */
    fun <T> redact(source: StateFlow<T>, transform: (T, Map<String, AppEntry>) -> T): StateFlow<T> =
        combine(source, on, standIns) { value, private, swapped -> if (private) transform(value, swapped) else value }
            .stateIn(scope, SharingStarted.Eagerly, source.value)

    /** An island event as it is shown right now. */
    fun redact(event: IslandEvent): IslandEvent =
        if (on.value) redactEvent(event, standIns.value, notificationWord, neutral((event as? IslandEvent.Message)?.packageName, standIns.value)) else event

    /**
     * Whether [packageName] is swapped by the saved Swap Apps rules but has no stand-in to show (Home never composed, as
     * when only the overlay service runs). Its real name and icon must not appear, so callers show a neutral label.
     */
    fun neutral(packageName: String?, standIns: Map<String, AppEntry>): Boolean =
        packageName != null && packageName !in standIns && isSwappedPackage(packageName, ScreenshotSwap.rules.value, SystemPackageCache::isSystem)
}

/**
 * Listens for the Android 15 screen-recording callback on a window manager and feeds [PrivateRecording] as [source].
 * Safe to start twice or stop twice. Below Android 15 it does nothing.
 */
internal class RecordingWatch(private val source: String) {
    private var callback: java.util.function.Consumer<Int>? = null

    @Synchronized fun start(windowManager: android.view.WindowManager, executor: java.util.concurrent.Executor) {
        if (android.os.Build.VERSION.SDK_INT < 35 || callback != null) return
        val visible = android.view.WindowManager.SCREEN_RECORDING_STATE_VISIBLE
        val next = java.util.function.Consumer<Int> { PrivateRecording.report(it == visible, source) }
        runCatching {
            val current = windowManager.addScreenRecordingCallback(executor, next)
            callback = next
            PrivateRecording.report(current == visible, source)
        }
    }

    @Synchronized fun stop(windowManager: android.view.WindowManager) {
        val old = callback ?: return
        callback = null
        if (android.os.Build.VERSION.SDK_INT >= 35) runCatching { windowManager.removeScreenRecordingCallback(old) }
        PrivateRecording.detach(source)
    }
}

/** Swap Apps applies in Screenshot Mode and in the private view; with no rule set, neither swaps anything. */
internal fun swapActive(screenshot: Boolean, private: Boolean, rules: DisguiseRules) = (screenshot || private) && !rules.isEmpty

/** The package an app id names (an id is a component, possibly with a profile in front). */
internal fun packageOfAppId(id: String): String? =
    android.content.ComponentName.unflattenFromString(parseProfileAppId(id)?.component ?: id)?.packageName

/**
 * Whether the saved Swap Apps [rules] swap [packageName]. Apps that shipped with the phone are never swapped; a package
 * whose class is not known yet ([isSystem] null) counts as swappable, so a doubt hides rather than shows.
 */
internal fun isSwappedPackage(packageName: String, rules: DisguiseRules, isSystem: (String) -> Boolean?): Boolean =
    !rules.isEmpty && isSystem(packageName) != true &&
        (rules.allThirdParty || rules.chosen.any { packageOfAppId(it) == packageName })

/** Notification badges while private: numbers become dots; Off stays Off. */
internal fun privateBadgeStyle(style: BadgeStyle, private: Boolean) = if (private && style == BadgeStyle.COUNT) BadgeStyle.DOT else style

/**
 * A notification with its title and text replaced by the word [word], and, for a swapped app, the stand-in's name and
 * icon, so a recording never names the real app. It cannot be replied to or marked read from here.
 */
internal fun redactNotification(item: NotificationItem, standIn: AppEntry?, word: String, neutral: Boolean = false): NotificationItem = item.copy(
    appLabel = if (neutral) word else standIn?.label ?: item.appLabel, icon = if (neutral) null else standIn?.icon ?: item.icon,
    title = null, text = word, canReply = false, canMarkRead = false)

/** Messages show the app and [word]; a device's name is dropped. Other events carry nothing personal. */
internal fun redactEvent(event: IslandEvent, standIns: Map<String, AppEntry>, word: String, neutral: Boolean = false): IslandEvent = when (event) {
    is IslandEvent.Message -> {
        val standIn = standIns[event.packageName]
        val app = if (neutral) word else standIn?.label ?: event.appLabel
        event.copy(appLabel = app, sender = app, text = word, avatar = null, appIcon = if (neutral) null else standIn?.icon ?: event.appIcon, canReply = false)
    }
    is IslandEvent.Bluetooth -> event.copy(name = null)
    else -> event
}

/** Music, calls, timers, directions and progress show as the app's icon only: no title, subtitle, caller photo or art. */
internal fun redactActivity(activity: IslandActivity, standIn: AppEntry?, neutral: Boolean = false): IslandActivity {
    val icon = if (neutral) null else standIn?.icon ?: activity.icon
    return when (activity) {
        is IslandActivity.Media -> {
            val controller = runCatching { activity.controller }.getOrNull()
            activity.copy(title = "", subtitle = null, icon = icon, art = null).also { c -> controller?.let { c.controller = it } }
        }
        is IslandActivity.Progress -> activity.copy(title = "", subtitle = null, icon = icon)
        is IslandActivity.Call -> activity.copy(title = "", icon = icon, avatar = null)
        is IslandActivity.Timer -> activity.copy(title = "", icon = icon)
        is IslandActivity.Navigation -> activity.copy(title = "", subtitle = null, icon = icon)
    }
}
