# What Folio asks for, and why

A launcher sees a lot of your phone, so here is every permission Folio declares, what uses it, and what happens if you
say no. You can check this list against `app/src/main/AndroidManifest.xml`; nothing is hidden behind a marketing word.

**Most of these are optional.** Folio's Home screen works with none of them granted.

| Permission | What it's for | If you don't grant it |
|---|---|---|
| `INTERNET`, `ACCESS_NETWORK_STATE` | Checking for Folio updates, the roadmap, the Supporters list, and the Market sources you add. `ACCESS_NETWORK_STATE` also tells the status area and Control Center whether Wi-Fi is connected, and lets the update and Market refresh jobs wait for a network (`SoftwareUpdate.kt`, `MarketRefreshJob.kt`, `DeviceStatus.kt`) | Nothing to grant: Android gives both with the app, no prompt. With Software Update on Manual and no Market sources, Folio goes online only when you open Software Update, the Roadmap or the Supporters list |
| `POST_NOTIFICATIONS` | Telling you a Folio update is ready, and that Market packages you installed have updates, if you turn those notices on (`SoftwareUpdate.kt`, `MarketUpdateNotice.kt`) | No update notices |
| `PACKAGE_USAGE_STATS` | App suggestions, and how often you open apps, for sorting (`Suggestions.kt`, `LayoutHistory.kt`). You grant it on a separate system screen | Suggestions are off; nothing else changes |
| `READ_CONTACTS` | People in Spotlight search (`Spotlight.kt`) | Search covers apps and settings only |
| `READ_CALENDAR` | The next event in Up Next and Smart Stacks (`UpNext.kt`) | Those widgets say there's nothing to show |
| `ACCESS_COARSE_LOCATION` | Switching the appearance between light and dark at your local sunrise and sunset, if you pick that (`MainActivity.kt`) | Folio follows the system light/dark setting |
| `ACCESS_WIFI_STATE` | Whether Wi-Fi is connected, and its signal, in the status area and Control Center (`DeviceStatus.kt`). Folio never reads the network's name | Nothing to grant: Android gives it with the app, no prompt |
| `ACCESS_NOTIFICATION_POLICY` | Turning Do Not Disturb on and off with a Focus, the Do Not Disturb control in Control Center, and the Do Not Disturb actions (`Focus.kt`, `TopPanels.kt`, `FolioActions.kt`). You grant it on Android's Do Not Disturb access screen | Focus changes everything except Do Not Disturb, the Control Center button opens that access screen, and the actions don't run |
| `WRITE_SETTINGS` | The brightness slider and rotation lock in Control Center (`TopPanels.kt`) | Those two controls are off |
| `WRITE_SECURE_SETTINGS` | Nothing uses it yet. Android lets an app change its protected settings only after it is granted once from a computer (`adb shell pm grant`), or, on a rooted phone, by the optional System Bridge: with root tested and your approval switched on in Settings › Advanced › System Bridge, a button there runs the same `pm grant` on Folio through root (`RootSettingsGrant.kt`). That page says whether it is on. It is declared so a later feature that changes a setting can work without Shizuku running | Nothing changes. It stays off, as it is for every app, until you grant it |
| `USE_BIOMETRIC` | Unlocking Hidden Apps with your fingerprint or face, or your screen lock (`CustomizationSheet.kt`). On a phone with no screen lock there is nothing to ask, so Hidden Apps opens straight away | Nothing to grant: Android gives it with the app, no prompt |
| `REQUEST_DELETE_PACKAGES` | Delete App (an app's menu › Remove App, or Delete App in the App Library) asks Android to uninstall that app (`AppRemoval.kt`). Android shows its own "uninstall?" confirmation every time, and nothing is deleted without it. Granted with the app, no prompt | Nothing to grant: without it, delete apps from Android's Settings |
| `REQUEST_INSTALL_PACKAGES`, `UPDATE_PACKAGES_WITHOUT_USER_ACTION` | Installing a Folio update you asked for (`SoftwareUpdate.kt`), and an app from a Market source if you turn that on (`MarketApkInstall.kt`) | Update the APK yourself, and Market apps can't be installed |
| `BIND_APPWIDGET` | Declared the way launchers do. Android never grants it to an app like Folio: adding a widget still asks you each time (`WidgetController.kt`) | No difference |

**Notification access** (the separate "Notification access" screen, not a manifest permission) is what lets the Dynamic
Island, Notification Center, badges, Cabinet and Lock Cover show notifications. Without it those features are empty;
Folio keeps working.

**Folio's accessibility service** (`SystemShadeAccessibilityService`, off until you turn it on) is how Android lets any
app open the system notifications and Quick Settings panels: there is no other way for an app to do it. It also shows
Folio's dock handle and Dynamic Island over other apps, if you turn those on. And it runs Android's "global actions"
when you ask for one: Back, Home and Recents (Big Buttons and the dock), lock screen, screenshot, the power menu, split
screen, play/pause, closing the shade, and Android's accessibility All Apps menu, from Control Center and Folio's
actions (`EverywhereOverlay.kt`, `ActionSpecs.kt`, `FolioActions.kt`, `TopPanels.kt`). It observes no events, cannot read what
is on screen, and cannot tap or type for you (`SystemShadeController.kt`). Without it, Home's swipe-down gestures use
Folio's own Notification Center and Control Center. Two launchable entries, Folio Notification Shade and Folio Quick Settings
(`ShadeShortcutActivity`), use the same service so a button or gesture action on your phone that opens an app can pull the
shade down over another app; they ask for nothing new, show nothing, and do nothing without the service.

**Banking and payment apps** sometimes warn you, refuse to open, or ask you to turn off accessibility services while
any accessibility service is on, whichever app it belongs to. That is the bank's own fraud check, and it cannot tell a
service that reads nothing from one that reads everything. If a bank app does it, turning Folio's service off in
Android Settings › Accessibility is safe: Folio keeps working, and you can turn it back on afterwards.

## What Folio never does

- **No ads, no analytics, no tracking, no accounts.** Nothing about you leaves the phone.
- **Network only for what you can see and switch off.** Software Update checks GitHub once a day by default
  (Automatic); set it to Notify Me to only be told, or Manual to check only when you ask. Beyond that, the roadmap and
  Supporters pages when you open them, and the sources you add, nothing else. Requests carry a plain `User-Agent: Folio`
  and no identifiers (a supporter's beta check also sends their code, see [PRIVACY.md](PRIVACY.md)), and everything is HTTPS ([`network_security_config.xml`](app/src/main/res/xml/network_security_config.xml)).
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
shasum -a 256 Folio-<version>.apk
```

Every release is signed with the same key, and its SHA-256 is on [foliolauncher.com/download](https://foliolauncher.com/download/)
(some releases also attach it as `signing-certificate.txt`), so you can check it's the key that signed the last one.
Android won't let a build signed by anyone else update the Folio you already have.

An unsigned or sideloaded launcher deserves suspicion. If anything here doesn't match what you see in the code, please
[open an issue](https://github.com/McCal-Codes/folio/issues/new). That's a bug worth fixing.
