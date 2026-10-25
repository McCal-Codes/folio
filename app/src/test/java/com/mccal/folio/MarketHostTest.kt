package com.mccal.folio

import com.mccal.folio.market.Capability
import com.mccal.folio.market.PackageChange
import com.mccal.folio.market.ParseResult
import com.mccal.folio.market.TweakBundle
import com.mccal.folio.market.TweakId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The seam between a Market package and the launcher: a package changes Folio only through [MarketHost], and removing
 * it has to put back exactly what was there before.
 */
class MarketHostTest {
    /** A launcher that keeps the state changes in memory, the way the real model does. */
    private class FakeLauncher(start: LauncherState = LauncherState()) : MarketLauncher {
        override var state = start
            private set
        val themes = mutableListOf<FolioTheme>()

        /** What is behind Home, as [BackgroundChoice.save] writes it, plus the art that has been installed. */
        var background = BackgroundChoice.None.save()
        val artwork = mutableMapOf<String, Artwork>()

        /** Each tweak added or taken out, in order: the real ones also flip the tweak's main switch. */
        val switched = mutableListOf<String>()

        override fun installTweak(feature: TweakFeature) {
            switched += "+${feature.id}"
            state = state.copy(installedTweaks = state.installedTweaks + feature.id)
        }

        override fun removeTweak(feature: TweakFeature) {
            switched += "-${feature.id}"
            state = state.copy(
                installedTweaks = state.installedTweaks - feature.id,
                featureScopes = state.featureScopes - feature.id,
                tweakOptions = state.tweakOptions - feature.id,
            )
        }

        override fun setTweakOptions(id: String, options: Map<String, String>) {
            state = state.copy(tweakOptions = TweakOptions.replace(state.tweakOptions, id, options))
        }

        override fun setFeatureScope(id: String, screen: FolioScreen, value: ScopeValue) {
            state = state.copy(featureScopes = FeatureScopes.set(state.featureScopes, id, screen, value))
        }

        override fun applyArtBackground(art: Artwork, bytes: ByteArray, sha256: String): String {
            val was = background
            artwork[art.id] = art
            background = BackgroundChoice.Art(art.id).save()
            return was
        }

        override fun restoreArtBackground(artId: String, snapshot: String) {
            artwork -= artId
            background = snapshot
        }

        override fun setDuet(options: com.mccal.folio.duet.DuetOptions, intensity: Float) {
            state = state.copy(duet = options, foldIntensity = intensity)
        }

        override fun applyTheme(theme: FolioTheme) {
            themes += theme
            state = state.withTheme(theme, installedPacks = emptySet())
        }
    }

    private val harborlineId = "com.mccal.folio.harborline"

    private fun bundle(vararg tweaks: com.mccal.folio.market.TweakSetting) = TweakBundle(tweaks.toList())

    private fun cabinetBundle(enabled: Boolean = true, cover: Boolean = true, inner: Boolean = true) =
        bundle(com.mccal.folio.market.TweakSetting(TweakId.APP_PANELS, enabled, cover, inner))

    @Test fun `a tweak package turns the tweak on, and removing it turns it back off`() {
        val launcher = FakeLauncher()
        val host = MarketHost(launcher)
        val change = PackageChange.Tweaks(cabinetBundle())
        val before = host.apply(change)
        assertTrue("appPanels" in launcher.state.installedTweaks)
        host.restore(change, before)
        assertTrue("appPanels" !in launcher.state.installedTweaks)
    }

    @Test fun `removing a package leaves a tweak the user already had exactly as it was`() {
        // The user had Cabinet on, with the cover screen turned off by hand.
        val start = LauncherState(
            installedTweaks = setOf("appPanels"),
            featureScopes = FeatureScopes.set(emptyMap(), "appPanels", FolioScreen.COVER, ScopeValue.OFF),
        )
        val launcher = FakeLauncher(start)
        val host = MarketHost(launcher)
        val change = PackageChange.Tweaks(cabinetBundle())
        val before = host.apply(change)
        // The package turned the cover screen back on.
        assertEquals(ScopeValue.DEFAULT, FeatureScopes.value(launcher.state.featureScopes, "appPanels", FolioScreen.COVER))
        host.restore(change, before)
        assertTrue("appPanels" in launcher.state.installedTweaks)
        assertEquals(ScopeValue.OFF, FeatureScopes.value(launcher.state.featureScopes, "appPanels", FolioScreen.COVER))
    }

