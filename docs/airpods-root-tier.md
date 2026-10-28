# AirPods in the root tier

27 Oct 2026. The owner asked for AirPods support on root devices first. This note says what root can add on top of a
normal app, the least powerful route for each, how it plugs into the System Bridge, and the phased plan. The first slice
(phase 1) is built behind `FeatureGate.AIRPODS_ROOT`.

Labels: **[read]** read on a page or in source (AOSP sources were fetched from android.googlesource.com, `main`, on 27 Oct
2026), **[assumed]** inference or general knowledge, **[unknown]** not established. The background research is
`folio-handoff/pc/research-airpods-compatibility-2026-10-27.md`; claims taken from it keep its labels.

Nothing in this note was checked on a phone. No AirPods, no real `dumpsys` output and no root device were used. The
parser was written from AOSP source and tested against fixtures written from that source.

The no-root route (the AirPods' own Bluetooth advertisement, `FeatureGate.AIRPODS_BLE`) is described in
[airpods-ble.md](airpods-ble.md). Its reading wins over this one while it is not expired, and the System Bridge card shows which
source a number came from.

## Rules this follows

PRV-17: every feature works without root; root only adds optional extras in the System Bridge tier, off until the person
turns them on. Folio never asks for root in the background (ADR 0012). Least power first. No new Android permission in
this slice. Nothing flashes (WCAG 2.3.1) and nothing new animates. GPL projects (LibrePods, CAPod, OpenPods,
furiousMAC/continuity) were read for facts only; no code was copied or paraphrased.

## What root can add, and the least powerful route

| Feature | A normal app (no root) | Least powerful privileged route | Why that route |
|---|---|---|---|
| Left, right, case battery and charging | BLE advertisement in 10% steps, needs `BLUETOOTH_SCAN` (a new permission) [read: research §2] | **A3: read Android's own per-device metadata** with `dumpsys bluetooth_manager`. Built in phase 1 through A4 (root), because Folio has no Shizuku provider yet | Read only, no new permission, no scan, no radio use. The shell user holds `android.permission.DUMP` [read: AOSP `packages/Shell/AndroidManifest.xml`], so root is more than this needs |
| One overall level from the headset | Not readable: `getBatteryLevel` is `@SystemApi` [read: research §1.1] | Same dump: the AirPods' `+IPHONEACCEV` battery report in the HFP state machine log [read: `HeadsetStateMachine.dump`, `HeadsetStackEvent.buildString`] | Comes free with the same read; 10% steps [read: `RemoteDevices.getBatteryLevelFromAppleBatteryVsc`] |
| Noise control (ANC, Transparency, Off), Conversation Awareness | Possible over AAP (L2CAP) with `BLUETOOTH_CONNECT` once the stack fix is present [read: research §3.1] | **A0 plus one runtime permission** if One UI 9 has the fix; otherwise a hook (A5), not root alone [read: research §3.1, LibrePods README says the workaround is an Xposed module] | Root by itself does not change the Bluetooth stack's L2CAP behavior [assumed] |
| Transparency customization, hearing-aid settings, multipoint, loud sound reduction | No [read: research §3.3] | **A5 (hook)**: the VendorID hook LibrePods describes [read: research §3.3] | Needs the stack to present Folio as an Apple device; no shell or root command does that [assumed] |
| Ear detection events | BLE status flags (scan permission) or AAP [read: research §3.3] | Same as noise control | Root adds nothing here [assumed] |
| Battery in Android's own Settings and widgets | No | Write the per-bud metadata as a privileged caller (what LibrePods' root module is for [read: research §1.1]); shell holds `BLUETOOTH_PRIVILEGED` [read: Shell manifest] | A write, so a later phase with its own approval |

