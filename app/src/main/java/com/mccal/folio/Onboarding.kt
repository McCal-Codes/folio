package com.mccal.folio

import androidx.compose.ui.res.stringResource
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle

/** One onboarding page. [done] is re-read whenever Folio comes back from a settings screen. */
private data class OnboardingPage(
    val key: String, val icon: ImageVector, val color: Long, val title: String, val body: String,
    val uses: List<String> = emptyList(), val action: String? = null, val done: () -> Boolean = { false },
    val onAction: (() -> Unit)? = null, val optional: Boolean = true,
    /** Show Folio's own icon instead of a symbol (the welcome page). */
    val appIcon: Boolean = false,
)

/**
 * iOS Setup Assistant-style onboarding: one clear page per thing Folio needs, each explaining why (and what it
 * doesn't do), every step skippable, progress saved so it resumes where you left off, and Home is usable the
 * whole time. Pages for things already allowed are left out.
 */
@Composable
internal fun Onboarding(isDefaultHome: Boolean, onMakeDefault: () -> Unit, onShadeSetup: () -> Unit,
    systemWallpaper: Boolean, onWallpaper: (Boolean) -> Unit, onFinish: () -> Unit,
    state: LauncherState? = null, model: LauncherModel? = null) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("setup_experience", Context.MODE_PRIVATE) }
    var tick by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) { lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { tick++ } }
    fun open(intent: Intent?) { intent?.let { runCatching { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } } }

    // Like iPhone's Setup Assistant: only what makes Folio work, one question per screen. Everything optional
    // (contacts, Bluetooth, Do Not Disturb, brightness, the side key) is asked where it's used, and all of it is
    // listed in Settings › Privacy & Permissions.
    val all = remember {
        listOf(
            OnboardingPage("welcome", Icons.Rounded.WavingHand, 0xFF2E5E66, context.getString(R.string.welcome_to_folio),
                context.getString(R.string.onboarding_welcome_detail),
                action = context.getString(R.string.continue_button), optional = false, appIcon = true),
            // Where you are coming from: how Folio starts. Skipping keeps today's defaults, which are the iPhone starting point.
            OnboardingPage("feel", Icons.Rounded.Tune, FolioColors.Value.Blue, context.getString(R.string.where_are_you_coming_from),
                context.getString(R.string.starting_point_detail), optional = false),
            OnboardingPage("home", Icons.Rounded.Home, FolioColors.Value.Blue, context.getString(R.string.make_folio_your_home),
                context.getString(R.string.onboarding_home_detail),
                action = context.getString(R.string.choose_home_app), done = { isDefaultHome }, onAction = onMakeDefault),
            OnboardingPage("done", Icons.Rounded.CheckCircle, FolioColors.Value.Green, context.getString(R.string.youre_all_set),
                context.getString(R.string.a_few_things_to_try), action = context.getString(R.string.get_started), optional = false),
        ).filter { page -> page.key in setOf("welcome", "feel", "done") || !page.done() }
    }
    // Resume by page key: the page list changes between versions (and skips what's already allowed), so an index
    // saved by an older Folio could land on the wrong page.
    var index by rememberSaveable { mutableIntStateOf(runCatching { prefs.getString(STEP_KEY, null) }.getOrNull()
        ?.let { key -> all.indexOfFirst { it.key == key } }?.takeIf { it >= 0 } ?: 0) }
    fun go(to: Int) { index = to.coerceIn(0, all.lastIndex); prefs.edit().remove(STEP).putString(STEP_KEY, all[index].key).apply() }
    fun finish() { prefs.edit().remove(STEP).remove(STEP_KEY).apply(); onFinish() }
    BackHandler(index > 0) { go(index - 1) }
    val page = all[index]
    val reduceMotion = LocalReduceMotion.current
    val done = remember(tick, page) { page.done() }
    // Granting something moves setup along by itself; a step that was already done when you got there waits for you.
    val doneOnArrival = remember(index) { all[index].done() }
    LaunchedEffect(done, index) {
        if (done && !doneOnArrival && page.onAction != null) { kotlinx.coroutines.delay(700); go(index + 1) }
    }

    Box(Modifier.fillMaxSize().testTag("onboarding")) {
        Column(Modifier.align(Alignment.TopCenter).widthIn(max = 560.dp).fillMaxSize().padding(horizontal = FolioSpace.XXL.dp)) {
            // Top bar: back and skip
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                if (index > 0) Row(Modifier.clip(RoundedCornerShape(FolioRadius.CONTROL.dp)).clickable { go(index - 1) }.padding(FolioSpace.SMALL.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.ChevronLeft, null, tint = LocalAccent.current.ink, modifier = Modifier.size(26.dp).mirroredForRtl())
                    Text(stringResource(R.string.back), color = LocalAccent.current.ink, fontSize = FolioType.BODY.sp)
                }
                Spacer(Modifier.weight(1f))
                // Setup is optional: Home works without it, and everything is in Settings.
                if (page.key != "done") Text(stringResource(R.string.skip), color = LocalAccent.current.ink, fontSize = FolioType.BODY.sp,
                    modifier = Modifier.clip(RoundedCornerShape(FolioRadius.CONTROL.dp)).clickable { finish() }.padding(FolioSpace.COMPACT.dp).testTag("onboarding-skip"))
            }
            AnimatedContent(index, Modifier.weight(1f), label = "onboarding page",
                transitionSpec = {
                    if (reduceMotion) return@AnimatedContent fadeIn() togetherWith fadeOut()
                    val forward = targetState > initialState
                    (slideInHorizontally { if (forward) it / 4 else -it / 4 } + fadeIn()) togetherWith
                        (slideOutHorizontally { if (forward) -it / 4 else it / 4 } + fadeOut())
                }) { i ->
                val p = all[i]
                Column(Modifier.fillMaxSize().fadingVerticalScroll().padding(top = FolioSpace.HUGE.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    val appIcon = if (p.appIcon) remember { folioIconBitmap(context) } else null
                    if (appIcon != null) androidx.compose.foundation.Image(appIcon, null, Modifier.size(96.dp).clip(RoundedCornerShape(22.dp)))
                    else Box(Modifier.size(96.dp).clip(RoundedCornerShape(FolioRadius.PANEL.dp)).background(Color(p.color)), contentAlignment = Alignment.Center) {
                        Icon(p.icon, null, tint = Color.White, modifier = Modifier.size(56.dp))
                    }
                    Text(p.title, color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                        lineHeight = 40.sp, modifier = Modifier.padding(top = FolioSpace.XXL.dp))
                    Text(p.body, color = Color.White.copy(alpha = .7f), fontSize = FolioType.BODY.sp, textAlign = TextAlign.Center, lineHeight = 23.sp,
                        modifier = Modifier.padding(top = FolioSpace.MEDIUM.dp))
                    if (p.uses.isNotEmpty()) SheetGroup(Modifier.padding(top = FolioSpace.XXL.dp)) {
                        p.uses.forEachIndexed { n, use ->
                            if (n > 0) MenuDivider()
                            Row(Modifier.fillMaxWidth().padding(horizontal = FolioSpace.LARGE.dp, vertical = FolioSpace.MEDIUM.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Check, null, tint = Color(p.color), modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(10.dp))
                                Text(use, color = Color.White, fontSize = FolioType.SUBHEAD.sp)
                            }
                        }
                    }
                    if (p.key == "done") {
                        val tips = listOf(
                            Icons.Rounded.TouchApp to context.getString(R.string.onboarding_tip_hold_app),
                            Icons.Rounded.Search to context.getString(R.string.swipe_down_on_home_for_spotlight),
                            Icons.Rounded.Settings to context.getString(R.string.onboarding_tip_settings),
                            Icons.Rounded.SwipeUp to context.getString(
                                if (NavigationTip.of(remember(tick) { gestureNavigation(context) }) == NavigationTip.GESTURES) R.string.onboarding_tip_nav_gestures
                                else R.string.onboarding_tip_nav_buttons),
                        )
                        SheetGroup(Modifier.padding(top = FolioSpace.XL.dp)) {
                            tips.forEachIndexed { n, (icon, tip) ->
                                if (n > 0) MenuDivider()
                                Row(Modifier.fillMaxWidth().padding(horizontal = FolioSpace.LARGE.dp, vertical = FolioSpace.MEDIUM.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(icon, null, tint = LocalAccent.current.ink, modifier = Modifier.size(22.dp))
                                    Spacer(Modifier.width(12.dp))
                                    Text(tip, color = Color.White, fontSize = FolioType.SUBHEAD.sp)
                                }
                            }
                        }
                    }
                    if (p.key == "feel" && state != null && model != null) StartingPointPage(state, model, tick, onShadeSetup)
                    if (p.key == "done") FinishSettingUp(tick, systemWallpaper, onWallpaper, onShadeSetup, ::open)
                    if (done && p.onAction != null) Row(Modifier.padding(top = FolioSpace.XL.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.CheckCircle, null, tint = FolioColors.Green)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.all_set), color = FolioColors.Green, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            // Bottom: primary action (or Continue once done), Not Now, and progress dots.
            val primary = when {
                page.key == "feel" && state != null -> context.getString(R.string.use_starting_point, context.getString((state.startingPoint() ?: StartingPoint.IPHONE).label))
                page.onAction != null && !done -> page.action ?: context.getString(R.string.continue_button)
                page.key == "done" -> page.action ?: context.getString(R.string.get_started)
                else -> context.getString(R.string.continue_button)
            }
            Box(Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(FolioRadius.CARD.dp)).background(LocalAccent.current.fill)
                .clickable {
                    when {
                        page.key == "done" -> finish()
                        page.onAction != null && !done -> page.onAction.invoke()
                        else -> go(index + 1)
                    }
                }.semantics { contentDescription = primary }.testTag("onboarding-primary"), contentAlignment = Alignment.Center) {
                Text(primary, color = Color.White, fontSize = FolioType.BODY.sp, fontWeight = FontWeight.SemiBold)
            }
            Box(Modifier.fillMaxWidth().heightIn(min = 48.dp), contentAlignment = Alignment.Center) {
                if (page.optional && !done) Text(if (page.key == "home") context.getString(R.string.try_folio_first) else context.getString(R.string.set_up_later_in_settings), color = LocalAccent.current.ink, fontSize = FolioType.BODY.sp,
                    modifier = Modifier.clip(RoundedCornerShape(FolioRadius.CONTROL.dp)).clickable { go(index + 1) }.padding(FolioSpace.COMPACT.dp).testTag("onboarding-not-now"))
            }
            Row(Modifier.fillMaxWidth().padding(bottom = FolioSpace.LARGE.dp), horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically) {
                all.indices.forEach { n ->
                    Box(Modifier.padding(3.dp).size(if (n == index) 8.dp else 6.dp).clip(CircleShape)
                        .background(Color.White.copy(alpha = if (n == index) 1f else .3f)))
                }
            }
        }
    }
}

private const val STEP = "onboardingStep"
private const val STEP_KEY = "onboardingPage"

/**
 * Where are you coming from: iPhone or Android, each with a picture of what it sets, then how you get around. "Not sure?"
 * shows both pictures side by side. Nothing here is locked in: it is a first set of ordinary settings, changed later in Settings.
 */
@Composable
private fun StartingPointPage(state: LauncherState, model: LauncherModel, tick: Int, onShadeSetup: () -> Unit) {
    val context = LocalContext.current
    val chosen = state.startingPoint() ?: StartingPoint.IPHONE
    var both by remember { mutableStateOf(false) }
    var navOpen by remember { mutableStateOf(false) }
    val gestures = remember(tick) { gestureNavigation(context) }
    if (both) {
        Row(Modifier.fillMaxWidth().padding(top = FolioSpace.XL.dp), horizontalArrangement = Arrangement.spacedBy(FolioSpace.LARGE.dp)) {
            StartingPoint.entries.forEach { point ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    StartingPointPreview(point, Modifier.fillMaxWidth(.8f))
                    Text(stringResource(point.label), color = Color.White, fontSize = FolioType.SUBHEAD.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = FolioSpace.SMALL.dp))
                    IosChip(selected = chosen == point, onClick = { model.setStartingPoint(point); both = false },
                        label = { Text(stringResource(R.string.use_starting_point, stringResource(point.label))) },
                        modifier = Modifier.padding(top = FolioSpace.SMALL.dp).testTag("starting-use-${point.name.lowercase()}"))
                }
            }
        }
    } else {
        Column(Modifier.fillMaxWidth().padding(top = FolioSpace.XL.dp), verticalArrangement = Arrangement.spacedBy(FolioSpace.COMPACT.dp)) {
            StartingPoint.entries.forEach { point ->
                val selected = chosen == point
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(FolioRadius.CARD.dp))
                    .background(if (selected) Color.White.copy(alpha = .14f) else Color.White.copy(alpha = .07f))
                    .selectable(selected = selected, role = Role.RadioButton, onClick = { model.setStartingPoint(point) })
                    .padding(FolioSpace.MEDIUM.dp).testTag("starting-${point.name.lowercase()}"), verticalAlignment = Alignment.CenterVertically) {
                    StartingPointPreview(point, Modifier.width(44.dp))
                    Column(Modifier.weight(1f).padding(horizontal = FolioSpace.MEDIUM.dp)) {
                        Text(stringResource(point.label), color = Color.White, fontSize = FolioType.BODY.sp, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(point.detail), color = Color.White.copy(alpha = .7f), fontSize = FolioType.SUBHEAD.sp)
                    }
                    Icon(if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, null,
                        tint = if (selected) LocalAccent.current.ink else Color.White.copy(alpha = .4f))
                }
            }
        }
        // How you get around. Folio follows what Android is set to; these are only optional extras.
        Row(Modifier.fillMaxWidth().padding(top = FolioSpace.COMPACT.dp).clip(RoundedCornerShape(FolioRadius.CARD.dp)).background(Color.White.copy(alpha = .07f))
            .clickable { navOpen = !navOpen }.padding(FolioSpace.MEDIUM.dp).testTag("starting-navigation"), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.navigation_row), color = Color.White, fontSize = FolioType.BODY.sp)
                Text(stringResource(R.string.navigation_follows, stringResource(if (gestures) R.string.navigation_gestures else R.string.navigation_buttons)),
                    color = Color.White.copy(alpha = .7f), fontSize = FolioType.SUBHEAD.sp)
            }
            Icon(if (navOpen) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = Color.White.copy(alpha = .7f))
        }
        if (navOpen) {
            Row(Modifier.fillMaxWidth().padding(top = FolioSpace.SMALL.dp), horizontalArrangement = Arrangement.spacedBy(FolioSpace.SMALL.dp)) {
                IosChip(selected = true, onClick = { navOpen = false }, label = { Text(stringResource(R.string.navigation_keep)) }, modifier = Modifier.weight(1f))
                IosChip(selected = false, onClick = { onShadeSetup() }, label = { Text(stringResource(R.string.navigation_big_buttons)) }, modifier = Modifier.weight(1f))
                IosChip(selected = false, onClick = { runCatching { context.startActivity(Intent(android.provider.Settings.ACTION_DISPLAY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } },
                    label = { Text(stringResource(R.string.navigation_android_settings)) }, modifier = Modifier.weight(1f))
            }
            Text(stringResource(R.string.navigation_note), color = Color.White.copy(alpha = .7f), fontSize = FolioType.FOOTNOTE.sp, modifier = Modifier.padding(top = FolioSpace.SMALL.dp))
        }
    }
    Text(stringResource(if (both) R.string.starting_back else R.string.starting_show_both), color = LocalAccent.current.ink, fontSize = FolioType.BODY.sp,
        modifier = Modifier.padding(top = FolioSpace.LARGE.dp).clip(RoundedCornerShape(FolioRadius.CONTROL.dp)).clickable { both = !both }.padding(FolioSpace.COMPACT.dp).testTag("starting-both"))
}

