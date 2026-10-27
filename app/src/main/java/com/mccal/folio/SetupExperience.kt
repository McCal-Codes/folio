package com.mccal.folio

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** [DEFERRED]: setup was left with the Home button, so it waits in Finish setting up instead of coming back on its own. */
internal enum class SetupEntryDecision { SHOW, ALREADY_FINISHED, EXISTING_INSTALL, DEFERRED }

internal fun setupEntryDecision(
    finished: Boolean,
    started: Boolean,
    hadLauncherState: Boolean,
    deferred: Boolean = false,
): SetupEntryDecision = when {
    finished -> SetupEntryDecision.ALREADY_FINISHED
    // Leaving with Home is "Skip for now": the saved page stays for Finish setting up, but the full-screen Welcome does not return by itself.
    deferred -> SetupEntryDecision.DEFERRED
    started -> SetupEntryDecision.SHOW
    hadLauncherState -> SetupEntryDecision.EXISTING_INSTALL
    else -> SetupEntryDecision.SHOW
}

/** Whether pressing Home (or the system returning to Home) defers setup: only when the Welcome was showing and setup is not finished. */
internal fun setupDeferredByHome(welcomeShowing: Boolean, finished: Boolean): Boolean = welcomeShowing && !finished

/** Setup is intentionally separate from launcher state so it cannot rewrite an upgraded layout. */
internal class SetupExperience(context: Context, prefsName: String = PREFS) {
    private val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    fun entryDecision(hadLauncherState: Boolean): SetupEntryDecision {
        val decision = setupEntryDecision(
            finished = prefs.getBoolean(FINISHED, false),
            started = prefs.getBoolean(STARTED, false),
            hadLauncherState = hadLauncherState,
            deferred = prefs.getBoolean(DEFERRED, false),
        )
        // A fresh launch writes only setup state. This keeps the cohort stable after the
        // launcher creates its normal layout preferences or Android recreates the activity.
        if (decision == SetupEntryDecision.SHOW && !prefs.getBoolean(STARTED, false))
            prefs.edit().putBoolean(STARTED, true).commit()
        return decision
    }

    /** Home dismissed the Welcome: it stays unfinished (Finish setting up still offers it, at the saved page) but is not shown again by itself. */
    fun deferByHome(welcomeShowing: Boolean) {
        if (setupDeferredByHome(welcomeShowing, prefs.getBoolean(FINISHED, false))) prefs.edit().putBoolean(DEFERRED, true).commit()
    }

    fun finish() {
        // Finish before dismissing the sheet so process death cannot make it reappear.
        prefs.edit().putBoolean(FINISHED, true).commit()
    }

    companion object {
        private const val PREFS = "setup_experience"
        private const val FINISHED = "finished"
        private const val STARTED = "started"
        private const val DEFERRED = "deferred"

        fun hadLauncherState(context: Context): Boolean =
            context.getSharedPreferences("launcher", Context.MODE_PRIVATE).all.isNotEmpty() ||
                context.getSharedPreferences("app_catalog", Context.MODE_PRIVATE).all.isNotEmpty()
    }
}
