package com.mccal.folio

import android.Manifest
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * AirPods battery with no root (docs/airpods-ble.md): the battery AirPods broadcast in their Bluetooth LE advertisement.
 *
 * Opt-in and on demand only. A short, filtered scan runs while an AirPods battery surface is on screen (the headphones card on
 * Home, the card in Settings › Dynamic Island, or the System Bridge card), the person's switch is on, Nearby devices is allowed,
 * Bluetooth is on and AirPods are the connected headphones. It stops after [AirPodsBleController.WINDOW_MS] or at once when any
 * of that stops being true. No service, no background scan, no repeat on a timer.
 *
 * A scan sees everyone's AirPods nearby. [AdvertSelector] keeps only the strongest near one held over two samples, refuses to
 * guess between two close ones, and every other advertisement is dropped as it arrives. Addresses and raw bytes are never kept.
 */

/**
 * One decoded proximity-pairing advertisement: the values only, never the bytes or the address. Battery is in 10% steps;
 * null is the "unknown" nibble. Fields marked [unverified] in docs/airpods-ble.md come from open decoders, not a primary source.
 */
internal data class ProximityAdvert(
    /** The two model bytes as sent, high byte first: AirPods Pro sends 0x0E 0x20, so 0x0E20. */
    val model: Int,
    val left: Int?,
    val right: Int?,
    val case: Int?,
    val leftCharging: Boolean,
    val rightCharging: Boolean,
    val caseCharging: Boolean,
    /** [unverified] */
    val leftInEar: Boolean,
    /** [unverified] */
    val rightInEar: Boolean,
    /** [unverified] */
    val lidOpen: Boolean,
) {
    val hasNumbers get() = left != null || right != null || case != null

    fun toBattery(atMillis: Long) = AirPodsBattery(
        left, right, case,
        leftCharging.takeIf { left != null }, rightCharging.takeIf { right != null }, caseCharging.takeIf { case != null },
        overall = null, readAtMillis = atMillis, source = AirPodsBattery.Source.BLE_ADVERTISEMENT,
    )
}

/**
 * Decodes Apple's proximity-pairing message from the manufacturer data Android hands over for company id 0x004C (the bytes after
 * the company id). Layout [read: furiousMAC/continuity proximity_pairing.md]: type 0x07, length, prefix, model (2), status,
 * battery (right pod high nibble, left low, ×10%), charging flags and case battery, lid counter, color, suffix, 16 encrypted bytes.
 * Pure: never throws, and anything that is not type 0x07 with the length 25 is null.
 */
internal object ProximityDecoder {
    /** Goes into the report, so a pasted report says which rules read it. Bump when the rules below change. */
    const val VERSION = 1
    const val APPLE_COMPANY_ID = 0x004C
    const val TYPE = 0x07
    /** 9 bytes of fields and the 16-byte encrypted tail after the type and length bytes [read: continuity]. */
    const val LENGTH = 0x19
    const val SIZE = 2 + LENGTH
    /** The scan filter's data and mask: only type 0x07 with length 25 reaches Folio at all. */
    val FILTER_DATA = byteArrayOf(TYPE.toByte(), LENGTH.toByte())
    val FILTER_MASK = byteArrayOf(0xFF.toByte(), 0xFF.toByte())

    private fun u(b: ByteArray, i: Int) = b[i].toInt() and 0xFF

    /** A battery nibble: 0 to 10 is that many tens of percent, 15 is "unknown", anything else is treated as unknown. */
    internal fun level(nibble: Int): Int? = nibble.takeIf { it in 0..10 }?.let { it * 10 }

