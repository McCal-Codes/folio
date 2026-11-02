package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure unit tests for StandBy 2 posture classification ([resolveStandByPosture]), effective configuration
 * fallback ([resolveStandByConfig]), and epilepsy/photosensitivity safety invariants ([StandBySafetyCaps]).
 */
class StandByPostureTest {

    // --- 1. Posture resolution: inner display half-open (Home / WindowManager) ---

    @Test fun `inner half-open with vertical crease resolves to BOOK`() {
        val posture = resolveStandByPosture(
            StandByPostureSignals(
                widthDp = 884f,
                heightDp = 768f,
                way = StandByWay.HALF_OPEN,
                halfOpenVerticalCrease = true,
            )
        )
        assertEquals(StandByPosture.BOOK, posture)
        assertFalse(posture.isStacked)
        assertTrue(posture.hasCrease)
    }

    @Test fun `inner half-open with horizontal crease resolves to TABLETOP`() {
        val posture = resolveStandByPosture(
            StandByPostureSignals(
                widthDp = 768f,
                heightDp = 884f,
                way = StandByWay.HALF_OPEN,
                halfOpenVerticalCrease = false,
            )
        )
        assertEquals(StandByPosture.TABLETOP, posture)
        assertTrue(posture.isStacked)
        assertTrue(posture.hasCrease)
    }

    @Test fun `inner half-open with LocalHinge spans and null WindowManager orientation resolves to BOOK`() {
        val posture = resolveStandByPosture(
            StandByPostureSignals(
                widthDp = 884f,
                heightDp = 768f,
                way = StandByWay.HALF_OPEN,
                halfOpenVerticalCrease = null,
                hingeSpansPx = listOf(1080..1120),
            )
        )
        assertEquals(StandByPosture.BOOK, posture)
    }

    @Test fun `inner half-open with empty LocalHinge spans and null WindowManager orientation resolves to TABLETOP`() {
        val posture = resolveStandByPosture(
            StandByPostureSignals(
                widthDp = 768f,
                heightDp = 884f,
                way = StandByWay.HALF_OPEN,
                halfOpenVerticalCrease = null,
                hingeSpansPx = emptyList(),
            )
        )
        assertEquals(StandByPosture.TABLETOP, posture)
    }

    // --- 2. Posture resolution: DreamService on inner display (no Activity / WindowManager) ---

    @Test fun `inner DreamService with hinge halfway and sideways gravity resolves to BOOK`() {
        val posture = resolveStandByPosture(
            StandByPostureSignals(
                widthDp = 884f,
                heightDp = 768f,
                way = null,
                hingeHalfway = true,
                sidewaysGravity = true,
            )
        )
        assertEquals(StandByPosture.BOOK, posture)
    }

    @Test fun `inner DreamService with hinge halfway and upright gravity resolves to TABLETOP`() {
        val posture = resolveStandByPosture(
            StandByPostureSignals(
                widthDp = 768f,
                heightDp = 884f,
                way = null,
                hingeHalfway = true,
                sidewaysGravity = false,
            )
        )
        assertEquals(StandByPosture.TABLETOP, posture)
    }

    @Test fun `inner flat landscape charging resolves to LANDSCAPE`() {
        val posture = resolveStandByPosture(
            StandByPostureSignals(
                widthDp = 884f,
                heightDp = 768f,
                way = StandByWay.CHARGING,
                hingeHalfway = false,
            )
        )
        assertEquals(StandByPosture.LANDSCAPE, posture)
        assertFalse(posture.isStacked)
        assertFalse(posture.hasCrease)
    }

    @Test fun `inner flat portrait resolves to PORTRAIT`() {
        val posture = resolveStandByPosture(
            StandByPostureSignals(
                widthDp = 768f,
                heightDp = 884f,
                way = null,
                hingeHalfway = false,
            )
        )
        assertEquals(StandByPosture.PORTRAIT, posture)
        assertTrue(posture.isStacked)
        assertFalse(posture.hasCrease)
    }

    // --- 3. Posture resolution: cover display and regular phones ---

    @Test fun `cover display with StandByWay TENT resolves to TENT`() {
        val posture = resolveStandByPosture(
            StandByPostureSignals(
                widthDp = 840f,
                heightDp = 390f,
                way = StandByWay.TENT,
            )
        )
        assertEquals(StandByPosture.TENT, posture)
        assertFalse(posture.isStacked)
        assertFalse(posture.hasCrease)
    }