/**
 * What setup used to ask on pages of its own, now offered when it is done and asked again where it is used: notification
 * access for the island and the accessibility service for the pull-down panels, only while they are still off, and the
 * wallpaper. Nothing is blocked on any of it.
 */
@Composable
private fun FinishSettingUp(tick: Int, systemWallpaper: Boolean, onWallpaper: (Boolean) -> Unit, onShadeSetup: () -> Unit, open: (Intent?) -> Unit) {
    val context = LocalContext.current
    val notifications = remember(tick) { IslandListenerService.hasAccess(context) }
    val panels = remember(tick) { SystemShadeAccessibilityService.isConnected() }
    Text(stringResource(R.string.finish_setting_up), color = Color.White.copy(alpha = .7f), fontSize = FolioType.SUBHEAD.sp, modifier = Modifier.padding(top = FolioSpace.XL.dp, bottom = FolioSpace.SMALL.dp))
    SheetGroup {
        if (!notifications) {
            FinishRow(Icons.Rounded.Notifications, stringResource(R.string.notifications_title), stringResource(R.string.finish_notifications_detail), "finish-notifications") { open(IslandListenerService.accessSettingsIntent(context)) }
            MenuDivider()
        }
        if (!panels) {
            FinishRow(Icons.Rounded.SwipeDown, stringResource(R.string.pull_down_for_more), stringResource(R.string.finish_panels_detail), "finish-panels") { onShadeSetup() }
            MenuDivider()
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = FolioSpace.LARGE.dp, vertical = FolioSpace.SMALL.dp), horizontalArrangement = Arrangement.spacedBy(FolioSpace.COMPACT.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Wallpaper, null, tint = LocalAccent.current.ink, modifier = Modifier.size(22.dp))
            IosChip(selected = systemWallpaper, onClick = { onWallpaper(true) }, label = { Text(stringResource(R.string.my_wallpaper)) }, modifier = Modifier.weight(1f))
            IosChip(selected = !systemWallpaper, onClick = { onWallpaper(false) }, label = { Text(stringResource(R.string.folio_dunes)) }, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun FinishRow(icon: ImageVector, title: String, detail: String, tag: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onClick).padding(horizontal = FolioSpace.LARGE.dp, vertical = FolioSpace.SMALL.dp).testTag(tag),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = LocalAccent.current.ink, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f).padding(horizontal = FolioSpace.MEDIUM.dp)) {
            Text(title, color = Color.White, fontSize = FolioType.SUBHEAD.sp)
            Text(detail, color = Color.White.copy(alpha = .7f), fontSize = FolioType.FOOTNOTE.sp)
        }
        Text(stringResource(R.string.set_up), color = LocalAccent.current.ink, fontSize = FolioType.SUBHEAD.sp, fontWeight = FontWeight.SemiBold)
    }
}
