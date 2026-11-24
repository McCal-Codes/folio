package com.mccal.folio

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.VolunteerActivism
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Simple Mode in first setup: one quiet row on "Where are you coming from?" ("Setting this up for someone else?") opens a
 * branch of three short pages, Home style, Apps and Ready, which skips the navigation page and the Advanced step and
 * ends by turning Simple on through [LauncherModel.turnOnSimple], the same way in as Settings. Setup keeps its own
 * always-dark look and sizes: the helper is the one using it. Behind [FeatureGate.SIMPLE_MODE]; with the gate shut,
 * setup is exactly what it was ([setupPageKeys]).
 */
internal object SimpleSetupPages {
    const val STYLE = "su-style"
    const val APPS = "su-apps"
    const val READY = "su-ready"
    val all = setOf(STYLE, APPS, READY)
}

/**
 * Setup's pages, in order, by key. Without the branch: welcome, starting point, navigation for Android, Make Folio Your
 * Home while it isn't, the Home apps page where the phone has apps to pick, the Advanced step where Shizuku or root is found, and the last page. With it: welcome, starting
 * point, Home style, Apps, Make Folio Your Home while it isn't (Simple Home is no use otherwise), and Ready, which
 * finishes setup in place of the last page.
 */
internal fun setupPageKeys(androidStart: Boolean, advanced: Boolean, homeDone: Boolean, simpleBranch: Boolean, homeApps: Boolean = false): List<String> =
    if (simpleBranch) listOfNotNull("welcome", "feel", SimpleSetupPages.STYLE, SimpleSetupPages.APPS, "home".takeUnless { homeDone }, SimpleSetupPages.READY)
    else listOfNotNull("welcome", "feel", "apps".takeIf { homeApps }, "nav".takeIf { androidStart }, "home".takeUnless { homeDone }, "adv".takeIf { advanced }, "done")

/** Whether setup offers the branch, or comes back into it after a restart: only where the gate is open. */
internal fun simpleSetupBranch(gateOpen: Boolean, wanted: Boolean): Boolean = gateOpen && wanted

/** What the helper picks in the branch, kept beside setup's progress so a restart comes back to the same choices. */
@Stable
internal class SimpleSetupChoices(private val prefs: SharedPreferences) {
    var style by mutableStateOf(SimpleStyle.entries.firstOrNull { it.name == prefs.getString(STYLE, null) } ?: SimpleStyle.LIST)
        private set
    /** Apps the helper unchecked, by id. Everything the phone's own roles offer starts checked. */
    var unchecked by mutableStateOf(prefs.getStringSet(OFF, null).orEmpty().toSet())
        private set

    fun pick(value: SimpleStyle) { style = value; prefs.edit().putString(STYLE, value.name).apply() }

    /** Checks or unchecks [id]; the last checked app stays, so Home is never empty. */
    fun toggle(id: String, offered: List<String>) {
        unchecked = if (id in unchecked) unchecked - id else if (apps(offered).size > 1) unchecked + id else unchecked
        prefs.edit().putStringSet(OFF, unchecked).apply()
    }

    fun apps(offered: List<String>): List<String> = offered.filter { it !in unchecked }

    fun clear() { prefs.edit().remove(STYLE).remove(OFF).apply() }

    private companion object {
        const val STYLE = "simpleSetupStyle"
        const val OFF = "simpleSetupUnchecked"
    }
}

/** The quiet row under "Not sure? Show me both", styled like setup's other optional rows. */
@Composable
internal fun SimpleSetupRow(onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = FolioSpace.LARGE.dp).clip(RoundedCornerShape(FolioRadius.CARD.dp)).background(Color.White.copy(alpha = .07f))
        .clickable(role = Role.Button, onClick = onClick).padding(FolioSpace.MEDIUM.dp).testTag("starting-simple"), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.VolunteerActivism, null, tint = LocalAccent.current.ink, modifier = Modifier.size(26.dp))
        Column(Modifier.weight(1f).padding(horizontal = FolioSpace.MEDIUM.dp)) {
            Text(stringResource(R.string.simple_setup_row_title), color = Color.White, fontSize = FolioType.BODY.sp, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.simple_setup_row_detail), color = Color.White.copy(alpha = .7f), fontSize = FolioType.SUBHEAD.sp)
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = Color.White.copy(alpha = .7f), modifier = Modifier.mirroredForRtl())
    }
}

/** The phone's own Call, Messages, Camera, Photos, Clock and Contacts apps, found off the main thread; null until then. */
@Composable
private fun rememberRoleApps(apps: List<AppEntry>): SimpleRoleApps? {
    val context = LocalContext.current
    return produceState<SimpleRoleApps?>(null, apps) { value = withContext(Dispatchers.IO) { simpleRoleApps(context, apps) } }.value
}

