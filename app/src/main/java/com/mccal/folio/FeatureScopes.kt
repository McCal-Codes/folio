package com.mccal.folio

import androidx.compose.material.icons.rounded.*

/** Where a tweak can be overridden (Choicy-style scopes). Focus overrides come later. */
enum class FolioScreen(@androidx.annotation.StringRes val label: Int) { COVER(R.string.cover_screen), INNER(R.string.inner_screen) }

/** DEFAULT inherits the tweak's main switch; ON/OFF force it on that screen. */
enum class ScopeValue(@androidx.annotation.StringRes val label: Int) { DEFAULT(R.string.default_choice), ON(R.string.on), OFF(R.string.off) }

internal object FeatureScopes {
    fun value(scopes: Map<String, Map<String, String>>, id: String, screen: FolioScreen): ScopeValue =
        scopes[id]?.get(screen.name)?.let { name -> ScopeValue.entries.firstOrNull { it.name == name } } ?: ScopeValue.DEFAULT

    /** Whether a feature is on for [screen]: an override wins, otherwise the main switch. */
    fun on(scopes: Map<String, Map<String, String>>, id: String, global: Boolean, screen: FolioScreen): Boolean =
        when (value(scopes, id, screen)) { ScopeValue.ON -> true; ScopeValue.OFF -> false; ScopeValue.DEFAULT -> global }

    fun set(scopes: Map<String, Map<String, String>>, id: String, screen: FolioScreen, value: ScopeValue): Map<String, Map<String, String>> {
        val current = scopes[id].orEmpty()
        val next = if (value == ScopeValue.DEFAULT) current - screen.name else current + (screen.name to value.name)
        return if (next.isEmpty()) scopes - id else scopes + (id to next)
    }
}

internal fun screenFor(wide: Boolean) = if (wide) FolioScreen.INNER else FolioScreen.COVER

/** "Barrel by Aaron Ash, Cylinder by Reed Weichler" without the authors: the short credit on a Tweak Library row. */
internal val TweakFeature.inspiredNames: String get() = inspiredBy.replace(AUTHOR, "")
private val AUTHOR = Regex(" by [^,]+")

/** A tweak-inspired feature: its page in Settings › Tweaks, with a main switch and per-screen overrides. */
internal data class TweakFeature(
    val id: String, val name: String, val inspiredBy: String,
    /** A resource, not a string: this is the one line of a tweak a person reads, so it is translated. The name and
     *  what it is after are proper nouns and stay as they are. */
    @androidx.annotation.StringRes val description: Int,
    val icon: androidx.compose.ui.graphics.vector.ImageVector, val color: Long,
    val get: (LauncherState) -> Boolean, val set: (LauncherModel, Boolean) -> Unit, val default: Boolean,
    /** The gate that has to be open before this is offered, or null for a tweak everyone has. */
    val gate: FeatureGate? = null,
    /** More words Settings search finds it by, for a tweak people know by what it does ("Page Effects"). */
    @androidx.annotation.StringRes val keywords: Int? = null,
    /** The credit line when this tweak includes someone's code, in place of "re-created from scratch". */
    @androidx.annotation.StringRes val credit: Int? = null,
)

/**
 * The tweaks to offer on this build.
 *
 * [TweakFeatures] is the whole table, and stays that way: a tweak a package already turned on has to keep resolving
 * by id even if this build would not offer it, or removing that package could not find what to put back. This is the
 * list a person is shown, which is the table minus anything whose gate is still shut. Without it a gated tweak would
 * sit in the Tweak Library with a switch that does nothing, because the gate is checked again where the effect is
 * actually drawn.
 */
internal fun visibleTweaks(context: android.content.Context): List<TweakFeature> =
    TweakFeatures.filter { it.gate == null || it.gate.isOpen(context) }

internal val TweakFeatures = listOf(
    TweakFeature("appPanels", "Cabinet", "Velox by Phillip Tennen", // english-only
        R.string.tweak_cabinet_detail,
        androidx.compose.material.icons.Icons.Rounded.Widgets, FolioColors.Value.Blue, { it.appPanels }, { m, v -> m.setAppPanels(v) }, true),
    TweakFeature("dockMagnify", "Harborline", "Harbor by Evan Swick", // english-only
        R.string.tweak_harborline_detail,
        androidx.compose.material.icons.Icons.Rounded.Add, FolioColors.Value.Indigo, { it.dockMagnify }, { m, v -> m.setDockMagnify(v) }, false),
    TweakFeature("notificationAppRow", "Roll Call", "Axon by Nepeta", // english-only
        R.string.tweak_roll_call_detail,
        androidx.compose.material.icons.Icons.Rounded.Notifications, FolioColors.Value.RedLight, { it.notificationAppRow }, { m, v -> m.setNotificationAppRow(v) }, true),
    TweakFeature("tintNotifications", "Palette", "Velvet by NoisyFlake & HiMyNameisUbik", // english-only
        R.string.tweak_palette_detail,
        androidx.compose.material.icons.Icons.Rounded.Star, FolioColors.Value.Orange, { it.tintNotifications }, { m, v -> m.setTintNotifications(v) }, false),
    TweakFeature("tintMedia", "Colored Albums", "ColorFlow by David Goldman", // english-only
        R.string.tweak_colored_albums_detail,
        androidx.compose.material.icons.Icons.Rounded.MusicNote, FolioColors.Value.Pink, { it.tintMedia }, { m, v -> m.setTintMedia(v) }, true),
    // The switch is on or off, and which effect it is lives on Flipbook's own page, beside the effects packages add
    // (Flipbook is their host, as Cylinder is for its scripts). Turning it on brings back the last one chosen; the
    // cube the first time, since that is the one Barrel was known for.
    TweakFeature("pageEffects", "Flipbook", "Barrel by Aaron Ash, Cylinder by Reed Weichler", // english-only
        R.string.tweak_flipbook_detail,
        androidx.compose.material.icons.Icons.Rounded.AutoStories, FolioColors.Value.Teal,
        { it.pageEffect != PageEffect.NONE },
        { m, v -> m.setPageEffectOn(v) }, false, FeatureGate.PAGE_EFFECTS, keywords = R.string.page_effects),
    // Folio's fold animation. It was built in, so it defaults on and a save from before it counts as installed.
    TweakFeature(DUET_ID, "Duet", "Duo Fold Live by joeconsorti", // english-only
        R.string.tweak_duet_detail,
        androidx.compose.material.icons.Icons.Rounded.MenuBook, FolioColors.Value.Indigo,
        { it.foldEffect }, { m, v -> m.setFoldEffect(v) }, true, credit = R.string.tweak_duet_credit),
)

internal const val DUET_ID = "duet"
