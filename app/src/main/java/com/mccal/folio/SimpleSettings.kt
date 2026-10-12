package com.mccal.folio

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MailOutline
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** How long a helper holds the button on a phone with no screen lock: long enough that a stray tap never opens it. */
internal const val SIMPLE_HOLD_MS = 2_000L

// ---------- opening Helper Settings ----------

/**
 * Helper Settings opens with the phone's own screen lock: Android's prompt, with a fingerprint, face or the PIN. There is
 * no new secret to forget. A phone with no screen lock gets [onNoLock] instead, which asks for a 2-second hold.
 */
internal fun unlockHelper(context: Context, onOpen: () -> Unit, onNoLock: () -> Unit) {
    val authenticators = BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
    val secure = context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true
    val able = context.getSystemService(BiometricManager::class.java)?.canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS
    if (!secure || !able) { onNoLock(); return }
    runCatching {
        BiometricPrompt.Builder(context).setTitle(context.getString(R.string.simple_unlock_title))
            .setSubtitle(context.getString(R.string.simple_unlock_subtitle)).setAllowedAuthenticators(authenticators).build()
            .authenticate(CancellationSignal(), context.mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onOpen()
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    // The lock was removed after the check above. Cancelling, or too many tries, opens nothing.
                    if (errorCode == BiometricPrompt.BIOMETRIC_ERROR_NO_DEVICE_CREDENTIAL) onNoLock()
                }
            })
    }.onFailure { onNoLock() }
}

/**
 * No screen lock on this phone: hold the button for [SIMPLE_HOLD_MS] while a ring fills. Letting go early starts over.
 * Cancel comes first and is the default. For TalkBack, Switch Access and keyboards, which can't hold, an "Open Helper
 * Settings" accessibility action and Enter or Space open a plain confirm instead, with Cancel as its default too.
 */
@Composable
internal fun SimpleHoldDialog(onCancel: () -> Unit, onOpen: () -> Unit) {
    SimpleDialog(stringResource(R.string.simple_helper_settings), stringResource(R.string.simple_hold_body), onCancel) {
        val colors = simpleTheme
        val progress = remember { Animatable(0f) }
        val scope = rememberCoroutineScope()
        var holding by remember { mutableStateOf(false) }
        var confirming by remember { mutableStateOf(false) }
        val label = stringResource(if (holding) R.string.simple_keep_holding else R.string.simple_hold)
        val description = stringResource(R.string.simple_hold_description)
        val openLabel = stringResource(R.string.simple_unlock_title)
        Box(Modifier.align(Alignment.CenterHorizontally).padding(top = FolioSpace.SNUG.dp).size(140.dp * simpleGrowth(SimpleGrowth.LABEL))
            .clip(CircleShape).background(colors.background)
            .semantics {
                contentDescription = description; role = Role.Button
                customActions = listOf(CustomAccessibilityAction(openLabel) { confirming = true; true })
            }
            .onKeyEvent { event ->
                val open = event.type == KeyEventType.KeyUp && event.key in setOf(Key.Enter, Key.NumPadEnter, Key.Spacebar, Key.DirectionCenter)
                if (open) confirming = true
                open
            }
            .focusable()
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    holding = true
                    val fill = scope.launch { progress.animateTo(1f, tween(SIMPLE_HOLD_MS.toInt(), easing = LinearEasing)) }
                    // The time decides, not the ring: it opens after the full hold even if frames stall. Lifting the
                    // finger, or sliding it off, before then starts over.
                    val letGo = withTimeoutOrNull(SIMPLE_HOLD_MS) { waitForUpOrCancellation(); true }
                    fill.cancel()
                    holding = false
                    if (letGo == null) onOpen() else scope.launch { progress.snapTo(0f) }
                }
            }, contentAlignment = Alignment.Center) {
            val track = colors.line
            val fill = colors.accent
            Canvas(Modifier.fillMaxSize().padding(FolioSpace.SNUG.dp)) {
                val stroke = 8.dp.toPx()
                drawArc(track, 0f, 360f, false, style = Stroke(stroke), topLeft = Offset(stroke / 2, stroke / 2),
                    size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke))
                drawArc(fill, -90f, 360f * progress.value, false, style = Stroke(stroke, cap = StrokeCap.Round), topLeft = Offset(stroke / 2, stroke / 2),
                    size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke))
            }
            Text(label, color = colors.text, fontSize = simpleText(20f), fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                modifier = Modifier.padding(FolioSpace.LARGE.dp))
        }
        if (confirming) SimpleDialog(stringResource(R.string.simple_open_confirm_title), stringResource(R.string.simple_open_confirm_body),
            onCancel = { confirming = false }) {
            SimpleButton(stringResource(R.string.simple_open_settings), SimpleButtonKind.PLAIN) { confirming = false; onOpen() }
        }
    }
}

