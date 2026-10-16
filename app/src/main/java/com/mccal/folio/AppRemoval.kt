package com.mccal.folio

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Deleting an app from Folio, the iOS way on Android's rules (Lab scene `delete-apps`): a Home icon's Remove App opens
 * one choice, Delete App, Remove from Home or Cancel; the App Library says Delete App directly. Android shows its own
 * "uninstall?" confirmation for every delete and a launcher can't skip it, so Folio's alert is a choice, never a
 * second "are you sure?". No Archive in this version (McCal, 10 Oct).
 */
internal enum class RemoveChoice { DELETE, HIDE, REMOVE_FROM_HOME, APP_INFO }

/** Why an app can't be deleted from Folio, if it can't. */
internal enum class RemoveReason { NONE, CAME_WITH_PHONE, WORK_APP }

internal data class RemoveOptions(val reason: RemoveReason, val choices: List<RemoveChoice>)

/**
 * What Remove App offers. Only Android can delete an app that came with the phone, so it gets Hide and App Info instead
 * of a Delete that fails. A work app is deleted from App Info for now: a launcher deleting across profiles is not
 * checked on a phone yet.
 */
internal fun removeOptions(cameWithPhone: Boolean, isWork: Boolean, onHome: Boolean): RemoveOptions {
    val home = listOfNotNull(RemoveChoice.REMOVE_FROM_HOME.takeIf { onHome })
    return when {
        cameWithPhone -> RemoveOptions(RemoveReason.CAME_WITH_PHONE, listOf(RemoveChoice.HIDE) + home + RemoveChoice.APP_INFO)
        isWork -> RemoveOptions(RemoveReason.WORK_APP, home + RemoveChoice.APP_INFO)
        else -> RemoveOptions(RemoveReason.NONE, listOf(RemoveChoice.DELETE) + home)
    }
}

/** Whether Folio can ask Android to delete this app (the App Library's Delete App row). */
internal fun canDelete(cameWithPhone: Boolean, isWork: Boolean) = !cameWithPhone && !isWork

/** Whether [packageName] shipped with the phone, updated or not. Unknown counts as yes, so Folio never offers a Delete that fails. */
internal fun cameWithPhone(context: Context, packageName: String): Boolean = shippedWithPhone(context.packageManager, packageName) ?: true

/**
 * Remove App and Delete App for [app] as Home shows it. A Screenshot Mode stand-in is one of the phone's own apps, so a
 * swapped app is removed like one: no Delete App, and the alert says only Android can delete it. Otherwise a screenshot
 * of the menu would give the swap away, since the real stand-in never offers Delete.
 */
internal fun cameWithPhone(context: Context, app: AppEntry): Boolean = app.iconFrom != null || cameWithPhone(context, app.packageName)

/**
 * Whether [packageName] shipped with the phone, updated or not; null when Android won't say. The one check behind
 * Delete App (unknown means no Delete) and Screenshot Mode's stand-ins (unknown means not a stand-in).
 */
internal fun shippedWithPhone(pm: android.content.pm.PackageManager, packageName: String): Boolean? = runCatching {
    pm.getApplicationInfo(packageName, 0).flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
}.getOrNull()

/**
 * Android's own uninstall confirmation for [packageName], returning whether it was deleted. Needs
 * REQUEST_DELETE_PACKAGES (an install-time permission, no prompt). Deprecated in favor of PackageInstaller.uninstall,
 * which would need a receiver and a background activity start for the same dialog; this asks for the same thing
 * from the screen the person is on.
 */
@Suppress("DEPRECATION")
internal fun uninstallIntent(packageName: String): Intent =
    Intent(Intent.ACTION_UNINSTALL_PACKAGE, Uri.fromParts("package", packageName, null)).putExtra(Intent.EXTRA_RETURN_RESULT, true)

/** What came of a Delete App, read from Android's answer and whether the package is still there. */
internal enum class DeleteOutcome { DELETED, CANCELED, FAILED }