/** "How should Home look?": List or Big Grid, each as a small picture of Home with the apps the next page offers. */
@Composable
internal fun SimpleSetupStyle(state: LauncherState, choices: SimpleSetupChoices) {
    val roles = rememberRoleApps(state.apps)
    val byId = remember(state.apps) { state.apps.associateBy { it.id } }
    val apps = roles?.let { choices.apps(it.inOrder()).mapNotNull(byId::get) }.orEmpty()
    val callLabel = stringResource(R.string.simple_call)
    val label: (AppEntry) -> String = { if (it.id == roles?.call) callLabel else it.label }
    Row(Modifier.fillMaxWidth().padding(top = FolioSpace.XL.dp), horizontalArrangement = Arrangement.spacedBy(FolioSpace.MEDIUM.dp)) {
        listOf(SimpleStyle.LIST to R.string.simple_style_list, SimpleStyle.GRID to R.string.simple_style_grid).forEach { (style, name) ->
            val selected = choices.style == style
            Column(Modifier.weight(1f).clip(RoundedCornerShape(FolioRadius.GROUPED_CARD.dp))
                .background(if (selected) Color.White.copy(alpha = .14f) else Color.White.copy(alpha = .07f))
                .border(1.5.dp, if (selected) LocalAccent.current.ink else Color.White.copy(alpha = .12f), RoundedCornerShape(FolioRadius.GROUPED_CARD.dp))
                .selectable(selected, role = Role.RadioButton) { choices.pick(style) }.padding(FolioSpace.COMPACT.dp).testTag("simple-setup-${style.name.lowercase()}"),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(FolioSpace.SMALL.dp)) {
                SimpleMiniHome(apps, style, label)
                Row(Modifier.heightIn(min = 28.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, null,
                        tint = if (selected) LocalAccent.current.ink else Color.White.copy(alpha = .55f), modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(FolioSpace.SNUG.dp))
                    Text(stringResource(name), color = Color.White, fontSize = FolioType.BODY.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/** A small Simple Home: a picture only, so TalkBack hears the choice's name beside it, not every label inside. */
@Composable
private fun SimpleMiniHome(apps: List<AppEntry>, style: SimpleStyle, label: (AppEntry) -> String) {
    val tile = FolioColors.SecondaryBackground
    // Corners scaled down with the picture.
    val mini = RoundedCornerShape((FolioRadius.CONTROL / 3).dp)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(FolioRadius.CONTROL.dp)).background(Color.Black).padding(FolioSpace.SMALL.dp).clearAndSetSemantics {},
        verticalArrangement = Arrangement.spacedBy(FolioSpace.TINY.dp)) {
        val small = (FolioType.GROUP_LABEL - 3).sp
        Box(Modifier.fillMaxWidth().heightIn(min = 14.dp)) { Box(Modifier.size(34.dp, 9.dp).clip(mini).background(Color.White)) }
        val cell: @Composable (AppEntry, Modifier) -> Unit = { app, modifier ->
            if (style == SimpleStyle.GRID) Column(modifier.clip(mini).background(tile).padding(FolioSpace.TINY.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                AppIcon(app, null, Modifier.size(14.dp), shape = mini, badge = false)
                Text(label(app), color = Color.White, fontSize = small, maxLines = 1, textAlign = TextAlign.Center)
            } else Row(modifier.clip(mini).background(tile).padding(FolioSpace.TINY.dp), verticalAlignment = Alignment.CenterVertically) {
                AppIcon(app, null, Modifier.size(12.dp), shape = mini, badge = false)
                Spacer(Modifier.width(FolioSpace.TINY.dp))
                Text(label(app), color = Color.White, fontSize = small, maxLines = 1)
            }
        }
        if (style == SimpleStyle.GRID) apps.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(FolioSpace.TINY.dp)) {
                pair.forEach { cell(it, Modifier.weight(1f)) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        } else apps.forEach { cell(it, Modifier.fillMaxWidth()) }
        Box(Modifier.fillMaxWidth().border(.8.dp, FolioColors.Gray, mini).padding(FolioSpace.HAIR.dp), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.simple_helper_settings), color = Color.White, fontSize = small, maxLines = 1)
        }
    }
}

/** "Which apps?": the phone's own six, checked; uncheck any they won't use. No reordering here. */
@Composable
internal fun SimpleSetupApps(state: LauncherState, choices: SimpleSetupChoices) {
    val roles = rememberRoleApps(state.apps) ?: return
    val byId = remember(state.apps) { state.apps.associateBy { it.id } }
    val offered = roles.inOrder()
    val callLabel = stringResource(R.string.simple_call)
    SheetGroup(Modifier.padding(top = FolioSpace.XL.dp)) {
        offered.mapNotNull(byId::get).forEachIndexed { n, app ->
            if (n > 0) MenuDivider()
            val on = app.id !in choices.unchecked
            Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).toggleable(on, role = Role.Checkbox) { choices.toggle(app.id, offered) }
                .padding(horizontal = FolioSpace.LARGE.dp, vertical = FolioSpace.SNUG.dp).testTag("simple-setup-app"), verticalAlignment = Alignment.CenterVertically) {
                AppIcon(app, null, Modifier.size(36.dp), shape = RoundedCornerShape(FolioRadius.CONTROL.dp), badge = false)
                Column(Modifier.weight(1f).padding(horizontal = FolioSpace.MEDIUM.dp)) {
                    Text(app.label, color = Color.White, fontSize = FolioType.BODY.sp, fontWeight = FontWeight.SemiBold)
                    if (app.id == roles.call) Text(stringResource(R.string.simple_setup_shows_as, callLabel), color = Color.White.copy(alpha = .7f), fontSize = FolioType.SUBHEAD.sp)
                }
                Icon(if (on) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, null,
                    tint = if (on) LocalAccent.current.ink else Color.White.copy(alpha = .55f))
            }
        }
    }
    val count = choices.apps(offered).size
    Text(if (offered.isEmpty()) stringResource(R.string.simple_setup_apps_none) else pluralStringResource(R.plurals.simple_setup_apps_note, count, count),
        color = Color.White.copy(alpha = .7f), fontSize = FolioType.FOOTNOTE.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = FolioSpace.MEDIUM.dp))
}

