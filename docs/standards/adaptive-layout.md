# Adaptive layout standard (`ADP`)

> **Available space decides the layout. Device identity doesn't.**

Folio's README already promises this: it lays out by how much room there is, not what device it is. This standard
makes that enforceable, and it's why Folio can be ready for tri-folds and flip covers without a branch for each.

## Rules

### Measuring

- **ADP-1 MUST** branch on the current window's size (`BoxWithConstraints`, `Configuration.screenWidthDp` /
  `screenHeightDp`, or window metrics) and on reported capabilities. **MUST NOT** branch on `Build.MODEL`,
  `Build.MANUFACTURER`, display IDs, "is a tablet", "is a Fold" or orientation.
- **ADP-2 MUST** multiply by `classScale` before comparing a size to a breakpoint, so a changed Display size or
  Smallest width can't promote a phone to the regular layout.
- **ADP-3 MUST** use the named breakpoints, never a bare number (new code):

  | Name | Rule | Kind |
  |---|---|---|
  | Android width classes | < 600, 600 to 839, 840 to 1199, 1200 to 1599, 1600+ dp | Android |
  | Android height classes | < 480, 480 to 899, 900+ dp | Android |
  | `fitsRegularHomeLayout` | width ≥ 600 and height ≥ 560 | Folio fit (560 is Folio's own, not Android's) |
  | Expanded two-panel Home | regular, width ≥ 650, not taller than wide | Folio fit |
  | `isMicroWindow` | short side ≤ 320 and long side < 480 | Folio tier C |
  | Settings columns | 1 below 700 wide, 3 at ≥ 920 when nested and off the fold, else 2 | Folio fit |

  Android classes and Folio fit rules stay separate; a Folio rule's KDoc says why its number is what it is.
- **ADP-4 SHOULD** compare width with height only through a named helper (`isLandscapeShaped`), not inline.

### The support envelope

- **ADP-5 MUST** work across the envelope:
  - **Tier A** (≥ 320 wide, ≥ 480 high): the full experience, and the Home page fits without scrolling at text
    scale ≤ 1.3.
  - **Tier B** (≥ 320 wide, 320 to 479 high): everything works, reflowed (split columns); content may scroll.
  - **Tier C** (< 320 wide, or a micro cover): focused `MicroHome` mode.
  - **600+**: the adaptive large layout.
- **ADP-6 MUST** add boundary and sweep cases to `ScreenCoverageTest` / `ScreenMatrixTest` for any layout change, not
  device names.

### One continuous interface

- **ADP-7 MUST** keep the same hierarchy folded and unfolded. Unfolding reveals more (Today View beside Home, a
  Settings sidebar, a second page); it never turns into a different launcher.
- **ADP-8 MUST NOT** stretch. With regular width: one column becomes two, a navigation stack becomes a split view,
  a tab bar becomes a sidebar.
- **ADP-9 SHOULD** keep controls reachable at the side edge (the right rail, mirrored for left-handed) and collapse
  into an overflow menu when there's no room, rather than shrinking everything.
- **ADP-10 MUST** keep elements on the same plane from colliding: bars, pills, the island, the Side Bar, the dock and
  the grid never crop or cover each other. Popovers and menus may float over content.

### Folds and hinges

- **ADP-11 MUST** get hinges from Jetpack WindowManager `FoldingFeature`s through `LocalHinge`, as a list (there may
  be two). **MUST NOT** use a tri-fold flag or assume one hinge (`firstOrNull`).
- **ADP-12 MUST** treat a half-open or separating hinge as a protected region: icons, buttons, sheets, alerts and menus
  avoid it (`FoldAvoidingBox`, `foldSafeSpan`). Scrollable content may cross it.
- **ADP-13 MUST** put controls on the bottom half in tabletop, information on the top (`FoldRole.CONTROLS` /
  `INFO`), and the list and detail either side of a book hinge.
- **ADP-14 MUST** treat postures as adaptations, never as the only place a feature exists. StandBy is an extra, not
  the one way to see the clock.
- **ADP-15 MUST** keep the layout correct when fold information is missing: use window geometry alone.

