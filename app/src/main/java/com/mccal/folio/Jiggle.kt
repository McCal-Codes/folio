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
    fun start() { active = true }
    fun stop() { active = false }
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
internal fun BoxScope.JiggleRemoveButton(label: String, inset: Dp = 0.dp, onRemove: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    // 44dp touch target (accessibility minimum) around a 22dp visual, centered on the corner; [inset] pulls it in so it
    // stays inside a tight container like the side dock, as iPhone keeps it inside the dock.
    Box(Modifier.align(Alignment.TopStart).offset((-11).dp + inset, (-11).dp + inset).size(44.dp)
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
    modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier.minimumInteractiveComponentSize(), contentAlignment = Alignment.Center) {
        Row(Modifier.heightIn(min = 34.dp).clip(CircleShape)
            .background(if (emphasized) Color.White.copy(alpha = .92f) else Color.White.copy(alpha = .22f))
            .clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = description }
            .padding(horizontal = if (label.isEmpty()) 7.dp else FolioSpace.LARGE.dp), verticalAlignment = Alignment.CenterVertically) {
            val ink = if (emphasized) Color.Black else Color.White
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
        .clickable(role = Role.Button, onClickLabel = "Search", onClick = onClick)
        .padding(horizontal = FolioSpace.COMFY.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Search, null, tint = ink.primary, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(5.dp))
        Text(stringResource(R.string.search), color = ink.primary, fontSize = FolioType.FOOTNOTE.sp, fontWeight = FontWeight.Medium)
    }
}

/** Extra top inset for the jiggle bar when the Dynamic Island is on, so its pills don't sit under the island. */
internal val JIGGLE_BAR_ISLAND_GAP = 8.dp
/** Where the jiggle bar's bottom edge falls (top inset + 48dp touch height + a little air), for Home's first-row clearance. */
internal val JIGGLE_BAR_BOTTOM = JIGGLE_BAR_ISLAND_GAP + 48.dp + 4.dp
