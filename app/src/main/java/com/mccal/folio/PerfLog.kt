package com.mccal.folio

import android.app.Activity
import android.app.Application
import android.content.BroadcastReceiver
import android.content.ComponentCallbacks
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Debug
import android.os.Handler
import android.os.HandlerThread
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import android.view.FrameMetrics
import android.view.Window
import androidx.core.content.ContextCompat
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the Performance card shows: whether a run is on, how far along, and whether there is a report to copy or share. */
internal data class PerfStatus(val running: Boolean = false, val readings: Int = 0, val elapsedMs: Long = 0, val hasReport: Boolean = false)

/**
 * The Performance log (Settings › Advanced › Diagnostics): off until you start it, it reads Folio's CPU time, memory,
 * threads, the battery and the frame times every few seconds, and writes one plain-text report. Nothing is sent
 * anywhere and it asks for no permission; the numbers stay in memory until you copy or share them. It stops itself
 * after [PerfMath.LIMIT_MS].
 *
 * It costs next to nothing: one reading is a few file and service reads on a thread of its own, memory (the dear one)
 * is read every sixth time, and the frame callback only increments counters.
 *
 * Fold marker: [noteFold] counts a fold or unfold. With no hook in the fold timeline, a change of screen size large
 * enough to be a fold counts too ([PerfContext.isFold]); the timeline's owner can call [noteFold] when an animation
 * starts to count the animations themselves.
 */
internal object PerfLog {
    private val mutable = MutableStateFlow(PerfStatus())
    val status: StateFlow<PerfStatus> = mutable.asStateFlow()

    private var app: Application? = null
    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private var startedReal = 0L
    private var startedAtText = ""
    private var tick = 0
    private val samples = ArrayList<PerfSample>()
    @Volatile private var histogram = FrameHistogram()
    @Volatile private var scenarios = PerfScenarios()
    private var clock = PerfContext({ SystemClock.elapsedRealtime() })
    private var header: PerfHeader? = null

    // Written by the main thread and the system's broadcasts, read by the sampling thread.
    @Volatile private var batteryIntent: Intent? = null
    @Volatile private var fallbackBudgetNs = 16_666_667L
    @Volatile private var lastSmallestWidthDp = 0
    private var resumed: Activity? = null
    private var attachedTo: Window? = null
    // Last memory reading, carried over to the samples that skip it.
    private var mem = IntArray(3)