    fun decode(data: ByteArray?): ProximityAdvert? = try {
        if (data == null || data.size < SIZE || u(data, 0) != TYPE || u(data, 1) != LENGTH) null
        else {
            val status = u(data, 5)
            // [unverified] Status bit 0x20 says which pod the nibbles start from; open decoders swap left and right when it is clear.
            val flipped = status and 0x20 == 0
            val battery = u(data, 6)
            val high = battery shr 4
            val low = battery and 0x0F
            val chargingAndCase = u(data, 7)
            val flags = chargingAndCase shr 4
            ProximityAdvert(
                model = (u(data, 3) shl 8) or u(data, 4),
                left = level(if (flipped) high else low),
                right = level(if (flipped) low else high),
                case = level(chargingAndCase and 0x0F),
                // [unverified] Charging flags in the high nibble: 0x1 and 0x2 the pods (swapped like the levels), 0x4 the case.
                leftCharging = flags and (if (flipped) 0x2 else 0x1) != 0,
                rightCharging = flags and (if (flipped) 0x1 else 0x2) != 0,
                caseCharging = flags and 0x4 != 0,
                // [unverified] In-ear bits 0x02 and 0x08 of the status byte, swapped like the levels.
                leftInEar = status and (if (flipped) 0x08 else 0x02) != 0,
                rightInEar = status and (if (flipped) 0x02 else 0x08) != 0,
                // [unverified] The lid counter's bit 0x08 is clear while the lid is open.
                lidOpen = u(data, 8) and 0x08 == 0,
            )
        }
    } catch (_: Exception) { null }

    /** Names for the model ids seen most. Only AirPods Pro's id is from the format note; the rest are [unverified]. */
    private val MODELS = mapOf(
        0x0220 to "AirPods", 0x0F20 to "AirPods (2nd generation)", 0x1320 to "AirPods (3rd generation)", // product names for the report // english-only
        0x0E20 to "AirPods Pro", 0x1420 to "AirPods Pro (2nd generation)", 0x2420 to "AirPods Pro (2nd generation, USB-C)", // english-only
        0x0A20 to "AirPods Max", 0x0B20 to "Powerbeats Pro", 0x1120 to "Beats Studio Buds", 0x1220 to "Beats Fit Pro", // english-only
    )

    fun modelName(model: Int) = MODELS[model] ?: "Apple model 0x%04X".format(model) // english-only
}

/**
 * Picks the person's own AirPods from what a scan saw, without keeping anyone else's. Each sample has an in-memory key (a salted
 * hash, made by the scanner, that lasts one scan), a signal strength and the decoded values.
 *
 * - Weaker than [nearDbm]: dropped.
 * - The strongest near one must be seen [samplesNeeded] times in a row, at near strength.
 * - If the next strongest is within [tieDb] and does not decode to the same pair (both buds of one pair can advertise), the answer
 *   is [Choice.Several], never a guess.
 * Only the leader's decoded values are held; of the runner-up only its key, strength and whether it matched are held.
 */
internal class AdvertSelector(
    private val nearDbm: Int = NEAR_DBM,
    private val tieDb: Int = TIE_DB,
    private val samplesNeeded: Int = SAMPLES_NEEDED,
) {
    companion object {
        const val NEAR_DBM = -60
        const val TIE_DB = 5
        const val SAMPLES_NEEDED = 2
    }

    sealed interface Choice {
        data class Mine(val advert: ProximityAdvert) : Choice
        data object Several : Choice
        data object None : Choice
    }

    private class Leader(val key: Int, var rssi: Int, var streak: Int, var advert: ProximityAdvert)
    private var leader: Leader? = null
    private var runnerKey: Int? = null
    private var runnerRssi = Int.MIN_VALUE
    private var runnerSamePair = false

    private fun samePair(a: ProximityAdvert, b: ProximityAdvert) =
        a.model == b.model && a.case == b.case && listOf(a.left, a.right).sortedBy { it ?: -1 } == listOf(b.left, b.right).sortedBy { it ?: -1 }

    private fun runner(key: Int, rssi: Int, same: Boolean) { runnerKey = key; runnerRssi = rssi; runnerSamePair = same }

    fun offer(key: Int, rssi: Int, advert: ProximityAdvert) {
        if (rssi < nearDbm) {
            if (leader?.key == key) leader = null
            if (runnerKey == key) runnerKey = null
            return
        }
        val l = leader
        when {
            l == null -> leader = Leader(key, rssi, 1, advert)
            l.key == key -> { l.rssi = rssi; l.streak++; l.advert = advert }
            rssi > l.rssi -> { runner(l.key, l.rssi, samePair(l.advert, advert)); leader = Leader(key, rssi, 1, advert) }
            runnerKey == null || runnerKey == key || rssi >= runnerRssi -> runner(key, rssi, samePair(l.advert, advert))
        }
    }

    fun choice(): Choice {
        val l = leader ?: return Choice.None
        val close = runnerKey != null && !runnerSamePair && l.rssi - runnerRssi < tieDb
        return when {
            l.streak < samplesNeeded -> if (runnerKey != null) Choice.Several else Choice.None
            close -> Choice.Several
            else -> Choice.Mine(l.advert)
        }
    }
}

