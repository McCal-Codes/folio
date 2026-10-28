package com.mccal.folio

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * AirPods battery from the system: the first slice of the AirPods root tier (docs/airpods-root-tier.md).
 *
 * Android's Bluetooth service keeps a record per paired device, and `dumpsys bluetooth_manager` prints it. With root tested on
 * the System Bridge page, Folio runs that one fixed command through `su`, keeps only the lines about paired devices and their
 * battery, and reads the left, right and case battery from them. Nothing polls: a read happens when the person taps Refresh or
 * the headphones card shows, never more often than [AirPodsBatteryReader.MIN_INTERVAL_MS], and it gives up after
 * [AirPodsBatteryReader.TIMEOUT_MS]. The format comes from AOSP's source (AdapterProperties, DatabaseManager, Metadata,
 * CustomizedMetadataEntity, HeadsetStateMachine), not from a real Fold, so the parser is defensive and the page can copy a
 * redacted report for correcting it from evidence.
 */

/** One reading. Every value is what Android's Bluetooth service held when Folio asked; null means it held none. */
internal data class AirPodsBattery(
    val left: Int?,
    val right: Int?,
    val case: Int?,
    val leftCharging: Boolean?,
    val rightCharging: Boolean?,
    val caseCharging: Boolean?,
    /** One number for the whole headset: Android's main battery, or the headset's own call-profile report in 10% steps. */
    val overall: Int?,
    /** Wall-clock time of the read, for "last read". */
    val readAtMillis: Long,
    val source: Source,
) {
    enum class Source {
        /** Android's per-device metadata (untethered left, right, case), written by whatever app manages the headset. */
        SYSTEM_METADATA,
        /** Only the single level the headset sent over the call profile (Apple's battery AT command), 10% steps. */
        HEADSET_REPORT,
        /** The AirPods' own Bluetooth LE advertisement, read with a short scan and no root (AirPodsBle.kt), 10% steps. */
        BLE_ADVERTISEMENT,
    }
    val hasPerBud get() = left != null || right != null || case != null
}

/** What one dump said about AirPods. Always a state, never an error. */
internal sealed interface AirPodsReading {
    data class Found(val battery: AirPodsBattery) : AirPodsReading
    /** No paired AirPods-like device, or the connected headset is something else. */
    data object NoAirPods : AirPodsReading
    /** AirPods are paired, and Android holds no battery number for them. */
    data object NoBattery : AirPodsReading
    /** The answer did not look like a Bluetooth dump at all: another Android version, or a vendor format. */
    data object Unreadable : AirPodsReading
}

/**
 * Reads the dump's text. Section order does not matter: each kind of line is recognized by its own shape, so a vendor that moves
 * sections around still parses, and anything unrecognized is ignored.
 */
internal object AirPodsDumpParser {
    /** Goes into every report, so a pasted report says which parser read it. Bump it when the rules below change. */
    const val FORMAT_VERSION = 1
    /** Lines read before the rest is dropped; the native stack part of the dump can be long. */
    const val MAX_LINES = 40_000
    /** Characters kept of one line; a metadata line with icon addresses is about 1,500. */
    const val MAX_LINE_CHARS = 4_000
    /** Lines kept in memory for parsing and the report. */
    const val MAX_KEPT = 400
    /** Of those, lines kept only because they say "battery" in some other format, for the report. */
    private const val MAX_OTHER_BATTERY = 60

