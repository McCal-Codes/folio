package com.mccal.folio

import android.graphics.Rect
import android.view.ViewTreeObserver
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import kotlin.math.roundToInt

/**
 * Adjust island: the island itself on a dimmed screen, with a round handle on its right edge to make it wider, arrows to
 * nudge it, Fit to camera, Reset and Done. It draws with the same placement the real island uses ([islandPlacement]), at the
 * same spot, so what is shown is the fit that Home and other apps will get. The width ([IslandSize]) and the nudge
 * ([IslandNudge]) are saved per screen and orientation, a drag saves when the finger lifts, and Reset returns to the
 * automatic size and place. The arrows shift the island where it sits (over the camera or a dragged spot), so the width it
 * wraps is never lost to a nudge.
 *
 * When the camera is on a side edge the island stands upright there ([VerticalIsland], which has no width to adjust), so the
 * dialog says so instead of offering controls that would do nothing.
 *
 * A real Dialog, not an inline overlay, for the same reason the folder panel is one (see FolderPanel): it must reach the
 * camera area at the top edge. Every drag has a tap equivalent: the arrows, and TalkBack actions for wider, narrower and the
 * four moves; the handle is also an adjustable value for TalkBack.
 */
@Composable
internal fun IslandAdjustDialog(onDone: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Dialog(onDismissRequest = onDone, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val view = LocalView.current
        LaunchedEffect(view) {
            (view.parent as? DialogWindowProvider)?.window?.let { w ->
                w.setDimAmount(0f)
                androidx.core.view.WindowCompat.getInsetsController(w, w.decorView).apply {
                    systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    hide(androidx.core.view.WindowInsetsCompat.Type.statusBars())
                }
                w.attributes = w.attributes.apply { layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS }
            }
        }
        val density = LocalDensity.current
        val d = density.density
        val haptics = LocalHapticFeedback.current
        var windowWidth by remember { mutableIntStateOf(view.width) }
        var windowHeight by remember { mutableIntStateOf(view.height) }
        var cutout by remember { mutableStateOf<Rect?>(null) }
        var cutoutRaw by remember { mutableStateOf<android.view.DisplayCutout?>(null) }
        // Keyed on the density: after a display-size change the camera and the reported width are read again with the new one.
        DisposableEffect(view, d) {
            val update = ViewTreeObserver.OnGlobalLayoutListener {
                windowWidth = view.rootView.width
                windowHeight = view.rootView.height
                val reported = view.rootWindowInsets?.displayCutout
                cutoutRaw = reported
                cutout = islandCamera(reported, view.display, d)
            }
            view.viewTreeObserver.addOnGlobalLayoutListener(update)
            update.onGlobalLayout()
            onDispose { view.viewTreeObserver.removeOnGlobalLayoutListener(update) }
        }
        val wide = islandWideWindow(windowWidth, d, androidx.compose.ui.platform.LocalConfiguration.current.classScale)
        val landscape = windowWidth > windowHeight
        var custom by remember(wide, landscape) { mutableStateOf(IslandPosition.load(context, wide, landscape)) }
        var extra by remember(wide, landscape) { mutableFloatStateOf(IslandSize.load(context, wide, landscape)) }
        var nudge by remember(wide, landscape) { mutableStateOf(IslandNudge.load(context, wide, landscape)) }
        var fitMessage by remember { mutableStateOf(false) }

        if (windowWidth <= 0) { Box(Modifier.fillMaxSize()); return@Dialog }
        val camera = cutout?.ltrb()
        // A camera on a side edge: Home draws the upright island there, which has no width to adjust (CutoutIsland.kt).
        val sideEdge = cameraSideEdge(cutout, windowWidth, windowHeight) != null
        val geometry = islandPlacement(camera, custom, windowWidth, windowHeight, d, extra, nudge)
        val shown = geometry.extraW
        val fit = IslandSize.fitExtra(islandReportedCameraWidthDp(cutoutRaw, d), geometry)
        val pillW = minOf(geometry.wrapW + geometry.pillH, maxOf(geometry.maxW, geometry.camW + geometry.pillH))

        /** Previews a width while dragging; [commit] saves it (a drag saves once, when the finger lifts). */
        fun preview(next: Float, tick: Boolean = false) {
            val clamped = IslandSize.clamp(next, geometry)
            if (clamped == shown) return
            if (tick && fit != null && (shown < fit) != (clamped < fit)) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            extra = clamped; fitMessage = false
        }
        // The drag below runs in one long coroutine: it reads the newest of these on every move, not the ones from when the finger went down.
        val previewNow by androidx.compose.runtime.rememberUpdatedState<(Float, Boolean) -> Unit>({ next, tick -> preview(next, tick) })
        fun commit() { IslandSize.save(context, wide, landscape, extra) }
        fun setExtra(next: Float) { preview(next); commit() }
        fun nudgeBy(dxDp: Float, dyDp: Float) {
            val next = nudge.plus(dxDp, dyDp)
            nudge = next; IslandNudge.save(context, wide, landscape, next)
        }
        fun reset() {
            custom = null; extra = 0f; nudge = IslandNudge.None; fitMessage = false
            IslandPosition.save(context, wide, landscape, null); IslandSize.save(context, wide, landscape, 0f); IslandNudge.save(context, wide, landscape, IslandNudge.None)
        }

        val fitted = stringResource(R.string.island_fit_already)
        val wider = stringResource(R.string.island_wider); val narrower = stringResource(R.string.island_narrower)
        val left = stringResource(R.string.island_nudge_left); val right = stringResource(R.string.island_nudge_right)
        val up = stringResource(R.string.island_nudge_up); val down = stringResource(R.string.island_nudge_down)
        val handleLabel = stringResource(R.string.island_handle_label)
        val handleState = stringResource(R.string.island_handle_state, shown.roundToInt())

        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .7f))) {
            // The camera as Android reports it, so the fit can be judged by eye.
            cutout?.let { c ->
                Box(Modifier.offset { IntOffset(c.left, c.top) }.size((c.width() / d).dp, (c.height() / d).dp)
                    .border(1.5.dp, Color.White.copy(alpha = .55f), CircleShape))
            }
            val pillLeftPx = islandLeftPx(geometry.centerXPx, pillW * d, windowWidth, d)
            val actions = if (sideEdge) emptyList() else listOf(
                CustomAccessibilityAction(wider) { setExtra(shown + IslandSize.STEP_DP); true },
                CustomAccessibilityAction(narrower) { setExtra(shown - IslandSize.STEP_DP); true },
                CustomAccessibilityAction(left) { nudgeBy(-IslandSize.STEP_DP, 0f); true },
                CustomAccessibilityAction(right) { nudgeBy(IslandSize.STEP_DP, 0f); true },
                CustomAccessibilityAction(up) { nudgeBy(0f, -IslandSize.STEP_DP); true },
                CustomAccessibilityAction(down) { nudgeBy(0f, IslandSize.STEP_DP); true },
            )
            Box(Modifier.offset { IntOffset(pillLeftPx.roundToInt(), (geometry.top * d).roundToInt()) }
                .width(pillW.dp).height(geometry.pillH.dp)
                .clip(RoundedCornerShape((geometry.pillH / 2).dp)).background(Color.Black)
                .border(1.5.dp, Color.White.copy(alpha = .8f), RoundedCornerShape((geometry.pillH / 2).dp))
                .semantics { contentDescription = handleLabel; stateDescription = handleState; customActions = actions })
            // The handle: a round grip on the right edge. Dragging it widens the pill from the center, so the camera stays inside.
            if (!sideEdge) Box(Modifier.offset { IntOffset((pillLeftPx + pillW * d - 24 * d).roundToInt(), ((geometry.top + geometry.pillH / 2 - 24) * d).roundToInt()) }
                .size(48.dp)
                .semantics {
                    contentDescription = handleLabel
                    stateDescription = handleState
                    progressBarRangeInfo = ProgressBarRangeInfo(shown, 0f..geometry.maxExtra.coerceAtLeast(1f))
                    setProgress { v -> setExtra(v); true }
                }
                .pointerInput(geometry.maxExtra, fit) {
                    detectDragGestures(onDragEnd = { commit() }, onDragCancel = { commit() }) { change, drag -> change.consume(); previewNow(extra + 2f * drag.x / d, true) }
                }, contentAlignment = Alignment.Center) {
                Box(Modifier.size(26.dp).clip(CircleShape).background(Color.White).border(2.dp, Color(0xFF0A84FF), CircleShape))
            }

            Column(Modifier.align(Alignment.TopCenter).padding(top = (geometry.top + geometry.pillH + 28).dp, start = 24.dp, end = 24.dp).widthIn(max = 420.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(if (sideEdge) R.string.island_adjust_side_note else R.string.island_adjust_hint), color = Color.White, fontSize = 15.sp, textAlign = TextAlign.Center)
                if (fitMessage && !sideEdge) Text(fitted, color = Color.White.copy(alpha = .8f), fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
            }

            Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 24.dp, start = 16.dp, end = 16.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!sideEdge) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AdjustChip("←", left, round = true) { nudgeBy(-IslandSize.STEP_DP, 0f) }
                    AdjustChip("↑", up, round = true) { nudgeBy(0f, -IslandSize.STEP_DP) }
                    AdjustChip("↓", down, round = true) { nudgeBy(0f, IslandSize.STEP_DP) }
                    AdjustChip("→", right, round = true) { nudgeBy(IslandSize.STEP_DP, 0f) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (!sideEdge) AdjustChip(stringResource(R.string.island_fit_camera), null) {
                        // Say so when there is nothing to fit, or when it already fits (a tap that changes nothing is a tap that looks broken).
                        if (fit != null && fit > shown + .5f) setExtra(fit) else fitMessage = true
                    }
                    AdjustChip(stringResource(R.string.island_adjust_reset), null) { reset() }
                    AdjustChip(stringResource(R.string.done), null, primary = true) { onDone() }
                }
            }
        }
    }
}

@Composable
private fun AdjustChip(label: String, description: String?, round: Boolean = false, primary: Boolean = false, onClick: () -> Unit) {
    Box(Modifier.heightIn(min = FolioTouch.MIN.dp).then(if (round) Modifier.width(FolioTouch.MIN.dp) else Modifier.widthIn(min = FolioTouch.MIN.dp))
        .clip(RoundedCornerShape(FolioTouch.MIN.dp / 2)).background(if (primary) Color.White else Color.White.copy(alpha = .2f))
        .clickable(role = Role.Button, onClickLabel = description) { onClick() }
        .semantics { description?.let { contentDescription = it } }
        .padding(horizontal = if (round) 0.dp else 18.dp), contentAlignment = Alignment.Center) {
        Text(label, color = if (primary) Color.Black else Color.White, fontSize = if (round) 20.sp else 14.sp, fontWeight = FontWeight.SemiBold)
    }
}
