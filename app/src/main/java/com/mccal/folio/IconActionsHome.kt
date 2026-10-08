package com.mccal.folio

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Indication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role

/**
 * What Home needs to run an icon's saved actions: the actions for an app, and a way to run one. Provided only while
 * Icon Actions is on for this phone and Home is not being edited, so an icon outside that keeps today's behavior.
 */
internal class IconActionHost(
    val actionsFor: (String) -> IconActions?,
    val run: (ActionRef) -> Unit,
)

/**
 * Whether Home provides the actions host at all. When it does not, every icon keeps its normal gestures: while Home is
 * being edited (jiggle and drag win), in Safe Mode (a swipe is not taken only to be refused), with the gate shut, or with
 * no action saved anywhere.
 */
internal fun iconActionsAvailable(editing: Boolean, safeMode: Boolean, anySaved: Boolean, gateOpen: Boolean) =
    !editing && !safeMode && anySaved && gateOpen

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

/** What an icon's saved actions add to it: the touch handling, and the same actions for TalkBack's Actions list. */
internal class IconGestureSet(val modifier: Modifier, val customActions: List<CustomAccessibilityAction>)

/**
 * The touch handling for one icon, whether it sits in Home's grid or in the dock. An icon with no saved action gets
 * [plain], the caller's own modifier, so it runs exactly the code it always did (no extra callbacks, no insets read, no
 * double-tap wait). An icon with actions gets swipes that run them (falling back to [openPanel] and [stack] for a swipe it
 * has no action for), a double tap, and a TalkBack action for each, so nothing depends on a gesture.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun rememberIconGestures(
    app: AppEntry?, interaction: MutableInteractionSource, indication: Indication?,
    openPanel: ((AppEntry) -> Unit)?, stack: ((AppEntry) -> Unit)?, onClick: () -> Unit, plain: Modifier,
): IconGestureSet {
    val host = LocalIconActions.current
    val saved = if (app == null) null else host?.actionsFor(app.id)
    val upRef = saved.runnable(IconGestureKind.UP)
    val downRef = saved.runnable(IconGestureKind.DOWN)
    val doubleRef = saved.runnable(IconGestureKind.DOUBLE)
    if (app == null || host == null || (upRef == null && downRef == null && doubleRef == null)) return IconGestureSet(plain, emptyList())
    val swipeUp = remember(app, upRef, openPanel, host) { upRef?.let { ref -> { host.run(ref) } } ?: openPanel?.let { { it(app) } } }
    val swipeDown = remember(app, downRef, stack, host) { downRef?.let { ref -> { host.run(ref) } } ?: stack?.let { { it(app) } } }
    val doubleTap = remember(doubleRef, host) { doubleRef?.let { ref -> { host.run(ref) } } }
    // A touch in the bottom gesture strip belongs to Android's navigation, so it never starts an icon swipe.
    val gestureInset = WindowInsets.systemGestures.getBottom(LocalDensity.current)
    val windowHeight = LocalWindowInfo.current.containerSize.height
    val tileTop = remember { floatArrayOf(0f) }
    val startAllowed = remember(gestureInset, windowHeight) { { y: Float -> IconGesture.startsAboveInset(tileTop[0] + y, windowHeight.toFloat(), gestureInset.toFloat()) } }
    val modifier = Modifier.onGloballyPositioned { tileTop[0] = it.positionInWindow().y }
        .iconSwipes(swipeUp, swipeDown, startAllowed)
        // A double tap makes this icon's single tap wait out the double-tap timeout, so only an icon that has one pays for it.
        .combinedClickable(interactionSource = interaction, indication = indication, role = Role.Button, onClick = onClick, onDoubleClick = doubleTap)
    val actions = listOfNotNull(upRef?.let { it to R.string.icon_action_swipe_up }, downRef?.let { it to R.string.icon_action_swipe_down },
        doubleRef?.let { it to R.string.icon_action_double_tap })
        .mapNotNull { (ref, gesture) -> ActionRegistry.standard.spec(ref.id)?.let { spec ->
            ref to stringResource(R.string.icon_action_talkback, stringResource(gesture), stringResource(spec.label)) } }
        .map { (ref, label) -> CustomAccessibilityAction(label) { host.run(ref); true } }
    return IconGestureSet(modifier, actions)
}
