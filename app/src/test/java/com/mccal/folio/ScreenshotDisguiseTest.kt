package com.mccal.folio

import android.graphics.Bitmap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ScreenshotDisguiseTest {
    private fun app(pkg: String, label: String, isWork: Boolean = false) =
        AppEntry("$pkg/.Main", label, Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888), isWork = isWork)

    private val camera = app("com.android.camera", "Camera")
    private val clock = app("com.android.clock", "Clock")
    private val phone = app("com.android.dialer", "Phone")
    private val reddit = app("com.reddit.frontpage", "Reddit")
    private val bank = app("com.example.bank", "Bank")
    private val system = setOf("com.android.camera", "com.android.clock", "com.android.dialer")
    private val apps = listOf(bank, camera, clock, phone, reddit)

    @Test fun `with no rules the list comes back untouched`() {
        assertSame(apps, ScreenshotDisguise.apply(apps, DisguiseRules(), system))
    }

    @Test fun `a chosen app shows a stock app's icon and name but keeps its id and component`() {
        val out = ScreenshotDisguise.apply(apps, DisguiseRules(chosen = setOf(reddit.id)), system)
        val swapped = out.single { it.id == reddit.id }
        val standIn = swapped.iconFrom!!
        assertTrue(standIn.packageName in system)
        assertEquals(standIn.label, swapped.label)
        assertEquals("search finds it only by the name on screen", standIn.label, swapped.systemLabel)
        assertSame(standIn.icon, swapped.icon)
        assertEquals(reddit.component, swapped.component)
        assertNull("an app not chosen is left alone", out.single { it.id == bank.id }.iconFrom)
    }

    @Test fun `the same app gets the same stand-in every time, even when other apps come and go`() {
        val rules = DisguiseRules(chosen = setOf(reddit.id))
        val first = ScreenshotDisguise.apply(apps, rules, system).single { it.id == reddit.id }.label
        val again = ScreenshotDisguise.apply(apps.shuffled(), rules, system).single { it.id == reddit.id }.label
        val more = ScreenshotDisguise.apply(apps + app("com.example.notes", "Notes"), rules, system)
            .single { it.id == reddit.id }.label
        assertEquals(first, again)
        assertEquals(first, more)
    }

    @Test fun `all third-party swaps every app that did not ship with the phone and no system app`() {
        val out = ScreenshotDisguise.apply(apps, DisguiseRules(allThirdParty = true), system)
        assertTrue(out.filter { it.packageName in system }.all { it.iconFrom == null })
        assertTrue(out.filter { it.packageName !in system }.all { it.iconFrom != null })
    }

    @Test fun `a system app is never swapped, even when chosen`() {
        val out = ScreenshotDisguise.apply(apps, DisguiseRules(chosen = setOf(camera.id)), system)
        assertSame(apps, out)
    }

    @Test fun `two apps may share a stand-in`() {
        val one = listOf(camera) + (1..10).map { app("com.example.app$it", "App $it") }
        val out = ScreenshotDisguise.apply(one, DisguiseRules(allThirdParty = true), setOf(camera.packageName))
        assertTrue(out.filter { it.iconFrom != null }.all { it.label == "Camera" })
    }

    @Test fun `a work app is never a stand-in`() {
        val workCamera = app("com.android.camera", "Camera", isWork = true)
        assertEquals(listOf(clock), ScreenshotDisguise.standIns(listOf(workCamera, clock), system))
    }

    @Test fun `with no stand-in on the phone nothing is swapped`() {
        val out = ScreenshotDisguise.apply(listOf(reddit, bank), DisguiseRules(allThirdParty = true), system)
        assertTrue(out.all { it.iconFrom == null })
    }

    @Test fun `the list is sorted by the names shown`() {
        val out = ScreenshotDisguise.apply(apps, DisguiseRules(allThirdParty = true), system)
        assertEquals(out.map { it.label }.sorted(), out.map { it.label })
    }
}
