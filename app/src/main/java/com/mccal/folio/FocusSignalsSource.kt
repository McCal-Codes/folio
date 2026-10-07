package com.mccal.folio

import android.app.Activity
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
import androidx.core.content.ContextCompat
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Output devices that count as "headphones" for a Focus trigger: wired and Bluetooth headsets and speakers, not the phone's own. */
internal fun isHeadphoneType(type: Int) = type in setOf(
    AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_USB_HEADSET,
    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER, AudioDeviceInfo.TYPE_HEARING_AID,
)

/**
 * How the screen is folded. Only a phone that folds answers: the cover screen has no fold in its window, the unfolded one
 * has a flat fold, and a half-open one is a tent. A phone that never reports a fold (and has no hinge sensor) answers null.
 */
internal fun foldStateOf(hasFold: Boolean, halfOpened: Boolean, foldable: Boolean): FoldState? = when {
    hasFold -> if (halfOpened) FoldState.TENT else FoldState.UNFOLDED
    foldable -> FoldState.COVER
    else -> null
}

/**
 * What the phone is doing, as one flow of [FocusSignals] for [FocusTriggers]. Local only and no permissions: window
 * layout for folding, the power broadcasts for charging and the audio device list for headphones. Collect it only while a
 * Focus has a trigger, so nothing is registered otherwise.
 */
internal fun focusSignals(activity: Activity): Flow<FocusSignals> {
    val context = activity.applicationContext
    val prefs = context.getSharedPreferences("focus_rules", 0)
    val hinge = context.packageManager.hasSystemFeature("android.hardware.sensor.hinge_angle")

    val fold = WindowInfoTracker.getOrCreate(activity).windowLayoutInfo(activity).map { info ->
        val folds = info.displayFeatures.filterIsInstance<FoldingFeature>()
        // Once a fold has been seen this is a foldable, even if it doesn't declare the hinge sensor.
        if (folds.isNotEmpty()) prefs.edit().putBoolean("sawFold", true).apply()
        foldStateOf(folds.isNotEmpty(), folds.any { it.state == FoldingFeature.State.HALF_OPENED }, hinge || prefs.getBoolean("sawFold", false))
    }.distinctUntilChanged()

    val charging = callbackFlow {
        fun now() = context.getSystemService(BatteryManager::class.java).isCharging
        trySend(now())
        val receiver = object : BroadcastReceiver() { override fun onReceive(c: Context, i: Intent) { trySend(now()) } }
        val filter = IntentFilter().apply { addAction(Intent.ACTION_POWER_CONNECTED); addAction(Intent.ACTION_POWER_DISCONNECTED) }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        awaitClose { context.unregisterReceiver(receiver) }
    }.distinctUntilChanged()

    val headphones = callbackFlow {
        val audio = context.getSystemService(AudioManager::class.java)
        fun now() = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { isHeadphoneType(it.type) }
        trySend(now())
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>) { trySend(now()) }
            override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>) { trySend(now()) }
        }
        audio.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
        awaitClose { audio.unregisterAudioDeviceCallback(callback) }
    }.distinctUntilChanged()

    return combine(fold, charging, headphones) { f, c, h -> FocusSignals(f, c, h) }.distinctUntilChanged()
}
