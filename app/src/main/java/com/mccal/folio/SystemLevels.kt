package com.mccal.folio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import kotlin.math.abs

/*
 * Control Center's volume and brightness sliders follow the phone, not only the finger: the side volume keys, the
 * system volume panel, a mute and an automatic brightness change all move them while the panel is open.
 *
 * Owner: SystemLevels reads the system and reports; one LiveLevel per slider decides what is shown. Nothing here
 * writes to the system (DeviceControls in TopPanels.kt does that), and nothing runs unless start() was called, which
 * the panel does while it is composed (DisposableEffect in ControlCenter) and undoes in onDispose.
 *
 * The volume slider is the music (media) stream, the one the side keys move while media plays, and what the
 * volume.* icon actions adjust.
 */

/** The slider position (0..1) for a stream volume between [min] and [max]; a muted stream reads as empty. */
internal fun volumeFraction(volume: Int, min: Int, max: Int, muted: Boolean): Float {
    val span = max - min
    if (muted || span <= 0) return 0f
    return ((volume - min).toFloat() / span).coerceIn(0f, 1f)
}

/** The stream volume for a slider position, the inverse of [volumeFraction] (rounded down, like a finger on a step). */
internal fun volumeStep(fraction: Float, min: Int, max: Int): Int =
    (min + (fraction.coerceIn(0f, 1f) * (max - min)).toInt()).coerceIn(min, maxOf(min, max))

/** The slider position for a raw SCREEN_BRIGHTNESS setting (0..255). */
internal fun brightnessFraction(raw: Int, max: Int = 255): Float = if (max <= 0) 0f else (raw.toFloat() / max).coerceIn(0f, 1f)

/**
 * One slider's value. The finger wins: while it is held, changes from outside are remembered and the latest one is
 * applied on release, so a slider never jumps from under a drag. Changes smaller than a step are ignored, so a burst of
 * identical readings (a key press fires several) redraws nothing.
 */
internal class LiveLevel(initial: Float) {
    var value by mutableFloatStateOf(initial.coerceIn(0f, 1f)); private set
    var held = false; private set
    private var pending: Float? = null

    /** The finger came down on the slider. */
    fun hold() { held = true; pending = null }

    /** The finger moved the slider. */
    fun fromUser(v: Float) { value = v.coerceIn(0f, 1f) }

    /** The system reports [v]. Returns true when the shown value changed. */
    fun fromOutside(v: Float): Boolean {
        val next = v.coerceIn(0f, 1f)
        if (held) { pending = next; return false }
        if (abs(next - value) < EPSILON) return false
        value = next
        return true
    }

    /** The finger lifted. Applies the latest outside value that arrived meanwhile and returns it, or null if none did. */
    fun release(): Float? {
        held = false
        val latest = pending
        pending = null
        if (latest != null && abs(latest - value) >= EPSILON) value = latest
        return latest
    }

    companion object {
        /** Below one step of the 0..255 brightness setting, so it is never a visible change. */
        const val EPSILON = 1f / 256f
    }
}

/** What one reading of the system says. */
internal data class SystemReading(val volume: Float, val brightness: Float, val brightnessManual: Boolean)

/**
 * Watches the music volume and the screen brightness and calls [onChange] on the main thread with a fresh reading.
 *
 * Why three sources: `android.media.VOLUME_CHANGED_ACTION` is what the side keys fire, but it is a hidden, undocumented
 * broadcast (not in the public AudioManager API), so no Android release promises it and One UI is not guaranteed to send
 * it. It is therefore only a hint to read sooner. The reading itself is always `AudioManager.getStreamVolume`, and a
 * slow poll (4 times a second) runs only while the panel is up and the screen is on, so the slider is right even if the
 * broadcast never comes. Brightness uses ContentObserver on the Settings.System values, which is public API.
 *
 * Bursts are coalesced: any number of hints before the main thread is free cause one reading.
 */
internal class SystemLevels(private val context: Context, private val onChange: (SystemReading) -> Unit) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val power = context.getSystemService(PowerManager::class.java)
    private val resolver = context.contentResolver
    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var polling = false
    private var queued = false

    private val refreshNow = Runnable { queued = false; if (running) onChange(read()) }
    private val tick = object : Runnable {
        override fun run() {
            if (!running || !polling) return
            onChange(read())
            handler.postDelayed(this, POLL_MS)
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> stopPolling()
                Intent.ACTION_SCREEN_ON -> { startPolling(); requestRefresh() }
                // Volume and mute broadcasts name a stream; only the music stream moves this slider.
                VOLUME_CHANGED_ACTION, STREAM_MUTE_CHANGED_ACTION ->
                    if (intent.getIntExtra(EXTRA_STREAM_TYPE, AudioManager.STREAM_MUSIC) == AudioManager.STREAM_MUSIC) requestRefresh()
                else -> requestRefresh() // ringer mode changed
            }
        }
    }

    private val observer = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) { requestRefresh() }
    }

    /** One reading of the system, now. */
    fun read(): SystemReading {
        val volume = runCatching {
            volumeFraction(audio.getStreamVolume(AudioManager.STREAM_MUSIC), audio.getStreamMinVolume(AudioManager.STREAM_MUSIC),
                audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC), audio.isStreamMute(AudioManager.STREAM_MUSIC))
        }.getOrDefault(0f)
        val brightness = runCatching { brightnessFraction(Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS)) }.getOrDefault(.5f)
        val manual = runCatching {
            Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE) == Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
        }.getOrDefault(true)
        return SystemReading(volume, brightness, manual)
    }

    /** Asks for a reading soon; many asks before it happens make one. */
    fun requestRefresh() {
        if (!running || queued) return
        queued = true
        handler.post(refreshNow)
    }

    fun start() {
        if (running) return
        running = true
        // Not exported: the hidden volume actions are not on every release's protected list, and Android 14 refuses an
        // unflagged receiver for those. The system can still reach a not-exported receiver. If this fails, the poll covers it.
        runCatching {
            androidx.core.content.ContextCompat.registerReceiver(context, receiver, IntentFilter().apply {
                addAction(VOLUME_CHANGED_ACTION); addAction(STREAM_MUTE_CHANGED_ACTION); addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
                addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_SCREEN_OFF)
            }, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
        }
        runCatching {
            resolver.registerContentObserver(Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS), false, observer)
            resolver.registerContentObserver(Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS_MODE), false, observer)
        }
        if (power?.isInteractive != false) startPolling()
    }

    /** Removes every receiver, observer and pending callback. Safe to call twice. */
    fun stop() {
        if (!running) return
        running = false
        stopPolling()
        handler.removeCallbacks(refreshNow)
        queued = false
        runCatching { context.unregisterReceiver(receiver) }
        runCatching { resolver.unregisterContentObserver(observer) }
    }

    private fun startPolling() {
        if (polling) return
        polling = true
        handler.postDelayed(tick, POLL_MS)
    }

    private fun stopPolling() {
        polling = false
        handler.removeCallbacks(tick)
    }

    companion object {
        /** Hidden in AudioManager, but the string has been stable since API 1; see the class comment. */
        const val VOLUME_CHANGED_ACTION = "android.media.VOLUME_CHANGED_ACTION"
        const val STREAM_MUTE_CHANGED_ACTION = "android.media.STREAM_MUTE_CHANGED_ACTION"
        const val EXTRA_STREAM_TYPE = "android.media.EXTRA_VOLUME_STREAM_TYPE"
        const val POLL_MS = 250L
    }
}