/** What the advertisement route has to say. Always a state with words, never a blank. */
internal sealed interface AirPodsBleRead {
    data object NotYet : AirPodsBleRead
    data class Blocked(val why: Why) : AirPodsBleRead
    data object Scanning : AirPodsBleRead
    data class Found(val advert: ProximityAdvert, val atMillis: Long) : AirPodsBleRead {
        val battery get() = advert.toBattery(atMillis)
    }
    /** The person's AirPods were picked, and they sent the "unknown" value for every part. */
    data class NoNumbers(val atMillis: Long) : AirPodsBleRead
    data class NothingSeen(val atMillis: Long) : AirPodsBleRead
    data class Several(val atMillis: Long) : AirPodsBleRead
    data class Failed(val atMillis: Long) : AirPodsBleRead

    enum class Why { GATE_CLOSED, SWITCH_OFF, NO_PERMISSION, BLUETOOTH_OFF, NO_AIRPODS, SCREEN_OFF }
}

/** Everything that must hold for a scan, read fresh each time. */
internal data class BleConditions(
    val gateOpen: Boolean,
    val switchOn: Boolean,
    val permission: Boolean,
    val bluetoothOn: Boolean,
    val screenOn: Boolean,
    val appleConnected: Boolean,
) {
    /** The first reason not to scan, or null. */
    fun blocked(): AirPodsBleRead.Why? = when {
        !gateOpen -> AirPodsBleRead.Why.GATE_CLOSED
        !switchOn -> AirPodsBleRead.Why.SWITCH_OFF
        !permission -> AirPodsBleRead.Why.NO_PERMISSION
        !bluetoothOn -> AirPodsBleRead.Why.BLUETOOTH_OFF
        !appleConnected -> AirPodsBleRead.Why.NO_AIRPODS
        !screenOn -> AirPodsBleRead.Why.SCREEN_OFF
        else -> null
    }
}

/** The scan, behind a small interface so tests use a fake. Results carry an in-memory key, the strength and Apple's data. */
internal interface AdvertScanner {
    /** Starts one filtered scan. False when Android refused or nothing could start. */
    fun start(onResult: (key: Int, rssi: Int, data: ByteArray?) -> Unit, onFailed: () -> Unit): Boolean
    fun stop()
}

internal fun interface Cancel { fun cancel() }
internal fun interface BleTicker { fun after(ms: Long, block: () -> Unit): Cancel }

/** Where an AirPods battery reading is on screen; a scan runs only while at least one is. */
internal enum class BleSurface { HOME_CARD, SETTINGS, BRIDGE }

/**
 * The scan's lifecycle, pure apart from [scanner] and [ticker]. Starts only when [conditions] hold, a surface is visible and the
 * last start was at least [minIntervalMs] ago; checks the conditions every [checkMs] while scanning and stops at the first that
 * fails; stops when the last surface hides or after [windowMs]. After a stop nothing from the scan is left but the one answer.
 */
