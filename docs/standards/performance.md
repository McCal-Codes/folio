# Performance standard (`PRF`)

Folio sits under every other app. "It works" isn't enough: Home has to feel immediate every time you come back to it.
Both Fold8 screens run at 120 Hz, so a frame has 8.3 ms.

## Critical journeys

These are the moments people feel. Each needs a measurement before anyone claims it's fast, and each should be covered
by the baseline profile.

| # | Journey | Measured? |
|---|---|---|
| J1 | Cold start to a usable Home | Yes, 30 Sep 2026: see below |
| J2 | Home page swipe (folded and unfolded) | Yes, 21 Sep 2026: see below |
| J3 | Fold / unfold transition | No |
| J4 | Open Spotlight, first result frame | No |
| J5 | Open and close a folder | No |
| J6 | Open the App Library, first frame | Profile recorded, not timed |
| J7 | Enter and leave jiggle mode | No |
| J8 | Swipe a widget-heavy page | No |
| J9 | Open and close Control Center | No |
| J10 | Return Home from an app | No |

Baseline, J2 on the Fold8 inner screen (`fast` build, 20 swipes, `dumpsys gfxinfo`): dunes wallpaper 5.1% janky
frames, P50 9 ms, P90 16 ms, P99 25 ms; Android wallpaper 4.5%, 5 / 8 / 19 ms.

Baseline, J1 on the Fold8 cover screen (`fast` build compiled with its baseline profile, Home started after an update,
medians of 5): first frame 80 ms, fully drawn 899 ms (827 to 1,151). Uncompiled, as it is right after an update (one
run): 114 ms and 1,501 ms. Fully drawn missed the 400 ms target because it waited for the app list: one background
thread loaded every app's icon in turn (about 250 apps and shortcuts), 587 ms compiled, mostly decoding icon images
(224, 304 ms) and making a `Resources` for each app (253, 156 ms). Reading and decoding the saved layout takes under
half a millisecond and a save 0.3 ms, so neither is worth moving off the main thread. Method: `atrace` with Folio's
trace sections and Android's "Displayed" and "Fully drawn" times. An update is what restarts Folio Dev between runs,
because force-stop turns its accessibility service off. After Home's own apps load first (same method, 30 Sep 2026):
first frame 78 ms, fully drawn 157 ms (143 to 236). Loading Home's 23 apps takes 127 ms beside the first frame; the
rest of the list still lands at about 0.8 s, as before. With icons saved between starts (same method, the cache filled
by an earlier start): Home's apps in at 53 ms (was 139), fully drawn 120 ms (was 157), the full list at 317 ms (was
824). A start with nothing saved yet saves every icon once, 435 ms in the background after the list is shown.

### Targets (proposed; the maintainer confirms before they're enforced)

- J1, J4, J5, J6: first meaningful frame under 400 ms (the Doherty threshold). Where the work really takes longer,
  show a placeholder, not a blank screen.
- J2, J3, J8: no more than 1% janky frames at 120 Hz, P90 under 8.3 ms.
- No regression over 10% on a measured journey without a note in the PR.

## Rules

### Measuring

- **PRF-1 MUST** measure before optimising, and put the before and after numbers in the PR.
  A change behind a closed `FeatureGate` may merge without them only while nothing can reach it: no screen sets it, and
  no Folio Dev or beta supporter build can turn it on (REL-4a opens gated features to those builds the day they merge).
  The PR says the numbers are still to come, and they are required in the PR that makes the change reachable. A change
  that supporters can already reach, or that everyone runs, needs them before it merges.
- **PRF-2 MUST NOT** draw performance conclusions from a debug build. Use the `fast` build (R8, release-like) or
  release.
- **PRF-3 MUST** give a new critical journey baseline profile coverage in `StartupProfile.kt` (or a sibling test) and
  re-record the profile when a journey's code changes a lot.
- **PRF-4 SHOULD** add a Macrobenchmark with `FrameTimingMetric` or `StartupTimingMetric` for any journey whose
  performance a change claims to improve.

### Compose

- **PRF-5 MUST** give lazy lists and pagers stable `key`s.
- **PRF-6 MUST NOT** do repeated expensive work in composition: sorting, filtering, bitmap decoding or icon loading go
  in `remember(keys)`, the model, or a background dispatcher.
- **PRF-7 SHOULD** read fast-changing values as late as possible: in `graphicsLayer { }`, `drawBehind`, or lambda
  `offset { }`, not as composable parameters ([DYN-16](dynamic-ui.md)).
- **PRF-8 SHOULD** keep state split so a clock tick or battery change recomposes only its own element.
- **PRF-9 SHOULD** keep nearby Home pages composed during a swipe (native widget reinflation is expensive), and keep
  far pages unloaded.

### System

- **PRF-10 MUST NOT** poll when a callback exists; stop work when invisible ([DYN-14, DYN-15](dynamic-ui.md)).
- **PRF-11 MUST** keep the release build on R8 with resource shrinking; new reflection needs a keep rule and a
  comment.
- **PRF-12 MUST** check new animations and gestures for jank on a real 120 Hz device before release.
- **PRF-13 SHOULD** trace new heavy work with `androidx.tracing` sections so Perfetto shows it by name.

## Where Folio is today

Good:

- R8 and resource shrinking on release; a `fast` build type that installs as Folio Dev beside the real one.
- A baseline profile recorded on the Fold (`baselineprofile/`, `StartupProfile.homeAndFirstGestures`: cold start,
  App Library, scroll, back), shipped in the APK.
- Keyed pagers and most keyed lists; visual transforms mostly in `graphicsLayer`; no per-frame blur.
- `DuoMotionTrace` logs gesture timing in debug builds (`setprop log.tag.DuoMotion DEBUG`).
- Trace sections on the layout load and save (`Folio.readState`, `Folio.decodeState`, `Folio.persist`), Home reports
  fully drawn once the first app list is in, and the `fast` build is profileable, so all of it shows in Perfetto.

Not yet:

- One journey in the profile; no timing benchmarks (no `FrameTimingMetric`, no startup metric).
- No JankStats.
- No Compose compiler reports or stability config.
- A refresh after a package change still loads every app's name to check it (about 0.45 s in the background on the
  Fold8), and pinned shortcuts and quiet work apps aren't saved between starts.
- J2 jank is mostly UI-thread spikes (recomposition), not GPU; see [STA gap 4](state-data.md).
- Unkeyed `HorizontalPager` in `MarketFeatured.kt:99`.

## Gaps

| # | Work | Size |
|---|---|---|
| 1 | A `FrameTimingMetric` swipe benchmark (J2), then Perfetto on the worst frames (the 0.7.1 Home swipe work) | M |
| 2 | Startup benchmark (J1); `reportFullyDrawn` is in | S |
| 3 | Extend the baseline profile to J3 to J9 | M |
| 4 | Compose compiler reports once, to find unstable parameters on Home | S |
| 5 | `Trace` sections around icon loading and widget inflation (the layout load has them) | S |
| 6 | Confirm or change the targets above | S |
| 7 | Check names without loading each app's resources, and save shortcuts and quiet work apps between starts too | M |
