package com.mccal.folio

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Stable names for settings. */
internal val IslandEvent.kind: String get() = when (this) {
    is IslandEvent.Charging -> "CHARGING"
    is IslandEvent.Silent -> "SILENT"
    is IslandEvent.Focus -> "FOCUS"
    is IslandEvent.Bluetooth -> "BLUETOOTH"
    is IslandEvent.Message -> if (alert) "ALERT" else "MESSAGE"
    is IslandEvent.Notice -> "NOTICE"
}

/**
 * The charging island's moment, and the Charging trigger's: the cable going in. Plugged in counts as charging, as it does
 * for the Side Bar and StandBy, so a phone held at a charge limit (Samsung's battery protection) shows it on plug-in,
 * and the battery topping back up at the limit doesn't show it again. The first reading after Folio starts listening
 * is the state it was already in, not news.
 */
internal fun startsCharging(wasCharging: Boolean?, status: Int, plugged: Int): Boolean = wasCharging == false && isCharging(status, plugged)

/** Listens for brief system moments (charging, silent, focus, Bluetooth) and publishes them for the island. */
class IslandEvents private constructor(private val context: Context) {
    private var registered = false
    private var lastCharging: Boolean? = null
    private var lastRinger: Int? = null
    private var lastFocus: Boolean? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_BATTERY_CHANGED -> {
                    val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                    val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
                    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1).takeIf { it >= 0 }
                        ?.let { it * 100 / intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1) }
                    if (startsCharging(lastCharging, status, plugged)) { emit(IslandEvent.Charging(level)); FolioActions.onTrigger(c, FolioTrigger.CHARGING) }
                    lastCharging = isCharging(status, plugged)
                }
                AudioManager.RINGER_MODE_CHANGED_ACTION -> {
                    val mode = intent.getIntExtra(AudioManager.EXTRA_RINGER_MODE, AudioManager.RINGER_MODE_NORMAL)
                    if (lastRinger != null && mode != lastRinger) emit(IslandEvent.Silent(mode != AudioManager.RINGER_MODE_NORMAL))
                    lastRinger = mode
                }
                NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED -> {
                    val on = c.getSystemService(NotificationManager::class.java).currentInterruptionFilter > NotificationManager.INTERRUPTION_FILTER_ALL
                    if (lastFocus != null && on != lastFocus) emit(IslandEvent.Focus(on))
                    lastFocus = on
                }
                AudioManager.ACTION_HEADSET_PLUG -> {
                    // The sticky state delivered on registration isn't a new plug-in.
                    if (!isInitialStickyBroadcast && intent.getIntExtra("state", 0) == 1) FolioActions.onTrigger(c, FolioTrigger.HEADPHONES)
                }
            }
        }
    }

    // Bluetooth headphones and speakers come from the audio device list, which carries the device's name and needs no
    // Bluetooth permission. One device can appear as several outputs (media and calls), so each name counts once.
    private val audio = context.getSystemService(AudioManager::class.java)
    private val knownOutputs = mutableSetOf<Int>()
    private var lastAudioName: String? = null
    private var lastAudioAt = 0L
    private val audioCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>) {
            added.filter { it.isSink && knownOutputs.add(it.id) }.firstOrNull { it.type in BLUETOOTH_OUTPUTS }?.let { device ->
                val name = device.productName?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                val now = System.currentTimeMillis()
                if (name != null && name == lastAudioName && now - lastAudioAt < 5_000) return
                lastAudioName = name; lastAudioAt = now
                emit(IslandEvent.Bluetooth(name, speaker = device.type == AudioDeviceInfo.TYPE_BLE_SPEAKER))
                FolioActions.onTrigger(context, FolioTrigger.BLUETOOTH)
            }
        }
        override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>) { removed.forEach { knownOutputs.remove(it.id) } }
    }

    private fun register() {
        if (registered) return
        // Outputs already connected aren't news; only ones added after this count.
        audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).mapTo(knownOutputs) { it.id }
        audio.registerAudioDeviceCallback(audioCallback, Handler(Looper.getMainLooper()))
        lastRinger = context.getSystemService(AudioManager::class.java).ringerMode
        lastFocus = context.getSystemService(NotificationManager::class.java).currentInterruptionFilter > NotificationManager.INTERRUPTION_FILTER_ALL
        ContextCompat.registerReceiver(context, receiver, IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED); addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
            addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
            addAction(AudioManager.ACTION_HEADSET_PLUG)
        }, ContextCompat.RECEIVER_NOT_EXPORTED) // all protected system broadcasts
        registered = true
    }

    private fun unregister() {
        if (registered) { runCatching { context.unregisterReceiver(receiver) }; audio.unregisterAudioDeviceCallback(audioCallback) }
        registered = false
        knownOutputs.clear()
        lastCharging = null
    }

    private fun emit(event: IslandEvent) = post(event)

    companion object {
        private val mutable = MutableStateFlow<Pair<IslandEvent, Long>?>(null)
        val latest: StateFlow<Pair<IslandEvent, Long>?> = mutable.asStateFlow()
        private val BLUETOOTH_OUTPUTS = setOf(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER)
        const val SHOW_MS = 2_600L
        const val MESSAGE_SHOW_MS = 6_000L
        const val NOTICE_SHOW_MS = 3_500L

        /**
         * How long [event] stays up: the times above, or longer for someone who has asked Android for more time to
         * read and act (A11Y-19), as the Market's banners do. A message can be opened or answered, so it counts as
         * having controls too.
         */
        fun showMs(context: Context, event: IslandEvent): Long {
            val shown = when (event) {
                is IslandEvent.Message -> MESSAGE_SHOW_MS
                is IslandEvent.Notice -> NOTICE_SHOW_MS
                else -> SHOW_MS
            }
            val content = AccessibilityManager.FLAG_CONTENT_TEXT or AccessibilityManager.FLAG_CONTENT_ICONS or
                (if (event is IslandEvent.Message) AccessibilityManager.FLAG_CONTENT_CONTROLS else 0)
            return context.getSystemService(AccessibilityManager::class.java)
                ?.getRecommendedTimeoutMillis(shown.toInt(), content)?.toLong() ?: shown
        }

        /** Home islands on screen that can show a notice card (not the upright one beside a side camera). */
        @Volatile internal var noticeIslands = 0

        /**
         * Folio's own brief feedback, shown next to where you are instead of as a toast: in Home's island when it's on
         * screen, and as a toast otherwise (island off, another app in front).
         */
        fun notice(context: Context, text: String, appIcon: android.graphics.Bitmap? = null) {
            // A sheet or full-screen page (Settings) would cover Home's island, so those keep the toast.
            val covered = LauncherSheetsOpen.intValue > 0 || LauncherPagesOpen.intValue > 0
            if (noticeIslands > 0 && FolioForeground.visible.value && !covered) post(IslandEvent.Notice(text, appIcon))
            else android.widget.Toast.makeText(context, text, android.widget.Toast.LENGTH_SHORT).show()
        }
        /** Hides the pop-up now (swiped away); the notification itself stays in Notification Center. */
        fun dismiss() { mutable.value = null }
        /** Posted by the notification listener for new messages and notifications. */
        internal fun post(event: IslandEvent) {
            // Messages and device names are personal; Screenshot Mode keeps them off screen.
            if (ScreenshotMode.on.value && (event is IslandEvent.Message || event is IslandEvent.Bluetooth)) return
            mutable.value = event to System.currentTimeMillis()
        }
        @android.annotation.SuppressLint("StaticFieldLeak") // holds only the application context
        private var shared: IslandEvents? = null
        private var users = 0

        /** Reference-counted so Home and the everywhere overlay share one registration. */
        @Synchronized fun acquire(context: Context) {
            if (users++ == 0) shared = IslandEvents(context.applicationContext).also { it.register() }
        }
        @Synchronized fun release() {
            if (users > 0 && --users == 0) { shared?.unregister(); shared = null }
        }
    }

    /** Lifecycle-bound acquire/release for an activity. */
    class Observer(private val context: Context) : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) = acquire(context)
        override fun onStop(owner: LifecycleOwner) = release()
    }
}
