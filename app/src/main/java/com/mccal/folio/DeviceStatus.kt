package com.mccal.folio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.SupplicantState
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.provider.Settings
import android.telephony.SignalStrength
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class DeviceStatus(
    val battery: Int? = null,
    val charging: Boolean = false,
    /** Remaining charge time in milliseconds from [BatteryManager.computeChargeTimeRemaining], or null when unknown/-1. */
    val chargeFullInMs: Long? = null,
    /** Plugged in at a battery-protection limit (e.g. holding at 80% without actively charging). */
    val chargeOnHold: Boolean = false,
    val wifiConnected: Boolean = false,
    val wifiLevel: Int? = null,
    /** The joined network's name, when Android hands it over (it needs the location permission, which Folio does not ask for); null otherwise. */
    val wifiName: String? = null,
    val cellularLevel: Int? = null,
    /** The network the phone is on (its carrier), when there is service; null otherwise. Needs no permission. */
    val carrierName: String? = null,
    val airplane: Boolean = false,
    /** Ringer on silent or vibrate. */
    val silent: Boolean = false,
)

/** A network name from [WifiInfo.getSsid] without its quotes, or null for a blank one or the placeholder Android gives when the name is hidden. */
internal fun wifiNameOrNull(raw: String?): String? =
    raw?.trim()?.removeSurrounding("\"")?.trim()?.takeIf { it.isNotEmpty() && it != WifiManager.UNKNOWN_SSID }

/**
 * Charging as Home shows it: on the cable. At a charge limit, like Samsung's battery protection holding at 80%, Android
 * reports the battery as not charging while the phone is still plugged in, and Home used to show it as on battery
 * (StandBy dropping out with it). Plugged in counts now, as iOS shows the bolt while charging is on hold.
 */
internal fun isCharging(status: Int, plugged: Int): Boolean =
    plugged != 0 || status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

/** Plugged in, below 100%, and Android says not charging: battery protection holding at a limit (e.g. 80%). */
internal fun isChargeOnHold(status: Int, plugged: Int, batteryPercent: Int?): Boolean =
    plugged != 0 &&
        status != BatteryManager.BATTERY_STATUS_CHARGING &&
        status != BatteryManager.BATTERY_STATUS_FULL &&
        batteryPercent != null &&
        batteryPercent in 1..99

