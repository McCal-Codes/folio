# Changelog

All notable changes to Folio. Versions follow [Semantic Versioning](https://semver.org) (MAJOR.MINOR.PATCH; 0.x while
Folio is in development), and this file follows [Keep a Changelog](https://keepachangelog.com). The app's
`versionCode` is derived from the version name ((MAJOR × 10000 + MINOR × 100 + PATCH) × 10 + HOTFIX), so every release sorts correctly and a fix on top of one has nine numbers of its own.
Folio shows the newest section on the phone after an update, and every version under Settings › What's New › Version History.

## [0.6.9] - Unreleased

### Added
- **Press and lift on icons:** with the new motion on (supporters first), apps, dock icons and folders all dip the same way under your finger, an icon lifts with a soft shadow while its menu is open, and after a tap it stays dipped until the app is up (half a second at most), so a slow app no longer looks like nothing happened. With Reduce Motion on the icon only dims.
- **Performance log by what Folio was doing:** besides the whole run, the report gives the frame timing (p50, p95, p99 and janky frames) of the fold animation, Home swipes, folders opening, widget resizing, pages, sheets, menus and alerts opening, and moving an icon on Home, each on their own, so a slow one shows up by name.
- **Copy Diagnostics beside Share:** Settings › Advanced › Diagnostics has Copy Diagnostics next to Share Diagnostics, so the report can be pasted into a form or a chat without going through a share sheet. It is the same text a shared report holds, and nothing leaves your phone.
- **Capabilities and Recent Activity:** Settings › Advanced › Diagnostics shows what Folio can use on this phone (Standard, Notification access and Accessibility, each On or Off, and what it is for), with a button to Android's settings for the ones that are off, and Folio's own recent activity, newest first, with a Failed filter for problems it carried on from. Both are read-only and stay on the phone.
- **Suggestions, any size:** the Suggestions widget now goes from one cell to the whole grid. Its size menu adds 1 × 1, Row (four across) and Column (four down), and the icons keep to the size of your Home icons instead of stretching to fill the card.
- **Your own picture for an app's icon:** long-press an app, More › Edit Icon › Your Picture › Choose Picture, and one icon shows a photo you pick, cropped to a square. Remove Picture or Reset Icon puts the normal icon back. The picture is copied into Folio, so it keeps working if the photo is deleted; it stays on this phone and is not part of a backup.
- **Focus that turns on by itself:** each Focus has an Also Turn On When card with Folding (Unfolded, Cover Screen or Tent), Charging and Headphones. When one holds, the Focus turns on, the list says why, and it turns off again when the signal ends. If you turn it off yourself, it stays off until the signal changes. For supporters and Folio Dev for now, and for everyone in 0.6.9.
- **Drag the dock in edit mode:** with the dock on the Side Bar, a handle under it lets you drag the dock up or down instead of using the Dock Height slider, and the handle stays on screen. For supporters and Folio Dev for now, and for everyone in 0.6.9.
- **System Bridge:** Settings › Advanced › System Bridge has one switch that turns off every way Folio reaches past a normal app. Advanced options (off by default) add an optional root hinge angle for a smoother fold animation, with a Test button, and a one-time command to grant a settings permission.
- **Fold motion:** on the open screen, icons ripple out from the hinge, the wallpaper and icons move with a little depth, and a faint light travels down the hinge. Each has its own switch in the Fold effect page and all stop with Reduce Motion.
- **Smoother fold:** the fold effect follows the hinge back up after a partial close, and the cover builds smoothly as the Fold opens.
- **StandBy tent** is detected from Android's device state on phones that report it.
- **Performance log:** Settings › Advanced › Performance log can record Folio's own CPU, memory and battery use, so slowdowns and drain can be measured instead of guessed. It stays on the phone.
- **The Market keeps your sources current:** Market settings has Refresh in the background (once a day, on Wi-Fi) and Tell me about updates, both off until you turn them on. The first time the Market opens it asks once, "Keep your packages up to date?", and Not Now is remembered and never asked again. A GitHub address works as a source, and one notice says how many updates the Market has.
- **Packages update themselves:** with Update packages in the background on, a package from your sources updates itself when the change touches none of your settings. It waits in Safe Mode and never runs beside a tap on Get. For supporters and Folio Dev for now, and for everyone in 0.6.9.
- **Bottom of Home and stronger rings:** Settings has Bottom of Home with Search button, Page dots or Nothing (Nothing is for supporters and Folio Dev for now). Dragging along the bottom still moves between pages, and the dots appear while you swipe or edit. Stronger rings gives the status rings a thicker stroke and a clearer track, and turns on by itself with Android's Bold text.
- **Updated recently, with Undo:** the Market's Installed tab lists the last packages Folio updated by itself, with Undo on each package's newest update while it is still the one installed. Undo puts the earlier version back and leaves Home exactly as it is. A package's own page has an Update automatically switch, so one package can opt out while the rest keep updating.
- **Resize a folder by its corner:** drag a folder's bottom-right corner to make it bigger or smaller, the way Good Lock's Home Up does. Folio remembers the size you leave it at, up to a bigger ceiling on the unfolded inner screen than the cover, and a folder never shrinks past what its grid needs.
- **Drop an app on another to make a folder:** drag one app's icon onto another's, on Home or in the dock, and the two become a new folder together, the way iOS and Android do. Dropping on a folder that already exists still just adds to it.
- **Drag to reorder inside a folder:** hold and drag an app over another one in an open folder to put it there instead, the way reordering works everywhere else in Folio.
- **Drag an app out of a folder, like iPhone:** hold an app in an open folder and drag it past the card's edge, and the folder steps aside while you carry the icon to any spot: any cell, another page (hold at the screen's edge), the dock, or onto another app or folder. The other icons make room as you go, and the folder closes when you let go.
- **Sort a folder A to Z:** a folder's options popup has a Sort A to Z row that puts its apps in alphabetical order in one tap.
- **Make the Big Clock your own:** long-press a Big Clock, then Customize, and the clock stays right where it is on Home while a bar lets you change it. Pick a Look (Classic, Thin, Bold, Tinted, Soft, Editorial or Stacked, with the hours over the minutes), then a color from one row: Automatic, a tint of your picture, the picture's own colors that stay readable, or White. Fine tune, closed until you open it and with a short line under each group that says what it does, has weight, size, typeface, shadow, date, 12 or 24-hour time with or without AM or PM, whether to show the next event or alarm, alignment, and Reset. Automatic and White work on any wallpaper; the picture's colors need a Folio picture behind Home, since Android doesn't let an app read its own wallpaper. A clock you never customize draws exactly what it did before.
- **Place the Big Clock anywhere:** long-press a Big Clock, then Place Freely, and drag it where you want it, a part of a cell off the grid. A dashed outline shows the cells it keeps for itself, so apps never end up underneath, and it turns red where those cells are taken. Arrows nudge it a quarter of a cell, Snap to Grid puts it back, and moving or resizing it on the grid starts it fresh. On the two-column and half-folded layouts it stays on its cells.
- **A Market tab bar that glides:** the highlight slides to the tab you pick instead of jumping, in the bar, the side rail and the sidebar. Supporters on the beta see it first.
- **Badge counts that roll:** when an app's badge number changes it rolls up or down instead of swapping. Supporters on the beta see it first.
- **Installs that finish with a pop:** when a package finishes installing, a green check pops in where the ring was, then the row settles to its button. Supporters on the beta see it first.
- **Animation Speed reaches sheets:** Settings › Gestures › Animation Speed now also speeds up or slows a page sliding in, an alert, a form sheet and the Library popup. Supporters on the beta see it first.
- **Smoother sheets and menus:** a page sliding in, a menu or alert opening, and the Library's category popup now settle on the same springs, a touch softer for pages and with a hint of life for menus, and the icon fan lands a little more gently. Supporters on the beta see it first.
- **Page dots that stretch:** with the new motion on (supporters first), the active page dot stretches toward the next one as you swipe and the tail follows, so a swipe has a visible companion. Off, or with Reduce Motion on, it is the dot it was.
- **Icons that land:** with the new motion on (supporters first), an app you move on Home or the dock lifts where it is and keeps the spot you grabbed, and when you let go it flies from your finger to its new place and settles, instead of appearing there. With Reduce Motion on it fades into place.
- **Folder or make room:** with the new motion on and folders on drop (supporters first), hold an app over the middle of another for a moment and a folder plate grows behind it; hold it over the edge of an app or a gap and the others make room, so you can put an app between two others again. Passing over an app no longer makes a folder by accident, and a folder that has to move along now slides with the apps instead of jumping.

