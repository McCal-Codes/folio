# AirPods battery without root (Bluetooth advertisement)

27 Oct 2026. The second AirPods slice, stacked on the root tier ([airpods-root-tier.md](airpods-root-tier.md)). Built behind
`FeatureGate.AIRPODS_BLE` (closed since 2026-10-27, opens in 0.7.0 with `OPEN_IN_0_7_0`; supporters with early access and
Folio Dev see it now).

Labels: **[read]** read on a page or in source this session, **[unverified]** taken from how open decoders behave, not from a
primary source, and implemented defensively, **[assumed]** inference. Nothing here was checked with real AirPods, a real
advertisement or a phone. The background research is `folio-handoff/pc/research-airpods-compatibility-2026-10-27.md` (§2).

## Why

A read-only dump of the owner's Fold 8 (Android 17, One UI 9) with AirPods connected showed the headset battery as -1 and no
per-bud or case metadata, so the root `dumpsys` route finds nothing on that phone. AirPods broadcast left, right and case battery
in their Bluetooth LE advertisement, unencrypted, to every phone nearby [read: research §2]. A normal app can hear it with one
runtime permission and no root.

## Design in five lines

1. `ProximityDecoder` (pure) turns Apple's manufacturer data (company id 0x004C, type 0x07, length 25) into values, or null.
2. `AdvertSelector` (pure) picks the person's own AirPods from a scan by signal strength and refuses to guess when unsure.
3. `AirPodsBleController` (pure apart from a scanner and a timer interface) runs one short scan only while every condition holds.
4. `AndroidAdvertScanner` is the only code that touches `BluetoothLeScanner`; tests use a fake.
5. The answer feeds the same `AirPodsBattery` the root slice built, with `Source.BLE_ADVERTISEMENT`, and wins while not expired.

## When a scan runs

All of these, checked when a scan would start and every 500 ms while it runs:

- the gate is open;
- the switch "AirPods battery (uses Bluetooth scan)" is on (off by default);
- Nearby devices (`BLUETOOTH_SCAN`) is granted;
- Bluetooth is on (`BluetoothAdapter.isEnabled`, no permission needed);
- the screen is on (`PowerManager.isInteractive`);
- the connected headphones are Apple's: the name Android's audio list gives (`AudioDeviceInfo.productName`, the same source the
  island and headphones card use, no permission) contains "AirPods" or "Beats".

