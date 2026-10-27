package com.mccal.folio

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * AirPods battery from the AirPods' own Bluetooth advertisement. The fixtures are built from the published byte layout
 * (furiousMAC/continuity proximity_pairing.md) and the bit rules in docs/airpods-ble.md, not captured from real AirPods: none
 * were available. When the owner copies a real report, check the decoded values against what his AirPods show.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AirPodsBleTest {
    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    /** A proximity-pairing message as Android hands it over: the manufacturer data after the company id. */
    private fun adv(
        status: Int = 0x2B, battery: Int = 0x87, chargingCase: Int = 0x34, lid: Int = 0x31, model: Int = 0x0E20,
        type: Int = 0x07, length: Int = 0x19, size: Int = 27,
    ): ByteArray {
        val head = intArrayOf(type, length, 0x01, model shr 8, model and 0xFF, status, battery, chargingCase, lid, 0x00, 0x00)
        return ByteArray(size) { i -> if (i < head.size) head[i].toByte() else 0x5A }
    }

    // Decoder

    @Test fun `every value, with the status saying the nibbles are as published`() {
        val a = ProximityDecoder.decode(adv())!!
        assertEquals(0x0E20, a.model)
        assertEquals("AirPods Pro", ProximityDecoder.modelName(a.model))
        assertEquals(70, a.left); assertEquals(80, a.right); assertEquals(40, a.case)
        assertTrue(a.leftCharging); assertTrue(a.rightCharging); assertFalse(a.caseCharging)
        assertTrue(a.leftInEar); assertTrue(a.rightInEar)
        assertTrue("lid bit 0x08 clear", a.lidOpen)
        assertTrue(a.hasNumbers)
    }

    @Test fun `the other orientation swaps left and right and their flags`() {
        val a = ProximityDecoder.decode(adv(status = 0x02, battery = 0x87, chargingCase = 0x14))!!
        assertEquals(80, a.left); assertEquals(70, a.right)
        assertFalse(a.leftCharging); assertTrue(a.rightCharging)
        assertFalse(a.leftInEar); assertTrue(a.rightInEar)
    }

    @Test fun `one bud in the case, the other unknown`() {
        val a = ProximityDecoder.decode(adv(battery = 0xF9, chargingCase = 0x56, lid = 0x39))!!
        assertEquals(90, a.left); assertNull(a.right); assertEquals(60, a.case)
        assertTrue(a.leftCharging); assertTrue(a.caseCharging)
        assertFalse(a.lidOpen)
        val b = a.toBattery(1_000L)
        assertEquals(true, b.leftCharging); assertNull("no level, no charging claim", b.rightCharging)
        assertEquals(AirPodsBattery.Source.BLE_ADVERTISEMENT, b.source); assertEquals(1_000L, b.readAtMillis)
    }

    @Test fun `case only`() {
        val a = ProximityDecoder.decode(adv(battery = 0xFF, chargingCase = 0x05))!!
        assertNull(a.left); assertNull(a.right); assertEquals(50, a.case); assertTrue(a.hasNumbers)
    }

    @Test fun `the unknown nibble everywhere means no numbers, and 11 to 14 are unknown too`() {
        val a = ProximityDecoder.decode(adv(battery = 0xFF, chargingCase = 0x0F))!!
        assertFalse(a.hasNumbers)
        assertEquals(100, ProximityDecoder.level(10)); assertEquals(0, ProximityDecoder.level(0))
        for (n in 11..15) assertNull(ProximityDecoder.level(n))
        assertNull(ProximityDecoder.decode(adv(battery = 0xBC, chargingCase = 0x0E))!!.left)
    }

    @Test fun `wrong type, wrong length, short, empty and null are rejected`() {
        assertNull(ProximityDecoder.decode(adv(type = 0x10)))
        assertNull(ProximityDecoder.decode(adv(type = 0x0F)))
        assertNull(ProximityDecoder.decode(adv(length = 0x18)))
        assertNull(ProximityDecoder.decode(adv(size = 26)))
        assertNull(ProximityDecoder.decode(adv().copyOf(11)))
        assertNull(ProximityDecoder.decode(ByteArray(0)))
        assertNull(ProximityDecoder.decode(null))
        assertNotNull("trailing bytes after the message are ignored", ProximityDecoder.decode(adv(size = 31)))
    }

    @Test fun `garbage never throws`() {
        val r = java.util.Random(7)
        repeat(20_000) {
            val b = ByteArray(r.nextInt(40)).also(r::nextBytes)
            if (it % 3 == 0 && b.size >= 2) { b[0] = 0x07; b[1] = 0x19 }
            ProximityDecoder.decode(b)
        }
    }

    @Test fun `an unknown model still decodes and is named by its id`() {
        val a = ProximityDecoder.decode(adv(model = 0x7720))!!
        assertEquals("Apple model 0x7720", ProximityDecoder.modelName(a.model))
    }

    // Choosing the person's own AirPods

    private val mine = ProximityDecoder.decode(adv())!!
    private val stranger = ProximityDecoder.decode(adv(battery = 0x33, chargingCase = 0x01, model = 0x1420))!!

    @Test fun `a single strong one held twice is chosen`() {
        val s = AdvertSelector()
        s.offer(1, -45, mine); assertEquals(AdvertSelector.Choice.None, s.choice())
        s.offer(1, -47, mine)
        assertEquals(AdvertSelector.Choice.Mine(mine), s.choice())
    }

    @Test fun `weak strangers are dropped and never chosen`() {
        val s = AdvertSelector()
        repeat(5) { s.offer(10 + it, -75, stranger); s.offer(10 + it, -70, stranger) }
        assertEquals(AdvertSelector.Choice.None, s.choice())
        s.offer(1, -50, mine); s.offer(1, -52, mine)
        repeat(5) { s.offer(10 + it, -80, stranger) }
        assertEquals(AdvertSelector.Choice.Mine(mine), s.choice())
    }

    @Test fun `a close stranger makes it refuse to guess`() {
        val s = AdvertSelector()
        s.offer(1, -50, mine); s.offer(2, -52, stranger); s.offer(1, -50, mine); s.offer(2, -51, stranger)
        assertEquals(AdvertSelector.Choice.Several, s.choice())
    }

    @Test fun `a clearly weaker stranger does not stop the choice`() {
        val s = AdvertSelector()
        s.offer(2, -58, stranger); s.offer(1, -42, mine); s.offer(1, -44, mine); s.offer(2, -59, stranger)
        assertEquals(AdvertSelector.Choice.Mine(mine), s.choice())
    }

    @Test fun `both buds of one pair are not a tie`() {
        val otherBud = ProximityDecoder.decode(adv(status = 0x02, battery = 0x78))!! // same pair, seen from the other bud
        val s = AdvertSelector()
        s.offer(1, -48, mine); s.offer(3, -49, otherBud); s.offer(1, -48, mine)
        assertEquals(AdvertSelector.Choice.Mine(mine), s.choice())
    }

    @Test fun `a strong one seen once, or one that fades, is not chosen`() {
        val once = AdvertSelector().apply { offer(1, -40, mine) }
        assertEquals(AdvertSelector.Choice.None, once.choice())
        val faded = AdvertSelector().apply { offer(1, -40, mine); offer(1, -41, mine); offer(1, -72, mine) }
        assertEquals(AdvertSelector.Choice.None, faded.choice())
        val blip = AdvertSelector().apply { offer(2, -40, stranger); offer(1, -50, mine); offer(1, -50, mine) }
        assertEquals("a stronger blip seen once leaves it unsure", AdvertSelector.Choice.Several, blip.choice())
    }

    @Test fun `exactly at the near threshold counts, just under does not`() {
        val at = AdvertSelector().apply { offer(1, AdvertSelector.NEAR_DBM, mine); offer(1, AdvertSelector.NEAR_DBM, mine) }
        assertEquals(AdvertSelector.Choice.Mine(mine), at.choice())
        val under = AdvertSelector().apply { offer(1, AdvertSelector.NEAR_DBM - 1, mine); offer(1, AdvertSelector.NEAR_DBM - 1, mine) }
        assertEquals(AdvertSelector.Choice.None, under.choice())
    }

    // Scan lifecycle with a fake scanner

    private class FakeScanner(var accept: Boolean = true) : AdvertScanner {
        var starts = 0; var stops = 0
        var onResult: ((Int, Int, ByteArray?) -> Unit)? = null
        var onFailed: (() -> Unit)? = null
        override fun start(onResult: (Int, Int, ByteArray?) -> Unit, onFailed: () -> Unit): Boolean {
            starts++; this.onResult = onResult; this.onFailed = onFailed; return accept
        }
        override fun stop() { stops++ }
        fun hear(key: Int, rssi: Int, data: ByteArray?) = onResult!!(key, rssi, data)
    }

    private class Clock { var now = 1_000_000L }

    private class FakeTicker(val clock: Clock) : BleTicker {
        private class Task(val at: Long, val block: () -> Unit) { var cancelled = false }
        private val tasks = ArrayList<Task>()
        override fun after(ms: Long, block: () -> Unit): Cancel { val t = Task(clock.now + ms, block); tasks += t; return Cancel { t.cancelled = true } }
        val pending get() = tasks.count { !it.cancelled }
        fun advance(ms: Long) {
            val end = clock.now + ms
            while (true) {
                val next = tasks.filter { !it.cancelled && it.at <= end }.minByOrNull { it.at } ?: break
                tasks -= next; clock.now = next.at; next.block()
            }
            clock.now = end
        }
    }

    private val all = BleConditions(gateOpen = true, switchOn = true, permission = true, bluetoothOn = true, screenOn = true, appleConnected = true)

    private inner class Rig(var cond: BleConditions = all, accept: Boolean = true) {
        val clock = Clock()
        val scanner = FakeScanner(accept)
        val ticker = FakeTicker(clock)
        val c = AirPodsBleController(scanner, ticker, { cond }, { clock.now }, { clock.now + 5_000_000L }, MutableStateFlow(AirPodsBleRead.NotYet))
        fun hearMine() { scanner.hear(1, -45, adv()); scanner.hear(1, -46, adv()) }
    }

    @Test fun `nothing scans while the gate, switch, permission, Bluetooth, AirPods or screen say no`() {
        val cases = mapOf(
            all.copy(gateOpen = false) to AirPodsBleRead.Why.GATE_CLOSED,
            all.copy(switchOn = false) to AirPodsBleRead.Why.SWITCH_OFF,
            all.copy(permission = false) to AirPodsBleRead.Why.NO_PERMISSION,
            all.copy(bluetoothOn = false) to AirPodsBleRead.Why.BLUETOOTH_OFF,
            all.copy(appleConnected = false) to AirPodsBleRead.Why.NO_AIRPODS,
            all.copy(screenOn = false) to AirPodsBleRead.Why.SCREEN_OFF,
        )
        for ((cond, why) in cases) {
            val r = Rig(cond)
            r.c.setVisible(BleSurface.HOME_CARD, true)
            assertFalse(r.c.refresh())
            assertEquals(0, r.scanner.starts)
            assertEquals(AirPodsBleRead.Blocked(why), r.c.state.value)
            assertEquals(0, r.ticker.pending)
        }
    }

    @Test fun `nothing scans while no card is on screen, even on Refresh`() {
        val r = Rig()
        assertFalse(r.c.refresh())
        assertEquals(0, r.scanner.starts)
    }

    @Test fun `a card appearing starts one short scan that stops itself and keeps only the answer`() {
        val r = Rig()
        r.c.setVisible(BleSurface.HOME_CARD, true)
        assertEquals(1, r.scanner.starts); assertEquals(AirPodsBleRead.Scanning, r.c.state.value)
        r.hearMine()
        r.scanner.hear(9, -80, adv(battery = 0x11)) // a stranger far away
        r.ticker.advance(AirPodsBleController.WINDOW_MS - 1)
        assertTrue(r.c.scanning)
        r.ticker.advance(1)
        assertFalse(r.c.scanning); assertEquals(1, r.scanner.stops)
        val found = r.c.state.value as AirPodsBleRead.Found
        assertEquals(70, found.advert.left); assertEquals(80, found.advert.right)
        assertFalse("nothing from the scan is held", r.c.holdsScanData)
        assertEquals(0, r.ticker.pending)
    }

    @Test fun `a second surface does not start a second scan`() {
        val r = Rig()
        r.c.setVisible(BleSurface.HOME_CARD, true); r.c.setVisible(BleSurface.BRIDGE, true)
        assertEquals(1, r.scanner.starts)
    }

    @Test fun `hiding the last card stops at once and keeps the last answer`() {
        val r = Rig()
        r.c.setVisible(BleSurface.SETTINGS, true); r.c.setVisible(BleSurface.HOME_CARD, true)
        r.c.setVisible(BleSurface.HOME_CARD, false)
        assertTrue("another card still shows", r.c.scanning)
        r.c.setVisible(BleSurface.SETTINGS, false)
        assertFalse(r.c.scanning); assertEquals(1, r.scanner.stops)
        assertEquals(AirPodsBleRead.NotYet, r.c.state.value)
        assertFalse(r.c.holdsScanData); assertEquals(0, r.ticker.pending)
    }

    @Test fun `each stop condition ends a running scan within one check`() {
        val stops = listOf<(BleConditions) -> BleConditions>(
            { it.copy(switchOn = false) }, { it.copy(permission = false) }, { it.copy(bluetoothOn = false) },
            { it.copy(screenOn = false) }, { it.copy(appleConnected = false) }, { it.copy(gateOpen = false) },
        )
        for (change in stops) {
            val r = Rig()
            r.c.setVisible(BleSurface.SETTINGS, true)
            r.hearMine()
            r.cond = change(all)
            r.ticker.advance(AirPodsBleController.CHECK_MS)
            assertFalse(r.c.scanning); assertEquals(1, r.scanner.stops)
            assertFalse(r.c.holdsScanData); assertEquals(0, r.ticker.pending)
            val why = r.cond.blocked()!!
            if (why == AirPodsBleRead.Why.SCREEN_OFF) assertEquals(AirPodsBleRead.NotYet, r.c.state.value)
            else assertEquals(AirPodsBleRead.Blocked(why), r.c.state.value)
        }
    }

    @Test fun `turning the switch off stops at once through a check`() {
        val r = Rig()
        r.c.setVisible(BleSurface.SETTINGS, true)
        r.cond = all.copy(switchOn = false)
        r.c.check()
        assertFalse(r.c.scanning); assertEquals(AirPodsBleRead.Blocked(AirPodsBleRead.Why.SWITCH_OFF), r.c.state.value)
    }

    @Test fun `a late result after a stop is dropped unread`() {
        val r = Rig()
        r.c.setVisible(BleSurface.SETTINGS, true)
        r.c.setVisible(BleSurface.SETTINGS, false)
        r.hearMine()
        assertEquals(AirPodsBleRead.NotYet, r.c.state.value); assertFalse(r.c.holdsScanData)
    }

    @Test fun `scans are at least the minimum interval apart`() {
        val r = Rig()
        r.c.setVisible(BleSurface.SETTINGS, true)
        r.ticker.advance(AirPodsBleController.WINDOW_MS)
        assertTrue(r.c.state.value is AirPodsBleRead.NothingSeen)
        r.ticker.advance(10_000)
        assertFalse(r.c.refresh()); assertEquals(1, r.scanner.starts)
        r.c.setVisible(BleSurface.SETTINGS, false); r.c.setVisible(BleSurface.SETTINGS, true)
        assertEquals("showing again inside the interval does not scan", 1, r.scanner.starts)
        assertTrue("the last answer stays", r.c.state.value is AirPodsBleRead.NothingSeen)
        r.ticker.advance(AirPodsBleController.MIN_INTERVAL_MS - AirPodsBleController.WINDOW_MS - 10_000)
        assertTrue(r.c.refresh()); assertEquals(2, r.scanner.starts)
    }

    @Test fun `an early stop still counts toward the interval`() {
        val r = Rig()
        r.c.setVisible(BleSurface.HOME_CARD, true); r.c.setVisible(BleSurface.HOME_CARD, false)
        r.c.setVisible(BleSurface.HOME_CARD, true)
        assertEquals(1, r.scanner.starts)
    }

    @Test fun `strangers alone, or two equally near, give words and no numbers`() {
        val alone = Rig()
        alone.c.setVisible(BleSurface.SETTINGS, true)
        repeat(4) { alone.scanner.hear(5 + it, -78, adv(battery = 0x22)) }
        alone.ticker.advance(AirPodsBleController.WINDOW_MS)
        assertTrue(alone.c.state.value is AirPodsBleRead.NothingSeen)
        val two = Rig()
        two.c.setVisible(BleSurface.SETTINGS, true)
        two.hearMine(); two.scanner.hear(2, -46, adv(battery = 0x33, model = 0x1420)); two.scanner.hear(2, -45, adv(battery = 0x33, model = 0x1420))
        two.ticker.advance(AirPodsBleController.WINDOW_MS)
        assertTrue(two.c.state.value is AirPodsBleRead.Several)
    }

    @Test fun `AirPods that send only unknown values say so`() {
        val r = Rig()
        r.c.setVisible(BleSurface.SETTINGS, true)
        r.scanner.hear(1, -40, adv(battery = 0xFF, chargingCase = 0x0F)); r.scanner.hear(1, -40, adv(battery = 0xFF, chargingCase = 0x0F))
        r.ticker.advance(AirPodsBleController.WINDOW_MS)
        assertTrue(r.c.state.value is AirPodsBleRead.NoNumbers)
    }

    @Test fun `a scan Android refuses, or that fails, is Failed and holds nothing`() {
        val refused = Rig(accept = false)
        refused.c.setVisible(BleSurface.SETTINGS, true)
        assertTrue(refused.c.state.value is AirPodsBleRead.Failed)
        assertFalse(refused.c.holdsScanData); assertEquals(0, refused.ticker.pending)
        val failed = Rig()
        failed.c.setVisible(BleSurface.SETTINGS, true)
        failed.scanner.onFailed!!()
        assertTrue(failed.c.state.value is AirPodsBleRead.Failed)
        assertEquals(1, failed.scanner.stops); assertFalse(failed.c.holdsScanData); assertEquals(0, failed.ticker.pending)
    }

    @Test fun `once allowed again, a check starts the scan`() {
        val r = Rig(all.copy(permission = false))
        r.c.setVisible(BleSurface.SETTINGS, true)
        assertEquals(AirPodsBleRead.Blocked(AirPodsBleRead.Why.NO_PERMISSION), r.c.state.value)
        r.cond = all
        r.c.check()
        assertEquals(1, r.scanner.starts); assertEquals(AirPodsBleRead.Scanning, r.c.state.value)
    }

    // Freshness and which reading wins

    @Test fun `fresh, then with its age, then gone after ten minutes`() {
        val now = 100_000_000L
        assertEquals(AirPodsFreshness.Age.Fresh, AirPodsFreshness.of(now - 60_000, now))
        assertEquals(AirPodsFreshness.Age.Minutes(2), AirPodsFreshness.of(now - AirPodsFreshness.AGE_SHOWN_MS, now))
        assertEquals(AirPodsFreshness.Age.Minutes(10), AirPodsFreshness.of(now - AirPodsFreshness.DROP_MS, now))
        assertEquals(AirPodsFreshness.Age.Expired, AirPodsFreshness.of(now - AirPodsFreshness.DROP_MS - 1, now))
        assertEquals("a clock that jumped back", AirPodsFreshness.Age.Expired, AirPodsFreshness.of(now + 120_000, now))
    }

    @Test fun `the advertisement wins over the root reading unless it has expired`() {
        val now = 100_000_000L
        val ble = AirPodsBattery(70, 80, 40, true, false, null, null, now - 30_000, AirPodsBattery.Source.BLE_ADVERTISEMENT)
        val root = AirPodsBattery(60, 60, 30, null, null, null, null, now - 10_000, AirPodsBattery.Source.SYSTEM_METADATA)
        assertEquals(ble, AirPodsFreshness.best(ble, root, now))
        assertEquals(root, AirPodsFreshness.best(ble.copy(readAtMillis = now - 11 * 60_000), root, now))
        assertEquals(root, AirPodsFreshness.best(null, root, now))
        assertNull(AirPodsFreshness.best(null, root.copy(readAtMillis = now - 11 * 60_000), now))
    }

    // Words

    @Test fun `every state has words, and numbers replace them only while current`() {
        val now = 100_000_000L
        assertEquals(R.string.airpods_not_read, AirPodsBleText.summary(AirPodsBleRead.NotYet, now))
        assertEquals(R.string.airpods_ble_scanning, AirPodsBleText.summary(AirPodsBleRead.Scanning, now))
        assertEquals(R.string.airpods_ble_off, AirPodsBleText.summary(AirPodsBleRead.Blocked(AirPodsBleRead.Why.SWITCH_OFF), now))
        assertEquals(R.string.airpods_ble_needs_permission, AirPodsBleText.summary(AirPodsBleRead.Blocked(AirPodsBleRead.Why.NO_PERMISSION), now))
        assertEquals(R.string.airpods_ble_bluetooth_off, AirPodsBleText.summary(AirPodsBleRead.Blocked(AirPodsBleRead.Why.BLUETOOTH_OFF), now))
        assertEquals(R.string.airpods_ble_no_airpods, AirPodsBleText.summary(AirPodsBleRead.Blocked(AirPodsBleRead.Why.NO_AIRPODS), now))
        assertEquals(R.string.airpods_ble_nothing_seen, AirPodsBleText.summary(AirPodsBleRead.NothingSeen(now), now))
        assertEquals(R.string.airpods_ble_several, AirPodsBleText.summary(AirPodsBleRead.Several(now), now))
        assertEquals(R.string.airpods_ble_no_numbers, AirPodsBleText.summary(AirPodsBleRead.NoNumbers(now), now))
        assertEquals(R.string.airpods_ble_failed, AirPodsBleText.summary(AirPodsBleRead.Failed(now), now))
        assertNull(AirPodsBleText.summary(AirPodsBleRead.Found(mine, now), now))
        assertEquals(R.string.airpods_ble_expired, AirPodsBleText.summary(AirPodsBleRead.Found(mine, now - 11 * 60_000), now))
        assertEquals(R.string.airpods_source_ble, AirPodsBleText.sourceLabel(AirPodsBattery.Source.BLE_ADVERTISEMENT))
        assertEquals(R.string.airpods_source_root, AirPodsBleText.sourceLabel(AirPodsBattery.Source.SYSTEM_METADATA))
    }

    @Test fun `the line shows the age after a few minutes and nothing once expired`() {
        val now = 100_000_000L
        val b = mine.toBattery(now - 3 * 60_000)
        assertEquals("Left 70%, charging · Right 80%, charging · Case 40% (3 min ago)", AirPodsBleText.line(context, b, now))
        assertEquals("Left 70%, charging · Right 80%, charging · Case 40%", AirPodsBleText.line(context, b, b.readAtMillis))
        assertNull(AirPodsBleText.line(context, b, now + AirPodsFreshness.DROP_MS))
    }

    @Test fun `the report holds decoded values and times, never addresses or raw bytes`() {
        val now = 100_000_000L
        val text = AirPodsBleText.report(AirPodsBleRead.Found(mine, now - 4_000), now, listOf("Folio AirPods report"))
        assertTrue(text.contains("Left: 70%")); assertTrue(text.contains("Right: 80%")); assertTrue(text.contains("Case: 40%"))
        assertTrue(text.contains("Read: 4 s ago")); assertTrue(text.contains("AirPods Pro (0x0E20)"))
        assertFalse("no address", AirPodsDumpParser.address.containsMatchIn(text))
        assertFalse("no raw bytes", Regex("(?i)\\b(?:[0-9a-f]{2}[ :]?){6,}").containsMatchIn(text))
        assertFalse(text.contains("0719")); assertFalse(text.contains("5A5A"))
        val nothing = AirPodsBleText.report(AirPodsBleRead.Several(now), now, emptyList())
        assertTrue(nothing.contains("several")); assertFalse(nothing.contains("Left"))
    }

    // Gate, names, strings and the manifest

    @Test fun `the gate is closed for everyone else until 0_7_0`() {
        val gate = FeatureGate.AIRPODS_BLE
        assertEquals("2026-10-27", gate.closedSince); assertEquals("0.7.0", gate.opensIn); assertFalse(gate.open)
    }

    @Test fun `only AirPods and Beats names count as Apple headphones`() {
        assertTrue(AirPodsBle.isAppleName("McCal's AirPods Pro")); assertTrue(AirPodsBle.isAppleName("Powerbeats Pro")); assertTrue(AirPodsBle.isAppleName("airpods"))
        assertFalse(AirPodsBle.isAppleName("Galaxy Buds3 Pro")); assertFalse(AirPodsBle.isAppleName("")); assertFalse(AirPodsBle.isAppleName(null))
    }

    @Test fun `the switch is off until the person turns it on`() {
        context.getSharedPreferences("airpods_ble", android.content.Context.MODE_PRIVATE).edit().clear().commit()
        assertFalse(AirPodsBle.switchOn(context))
    }

    private val root = generateSequence(java.io.File("").absoluteFile) { it.parentFile }.first { java.io.File(it, "CHANGELOG.md").exists() }

    @Test fun `every new string is in English, Korean and Simplified Chinese`() {
        val names = listOf("airpods_ble_switch", "airpods_ble_note", "airpods_ble_permission_note", "airpods_ble_allow", "airpods_ble_open_settings",
            "airpods_ble_scanning", "airpods_ble_off", "airpods_ble_needs_permission", "airpods_ble_bluetooth_off", "airpods_ble_no_airpods",
            "airpods_ble_nothing_seen", "airpods_ble_several", "airpods_ble_no_numbers", "airpods_ble_failed", "airpods_ble_expired",
            "airpods_ble_report_note", "airpods_with_age", "airpods_source", "airpods_source_ble", "airpods_source_root", "nearby_devices")
        for (dir in listOf("values", "values-b+ko", "values-b+zh+Hans")) {
            val xml = java.io.File(root, "app/src/main/res/$dir/strings.xml").readText()
            for (n in names) assertTrue("$n missing in $dir", Regex("""<string name="$n"[ >]""").containsMatchIn(xml))
            assertTrue("$dir keeps both placeholders", Regex("name=\"airpods_with_age\">[^<]*%1\\\$s[^<]*%2\\\$d").containsMatchIn(xml))
        }
    }

    @Test fun `the scan permission is never for location, and nothing else Bluetooth or location is added`() {
        val manifest = java.io.File(root, "app/src/main/AndroidManifest.xml").readText()
        val scan = Regex("""<uses-permission[^>]*android\.permission\.BLUETOOTH_SCAN[^>]*>""").find(manifest)?.value
        assertNotNull("BLUETOOTH_SCAN is declared", scan)
        assertTrue(scan!!.contains("""android:usesPermissionFlags="neverForLocation""""))
        for (p in listOf("ACCESS_FINE_LOCATION", "ACCESS_BACKGROUND_LOCATION", "BLUETOOTH_CONNECT", "BLUETOOTH_ADMIN", "FOREGROUND_SERVICE_CONNECTED_DEVICE"))
            assertFalse("$p is not needed", manifest.contains("android.permission.$p\""))
        assertTrue(manifest.contains("""<uses-feature android:name="android.hardware.bluetooth_le" android:required="false" />"""))
    }
}
