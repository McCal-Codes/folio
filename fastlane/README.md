# Fastlane metadata

What app stores and repositories read: the name, the descriptions, the icon, the screenshots and the changelogs.
IzzyOnDroid and F-Droid both look for exactly this layout, so it lives here rather than in a listing somewhere.

- `title.txt`, `short_description.txt` (80 characters at most), `full_description.txt`: McCal's own words, taken from
  the README. Keep them in step when the README changes.
- `images/icon.png`: 512 x 512, rendered from the launcher icon's own vector sources, so it matches the app exactly.
- `images/phoneScreenshots/`: the cover screen. `images/sevenInchScreenshots/`: the Fold8 inner screen.
  Real captures only, from a phone in Screenshot Mode.
- `changelogs/<versionCode>.txt`: what changed, per release. The code is `major * 10000 + minor * 100 + patch`,
  so 0.6.6 is `606`.