### Changed
- **A simpler folder header:** opening a folder now shows just its name and one options button. Color and Add Apps used to sit under the title every time a folder opened; they're in that button's popup now instead.
- **What to Test:** on a beta build, Settings › Help › What to Test lists what to try, most likely broken first, with the steps. Tap It Worked, Something's Wrong (which opens a bug report with the item's name filled in) or Skip. Your answers stay on your phone.
- **A setup that asks where you are from:** setup now asks whether you are coming from iPhone or Android and starts Folio looking like it, with a How do you move around? page when you choose Android, a quiet extra step only on a phone that already has Shizuku or a root manager, and the rest under Finish setting up. Open it again from Settings › Help › Show Welcome Again, where Keep Things as They Are changes nothing.
- **Notices that fit and stay clear:** the notices in the Dynamic Island size themselves to their text (up to three lines) and stay clear of the camera and of the fold, on the cover, the open screen and half folded.
- **Undo from the update notice:** when Folio updates a package by itself, the notice says so with an Undo. Undo puts the earlier version back, leaves Home alone, and that version is not installed again by itself.
- **Two first-use hints:** once, Folio tells you to press and hold an icon to rearrange or edit Home, and, unfolded, that it is the same Home with more room.
- **Focus says when it turns itself on:** when a trigger turns a Focus on or off, a notice in the island says which Focus and why, and nothing is shown when Home is not on screen. The half-open fold pose is called Half Open, for a tent or a laptop.
- **Setup progress and quiet confirmations:** Finish setting up shows how many steps are done (1 of 2) with a tick on the finished ones, which stay where they were, and a short confirmation such as a saved layout backup is an island notice instead of a dialog you have to dismiss.

