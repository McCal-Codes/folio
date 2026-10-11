package com.mccal.folio

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape

/**
 * How an icon answers a finger: one dip for apps, dock icons and folders; a lift with a soft shadow while an icon's menu is open; and, after a
 * tap, the icon stays dipped for a moment while the app starts (Samsung gives a launcher no opening animation, so a slow app would otherwise look
 * like nothing happened). Mocked in the Lab (scene press-feedback) on 8 Oct 2026.
 *
 * Done the way motion is done elsewhere in Folio: a spring that follows the finger and can be interrupted at any point, only scale, alpha and a
 * shadow (all graphics-layer values read when drawing, so nothing recomposes per frame), and no scale at all under Reduce Motion, only a dim.
 */
internal object PressFeedback {
    const val DIP = .9f
    const val DIP_ALPHA = .9f
    const val LIFT = 1.06f
    const val LIFT_SHADOW_DP = 12f
    /** The longest an icon stays dipped after a tap; the app is normally up well before this. */
    const val HOLD_MS = 600L

    /** Which kind of icon is pressed: what it did before this pass differs, and the old feel must stay when the motion gate is shut. */
    enum class Kind { APP, DOCK, FOLDER }

    /**
     * How far the icon shrinks. With the motion pass off ([v2] false) every kind keeps what it did before: an app dips to .88,
     * a dock icon to .92, a folder not at all, and nothing lifts.
     */
    fun scale(pressed: Boolean, lifted: Boolean, reduceMotion: Boolean, kind: Kind = Kind.APP, v2: Boolean = true): Float = when {
        !v2 -> if (!pressed) 1f else when (kind) { Kind.APP -> .88f; Kind.DOCK -> .92f; Kind.FOLDER -> 1f }
        reduceMotion -> 1f
        lifted -> LIFT
        pressed -> DIP
        else -> 1f
    }

    fun alpha(pressed: Boolean, lifted: Boolean, kind: Kind = Kind.APP, v2: Boolean = true): Float = when {
        !v2 -> if (pressed && kind == Kind.APP) .82f else 1f
        else -> if (!lifted && pressed) DIP_ALPHA else 1f
    }

    fun shadowDp(lifted: Boolean, reduceMotion: Boolean, v2: Boolean = true): Float = if (v2 && lifted && !reduceMotion) LIFT_SHADOW_DP else 0f

    /** The spring token: the new pass follows the finger on Quick; the old feel kept an app's bounce (.55) and a dock icon's plain settle. */
    fun springFor(kind: Kind, v2: Boolean): Pair<Float, Float> = when {
        v2 -> FolioMotion.Quick
        kind == Kind.APP -> .55f to 1500f
        else -> FolioMotion.Snap
    }
}

/** The id of the app or folder whose menu is open, so its icon can lift while the menu is up. */
internal val LocalOpenMenuId = compositionLocalOf<String?> { null }

/** Dip, lift and shadow for one icon. [cornerDp] is the icon's corner radius, so the shadow follows its shape. */
@Composable
internal fun Modifier.pressFeedback(pressed: Boolean, lifted: Boolean = false, cornerDp: Float = 0f, dimOnly: Boolean = false, kind: PressFeedback.Kind = PressFeedback.Kind.APP): Modifier {
    val reduce = LocalReduceMotion.current
    val v2 = FolioMotion.v2
    val spec = FolioMotion.spring<Float>(PressFeedback.springFor(kind, v2))
    val scale = animateFloatAsState(PressFeedback.scale(pressed, lifted, reduce || dimOnly, kind, v2), spec, label = "icon press scale")
    val alpha = animateFloatAsState(PressFeedback.alpha(pressed, lifted, kind, v2), spec, label = "icon press alpha")
    val shadow = animateFloatAsState(PressFeedback.shadowDp(lifted, reduce || dimOnly, v2), spec, label = "icon lift shadow")
    return graphicsLayer {
        scaleX = scale.value; scaleY = scale.value; this.alpha = alpha.value
        shadowElevation = shadow.value * density
        if (shadowElevation > 0f) { shape = RoundedCornerShape(cornerDp.dp); clip = false }
    }
}
