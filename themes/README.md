# Folio themes

A theme is one small JSON file with a look: icons, badges, glass, text on Home and the status bar. It never
touches your apps, pages, widgets or permissions.

## Use a theme

- **In Folio:** Settings › Themes › **Import Theme…** and pick the file.
- **From another app:** share the `.json` file to **Folio Theme** (Files, Chrome downloads, a chat). Folio asks before
  applying it.
- Changed your mind? Settings › Themes › **Undo Theme Change**.

To make your own, set things up how you like and use Settings › Themes › **Save Current Look as Theme…**.

> Folio themes pair well with icon packs. Those are made by independent designers, so if you use one, please buy it
> from them on Google Play. Much love.

## Share a theme here

1. Save your look from Folio and give it a name (up to 40 characters).
2. Set `"iconPack"` to `null`. Other people won't have your icon pack installed.
3. Add the file to this folder as `your-theme-name.json` and open a pull request.
4. `CommunityThemesTest` checks every file in this folder. Run `./gradlew :app:testDebugUnitTest` before opening the PR.

## Format

`"folioTheme": 1` marks the file as a Folio theme (version 1). Anything missing or unknown falls back to Folio's
default for that field.

| Field | Values | Default |
| --- | --- | --- |
| `name` | Text, up to 40 characters | `Imported Theme` |
| `iconStyle` | `DEFAULT`, `DARK`, `TINTED` | `DEFAULT` |
| `iconTint` | ARGB color as a number (used by `TINTED`) | `4294947648` (orange) |
| `iconTintFromWallpaper` | `true`, `false` | `false` |
| `iconShape` | `DEFAULT`, `SQUIRCLE`, `CIRCLE`, `ROUNDED` | `DEFAULT` |
| `iconPack` | `null` for shared themes | `null` |
| `badgeStyle` | `OFF`, `DOT`, `COUNT` | `DOT` |
| `badgeColor` | `RED`, `APP`, `SOFT`, `BLUE`, `GREEN`, `ORANGE`, `PURPLE` | `RED` |
| `badgeLook` | `IOS`, `CLASSIC`, `GLASS` | `IOS` |
| `badgeSize` | `SMALL`, `STANDARD`, `LARGE` | `STANDARD` |
| `liveIcons` | `true`, `false` | `true` |
| `homeInk` | `AUTO`, `LIGHT`, `DARK` | `AUTO` |
| `tintedGlass` | `true`, `false` | `true` |
| `dimWallpaperDark` | `true`, `false` | `true` |
| `statusStyle.showTime` | `true`, `false` | `true` |
| `statusStyle.showDate` | `true`, `false` | `true` |
| `statusStyle.showBatteryPercent` | `true`, `false` | `true` |
| `statusStyle.glyph` | `RING`, `ICONS`, `MINIMAL`, `NONE` | `RING` |
| `statusStyle.colorfulBattery` | `true`, `false` | `true` |
| `statusStyle.chargingBolt` | `true`, `false` | `true` |
| `statusStyle.railGlass` | `0` (clear) to `1` (solid) | `0.26` |

[`dark-example.json`](dark-example.json) is Folio's built-in Dark theme, for reference.