internal class AirPodsBleController(
    private val scanner: AdvertScanner,
    private val ticker: BleTicker,
    private val conditions: () -> BleConditions,
    private val monotonicNow: () -> Long,
    private val wallNow: () -> Long,
    private val out: MutableStateFlow<AirPodsBleRead> = MutableStateFlow(AirPodsBleRead.NotYet),
    private val windowMs: Long = WINDOW_MS,
    private val minIntervalMs: Long = MIN_INTERVAL_MS,
    private val checkMs: Long = CHECK_MS,
) {
    companion object {
        const val WINDOW_MS = 5_000L
        /** Also keeps Folio far under Android's limit of five scan starts per 30 seconds. */
        const val MIN_INTERVAL_MS = 30_000L
        const val CHECK_MS = 500L
    }

    val state: StateFlow<AirPodsBleRead> = out.asStateFlow()
    private val visible = HashSet<BleSurface>()
    private var selector: AdvertSelector? = null
    private val timers = ArrayList<Cancel>()
    private var lastStart = Long.MIN_VALUE
    /** The last finished answer, shown again after a stop that was not about the person's settings. */
    private var lastResult: AirPodsBleRead = AirPodsBleRead.NotYet

    val scanning: Boolean @Synchronized get() = selector != null
    /** For tests: whether anything from a scan is still held. */
    internal val holdsScanData: Boolean @Synchronized get() = selector != null || timers.isNotEmpty()

    @Synchronized fun setVisible(surface: BleSurface, on: Boolean) {
        if (on) { if (visible.add(surface)) startIfAllowed() }
        else if (visible.remove(surface) && visible.isEmpty()) interrupt(null)
    }

    /** The person tapped Refresh. True when a scan started. */
    @Synchronized fun refresh(): Boolean = startIfAllowed()

    /** Something changed (a switch, the permission, the screen coming back): stop if it must, or start if it now may. */
    @Synchronized fun check() {
        val why = conditions().blocked()
        when {
            selector != null -> if (why != null) interrupt(why)
            why != null -> out.value = AirPodsBleRead.Blocked(why)
            else -> { if (out.value is AirPodsBleRead.Blocked) out.value = lastResult; startIfAllowed() }
        }
    }

    private fun startIfAllowed(): Boolean {
        if (selector != null) return false
        conditions().blocked()?.let { out.value = AirPodsBleRead.Blocked(it); return false }
        if (out.value is AirPodsBleRead.Blocked) out.value = lastResult
        if (visible.isEmpty()) return false
        if (lastStart != Long.MIN_VALUE && monotonicNow() - lastStart < minIntervalMs) return false
        lastStart = monotonicNow()
        val sel = AdvertSelector()
        selector = sel
        out.value = AirPodsBleRead.Scanning
        val started = try {
            scanner.start({ key, rssi, data -> onResult(sel, key, rssi, data) }, { onFailed(sel) })
        } catch (_: Exception) { false }
        if (!started) { selector = null; done(AirPodsBleRead.Failed(wallNow())); return false }
        timers += ticker.after(windowMs) { synchronized(this) { if (selector === sel) finish() } }
        scheduleCheck(sel)
        return true
    }

    private fun scheduleCheck(sel: AdvertSelector) {
        timers += ticker.after(checkMs) {
            synchronized(this) {
                if (selector !== sel) return@synchronized
                val why = conditions().blocked()
                if (why != null) interrupt(why) else scheduleCheck(sel)
            }
        }
    }

    @Synchronized private fun onResult(sel: AdvertSelector, key: Int, rssi: Int, data: ByteArray?) {
        if (selector !== sel) return // a late result after a stop is dropped unread
        val advert = ProximityDecoder.decode(data) ?: return
        sel.offer(key, rssi, advert)
    }

    @Synchronized private fun onFailed(sel: AdvertSelector) {
        if (selector !== sel) return
        halt()
        done(AirPodsBleRead.Failed(wallNow()))
    }

    private fun halt() {
        runCatching { scanner.stop() }
        timers.forEach { runCatching { it.cancel() } }
        timers.clear()
        selector = null
    }

    private fun finish() {
        val choice = selector?.choice() ?: AdvertSelector.Choice.None
        halt()
        val at = wallNow()
        done(when (choice) {
            is AdvertSelector.Choice.Mine -> if (choice.advert.hasNumbers) AirPodsBleRead.Found(choice.advert, at) else AirPodsBleRead.NoNumbers(at)
            AdvertSelector.Choice.Several -> AirPodsBleRead.Several(at)
            AdvertSelector.Choice.None -> AirPodsBleRead.NothingSeen(at)
        })
    }

    private fun done(result: AirPodsBleRead) { lastResult = result; out.value = result }

    /** Stops a scan early. A hidden card or the screen going off keeps the last answer; a setting that changed says so. */
    private fun interrupt(why: AirPodsBleRead.Why?) {
        if (selector == null) { if (why != null && why != AirPodsBleRead.Why.SCREEN_OFF) out.value = AirPodsBleRead.Blocked(why); return }
        halt()
        out.value = if (why == null || why == AirPodsBleRead.Why.SCREEN_OFF) lastResult else AirPodsBleRead.Blocked(why)
    }
}

