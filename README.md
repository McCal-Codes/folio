<p align="center">
  <img src="docs/images/folio-icon.png" width="96" alt="Folio app icon: two white panels unfolding on muted teal">
</p>

<h1 align="center">Folio</h1>

<p align="center">
  <b>Folio Launcher: a clean, iPhone-style Home Screen for Android</b><br>
  with the jailbreak tweaks I always wanted, and none of the lockdown.
</p>

<p align="center">
  <a href="https://github.com/McCal-Codes/folio/releases/latest"><img src="https://img.shields.io/github/v/release/McCal-Codes/folio?label=download" alt="Download the latest release"></a>
  <a href="https://github.com/McCal-Codes/folio/actions/workflows/ci.yml"><img src="https://github.com/McCal-Codes/folio/actions/workflows/ci.yml/badge.svg" alt="CI"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-MIT-blue" alt="License: MIT"></a>
  <a href="#what-you-need"><img src="https://img.shields.io/badge/Android-12%2B-3DDC84" alt="Android 12+"></a>
  <img src="https://img.shields.io/badge/root-not%20needed-2E5E66" alt="Root not needed">
  <img src="https://img.shields.io/badge/made%20for-foldables-6DB7B4" alt="Made for foldables">
  <img src="https://img.shields.io/badge/inspired%20by-jailbreak%20tweaks-BF5AF2" alt="Inspired by jailbreak tweaks">
  <a href="https://ko-fi.com/mccal"><img src="https://img.shields.io/badge/Ko--fi-buy%20me%20a%20coffee-FF5E5B" alt="Buy me a coffee on Ko-fi"></a>
</p>

<p align="center">
  <img src="docs/images/folio-inner-today.webp" width="720" alt="Folio on the unfolded Galaxy Z Fold8: the Today View with search, Suggestions and widgets on the left, Home with widgets and apps on the right, and the Side Bar with the time, battery and dock along the edge">
</p>

<div align="center">

| Version | Price | Android | Root | Tested on |
| :---: | :---: | :---: | :---: | :---: |
| 0.6.0 | Free | 12+ | Not needed | Galaxy Z Fold8 |

</div>

> **Status:** very early. I just wanted to get something out. It's a bit rusty in places, and things will settle down
> as more people use it: more feedback means I can work on performance and fine-tuning. I plan to keep maintaining it.
> Developed and tested on a Galaxy Z Fold8. See what's changed in [CHANGELOG.md](CHANGELOG.md).