/**
 * Simple's confirm: opaque, in its own window, with Cancel first and the default (it takes focus), then [action].
 * Used for every question Simple asks, so each one reads and works the same.
 */
@Composable
internal fun SimpleDialog(title: String, body: String, onCancel: () -> Unit, action: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val view = LocalView.current
        LaunchedEffect(view) { (view.parent as? DialogWindowProvider)?.window?.let { it.setDimAmount(0f); it.setWindowAnimations(0) } }
        val colors = simpleTheme
        val fade = remember { Animatable(0f) }
        LaunchedEffect(Unit) { fade.animateTo(1f, tween(FolioMotion.FADE_MS)) }
        val cancelFocus = remember { FocusRequester() }
        LaunchedEffect(Unit) { runCatching { cancelFocus.requestFocus() } }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .62f)).windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(FolioSpace.LARGE.dp), contentAlignment = Alignment.Center) {
            Column(Modifier.widthIn(max = 460.dp).fillMaxWidth().graphicsLayer { alpha = fade.value }
                .clip(RoundedCornerShape(FolioRadius.SHEET_TOP.dp)).background(colors.raised)
                .verticalScroll(rememberScrollState()).padding(start = 18.dp, end = 18.dp, top = 22.dp, bottom = 18.dp)
                .semantics { paneTitle = title },
                verticalArrangement = Arrangement.spacedBy(FolioSpace.MEDIUM.dp)) {
                Text(title, Modifier.semantics { heading() }, color = colors.text, fontSize = simpleText(26f), fontWeight = FontWeight.Bold)
                Text(body, color = colors.text, fontSize = simpleText(19f))
                SimpleButton(stringResource(R.string.cancel), SimpleButtonKind.PRIMARY, modifier = Modifier.focusRequester(cancelFocus), onClick = onCancel)
                action()
            }
        }
    }
}

// ---------- Helper Settings and Edit Home ----------

/** The helper's pages: Helper Settings, Text Size, and Edit Home with Add, Reorder and Remove. */
@Composable
internal fun SimpleHelperPages(screen: SimpleScreen, state: LauncherState, setup: SimpleSetup, model: LauncherModel,
    label: (AppEntry) -> String, go: (SimpleScreen) -> Unit) {
    val byId = remember(state.apps) { state.apps.associateBy { it.id } }
    val onHome = setup.apps.filter { it in byId }
    when (screen) {
        SimpleScreen.SETTINGS -> SimpleHelperSettings(setup, onHome.size, model, go)
        SimpleScreen.TEXT_SIZE -> SimplePage(primary = { SimpleButton(stringResource(R.string.done), SimpleButtonKind.PRIMARY) { go(SimpleScreen.SETTINGS) } },
            title = stringResource(R.string.simple_text_size)) {
            SimpleLead(stringResource(R.string.simple_text_size_note))
            SimpleChoiceGroup(stringResource(R.string.simple_text_size)) {
                SimpleTextSize.entries.forEach { size ->
                    SimpleRow(stringResource(size.label), mark = setup.textSize == size, role = Role.RadioButton) {
                        model.setSimpleSetup(setup.copy(textSize = size))
                    }
                }
            }
        }
        SimpleScreen.EDIT -> SimpleEditHome(onHome.size, go)
        SimpleScreen.ADD -> SimpleAddApps(state, setup, onHome, label, model, go)
        SimpleScreen.REORDER -> SimpleReorder(onHome.mapNotNull { byId[it] }, setup, label, model, go)
        SimpleScreen.REMOVE -> SimpleRemove(onHome.mapNotNull { byId[it] }, setup, label, model, go)
        SimpleScreen.HOME, SimpleScreen.MORE -> Unit
    }
}

