package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Tweak options: every default is what the tweak did before it had options, and nothing odd gets stored. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TweakOptionsTest {
    private val none = emptyMap<String, Map<String, String>>()

    @Test fun `an untouched tweak reads its defaults, which are what it always did`() {
        assertEquals("standard", TweakOptions.value(none, "dockMagnify", "amount"))
        assertEquals(.38f, TweakOptions.magnifyAmount(none), 0f)
        assertTrue(TweakOptions.on(none, "dockMagnify", "tick"))
        assertTrue(TweakOptions.on(none, "tintMedia", "card"))
        assertTrue(TweakOptions.on(none, "tintMedia", "island"))
    }

    @Test fun `a changed option is read back, and the amounts are ordered`() {
        val subtle = TweakOptions.set(none, "dockMagnify", "amount", "subtle")
        val strong = TweakOptions.set(none, "dockMagnify", "amount", "strong")
        assertTrue(TweakOptions.magnifyAmount(subtle) < TweakOptions.magnifyAmount(none))
        assertTrue(TweakOptions.magnifyAmount(none) < TweakOptions.magnifyAmount(strong))
        assertFalse(TweakOptions.on(TweakOptions.set(none, "tintMedia", "island", TweakOptions.OFF), "tintMedia", "island"))
    }

    @Test fun `setting the default stores nothing, so reset and never touched are the same`() {
        val changed = TweakOptions.set(none, "dockMagnify", "tick", TweakOptions.OFF)
        assertEquals(mapOf("dockMagnify" to mapOf("tick" to "off")), changed)
        assertEquals(none, TweakOptions.set(changed, "dockMagnify", "tick", TweakOptions.ON))
    }

    @Test fun `an unknown option or value changes nothing and reads as the default`() {
        assertEquals(none, TweakOptions.set(none, "dockMagnify", "nope", "on"))
        assertEquals(none, TweakOptions.set(none, "dockMagnify", "amount", "enormous"))
        assertEquals(none, TweakOptions.set(none, "notATweak", "x", "y"))
        val odd = mapOf("dockMagnify" to mapOf("amount" to "enormous"))
        assertEquals("standard", TweakOptions.value(odd, "dockMagnify", "amount"))
    }

    @Test fun `a save keeps its options, and one from a newer Folio loses only what this build does not know`() {
        val state = decodeLauncherState(
            """{"tweakOptions":{"dockMagnify":{"amount":"strong","tick":"off","future":"x"},"someNewTweak":{"a":"b"},"tintMedia":{"card":"maybe"}}}""",
            legacyRaw = null)
        assertEquals(mapOf("dockMagnify" to mapOf("amount" to "strong", "tick" to "off")), state.tweakOptions)
        assertEquals("a save from before options has none", none, decodeLauncherState("{}", legacyRaw = null).tweakOptions)
    }

    @Test fun `every option has a default that is one of its own choices`() {
        listOf("dockMagnify", "tintMedia").flatMap { TweakOptions.of(it) }.forEach { option ->
            if (option is TweakOption.Choice) assertTrue("${option.id} default", option.choices.any { it.first == option.default })
            if (option is TweakOption.Toggle) assertTrue(option.default == TweakOptions.ON || option.default == TweakOptions.OFF)
        }
    }
}
