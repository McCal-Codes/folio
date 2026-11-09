package com.mccal.folio

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Where the Icon Actions editor is: the three gestures, then the picker for one of them and what it opens. */
private sealed interface IconActionsScreen {
    data object Gestures : IconActionsScreen
    data class Picker(val gesture: IconGestureKind) : IconActionsScreen
    data class Category(val gesture: IconGestureKind, val category: ActionCategory) : IconActionsScreen
    data class Access(val gesture: IconGestureKind, val category: ActionCategory?) : IconActionsScreen
    data class ChooseApp(val gesture: IconGestureKind) : IconActionsScreen
    data class ChooseShortcut(val gesture: IconGestureKind) : IconActionsScreen
    data class Caution(val gesture: IconGestureKind) : IconActionsScreen
}

internal fun IconActions.get(gesture: IconGestureKind): ActionRef? = when (gesture) {
    IconGestureKind.UP -> up; IconGestureKind.DOWN -> down; IconGestureKind.DOUBLE -> double
}

internal fun IconActions.with(gesture: IconGestureKind, ref: ActionRef?): IconActions = when (gesture) {
    IconGestureKind.UP -> copy(up = ref); IconGestureKind.DOWN -> copy(down = ref); IconGestureKind.DOUBLE -> copy(double = ref)
}

/** The Android release an API level shipped in, for "Needs Android 16 or newer". */
internal fun androidRelease(sdk: Int): Int = when { sdk >= 33 -> sdk - 20; sdk >= 31 -> 12; else -> sdk }

@androidx.annotation.StringRes
internal fun gestureName(gesture: IconGestureKind) = when (gesture) {
    IconGestureKind.UP -> R.string.icon_action_swipe_up
    IconGestureKind.DOWN -> R.string.icon_action_swipe_down
    IconGestureKind.DOUBLE -> R.string.icon_action_double_tap
}

/**
 * Icon Actions for one app, as in the Lab scene `icon-actions`: what a swipe up, a swipe down or a double tap runs. Tap and
 * press and hold keep working as they do. Every change is saved through [onChange] at once, like the icon's looks.
 */