And one surface is on screen: the headphones card on Home (only for an Apple-named device), the AirPods battery card in
Settings › Dynamic Island (while resumed) or the System Bridge AirPods card (while resumed). A scan starts when a surface appears
or the person taps Refresh, lasts at most 5 s (`WINDOW_MS`), and the next may start 30 s after the last start (`MIN_INTERVAL_MS`,
which also keeps Folio far under Android's five starts per 30 s). It stops at once when the last surface hides or any condition
fails. No foreground service, no background scan, no receiver, no alarm, nothing on a timer outside a running scan.

Scan settings: one `ScanFilter` with manufacturer id 0x004C and data `07 19` under mask `FF FF`, so only proximity-pairing
messages of the expected length reach Folio; `SCAN_MODE_LOW_LATENCY`; report delay 0.

### Why the card lives in Settings › Dynamic Island

The headphones card's own switch ("Headphones & speakers") is on the Dynamic Island page, under Brief pop-ups, and this route
needs no root, so it does not belong behind System Bridge › Advanced. The System Bridge AirPods card (root tier) keeps its place
and now shows the best reading and its source.

### Why only `BLUETOOTH_SCAN`

- minSdk is 31 (`app/build.gradle.kts`), so the Android 12 rules apply everywhere: `BLUETOOTH_SCAN` with
  `usesPermissionFlags="neverForLocation"` scans without any location permission [read: Android Bluetooth permissions page, via
  the research note]. No legacy `BLUETOOTH`/`BLUETOOTH_ADMIN`/location entries are needed.
- `BLUETOOTH_CONNECT` is not declared and not needed: the connected device comes from the audio list by name, scan results'
  addresses are read without it, and the device's Device ID (vendor 0x004C) record is not public API. The cost is that a pair
  renamed without "AirPods" or "Beats" in its name is not recognized.
- `<uses-feature android:name="android.hardware.bluetooth_le" android:required="false" />` keeps Folio installable on devices
  without Bluetooth LE.
- Limit of `neverForLocation`: Android may filter some beacon advertisements from such scans [read: research §1.3]. CAPod ships
  with the same flag and reads AirPods [read: CAPod manifest via research]; on One UI 9 it is [unverified].

## Byte layout implemented

Android's `ScanRecord.getManufacturerSpecificData(0x004C)` returns the bytes after the company id. Offsets below are into that.

| Offset | Field | How Folio reads it | Label |
|---|---|---|---|
| 0 | Type | must be 0x07 (proximity pairing) | [read: continuity] |
| 1 | Length | must be 0x19 (25): 9 field bytes and the 16-byte encrypted tail; the array must hold at least 27 bytes, extra trailing bytes are ignored | [read: continuity field list]; that every model sends 25 is [unverified] |
| 2 | Prefix | not checked (documented as 0x01, meaning unknown) | [read] |
| 3-4 | Model | two bytes as sent, high byte first; AirPods Pro is 0x0E20 | [read: continuity]; other model names in the table are [unverified] |
| 5 | Status | bit 0x20 clear swaps left and right for byte 6, the charging flags and in-ear; in-ear bits 0x02 and 0x08 | [unverified] |
| 6 | Battery | high nibble right pod, low nibble left pod, in tens of percent | [read: continuity] |
| 7 | Charging and case | low nibble case battery in tens of percent; high nibble flags 0x1 and 0x2 the pods, 0x4 the case | case nibble and the flag set [read]; bit positions [unverified] |
| 8 | Lid counter | bit 0x08 clear read as lid open | [unverified] |
| 9 | Color | not read | [read] |
| 10 | Suffix | not read | [read] |
| 11-26 | Encrypted payload | not read (needs keys from the case; 1% steps and identity are in here) | [read: research §2] |

Nibble values: 0 to 10 are 0% to 100%; 15 is "unknown"; 11 to 14 are treated as unknown [unverified: 15 as unknown comes from
open decoders]. A charging flag is only reported for a part with a level. The decoder never throws (20,000 random arrays in the
tests) and returns null for anything else. `ProximityDecoder.VERSION = 1` is printed in every report.

What Folio shows: left, right and case in 10% steps, charging as a static glyph and the word "charging". In-ear and lid are
decoded (and appear in the report marked unverified) but not shown until a real capture confirms them.

## Privacy: choosing the person's own AirPods

A scan hears every AirPods nearby, and their rotating addresses do not make the battery private [read: research §2]. Rules
(`AdvertSelector`, unit-tested with strangers, ties, weak signals and a single strong one):

- A sample weaker than -60 dBm (`NEAR_DBM`) is dropped on arrival.
- The strongest near advertisement must be seen twice in a row at near strength (`SAMPLES_NEEDED = 2`).
- If the next strongest is within 5 dB (`TIE_DB`) and decodes to a different pair, the answer is "several nearby": show nothing,
  never guess. Two adverts that decode to the same model and the same battery set (the two buds of one pair) are not a tie;
  whichever wins, the numbers are the same.
- Only the leader's decoded values are held, and of the runner-up only its in-memory key, strength and whether it matched.
  Every other advertisement is decoded, compared and dropped in the same call.
- The key is a hash of the address mixed with a random salt made per scan; the address itself is never stored. Without an
  address every sample gets its own key, so nothing can be chosen (fails closed).
- When the scan stops the selector is dropped; only the chosen pair's decoded values and the time stay, in memory.
- Nothing is persisted except the switch. Nothing is logged or sent. Copy AirPods report holds the outcome, the model, the
  decoded values and how long ago they were read: no address, key or raw bytes (unit-tested).

Limits: with another pair held as close as the person's own, Folio says so instead of answering; a stranger's pair much closer
than the person's own could be read as theirs while their AirPods are connected [assumed rare at -60 dBm, which is roughly
arm's length]. Keys from the case (what CAPod uses for certainty) are out of scope.

## Freshness and which reading wins

- Under 2 minutes: shown plain. 2 to 10 minutes: shown with "(N min ago)". Over 10 minutes, or a clock that moved back by more
  than a minute: dropped, and the card says "No recent reading. Tap Refresh." (`AirPodsFreshness`).
- The advertisement reading wins over the root reading while it is not expired (`AirPodsFreshness.best`). The headphones card
  falls back to the root read only under its own 60 s rule, as before.

## What the person sees

Settings › Dynamic Island › AirPods battery (gate open): a note, the switch, a one-line permission note with Allow Nearby
devices (after Android stops asking, the button opens Folio's page in Android Settings), Now, Source, Last read, Refresh and Copy
AirPods report. Words for every state: Off; Needs the Nearby devices permission; Bluetooth is off; No AirPods connected; Looking
for your AirPods; No AirPods battery heard nearby; Several AirPods are nearby; Your AirPods sent no battery numbers; Android could
not start the Bluetooth scan; No recent reading. The System Bridge card shows the best reading and a Source row ("Bluetooth
advertisement" or "System record (root)"). Settings › Permissions lists Nearby devices for AirPods battery while the gate is
open. Nothing animates: text changes in place, the charging glyph is static (WCAG 2.3.1).

## Failure modes

| Case | What happens |
|---|---|
| Permission refused or revoked | The card says it needs Nearby devices and offers the request; a running scan stops at the next check |
| Bluetooth off, AirPods disconnect, screen off, card hidden | The scan stops within 500 ms (hidden: at once); the last answer stays, with its age |
| No advertisement in 5 s (batching, far away, a phone that filters it) | "No AirPods battery heard nearby", never a number |
| Two pairs equally near | "Several AirPods are nearby" |
| Android refuses the scan or reports a failure | "Android could not start the Bluetooth scan" |
| A model with another length, or a changed layout | Rejected by the filter and the decoder: nothing heard, never a wrong number |
| Left and right swapped on some model | Possible while the orientation bit is [unverified]; the report shows what was decoded |
| A renamed pair without "AirPods" or "Beats" | Not recognized as Apple's; no scan |

## What remains

- A real advertisement from the owner's AirPods Pro 3 (copy the report, compare with the case) to confirm the orientation bit,
  the charging bits, the length, the model id and the lid bit; then bump `ProximityDecoder.VERSION`.
- Whether One UI 9 delivers these advertisements under `neverForLocation` and without hardware batching delays: [unverified].
- The AAP control channel (noise control, conversation awareness) is not built here; see the root-tier note's phase 3.
- The encrypted tail (1% battery, identity) needs keys from the case: not planned.

## Credits and names

The byte layout comes from furiousMAC/continuity's `proximity_pairing.md` (GPL-2.0, read for facts). LibrePods (kavishdevar,
GPL-3.0), CAPod (d4rken-org, GPL-3.0) and OpenPods (GPL-3.0) were read through the research note for behavior; none of their
code is in Folio and the decoder was written from the documented layout. AirPods and Beats are trademarks of Apple Inc. Folio
uses the names only to say what it reads, with a generic headphones icon and no Apple artwork.
