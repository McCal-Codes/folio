package com.mccal.folio

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Clean screens for sharing, like Apple's 9:41 product shots, but for anyone: Folio's own clocks show 9:41, its Side
 * Bar shows full battery and signal, and notifications, media, messages, device names, calendar events and alarms
 * are hidden. Android's own screens aren't changed. Kept in memory only and turns itself off after 30 minutes, so
 * nobody is left looking at a stopped clock.
 */
object ScreenshotMode {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutable = MutableStateFlow(false)
    val on: StateFlow<Boolean> = mutable.asStateFlow()
    private var timer: Job? = null
    const val AUTO_OFF_MS = 30 * 60 * 1000L

    @Synchronized fun set(enabled: Boolean) {
        timer?.cancel()
        mutable.value = enabled
        if (enabled) timer = scope.launch { delay(AUTO_OFF_MS); mutable.value = false }
    }

    /** The time Folio's clocks show. */
    fun now(screenshot: Boolean = on.value): LocalDateTime = if (screenshot) LocalDate.now().atTime(9, 41) else LocalDateTime.now()

    fun status(real: DeviceStatus, screenshot: Boolean = on.value): DeviceStatus =
        if (screenshot) DeviceStatus(battery = 100, wifiConnected = true, wifiLevel = 4, cellularLevel = 4) else real

    /** [source] as is, or [hidden] while Screenshot Mode is on. */
    fun <T> hide(source: StateFlow<T>, hidden: T): StateFlow<T> =
        combine(source, on) { value, screenshot -> if (screenshot) hidden else value }.stateIn(scope, SharingStarted.Eagerly, source.value)
}

/** The time a Folio clock shows, refreshed with [tick] and right away when Screenshot Mode changes. */
@Composable
internal fun displayNow(tick: Any?): LocalDateTime {
    val screenshot by ScreenshotMode.on.collectAsStateWithLifecycle()
    return remember(tick, screenshot) { ScreenshotMode.now(screenshot) }
}
