package com.mccal.folio

import android.content.Context
import androidx.compose.runtime.*
import java.time.*
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

enum class AppearanceMode { LIGHT, DARK, SYSTEM, SUNRISE_SUNSET }
data class AppearanceState(val mode: AppearanceMode = AppearanceMode.LIGHT, val accent: AccentChoice = AccentChoice.FOLIO_TEAL, val place: String = "",
    val latitude: Double? = null, val longitude: Double? = null, val locationTime: Long = 0,
    val deviceLocation: Boolean = false, val dark: Boolean = false, val fallback: String? = null,
    val locationStatus: String? = null)

class AppearanceStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)
    private fun currentSystemDark() = context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
        android.content.res.Configuration.UI_MODE_NIGHT_YES
    var state by mutableStateOf(load(currentSystemDark())); private set
    init { DuoAppearanceRuntime.dark = state.dark; DuoAppearanceRuntime.accent = state.accent }
    private fun load(systemDark: Boolean): AppearanceState {
        val mode = runCatching { AppearanceMode.valueOf(prefs.getString("mode", "LIGHT")!!) }.getOrDefault(AppearanceMode.LIGHT)
        val accent = runCatching { AccentChoice.valueOf(prefs.getString("accent", "FOLIO_TEAL")!!) }.getOrDefault(AccentChoice.FOLIO_TEAL)
        val lat = runCatching { prefs.getString("lat", null)?.toDoubleOrNull() }.getOrNull()
        val lon = runCatching { prefs.getString("lon", null)?.toDoubleOrNull() }.getOrNull()
        return resolve(AppearanceState(mode, accent, runCatching { prefs.getString("place", "") ?: "" }.getOrDefault(""), lat, lon,
            runCatching { prefs.getLong("locationTime", 0) }.getOrDefault(0),
            runCatching { prefs.getBoolean("deviceLocation", false) }.getOrDefault(false)), systemDark)
    }
    fun setMode(mode: AppearanceMode, systemDark: Boolean) { save(state.copy(mode = mode), systemDark) }
    fun setAccent(accent: AccentChoice, systemDark: Boolean) { save(state.copy(accent = accent), systemDark) }
    fun setManual(place: String, latitude: Double, longitude: Double, systemDark: Boolean) {
        require(latitude in -90.0..90.0 && longitude in -180.0..180.0)
        save(state.copy(place = place.trim(), latitude = latitude, longitude = longitude,
            locationTime = System.currentTimeMillis(), deviceLocation = false), systemDark)
    }
    fun setDeviceLocation(latitude: Double, longitude: Double, systemDark: Boolean) = save(state.copy(
        place = "Approximate device location", latitude = latitude, longitude = longitude,
        locationTime = System.currentTimeMillis(), deviceLocation = true, locationStatus = null), systemDark)
    fun locationStatus(message: String?) { state = state.copy(locationStatus = message) }
    fun clearLocation(systemDark: Boolean) = save(state.copy(place = "", latitude = null, longitude = null,
        locationTime = 0, deviceLocation = false), systemDark)
    fun refresh(systemDark: Boolean) { state = resolve(state, systemDark); DuoAppearanceRuntime.dark = state.dark }
    fun reloadFromPreferences(systemDark: Boolean = currentSystemDark()) {
        val transientStatus = state.locationStatus
        state = load(systemDark).copy(locationStatus = transientStatus)
        DuoAppearanceRuntime.dark = state.dark
    }
    private fun save(value: AppearanceState, systemDark: Boolean) {
        prefs.edit().putString("mode", value.mode.name).putString("accent", value.accent.name).putString("place", value.place)
            .putString("lat", value.latitude?.toString()).putString("lon", value.longitude?.toString())
            .putLong("locationTime", value.locationTime).putBoolean("deviceLocation", value.deviceLocation).apply()
        state = resolve(value, systemDark)
        DuoAppearanceRuntime.dark = state.dark
        DuoAppearanceRuntime.accent = state.accent
    }
    private fun resolve(value: AppearanceState, systemDark: Boolean): AppearanceState = when (value.mode) {
        AppearanceMode.LIGHT -> value.copy(dark = false, fallback = null)
        AppearanceMode.DARK -> value.copy(dark = true, fallback = null)
        AppearanceMode.SYSTEM -> value.copy(dark = systemDark, fallback = null)
        AppearanceMode.SUNRISE_SUNSET -> {
            val lat = value.latitude; val lon = value.longitude
            if (lat == null || lon == null) value.copy(dark = systemDark, fallback = "Using system theme until a location is set")
            else if (value.deviceLocation && System.currentTimeMillis() - value.locationTime > 30L * 24 * 60 * 60 * 1000)
                value.copy(dark = systemDark, fallback = "Using system theme because the device location is stale")
            else runCatching { val now = ZonedDateTime.now(); value.copy(dark = solarSchedule(now.toLocalDate(), lat, lon, now.zone).isDark(now), fallback = null) }
                .getOrElse { value.copy(dark = systemDark, fallback = "Using system theme because this location is unavailable") }
        }
    }
}

object DuoAppearanceRuntime {
    @Volatile var dark: Boolean = false
    /** Mirrors the saved accent, so a service or an overlay outside the theme can draw with it too. */
    @Volatile var accent: AccentChoice = AccentChoice.FOLIO_TEAL
}

data class DuoPalette(val ink: androidx.compose.ui.graphics.Color, val glass: androidx.compose.ui.graphics.Color,
    val backgroundTop: androidx.compose.ui.graphics.Color, val backgroundBottom: androidx.compose.ui.graphics.Color, val dark: Boolean)
val LightDuoPalette = DuoPalette(androidx.compose.ui.graphics.Color(0xFF243A46), androidx.compose.ui.graphics.Color(0xFFE8EFF2),
    androidx.compose.ui.graphics.Color(0xFF41687E), androidx.compose.ui.graphics.Color(0xFFD8CEB6), false)
val DarkDuoPalette = DuoPalette(androidx.compose.ui.graphics.Color(0xFFEAF3F6), androidx.compose.ui.graphics.Color(0xFF263A43),
    androidx.compose.ui.graphics.Color(0xFF132832), androidx.compose.ui.graphics.Color(0xFF463F35), true)
val LocalDuoPalette = staticCompositionLocalOf { LightDuoPalette }

@Composable
fun rememberSavedAppearance(): AppearanceState {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val store = remember(context) { AppearanceStore(context.applicationContext) }
    DisposableEffect(context, store, lifecycleOwner) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) { store.reloadFromPreferences() }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK); addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED); addAction(Intent.ACTION_DATE_CHANGED)
        }
        var registered = false
        fun register() { if (!registered) {
            ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            registered = true; store.reloadFromPreferences()
        } }
        fun unregister() { if (registered) { runCatching { context.unregisterReceiver(receiver) }; registered = false } }
        val observer = LifecycleEventObserver { _, event -> when (event) {
            Lifecycle.Event.ON_START -> register()
            Lifecycle.Event.ON_STOP -> unregister()
            else -> Unit
        } }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) register()
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer); unregister() }
    }
    return store.state
}