    @Test fun `cover DreamService with hinge halfway and sideways gravity resolves to TENT`() {
        val posture = resolveStandByPosture(
            StandByPostureSignals(
                widthDp = 840f,
                heightDp = 390f,
                way = null,
                hingeHalfway = true,
                sidewaysGravity = true,
            )
        )
        assertEquals(StandByPosture.TENT, posture)
    }

    @Test fun `cover landscape charging without tent resolves to LANDSCAPE`() {
        val posture = resolveStandByPosture(
            StandByPostureSignals(
                widthDp = 840f,
                heightDp = 390f,
                way = StandByWay.CHARGING,
                hingeHalfway = false,
            )
        )
        assertEquals(StandByPosture.LANDSCAPE, posture)
    }

    @Test fun `cover portrait on stand resolves to PORTRAIT`() {
        val posture = resolveStandByPosture(
            StandByPostureSignals(
                widthDp = 390f,
                heightDp = 840f,
                way = null,
                hingeHalfway = false,
            )
        )
        assertEquals(StandByPosture.PORTRAIT, posture)
        assertTrue(posture.isStacked)
    }

    @Test fun `large font scale on cover does not misclassify cover as inner display`() {
        val posture = resolveStandByPosture(
            StandByPostureSignals(
                widthDp = 920f,
                heightDp = 640f,
                classScale = 0.8f, // 640 * 0.8 = 512 < 600 -> cover display
                way = StandByWay.TENT,
            )
        )
        assertEquals(StandByPosture.TENT, posture)
    }

    // --- 4. Effective config fallback (eff()) ---

    @Test fun `closed FeatureGate STANDBY_2 returns exact 0_6_9 legacy defaults regardless of tweak state`() {
        val cfg = resolveStandByConfig(
            gateOpen = false,
            packageInstalled = true,
            packageEnabled = true,
            clockLook = StandByClockLook.BOLD,
            playerStyle = StandByPlayerStyle.OFF,
            upNext = false,
            timersBesideMedia = false,
            notifMode = StandByNotifMode.COLORS,
            nightMode = StandByNightMode.NEVER,
        )
        assertEquals(StandByEffectiveConfig.LEGACY_0_6_9, cfg)
        assertFalse(cfg.activePackage)
        assertTrue(cfg.tapEmptySpaceCloses)
        assertEquals(StandByClockLook.THIN, cfg.clockLook)
        assertEquals(StandByPlayerStyle.LEGACY_SLAB, cfg.playerStyle)
        assertTrue(cfg.upNext)
        assertFalse(cfg.timersBesideMedia)
        assertEquals(StandByNotifMode.OFF, cfg.notifMode)
        assertEquals(StandByNightMode.SCHEDULE, cfg.nightMode)
    }

    @Test fun `uninstalled tweak returns exact 0_6_9 legacy defaults even when gate is open`() {
        val cfg = resolveStandByConfig(
            gateOpen = true,
            packageInstalled = false,
            packageEnabled = true,
            clockLook = StandByClockLook.BOLD,
        )
        assertEquals(StandByEffectiveConfig.LEGACY_0_6_9, cfg)
    }

    @Test fun `disabled tweak switch returns exact 0_6_9 legacy defaults`() {
        val cfg = resolveStandByConfig(
            gateOpen = true,
            packageInstalled = true,
            packageEnabled = false,
            clockLook = StandByClockLook.BOLD,
        )
        assertEquals(StandByEffectiveConfig.LEGACY_0_6_9, cfg)
    }

    @Test fun `active StandBy 2 tweak resolves to StandBy 2 defaults`() {
        val cfg = resolveStandByConfig(
            gateOpen = true,
            packageInstalled = true,
            packageEnabled = true,
        )
        assertTrue(cfg.activePackage)
        assertFalse(cfg.tapEmptySpaceCloses)
        assertEquals(StandByClockLook.THIN, cfg.clockLook)
        assertEquals(StandByPlayerStyle.CARD, cfg.playerStyle)
        assertTrue(cfg.upNext)
        assertTrue(cfg.timersBesideMedia)
        assertEquals(StandByNotifMode.COLORS, cfg.notifMode)
        assertEquals(StandByNightMode.SCHEDULE, cfg.nightMode)
    }