/** How old a reading may be: plain while fresh, with its age after [AGE_SHOWN_MS], gone after [DROP_MS]. */
internal object AirPodsFreshness {
    const val AGE_SHOWN_MS = 2 * 60_000L
    const val DROP_MS = 10 * 60_000L
    /** A clock that moved back by more than this makes a reading untrustworthy. */
    private const val CLOCK_SLACK_MS = 60_000L

    sealed interface Age {
        data object Fresh : Age
        data class Minutes(val minutes: Long) : Age
        data object Expired : Age
    }

    fun of(atMillis: Long, nowMillis: Long): Age {
        val age = nowMillis - atMillis
        return when {
            age < -CLOCK_SLACK_MS || age > DROP_MS -> Age.Expired
            age >= AGE_SHOWN_MS -> Age.Minutes(age / 60_000L)
            else -> Age.Fresh
        }
    }

    /** The reading to show: the advertisement's when it is not expired, otherwise the root reading's, otherwise none. */
    fun best(ble: AirPodsBattery?, root: AirPodsBattery?, nowMillis: Long): AirPodsBattery? =
        ble?.takeIf { of(it.readAtMillis, nowMillis) != Age.Expired } ?: root?.takeIf { of(it.readAtMillis, nowMillis) != Age.Expired }
}

/** Words for the advertisement route. Pure, so the mapping is a unit test. */
internal object AirPodsBleText {
    @androidx.annotation.StringRes fun summary(read: AirPodsBleRead, nowMillis: Long): Int? = when (read) {
        AirPodsBleRead.NotYet -> R.string.airpods_not_read
        AirPodsBleRead.Scanning -> R.string.airpods_ble_scanning
        is AirPodsBleRead.Blocked -> when (read.why) {
            AirPodsBleRead.Why.GATE_CLOSED, AirPodsBleRead.Why.SWITCH_OFF -> R.string.airpods_ble_off
            AirPodsBleRead.Why.NO_PERMISSION -> R.string.airpods_ble_needs_permission
            AirPodsBleRead.Why.BLUETOOTH_OFF -> R.string.airpods_ble_bluetooth_off
            AirPodsBleRead.Why.NO_AIRPODS -> R.string.airpods_ble_no_airpods
            AirPodsBleRead.Why.SCREEN_OFF -> R.string.airpods_not_read
        }
        is AirPodsBleRead.Found -> if (AirPodsFreshness.of(read.atMillis, nowMillis) == AirPodsFreshness.Age.Expired) R.string.airpods_ble_expired else null
        is AirPodsBleRead.NoNumbers -> R.string.airpods_ble_no_numbers
        is AirPodsBleRead.NothingSeen -> R.string.airpods_ble_nothing_seen
        is AirPodsBleRead.Several -> R.string.airpods_ble_several
        is AirPodsBleRead.Failed -> R.string.airpods_ble_failed
    }

    @androidx.annotation.StringRes fun sourceLabel(source: AirPodsBattery.Source): Int =
        if (source == AirPodsBattery.Source.BLE_ADVERTISEMENT) R.string.airpods_source_ble else R.string.airpods_source_root

    fun atMillis(read: AirPodsBleRead): Long? = when (read) {
        is AirPodsBleRead.Found -> read.atMillis
        is AirPodsBleRead.NoNumbers -> read.atMillis
        is AirPodsBleRead.NothingSeen -> read.atMillis
        is AirPodsBleRead.Several -> read.atMillis
        is AirPodsBleRead.Failed -> read.atMillis
        else -> null
    }

