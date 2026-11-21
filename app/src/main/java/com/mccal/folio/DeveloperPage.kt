package com.mccal.folio

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

/** Developer's words stay English, one per line, like the rest of Folio Dev's page (DevBuild.kt). */
private object DeveloperText {
    const val WARNING = "Developer settings change how this phone behaves, not only Folio Dev. Show taps and Pointer location draw over every app, Don't keep activities makes Android close apps when you leave them, which can lose unsaved work, and Stay awake keeps the screen lit while charging. They are for testing: turn them off when you finish. Only Folio Dev has this page; a release build never does." // english-only
    const val ENABLE = "I understand, show developer settings" // english-only
    const val TURN_OFF = "Hide developer settings" // english-only
    const val BUILD_GROUP = "This build" // english-only
    const val SHOW_BUILD = "Show the build page again" // english-only
    const val GOOGLE_GROUP = "Google account" // english-only
    const val GOOGLE_ROW = "Link a Google account: not built. It needs a sign-in credential made in Google Cloud." // english-only
}

/**
 * Settings › Advanced › Developer (Folio Dev only). There is no passphrase: a warning first, then the switches for the rest of
 * this visit. Leaving the page puts the warning back, so they are never on display by accident.
 */
@Composable
internal fun DeveloperPage(onShowBuild: () -> Unit, onClose: () -> Unit) {
    var enabled by remember { mutableStateOf(false) }
    if (enabled) {
        SettingsCard(DeveloperText.BUILD_GROUP) {
            IosActionRow(DeveloperText.SHOW_BUILD, onClick = onShowBuild)
        }
        DisplayAidsCard()
        SettingsCard(DeveloperText.GOOGLE_GROUP) { CardNote(DeveloperText.GOOGLE_ROW) }
        CardAction(DeveloperText.TURN_OFF, Modifier.fillMaxWidth().testTag("developer-hide"), onClick = { enabled = false })
    } else {
        Text(DeveloperText.WARNING, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("developer-warning"))
        CardAction(DeveloperText.ENABLE, Modifier.fillMaxWidth().testTag("developer-enable"), onClick = { enabled = true })
    }
    // Made-up island activity only; it changes nothing on the phone, so it needs no warning.
    IslandTesterCard(onClose)
}