**Contents:** [Why I made this](#why-i-made-this) · [Screenshots](#screenshots) · [What it does](#what-it-does) ·
[What you need](#what-you-need) · [Install](#install) · [FAQ](#faq) · [Get involved](#get-involved) ·
[Contact](#contact) · [Credits](#credits) · [License](#license)

## Why I made this

Honestly, I started this for fun. I've spent the last few months building an iPhone app, then I got a Galaxy Z Fold8
and got excited by how much Android lets you customize. At the same time I felt a little homesick for Apple, or at
least for the jailbreak features I loved. I've been in the iOS jailbreak world since iOS 7 or 8, so this was me getting
back into it.

The hardest part was learning Android: how permissions work and how launchers work. I got the hang of it. Anything
that came from someone else, or that inspired me, is credited [below](#credits). (Since I'm the only tester for now,
AI helps with bug testing.)

I want to give back to the open-source community, so Folio is free and open source.

## Who it's for

Anyone who wants a clean look, likes the Apple style without Apple's restrictions, or just wants their phone to work
the way *they* want. I'm building what I knew I couldn't have on iPhone.

I found jailbreaking in middle school and loved it. iOS keeps getting more locked down, so I moved to something more
open (for now, until Google locks theirs down too). I also have an app in testing on the App Store, and that alone was
a three-month process, so I'll do what it takes to make what I want.

**Have an idea? Tell me!** And if you want to help build Folio, or make a theme or a tweak for it, [let me know](#get-involved).

## A few of my favorite parts

Some of my favorite days on my phone were spent with tweaks like Barrel, just making the phone my own. Folio is my
way of bringing that back:

- **Badges that match the app.** Badges can take the color of each app's icon, like the ColorBadges tweak. It was more
  complex than I expected, but it's neat, so check it out (Settings › Icons & Side Bar › Badge color).
- **Themes, and more on top.** I loved SnowBoard. Android already has icon packs and themes, so Folio builds on them
  with its own themes you can save and share.
- **The iPhone Duo Side Bar.** The status bar, Dynamic Island and dock live on the right edge. Thanks to
  [DuoLauncher](https://github.com/jakesgoodapps/DuoLauncher), whose dev made it possible for me to build on top of their work.
- **Left-handed mode and Spotlight.** Flip the Side Bar to the left, and search everything from Spotlight.
- **Coming soon: Page Effects,** inspired by Barrel. See Settings › Roadmap for what else is planned.

There's a lot to this launcher, and it's fun. I hope you have as much fun with it as I've had making it.

## Screenshots

<p align="center">
  <img src="docs/images/folio-060-home.gif" width="720" alt="Folio 0.6.0 on the unfolded Galaxy Z Fold8, one feature at a time: Home and the Side Bar, Spotlight, the App Library, Control Center, jiggle mode, the widget gallery, Dock Magnification and App Panels"><br>
  <sub>Home, Spotlight, App Library, Control Center, jiggle mode, widgets and tweaks</sub>
</p>

<p align="center">
  <img src="docs/images/folio-060-personalize.gif" width="720" alt="Folio 0.6.0 personalization: tinted icons in several colors, left-handed mode, the Classic, Dark, Tinted and Clear themes, the Tweak Library, the Roadmap and the Fold Effect preview"><br>
  <sub>Tinted icons, left-handed mode, themes, the Tweak Library, the Roadmap and the fold effect</sub>
</p>

<table>
  <tr>
    <th colspan="3">Cover screen</th>
  </tr>
  <tr>
    <td align="center" width="33%"><img src="docs/images/folio-cover-home.webp" width="220" alt="Home on the cover screen: clock and date widgets, a four-column app grid, and the Side Bar with the status capsule and dock"><br><sub>Home and the Side Bar</sub></td>
    <td align="center" width="33%"><img src="docs/images/folio-cover-spotlight.webp" width="220" alt="Spotlight on the cover screen: a search field with Cancel, app suggestions, and the keyboard"><br><sub>Spotlight</sub></td>
    <td align="center" width="33%"><img src="docs/images/folio-cover-settings.webp" width="220" alt="Icons &amp; Side Bar settings: menu rows for icon pack, shape and badges, a switch, and a segmented control for icon style"><br><sub>iOS-style Settings</sub></td>
  </tr>
  <tr>
    <th colspan="3">Unfolded</th>
  </tr>
  <tr>
    <td align="center" colspan="3"><img src="docs/images/folio-inner-cc.webp" width="640" alt="Control Center on the unfolded screen: connectivity toggles, a music player, Focus, brightness and volume sliders, and quick controls"><br><sub>Control Center</sub></td>
  </tr>
  <tr>
    <td align="center" colspan="3"><img src="docs/images/folio-inner-settings.webp" width="640" alt="Settings in split view on the unfolded screen: the sidebar on the left and the Icons &amp; Side Bar page with a live Home preview on the right"><br><sub>Settings in split view, like iPad</sub></td>
  </tr>
  <tr>
    <th colspan="3">New in 0.6.0</th>
  </tr>
  <tr>
    <td align="center" colspan="3"><img src="docs/images/folio-inner-tweak-library.webp" width="640" alt="The Tweak Library on the unfolded screen: App Panels, Dock Magnification, Notification App Row, Tinted Notifications and Album Art Colors, each with its inspiration and a Get or Open button"><br><sub>The Tweak Library: get only the tweaks you want</sub></td>
  </tr>
  <tr>
    <td align="center" colspan="3"><img src="docs/images/folio-inner-roadmap.webp" width="640" alt="The Roadmap in Settings: Tweak Library, Software Update, Badge styles and Folder options marked In this update, then The Folio app and Home grid columns marked Planned"><br><sub>The Roadmap in Settings</sub></td>
  </tr>
</table>

<sub>Screenshots use the Minimal O icon pack, and the wallpaper is my own. Song details and album art are hidden.</sub>

> Icon packs and themes (like the iOS-style ones I use) are made by independent designers. If you use one, please buy it
> from its designer on Google Play and support their work. Much love.

## What it does

**Adapts to the screen, doesn't just stretch**
- Lays out by how much room there is, not what device it is: phones, flips, foldables, split-screen, tablets.
- Open the Fold and you get two Home pages side by side. Portrait unfolded gets one centered page with a dock bar. The cover in landscape gets two columns.
- Knows where the fold is: when the phone is half folded, sheets, menus and Home rows move off the crease.
- Bigger screens draw everything bigger, like iPad, instead of a tiny phone layout in the middle.

**The iOS stuff**
- Side Bar with the status bar, a Dynamic Island that wraps the camera, and the dock.
- Notification Center, Control Center, Spotlight, Today View, Smart Stacks and the widget gallery.
- Jiggle mode, folders, Icon Stacks, per-page icon size and labels, and App Library search.
- Clear Badge from an app's long-press menu.
- Focus modes with schedules and the Home pages you want to see.
- Download rings on updating apps, blue dots on new ones, and Add to Home Screen for shortcuts and websites.
- Settings built like iOS: menus with checkmarks, segmented controls, switches, sheets and alerts. What's New shows up after updates.

**Tweaks (you pick which ones you get)**
- Ideas from jailbreak tweaks I liked (Velox, Activator, Velvet, Axon, ColorFlow, Harbor), rebuilt from scratch.
- Get the ones you want from the Tweak Library, Sileo-style. Only those show up in Settings, and you can remove them any time.
- Turn a tweak on for just the cover or just the inner screen, and Safe Mode if something crashes.

**Themes**
- Save your look as a theme file, import one, or share a `.json` theme straight to Folio from Files or Chrome.
- Grab community themes (or add yours) in [`themes/`](themes/).
- Pick an app icon (Teal, Soft or Olive), and live Clock and Calendar icons that match your other icons.

**Betas (off until you turn them on)**
- Layout History: saves Home before big changes so you can go back.
- Recent App Dots: a dot beside dock apps you used in the last hour.

**Private**
- No accounts, no ads, no analytics. Folio itself doesn't send anything anywhere unless you share a crash report or check
  for updates (which only asks GitHub for the latest release).
  Google search, Discover and widgets from other apps use those apps' own services. Every permission is optional and
  explained where it's used. Every permission is listed in [PERMISSIONS.md](PERMISSIONS.md), with what happens if you
  say no; more about data in [PRIVACY.md](PRIVACY.md).

## What you need

- Android 12 or newer. No root.
- Made on a Galaxy Z Fold, but it's meant to work on any Android phone, flip, tablet or Chromebook.

| Device | Android | Status |
| --- | --- | --- |
| Samsung Galaxy Z Fold8 (SM-F971U), cover and inner screens | 17 (One UI 9) | Used for development and testing |
| Other phones, flips, tablets, Chromebooks | 12+ | Layout unit-tested on Android Studio's device sizes, not yet on real hardware. [Tell me how it goes!](https://github.com/McCal-Codes/folio/issues/new/choose) |

### Known limitations

Android doesn't allow everything iOS does, so a few things work differently:

- **Lock screen:** Folio can't replace Android's lock screen. The Lock Cover shows after you unlock.
- **Status Bar in other apps:** Android doesn't let an app reserve space inside other apps, so the Side Bar status stays on Home. The Dynamic Island and dock can float over other apps with the gestures service.
- **Pull-down panels:** opening Notification Center and Control Center from the top corners needs Folio's accessibility (gestures) service.
- **Folding from Home on Samsung:** set Settings › Display › Continue apps on cover screen to Always, or the phone locks when you fold.
- **Google Discover** needs the Google app and depends on your phone supporting it.

## Install

Download the APK from [Releases](https://github.com/McCal-Codes/folio/releases), open it on your phone and allow the
install. Then press Home and pick Folio, or open Folio and tap **Set as home app**.

<p align="center">
  <a href="https://github.com/McCal-Codes/folio/releases/latest"><img src="https://img.shields.io/badge/Download-Folio%20APK-2E5E66?style=for-the-badge&logo=android&logoColor=white" alt="Download the latest Folio APK"></a>
  <a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium%3A%2F%2Fapp%2F%257B%2522id%2522%253A%2522com.mccal.folio%2522%252C%2522url%2522%253A%2522https%253A%252F%252Fgithub.com%252FMcCal-Codes%252Ffolio%2522%252C%2522author%2522%253A%2522McCal-Codes%2522%252C%2522name%2522%253A%2522Folio%2522%257D"><img src="https://img.shields.io/badge/Add%20to-Obtainium-6DB7B4?style=for-the-badge&logo=android&logoColor=white" alt="Add Folio to Obtainium"></a>
</p>

**Updates on their own:** tap **Add to Obtainium** above on your phone.
[Obtainium](https://github.com/ImranR98/Obtainium) installs and updates apps straight from GitHub Releases, so Folio
updates like any other app without a store. The button opens Obtainium with Folio already filled in; without Obtainium
installed it explains what it is first.

Each release lists the APK's SHA-256 and the signing certificate, so you can check an update comes from the same key.
Folio's signing certificate SHA-256 is `bad8e099557b70c690e71a3fffe13561c8662c20612047ce1e2559a1c14d8441`.
A release APK can't install over a build you made yourself (different signing keys); see
[troubleshooting](docs/troubleshooting.md#an-update-wont-install).

## Build it

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:assembleFast
adb install -r app/build/outputs/apk/fast/app-fast.apk
```

`assembleFast` is the optimized build I use day to day. `:app:assembleDebug` works too (APK in
`app/build/outputs/apk/debug/`). After installing, press Home and pick Folio, or open Folio Settings and tap
**Set as home app**.

Tests: `./gradlew :app:testDebugUnitTest`

## FAQ

**Does it need root?** No. Folio is a normal Android launcher.

**Why does it ask for so many permissions?** It doesn't need most of them. Only being your Home app is required. The
rest are optional and asked for where they're used, like notification access for the Dynamic Island, or the gestures
service for pull-down panels. Every permission is listed with what it's for in Settings › Privacy & Permissions, in
[PERMISSIONS.md](PERMISSIONS.md) (all 14, with what breaks if you say no) and in [PRIVACY.md](PRIVACY.md).

**Does Folio send my data anywhere?** No. No accounts, no ads, no analytics. Crash reports stay on your phone unless you
share one. Software Update only asks GitHub for the latest release when you check (or once a day if you turn that on).

**How do I update?** Settings › Software Update checks GitHub, verifies the download and installs it. You can also
turn on daily checks and automatic installs there, or use Obtainium.

**Can I try new features early?** Yes: Settings › Software Update › Beta Updates › Folio Beta. Betas are GitHub
pre-releases, signed with the same key, and may have bugs.

**How do I go back to my old launcher?** Settings › Apps › Default apps › Home app on your phone (the exact path can
vary by phone). Your Folio layout stays saved if you switch back later.

**Something broke. What do I do?** Settings › Help › Report a Bug in Folio opens the bug form with your details filled
in. If Folio crashes a few times in a row, it opens in Safe Mode with tweaks paused. More in
[troubleshooting](docs/troubleshooting.md).

**What's coming next?** See Settings › Roadmap in the app, or suggest something with the
[feature form](https://github.com/McCal-Codes/folio/issues/new/choose).

## Where things are

- `app/src/main/java/com/mccal/folio/`: the launcher (Kotlin, Jetpack Compose, Jetpack WindowManager)
  - `LayoutModel.kt`: screen sizes, Home layout and big-screen scaling
  - `LauncherScreen.kt`, `HomeWorkspace.kt`: Home, pages and editing
  - `StatusRail.kt`, `CutoutIsland.kt`: the Side Bar status bar and Dynamic Island
  - `CustomizationSheet.kt`, `IosControls.kt`, `FolioSheet.kt`: Settings and the iOS controls
- `app/src/test/`: unit tests, including the screen size tests
- `themes/`: community themes and the theme format
- `docs/`: [user guide](docs/user-guide.md), [troubleshooting](docs/troubleshooting.md), [architecture](docs/architecture.md)

## Get involved

**Bug reports, please!** That's the help I need most right now. Once there's a community, I'll give a shoutout (or
something for whoever finds the most). Themes and code can come later. I'm one person doing this, but anyone who's
interested is welcome. Read [CONTRIBUTING.md](CONTRIBUTING.md) first. No GPL code and nothing that needs root.

- Found a bug? In Folio, go to Settings › Help › **Report a Bug**, or use the [bug form](https://github.com/McCal-Codes/folio/issues/new/choose).
- Questions or want to show off your setup? [Discussions](https://github.com/McCal-Codes/folio/discussions).
- Security problem? Report it privately, see [SECURITY.md](SECURITY.md).
- Everyone here follows the [Code of Conduct](CODE_OF_CONDUCT.md).

If you want to support the project, you can [buy me a coffee on Ko-fi](https://ko-fi.com/mccal). It's never
expected, but it's always appreciated.

## Contact

- **Email:** [contact@mcc-cal.com](mailto:contact@mcc-cal.com) (for serious things)
- **Discord:** [the Folio server](https://discord.gg/pxQT9Xj2Ed), or mcc_cal
- **Reddit:** [u/wolftech029](https://www.reddit.com/user/wolftech029)
- **X (Twitter):** [@mcc_cal_](https://x.com/mcc_cal_), where I post new features and sneak peeks

## Thank you

To the iOS jailbreak community, for inspiring me since I was a kid. To the few people on Reddit making things I could
build a bridge from. And to anyone else who's interested in my fun little project.

## Credits
- **[DuoLauncher](https://github.com/jakesgoodapps/DuoLauncher)** by [jakesgoodapps](https://github.com/jakesgoodapps) and the Duo Launcher contributors (MIT): Folio's starting codebase, giving it the iPhone Duo-style right-side dock and layouts for both Fold screens, paired Home pages and the unfolded workspace, Android widgets, folders, work profiles, wallpapers with sunrise/sunset switching, and Google search/Discover.
- **[iphone-duo](https://github.com/chuspeeism/iphone-duo)** by chuspeeism (MIT): the blur/darkening curves and hinge-angle model that Folio's fold shader follows.
- **u/moomanjohnny** on r/GalaxyFold: the Galaxy Z Fold 8 proof of concept that showed screenshots + shaders + hinge sensors can recreate the iPhone Duo unfold; inspired Folio's screenshot-morph fold style. No code was released or used.
- **[ZFoldDuo](https://github.com/nnnnnnn0090/Z-Fold-Duo-TEST)** by nnnnnnn0090 (MIT): showed that a Galaxy Z Fold7/Fold8 can read Samsung's internal hinge angle on the phone itself, without root, using Wireless Debugging. Research for Enhanced Fold Tracking, which is being explored; no ZFoldDuo code is in Folio yet.
- **FoldFX** by u/FixHour8452 on r/GalaxyFold: ideas for the fold transition (a haptic tick halfway, a light sweep, a slight scale, and following a smooth hinge angle where the phone reports one). Re-created from scratch; no code was used.
- **[QuickLaunch](https://github.com/AhmedTheGeek/QuickLaunch)** by AhmedTheGeek (GPL-3.0): ideas for Spotlight, namely requesting the keyboard after the first frame, frecency ranking with a 7-day half-life, and drag-to-split-screen. Re-implemented independently; no QuickLaunch code is included.
- **iOS jailbreak tweaks** (ideas only, re-created from scratch; no code): Velox by Phillip Tennen (app panels), Activator by Ryan Petrich (gesture and event actions), Axon by Nepeta (notification app row), Velvet by NoisyFlake & HiMyNameisUbik (tinted notifications), ColorFlow by David Goldman (album-art colors), Harbor by Evan Swick (dock magnification), SnowBoard by SparkDev (themes), Apex by Sticktron (Icon Stacks), Icon Restore (Layout History), Lynx 2 (recent-app dots), ColorBadges (badges that match the app) and Barrel (Page Effects, coming soon). Authors for Velox through Harbor as credited by iDownloadBlog.

Folio started from [DuoLauncher](https://github.com/jakesgoodapps/DuoLauncher)
(commit `f1bc0f1`, 10 Sep 2026), © 2026 Duo Launcher contributors, used under the MIT License (see `LICENSE`).
Third-party dependency licenses are listed in `THIRD_PARTY_NOTICES.md`.
Apple, iPhone and iPad are trademarks of Apple Inc., registered in the U.S. and other countries and regions.
Google and Android are trademarks of Google LLC. Samsung and Galaxy are trademarks of Samsung Electronics Co., Ltd.
Folio is an independent project and is not affiliated with or endorsed by Apple, Google or Samsung.

## License

Folio is released under the [MIT License](LICENSE). It started from DuoLauncher, which is also MIT; that notice is kept
in `LICENSE`. Third-party licenses are in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