/**
 * "Ready for them": what the person sees, how the helper gets back in and how to leave, all before it starts. A phone
 * with no screen lock is offered one (checked again each time setup comes back from Android's settings); Not Now
 * keeps the 2-second hold.
 */
@Composable
internal fun SimpleSetupReady(state: LauncherState, choices: SimpleSetupChoices, tick: Int) {
    val context = LocalContext.current
    val roles = rememberRoleApps(state.apps)
    val count = roles?.let { choices.apps(it.inOrder()).size } ?: 0
    val secure = remember(tick) { context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true }
    var later by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(top = FolioSpace.MEDIUM.dp), verticalArrangement = Arrangement.spacedBy(FolioSpace.LARGE.dp)) {
        ReadyLine(Icons.AutoMirrored.Rounded.ViewList, stringResource(R.string.simple_setup_see_title),
            pluralStringResource(if (choices.style == SimpleStyle.GRID) R.plurals.simple_setup_see_grid else R.plurals.simple_setup_see_list, count, count))
        ReadyLine(Icons.Rounded.Lock, stringResource(R.string.simple_setup_back_in_title),
            stringResource(if (secure) R.string.simple_setup_back_in_lock else R.string.simple_setup_back_in_hold))
        ReadyLine(Icons.AutoMirrored.Rounded.Logout, stringResource(R.string.simple_setup_leave_title), stringResource(R.string.simple_setup_leave_body))
        if (!secure && !later) Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(FolioRadius.CARD.dp)).background(FolioColors.SheetSurface)
            .padding(FolioSpace.COMFY.dp).testTag("simple-setup-lock"), verticalArrangement = Arrangement.spacedBy(FolioSpace.SNUG.dp)) {
            Text(stringResource(R.string.simple_setup_lock_title), color = Color.White, fontSize = FolioType.BODY.sp, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.simple_setup_lock_body), color = Color.White.copy(alpha = .82f), fontSize = FolioType.SUBHEAD.sp)
            Row(Modifier.padding(top = FolioSpace.TINY.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FolioSpace.MEDIUM.dp)) {
                Box(Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(FolioRadius.PANEL.dp)).background(FolioColors.MenuSurface)
                    .clickable(role = Role.Button) { openScreenLockSettings(context) }.padding(horizontal = FolioSpace.LARGE.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.simple_setup_set_lock), color = Color.White, fontSize = FolioType.BODY.sp, fontWeight = FontWeight.SemiBold)
                }
                Box(Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(FolioRadius.CONTROL.dp)).clickable(role = Role.Button) { later = true }
                    .padding(horizontal = FolioSpace.SMALL.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.not_now), color = LocalAccent.current.ink, fontSize = FolioType.BODY.sp)
                }
            }
        }
    }
}

@Composable
private fun ReadyLine(icon: ImageVector, title: String, detail: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Icon(icon, null, tint = LocalAccent.current.ink, modifier = Modifier.size(26.dp))
        Column(Modifier.weight(1f).padding(start = FolioSpace.MEDIUM.dp)) {
            Text(title, color = Color.White, fontSize = FolioType.BODY.sp, fontWeight = FontWeight.SemiBold)
            Text(detail, color = Color.White.copy(alpha = .7f), fontSize = FolioType.SUBHEAD.sp)
        }
    }
}

/** Android's own screen lock settings. Nothing is set for the person; the page is only opened. */
private fun openScreenLockSettings(context: Context) {
    runCatching { context.startActivity(Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/**
 * The one line Simple Home shows the first time it opens after setup: where Helper Settings is. It never comes back
 * after OK, and Settings' way in doesn't arm it (the helper is already looking at the button there).
 */
internal object SimpleFirstOpen {
    private const val KEY = "firstOpenHint"
    private fun prefs(context: Context) = context.getSharedPreferences("simple_mode", Context.MODE_PRIVATE)
    fun arm(context: Context) = prefs(context).edit().putBoolean(KEY, true).apply()
    fun pending(context: Context) = prefs(context).getBoolean(KEY, false)
    fun done(context: Context) = prefs(context).edit().putBoolean(KEY, false).apply()
}