### Insets

- **ADP-16 MUST** draw backgrounds and wallpapers edge to edge, and keep foreground and interactive content inside
  `safeDrawing` (plus the cutout and hidden-camera insets through `folioSafeTop`).
- **ADP-17 MUST** handle each inset side independently. A bar may be on the left in split view or landscape.
- **ADP-18 MUST** respect the IME inset in anything with a text field.

### Windows and input

- **ADP-19 MUST** stay resizable and handle size changes without restarting (`configChanges` in the manifest), and
  keep working in split screen and free-form windows at any size in the envelope.
- **ADP-20 MUST** keep state across a size change: page, open folder, sheet, scroll ([STA](state-data.md)).
- **ADP-21 SHOULD** support keyboard and pointer on large windows ([INT-16, INT-17](interaction.md)); Play treats this
  as part of large-screen quality.

## Checklist

- [ ] No device, model, manufacturer or orientation check
- [ ] Named breakpoints only, scaled by `classScale`
- [ ] Coverage test cases at the breakpoint edges the change touches
- [ ] Cover, inner portrait, inner landscape, half-open book, tabletop, split screen on both sides
- [ ] Left-handed mode mirrored
- [ ] Nothing interactive on the hinge when half open
- [ ] Mockup Lab scene passes its window checks

## Where Folio is today

Good:

- `fitsRegularHomeLayout` with `classScale`, and its KDoc forbids device checks (`LayoutModel.kt:132-159`).
- Hinges as a list with multi-hinge `foldSafeSpan`; `FoldAvoidingBox` used by sheets, alerts, Control Center,
  Spotlight and the Library.
- Tabletop sends the dock below and status above the hinge; the Settings divider sits on the book hinge.
- `ScreenMatrixTest` (25 named windows, both orientations, split halves) and `ScreenCoverageTest` (an 8dp sweep over
  320 to 1600 × 320 to 1200, breakpoint edges, fold, tri-fold and micro sizes).
- Edge-to-edge with `safeDrawing`, cutout mode `always`, and rounded corners from window insets.

Not yet:

- The only device branch: `CameraArea.kt:22-33` (SM-F971 hidden camera). It's a real hardware fact the platform
  doesn't report, so it's a recorded exception, but `DisplayCutout` and `getDisplayShape()` should be read first.
- Bare breakpoints: 650 in four files, 700 / 920 in Settings and `MarketScreen.kt:331-337`, 560 in `TodayView.kt:59`,
  500 height in `LauncherScreen.kt:788` and `DiscoverActivity.kt:406`, 360 in `CustomizationSheet.kt:959`.
- Width-vs-height comparisons inline in `TopPanels.kt:105`, `LockCover.kt:89`, `LayoutModel.kt:215`.
- `rememberHinge` still uses `firstOrNull` in places; posture variants aren't in the coverage test.
- No hover, wheel, right-click or focus rings; no desktop-specific handling.

## Recorded exceptions

| Site | Rule | Why | Remove when |
|---|---|---|---|
| `CameraArea.kt:22-33` | ADP-1 | The Fold8 (SM-F971) inner camera isn't in `DisplayCutout` | The platform reports it |
| `DiscoverBounds.kt` | ADP-1 | Window Extensions 8 to 10 alignment hint, audited | Those versions age out |

## Gaps

| # | Work | Size |
|---|---|---|
| 1 | One `FolioBreakpoints` / size-class helper with the table above; replace the bare numbers | S |
| 2 | `CameraArea`: try `DisplayCutout` and `getDisplayShape()` before the model table | S |
| 3 | Multi-hinge everywhere (drop `firstOrNull`), and posture variants in `ScreenCoverageTest` | M |
| 4 | Geometry fuzz (Screen Coverage v2 step 5): assert icon ≥ 32dp, rows ≥ 48dp, no negative sizes | M |
| 5 | Keyboard, pointer and focus support (shared with INT gaps 2 and 3) | M |
| 6 | Check API 37 behaviour (orientation and resizability ignored on ≥ 600dp) | S |
