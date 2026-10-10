package com.mccal.folio

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Settings › Advanced › Developer: set a passphrase, lock, get one wrong, unlock. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class DeveloperPageTest {
    @get:Rule val compose = createComposeRule()
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before fun fresh() { DevLockStore.clear(context); DevLockSession.lock() }
    @After fun clean() { DevLockStore.clear(context); DevLockSession.lock() }

    private fun page(shown: () -> Unit = {}) = compose.setContent {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) { DeveloperPage(onShowBuild = shown) }
    }

    private fun setPassphrase(pass: String, again: String = pass) {
        compose.onNodeWithTag("developer-set-start").performClick()
        compose.onNodeWithTag("developer-new").performTextInput(pass)
        compose.onNodeWithTag("developer-again").performTextInput(again)
        compose.onNodeWithTag("developer-set").performClick()
        compose.waitForIdle()
    }

    @Test fun `with no passphrase the page offers to set one`() {
        page()
        compose.onNodeWithTag("developer-set-start").assertIsDisplayed()
        assertNull(DevLockStore.load(context))
    }

    @Test fun `a short or mismatched passphrase is refused and nothing is saved`() {
        page()
        setPassphrase("short")
        compose.onNodeWithTag("developer-message").assertIsDisplayed()
        assertNull(DevLockStore.load(context))
        compose.onNodeWithTag("developer-new").performTextInput("long enough 1")
        compose.onNodeWithTag("developer-again").performTextInput("long enough 2")
        compose.onNodeWithTag("developer-set").performClick()
        compose.onNodeWithTag("developer-message").assertIsDisplayed()
        assertNull(DevLockStore.load(context))
    }

    @Test fun `setting a passphrase saves a hash, never the text, and unlocks`() {
        page()
        setPassphrase("correct horse")
        val saved = DevLockStore.load(context)
        assertNotNull(saved)
        assertFalse(String(saved!!.hash, Charsets.ISO_8859_1).contains("correct horse"))
        assertTrue(DevLockSession.unlocked())
        compose.onNodeWithTag("developer-lock").assertIsDisplayed()
    }

    @Test fun `a locked page asks for the passphrase and a wrong one is counted`() {
        DevLockStore.save(context, DevLock.create("correct horse".toCharArray(), iterations = 1_000))
        page()
        compose.onNodeWithTag("developer-pass").performTextInput("wrong wrong")
        compose.onNodeWithTag("developer-unlock").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("developer-message").assertIsDisplayed()
        assertTrue(DevLockStore.load(context)!!.failures == 1)
        assertFalse(DevLockSession.unlocked())
    }

    @Test fun `the right passphrase unlocks and clears the failures`() {
        DevLockStore.save(context, DevLock.create("correct horse".toCharArray(), iterations = 1_000).copy(failures = 2))
        page()
        compose.onNodeWithTag("developer-pass").performTextInput("correct horse")
        compose.onNodeWithTag("developer-unlock").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("developer-lock").assertIsDisplayed()
        assertTrue(DevLockStore.load(context)!!.failures == 0)
    }

    @Test fun `five wrong tries start a wait that blocks the page`() {
        val seed = DevLock.create("correct horse".toCharArray(), iterations = 1_000)
        DevLockStore.save(context, seed.copy(failures = 4))
        page()
        compose.onNodeWithTag("developer-pass").performTextInput("wrong wrong")
        compose.onNodeWithTag("developer-unlock").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("developer-waiting").assertIsDisplayed()
        assertTrue(DevLockStore.load(context)!!.lockedUntilMs > System.currentTimeMillis())
    }
}