    /** Starts a run. [front] is the activity on screen right now, if there is one, so its frames count from the first. */
    @Synchronized fun start(appContext: Context, front: Activity? = null) {
        if (mutable.value.running) return
        val application = appContext.applicationContext as Application
        app = application
        samples.clear(); histogram = FrameHistogram(); scenarios = PerfScenarios(); tick = 0; mem = IntArray(3)
        startedReal = SystemClock.elapsedRealtime()
        startedAtText = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
        val power = application.getSystemService(PowerManager::class.java)
        clock = PerfContext({ SystemClock.elapsedRealtime() }, frontNow = front != null, screenNow = power?.isInteractive ?: true)
        lastSmallestWidthDp = application.resources.configuration.smallestScreenWidthDp
        header = PerfHeader(
            runCatching { application.packageManager.getPackageInfo(application.packageName, 0).versionName }.getOrNull().orEmpty(),
            "${Build.MANUFACTURER} ${Build.MODEL}", Build.VERSION.RELEASE,
            Diagnostics.screenSummary(application).replace('\n', ';'), startedAtText, running = true)

        val t = HandlerThread("folio-perf", Process.THREAD_PRIORITY_BACKGROUND).also { it.start() }
        thread = t; handler = Handler(t.looper)
        batteryIntent = ContextCompat.registerReceiver(application, receiver, IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED); addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_SCREEN_OFF)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
        batteryIntent?.let { clock.setCharging(it.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0) }
        application.registerActivityLifecycleCallbacks(lifecycle)
        application.registerComponentCallbacks(configuration)
        front?.let { attach(it) }
        mutable.value = PerfStatus(running = true)
        handler?.post(sampler)
    }

    @Synchronized fun stop() {
        if (!mutable.value.running) return
        val application = app ?: return
        handler?.removeCallbacksAndMessages(null)
        takeSample(application) // the last reading, so the report ends where the run did
        detach()
        runCatching { application.unregisterReceiver(receiver) }
        application.unregisterActivityLifecycleCallbacks(lifecycle)
        application.unregisterComponentCallbacks(configuration)
        thread?.quitSafely(); thread = null; handler = null
        header = header?.copy(running = false)
        mutable.value = PerfStatus(running = false, readings = samples.size, elapsedMs = samples.lastOrNull()?.atMs ?: 0, hasReport = samples.size >= 2)
    }

    /** Counts a fold, unfold or fold animation in the report's context line. */
    fun noteFold() = clock.noteFold()

    /** The start and end of something whose smoothness is worth knowing on its own: the frames drawn in between are counted for it too. Cheap and safe to call when no run is going. */
    fun begin(s: PerfScenario) = scenarios.begin(s)
    fun end(s: PerfScenario) = scenarios.end(s)

    /**
     * The report as a plain-text share: only EXTRA_TEXT. No file, no stream and no selector, so the chooser lists every
     * app that takes text (a mail selector in the chooser once made "Email a Report" find no app).
     */
    fun shareIntent(text: String, title: String): Intent =
        Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), title)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** The report so far (or the last finished one), as plain text. */
    @Synchronized fun reportText(): String {
        val h = header ?: return "Folio performance report\nNo recording has been made yet.\n"
        return PerfReport.build(h, samples.toList(), histogram, scenarios)
    }

    private val sampler = object : Runnable {
        override fun run() {
            val application = app ?: return
            synchronized(PerfLog) {
                if (!mutable.value.running) return
                takeSample(application)
                if (PerfMath.shouldStop(SystemClock.elapsedRealtime() - startedReal)) { stop(); return }
            }
            handler?.postDelayed(this, PerfMath.INTERVAL_MS)
        }
    }

    @Synchronized private fun takeSample(application: Context) {
        val now = SystemClock.elapsedRealtime()
        if (PerfMath.readsMemory(tick++)) {
            val info = Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }
            mem = intArrayOf(info.totalPss, info.nativePss, info.dalvikPss)
        }
        val runtime = Runtime.getRuntime()
        val battery = batteryIntent
        val manager = application.getSystemService(BatteryManager::class.java)
        val (front, screen, charging) = clock.totals()
        samples += PerfSample(
            atMs = now - startedReal, cpuMs = Process.getElapsedCpuTime(),
            pssKb = mem[0], nativePssKb = mem[1], javaPssKb = mem[2], javaHeapKb = ((runtime.totalMemory() - runtime.freeMemory()) / 1024).toInt(),
            threads = threadCount(),
            batteryPercent = battery?.let { level(it) } ?: manager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1,
            chargeUah = manager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)?.takeIf { it != Int.MIN_VALUE } ?: 0,
            currentUa = manager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)?.takeIf { it != Int.MIN_VALUE } ?: 0,
            status = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, 0) ?: 0,
            plugged = battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0,
            tempTenthsC = battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0,
            frames = histogram.frames, janky = histogram.janky,
            frontMs = front, screenOnMs = screen, chargingMs = charging, folds = clock.folds)
        mutable.value = PerfStatus(running = true, readings = samples.size, elapsedMs = now - startedReal, hasReport = samples.size >= 2)
    }

    private fun level(i: Intent): Int {
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1); val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        return if (level < 0 || scale <= 0) -1 else level * 100 / scale
    }

    private fun threadCount(): Int = runCatching {
        File("/proc/self/status").useLines { lines -> lines.firstOrNull { it.startsWith("Threads:") }?.substringAfter(':')?.trim()?.toInt() }
    }.getOrNull() ?: -1

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_BATTERY_CHANGED -> { batteryIntent = intent; clock.setCharging(intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0) }
                Intent.ACTION_SCREEN_ON -> clock.setScreen(true)
                Intent.ACTION_SCREEN_OFF -> clock.setScreen(false)
            }
        }
    }

    private val lifecycle = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityResumed(activity: Activity) { attach(activity) }
        override fun onActivityPaused(activity: Activity) { if (resumed === activity) detach() }
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
        override fun onActivityStarted(activity: Activity) {}
        override fun onActivityStopped(activity: Activity) {}
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
        override fun onActivityDestroyed(activity: Activity) {}
    }

    private val configuration = object : ComponentCallbacks {
        override fun onConfigurationChanged(newConfig: Configuration) {
            val now = newConfig.smallestScreenWidthDp
            if (PerfContext.isFold(lastSmallestWidthDp, now)) clock.noteFold()
            if (now > 0) lastSmallestWidthDp = now
        }
        @Deprecated("Deprecated in Java") override fun onLowMemory() {}
    }

    @Synchronized private fun attach(activity: Activity) {
        detach()
        resumed = activity
        clock.setFront(true)
        // The screen's refresh rate as the fallback deadline where the system gives a frame none.
        val hz = activity.display?.refreshRate ?: 60f
        fallbackBudgetNs = (1_000_000_000L / hz.coerceAtLeast(30f)).toLong()
        attachedTo = activity.window.also { it.addOnFrameMetricsAvailableListener(frames, handler) }
    }

    @Synchronized private fun detach() {
        attachedTo?.let { runCatching { it.removeOnFrameMetricsAvailableListener(frames) } }
        attachedTo = null
        if (resumed != null) clock.setFront(false)
        resumed = null
    }

    /** Called for every frame drawn: counts it and nothing more, so it makes no garbage. */
    private val frames = Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
        if (metrics.getMetric(FrameMetrics.FIRST_DRAW_FRAME) == 1L) return@OnFrameMetricsAvailableListener
        val total = metrics.getMetric(FrameMetrics.TOTAL_DURATION)
        val janky = PerfMath.isJank(total, metrics.getMetric(FrameMetrics.DEADLINE), fallbackBudgetNs)
        histogram.record(total, janky)
        scenarios.record(total, janky)
    }
}
