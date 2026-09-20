# What Folio asks for, and why

A launcher sees a lot of your phone, so here is every permission Folio declares, what uses it, and what happens if you
say no. You can check this list against `app/src/main/AndroidManifest.xml`; nothing is hidden behind a marketing word.

**Most of these are optional.** Folio's Home screen works with none of them granted.

| Permission | What it's for | If you don't grant it |
|---|---|---|
| `INTERNET`, `ACCESS_NETWORK_STATE` | Checking for Folio updates and the roadmap | Folio stays entirely offline |
| `POST_NOTIFICATIONS` | Telling you an update is ready | No update notices |
| `PACKAGE_USAGE_STATS` | App suggestions, and how often you open apps, for sorting (`Suggestions.kt`, `LayoutHistory.kt`). You grant it on a separate system screen | Suggestions are off; nothing else changes |
| `READ_CONTACTS` | People in Spotlight search (`Spotlight.kt`) | Search covers apps and settings only |
| `READ_CALENDAR` | The next event in Up Next and Smart Stacks (`UpNext.kt`) | Those widgets say there's nothing to show |
| `ACCESS_COARSE_LOCATION` | Switching the appearance between light and dark at your local sunrise and sunset, if you pick that (`MainActivity.kt`) | Folio follows the system light/dark setting |
| `ACCESS_WIFI_STATE` | The Wi-Fi network's name and signal in the status area and Control Center (`DeviceStatus.kt`) | The Wi-Fi row shows no name |
| `ACCESS_NOTIFICATION_POLICY` | Turning Do Not Disturb on with a Focus (`Focus.kt`) | Focus changes everything except Do Not Disturb |
| `WRITE_SETTINGS` | The brightness slider and rotation lock in Control Center (`TopPanels.kt`) | Those two controls are off |
| `USE_BIOMETRIC` | Unlocking Hidden Apps with your fingerprint or face (`CustomizationSheet.kt`) | Hidden Apps falls back to your screen lock |
| `REQUEST_INSTALL_PACKAGES`, `UPDATE_PACKAGES_WITHOUT_USER_ACTION` | Installing a Folio update you asked for (`SoftwareUpdate.kt`) | Update the APK yourself |
| `BIND_APPWIDGET` | Declared the way launchers do. Android never grants it to an app like Folio: adding a widget still asks you each time (`WidgetController.kt`) | No difference |

**Notification access** (the separate "Notification access" screen, not a manifest permission) is what lets the Dynamic
Island, Notification Center, badges, Cabinet and Lock Cover show notifications. Without it those features are empty;
Folio keeps working.

## What Folio never does

- **No ads, no analytics, no tracking, no accounts.** Nothing about you leaves the phone.
- **Network only when you ask.** Update checks and the roadmap — nothing else. Requests carry a plain
  `User-Agent: Folio` and no identifiers, and everything is HTTPS: Folio targets a recent Android, where plain
  HTTP is blocked unless an app opts in, and Folio doesn't.
- **Contacts, calendar and notifications stay on the phone.** They're read to draw a screen and never uploaded.

## Checking a build yourself

Every release lists the APK's SHA-256. To check the file you downloaded:

```bash
shasum -a 256 folio-0.6.5.apk
```

Releases also link a [VirusTotal](https://www.virustotal.com/) scan of that exact APK, so you don't have to take the
checksum on faith, and the build comes from a tagged GitHub Actions run whose provenance you can inspect.

An unsigned or sideloaded launcher deserves suspicion. If anything here doesn't match what you see in the code, please
[open an issue](https://github.com/McCal-Codes/folio/issues/new) — that's a bug worth fixing.