/** Observe only while visible. No location, phone-state, or notification access required. */
class DeviceStatusMonitor(private val context: Context) : DefaultLifecycleObserver {
    private val connection = context.getSystemService(ConnectivityManager::class.java)
    private val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
    private val phone = context.getSystemService(TelephonyManager::class.java)
    private val batteryManager = context.getSystemService(BatteryManager::class.java)
    private val mutable = MutableStateFlow(DeviceStatus())
    val state = mutable.asStateFlow()
    private var networkRegistered = false
    private var phoneRegistered = false
    private var receiverRegistered = false
    // Android hands out the network's name only to a callback that asks for location info, and every update that callback gets counts as
    // a read of the phone's location (One UI shows its blue "location in use" dot for it, about every 20 seconds as the signal moved).
    // So the everyday callback asks for nothing, and a second one that does is registered only until the name is known: once per Wi-Fi
    // network, not on every signal change.
    @Volatile private var networkCaps: NetworkCapabilities? = null
    @Volatile private var cachedWifiName: String? = null
    private var nameRegistered = false
    private val nameLock = Any()
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        // A new default network (another Wi-Fi, or back from mobile data) has a name of its own to look up.
        override fun onAvailable(network: Network) { forgetWifiName(); updateConnection() }
        override fun onLost(network: Network) { networkCaps = null; forgetWifiName(); updateConnection() }
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            networkCaps = capabilities
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) { if (cachedWifiName == null) askForWifiName() } else forgetWifiName()
            updateConnection()
        }
    }
    private val nameCallback = object : ConnectivityManager.NetworkCallback(FLAG_INCLUDE_LOCATION_INFO) {
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            val name = wifiNameOrNull((capabilities.transportInfo as? WifiInfo)?.ssid) ?: return
            synchronized(nameLock) {
                cachedWifiName = name
                // Known: stop being told about every signal change, so the location is not read again until the network changes.
                if (nameRegistered) { runCatching { connection.unregisterNetworkCallback(this) }; nameRegistered = false }
            }
            updateConnection()
        }
    }
    private fun askForWifiName() = synchronized(nameLock) {
        if (!nameRegistered) nameRegistered = runCatching { connection.registerDefaultNetworkCallback(nameCallback); true }.getOrDefault(false)
    }
    private fun forgetWifiName() = synchronized(nameLock) {
        cachedWifiName = null
        if (nameRegistered) { runCatching { connection.unregisterNetworkCallback(nameCallback) }; nameRegistered = false }
    }
    private fun carrierOrNull(): String? = runCatching { phone.networkOperatorName }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() }
    private val phoneCallback = object : TelephonyCallback(), TelephonyCallback.SignalStrengthsListener {
        override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
            mutable.update { it.copy(cellularLevel = signalStrength.level.coerceIn(0, 4), carrierName = carrierOrNull()) }
        }
    }
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_BATTERY_CHANGED) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                val charge = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
                val pct = if (level >= 0 && scale > 0) (level * 100 / scale).coerceIn(0, 100) else null
                val chargingNow = isCharging(charge, plugged)
                val onHold = isChargeOnHold(charge, plugged, pct)
                val remainingMs = if (chargingNow && !onHold) {
                    runCatching { batteryManager?.computeChargeTimeRemaining() }.getOrNull()?.takeIf { it > 0L }
                } else null
                mutable.update {
                    it.copy(
                        battery = pct,
                        charging = chargingNow,
                        chargeFullInMs = remainingMs,
                        chargeOnHold = onHold,
                    )
                }
            }
            updateConnection()
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        val sticky = context.registerReceiver(receiver, IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED); addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED)
            addAction(WifiManager.RSSI_CHANGED_ACTION); addAction(android.media.AudioManager.RINGER_MODE_CHANGED_ACTION)
        })
        receiverRegistered = true
        if (sticky != null) {
            receiver.onReceive(context, sticky)
        }
        networkRegistered = runCatching { connection.registerDefaultNetworkCallback(networkCallback); true }.getOrDefault(false)
        phoneRegistered = runCatching { phone.registerTelephonyCallback(context.mainExecutor, phoneCallback); true }.getOrDefault(false)
        mutable.update { it.copy(cellularLevel = runCatching { phone.signalStrength?.level }.getOrNull(), carrierName = carrierOrNull()) }
        updateConnection()
    }

    @Suppress("DEPRECATION")
    private fun updateConnection() {
        val caps = networkCaps ?: runCatching { connection.getNetworkCapabilities(connection.activeNetwork) }.getOrNull()
        val info = (caps?.transportInfo as? WifiInfo) ?: runCatching { wifi.connectionInfo }.getOrNull()
        val connected = info?.supplicantState == SupplicantState.COMPLETED || caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val level = info?.rssi?.takeIf { connected && it > -127 }?.let { WifiManager.calculateSignalLevel(it, 5) }
        val airplane = Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1
        val silent = runCatching { context.getSystemService(android.media.AudioManager::class.java).ringerMode != android.media.AudioManager.RINGER_MODE_NORMAL }
            .getOrDefault(false)
        mutable.update { it.copy(wifiConnected = connected, wifiLevel = level, wifiName = if (connected) cachedWifiName else null, airplane = airplane, silent = silent) }
    }

    override fun onStop(owner: LifecycleOwner) {
        if (networkRegistered) runCatching { connection.unregisterNetworkCallback(networkCallback) }
        forgetWifiName()
        if (phoneRegistered) runCatching { phone.unregisterTelephonyCallback(phoneCallback) }
        if (receiverRegistered) runCatching { context.unregisterReceiver(receiver) }
        networkRegistered = false; phoneRegistered = false; receiverRegistered = false
    }
}
