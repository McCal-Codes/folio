package com.mccal.folio

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics

/** Advanced › Diagnostics: start and stop the Performance log (see [PerfLog]), then copy or share its report. */
@Composable internal fun PerformanceCard() {
    val context = LocalContext.current
    val status by PerfLog.status.collectAsState()
    SettingsCard(stringResource(R.string.performance_log)) {
        val minutes = (status.elapsedMs / 60_000).toInt()
        Text(when {
            status.running -> stringResource(R.string.performance_status_running, minutes, status.readings)
            status.hasReport -> stringResource(R.string.performance_status_done, minutes)
            else -> stringResource(R.string.performance_status_off)
        }, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("performance-status"))
        CardAction(stringResource(if (status.running) R.string.performance_stop else R.string.performance_start), onClick = {
            if (status.running) PerfLog.stop() else PerfLog.start(context, context.findActivity())
        }, modifier = Modifier.testTag("performance-toggle"))
        if (status.hasReport) {
            var copied by remember { mutableStateOf(false) }
            CardAction(stringResource(R.string.performance_copy), onClick = {
                context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Folio performance report", PerfLog.reportText()))
                copied = true
            }, modifier = Modifier.testTag("performance-copy"))
            CardAction(stringResource(R.string.performance_share), onClick = {
                runCatching { context.startActivity(PerfLog.shareIntent(PerfLog.reportText(), context.getString(R.string.performance_share))) }
            }, modifier = Modifier.testTag("performance-share"))
            if (copied) {
                LaunchedEffect(Unit) { kotlinx.coroutines.delay(2500); copied = false }
                Text(stringResource(R.string.performance_copied), style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag("performance-copied").semantics { liveRegion = LiveRegionMode.Polite })
            }
        }
        CardNote(stringResource(R.string.performance_note))
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
