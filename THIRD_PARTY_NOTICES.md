# Third-party notices

Folio is derived from [Duo Launcher](https://github.com/jakesgoodapps/DuoLauncher) by [jakesgoodapps](https://github.com/jakesgoodapps) (MIT, commit `f1bc0f1`) and uses the MIT license in LICENSE. Dependencies retain their own licenses. The app includes this notice and the Apache 2.0 license text under `assets/licenses/`.

| Component family | Source | License |
| --- | --- | --- |
| AndroidX, Jetpack Compose, Material components and icons, Window | https://android.googlesource.com/platform/frameworks/support/ | Apache 2.0 |
| Kotlin standard library | https://github.com/JetBrains/kotlin | Apache 2.0 |
| Kotlin coroutines | https://github.com/Kotlin/kotlinx.coroutines | Apache 2.0 |
| Kotlin serialization | https://github.com/Kotlin/kotlinx.serialization | Apache 2.0 |
| JetBrains annotations | https://github.com/JetBrains/java-annotations | Apache 2.0 |
| Guava ListenableFuture | https://github.com/google/guava | Apache 2.0 |
| JSpecify annotations | https://github.com/jspecify/jspecify | Apache 2.0 |
| Gradle wrapper and build tooling | https://github.com/gradle/gradle | Apache 2.0; build-tool distributions include their additional notices |

The Gradle dependency graph records the resolved artifact versions. Test and build tools are not application features; their upstream distributions provide their respective notices.

The default wallpaper and launcher icon are generated locally from project drawing code/resources. Installed application icons and widget content belong to their respective providers. Google Discover and Google search run in the installed Google application; that application and its content are not redistributed here.

Private design-study images, copied reference files, device captures, and probe research are excluded from the public source package. Apple, Google, Android, Samsung, and other referenced names are trademarks of their respective owners; this project is unaffiliated with those companies.

## Code included in Duet, the fold animation

Duet's styles carry renderer math from these MIT projects. Each file keeps its upstream notice in `third_party/duet/`, and `third_party/duet/PROVENANCE.md` pins the commit, says what was taken and what Folio changed.

| Project | Author | License | What Folio took |
| --- | --- | --- | --- |
| [duo-fold-live](https://github.com/joeconsorti/duo-fold-live) | joeconsorti and contributors | MIT | Classic Glass projection, the inner blur, the Fold-Only and Unfold-Only idea |
| [iphone-duo](https://github.com/chuspeeism/iphone-duo) | jadon7 (chuspeeism) | MIT | The projection and shade model Classic Glass and the iPhone Duo style are adapted from, by way of duo-fold-live |
| [hingewave](https://github.com/Ant-lib/hingewave) | Ant-lib | MIT | The perspective projection and blur floor behind the Deep style |
| [FoldFX](https://github.com/iamkeeler/FoldFX) | Gary Keeler | MIT | The idea of one style catalog that the picker and the renderer both read (no code) |

[duo-open](https://github.com/marcoazeem/duo-open) (marcoazeem) gave ideas only: no code.

## Inspiration (no code included)

| Project | Author | License | What inspired Folio |
| --- | --- | --- | --- |
| Galaxy Z Fold 8 iPhone Duo animation demo (r/GalaxyFold) | u/moomanjohnny | — (no code released) | Screenshot + shader + hinge-sensor approach behind the screenshot-morph fold style |
| [QuickLaunch](https://github.com/AhmedTheGeek/QuickLaunch) | AhmedTheGeek | GPL-3.0 | Spotlight ideas: keyboard after first frame, frecency ranking (7-day half-life), drag to split screen. Independently re-implemented; no source copied |
| Velox (iOS jailbreak tweak) | Phillip Tennen; Velox Reloaded by DanielVolt | Proprietary (idea only) | Swipe up on an app icon for a small panel with that app's shortcuts, notifications and media |
| Activator (iOS jailbreak tweak) | Ryan Petrich | Proprietary (idea only) | Gestures and events (charging, Bluetooth, headphones) that trigger any action |
| Axon (iOS jailbreak tweak) | Nepeta | Proprietary (idea only) | Row of app icons above notifications to filter them by app |
| Velvet (iOS jailbreak tweak) | NoisyFlake & HiMyNameisUbik | Proprietary (idea only) | Notification cards tinted with their app's color |
| ColorFlow (iOS jailbreak tweak) | David Goldman; successor ChromaFlow by Ryan Nair | Proprietary (idea only) | Music player colors taken from the album art |
| Harbor (iOS jailbreak tweak) | Evan Swick | Proprietary (idea only) | macOS-style dock magnification under the finger |
