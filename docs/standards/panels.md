# Panels standard (`PNL`)

Control Center and Notification Center are the two surfaces people pull down all day, over Home and over other apps. This
standard keeps them fitting, readable, truthful and calm. It builds on [Design](design.md), [Dynamic UI](dynamic-ui.md),
[Interaction](interaction.md), [Adaptive layout](adaptive-layout.md), [Accessibility](accessibility.md) and
[Privacy](privacy-permissions.md); where they disagree about a panel, this one is more specific.

> **A panel fits its window, says only what Android lets it say, and never makes the person work to reach a control.**

(McCal, 25 Oct 2026: "you shouldn't have to scroll in it", "don't overlap things", "it shouldn't look choppy, but better
than Apple", "there should be some standards for the control center and notification center too".)

## Rules

### Fit

- **PNL-1 MUST** keep the Controls pages of Control Center free of scrolling. What does not fit goes to another page (the
  page strip counts it), never to a scroll. Lists, such as the notification list, scroll: that is what a list is for.
- **PNL-2 MUST NOT** overlap: the header, the pages, the page strip, the plate, the system bars, the camera cutout and the
  hinge never cover one another. When a window is too short, first move the chrome (the page strip goes into the header),
  then paginate; do not shrink a tile below its floor.
- **PNL-3 MUST** work the size of every cell, gap and column out of the window and the size the person chose
  (`controlCenterGrid`), never out of a device name ([ADP](adaptive-layout.md)). No fixed ceiling on a list's height; a card
  is at most two thirds of the window.
- **PNL-4 MUST** prove PNL-1 and PNL-2 by test: every screen in `screen-matrix.json`, both orientations and as a split
  half, at each size, with the starting controls and with all of them (`ControlCenterFitTest`).

### Readability

- **PNL-5 MUST** give a tile a 48 dp touch target ([A11Y](accessibility.md)); its picture may be smaller but not under 40 dp.
- **PNL-6 MUST** keep white text on the starting look at 4.5:1 or better over any wallpaper or app (WCAG 1.4.3), measured, not
  judged. Measured on the Lab's backdrop: Clear glass 3.5:1, Frosted 6.9:1, Solid 10.6:1. A look that can fall below 4.5:1
  says so beside its setting (Clear glass does) and is never the default.
- **PNL-7 MUST** take every color and radius from `FolioColors`, `FolioGlass` and `FolioRadius`; no new raw values.

### Backdrop and motion

- **PNL-8 MUST** draw the backdrop once: one screenshot, shrunk and blurred once off the main thread, drawn as an image.
  Never blur per frame and never per tile. Where a picture is not possible (a secure window, the service not allowed, a slow
  phone) the frosted stand-in shows, so the panel never waits on it.
- **PNL-9 MUST** cover every pixel of the window with the dim, edge to edge, including under the cutout and the bars. Check it
  by measuring the dim against Home row by row (it was 0.57 at every row on the Fold 8 cover and the phone emulator).
- **PNL-10 MUST** open and close on springs from `FolioMotion`; the blur and the dim fade in and out rather than pop;
  Reduce Motion fades instead of sliding ([DYN-6, DYN-11](dynamic-ui.md)). Nothing flashes or strobes (WCAG 2.3.1).
- **PNL-11 SHOULD** hold the first frame back rather than show a jump: if the backdrop arrives within about a quarter of a
  second it fades in, and if not the frosted stand-in stays.

### Over other apps

- **PNL-12 MUST** make the panel window touchable only while the panel is open; while it closes, taps go to the app beneath.
- **PNL-13 MUST** cancel any request still waiting (a screenshot, a timer) when the panel is removed, so nothing adds a window
  afterwards (found in review of PR 482: Home coming to the front left an invisible window that swallowed touches).
- **PNL-14 MUST** open through an activity of Folio's own where the phone only blurs behind a window with one in front.

### Truth

- **PNL-15 MUST** show only the states Android gives an app without a special permission: Wi-Fi and Bluetooth on or off, the
  carrier name. The Wi-Fi network name needs the location permission, so the tile says "Connected" unless it is granted. Which
  app is using the location is not readable without a privileged permission: leave that chip out rather than fake it.
- **PNL-16 MUST NOT** claim a switch Folio cannot flip. Where Android has no direct way, the tile opens the system's own
  panel for it, and the shortcut to Android's Quick Settings stays.
- **PNL-17 MUST** list only controls the phone can run (no flashlight tile without a torch).

### Controls and customizing

- **PNL-18 MUST** keep Control Center's layout as one model: each control has a place and a size (`CcSlot`), and the
  sizes a control may take come from one table (`ccAllowedSizes`). Reordering, resizing, adding and removing use that model.
- **PNL-19 MUST** resize from a corner handle as in [INT-20](interaction.md); a drop on a taken place, or a size that does
  not fit, leaves the layout as it was. No control moves by itself.
- **PNL-20 SHOULD** show editing as a still outline with a minus on each removable tile and a gallery to add from, searchable
  by name. Under Reduce Motion nothing moves except the tile being dragged.
- **PNL-21 MUST** put a new setting for either panel in Settings with a search entry, and draw it in the Lab first.

### Notification Center

- **PNL-22 MUST** share one presentation of a notification between the Dynamic Island and Notification Center: a message can
  appear in the island, then wait in the list, and read as the same thing.
- **PNL-23 SHOULD** offer Count, Stack and List styles with conversations and apps grouped, a quiet section folded away
  with its count, and every action (open, snooze, clear) reachable by a visible control as well as a gesture
  ([INT-2](interaction.md)). Clearing a whole group asks first.
- **PNL-24 SHOULD** let a style set per app inherit the global one: a global default, an override per app, the override
  shown beside the default it changes.
- **PNL-25 MUST** keep previews private: they follow the lock state and the preview setting, a protected or sensitive
  notification stays hidden while the screen is locked or recorded, and nothing leaves the phone ([PRV](privacy-permissions.md)).
- **PNL-26 MUST** read notifications only through the system's notification-access setting, and say what it is for.

### Ideas taken from tweaks

- **PNL-27 SHOULD** treat a jailbreak tweak as a reference for behaviour, not code: take the idea (a per-app style with a
  live preview, grouping, a layout of controls), write it on Folio's own model, and never copy source. Many of those tweaks
  are closed source or under licenses Folio cannot use.
- **PNL-28 MUST** apply a changed setting at once and show it in a live preview on the Settings page, with no restart.
- **PNL-29 MUST NOT** let a Market package change either panel with code. A package names the settings it sets
  ([MKT](market.md)); the panel decides what is allowed.

## Where Folio is today

Good:

- `controlCenterGrid` sizes columns, cells and gaps from the window; the three pages and the strip at the edge or under
  the grid; a look setting (Clear glass, Frosted, Solid); one screenshot blur drawn once; the dim covers the window.
- Notification Center takes the room the window has; groups, sections, a Quiet section, actions on each notification.
- `ControlCenterLayout.kt` holds the placement rules (move, resize, snap, add, remove) with tests, not yet drawn.

Not yet:

- 86 of the matrix's window and size combinations would still scroll the Controls page (`ControlCenterFitTest`): short
  landscape windows and split-screen halves, and 15 controls on a small phone. Fix: the strip into the header, then pages.
- The Island and Notification Center use two notification presentations.
- No per-app notification style and no live preview.
- Editing is Home's wiggle with a gallery under the grid, which scrolls.

## References

Outside research, 25 Oct 2026, not all independently verified (pasted by McCal; the sources are named, so check before
relying on a detail):

- Apple's iPhone Duo and iOS 27 layout: the side-mounted controls, two Home pages, the vertical island and the corner status
  circle. Control Center's groups, resizing and navigation (Apple Support).
- Jailbreak tweaks, as ideas only: Velvet 2 (a notification style per app, with a live preview), Grupi (grouping), Prysm
  (organizing controls around frequent use), Axon (the row of app icons that filters notifications, already in Folio),
  Lunar Reborn (clock styles), Griddy and SBTweaker (finer Home placement).
- Android: `NotificationListenerService` (dismiss and snooze), `TileService` (Android's own Quick Settings), `KeyguardManager`
  (cannot replace the secure lock screen), the accessibility screenshot (API 30), cross-window blur (only where the phone
  turns it on).
- WCAG 2.2: 1.4.3 contrast (4.5:1), 1.4.11 non-text contrast (3:1), 2.3.1 three flashes, 2.5.8 target size.
- 60fps.design for motion.