@Composable
internal fun IconActionsEditor(app: AppEntry, current: IconActions, apps: List<AppEntry>, onChange: (IconActions) -> Unit, onBack: () -> Unit,
    env: (android.content.Context) -> ActionEnv = ActionEnv::live, backLabel: String = stringResource(R.string.edit_icon)) {
    // The actions as they were when the editor opened, so every change made here can be undone at once.
    val opened = remember(app.id) { current }
    val context = LocalContext.current
    val registry = ActionRegistry.standard
    var screen by remember { mutableStateOf<IconActionsScreen>(IconActionsScreen.Gestures) }
    var last by remember { mutableStateOf<IconGestureKind?>(null) }
    // Read again whenever Folio comes back, so turning the service on in Android's settings shows at once.
    var resumed by remember { mutableIntStateOf(0) }
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) { resumed++; onPauseOrDispose { } }
    val verdict: (ActionRef) -> ActionVerdict = remember(resumed) {
        val phone = env(context); { ref -> registry.verdict(ref, phone, ActionSource.ICON) }
    }
    fun set(gesture: IconGestureKind, ref: ActionRef?) {
        onChange(current.with(gesture, ref)); last = gesture.takeIf { ref != null }
        val disruptive = ref?.let { registry.spec(it.id)?.risk == OperationRisk.DISRUPTIVE } == true
        screen = if (disruptive && gesture == IconGestureKind.DOUBLE) IconActionsScreen.Caution(gesture) else IconActionsScreen.Gestures
    }
    fun pick(gesture: IconGestureKind, row: ActionRow, from: ActionCategory?) = when {
        row.dimmed -> Unit
        row.verdict == ActionVerdict.NeedsAccessibility -> screen = IconActionsScreen.Access(gesture, from)
        row.spec.id == "app.open" -> screen = IconActionsScreen.ChooseApp(gesture)
        row.spec.id == "shortcut.open" -> screen = IconActionsScreen.ChooseShortcut(gesture)
        else -> set(gesture, ActionRef(row.spec.id))
    }

    Column(Modifier.fillMaxWidth().testTag("icon-actions-editor")) {
        when (val s = screen) {
            IconActionsScreen.Gestures -> {
                BackRow(backLabel, onBack)
                Title(stringResource(R.string.icon_actions_title))
                CardNote(stringResource(R.string.icon_actions_lead, app.label))
                SettingsCard(null) {
                    EditorRow(stringResource(R.string.icon_actions_tap), stringResource(R.string.icon_actions_tap_note), null, enabled = false)
                    IconGestureKind.entries.forEach { gesture ->
                        val ref = current.get(gesture)
                        EditorRow(stringResource(gestureName(gesture)),
                            if (gesture == IconGestureKind.DOUBLE && ref != null) stringResource(R.string.icon_actions_double_waits, app.label) else null,
                            actionName(ref), tag = "icon-actions-${gesture.name.lowercase()}") { screen = IconActionsScreen.Picker(gesture) }
                    }
                }
                last?.let { gesture -> current.get(gesture)?.let { ref -> TryIt(ref, gesture, registry) } }
                CardNote(stringResource(R.string.icon_actions_footnote))
                if (current != opened) IosActionRow(stringResource(R.string.icon_actions_undo_changes), tag = "icon-actions-undo", onClick = { onChange(opened); last = null })
                if (current != IconActions()) IosActionRow(stringResource(R.string.icon_actions_reset), destructive = true, onClick = { onChange(IconActions()); last = null })
            }
            is IconActionsScreen.Picker -> {
                var query by remember { mutableStateOf("") }
                BackRow(stringResource(R.string.icon_actions_title)) { screen = IconActionsScreen.Gestures }
                Title(stringResource(gestureName(s.gesture)))
                IosSearchField(query, { query = it }, stringResource(R.string.icon_actions_search), Modifier.padding(vertical = FolioSpace.SMALL.dp))
                val selected = current.get(s.gesture)?.id
                if (query.isNotBlank()) {
                    val found = ActionCatalog.search(query, registry, verdict) { context.getString(it) }
                    SettingsCard(null) {
                        if (found.isEmpty()) EditorRow(stringResource(R.string.icon_actions_no_match), null, null, enabled = false)
                        found.forEach { ActionRowView(it, it.spec.id == selected) { pick(s.gesture, it, null) } }
                    }
                } else {
                    SettingsCard(null) {
                        EditorRow(stringResource(R.string.nothing), null, null, checked = selected == null, tag = "icon-actions-nothing") { set(s.gesture, null) }
                    }
                    SettingsCard(stringResource(R.string.icon_actions_suggested)) {
                        ActionCatalog.rows(ActionCatalog.suggested, registry, verdict).forEach { ActionRowView(it, it.spec.id == selected) { pick(s.gesture, it, null) } }
                    }
                    SettingsCard(stringResource(R.string.icon_actions_all)) {
                        ActionCategory.entries.forEach { category ->
                            val off = category == ActionCategory.ACCESSIBILITY && verdict(ActionRef("a11y.back")) == ActionVerdict.NeedsAccessibility
                            EditorRow(stringResource(category.title), if (off) stringResource(R.string.capability_off) else null,
                                category.ids.size.toString(), tag = "icon-actions-category-${category.name.lowercase()}") {
                                screen = IconActionsScreen.Category(s.gesture, category)
                            }
                        }
                    }
                }
            }
            is IconActionsScreen.Category -> {
                val back = { screen = IconActionsScreen.Picker(s.gesture) }
                BackRow(stringResource(gestureName(s.gesture)), back)
                Title(stringResource(s.category.title))
                val rows = ActionCatalog.category(s.category, registry, verdict)
                if (rows.any { it.verdict == ActionVerdict.NeedsAccessibility }) SettingsCard(null) {
                    Text(stringResource(R.string.icon_actions_access_banner), color = Color.White, fontSize = FolioType.BODY.sp)
                    CardAction(stringResource(R.string.turn_on), onClick = { screen = IconActionsScreen.Access(s.gesture, s.category) },
                        modifier = Modifier.testTag("icon-actions-turn-on"))
                }
                val selected = current.get(s.gesture)?.id
                SettingsCard(null) { rows.forEach { ActionRowView(it, it.spec.id == selected) { pick(s.gesture, it, s.category) } } }
                if (s.category == ActionCategory.PANELS) CardNote(stringResource(R.string.icon_actions_panels_note))
            }
            is IconActionsScreen.Access -> {
                val back = { screen = s.category?.let { IconActionsScreen.Category(s.gesture, it) } ?: IconActionsScreen.Picker(s.gesture) }
                BackRow(stringResource(s.category?.title ?: gestureName(s.gesture)), back)
                Title(stringResource(R.string.icon_actions_access_title))
                CardNote(stringResource(R.string.icon_actions_access_lead))
                CardNote(stringResource(R.string.icon_actions_access_off_note))
                IosActionRow(stringResource(R.string.icon_actions_access_open), onClick = {
                    runCatching { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    back()
                })
                IosActionRow(stringResource(R.string.not_now), onClick = back)
                CardNote(stringResource(R.string.icon_actions_access_restricted))
            }
            is IconActionsScreen.ChooseApp -> {
                var query by remember { mutableStateOf("") }
                BackRow(stringResource(gestureName(s.gesture))) { screen = IconActionsScreen.Picker(s.gesture) }
                Title(stringResource(R.string.icon_actions_choose_app))
                IosSearchField(query, { query = it }, stringResource(R.string.search_apps), Modifier.padding(vertical = FolioSpace.SMALL.dp))
                val shown = remember(apps, query) { apps.filter { !it.isShortcut && it.id != app.id && appMatches(it, query.trim()) } }
                SettingsCard(null) {
                    shown.forEach { other -> androidx.compose.runtime.key(other.id) {
                        AppPickRow(other) {
                            set(s.gesture, ActionRef("app.open", mapOf("pkg" to other.packageName, "component" to other.component.flattenToString(),
                                "user" to other.userSerial.toString(), "name" to other.label)))
                        }
                    } }
                }
            }
            is IconActionsScreen.ChooseShortcut -> {
                BackRow(stringResource(gestureName(s.gesture))) { screen = IconActionsScreen.Picker(s.gesture) }
                Title(stringResource(R.string.icon_actions_choose_shortcut))
                val shortcuts by produceState<List<QuickAction>?>(null, app.id) { value = withContext(Dispatchers.IO) { loadQuickActions(context, app, limit = 20) } }
                SettingsCard(null) {
                    val list = shortcuts
                    if (list != null && list.isEmpty()) EditorRow(stringResource(R.string.icon_actions_no_shortcuts, app.label), null, null, enabled = false)
                    list.orEmpty().forEach { shortcut ->
                        EditorRow(shortcut.label, null, null, tag = "icon-actions-shortcut-${shortcut.info.id}") {
                            set(s.gesture, ActionRef("shortcut.open", mapOf("pkg" to app.packageName, "id" to shortcut.info.id,
                                "user" to app.userSerial.toString(), "name" to shortcut.label)))
                        }
                    }
                }
            }
            is IconActionsScreen.Caution -> {
                BackRow(stringResource(R.string.icon_actions_title)) { screen = IconActionsScreen.Gestures }
                Title(stringResource(gestureName(s.gesture)))
                current.get(s.gesture)?.let { ref -> registry.spec(ref.id)?.let { spec ->
                    SettingsCard(null) { ActionRowView(ActionCatalog.row(spec, verdict(ref)), selected = true) {} }
                    TryIt(ref, s.gesture, registry)
                } }
                CardNote(stringResource(R.string.icon_actions_caution))
                IosActionRow(stringResource(R.string.icon_actions_use_swipe_up), onClick = {
                    onChange(current.copy(up = current.double, double = null)); last = IconGestureKind.UP; screen = IconActionsScreen.Gestures
                })
                IosActionRow(stringResource(R.string.icon_actions_keep_double), onClick = { screen = IconActionsScreen.Gestures })
            }
        }
    }
}

