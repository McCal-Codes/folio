package com.mccal.folio

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * What Home needs to run an icon's saved actions: the actions for an app, and a way to run one. Provided only while
 * Icon Actions is on for this phone and Home is not being edited, so an icon outside that keeps today's behavior.
 */
internal class IconActionHost(
    val actionsFor: (String) -> IconActions?,
    val run: (ActionRef) -> Unit,
)

internal val LocalIconActions = staticCompositionLocalOf<IconActionHost?> { null }

/**
 * One gesture's action, if the icon has one this build can run. An id this build does not know counts as no action, so
 * the icon keeps today's behavior instead of a swipe that does nothing.
 */
internal fun IconActions?.runnable(gesture: IconGestureKind, registry: ActionRegistry = ActionRegistry.standard): ActionRef? {
    val ref = when (gesture) { IconGestureKind.UP -> this?.up; IconGestureKind.DOWN -> this?.down; IconGestureKind.DOUBLE -> this?.double }
    return ref?.takeIf { registry.spec(it.id) != null }
}

internal enum class IconGestureKind { UP, DOWN, DOUBLE }