Important limit of phase 1 [assumed]: Android fills the per-bud metadata only when some app writes it (Google Fast Pair,
a vendor companion app, LibrePods' root module). For AirPods on a stock Samsung phone nobody may write it, so the
likely result on the Fold is the single 10% level from the HFP log, or "no battery numbers". The report button exists to
find out.

## How it plugs into the System Bridge (phase 1, built)

- **Capability** `device.headphones.battery.system` (`FolioCapability.HEADPHONES_BATTERY_SYSTEM`), least tier A3
  (SHIZUKU), risk OBSERVE. Name says what it does, not how.
- **Provider** `AirPodsBatteryProvider`, tier A4 (ROOT). Available only while the gate is open and the owner's root test
  reads READY; UNKNOWN or DENIED read "needs your permission", LOST reads lost. The broker already turns A3 and up off for
  the system access switch and Safe Mode. A later A3 provider (Shizuku, or a one-time `pm grant ... DUMP`) would win
  automatically, because the broker picks the lowest tier that is available.
- **Who may run it** (`AirPodsRoot.allowed`): gate open, system access on, no Safe Mode, Advanced options on, root test
  READY with a remembered `su` path. Every read also goes through `broker.perform`, so it shows in Recent calls.
- **Command**: `su -c "dumpsys bluetooth_manager"`, fixed text, nothing variable in it.
- **When it runs**: only when the person taps Refresh (and Bluetooth headphones are connected), or when the headphones card
  appears with the "Show on the headphones card" switch on (off by default). At most once per 30 s
  (`MIN_INTERVAL_MS`), 5 s timeout, 40,000 lines read, 400 kept. Nothing polls, no service, no receiver.
- **Versioning**: the capability id stays; the parser carries `FORMAT_VERSION = 1`, printed in every report. A dump
  Folio does not recognize is the state "Android answered in a form Folio does not know yet", never a wrong number.
  No bridge APK, so the ADR 0011 handshake is unchanged.
- **What the person sees**: with the gate open and Advanced options on, an "AirPods battery" card under the Root card on
  Settings › Advanced › System Bridge: Now (the reading or the reason), Last read, Refresh, the card switch, Copy
  AirPods report (names shortened, or with names). A row "AirPods battery (root)" in What it lets Folio do. With the
  switch on, the headphones card on Home shows "Left 80% · Right 70% · Case 40%, charging" under Connected, only when the
  read is for that device and under 60 s old. Text only, a static charging glyph, no motion.
- **When root disappears** (the owner's Fold 8 has temporary root re-applied at boot [read: memory note]): `su` is missing
  or refuses, the read returns Failed (NO_SU or DENIED) and the card says so in words; the card on Home shows nothing.
  Nothing retries. The saved root state is not changed by a failed read; the owner's next root test updates it.

## Failure modes

| Case | What happens |
|---|---|
| No AirPods paired, or the connected headset is not AirPods | "No AirPods in Android's Bluetooth list." The connected headset's name from Android's audio list picks the device; a non-AirPods match shows nothing |
| AirPods paired, no numbers in the system | "Android holds no battery numbers for these AirPods." |
| Wrong model or renamed AirPods | A device counts as AirPods when its name or `model_name` says AirPods, or `manufacturer_name` is Apple with `is_untethered_headset=true`. A renamed pair with no metadata is missed [assumed acceptable for phase 1] |
| Two AirPods paired | The active HFP device, then the one with numbers, then the most recent `last_active_time` |
| Dump format changes (Android version, Samsung's build) | Lines are matched by shape, not position. Unknown shape: Unreadable. Samsung's format is [unknown] |
| A vendor prints fully masked addresses | Devices could not be told apart; the parser would merge them [assumed rare] |
| Huge or slow dump | Bounded by line count, kept lines and the 5 s timeout; a slow dump that already printed the bonded and metadata lines is still parsed |
| Root missing or refused | Failed, in words; no retry |
| Stale metadata | The dump has no time for when metadata was written [read: `Metadata.toString`]; the page shows when Folio read it, not when the AirPods sent it |

## Data handling

- Nothing leaves the phone. The report goes only to the clipboard, by the owner's tap.
- Nothing is stored: the reading and the kept lines live in memory; the only saved value is the card switch.
- No scan, so nothing about other people's AirPods is ever seen. Only devices paired with this phone are in the dump.
- The report replaces every Bluetooth address with `dev1`, `dev2` (the same device keeps its number), keeps only the
  battery, charging, model and manufacturer fields of each metadata line, never keeps the phone's own name or address,
  and shortens paired device names to their first two letters and length unless the owner picks "with device names".
- Rotating addresses: the dump prints addresses through `BluetoothUtils.toAnonymizedAddress` [read: call sites in
  `AdapterProperties` and `Metadata`]; that the result keeps only the last two bytes (`XX:XX:XX:XX:AB:CD`) is [assumed].
  The parser joins sections by those two bytes and never stores them.
- No new Android permission: `AndroidManifest.xml` is unchanged by this slice.

## Phased plan

0. **Evidence (owner, no Folio code).** A real dump with AirPods connected, and the CAPod test from the research (§7).
1. **Battery from the system, read only. THIS SLICE, built.** Gate `AIRPODS_ROOT`, opens in 0.7.0.
2. **Correct from evidence.** Add the real dump as a fixture, fix the parser, bump `FORMAT_VERSION`. Then consider an A3
   route so root is not needed: a Shizuku provider running the same dump, or reading `getMetadata` as the shell user
   (it holds `BLUETOOTH_PRIVILEGED` [read]). A one-time `pm grant <folio> android.permission.DUMP` (protection level
   includes `development` [read: framework manifest]) would need the manifest to declare DUMP, a new permission under
   REL-24, and whether SELinux lets an app run `dumpsys` is [unknown].
3. **AAP probe, read only.** Open the AAP channel, read device info and the current noise mode, change nothing.
4. **AAP controls.** Noise control and Conversation Awareness, one control at a time, each behind its own switch.
5. **Hook tier (A5), research only.** Transparency customization, hearing-aid settings, multipoint, loud sound reduction.
   Not planned for a release.

## Next step for the AAP channel (phase 3), and the evidence it needs

Not built. Before any AAP code:

1. **Which Android and build.** The Fold 8's exact One UI 9 build and Bluetooth module version
   (`dumpsys package com.android.bluetooth | grep versionName` or the btservices APEX version). The stack fix is reported
   for Android 16 QPR3 and Android 17 [read: research §3.1]; on One UI 9 it is [unknown].
2. **Whether root is needed at all on his build.** CAPod 5.0 or later on the Fold 8: does its device settings screen
   switch ANC and Transparency? If yes, a normal app with `BLUETOOTH_CONNECT` can do it and phase 3 does not belong in
   the root tier. If CAPod says settings are unavailable, the stack lacks the fix and only a hook (A5) helps.
3. **A real capture.** An HCI snoop log (Developer options › Enable Bluetooth HCI snoop log, toggle Bluetooth, use CAPod
   to switch ANC once, then `adb bugreport`) to confirm the L2CAP PSM (0x1001 per the brief [assumed]) and the packet
   for the noise-mode change on his AirPods Pro 3 firmware. The capture holds his AirPods' address and must stay private.
4. **Design gate.** A new runtime permission (`BLUETOOTH_CONNECT`) goes through REL-24, PERMISSIONS.md and PRIVACY.md,
   and an ADR amendment before code.

## What could not be verified

- Any real `dumpsys bluetooth_manager` output, on any phone. Samsung's format and whether it keeps AOSP's lines: [unknown].
- Whether anything writes per-bud metadata for AirPods on One UI 9: [unknown]; [assumed] nothing does.
- Whether AirPods send `+IPHONEACCEV` to the Fold 8, and whether that line survives the HFP state machine's log ring
  until Folio reads it: [unknown].
- The root path end to end: `su` was never run. The emulator check covers only the "not available" states.
- The headphones card line was not seen with real numbers; only unit tests cover it.

## Credits and names

LibrePods (kavishdevar, GPL-3.0), CAPod (d4rken-org, GPL-3.0), OpenPods (GPL-3.0) and furiousMAC/continuity (GPL-2.0)
were read through the research note for facts about behavior; none of their code is in Folio. The dump format comes from
AOSP's Bluetooth module (Apache 2.0). AirPods is a trademark of Apple Inc. Folio uses the name only to say what it reads,
with a generic headphones icon and no Apple artwork.
