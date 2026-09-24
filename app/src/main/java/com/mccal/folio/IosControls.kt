package com.mccal.folio

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Search
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** iOS-style controls for Folio's settings (the sheet is always dark glass). */
private val IosTrackOff = Color(0xFF39393D)

/** iOS switch: 51×31 green track with a white thumb that springs across. */
@Composable
internal fun IosSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val haptic = LocalHapticFeedback.current
    val track by animateColorAsState(if (checked) FolioColors.GreenLight else IosTrackOff, label = "switch track")
    val offset by animateDpAsState(if (checked) 20.dp else 0.dp, spring(dampingRatio = .7f, stiffness = Spring.StiffnessMedium), label = "switch thumb")
    Box(modifier.minimumInteractiveComponentSize()
        .toggleable(checked, role = Role.Switch, onValueChange = {
            haptic.performHapticFeedback(if (it) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff); onCheckedChange(it)
        }), contentAlignment = Alignment.Center) {
        Box(Modifier.size(51.dp, 31.dp).clip(CircleShape).background(track).padding(2.dp)) {
            Box(Modifier.offset { androidx.compose.ui.unit.IntOffset(offset.roundToPx(), 0) }.size(27.dp).shadow(2.dp, CircleShape).background(Color.White, CircleShape))
        }
    }
}

/** iOS-style choice pill (a drop-in for the settings' FilterChips): white when selected, frosted when not. */
@Composable
internal fun IosChip(selected: Boolean, onClick: () -> Unit, label: @Composable () -> Unit, modifier: Modifier = Modifier) {
    val background by animateColorAsState(if (selected) Color.White else Color.White.copy(alpha = .1f), label = "chip")
    Box(modifier.heightIn(min = 36.dp).clip(RoundedCornerShape(FolioRadius.CONTROL.dp)).background(background)
        .selectable(selected, role = Role.RadioButton, onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center) {
        androidx.compose.material3.ProvideTextStyle(TextStyle(color = if (selected) Color.Black else Color.White, fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)) { label() }
    }
}

/** iOS slider: thin track filled in system blue with a round white thumb. */
@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
internal fun IosSlider(value: Float, onValueChange: (Float) -> Unit, valueRange: ClosedFloatingPointRange<Float>, modifier: Modifier = Modifier,
    interactionSource: androidx.compose.foundation.interaction.MutableInteractionSource? = null) {
    val interaction = interactionSource ?: androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Slider(value, onValueChange, modifier, valueRange = valueRange, interactionSource = interaction,
        thumb = { Box(Modifier.size(28.dp).shadow(3.dp, CircleShape).background(Color.White, CircleShape)) },
        track = { state ->
            val span = state.valueRange.endInclusive - state.valueRange.start
            val fraction = if (span > 0f) ((state.value - state.valueRange.start) / span).coerceIn(0f, 1f) else 0f
            Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(Color.White.copy(alpha = .22f))) {
                Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(LocalAccent.current.fill))
            }
        })
}

/** iOS search field: frosted rounded capsule, magnifier, placeholder and a clear button. */
@Composable
internal fun IosSearchField(query: String, onQuery: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier,
    fieldModifier: Modifier = Modifier, ink: Color = Color.White, onSearch: (() -> Unit)? = null) {
    // Fixed height, so the field doesn't grow when the clear button appears.
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(ink.copy(alpha = .12f))
        .height(40.dp).padding(start = 10.dp, end = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Rounded.Search, null, tint = ink.copy(alpha = .55f), modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) androidx.compose.material3.Text(placeholder, color = ink.copy(alpha = .55f), fontSize = FolioType.BODY.sp, maxLines = 1)
            androidx.compose.foundation.text.BasicTextField(query, onQuery, fieldModifier.fillMaxWidth(), singleLine = true,
                textStyle = TextStyle(color = ink, fontSize = FolioType.BODY.sp), cursorBrush = androidx.compose.ui.graphics.SolidColor(ink),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { onSearch?.invoke() }))
        }
        if (query.isNotEmpty()) Box(Modifier.size(36.dp).clip(androidx.compose.foundation.shape.CircleShape).clickable { onQuery("") },
            contentAlignment = Alignment.Center) {
            androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Rounded.Cancel, "Clear search", tint = ink.copy(alpha = .5f), modifier = Modifier.size(20.dp))
        }
    }
}

/** iOS blue (or red) text action row inside a grouped list. */
@Composable
internal fun IosActionRow(text: String, tag: String? = null, destructive: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val color = if (destructive) FolioColors.RedOnDark else LocalAccent.current.ink
    androidx.compose.material3.Text(text, color = if (enabled) color else Color.White.copy(alpha = .3f), fontSize = FolioType.BODY.sp,
        modifier = Modifier.fillMaxWidth().heightIn(min = FolioRow.ACTION.dp).clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp).then(if (tag != null) Modifier.testTag(tag) else Modifier))
}

