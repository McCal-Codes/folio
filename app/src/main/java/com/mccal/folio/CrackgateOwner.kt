package com.mccal.folio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import kotlin.math.sqrt
import kotlin.random.Random

/** One Crackgate fault that is up right now: what it looks like, the few slow changes planned for it, and when it began. */
internal data class CrackgateShown(
    val look: CrackgateLook,
    val plan: List<CrackgateGlitch>,
    val reduceMotion: Boolean,
    val startedAtMs: Long,
)

/**
 * The one place Crackgate's state lives. The joke is drawn by the gestures service's overlay ([EverywhereOverlay]) while [shown] is
 * not null; this object starts and stops it. It is never saved: a restart of Folio or the service always begins with nothing showing.
 *
 * While a fault is up this listens for a shake (the accelerometer), for the screen turning off and for the ten minute limit; the
 * moment it is down it listens for nothing. [start] and [stop] can be called any number of times.
 */
internal object CrackgateOwner {
    private val state = MutableStateFlow<CrackgateShown?>(null)
    val shown: StateFlow<CrackgateShown?> = state

    private val main = Handler(Looper.getMainLooper())
    private val shake = CrackgateShake()
    private var context: Context? = null

    private const val AUTO_CLEAR_CHECK_MS = 30_000L

    private val autoClear = object : Runnable {
        override fun run() {
            val current = state.value ?: return
            if (crackgateShouldClear(current.startedAtMs, SystemClock.elapsedRealtime())) stop() else main.postDelayed(this, AUTO_CLEAR_CHECK_MS)
        }
    }

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val v = event.values
            val g = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]) / SensorManager.GRAVITY_EARTH
            if (shake.onSample(event.timestamp / 1_000_000L, g)) stop()
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) { stop() }
    }

    /**
     * Shows the fault. False, and nothing shown, when the gate is shut, Safe Mode is on or the gestures service (which owns the window) is
     * not running. True when it is up, including when it already was.
     */
    fun start(context: Context, glitchy: Boolean, random: Random = Random.Default): Boolean {
        val app = context.applicationContext
        if (!FeatureGate.CRACKGATE.isOpen(app) || SafeMode.active || !SystemShadeAccessibilityService.isConnected()) return false
        if (state.value != null) return true
        val reduce = reduceMotionEnabled(app) || runCatching {
            JSONObject(app.getSharedPreferences(SettingKeys.PREFS, 0).getString(SettingKeys.STATE, "{}") ?: "{}").optBoolean("reduceMotion", false)
        }.getOrDefault(false)
        this.context = app
        shake.reset()
        val now = SystemClock.elapsedRealtime()
        listen(app)
        state.value = CrackgateShown(randomCrackgateLook(random), planCrackgateGlitches(random, reduce, glitchy), reduce, now)
        main.removeCallbacks(autoClear)
        main.postDelayed(autoClear, AUTO_CLEAR_CHECK_MS)
        return true
    }

    /** Takes the fault down and stops listening for anything. Does nothing when nothing is showing. */
    fun stop() {
        main.removeCallbacks(autoClear)
        unlisten()
        shake.reset()
        state.value = null
    }

    private fun listen(app: Context) {
        runCatching {
            val manager = app.getSystemService(SensorManager::class.java)
            manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { manager.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_GAME) }
        }
        runCatching { app.registerReceiver(screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF)) }
    }

    private fun unlisten() {
        val app = context ?: return
        runCatching { app.getSystemService(SensorManager::class.java)?.unregisterListener(sensorListener) }
        runCatching { app.unregisterReceiver(screenOff) }
        context = null
    }
}
