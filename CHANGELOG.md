# Changelog

All notable changes to Folio. Versions follow [Semantic Versioning](https://semver.org) (MAJOR.MINOR.PATCH; 0.x while
Folio is in development), and this file follows [Keep a Changelog](https://keepachangelog.com). The app's
`versionCode` is derived from the version name ((MAJOR × 10000 + MINOR × 100 + PATCH) × 10 + HOTFIX), so every release sorts correctly and a fix on top of one has nine numbers of its own.
Folio shows the newest section on the phone after an update, and every version under Settings › What's New › Version History.

## [0.6.7] - Unreleased

### Added
- **Folio Beta in the Market:** supporters see Folio Beta under Sources, with what you're on, the newest beta and an Update button. It's the same download Settings › Software Update makes: through your supporter code, checked against Folio's own signing key before anything installs, with Update Now or Tonight.
- **Made with AI, said plainly:** a package whose author says AI helped make it shows an AI-assisted tag beside its developer, which tools helped under Information, and the same on the Get sheet before anything installs.
- **A lasting Supporter badge:** back Folio with $15 or more and Settings › Supporter carries a Folio Supporter badge with the month you started. It stays after the code runs out, and after you remove the code. Nothing about it leaves your phone: the code says so, and Folio reads it offline.
- **Refresh Icons:** Settings › Icons & Side Bar loads your icons again, for one that a theme app changed and Folio still shows the old way.
- **Supporters in Settings:** a page beside Credits listing the people who backed Folio and said their name could be there, newest first. The list is a public file Folio fetches like the Roadmap, so a name can go on or come off without an update.

### Changed
- **Panes fit the screen:** the Market and Settings show as many panes as the window has room for, the way iPad's App Store and Settings do. On a Fold's inner screen that's two: a package, a source or a nested setting opens over the list with Back, and the Market's list uses the whole width, in two columns. Three panes are for windows 1200 dp and wider.
- **Home's background costs the GPU about a third less while swiping.** The dunes never move, so they're drawn once and reused instead of being redrawn every frame. Measured on a Fold8, unfolded at 120 Hz: 7.0 ms per frame down to 4.6 ms.

### Fixed
- A beta from the supporters' source can be downloaded. Folio asked for the list of betas with your code but downloaded the file without it, and the supporter service turns those downloads away.
- **Double tap on an empty spot works again** (#61): every empty grid cell was taking its own taps, and 0.6.5's extra rows made those cells cover most of a page, so the page's Double Tap action never saw the second tap.
- **The dock steps aside for Today View** (#25): unfolded and upright it sat over Today's widgets and its Edit button. Like iPhone, it now fades away as you swipe there and comes back on Home, so it can't take Today's taps.
- **Icons follow a theme that changes them** (#19): Folio kept an app's icon until its package, density, language or dark mode changed, and a theme engine changes none of those. Icons now also follow Android's theme and overlay changes, and folding or rotating still costs nothing.
- **No more smeared, doubled pages** (#12, #35): choosing Android's wallpaper as your background left every swipe painted over the last. The screen starts again with a window that really shows the wallpaper, and Folio draws its own background whenever it isn't.
- **The Preview bar no longer covers the App Library**: before Folio is your Home app, the space kept for the bar was a guess. It's measured now. Once Folio is the Home app nothing moves, so no one's rows shrink.
- **Every Settings page has a way back**: unfolded and upright, a top-level page showed only the sidebar button, which doesn't read as a way back. It now shows the sidebar button and a way back to Folio together.

## [0.6.6] - 2026-09-21

### Added
- **The Folio Market:** the app icon opens a store with Featured, Sources, Packages, Installed and Settings. For now it's for supporters: a code redeemed in Settings › Supporter opens it, and switches on Beta Updates so the builds come too. Everyone gets it in 0.7.0. Every theme and tweak it hands out is still in Settings for everyone else, so nothing is waiting behind it.
- **Themes and tweaks are packages:** Folio's own now have a page each, with what they do, what you see, screenshots, what's changed, and a privacy label built from what the package asks for rather than from anything its author wrote.
- **Add a source:** any HTTPS address, or a `folio://source/` link. Folio shows the source's key fingerprint before you trust it, remembers it, and says plainly what a changed key looks like.
- **A source is a place you go into:** tapping one opens its page, the way tapping a repo does in Cydia and Sileo, with its address, when it was signed, Refresh and Remove, and every package it offers. A package's page names the source that lists it, and that leads back there.
- **Nothing is applied unchecked:** size, checksum and signature first, then the whole package in one go, with Undo beside it. A package that fails halfway is put back the way it was.
- **Signed by its developer:** not just by the source handing it out, so a mirror can carry a package but can't change it or publish under someone else's name. A `.foliopkg` sent to you carries its own signature too.
- **Progress in the Get button:** a ring that fills, App Store style, and roughly how much longer on the package's page.
- **Open a `.foliopkg`:** a package file installs like a shared theme, and `folio://package/<id>` opens its page.
- **Updates in Installed:** a banner and an optional badge when one is ready. Background refresh is off by default and waits for Wi-Fi when it's on.
- **Safe Mode per package:** if Folio stops twice just after a package changed something, only that package is turned off, and its settings are kept.
- **A source can pull a package:** a withdrawn one can be removed but never installed again, including as an update.
- **Share and Report:** a package's page links to its source's issue form with the id, version and checksum filled in.
- **Your packages travel with your layout:** a layout backup now carries what you got from the Market, and restoring one puts those packages back on the new phone, applied over the layout it just restored, not over the old phone's. Anything Safe Mode had turned off comes back turned off, and one this Folio can't apply waits in Installed instead of being half applied.
- **Folio can install an app from a source** (Settings › Market › Installing apps, off until you turn it on). Some listings are apps of their own rather than something Folio applies, because Android says a keyboard has to be its own app. With this on, Folio downloads one, checks it against the checksum its source signed, and hands it to Android, which asks before installing and names the app itself. With it off, Folio opens the store the author named. What Folio can't check is the app itself: the source vouches for that, and the setting says so.
- **Keyd, for supporters:** redeeming a code adds Keyd's own source to the Market, so the keyboard is there to get. Keyd splits around the crease, corrects and suggests in six languages, and asks for no permissions at all. It's also on [GitHub](https://github.com/McCal-Codes/folio-keyd/releases).
- **Simplified Chinese (beta):** Folio speaks 简体中文 when your phone does, on nearly every screen, the Market included. On Android 13 and later you can also set Folio's language on its own in Android Settings › Apps › Folio › Language. Spotlight and the App Library find Chinese app names by pinyin or initials ("weixin" or "wx" for 微信), and the A–Z list files them under their pinyin letter, as on iPhone. It's a beta translation until a native speaker has reviewed it; corrections are welcome through the Translation form on GitHub.
- **Report a bug by email:** Settings › Help › Report a Bug opens your mail app with Folio's details attached as a file, so it needs no GitHub account, and you can read everything before you send it. GitHub is still there if you have an account.
- **A crash offers to send a report:** after Folio closes unexpectedly or freezes, the next launch asks once whether to send one. It never asks twice about the same report.

### Changed
- Settings shows two columns from 700 dp in either orientation, not only in landscape, and three from 920 dp: the list, the page, and whatever you opened from it, so tapping a tweak no longer replaces the list you tapped it in. Half folded, the divider stays on the crease.
- A settings row title wraps to a second line in a narrow window or at a large text size instead of being cut short, and its value moves underneath when there isn't room beside it.

### Fixed
- Switching Background to Android wallpaper shows it straight away. Turned on from Settings it did nothing to Home's window, so the wallpaper flashed up and vanished and Home was left a flat colour. Left of Home and the setup step's wallpaper button were quietly doing nothing in the same way.
- Coming from Folio 0.6.0 or earlier, Home keeps the four rows you arranged instead of growing on a tall screen. Settings › Home Screen & Dock › Rows turns Automatic on.
- With TalkBack, every saved layout under Layout History offered a button that read as just "Restore", with no way to hear which layout it would put back. Each row is now one thing to land on, and it names the layout and when it was saved.

## [0.6.5] - 2026-09-20

### Added
- **Gauge status glyph:** a sixth Icon style for the Side Bar: the battery as an arc, your connection inside it, and the percentage above (Icons & Side Bar › Icon style).
- **More rows:** Home fills taller screens with up to 3 more rows, the same on both screens of a foldable.
- **Layout sliders:** adjust row, column and dock spacing, widget size and status spacing, and watch Home change as you drag.
- **Apps, dock and status position:** apps at the top, the dock on the side or bottom, and the status anywhere, for each screen.
- **Software Update:** updates install automatically overnight, with release notes, Update Now and Update Tonight. A new install starts there; updating from an earlier Folio does not change what you chose, and if you never chose, Folio stays on Manual until you say otherwise.
- **Big Clock:** a large Lock Screen-style clock with the date and what's next.
- **Try Folio first:** open Folio as a preview before making it your Home app.
- **Clear icons:** frosted glass icons with white symbols, like iOS.
- **Small cover screens:** a focused Home for tiny flip-phone covers, with the time, your dock apps and what's playing.
- **App Library pull-down:** pull down from the top of the App Library for Notification Center and Control Center.
- **Folder badges:** folders show the total of their apps' notification badges.
- **Better bug reports:** Folio notes freezes and restarts, and Report a Bug can include them (kept on your phone).
- **A live Roadmap:** see what's coming, updated from Folio's GitHub page.
- **Supporter codes with months:** a code can carry a number of months that starts the day you redeem it, so one handed out from a batch still gives its full time. Settings › Supporter shows the day it runs out.
- **Folio Keys, for supporters:** a code that carries the keyboard scope shows where Folio's own keyboard has got to, in Settings › Supporter. It's in design (a separate app, because Android needs a keyboard to be its own input method), so there is nothing to install yet.
- **Beta updates for supporters:** with a code that carries beta access, Beta Updates installs the beta builds the same way as any other update. They live in a private repository, and Folio reaches them through the supporter worker, which checks your code and never puts a key on your phone. No GitHub account, no downloading APKs by hand.

- **Move apps without dragging:** TalkBack actions and Alt+arrow keys move apps and folders around Home and between pages.
- **Wallpaper Tint:** one slider from Clear to Tinted glass (Wallpaper & Appearance › Glass).
- **Reduce Transparency:** nearly solid widgets, Side Bar and dock; it also turns on with Android's high contrast.
- **Big Buttons:** optional large Back, Home and Recents buttons over other apps, for when the system's are too small (Dynamic Island › In Every App). They sit above Android's own navigation, hide in full-screen apps and fade when idle.
- **Swipe Down on Home:** pick what a swipe down the middle of Home does: Spotlight, Notification Center or nothing (Gestures & Actions). Set to Notification Center it works like Android's usual one-finger pull-down, and follows your choice of Folio's panels or Android's own shade.
- **Move the buttons:** long-press and drag Big Buttons up the screen, away from the keyboard or an app's own bottom bar; Settings puts them back.
- **Rename apps:** long-press an app, More › Rename… and give it any name; the new name shows on Home, in the dock, in folders, in the App Library and in search. Searching the app's original name still finds it, and clearing the field puts that name back.
- **The island steps aside in full screen:** the island in every app now leaves full-screen video and games alone, with switches for full screen and landscape (Dynamic Island › In Every App).
- **Predictive back:** folders, the App Library and Settings follow your back swipe before closing.

### Changed
- Cleaner Settings, like iOS: every group is one card with thin dividers between rows, explanations sit under their card, and actions line up with the other rows. Island pop-ups have their own group.

### Fixed
- **Google Discover works again:** Folio was asking the Google app for an old version of its feed connection, and newer Google app builds answered with nothing at all. It now asks for the same version Android's own launcher does.
- **No button to a page that isn't there:** with Today View and Discover both off, Home stops offering the button that led nowhere.
- **The island lets go of forgotten music:** a player that was paused and closed used to sit in the island for good, with buttons that did nothing.
- With a keyboard, Tab and the arrow keys now move between apps on Home instead of stopping on empty spaces behind them.
- If your saved Home layout can't be read, Folio now says so and offers to restore a backup or start fresh (keeping a copy), instead of quietly showing an empty Home.
- Half folded, Home keeps off the hinge: unfolded pages stay on their side of a book fold, the bottom dock moves to one half, and on a table-style fold the status stays above the hinge and the dock goes below it.
- On tall phones, Home sits centered in the screen instead of high up with empty space below.
- On narrower phones, apps keep more space between them: icons never take more than 80% of their column.
- Android's status bar no longer reappears over the Side Bar status after changing Display size, Smallest width or window size.
- On folds with more than one hinge (tri-folds) and dual-screen phones, sheets, menus and alerts stay on one panel instead of crossing a hinge.
- With a larger Smallest width (like 600 dp for the cover screen), the unfolded screen no longer gets bigger icons and the cover's two-column layout: Folio's tablet scaling now judges the screen at the phone's own density.
- The Home Screen & Dock preview shows the unfolded layout on the Inner tab.
- An app that re-posts the same notification (like a repeating warning) no longer pops up in the island each time; it stays in Notification Center.
- In short landscape windows, the widget row on the left no longer runs under the page controls.
- The Dynamic Island now always covers a punch-hole camera, instead of sitting below it on screens where the camera is close to the top (like the Galaxy Z Fold7's inner screen).
- A panel or Spotlight that was closed while Folio wasn't drawing can no longer stay on screen (or leave Home blurred) with no way out: it finishes closing on its own.
- Turning on Folio gestures now names Samsung's "App was denied access" message and walks through Allow restricted settings step by step.
- Google Discover beside Home is only used on Android 17 and newer; on Android 16 (reported on the Galaxy Z Fold7, issue #12) it could leave smeared copies of Home on screen, so Discover opens as its own page there.
- Creating a folder no longer shows a second copy of it on the unfolded screen's extra left page.
- Folders can be moved again: dragging one no longer drops it onto itself (which buzzed and put it back).
- Folding or rotating no longer throws away a search: the pull-down panel closes, Spotlight keeps what you typed, and the panel now closes on the first fold after a restart too.
- In the Gauge, the mark shown when there's nothing to connect to sits in the middle of the ring, level with the Wi-Fi one.
- Music that stops to load no longer drops out of the island and back in on every skip, and a player with no track title no longer hides one that has it.
- On a phone set to Turkish, supporter codes typed in lower case can be redeemed, the Roadmap shows every item, and What's New keeps its symbols: Folio was reading its own data through the phone's language.
- The time on each notification ("now", "5m ago") follows your language and its plural rules, and a phone whose clock has just been corrected no longer shows a notification as arriving in the future.
- The last of Folio's own words follow your language: brief messages (a file that isn't a theme, an app that won't open, a redeemed code) and the text left in What's New, Choose Home Apps, widget editing, Up Next, restore and the alerts.
- A backup now carries the names you gave your apps, and restoring puts them back: renames lived in one file on the phone and went no further.
- A clock that comes back years ahead after a flat battery no longer expires your supporter code for good; winding the date back still can't hand time back.
- Layout History keeps the snapshots it can read instead of dropping all ten when one of them can't be, and a second damaged layout no longer writes over the first rescue copy.
- A message with the same words as the last one shows in the island again: repeats are judged by when the app posted them, and dismissing one ends its quiet window.
- An automatic update tidies up after itself again, instead of leaving the installed APK in Folio's storage.
- Opening Software Update no longer uses up the day's check, so an automatic update still installs; a check that Android stops mid-way no longer stops the daily check for good.
- A folio://redeem link can add a supporter code but never replace one you already have. Swapping is done in Settings › Supporter, which the link now opens.
- Unfolded, the row of page dots, Search and App Library sits under Home instead of across the middle of the screen, where it ran into the widgets on the page beside it.

## [0.6.0] - 2026-09-16

### Added
- Badge options: an iOS, Classic or Glass look, three sizes, and blue, green, orange and purple colors, with a live preview (Icons & Side Bar).
- Folder options (columns and a glass, solid or clear background), app name size, and Animation Speed (Relaxed, Standard or Snappy).
- Side key: choose what holding it does: Folio's picker, a Google search without AI Overviews, or talking straight to Google, Claude or Perplexity (Side Key page).
- Software Update: check GitHub for a new Folio, download and install it after verifying its checksum and signing key; optional daily checks, update notifications and automatic installs.
- Roadmap in Settings (replaces Coming Soon): what's in this update, what's next, later and being explored.
- Tweak Library: tweaks are packages you Get (Sileo-style) and only the ones you get show in Settings › Tweaks. New installs start with none; updating keeps the tweaks you already use.
- Hidden apps stay out of the App Library, like iOS: they're listed in Settings › Search & App Library after you unlock with your fingerprint, face or PIN. A Work Apps switch hides the Personal and Work toggle.
- Left of Home can be None (Settings › Today View), alongside Today View and Google Discover.
- Support Folio in Settings, for buying me a coffee on Ko-fi.
- Save Backup and Save Theme go straight to Download/Folio; backups can be named (the default is dated).
- Fold transition: a light tick as the hinge passes halfway, a soft light sweep and a slight settle in size as the open screen clears, and a Preview slider in Fold & Displays to see the effect without folding. Phones whose hinge sensor reports in-between angles follow the real angle. Ideas from FoldFX.
- Beta Updates in Software Update: choose Folio Beta to get GitHub pre-releases too. Turning it off keeps your beta until a newer public release.
- Other notifications in the Dynamic Island (off by default): new notifications from apps you choose pop up in the island like messages, following each app's alert settings, Do Not Disturb and "Don't double up with Android pop-ups". Choose apps in Dynamic Island › Other Notifications.
- Swipe an island pop-up up to hide it early, on Home and over other apps; it stays in Notification Center.
- Finish Setting Up: if required setup is left, a card on Home brings you back (not on the first day; Not Now waits three days; never again once setup is finished), and Settings shows a progress ring.
- Folio's own short messages (an app that won't open, a panel Android couldn't open) show in the Dynamic Island instead of a toast when the island is on screen.
- Status styles for the Side Bar: Rings (battery, Wi-Fi and cellular as Activity-style rings) and Ring with Percentage, alongside Ring, Icons and Battery only.
- A red bell in the Side Bar while the phone is on silent or vibrate, like iPhone (Icons & Side Bar › Silent mode icon). "Color battery when charging or low" is now "Status colors" and covers it.
- Rounded screen corners (Wallpaper & Appearance › Screen Corners, off by default): black iPhone-style corners over Home with a size slider, for the iPhone Duo look (issue #8).
- Settings opens where you left it, on the same page and scrolled the same, like iPhone Settings.
- Report a Bug and Show Welcome Again moved into Settings › Help, so the main list is shorter; searching Settings for "bug" or "welcome" finds them.
- Screenshot Mode (Advanced): Folio shows 9:41 with full battery and signal and hides notifications, music, messages, device names, calendar events and alarms, for sharing your setup. Turns off by itself after 30 minutes.
- Headphones & speakers: when Bluetooth headphones or a speaker connects, an iPhone-style card shows its name on Home, and the Dynamic Island shows it over other apps.

### Changed
- New muted teal app icon. Olive and Soft are alternate icons in Wallpaper & Appearance › App Icon.
- Development builds install as a separate "Folio Dev" app with an amber icon, next to the release.
- Folio no longer asks for the Nearby devices (Bluetooth) permission: headphone and speaker names now come from Android's audio device list.

### Fixed
- Home can no longer stay blurred behind a Lock Cover that was turned off while it was about to show.
- Folio's short notices fall back to a regular message while Settings or another sheet covers Home, so they're never hidden behind it, and they don't follow you into other apps.
- The dock no longer overlaps the Side Bar's status when it grows (Focus or Silent icons, the Rings styles); it always starts just below it, including in Discover.
- In jiggle mode, the dock's remove buttons stay inside the Side Bar instead of hanging off its edge.
- Spotlight's Suggestions show whole rows only, so the unfolded screen shows one clean row above the keyboard instead of a cut-off second row.
- Unfolded in portrait, Home's four columns spread across the screen at every Screen zoom instead of sitting in a narrow block with a wide gap (issue #10).
- On phones without Samsung's "Continue apps on cover screen" setting, setup no longer lists it as a required step that can never be done.
- Scrolling lists and grids fade softly at their edges instead of being cut off.
- The App Library on the unfolded screen shows more, phone-sized category tiles (five across in landscape) instead of two giant columns (issue #9), and no longer covers the Side Bar's status.
- Beta labels no longer wrap in narrow Settings layouts.
- Turning on Folio gestures explains Android's "Allow restricted settings" step for apps installed from a file, with a button to App Info.
- Friendlier setup: Folio's own icon on the welcome page, Skip on every step, setup moves on by itself after you allow something, clearer tips at the end, and choosing a wallpaper no longer restarts the screen.
- Phone-sized screens keep the phone layout when Developer options' Smallest width or Display size is changed (a Fold8 cover set to 600dp got the unfolded layout, with the dock at the bottom and wide margins).
- Folders take more than two apps from the app menu: Create Folder becomes Add to Folder once you have one, listing your folders first.
- Scrolling lists fade at their edges more softly, and the fade grows in as you scroll instead of popping in.
- App Library folders open without building every app at once, and holding an app there opens its menu.
- Setting Folio up no longer leaves the screen blurred and unresponsive. A sheet opened while Folio was behind a system permission screen could stay invisible and still take every tap, so Home sat blurred with no way out but a restart. Sheets now always appear, and the Home button closes anything that's open.
- Setup and full-screen Settings pages are smoother: Home no longer blurs behind a page that covers it, which was work nobody could see.
- Message pop-ups can be handed back to Android: every messaging app in Dynamic Island settings has a button to its own notification settings, both ways, and that list stays reachable while the island is off. Apps switched to the island used to end up with no pop-up at all and no way back.
- With Dock Magnification on, sliding along the side dock no longer opens Spotlight.
- The fold preview in Settings shows one Side Bar, like the open Fold, instead of one on each page.
- Folio's own screens always show its real icon; only the Folio Dev launcher icon is amber.
- Back in Settings returns to the page you came from, such as Tweaks or Focus, instead of the top of Settings.
- Unfolded in portrait, going back to the top of Settings opens the settings list again instead of a mostly empty page.

## [0.5.1] - 2026-09-16

### Added
- Software Update (Settings › Software Update): check GitHub for a new Folio, download and install it after verifying its checksum and signing key, with optional daily checks, update notifications and automatic installs. Beta Updates lets you try pre-releases.
- Support Folio in Settings, for buying me a coffee on Ko-fi.
- Hidden Apps and Work Apps in Settings › Search & App Library.

### Changed
- Friendlier setup: Folio's icon on the welcome page, Skip on every step, setup moves on by itself after you allow something, and clearer tips at the end.
- Turning on Folio gestures explains Android's "Allow restricted settings" step for apps installed from a file, with a button to App Info.
- Hidden apps no longer appear in the App Library; they're listed in Settings after you unlock with your fingerprint, face or PIN.
- Choosing a wallpaper no longer restarts the screen.

### Fixed
- Setup no longer leaves the screen blurry and frozen until you restart the phone. The Home button now also closes anything Folio has open.
- Setup and full-screen Settings pages are smoother.
- Apps you switched to the Dynamic Island can go back to Android's own pop-ups: Settings › Dynamic Island lists each messaging app with a button to change it, even while the island is off.
- The App Library on the unfolded screen shows phone-sized category tiles instead of two giant columns (issue #9).
- Phone-sized screens keep the phone layout when Developer options' Smallest width or Display size is changed.
- Folders take more than two apps: Create Folder becomes Add to Folder once you have one.
- App Library folders open without building every app at once, and holding an app there opens its menu.
- With Dock Magnification on, sliding along the side dock no longer opens Spotlight.

## [0.5.0] - 2026-09-15

### Added
- Alternate app icons: choose Olive or Soft in Settings › Wallpaper & Appearance › App Icon.
- Folio shows up as an app: its icon (in the App Library or another launcher) opens Settings, like iOS Settings.
- Clear Badge in an app's long-press menu hides its badge until a new notification arrives.
- Clock & Calendar setting: the apps' own icons, or live icons that are Automatic, Light or Dark.
- Coming Soon in Settings: what's planned next (including Page Effects, inspired by Barrel), with a Suggest a Feature link.
- Version History in What's New, with every earlier version.
- Beta: Layout History saves Home before big changes (restoring a backup, Arrange Like iPhone, restoring an older layout) so you can go back. Settings › Backup.
- Beta: Recent App Dots mark dock apps you used in the last hour, using Usage Access. Settings › Home Screen & Dock.
- Credits for SnowBoard, Apex, Icon Restore, Lynx 2, ColorBadges, Barrel and Contributor Covenant.
- Email the Developer and Buy Me a Coffee (Ko-fi) in Settings › Help.

### Changed
- New olive green app icon.
- Glass settings in Wallpaper & Appearance: a Clear, Light, Frosted or Solid style, plus sliders for widget frost, Side Bar frost and the outline.
- Shorter, iPhone-style setup: Home app, notifications, pull-down gestures and a look. Optional permissions are asked where they're used.
- Settings has one list of permissions (Privacy & Permissions) instead of a separate Setup Checklist, and no repeated Home app rows.
- Settings tidied: the Notification Center and Control Center switch sits with their options, page dots and haptics moved to Gestures & Actions, and the Side Bar frost moved into the new Glass settings.
- Live Clock and Calendar icons match the icons around them: light or dark to fit the app icons, the tint color for Tinted, and an icon pack's own Clock and Calendar when it has them.
- Notification Center slides down like iOS instead of zooming, stacks slide apart when you expand them, swipe buttons grow in as you swipe, and cards press down when tapped.
- Built with Android Gradle Plugin 9.4, Gradle 9.7.1 and Kotlin 2.4.20.
- Layouts are checked against real Android phone, foldable, tablet and desktop screen sizes from Android Studio's device list.

### Fixed
- Back always closes Spotlight first, instead of sometimes changing the Home page behind it.
- Spotlight's keyboard comes back if it didn't appear when Spotlight opened.
- Unfolded, Settings › Icons & Side Bar no longer shows its Home preview twice.
- Restoring a Layout History snapshot handles removed apps and folders the same way as a normal refresh, so the saved layout always loads again.
- Switching the app icon keeps Folio's place on Home, in the dock and in folders.
- Sharing a theme file to Folio reads it in the background, so a slow or broken file can't freeze Home.
- Spotlight no longer closes when the keyboard drops for a moment while folding, rotating or switching to voice typing.
- Clear Badge stays cleared while an app updates the same notification, and is hidden when badges are off.
- Setup resumes on the right screen after an update.
- Discover's Side Bar follows the Glass outline setting.
- Holding the side key opens Folio's assistant picker on phones that start the assistant through a voice interaction service (like One UI 9). Folio asks Android not to share the current app's screen with it, and the Side Key page warns when Good Lock's RegiStar can override the key.
- The live Clock icon no longer occasionally stays a normal icon.
- An app's long-press menu no longer cuts off its last row when Clear Badge is showing.
- Spotlight's Cancel hides the keyboard and has a bigger touch target.

### Known issues
- During setup, the screen can go blurry and stop responding, and only a restart clears it: a sheet opened while Folio was behind a system permission screen stays invisible while still taking every tap. If it happens, restart the phone; setup works afterwards. Fixed in 0.6.0.
- Lists and grids are cut off hard at their edges instead of fading out (App Library, Settings, Notification Center, Spotlight and folders). Fixed in 0.6.0.
- On the unfolded screen in portrait, the App Library shows two oversized columns and can cover the Side Bar's status. Fixed in 0.6.0.

## [0.4.0] - 2026-09-14

### Added
- Focus: Do Not Disturb, Sleep, Personal and Work, with schedules, silencing, Home pages to show or open, Android 15 look changes, a Control Center module, and Focus actions.
- iOS-style app downloads: progress rings on updating icons, a Downloading row in the App Library, a blue dot on new apps, and an option to add new apps to Home.
- Add to Home Screen for widgets and shortcuts that apps offer, including websites from Chrome.
- Suggestions for this time of day in Spotlight, the Today View and a new Suggestions widget; Up Next widget, and Up Next on StandBy and the Lock Cover.
- Icon Stacks: swipe down on a Home icon to fan out the apps stacked behind it.
- Per-page icon size and labels.
- Themes: Classic, Dark, Tinted and Clear, plus saving and importing theme files.
- Half folded with the phone upright, Home rows that would sit in the fold move below it.
- What's New after an update.
- Report a Bug in Settings opens GitHub's bug form with your Folio version and phone filled in.
- Icon packs made for Lawnchair or Apex show up in Icon Pack, along with ADW and Nova packs.
- Share a theme file to Folio from Files or Chrome to apply it, and community themes in the repo's themes/ folder.
- Big screens: on tablets, Chromebooks and desktop windows Folio scales up like iPad instead of looking like a phone layout in a big window. Phones and foldables are unchanged.

### Changed
- Everything says Folio now: the README, user guide, troubleshooting, privacy notes, backup file name (folio-layout.json) and release files.
- Settings choices use iOS controls: a menu row with the current value that opens a checkmark menu, and segmented controls for two or three options.
- The Status Bar picks its text color from its frosted background, and uses stronger colors on light wallpapers.
- The side column is now called the Side Bar (status bar, Dynamic Island and dock), as on iPhone Duo.
- Edit while icons wiggle opens a short iOS 18-style menu under the button.
- Smart Rotate moves a stack to the widget that matters now.
- Settings previews draw Home with its real layout, widgets, status bar and dock.
- The status bar shows cellular bars, an airplane, or a searching fan when there's no Wi-Fi, instead of a line.

### Fixed
- Spotlight's and Settings' search fields no longer grow and jump when you start typing.
- Edit mode no longer pushes Home down or cuts off the bottom row: unfolded, + / Edit / Done sit beside the page dots and the Edit menu opens upward; folded, the bar clears the Dynamic Island.
- Apps and shortcuts added while a Focus hides pages go to a page that's showing.
- Selected rows in the Settings sidebar use a rounded, inset highlight; the widget resize hint is rounded.
- Long app menus no longer push Edit Home Screen and More out of view.
- Settings pages that could miss updates while a Focus hides Home pages.

## [0.3.0] - 2026-09-14

### Added
- Layouts that follow size classes: the unfolded screen in either rotation, the cover in portrait and landscape, and short windows.
- iPhone Duo-style Home: two columns on the cover in landscape, a centered page with a bottom dock bar in unfolded portrait.
- Settings split view with a sidebar on the unfolded screen; centered form sheets and iOS alerts.
- Hinge awareness: sheets, alerts and panels move off the fold when the phone is partly folded.
- Dynamic Island that wraps a side-edge camera, expands along the edge, and handles calls; live activities under the status bar as an option.
- Arrange Like iPhone; Lock Cover layout for wide windows; fold effect that follows the hinge and rotation.

## [0.2.0] - 2026-09-14

### Added
- Full-screen iOS-style Settings with search, Privacy & Permissions, Side Key and Lock Cover pages, and credits.
- Setup Assistant-style onboarding that walks through every permission Folio uses.
- Tweaks with per-screen overrides, Safe Mode after repeated crashes, local crash reports, and the Folio app icon.

### Changed
- Everything says Folio now: the README, user guide, troubleshooting, privacy notes, backup file name (folio-layout.json) and release files.
- Accessibility labels, text moved to string resources, and iOS styling across settings and pickers.

## [0.1.0] - 2026-09-13

### Added
- Folio, forked from DuoLauncher by jakesgoodapps: Notification Center and Control Center panels, Spotlight, Dynamic Island, jiggle mode, quick replies, Smart Stacks, Today View, the iOS widget gallery, Quick Settings tiles, page scrubbing, automatic text color over the wallpaper, tinted glass, and tweak-inspired features.

## DuoLauncher history (before Folio)

### 0.15.0-beta01

First public-beta preparation release. Tested scope and APK checksums accompany the release package.

- Add a skippable introduction for fresh installations and help through customization; existing layouts open directly.
- Improve recovery choices when Google Discover is unavailable.
- Show distinct Wi-Fi levels across the dot and three arcs.
- Preserve the current wallpaper when photo selection is canceled or fails, and improve interrupted preview recovery and temporary permission cleanup.
- Prepare optimized release builds, external signing, public-source export, and automated build checks.
- Add installation, update, permission, contribution, and compatibility documentation.

### 0.14.7

- Restore long-press pickup in scrollable Android widgets while preserving native vertical scrolling.

### 0.14.6

- Preserve the selected Home page or unfolded pair when returning from an app.

### 0.14.5

- Allow vertical scrolling inside native Android widgets.

### Earlier development

Home/All apps paging; right-side dock; overlapping unfolded pages and an unfolded-only workspace; native widgets and visual selection; cross-page dragging and temporary pages; work/personal profiles; Home folders; local wallpapers and daylight appearance; layout backup; Google search/Discover; long-press customization; and motion/recovery refinements.