    @Test fun `resolveStandByConfig with LauncherState honors installedTweaks, standBy2 switch, FeatureScopes and TweakOptions`() {
        val base = LauncherState()
        // Default LauncherState does not have standby2 installed yet -> LEGACY_0_6_9
        assertEquals(StandByEffectiveConfig.LEGACY_0_6_9, resolveStandByConfig(base, gateOpen = true, onCover = false))

        // Installed and enabled on inner screen with custom options
        val installed = base.copy(
            standBy2 = true,
            installedTweaks = base.installedTweaks + STANDBY2_ID,
            tweakOptions = mapOf(
                STANDBY2_ID to mapOf(
                    "clock" to "bold",
                    "player" to "compact",
                    "upNext" to "off",
                    "timers" to "off",
                    "notif" to "count",
                    "nightMode" to "never",
                    "nightBrightness" to "20",
                )
            ),
        )
        val activeInner = resolveStandByConfig(installed, gateOpen = true, onCover = false)
        assertTrue(activeInner.activePackage)
        assertFalse(activeInner.tapEmptySpaceCloses)
        assertEquals(StandByClockLook.BOLD, activeInner.clockLook)
        assertEquals(StandByPlayerStyle.COMPACT, activeInner.playerStyle)
        assertFalse(activeInner.upNext)
        assertFalse(activeInner.timersBesideMedia)
        assertEquals(StandByNotifMode.COUNT_ONLY, activeInner.notifMode)
        assertEquals(StandByNightMode.NEVER, activeInner.nightMode)
        assertEquals(20, activeInner.nightBrightnessPercent)

        // Gate closed -> LEGACY_0_6_9 even when installed and enabled
        assertEquals(StandByEffectiveConfig.LEGACY_0_6_9, resolveStandByConfig(installed, gateOpen = false, onCover = false))

        // Scoped off on COVER -> onCover = true returns LEGACY_0_6_9
        val innerOnly = installed.copy(featureScopes = FeatureScopes.set(emptyMap(), STANDBY2_ID, FolioScreen.COVER, ScopeValue.OFF))
        assertEquals(StandByEffectiveConfig.LEGACY_0_6_9, resolveStandByConfig(innerOnly, gateOpen = true, onCover = true))
        assertTrue(resolveStandByConfig(innerOnly, gateOpen = true, onCover = false).activePackage)

        // StandBy 2.5 options: analog, flip, world, nightColor, offCharger
        val v25 = installed.copy(
            tweakOptions = mapOf(
                STANDBY2_ID to mapOf(
                    "clock" to "analog",
                    "nightColor" to "amber",
                    "offCharger" to "on",
                )
            )
        )
        val v25Config = resolveStandByConfig(v25, gateOpen = true, onCover = false)
        assertEquals(StandByClockLook.ANALOG, v25Config.clockLook)
        assertEquals("amber", v25Config.nightColor)
        assertTrue(v25Config.offCharger)

        val flipConfig = resolveStandByConfig(v25.copy(tweakOptions = mapOf(STANDBY2_ID to mapOf("clock" to "flip", "nightColor" to "blue"))), gateOpen = true)
        assertEquals(StandByClockLook.FLIP, flipConfig.clockLook)
        assertEquals("blue", flipConfig.nightColor)

        val worldConfig = resolveStandByConfig(v25.copy(tweakOptions = mapOf(STANDBY2_ID to mapOf("clock" to "world"))), gateOpen = true)
        assertEquals(StandByClockLook.WORLD, worldConfig.clockLook)
    }

    // --- 5. Epilepsy / photosensitivity safety invariants (WCAG 2.3.1 / A11Y-15) ---

    @Test fun `every transition duration is at least 300ms and never flashes`() {
        assertTrue(StandBySafetyCaps.MIN_FADE_MS >= 300)
        assertTrue(StandBySafetyCaps.ENTER_FADE_MS >= StandBySafetyCaps.MIN_FADE_MS)
        assertTrue(StandBySafetyCaps.EXIT_FADE_MS >= StandBySafetyCaps.MIN_FADE_MS)
        assertTrue(StandBySafetyCaps.VIEW_FADE_MS >= StandBySafetyCaps.MIN_FADE_MS)
        assertTrue(StandBySafetyCaps.BURN_IN_GLIDE_MS >= StandBySafetyCaps.MIN_FADE_MS)
    }