@Composable
private fun SimpleHelperSettings(setup: SimpleSetup, appCount: Int, model: LauncherModel, go: (SimpleScreen) -> Unit) {
    var leaving by remember { mutableStateOf(false) }
    SimplePage(primary = { SimpleButton(stringResource(R.string.simple_back_to_home), SimpleButtonKind.PLAIN, Icons.Rounded.ChevronLeft) { go(SimpleScreen.HOME) } },
        title = stringResource(R.string.simple_helper_settings)) {
        SimpleRow(stringResource(R.string.simple_edit_home), pluralStringResource(R.plurals.simple_apps_on_home, appCount, appCount),
            icon = Icons.AutoMirrored.Rounded.ViewList, iconColor = simpleTheme.accent, minHeight = 88.dp) { go(SimpleScreen.EDIT) }
        SimpleChoiceGroup(stringResource(R.string.simple_home_style)) {
            SimpleRow(stringResource(R.string.simple_style_list), mark = setup.style == SimpleStyle.LIST, role = Role.RadioButton) {
                model.setSimpleSetup(setup.copy(style = SimpleStyle.LIST))
            }
            SimpleRow(stringResource(R.string.simple_style_grid), mark = setup.style == SimpleStyle.GRID, role = Role.RadioButton) {
                model.setSimpleSetup(setup.copy(style = SimpleStyle.GRID))
            }
        }
        SimpleRow(stringResource(R.string.simple_text_size), stringResource(R.string.simple_text_size_detail, stringResource(setup.textSize.label)),
            icon = Icons.Rounded.TextFields) { go(SimpleScreen.TEXT_SIZE) }
        // How families tell us what works during the beta: an email they write, with nothing attached.
        val context = LocalContext.current
        SimpleRow(stringResource(R.string.simple_send_feedback), stringResource(R.string.simple_send_feedback_detail),
            icon = Icons.Rounded.MailOutline) { simpleFeedback(context) }
        SimpleRow(stringResource(R.string.simple_all_settings), stringResource(R.string.simple_all_settings_detail),
            icon = Icons.Rounded.Settings) { openAllSettings(context) }
        // Last, in a group of its own and in red, as Apple's Settings sets apart what can't be done by accident: easy to
        // find on purpose, hard to reach by mistake, like Assistive Access's exit.
        Spacer(Modifier.size(FolioSpace.LARGE.dp))
        SimpleRow(stringResource(R.string.simple_leave), stringResource(R.string.simple_leave_detail), icon = Icons.AutoMirrored.Rounded.Logout,
            destructive = true) { leaving = true }
        SimpleLead(stringResource(R.string.simple_settings_note))
    }
    if (leaving) SimpleDialog(stringResource(R.string.simple_leave_title), stringResource(R.string.simple_leave_body), onCancel = { leaving = false }) {
        SimpleButton(stringResource(R.string.simple_leave), SimpleButtonKind.DANGER) { leaving = false; model.leaveSimple() }
    }
}

