# 0012: A continuous hinge angle is an optional root provider, behind the broker

- **Status:** built and checked on a Galaxy Z Fold8, 2026-10-06 (proposed as a sketch earlier the same day). Off by default.
- **Extends:** [0011](0011-system-bridge.md).

## Context

Folio's fold animation follows the public `TYPE_HINGE_ANGLE` sensor, which on the Galaxy Z Fold8 gives only 0, 90 and 180
degrees, so the motion between the steps is predicted from learned timing. Samsung's own Folding Angle sensor
(type 65686) is every degree, but it needs `com.samsung.permission.SSENSOR`.

Measured on the Fold8 on 6 October 2026, hinge moving, 25 seconds each (`tools/hinge-probe/run.sh`):

| Identity | Public sensor | Folding Angle (65686) |
|---|---|---|
| shell user (A3, what Shizuku runs code as) | 3 distinct values | refused: "without holding com.samsung.permission.SSENSOR" |
| root (A4, KernelSU, shell enabled by the owner) | 3 distinct values | 157 readings, 89 distinct values, 0 to 179 degrees |

So a continuous angle is available to a root process and to nothing else on this phone.

## Decision

1. **One new capability, `device.hinge.angle.continuous`, at tier ROOT.** Features ask the broker for it. The public sensor
   stays `device.hinge.angle` (A0) and is always the way back: with the capability off, nothing changes.
2. **`RootHingeProvider` offers it only when `RootState` is READY.** `RootState` comes from one test the owner starts
   (Developer or Settings, a button named for what it does). Folio never asks for root in the background, on launch, or to
   find out whether root exists: a root manager may show the person a prompt, and what runs as root is their decision.
3. **The helper is Folio's own code, not a payload.** It is a small `main` in the installed APK, started as
   `su -c "CLASSPATH=<this app's own APK path> app_process /system/bin <class>"`. The APK path comes from the package
   manager for Folio's own package. Nothing is downloaded or unpacked, nothing is taken from a package or a setting, and
   the command line has no variable part the owner or a package can influence (PRV-13, ADR 0004).
4. **The pipe is the only channel.** The helper prints one line per message (`R`, `H <nanos> <degrees>`, `E <word>`) to
   the pipe of the `su` process Folio started. No socket, no Binder service, nothing another app can connect to.
   `RootHingeProtocol.parse` ignores anything else; a gate keeps readings in time order and at most 100 a second.
5. **The helper runs only while Folio is in front.** The fold timeline starts it when Folio starts and stops it when Folio
   stops. It also exits when its input closes (the app died) and ends by itself after 300 seconds, so it cannot outlive Folio;
   the feed starts it again if the end was a normal one. Checked on the phone: force-stopping Folio ended it in under a second.
6. **Losing root is an ordinary state.** `LOST` (helper died, grant removed) switches the source back to the public sensor
   at once and tells the owner in plain words. The kill switch and Safe Mode already turn the ROOT tier off; nothing new is
   needed for them.
7. **Each source remembers what it learned under its own key** (`hingeCapabilityKey`). A root feed seen to be continuous
   must not be remembered as the public sensor's, or the animation would expect smooth readings after the helper is gone.
8. **Optional and labeled.** PRV-17: off by default, in the System Bridge tier, named as root, with the Knox note already on
   that page. No Folio feature needs it.

## What is built

- `RootHinge.kt`: `RootState`, `RootHingeProvider`, the message format and its gate, `hingeSource`, `hingeCapabilityKey`.
- `RootHingeHelper.java`: the helper's `main`, in Folio's own APK, started as `su -c "CLASSPATH=<own APK> app_process /system/bin
  com.mccal.folio.RootHingeHelper <seconds>"`. The R8 keep rule is in `app/proguard-rules.pro`.
- `RootHingeRunner.kt`: the owner's test (the Test button), the `su` locations it tries, the report to copy. KernelSU,
  Magisk and APatch behaviors (a prompt that waits, a refusal, no `su`) are played back in `RootHingeRunnerTest`.
- `RootHingeTester`: runs the test where a screen change cannot cancel it. Found on the phone: opening the Fold rebuilds the screen,
  and a test tied to the screen stopped halfway.
- `RootHingeFeed.kt` and the `FoldTimeline` hook: the live feed, behind the "Use in the fold animation" switch (off by
  default, offered only after a test reads READY). A lost feed marks the state LOST and the timeline carries on with the public sensor.
- The Root card on the System Bridge page: the Test button, the result in words, the switch, Copy test report.

Measured on the Fold8: the test gave 124 readings and 80 different angles from 0 to 179 degrees; the helper runs as root only
while Folio is in front, stops when Folio goes behind another app, and starts again on return.

## What is still undecided

- Whether Folio shows its own explanation before the root manager's grant. I would.
- Magisk and APatch: tested only by playback, never on a phone that has them.
- Whether the optional root backend ships in 0.6.9 behind Developer settings or moves to 0.7.0 (the 0.6.9 plan lists
  "root backend" under not in 0.6.9). McCal decides.
- Whether the helper's cost (battery, CPU) while Folio is in front is acceptable. Not measured.

## Consequences

- The System Bridge page gains a row, "Live hinge angle (root)", which reads "Not available yet" until a provider is wired.
- Threat model T27 to T31 cover the new trust boundary.
- If this is never built, the finding stands: on this phone a live angle is root-only, and Folio's stepped prediction is the
  honest ceiling for everyone else.

## Amendment, 6 Oct 2026: advanced options, and root can grant the settings permission

- **Advanced options.** Root and computer-command options sit behind one switch on the System Bridge page, off by default, with a short
  warning first ("leave it off if this isn't your phone or you're not comfortable") and an "I know" button. A Details button
  opens the full disclosure. The root hinge feed also stops running while advanced options are off.
- **A second thing Folio can do as root.** If the owner turns on "Allow Folio to use root for this" (off by default, offered only after a
  root test works), a button runs `pm grant` or `pm revoke` of `WRITE_SECURE_SETTINGS` for Folio's own package. The command is built only from
  that package name (checked as a plain name) and that one permission. It is safer than the owner pasting the command themselves, and
  the same kill switch and Safe Mode turn it off.
- **Why the manifest declares the permission.** `pm grant` ignores a permission an app has not declared. Declaring it is a visible change
  (PERMISSIONS.md, row 15), made on purpose: nothing uses the permission yet.