    @Test fun `notification glow is rate-limited to at most once per 10 seconds with soft alpha`() {
        assertTrue(StandBySafetyCaps.MIN_GLOW_INTERVAL_MS >= 10_000L)
        assertTrue(StandBySafetyCaps.GLOW_IN_MS >= 300)
        assertTrue(StandBySafetyCaps.GLOW_OUT_MS >= 300)
        assertTrue(StandBySafetyCaps.MAX_GLOW_ALPHA <= 0.45f)
    }

    @Test fun `burn-in shift step never exceeds 6 dp`() {
        assertEquals(6, StandBySafetyCaps.MAX_BURN_IN_DP)
        val minute = 60_000L
        for (i in 0 until 16) {
            val (dx, dy) = burnInShift(i * minute)
            assertTrue(kotlin.math.abs(dx) <= StandBySafetyCaps.MAX_BURN_IN_DP)
            assertTrue(kotlin.math.abs(dy) <= StandBySafetyCaps.MAX_BURN_IN_DP)
        }
    }

    // --- 6. Option A: Now Playing skip actions, Timer formatting, Charging line, Night art filter ---

    @Test fun `mediaSkipOffered checks PlaybackState action flags for Previous and Next`() {
        val prevFlag = android.media.session.PlaybackState.ACTION_SKIP_TO_PREVIOUS
        val nextFlag = android.media.session.PlaybackState.ACTION_SKIP_TO_NEXT
        val playPauseOnly = android.media.session.PlaybackState.ACTION_PLAY_PAUSE

        assertFalse(mediaSkipOffered(playPauseOnly, prevFlag))
        assertFalse(mediaSkipOffered(playPauseOnly, nextFlag))
        assertTrue(mediaSkipOffered(playPauseOnly or prevFlag, prevFlag))
        assertFalse(mediaSkipOffered(playPauseOnly or prevFlag, nextFlag))
        assertTrue(mediaSkipOffered(playPauseOnly or prevFlag or nextFlag, prevFlag))
        assertTrue(mediaSkipOffered(playPauseOnly or prevFlag or nextFlag, nextFlag))
    }

    @Test fun `formatStandByTimer formats countdown and stopwatch in tabular MM_SS and H_MM_SS and clamps at zero`() {
        val now = 100_000L
        // 4 min 59 sec remaining
        assertEquals("04:59", formatStandByTimer(baseMs = now + 299_000L, nowMs = now, countDown = true))
        // 1 hr 5 min 9 sec remaining
        assertEquals("1:05:09", formatStandByTimer(baseMs = now + 3_909_000L, nowMs = now, countDown = true))
        // Expired countdown clamps to 00:00
        assertEquals("00:00", formatStandByTimer(baseMs = now - 5_000L, nowMs = now, countDown = true))
        // Stopwatch counting up 42 sec
        assertEquals("00:42", formatStandByTimer(baseMs = now - 42_000L, nowMs = now, countDown = false))
    }

    @Test fun `formatStandByChargeLine formats Full in, On hold at N percent, and hides when disabled or unknown`() {
        val onHoldFmt = { pct: Int -> "On hold at $pct%" }
        val fullInFmt = { dur: String -> "Full in $dur" }

        // Legacy 0.6.9 (showChargingLine = false) never shows a charging line
        assertEquals(
            null,
            formatStandByChargeLine(
                showChargingLine = false,
                charging = true,
                battery = 65,
                chargeFullInMs = 80 * 60_000L,
                chargeOnHold = false,
                onHoldFormat = onHoldFmt,
                fullInFormat = fullInFmt,
            )
        )

        // Unplugged -> null
        assertEquals(
            null,
            formatStandByChargeLine(
                showChargingLine = true,
                charging = false,
                battery = 65,
                chargeFullInMs = 80 * 60_000L,
                chargeOnHold = false,
                onHoldFormat = onHoldFmt,
                fullInFormat = fullInFmt,
            )
        )

        // Active charging with 1 h 20 min remaining
        assertEquals(
            "Full in 1 h 20 min",
            formatStandByChargeLine(
                showChargingLine = true,
                charging = true,
                battery = 50,
                chargeFullInMs = 80 * 60_000L,
                chargeOnHold = false,
                onHoldFormat = onHoldFmt,
                fullInFormat = fullInFmt,
            )
        )

        // Battery protection holding at 80%
        assertEquals(
            "On hold at 80%",
            formatStandByChargeLine(
                showChargingLine = true,
                charging = true,
                battery = 80,
                chargeFullInMs = null,
                chargeOnHold = true,
                onHoldFormat = onHoldFmt,
                fullInFormat = fullInFmt,
            )
        )

        // Unknown charge time (-1L / null) while not on hold -> hidden (null)
        assertEquals(
            null,
            formatStandByChargeLine(
                showChargingLine = true,
                charging = true,
                battery = 50,
                chargeFullInMs = null,
                chargeOnHold = false,
                onHoldFormat = onHoldFmt,
                fullInFormat = fullInFmt,
            )
        )
    }

