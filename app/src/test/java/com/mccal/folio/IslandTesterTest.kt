package com.mccal.folio

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The Island tester: a made-up activity wins over the real one in Folio Dev, clears itself, and never works in a public build. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IslandTesterTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val dev: Context = object : ContextWrapper(app) { override fun getPackageName() = "com.mccal.folio.dev" }
    private val public: Context = object : ContextWrapper(app) { override fun getPackageName() = "com.mccal.folio" }
    private val real = IslandActivity.Progress("com.real", "Real", null, null, 0.5f, "real")
    private val fake = IslandActivity.Progress("com.mccal.folio.dev", "Example", null, null, 0.4f, "fake")

    @Before fun fresh() { IslandTester.clear(dev) }
    @After fun done() { IslandTester.clear(dev) }

    @Test fun `a fake wins over the real activity and Clear brings the real one back`() = runTest(StandardTestDispatcher()) {
        IslandTester.scope = this
        val source = MutableStateFlow<IslandActivity?>(real)
        val shown = IslandTester.overlay(source, backgroundScope)
        runCurrent()
        assertSame(real, shown.value)
        assertTrue(IslandTester.show(dev, fake)); runCurrent()
        assertSame(fake, shown.value)
        IslandTester.clear(dev); runCurrent()
        assertSame(real, shown.value)
    }

    @Test fun `a fake clears itself after a minute`() = runTest(StandardTestDispatcher()) {
        IslandTester.scope = this
        val source = MutableStateFlow<IslandActivity?>(real)
        val shown = IslandTester.overlay(source, backgroundScope)
        IslandTester.show(dev, fake); runCurrent()
        advanceTimeBy(IslandTester.FAKE_MS - 1); runCurrent()
        assertSame(fake, shown.value)
        advanceTimeBy(2); runCurrent()
        assertSame(real, shown.value)
        assertNull(IslandTester.fake.value)
    }

    @Test fun `a public build injects nothing`() {
        assertFalse(IslandTester.show(public, fake))
        assertFalse(IslandTester.incomingCall(public))
        assertFalse(IslandTester.music(public))
        assertFalse(IslandTester.messageWithReply(public))
        assertFalse(IslandTester.silent(public, true))
        assertNull(IslandTester.fake.value)
        assertEquals(null, IslandEvents.latest.value?.takeIf { (it.first as? IslandEvent.Message)?.key == "tester-message" })
    }

    @Test fun `a public build cannot clear a fake`() {
        IslandTester.show(dev, fake)
        IslandTester.clear(public)
        assertSame(fake, IslandTester.fake.value)
    }
}
