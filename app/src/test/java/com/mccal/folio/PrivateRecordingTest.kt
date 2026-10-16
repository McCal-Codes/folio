package com.mccal.folio

import android.graphics.Bitmap
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Private While Recording: the on/off logic, the debounce, and what each surface shows while it is on. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PrivateRecordingTest {
    private fun bitmap() = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    private fun app(pkg: String, label: String) = AppEntry("$pkg/.Main", label, bitmap())
    private val clock = app("com.android.clock", "Clock")

    // ---- the state holder ----

    @Test fun `it is on only when the switch is on and Android says Folio is being recorded`() = runTest {
        val st = PrivateViewState(this, 1_000)
        st.report(true)
        assertFalse("recording but the switch is off", st.on.value)
        st.setEnabled(true)
        assertTrue(st.on.value)
        st.setEnabled(false)
        assertFalse("turning the switch off ends it at once", st.on.value)
    }

    @Test fun `it goes on at once and off only after a steady second`() = runTest {
        val st = PrivateViewState(this, 1_000)
        st.setEnabled(true)
        st.report(true)
        assertTrue("on at once", st.on.value)
        st.report(false)
        assertTrue("still on right after not visible", st.on.value)
        advanceTimeBy(999); runCurrent()
        assertTrue(st.on.value)
        advanceTimeBy(2); runCurrent()
        assertFalse(st.on.value)
        assertFalse(st.recording.value)
    }

    @Test fun `a quick flip does not make it flap`() = runTest {
        val st = PrivateViewState(this, 1_000)
        st.setEnabled(true)
        val changes = mutableListOf<Boolean>()
        val watch = launch { st.on.collect { changes += it } }
        runCurrent()
        st.report(true)
        repeat(5) { st.report(false); advanceTimeBy(300); runCurrent(); st.report(true) }
        advanceTimeBy(5_000); runCurrent()
        assertEquals("one change on, nothing else", listOf(false, true), changes)
        watch.cancel()
    }

    @Test fun `the notice fires once when it goes on and never when it goes off`() = runTest {
        val st = PrivateViewState(this, 1_000)
        st.setEnabled(true)
        var started = 0
        val watch = launch { st.started.collect { started++ } }
        runCurrent()
        st.report(true); runCurrent()
        st.report(true); runCurrent()
        assertEquals(1, started)
        st.report(false); advanceTimeBy(1_500); runCurrent()
        assertEquals("no notice when it stops", 1, started)
        st.report(true); runCurrent()
        assertEquals(2, started)
        watch.cancel()
    }

    @Test fun `a not-visible report while nothing is recorded changes nothing`() = runTest {
        val st = PrivateViewState(this, 1_000)
        st.setEnabled(true)
        st.report(false)
        assertFalse(st.on.value)
        assertFalse(st.recording.value)
    }

    // ---- Swap Apps ----

    @Test fun `the swap applies in Screenshot Mode or the private view, and never with no apps chosen`() {
        val rules = DisguiseRules(chosen = setOf("a"))
        assertFalse(swapActive(screenshot = false, private = false, rules = rules))
        assertTrue(swapActive(screenshot = true, private = false, rules = rules))
        assertTrue(swapActive(screenshot = false, private = true, rules = rules))
        assertFalse("nothing chosen: nothing swapped", swapActive(screenshot = false, private = true, rules = DisguiseRules()))
        assertTrue(swapActive(false, true, DisguiseRules(allThirdParty = true)))
    }

    // ---- badges ----

    @Test fun `numbers become dots while private, Dot and Off stay`() {
        assertEquals(BadgeStyle.DOT, privateBadgeStyle(BadgeStyle.COUNT, true))
        assertEquals(BadgeStyle.DOT, privateBadgeStyle(BadgeStyle.DOT, true))
        assertEquals(BadgeStyle.OFF, privateBadgeStyle(BadgeStyle.OFF, true))
        assertEquals(BadgeStyle.COUNT, privateBadgeStyle(BadgeStyle.COUNT, false))
    }

    // ---- notifications ----

    private fun notification(pkg: String = "com.chat") = NotificationItem("k", pkg, "Chat", bitmap(), "Alex", "Running late", 1L, true, null,
        canReply = true, canMarkRead = true)

    @Test fun `a notification shows the app and the word, with no title, text or reply`() {
        val out = redactNotification(notification(), null, "Notification")
        assertNull(out.title)
        assertEquals("Notification", out.text)
        assertEquals("Chat", out.appLabel)
        assertFalse(out.canReply)
        assertFalse(out.canMarkRead)
    }

    @Test fun `a swapped app's notification shows the stand-in's name and icon`() {
        val out = redactNotification(notification(), clock, "Notification")
        assertEquals("Clock", out.appLabel)
        assertSame(clock.icon, out.icon)
        assertEquals("it still opens the real app", "com.chat", out.packageName)
    }

    // ---- island events ----

    private fun message() = IslandEvent.Message("k", "com.chat", "Chat", "Alex", "Running late", bitmap(), bitmap(), canReply = true)

    @Test fun `a message shows the app and the word instead of the sender and text`() {
        val out = redactEvent(message(), emptyMap(), "Notification") as IslandEvent.Message
        assertEquals("Chat", out.sender)
        assertEquals("Notification", out.text)
        assertNull(out.avatar)
        assertFalse(out.canReply)
    }

    @Test fun `a swapped app's message never names the real app`() {
        val out = redactEvent(message(), mapOf("com.chat" to clock), "Notification") as IslandEvent.Message
        assertEquals("Clock", out.appLabel)
        assertEquals("Clock", out.sender)
        assertSame(clock.icon, out.appIcon)
        assertEquals("it still opens the real app", "com.chat", out.packageName)
    }

    @Test fun `a Bluetooth device loses its name and other events pass through`() {
        assertNull((redactEvent(IslandEvent.Bluetooth("Alex's Buds"), emptyMap(), "Notification") as IslandEvent.Bluetooth).name)
        val charging = IslandEvent.Charging(50)
        assertSame(charging, redactEvent(charging, emptyMap(), "Notification"))
        val notice = IslandEvent.Notice("Recording: private view on")
        assertSame("the notice itself is never redacted", notice, redactEvent(notice, mapOf("x" to clock), "Notification"))
    }

    // ---- island activities ----

    @Test fun `calls, timers, directions and progress show as an icon only`() {
        val call = IslandActivity.Call("com.phone", "Mom", bitmap(), 1L, "k", incoming = true, avatar = bitmap(), canAnswer = true)
        val c = redactActivity(call, null) as IslandActivity.Call
        assertEquals("", c.title)
        assertNull(c.avatar)
        assertTrue("the answer button stays", c.canAnswer)
        assertEquals("", redactActivity(IslandActivity.Timer("com.clock", "Tea", bitmap(), 1L, true, "k"), null).title)
        val nav = redactActivity(IslandActivity.Navigation("com.maps", "Home", "5 min", bitmap(), "k"), null) as IslandActivity.Navigation
        assertEquals("", nav.title); assertNull(nav.subtitle)
        val progress = redactActivity(IslandActivity.Progress("com.dl", "File.zip", "50%", bitmap(), .5f, "k"), null) as IslandActivity.Progress
        assertEquals("", progress.title); assertNull(progress.subtitle)
    }

    @Test fun `a swapped app's activity shows the stand-in's icon`() {
        val out = redactActivity(IslandActivity.Call("com.chat", "Mom", bitmap(), 1L, "k"), clock)
        assertSame(clock.icon, out.icon)
        assertEquals("com.chat", out.packageName)
    }

    @Test fun `the flows follow the switch and the swapped apps`() = runTest {
        // Not a Compose test: the flow helper is what the island and Notification Center read.
        val source = kotlinx.coroutines.flow.MutableStateFlow(listOf(notification()))
        val shown = PrivateRecording.redact(source) { list, swapped -> list.map { redactNotification(it, swapped[it.packageName], "Notification") } }
        assertEquals("Alex", shown.value.single().title)
    }

    // ---- two sources: the activity and the overlay service ----

    @Test fun `any source seeing a recording keeps it on, and one stopping does not clear it`() = runTest {
        val st = PrivateViewState(this, 1_000)
        st.setEnabled(true)
        st.report(true, PrivateViewState.OVERLAY)
        st.report(true, PrivateViewState.ACTIVITY)
        st.detach(PrivateViewState.ACTIVITY)
        advanceTimeBy(5_000); runCurrent()
        assertTrue("the overlay still reports a recording", st.on.value)
        st.report(false, PrivateViewState.OVERLAY)
        advanceTimeBy(999); runCurrent()
        assertTrue(st.on.value)
        advanceTimeBy(2); runCurrent()
        assertFalse(st.on.value)
    }

    @Test fun `one source saying no does not end it while another says yes`() = runTest {
        val st = PrivateViewState(this, 1_000)
        st.setEnabled(true)
        st.report(true, PrivateViewState.OVERLAY)
        st.report(false, PrivateViewState.ACTIVITY)
        advanceTimeBy(5_000); runCurrent()
        assertTrue(st.on.value)
    }

    @Test fun `with no source left the state clears at once`() = runTest {
        val st = PrivateViewState(this, 1_000)
        st.setEnabled(true)
        st.report(true, PrivateViewState.ACTIVITY)
        st.detach(PrivateViewState.ACTIVITY)
        assertFalse(st.on.value)
        st.detach(PrivateViewState.ACTIVITY) // twice is fine
        assertFalse(st.on.value)
    }

    // ---- the swap before the package scan is done ----

    private val reddit = app("com.reddit.frontpage", "Reddit")
    private val camera = app("com.android.camera", "Camera")
    private val rules = DisguiseRules(chosen = setOf(reddit.id))
    private val home = LauncherState(apps = listOf(camera, reddit))

    @Test fun `the private view never shows the real apps while the system packages are unknown`() {
        val out = disguisedState(home, rules, system = null, onHome = emptySet(), hideUntilKnown = true)
        assertTrue(out.apps.isEmpty())
    }

    @Test fun `Screenshot Mode keeps the real apps until the scan is done, as before`() {
        assertSame(home, disguisedState(home, rules, system = null, onHome = emptySet(), hideUntilKnown = false))
    }

    @Test fun `once classified the private view shows the disguised apps`() {
        val out = disguisedState(home, rules, system = setOf(camera.packageName), onHome = emptySet(), hideUntilKnown = true)
        assertEquals("Camera", out.apps.single { it.id == reddit.id }.label)
    }

    @Test fun `the package cache knows nothing at first, then what it was told, and treats a newcomer as third-party`() {
        SystemPackageCache.clear()
        val both = setOf(camera.packageName, reddit.packageName)
        assertNull(SystemPackageCache.systemFor(both))
        SystemPackageCache.put(camera.packageName, true)
        SystemPackageCache.put(reddit.packageName, false)
        assertEquals(setOf(camera.packageName), SystemPackageCache.systemFor(both))
        assertEquals(setOf(camera.packageName), SystemPackageCache.systemFor(both + "com.new.app"))
        SystemPackageCache.clear()
    }

    // ---- a swapped app with no stand-in (the overlay runs, Home never composed) ----

    @Test fun `a swapped package is found from the saved rules, and a system app never is`() {
        val unknown: (String) -> Boolean? = { null }
        assertTrue(isSwappedPackage("com.reddit.frontpage", rules, unknown))
        assertFalse(isSwappedPackage("com.other", rules, unknown))
        assertFalse("shipped with the phone", isSwappedPackage("com.reddit.frontpage", rules) { true })
        assertTrue(isSwappedPackage("com.other", DisguiseRules(allThirdParty = true), unknown))
        assertFalse(isSwappedPackage("com.android.camera", DisguiseRules(allThirdParty = true)) { true })
        assertFalse("no rules, nothing swapped", isSwappedPackage("com.reddit.frontpage", DisguiseRules(), unknown))
    }

    @Test fun `private on with a swapped package and no stand-in, the real label and icon never show`() {
        val n = NotificationItem("k", "com.reddit.frontpage", "Reddit", bitmap(), "u/someone", "hi", 1L, true, null)
        val out = redactNotification(n, null, "Notification", neutral = true)
        assertEquals("Notification", out.appLabel)
        assertNull(out.icon)
        val msg = IslandEvent.Message("k", "com.reddit.frontpage", "Reddit", "u/someone", "hi", bitmap(), bitmap(), canReply = true)
        val e = redactEvent(msg, emptyMap(), "Notification", neutral = true) as IslandEvent.Message
        assertEquals("Notification", e.appLabel)
        assertEquals("Notification", e.sender)
        assertNull(e.appIcon)
        val call = IslandActivity.Call("com.reddit.frontpage", "Mom", bitmap(), 1L, "k")
        assertNull(redactActivity(call, null, neutral = true).icon)
    }

    @Test fun `PrivateRecording says neutral for a swapped package without a stand-in and not once it has one`() {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        ScreenshotSwap.set(context, rules)
        SystemPackageCache.clear()
        assertTrue(PrivateRecording.neutral(reddit.packageName, emptyMap()))
        assertFalse(PrivateRecording.neutral(reddit.packageName, mapOf(reddit.packageName to camera)))
        assertFalse(PrivateRecording.neutral("com.other", emptyMap()))
        ScreenshotSwap.set(context, DisguiseRules())
    }
}