    @Test fun `isChargeOnHold detects battery protection pause when plugged in and not charging below 100 percent`() {
        assertTrue(isChargeOnHold(android.os.BatteryManager.BATTERY_STATUS_NOT_CHARGING, plugged = 1, batteryPercent = 80))
        assertFalse(isChargeOnHold(android.os.BatteryManager.BATTERY_STATUS_CHARGING, plugged = 1, batteryPercent = 80))
        assertFalse(isChargeOnHold(android.os.BatteryManager.BATTERY_STATUS_FULL, plugged = 1, batteryPercent = 100))
        assertFalse(isChargeOnHold(android.os.BatteryManager.BATTERY_STATUS_NOT_CHARGING, plugged = 0, batteryPercent = 80))
    }

    @Test fun `nightArtColorMatrix maps album art strictly into red channel with zero green and blue`() {
        val m = nightArtColorMatrix()
        assertEquals(20, m.size)
        // Row 0 (Red): positive luminance weights summing below 1.0
        val rSum = m[0] + m[1] + m[2]
        assertTrue(rSum in 0.5f..0.9f)
        assertEquals(0f, m[3], 0f)
        assertEquals(0f, m[4], 0f)
        // Row 1 (Green) and Row 2 (Blue): strictly zero so no green/blue light is emitted at night
        for (i in 5..14) {
            assertEquals("index $i must be 0f", 0f, m[i], 0f)
        }
        // Row 3 (Alpha): identity
        assertEquals(1f, m[18], 0f)
    }

    @Test fun `nightArtColorMatrix amber maps to red and green channels without blue`() {
        val m = nightArtColorMatrix("amber")
        assertEquals(20, m.size)
        assertTrue(m[0] > 0f)
        assertTrue(m[5] > 0f)
        for (i in 10..14) assertEquals("index $i must be 0f", 0f, m[i], 0f)
    }

    @Test fun `nightArtColorMatrix blue maps to blue and soft green channels without red`() {
        val m = nightArtColorMatrix("blue")
        assertEquals(20, m.size)
        for (i in 0..4) assertEquals("index $i must be 0f", 0f, m[i], 0f)
        assertTrue(m[10] > 0f)
    }

    // --- 7. Phase 6: Notification dots, count, contrast nudging, lock-screen privacy, and glow rate-limiter ---

    @Test fun `nudgeStandByColorOnBlack lifts dark colors to at least 3 to 1 non-text contrast on black`() {
        // Already bright Folio Blue passes unchanged
        val blue = FolioColors.Value.Blue.toInt()
        assertTrue(standByRelativeLuminance(blue) >= 0.10)
        assertEquals(blue, nudgeStandByColorOnBlack(blue))

        // Very dark navy (0xFF050A14) is lifted until relative luminance >= 0.10
        val darkNavy = (255 shl 24) or (5 shl 16) or (10 shl 8) or 20
        assertTrue(standByRelativeLuminance(darkNavy) < 0.10)
        val nudged = nudgeStandByColorOnBlack(darkNavy)
        assertTrue(standByRelativeLuminance(nudged) >= 0.10)
    }

