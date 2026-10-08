package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The actions added with Icon Actions: the ids are saved in people's layouts, so a rename must fail here first. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ActionSpecsTest {
    private val context get() = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()

    private fun env(sdk: Int = 36, accessibility: Boolean = true, safeMode: Boolean = false) =
        ActionEnv({ accessibility }, { false }, sdk, { safeMode })

    private val newIds = listOf(
        "app.open", "shortcut.open", "media.playpause", "media.next", "media.prev",
        "volume.up", "volume.down", "volume.mute", "panel.wifi", "panel.bluetooth",
        "a11y.back", "a11y.home", "a11y.recents", "a11y.quicksettings", "a11y.allapps", "a11y.dismissshade",
        "a11y.power", "a11y.shade", "a11y.splitscreen", "a11y.mediaplaypause",
    )

    @Test fun `the new ids are pinned`() {
        assertEquals(newIds, ActionSpecs.all.map { it.id })
    }

    @Test fun `every id in the standard registry is unique and the original twelve are still there`() {
        val ids = ActionRegistry.standard.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        FolioAction.entries.filter { it != FolioAction.NONE }.forEach { assertNotNull(it.name, ActionRegistry.standard.spec(it.name)) }
        newIds.forEach { assertNotNull(it, ActionRegistry.standard.spec(it)) }
    }

    @Test fun `accessibility actions need accessibility and the rest need nothing`() {
        ActionSpecs.all.forEach {
            val expected = if (it.id.startsWith("a11y.")) ActionNeeds.ACCESSIBILITY else ActionNeeds.NONE
            assertEquals(it.id, expected, it.needs)
        }
    }

    @Test fun `only the media play pause global action needs a newer Android`() {
        ActionSpecs.all.forEach { assertEquals(it.id, if (it.id == "a11y.mediaplaypause") 36 else 0, it.minSdk) }
    }

    @Test fun `the power menu is the only disruptive new action`() {
        assertEquals(listOf("a11y.power"), ActionSpecs.all.filter { it.risk == OperationRisk.DISRUPTIVE }.map { it.id })
    }

    @Test fun `every new spec has a group`() {
        ActionSpecs.all.forEach { assertTrue(it.id, it.group.isNotEmpty()) }
    }

    @Test fun `media play pause by accessibility is refused below Android 16 and allowed on it`() {
        val ref = ActionRef("a11y.mediaplaypause")
        assertEquals(ActionVerdict.NeedsAndroid(36), ActionRegistry.standard.verdict(ref, env(sdk = 31)))
        assertEquals(ActionVerdict.Allowed, ActionRegistry.standard.verdict(ref, env(sdk = 36)))
    }

    @Test fun `accessibility actions follow the service and others ignore it`() {
        assertEquals(ActionVerdict.NeedsAccessibility, ActionRegistry.standard.verdict(ActionRef("a11y.back"), env(accessibility = false)))
        assertEquals(ActionVerdict.Allowed, ActionRegistry.standard.verdict(ActionRef("a11y.back"), env(accessibility = true)))
        assertEquals(ActionVerdict.Allowed, ActionRegistry.standard.verdict(ActionRef("volume.up"), env(accessibility = false, sdk = 31)))
    }

    @Test fun `an icon action is refused in Safe Mode`() {
        assertEquals(ActionVerdict.SafeMode, ActionRegistry.standard.verdict(ActionRef("volume.up"), env(safeMode = true), ActionSource.ICON))
    }

    @Test fun `opening an app or shortcut that is not there returns false`() {
        assertFalse(ActionSpecs.openApp(context, null))
        assertFalse(ActionSpecs.openApp(context, ""))
        assertFalse(ActionSpecs.openApp(context, "no.such.app"))
        assertFalse(ActionSpecs.openShortcut(context, "no.such.app", null))
        assertFalse(ActionSpecs.openShortcut(context, "no.such.app", "gone"))
    }

    @Test fun `an action that names a profile this phone does not have never falls back to the personal copy`() {
        assertFalse(ActionSpecs.openApp(context, "com.example", "com.example/.Main", "987654"))
        assertFalse(ActionSpecs.openApp(context, "com.example", null, "987654"))
        assertFalse(ActionSpecs.openApp(context, "com.example", null, "not a number"))
        assertFalse(ActionSpecs.openShortcut(context, "com.example", "x", "987654"))
    }

    @Test fun `a component that is not on the phone does not launch`() {
        assertFalse(ActionSpecs.openApp(context, "no.such.app", "no.such.app/.Main"))
        assertFalse("a damaged component is not a launch", ActionSpecs.openApp(context, null, "not a component"))
    }

    private fun fakeRegistry(run: () -> Boolean, needs: ActionNeeds = ActionNeeds.NONE) =
        ActionRegistry(listOf(ActionSpec("test.one", R.string.action_volume_up, OperationRisk.OBSERVE, needs, run = { _, _ -> run() })))

    private class Calls {
        val notices = mutableListOf<Int>()
        val trail = mutableListOf<String>()
    }

    private fun run(registry: ActionRegistry, e: ActionEnv, calls: Calls, id: String = "test.one") =
        ActionRunner.run(context, ActionRef(id), ActionSource.ICON, registry, e, calls.notices::add, calls.trail::add)

    @Test fun `a spec that throws leaves no exception and returns false`() {
        val calls = Calls()
        assertFalse(run(fakeRegistry({ error("boom") }), env(), calls))
        assertTrue(calls.notices.isEmpty())
    }

    @Test fun `a refused action says what to turn on and leaves a trail line`() {
        val calls = Calls()
        assertFalse(run(fakeRegistry({ true }, ActionNeeds.ACCESSIBILITY), env(accessibility = false), calls))
        assertEquals(listOf(R.string.needs_accessibility_service), calls.notices)
        assertTrue(calls.trail.single().contains("refused"))
    }

    @Test fun `an unknown id is refused without a notice`() {
        val calls = Calls()
        assertFalse(run(fakeRegistry({ true }), env(), calls, id = "from.the.future"))
        assertTrue(calls.notices.isEmpty())
        assertTrue(calls.trail.single().contains("unknown action"))
    }

    @Test fun `a success writes a ran line`() {
        val calls = Calls()
        assertTrue(run(fakeRegistry({ true }), env(), calls))
        assertEquals(listOf("Action test.one (icon) ran"), calls.trail)
        assertTrue(calls.notices.isEmpty())
    }

    @Test fun `a false from an accessibility spec asks for the service`() {
        val calls = Calls()
        assertFalse(run(fakeRegistry({ false }, ActionNeeds.ACCESSIBILITY), env(), calls))
        assertEquals(listOf(R.string.needs_accessibility_service), calls.notices)
    }
}
