package com.mccal.folio

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlin.math.sqrt
import kotlinx.coroutines.launch

/**
 * The 0.6.9 motion pass for an app carried on Home or the dock (the Mockup Lab's "Letting go of an icon", chosen 9 Oct 2026).
 * With the pass on, the copy under the finger starts exactly on the icon and keeps the spot that was grabbed, lifts the way a
 * held icon does ([PressFeedback.LIFT], with its shadow), and on release flies from the finger's speed to wherever the icon now
 * lives, on [FolioMotion.Bounce], while the icon itself stays hidden in its cell until the copy arrives. Under Reduce Motion
 * nothing lifts or flies: the copy fades out where the finger let go as the icon fades in. With the pass off, a drag looks
 * exactly as it did before (a 66 dp copy centered under the finger that disappears on release).
 *
 * Positions are in the launcher root's pixels, the same space the drag copy is drawn in.
 */
@Stable
internal class DragLanding {
    /** True while the current drag (or its landing) uses the pass. */
    var lifted by mutableStateOf(false)
        private set
    /** The app whose copy is still in the air after the finger let go; its own cell draws nothing until it lands. */
    var flyingId by mutableStateOf<String?>(null)
        private set
    /** The copy's size in pixels: the icon's own size, so it starts exactly on top of it. */
    var size by mutableStateOf(0f)
        private set
    private var reduced = false
    private var grab = Offset.Zero
    private var flight: Job? = null
    /** Where the launcher root sits in the window; icon bounds are recorded in window pixels. */
    var rootInWindow = Offset.Zero
    val position = Animatable(Offset.Zero, Offset.VectorConverter)
    val scale = Animatable(1f)
    /** The copy's opacity; only below 1 while it fades out under Reduce Motion. */
    val fade = Animatable(1f)

    /** The copy's top-left while the finger holds it: the finger minus the spot it grabbed. */
    fun heldAt(pointer: Offset) = pointer - grab

    /** How much of the lift is showing, 0 to 1, for the shadow. */
    fun liftProgress() = ((scale.value - 1f) / (PressFeedback.LIFT - 1f)).coerceIn(0f, 1f)

    /**
     * A drag started. Uses the pass only when it is on and the app's icon is on screen to start from (Home or the dock);
     * anything else (a folder, a widget, an app pulled out of the Library or a folder) keeps the drag as it was.
     */
    fun begin(scope: CoroutineScope, appId: String?, pointer: Offset, pass: Boolean, reduceMotion: Boolean) {
        flight?.cancel(); flyingId = null
        val icon = if (pass && appId != null) iconTopLeft(appId) else null
        lifted = icon != null
        if (icon == null) return
        reduced = reduceMotion
        size = IconBounds.of(appId!!)!!.width().toFloat()
        grab = pointer - icon
        scope.launch {
            fade.snapTo(1f); scale.snapTo(1f)
            if (!reduceMotion) scale.animateTo(PressFeedback.LIFT, FolioMotion.spring(FolioMotion.Quick))
        }
    }

    /** The drag ended without a landing (a long press that opened the menu, a drop on Remove, a folder): the copy just goes. */
    fun drop() { flight?.cancel(); lifted = false; flyingId = null }

    /**
     * The finger let go of [appId] at [pointer] moving at [velocity]. The copy flies to the icon's place once the drop has been
     * drawn; the place is read again every frame, because the icon only reports its new bounds after the layout has moved.
     */
    fun land(scope: CoroutineScope, appId: String, pointer: Offset, velocity: Offset) {
        if (!lifted) return
        flight?.cancel()
        flyingId = appId
        val start = heldAt(pointer)
        flight = scope.launch {
            position.snapTo(start)
            if (reduced) {
                fade.animateTo(0f, tween(REDUCED_FADE_MS))
                finish(); return@launch
            }
            launch { scale.animateTo(1f, FolioMotion.spring(FolioMotion.Quick)) }
            // Let the drop compose and lay out, so the icon's bounds are the new ones.
            withFrameNanos { }
            var last = withFrameNanos { it }
            val spring = BounceFlight(start, velocity)
            var frames = 0
            while (true) {
                val now = withFrameNanos { it }
                val target = iconTopLeft(appId)
                frames++
                if (target == null) { last = now; if (frames > 10) break else continue }
                // One spring steered toward wherever the icon is this frame: Home slides down as jiggle mode starts right after a
                // drop, so the place moves while the copy flies, and restarting an animation on every move would stall it.
                position.snapTo(spring.step(target, ((now - last) / 1e9f).coerceIn(0f, .05f), MotionSpeed.current.factor))
                last = now
                if (spring.arrived(target) || frames > MAX_FLIGHT_FRAMES) break
            }
            finish()
        }
    }

    private fun finish() { flyingId = null; lifted = false }

    /** How visible an app's own cell is: hidden while its copy is in the air, fading in under Reduce Motion. */
    fun cellAlpha(appId: String) = cellAlpha(appId, flyingId, reduced, fade.value)

    private fun iconTopLeft(appId: String) = IconBounds.of(appId)?.let { Offset(it.left.toFloat(), it.top.toFloat()) - rootInWindow }

    companion object {
        const val REDUCED_FADE_MS = 150
        /** Two seconds at 60 Hz; a Bounce landing settles in well under half that. */
        const val MAX_FLIGHT_FRAMES = 120

        fun cellAlpha(appId: String, flyingId: String?, reduced: Boolean, copyAlpha: Float) = when {
            appId != flyingId -> 1f
            reduced -> 1f - copyAlpha
            else -> 0f
        }
    }
}

/**
 * The landing's spring, stepped by hand so its target can move mid-flight: [FolioMotion.Bounce]'s damping and stiffness, the
 * stiffness scaled by Settings › Animation Speed like every other Folio spring ([MotionSpeed]). It starts with the finger's speed.
 */
internal class BounceFlight(start: Offset, velocity: Offset) {
    var position = start
        private set
    var velocity = velocity
        private set

    fun step(target: Offset, seconds: Float, speed: Float): Offset {
        val (damping, stiffness) = FolioMotion.Bounce
        val k = stiffness * speed
        val c = 2f * damping * sqrt(k)
        repeat(SUBSTEPS) {
            val h = seconds / SUBSTEPS
            velocity += ((target - position) * k - velocity * c) * h
            position += velocity * h
        }
        return position
    }

    /** Within half a pixel and nearly still: the copy can hand over to the icon without a visible jump. */
    fun arrived(target: Offset) = (target - position).getDistance() < .5f && velocity.getDistance() < 20f

    private companion object { const val SUBSTEPS = 4 }
}
