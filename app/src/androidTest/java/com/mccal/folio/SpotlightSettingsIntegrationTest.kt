package com.mccal.folio

import android.content.Context
import android.content.IntentFilter
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.Settings
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SpotlightSettingsIntegrationTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val device get() = UiDevice.getInstance(instrumentation)
    val compose = createAndroidComposeRule<MainActivity>()
    private val onboarding = object : ExternalResource() {
        private val saved = mutableMapOf<Pair<String, String>, Any?>()
        override fun before() {
            check(Build.HARDWARE in listOf("ranchu", "goldfish")) { "Only run on a disposable emulator" }
            val context = instrumentation.targetContext
            for ((file, keys) in mapOf("setup_experience" to listOf("finished"),
                "whats_new" to listOf("seen_version"), "dev_build" to listOf("seen_sha", "seen_branch"))) {
                val prefs = context.getSharedPreferences(file, Context.MODE_PRIVATE)
                keys.forEach { saved[file to it] = prefs.all[it] }
            }
            SetupExperience(context).finish()
            WhatsNew.markSeen(context)
            DevBuild.load(context)?.let { DevBuild.markSeen(context, it) }
            device.wakeUp()
        }
        override fun after() {
            for ((key, value) in saved) {
                val edit = instrumentation.targetContext.getSharedPreferences(key.first, 0).edit()
                when (value) {
                    null -> edit.remove(key.second)
                    is Boolean -> edit.putBoolean(key.second, value)
                    is String -> edit.putString(key.second, value)
                }
                edit.commit()
            }
        }
    }
    @get:Rule val rules = RuleChain.outerRule(onboarding).around(WithoutNativeFeed()).around(compose)

    private fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(
        instrumentation.uiAutomation.executeShellCommand(command)
    ).bufferedReader().use { it.readText() }

    private fun open(query: String, title: Int) {
        compose.waitUntil(15000) { !ViewModelProvider(compose.activity)[LauncherModel::class.java].state.value.loading }
        compose.runOnIdle { compose.activity.openSpotlight() }
        compose.onNodeWithTag("spotlight-field").performTextInput(query)
        compose.onNodeWithText(compose.activity.getString(title)).assertIsDisplayed()
    }

    private fun capture(name: String) {
        if (InstrumentationRegistry.getArguments().getString("captureSettings") != "true") return
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "spotlight-settings").apply { mkdirs() }
        assertTrue(device.takeScreenshot(File(directory, "$name.png")))
    }

    private fun launch(action: String, keyboard: Boolean, title: Int) {
        val launcher = compose.activity
        val monitor = instrumentation.addMonitor(IntentFilter(action), null, false)
        try {
            if (keyboard) compose.onNodeWithTag("spotlight-field").performImeAction()
            else compose.onNodeWithText(compose.activity.getString(title)).performClick()
            // Settings may rebuild its preference screen after an emulator display resize.
            assertTrue("Android Settings content did not open", device.wait(
                Until.hasObject(By.pkg("com.android.settings").clazz("android.widget.TextView")), 90000))
            assertTrue("Wrong settings action", monitor.hits > 0)
            instrumentation.runOnMainSync { assertFalse("Spotlight stayed open", launcher.spotlightVisible.value) }
            capture(action.substringAfterLast('.'))
            device.pressBack()
            // Keep the launcher intent so ActivityScenario still observes lifecycle events after onNewIntent.
            // The test package need not be the selected Home, and this does not change that role.
            shell("am start -W -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n ${instrumentation.targetContext.packageName}/${MainActivity::class.java.name}")
            assertTrue("Folio did not resume", device.wait(Until.hasObject(By.pkg(instrumentation.targetContext.packageName)), 30000))
            compose.runOnIdle { assertFalse(compose.activity.spotlightVisible.value) }
        } finally { instrumentation.removeMonitor(monitor) }
    }

    @Test fun displayResultOpensFromCoverAndInnerWindows() {
        val original = Regex("Override size: (\\d+x\\d+)").find(shell("wm size"))?.groupValues?.get(1)
        try {
            for ((name, size) in listOf("cover" to "984x2184", "inner" to "1968x2184")) {
                shell("wm size $size")
                compose.waitUntil(15000) { compose.activity.windowManager.currentWindowMetrics.bounds.width() == size.substringBefore('x').toInt() }
                open("display settings", R.string.display_brightness)
                capture(name)
                launch(Settings.ACTION_DISPLAY_SETTINGS, keyboard = false, R.string.display_brightness)
            }
        } finally { shell("wm size ${original ?: "reset"}") }
    }

    @Test fun keyboardSearchOpensAndroidNotifications() {
        open("notification settings", R.string.notifications_title)
        launch(if (Build.VERSION.SDK_INT >= 33) Settings.ACTION_ALL_APPS_NOTIFICATION_SETTINGS else Settings.ACTION_SETTINGS,
            keyboard = true, R.string.notifications_title)
    }
}