    @Test fun `resolveStandByNotifications caps unique app dots at 5 with overflow count and counts all new notifications`() {
        val sessionStart = 1_000L
        val items = (1..7).flatMap { appIndex ->
            listOf(
                StandByNotifCandidate(
                    key = "pkg$appIndex-a",
                    packageName = "com.example.app$appIndex",
                    postTime = sessionStart + appIndex * 100L,
                    color = FolioColors.Value.Green.toInt(),
                ),
                StandByNotifCandidate(
                    key = "pkg$appIndex-b",
                    packageName = "com.example.app$appIndex",
                    postTime = sessionStart + appIndex * 100L + 10L,
                    color = FolioColors.Value.Green.toInt(),
                ),
            )
        } + listOf(
            // Old notification before StandBy started -> ignored
            StandByNotifCandidate(key = "old", packageName = "com.example.old", postTime = sessionStart - 1L),
            // Ongoing service -> ignored
            StandByNotifCandidate(key = "ongoing", packageName = "com.example.ongoing", postTime = sessionStart + 500L, ongoing = true),
            // Low importance (< IMPORTANCE_DEFAULT = 3) -> ignored
            StandByNotifCandidate(key = "low", packageName = "com.example.low", postTime = sessionStart + 500L, importance = 2),
        )

        val summary = resolveStandByNotifications(
            items = items,
            sessionStartMs = sessionStart,
            mode = StandByNotifMode.COLORS,
            locked = false,
        )
        assertEquals(14, summary.totalCount)
        assertEquals(5, summary.dotColors.size)
        assertEquals(2, summary.overflowApps) // 7 unique apps - 5 dots = +2
        assertEquals("pkg7-b@1710", summary.newestGlowKey)
    }

    @Test fun `resolveStandByNotifications respects COUNT_ONLY, OFF, and lock-screen privacy rules`() {
        val sessionStart = 1_000L
        val items = listOf(
            StandByNotifCandidate(key = "pub", packageName = "app.pub", postTime = 1_500L, visibility = 1),
            StandByNotifCandidate(key = "priv", packageName = "app.priv", postTime = 1_600L, visibility = 0),
            StandByNotifCandidate(key = "sec", packageName = "app.sec", postTime = 1_700L, visibility = -1), // VISIBILITY_SECRET
        )

        // OFF mode -> empty
        val off = resolveStandByNotifications(items, sessionStart, StandByNotifMode.OFF, locked = false)
        assertEquals(0, off.totalCount)
        assertTrue(off.dotColors.isEmpty())

        // COUNT_ONLY mode -> totalCount only, no dots or glow key
        val countOnly = resolveStandByNotifications(items, sessionStart, StandByNotifMode.COUNT_ONLY, locked = false)
        assertEquals(3, countOnly.totalCount)
        assertTrue(countOnly.dotColors.isEmpty())
        assertEquals(null, countOnly.newestGlowKey)

        // Locked with VISIBILITY_SECRET excluded (leaves pub + priv = 2)
        val lockedPartial = resolveStandByNotifications(items, sessionStart, StandByNotifMode.COLORS, locked = true, lockScreenShowNotifs = true)
        assertEquals(2, lockedPartial.totalCount)
        assertEquals(2, lockedPartial.dotColors.size)

        // Locked with Android's lock_screen_show_notifications == 0 -> hides everything
        val lockedHidden = resolveStandByNotifications(items, sessionStart, StandByNotifMode.COLORS, locked = true, lockScreenShowNotifs = false)
        assertEquals(0, lockedHidden.totalCount)
        assertTrue(lockedHidden.dotColors.isEmpty())
        assertEquals(null, lockedHidden.newestGlowKey)
    }