/** A row that opens another page, like iOS Settings: label, current value and a chevron. */
@Composable
internal fun IosNavRow(text: String, value: String?, onClick: () -> Unit, tag: String? = null) {
    Row(Modifier.fillMaxWidth().heightIn(min = FolioRow.NAV.dp).clickable(role = Role.Button, onClick = onClick)
        .then(if (tag != null) Modifier.testTag(tag) else Modifier), verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.Text(text, color = Color.White, fontSize = FolioType.BODY.sp, modifier = Modifier.weight(1f))
        value?.let { androidx.compose.material3.Text(it, color = Color.White.copy(alpha = .55f), fontSize = FolioType.BODY.sp) }
        androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Rounded.ChevronRight, null, tint = Color.White.copy(alpha = .3f))
    }
}

/** Used when haptic feedback is turned off in settings. */
internal object NoHaptics : androidx.compose.ui.hapticfeedback.HapticFeedback {
    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) = Unit
}

/**
 * Android's "Remove animations" (animator duration scale 0), treated like iOS Reduce Motion: no wiggling,
 * no sliding pages, no fold blur. Read once per composition of the provider.
 */
internal val LocalReduceMotion = androidx.compose.runtime.staticCompositionLocalOf { false }

internal fun reduceMotionEnabled(context: android.content.Context): Boolean =
    runCatching { android.provider.Settings.Global.getFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) }
        .getOrDefault(1f) == 0f

/**
 * iOS pop-up button row (Settings' "Menu" style): the title on the left, the current choice on the right with ⌃⌄, and a
 * tap opens a compact menu of choices under it with a checkmark on the current one. Use for three or more choices.
 */
@Composable
internal fun <T> IosMenuRow(title: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit,
    modifier: Modifier = Modifier, tag: String? = null, enabled: Boolean = true) {
    var open by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val current = options.firstOrNull { it.first == selected }?.second ?: ""
    Row(modifier.fillMaxWidth().heightIn(min = FolioRow.ACTION.dp).clip(RoundedCornerShape(FolioRadius.CONTROL.dp))
        .clickable(enabled = enabled, role = Role.Button, onClickLabel = "Choose $title") { open = true }
        .then(if (tag != null) Modifier.testTag(tag) else Modifier)
        .androidxAlpha(if (enabled) 1f else .4f), verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.Text(title, color = Color.White, fontSize = FolioType.BODY.sp, modifier = Modifier.weight(1f))
        Box {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Text(current, color = Color.White.copy(alpha = .55f), fontSize = FolioType.BODY.sp, maxLines = 1)
                androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Rounded.UnfoldMore, null, tint = Color.White.copy(alpha = .4f),
                    modifier = Modifier.padding(start = 2.dp).size(18.dp))
            }
            FolioMenuPopup(open, onDismiss = { open = false }, tag = tag) {
                    options.forEachIndexed { index, (value, label) ->
                        if (index > 0) androidx.compose.material3.HorizontalDivider(color = Color.White.copy(alpha = .1f), thickness = .5.dp)
                        Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.SegmentTick); open = false; if (value != selected) onSelect(value)
                        }.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            // iOS menus mark the choice with a leading checkmark and keep the labels lined up.
                            Box(Modifier.size(24.dp), contentAlignment = Alignment.CenterStart) {
                                if (value == selected) androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Rounded.Check, null,
                                    tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                            androidx.compose.material3.Text(label, color = Color.White, fontSize = FolioType.BODY.sp)
                        }
                    }
            }
        }
    }
}

/**
 * The shell every Folio pop-up menu shares: Android's anchored menu doing the placing and the dismissing, wearing
 * Folio's dark iOS surface. It hangs under the control that opened it, flips above it near the bottom of the window,
 * stays inside the window, sizes itself to its rows between 200 and 280 dp, scrolls when there are more rows than
 * there's room for (a folder on a page of twelve offers a row per page), grows from the corner it was opened at the
 * way iOS's menus do, and closes on a tap outside it or on Back. Use it with [MenuRow] items for a menu of actions,
 * as the folder panel does.
 *
 * It was hand-placed until 0.6.7 (#117): a `Popup` aligned to the window's top end whose content filled the window
 * and then centered itself in it, so the menu appeared in the middle of the screen instead of on its row, and its
 * window, as big as the screen and transparent everywhere the menu wasn't, swallowed every tap meant to close it.
 *
 * ADP-12: a menu anchored to its control is clear of a half-open hinge because the control is. Settings puts its
 * divider on the crease and the folder panel steps off it, and a menu hangs from the control's edge into the panel
 * the control is already on. Displacing the menu away from its control instead is what caused #117.
 */
