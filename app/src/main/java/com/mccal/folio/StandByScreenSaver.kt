package com.mccal.folio

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.provider.Settings
import android.service.dreams.DreamService
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import java.time.LocalDateTime
import kotlin.math.acos
import kotlin.math.sqrt

/**
 * StandBy as Android's screen saver: "Folio StandBy" in Settings › Display › Screen saver. Android starts it when the
 * screen turns off by itself on a charger and draws it over the lock screen, and the phone locks underneath as usual
 * (checked on a Galaxy Z Fold8, One UI 9, 1 Oct 2026). Swipes and taps reach it but Back doesn't, so picking the phone
 * up or swiping up from the bottom leaves it, onto the lock screen, as iPhone's StandBy does. It dims at night, and the
 * clock shifts a few pixels each minute so a clock left on all night doesn't burn in (after Google's Clock,
 * Apache-2.0). Compose needs the owners an activity would give it, so the service provides them, as StandBy-Android
 * (MIT) does.
 */
class StandByScreenSaver : DreamService(), LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    private val registry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry
    override val viewModelStore = ViewModelStore()

    // Made in onCreate: a service has no context while it's being constructed. The screen saver runs in Home's
    // process, so a crash here takes Home down with it; StandByScreenSaverTest builds the service to keep it honest.
    private lateinit var status: DeviceStatusMonitor
    private var sensors: SensorManager? = null
    private var dreaming = false
    private val pickUp = PickUpDetector()
    private val motion = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (pickUp.add(event.values[0], event.values[1], event.values[2], event.timestamp / 1_000_000)) leave()
        }
        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
    }

    override fun onCreate() {
        super.onCreate()
        status = DeviceStatusMonitor(this)
        savedState.performRestore(null)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        registry.addObserver(status)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = true
        isFullscreen = true
        isScreenBright = !isStandByNight(LocalDateTime.now())
        val content = ComposeView(this)
        listOfNotNull(content, window?.decorView).forEach {
            it.setViewTreeLifecycleOwner(this)
            it.setViewTreeSavedStateRegistryOwner(this)
            it.setViewTreeViewModelStoreOwner(this)
        }
        content.setContent { DuoTheme(dark = true) { ScreenSaverFace(status, onNight = { isScreenBright = !it }, onLeave = ::leave) } }
        setContentView(content)
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        dreaming = true
        registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        sensors = getSystemService(SensorManager::class.java)
        sensors?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { sensors?.registerListener(motion, it, SensorManager.SENSOR_DELAY_UI) }
    }

    override fun onDreamingStopped() {
        dreaming = false
        sensors?.unregisterListener(motion)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        super.onDreamingStopped()
    }

    override fun onDetachedFromWindow() {
        sensors?.unregisterListener(motion)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        viewModelStore.clear()
        super.onDetachedFromWindow()
    }

    /** Off to the lock screen, once. */
    private fun leave() {
        if (!dreaming) return
        dreaming = false
        wakeUp()
    }
}

@Composable
private fun ScreenSaverFace(monitor: DeviceStatusMonitor, onNight: (Boolean) -> Unit, onLeave: () -> Unit) {
    val status by monitor.state.collectAsState()
    val tick by rememberMinuteTick()
    val night = isStandByNight(displayNow(tick))
    LaunchedEffect(night) { onNight(night) }
    val (dx, dy) = burnInShift(tick)
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black).swipeUpToLeave(onLeave)) {
        StandByFace(status, stacked = maxHeight > maxWidth, Modifier.fillMaxSize().safeDrawingPadding().offset(dx.dp, dy.dp))
    }
}

/** A swipe up that starts near the bottom edge leaves, like the swipe up out of iPhone's StandBy. */
private fun Modifier.swipeUpToLeave(onLeave: () -> Unit) = pointerInput(Unit) {
    val travel = 80.dp.toPx()
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        while (true) {
            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
            if (isSwipeUpToLeave(down.position.y, change.position.y, size.height.toFloat(), travel)) { onLeave(); break }
            if (!change.pressed) break
        }
    }
}

/** Started in the bottom fifth of the screen and moved up at least [minTravel] pixels. */
internal fun isSwipeUpToLeave(startY: Float, y: Float, height: Float, minTravel: Float): Boolean =
    startY >= height * .8f && startY - y >= minTravel

