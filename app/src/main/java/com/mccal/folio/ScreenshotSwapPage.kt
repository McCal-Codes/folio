package com.mccal.folio

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Settings › General › Advanced › Swap Apps: which apps Screenshot Mode shows as stock apps. [apps] are the real apps
 * from the model, never the disguised ones Home draws, so a swapped app is chosen by its real name. Apps that came with
 * the phone are listed but can't be chosen, since they are never swapped.
 */
@Composable
internal fun ScreenshotSwapPage(apps: List<AppEntry>) {
    val context = LocalContext.current
    remember { ScreenshotSwap.load(context) }
    val rules by ScreenshotSwap.rules.collectAsState()
    val choosable = remember(apps) { apps.filter { !it.isShortcut } }
    val packages = remember(choosable) { choosable.mapTo(mutableSetOf()) { it.packageName } }
    val system by produceState<Set<String>?>(null, packages) { value = withContext(Dispatchers.IO) { systemPackages(context, packages) } }
    var query by remember { mutableStateOf("") }
    val shown = remember(choosable, query) { choosable.filter { appMatches(it, query.trim()) } }
    fun set(next: DisguiseRules) = ScreenshotSwap.set(context, next)

    Text(stringResource(R.string.screenshot_swap_intro), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = FolioSpace.TINY.dp))
    SettingsCard(null) {
        SettingsSwitch(stringResource(R.string.screenshot_swap_all_third_party), rules.allThirdParty,
            { set(rules.copy(allThirdParty = it)) }, "screenshot-swap-all")
        CardNote(stringResource(R.string.screenshot_swap_all_third_party_note))
    }
    SettingsCard(stringResource(R.string.screenshot_swap_choose)) {
        Text(stringResource(R.string.screenshot_swap_count, rules.chosen.size), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("screenshot-swap-count"))
        IosSearchField(query, { query = it }, stringResource(R.string.search_apps), Modifier.padding(vertical = FolioSpace.MEDIUM.dp))
        if (shown.isEmpty()) Text(stringResource(R.string.screenshot_swap_none), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = FolioSpace.MEDIUM.dp))
        shown.forEach { app -> key(app.id) {
            val isSystem = system?.let { app.packageName in it } ?: false
            val on = !isSystem && (rules.allThirdParty || app.id in rules.chosen)
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                AppIcon(app, null, Modifier.size(32.dp), shape = RoundedCornerShape(FolioRadius.CONTROL.dp), badge = false)
                Column(Modifier.weight(1f).padding(start = FolioSpace.MEDIUM.dp)) {
                    Text(app.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (isSystem) Text(stringResource(R.string.screenshot_swap_system_app), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                // Not choosable until the system check is in, so a stock app is never ticked by a quick tap.
                IosSwitch(on, { pick -> set(rules.copy(chosen = if (pick) rules.chosen + app.id else rules.chosen - app.id)) },
                    Modifier.testTag("screenshot-swap-${app.id}"), enabled = system != null && !isSystem && !rules.allThirdParty)
            }
        } }
    }
    CardNote(stringResource(R.string.screenshot_swap_footnote), Modifier.padding(horizontal = FolioSpace.LARGE.dp))
}