/** Edit Home: a mode of its own, like Lively's Options, with Add, Reorder and Remove. Home stays locked meanwhile. */
@Composable
private fun SimpleEditHome(appCount: Int, go: (SimpleScreen) -> Unit) {
    val room = SIMPLE_MAX_APPS - appCount
    SimplePage(primary = { SimpleButton(stringResource(R.string.done), SimpleButtonKind.PRIMARY) { go(SimpleScreen.SETTINGS) } },
        title = stringResource(R.string.simple_edit_home)) {
        SimpleLead(stringResource(R.string.simple_edit_note))
        SimpleRow(stringResource(R.string.simple_add_apps),
            if (room > 0) pluralStringResource(R.plurals.simple_room_for, room, room) else stringResource(R.string.simple_home_full),
            icon = Icons.Rounded.Add, iconColor = FolioColors.GreenLight) { go(SimpleScreen.ADD) }
        SimpleRow(stringResource(R.string.simple_reorder_apps), icon = Icons.Rounded.SwapVert, iconColor = FolioColors.Blue) { go(SimpleScreen.REORDER) }
        SimpleRow(stringResource(R.string.simple_remove_apps), icon = Icons.Rounded.Remove) { go(SimpleScreen.REMOVE) }
    }
}

/** Add Apps: pick up to the room left, then Add to Home. Apps already on Home and hidden apps aren't listed. */
@Composable
private fun SimpleAddApps(state: LauncherState, setup: SimpleSetup, onHome: List<String>, label: (AppEntry) -> String, model: LauncherModel, go: (SimpleScreen) -> Unit) {
    val picked = remember { mutableStateListOf<String>() }
    val choices = remember(state.apps, onHome) {
        state.apps.filter { it.id !in onHome && it.id !in state.hiddenApps && it.available }.sortedBy { it.label.lowercase() }
    }
    val room = SIMPLE_MAX_APPS - onHome.size - picked.size
    SimplePage(primary = { SimpleButton(stringResource(R.string.add_to_home), SimpleButtonKind.PRIMARY) {
        model.setSimpleSetup(setup.copy(apps = onHome + picked)); go(SimpleScreen.EDIT)
    } }, title = stringResource(R.string.simple_add_apps)) {
        SimpleStatus(pluralStringResource(R.plurals.simple_selected, picked.size, picked.size),
            if (room > 0) pluralStringResource(R.plurals.simple_room_for_sentence, room, room) else stringResource(R.string.simple_home_full_short))
        choices.forEach { app ->
            val on = app.id in picked
            SimpleAppChoice(app, label, on, enabled = on || room > 0) { if (on) picked.remove(app.id) else if (room > 0) picked.add(app.id) }
        }
        SimpleButton(stringResource(R.string.cancel), SimpleButtonKind.PLAIN) { go(SimpleScreen.EDIT) }
    }
}

/** Reorder Apps: tap an app, then Move Up or Move Down; nothing is dragged. Save Order puts it on Home. */
@Composable
private fun SimpleReorder(apps: List<AppEntry>, setup: SimpleSetup, label: (AppEntry) -> String, model: LauncherModel, go: (SimpleScreen) -> Unit) {
    val order = remember { mutableStateListOf<String>().apply { addAll(apps.map { it.id }) } }
    var chosen by remember { mutableStateOf<String?>(null) }
    val byId = apps.associateBy { it.id }
    fun move(step: Int) {
        val from = order.indexOf(chosen ?: return)
        val to = from + step
        if (from < 0 || to !in order.indices) return
        order[from] = order[to].also { order[to] = order[from] }
    }
    SimplePage(primary = { SimpleButton(stringResource(R.string.simple_save_order), SimpleButtonKind.PRIMARY) {
        model.setSimpleSetup(setup.copy(apps = order.toList())); go(SimpleScreen.EDIT)
    } }, title = stringResource(R.string.simple_reorder_apps)) {
        SimpleLead(stringResource(R.string.simple_reorder_note))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(FolioSpace.MEDIUM.dp)) {
            SimpleButton(stringResource(R.string.move_up), SimpleButtonKind.PLAIN, Icons.Rounded.ArrowUpward, Modifier.weight(1f)) { move(-1) }
            SimpleButton(stringResource(R.string.move_down), SimpleButtonKind.PLAIN, Icons.Rounded.ArrowDownward, Modifier.weight(1f)) { move(1) }
        }
        SimpleChoiceGroup(stringResource(R.string.simple_home_order)) {
            order.mapNotNull { byId[it] }.forEach { app ->
                SimpleRow(label(app), mark = chosen == app.id, role = Role.RadioButton, app = app) { chosen = app.id }
            }
        }
        SimpleButton(stringResource(R.string.cancel), SimpleButtonKind.PLAIN) { go(SimpleScreen.EDIT) }
    }
}

