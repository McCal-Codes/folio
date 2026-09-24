package com.mccal.folio

import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
internal fun DiscoverContent(modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val message = LiveDiscover.message.value
    val googleIntent = remember(message) {
        context.packageManager.getLaunchIntentForPackage(DiscoverClient.GOOGLE_PACKAGE)
    }
    var showMessage by remember { mutableStateOf(false) }
    LaunchedEffect(message) {
        showMessage = false
        if (message != null) { delay(650); showMessage = true }
    }
    Box(modifier.testTag("discover-page")) {
        // The healthy native feed moves above this page. Keep its backing page transparent
        // so the retained Home layer is revealed during entry and exit, not an empty glass card.
        if (showMessage && message != null) Surface(Modifier.fillMaxSize().testTag("discover-recovery-surface"),
            shape = RoundedCornerShape(30.dp), color = Glass.copy(alpha = .92f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = .5f))) {
            Column(Modifier.fillMaxSize().padding(FolioSpace.HUGE.dp),
                verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.discover), style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(16.dp))
                Text(message ?: "", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(20.dp))
                FolioButton(stringResource(R.string.retry), LiveDiscover::retry, style = FolioButtonStyle.TONAL, tag = "discover-retry")
                if (googleIntent != null) TextButton(onClick = {
                    runCatching { context.startActivity(googleIntent) }
                }, Modifier.testTag("discover-open-google")) { Text(stringResource(R.string.open_google)) }
                TextButton(onClick = { LiveDiscover.onHomeRequest?.invoke() },
                    Modifier.testTag("discover-return-home")) { Text(stringResource(R.string.back_to_home_2)) }
            }
        }
    }
}