    private const val ADDR = "(?:[0-9A-Fa-fX]{2}:){5}[0-9A-Fa-fX]{2}"
    internal val address = Regex(ADDR)
    /** AdapterProperties: `    XX:XX:XX:XX:AB:CD [ BR/EDR ][ 0x240418 ] Name`, or `A => B [type][ 0x... ] Name` for a device with two addresses. */
    private val bonded = Regex("""^\s+($ADDR)(?:\s*=>\s*($ADDR))?\s*\[[^\]]*\]\[\s*0x[0-9A-Fa-f]+\s*\]\s?(.*)$""")
    /** DatabaseManager: `  XX:XX:XX:XX:AB:CD last_active_time=7 {... custom metadata(manufacturer_name=...|...) ...}`. */
    private val metadata = Regex("""^\s*($ADDR)\s.*custom metadata\(""")
    private val activeHfp = Regex("""isActiveHfpDevice \((true|false)\)""")
    private val lastActive = Regex("""last_active_time=(-?\d+)""")
    /** HeadsetStateMachine's log of the AT commands Apple headsets send: `+IPHONEACCEV=2,1,7,2,0` and `+XEVENT=BATTERY,6,11,...`. */
    private val iphoneAccEv = Regex("""\+IPHONEACCEV=([0-9,]+)""")
    private val xEventBattery = Regex("""\+XEVENT=BATTERY,([0-9,]+)""")
    private val deviceField = Regex("""device=($ADDR)""")
    private val battery = Regex("battery", RegexOption.IGNORE_CASE)

    /** The metadata fields read, and the only ones a report keeps (the rest are icons, URIs and codec state). */
    internal val FIELDS = listOf(
        "manufacturer_name", "model_name", "is_untethered_headset", "device_type",
        "untethered_left_battery", "untethered_right_battery", "untethered_case_battery",
        "untethered_left_charging", "untethered_right_charging", "untethered_case_charging",
        "main_battery", "main_charging",
    )
    private val fieldPatterns = FIELDS.associateWith { Regex("""(?:^|[|(])$it=([^|)]*)""") }

    /** Same device in every section: the last two bytes of its (anonymized) address, which Android prints in full. */
    internal fun key(address: String) = address.takeLast(5).uppercase()

    fun isBonded(line: String) = bonded.containsMatchIn(line)

    /** Keeps the lines worth parsing as the dump streams in, so a long dump never sits in memory whole. */
    class Collector {
        private val _kept = ArrayList<String>()
        val kept: List<String> get() = _kept
        var lines = 0; private set
        var truncated = false; private set
        /** Whether anything looked like Android's Bluetooth dump at all. */
        var sawBluetooth = false; private set
        private var otherBattery = 0

        /** Takes one line. False once enough has been read, so the caller can stop the process. */
        fun feed(raw: String): Boolean {
            if (lines >= MAX_LINES) { truncated = true; return false }
            lines++
            val line = if (raw.length > MAX_LINE_CHARS) raw.substring(0, MAX_LINE_CHARS) else raw
            val trimmed = line.trim()
            if (trimmed == "Bluetooth Status" || trimmed == "Bonded devices:" || trimmed == "AdapterProperties") sawBluetooth = true
            val keep = when {
                trimmed == "Bluetooth Status" || trimmed == "Bonded devices:" || trimmed == "Metadata:" -> true
                // The adapter's own state, not its name or address.
                trimmed.startsWith("enabled:") || trimmed.startsWith("state:") -> true
                bonded.containsMatchIn(line) -> { sawBluetooth = true; true }
                metadata.containsMatchIn(line) -> { sawBluetooth = true; true }
                "IPHONEACCEV" in line || "XEVENT" in line -> true
                battery.containsMatchIn(line) && otherBattery < MAX_OTHER_BATTERY -> { otherBattery++; true }
                else -> false
            }
            if (keep && _kept.size < MAX_KEPT) _kept += line
            return true
        }
    }

    fun collect(text: String): Collector = Collector().also { c -> for (line in text.lineSequence()) if (!c.feed(line)) break }

    private class Device(val key: String) {
        var name: String? = null
        var fields: Map<String, String> = emptyMap()
        var activeHfp = false
        var lastActive = Long.MIN_VALUE
        var hfpLevel: Int? = null
        fun field(name: String) = fields[name]
        val airPodsLike get() = name?.contains("airpods", ignoreCase = true) == true ||
            field("model_name")?.contains("airpods", ignoreCase = true) == true ||
            (field("manufacturer_name")?.contains("apple", ignoreCase = true) == true && field("is_untethered_headset") == "true")
        val anyBattery get() = listOf("untethered_left_battery", "untethered_right_battery", "untethered_case_battery", "main_battery").any { percent(field(it)) != null } || hfpLevel != null
    }

