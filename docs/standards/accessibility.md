# Accessibility standard (`A11Y`)

Accessibility shapes the components, not a clean-up pass at the end. These rules follow Android's core app quality
guidelines, WCAG 2.2 AA and Apple's HIG.

## Rules

### Targets

- **A11Y-1 MUST** give every interactive element a hit area of at least 48 × 48dp. The glyph can be smaller; grow the
  hit area with `minimumInteractiveComponentSize()` or padding.
- **A11Y-2 MUST** keep hit areas from overlapping, and stable while their element moves ([DYN-19](dynamic-ui.md)).

### Screen readers

- **A11Y-3 MUST** give icon-only controls a content description from `strings.xml`, saying what it does ("Close
  folder"), not what it looks like.
- **A11Y-4 MUST** hide decorative images from TalkBack (`contentDescription = null`) and merge a row into one node
  where it reads as one thing.
- **A11Y-5 MUST** expose state with semantics, not color or position alone: `Role.Switch` with `toggleableState`,
  `selected`, `stateDescription` for things like "Page 2 of 4", `heading()` for section titles.
- **A11Y-6 MUST** give every drag or gesture an accessible alternative: custom actions (like `moveActions`), a menu
  item, or a button ([INT-2](interaction.md)).
- **A11Y-7 MUST** announce important changes with a polite live region: island notices, page changes, a finished
  install, an undo offer.
- **A11Y-8 SHOULD** keep TalkBack order the same as visual order, including across the two panes when unfolded.

### Seeing

- **A11Y-9 MUST** meet contrast of 4.5:1 for normal text and 3:1 for large text, icons and control boundaries,
  including over a bright wallpaper. `FolioColors.SecondaryLabel` (white at .6) and white at .55 need a scrim or a
  stronger value on light backgrounds.
- **A11Y-10 MUST NOT** use color as the only signal (a red dot also has a shape or label; an active Focus also has
  text).
- **A11Y-11 MUST** support Reduce Transparency and high contrast through `LocalSolidGlass`.

### Text size

- **A11Y-12 MUST** use `sp` for text and keep layouts working at 200% font scale: text wraps, rows grow, nothing is
  cut off. Prefer wrapping to `maxLines = 1` with an ellipsis where there's room.
- **A11Y-13 MAY** cap scaling only for glanceable chrome where the system does too (status icons, the Side Bar clock),
  and must say so in a comment.

### Motion

- **A11Y-14 MUST** honour Reduce Motion through `LocalReduceMotion` ([DYN-11](dynamic-ui.md)).
- **A11Y-15 MUST NOT** flash more than three times a second.

### Language and direction

- **A11Y-16 MUST** keep every user-visible string translatable, with plurals as plurals ([DES-21](design.md)).
- **A11Y-17 SHOULD** work right to left: use `start` / `end`, AutoMirrored icons, and never hard-code
  `LayoutDirection.Ltr` except for drawing that really is direction-free. Folio's left-handed mode is a separate choice
  and must not replace RTL.

### Input

- **A11Y-18 MUST** be operable by keyboard and Switch Access where it's operable by touch
  ([INT-16](interaction.md)).
- **A11Y-19 SHOULD** use `getRecommendedTimeoutMillis` for anything that disappears on its own.

## Checklist

- [ ] TalkBack: every control reachable, named, and its state read
- [ ] 200% font scale in the cover and inner windows
- [ ] Contrast checked over a white and a black wallpaper
- [ ] Animations off
- [ ] Reduce Transparency / high contrast
- [ ] Keyboard only

## Where Folio is today

Good:

- 48dp rows and dock minimums (`LayoutModel.kt`), 44dp jiggle remove, `minimumInteractiveComponentSize` in 7 files.
- 77 content descriptions, 62 semantics blocks, custom move actions mirrored on the keyboard.
- `LocalReduceMotion` and `LocalSolidGlass` exist and are used.
- `getRecommendedTimeoutMillis` for Market notices.

Not yet:

- Two targets are fixed: the widget options close draws a 32 dp circle inside a 48 dp tap, and the search field's
  clear is as wide as the row is tall (40 x 48), since that row's height is fixed by design. `FolioTouch.MIN` names the minimum.
- All six are fixed, in two shapes. Where a row of small things would space apart, the strip takes the tap and the
  things keep their size and spacing: the page dots (a 48 dp strip, one 28 dp slice per dot) and the folder swatches
  (40 x 48 each, the gap counted in). Where one control stands alone, it draws the same circle inside a 48 dp box:
  the widget options close, the picker's close and the media transport.
- What it cost in layout: Home's page-indicator row grew from 32 to 48 dp, the folder panel's swatch row from 30 to
  48, the widget picker's header by 8, and an app panel's media row by 14. Nothing changed size on screen.
- Widget picker secondary text scores 2.59:1 (`WidgetPicker.kt:228`).
- 51 ellipses and 68 `maxLines = 1`; no 200% pass yet.
- 3 live regions; no focus rings; no RTL check; `Role.Switch` used once.

## Gaps

| # | Work | Size |
|---|---|---|
| 1 | ~~Grow the six small hit areas~~ (done). The page dots and the folder swatches keep their size and let the strip around them take the tap; the widget options close, the picker's close and the media transport draw the same circle inside a 48 dp box | M |
| 2 | A `secondaryLabel` token that meets 4.5:1 over bright wallpapers | S |
| 3 | 200% font scale: ~~text in fixed-height rows~~ (done: seven rows are a minimum height now, the same at the normal size). Left: the 68 `maxLines = 1` labels that end in "…", and the rows in `CustomizationSheet.kt` and `MarketScreen.kt`, open in other pull requests | M |
| 4 | ~~Live regions for island notices and page changes~~ (done) | S |
| 5 | TalkBack labels for the editor. The page dots have theirs (one named tap slice per dot, #144) | S |
| 6 | `enableAccessibilityChecks()` in Compose UI tests. Blocked: Compose BOM 2025.06.01 does not expose it on `createComposeRule()`; revisit when the BOM bump (Dependabot #5) lands | S |
| 7 | RTL: ~~back and disclosure chevrons~~ (done: `Modifier.mirroredForRtl()`). Left: the six chevrons in `CustomizationSheet.kt` and `MarketScreen.kt` (open in other pull requests), and a product decision: whether Home's page order (Discover and Today View to the left) flips in a right-to-left language the way iOS's does. The Home page arrows and Discover's arrow follow that order, so they stay as they are until it is decided | M |
