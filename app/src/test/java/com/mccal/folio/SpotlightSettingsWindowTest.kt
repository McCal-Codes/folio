package com.mccal.folio

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SpotlightSettingsWindowTest {
    @get:Rule val compose = createComposeRule()
    private fun checkWindow(query: String, title: Int, imageName: String) {
        var host: View? = null
        compose.setContent {
            val view = LocalView.current
            SideEffect { host = view }
            SpotlightOverlay(true, { 1f }, LauncherState(spotlightHidden = setOf("SUGGESTIONS", "CONTACTS", "CALCULATOR", "WEB")), {}, {}, {})
        }
        compose.onNodeWithTag("spotlight-field").performTextInput(query)
        val application = ApplicationProvider.getApplicationContext<Application>()
        compose.onNodeWithText(application.getString(title)).assertIsDisplayed()
        System.getenv("FOLIO_UI_CAPTURE_DIR")?.let { directory ->
            val output = File(directory, imageName)
            output.parentFile.mkdirs()
            // Draw the view directly: Robolectric has no real Surface for Android's asynchronous PixelCopy API.
            compose.runOnIdle {
                val view = checkNotNull(host).rootView
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap))
                output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
    }

    @Test @Config(qualifiers = "w360dp-h800dp")
    fun phoneSettingsFitCoverWindow() = checkWindow("display settings", R.string.display_brightness, "finder-cover.png")

    @Test @Config(qualifiers = "w820dp-h900dp")
    fun phoneSettingsFitInnerWindow() = checkWindow("notification settings", R.string.notifications_title, "finder-inner.png")
}