    private fun percent(s: String?) = s?.trim()?.toIntOrNull()?.takeIf { it in 0..100 }
    private fun flag(s: String?) = when (s?.trim()?.lowercase()) { "true" -> true; "false" -> false; else -> null }

    /** Apple's `+IPHONEACCEV=<count>,<key>,<value>,...`: key 1 is battery, 0 to 9, which AOSP turns into (v + 1) × 10 %. */
    internal fun iphoneAccEvLevel(args: String): Int? {
        val n = args.split(',').filter { it.isNotEmpty() }.map { it.toIntOrNull() ?: return null }
        val count = n.firstOrNull() ?: return null
        if (count < 1 || n.size < 1 + 2 * count) return null
        for (i in 0 until count) if (n[1 + 2 * i] == 1) return n[2 + 2 * i].takeIf { it in 0..9 }?.let { (it + 1) * 10 }
        return null
    }

    /** `+XEVENT=BATTERY,<level>,<levels>,...`: AOSP's level × 100 / (levels − 1). */
    internal fun xEventLevel(args: String): Int? {
        val n = args.split(',').filter { it.isNotEmpty() }.map { it.toIntOrNull() ?: return null }
        val level = n.getOrNull(0) ?: return null
        val levels = n.getOrNull(1) ?: return null
        if (level < 0 || levels <= 1 || level > levels) return null
        return (level * 100 / (levels - 1)).takeIf { it in 0..100 }
    }

