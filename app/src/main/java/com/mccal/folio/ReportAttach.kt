package com.mccal.folio

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource

/**
 * Optional screenshots for an emailed or shared report. The system photo picker hands Folio only the pictures the person
 * chooses (no storage permission), they travel as attachments of the same message, and nothing is read or kept by Folio.
 */
private const val MAX_SCREENSHOTS = 3

@Composable
internal fun ReportAttach(picked: List<Uri>, onPicked: (List<Uri>) -> Unit) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_SCREENSHOTS)) { onPicked(it) }
    Column {
        TextButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            modifier = Modifier.testTag("report-attach")) {
            Text(if (picked.isEmpty()) stringResource(R.string.attach_screenshots) else stringResource(R.string.screenshots_attached, picked.size))
        }
        if (picked.isNotEmpty()) TextButton(onClick = { onPicked(emptyList()) }, modifier = Modifier.testTag("report-attach-remove")) {
            Text(stringResource(R.string.remove_attached_screenshots))
        }
        Text(stringResource(R.string.attach_screenshots_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
