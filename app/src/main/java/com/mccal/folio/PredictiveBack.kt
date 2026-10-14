package com.mccal.folio

import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.findViewTreeOnBackPressedDispatcherOwner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Predictive back (Android 14+, on by default when targeting API 36): [onProgress] follows the back swipe from 0 to 1
 * so the surface can shrink toward where it came from; [onBack] runs when the swipe is released, [onCancel] when it's
 * abandoned. On older Android, or with gesture navigation off, only [onBack] runs.
 */
@Composable
internal fun PredictiveBack(enabled: Boolean = true, onProgress: (Float) -> Unit, onCancel: () -> Unit, onBack: () -> Unit) {
    val step by rememberUpdatedState(onProgress)
    val cancel by rememberUpdatedState(onCancel)
    val back by rememberUpdatedState(onBack)
    PredictiveBackHandler(enabled) { events ->
        try {
            events.collect { event -> step(event.progress) }
            back()
        } catch (e: CancellationException) {
            cancel()
            throw e
        }
    }
}

/**
 * The back swipe over Spotlight or a top panel, for the 0.6.9 motion pass (MO7, the Lab's "Predictive back"). Both draw from
 * one shared open value (MainActivity's overlay progress); while the finger swipes they show [shown], so the overlay and the
 * blur on Home ease back toward closed by up to [PREVIEW], at full opacity. Let go and the usual close runs from there, because
 * [swipe] keeps its value until the next overlay opens; change your mind and it springs back to 0.
 */
internal object OverlayBack {
    /** The folder's number: a preview goes up to this share of the way to closed. */
    const val PREVIEW = .35f
    val swipe = androidx.compose.animation.core.Animatable(0f)

    fun shown(progress: Float, swipe: Float) = progress * (1f - PREVIEW * swipe.coerceIn(0f, 1f))

    /** The open value without the preview, for opacity: a surface moves and shrinks during the swipe but only fades as it closes. */
    fun unpreviewed(shown: Float, swipe: Float = this.swipe.value) = (shown / (1f - PREVIEW * swipe.coerceIn(0f, 1f))).coerceIn(0f, 1f)
}

/**
 * Back on Spotlight or a top panel: with the motion pass on (and Reduce Motion off) it previews through [OverlayBack];
 * otherwise it is the plain back handler it always was.
 */
@Composable
internal fun OverlayBackHandler(open: Boolean, onClose: () -> Unit) {
    val pass = FolioMotion.v2 && !LocalReduceMotion.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    if (pass) PredictiveBack(enabled = open,
        onProgress = { p -> scope.launch { OverlayBack.swipe.snapTo(p) } },
        onCancel = { scope.launch { OverlayBack.swipe.animateTo(0f, FolioMotion.spring(FolioMotion.Quick)) } },
        onBack = onClose)
    else androidx.activity.compose.BackHandler(open) { onClose() }
}

/**
 * [PredictiveBack] for an overlay drawn in its own Dialog window; call it inside the Dialog's content. While the dialog
 * has focus, Android sends Back to the dialog's own dispatcher, but a Compose back handler there still registers on the
 * activity's (the dialog's composition inherits it), so it never hears Back. This registers on the dialog's, the way
 * [ModalDialogBackHandler] does for sheets. Outside a Dialog it falls back to [PredictiveBack].
 */
@Composable
internal fun DialogPredictiveBack(enabled: Boolean = true, onProgress: (Float) -> Unit, onCancel: () -> Unit, onBack: () -> Unit) {
    val view = androidx.compose.ui.platform.LocalView.current
    val dispatcherOwner = androidx.compose.runtime.remember(view) {
        (view.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window?.decorView?.findViewTreeOnBackPressedDispatcherOwner()
    }
    if (dispatcherOwner == null) { PredictiveBack(enabled, onProgress, onCancel, onBack); return }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val step by rememberUpdatedState(onProgress)
    val cancel by rememberUpdatedState(onCancel)
    val back by rememberUpdatedState(onBack)
    val callback = androidx.compose.runtime.remember {
        object : androidx.activity.OnBackPressedCallback(enabled) {
            override fun handleOnBackProgressed(backEvent: androidx.activity.BackEventCompat) { step(backEvent.progress) }
            override fun handleOnBackCancelled() { cancel() }
            override fun handleOnBackPressed() { back() }
        }
    }
    androidx.compose.runtime.SideEffect { callback.isEnabled = enabled }
    androidx.compose.runtime.DisposableEffect(dispatcherOwner, lifecycleOwner) {
        dispatcherOwner.onBackPressedDispatcher.addCallback(lifecycleOwner, callback)
        onDispose { callback.remove() }
    }
}
