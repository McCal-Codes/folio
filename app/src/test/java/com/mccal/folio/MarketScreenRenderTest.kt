package com.mccal.folio

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText

import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Renders the Market on the JVM, so the screen is checked without a phone: the packages Folio ships really appear,
 * Get applies one, and the introduction is shown once.
 *
 * These check structure and behaviour, not looks: Robolectric has no fonts, so text measures at the wrong size and a
 * screenshot would be meaningless. Looks are checked in the Mockup Lab and on the phone.
 */
@RunWith(RobolectricTestRunner::class)
// A real phone window: the default test window is too small for anything to count as displayed.
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class MarketScreenRenderTest {
    @get:Rule val compose = createComposeRule()

    /** Installing reads and writes files, so it happens off the main thread: the banner arrives a moment later. */
    private fun awaitText(text: String) = compose.waitUntil(5_000) {
        compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }

    private fun session(): MarketSession {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        return MarketSession(context, NoopLauncher(), kotlinx.coroutines.Dispatchers.Unconfined)
    }

    private class NoopLauncher : MarketLauncher {
        override var state = LauncherState()
        override fun installTweak(feature: TweakFeature) { state = state.copy(installedTweaks = state.installedTweaks + feature.id) }
        override fun removeTweak(feature: TweakFeature) { state = state.copy(installedTweaks = state.installedTweaks - feature.id) }
        override fun setFeatureScope(id: String, screen: FolioScreen, value: ScopeValue) = Unit
        override fun applyTheme(theme: FolioTheme) = Unit
        override fun applyArtBackground(art: Artwork, bytes: ByteArray, sha256: String): String = ""
        override fun restoreArtBackground(artId: String, snapshot: String) = Unit
    }

    /** A `.foliopkg` of one of Folio's own packages, zipped the way the build does it. */
    private fun packageFile(name: String): ByteArray {
        val root = generateSequence(java.io.File("").absoluteFile) { it.parentFile }.first { java.io.File(it, "CHANGELOG.md").exists() }
        val dir = java.io.File(root, "docs/sdk/source/packages/$name")
        val out = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(out).use { zip ->
            dir.walkTopDown().filter { it.isFile }.sortedBy { it.path }.forEach { file ->
                zip.putNextEntry(java.util.zip.ZipEntry(file.relativeTo(dir).invariantSeparatorsPath))
                zip.write(file.readBytes())
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    // Found on the Fold8, 29 Sep 2026: opening a .foliopkg opened the Market and nothing else. The effect that reads
    // the file was keyed on MarketImport.pending and cleared it before its read suspended, so the next frame cancelled
    // the read. The other tests read on Dispatchers.Unconfined, which never suspends, so this one uses a real thread.
    @Test fun `a package file opened from another app shows its confirm sheet`() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val session = MarketSession(context, NoopLauncher(), kotlinx.coroutines.Dispatchers.IO)
        session.prefs.introductionSeen = true
        try {
            MarketImport.pending = packageFile("cabinet")
            // On the phone the Market sits in a bottom sheet with its own window, and a composition there can be
            // thrown away and built again while the sheet opens. The sheet has to survive that.
            val build = androidx.compose.runtime.mutableIntStateOf(0)
            compose.setContent { androidx.compose.runtime.key(build.intValue) { MarketScreen(session, emptySet(), onClose = {}) } }
            compose.runOnIdle { build.intValue++ }
            compose.waitUntil(5_000) {
                compose.onAllNodesWithText("From a file you opened", substring = true).fetchSemanticsNodes().isNotEmpty()
            }
            // The file waits until the user decides; Cancel is deciding.
            compose.onNodeWithText("Cancel").performScrollTo().performClick()
            compose.waitUntil(5_000) {
                compose.onAllNodesWithText("From a file you opened", substring = true).fetchSemanticsNodes().isEmpty()
            }
            assertEquals(null, MarketImport.pending)
        } finally {
            MarketImport.pending = null
        }
    }

    /** The SDK's Tilt example as a .foliopkg. The example says 0.6.9; this build may be older, and the version check
     *  isn't what these tests are about. */
    private fun tiltPackage(): ByteArray {
        val root = generateSequence(java.io.File("").absoluteFile) { it.parentFile }.first { java.io.File(it, "CHANGELOG.md").exists() }
        val example = java.io.File(root, "docs/sdk/examples/page-effect-tilt")
        val manifest = org.json.JSONObject(java.io.File(example, "manifest.json").readText()).put("minFolio", "0.6.6")
        val out = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(out).use { zip ->
            for ((name, bytes) in listOf("manifest.json" to manifest.toString().toByteArray(), "effect.json" to java.io.File(example, "effect.json").readBytes())) {
                zip.putNextEntry(java.util.zip.ZipEntry(name)); zip.write(bytes); zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    // When Flipbook's listing is installed but its tweak was removed in Settings, the listing offers only Remove, so
    // the message's action adds the tweak back itself (found in review of #191).
    @Test fun `the Market can add a tweak back without its listing`() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val launcher = NoopLauncher()
        val session = MarketSession(context, launcher, kotlinx.coroutines.Dispatchers.Unconfined)
        assertEquals(true, session.addTweak("pageEffects"))
        assertEquals(true, "pageEffects" in launcher.state.installedTweaks)
        assertEquals(false, session.addTweak("no such tweak"))
    }

    // Like a script for jailbreak Cylinder, a page effect is an add-on to Flipbook. Getting one without Flipbook says
    // so, and the message's action opens Flipbook's own page, where it can be got.
    @Test fun `an effect without Flipbook offers Flipbook`() {
        val session = session()
        session.prefs.introductionSeen = true
        try {
            MarketImport.pending = tiltPackage()
            compose.setContent { MarketScreen(session, emptySet(), onClose = {}) }
            compose.waitUntil(5_000) { compose.onAllNodesWithText("From a file you opened", substring = true).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("market-install-confirm").performScrollTo().performClick()
            awaitText("Tilt works with Flipbook. Get Flipbook first.")
            assertEquals(true, session.installed().none { it.id == "com.mccal.folio.effect.tilt" })
            compose.onNodeWithText("Get Flipbook").performClick()
            // Flipbook's page, with its own Get.
            compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription("Get Flipbook").fetchSemanticsNodes().isNotEmpty() }
        } finally {
            MarketImport.pending = null
        }
    }

    // Found on the Fold8, 29 Sep 2026: a package installed from a file is listed by no source, and the Installed tab
    // only showed listings, so it was on the phone with no row to remove it by.
    @Test fun `a package installed from a file is under Installed and can be removed there`() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        // Tilt is a Flipbook add-on, so Flipbook has to be on the phone for it to install.
        val session = MarketSession(context, NoopLauncher().apply { state = LauncherState(installedTweaks = setOf("pageEffects")) },
            kotlinx.coroutines.Dispatchers.Unconfined)
        session.prefs.introductionSeen = true
        val result = kotlinx.coroutines.runBlocking { session.installFile(tiltPackage()) }
        assertEquals(true, result is com.mccal.folio.market.InstallResult.Installed)
        compose.setContent { MarketScreen(session, emptySet(), onClose = {}) }
        compose.onNodeWithTag("market-tab-installed").performClick()
        // A lazy list only builds what's on screen, so the list is scrolled to the section rather than the node.
        compose.onNode(androidx.compose.ui.test.hasScrollToNodeAction())
            .performScrollToNode(androidx.compose.ui.test.hasText("Opened from Files", ignoreCase = true))
        // Group labels are drawn in capitals, as iOS does.
        compose.onNodeWithText("Opened from Files", ignoreCase = true).assertIsDisplayed()
        compose.onNode(androidx.compose.ui.test.hasScrollToNodeAction())
            .performScrollToNode(androidx.compose.ui.test.hasContentDescription("Remove Tilt"))
        compose.onNodeWithContentDescription("Remove Tilt").performClick()
        compose.waitUntil(5_000) { session.installed().none { it.id == "com.mccal.folio.effect.tilt" } }
        awaitText("Tilt removed")
    }

    @Test fun `the introduction comes first, then Featured lists Folio's packages`() {
        val session = session()
        session.prefs.introductionSeen = false
        compose.setContent { MarketScreen(session, emptySet(), onClose = {}) }
        compose.onNodeWithText("Welcome to the Folio Market").assertIsDisplayed()
        compose.onNodeWithText("Skip").performClick()
        compose.onNodeWithText("Cabinet").assertIsDisplayed()
    }

    @Test fun `Get applies a package and offers Undo`() {
        val session = session()
        session.prefs.introductionSeen = true
        session.installed().forEach { session.remove(it.id) }
        compose.setContent { MarketScreen(session, emptySet(), onClose = {}) }
        compose.onNodeWithTag("market-tab-packages").performClick()
        compose.onNodeWithContentDescription("Get Cabinet").performClick()
        compose.onNodeWithTag("market-install-confirm").performScrollTo().performClick()
        awaitText("Cabinet is on")
        compose.onNodeWithText("Undo").assertExists()
    }

    @Test fun `a message with Undo stays, and a plain one goes away by itself`() {
        val session = session()
        session.prefs.introductionSeen = true
        session.installed().forEach { session.remove(it.id) }
        compose.setContent { MarketScreen(session, emptySet(), onClose = {}) }
        compose.onNodeWithTag("market-tab-packages").performClick()
        compose.onNodeWithContentDescription("Get Cabinet").performClick()
        compose.onNodeWithTag("market-install-confirm").performScrollTo().performClick()
        awaitText("Cabinet is on")
        // Dismissing Undo is what ends the chance to undo, so it waits for that rather than a clock.
        compose.mainClock.advanceTimeBy(10_000)
        compose.onNodeWithText("Undo").assertExists()
        // Removing says so with a plain message, which leaves on its own.
        compose.onNodeWithContentDescription("Remove Cabinet").performClick()
        awaitText("Cabinet removed")
        compose.mainClock.advanceTimeBy(10_000)
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Cabinet removed").fetchSemanticsNodes().isEmpty() }
    }

    @Test fun `a package Safe Mode turned off says so, and Try Again puts it back`() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val launcher = NoopLauncher()
        val session = MarketSession(context, launcher, kotlinx.coroutines.Dispatchers.Unconfined)
        session.prefs.introductionSeen = true
        session.installed().forEach { session.remove(it.id) }

        val cabinet = session.source.index()!!.packages.first { it.id == "com.mccal.folio.cabinet" }
        session.get(cabinet)
        assertEquals("the tweak is on", setOf("appPanels"), launcher.state.installedTweaks)

        // What Safe Mode does after two crashes: the changes come off Home, the record stays.
        session.disable(cabinet.id, "Folio stopped twice just after this package changed.")
        assertEquals("off means off", emptySet<String>(), launcher.state.installedTweaks)

        compose.setContent { MarketScreen(session, emptySet(), onClose = {}) }
        compose.onNodeWithTag("market-tab-packages").performClick()
        compose.onNodeWithText("Cabinet").performClick()
        compose.onNodeWithText("Turned off after a crash").assertIsDisplayed()

        compose.onNodeWithTag("package-try-again").performScrollTo().performClick()
        awaitText("Cabinet is back on")
        assertEquals("and the tweak is on again", setOf("appPanels"), launcher.state.installedTweaks)
    }

    @Test fun `every tab opens, and the Market's settings offer both Featured styles`() {
        val session = session()
        session.prefs.introductionSeen = true
        compose.setContent { MarketScreen(session, emptySet(), onClose = {}) }
        for (tab in MarketTab.entries) {
            compose.onNodeWithTag("market-tab-${tab.name.lowercase()}").performClick()
        }
        // Settings is the last tab; it offers the style choice and a way back to Folio's own settings.
        compose.onNodeWithText("Carousel").assertIsDisplayed()
        compose.onNodeWithText("Calm").assertIsDisplayed()
        compose.onNodeWithText("Open Folio Settings").assertIsDisplayed()
    }

    @Test fun `Sources lists Folio's own and offers to add one`() {
        val session = session()
        session.prefs.introductionSeen = true
        compose.setContent { MarketScreen(session, emptySet(), onClose = {}) }
        compose.onNodeWithTag("market-tab-sources").performClick()
        compose.onNodeWithText("Built into the app · no network").assertExists()
        compose.onNodeWithText("Add a source").performClick()
        // Adding one starts by asking for the address; nothing is trusted yet.
        compose.onNodeWithTag("market-add-source", useUnmergedTree = true).assertExists()
    }

    @Test fun `the Settings tab shows Folio's own settings when the launcher gives them`() {
        val session = session()
        session.prefs.introductionSeen = true
        compose.setContent {
            MarketScreen(session, emptySet(), onClose = {}, settingsContent = { androidx.compose.material3.Text("Folio settings live here") })
        }
        compose.onNodeWithTag("market-tab-settings").performClick()
        compose.onNodeWithText("Folio settings live here").assertIsDisplayed()
    }

    @Test fun `the app icon opens the Market, unless something asked for a Settings page`() {
        assertEquals("market", sheetForAppIcon(null, CustomizationPage.OVERVIEW, marketEnabled = true))
        assertEquals("settings", sheetForAppIcon(null, CustomizationPage.OVERVIEW, marketEnabled = false))
        assertEquals("settings", sheetForAppIcon(CustomizationPage.PERMISSIONS, CustomizationPage.OVERVIEW, marketEnabled = true))
        assertEquals("settings", sheetForAppIcon(null, CustomizationPage.SOFTWARE_UPDATE, marketEnabled = true))
    }

    @Test fun `Get asks first, and the sheet says what changes and what it can't reach`() {
        val session = session()
        session.prefs.introductionSeen = true
        session.installed().forEach { session.remove(it.id) }
        compose.setContent { MarketScreen(session, emptySet(), onClose = {}) }
        compose.onNodeWithTag("market-tab-packages").performClick()
        compose.onNodeWithContentDescription("Get Cabinet").performClick()
        // Nothing has been applied yet: this is the confirm step.
        compose.onNodeWithTag("market-install-sheet", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Changes Folio tweaks").assertExists()
        compose.onNodeWithText("Your apps or their data").assertExists()
        assertEquals(emptyList<com.mccal.folio.market.InstalledPackage>(), session.installed())
        compose.onNodeWithTag("market-install-confirm").performScrollTo().performClick()
        awaitText("Cabinet is on")
        assertEquals(listOf("com.mccal.folio.cabinet"), session.installed().map { it.id })
    }

    @Test fun `the install sheet says when AI helped make a package, and says nothing when it didn't`() {
        val root = generateSequence(java.io.File("").absoluteFile) { it.parentFile }.first { java.io.File(it, "CHANGELOG.md").exists() }
        val cabinet = org.json.JSONObject(java.io.File(root, "docs/sdk/source/packages/cabinet/manifest.json").readText())
        fun manifest(json: org.json.JSONObject) =
            (com.mccal.folio.market.PackageManifest.parse(json.toString()) as com.mccal.folio.market.ParseResult.Ok).value
        val helped = manifest(org.json.JSONObject(cabinet.toString()).put("aiAssisted",
            org.json.JSONObject().put("tools", org.json.JSONArray().put("Claude").put("Example tool")).put("note", "Drafted the page.")))
        val shown = androidx.compose.runtime.mutableStateOf(helped)
        compose.setContent { MarketInstallSheet(shown.value, InstallOrigin("From a source"), onGet = {}, onCancel = {}) }
        compose.onNodeWithText("Made with help from AI: Claude, Example tool").assertExists()
        compose.onNodeWithText("Drafted the page.").assertExists()
        shown.value = manifest(cabinet)
        compose.waitForIdle()
        compose.onNodeWithTag("install-ai-assisted", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun `a package from a source shows up in the list, with where it came from`() {
        val session = session()
        session.prefs.introductionSeen = true
        // A source the user added, cached the way a refresh leaves it.
        addCachedSource("https://maya.example/folio/", "Maya", mayaIndex())
        compose.setContent { MarketScreen(session, emptySet(), onClose = {}) }
        compose.onNodeWithTag("market-tab-packages").performClick()
        compose.onNodeWithText("Cabinet").assertExists()
        // Folio's own packages say nothing about a source; this one names it.
        compose.onNodeWithText("Example · Maya").assertExists()
    }

    @Test fun `a source is a place with its packages in it`() {
        val session = session()
        session.prefs.introductionSeen = true
        addCachedSource("https://maya.example/folio/", "Maya", mayaIndex())
        compose.setContent { MarketScreen(session, emptySet(), onClose = {}) }
        compose.onNodeWithTag("market-tab-sources").performClick()
        // The row is the way in, the way a repo is in Cydia and Sileo - not a line with buttons on it.
        compose.onNodeWithText("Maya").performClick()
        compose.onNodeWithTag("market-source-page", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("https://maya.example/folio/").assertExists()
        compose.onNodeWithText("FROM THIS SOURCE").assertExists()
        // Its package is listed here, and it is the same row as anywhere else: Get and all.
        compose.onNodeWithText("Sunset Icons").assertExists()
        compose.onNodeWithContentDescription("Get Sunset Icons").assertExists()
    }

    @Test fun `a package names the source that lists it, and that leads there`() {
        val session = session()
        session.prefs.introductionSeen = true
        addCachedSource("https://maya.example/folio/", "Maya", mayaIndex())
        compose.setContent { MarketScreen(session, emptySet(), onClose = {}) }
        compose.onNodeWithTag("market-tab-packages").performClick()
        compose.onNodeWithText("Sunset Icons").performClick()
        compose.onNodeWithTag("package-show-source", useUnmergedTree = true).performScrollTo().performClick()
        compose.onNodeWithTag("market-source-page", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("https://maya.example/folio/").assertExists()
    }

    @Test fun `an app with a newer listing than the copy on the phone says Update, with both versions`() {
        val session = session()
        session.prefs.introductionSeen = true
        addCachedSource("https://mccal-codes.github.io/folio-keyd/", "Keyd", keydIndex())
        keydOnThePhone("0.2.0")
        compose.setContent { MarketScreen(session, emptySet(), onClose = {}) }
        compose.onNodeWithTag("market-tab-packages").performClick()
        compose.onNodeWithContentDescription("Update Keyd").assertExists()
        compose.onNodeWithText("0.2.0 → 0.3.0").assertExists()
        // And it is listed under Updates in Installed, though Android has it rather than Folio.
        compose.onNodeWithTag("market-tab-installed").performClick()
        compose.onNodeWithContentDescription("Update Keyd").assertExists()
    }

    @Test fun `an app that is up to date says Open`() {
        val session = session()
        session.prefs.introductionSeen = true
        addCachedSource("https://mccal-codes.github.io/folio-keyd/", "Keyd", keydIndex())
        keydOnThePhone("0.3.0")
        compose.setContent { MarketScreen(session, emptySet(), onClose = {}) }
        compose.onNodeWithTag("market-tab-packages").performClick()
        compose.onNodeWithContentDescription("Open Keyd").assertExists()
        assertEquals(0, compose.onAllNodesWithText("0.2.0 → 0.3.0").fetchSemanticsNodes().size)
    }

    private fun keydOnThePhone(versionName: String) {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        org.robolectric.Shadows.shadowOf(context.packageManager).installPackage(
            android.content.pm.PackageInfo().apply {
                packageName = "com.mccal.keyd"
                this.versionName = versionName
                applicationInfo = android.content.pm.ApplicationInfo().apply { packageName = "com.mccal.keyd" }
            },
        )
    }

    /** Keyd's own source as it is published: one app, 0.3.0, installed from Obtainium or by Folio. */
    private fun keydIndex(): String {
        val manifest = """
            {"format":1,"id":"com.mccal.keyd","name":"Keyd","version":"0.3.0","author":{"name":"McCal"},
             "minFolio":"0.6.6","section":"tweaks","kind":["externalApp"],"permissions":[],
             "via":[{"store":"obtainium","repoUrl":"https://github.com/McCal-Codes/folio-keyd","id":"com.mccal.keyd"}]}
        """.trimIndent()
        return """{"format":1,"name":"Keyd","packages":[{"id":"com.mccal.keyd","version":"0.3.0",
            "url":"https://example.test/Keyd-0.3.0.apk","sha256":"${"a".repeat(64)}","size":1024,"manifest":$manifest}]}"""
    }

    /**
     * Writes what a successful refresh leaves behind — the source, its list and the entry that pinned it — using the
     * same store the client reads, so the screen is showing a real cached source rather than a stub.
     */
    private fun addCachedSource(url: String, name: String, indexJson: String) {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val files = com.mccal.folio.market.FileStore(java.io.File(context.filesDir, "market"))
        com.mccal.folio.market.SourceList(files).add(com.mccal.folio.market.Source(url, name = name, addedAt = 1))
        val store = com.mccal.folio.market.SourceStore(files)
        val bytes = indexJson.toByteArray()
        val hash = java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        store.cache(url, "index", indexJson)
        store.cache(
            url, "entry",
            """{"format":1,"keyId":"A1B2C3D4E5F60789","timestamp":1789660320,"maxAge":604800,
                "index":{"path":"index.json","sha256":"$hash","size":${bytes.size}}}""",
        )
    }

    /** A one-package index from another source, as its cached list. */
    private fun mayaIndex(): String {
        val manifest = """
            {"format":1,"id":"dev.maya.sunset-icons","name":"Sunset Icons","version":"1.2.0",
             "author":{"name":"Example"},"minFolio":"0.7.0","section":"themes","kind":["theme"],
             "permissions":["home.appearance"]}
        """.trimIndent()
        return """{"format":1,"name":"Maya","packages":[{"id":"dev.maya.sunset-icons","version":"1.2.0",
            "url":"packages/sunset.foliopkg","sha256":"${"a".repeat(64)}","size":1024,"manifest":$manifest}]}"""
    }
}