/** The name a gesture's action shows: the app or shortcut it opens, the action's own name, or Nothing. */
@Composable
internal fun actionName(ref: ActionRef?): String {
    if (ref == null) return stringResource(R.string.nothing)
    val name = ref.args["name"]
    return when {
        ref.id == "app.open" && name != null -> stringResource(R.string.icon_actions_open_app, name)
        ref.id == "shortcut.open" && name != null -> name
        else -> ActionRegistry.standard.spec(ref.id)?.let { stringResource(ActionCatalog.row(it, ActionVerdict.Allowed).label) }
            ?: stringResource(R.string.icon_actions_unknown)
    }
}

/**
 * Runs the action once so a person can check it. One that locks the phone, opens the power menu or takes a screenshot
 * waits three seconds with Cancel, so trying Lock Screen doesn't lock the phone mid-tap.
 */
@Composable
private fun TryIt(ref: ActionRef, gesture: IconGestureKind, registry: ActionRegistry) {
    val context = LocalContext.current
    val disruptive = registry.spec(ref.id)?.risk == OperationRisk.DISRUPTIVE
    var countdown by remember(ref) { mutableIntStateOf(0) }
    var tried by remember(ref) { mutableStateOf(false) }
    LaunchedEffect(countdown) {
        if (countdown <= 0) return@LaunchedEffect
        delay(1_000)
        if (countdown == 1) { ActionRunner.run(context, ref, ActionSource.TRY); tried = true }
        countdown--
    }
    SettingsCard(null) {
        if (countdown > 0) {
            EditorRow(stringResource(R.string.icon_actions_try_countdown, countdown), null, null, enabled = false)
            CardAction(stringResource(R.string.cancel), onClick = { countdown = 0 }, modifier = Modifier.testTag("icon-actions-try-cancel"))
        } else EditorRow(stringResource(R.string.icon_actions_try, stringResource(gestureName(gesture)).lowercase()),
            stringResource(if (tried) R.string.icon_actions_tried else R.string.icon_actions_try_note), null, tag = "icon-actions-try") {
            if (disruptive) countdown = 3 else { ActionRunner.run(context, ref, ActionSource.TRY); tried = true }
        }
    }
}