    @Test fun `shouldTriggerStandByGlow enforces 10 second cooldown, Reduce Motion suppression, and DND silence`() {
        // Initial glow allowed when COLORS, !reduceMotion, !silenced
        assertTrue(
            shouldTriggerStandByGlow(
                mode = StandByNotifMode.COLORS,
                reduceMotion = false,
                silenced = false,
                nowElapsedMs = 5_000L,
                lastGlowElapsedMs = -1L,
            )
        )

        // Second notification 4 seconds later -> blocked by MIN_GLOW_INTERVAL_MS (10_000L)
        assertFalse(
            shouldTriggerStandByGlow(
                mode = StandByNotifMode.COLORS,
                reduceMotion = false,
                silenced = false,
                nowElapsedMs = 9_000L,
                lastGlowElapsedMs = 5_000L,
            )
        )

        // Notification at 15_000L (exactly 10_000ms after 5_000L) -> allowed
        assertTrue(
            shouldTriggerStandByGlow(
                mode = StandByNotifMode.COLORS,
                reduceMotion = false,
                silenced = false,
                nowElapsedMs = 15_000L,
                lastGlowElapsedMs = 5_000L,
            )
        )

        // Reduce Motion enabled -> never glows
        assertFalse(
            shouldTriggerStandByGlow(
                mode = StandByNotifMode.COLORS,
                reduceMotion = true,
                silenced = false,
                nowElapsedMs = 30_000L,
                lastGlowElapsedMs = -1L,
            )
        )

        // Do Not Disturb / silenced notification -> never glows
        assertFalse(
            shouldTriggerStandByGlow(
                mode = StandByNotifMode.COLORS,
                reduceMotion = false,
                silenced = true,
                nowElapsedMs = 30_000L,
                lastGlowElapsedMs = -1L,
            )
        )

        // COUNT_ONLY or OFF mode -> never glows
        assertFalse(
            shouldTriggerStandByGlow(
                mode = StandByNotifMode.COUNT_ONLY,
                reduceMotion = false,
                silenced = false,
                nowElapsedMs = 30_000L,
                lastGlowElapsedMs = -1L,
            )
        )
    }

    // --- 8. Phase 5: Room light hysteresis tracker & resolveStandByNight ---

    @Test fun `RoomLightTracker enters night after 2s below 0_4 lux and ignores brief passing shadows`() {
        val tracker = RoomLightTracker(initialNight = false)
        assertFalse(tracker.onReading(lux = 5.0f, timeMs = 0L))

        // Brief shadow (1.5s at 0.2 lux, then back to 2.0 lux) does NOT flip to night
        assertFalse(tracker.onReading(lux = 0.2f, timeMs = 1_000L))
        assertFalse(tracker.onReading(lux = 0.2f, timeMs = 2_500L))
        assertFalse(tracker.onReading(lux = 2.0f, timeMs = 2_800L))

        // Sustained dark room (<= 0.4 lux for >= 2,000 ms) flips to night
        assertFalse(tracker.onReading(lux = 0.3f, timeMs = 5_000L))
        assertFalse(tracker.onReading(lux = 0.3f, timeMs = 6_999L))
        assertTrue(tracker.onReading(lux = 0.3f, timeMs = 7_000L))
    }

    @Test fun `RoomLightTracker exits night after 10s above 0_8 lux and ignores brief lamp flashes or deadband`() {
        val tracker = RoomLightTracker(initialNight = true)
        // Deadband (0.6 lux is between 0.4 and 0.8) keeps night
        assertTrue(tracker.onReading(lux = 0.6f, timeMs = 1_000L))
        assertTrue(tracker.onReading(lux = 0.6f, timeMs = 20_000L))

        // Brief lamp flash (5s at 15 lux, then back to 0.5 lux) does NOT exit night
        assertTrue(tracker.onReading(lux = 15.0f, timeMs = 30_000L))
        assertTrue(tracker.onReading(lux = 15.0f, timeMs = 35_000L))
        assertTrue(tracker.onReading(lux = 0.5f, timeMs = 36_000L))

        // Sustained room light (>= 0.8 lux for >= 10,000 ms) exits night
        assertTrue(tracker.onReading(lux = 1.2f, timeMs = 40_000L))
        assertTrue(tracker.onReading(lux = 1.2f, timeMs = 49_999L))
        assertFalse(tracker.onReading(lux = 1.2f, timeMs = 50_000L))
    }

    @Test fun `resolveStandByNight honors SCHEDULE, ROOM_LIGHT with sensor fallback, and NEVER`() {
        assertEquals(true, resolveStandByNight(StandByNightMode.SCHEDULE, scheduleNight = true, roomDark = false))
        assertEquals(false, resolveStandByNight(StandByNightMode.SCHEDULE, scheduleNight = false, roomDark = true))

        assertEquals(true, resolveStandByNight(StandByNightMode.ROOM_LIGHT, scheduleNight = false, roomDark = true))
        assertEquals(false, resolveStandByNight(StandByNightMode.ROOM_LIGHT, scheduleNight = true, roomDark = false))
        assertEquals(true, resolveStandByNight(StandByNightMode.ROOM_LIGHT, scheduleNight = true, roomDark = null))

        assertEquals(false, resolveStandByNight(StandByNightMode.NEVER, scheduleNight = true, roomDark = true))
    }
}
