package com.mccal.folio

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mccal.folio.market.IndexPackage
import com.mccal.folio.market.PackageSafety

/**
 * What you agree to before a package is applied: what it changes, what it can't reach, and where it came from.
 *
 * Everything here is read from the manifest, so it says the same thing whoever wrote the package. Nothing has been
 * applied when this is on screen; Get is the first moment anything changes.
 */
@Composable
internal fun MarketInstallSheet(entry: IndexPackage, builtIn: Boolean, onGet: () -> Unit, onCancel: () -> Unit) {
    val manifest = entry.manifest ?: return
    MarketInstallSheet(
        manifest = manifest,
        origin = InstallOrigin(
            line = stringResource(if (builtIn) R.string.built_into_folio_so_there_s_nothing_to else R.string.downloaded_from_a_source_you_added),
            provenance = entry.provenance?.let { stringResource(R.string.built_from_1_s_2_s, it.repo, it.commit) },
            checksum = entry.sha256,
        ),
        onGet = onGet,
        onCancel = onCancel,
    )
}

/** Where a package came from, in the words the sheet shows. */
internal data class InstallOrigin(
    val line: String,
    val provenance: String? = null,
    val checksum: String? = null,
    /** Shown in amber when there's something to be careful about, like a file nobody signed. */
    val warning: String? = null,
)

@Composable
internal fun MarketInstallSheet(
    manifest: com.mccal.folio.market.PackageManifest,
    origin: InstallOrigin,
    onGet: () -> Unit,
    onCancel: () -> Unit,
) {
    val safety = PackageSafety.of(manifest)
    val name = manifest.name.english
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).testTag("market-install-sheet")) {
        Text(stringResource(R.string.get_1_s, name), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
        Text(
            stringResource(R.string.text_1_s_version_2_s, manifest.author.name.english, manifest.version.toString()),
            color = Color.White.copy(alpha = .55f), fontSize = 14.sp, modifier = Modifier.padding(bottom = 12.dp),
        )
        // Said before Get, because it's part of deciding: the author declared AI helped make this.
        manifest.aiAssisted?.let { ai ->
            Row(Modifier.padding(bottom = 12.dp).testTag("install-ai-assisted"), verticalAlignment = Alignment.Top) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = Color.White.copy(alpha = .55f), modifier = Modifier.size(16.dp).padding(top = 2.dp))
                Column(Modifier.padding(start = 8.dp)) {
                    Text(stringResource(R.string.made_with_help_from_ai_1_s, ai.tools.joinToString()), color = Color.White.copy(alpha = .85f), fontSize = 14.sp)
                    ai.note?.let { Text(it.english, color = Color.White.copy(alpha = .55f), fontSize = 13.sp) }
                }
            }
        }

        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFF2C2C2E)).padding(14.dp)) {
            Icon(Icons.Rounded.Shield, contentDescription = null, tint = FolioColors.Green, modifier = Modifier.size(20.dp))
            Text(safety.summary, color = Color.White.copy(alpha = .85f), fontSize = 13.sp, modifier = Modifier.padding(start = 10.dp))
        }

        SheetGroupLabel(stringResource(if (safety.changes.isEmpty()) R.string.changes else R.string.what_it_changes))
        SheetGroup {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (safety.changes.isEmpty()) {
                    Line(Icons.Rounded.Check, FolioColors.Green, stringResource(R.string.how_folio_looks_and_nothing_else))
                } else {
                    safety.changes.forEach { Line(Icons.Rounded.Check, Color(0xFFFFB340), it) }
                }
            }
        }

        SheetGroupLabel(stringResource(R.string.what_it_can_t_reach))
        SheetGroup {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                safety.cannotAccess.forEach { Line(Icons.Rounded.Close, Color.White.copy(alpha = .45f), it) }
            }
        }

        SheetGroupLabel(stringResource(R.string.where_it_came_from))
        SheetGroup {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                origin.warning?.let {
                    Text(it, color = Color(0xFFFFB340), fontSize = 14.sp)
                }
                Text(origin.line, color = Color.White.copy(alpha = .85f), fontSize = 14.sp)
                origin.provenance?.let { Text(it, color = Color.White.copy(alpha = .55f), fontSize = 13.sp) }
                origin.checksum?.let {
                    Text(stringResource(R.string.checksum_1_s, it.take(16)), color = Color.White.copy(alpha = .55f), fontSize = 13.sp)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        val getName = stringResource(R.string.get_1_s, name)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.cancel),
                color = LocalAccent.current.ink, fontSize = 16.sp,
                modifier = Modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onCancel)
                    .heightIn(min = 44.dp).padding(horizontal = 16.dp, vertical = 12.dp),
            )
            Text(
                stringResource(R.string.get),
                color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(LocalAccent.current.fill)
                    .clickable(onClickLabel = getName, onClick = onGet)
                    .heightIn(min = 44.dp).padding(horizontal = 22.dp, vertical = 12.dp)
                    .testTag("market-install-confirm"),
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Line(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp).padding(top = 2.dp))
        Text(text, color = Color.White.copy(alpha = .85f), fontSize = 14.sp, modifier = Modifier.padding(start = 8.dp))
    }
}