### Fixed
- **Animation Speed reaches more motion:** panels, the Dynamic Island, the notification shade, switches, segmented controls and the icon fan now follow Settings › Animation Speed, and they share a handful of named springs instead of numbers picked one by one, so they feel alike.
- **System Bridge changes take effect at once:** turning "Allow system access" off stops the root hinge feed right away, and turning it (or the root options) back on starts it again, instead of waiting for Folio to restart.
- **A scheduled Focus you turned off stays off:** a Focus on a schedule (Sleep at night, say) that you turned off, or replaced with another, no longer switches itself back on the next time Home starts or another schedule's alarm goes off. It stays off until its window ends and comes back at the next one.
- **No alarm storm when the clocks go back:** a Focus schedule with a time in the repeated hour no longer fires its alarm again and again until that time arrives.
- **Media controls and Notification access:** the island no longer risks a crash or a stuck media callback when notification access is turned off while a media session is changing.
- **Less work while apps install and when offline:** while any app installs or updates, only that app's icon redraws instead of the whole of Home, the status rail no longer redraws its whole self every frame when there is no connection, and a clock icon on Home redraws its hands without rebuilding the icon each second.
- **The Roadmap stops retrying offline:** when Folio can't reach GitHub, opening Settings › Roadmap no longer waits on the network every time; it tries again after an hour. With nothing downloaded yet, the note says the copy shown came with Folio instead of claiming it was saved on your phone.
- **Dropping an app on a folder adds it:** holding an app over a folder on Home used to slide the folder out of the way, so letting go swapped the two places and the app never went in. The folder now stays where it is, lifts a little, and takes the app, whether it comes from Home, the dock or the App Library.
- **Even rows under a widget (#13):** a widget at the top of Home now fills exactly two rows, so every row has the same spacing and the dock lines up with them, where the rows below a widget used to sit a little off. The Widget Size slider is hidden while this is on, because the widget's height is now set by the rows. For supporters and Folio Dev for now, and for everyone in 0.6.9.
- **An install cut short is put back:** if Folio is killed while a package is being installed or updated, the next start puts things back as they were and says which package didn't finish installing, instead of leaving it half applied. Safe Mode now counts only real crashes, not Android closing Folio for memory.
- **Native crashes and freezes count toward Safe Mode:** a crash in Folio's own native code, or a freeze right at the start, now counts the same as any other quick crash, so Folio offers Safe Mode instead of starting the same way again.

## [0.6.8] - 2026-10-06

### Added
- **Edit an app's icon on its own:** long-press an app, More › Edit Icon, and give that one icon its own style (Default, Dark, Tinted or Clear) and shape, or leave either like the other icons. It shows everywhere Folio draws the icon, Reset Icon puts it back, backups carry the choices, and the icon pictures Folio saves are untouched, so nothing can be left stale. Custom pictures and per-app icon-pack icons are not part of this yet.
- **More search engines, and your own:** Settings › Search & App Library › Search with Enter now offers Bing, Brave Search, Ecosia, Startpage, Qwant and Kagi beside Google and DuckDuckGo, and Custom…, where you type your own search address with %s where the search goes. Folio checks it as you type and falls back to Google if it can't be used, and only http and https addresses are accepted. The one you pick also gets a chip under a search.
- **Add several apps to a folder at once:** an open folder has an Add Apps button. It lists your apps with a check each, shows how many you have picked, and Done puts them all in as one change, so Undo takes them all back. Apps already in the folder are shown ticked, and hidden apps stay out of the list.
- **Home beside an app in split screen:** when Folio is one half of a split screen, it shows a Home page, the one you were on or your last one, instead of the App Library or Today View it was left on. It is one cover-sized page next to your app. Android and your phone decide which half Folio gets.
- **Full-Width Home for ordinary phones:** Settings › Home Screen & Dock › Layout › Full-Width Home puts the dock along the bottom and turns off the status Side Bar, and Home now uses the whole width instead of leaving the Side Bar's empty strip beside the dock. It is two settings you already had, in one switch, and nothing changes until you turn it on.
- **Full-Width Home puts things back:** turning Full-Width Home off now restores the dock placement and status Side Bar you had before, instead of going to Automatic, so a dock you set to Side Bar comes back as Side Bar. With both screens in Full-Width Home, the Side Bar returns when the last one is turned off.
- **A dock that grows as you fill it:** the dock is four places, as it has always been, until you drag an app onto it with every place full: an open place appears at the end, drop there and the dock is a place bigger, up to six. Taking an app out closes the empty place again, never below four. There is nothing to set, and a dock of four is exactly what it was. On a bottom dock bar with more apps than the window has room for, the bar stops at the room it has and scrolls. The bar and the icons ease into their new spacing as a place opens or closes (at once with Reduce Motion), and dragging an app onto a dock that already holds six gives a refusal tick and a short shake. A backup of a four-place dock is unchanged, but a Folio older than this one can't restore a bigger one.
- **A layout for the upright inner screen:** Settings › Home Screen & Dock › Layout › Inner has Separate layout when upright. Turned on, the unfolded screen held upright gets its own spacing, dock and status settings (starting as a copy of the sideways ones) and the sideways layout keeps its own; turned off, they share one again as before. Nothing changes until you turn it on.
- **Choose how Folio feels at setup:** a new setup page offers Folio (Dynamic Island, Folio's own Notification Center and Control Center, Spotlight on a swipe down) or Android style (Android's notification shade and Quick Settings, no Dynamic Island). Skipping keeps Folio, and if you already use Folio nothing changes. Each is only a set of existing switches, so everything stays changeable in Settings.
- **The notification shade over other apps:** Folio Notification Shade and Folio Quick Settings are launchable entries, so a button or gesture action on your phone that opens an app (Samsung's One-hand operation, Routines, a Home shortcut) can pull Android's shade down over whatever is open. They use Folio's gestures service, so nothing is asked for that wasn't already, and without it they take you to how to turn it on.
- **Touch and Hold, shorter or longer:** Settings › Gestures & Actions › Touch and Hold sets how long you hold an app or widget on Home before its menu opens or it lifts. Standard follows Android, including Samsung's Touch and hold delay in Accessibility, so nothing changes unless you pick Shorter or Longer.
- **Add Folio Settings to Home:** Settings › Icons & Side Bar › App Icon has an Add Folio Settings to Home button that puts the Settings icon on Home in the first free place, so getting to Folio's Settings is one tap. It reads "on Home" once it is there.
- **A switch for Today View's Suggestions:** Settings › Today View › Suggestions turns off the row of apps you may want next at the top of Today View, and with it the usage check that picks them. The Suggestions widget and Spotlight's suggestions have their own switches and are not affected.
- **Duet, the fold animation as a tweak:** Settings › Tweaks › Duet has the fold effect's own page, with a Style (iPhone Duo, Duo, Classic Glass, Deep, Subtle or Minimal), Plays When (opening, closing or both), and Frost, Shade and Tilt sliders beside Intensity and the preview. Fold & Displays links to it instead of carrying the old controls. If you updated from an earlier Folio, you keep the fold you had, which is Duo; a new install starts on iPhone Duo. A package can set Duet's look. Classic Glass, Deep and the style list use code from Duo Fold Live, hingewave and iphone-duo (MIT), credited under Settings › Credits.
- **StandBy comes on in more ways:** besides half-open like a laptop, it comes on while the phone is charging, turned on its side and set down, in any pose, as on iPhone, and optionally when it stands as a tent on the cover screen. Each is a switch in Settings › Fold & Displays › StandBy. Ideas from nightstand and StandBy-Android, re-created for Folio.
- **StandBy on the lock screen:** choose Folio StandBy as Android's screen saver from Settings › Fold & Displays › StandBy › Screen Saver, and when the screen turns off on a charger, StandBy carries on over the lock screen while the phone locks as usual. Pick the phone up or swipe up from the bottom to leave. It dims at night and moves the clock a few pixels each minute so it can't burn in. Without it, StandBy on a charger now stays up only until the screen turns off, so the phone always locks.
- **Two more Flipbook effects:** Inside Cube folds Home pages toward you as you swipe, like the walls of a room, and Stack steps them back without turning. They re-create Cylinder's Cube (inside) by Reed Weichler and Page Squeeze by Beta382, and Settings › General › Credits names both.
- **Updates for apps in the Market:** an app a source lists, like Keyd, shows Update with the version it goes from and to, instead of Open, when the source has a newer one. Folio checks the download is the same app signed by the same developer before it installs it over the old copy.
- **Widgets, one app at a time:** the widget picker has a row of app icons across the top; tap one to see only that app's widgets. While browsing, each app shows a single row with Show All for the rest, so an app with hundreds of widgets no longer stands in front of everything else.
- **An Accessibility page in Settings:** it sits under General, as in iOS Settings, and holds Big Buttons (the larger Back, Home and Recents bar), which used to be on the Dynamic Island page where few people would look. Its options are unchanged.
- **Settings search finds the setting itself:** searching "badge size", "touch and hold", "dock height", "save backup", "check for updates" and well over a hundred more names now lists the setting and opens its page. Searching a page by its full name, such as "Fold & Displays", works too; the "&" used to make it find nothing. Before, only page names and a fixed set of keywords matched. Each result names the page it opens, and tapping it scrolls to that row and lights it for a moment. A test now fails when a switch, menu or slider has no way to be found.
- **Screenshots that move:** a package page can show a short looping clip (an animated WebP) in place of a still, and Cabinet, Harborline and Flipbook now do: the panel opening, the dock swelling under a finger, a page turning. With Reduce Motion on, a clip stays on its first frame. A clip over 1.5 MB isn't played.
- **Harborline and Afterglow options:** each has an Options group on its page in Settings › Tweaks. Harborline: Amount (Subtle, Standard, Strong) for how far the icon under your finger grows, and Tick as you slide. Afterglow (the new name for Colored Albums): Music card and Island sound bars, to choose where the album art's color goes. Everything starts as it was, they are kept when a tweak is turned off, and Reset puts them back.
- **Market shows if a package will work:** a package's page has a Compatibility card (works with your Folio, needs a later Folio, which screens it covers, what it needs or replaces), and a row in a list says "Needs Folio 0.8.0" or "Inner screen only" under its name. A package that can't be installed on this phone no longer offers Get, instead of failing after you tap it.

### Changed
- **A cleaner Roadmap:** Settings › General › Roadmap shows what is in progress now, what comes next, and what is later, with the releases you already have folded under Shipped. Each item has one status, and one in the current beta says In beta rather than Done. It says when it was last updated, and when it can't reach GitHub it says so and shows the copy saved on your phone.
- **StandBy fits phones without a fold:** Settings › Fold & Displays › StandBy offers only the ways a phone can use. Half-open, like a laptop and Standing as a tent need a hinge, so a phone without one sees While charging on its side and the screen saver, which work on any phone. Folio asks Android whether the phone has a hinge rather than keeping a list of models.
- **Home is ready sooner after Folio starts:** Folio loaded every app's icon, one after another, before Home showed any, which took most of a second on a Galaxy Z Fold8. It now loads the apps on Home, in the dock and in Home's folders first, so Home is complete in about a sixth of the time. The App Library and search fill in as the rest load.
- **The App Library is ready sooner after a start:** Folio saves app icons and names between starts and uses them while an app is unchanged, so after a start the App Library and search are ready in well under half the time (about 0.3 s instead of 0.8 s on a Galaxy Z Fold8). An app that updates is loaded again, and Refresh Icons forgets them all.
- **Edit Home Screen gives a tap:** choosing Edit Home Screen from an app's menu now vibrates lightly as the icons start to jiggle, like pressing and holding an empty spot on Home does.
- **Effects say what they work with:** a page effect's page in the Market and its install sheet say it works with Flipbook. Without Flipbook on your phone they say so before Get, with a way to get Flipbook, instead of an error afterward. Try Again and restoring a backup leave an effect off until Flipbook is back.
- **Flipbook's effects are on Flipbook's page:** the built-in effects and any you add from the Market are listed on Flipbook's own page in Settings › Tweaks, instead of a menu in Gestures & Actions. Choosing one turns Flipbook on, and Settings search finds Flipbook when you type Page Effects.
- **A shorter Settings list:** the first screen goes from 26 rows to 16. Software Update, What's New, Help, the Roadmap, Credits and Advanced are under a new General page, which also has Language (Android's own per-app language screen). Ko-fi, your supporter code and the Supporters list are one Support Folio row. The Market opens from Tweaks, and Themes from Wallpaper & Appearance.
- **Easier to tap:** the Discover and All Apps buttons beside Home's page dots and the round buttons in Spotlight look the same, but a finger now has a 48 dp target to land on.
- **Settings is ordered like iOS Settings:** General and Accessibility come first, Backup is now inside General, the Dynamic Island page opens with the island and its pop-ups before the options for other apps, and the App Icons card starts with Style (Default, Dark, Tinted) and puts Notification Badges last, where its options open. Nothing is renamed and every setting keeps its value. The Brief pop-ups card no longer also shows on the Icons & Side Bar page.
- **Colored Albums is now Afterglow:** the tweak that tints the music card and the island's sound bars with the album art's color has a new name, in Settings › Tweaks and the Market. Nothing else changes, and searching "colored albums" still finds it.
- **One compatibility check for the Market and the installer:** whether a package works on your phone (it needs a later Folio, a tweak you don't have, a package that conflicts or one it depends on) is now answered in one place, in the order the installer always refused in, so a package's page can say it before you tap Get.
- **Layout History is out of beta:** the Beta label is gone from Settings › General › Backup › Layout History. It works as it did, and the snapshots you already have are still there.

### Fixed
- **Arrange Like iPhone no longer crashes Folio:** Settings › Home Screen & Dock › Arrange Like iPhone closed Folio as soon as it found an app for any of the iPhone spots. It now lays out Home's first page and dock as it says.
- **Beta Updates says it's for supporters:** Settings › Software Update › Beta Updates now says that betas are for supporters and that without a supporter code Folio only finds public releases, instead of saying you're up to date while a beta is out (#239).
- **More of Folio in your language:** in Korean and Chinese, the setup card, What's New's headings, Settings' Location fields and its Top choice, the Home for tiny cover screens, Lock Cover's buttons, the headphones card and the folder panel are translated, and TalkBack reads the Side Bar, the island rail's call and music controls and the search field's clear button in your language. In English, What's New now says "1 more new feature".
- **Dates and switches in your language:** in Korean and Chinese, StandBy, the Big Clock, Lock Cover, the Side Bar and Today View show the date in that language's order (10월 2일 금요일, 10月2日 星期五) instead of English order, and Control Center, the island, Settings and the dock chooser say On and Off, Airplane Mode, Cellular Data, Your Apps and Dock Position in your language instead of English.
- **Back in Settings goes up a page from the Folio icon too:** opened from the Folio icon or Android's settings gear, Settings is a tab beside the Market, and there Back closed everything from any page. It now goes up a page, as it does everywhere else, and closes Settings only from the top.
- **TalkBack in Settings:** the search field is read as "Search" instead of a nameless edit box, rows on the phone no longer each say "Not selected", and the preview of Home at the top is one picture, "Preview of Home", instead of about 25 of its parts and a made-up battery level read out before General.
- **Nothing covers StandBy:** while StandBy is up on Home, the island, the headphones card and the setup reminder wait instead of drawing over it.
- **Gestures say what they need:** a gesture or Control Center button set to Lock or Screenshot, with Folio's accessibility service off, did nothing. A Do Not Disturb action without Do Not Disturb access did nothing too. Each now says what to turn on.
- **Safe Mode pauses the Market refresh:** while Folio is in Safe Mode, the daily check of your Market sources waits, so a refresh that finds packages is not added to a crash loop.
- **StandBy's night text is readable:** at night the date, the battery and alarm, and the cards used a red too dim to read (2.2:1 against black). They use brighter reds now, at least 4.5:1, while the big clock keeps its dim red.
- **The charging island follows the cable:** plugging in at a charge limit, like Samsung's battery protection, shows the charging island and runs your Charging automations, and the battery topping back up at the limit no longer shows the island again with nobody touching the cable.
- **StandBy shows more of a song's title:** on the cover screen, Now Playing in StandBy puts its controls under the title, so a long title has the card's width and two lines instead of a few letters, and breaks between words.
- **Plugged in shows as charging:** at a charge limit, like Samsung's battery protection holding at 80%, Android reports the battery as not charging while the phone is still on the cable, so the Side Bar and StandBy showed it as running on battery. They follow the cable now, as iOS does.
- **The Market's buttons are easier to tap:** Get, Remove, Try Again and the Undo in a banner are full 48 dp targets now. A tap just above Undo used to dismiss the banner instead.
- **Packages Folio can't use yet are refused:** a package of a kind Folio doesn't support yet, such as a script, offered Get and then installed as nothing, labeled as running a script. The Market now says it needs a newer Folio and doesn't install it; Folio doesn't run scripts.
- **Opening a package file shows it again:** opening a `.foliopkg` from Files or a download opened the Market and nothing else, so the package couldn't be installed. Its install sheet comes up now, opening one while the Market is already open no longer lands on Settings, and a package installed from a file is listed under Installed (Opened from Files), where it can be removed. If Safe Mode turned one off after a crash, its row says so and offers Try Again, as a listed package's page does.
- **Flipbook turns the way it was designed to:** Cube and Carousel drew with their camera about six times too close, so a page shrank to a sliver partway through a swipe instead of turning like the side of a box. The camera sits where it was meant to now, and a page effect looks the same on every phone, whatever its screen density.
- **Hidden apps stay hidden:** the dock's app chooser and the list of apps for a new folder offered every app, including the ones hidden in Settings. They leave hidden apps out now, as the App Library and search do. Searching the dock's chooser or Stack Apps also finds an app you renamed by its original name, and a Chinese name by its pinyin.
- **An icon pack Folio can't read doesn't slow the App Library:** a pack with no usable list of icons was read again for every icon. It is left alone for half a minute after it fails, then tried again, so a pack that was updating still comes back.
- **Apply and Undo for a theme go in order:** choosing a theme and then Undo within a moment of each other could end with the theme applied instead of undone.
- **TalkBack reads the Focus switch once:** in Control Center it read "Turn off Focus, On, Switch". It reads the Focus's name and whether it is on.
- **Settings keeps your place on Back:** going back to a list, like Tweaks from one of its tweaks, always showed it from the top. It opens where you left it now. Back names the page it returns to, and next to the Settings list the pages under General, Support Folio and Wallpaper & Appearance have a Back, as the pages under Tweaks do.
- **Tapping a clock or a date opens its app:** tapping Folio's Clock, Big Clock, Date or Up Next widget opened the widget's options, which holding it already does, and in the Today View it started editing. A tap opens Clock or your calendar now, and in the Today View holding a widget starts editing. While you're editing Home, a tap still opens a widget's options.
- **Problem messages in your language:** in Korean and Chinese, the alert for apps that couldn't be loaded told you to tap to retry, beside its own Try Again button, and a saved Home layout that couldn't be read was reported in English. Both are in your language now. Tapping that layout message on Home brings back the choices to restore it or start fresh; it used to reload your apps, which can't repair a layout.
- **The island waits as long as you've asked:** what the island shows for a moment, like Folio's notices and new messages, went away after a fixed few seconds. It follows Time to take action in Android's accessibility settings now.
- **A quick action that won't start says so:** choosing one of an app's quick actions, from its menu or its panel, that the app had removed or turned off closed the menu and did nothing. Folio says it's unavailable now, as it does for an app that won't open.
- **TalkBack says what controls do:** the middle music button was read as "Play or pause"; it's Play or Pause now, whichever a tap will do. The widget gallery's close button is called Close instead of Back, a widget waiting to be set up is read by its name instead of a name from the app's code, and the Today View's Remove, Move Up and Move Down buttons are read in your language.
- **TalkBack follows your language:** in Korean and Chinese, TalkBack read a Focus's switch, the reply field and Send button on a message, "Show all apps" in the App Library, an app's Open, Moving and Choose, and an install's progress in English. They are in your language now. The reply Send button, a message's action buttons and the Clear button in search have a 48 dp tap area, drawn at the size they were.
- **Market clips only play on screen:** a clip scrolled out of view stops playing, and a still screenshot is no longer decoded twice.
- **Remove stays on a package you have:** when its newer version is one Folio can't read, the row and the page still offer Remove, and neither offers an Update that can't work.
- **A change right before you leave Home is kept:** a change to Home made in the last third of a second before you left Folio, or folded the phone, could be lost. Folio now saves it as Home stops.
- **Big theme files are refused:** choosing a file that isn't a Folio theme, but is very large, no longer reads it all into memory. It says it isn't a Folio theme, as before.
- **Less waiting on the main thread:** looking up your icon packs for the Icons page and for a theme, and saving the trail Folio keeps for Diagnostics, now happen off the thread that draws Home. A pack that was updating when Folio read it is tried again instead of being treated as empty.
- **An update that can't be saved says so:** if Folio can't put a checked update in place after downloading it, it says the update couldn't be downloaded instead of showing it as ready.

## [0.6.7.3] - 2026-09-28

### Fixed
- **Settings search finds tweaks:** searching Settings for a tweak by name, like Flipbook or Cabinet, found nothing. Tweaks now show up by their name, by the tweak they're based on and by what they do, and open their own page.
- **Wallpaper & Appearance lines up again:** the Dark Appearance Dims Wallpaper switch and the note under Also Set as Phone Wallpaper were drawn against the edge of their card. They have the same margins as the rows around them now.

## [0.6.7.2] - 2026-09-28

### Fixed
- **Flipbook remembers your style:** turning Flipbook off and on in Tweaks always brought back Cube, so a Carousel choice was lost. It now comes back as whichever you chose last.
- **Your photo comes back in one tap:** after choosing an artwork in Wallpaper & Appearance, tapping Your Photo opened the photo picker, so the photo you had was gone unless you found it again. It now puts your photo back; Choose a Different Photo still picks a new one.
- **Restored backups tidy up wallpapers:** restoring a Market backup that didn't include a wallpaper you had installed left that wallpaper in the Installed grid and on the phone. It's cleared now, the same as removing it.

## [0.6.7.1] - 2026-09-28

### Fixed
- **Searching works again** (#166): typing in the App Library's search field showed nothing, because the field grew to fill the panel and left the results no room. The same happened in Settings search, Choose Home Apps, the Market's sources and icon stacks.

## [0.6.7] - 2026-09-27

### Added
- **Folio speaks Korean:** the whole interface, from a community translation by qlife1146, with the newest strings drafted alongside it. A beta translation until a native speaker reviews it; Settings › General › Language lists it, and it follows the phone's language on its own.
- **White text reads on a pale wallpaper:** Home darkens a little at the top and bottom, the way iPhone does, so the clock, app names, page dots and the buttons beside them stay legible over a dawn beach or a cream painting. The middle of your wallpaper is left as it is, and it steps aside where Folio is already using dark text on Home or already dimming the wallpaper for dark appearance. Settings > Wallpaper & Appearance > Text on Home > Darken behind text turns it off. It is drawn once with the background rather than over it, so a swipe costs the same as before.
- **Flipbook, for supporters first:** Home pages turn in 3D as you swipe instead of sliding flat, after Barrel by Aaron Ash. Supporters get it from Folio's own source in the Market, and everyone gets it in 0.6.8. Cube or Carousel is picked in Settings › Gestures.
- **Folio's backgrounds are real art:** Folio ships two woodblock prints by Utagawa Hiroshige, Night View of Saruwaka-machi from the Library of Congress and Naruto Whirlpools from the National Library of New Zealand, and Settings › Wallpaper & Appearance is now one grid of everything you can put behind Home: your photo, the art Folio ships, and the art you install. Every one names its artist and its license. The drawn dunes are gone.
- **Installed wallpapers keep their credit:** Folio shows who made a picture and what it is licensed under wherever that picture is offered, and refuses one that cannot say. Removing it puts back whatever was behind Home before.
- **Folio has its own color:** buttons, switches, selection and links use Folio Teal, the teal the app icon ships in, instead of Apple's blue. Settings › Wallpaper & Appearance › Accent keeps Classic Blue for anyone who prefers it. Status colors stay as they are, so Wi-Fi is still blue and a warning still amber.
- **Folio Beta in the Market:** supporters see Folio Beta under Sources, with what you're on, the newest beta and an Update button. It's the same download Settings › Software Update makes: through your supporter code, checked against Folio's own signing key before anything installs, with Update Now or Tonight.
- **Made with AI, said plainly:** a package whose author says AI helped make it shows an AI-assisted tag beside its developer, which tools helped under Information, and the same on the Get sheet before anything installs.
- **A lasting Supporter badge:** back Folio with $15 or more and Settings › Supporter carries a Folio Supporter badge with the month you started. It stays after the code runs out, and after you remove the code. Nothing about it leaves your phone: the code says so, and Folio reads it offline.
- **Refresh Icons:** Settings › Icons & Side Bar loads your icons again, for one that a theme app changed and Folio still shows the old way.
- **Supporters in Settings:** a page beside Credits listing the people who backed Folio and said their name could be there, newest first. The list is a public file Folio fetches like the Roadmap, so a name can go on or come off without an update.

### Changed
- **Large text and right-to-left languages:** rows that held text at a fixed height now grow with the text instead of cutting it off, and back and disclosure chevrons point the right way in Arabic and Hebrew.
- **A proper welcome to the Market:** the first time it opens, the Market says what it is in three rows (packages, the sources you choose, and what is checked before anything changes), then lets you pick how Featured looks. Two steps instead of three, and it no longer says adding a source is coming later.
- **TalkBack keeps up:** it says the new Home page when a swipe lands, and reads a Dynamic Island notice when it appears.
- **Release notes you can read:** What's New draws a release's notes properly now, so a line can stress the word that matters, put a name in italics, or link to [the page that explains it](https://github.com/McCal-Codes/folio/blob/main/docs/user-guide.md), instead of showing the markers around them.
- **Folio's own buttons, menus and alerts:** the work-profile, update, retry and Add widget buttons, the folder's move menu and Rename app are Folio's controls now rather than Android's. Labels wrap instead of ending in "...", and the blues and reds they use were picked so the words stay readable on every background.
- **Panes fit the screen:** the Market and Settings show as many panes as the window has room for, the way iPad's App Store and Settings do. On a Fold's inner screen that's two: a package, a source or a nested setting opens over the list with Back, and the Market's list uses the whole width, in two columns. Three panes are for windows 1200 dp and wider.

### Fixed
- **Apps can't sneak widgets onto Home anymore:** a widget or shortcut only gets added once Android has actually approved it, so an app faking an Add to Home Screen request gets turned away.
- **Your background is decoded once, not three times:** on a cold start Home, the wallpaper preview and Folio's live wallpaper each read your background picture from disk and each kept its own copy in memory, because the shared copy was only ever filled in by the photo picker. They share one now, which is roughly twice the memory back on every start.
- **Android's wallpaper drifts the right distance:** with Android's own wallpaper behind Home, Folio asked it to slide the full width of your pages. A still picture has no spare width to slide, so it moved further than it could and the system squashed the difference. Folio now spreads the drift the way Android's own launcher does, and uses the full travel only for a live wallpaper, which can draw itself as wide as it likes.
- **The island lets go of a card it was told to drop:** swiping a message or notice away from the Everywhere overlay, or an event arriving for a kind you had turned off, used to leave the previous card on the island until the next event happened along. And an island dragged to the bottom edge is kept where you can still reach it.
- **A menu opens on its row, and a tap anywhere closes it** (#117): every pop-up menu of choices was drawn in the middle of the screen instead of under the row you tapped, and nothing but Back would close it, because the menu's window covered the whole screen and took the taps meant for what was behind it. Menus now hang from the control that opened them, flip above it near the bottom of the screen, and close on a tap outside.
- **The Settings search field takes the keyboard unfolded** (#117): tapping it opened the keyboard and closed it again, and nothing could be typed. Settings was reading the room the keyboard left as a smaller screen, folding its two panes back into one, and the list holding the field you had just tapped went with them. The keyboard covers Settings now instead of resizing it.
- **The Market holds its shape while you type** (#117): opening the keyboard, to add a source or to search the Settings tab, moved the tabs from the sidebar to the edge and took the pane beside the list away until the keyboard went again. The Market was reading the room the keyboard left as a smaller screen, the same mistake Settings made. The keyboard covers the Market now instead of resizing it.
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