/** Remove Apps: pick, then Remove from Home, which asks first with Cancel as the default. Apps stay on the phone. */
@Composable
private fun SimpleRemove(apps: List<AppEntry>, setup: SimpleSetup, label: (AppEntry) -> String, model: LauncherModel, go: (SimpleScreen) -> Unit) {
    val picked = remember { mutableStateListOf<String>() }
    var confirming by remember { mutableStateOf(false) }
    SimplePage(primary = { SimpleButton(stringResource(R.string.simple_remove_from_home), SimpleButtonKind.PRIMARY) { if (picked.isNotEmpty()) confirming = true } },
        title = stringResource(R.string.simple_remove_apps)) {
        SimpleStatus(pluralStringResource(R.plurals.simple_selected, picked.size, picked.size), stringResource(R.string.simple_remove_note))
        apps.forEach { app ->
            val on = app.id in picked
            SimpleAppChoice(app, label, on, enabled = true) { if (on) picked.remove(app.id) else picked.add(app.id) }
        }
        SimpleButton(stringResource(R.string.cancel), SimpleButtonKind.PLAIN) { go(SimpleScreen.EDIT) }
    }
    if (confirming) {
        val names = apps.filter { it.id in picked }.joinToString(stringResource(R.string.simple_list_separator)) { label(it) }
        SimpleDialog(pluralStringResource(R.plurals.simple_remove_title, picked.size, picked.size),
            pluralStringResource(R.plurals.simple_remove_body, picked.size, picked.size, names), onCancel = { confirming = false }) {
            SimpleButton(stringResource(R.string.remove), SimpleButtonKind.DANGER) {
                confirming = false
                model.setSimpleSetup(setup.copy(apps = setup.apps.filterNot { it in picked }))
                go(SimpleScreen.EDIT)
            }
        }
    }
}

// ---------- rows ----------

/** A short explanation under a title. */
@Composable
internal fun SimpleLead(text: String) {
    Text(text, Modifier.padding(horizontal = FolioSpace.TINY.dp), color = simpleTheme.secondary, fontSize = simpleText(19f))
}

/** How many are picked and how much room is left, read out as it changes. */
@Composable
private fun SimpleStatus(first: String, second: String) {
    Text(stringResource(R.string.simple_two_sentences, first, second), Modifier.padding(horizontal = FolioSpace.TINY.dp)
        .semantics { liveRegion = LiveRegionMode.Polite }, color = simpleTheme.secondary, fontSize = simpleText(19f))
}

/** A group heading over rows that belong together, like Home Style's two choices. */
@Composable
private fun SimpleChoiceGroup(title: String, rows: @Composable () -> Unit) {
    Text(title, Modifier.padding(start = FolioSpace.TINY.dp, end = FolioSpace.TINY.dp, top = FolioSpace.COMFY.dp).semantics { heading() },
        color = simpleTheme.secondary, fontSize = simpleText(17f))
    rows()
}

/** An app with a check, for Add and Remove. Phone says it shows as Call on Home. */
@Composable
private fun SimpleAppChoice(app: AppEntry, label: (AppEntry) -> String, on: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val shown = label(app)
    SimpleRow(app.label, if (shown != app.label) stringResource(R.string.simple_shows_as, shown) else null, mark = on,
        role = Role.Checkbox, app = app, enabled = enabled, onClick = onClick)
}

/**
 * A Simple row: 72 dp at least, the whole row the target, an icon (an app's or a symbol), the title and an optional
 * second line, and a round check for a choice ([mark] not null). TalkBack hears one control with its state.
 */