/**
 * Android's uninstall screen answers Cancel with RESULT_FIRST_USER, not RESULT_CANCELED: the newer screen
 * (packageinstaller v2, seen on the Android 17 emulator, 12 Oct) sends no status with it, the older one sends
 * DELETE_FAILED_ABORTED. A real failure always carries its status code. So reading the result code alone called a
 * Cancel a failure. The package being gone is the surest sign of a delete, whatever the code says.
 */
internal fun deleteOutcome(resultCode: Int, installResult: Int?, stillInstalled: Boolean): DeleteOutcome = when {
    !stillInstalled -> DeleteOutcome.DELETED
    resultCode == android.app.Activity.RESULT_CANCELED || installResult == null || installResult == DELETE_FAILED_ABORTED ->
        DeleteOutcome.CANCELED
    else -> DeleteOutcome.FAILED
}

/** PackageManager.DELETE_FAILED_ABORTED, hidden in the SDK: the person backed out of Android's confirmation. */
private const val DELETE_FAILED_ABORTED = -5

/** Intent.EXTRA_INSTALL_RESULT, hidden in the SDK: the uninstall screen's status code, when it sends one. */
private const val EXTRA_INSTALL_RESULT = "android.intent.extra.INSTALL_RESULT"

internal fun installResultOf(data: Intent?): Int? = data?.takeIf { it.hasExtra(EXTRA_INSTALL_RESULT) }?.getIntExtra(EXTRA_INSTALL_RESULT, 0)

internal fun isInstalled(context: Context, packageName: String): Boolean =
    runCatching { context.packageManager.getApplicationInfo(packageName, 0); true }.getOrDefault(false)

/** Remove App's one alert: the choices [options] allows, and Cancel. */
@Composable
internal fun RemoveAppAlert(app: AppEntry, options: RemoveOptions, onChoice: (RemoveChoice) -> Unit, onDismiss: () -> Unit) {
    // The alert follows the light or dark theme, so the choices take the buttons' own color (white on a light alert
    // was all but invisible, seen on the emulator 12 Oct) and Delete a red that holds contrast on either.
    val darkAlert = androidx.compose.material3.MaterialTheme.colorScheme.surface.luminance() < .5f
    val deleteInk = if (darkAlert) FolioColors.RedOnDark else androidx.compose.material3.MaterialTheme.colorScheme.error
    AlertDialog(onDismissRequest = onDismiss, modifier = Modifier.testTag("remove-app-alert"),
        title = { Text(stringResource(R.string.remove_app_title, app.label)) },
        text = {
            Column {
                Text(when (options.reason) {
                    RemoveReason.NONE -> stringResource(R.string.remove_app_message)
                    RemoveReason.CAME_WITH_PHONE -> stringResource(R.string.remove_app_came_with_phone, app.label)
                    RemoveReason.WORK_APP -> stringResource(R.string.remove_app_work, app.label)
                }, fontSize = FolioType.FOOTNOTE.sp)
                options.choices.forEach { choice ->
                    TextButton({ onChoice(choice) }, Modifier.fillMaxWidth().testTag("remove-app-${choice.name.lowercase()}")) {
                        Text(stringResource(when (choice) {
                            RemoveChoice.DELETE -> R.string.delete_app
                            RemoveChoice.HIDE -> R.string.hide_from_app_library
                            RemoveChoice.REMOVE_FROM_HOME -> R.string.remove_from_home
                            RemoveChoice.APP_INFO -> R.string.app_info
                        }), color = if (choice == RemoveChoice.DELETE) deleteInk else androidx.compose.ui.graphics.Color.Unspecified,
                            fontWeight = if (choice == RemoveChoice.DELETE) FontWeight.SemiBold else FontWeight.Normal)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onDismiss, Modifier.testTag("remove-app-cancel")) { Text(stringResource(R.string.cancel)) } })
}
