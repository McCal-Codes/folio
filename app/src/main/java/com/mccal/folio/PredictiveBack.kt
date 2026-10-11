package com.mccal.folio

import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.findViewTreeOnBackPressedDispatcherOwner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.CancellationException

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