@Composable
private fun ActionRowView(row: ActionRow, selected: Boolean, onClick: () -> Unit) {
    val reason = when {
        row.verdict is ActionVerdict.NeedsAndroid -> stringResource(R.string.icon_actions_needs_android, androidRelease((row.verdict as ActionVerdict.NeedsAndroid).sdk))
        else -> null
    }
    val risk = when (row.spec.id) {
        "LOCK" -> R.string.icon_actions_risk_lock; "a11y.power" -> R.string.icon_actions_risk_power; "SCREENSHOT" -> R.string.icon_actions_risk_screenshot
        else -> null
    }
    val value = if (row.spec.group == "panel") stringResource(R.string.icon_actions_opens_panel) else null
    EditorRow(stringResource(row.label), reason ?: risk?.let { stringResource(it) }, value, checked = selected, enabled = !row.dimmed,
        tag = "icon-actions-row-${row.spec.id}", chevron = row.needsChoice, onClick = onClick)
}

@Composable
private fun AppPickRow(app: AppEntry, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(role = Role.Button, onClick = onClick).testTag("icon-actions-app-${app.id}"),
        verticalAlignment = Alignment.CenterVertically) {
        AppIcon(app, null, Modifier.size(32.dp), shape = RoundedCornerShape(FolioRadius.CONTROL.dp), badge = false)
        Text(app.label, color = Color.White, fontSize = FolioType.BODY.sp, modifier = Modifier.weight(1f).padding(start = FolioSpace.MEDIUM.dp))
    }
}

@Composable
private fun EditorRow(title: String, subtitle: String?, value: String?, checked: Boolean = false, enabled: Boolean = true,
    tag: String? = null, chevron: Boolean = value != null && enabled, onClick: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp)
        .then(if (onClick != null && enabled) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
        .then(if (tag != null) Modifier.testTag(tag) else Modifier), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(vertical = FolioSpace.SNUG.dp)) {
            Text(title, color = Color.White.copy(alpha = if (enabled) 1f else .5f), fontSize = FolioType.BODY.sp)
            subtitle?.let { Text(it, color = Color.White.copy(alpha = .7f), fontSize = FolioType.FOOTNOTE.sp) }
        }
        value?.let { Text(it, color = Color.White.copy(alpha = .6f), fontSize = FolioType.FOOTNOTE.sp, maxLines = 1,
            modifier = Modifier.padding(start = FolioSpace.SMALL.dp)) }
        if (checked) Icon(Icons.Rounded.Check, stringResource(R.string.app_selected), tint = LocalAccent.current.ink, modifier = Modifier.padding(start = FolioSpace.SMALL.dp))
        if (chevron && onClick != null) Icon(Icons.Rounded.ChevronRight, null, tint = Color.White.copy(alpha = .3f), modifier = Modifier.mirroredForRtl())
    }
}

@Composable
private fun BackRow(label: String, onBack: () -> Unit) {
    Row(Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(FolioRadius.CONTROL.dp)).clickable(role = Role.Button, onClick = onBack)
        .testTag("icon-actions-back"), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.ChevronLeft, null, tint = LocalAccent.current.ink, modifier = Modifier.mirroredForRtl())
        Text(label, color = LocalAccent.current.ink, fontSize = FolioType.BODY.sp)
    }
}

@Composable
private fun Title(text: String) =
    Text(text, color = Color.White, fontSize = FolioType.TITLE.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = FolioSpace.SMALL.dp))

/** An icon's set actions as menu rows ("Swipe up: Flashlight") with what each runs, in gesture order. */
@Composable
internal fun iconActionMenuRows(actions: IconActions?, run: (ActionRef) -> Unit): List<Pair<String, () -> Unit>> =
    IconGestureKind.entries.mapNotNull { gesture ->
        actions.runnable(gesture)?.let { ref -> stringResource(R.string.icon_action_talkback, stringResource(gestureName(gesture)), actionName(ref)) to { run(ref) } }
    }