@Composable
internal fun FolioMenuPopup(expanded: Boolean, onDismiss: () -> Unit, tag: String? = null, content: @Composable ColumnScope.() -> Unit) {
    androidx.compose.material3.DropdownMenu(expanded, onDismiss,
        modifier = Modifier.widthIn(min = 200.dp, max = 280.dp).then(if (tag != null) Modifier.testTag("$tag-menu") else Modifier),
        shape = RoundedCornerShape(FolioRadius.CARD.dp), containerColor = Color(0xFF3A3A3C),
        tonalElevation = 0.dp, shadowElevation = 24.dp,
        border = androidx.compose.foundation.BorderStroke(.5.dp, Color.White.copy(alpha = .12f)),
        content = content)
}

/** How much weight a [FolioButton] carries, the way iOS's filled, tinted and plain buttons do. */
internal enum class FolioButtonStyle { FILLED, TONAL, PLAIN }

/**
 * Folio's button: a rounded iOS button instead of Material's. Use this rather than `Button`, `OutlinedButton` or
 * `FilledTonalButton` (docs/standards/design.md, DES-10). [FolioButtonStyle.FILLED] is the one thing a screen most
 * wants you to do, TONAL sits beside it, and PLAIN is a link-weight action.
 */
@Composable
internal fun FolioButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: FolioButtonStyle = FolioButtonStyle.FILLED,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    tag: String? = null,
    enabled: Boolean = true,
) {
    val accent = LocalAccent.current
    val ink = if (style == FolioButtonStyle.FILLED) Color.White else accent.ink
    val background = when (style) {
        FolioButtonStyle.FILLED -> accent.fill
        FolioButtonStyle.TONAL -> Color.White.copy(alpha = .12f)
        FolioButtonStyle.PLAIN -> Color.Transparent
    }
    Row(
        modifier
            .clip(RoundedCornerShape(FolioRadius.CARD.dp))
            .background(background)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            // 48 dp tall, so a finger has the whole button rather than just its text (A11Y-1).
            .heightIn(min = FolioRow.ACTION.dp)
            .padding(horizontal = 18.dp, vertical = 12.dp)
            .then(if (tag != null) Modifier.testTag(tag) else Modifier)
            .androidxAlpha(if (enabled) 1f else .4f),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let {
            androidx.compose.material3.Icon(it, null, tint = ink, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(FolioSpace.SNUG.dp))
        }
        // No maxLines: at 200% text, or in a language with longer words, the label wraps and the button grows
        // rather than losing its end to an ellipsis (A11Y-12).
        androidx.compose.material3.Text(
            text, color = ink, fontSize = FolioType.SUBHEAD.sp, fontWeight = FontWeight.SemiBold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

/** iOS segmented control: one rounded track with a sliding thumb behind the chosen option. Use for two or three short choices. */
@Composable
internal fun <T> IosSegmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier, tag: String? = null) {
    val haptic = LocalHapticFeedback.current
    val index = options.indexOfFirst { it.first == selected }.coerceAtLeast(0)
    androidx.compose.foundation.layout.BoxWithConstraints(modifier.fillMaxWidth().height(36.dp).clip(RoundedCornerShape(9.dp))
        .background(Color.White.copy(alpha = .12f)).padding(2.dp).then(if (tag != null) Modifier.testTag(tag) else Modifier)) {
        val segment = maxWidth / options.size
        val x by animateDpAsState(segment * index, spring(dampingRatio = .85f, stiffness = Spring.StiffnessMedium), label = "segment")
        Box(Modifier.offset { androidx.compose.ui.unit.IntOffset(x.roundToPx(), 0) }.width(segment).fillMaxHeight()
            .shadow(2.dp, RoundedCornerShape(7.dp)).clip(RoundedCornerShape(7.dp)).background(Color(0xFF636366)))
        Row(Modifier.fillMaxSize()) {
            options.forEach { (value, label) ->
                Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(7.dp))
                    .selectable(value == selected, role = Role.RadioButton) {
                        if (value != selected) { haptic.performHapticFeedback(HapticFeedbackType.SegmentTick); onSelect(value) }
                    }, contentAlignment = Alignment.Center) {
                    androidx.compose.material3.Text(label, color = Color.White, fontSize = FolioType.FOOTNOTE.sp, maxLines = 1,
                        fontWeight = if (value == selected) FontWeight.SemiBold else FontWeight.Medium)
                }
            }
        }
    }
}

private fun Modifier.androidxAlpha(alpha: Float) = graphicsLayer { this.alpha = alpha }
