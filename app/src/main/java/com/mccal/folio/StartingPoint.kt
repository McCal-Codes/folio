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

/**
 * Where a person is coming from, which is how setup starts Folio: it sets the experience profile (what Folio does) and
 * the icon shape (how it looks) and nothing else, every one an ordinary setting that stays changeable. It is not a mode
 * and not saved on its own: [LauncherState.startingPoint] reads it back from the settings, so Settings can never disagree.
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

/** [point]'s settings applied: its profile, then its icon shape. Everything else is left alone. */
internal fun LauncherState.withStartingPoint(point: StartingPoint): LauncherState =
    withProfile(point.profile).copy(iconShape = point.shape)

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
    val colors = listOf(0xFFFF3B30, 0xFF8E8E93, 0xFFFF9F0A, 0xFF5E5CE6, 0xFF0A84FF, 0xFF30D158, 0xFFFFD60A, 0xFF64D2FF).map(::Color)
    val shape = if (point.shape == IconShape.CIRCLE) CircleShape else RoundedCornerShape(24)
    Column(modifier.aspectRatio(9f / 16f).clip(RoundedCornerShape(FolioRadius.CARD.dp))
        .background(Brush.linearGradient(listOf(Color(0xFF2B4A52), Color(0xFF6B4F3D)))).padding(8.dp).clearAndSetSemantics {},
        verticalArrangement = Arrangement.SpaceBetween) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            colors.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { row.forEach { Box(Modifier.size(14.dp).clip(shape).background(it)) } }
            }
        }
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(FolioRadius.CONTROL.dp)).background(Color.White.copy(alpha = if (point == StartingPoint.ANDROID) .5f else .25f)).padding(4.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            colors.drop(4).forEach { Box(Modifier.size(14.dp).clip(shape).background(it)) }
        }
    }
}