@Composable
internal fun SimpleRow(title: String, subtitle: String? = null, icon: ImageVector? = null, iconColor: Color = FolioColors.Gray,
    mark: Boolean? = null, role: Role = Role.Button, app: AppEntry? = null, minHeight: Dp = 72.dp, enabled: Boolean = true,
    destructive: Boolean = false, onClick: () -> Unit) {
    val colors = simpleTheme
    val shape = RoundedCornerShape(FolioRadius.GROUPED_CARD.dp)
    val target = when {
        mark != null && role == Role.Checkbox -> Modifier.toggleable(mark, enabled = enabled, role = role) { onClick() }
        mark != null -> Modifier.selectable(mark, enabled = enabled, role = role, onClick = onClick)
        else -> Modifier.simpleClickable(role, onClick)
    }
    Row(Modifier.fillMaxWidth().heightIn(min = minHeight).clip(shape).background(colors.tile).then(target)
        .graphicsLayer { alpha = if (enabled) 1f else .5f }
        .padding(horizontal = FolioSpace.LARGE.dp, vertical = FolioSpace.COMPACT.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FolioSpace.LARGE.dp)) {
        when {
            app != null -> AppIcon(app, null, Modifier.size(48.dp), shape = RoundedCornerShape(FolioRadius.CARD.dp), badge = false)
            icon != null -> SimpleGlyph(icon, if (destructive) colors.danger else iconColor, 48.dp)
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = if (destructive) colors.danger else colors.text, fontSize = simpleText(22f), fontWeight = FontWeight.SemiBold)
            subtitle?.let { Text(it, color = colors.secondary, fontSize = simpleText(17f)) }
        }
        mark?.let { SimpleMark(it) }
    }
}

/** The round check of a Simple choice: filled with a tick when on, an empty ring when off. */
@Composable
private fun SimpleMark(on: Boolean) {
    val colors = simpleTheme
    Box(Modifier.size(36.dp).clip(CircleShape).then(if (on) Modifier.background(colors.accent) else Modifier.border(2.5.dp, colors.secondary, CircleShape)),
        contentAlignment = Alignment.Center) {
        if (on) Icon(Icons.Rounded.Check, null, tint = colors.onAccent, modifier = Modifier.size(24.dp))
    }
}

// ---------- turning Simple on: Settings › Accessibility › Experience ----------

/**
 * The Experience group at the top of Settings › Accessibility: Folio, Android style and Simple, each with what it does.
 * Shown only where [FeatureGate.SIMPLE_MODE] is open (Folio Dev and beta supporters), since Settings had no profile
 * picker before this preview. Picking Simple asks first, and says how to leave, before anything changes.
 */
@Composable
internal fun SimpleExperienceCard(state: LauncherState, model: LauncherModel) {
    val context = LocalContext.current
    if (!remember { FeatureGate.SIMPLE_MODE.isOpen(context) }) return
    var confirming by remember { mutableStateOf(false) }
    val now = state.profile()
    SettingsCard(stringResource(R.string.experience)) {
        listOf(ExperienceProfile.FOLIO, ExperienceProfile.ANDROID_STYLE, ExperienceProfile.SIMPLE).forEach { profile ->
            ExperienceRow(profile, now == profile) {
                when {
                    profile == ExperienceProfile.SIMPLE -> confirming = true
                    // From All Settings while Simple is on: leave it properly first, so what it saved comes back.
                    now == ExperienceProfile.SIMPLE -> { model.leaveSimple(); model.setExperienceProfile(profile) }
                    else -> model.setExperienceProfile(profile)
                }
            }
        }
        if (now == ExperienceProfile.CUSTOM) CardNote(stringResource(R.string.profile_custom_detail))
        CardNote(stringResource(R.string.simple_experience_note))
    }
    if (confirming) SimpleTurnOnSheet(onCancel = { confirming = false }) {
        confirming = false
        val apps = state.apps
        val appContext = context.applicationContext
        model.turnOnSimple { simpleRoleApps(appContext, apps).inOrder() }
    }
}

