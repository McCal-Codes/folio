# Interaction standard (`INT`)

A launcher is almost entirely interaction. The same finger on the same page might mean "next page", "scroll this
widget", "pick up this icon" or "open Spotlight". This standard keeps those meanings stable.

## Rules

### Meaning of each gesture

- **INT-1 MUST** keep the iOS vocabulary:

  | Input | Means |
  |---|---|
  | Tap | The obvious action (open, toggle, select) |
  | Long press | More about this object: its context menu, then pickup if the finger moves |
  | Horizontal swipe on Home | Exactly one page (Discover and Today View at the leading edge) |
  | Swipe down on Home | Notifications (top left), Control Center (top right), Spotlight (elsewhere); configurable |
  | Swipe up | App Library, or an icon's panel where enabled |
  | Back / predictive back | Close the top-most transient surface, then leave editing |

- **INT-2 MUST** give every gesture a non-gesture path: a menu item, a button, a setting, or a TalkBack action.
  Gestures are accelerators, never the only way.
- **INT-3 MUST NOT** change what an existing gesture means without a setting that keeps the old meaning, and a line in
  the changelog.

### Arbitration

- **INT-4 MUST NOT** fix one gesture by consuming every pointer event. That breaks another path. (This is the rule from
  [architecture.md](../architecture.md), made formal.)
- **INT-5 MUST** decide the axis once, after touch slop (`viewConfiguration.touchSlop`), and then keep it for the rest
  of the gesture.
- **INT-6 MUST** let native widget content keep vertical scrolling. Page swipes are horizontal; the swipe-down action
  yields to scrollable widgets, stacks, the dock while magnifying, and the page scrubber (`canStartDownwardSwipe`).
- **INT-7 MUST** follow this priority, highest first:
  1. An open transient surface (sheet, menu, folder, Control Center) owns input.
  2. An active drag or resize.
  3. A native widget's own scrolling.
  4. Object gestures (long press, icon swipes).
  5. Page gestures.
- **INT-8 MUST** pick drop targets by fixed priority (folder over widget over empty cell), as `HomeDrag` does.

### Cancellation

- **INT-9 MUST** cancel cleanly: a second pointer, a lost pointer, a consumed event, entering edit mode or a D4 window
  change ends the gesture and settles to a valid state (nearest page, original slot).
- **INT-10 MUST** clear drag state on coroutine cancellation (`CancellationException`), never leave a floating icon.
- **INT-11 SHOULD** let a new gesture catch an animation in flight (grab a settling page) instead of waiting.

### Thresholds

- **INT-12 MUST** express thresholds in dp or as a fraction of the page, never pixels. Folio's current values are the
  reference: page release at 250dp/s or min(20% page, 72dp); icon swipes at 28dp with a 0.6 axis ratio; edge paging
  at 30dp with a 650 ms hold; Discover dismiss at min(72dp, 25% width).
- **INT-13 SHOULD** keep one page per swipe (`PageGestureLimits`) in every layout, paired or single.

### Haptics

- **INT-14 MUST** go through one vocabulary (new code), mapped to meaning rather than chosen per call:

  | Meaning | Haptic |
  |---|---|
  | Picked up / context menu opened | `LongPress` |
  | Crossed a step (page, drop target, picker value) | `SegmentTick` |
  | Continuous scrub (dock magnification, slider) | `SegmentFrequentTick` |
  | Toggled | `ToggleOn` / `ToggleOff` |
  | Committed (drop, confirm) | `Confirm` |
  | Refused (full dock, invalid drop, remove) | `Reject` |
  | Gesture finished past its threshold | `GestureEnd` |

- **INT-15 MUST** honour the Haptics switch (`NoHaptics`), and **SHOULD** save haptics for moments that matter; a
  haptic on every tap is noise.

### Keyboard, mouse and trackpad

- **INT-16 MUST** make every action reachable by keyboard (new code): Tab / arrow focus, Enter to activate, Escape to
  close a transient surface, and a visible focus ring.
- **INT-17 SHOULD** support pointer input on large and desktop windows: hover highlights, right-click as long press,
  scroll wheel for pages, and a pointer icon change over draggable items.
- **INT-18 SHOULD** keep Alt+arrows and PageUp/PageDown for moving Home items (`HomeWorkspace.moveActions`) and extend
  the same pattern to folders and the dock.

### Feedback

- **INT-19 SHOULD** show feedback next to the object it's about (island pills, inline undo) rather than a `Toast` or
  modal `AlertDialog`. Keep dialogs for what can't be undone.

## Where Folio is today

Good:

- `PageGestures.kt` decides the axis after slop on the Initial pass, clamps to one page, and settles to the nearest
  page on any cancellation.
- Widget arbitration is careful: `WidgetVerticalGestures.kt` finds vertical scrollers, `ZeroPaddingWidgetHost.kt`
  keeps them, and the widget long press tolerates provider move events.
- Drop priority and edge paging in `HomeDrag` / `LauncherScreen.kt:426-440`.
- Predictive back for folders, the App Library and Settings, with sheet progress on a spring.
- TalkBack move actions mirrored by Alt+arrows.
- A haptic switch, and haptic types that already mostly match the table.

Not yet:

- 29 haptic calls pick their type locally; there's no `FolioHaptics`. Jiggle remove uses `ContextClick` where `Reject`
  fits (`Jiggle.kt:98`).
- No hover, right-click, scroll wheel or pointer icon support; no focus rings.
- About 15 plain `BackHandler`s without predictive progress.
- About 26 `Toast` / `AlertDialog` calls for feedback that could sit next to the object.

## Gaps

| # | Work | Size |
|---|---|---|
| 1 | ~~One haptic vocabulary~~ (done: `FolioHaptic` and `haptic.perform(meaning)`; all 17 call sites moved, and removing an app in jiggle mode refuses rather than clicks) | S |
| 2 | Keyboard focus and focus rings across Home, the dock, folders and Settings | M |
| 3 | Pointer support: hover, right-click menu, wheel paging | M |
| 4 | Remaining `BackHandler`s onto `PredictiveBack` where there's something to animate | S |
| 5 | Result dialogs → island notices with Undo | M |
