package com.mccal.folio

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.rounded.Check
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import org.json.JSONObject

/**
 * The look Folio has, Folio look ([IPHONE]) or Android look ([ANDROID]), picked at setup and in Settings › Wallpaper &
 * Appearance › Look: it sets the experience profile (what Folio does), the icon shape and the [LookSettings] (how it
 * looks) and nothing else, every one an ordinary setting that stays changeable. The fold effect stays as it is in both.
 * It is not a mode: [LauncherState.startingPoint] reads it back from the profile and icon shape, so Settings can never
 * disagree. Only the look settings the Android look replaced are kept aside ([LOOK_BEFORE_ANDROID]), for going back.
 * (The names are from when setup asked "Where are you coming from?", iPhone or Android.)
 */
internal enum class StartingPoint(
    val profile: ExperienceProfile,
    val shape: IconShape,
    @androidx.annotation.StringRes val label: Int,
    @androidx.annotation.StringRes val detail: Int,
) {
    IPHONE(ExperienceProfile.FOLIO, IconShape.DEFAULT, R.string.starting_iphone, R.string.starting_iphone_detail),
    ANDROID(ExperienceProfile.ANDROID_STYLE, IconShape.CIRCLE, R.string.starting_android, R.string.starting_android_detail),
}

/**
 * [point]'s settings applied: its profile, its icon shape, then its [LookSettings]. Android brings [LookSettings.ANDROID];
 * Folio puts back each look setting still at Android's value, from [before] (what Android replaced) or else Folio's
 * own, and leaves alone any the person changed since. Everything else, the fold effect (Duet) included, is left alone.
 */
internal fun LauncherState.withStartingPoint(point: StartingPoint, before: LookSettings? = null): LauncherState {
    val base = withProfile(point.profile).copy(iconShape = point.shape)
    return when (point) {
        StartingPoint.ANDROID -> LookSettings.ANDROID.applyTo(base)
        // Only on the way out of the Android look: tapping Folio again (setup's page allows it) changes nothing of the person's own.
        StartingPoint.IPHONE -> if (startingPoint() != StartingPoint.ANDROID) base
            else LookSettings.of(base).leaving(LookSettings.ANDROID, before ?: LookSettings.FOLIO).applyTo(base)
    }
}

/** Where the copy of the look settings from before the Android look is kept, as [LookSettings.toJson]. */
internal const val LOOK_PREFS = "folio_look"
internal const val LOOK_BEFORE_ANDROID = "beforeAndroid"

/**
 * The Android look's settings beyond the profile and icon shape: the iPhone-style touches that have a plain counterpart.
 * Every one is an ordinary setting in Settings. Saved whole before the Android look replaces them, so Folio look can put
 * the person's own values back rather than Folio's defaults.
 */
internal data class LookSettings(
    val homeStrip: HomeStrip, val glyph: StatusGlyph, val colorfulBattery: Boolean, val statusBackground: Boolean,
    val railGlass: Float, val glassOutline: Float, val badgeColor: BadgeColor, val folderBackground: FolderBackground,
    val leftPage: String,
) {
    fun applyTo(s: LauncherState) = s.copy(homeStrip = homeStrip, badgeColor = badgeColor, folderBackground = folderBackground,
        leftPage = leftPage, glassOutline = glassOutline, statusStyle = s.statusStyle.copy(glyph = glyph, colorfulBattery = colorfulBattery,
            background = statusBackground, railGlass = railGlass))

    /** Each setting still at [look]'s value becomes [back]'s; one the person changed since stays theirs. */
    fun leaving(look: LookSettings, back: LookSettings) = LookSettings(
        if (homeStrip == look.homeStrip) back.homeStrip else homeStrip,
        if (glyph == look.glyph) back.glyph else glyph,
        if (colorfulBattery == look.colorfulBattery) back.colorfulBattery else colorfulBattery,
        if (statusBackground == look.statusBackground) back.statusBackground else statusBackground,
        if (railGlass == look.railGlass) back.railGlass else railGlass,
        if (glassOutline == look.glassOutline) back.glassOutline else glassOutline,
        if (badgeColor == look.badgeColor) back.badgeColor else badgeColor,
        if (folderBackground == look.folderBackground) back.folderBackground else folderBackground,
        if (leftPage == look.leftPage) back.leftPage else leftPage)

    fun toJson(): JSONObject = JSONObject().put("homeStrip", homeStrip.name).put("glyph", glyph.name).put("colorfulBattery", colorfulBattery)
        .put("statusBackground", statusBackground).put("railGlass", railGlass.toDouble()).put("glassOutline", glassOutline.toDouble())
        .put("badgeColor", badgeColor.name).put("folderBackground", folderBackground.name).put("leftPage", leftPage)

    companion object {
        fun of(s: LauncherState) = LookSettings(s.homeStrip, s.statusStyle.glyph, s.statusStyle.colorfulBattery, s.statusStyle.background,
            s.statusStyle.railGlass, s.glassOutline, s.badgeColor, s.folderBackground, s.leftPage)

        /** Folio's own values, for a phone that has no saved copy (Android was picked at first setup). */
        val FOLIO: LookSettings get() = of(LauncherState())

        /**
         * Page dots instead of the Search button, no capsule or outline behind the status and dock, plain status icons,
         * badges in the app's color, solid folders, and Google Discover left of Home (it shows Today View when Google refuses).
         */
        val ANDROID = LookSettings(HomeStrip.DOTS, StatusGlyph.ICONS, colorfulBattery = false, statusBackground = false,
            railGlass = 0f, glassOutline = 0f, badgeColor = BadgeColor.APP, folderBackground = FolderBackground.SOLID, leftPage = "DISCOVER")

        /** Strict: a copy missing a value is not used, so a damaged one never puts back a half-guessed setting. */
        fun fromJson(o: JSONObject) = LookSettings(HomeStrip.valueOf(o.getString("homeStrip")), StatusGlyph.valueOf(o.getString("glyph")),
            o.getBoolean("colorfulBattery"), o.getBoolean("statusBackground"), o.getDouble("railGlass").toFloat(), o.getDouble("glassOutline").toFloat(),
            BadgeColor.valueOf(o.getString("badgeColor")), FolderBackground.valueOf(o.getString("folderBackground")), o.getString("leftPage"))

        fun parse(saved: String?): LookSettings? = saved?.let { runCatching { fromJson(JSONObject(it)) }.getOrNull() }
    }
}

