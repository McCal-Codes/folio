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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
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
 * same spot, so what is shown is the fit that Home and other apps will get. Every change is saved at once ([IslandSize],
 * [IslandPosition]) and Reset returns to the automatic fit and place. The saved size is per screen and orientation.
 *
 * A real Dialog, not an inline overlay, for the same reason the folder panel is one (see FolderPanel): it must reach the
 * camera area at the top edge. Every drag has a tap equivalent: the arrows, and TalkBack actions for wider, narrower and the
 * four moves.
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
        val haptics = LocalHapticFeedback.current
        var windowWidth by remember { mutableIntStateOf(view.width) }
        var windowHeight by remember { mutableIntStateOf(view.height) }
        var cutout by remember { mutableStateOf<Rect?>(null) }
        var reportedWidthDp by remember { mutableStateOf<Float?>(null) }
        DisposableEffect(view) {
            val update = ViewTreeObserver.OnGlobalLayoutListener {
                windowWidth = view.rootView.width
                windowHeight = view.rootView.height
                val reported = view.rootWindowInsets?.displayCutout
                cutout = islandCamera(reported, view.display, density.density)
                reportedWidthDp = islandReportedCameraWidthDp(reported, density.density)
            }
            view.viewTreeObserver.addOnGlobalLayoutListener(update)
            update.onGlobalLayout()
            onDispose { view.viewTreeObserver.removeOnGlobalLayoutListener(update) }
        }
        val wide = islandWideWindow(windowWidth, density.density, androidx.compose.ui.platform.LocalConfiguration.current.classScale)
        val landscape = windowWidth > windowHeight
        var custom by remember(wide, landscape) { mutableStateOf(IslandPosition.load(context, wide, landscape)) }
        var extra by remember(wide, landscape) { mutableFloatStateOf(IslandSize.load(context, wide, landscape)) }
        var fitMessage by remember { mutableStateOf(false) }

        if (windowWidth <= 0) { Box(Modifier.fillMaxSize()); return@Dialog }
        val d = density.density
        val camera = cutout?.ltrb()
        val geometry = islandPlacement(camera, custom, windowWidth, windowHeight, d, extra)
        val fit = IslandSize.fitExtra(reportedWidthDp, geometry)
        val pillW = minOf(geometry.wrapW + geometry.pillH, maxOf(geometry.maxW, geometry.camW + geometry.pillH))

        fun setExtra(next: Float, tick: Boolean = false) {
            val clamped = IslandSize.clamp(next, geometry)
            if (clamped == extra) return
            if (tick && fit != null && (extra < fit) != (clamped < fit)) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            extra = clamped; fitMessage = false
            IslandSize.save(context, wide, landscape, clamped)
        }
        fun nudge(dxDp: Float, dyDp: Float) {
            val next = IslandPosition((geometry.centerXPx + dxDp * d) / windowWidth, geometry.top + dyDp)
                .clamped(windowWidth, windowHeight, d, geometry.pillH)
            custom = next; IslandPosition.save(context, wide, landscape, next)
        }
        fun reset() {
            custom = null; extra = 0f; fitMessage = false
            IslandPosition.save(context, wide, landscape, null); IslandSize.save(context, wide, landscape, 0f)
        }

        val fitted = stringResource(R.string.island_fit_already)
        val wider = stringResource(R.string.island_wider); val narrower = stringResource(R.string.island_narrower)
        val left = stringResource(R.string.island_nudge_left); val right = stringResource(R.string.island_nudge_right)
        val up = stringResource(R.string.island_nudge_up); val down = stringResource(R.string.island_nudge_down)
        val handleLabel = stringResource(R.string.island_handle_label)
        val handleState = stringResource(R.string.island_handle_state, extra.roundToInt())

        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .62f))) {
            // The camera as Android reports it, so the fit can be judged by eye.
            cutout?.let { c ->
                Box(Modifier.offset { IntOffset(c.left, c.top) }.size((c.width() / d).dp, (c.height() / d).dp)
                    .border(1.5.dp, Color.White.copy(alpha = .55f), CircleShape))
            }
            val pillLeftPx = islandLeftPx(geometry.centerXPx, pillW * d, windowWidth, d)
            val actions = listOf(
                CustomAccessibilityAction(wider) { setExtra(extra + IslandSize.STEP_DP); true },
                CustomAccessibilityAction(narrower) { setExtra(extra - IslandSize.STEP_DP); true },
                CustomAccessibilityAction(left) { nudge(-IslandSize.STEP_DP, 0f); true },
                CustomAccessibilityAction(right) { nudge(IslandSize.STEP_DP, 0f); true },
                CustomAccessibilityAction(up) { nudge(0f, -IslandSize.STEP_DP); true },
                CustomAccessibilityAction(down) { nudge(0f, IslandSize.STEP_DP); true },
            )
            Box(Modifier.offset { IntOffset(pillLeftPx.roundToInt(), (geometry.top * d).roundToInt()) }
                .width(pillW.dp).height(geometry.pillH.dp)
                .clip(RoundedCornerShape((geometry.pillH / 2).dp)).background(Color.Black)
                .border(1.5.dp, Color.White.copy(alpha = .8f), RoundedCornerShape((geometry.pillH / 2).dp))
                .semantics { contentDescription = handleLabel; stateDescription = handleState; customActions = actions })
            // The handle: a round grip on the right edge. Dragging it widens the pill from the center, so the camera stays inside.
            Box(Modifier.offset { IntOffset((pillLeftx(pillLeftPx, pillW, d) - 24 * d).roundToInt(), ((geometry.top + geometry.pillH / 2 - 24) * d).roundToInt()) }
                .size(48.dp)
                .pointerInput(geometry.maxExtra, fit) {
                    detectDragGestures { change, drag -> change.consume(); setExtra(extra + 2f * drag.x / d, tick = true) }
                }, contentAlignment = Alignment.Center) {
                Box(Modifier.size(26.dp).clip(CircleShape).background(Color.White).border(2.dp, Color(0xFF0A84FF), CircleShape))
            }

            Column(Modifier.align(Alignment.TopCenter).padding(top = (geometry.top + geometry.pillH + 28).dp, start = 24.dp, end = 24.dp).widthIn(max = 420.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.island_adjust_hint), color = Color.White, fontSize = 15.sp, textAlign = TextAlign.Center)
                if (fitMessage) Text(fitted, color = Color.White.copy(alpha = .8f), fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
            }

            Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 24.dp, start = 16.dp, end = 16.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AdjustChip("←", left, round = true) { nudge(-IslandSize.STEP_DP, 0f) }
                    AdjustChip("↑", up, round = true) { nudge(0f, -IslandSize.STEP_DP) }
                    AdjustChip("↓", down, round = true) { nudge(0f, IslandSize.STEP_DP) }
                    AdjustChip("→", right, round = true) { nudge(IslandSize.STEP_DP, 0f) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AdjustChip(stringResource(R.string.island_fit_camera), null) {
                        if (fit != null) setExtra(fit) else fitMessage = true
                    }
                    AdjustChip(stringResource(R.string.island_adjust_reset), null) { reset() }
                    AdjustChip(stringResource(R.string.done), null, primary = true) { onDone() }
                }
            }
        }
    }
}

/** Right edge of a pill that starts at [leftPx] and is [widthDp] wide, in px. */
private fun pillLeftx(leftPx: Float, widthDp: Float, density: Float): Float = leftPx + widthDp * density

@Composable
private fun AdjustChip(label: String, description: String?, round: Boolean = false, primary: Boolean = false, onClick: () -> Unit) {
    Box(Modifier.height(FolioTouch.MIN.dp).then(if (round) Modifier.width(FolioTouch.MIN.dp) else Modifier.widthIn(min = FolioTouch.MIN.dp))
        .clip(RoundedCornerShape(FolioTouch.MIN.dp / 2)).background(if (primary) Color.White else Color.White.copy(alpha = .2f))
        .clickable(onClickLabel = description ?: label) { onClick() }
        .semantics { description?.let { contentDescription = it } }
        .padding(horizontal = if (round) 0.dp else 18.dp), contentAlignment = Alignment.Center) {
        Text(label, color = if (primary) Color.Black else Color.White, fontSize = if (round) 20.sp else 14.sp, fontWeight = FontWeight.SemiBold)
    }
}
