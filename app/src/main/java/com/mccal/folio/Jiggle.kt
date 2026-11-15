package com.mccal.folio

import androidx.compose.ui.res.stringResource
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Search
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** iPhone-style "jiggle mode" for editing Home: icons wiggle, show a remove button, and move with one drag. */
@Stable
internal class HomeEditMode {
    var active by mutableStateOf(false)
        private set
    /** Removes an item from Home (it stays in the App Library); set by LauncherScreen. */
    var onRemove: (DropTarget) -> Unit = {}
    /** Home cell that was long-pressed to start editing, for "Edit" and "+". */
    var lastEmptyIndex: Int? = null
    /**
     * How far the unfolded bar is really lifted: [JIGGLE_BAR_LIFT] where the window has that much empty strip above the page, less where it
     * has not (a short, wide window), so the pills are never pushed off the top edge. Home gives way for whatever the lift falls short by.
     */
    var barLift by mutableStateOf(JIGGLE_BAR_LIFT)
    /**
     * How much taller the bar is than one row of pills: nothing where they all fit, one more row where a narrow window (a flip's cover
     * screen) wraps Done under the rest. Measured, so Home gives way for exactly what the bar takes.
     */
    var barExtra by mutableStateOf(0.dp)
    fun start() { active = true }
    fun stop() { active = false; barExtra = 0.dp }
}

internal val LocalHomeEdit = staticCompositionLocalOf { HomeEditMode() }

/** Shared wiggle phase (0..1) while jiggling, null otherwise. Read only in the draw phase. */
internal val LocalJiggle = compositionLocalOf<State<Float>?> { null }

@Composable
internal fun ProvideJiggle(edit: HomeEditMode, content: @Composable () -> Unit) {
    val clock = if (edit.active && !LocalReduceMotion.current) {
        val transition = rememberInfiniteTransition(label = "jiggle")
        transition.animateFloat(0f, 1f, infiniteRepeatable(tween(280, easing = LinearEasing)), label = "jiggle phase")
    } else null
    CompositionLocalProvider(LocalHomeEdit provides edit, LocalJiggle provides clock, content = content)
}

/** Wiggle this element while jiggling; [key] staggers the phase so neighbors don't move in sync. */
@Composable
internal fun Modifier.jiggle(key: Any, amount: Float = 1f): Modifier {
    val clock = LocalJiggle.current ?: return this
    val hash = key.hashCode()
    val seed = (hash and 0xFF) / 255f
    val direction = if (hash and 0x100 == 0) 1f else -1f
    return graphicsLayer {
        val angle = 2f * PI.toFloat() * (clock.value + seed)
        rotationZ = sin(angle) * 1.5f * amount * direction
        translationY = cos(angle) * .5f * density * amount
    }
}

/** The gray "–" in an icon's corner while jiggling. */
@Composable
internal fun BoxScope.JiggleRemoveButton(label: String, inset: Dp = 0.dp, insetX: Dp = inset, onRemove: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    // 44dp touch target (accessibility minimum) around a 22dp visual, centered on the corner; [inset] pulls it in so it
    // stays inside a tight container like the side dock, as iPhone keeps it inside the dock.
    Box(Modifier.align(Alignment.TopStart).offset((-11).dp + insetX, (-11).dp + inset).size(44.dp)
        .clickable(role = Role.Button, onClickLabel = label, interactionSource = null, indication = null) {
            haptic.perform(FolioHaptic.Refuse); onRemove()
        }.semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        Box(Modifier.size(22.dp).shadow(2.dp, CircleShape).background(Color(0xFFD1D1D6), CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Remove, null, tint = FolioColors.SecondaryBackground, modifier = Modifier.size(15.dp))
        }
    }
}

