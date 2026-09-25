# Dynamic UI standard (`DYN`)

A launcher is mostly live surfaces: the island, the Side Bar, live icons, badges, install rings, Smart Stacks, jiggle
mode, dock magnification, folds. A normal app could skip a standard like this. Folio can't.

> **State decides, motion explains.** Dynamic behaviour is driven by meaningful state. Animation shows the change.
> Animation never owns the state.

## The pipeline

Every dynamic element follows the same shape:

```
system / app / user event
        ↓
source state            (one owner, see state-data.md)
        ↓
derived UI state        (semantic, finite)
        ↓
visual state            (sizes, colors, text, semantics)
        ↓
animation / transition  (optional; can be skipped entirely)
```

Example: `DeviceStatus` gets a battery broadcast → `battery = 23, charging = true` → a low-and-charging gauge with its
content description → the gauge animates over `FolioMotion.GAUGE_MS`, or snaps when Reduce Motion is on.

The wrong shape is the reverse: a frame callback or an animation end listener setting `charging = true`.

## Classes

Every dynamic feature declares one class in its KDoc or PR. The class sets which rules matter most.

| Class | What changes it | Folio examples |
|---|---|---|
| **D1 Reactive** | A value changes; little or no spatial motion | Battery and signal, badge counts, calendar date, theme colors |
| **D2 Transitional** | One UI state becomes another | Folder open, sheets, island expand, Control Center, context menus |
| **D3 Interactive** | Motion follows a finger or pointer | Page swipe, drag and drop, dock magnification, widget resize, predictive back |
| **D4 Adaptive** | The window or posture changes | Fold and unfold, rotation, resize, multi-window, half-open hinge |
| **D5 Ambient** | Time or the system, with no user action | Clock icon, sunrise/sunset appearance, install rings, Smart Stack rotation, recent-app dots |

Declaration template (for the KDoc or PR):

```
Dynamic class: D3 Interactive
Source of truth: pointer position (transient, owned by the Dock)
Persistent state: none
Reduce Motion: scale capped at 1.0 (no magnification)
When not visible: no work (pointer-driven)
Animation owner: HomeTiles dock row
```

## Rules

### State

- **DYN-1 MUST** model dynamic states as a sealed type or enum when there are more than two, not as a set of booleans
  that allow impossible combinations. `IslandActivity` and `IslandContent` are the model.
- **DYN-2 MUST NOT** change source state from an animation callback, frame count or animation end. Animations read
  state; they don't write it.
- **DYN-3 MUST** give every dynamic element a static, understandable form: what the user sees with animations off, with
  the data source unavailable, and on first frame.
- **DYN-4 MUST** degrade when a source goes away: no album art still shows media controls, no location still resolves
  an appearance, no hinge information falls back to window geometry, an unknown battery shows a generic glyph.

### Motion

- **DYN-5 MUST** have a reason to move: causality (the icon travels into the folder), continuity (the island is one
  object growing), hierarchy (a sheet rises over its source), feedback (a switch settles), or state (the gauge fills).
  Decoration alone isn't a reason.
- **DYN-6 MUST** take springs from `FolioMotion` and apply `MotionSpeed` (new code). A new motion need gets a new named
  spring in `FolioTokens.kt` with a one-line comment on what it's for; call sites don't pick numbers.
- **DYN-7 SHOULD** prefer springs over `tween` for anything spatial. `tween` is for opacity, color, and fixed-length
  loops (jiggle, the offline sweep).
- **DYN-8 MUST** be interruptible: a new target retargets the running animation. Nothing waits for an animation to
  finish before accepting a valid state change. Use `animate*AsState`, `Animatable.animateTo` or
  `AnimatedContent`, which all retarget.
- **DYN-9 SHOULD** carry velocity across an interruption for D3 motion (a flick that reverses mid-settle keeps its
  momentum).
- **DYN-10 MUST** grow transient surfaces from their source and collapse them back into it (menus, folders, sheets,
  the island), so the user never loses where they were.
- **DYN-11 MUST** respect Reduce Motion through `LocalReduceMotion`: spatial motion becomes a fade or a snap, loops
  stop, and the fold effect is off. Never read the system setting directly in a component.

### Adaptive transitions (D4)

- **DYN-12 MUST** compute the destination layout from window and posture alone. The fold animation is only the
  journey; turning it off must land on exactly the same layout.
- **DYN-13 MUST** keep identity across a D4 change: the same page, the same open folder, the same scroll position
  where the content still exists.

### Cost

- **DYN-14 MUST NOT** poll when Android offers a callback or broadcast. Where a timer is the only source (the clock),
  use the shared `Ticker` flow, aligned to the second or minute, not a private `while (true)` loop.
- **DYN-15 MUST** stop dynamic work when it isn't visible: collect with `collectAsStateWithLifecycle`, register
  callbacks in `onStart` / unregister in `onStop`, or gate loops with `repeatOnLifecycle`.