    @Test fun `removing a package that turned a tweak off puts back the options the person had set`() {
        val start = LauncherState(
            installedTweaks = setOf("dockMagnify"),
            tweakOptions = mapOf("dockMagnify" to mapOf("amount" to "strong", "tick" to "off")),
        )
        val launcher = FakeLauncher(start)
        val host = MarketHost(launcher)
        val off = PackageChange.Tweaks(bundle(com.mccal.folio.market.TweakSetting(TweakId.DOCK_MAGNIFY, enabled = false)))
        val before = host.apply(off)
        assertTrue("turning it off forgot the options", launcher.state.tweakOptions.isEmpty())
        host.restore(off, before)
        assertTrue("dockMagnify" in launcher.state.installedTweaks)
        assertEquals(mapOf("dockMagnify" to mapOf("amount" to "strong", "tick" to "off")), launcher.state.tweakOptions)
    }

    @Test fun `a bundle that names one screen leaves the other off`() {
        val launcher = FakeLauncher()
        MarketHost(launcher).apply(PackageChange.Tweaks(cabinetBundle(cover = false)))
        assertEquals(ScopeValue.OFF, FeatureScopes.value(launcher.state.featureScopes, "appPanels", FolioScreen.COVER))
        assertEquals(ScopeValue.DEFAULT, FeatureScopes.value(launcher.state.featureScopes, "appPanels", FolioScreen.INNER))
    }

    @Test fun `a theme package applies the theme and removing it puts the old look back`() {
        val launcher = FakeLauncher(LauncherState(iconShape = IconShape.CIRCLE))
        val host = MarketHost(launcher)
        val clear = FolioTheme.PRESETS.first { it.name == "Clear" }
        val change = PackageChange.Theme(clear.toJson().toString())
        val before = host.apply(change)
        assertEquals(IconShape.SQUIRCLE, launcher.state.iconShape)
        host.restore(change, before)
        assertEquals(IconShape.CIRCLE, launcher.state.iconShape)
    }

    @Test fun `a theme file Folio can't read is refused instead of applied`() {
        val launcher = FakeLauncher()
        val failed = runCatching { MarketHost(launcher).apply(PackageChange.Theme("{\"not\":\"a theme\"}")) }
        assertTrue(failed.isFailure)
        assertTrue("nothing was applied", launcher.themes.isEmpty())
    }

    @Test fun `a Duet package sets only the options it names, and removing it puts back the look it replaced`() {
        val mine = com.mccal.folio.duet.DuetOptions(style = "subtle", frost = .7f)
        val launcher = FakeLauncher(LauncherState(duet = mine, foldIntensity = .9f, installedTweaks = setOf(DUET_ID)))
        val host = MarketHost(launcher)
        val change = PackageChange.Tweaks(bundle(com.mccal.folio.market.TweakSetting(TweakId.DUET, true,
            options = mapOf("style" to "deep", "intensity" to 1.4))))
        val before = host.apply(change)
        assertEquals(mine.copy(style = "deep"), launcher.state.duet)
        assertEquals(1.4f, launcher.state.foldIntensity)
        host.restore(change, before)
        assertEquals(mine, launcher.state.duet)
        assertEquals(.9f, launcher.state.foldIntensity)
        assertTrue(DUET_ID in launcher.state.installedTweaks)
    }

    @Test fun `a Duet package without options leaves the look alone`() {
        val mine = com.mccal.folio.duet.DuetOptions(style = "minimal")
        val launcher = FakeLauncher(LauncherState(duet = mine))
        MarketHost(launcher).apply(PackageChange.Tweaks(bundle(com.mccal.folio.market.TweakSetting(TweakId.DUET, true))))
        assertEquals(mine, launcher.state.duet)
    }

    @Test fun `Folio only claims the capabilities it really has`() {
        val host = MarketHost(FakeLauncher())
        assertEquals(
            setOf(
                Capability.THEME, Capability.WALLPAPER, Capability.APP_PANELS, Capability.DOCK_MAGNIFY,
                Capability.NOTIFICATION_APP_ROW, Capability.TINT_NOTIFICATIONS, Capability.TINT_MEDIA,
                Capability.PAGE_EFFECTS, Capability.FOLD_TRANSITION,
            ),
            host.capabilities,
        )
        // Every built-in tweak has a capability, and every tweak capability has a built-in tweak.
        assertEquals(TweakFeatures.map { it.id }.toSet(), TweakId.entries.map { it.id }.toSet())
        assertTrue(TweakId.entries.all { it.capability in host.capabilities })
    }

