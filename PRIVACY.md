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

Notification access (optional) lets the Dynamic Island and Notification Center show music, calls, timers, navigation, progress and your notifications. Other apps' notifications only pop up in the island if you turn on Dynamic Island › Other notifications and leave that app switched on; the list of apps there is built from notifications you've received and isn't saved, except for the apps you turn off. Notification content is kept only in memory while shown and is cleared when access is turned off; it is never stored or sent anywhere. When you use quick reply or Mark as Read, Folio passes your text to that notification’s own reply action inside the messaging app (the same thing the system notification shade does); Folio itself sends nothing over the network. Spotlight’s Message button opens your texting app, or OpenBubbles/BlueBubbles if you choose one, with the contact’s number or email.

Contacts (optional) are searched on the device from Spotlight only. When Bluetooth headphones or a speaker connects, its name is shown on Home or in the island; the name comes from Android's list of audio devices, needs no permission and isn't stored. Folio remembers the last apps you launched on the device to suggest them in Spotlight, and learns how fast you open and close the phone to pace the fold animation; both stay in Folio’s private storage.

Android controls widget-binding approval and Home-app selection. Providers can require separate setup or permissions.

## Google and other apps

Discover and Google search use the installed Google app. Apps, search results, articles, and widgets may use their providers' network services and accounts. Those apps' policies and settings apply; Folio does not proxy their traffic or collect their content.

## Export, reports, and removal

A layout export is created only when you choose Save in Backup and select a destination. It can reveal installed apps, folder names, profile metadata, and layout preferences. Photos are excluded. Review it before sharing.

**Software Update** (Settings › Software Update) contacts GitHub's public releases API (api.github.com) about once a day (Automatic Updates: Automatic, the default, or Notify Me), when you open Software Update, and when you tap Check for Updates. Choose Manual to only check when you open the page or tap the button. It downloads the release APK from GitHub, verifies its SHA-256 and signing key on your phone, and hands it to Android's installer; nothing about you or your phone is sent. Folio asks for "Install unknown apps" only to install its own updates.

Opening Settings › Roadmap fetches Folio's roadmap file from GitHub (raw.githubusercontent.com) at most every six hours; it's a plain request for a public file and sends nothing about you beyond what any web request does. There is no automatic diagnostic upload. Folio keeps a few local problem reports (crashes, freezes, being closed by Android, or the phone restarting while Folio was on screen) with your phone model, screen settings and the last few Folio events, such as "Home shown" or "Control Center open"; no notification content, messages or app lists. Report a Bug › Copy Diagnostics and Advanced › Share Diagnostics also include Folio's own recent log lines; you see and choose where they go. Settings › Help › Report a Bug opens GitHub's bug form in your browser with the Folio version, phone model and Android version in the link; nothing is sent unless you submit the form, and GitHub's privacy policy applies to what you post there. Screenshots and logs you manually attach to issues may contain personal information, widget content, account names, or work data. Review them first.

Uninstalling or clearing storage removes Folio's local settings, photos, and widget bindings. Exported files remain where you saved them. Android and device vendors may provide their own diagnostics independently of Folio.

## Banking apps and the gestures service

Some banking apps refuse to run while any accessibility service is enabled, Folio's included. That is their own
check against screen-reading malware, and nothing Folio can change from its side: Android doesn't let an app say
"I only use this for gestures."

Folio's gestures service is optional. Without it you lose the pull-down Notification Center and Control Center, the
dock and island over other apps, and Big Buttons; everything else (Home, layouts, folders, the App Library,
Spotlight, themes, the island on Home) works exactly the same. Turn it off in Android's Settings › Accessibility
› Installed apps whenever a banking app objects.
