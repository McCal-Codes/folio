package com.mccal.folio

import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * Moving controls in Control Center (FeatureGate.CC_REORDER). In edit mode a control is pressed and held, then dragged onto another,
 * and takes its place; the rest keep their order, and a drop on nothing puts it back, so nothing ever moves by itself.
 */

/** The list with [moving] put where [target] is. A name that is not in the list, or the same name twice, leaves it as it was. */
internal fun ccMoveControl(names: List<String>, moving: String, target: String): List<String> {
    val from = names.indexOf(moving)
    val to = names.indexOf(target)
    if (from < 0 || to < 0 || from == to) return names
    return names.toMutableList().also { it.removeAt(from); it.add(to, moving) }
}

/** The list with [name] moved [by] places (negative is earlier), stopping at either end: what TalkBack's "Move earlier" and "Move later" do. */
internal fun ccMoveControlBy(names: List<String>, name: String, by: Int): List<String> {
    val from = names.indexOf(name)
    if (from < 0) return names
    val to = (from + by).coerceIn(0, names.lastIndex)
    return if (to == from) names else ccMoveControl(names, name, names[to])
}

/** The control whose box holds [point], never [moving] itself; null over nothing. */
internal fun ccDropTarget(bounds: Map<String, Rect>, moving: String, point: Offset): String? =
    bounds.entries.firstOrNull { it.key != moving && it.value.contains(point) }?.key

/** The control being dragged, how far it is from where it was, and the one under it, shared by the controls on a page. */
internal class CcReorderState {
    var moving by mutableStateOf<String?>(null)
        private set
    var hover by mutableStateOf<String?>(null)
        private set
    var lift by mutableStateOf(Offset.Zero)
        private set

    /** Where each control is on screen, written by the controls as they are laid out. */
    val bounds = mutableMapOf<String, Rect>()
    private var origin = Offset.Zero
    private var pointer = Offset.Zero

    /** The press that lifts [name] was at [local] inside it. */
    fun start(name: String, local: Offset) {
        val box = bounds[name] ?: return
        moving = name
        origin = box.topLeft + local
        pointer = origin
        lift = Offset.Zero
        hover = null
    }

    fun drag(by: Offset) {
        val held = moving ?: return
        pointer += by
        lift = pointer - origin
        hover = ccDropTarget(bounds, held, pointer)
    }

    /** Lets go: [onDrop] gets the held control and the one it was dropped on, if any. */
    fun end(onDrop: (moving: String, target: String) -> Unit) {
        val held = moving
        val over = hover
        cancel()
        if (held != null && over != null) onDrop(held, over)
    }

    fun cancel() { moving = null; hover = null; lift = Offset.Zero }
}

/**
 * Lets a control be pressed, held and dragged onto another when [enabled]. The held one follows the finger a little larger; the one under it
 * gets a ring (still, nothing flashes). TalkBack gets the same move as two actions.
 */
internal fun Modifier.ccReorderable(
    name: String, state: CcReorderState, enabled: Boolean, earlierLabel: String, laterLabel: String,
    onDrop: (moving: String, target: String) -> Unit, onNudge: (by: Int) -> Unit,
): Modifier =
    if (!enabled) this
    else this
        .onGloballyPositioned { state.bounds[name] = it.boundsInRoot() }
        .pointerInput(name) {
            detectDragGesturesAfterLongPress(
                onDragStart = { state.start(name, it) },
                onDrag = { change, amount -> change.consume(); state.drag(amount) },
                onDragEnd = { state.end(onDrop) },
                onDragCancel = { state.cancel() },
            )
        }
        .semantics { customActions = listOf(CustomAccessibilityAction(earlierLabel) { onNudge(-1); true }, CustomAccessibilityAction(laterLabel) { onNudge(1); true }) }
        .graphicsLayer {
            if (state.moving == name) {
                translationX = state.lift.x
                translationY = state.lift.y
                scaleX = 1.08f
                scaleY = 1.08f
            }
        }
        .then(if (state.hover == name) Modifier.border(2.dp, Color.White.copy(alpha = .9f), RoundedCornerShape(26.dp)) else Modifier)
