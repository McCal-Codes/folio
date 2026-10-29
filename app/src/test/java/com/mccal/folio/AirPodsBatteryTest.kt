package com.mccal.folio

import androidx.test.core.app.ApplicationProvider
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * AirPods battery from the system. The fixtures are written from AOSP's dump code (AdapterProperties.dump, DatabaseManager.dump,
 * Metadata.toString, CustomizedMetadataEntity.toString, HeadsetStateMachine's log lines), not captured from a phone: no real dump was
 * available. When the owner pastes a real one, add it here as a fixture first.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AirPodsBatteryTest {
    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    /** AdapterProperties' bonded list: anonymized address, device type, class of device, name. */
    private val BONDED = listOf(
        "    XX:XX:XX:XX:A1:B2 [ BR/EDR ][ 0x240418 ] McCal's AirPods Pro",
        "    XX:XX:XX:XX:C3:D4 [ DUAL ][ 0x240404 ] Galaxy Buds3 Pro",
        "    XX:XX:XX:XX:E5:F6 [ BR/EDR ][ 0x200404 ] Car Kit",
    )

    private fun meta(addr: String, active: Boolean, fields: Map<String, String>): String {
        val all = listOf("manufacturer_name", "model_name", "software_version", "hardware_version", "companion_app", "main_icon", "is_untethered_headset",
            "untethered_left_icon", "untethered_right_icon", "untethered_case_icon", "untethered_left_battery", "untethered_right_battery",
            "untethered_case_battery", "untethered_left_charging", "untethered_right_charging", "untethered_case_charging", "enhanced_settings_ui_uri",
            "device_type", "main_battery", "main_charging", "main_low_battery_threshold", "untethered_left_low_battery_threshold",
            "untethered_right_low_battery_threshold", "untethered_case_low_battery_threshold", "spatial_audio", "fastpair_customized", "le_audio",
            "gmcs_cccd", "gtbs_cccd", "exclusive_manager")
        val custom = all.joinToString("|") { "$it=${fields[it] ?: "null"}" }
        return "  $addr last_active_time=${if (active) 42 else 7} {profile connection policy(a2dp=100|hfp=100), optional codec(support=-1|enabled=-1), " +
            "isActiveHfpDevice ($active), custom metadata($custom), hfp client audio policy(null), is_preferred_microphone_for_calls(true)}"
    }

    private fun dump(metadata: List<String>, bonded: List<String> = BONDED, hfp: List<String> = emptyList()) = buildString {
        appendLine("Bluetooth Status"); appendLine("  enabled: true"); appendLine("  state: ON")
        appendLine("  address: XX:XX:XX:XX:11:22"); appendLine("  name: McCal's Fold8"); appendLine("  time since enabled: 01:02:03.004")
        appendLine(); appendLine("BluetoothManagerService:"); appendLine("  mEnable:true"); appendLine()
        appendLine("🚩Flag dump:"); appendLine("\t[■]: com.android.bluetooth.flags.leaudio_battery_level_report")
        appendLine(); appendLine("AdapterProperties"); appendLine("  Name: McCal's Fold8"); appendLine("  Address: XX:XX:XX:XX:11:22")
        appendLine("  ConnectionState: STATE_CONNECTED"); appendLine("  State: ON"); appendLine("  Bonded devices:")
        bonded.forEach(::appendLine); appendLine()
        appendLine("ScanMode: SCAN_MODE_CONNECTABLE"); appendLine()
        appendLine("Metadata:"); metadata.forEach(::appendLine)
        appendLine("Profile: HeadsetService"); appendLine("  mCurrentDevice: XX:XX:XX:XX:A1:B2"); appendLine("  StateMachineLog:")
        hfp.forEach(::appendLine)
    }

    private val airPods = "XX:XX:XX:XX:A1:B2"
    private val all = mapOf("is_untethered_headset" to "true", "untethered_left_battery" to "80", "untethered_right_battery" to "70",
        "untethered_case_battery" to "40", "untethered_left_charging" to "false", "untethered_right_charging" to "false", "untethered_case_charging" to "true")

    private fun found(r: AirPodsReading): AirPodsBattery = (r as? AirPodsReading.Found)?.battery ?: error("expected numbers, got $r")

    // Parser

    @Test fun `AirPods connected with every value`() {
        val b = found(AirPodsDumpParser.parse(dump(listOf(meta(airPods, true, all), meta("XX:XX:XX:XX:C3:D4", false, mapOf("untethered_left_battery" to "20")))), 1_000L))
        assertEquals(80, b.left); assertEquals(70, b.right); assertEquals(40, b.case)
        assertEquals(false, b.leftCharging); assertEquals(false, b.rightCharging); assertEquals(true, b.caseCharging)
        assertEquals(AirPodsBattery.Source.SYSTEM_METADATA, b.source); assertEquals(1_000L, b.readAtMillis)
    }

    @Test fun `one bud in the case`() {
        val b = found(AirPodsDumpParser.parse(dump(listOf(meta(airPods, true, mapOf("untethered_left_battery" to "55", "untethered_left_charging" to "false",
            "untethered_right_battery" to "100", "untethered_right_charging" to "true", "untethered_case_battery" to "35")))), 0L))
        assertEquals(listOf(AirPodsText.Shown(AirPodsText.Part.LEFT, 55, false), AirPodsText.Shown(AirPodsText.Part.RIGHT, 100, true),
            AirPodsText.Shown(AirPodsText.Part.CASE, 35, false)), AirPodsText.parts(b))
    }

    @Test fun `case only, and an unknown level of -1 is no number`() {
        val b = found(AirPodsDumpParser.parse(dump(listOf(meta(airPods, true, mapOf("untethered_left_battery" to "-1", "untethered_case_battery" to "60")))), 0L))
        assertNull(b.left); assertNull(b.right); assertEquals(60, b.case)
        assertEquals(listOf(AirPodsText.Part.CASE), AirPodsText.parts(b).map { it.part })
    }

    @Test fun `no AirPods paired, even with another headset's numbers`() {
        val bonded = listOf("    XX:XX:XX:XX:C3:D4 [ DUAL ][ 0x240404 ] Galaxy Buds3 Pro", "    XX:XX:XX:XX:E5:F6 [ BR/EDR ][ 0x200404 ] Car Kit")
        assertEquals(AirPodsReading.NoAirPods, AirPodsDumpParser.parse(dump(listOf(meta("XX:XX:XX:XX:C3:D4", true, all)), bonded), 0L))
    }

    @Test fun `the connected headset is not AirPods, so nothing is shown even though AirPods are paired`() {
        assertEquals(AirPodsReading.NoAirPods, AirPodsDumpParser.parse(dump(listOf(meta(airPods, false, all))), 0L, preferredName = "Galaxy Buds3 Pro"))
        assertTrue(AirPodsDumpParser.parse(dump(listOf(meta(airPods, false, all))), 0L, preferredName = "McCal's AirPods Pro") is AirPodsReading.Found)
    }

    @Test fun `AirPods paired with no numbers anywhere`() {
        assertEquals(AirPodsReading.NoBattery, AirPodsDumpParser.parse(dump(listOf(meta(airPods, true, emptyMap()))), 0L))
        assertEquals(AirPodsReading.NoBattery, AirPodsDumpParser.parse(dump(emptyList()), 0L))
    }

    @Test fun `garbage and empty answers are unreadable`() {
        listOf("", "su: not found", "Can't find service: bluetooth_manager", "\u0000\u0001 random bytes [ ][ 0x ] ",
            "Bonded devices: none (this line is not the section header)").forEach {
            assertEquals("'$it'", AirPodsReading.Unreadable, AirPodsDumpParser.parse(it, 0L))
        }
    }

    @Test fun `the headset's own call-profile report is the fallback, in 10 percent steps`() {
        val hfp = listOf("     rec[3]: time=10-27 10:00:00.000 processed=Connected org=Connected dest=<null> what=STACK_EVENT: arg1=0, arg2=0, " +
            "obj=EVENT_TYPE_VENDOR_SPECIFIC_AT[13], valInt=0, valInt2=0, valString=+IPHONEACCEV=2,1,7,2,0, valObject=null, device=$airPods")
        val b = found(AirPodsDumpParser.parse(dump(listOf(meta(airPods, true, emptyMap())), hfp = hfp), 0L))
        assertEquals(80, b.overall); assertEquals(AirPodsBattery.Source.HEADSET_REPORT, b.source); assertFalse(b.hasPerBud)
        assertEquals(listOf(AirPodsText.Shown(AirPodsText.Part.OVERALL_ABOUT, 80, false)), AirPodsText.parts(b))
    }

    @Test fun `AT battery arguments follow AOSP's arithmetic and reject nonsense`() {
        assertEquals(80, AirPodsDumpParser.iphoneAccEvLevel("2,1,7,2,0"))
        assertEquals(10, AirPodsDumpParser.iphoneAccEvLevel("1,1,0"))
        assertEquals(100, AirPodsDumpParser.iphoneAccEvLevel("2,2,0,1,9,"))
        listOf("", "0", "1,1,10", "1,2,5", "3,1,5", "x,1,5", "1,1,-1").forEach { assertNull("'$it'", AirPodsDumpParser.iphoneAccEvLevel(it)) }
        assertEquals(60, AirPodsDumpParser.xEventLevel("6,11,461,0"))
        listOf("", "5", "5,1", "12,11", "-1,11").forEach { assertNull("'$it'", AirPodsDumpParser.xEventLevel(it)) }
    }

    @Test fun `a device with two addresses is one device`() {
        val bonded = listOf("    XX:XX:XX:XX:77:88 => XX:XX:XX:XX:A1:B2 [ DUAL ][ 0x240418 ] AirPods Pro")
        // The bonded line names the device's own address first and its classic address second; a line keyed by either is the same device.
        val b = found(AirPodsDumpParser.parse(dump(listOf(meta(airPods, true, all)), bonded), 0L))
        assertEquals(80, b.left)
    }

    @Test fun `of two AirPods, the active one is read`() {
        val bonded = listOf("    XX:XX:XX:XX:A1:B2 [ BR/EDR ][ 0x240418 ] AirPods Pro", "    XX:XX:XX:XX:99:00 [ BR/EDR ][ 0x240418 ] AirPods (old)")
        val b = found(AirPodsDumpParser.parse(dump(listOf(meta("XX:XX:XX:XX:99:00", false, mapOf("untethered_left_battery" to "10")), meta(airPods, true, all)), bonded), 0L))
        assertEquals(80, b.left)
    }

    @Test fun `a huge dump is bounded and still parses when the lines come early enough`() {
        val noise = (1..30_000).joinToString("\n") { "      native: frame $it 0x${it.toString(16)} libbluetooth_jni.so" }
        val early = dump(listOf(meta(airPods, true, all))) + noise
        assertEquals(80, found(AirPodsDumpParser.parse(early, 0L)).left)
        val c = AirPodsDumpParser.collect(early + "\n" + noise + "\n" + noise)
        assertTrue(c.truncated); assertEquals(AirPodsDumpParser.MAX_LINES, c.lines); assertTrue(c.kept.size <= AirPodsDumpParser.MAX_KEPT)
        val long = AirPodsDumpParser.collect(dump(listOf(meta(airPods, true, all) + "x".repeat(1_000_000))))
        assertTrue(long.kept.all { it.length <= AirPodsDumpParser.MAX_LINE_CHARS })
        // Battery numbers past the limit are simply not seen: that is a no-numbers answer, never a wrong one.
        val late = AirPodsDumpParser.parse(dump(emptyList()) + noise + "\n" + noise + "\n" + meta(airPods, true, all), 0L)
        assertEquals(AirPodsReading.NoBattery, late)
    }

    // Report and redaction

    @Test fun `the report replaces addresses, shortens names and drops everything but the battery fields`() {
        val c = AirPodsDumpParser.collect(dump(listOf(meta(airPods, true, all + ("companion_app" to "com.example.pods")))))
        val r = AirPodsDumpParser.report(c, keepNames = false, header = listOf("Folio AirPods report"))
        assertFalse(r, "McCal" in r); assertFalse(r, "A1:B2" in r); assertFalse(r, "11:22" in r); assertFalse(r, Regex("(?:[0-9A-FX]{2}:){5}").containsMatchIn(r))
        assertFalse("the phone's own name line is never kept", "Fold8" in r)
        assertFalse(r, "companion_app" in r); assertFalse(r, "main_icon" in r)
        assertTrue(r, "untethered_left_battery=80" in r); assertTrue(r, "dev1" in r)
        assertTrue(r, AirPodsDumpParser.redactName("McCal's AirPods Pro") in r)
        assertEquals("Mc…(19)", AirPodsDumpParser.redactName("McCal's AirPods Pro"))
        assertEquals("…(2)", AirPodsDumpParser.redactName("Al"))
        assertTrue(r.startsWith("Folio AirPods report\nParser: ${AirPodsDumpParser.FORMAT_VERSION}"))
        val named = AirPodsDumpParser.report(c, keepNames = true, header = emptyList())
        assertTrue(named, "McCal's AirPods Pro" in named); assertFalse(named, "A1:B2" in named)
    }

    @Test fun `the same device keeps its number in every line of a report`() {
        val hfp = listOf("    valString=+IPHONEACCEV=1,1,4, device=$airPods")
        val r = AirPodsDumpParser.report(AirPodsDumpParser.collect(dump(listOf(meta(airPods, true, all)), hfp = hfp)), false, emptyList())
        val bondedLine = r.lines().first { "0x240418" in it }
        val dev = Regex("dev\\d+").find(bondedLine)!!.value
        assertTrue(r.lines().first { "untethered_left_battery" in it }.contains(dev))
        assertTrue(r.lines().first { "IPHONEACCEV" in it }.contains(dev))
    }

    // Reader: the command, the interval, the timeout, and that nothing runs without permission

    private class Fake(lines: List<SuLine>, private val code: Int? = 0, private val error: String = "") : SuProcess {
        private val queue = ArrayDeque(lines)
        var closed = false
        override fun next(timeoutMs: Long): SuLine = queue.removeFirstOrNull() ?: SuLine.Eof
        override fun exitCode() = code
        override fun errorText() = error
        override fun close() { closed = true }
    }

    private fun lines(text: String) = text.lines().map { SuLine.Text(it) }

    @Test fun `a read runs the one fixed command and closes the process`() {
        val started = mutableListOf<List<String>>(); val p = Fake(lines(dump(listOf(meta(airPods, true, all)))))
        val reader = AirPodsBatteryReader({ started += it; p }, monotonicNow = { 0L }, wallNow = { 5L })
        val r = reader.read(allowed = true, su = "su", preferredName = null) as AirPodsRead.Done
        assertEquals(listOf("su", "-c", "dumpsys bluetooth_manager"), started.single())
        assertTrue(p.closed); assertEquals(80, found(r.reading).left); assertEquals(5L, r.atMillis); assertNotNull(reader.lastCollector)
    }

    @Test fun `nothing runs when not allowed or without a known su`() {
        val reader = AirPodsBatteryReader({ throw AssertionError("must not start a process") }, monotonicNow = { 0L }, wallNow = { 0L })
        assertEquals(AirPodsRead.NotAllowed, reader.read(allowed = false, su = "su", preferredName = null))
        assertEquals(AirPodsRead.NotAllowed, reader.read(allowed = true, su = null, preferredName = null))
        assertEquals(AirPodsRead.NotAllowed, reader.read(allowed = true, su = "/data/local/tmp/su; reboot", preferredName = null))
        assertNull(reader.last)
    }

    @Test fun `a second read inside the minimum interval gives back the last answer without running`() {
        var t = 0L; var runs = 0
        val reader = AirPodsBatteryReader({ runs++; Fake(lines(dump(listOf(meta(airPods, true, all))))) }, monotonicNow = { t }, wallNow = { t })
        val first = reader.read(true, "su", null)
        t += AirPodsBatteryReader.MIN_INTERVAL_MS - 1
        assertTrue(reader.tooSoon()); assertSame(first, reader.read(true, "su", null)); assertEquals(1, runs)
        t += 1
        assertFalse(reader.tooSoon()); reader.read(true, "su", null); assertEquals(2, runs)
    }

    @Test fun `a dump that never ends times out and the process is closed`() {
        var t = 0L
        val p = object : SuProcess {
            var closed = false
            override fun next(timeoutMs: Long): SuLine { t += timeoutMs; return SuLine.Timeout }
            override fun exitCode() = null; override fun errorText() = ""; override fun close() { closed = true }
        }
        val r = AirPodsBatteryReader({ p }, monotonicNow = { t }, wallNow = { 0L }).read(true, "su", null)
        assertEquals(AirPodsRead.Failed.Why.TIMEOUT, (r as AirPodsRead.Failed).why); assertTrue(p.closed)
    }

    @Test fun `a slow dump that already said enough is parsed at the timeout`() {
        var t = 0L
        val queue = ArrayDeque(lines(dump(listOf(meta(airPods, true, all)))))
        val p = object : SuProcess {
            override fun next(timeoutMs: Long): SuLine = queue.removeFirstOrNull() ?: run { t += timeoutMs; SuLine.Timeout }
            override fun exitCode() = null; override fun errorText() = ""; override fun close() {}
        }
        assertEquals(80, found((AirPodsBatteryReader({ p }, monotonicNow = { t }, wallNow = { 0L }).read(true, "su", null) as AirPodsRead.Done).reading).left)
    }

    @Test fun `a refusal, a missing su and a failed dump are told apart`() {
        fun read(launcher: SuLauncher) = AirPodsBatteryReader(launcher, { 0L }, { 0L }).read(true, "su", null)
        assertEquals(AirPodsRead.Failed.Why.DENIED, (read { Fake(emptyList(), 1, "Permission denied") } as AirPodsRead.Failed).why)
        assertEquals(AirPodsRead.Failed.Why.NO_SU, (read { throw IOException("no su") } as AirPodsRead.Failed).why)
        assertEquals(AirPodsRead.Failed.Why.ERROR, (read { Fake(emptyList(), 255, "Can't find service: bluetooth_manager") } as AirPodsRead.Failed).why)
        assertEquals(AirPodsReading.Unreadable, (read { Fake(lines("Can't find service: bluetooth_manager"), 0) } as AirPodsRead.Done).reading)
    }

    // State mapping

    @Test fun `every outcome has words, and numbers replace them only when found`() {
        val b = AirPodsBattery(80, 70, 40, false, false, true, null, 0L, AirPodsBattery.Source.SYSTEM_METADATA)
        assertEquals(R.string.airpods_reading, AirPodsText.summary(null, running = true))
        assertEquals(R.string.airpods_not_read, AirPodsText.summary(null, false))
        assertEquals(R.string.airpods_needs_root, AirPodsText.summary(AirPodsRead.NotAllowed, false))
        assertEquals(R.string.airpods_connect_first, AirPodsText.summary(AirPodsRead.NotConnected, false))
        assertEquals(R.string.bridge_rootgrant_denied, AirPodsText.summary(AirPodsRead.Failed(AirPodsRead.Failed.Why.DENIED, 0L), false))
        assertEquals(R.string.airpods_timeout, AirPodsText.summary(AirPodsRead.Failed(AirPodsRead.Failed.Why.TIMEOUT, 0L), false))
        assertEquals(R.string.bridge_rootgrant_failed, AirPodsText.summary(AirPodsRead.Failed(AirPodsRead.Failed.Why.NO_SU, 0L), false))
        assertNull(AirPodsText.summary(AirPodsRead.Done(AirPodsReading.Found(b), 0L, null), false))
        assertEquals(R.string.airpods_none, AirPodsText.summary(AirPodsRead.Done(AirPodsReading.NoAirPods, 0L, null), false))
        assertEquals(R.string.airpods_no_numbers, AirPodsText.summary(AirPodsRead.Done(AirPodsReading.NoBattery, 0L, null), false))
        assertEquals(R.string.airpods_unreadable, AirPodsText.summary(AirPodsRead.Done(AirPodsReading.Unreadable, 0L, null), false))
        assertEquals("Left 80% · Right 70% · Case 40%, charging", AirPodsText.line(context, b))
    }

    // Gate, capability and who may run it

    @Test fun `the capability is read only, A3 at least, and only root provides it while the gate is open`() {
        val cap = FolioCapability.HEADPHONES_BATTERY_SYSTEM
        assertEquals(PrivilegeTier.SHIZUKU, cap.minTier); assertEquals(OperationRisk.OBSERVE, cap.risk); assertEquals(cap, FolioCapability.fromId("device.headphones.battery.system"))
        val provider = AirPodsBatteryProvider({ true }) { RootState.READY }
        assertEquals(PrivilegeTier.ROOT, provider.tier); assertEquals(setOf(cap), provider.capabilities)
        assertEquals(BackendStatus.AVAILABLE, provider.status())
        assertEquals(BackendStatus.UNSUPPORTED, AirPodsBatteryProvider({ false }) { RootState.READY }.status())
        assertEquals(BackendStatus.NOT_GRANTED, AirPodsBatteryProvider({ true }) { RootState.UNKNOWN }.status())
        assertEquals(BackendStatus.LOST, AirPodsBatteryProvider({ true }) { RootState.LOST }.status())
        assertEquals(BackendStatus.UNSUPPORTED, AirPodsBatteryProvider({ true }) { RootState.NO_ROOT }.status())
        val broker = CapabilityBroker({ listOf(provider) }, integrationOff = { true })
        assertEquals(UnavailableReason.DISABLED_BY_USER, broker.state(cap).reason)
        assertTrue(SystemBridge.broker(context).states().any { it.capability == cap })
    }

    @Test fun `a closed gate hides the row and an open one shows it`() {
        val states = CapabilityBroker({ emptyList() }).states()
        assertFalse(SystemBridge.visibleStates(states, airPodsOpen = false).any { it.capability == FolioCapability.HEADPHONES_BATTERY_SYSTEM })
        assertTrue(SystemBridge.visibleStates(states, airPodsOpen = true).any { it.capability == FolioCapability.HEADPHONES_BATTERY_SYSTEM })
        assertEquals(states.size - 1, SystemBridge.visibleStates(states, false).size)
    }

    @Test fun `the gate is closed for everyone else until 0_7_0`() {
        val gate = FeatureGate.AIRPODS_ROOT
        assertEquals("2026-10-27", gate.closedSince); assertEquals("0.7.0", gate.opensIn); assertFalse(gate.open)
    }

    @Test fun `root may run only with the gate, system access, no Safe Mode, advanced options and a working root test`() {
        RootHingeStore.clear(context); SystemBridge.setOff(context, false)
        assertFalse("root not tested", AirPodsRoot.allowed(context, gateOpen = true, safe = false))
        val ready = RootTestReport(RootTestReport.Outcome.MOVING, RootState.READY, "su", "3.3.0:KernelSU", 124, 80, 0f, 179f, "")
        RootHingeStore.save(context, ready, "ok")
        assertFalse("advanced options off", AirPodsRoot.allowed(context, gateOpen = true, safe = false))
        RootHingeStore.setAdvanced(context, true)
        assertTrue(AirPodsRoot.allowed(context, gateOpen = true, safe = false))
        assertFalse("gate closed", AirPodsRoot.allowed(context, gateOpen = false, safe = false))
        assertFalse("Safe Mode", AirPodsRoot.allowed(context, gateOpen = true, safe = true))
        SystemBridge.setOff(context, true)
        assertFalse("system access off", AirPodsRoot.allowed(context, gateOpen = true, safe = false))
        SystemBridge.setOff(context, false); RootHingeStore.clear(context)
    }

    @Test fun `the headphones card reads nothing until its switch is on, and nothing without root`() {
        RootHingeStore.clear(context); AirPodsRoot.setShowOnCard(context, false)
        assertFalse("off until the owner turns it on", AirPodsRoot.showOnCard(context))
        val before = AirPodsRoot.state.value
        AirPodsRoot.onCardShown(context, "AirPods Pro")
        AirPodsRoot.setShowOnCard(context, true)
        AirPodsRoot.onCardShown(context, "AirPods Pro") // switch on, root never tested
        assertSame(before, AirPodsRoot.state.value); assertFalse(AirPodsRoot.state.value.running)
        AirPodsRoot.setShowOnCard(context, false)
    }
}