    @Test fun `a wallpaper is shown, and removing it puts back what was there`() {
        val launcher = FakeLauncher()
        val host = MarketHost(launcher)
        val change = com.mccal.folio.market.PackageChange.Wallpaper(
            path = "assets/hills.webp", bytes = byteArrayOf(1, 2, 3), id = "dev.example.hills",
            title = "Green Hills", artist = "A Painter", license = "CC0-1.0",
        )

        val snapshot = host.apply(change)

        assertEquals("nothing was behind Home before", BackgroundChoice.None.save(), snapshot)
        assertEquals(BackgroundChoice.Art("dev.example.hills").save(), launcher.background)
        assertEquals("A Painter", launcher.artwork.getValue("dev.example.hills").artist)

        host.restore(change, snapshot)

        assertEquals("removing it puts Home back", BackgroundChoice.None.save(), launcher.background)
        assertTrue("and forgets the art", launcher.artwork.isEmpty())
    }

    @Test fun `a wallpaper with no artist or no license is refused`() {
        val host = MarketHost(FakeLauncher())
        val noArtist = com.mccal.folio.market.PackageChange.Wallpaper(
            path = "assets/x.webp", bytes = byteArrayOf(1), id = "dev.example.x",
            title = "X", artist = "", license = "CC0-1.0",
        )
        val noLicense = noArtist.copy(artist = "Someone", license = "")

        for (change in listOf(noArtist, noLicense)) {
            val failed = runCatching { host.apply(change) }.exceptionOrNull()
            assertTrue("$change should not be applied", failed is IllegalStateException)
        }
    }

    @Test fun `every package Folio ships can be applied by this host`() {
        val source = builtInSourceForTests()
        val index = requireNotNull(source.index())
        val installer = com.mccal.folio.market.PackageInstaller(
            com.mccal.folio.market.InstalledStore(com.mccal.folio.market.MemoryStore()),
            MarketHost(FakeLauncher()),
        )
        for (entry in index.packages) {
            val files = requireNotNull(source.filesFor(entry.id)) { "${entry.id} has no files" }
            val result = installer.installBuiltIn(files)
            assertTrue("${entry.id}: $result", result is com.mccal.folio.market.InstallResult.Installed)
            assertTrue(installer.remove(entry.id))
        }
    }

    @Test fun `each built-in theme package carries exactly the theme Folio ships`() {
        val source = builtInSourceForTests()
        for (preset in FolioTheme.PRESETS) {
            val id = "com.mccal.folio.theme." + preset.name.lowercase()
            val files = requireNotNull(source.filesFor(id)) { "$id has no files" }
            val json = requireNotNull(files["theme.json"]) { "$id has no theme.json" }.decodeToString()
            assertEquals("$id doesn't match the preset", preset, FolioTheme.fromJson(json))
        }
    }

    /** Reads `docs/sdk/source` straight from the repository, which is what the build copies into assets. */
    private fun builtInSourceForTests(): com.mccal.folio.market.BuiltInSource {
        val root = generateSequence(java.io.File("").absoluteFile) { it.parentFile }
            .first { java.io.File(it, "CHANGELOG.md").exists() }
        val dir = java.io.File(root, "docs/sdk/source")
        val prefix = com.mccal.folio.market.BuiltInSource.ROOT + "/"
        return com.mccal.folio.market.BuiltInSource(
            read = { path -> java.io.File(dir, path.removePrefix(prefix)).takeIf { it.isFile }?.readBytes() },
            list = { path -> java.io.File(dir, path.removePrefix(prefix)).list()?.sorted().orEmpty() },
        )
    }

    private fun parsed(result: ParseResult<*>) = result is ParseResult.Ok

    // Page effects are Flipbook add-ons (PackageKind.hostTweak). Flipbook added from Settings' Tweak Library leaves no
    // Market record, so the host answers from installedTweaks, not from the Market's list: a `depends` on Flipbook's
    // package would refuse Tilt on a phone that plainly has Flipbook.
    @Test fun `a tweak counts as there however it was added, and not once it's removed`() {
        val launcher = FakeLauncher()
        val host = MarketHost(launcher)
        assertFalse(host.hasTweak("pageEffects"))
        launcher.installTweak(TweakFeatures.first { it.id == "pageEffects" }) // what Settings' Tweak Library does
        assertTrue(host.hasTweak("pageEffects"))
        launcher.removeTweak(TweakFeatures.first { it.id == "pageEffects" })
        assertFalse(host.hasTweak("pageEffects"))
    }