- **DYN-16 SHOULD** apply visual-only changes in the draw or layer phase (`graphicsLayer { }`, `drawBehind`, the
  lambda forms of `offset` and `alpha`) so a moving value doesn't recompose its neighbours.
- **DYN-17 SHOULD** turn fast-changing inputs into coarse states with `derivedStateOf` before they reach composition
  (a pointer position becomes "which icon is nearest").

### Meaning

- **DYN-18 MUST** update semantics with visuals: a changed state changes its `stateDescription` or content description,
  and important changes (island notices, page changes) use a polite live region.
- **DYN-19 MUST** keep touch targets stable while something moves. A magnified or jiggling icon hits where it was.
- **DYN-20 SHOULD** be deterministic enough to test: time comes from `Ticker` or an injected clock, and the
  state-to-visual mapping is a pure function.

## Acceptance checklist

- [ ] Declared class (D1 to D5) and the template above
- [ ] One source of truth; the events that change it are listed
- [ ] States are a sealed type or enum, not loose booleans
- [ ] Behaviour when the source is unavailable
- [ ] Correct after rotation, fold and resize
- [ ] Motion has a stated reason
- [ ] Interruptible; retargets instead of queueing
- [ ] Correct with animations off (`adb shell settings put global animator_duration_scale 0`)
- [ ] No unrelated recomposition (check with Layout Inspector's recomposition counts)
- [ ] No work while invisible
- [ ] Semantics change with the visuals; TalkBack reads the new state
- [ ] Touch target stable while moving
- [ ] Testable without real time

## Where Folio is today

Good:

- Sealed state for the island (`Island.kt:80`, `:116`, `CutoutIsland.kt:69`), exposed as `StateFlow`s.
- `DeviceStatus` is callbacks only, registered on start and removed on stop (`DeviceStatus.kt:68-97`).
- `Ticker` is one shared clock with `WhileSubscribed(0)`; install rings come from `PackageInstaller.SessionCallback`.
- Jiggle is one shared infinite transition read in `graphicsLayer` (`Jiggle.kt:69-88`); dock magnification scales in
  `graphicsLayer` (`HomeTiles.kt:143-215`). 78 `graphicsLayer` uses against 10 `.alpha(`.
- `LocalReduceMotion` is honoured in 14 files, including jiggle, entrances and the fold effect.
- `rememberEntrance` / `rememberSettlingProgress` snap to the end if frames stall.

Not yet:

- `FolioMotion` is used 4 times. About 40 other springs pick their own numbers: 13 damping ratios and 11 stiffness
  values, plus 12 tweens.
- `FolioSheet.kt:161` uses a tween for the sheet, so it doesn't retarget like a spring.
- Page settle (`PageGestures.kt:212`) drops the previous settle's velocity when a new drag starts.
- Private loops: `EverywhereOverlay.kt:184` still polls every 600 ms for a full-screen app. The Smart Stack rotation
  runs under `repeatOnLifecycle(RESUMED)` now, and `StatusRail` and `MicroHome` take the shared minute tick.
- Reduce Motion is binary (scale exactly 0), and `DiscoverFrame.kt:44` reads the setting itself.
- 16 `collectAsState()` left, in `CustomizationSheet.kt`, `StandBy.kt` and `EverywhereOverlay.kt`; everything else
  collects with the lifecycle, the island included.
- Only 3 polite live regions; island notices and page changes are silent to TalkBack.

## Gaps

| # | Work | Size |
|---|---|---|
| 1 | Name the springs Folio actually needs (`Settle`, `Quick`, `Firm`, plus `Sheet`, `Menu`, `Bounce` for switches) and move call sites onto them | M |
| 2 | Sheet motion on a spring, growing from the source, with detents | M |
| 3 | ~~Smart Stack rotation behind `repeatOnLifecycle`; `StatusRail` and `MicroHome` clocks onto `Ticker`~~ (done) | S |
| 4 | ~~`collectAsState` → `collectAsStateWithLifecycle`~~ (done for the island, the cover, installs, notifications and the update status). Left: `CustomizationSheet.kt`, `StandBy.kt` (open in other pull requests) and `EverywhereOverlay.kt`, whose window has no lifecycle to follow | S |
| 5 | `DiscoverFrame` onto `LocalReduceMotion` rather than reading the setting itself. **Not** "a scale below 1 counts as Reduce Motion", as this row used to say: a scale of 0.5 means the person wants animations *faster*, not gone. Honouring the scale as a speed, alongside Animation Speed, is the right shape and is its own decision | S |
| 6 | ~~Live regions for island notices and page changes~~ (done) | S |
| 7 | Carry velocity into a re-grabbed page settle (part of the 0.7.1 Home swipe work; measure first) | M |
| 8 | Replace the 600 ms full-screen poll in `EverywhereOverlay` with a window-insets or accessibility-event signal, if one proves reliable | M |
