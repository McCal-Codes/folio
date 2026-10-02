package com.mccal.folio

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mccal.folio.market.CompatCheck
import com.mccal.folio.market.CompatState
import com.mccal.folio.market.Screen

/** One line of the Compatibility card: how it came out, and the words for it. [args] fill both strings. */
internal data class CompatLine(
    val state: CompatState,
    @StringRes val title: Int,
    @StringRes val detail: Int,
    val args: List<String> = emptyList(),
)

/**
 * What the package page says about whether a package works here, from the checks [com.mccal.folio.market.PackageCompatibility]
 * made: the same answer the installer refuses on. Tweak hosts are left out, since the note under the Get button already
 * says which tweak a package adds to and offers it.
 */
internal fun compatLines(checks: List<CompatCheck>): List<CompatLine> = checks.mapNotNull { check ->
    when (check) {
        is CompatCheck.Release -> when {
            check.state == CompatState.BLOCKED -> CompatLine(CompatState.BLOCKED, R.string.compat_needs_folio, R.string.compat_needs_folio_detail,
                listOf(check.needs.toString(), check.have.toString()))
            check.have != null -> CompatLine(CompatState.OK, R.string.compat_works, R.string.compat_works_detail, listOf(check.have.toString()))
            else -> null
        }
        is CompatCheck.Kinds ->
            if (check.state == CompatState.BLOCKED) CompatLine(CompatState.BLOCKED, R.string.needs_a_newer_folio, R.string.compat_kind_detail) else null
        is CompatCheck.Features ->
            if (check.state == CompatState.BLOCKED) CompatLine(CompatState.BLOCKED, R.string.needs_a_newer_folio, R.string.compat_features_detail) else null
        is CompatCheck.Hosts -> null
        is CompatCheck.Replaces -> check.installed?.let {
            CompatLine(CompatState.BLOCKED, R.string.compat_replaces, R.string.compat_replaces_detail, listOf(it.name))
        }
        is CompatCheck.Needs -> check.required.map { need ->
            if (need in check.missing) CompatLine(CompatState.BLOCKED, R.string.compat_needs_package, R.string.compat_needs_package_detail, listOf(need.id))
            else CompatLine(CompatState.OK, R.string.compat_needs_package, R.string.compat_have_package_detail, listOf(need.id))
        }.let { it.firstOrNull { line -> line.state == CompatState.BLOCKED } ?: it.firstOrNull() }
        is CompatCheck.Screens -> when {
            check.state == CompatState.OK -> CompatLine(CompatState.OK, R.string.compat_screens_both, R.string.compat_screens_both_detail)
            Screen.INNER in check.supported && Screen.COVER !in check.supported ->
                CompatLine(CompatState.NOTE, R.string.compat_screen_inner_only, R.string.compat_screen_inner_only_detail)
            Screen.COVER in check.supported && Screen.INNER !in check.supported ->
                CompatLine(CompatState.NOTE, R.string.compat_screen_cover_only, R.string.compat_screen_cover_only_detail)
            else -> null
        }
    }
}

/** The one line a list row says about a package: why it can't be installed, or else a note worth reading. */
internal fun compatSummary(lines: List<CompatLine>): CompatLine? =
    lines.firstOrNull { it.state == CompatState.BLOCKED } ?: lines.firstOrNull { it.state == CompatState.NOTE }

internal fun compatBlocked(lines: List<CompatLine>): CompatLine? = lines.firstOrNull { it.state == CompatState.BLOCKED }

@Composable
private fun CompatLine.titleText() = stringResource(title, *args.toTypedArray())

@Composable
private fun CompatLine.detailText() = stringResource(detail, *args.toTypedArray())

/** The short form for a list row, a note under the name. */
@Composable
internal fun CompatLine.rowText() = titleText()

@Composable
internal fun CompatLine.rowColor(): Color = if (state == CompatState.BLOCKED) FolioColors.Warning else Color.White.copy(alpha = .55f)

/** The card on the package page: each line says what it means and whether it is fine, a note, or in the way. */
@Composable
internal fun CompatibilityCard(lines: List<CompatLine>, modifier: Modifier = Modifier) {
    if (lines.isEmpty()) return
    val blocked = compatBlocked(lines) != null
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(FolioRadius.GROUP.dp)).background(FolioColors.SheetSurface)
            .then(if (blocked) Modifier.border(1.dp, FolioColors.Warning, RoundedCornerShape(FolioRadius.GROUP.dp)) else Modifier)
            .padding(FolioSpace.LARGE.dp),
        verticalArrangement = Arrangement.spacedBy(FolioSpace.MEDIUM.dp),
    ) {
        lines.forEach { line ->
            Row(verticalAlignment = Alignment.Top) {
                val (icon, tint) = when (line.state) {
                    CompatState.OK -> Icons.Rounded.CheckCircle to FolioColors.Green
                    CompatState.NOTE -> Icons.Rounded.Info to Color.White.copy(alpha = .55f)
                    CompatState.BLOCKED -> Icons.Rounded.WarningAmber to FolioColors.Warning
                }
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
                Column(Modifier.padding(start = FolioSpace.COMPACT.dp)) {
                    Text(line.titleText(), color = Color.White, fontSize = FolioType.SUBHEAD.sp)
                    Text(line.detailText(), color = Color.White.copy(alpha = .55f), fontSize = FolioType.FOOTNOTE.sp)
                }
            }
        }
    }
}