/** The starting point these settings match, or null when they were changed to something else (the person's own mix). */
internal fun LauncherState.startingPoint(): StartingPoint? =
    StartingPoint.entries.firstOrNull { profile() == it.profile && iconShape == it.shape }

/** What setup tells the person about getting around, from how Android is set up. */
internal enum class NavigationTip { GESTURES, BUTTONS;
    companion object { fun of(gestures: Boolean) = if (gestures) GESTURES else BUTTONS }
}

/**
 * A small picture of Home as [point] makes it: the icon shape, and a dock. Drawn from plain shapes, so there is nothing
 * to load and it matches the choice. Hidden from TalkBack: the label beside it says the same.
 */
@Composable
internal fun StartingPointPreview(point: StartingPoint, modifier: Modifier = Modifier) {
    val colors = with(FolioColors.Value) { listOf(RedLight, Gray, Orange, Indigo, Blue, Green, Yellow, Cyan) }.map(::Color)
    val shape = if (point.shape == IconShape.CIRCLE) CircleShape else RoundedCornerShape(24)
    Column(modifier.aspectRatio(9f / 16f).clip(RoundedCornerShape(FolioRadius.CARD.dp))
        .background(Brush.linearGradient(listOf(Color(0xFF2B4A52), Color(0xFF6B4F3D)))).padding(8.dp).clearAndSetSemantics {},
        verticalArrangement = Arrangement.SpaceBetween) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            colors.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { row.forEach { Box(Modifier.size(14.dp).clip(shape).background(it)) } }
            }
        }
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(FolioRadius.CONTROL.dp)).background(Color.White.copy(alpha = if (point == StartingPoint.ANDROID) 0f else .25f)).padding(4.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            colors.drop(4).forEach { Box(Modifier.size(14.dp).clip(shape).background(it)) }
        }
    }
}

/**
 * Settings › Wallpaper & Appearance › Look: Folio look or Android look, for everyone, one tap each way. Beside Themes,
 * which also change the look as a set. A phone set up its own way has neither checked until one is tapped.
 */
@Composable
internal fun LookCard(state: LauncherState, model: LauncherModel) {
    val chosen = state.startingPoint()
    SettingsCard(androidx.compose.ui.res.stringResource(R.string.look_card)) {
        StartingPoint.entries.forEach { point ->
            LookRow(point, chosen == point) {
                // From Simple Mode: leave it properly first, so what it saved comes back before the look applies.
                if (state.simple != null) model.leaveSimple()
                model.setStartingPoint(point)
            }
        }
        CardNote(androidx.compose.ui.res.stringResource(R.string.look_note))
    }
}

/** One look: its name, what it does, and a check on the one in use. A radio button to TalkBack. */
@Composable
private fun LookRow(point: StartingPoint, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = FolioRow.NAV.dp)
        .selectable(selected, role = androidx.compose.ui.semantics.Role.RadioButton) { if (!selected) onClick() }
        .padding(vertical = FolioSpace.COMPACT.dp).testTag("look-${point.name.lowercase()}"), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(point.label), color = Color.White, fontSize = FolioType.BODY.sp)
            androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(point.detail), color = FolioColors.SecondaryLabel, fontSize = FolioType.FOOTNOTE.sp)
        }
        Spacer(Modifier.width(FolioSpace.MEDIUM.dp))
        if (selected) androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Rounded.Check, null, tint = LocalAccent.current.ink, modifier = Modifier.size(20.dp))
        else Spacer(Modifier.size(20.dp))
    }
}
