package com.mccal.folio

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mccal.folio.market.InstallResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * An install that's already started, kept outside the screen that started it.
 *
 * Downloading and applying a package can't be stopped halfway - the work is a single block of file and network calls,
 * and cancelling a coroutine doesn't interrupt those. So when someone pressed Back while a package was downloading,
 * the package still landed on their Home screen, and the screen that would have said so was gone: no message, no
 * Undo, and no way to put it back.
 *
 * Keeping the work here means the Market can leave and come back. It holds what's in progress and the result nobody
 * has read yet, so reopening the store shows "… is on · Undo" exactly as if it had never been closed.
 */
internal object MarketWork {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** The package being installed, for the "Working…" line. Null when nothing is. */
    var busyId by mutableStateOf<String?>(null)
        private set

    /** A finished install the store hasn't shown yet. */
    var finished by mutableStateOf<Finished?>(null)
        private set

    /** How far the install has got, for the ring in the Get button. */
    var progress by mutableStateOf<MarketProgress?>(null)
        private set

    /** Called from the download as bytes arrive. [total] is -1 when the source didn't say how big the file is. */
    fun downloaded(bytes: Long, total: Long) {
        val started = progress?.takeIf { it.phase == MarketProgress.Phase.DOWNLOADING }?.startedAt
            ?: System.currentTimeMillis()
        progress = MarketProgress(
            MarketProgress.Phase.DOWNLOADING, bytes, total, started, System.currentTimeMillis(),
        )
    }

    /** The bytes are here and checked; what's left is writing, which is quick and has nothing to measure. */
    fun applying() {
        progress = MarketProgress(MarketProgress.Phase.APPLYING)
    }

    data class Finished(val name: String, val result: InstallResult)

    /** True while a package is being installed. Only one at a time: two would each record the other out of the list. */
    val busy: Boolean get() = busyId != null

    /**
     * Runs [work] only while nothing else is installing, holding the same one-at-a-time slot meanwhile, so an
     * automatic update and a tap on Get can never run together and record each other out of the list. Null when busy.
     */
    suspend fun <T> exclusive(id: String, work: suspend () -> T): T? = kotlinx.coroutines.withContext(Dispatchers.Main.immediate) {
        if (busy) return@withContext null
        busyId = id
        try { kotlinx.coroutines.withContext(Dispatchers.IO) { work() } } finally { busyId = null }
    }

    /**
     * Starts an install, unless one is already running. [work] is the whole thing - download, checks, apply - and it
     * runs to the end whether or not anyone is still looking. [failed] is what to say if it throws; it comes from the
     * caller because this object has no context to read a string with, and it has to be in the phone's language.
     */
    fun install(id: String, name: String, failed: String, work: suspend () -> InstallResult): Boolean {
        if (busy) return false
        busyId = id
        progress = MarketProgress(MarketProgress.Phase.APPLYING)
        scope.launch {
            // Nothing cancels this scope, so any failure, cancellation included, is an install that failed.
            val result = caught("Market: installing a package", rethrowCancellation = false) { work() }.getOrElse {
                InstallResult.Failed(InstallResult.Reason.APPLY, failed)
            }
            finished = Finished(name, result)
            busyId = null
            progress = null
        }
        return true
    }

    /** Called by the store once it has said what happened. */
    fun taken(): Finished? = finished.also { finished = null }

    /**
     * Work that takes the same turn as an install - the ring, and nothing else starting while it runs - but says
     * what happened itself rather than ending in an [InstallResult].
     *
     * Handing an app to Android is the one of these: the download and the checksum finish here, and whether the
     * app arrived is answered later by [MarketInstallReceiver], through [MarketApkInstall.status]. Ending this
     * with a made-up result would put "Android is installing it" on the screen before Android had asked, and
     * leave it there whatever the answer turned out to be.
     */
    fun run(id: String, work: suspend () -> Unit): Boolean {
        if (busy) return false
        busyId = id
        progress = MarketProgress(MarketProgress.Phase.APPLYING)
        scope.launch {
            caught("Market: handing an app to Android", rethrowCancellation = false) { work() }
            busyId = null
            progress = null
        }
        return true
    }

    /**
     * Something small that has to finish whether or not a screen is open - adding the supporter source after a
     * code is redeemed, say. It isn't an install, so it doesn't touch [busy] and two of them may overlap.
     */
    fun background(work: suspend () -> Unit) {
        scope.launch { caught("Market: background work", rethrowCancellation = false) { kotlinx.coroutines.withContext(Dispatchers.IO) { work() } } }
    }
}
