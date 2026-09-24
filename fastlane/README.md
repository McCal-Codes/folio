# Fastlane metadata

What app stores and repositories read: the name, the descriptions, the icon, the screenshots and the changelogs.
IzzyOnDroid and F-Droid both look for exactly this layout, so it lives here rather than in a listing somewhere.

- `title.txt`: **Folio Launcher**, not Folio. The app's own name stays Folio; listings say Folio Launcher because
  "Folio" is already taken on F-Droid by `com.folio.reader`, and because it says what it is to someone reading a list
  of apps.
- `short_description.txt` (80 characters at most) and `full_description.txt`: McCal's own words, taken from the
  README. Keep them in step when the README changes.
- `images/icon.png`: 512 x 512, rendered from the launcher icon's own vector sources, so it matches the app exactly.
- `images/phoneScreenshots/`: the cover screen. `images/sevenInchScreenshots/`: the Fold8 inner screen.
  Real captures only, from a phone in Screenshot Mode.
- `changelogs/<versionCode>.txt`: what changed, per release. The code is
  `(major * 10000 + minor * 100 + patch) * 10 + hotfix` (ADR 0007), so 0.6.7 is `6070` and a `0.6.7.1` on top of it
  would be `6071`. `606.txt` predates that formula and keeps its name, because the build it describes was published
  under it.
