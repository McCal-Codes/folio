package com.mccal.folio

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.text.Collator

/**
 * Which apps Screenshot Mode shows as a stock app instead: the ones the person picked, and with [allThirdParty] every
 * app that did not ship with the phone, including ones installed later. Keyed by app id like icon looks.
 */
data class DisguiseRules(
    val chosen: Set<String> = emptySet(),
    val allThirdParty: Boolean = false,
) {
    val isEmpty: Boolean get() = chosen.isEmpty() && !allThirdParty
}

/**
 * Screenshot Mode's app swap, visual only: a swapped app keeps its id, component and user, so layout, drag and drop
 * and launching all still work and open the real app, but it shows a stock app's icon and name. The stand-ins are
 * the phone's own apps (Phone, Camera, Clock...), so nothing is drawn or bundled.
 *
 * Apps that shipped with the phone are never swapped and are the only stand-ins. A work-profile app is never a
 * stand-in, since its badge is part of the icon. With no stand-in on the phone nothing is swapped.
 *
 * A stand-in gives the swap away when the real app shows next to it, so the pick ([pickStandIns]) avoids a stock app
 * that is already on Home and gives no two swapped apps the same one, for as long as the phone has enough stock apps.
 * It starts from a hash of the app's id, so the same app shows as the same stock app every time unless what is on
 * Home or the set of swapped apps changes.
 */
object ScreenshotDisguise {
    /**
     * [apps] with the swapped ones disguised and re-sorted by the name shown, or [apps] itself when nothing is
     * swapped. [systemPackages] are the packages that shipped with the phone, and [onHome] the app ids Home shows
     * (pages, dock, folders, stacks; see [LauncherState.homeAppIds]).
     */
    fun apply(
        apps: List<AppEntry>,
        rules: DisguiseRules,
        systemPackages: Set<String>,
        onHome: Set<String> = emptySet(),
    ): List<AppEntry> {
        if (rules.isEmpty) return apps
        val pool = standIns(apps, systemPackages)
        if (pool.isEmpty()) return apps
        val swapped = apps.filter { isSwapped(it, rules, systemPackages) }
        if (swapped.isEmpty()) return apps
        val homePackages = apps.filter { it.id in onHome }.mapTo(mutableSetOf()) { it.packageName }
        val picks = pickStandIns(swapped.map { it.id }, pool, homePackages, onHome)
        val disguised = apps.map { app ->
            val standIn = picks[app.id] ?: return@map app
            // systemLabel too, so search finds the app only by the name on screen.
            app.copy(label = standIn.label, systemLabel = standIn.label, icon = standIn.icon, iconFrom = standIn)
        }
        val collator = Collator.getInstance()
        return disguised.sortedWith { a, b -> collator.compare(a.label, b.label) }
    }

    /**
     * A stand-in from [pool] for each of [swappedIds]. Apps on Home ([onHome]) pick first, since that is where a
     * repeat shows; each group goes in id order. Each app starts at its hash in [pool] and takes the first stock app
     * that is neither on Home ([homePackages]) nor taken. Only when none is left does it repeat: the least used stock
     * app that is not on Home, and after those the least used of the rest. Same input, same picks.
     */
    internal fun pickStandIns(
        swappedIds: Collection<String>,
        pool: List<AppEntry>,
        homePackages: Set<String>,
        onHome: Set<String>,
    ): Map<String, AppEntry> {
        if (pool.isEmpty()) return emptyMap()
        val uses = IntArray(pool.size)
        val home = BooleanArray(pool.size) { pool[it].packageName in homePackages }
        val best = compareBy<Int>({ home[it] }, { uses[it] })
        val order = swappedIds.distinct().sortedWith(compareBy<String> { it !in onHome }.thenBy { it })
        return order.associateWith { id ->
            val start = Math.floorMod(id.hashCode(), pool.size)
            // minWith keeps the first of equals, so ties go to the nearest stock app after the hash.
            val at = pool.indices.map { (start + it) % pool.size }.minWith(best)
            uses[at]++
            pool[at]
        }
    }

    fun isSwapped(app: AppEntry, rules: DisguiseRules, systemPackages: Set<String>): Boolean =
        app.packageName !in systemPackages && (app.id in rules.chosen || rules.allThirdParty)

    /** How many of [apps] the rules swap, counted the way the Swap Apps rows show them (shortcuts are not listed). */
    fun swapCount(apps: List<AppEntry>, rules: DisguiseRules, systemPackages: Set<String>): Int =
        apps.count { !it.isShortcut && isSwapped(it, rules, systemPackages) }