    // Until 0.6.9 an update undid the old version, which put each setting back as it was before the package, and then
    // applied the new one with its own values, so whatever the person had changed since was gone.
    @Test fun `updating Folio's own Harborline keeps the screen and options the person changed, through Undo too`() {
        val launcher = FakeLauncher()
        val store = com.mccal.folio.market.InstalledStore(com.mccal.folio.market.MemoryStore())
        val installer = com.mccal.folio.market.PackageInstaller(store, MarketHost(launcher))
        val files = requireNotNull(builtInSourceForTests().filesFor(harborlineId))
        assertTrue(installer.installBuiltIn(files) is com.mccal.folio.market.InstallResult.Installed)
        launcher.setFeatureScope("dockMagnify", FolioScreen.INNER, ScopeValue.OFF)
        launcher.setTweakOptions("dockMagnify", mapOf("amount" to "strong", "tick" to "off"))
        val mine = launcher.state.tweakOptions.getValue("dockMagnify")

        val manifest = requireNotNull(files["manifest.json"]).decodeToString()
        assertTrue(manifest.contains(""""version": "1.0.0""""))
        val newer = files + ("manifest.json" to manifest.replace(""""version": "1.0.0"""", """"version": "1.0.1"""").toByteArray())
        val update = installer.installBuiltIn(newer)
        assertTrue("$update", update is com.mccal.folio.market.InstallResult.Installed)
        assertEquals(ScopeValue.OFF, scope(launcher, "dockMagnify", FolioScreen.INNER))
        assertEquals(mine, launcher.state.tweakOptions["dockMagnify"])

        assertTrue(installer.undo(update as com.mccal.folio.market.InstallResult.Installed))
        assertEquals("1.0.0", requireNotNull(store.find(harborlineId)).version.text)
        assertEquals(ScopeValue.OFF, scope(launcher, "dockMagnify", FolioScreen.INNER))
        assertEquals(mine, launcher.state.tweakOptions["dockMagnify"])

        // Remove still puts back what was there before the package came: no Harborline, nothing set for it.
        assertTrue(installer.remove(harborlineId))
        assertFalse("dockMagnify" in launcher.state.installedTweaks)
        assertEquals(ScopeValue.DEFAULT, scope(launcher, "dockMagnify", FolioScreen.INNER))
        assertTrue(launcher.state.tweakOptions.isEmpty())
    }

    private fun scope(launcher: FakeLauncher, id: String, screen: FolioScreen) = FeatureScopes.value(launcher.state.featureScopes, id, screen)

    @Test fun `only tweak changes move in place`() {
        val host = MarketHost(FakeLauncher())
        val tweaks = PackageChange.Tweaks(cabinetBundle())
        val theme = PackageChange.Theme(FolioTheme.PRESETS.first().toJson().toString())
        assertTrue(host.canUpdateInPlace(tweaks, PackageChange.Tweaks(cabinetBundle(inner = false))))
        assertFalse(host.canUpdateInPlace(theme, theme))
        assertFalse(host.canUpdateInPlace(tweaks, theme))
    }

    @Test fun `an update that sets the same values leaves everything the person changed`() {
        val launcher = FakeLauncher()
        val host = MarketHost(launcher)
        val harborline = PackageChange.Tweaks(bundle(com.mccal.folio.market.TweakSetting(TweakId.DOCK_MAGNIFY, true)))
        val before = host.apply(harborline)
        launcher.setFeatureScope("dockMagnify", FolioScreen.INNER, ScopeValue.OFF)
        launcher.setTweakOptions("dockMagnify", mapOf("amount" to "strong"))
        val mine = launcher.state.tweakOptions.getValue("dockMagnify")
        launcher.switched.clear()

        val kept = host.updateInPlace(harborline, before, harborline.copy())

        assertEquals(ScopeValue.OFF, scope(launcher, "dockMagnify", FolioScreen.INNER))
        assertEquals(mine, launcher.state.tweakOptions["dockMagnify"])
        assertTrue("the tweak's switch was never flipped: ${launcher.switched}", launcher.switched.isEmpty())
        // What the new version keeps still describes Home before the package.
        host.restore(harborline, kept)
        assertFalse("dockMagnify" in launcher.state.installedTweaks)
        assertEquals(ScopeValue.DEFAULT, scope(launcher, "dockMagnify", FolioScreen.INNER))
        assertTrue(launcher.state.tweakOptions.isEmpty())
    }

    @Test fun `a screen the update changes follows it, unless the person had already changed that screen`() {
        val launcher = FakeLauncher()
        val host = MarketHost(launcher)
        val first = PackageChange.Tweaks(cabinetBundle(cover = true, inner = true))
        val before = host.apply(first)
        launcher.setFeatureScope("appPanels", FolioScreen.COVER, ScopeValue.ON)

        host.updateInPlace(first, before, PackageChange.Tweaks(cabinetBundle(cover = false, inner = false)))

        assertEquals("the person's own choice stays", ScopeValue.ON, scope(launcher, "appPanels", FolioScreen.COVER))
        assertEquals("left alone, so it follows the update", ScopeValue.OFF, scope(launcher, "appPanels", FolioScreen.INNER))
    }

    @Test fun `a tweak the person took out stays out through an update`() {
        val launcher = FakeLauncher()
        val host = MarketHost(launcher)
        val first = PackageChange.Tweaks(cabinetBundle())
        val before = host.apply(first)
        launcher.removeTweak(TweakFeatures.first { it.id == "appPanels" })
        launcher.switched.clear()

        host.updateInPlace(first, before, PackageChange.Tweaks(cabinetBundle(inner = false)))

        assertFalse("appPanels" in launcher.state.installedTweaks)
        assertTrue(launcher.switched.isEmpty())
    }

    @Test fun `an update that turns a tweak off turns it off`() {
        val launcher = FakeLauncher()
        val host = MarketHost(launcher)
        val first = PackageChange.Tweaks(cabinetBundle())
        val before = host.apply(first)

        host.updateInPlace(first, before, PackageChange.Tweaks(cabinetBundle(enabled = false)))

        assertFalse("appPanels" in launcher.state.installedTweaks)
    }

    @Test fun `a tweak new in the update goes on, and one it drops goes back to how it was before the package`() {
        val launcher = FakeLauncher()
        val host = MarketHost(launcher)
        val first = PackageChange.Tweaks(cabinetBundle())
        val before = host.apply(first)
        val next = PackageChange.Tweaks(bundle(com.mccal.folio.market.TweakSetting(TweakId.DOCK_MAGNIFY, true, cover = false)))

        val kept = host.updateInPlace(first, before, next)

        assertFalse("appPanels" in launcher.state.installedTweaks)
        assertTrue("dockMagnify" in launcher.state.installedTweaks)
        assertEquals(ScopeValue.OFF, scope(launcher, "dockMagnify", FolioScreen.COVER))
        host.restore(next, kept)
        assertFalse("dockMagnify" in launcher.state.installedTweaks)
        assertEquals(ScopeValue.DEFAULT, scope(launcher, "dockMagnify", FolioScreen.COVER))
    }

    @Test fun `Duet's look moves key by key, keeping what the person changed`() {
        val mine = com.mccal.folio.duet.DuetOptions(style = "subtle", frost = .7f)
        val launcher = FakeLauncher(LauncherState(duet = mine, foldIntensity = .9f, installedTweaks = setOf(DUET_ID)))
        val host = MarketHost(launcher)
        fun duet(vararg options: Pair<String, Any>) =
            PackageChange.Tweaks(bundle(com.mccal.folio.market.TweakSetting(TweakId.DUET, true, options = options.toMap())))
        val first = duet("style" to "deep", "frost" to 1.2, "intensity" to 1.4)
        val before = host.apply(first)
        // The person picks another style afterwards; frost and intensity stay as the package set them.
        launcher.setDuet(launcher.state.duet.copy(style = "minimal"), launcher.state.foldIntensity)

        val kept = host.updateInPlace(first, before, duet("style" to "classic", "frost" to 1.5, "intensity" to 1.4))

        assertEquals("changed by both: the person's stays", "minimal", launcher.state.duet.style)
        assertEquals("changed by the update only: it follows", 1.5f, launcher.state.duet.frost, 1e-4f)
        assertEquals("the same in both: as it is", 1.4f, launcher.state.foldIntensity, 1e-4f)
        // Remove still puts back the look from before the package.
        host.restore(duet(), kept)
        assertEquals(mine, launcher.state.duet)
        assertEquals(.9f, launcher.state.foldIntensity, 1e-4f)
    }

    // Updating an installed Community tweak: what the person's own screens and options do through the real installer
    // and the real host, by the manual path (install) and the automatic one (updateKeepingSettings). Harborline stands
    // in for any hosted tweak; the other five behave the same because the host treats every tweak alike.

    private val dockMagnify = TweakFeatures.first { it.id == "dockMagnify" }

    private class Tweaked(val launcher: FakeLauncher, val store: com.mccal.folio.market.InstalledStore, val installer: com.mccal.folio.market.PackageInstaller)

    private fun tweaked(launcher: FakeLauncher = FakeLauncher()): Tweaked {
        val store = com.mccal.folio.market.InstalledStore(com.mccal.folio.market.MemoryStore())
        return Tweaked(launcher, store, com.mccal.folio.market.PackageInstaller(store, MarketHost(launcher), isLibraryTweakPackage = { it in LIBRARY_TWEAK_PACKAGES }))
    }

    /** Harborline's shipped files as a package from a source: [version], and the screens its bundle names. */
    private fun hosted(version: String, cover: Boolean = true, inner: Boolean = true, text: String? = null): ByteArray {
        val files = requireNotNull(builtInSourceForTests().filesFor(harborlineId))
        val manifest = requireNotNull(files["manifest.json"]).decodeToString()
            .replace(""""version": "1.0.0"""", """"version": "$version"""")
            .let { if (text == null) it else it.replace(Regex(""""description": ".*""""), """"description": "$text"""") }
        val tweaks = """{"format":1,"tweaks":[{"id":"dockMagnify","enabled":true,"screens":{"cover":$cover,"inner":$inner}}]}"""
        val out = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(out).use { zip ->
            for ((name, bytes) in mapOf("manifest.json" to manifest.toByteArray(), "tweaks.json" to tweaks.toByteArray())) {
                zip.putNextEntry(java.util.zip.ZipEntry(name)); zip.write(bytes); zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun installedNow(result: com.mccal.folio.market.InstallResult) =
        result as? com.mccal.folio.market.InstallResult.Installed ?: error("expected an install, got $result")

    /** The person's own choices after the first version: cover turned off by hand, inner left alone, options set. */
    private fun personChanges(launcher: FakeLauncher): Map<String, String> {
        launcher.setFeatureScope("dockMagnify", FolioScreen.COVER, ScopeValue.OFF)
        launcher.setTweakOptions("dockMagnify", mapOf("amount" to "strong", "tick" to "off"))
        return launcher.state.tweakOptions.getValue("dockMagnify")
    }

    // Case (a), manual path: a text-only bump goes through PackageInstaller.install. Observed: the screens and options
    // are kept, because install() already moves tweak changes in place (carriedOver/updateInPlace, 0.6.9).
    @Test fun `a text-only update by hand keeps the screens and options the person set`() {
        val t = tweaked()
        installedNow(t.installer.install(hosted("1.0.0")))
        val mine = personChanges(t.launcher)

        installedNow(t.installer.install(hosted("1.0.1", text = "New words.")))

        assertEquals(ScopeValue.OFF, scope(t.launcher, "dockMagnify", FolioScreen.COVER))
        assertEquals(mine, t.launcher.state.tweakOptions["dockMagnify"])
    }

    // Case (a), automatic path. Observed: kept, and Home is not touched at all.
    @Test fun `a text-only update by the automatic path keeps the screens and options the person set`() {
        val t = tweaked()
        installedNow(t.installer.install(hosted("1.0.0")))
        val mine = personChanges(t.launcher)
        t.launcher.switched.clear()

        installedNow(t.installer.updateKeepingSettings(hosted("1.0.1", text = "New words.")))

        assertEquals(ScopeValue.OFF, scope(t.launcher, "dockMagnify", FolioScreen.COVER))
        assertEquals(mine, t.launcher.state.tweakOptions["dockMagnify"])
        assertTrue(t.launcher.switched.isEmpty())
    }

    // Case (a) with a package that leaves a screen off and the person turning it on by hand.
    @Test fun `a screen the person turned on by hand stays on through a text-only update`() {
        val t = tweaked()
        installedNow(t.installer.install(hosted("1.0.0", inner = false)))
        t.launcher.setFeatureScope("dockMagnify", FolioScreen.INNER, ScopeValue.ON)

        installedNow(t.installer.install(hosted("1.0.1", inner = false, text = "New words.")))

        assertEquals(ScopeValue.ON, scope(t.launcher, "dockMagnify", FolioScreen.INNER))
    }

    // Case (b), manual path: version 2 turns the inner screen off. Observed: the inner screen follows the update, the
    // cover screen and options the person set are kept.
    @Test fun `an update that changes a screen follows it and keeps the rest of what the person set`() {
        val t = tweaked()
        installedNow(t.installer.install(hosted("1.0.0")))
        val mine = personChanges(t.launcher)

        installedNow(t.installer.install(hosted("1.1.0", inner = false)))

        assertEquals("the person's choice", ScopeValue.OFF, scope(t.launcher, "dockMagnify", FolioScreen.COVER))
        assertEquals("the update's change", ScopeValue.OFF, scope(t.launcher, "dockMagnify", FolioScreen.INNER))
        assertEquals(mine, t.launcher.state.tweakOptions["dockMagnify"])
    }

    // Case (b), automatic path. Observed: refused, so the person taps it and the manual path above runs.
    @Test fun `the automatic path leaves an update that changes a screen for the person to tap`() {
        val t = tweaked()
        installedNow(t.installer.install(hosted("1.0.0")))
        personChanges(t.launcher)

        val result = t.installer.updateKeepingSettings(hosted("1.1.0", inner = false))

        assertTrue("$result", result is com.mccal.folio.market.InstallResult.Failed)
        assertEquals("1.0.0", requireNotNull(t.store.find(harborlineId)).version.text)
        assertEquals(ScopeValue.DEFAULT, scope(t.launcher, "dockMagnify", FolioScreen.INNER))
    }

    // Case (b) where the person had already changed the screen the update changes.
    @Test fun `a screen the person already changed keeps their value when the update changes it too`() {
        val t = tweaked()
        installedNow(t.installer.install(hosted("1.0.0")))
        t.launcher.setFeatureScope("dockMagnify", FolioScreen.INNER, ScopeValue.ON)

        installedNow(t.installer.install(hosted("1.1.0", inner = false)))

        assertEquals(ScopeValue.ON, scope(t.launcher, "dockMagnify", FolioScreen.INNER))
    }

    // Case (c): a tweak added from Settings' Tweak Library has no package record. Observed before the fix: a hosted copy
    // with the same id was a first install, the options were kept and the screens the person turned off were reset to
    // the bundle's (inner went from OFF back to DEFAULT). Now it is an update of the Library's copy.
    @Test fun `a hosted copy of a tweak added from the Tweak Library keeps the screens and options`() {
        val t = tweaked()
        t.launcher.installTweak(dockMagnify)
        t.launcher.setFeatureScope("dockMagnify", FolioScreen.INNER, ScopeValue.OFF)
        t.launcher.setTweakOptions("dockMagnify", mapOf("amount" to "strong"))

        installedNow(t.installer.install(hosted("1.0.0")))

        assertEquals(mapOf("amount" to "strong"), t.launcher.state.tweakOptions["dockMagnify"])
        assertEquals(ScopeValue.OFF, scope(t.launcher, "dockMagnify", FolioScreen.INNER))
        assertTrue("dockMagnify" in t.launcher.state.installedTweaks)
    }

    @Test fun `a hosted copy over the Tweak Library's copy follows its own change to a screen the person left alone`() {
        val t = tweaked()
        t.launcher.installTweak(dockMagnify)
        t.launcher.setFeatureScope("dockMagnify", FolioScreen.COVER, ScopeValue.ON)

        installedNow(t.installer.install(hosted("1.0.0", cover = false, inner = false)))

        assertEquals("the person's own choice", ScopeValue.ON, scope(t.launcher, "dockMagnify", FolioScreen.COVER))
        assertEquals("left alone, so it follows the package", ScopeValue.OFF, scope(t.launcher, "dockMagnify", FolioScreen.INNER))
    }

    @Test fun `Undo and Remove of a hosted copy over the Tweak Library's copy put back what the person had`() {
        val t = tweaked()
        t.launcher.installTweak(dockMagnify)
        t.launcher.setFeatureScope("dockMagnify", FolioScreen.INNER, ScopeValue.OFF)
        t.launcher.setTweakOptions("dockMagnify", mapOf("amount" to "strong"))
        val update = installedNow(t.installer.install(hosted("1.0.0")))

        assertTrue(t.installer.undo(update))

        assertTrue("the person's tweak is still there", "dockMagnify" in t.launcher.state.installedTweaks)
        assertEquals(ScopeValue.OFF, scope(t.launcher, "dockMagnify", FolioScreen.INNER))
        assertEquals(mapOf("amount" to "strong"), t.launcher.state.tweakOptions["dockMagnify"])

        installedNow(t.installer.install(hosted("1.0.0")))
        assertTrue(t.installer.remove(harborlineId))
        assertTrue("Remove leaves the Library's tweak, as the person had it", "dockMagnify" in t.launcher.state.installedTweaks)
        assertEquals(ScopeValue.OFF, scope(t.launcher, "dockMagnify", FolioScreen.INNER))
    }

    @Test fun `a hosted copy where the Library has none is a first install, as before`() {
        val t = tweaked()

        installedNow(t.installer.install(hosted("1.0.0", inner = false)))

        assertTrue("dockMagnify" in t.launcher.state.installedTweaks)
        assertEquals(ScopeValue.OFF, scope(t.launcher, "dockMagnify", FolioScreen.INNER))
        assertTrue(t.installer.remove(harborlineId))
        assertFalse("dockMagnify" in t.launcher.state.installedTweaks)
    }

    @Test fun `the next update after a hosted copy over the Library's copy still keeps what the person set`() {
        val t = tweaked()
        t.launcher.installTweak(dockMagnify)
        t.launcher.setFeatureScope("dockMagnify", FolioScreen.INNER, ScopeValue.OFF)
        installedNow(t.installer.install(hosted("1.0.0")))

        installedNow(t.installer.updateKeepingSettings(hosted("1.0.1", text = "New words.")))

        assertEquals(ScopeValue.OFF, scope(t.launcher, "dockMagnify", FolioScreen.INNER))
    }

    // Case (c): the built-in package itself recorded (installBuiltIn), then the hosted copy as its update.
    // Observed: kept, the record is the old version.
    @Test fun `a hosted copy over the built-in package keeps the screens and options`() {
        val t = tweaked()
        installedNow(t.installer.installBuiltIn(requireNotNull(builtInSourceForTests().filesFor(harborlineId))))
        val mine = personChanges(t.launcher)

        installedNow(t.installer.install(hosted("1.0.1"), origin = com.mccal.folio.market.InstalledPackage.Origin.FOLIO_SOURCE, sourceUrl = "https://example.test/"))

        assertEquals(ScopeValue.OFF, scope(t.launcher, "dockMagnify", FolioScreen.COVER))
        assertEquals(mine, t.launcher.state.tweakOptions["dockMagnify"])
    }

    /** Harborline's shipped files with only the version raised: an update that changes none of the settings. */
    private fun sameChanges(version: String): ByteArray {
        val files = requireNotNull(builtInSourceForTests().filesFor(harborlineId)).toMutableMap()
        files["manifest.json"] = requireNotNull(files["manifest.json"]).decodeToString()
            .replace(""""version": "1.0.0"""", """"version": "$version"""").toByteArray()
        val out = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(out).use { zip ->
            for ((name, bytes) in files) { zip.putNextEntry(java.util.zip.ZipEntry(name)); zip.write(bytes); zip.closeEntry() }
        }
        return out.toByteArray()
    }

    // Slice 3: the built-in package, updated from the hosted source by the automatic path. The record takes the source
    // address (so auto-update follows it from then on), the person's choices stay, and Undo gives the built-in copy back.
    @Test fun `a built-in package updated from the hosted source keeps the person's choices and Undo restores the built-in`() {
        val t = tweaked()
        val builtIn = installedNow(t.installer.installBuiltIn(requireNotNull(builtInSourceForTests().filesFor(harborlineId)))).installed
        assertEquals(null, builtIn.sourceUrl)
        val mine = personChanges(t.launcher)
        val hostedUrl = "https://mccal-codes.github.io/folio-tweaks/"

        val updated = installedNow(
            t.installer.updateKeepingSettings(sameChanges("1.0.1"), origin = com.mccal.folio.market.InstalledPackage.Origin.FOLIO_SOURCE, sourceUrl = hostedUrl),
        )
        assertEquals(hostedUrl, updated.installed.sourceUrl)
        assertEquals(ScopeValue.OFF, scope(t.launcher, "dockMagnify", FolioScreen.COVER))
        assertEquals(mine, t.launcher.state.tweakOptions["dockMagnify"])

        assertTrue(t.installer.undoUpdateInPlace(harborlineId, builtIn.version, updated.installed.version, builtInVersion = builtIn.version))
        val back = requireNotNull(t.store.find(harborlineId))
        assertEquals(builtIn.version, back.version)
        assertEquals("the built-in copy has no source address again", null, back.sourceUrl)
        assertEquals(mine, t.launcher.state.tweakOptions["dockMagnify"])
    }
}