/** One profile in the Experience group: its name, what it does, and a check on the one in use. A radio button to TalkBack. */
@Composable
private fun ExperienceRow(profile: ExperienceProfile, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = FolioRow.NAV.dp).selectable(selected, role = Role.RadioButton) { if (!selected) onClick() }
        .padding(vertical = FolioSpace.COMPACT.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(profile.label), color = Color.White, fontSize = FolioType.BODY.sp)
            Text(stringResource(profile.detail), color = FolioColors.SecondaryLabel, fontSize = FolioType.FOOTNOTE.sp)
        }
        Spacer(Modifier.width(FolioSpace.MEDIUM.dp))
        if (selected) Icon(Icons.Rounded.Check, null, tint = LocalAccent.current.ink, modifier = Modifier.size(20.dp))
        else Spacer(Modifier.size(20.dp))
    }
}

/**
 * "Use Simple?": what changes, that the current Home is saved, and the way out, all before it starts, as Assistive
 * Access shows its exit at setup. Settings' own look, since it opens over Settings.
 */
@Composable
private fun SimpleTurnOnSheet(onCancel: () -> Unit, onUse: () -> Unit) {
    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val view = LocalView.current
        LaunchedEffect(view) { (view.parent as? DialogWindowProvider)?.window?.setDimAmount(0f) }
        val title = stringResource(R.string.simple_use_title)
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .5f)).windowInsetsPadding(WindowInsets.navigationBars),
            contentAlignment = Alignment.BottomCenter) {
            Column(Modifier.widthIn(max = 520.dp).fillMaxWidth()
                .clip(RoundedCornerShape(topStart = FolioRadius.SHEET_TOP.dp, topEnd = FolioRadius.SHEET_TOP.dp))
                .background(FolioColors.SheetSurface).verticalScroll(rememberScrollState())
                .padding(horizontal = FolioSpace.XL.dp, vertical = FolioSpace.MEDIUM.dp)
                .semantics { paneTitle = title },
                verticalArrangement = Arrangement.spacedBy(FolioSpace.SMALL.dp)) {
                Box(Modifier.align(Alignment.CenterHorizontally).size(width = 36.dp, height = 5.dp).clip(CircleShape).background(Color.White.copy(alpha = .3f)))
                Text(title, Modifier.padding(top = FolioSpace.SMALL.dp).semantics { heading() },
                    color = Color.White, fontSize = FolioType.TITLE.sp, fontWeight = FontWeight.Bold)
                TurnOnLine(Icons.Rounded.TouchApp, stringResource(R.string.simple_change_buttons))
                TurnOnLine(Icons.Rounded.Lock, stringResource(R.string.simple_change_locked))
                TurnOnLine(Icons.Rounded.VisibilityOff, stringResource(R.string.simple_change_hidden))
                TurnOnLine(Icons.Rounded.Contrast, stringResource(R.string.simple_change_solid))
                TurnOnLine(Icons.Rounded.Save, stringResource(R.string.simple_change_saved))
                Text(stringResource(R.string.simple_how_to_leave), color = FolioColors.SecondaryLabel, fontSize = FolioType.SUBHEAD.sp,
                    modifier = Modifier.padding(vertical = FolioSpace.SNUG.dp))
                SheetButton(stringResource(R.string.simple_use), filled = true, onClick = onUse)
                SheetButton(stringResource(R.string.cancel), filled = false, onClick = onCancel)
                Text(stringResource(R.string.simple_preview_label), Modifier.fillMaxWidth().padding(top = FolioSpace.TINY.dp),
                    color = FolioColors.SecondaryLabel, fontSize = FolioType.FOOTNOTE.sp, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun TurnOnLine(icon: ImageVector, text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = FolioSpace.TINY.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, null, tint = LocalAccent.current.ink, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(FolioSpace.MEDIUM.dp))
        Text(text, color = Color.White, fontSize = FolioType.SUBHEAD.sp)
    }
}

@Composable
private fun SheetButton(text: String, filled: Boolean, onClick: () -> Unit) {
    val accent = LocalAccent.current
    Box(Modifier.fillMaxWidth().heightIn(min = 50.dp).clip(RoundedCornerShape(FolioRadius.CARD.dp))
        .background(if (filled) accent.fill else Color.White.copy(alpha = .08f)).simpleClickable(Role.Button, onClick),
        contentAlignment = Alignment.Center) {
        Text(text, color = if (filled) Color.White else accent.ink, fontSize = FolioType.BODY.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** The app for each of Simple's six starting places, found by role and intent; null where nothing answers. */
internal data class SimpleRoleApps(val call: String?, val messages: String?, val camera: String?, val photos: String?,
    val clock: String?, val contacts: String?) {
    /** In Home's order: Call, Messages, Camera, Photos, Clock, Contacts. One app fills one place. */
    fun inOrder(): List<String> = listOfNotNull(call, messages, camera, photos, clock, contacts).distinct().take(SIMPLE_MAX_APPS)
}

/**
 * Simple's starting apps, by what they do rather than by package name, so it works on Samsung, Pixel and anything
 * else: the default dialer and texting app (the dialer and SMS role holders, read without a permission), and the apps
 * that take a photo, show the gallery, show alarms and show contacts. Only apps Home can launch count; a place nothing
 * answers for stays empty, so nothing is made up.
 */
internal fun simpleRoleApps(context: Context, apps: List<AppEntry>): SimpleRoleApps {
    val pm = context.packageManager
    val personal = apps.filter { !it.isWork && it.available && !it.isShortcut }
    fun entryFor(pkg: String?, activity: String? = null): String? {
        val matches = personal.filter { it.packageName == (pkg ?: return null) }
        return (matches.firstOrNull { it.component.className == activity } ?: matches.firstOrNull())?.id
    }
    fun resolved(intent: Intent): String? {
        val info = runCatching { pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo }.getOrNull()
        // "android" is the chooser: no default is set, so the first app that can do it.
        if (info != null && info.packageName != "android") entryFor(info.packageName, info.name)?.let { return it }
        return runCatching { pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY) }.getOrDefault(emptyList())
            .firstNotNullOfOrNull { entryFor(it.activityInfo.packageName, it.activityInfo.name) }
    }
    val dial = Intent(Intent.ACTION_DIAL)
    val dialer = runCatching { context.getSystemService(android.telecom.TelecomManager::class.java)?.defaultDialerPackage }.getOrNull()
    val sms = runCatching { android.provider.Telephony.Sms.getDefaultSmsPackage(context) }.getOrNull()
    return SimpleRoleApps(
        call = dialer?.let { resolved(Intent(dial).setPackage(it)) ?: entryFor(it) } ?: resolved(dial),
        messages = entryFor(sms) ?: resolved(Intent(Intent.ACTION_SENDTO, android.net.Uri.parse("smsto:"))),
        camera = resolved(Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)),
        photos = resolved(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_GALLERY))
            ?: resolved(Intent(Intent.ACTION_VIEW).setDataAndType(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*")),
        clock = resolved(Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS)),
        contacts = resolved(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CONTACTS))
            ?: resolved(Intent(Intent.ACTION_VIEW, android.provider.ContactsContract.Contacts.CONTENT_URI)),
    )
}

/** Feedback from a helper, by email: only what they write. Nothing is attached and no report is made (Diagnostics). */
internal fun simpleFeedback(context: Context): Boolean {
    val email = Diagnostics.hasMailApp(context)
    val send = Intent(Intent.ACTION_SEND).setType(Diagnostics.mimeFor(email))
        .putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.simple_feedback_subject))
        .apply { if (email) putExtra(Intent.EXTRA_EMAIL, arrayOf(Diagnostics.SUPPORT_EMAIL)) }
    return runCatching {
        context.startActivity(Intent.createChooser(send, context.getString(R.string.simple_send_feedback)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.isSuccess
}

/**
 * Folio's full Settings, from Helper Settings (already unlocked). Simple steps aside while it is open and comes back
 * when it closes; Settings opens the way Android's own "app settings" link opens it.
 */
internal fun openAllSettings(context: Context) {
    SimpleQuiet.allSettings.value = true
    runCatching {
        context.startActivity(Intent(Intent.ACTION_APPLICATION_PREFERENCES).setClass(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure { SimpleQuiet.allSettings.value = false }
}
