package com.mccal.folio

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/** How long the sliders rest before the full-screen copy is drawn, so a moving slider only ever pays for the quick one. */
internal const val TWEAK_SETTLE_MS = 180L

/** Room the panel takes under Home in the preview, so Home's page shrinks to fit above it instead of running behind it. */
internal const val WALLPAPER_TWEAK_PANEL_DP = 268

/** On a wide screen the panel stays a comfortable width instead of stretching its sliders across the whole display. */
internal const val WALLPAPER_TWEAK_PANEL_MAX_WIDTH_DP = 520

/**
 * Hue, saturation and lightness for a piece Folio drew, for everything at once or for the sky, the hills or the glow.
 *
 * Stateless apart from which part is selected: the colors live in the controller ([LauncherBackgroundController.pendingTweaks])
 * and every move goes up as a whole new [WallpaperTweaks], so the picture and the sliders cannot disagree. Set is what keeps
 * them (the same rule as every other wallpaper change), and Cancel leaves the piece as it was.
 */
@Composable
internal fun WallpaperTweakPanel(tweaks: WallpaperTweaks, onTweaks: (WallpaperTweaks) -> Unit, modifier: Modifier = Modifier) {
    var part by remember { mutableStateOf(TweakPart.ALL) }
    val tweak = tweaks[part]
    Column(
        modifier.widthIn(max = WALLPAPER_TWEAK_PANEL_MAX_WIDTH_DP.dp).fillMaxWidth().clip(RoundedCornerShape(FolioRadius.ROW.dp)).background(Color.Black.copy(alpha = .42f))
            .padding(horizontal = FolioSpace.MEDIUM.dp, vertical = FolioSpace.SMALL.dp).testTag("wallpaper-tweak-panel"),
        verticalArrangement = Arrangement.spacedBy(FolioSpace.HAIR.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(FolioSpace.SMALL.dp)) {
            listOf(
                TweakPart.ALL to R.string.wallpaper_tweak_everything, TweakPart.SKY to R.string.wallpaper_tweak_sky,
                TweakPart.HILLS to R.string.wallpaper_tweak_hills, TweakPart.GLOW to R.string.wallpaper_tweak_glow,
            ).forEach { (p, label) ->
                val selected = p == part
                val text = stringResource(label)
                // A longer name gets a wider pill, so "Everything" is not cut off on the narrow cover screen.
                Box(
                    Modifier.weight(text.length.coerceIn(4, 12).toFloat()).defaultMinSize(minHeight = 36.dp).clip(RoundedCornerShape(50))
                        .background(if (selected) LocalAccent.current.fill else Color.White.copy(alpha = .16f))
                        .selectable(selected, role = Role.Tab) { part = p }.testTag("wallpaper-tweak-part-${p.name.lowercase()}"),
                    contentAlignment = Alignment.Center,
                ) { Text(text, color = Color.White, fontSize = FolioType.FOOTNOTE.sp, maxLines = 1) }
            }
        }
        TweakSlider(stringResource(R.string.wallpaper_tweak_hue), tweak.hue, -180f..180f, "°", "hue") { onTweaks(tweaks.with(part, tweak.copy(hue = it))) }
        TweakSlider(stringResource(R.string.wallpaper_tweak_saturation), tweak.saturation, -100f..100f, "", "saturation") { onTweaks(tweaks.with(part, tweak.copy(saturation = it))) }
        TweakSlider(stringResource(R.string.wallpaper_tweak_lightness), tweak.lightness, -100f..100f, "", "lightness") { onTweaks(tweaks.with(part, tweak.copy(lightness = it))) }
        Row(Modifier.fillMaxWidth().padding(top = FolioSpace.HAIR.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(FolioSpace.COMPACT.dp)) {
                PRESETS.forEach { preset ->
                    val name = stringResource(preset.name)
                    Box(
                        Modifier.size(32.dp).clip(CircleShape).background(preset.dot).semantics { contentDescription = name; role = Role.Button }
                            .clickable { onTweaks(WallpaperTweaks(all = preset.tweak)) }.testTag("wallpaper-tweak-preset-${preset.tag}"),
                    )
                }
            }
            Text(
                stringResource(R.string.wallpaper_tweak_reset), color = LocalAccent.current.ink, fontSize = FolioType.FOOTNOTE.sp, maxLines = 1,
                modifier = Modifier.defaultMinSize(minHeight = 44.dp).clickable(role = Role.Button) { onTweaks(WallpaperTweaks.NONE) }
                    .padding(horizontal = FolioSpace.SMALL.dp).testTag("wallpaper-tweak-reset"),
            )
        }
    }
}

@Composable
private fun TweakSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, unit: String, tag: String, onValue: (Float) -> Unit) {
    Row(Modifier.fillMaxWidth().defaultMinSize(minHeight = 40.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White.copy(alpha = .85f), fontSize = FolioType.FOOTNOTE.sp, maxLines = 1, modifier = Modifier.width(88.dp))
        IosSlider(value, { onValue(it.roundToInt().toFloat()) }, range, Modifier.weight(1f).testTag("wallpaper-tweak-$tag"))
        Text("${value.roundToInt()}$unit", color = Color.White.copy(alpha = .85f), fontSize = FolioType.FOOTNOTE.sp, maxLines = 1,
            textAlign = TextAlign.End, modifier = Modifier.width(48.dp))
    }
}

/** A starting point: a change to everything, with the color it tends toward as the dot. Teal is no change at all. */
private class TweakPreset(val tag: String, val name: Int, val tweak: ColorTweak) {
    /** The pieces' own mid-sky teal moved by this change, so the dot is the color the change really gives, not a second guess at it. */
    val dot: Color = tweakedRgb(PRESET_BASE_RGB, tweak).let { Color(it[0], it[1], it[2]) }
}

/** Mid-sky teal of Moon Hills, the color each preset dot starts from. Six digits, so it is a shade and not a stored ARGB. */
private const val PRESET_BASE_RGB = 0x2A6A70

private val PRESETS = listOf(
    TweakPreset("teal", R.string.wallpaper_tweak_preset_teal, ColorTweak()),
    TweakPreset("violet", R.string.wallpaper_tweak_preset_violet, ColorTweak(hue = 95f)),
    TweakPreset("rose", R.string.wallpaper_tweak_preset_rose, ColorTweak(hue = 150f, saturation = 10f)),
    TweakPreset("amber", R.string.wallpaper_tweak_preset_amber, ColorTweak(hue = -140f, lightness = 5f)),
    TweakPreset("slate", R.string.wallpaper_tweak_preset_slate, ColorTweak(saturation = -80f)),
)
