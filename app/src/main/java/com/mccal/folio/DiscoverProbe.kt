package com.mccal.folio

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.IBinder
import android.os.Process
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Whether the Google app will hand Folio its feed right now: binds the overlay service the way Discover does and hangs up at once. The Google
 * app answers a client it will not serve with no binder, which is how Settings can say so before anyone swipes right and finds an error.
 */
internal object DiscoverProbe {
    private const val WAIT_MS = 4_000L

    suspend fun reachable(context: Context): Boolean {
        val app = context.applicationContext
        val answer = withTimeoutOrNull(WAIT_MS) {
            suspendCancellableCoroutine<Boolean> { continuation ->
                var connection: ServiceConnection? = null
                fun finish(result: Boolean) {
                    connection?.let { runCatching { app.unbindService(it) } }
                    connection = null
                    if (continuation.isActive) continuation.resume(result)
                }
                val link = object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName, service: IBinder) = finish(true)
                    override fun onServiceDisconnected(name: ComponentName) = Unit
                    override fun onNullBinding(name: ComponentName) = finish(false)
                    override fun onBindingDied(name: ComponentName) = finish(false)
                }
                connection = link
                val intent = Intent(DiscoverClient.OVERLAY_ACTION).setPackage(DiscoverClient.GOOGLE_PACKAGE)
                    .setData(Uri.parse("app://${app.packageName}:${Process.myUid()}?v=${DiscoverClient.OVERLAY_VERSION}&cv=${DiscoverClient.OVERLAY_VERSION}"))
                val bound = runCatching { app.bindService(intent, link, Context.BIND_AUTO_CREATE) }.getOrDefault(false)
                if (!bound) finish(false)
                continuation.invokeOnCancellation { connection?.let { runCatching { app.unbindService(it) } } }
            }
        }
        val result = answer ?: false
        DiscoverClient.lastResult = if (result) "probe from Settings: the Google app answered" else "probe from Settings: the Google app gave no feed"
        return result
    }
}