    /**
     * The reading for the AirPods in [c]. [preferredName] is the connected headset's name as Android's audio list gives it: when a
     * paired device has that name, it is the one read, and if it is not AirPods there is nothing to show.
     */
    fun parse(c: Collector, nowMillis: Long, preferredName: String? = null): AirPodsReading {
        if (!c.sawBluetooth) return AirPodsReading.Unreadable
        val devices = LinkedHashMap<String, Device>()
        val alias = HashMap<String, String>()
        fun device(k: String) = devices.getOrPut(alias[k] ?: k) { Device(alias[k] ?: k) }
        val unassigned = ArrayList<Int>()
        for (line in c.kept) {
            val m = bonded.find(line)
            if (m != null) {
                val d = device(key(m.groupValues[1]))
                d.name = m.groupValues[3].trim().takeIf { it.isNotEmpty() }
                m.groupValues[2].takeIf { it.isNotEmpty() }?.let { alias[key(it)] = d.key }
                continue
            }
            if (metadata.containsMatchIn(line)) {
                val d = device(key(address.find(line)!!.value))
                d.fields = fieldPatterns.mapNotNull { (name, re) -> re.find(line)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() && it != "null" }?.let { name to it } }.toMap()
                d.activeHfp = activeHfp.find(line)?.groupValues?.get(1) == "true"
                d.lastActive = lastActive.find(line)?.groupValues?.get(1)?.toLongOrNull() ?: Long.MIN_VALUE
                continue
            }
            val level = iphoneAccEv.find(line)?.let { iphoneAccEvLevel(it.groupValues[1]) }
                ?: xEventBattery.find(line)?.let { xEventLevel(it.groupValues[1]) }
                ?: continue
            val at = deviceField.find(line)?.groupValues?.get(1)
            if (at != null) device(key(at)).hfpLevel = level else unassigned += level
        }
        val hint = preferredName?.trim()?.takeIf { it.isNotEmpty() }
        val hinted = hint?.let { h -> devices.values.firstOrNull { it.name?.trim().equals(h, ignoreCase = true) } }
        if (hinted != null && !hinted.airPodsLike) return AirPodsReading.NoAirPods
        val candidates = devices.values.filter { it.airPodsLike }
        val chosen = hinted ?: candidates.maxWithOrNull(compareBy<Device>({ it.activeHfp }, { it.anyBattery }, { it.lastActive }))
            ?: return AirPodsReading.NoAirPods
        // A log line without a device can only be trusted when there is one pair of AirPods to give it to.
        if (chosen.hfpLevel == null && candidates.size == 1 && unassigned.isNotEmpty()) chosen.hfpLevel = unassigned.last()
        val left = percent(chosen.field("untethered_left_battery"))
        val right = percent(chosen.field("untethered_right_battery"))
        val case = percent(chosen.field("untethered_case_battery"))
        val main = percent(chosen.field("main_battery"))
        return when {
            left != null || right != null || case != null || main != null -> AirPodsReading.Found(AirPodsBattery(
                left, right, case,
                flag(chosen.field("untethered_left_charging")), flag(chosen.field("untethered_right_charging")), flag(chosen.field("untethered_case_charging")),
                main ?: chosen.hfpLevel, nowMillis, AirPodsBattery.Source.SYSTEM_METADATA))
            chosen.hfpLevel != null -> AirPodsReading.Found(AirPodsBattery(null, null, null, null, null, null, chosen.hfpLevel, nowMillis, AirPodsBattery.Source.HEADSET_REPORT))
            else -> AirPodsReading.NoBattery
        }
    }

    fun parse(text: String, nowMillis: Long, preferredName: String? = null) = parse(collect(text), nowMillis, preferredName)

    /** A name in a report: its first two letters and its length, so the owner can tell devices apart without showing them. */
    fun redactName(name: String) = "${name.take(2).takeIf { name.length > 2 }.orEmpty()}…(${name.length})"

    /**
     * The kept lines as a report the owner can paste into an issue: every address becomes `dev1`, `dev2`, … (the same device keeps
     * its number in every line), metadata lines keep only [FIELDS], and paired device names are shortened by [redactName] unless
     * [keepNames]. The phone's own Bluetooth name and address are never kept in the first place.
     */
    fun report(c: Collector, keepNames: Boolean, header: List<String>): String {
        val names = c.kept.mapNotNull { bonded.find(it)?.groupValues?.get(3)?.trim()?.takeIf { n -> n.isNotEmpty() } }.distinct().sortedByDescending { it.length }
        val aliases = LinkedHashMap<String, String>()
        fun mask(line: String) = address.replace(line) { m -> aliases.getOrPut(key(m.value)) { "dev${aliases.size + 1}" } }
        val body = c.kept.map { line ->
            val shaped = if (metadata.containsMatchIn(line)) {
                val addr = address.find(line)!!.value
                val kept = fieldPatterns.mapNotNull { (name, re) -> re.find(line)?.groupValues?.get(1)?.let { "$name=${it.trim()}" } }
                "  $addr ${lastActive.find(line)?.value.orEmpty()} ${activeHfp.find(line)?.value.orEmpty()} ${kept.joinToString("|")}"
            } else line
            var out = mask(shaped)
            if (!keepNames) names.forEach { out = out.replace(it, redactName(it)) }
            out.trimEnd()
        }
        return buildString {
            header.forEach(::appendLine)
            appendLine("Parser: $FORMAT_VERSION, lines read: ${c.lines}${if (c.truncated) " (stopped at the limit)" else ""}, lines kept: ${c.kept.size}") // english-only
            appendLine("Names: ${if (keepNames) "shown" else "shortened"}, addresses: replaced") // english-only
            appendLine("---")
            body.forEach(::appendLine)
        }
    }
}

/** Offers [FolioCapability.HEADPHONES_BATTERY_SYSTEM] while its gate is open and the owner's root test says root works. */
internal class AirPodsBatteryProvider(private val gateOpen: () -> Boolean, private val rootState: () -> RootState) : CapabilityProvider {
    override val tier = PrivilegeTier.ROOT
    override val capabilities = setOf(FolioCapability.HEADPHONES_BATTERY_SYSTEM)
    override fun status() = when {
        !gateOpen() -> BackendStatus.UNSUPPORTED
        // The root test is the hinge test: a phone without the hinge sensor never keeps a working su path, so it cannot read either.
        else -> when (rootState()) {
            RootState.READY -> BackendStatus.AVAILABLE
            RootState.LOST -> BackendStatus.LOST
            RootState.NO_ROOT, RootState.NO_SENSOR -> BackendStatus.UNSUPPORTED
            RootState.UNKNOWN, RootState.DENIED -> BackendStatus.NOT_GRANTED
        }
    }
}

