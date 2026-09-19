package com.mccal.folio

import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
internal fun LayoutRestorePreview(preview: LayoutImportPreview, onRestore: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(onDismissRequest = onCancel, modifier = Modifier.testTag("layout-restore-preview"),
        title = { Text(stringResource(R.string.review_restored_layout)) }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.apps_folders_widgets_1_2_3, preview.appCount, preview.folderCount, preview.widgetCount))
                if (preview.layout.leadingSlots.any { it != null } || preview.layout.widgetPlacements.any { it.page == -1 })
                    Text(stringResource(R.string.includes_your_unfolded_only_page), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.this_also_restores_icon_layout_labels_se))
                Text(stringResource(R.string.your_selected_launcher_background_photo),
                    style = MaterialTheme.typography.bodySmall)
                if (preview.missingApps.isNotEmpty()) {
                    Text(stringResource(R.string.unavailable_apps_1, preview.missingApps.size), style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.error)
                    preview.missingApps.forEach { saved ->
                        val label = saved.substringAfterLast('(').removeSuffix(")").takeIf { it.isNotBlank() } ?: "Unavailable app"
                        Text(stringResource(R.string.its_saved_position_will_stay_empty_1, label))
                    }
                }
                if (preview.profileIssues.isNotEmpty()) {
                    Text(stringResource(R.string.profile_attention), style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.error)
                    preview.profileIssues.forEach { Text("• $it") }
                }
                val reconnect = preview.layout.widgetPlacements.count { it.id == NEEDS_BINDING_WIDGET }
                if (reconnect > 0) Text(pluralStringResource(R.plurals.widgets_keep_their_saved_space, reconnect, reconnect))
                Text(stringResource(R.string.nothing_changes_until_you_choose_restore), style = MaterialTheme.typography.bodySmall)
            }
        }, confirmButton = { TextButton(onClick = onRestore, modifier = Modifier.testTag("layout-restore-apply")) { Text(stringResource(R.string.restore)) } },
        dismissButton = { TextButton(onClick = onCancel, modifier = Modifier.testTag("layout-restore-cancel")) { Text(stringResource(R.string.cancel)) } })
}