    /** The phone's own apps that can stand in, in a fixed order so a pick does not move when apps are added. */
    internal fun standIns(apps: List<AppEntry>, systemPackages: Set<String>): List<AppEntry> =
        apps.filter { it.packageName in systemPackages && !it.isWork && !it.isShortcut && it.available }
            .distinctBy { it.packageName }
            .sortedBy { it.id }
}

/**
 * The swap rules, kept in a file of their own rather than in `launcher/state`: an older Folio ignores this file, so a
 * phone with rules set stays readable by every older build, where a key in the state would need a newer schema
 * (STA-5). The list of apps someone wants hidden is itself private, so it is not part of Layout Backup either.
 */
internal object ScreenshotSwap {
    private const val FILE = "screenshot_swap"
    private const val KEY = "rules"
    private val mutable = MutableStateFlow(DisguiseRules())
    val rules: StateFlow<DisguiseRules> = mutable.asStateFlow()
    @Volatile private var loaded = false

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** Reads the saved rules once, and returns the current ones. */
    fun load(context: Context): DisguiseRules {
        if (!loaded) synchronized(this) {
            if (!loaded) { mutable.value = decode(prefs(context).getString(KEY, null)); loaded = true }
        }
        return mutable.value
    }

    @Synchronized fun set(context: Context, rules: DisguiseRules) {
        load(context)
        mutable.value = rules
        prefs(context).edit().apply { if (rules.isEmpty) remove(KEY) else putString(KEY, encode(rules)) }.apply()
    }

    fun chosen(context: Context): Set<String> = load(context).chosen

    /** Apps that were uninstalled leave the list quietly, so nothing stale comes back with a reinstall. */
    fun forget(context: Context, removedIds: Collection<String>) {
        load(context)
        val current = mutable.value
        if (removedIds.none { it in current.chosen }) return
        set(context, current.copy(chosen = current.chosen - removedIds.toSet()))
    }

    internal fun encode(rules: DisguiseRules): String =
        JSONObject().put("chosen", JSONArray(rules.chosen.sorted())).put("allThirdParty", rules.allThirdParty).toString()

    /** Rules from a saved file, or none when there is no file or it can't be read. */
    internal fun decode(raw: String?): DisguiseRules = raw?.let {
        runCatching {
            val o = JSONObject(it)
            val chosen = o.optJSONArray("chosen")?.let { a -> (0 until a.length()).mapNotNull { i -> a.optString(i).takeIf(String::isNotBlank) } }.orEmpty()
            DisguiseRules(chosen.toSet(), o.optBoolean("allThirdParty", false))
        }.getOrNull()
    } ?: DisguiseRules()
}

/**
 * [state] as Home should draw it: with Screenshot Mode's swap applied while the mode is on and a rule is set, and
 * [state] itself otherwise, so Home costs nothing extra when the swap is not in use. Applied where a screen collects
 * the launcher state, so every list under it (grid, dock, folders, App Library, Spotlight, Today) follows; the model
 * and Layout Backup keep the real apps.
 */
@Composable
internal fun rememberDisguised(state: LauncherState): LauncherState {
    val context = LocalContext.current
    remember { ScreenshotSwap.load(context) }
    val on by ScreenshotMode.on.collectAsStateWithLifecycle()
    val rules by ScreenshotSwap.rules.collectAsStateWithLifecycle()
    val active = on && !rules.isEmpty
    val packages = remember(state.apps) { state.apps.mapTo(mutableSetOf()) { it.packageName } }
    // Asked only while the swap is in use, and off the main thread: one package manager call per app.
    val system by produceState<Set<String>?>(null, active, packages) {
        value = if (active) withContext(Dispatchers.IO) { systemPackages(context, packages) } else null
    }
    val shown = system
    if (!active || shown == null) return state
    val onHome = remember(state.homeSlots, state.leadingSlots, state.dock, state.folders, state.iconStacks) { state.homeAppIds() }
    val apps = remember(state.apps, rules, shown, onHome) { ScreenshotDisguise.apply(state.apps, rules, shown, onHome) }
    return remember(state, apps) { if (apps === state.apps) state else state.copy(apps = apps) }
}

/** The packages in [packages] that shipped with the phone, updated or not. */
internal fun systemPackages(context: Context, packages: Set<String>): Set<String> {
    val pm = context.packageManager
    return packages.filterTo(mutableSetOf()) { pkg -> shippedWithPhone(pm, pkg) == true }
}

/** Swapped apps by package, each with its stand-in, for what Home draws by package rather than by app (widgets). */
internal val LocalSwappedApps = androidx.compose.runtime.staticCompositionLocalOf { emptyMap<String, AppEntry>() }

internal fun swappedByPackage(apps: List<AppEntry>): Map<String, AppEntry> =
    apps.mapNotNull { app -> app.iconFrom?.let { app.packageName to it } }.toMap()
