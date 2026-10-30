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
    val startedAtMs: Long,
)

/**
 * The one place Crackgate's state lives. The joke is drawn by the gestures service's overlay ([EverywhereOverlay]) while [shown] is
 * not null; this object starts and stops it. It is never saved: a restart of Folio or the service always begins with nothing showing.
 *
 * While a fault is up this listens for a shake (the accelerometer), for the screen turning off and for the ten minute limit; the
 * moment it is down it listens for nothing. [start] and [stop] can be called any number of times.
 */
/** Reduce Motion is on when Folio's own switch is or Android's Remove animations is. */
internal fun crackgateReduceMotion(folioSwitch: Boolean, androidRemovesAnimations: Boolean): Boolean = folioSwitch || androidRemovesAnimations

internal object CrackgateOwner {
    private val state = MutableStateFlow<CrackgateShown?>(null)
    val shown: StateFlow<CrackgateShown?> = state

    private var folioReduce = false
    private var androidReduce = false
    private val reduce = MutableStateFlow(false)

    /** Whether Reduce Motion is on right now, kept live while a fault is up: when true the fault takes no more fade and makes no more changes. */
    val reduceMotion: StateFlow<Boolean> = reduce

    /** Folio's own Reduce Motion switch, passed in by the overlay whenever settings change. */
    fun onFolioReduceMotion(on: Boolean) { folioReduce = on; reduce.value = crackgateReduceMotion(folioReduce, androidReduce) }

    private val animationScaleObserver = object : android.database.ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            val app = context ?: return
            androidReduce = reduceMotionEnabled(app)
            reduce.value = crackgateReduceMotion(folioReduce, androidReduce)
        }
    }

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
        folioReduce = runCatching {
            JSONObject(app.getSharedPreferences(SettingKeys.PREFS, 0).getString(SettingKeys.STATE, "{}") ?: "{}").optBoolean("reduceMotion", false)
        }.getOrDefault(false)
        androidReduce = reduceMotionEnabled(app)
        val reduce = crackgateReduceMotion(folioReduce, androidReduce)
        this.reduce.value = reduce
        this.context = app
        shake.reset()
        val now = SystemClock.elapsedRealtime()
        listen(app)
        state.value = CrackgateShown(randomCrackgateLook(random), planCrackgateGlitches(random, reduce, glitchy), now)
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
        runCatching { app.contentResolver.registerContentObserver(android.provider.Settings.Global.getUriFor(android.provider.Settings.Global.ANIMATOR_DURATION_SCALE), false, animationScaleObserver) }
    }

    private fun unlisten() {
        val app = context ?: return
        runCatching { app.getSystemService(SensorManager::class.java)?.unregisterListener(sensorListener) }
        runCatching { app.unregisterReceiver(screenOff) }
        runCatching { app.contentResolver.unregisterContentObserver(animationScaleObserver) }
        context = null
    }
}
