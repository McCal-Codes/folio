package com.mccal.folio

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.Executor

/**
 * Whether Android's own device state says the phone is standing as a tent. Android's public window API never reports a tent (a Fold8
 * shows "no folding feature" in that pose), but the framework's device-state service does, by name, and let an ordinary app
 * listen to it (measured on a Fold8, 6 Oct 2026). That service is not part of the SDK, so this is a guarded extra: any failure reads
 * as "can't tell" and StandBy goes on with the hinge and motion signals it always had.
 */
internal object DeviceStateTent {
    /** True for a state named TENT, false for any other named state, null when [state] says nothing readable. */
    fun isTent(state: Any?): Boolean? {
        val name = NAME.find(state?.toString() ?: return null)?.groupValues?.get(1) ?: return null
        return name.equals("TENT", ignoreCase = true)
    }

    private val NAME = Regex("name='([A-Za-z_]+)'")

    /**
     * What the framework's callback object does when it is called: a device-state change reports [onTent]; the three Object methods
     * a proxy must answer get ordinary answers; everything else (the supported-state list) is ignored.
     */
    fun invoke(onTent: (Boolean?) -> Unit, proxy: Any, method: String, args: Array<out Any?>?): Any? = when (method) {
        "onDeviceStateChanged" -> { onTent(isTent(args?.firstOrNull())); null }
        "equals" -> proxy === args?.firstOrNull()
        "hashCode" -> System.identityHashCode(proxy)
        "toString" -> "FolioDeviceStateCallback"
        else -> null
    }
}

/** Listens while StandBy could use it. [onTent] is called on the main thread; null means the service is not usable here. */
internal class DeviceStateWatcher(private val context: Context, private val onTent: (Boolean?) -> Unit) {
    private var manager: Any? = null
    private var callback: Any? = null
    private var unregister: Method? = null

    fun start() {
        if (callback != null) return
        try {
            val service = context.getSystemService("device_state") ?: return unusable("no device_state service")
            val type = Class.forName("android.hardware.devicestate.DeviceStateManager\$DeviceStateCallback")
            val proxy = Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { p, method, args -> DeviceStateTent.invoke(onTent, p, method.name, args) }
            val main = Handler(Looper.getMainLooper())
            service.javaClass.getMethod("registerCallback", Executor::class.java, type).invoke(service, Executor { main.post(it) }, proxy)
            unregister = service.javaClass.getMethod("unregisterCallback", type)
            manager = service; callback = proxy
        } catch (t: Throwable) {
            // Hidden API: it can be absent, renamed or refused on another phone or after an update. Never a crash, never a guess.
            unusable("${t.cause ?: t}")
        }
    }

    fun stop() {
        val m = manager; val c = callback
        manager = null; callback = null
        if (m != null && c != null) runCatching { unregister?.invoke(m, c) }
    }

    private fun unusable(why: String) {
        runCatching { Log.i("FolioDeviceState", "Device state not available to this app: $why") }
        onTent(null)
    }
}
