package com.mccal.folio

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Exits that match entrances (New motion): a page closed with Done slides back out, a form sheet drops back the way it
 * rose, an alert shrinks a little as it fades. Each runs on [FolioMotion.Firm]; under Reduce Motion it is a
 * [FolioMotion.FADE_MS] fade. With New motion off the close happens at once, as before.
 *
 * Like the entrances, an exit never leaves a surface behind: if frames stop (Folio behind a system screen) the close still
 * happens after [EXIT_TIMEOUT_MS], and if the caller keeps the surface after its close (a required alert) it comes back.
 */
@Stable
internal class OverlayExit(private val frames: CoroutineScope, val reduce: Boolean) {
    /** 0 while the surface is shown, 1 when it has left. Read it in drawing only. */
    val progress = Animatable(0f)
    private val gate = ExitGate()
    private val clock = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Plays the exit, then runs [action] (which removes the surface). A second close while leaving is ignored. */
    fun close(action: () -> Unit) {
        if (!FolioMotion.v2) { action(); return }
        if (!gate.begin(action)) return
        frames.launch {
            progress.animateTo(1f, if (reduce) tween(FolioMotion.FADE_MS) else FolioMotion.spring(FolioMotion.Firm, .01f))
            end()
        }
        clock.launch { delay(EXIT_TIMEOUT_MS); end() }
    }

    private fun end() {
        val action = gate.finish() ?: return
        action()
        // Still here a moment later: the close did not remove it. Show it again rather than leave an invisible window.
        clock.launch { delay(EXIT_RESTORE_MS); gate.reopen(); progress.snapTo(0f) }
    }

    fun dispose() = clock.cancel()
}

/** Runs [action] through the overlay's exit when there is one, or at once (outside an overlay, or New motion off). */
internal fun OverlayExit?.closeWithExit(action: () -> Unit) = if (this == null) action() else close(action)

/** The exit of the overlay this is read in: Done buttons and Back handlers inside a page or sheet close through it. */
internal val LocalOverlayExit = staticCompositionLocalOf<OverlayExit?> { null }

@Composable
internal fun rememberOverlayExit(): OverlayExit {
    val reduce = LocalReduceMotion.current
    val scope = rememberCoroutineScope()
    val exit = remember { OverlayExit(scope, reduce) }
    DisposableEffect(exit) { onDispose { exit.dispose() } }
    return exit
}

/** Longer than Firm takes at the slowest Animation Speed, so a normal exit never jumps. */
private const val EXIT_TIMEOUT_MS = 900L
/** Long enough for the caller to take the surface away after its close. */
private const val EXIT_RESTORE_MS = 500L

/** One overlay's close: the action runs exactly once, whichever of the animation and the timeout ends first. */
internal class ExitGate {
    private var action: (() -> Unit)? = null
    var closing = false
        private set

    /** Starts closing with [close]; false if it is already closing (a second tap on the scrim or Done). */
    fun begin(close: () -> Unit): Boolean {
        if (closing) return false
        closing = true; action = close
        return true
    }

    /** The close action, the first time only. */
    fun finish(): (() -> Unit)? = action.also { action = null }

    /** The caller kept the surface: it can close again. */
    fun reopen() { closing = false; action = null }
}

/** Where each surface is while it leaves, for an exit [progress][OverlayExit.progress] from 0 to 1. */
internal object OverlayExits {
    /** How much an alert shrinks as it fades out. */
    const val ALERT_SHRINK = .06f
    /** How far a form sheet drops as it leaves, as a share of its height: the distance it rose. */
    const val FORM_DROP = .12f

    /** A page slides back out to the trailing edge, in page widths; under Reduce Motion it stays put and fades. */
    fun pageShift(exit: Float, reduce: Boolean) = if (reduce) 0f else exit
    /** Opacity left: a page slides out whole, sheets and alerts fade as they go, and everything fades under Reduce Motion. */
    fun alpha(exit: Float, fades: Boolean, reduce: Boolean) = if (fades || reduce) 1f - exit.coerceIn(0f, 1f) else 1f
    fun formDrop(exit: Float, reduce: Boolean) = if (reduce) 0f else FORM_DROP * exit
    fun alertScale(exit: Float, reduce: Boolean) = if (reduce) 1f else 1f - ALERT_SHRINK * exit.coerceIn(0f, 1f)
    /** Home can stop drawing behind a page only while the page covers it completely and is not on its way out. */
    fun covering(slide: Float, exit: Float) = slide == 0f && exit == 0f
}
