package com.mccal.folio

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** First setup's Simple branch: only behind its gate, three pages that skip navigation and Advanced, and a resumable pick. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SimpleSetupTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun `with the gate shut, setup is exactly what it was`() {
        assertFalse("no branch, whatever was asked for or saved", simpleSetupBranch(gateOpen = false, wanted = true))
        assertEquals(listOf("welcome", "feel", "home", "done"), setupPageKeys(androidStart = false, advanced = false, homeDone = false, simpleBranch = false))
        assertEquals(listOf("welcome", "feel", "nav", "home", "adv", "done"), setupPageKeys(androidStart = true, advanced = true, homeDone = false, simpleBranch = false))
        assertEquals(listOf("welcome", "feel", "done"), setupPageKeys(androidStart = false, advanced = false, homeDone = true, simpleBranch = false))
        // The old filter's rule for each page, so a change to either is caught.
        for (android in listOf(false, true)) for (advanced in listOf(false, true)) for (home in listOf(false, true)) {
            val keys = setupPageKeys(android, advanced, home, simpleBranch = false)
            assertTrue(SimpleSetupPages.all.none { it in keys })
            assertEquals(android, "nav" in keys); assertEquals(advanced, "adv" in keys); assertEquals(!home, "home" in keys)
        }
    }

    @Test fun `the Home apps page sits after the starting point, and the branch keeps its own`() {
        assertEquals(listOf("welcome", "feel", "apps", "nav", "home", "done"),
            setupPageKeys(androidStart = true, advanced = false, homeDone = false, simpleBranch = false, homeApps = true))
        assertEquals(listOf("welcome", "feel", "su-style", "su-apps", "home", "su-ready"),
            setupPageKeys(androidStart = true, advanced = false, homeDone = false, simpleBranch = true, homeApps = true))
    }

    @Test fun `the branch has three pages, drops navigation and Advanced, and still asks for Home`() {
        assertEquals(listOf("welcome", "feel", "su-style", "su-apps", "home", "su-ready"),
            setupPageKeys(androidStart = true, advanced = true, homeDone = false, simpleBranch = true))
        assertEquals(listOf("welcome", "feel", "su-style", "su-apps", "su-ready"),
            setupPageKeys(androidStart = false, advanced = false, homeDone = true, simpleBranch = true))
        assertTrue("a restart mid-branch comes back into it", simpleSetupBranch(gateOpen = true, wanted = "su-apps" in SimpleSetupPages.all))
    }

    @Test fun `the helper's picks survive a restart, start checked, and never leave Home empty`() {
        val prefs = context.getSharedPreferences("setup_experience_test", Context.MODE_PRIVATE)
        val offered = listOf("com.phone/.P", "com.msg/.M")
        val choices = SimpleSetupChoices(prefs)
        assertEquals(SimpleStyle.LIST, choices.style)
        assertEquals("everything the phone offers starts checked", offered, choices.apps(offered))
        choices.pick(SimpleStyle.GRID)
        choices.toggle("com.msg/.M", offered)
        choices.toggle("com.phone/.P", offered)
        assertEquals("the last checked app stays", listOf("com.phone/.P"), choices.apps(offered))
        val again = SimpleSetupChoices(prefs)
        assertEquals(SimpleStyle.GRID, again.style)
        assertEquals(listOf("com.phone/.P"), again.apps(offered))
        again.clear()
        assertEquals(SimpleStyle.LIST, SimpleSetupChoices(prefs).style)
    }

    @Test fun `the first-open line shows once, after setup, and never again after OK`() {
        assertFalse(SimpleFirstOpen.pending(context))
        SimpleFirstOpen.arm(context)
        assertTrue(SimpleFirstOpen.pending(context))
        SimpleFirstOpen.done(context)
        assertFalse(SimpleFirstOpen.pending(context))
    }
}
