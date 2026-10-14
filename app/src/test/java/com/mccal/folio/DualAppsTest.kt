package com.mccal.folio

import android.graphics.Bitmap
import android.os.UserManager
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Clone profiles (Samsung's Dual Messenger, Android's App cloning) are listed beside the personal apps (Todd Z's
 * report, plan-feature-queue C7). Their apps keep `work=true` in backups, which has always meant "not personal".
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DualAppsTest {
    private val icon: Bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    private val personalSerial = 0L
    private val cloneSerial = 95L
    private val chat = "com.example.chat/com.example.chat.Main"
    private val cloneId = profileAppId(chat, cloneSerial, personalSerial)

    private fun personalApp(component: String) = AppEntry(component, "Chat", icon)
    private fun cloneApp() = AppEntry(cloneId, "Chat", icon, userSerial = cloneSerial, profileLabel = "Clone",
        isWork = true, kind = ProfileKind.CLONE)

    @Test fun `each user type maps to one kind`() {
        val table = mapOf(
            "android.os.usertype.full.SYSTEM" to ProfileKind.PERSONAL,
            "android.os.usertype.full.SECONDARY" to ProfileKind.PERSONAL,
            "android.os.usertype.profile.MANAGED" to ProfileKind.WORK,
            "android.os.usertype.profile.CLONE" to ProfileKind.CLONE,
            "android.os.usertype.profile.PRIVATE" to ProfileKind.SKIP,
            "android.os.usertype.profile.TEST" to ProfileKind.SKIP,
            "com.samsung.android.usertype.SECURE_FOLDER" to ProfileKind.SKIP,
            "" to ProfileKind.SKIP,
        )
        table.forEach { (type, kind) -> assertEquals(type, kind, profileKind(type)) }
        assertEquals(ProfileKind.SKIP, profileKind(null))
    }

    @Test fun `the user type text matches Android's constants`() {
        assertEquals(UserManager.USER_TYPE_PROFILE_MANAGED, USER_TYPE_PROFILE_MANAGED)
    }

    @Test fun `a clone is not personal but is not Work either`() {
        val clone = cloneApp()
        assertTrue("backups and the cache keep work=true for anything not personal", clone.isWork)
        assertEquals(ProfileKind.CLONE, clone.kind)
        val profile = AppProfile(cloneSerial, "Clone", isPersonal = false, isWork = false, quiet = false, unlocked = true,
            available = true, kind = ProfileKind.CLONE)
        assertFalse("no Work switch, widget chip or Turn On button for a clone", profile.isWork)
        // The old two-flag constructor still means what it did.
        assertEquals(ProfileKind.WORK, AppProfile(42, "Work", false, true, false, true, true).kind)
        assertEquals(ProfileKind.PERSONAL, AppProfile(0, "Personal", true, false, false, true, true).kind)
        assertEquals(ProfileKind.WORK, AppEntry("duo-profile:v1:42:$chat", "Chat", icon, userSerial = 42, isWork = true).kind)
    }

    @Test fun `the stored kind reads back, and files without one read as before`() {
        assertEquals("clone", storedProfileKind(ProfileKind.CLONE))
        assertEquals(null, storedProfileKind(ProfileKind.WORK))
        assertEquals(null, storedProfileKind(ProfileKind.PERSONAL))
        assertEquals(ProfileKind.CLONE, storedProfileKind("clone", notPersonal = true))
        assertEquals(ProfileKind.WORK, storedProfileKind(null, notPersonal = true))
        assertEquals(ProfileKind.WORK, storedProfileKind("something-newer", notPersonal = true))
        assertEquals(ProfileKind.PERSONAL, storedProfileKind(null, notPersonal = false))
        assertEquals(ProfileKind.PERSONAL, storedProfileKind("clone", notPersonal = false))
    }

    @Test fun `a layout backup with a clone on Home round-trips`() {
        val personal = personalApp(chat)
        val clone = cloneApp()
        val slots = MutableList<String?>(HOME_CELLS) { null }.apply { this[0] = chat; this[1] = cloneId }
        val state = LauncherState(apps = listOf(personal, clone), homeSlots = slots, widgetPlacements = emptyList(), loading = false)
        val raw = encodeLayoutBackup(state, emptyList(), "this-phone")

        val apps = JSONObject(raw).getJSONArray("apps")
        val saved = (0 until apps.length()).map { apps.getJSONObject(it) }.single { it.getString("id") == cloneId }
        assertTrue(saved.getBoolean("work"))
        assertEquals("clone", saved.getString("kind"))
        val savedPersonal = (0 until apps.length()).map { apps.getJSONObject(it) }.single { it.getString("id") == chat }
        assertFalse("a personal app's entry is exactly what it was", savedPersonal.has("kind"))

        val profiles = listOf(AppProfile(personalSerial, "Personal", true, false, false, true, true),
            AppProfile(cloneSerial, "Clone", false, false, false, true, true, ProfileKind.CLONE))
        val preview = decodeLayoutBackup(raw, listOf(personal, clone), profiles, "this-phone")
        assertEquals(emptyList<String>(), preview.missingApps)
        assertEquals(chat, preview.layout.slots[0])
        assertEquals(cloneId, preview.layout.slots[1])
    }

    @Test fun `a backup an older Folio wrote without a kind still reads`() {
        val clone = cloneApp()
        val slots = MutableList<String?>(HOME_CELLS) { null }.apply { this[0] = cloneId }
        val raw = encodeLayoutBackup(LauncherState(apps = listOf(clone), homeSlots = slots, widgetPlacements = emptyList(), loading = false), emptyList(), "this-phone")
        val root = JSONObject(raw)
        root.getJSONArray("apps").getJSONObject(0).remove("kind")
        val preview = decodeLayoutBackup(root.toString(), listOf(clone), emptyList(), "this-phone")
        assertEquals(cloneId, preview.layout.slots[0])
    }

    @Test fun `turning Dual Messenger off removes the clone's apps from Home`() {
        val personal = personalApp(chat)
        val clone = cloneApp()
        val work = AppEntry("duo-profile:v1:42:$chat", "Chat", icon, userSerial = 42, isWork = true)
        // Android stopped listing the clone profile; the work profile is still there.
        val gone = removedProfileSerials(listOf(personal, clone, work), associatedSerials = setOf(personalSerial, 42L))
        assertEquals(setOf(cloneSerial), gone)
        val removed = removedAppIds(
            savedIds = listOf(chat, cloneId, work.id),
            availableIds = setOf(chat),
            authoritativeProfiles = setOf(personalSerial),
            temporarilyUnavailable = emptySet(),
            confirmedRemoved = emptySet(),
            personalSerial = personalSerial,
            definitivelyRemovedProfiles = gone,
        )
        assertEquals(setOf(cloneId), removed)
    }

    @Test fun `a clone profile lists only apps the person cloned`() {
        val system = android.content.pm.ApplicationInfo.FLAG_SYSTEM
        val updated = android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP or system
        val other = android.content.pm.ApplicationInfo.FLAG_HAS_CODE
        // A clone of an app the person installed is listed; a preinstalled app Android also put in the clone is not.
        assertTrue(listedInProfile(ProfileKind.CLONE, 0))
        assertTrue(listedInProfile(ProfileKind.CLONE, other))
        assertFalse(listedInProfile(ProfileKind.CLONE, system))
        assertFalse(listedInProfile(ProfileKind.CLONE, updated))
        assertFalse(listedInProfile(ProfileKind.CLONE, android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP))
        // Personal and Work keep every app, system ones included.
        for (kind in listOf(ProfileKind.PERSONAL, ProfileKind.WORK)) for (flags in listOf(0, other, system, updated))
            assertTrue("$kind $flags", listedInProfile(kind, flags))
    }

    @Test fun `while the clone profile is listed nothing is removed`() {
        assertEquals(emptySet<Long>(), removedProfileSerials(listOf(personalApp(chat), cloneApp()), setOf(personalSerial, cloneSerial)))
    }
}