/** What one try gave. */
internal sealed interface AirPodsRead {
    /** Not run: the gate, the system access switch, Safe Mode, advanced options or root said no. */
    data object NotAllowed : AirPodsRead
    /** Not run: no Bluetooth headphones are connected, so there is nothing current to read. */
    data object NotConnected : AirPodsRead
    data class Done(val reading: AirPodsReading, val atMillis: Long, val forName: String?) : AirPodsRead
    data class Failed(val why: Why, val atMillis: Long) : AirPodsRead {
        enum class Why { DENIED, NO_SU, TIMEOUT, ERROR }
    }
}

/**
 * Runs `su -c "dumpsys bluetooth_manager"` and parses it, written against [SuLauncher] so tests play back a root manager. The
 * command is fixed: no name, path or value from anywhere else is in it.
 */
internal class AirPodsBatteryReader(
    private val launcher: SuLauncher,
    private val monotonicNow: () -> Long,
    private val wallNow: () -> Long,
    private val minIntervalMs: Long = MIN_INTERVAL_MS,
    private val timeoutMs: Long = TIMEOUT_MS,
) {
    companion object {
        const val COMMAND = "dumpsys bluetooth_manager"
        /** One read at most this often, whoever asks: a card shown twice or Refresh tapped again gets the last answer. */
        const val MIN_INTERVAL_MS = 30_000L
        const val TIMEOUT_MS = 5_000L
    }

    private var lastAt = Long.MIN_VALUE
    @Volatile var last: AirPodsRead? = null; private set
    /** The lines of the last dump, in memory only, for Copy AirPods report. */
    @Volatile var lastCollector: AirPodsDumpParser.Collector? = null; private set

    /** Whether a read now would run, or give back [last] because the last one was too recent. */
    @Synchronized fun tooSoon() = lastAt != Long.MIN_VALUE && monotonicNow() - lastAt < minIntervalMs

    /** One read if [allowed] and the last was long enough ago; otherwise the reason or the last answer, and nothing runs. */
    @Synchronized fun read(allowed: Boolean, su: String?, preferredName: String?): AirPodsRead {
        if (!allowed) return AirPodsRead.NotAllowed
        if (su == null || su !in RootHingeRunner.SU_CANDIDATES) return AirPodsRead.NotAllowed
        last?.let { if (tooSoon()) return it }
        lastAt = monotonicNow()
        return run(su, preferredName).also { last = it }
    }

    private fun run(su: String, preferredName: String?): AirPodsRead {
        val process = try { launcher.start(listOf(su, "-c", COMMAND)) } catch (_: IOException) { return AirPodsRead.Failed(AirPodsRead.Failed.Why.NO_SU, wallNow()) }
        val collector = AirPodsDumpParser.Collector()
        try {
            val deadline = monotonicNow() + timeoutMs
            var timedOut = false
            while (true) {
                val left = deadline - monotonicNow()
                if (left <= 0) { timedOut = true; break }
                when (val line = process.next(minOf(left, 500L))) {
                    SuLine.Timeout -> Unit
                    SuLine.Eof -> break
                    is SuLine.Text -> if (!collector.feed(line.line)) break
                }
            }
            lastCollector = collector
            if (!collector.sawBluetooth) {
                if (timedOut) return AirPodsRead.Failed(AirPodsRead.Failed.Why.TIMEOUT, wallNow())
                val code = process.exitCode()
                if (code != null && code != 0) {
                    val why = if (RootHingeRunner.refusedRoot(process.errorText())) AirPodsRead.Failed.Why.DENIED else AirPodsRead.Failed.Why.ERROR
                    return AirPodsRead.Failed(why, wallNow())
                }
            }
            val now = wallNow()
            return AirPodsRead.Done(AirPodsDumpParser.parse(collector, now, preferredName), now, preferredName)
        } catch (e: Exception) {
            return AirPodsRead.Failed(AirPodsRead.Failed.Why.ERROR, wallNow())
        } finally {
            process.close()
        }
    }
}

