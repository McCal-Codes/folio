# 0010: A continuous hinge angle is an optional root provider, behind the broker

- **Status:** proposed (a sketch), 2026-10-06
- **Extends:** [0009](0009-system-bridge.md). Nothing here is wired into the app yet.

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
5. **The helper runs only while something needs it.** The fold animation or StandBy starts it and stops it when done. It
   also exits when its input closes (the app died) and after 60 seconds with no reader, so it cannot outlive Folio.
6. **Losing root is an ordinary state.** `LOST` (helper died, grant removed) switches the source back to the public sensor
   at once and tells the owner in plain words. The kill switch and Safe Mode already turn the ROOT tier off; nothing new is
   needed for them.
7. **Each source remembers what it learned under its own key** (`hingeCapabilityKey`). A root feed seen to be continuous
   must not be remembered as the public sensor's, or the animation would expect smooth readings after the helper is gone.
8. **Optional and labeled.** PRV-17: off by default, in the System Bridge tier, named as root, with the Knox note already on
   that page. No Folio feature needs it.

## What the sketch contains

`RootHinge.kt`: `RootState`, `RootHingeProvider`, the message format and its gate, `hingeSource` and `hingeCapabilityKey`,
with `RootHingeTest`. `FolioCapability.HINGE_ANGLE_CONTINUOUS` and its name on the System Bridge page.

## What it does not contain, and what is undecided

- The helper's `main`, the code that starts `su`, the test button, and the hook into `FoldTimeline`. The probe in
  `tools/hinge-probe` is the reference for the helper's core.
- Whether the root manager's own listing of Folio is enough as the grant, or Folio should show its own explanation first.
  I would show one.
- Magisk and APatch. `su -c` is the same on all three; only KernelSU was tested.
- Whether a continuous angle is worth a root dependency at all for what the fold animation gains. That is a product
  question for the owner, not a technical one.

## Consequences

- The System Bridge page gains a row, "Live hinge angle (root)", which reads "Not available yet" until a provider is wired.
- Threat model T27 to T31 cover the new trust boundary.
- If this is never built, the finding stands: on this phone a live angle is root-only, and Folio's stepped prediction is the
  honest ceiling for everyone else.
