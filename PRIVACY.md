# Data and permissions

Folio stores settings, Home layout, widget placement, and selected wallpaper locally. It has no account system, backend, advertising, analytics SDK, or automatic crash reporting.

## Data used on your device

- Installed app names, icons, launch activities, and eligible work-profile entries populate Home and the App Library.
- Widget providers control their content, accounts, and network activity; Android hosts their widgets.
- Battery, Wi-Fi, cellular signal, and airplane-mode readings populate the Home status bar while visible. Signal display does not require location access.
- Selecting a photo creates a local preview. Apply commits it; cancel preserves the previous background. Android's picker grants access to chosen images only.
- Sunrise/sunset appearance stores coordinates you enter or explicitly request through approximate location. Times are calculated locally. There is no background location tracking, and Clear location removes the stored coordinates.

## Optional access

The “Folio gestures & overlays” accessibility service opens notifications or Quick Settings in response to your gesture. If you turn them on, it also draws Folio’s dock handle and Dynamic Island over other apps, launches your dock apps and performs Home. It cannot retrieve window contents or perform gesture injection and unsubscribes from accessibility events when connected. You can disable it in Android Accessibility settings and continue using the launcher.

Notification access (optional) lets the Dynamic Island and Notification Center show music, calls, timers, navigation, progress and your notifications. Notification content is kept only in memory while shown and is cleared when access is turned off; it is never stored or sent anywhere. When you use quick reply or Mark as Read, Folio passes your text to that notification’s own reply action inside the messaging app (the same thing the system notification shade does); Folio itself sends nothing over the network. Spotlight’s Message button opens your texting app, or OpenBubbles/BlueBubbles if you choose one, with the contact’s number or email.

Contacts (optional) are searched on the device from Spotlight only. Bluetooth device names (optional) are shown in the island when a device connects. Folio remembers the last apps you launched on the device to suggest them in Spotlight, and learns how fast you open and close the phone to pace the fold animation; both stay in Folio’s private storage.

Android controls widget-binding approval and Home-app selection. Providers can require separate setup or permissions.

## Google and other apps

Discover and Google search use the installed Google app. Apps, search results, articles, and widgets may use their providers' network services and accounts. Those apps' policies and settings apply; Folio does not proxy their traffic or collect their content.

## Export, reports, and removal

A layout export is created only when you choose Save in Backup and select a destination. It can reveal installed apps, folder names, profile metadata, and layout preferences. Photos are excluded. Review it before sharing.

There is no automatic diagnostic upload. Settings › Report a Bug opens GitHub's bug form in your browser with the Folio version, phone model and Android version in the link; nothing is sent unless you submit the form, and GitHub's privacy policy applies to what you post there. Screenshots and logs you manually attach to issues may contain personal information, widget content, account names, or work data. Review them first.

Uninstalling or clearing storage removes Folio's local settings, photos, and widget bindings. Exported files remain where you saved them. Android and device vendors may provide their own diagnostics independently of Folio.

**Software Update** (Settings › Software Update) contacts GitHub's public releases API (api.github.com) only when you tap Check for Updates, or once a day if you turn on automatic checks. It downloads the release APK from GitHub, verifies its SHA-256 and signing key on your phone, and hands it to Android's installer; nothing about you or your phone is sent. Folio asks for "Install unknown apps" only to install its own updates, and for notifications only if you turn on update notifications.

**Hidden apps** are listed in Settings only after Android confirms it's you (fingerprint, face or PIN). Folio never sees your biometrics or PIN; Android only tells it whether the check passed.