/** The words the page and the card use for a read. Pure, so the mapping is a unit test. */
internal object AirPodsText {
    /** The sentence for [read] when there are no numbers to show, or null when there are. */
    @androidx.annotation.StringRes fun summary(read: AirPodsRead?, running: Boolean): Int? = when {
        running -> R.string.airpods_reading
        read == null -> R.string.airpods_not_read
        read is AirPodsRead.NotAllowed -> R.string.airpods_needs_root
        read is AirPodsRead.NotConnected -> R.string.airpods_connect_first
        read is AirPodsRead.Failed && read.why == AirPodsRead.Failed.Why.DENIED -> R.string.bridge_rootgrant_denied
        read is AirPodsRead.Failed && read.why == AirPodsRead.Failed.Why.TIMEOUT -> R.string.airpods_timeout
        read is AirPodsRead.Failed -> R.string.bridge_rootgrant_failed
        read is AirPodsRead.Done -> when (read.reading) {
            is AirPodsReading.Found -> null
            AirPodsReading.NoAirPods -> R.string.airpods_none
            AirPodsReading.NoBattery -> R.string.airpods_no_numbers
            AirPodsReading.Unreadable -> R.string.airpods_unreadable
        }
        else -> R.string.airpods_not_read
    }

    enum class Part { LEFT, RIGHT, CASE, OVERALL, OVERALL_ABOUT }
    data class Shown(val part: Part, val percent: Int, val charging: Boolean)

    /** What a reading shows, in order: per bud and case when Android has them, otherwise the one overall number. */
    fun parts(b: AirPodsBattery): List<Shown> = if (b.hasPerBud) listOfNotNull(
        b.left?.let { Shown(Part.LEFT, it, b.leftCharging == true) },
        b.right?.let { Shown(Part.RIGHT, it, b.rightCharging == true) },
        b.case?.let { Shown(Part.CASE, it, b.caseCharging == true) },
    ) else listOfNotNull(b.overall?.let {
        Shown(if (b.source == AirPodsBattery.Source.HEADSET_REPORT) Part.OVERALL_ABOUT else Part.OVERALL, it, false)
    })

    @androidx.annotation.StringRes fun partLabel(p: Part): Int = when (p) {
        Part.LEFT -> R.string.airpods_left
        Part.RIGHT -> R.string.airpods_right
        Part.CASE -> R.string.airpods_case
        Part.OVERALL -> R.string.airpods_overall
        Part.OVERALL_ABOUT -> R.string.airpods_overall_about
    }

    fun line(context: Context, b: AirPodsBattery): String = parts(b).joinToString(" · ") { s ->
        context.getString(partLabel(s.part), s.percent).let { if (s.charging) context.getString(R.string.airpods_part_charging, it) else it }
    }
}

/**
 * The app-wide holder: one reader, its last answer as state for the page and the card, and the owner's switch for the card. A read
 * runs here, off the main thread, so a Fold display swap that rebuilds the screen does not cancel it.
 */
internal object AirPodsRoot {
    private const val PREFS = "airpods_root"

    data class UiState(val running: Boolean = false, val last: AirPodsRead? = null)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()
    private val reader by lazy {
        AirPodsBatteryReader(ProcessSuLauncher, monotonicNow = { android.os.SystemClock.elapsedRealtime() }, wallNow = { System.currentTimeMillis() })
    }