    /** The numbers line, with "N min ago" once the reading is a few minutes old; null when there is nothing current to show. */
    fun line(context: Context, b: AirPodsBattery, nowMillis: Long): String? = when (val age = AirPodsFreshness.of(b.readAtMillis, nowMillis)) {
        AirPodsFreshness.Age.Expired -> null
        AirPodsFreshness.Age.Fresh -> AirPodsText.line(context, b)
        is AirPodsFreshness.Age.Minutes -> context.getString(R.string.airpods_with_age, AirPodsText.line(context, b), age.minutes.toInt())
    }

    /**
     * The text Copy AirPods report puts on the clipboard: the decoded values of the person's own AirPods and the times only. No
     * address, no key, no raw bytes and nothing about any other advertisement is ever in it.
     */
    fun report(read: AirPodsBleRead, nowMillis: Long, header: List<String>): String = buildString {
        header.forEach(::appendLine)
        appendLine("Source: Bluetooth advertisement, decoder ${ProximityDecoder.VERSION}") // english-only
        val at = atMillis(read)
        appendLine("Outcome: ${outcome(read)}") // english-only
        if (at != null) appendLine("Read: ${(nowMillis - at) / 1000} s ago") // english-only
        if (read is AirPodsBleRead.Found) {
            val a = read.advert
            fun pct(v: Int?) = v?.let { "$it%" } ?: "unknown"
            appendLine("Model: ${ProximityDecoder.modelName(a.model)} (0x%04X)".format(a.model)) // english-only
            appendLine("Left: ${pct(a.left)}, charging ${a.leftCharging}, in ear ${a.leftInEar} (unverified)") // english-only
            appendLine("Right: ${pct(a.right)}, charging ${a.rightCharging}, in ear ${a.rightInEar} (unverified)") // english-only
            appendLine("Case: ${pct(a.case)}, charging ${a.caseCharging}, lid open ${a.lidOpen} (unverified)") // english-only
        }
    }

    private fun outcome(read: AirPodsBleRead) = when (read) {
        AirPodsBleRead.NotYet -> "not read"
        AirPodsBleRead.Scanning -> "scanning"
        is AirPodsBleRead.Blocked -> "not scanning (${read.why})" // english-only
        is AirPodsBleRead.Found -> "found"
        is AirPodsBleRead.NoNumbers -> "AirPods sent no battery numbers" // english-only
        is AirPodsBleRead.NothingSeen -> "nothing near enough" // english-only
        is AirPodsBleRead.Several -> "several AirPods equally near" // english-only
        is AirPodsBleRead.Failed -> "scan could not start" // english-only
    }
}

/** The real scan: filtered on Apple's proximity-pairing prefix, low latency, results only while it runs. */
internal class AndroidAdvertScanner(private val app: Context) : AdvertScanner {
    private var scanner: BluetoothLeScanner? = null
    private var callback: ScanCallback? = null

    override fun start(onResult: (key: Int, rssi: Int, data: ByteArray?) -> Unit, onFailed: () -> Unit): Boolean {
        if (app.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) return false
        val leScanner = app.getSystemService(BluetoothManager::class.java)?.adapter?.takeIf { it.isEnabled }?.bluetoothLeScanner ?: return false
        // A per-scan salt: the key tells samples of one scan apart and means nothing after it. The address itself is not kept.
        val salt = kotlin.random.Random.nextInt()
        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) = deliver(result)
            override fun onBatchScanResults(results: MutableList<ScanResult>) { results.forEach(::deliver) }
            override fun onScanFailed(errorCode: Int) = onFailed()
            private fun deliver(r: ScanResult) {
                // Without an address every sample gets its own key, so nothing can ever be held twice and nothing is chosen.
                val key = runCatching { r.device.address.hashCode() }.getOrNull()?.let { it * 31 xor salt } ?: System.identityHashCode(r)
                onResult(key, r.rssi, r.scanRecord?.getManufacturerSpecificData(ProximityDecoder.APPLE_COMPANY_ID))
            }
        }
        val filter = ScanFilter.Builder().setManufacturerData(ProximityDecoder.APPLE_COMPANY_ID, ProximityDecoder.FILTER_DATA, ProximityDecoder.FILTER_MASK).build()
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).setReportDelay(0).build()
        return try {
            leScanner.startScan(listOf(filter), settings, cb)
            scanner = leScanner; callback = cb
            true
        } catch (_: Exception) { false }
    }

    override fun stop() {
        val s = scanner; val cb = callback
        scanner = null; callback = null
        if (s == null || cb == null) return
        // Taking the permission back ends Folio's process, and with it the scan, so there is nothing to stop without it.
        if (app.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) return
        try { s.stopScan(cb) } catch (_: SecurityException) { } catch (_: IllegalStateException) { } // Bluetooth already off
    }
}