/** Glass capsule button for the jiggle-mode bar ("+", "Edit", "Done"). */
@Composable
internal fun JigglePill(label: String, icon: ImageVector? = null, description: String = label, emphasized: Boolean = false,
    modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    // The bar sits on the wallpaper, so it follows Home's ink like the Search pill does: white text on a light surface vanished (27 Oct sweep, F03 follow-up).
    val homeInk = LocalHomeInk.current
    Box(modifier.minimumInteractiveComponentSize().alpha(if (enabled) 1f else .4f), contentAlignment = Alignment.Center) {
        Row(Modifier.heightIn(min = 34.dp).clip(CircleShape)
            .background(if (emphasized) (if (homeInk.dark) homeInk.primary.copy(alpha = .92f) else Color.White.copy(alpha = .92f))
                else if (homeInk.dark) Color.White.copy(alpha = .45f) else Color.White.copy(alpha = .22f))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick).semantics { contentDescription = description }
            .padding(horizontal = if (label.isEmpty()) 7.dp else FolioSpace.LARGE.dp), verticalAlignment = Alignment.CenterVertically) {
            val ink = if (emphasized) (if (homeInk.dark) Color.White else Color.Black) else homeInk.primary
            icon?.let { Icon(it, null, tint = ink, modifier = Modifier.size(20.dp)) }
            if (label.isNotEmpty()) Text(label, color = ink, fontSize = FolioType.SUBHEAD.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** iOS 16+ Home "Search" capsule that sits where the page dots are. */
@Composable
internal fun HomeSearchPill(onClick: () -> Unit) {
    val ink = LocalHomeInk.current
    Row(Modifier.heightIn(min = 30.dp).clip(CircleShape).background(if (ink.dark) Color.White.copy(alpha = .45f) else Color.White.copy(alpha = .2f))
        .clickable(role = Role.Button, onClickLabel = stringResource(R.string.search), onClick = onClick)
        .padding(horizontal = FolioSpace.COMFY.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Search, null, tint = ink.primary, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(5.dp))
        Text(stringResource(R.string.search), color = ink.primary, fontSize = FolioType.FOOTNOTE.sp, fontWeight = FontWeight.Medium)
    }
}

/** Extra top inset for the jiggle bar when the Dynamic Island is on, so its pills don't sit under the island. */
internal val JIGGLE_BAR_ISLAND_GAP = 8.dp
/** Where the jiggle bar's bottom edge falls (top inset + 48dp touch height + a little air), for Home's first-row clearance. */
internal val JIGGLE_BAR_ROW = 48.dp
internal val JIGGLE_BAR_BOTTOM = JIGGLE_BAR_ISLAND_GAP + JIGGLE_BAR_ROW + 4.dp
/** The air between the unfolded bar's pills and the first row (a 12 dp gap). */
internal val JIGGLE_BAR_GRID_GAP = 12.dp
/**
 * How far the unfolded bar is lifted into the empty strip at the very top of the window, so Home gives way by almost nothing for it:
 * the 26 dp that put the pills' bottom on the first row, and [JIGGLE_BAR_GRID_GAP] more so they clear it.
 */
internal val JIGGLE_BAR_LIFT = 26.dp + JIGGLE_BAR_GRID_GAP
/**
 * The same for the unfolded window, where the bar sits at the pager's far end with no island gap and only its pills' drawn bottom (the
 * 48 dp touch box is 16 dp taller than the pill) has to clear the first row: 2 dp top + 48 dp box - 8 dp of unused touch height + 2 dp air.
 */
internal val JIGGLE_BAR_BOTTOM_UNFOLDED = 44.dp - 26.dp

/**
 * How wide the unfolded edit bar's pills run from the pager's far end, in dp at the default text size (+, Edit, Undo, Redo, Done): the span
 * the island pill is tested against to see whether it sits over them (see [IslandFootprint]).
 */
internal val JIGGLE_BAR_SPAN = 300.dp
/** The air between the island pill and the bar's pills on either side of it. */
internal val JIGGLE_BAR_ISLAND_SIDE_GAP = 8.dp
/** How far Home's bottom controls drop in edit mode on the unfolded screen, so the last row's labels keep clear of them. */
internal val JIGGLE_BAR_CONTROLS_DROP = 22.dp
/**
 * The same on the folded screen, where a full last row also ended under the page controls (the cover screen, 14 Nov 2026: the library
 * button sat over the last row's labels by about 11 dp). A little less than unfolded, so the controls stay clear of the gesture bar.
 */
internal val JIGGLE_BAR_CONTROLS_DROP_FOLDED = 18.dp
