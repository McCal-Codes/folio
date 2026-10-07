# Performance logging

Two tools answer "what does Folio cost?". They measure different things, so use both when a number matters.

| | In-app Performance log | `tools/perf-capture.sh` |
|---|---|---|
| Who runs it | Anyone, from Settings | A developer, with the phone on adb |
| Whose numbers | Folio's own view of itself | Android's own account (the OS) |
| Needs | Nothing (no permission, no root) | `adb`, USB debugging |
| Changes the phone | No | Only resets Android's battery and frame counters |

## 1. The Performance log (in the app)

Settings, Advanced, Diagnostics, **Performance log** card. It is off until you tap **Start recording**.

While it runs it reads, about every 5 seconds:

- Folio's CPU time, turned into a percent of one core (100 means one core fully busy; an 8-core phone can read up to 800).
- Memory: PSS (total, native, Java) and the Java heap in use, plus thread count. PSS is the dear reading, so it is taken every 30 seconds and carried over between.
- Battery: level, charge counter (uAh), current now, status, plugged, temperature.
- Frame timing, only while Folio is in front (`Window.addOnFrameMetricsAvailableListener`): total frames, janky frames (past the frame's own deadline), and p50, p95 and p99 frame time. First-draw frames are left out.
- Context: how long Folio was in front, the screen was on and the phone was charging, and how many folds or unfolds were seen.

It stops by itself after 6 hours, or when you tap **Stop recording**. Then **Copy Report** and **Share Report** appear (plain text only; the share sheet gets the text and nothing else). Nothing is sent anywhere unless you share it. The numbers live in memory: if Android ends Folio's process, the run is lost.

### Reading the report

- **CPU average** is total CPU time over the run divided by the run's length. Idle should be close to 0. **Peak** is the busiest single 5-second reading.
- **Drain** is the whole phone's battery fall per hour, not just Folio's. It is shown only if the phone was never plugged in and the run was at least 30 minutes, and it is rough: the level moves in whole percents. The mAh figure comes from the charge counter where the phone has one, and is finer.
- **Jank** is the share of frames that missed their deadline. Percentiles are rounded up to the half millisecond. Frame stats only exist while Folio is on screen; a run spent mostly idle has few or none.
- **Memory**: PSS is what Android charges Folio for, shared pages split between users. The peak is the largest reading, but readings are 30 seconds apart.
- **Per minute table**: one row per minute. `front%` and `screen%` say how much of that minute Folio was in front and the screen was on, so you can match a CPU or jank bump to what you were doing.
- **Folds** are counted from big changes of window size (the phone folding or unfolding). The fold animation itself has no hook yet; `PerfLog.noteFold()` is there to call from it.

To compare cases (idle, in Home, folding, the optional root hinge helper), make one run for each and read the same lines.

## 2. `tools/perf-capture.sh` (OS-attributed)

```
tools/perf-capture.sh [-p com.mccal.folio] [-d SECONDS] [-s ADB_SERIAL] OUTPUT_DIR
```

- `-p` defaults to `com.mccal.folio.dev`; pass `com.mccal.folio` for the release app.
- `-d` is the duration in seconds (default 600).
- `-s` picks a phone when several are connected.

It runs `dumpsys batterystats --reset` (and `dumpsys gfxinfo <package> reset`) first, waits, then reads `dumpsys batterystats`, `dumpsys cpuinfo`, `dumpsys gfxinfo <package>` with `framestats`, and `top -H -b -n 2 -d 3` for the app's process. Those resets are the only changes it makes; it never touches a setting, installs anything or uses root. It saves the raw output in `OUTPUT_DIR` and prints a short `summary.txt`.

Unplug the phone, start the script, then do what you want measured while it waits (leave it alone for an idle figure). The app must be running when you start; if it restarts during the run the script says so.

### Reading the summary

- **Estimated power use** (batterystats, per app) is Android's own attribution of mAh to Folio, from CPU, wakelocks and so on. It is the figure to trust for "what did Folio use".
- **cpuinfo** is the OS's load for the package over its own recent window (user plus kernel).
- **gfxinfo** counts frames since the reset; framestats in the raw file has every frame's timings.
- **top -H** names the busiest threads, which is where to look when CPU is high.

## The caveat

The in-app numbers are Folio's own view: Folio measuring itself, on a thread that costs a little, with a log that only runs when it is on. `batterystats` is the OS's view and covers the whole phone, with Folio's share attributed by Android. They will not match exactly, and when they disagree, trust batterystats for battery and the in-app frame figures for jank. The in-app log's value is that anyone can run it without a computer.
