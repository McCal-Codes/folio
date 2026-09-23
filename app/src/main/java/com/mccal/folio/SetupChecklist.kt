package com.mccal.folio

import androidx.compose.ui.res.stringResource
import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle

/** One thing Folio needs (or recommends) and whether it's done. */
internal data class SetupStep(
    val icon: ImageVector, val title: String, val detail: String, val done: Boolean,
    val required: Boolean, val action: String, val onAction: () -> Unit,
)

@Composable
internal fun rememberSetupSteps(isDefaultHome: Boolean, onMakeDefault: () -> Unit, onShadeSetup: () -> Unit,
    messagesApp: String? = null, onMessagesApp: (String?) -> Unit = {},
    systemWallpaper: Boolean = false, onSystemWallpaper: (Boolean) -> Unit = {}): List<SetupStep> {
    val context = LocalContext.current
    // Re-check every time Folio comes back from a settings screen.
    var tick by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) { lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { tick++ } }
    val contacts = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }
    fun open(intent: Intent) = runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }

    return remember(tick, isDefaultHome, messagesApp, systemWallpaper) {
        val notifications = context.getSystemService(NotificationManager::class.java)
        listOfNotNull(
            SetupStep(Icons.Rounded.Home, context.getString(R.string.make_folio_your_home_app), context.getString(R.string.so_home_gestures_and_the_fold_effect_are),
                isDefaultHome, true, context.getString(R.string.set)) { onMakeDefault() },
            SetupStep(Icons.Rounded.Notifications, context.getString(R.string.notification_access),
                context.getString(R.string.powers_the_dynamic_island_and_notificati),
                IslandListenerService.hasAccess(context), true, context.getString(R.string.allow)) { open(IslandListenerService.accessSettingsIntent(context)) },
            SetupStep(Icons.Rounded.Accessibility, context.getString(R.string.folio_gestures_service),
                context.getString(R.string.lets_pull_downs_open_system_panels_and_s),
                SystemShadeAccessibilityService.isConnected(), true, context.getString(R.string.turn_on)) { onShadeSetup() },
            // Samsung's setting; on phones without it there's nothing to set, so it isn't a step.
            if (foldLockSetting(context) != null) SetupStep(Icons.Rounded.Devices, context.getString(R.string.continue_apps_on_cover_screen_always),
                context.getString(R.string.keeps_the_cover_screen_on_when_you_fold),
                foldStaysAwake(context), true, context.getString(R.string.open)) { open(Intent(Settings.ACTION_DISPLAY_SETTINGS)) } else null,
            SetupStep(Icons.Rounded.LightMode, context.getString(R.string.modify_system_settings),
                context.getString(R.string.lets_control_center_change_brightness_an),
                Settings.System.canWrite(context), false, context.getString(R.string.allow)) {
                open(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}")))
            },
            SetupStep(Icons.Rounded.DarkMode, context.getString(R.string.do_not_disturb_access), context.getString(R.string.lets_control_center_turn_do_not_disturb),
                notifications.isNotificationPolicyAccessGranted, false, context.getString(R.string.allow)) { open(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) },
            SetupStep(Icons.Rounded.Wallpaper, context.getString(R.string.keep_your_wallpaper),
                context.getString(R.string.coming_from_samsung_s_or_another_launche),
                systemWallpaper, false, context.getString(R.string.use)) { onSystemWallpaper(true); context.asActivity()?.applyWallpaperWindow(true) },
            SetupStep(Icons.Rounded.Assistant, context.getString(R.string.folio_as_your_digital_assistant),
                context.getString(R.string.holding_the_side_key_opens_folio_s_picke),
                AssistPickerActivity.isDefaultAssistant(context), false, context.getString(R.string.choose)) { open(AssistPickerActivity.settingsIntent()) },
            if (sideKeySettings(context) != null) SetupStep(Icons.Rounded.TouchApp, context.getString(R.string.hold_side_key_digital_assistant),
                context.getString(R.string.samsung_side_button_press_and_hold_digit),
                sideKeyHoldIsAssistant(context), false, context.getString(R.string.open)) { sideKeySettings(context)?.let(::open) } else null,
            SetupStep(Icons.Rounded.Contacts, context.getString(R.string.contacts_in_spotlight), context.getString(R.string.search_your_contacts_from_spotlight),
                granted(context, Manifest.permission.READ_CONTACTS), false, context.getString(R.string.allow)) { contacts.launch(Manifest.permission.READ_CONTACTS) },
            // Only for people who already use OpenBubbles (iMessage on Android); Folio just opens it.
            if (Messaging.installed(context, Messaging.OPENBUBBLES)) SetupStep(Icons.Rounded.Forum, context.getString(R.string.imessage_with_openbubbles),
                context.getString(R.string.message_contacts_from_spotlight_in_openb),
                messagesApp == Messaging.OPENBUBBLES, false, context.getString(R.string.use)) { onMessagesApp(Messaging.OPENBUBBLES) } else null,
        )
    }
}

/** Samsung's "Side button › Press and hold" screen, when this phone has it. */
internal fun sideKeySettings(context: Context): Intent? =
    Intent("com.samsung.android.intent.action.SIDE_KEY_LONG_PRESS_SETTINGS")
        .takeIf { it.resolveActivity(context.packageManager) != null }

/** Samsung stores the side key hold action as a global setting; 2 is the digital assistant. */
internal fun sideKeyHoldIsAssistant(context: Context) =
    runCatching { Settings.Global.getInt(context.contentResolver, "function_key_config_longpress_type") }.getOrNull() == 2

/** Samsung's "Side button › Double press" screen, when this phone has it. */
internal fun sideKeyDoublePressSettings(context: Context): Intent? =
    Intent("com.samsung.android.intent.action.SIDE_KEY_DOUBLE_PRESS_SETTINGS")
        .takeIf { it.resolveActivity(context.packageManager) != null }

/** Whether Samsung's double press opens Google Wallet (its settings name the app it launches). */
internal fun sideKeyDoublePressIsWallet(context: Context): Boolean = runCatching {
    val r = context.contentResolver
    listOf("function_key_config_doublepress_value", "function_key_config_doublepress_intent")
        .any { Settings.Global.getString(r, it)?.contains("com.google.android.apps.walletnfcrel") == true }
}.getOrDefault(false)

private fun granted(context: Context, permission: String) =
    context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

private fun foldLockSetting(context: Context): String? =
    runCatching { Settings.System.getString(context.contentResolver, "fold_lock_behavior_setting") }.getOrNull()

internal fun foldStaysAwake(context: Context) = foldLockSetting(context) == "stay_awake_on_fold_key"

