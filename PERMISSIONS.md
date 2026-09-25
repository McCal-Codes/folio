# What Folio asks for, and why

A launcher sees a lot of your phone, so here is every permission Folio declares, what uses it, and what happens if you
say no. You can check this list against `app/src/main/AndroidManifest.xml`; nothing is hidden behind a marketing word.

**Most of these are optional.** Folio's Home screen works with none of them granted.

| Permission | What it's for | If you don't grant it |
|---|---|---|
| `INTERNET`, `ACCESS_NETWORK_STATE` | Checking for Folio updates, the roadmap, and the Market sources you add | Folio stays entirely offline |
| `POST_NOTIFICATIONS` | Telling you an update is ready | No update notices |
| `PACKAGE_USAGE_STATS` | App suggestions, and how often you open apps, for sorting (`Suggestions.kt`, `LayoutHistory.kt`). You grant it on a separate system screen | Suggestions are off; nothing else changes |
| `READ_CONTACTS` | People in Spotlight search (`Spotlight.kt`) | Search covers apps and settings only |
| `READ_CALENDAR` | The next event in Up Next and Smart Stacks (`UpNext.kt`) | Those widgets say there's nothing to show |
| `ACCESS_COARSE_LOCATION` | Switching the appearance between light and dark at your local sunrise and sunset, if you pick that (`MainActivity.kt`) | Folio follows the system light/dark setting |
| `ACCESS_WIFI_STATE` | The Wi-Fi network's name and signal in the status area and Control Center (`DeviceStatus.kt`) | The Wi-Fi row shows no name |
| `ACCESS_NOTIFICATION_POLICY` | Turning Do Not Disturb on with a Focus (`Focus.kt`) | Focus changes everything except Do Not Disturb |
| `WRITE_SETTINGS` | The brightness slider and rotation lock in Control Center (`TopPanels.kt`) | Those two controls are off |
| `USE_BIOMETRIC` | Unlocking Hidden Apps with your fingerprint or face (`CustomizationSheet.kt`) | Hidden Apps falls back to your screen lock |
| `REQUEST_INSTALL_PACKAGES`, `UPDATE_PACKAGES_WITHOUT_USER_ACTION` | Installing a Folio update you asked for (`SoftwareUpdate.kt`), and an app from a Market source if you turn that on (`MarketApkInstall.kt`) | Update the APK yourself, and Market apps can't be installed |
| `BIND_APPWIDGET` | Declared the way launchers do. Android never grants it to an app like Folio: adding a widget still asks you each time (`WidgetController.kt`) | No difference |

**Notification access** (the separate "Notification access" screen, not a manifest permission) is what lets the Dynamic
Island, Notification Center, badges, Cabinet and Lock Cover show notifications. Without it those features are empty;
Folio keeps working.

**Folio's accessibility service** (`SystemShadeAccessibilityService`, off until you turn it on) is how Android lets any
app open the system notifications and Quick Settings panels: there is no other way for an app to do it. It also shows
Folio's dock handle and Dynamic Island over other apps, if you turn those on. It observes no events, cannot read what
is on screen, and cannot tap or type for you (`SystemShadeController.kt`). Without it, Home's swipe-down gestures use
Folio's own Notification Center and Control Center.

**Banking and payment apps** sometimes warn you, refuse to open, or ask you to turn off accessibility services while
any accessibility service is on, whichever app it belongs to. That is the bank's own fraud check, and it cannot tell a
service that reads nothing from one that reads everything. If a bank app does it, turning Folio's service off in
Android Settings › Accessibility is safe: Folio keeps working, and you can turn it back on afterwards.

## What Folio never does

- **No ads, no analytics, no tracking, no accounts.** Nothing about you leaves the phone.
- **Network only when you ask.** Update checks, the roadmap, and the sources you add, nothing else. Requests carry a
  plain `User-Agent: Folio` and no identifiers, and everything is HTTPS ([`network_security_config.xml`](app/src/main/res/xml/network_security_config.xml)).
- **Contacts, calendar and notifications stay on the phone.** They're read to draw a screen and never uploaded.
- **Market packages get no Android permissions.** A package is data: it configures things Folio already does, and its
  page lists exactly what it may change. Folio never runs code from a source
  ([ADR 0004](docs/adr/0004-declarative-first.md)).
- **An app from a source is Android's to install.** Some listings are apps of their own, like a keyboard. Folio only
  downloads one if you turn on Settings › Market › Installing apps, checks it against the checksum the source
  signed, and then hands it to Android, which asks you before installing it and names the app.

## Checking a build yourself

Every release lists the APK's SHA-256. To check the file you downloaded:

```bash
shasum -a 256 Folio-0.6.6.apk
```

Releases also link a [VirusTotal](https://www.virustotal.com/) scan of that exact APK, so you don't have to take the
checksum on faith. Every release carries the certificate it was signed with (`signing-certificate.txt`), so you can
check it's the same key that signed the last one. Android won't let a build signed by anyone else update the
Folio you already have.

An unsigned or sideloaded launcher deserves suspicion. If anything here doesn't match what you see in the code, please
[open an issue](https://github.com/McCal-Codes/folio/issues/new). That's a bug worth fixing.