/**
 * Where the clock sits this minute, in dp: a fixed walk around the middle, never more than 6 dp out, so no pixel shows
 * the same thing all night. The same for a given minute, so the screen saver and a check agree.
 */
internal fun burnInShift(timeMillis: Long): Pair<Int, Int> = BURN_IN_STEPS[((timeMillis / 60_000) % BURN_IN_STEPS.size).toInt()]

private val BURN_IN_STEPS = listOf(0 to 0, 4 to -3, -5 to 2, 3 to 5, -2 to -5, 6 to 1, -6 to -2, 1 to 6)

/**
 * Whether the phone has been picked up or turned since it came to rest: gravity, smoothed over a few readings, has
 * swung more than [degrees] from where it settled. A tap or a bump doesn't turn the phone, so it doesn't count.
 */
internal class PickUpDetector(private val degrees: Float = 35f, private val settleMs: Long = 1_000) {
    private val smooth = FloatArray(3)
    private var rest: FloatArray? = null
    private var firstMs = -1L

    fun add(x: Float, y: Float, z: Float, timeMs: Long): Boolean {
        if (firstMs < 0) { smooth[0] = x; smooth[1] = y; smooth[2] = z; firstMs = timeMs }
        else { smooth[0] += .3f * (x - smooth[0]); smooth[1] += .3f * (y - smooth[1]); smooth[2] += .3f * (z - smooth[2]) }
        val settled = rest ?: run {
            if (timeMs - firstMs >= settleMs) rest = smooth.copyOf()
            return false
        }
        return angleBetween(settled, smooth) > degrees
    }

    private fun angleBetween(a: FloatArray, b: FloatArray): Float {
        val la = sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2])
        val lb = sqrt(b[0] * b[0] + b[1] * b[1] + b[2] * b[2])
        if (la < 1f || lb < 1f) return 0f // falling or a bad reading: no direction to compare
        val cos = ((a[0] * b[0] + a[1] * b[1] + a[2] * b[2]) / (la * lb)).coerceIn(-1f, 1f)
        return Math.toDegrees(acos(cos).toDouble()).toFloat()
    }
}

/**
 * Whether Android's screen saver is on and set to one of this app's: [components] is the setting's comma-separated
 * list of "package/class" names. Compared by package, so Folio and Folio Dev don't count for each other.
 */
internal fun screenSaverIsFolio(enabled: Int, components: String?, packageName: String): Boolean =
    enabled == 1 && components.orEmpty().split(',').any { it.trim().substringBefore('/') == packageName }

/** Android lets apps read these two settings; anything it won't say counts as not chosen. */
internal fun folioScreenSaverChosen(context: Context): Boolean = runCatching {
    val resolver = context.contentResolver
    screenSaverIsFolio(Settings.Secure.getInt(resolver, "screensaver_enabled", 0),
        Settings.Secure.getString(resolver, "screensaver_components"), context.packageName)
}.getOrDefault(false)

/** Read again whenever Home comes back, as it does after choosing a screen saver in Android's settings. */
@Composable
internal fun rememberFolioScreenSaverChosen(): Boolean {
    val context = LocalContext.current
    val state by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    return remember(state) { folioScreenSaverChosen(context) }
}

/** Android's screen saver list (Settings › Display › Screen saver), or Display settings where a phone has no direct link. */
internal fun openScreenSaverSettings(context: Context) {
    listOf(Settings.ACTION_DREAM_SETTINGS, Settings.ACTION_DISPLAY_SETTINGS).firstOrNull { action ->
        runCatching { context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
    }
}

/**
 * Android lists Folio StandBy among its screen savers only once StandBy's charger ways are open (0.6.8, Folio Dev, or a
 * supporter's early access), so the list never offers what Folio's Settings doesn't show. The service is off in the
 * manifest; Home checks this as it starts, off the main thread, and writes only when it differs.
 */
internal fun syncStandByScreenSaver(context: Context, open: Boolean = FeatureGate.STANDBY_CHARGING.isOpen(context)) {
    val packages = context.packageManager
    val component = ComponentName(context, StandByScreenSaver::class.java)
    val want = if (open) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
    if (packages.getComponentEnabledSetting(component) != want) {
        runCatching { packages.setComponentEnabledSetting(component, want, PackageManager.DONT_KILL_APP) }
    }
}
