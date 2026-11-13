package com.mccal.folio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/**
 * Settings > General > Tips: every new feature of every release in one place, newest first, read from the same CHANGELOG.md
 * that What's New shows once after an update. A feature added to the changelog shows up here with no second list to keep.
 * The current release is open; older ones sit behind a row each. What's New and, on Folio Dev, This build are one tap from the top.
 */
@Composable
internal fun TipsPage(onShowWhatsNew: () -> Unit, onShowBuild: (() -> Unit)?) {
    val context = LocalContext.current
    val releases = remember { WhatsNew.notes(context).map { r -> r to r.sections.filter { it.first.equals("Added", true) }.flatMap { it.second }.map(WhatsNew::split) }.filter { it.second.isNotEmpty() } }
    var expanded by remember { mutableStateOf(setOf<String>()) }
    Column(Modifier.padding(horizontal = FolioSpace.LARGE.dp), verticalArrangement = Arrangement.spacedBy(FolioSpace.COMPACT.dp)) {
        SheetGroup {
            TweakRow(Icons.Rounded.NewReleases, FolioColors.Value.Green, stringResource(R.string.what_s_new), "tips-whats-new", "v" + WhatsNew.currentVersion(context)) { onShowWhatsNew() }
            if (onShowBuild != null) {
                MenuDivider()
                TweakRow(Icons.Rounded.Build, FolioColors.Value.Orange, DevBuild.THIS_BUILD_LABEL, "tips-dev-build", DevBuild.load(context)?.let { DevBuild.buildLabel(it) ?: it.sha }) { onShowBuild() }
            }
        }
        if (releases.isEmpty()) CardNote(stringResource(R.string.tips_none))
        releases.forEachIndexed { index, (release, features) ->
            if (index == 0) {
                SheetGroupLabel(stringResource(R.string.version_1, release.version))
                features.forEach { note -> FeatureRow(note) }
            } else {
                val open = release.version in expanded
                SheetGroup {
                    DisclosureRow(stringResource(R.string.version_1, release.version), open = open, count = features.size, tag = "tips-release-${release.version}") {
                        expanded = if (open) expanded - release.version else expanded + release.version
                    }
                    if (open) features.forEach { note ->
                        MenuDivider()
                        FeatureRow(note, Modifier.padding(horizontal = FolioSpace.LARGE.dp))
                    }
                }
            }
        }
        CardNote(stringResource(R.string.tips_footer))
    }
}