/** The app-wide holder: one controller, its answer as state, and the person's switch. */
internal object AirPodsBle {
    private const val PREFS = "airpods_ble"
    private val out = MutableStateFlow<AirPodsBleRead>(AirPodsBleRead.NotYet)
    val state: StateFlow<AirPodsBleRead> = out.asStateFlow()
    @Volatile private var controller: AirPodsBleController? = null

    fun switchOn(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("on", false)
    fun setSwitchOn(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("on", on).apply()
        recheck(context)
    }

    fun hasPermission(context: Context) = context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED

    /** Whether a connected headset's name says it is Apple's: AirPods or Beats. A renamed pair without either word is missed. */
    fun isAppleName(name: String?) = name != null && (name.contains("airpods", ignoreCase = true) || name.contains("beats", ignoreCase = true))

    fun conditions(app: Context) = BleConditions(
        gateOpen = FeatureGate.AIRPODS_BLE.isOpen(app),
        switchOn = switchOn(app),
        permission = hasPermission(app),
        // getState needs no permission; if a vendor refuses it anyway, the scan itself says no.
        bluetoothOn = runCatching { app.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true }.getOrDefault(true),
        screenOn = app.getSystemService(PowerManager::class.java)?.isInteractive != false,
        appleConnected = isAppleName(AirPodsRoot.connectedHeadset(app)),
    )

    private fun controller(app: Context): AirPodsBleController = controller ?: synchronized(this) {
        controller ?: AirPodsBleController(
            AndroidAdvertScanner(app),
            ticker = { ms, block -> val h = Handler(Looper.getMainLooper()); val r = Runnable(block); h.postDelayed(r, ms); Cancel { h.removeCallbacks(r) } },
            conditions = { conditions(app) },
            monotonicNow = { android.os.SystemClock.elapsedRealtime() },
            wallNow = { System.currentTimeMillis() },
            out = out,
        ).also { controller = it }
    }

    /** A surface showing the reading appeared or went away. The gate is asked first, so a closed gate costs nothing. */
    fun setVisible(context: Context, surface: BleSurface, on: Boolean) {
        if (!on) { controller?.setVisible(surface, false); return }
        val app = context.applicationContext
        if (!FeatureGate.AIRPODS_BLE.isOpen(app)) return
        controller(app).setVisible(surface, true)
    }

    fun refresh(context: Context) {
        val app = context.applicationContext
        if (FeatureGate.AIRPODS_BLE.isOpen(app)) controller(app).refresh()
    }

    fun recheck(context: Context) {
        val app = context.applicationContext
        if (FeatureGate.AIRPODS_BLE.isOpen(app) || controller != null) controller(app).check()
    }

    /** The advertisement's battery when there is one that has not expired. */
    fun battery(nowMillis: Long): AirPodsBattery? =
        (state.value as? AirPodsBleRead.Found)?.battery?.takeIf { AirPodsFreshness.of(it.readAtMillis, nowMillis) != AirPodsFreshness.Age.Expired }

    fun copyReport(context: Context) {
        val app = context.applicationContext
        val header = listOf(
            "Folio AirPods report", // english-only
            "Device: ${android.os.Build.MODEL}, Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT}), Folio ${WhatsNew.currentVersion(app)}", // english-only
        )
        val text = AirPodsBleText.report(state.value, System.currentTimeMillis(), header)
        app.getSystemService(android.content.ClipboardManager::class.java)?.setPrimaryClip(android.content.ClipData.newPlainText("Folio AirPods report", text)) // english-only
    }
}
