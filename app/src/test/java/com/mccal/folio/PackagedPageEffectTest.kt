package com.mccal.folio

import com.mccal.folio.market.PackageChange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A page effect from a package lands in the picker's list clamped, survives a restart, and leaves on restore. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PackagedPageEffectTest {
    @Test fun `a package's numbers are clamped when the effect is recorded`() {
        val effect = PackagedPageEffect.of("dev.example.wild", "Wild", 400f, "center", 2f, .2f)
        assertEquals(PageEffectSpec.MAX_ROTATION, effect.spec.maxRotation, 0f)
        assertEquals(PageEffectSpec.MAX_SHRINK, effect.spec.shrink, 0f)
        assertEquals(PageEffectSpec.MIN_CAMERA, effect.spec.cameraWidths, 0f)
        assertEquals(PageEffectSpec.Pivot.CENTER, effect.spec.pivot)
    }

    @Test fun `a recorded effect survives a save and a load`() {
        val tilt = PackagedPageEffect.of("com.mccal.folio.effect.tilt", "Tilt", 18f, "center", .08f, 3.5f)
        assertEquals(tilt, PackagedPageEffect.from(tilt.toJson()))
        assertEquals(null, PackagedPageEffect.from(org.json.JSONObject().put("name", "no id")))
    }

    @Test fun `the host adds an effect on apply and removes it on restore`() {
        val added = mutableListOf<PackagedPageEffect>(); val removed = mutableListOf<String>()
        val launcher = object : MarketLauncher {
            override val state = LauncherState()
            override fun installTweak(feature: TweakFeature) = Unit
            override fun removeTweak(feature: TweakFeature) = Unit
            override fun setFeatureScope(id: String, screen: FolioScreen, value: ScopeValue) = Unit
            override fun applyTheme(theme: FolioTheme) = Unit
            override fun applyArtBackground(art: Artwork, bytes: ByteArray, sha256: String) = ""
            override fun restoreArtBackground(artId: String, snapshot: String) = Unit
            override fun addPageEffect(effect: PackagedPageEffect) { added += effect }
            override fun removePageEffect(id: String) { removed += id }
        }
        val host = MarketHost(launcher)
        val change = PackageChange.PageEffect("com.mccal.folio.effect.tilt", "Tilt", 18f, "center", .08f, 3.5f)
        val snapshot = host.apply(change)
        assertEquals(listOf("Tilt"), added.map { it.name })
        host.restore(change, snapshot)
        assertEquals(listOf("com.mccal.folio.effect.tilt"), removed)
        assertTrue("nothing is chosen for the user", launcher.state.pageEffect == PageEffect.NONE)
    }

    private val tilt = PackagedPageEffect.of("com.mccal.folio.effect.tilt", "Tilt", 18f, "center", .08f, 3.5f)
    private val installed = LauncherState(packagedEffects = listOf(tilt))

    @Test fun `choosing a packaged effect draws it, and turns effects on if they were off`() {
        val chosen = installed.withPackagedEffect(tilt.id)
        assertEquals(tilt.spec, chosen.pageEffectSpec())
        assertTrue(chosen.pageEffect != PageEffect.NONE)
        assertEquals("an id nobody installed changes nothing", installed, installed.withPackagedEffect("dev.nobody"))
    }

    @Test fun `Flipbook's switch turns a packaged effect off and brings it back`() {
        val chosen = installed.withPackagedEffect(tilt.id)
        assertEquals(null, chosen.withPageEffectOn(false).pageEffectSpec())
        assertEquals(tilt.spec, chosen.withPageEffectOn(false).withPageEffectOn(true).pageEffectSpec())
    }

    @Test fun `choosing a built-in sets the packaged one aside`() {
        val back = installed.withPackagedEffect(tilt.id).withPageEffect(PageEffect.CAROUSEL)
        assertEquals(PageEffect.CAROUSEL.spec, back.pageEffectSpec())
        assertEquals(null, back.packagedEffectId)
    }

    @Test fun `removing the chosen package falls back to the built-in choice`() {
        val gone = installed.withPageEffect(PageEffect.CUBE).withPackagedEffect(tilt.id).withoutPackagedEffect(tilt.id)
        assertEquals(PageEffect.CUBE.spec, gone.pageEffectSpec())
        assertTrue(gone.packagedEffects.isEmpty())
    }
}
