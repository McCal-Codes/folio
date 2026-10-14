package com.mccal.folio

import android.graphics.Bitmap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
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

    @Test fun `with one stock app every swapped app shares it`() {
        val one = listOf(camera) + (1..10).map { app("com.example.app$it", "App $it") }
        val out = ScreenshotDisguise.apply(one, DisguiseRules(allThirdParty = true), setOf(camera.packageName))
        assertTrue(out.filter { it.iconFrom != null }.all { it.label == "Camera" })
    }

    private val maps = app("com.google.android.apps.maps", "Maps")
    private val gallery = app("com.android.gallery", "Gallery")
    private val settings = app("com.android.settings", "Settings")
    private val stock = listOf(camera, clock, phone, maps, gallery, settings)
    private val stockPackages = stock.mapTo(mutableSetOf()) { it.packageName }
    private fun thirdParty(n: Int) = (1..n).map { app("com.example.app$it", "App $it") }
    private fun picks(swapped: List<AppEntry>, homeStock: List<AppEntry>, onHome: Set<String> = emptySet()) =
        ScreenshotDisguise.pickStandIns(swapped.map { it.id }, ScreenshotDisguise.standIns(stock, stockPackages),
            homeStock.mapTo(mutableSetOf()) { it.packageName }, onHome)

    @Test fun `a stock app that is on Home is never a stand-in while others are free`() {
        // The Fold8 case: Notes showed as Maps next to the real Maps, Spotify as Camera next to the real Camera.
        val notes = app("com.example.notes", "Notes")
        val spotify = app("com.spotify.music", "Spotify")
        val all = stock + notes + spotify
        val onHome = setOf(maps.id, camera.id, notes.id, spotify.id)
        val out = ScreenshotDisguise.apply(all, DisguiseRules(allThirdParty = true), stockPackages, onHome)
        val home = out.filter { it.id in onHome }.map { it.label }
        assertEquals("no name twice on Home: $home", home.size, home.toSet().size)
        assertTrue(out.filter { it.iconFrom != null }.none { it.iconFrom!!.packageName in setOf(maps.packageName, camera.packageName) })
    }

    @Test fun `swapped apps take the stock apps that are not on Home, one each`() {
        // Six stock apps, three on Home, three swapped: whatever the hashes, the three free ones are used once each.
        val swapped = thirdParty(3)
        val got = picks(swapped, homeStock = listOf(camera, maps, phone)).values.map { it.packageName }
        assertEquals(setOf(clock, gallery, settings).map { it.packageName }.toSet(), got.toSet())
        assertEquals(3, got.size)
    }

    @Test fun `no two swapped apps share a stand-in while the stock apps last`() {
        val got = picks(thirdParty(6), homeStock = emptyList()).values.map { it.packageName }
        assertEquals(6, got.toSet().size)
    }

    @Test fun `when the stock apps run out, repeats use the ones not on Home, evenly`() {
        // Two free stock apps (four are on Home) and five swapped apps: only the free two, used 3 and 2 times.
        val got = picks(thirdParty(5), homeStock = listOf(camera, maps, phone, settings)).values.groupingBy { it.packageName }.eachCount()
        assertEquals(setOf(clock.packageName, gallery.packageName), got.keys)
        assertEquals(listOf(2, 3), got.values.sorted())
    }

    @Test fun `with every stock app on Home a swapped app still gets one`() {
        val got = picks(thirdParty(8), homeStock = stock).values.groupingBy { it.packageName }.eachCount()
        assertEquals(stock.size, got.size)
        assertTrue(got.values.all { it in 1..2 })
    }

    @Test fun `swapped apps on Home pick first, so they get the free stock apps`() {
        // Two free stock apps and nine swapped apps: the two on Home get different ones, whatever the rest do.
        val swapped = thirdParty(9)
        val home = setOf(swapped[3].id, swapped[7].id)
        val got = picks(swapped, homeStock = listOf(camera, maps, phone, settings), onHome = home)
        assertTrue(got.getValue(swapped[3].id) != got.getValue(swapped[7].id))
    }

    @Test fun `the count is the apps that will be swapped, not only the chosen ones`() {
        val shortcut = AppEntry("com.reddit.frontpage/${SHORTCUT_CLASS_PREFIX}popular", "Popular", Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888))
        val list = apps + shortcut
        assertEquals(0, ScreenshotDisguise.swapCount(list, DisguiseRules(), system))
        assertEquals(1, ScreenshotDisguise.swapCount(list, DisguiseRules(chosen = setOf(reddit.id, camera.id, "gone/.Main")), system))
        // Swap All: Reddit and Bank, not the three stock apps and not the shortcut (it has no row).
        assertEquals(2, ScreenshotDisguise.swapCount(list, DisguiseRules(allThirdParty = true), system))
    }

    @Test fun `the picks are the same for the same apps in any order`() {
        val all = stock + thirdParty(10)
        val onHome = setOf(maps.id, camera.id, "com.example.app2/.Main", "com.example.app7/.Main")
        fun run(list: List<AppEntry>) = ScreenshotDisguise.apply(list, DisguiseRules(allThirdParty = true), stockPackages, onHome)
            .associate { it.id to it.label }
        val first = run(all)
        repeat(5) { assertEquals(first, run(all.shuffled())) }
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

    @Test fun `rules survive a save and a read`() {
        val rules = DisguiseRules(chosen = setOf(reddit.id, bank.id), allThirdParty = true)
        assertEquals(rules, ScreenshotSwap.decode(ScreenshotSwap.encode(rules)))
    }

    @Test fun `no file, a damaged file or blank ids read as no rules`() {
        assertEquals(DisguiseRules(), ScreenshotSwap.decode(null))
        assertEquals(DisguiseRules(), ScreenshotSwap.decode("not json"))
        assertEquals(DisguiseRules(chosen = setOf(reddit.id)), ScreenshotSwap.decode("""{"chosen":["${reddit.id}"," ",""]}"""))
    }

    @Test fun `rules are kept in their own file, and an uninstalled app leaves the list`() {
        val context = RuntimeEnvironment.getApplication()
        ScreenshotSwap.set(context, DisguiseRules(chosen = setOf(reddit.id, bank.id)))
        val file = context.getSharedPreferences("screenshot_swap", 0).getString("rules", null)
        assertEquals(setOf(reddit.id, bank.id), ScreenshotSwap.decode(file).chosen)
        assertTrue("not in the launcher state", context.getSharedPreferences("launcher", 0).getString("state", "").orEmpty().indexOf(reddit.id) < 0)
        ScreenshotSwap.forget(context, listOf(reddit.id))
        assertEquals(setOf(bank.id), ScreenshotSwap.chosen(context))
        ScreenshotSwap.set(context, DisguiseRules())
        assertNull("no rules, no file entry", context.getSharedPreferences("screenshot_swap", 0).getString("rules", null))
    }

    @Test fun `swapped apps are found by package with their stand-in, for widgets`() {
        val out = ScreenshotDisguise.apply(apps, DisguiseRules(chosen = setOf(reddit.id)), system)
        val byPackage = swappedByPackage(out)
        assertEquals(setOf(reddit.packageName), byPackage.keys)
        assertEquals(out.single { it.id == reddit.id }.iconFrom, byPackage[reddit.packageName])
    }

    @Test fun `a swapped app offers none of its own shortcuts`() {
        val swapped = ScreenshotDisguise.apply(apps, DisguiseRules(chosen = setOf(reddit.id)), system).single { it.id == reddit.id }
        assertEquals(emptyList<QuickAction>(), loadQuickActions(RuntimeEnvironment.getApplication(), swapped))
    }
}
