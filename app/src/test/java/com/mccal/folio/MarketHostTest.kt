package com.mccal.folio

import com.mccal.folio.market.Capability
import com.mccal.folio.market.PackageChange
import com.mccal.folio.market.ParseResult
import com.mccal.folio.market.TweakBundle
import com.mccal.folio.market.TweakId
import org.junit.Assert.assertEquals
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

        override fun installTweak(feature: TweakFeature) {
            state = state.copy(installedTweaks = state.installedTweaks + feature.id)
        }

        override fun removeTweak(feature: TweakFeature) {
            state = state.copy(
                installedTweaks = state.installedTweaks - feature.id,
                featureScopes = state.featureScopes - feature.id,
            )
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

        override fun applyTheme(theme: FolioTheme) {
            themes += theme
            state = state.withTheme(theme, installedPacks = emptySet())
        }
    }

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

    @Test fun `Folio only claims the capabilities it really has`() {
        val host = MarketHost(FakeLauncher())
        assertEquals(
            setOf(
                Capability.THEME, Capability.WALLPAPER, Capability.APP_PANELS, Capability.DOCK_MAGNIFY,
                Capability.NOTIFICATION_APP_ROW, Capability.TINT_NOTIFICATIONS, Capability.TINT_MEDIA,
                Capability.PAGE_EFFECTS,
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
}
