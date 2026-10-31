package com.mccal.folio

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Whether the Google app is willing to serve Discover to Folio, as last asked ([DiscoverProbe]) and remembered, so Home starts with the answer
 * already known and the page left of Home does not change shape a moment after launch. When Google refuses, that page shows Today View in
 * place of an error: a swipe right should always land somewhere useful. Asked again at every launch and whenever the choice changes, so a
 * Google app that starts serving Folio again brings Discover back.
 */
internal object DiscoverAvailability {
    private const val KEY = "discoverRefused"
    private val state = MutableStateFlow(false)
    val refused: StateFlow<Boolean> = state

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("folio_discover", Context.MODE_PRIVATE)

    /** Reads the remembered answer into [refused] and returns it. */
    fun load(context: Context): Boolean = prefs(context).getBoolean(KEY, false).also { state.value = it }

    suspend fun refresh(context: Context) {
        val nowRefused = !DiscoverProbe.reachable(context)
        if (nowRefused != state.value) {
            state.value = nowRefused
            prefs(context).edit().putBoolean(KEY, nowRefused).apply()
        }
    }
}
