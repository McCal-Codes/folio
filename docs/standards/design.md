# Design standard (`DES`)

Folio looks and behaves like iOS, running on Android. This standard says what that means in pixels, and where
Folio's own identity takes over.

## Rules

### Identity

- **DES-1 MUST** be iOS-inspired. A new surface starts from its iOS equivalent (grouped lists, sheets, pills, context
  menus, springs). Where iOS has none (smart collections, home modes, a command palette), model it on a well-known iOS
  jailbreak tweak, not an Android launcher convention.
- **DES-2 MUST NOT** copy Apple assets: no SF Symbols, Apple fonts, wallpapers or screenshots. Folio borrows patterns
  and measurements, not artwork.
- **DES-3 SHOULD** use Android's own behaviour where users expect it (system share sheet, permission dialogs,
  predictive back, the system notification shade when chosen) and describe it in iOS terms in the UI.

### Tokens

- **DES-4 MUST** take colours from `FolioColors`, glass fills from `FolioGlass`, springs from `FolioMotion`, and app
  label sizes from `LabelSize` (new code). A hex literal is allowed only inside a token file, a theme file, or a
  deliberately fixed artwork such as `DuneWallpaper`.
- **DES-5 MUST NOT** re-type a token's value. `Color(0xFF0A84FF)` outside `FolioTokens.kt` is a bug waiting to drift.
- **DES-6 SHOULD** use the spacing, radius and type scales below. A value off the scale needs a reason in a comment.
- **DES-7 MUST** add a token, not a literal, when a new value is used in more than one file.

#### Scales (to be added as `FolioSpace`, `FolioRadius`, `FolioType`; see Gaps)

These are the values Folio already uses most, so adopting them changes little on screen.

| Scale | Values |
|---|---|
| Spacing (dp) | 2, 4, 6, 8, 12, 16, 20, 24, 32 |
| Corner radius (dp) | 10 (small controls), 14 (menus, alerts, form sheets, cards), 16 (sheet groups), 20 (grouped cards), 24 (panels), 28 (sheet top) |
| Type (sp) | 28 Bold title, 17 body, 15 subheadline, 13 footnote, 12 SemiBold caps group label (+.4 tracking); Home labels via `LabelSize` |
| Row height (dp) | 48 action and menu rows, 52 navigation rows |

- **DES-8 SHOULD** make corners concentric: an inner radius is the outer radius minus the padding between them.

### Components

- **DES-9 MUST** build from Folio's components before writing a new one:

  | Need | Use |
  |---|---|
  | Switch, chip, slider, search, rows, pop-up menu, segmented control | `IosControls.kt` |
  | Grouped settings card, note, action | `SettingsGroup.kt` |
  | Sheet group and label, app picker | `LauncherSheetParts.kt` |
  | Bottom sheet, form sheet, alert, full-screen page, peek capsule | `FolioSheet.kt` |
  | App context menu | `AppContextMenu.kt` |

- **DES-10 MUST NOT** use a Material 3 control directly in user-facing UI when a Folio component exists (new code).
  Material may sit underneath a Folio component, as `IosSlider` does.
- **DES-11 SHOULD** add a missing component to one of those files, with a short comment on its iOS reference, rather
  than building it inline in a screen.

### Colour and materials

- **DES-12 MUST** reserve the accent colour for what needs attention: selection, badges, an active Focus, unfinished
  setup. If everything is blue, nothing is.
- **DES-13 MUST** use `FolioColors.Red` (or `RedLight` on light surfaces) only for destructive actions and errors.
- **DES-14 MUST** give every glass surface a solid fallback through `LocalSolidGlass` for Reduce Transparency and high
  contrast.
- **DES-15 MUST NOT** run a per-frame Compose blur. Blur comes from the window (`FolioDialogWindow`) or a cached
  shader pass.
- **DES-16 MUST** keep text readable over any wallpaper: use `HomeInk` on Home, and a scrim or glass behind text that
  can't rely on it ([A11Y-6](accessibility.md)).

### Icons and imagery

- **DES-17 SHOULD** use `Icons.Rounded` (AutoMirrored where the glyph has a direction) for UI glyphs, 18 to 22dp.
- **DES-18 MUST** respect the user's `IconStyle`, `IconShape`, badge options and icon pack everywhere an app icon is
  drawn, by going through `AppIcon`.

### Theming

- **DES-19 MUST** support light and dark on Home surfaces through `LocalDuoPalette`. Sheets and overlays are dark by
  design (`FolioSheetColors`); a new sheet follows that unless a Gap item changes it.
- **DES-20 MUST** keep community themes (`themes/`, `Themes.kt`) data only, validated by `CommunityThemesTest`.

### Words

- **DES-21 MUST** put user-visible text in `strings.xml` (new code), with plurals as plurals.
- **DES-22 SHOULD** name things the way iOS does (Dynamic Island, Control Center, App Library, Spotlight) and use
  sentence case. Product names are not translated.

## Where Folio is today

Good:

- `FolioColors`, `FolioGlass`, `FolioMotion`, `MotionSpeed`, `LabelSize` exist (`FolioTokens.kt`, `FolioGlass.kt`).
- A real iOS component set: `IosSwitch`, `IosSegmented`, `IosMenuRow`, `GroupedCard`, `SheetGroup`, and
  `ModalBottomSheet` / `AlertDialog` replacements that shadow Material's by name (`FolioSheet.kt:89`, `:243`).
- Glass has a solid fallback (`LocalSolidGlass`), blur is window-level, and there's no per-frame blur.
- Icon looks, shapes, badges, live icons and icon packs all flow through `AppIcon` and `LocalIconLook`.

Not yet:

- No spacing, radius or type scale. Outside token files there are about 1,630 `.dp` literals, 242
  `RoundedCornerShape(N)`, 343 `fontSize` literals across 15 sizes, and 145 `Color(0x…)` literals. Worst files:
  `CustomizationSheet.kt`, `MarketScreen.kt`, `LauncherScreen.kt`, `TopPanels.kt`, `MarketSourcesUi.kt`.
- Token hex values are re-typed (`0xFF0A84FF` in six Market and Settings files; Red and Blue in `SettingsGroup.kt:110`).
- `IosControls.kt:41` has its own `IosGreen 0xFF34C759`, different from `FolioColors.Green 0xFF30D158`.
- Material used directly: `AlertDialog` for rename (`AppContextMenu.kt:238`), `Button` / `OutlinedButton` /
  `FilledTonalButton` in 6 places, `DropdownMenu` in `FolderPanel.kt:161`, `AssistChip` in `AppLibrary.kt:213`,
  Material progress indicators.
- `FolioColors` is dark-appearance only; there are no named status colours or light variants besides `RedLight`.

## Gaps

| # | Work | Size |
|---|---|---|
| 1 | Add `FolioSpace`, `FolioRadius`, `FolioType` with the scales above; migrate `CustomizationSheet.kt` first, as the largest | M |
| 2 | Replace re-typed token hex values with the tokens; decide `IosGreen` vs `FolioColors.Green` (one green) | S |
| 3 | Move the rename dialog to Folio's `AlertDialog`; `FolderPanel`'s `DropdownMenu` to `IosMenuRow`; the direct Buttons to one `FolioButton` | S |
| 4 | Light-surface variants and named status colours (`Success`, `Warning`) in `FolioColors` | S |
| 5 | A lint check (or a unit test like `HardcodedTextTest`) that counts `Color(0x` and `fontSize = N.sp` outside token files and fails if the count rises | S |