    /** Whether the headphones card may read: the owner's own switch, off until they turn it on. */
    fun showOnCard(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("card", false)
    fun setShowOnCard(context: Context, on: Boolean) { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("card", on).apply() }

    /** Every condition for running root here: the gate, system access on, no Safe Mode, advanced options on, root tested and working. */
    fun allowed(context: Context, gateOpen: Boolean = FeatureGate.AIRPODS_ROOT.isOpen(context), safe: Boolean = SafeMode.active): Boolean =
        gateOpen && !SystemBridge.isOff(context) && !safe && RootHingeStore.advanced(context) &&
            RootHingeStore.state(context) == RootState.READY && RootHingeStore.suPath(context) != null

    /** The connected Bluetooth headphones' name from Android's audio list (no permission), "" when unnamed, null when none. */
    fun connectedHeadset(context: Context): String? = runCatching {
        context.getSystemService(AudioManager::class.java)?.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            ?.firstOrNull { it.type in HEADSET_OUTPUTS }?.let { it.productName?.toString()?.trim().orEmpty() }
    }.getOrNull()

    private val HEADSET_OUTPUTS = setOf(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLUETOOTH_SCO)

    /** Refresh on the page: reads only while headphones are connected, so a reading is about the pair in use. */
    fun refresh(context: Context) {
        val app = context.applicationContext
        val connected = connectedHeadset(app)
        if (connected == null) { if (!_state.value.running) _state.value = UiState(false, AirPodsRead.NotConnected); return }
        start(app, connected.ifEmpty { null }) {}
    }

    /** The headphones card appeared for [name]: read if the owner switched the card on and root may run. */
    fun onCardShown(context: Context, name: String?) {
        val app = context.applicationContext
        if (!showOnCard(app) || !allowed(app)) return
        start(app, name) {}
    }

    /** Copies the report of the last dump, reading first if there has been none. Only the owner's tap gets here. */
    fun copyReport(context: Context, keepNames: Boolean, done: () -> Unit) {
        val app = context.applicationContext
        val copy: () -> Unit = {
            val c = reader.lastCollector
            val text = if (c == null) "Folio AirPods report: nothing was read. ${describe(reader.last)}" // english-only
                else AirPodsDumpParser.report(c, keepNames, header(app))
            scope.launch {
                withContext(Dispatchers.Main) {
                    app.getSystemService(android.content.ClipboardManager::class.java)?.setPrimaryClip(android.content.ClipData.newPlainText("Folio AirPods report", text)) // english-only
                    done()
                }
            }
        }
        if (reader.lastCollector != null) copy() else start(app, connectedHeadset(app)?.ifEmpty { null }, copy)
    }

    private fun start(app: Context, hint: String?, then: () -> Unit) {
        synchronized(this) {
            if (_state.value.running) return
            _state.value = _state.value.copy(running = true)
        }
        scope.launch {
            val result = try {
                if (!allowed(app)) AirPodsRead.NotAllowed
                else SystemBridge.broker(app).perform(FolioCapability.HEADPHONES_BATTERY_SYSTEM, fallback = { AirPodsRead.NotAllowed }) {
                    reader.read(allowed = true, su = RootHingeStore.suPath(app), preferredName = hint)
                }
            } catch (_: Exception) { AirPodsRead.Failed(AirPodsRead.Failed.Why.ERROR, System.currentTimeMillis()) }
            _state.value = UiState(false, result)
            then()
        }
    }

    private fun header(app: Context) = listOf(
        "Folio AirPods report", // english-only
        "Device: ${android.os.Build.MODEL}, Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT}), Folio ${WhatsNew.currentVersion(app)}", // english-only
        "Outcome: ${describe(reader.last)}", // english-only
    )

    /** The outcome in plain words for the report: kinds and numbers only. */
    internal fun describe(read: AirPodsRead?): String = when (read) {
        null -> "not read"
        AirPodsRead.NotAllowed -> "not allowed"
        AirPodsRead.NotConnected -> "no headphones connected"
        is AirPodsRead.Failed -> "failed (${read.why})"
        is AirPodsRead.Done -> when (val r = read.reading) {
            is AirPodsReading.Found -> r.battery.let { "found ${it.source}: left=${it.left} right=${it.right} case=${it.case} overall=${it.overall} charging=${it.leftCharging}/${it.rightCharging}/${it.caseCharging}" } // english-only
            AirPodsReading.NoAirPods -> "no AirPods"
            AirPodsReading.NoBattery -> "AirPods without battery numbers" // english-only
            AirPodsReading.Unreadable -> "unreadable"
        }
    }
}
