# Developer tooling

Small scripts under `tools/` for working on Folio from a shell, with several people and Claude sessions sharing one
clone and one or two real phones. None of them changes the app. All are Bash (Git Bash on Windows) except the
translation check, which is Python.

## Set up the shell

```bash
source tools/dev-env.sh          # JAVA_HOME (Java 17), ANDROID_HOME, adb and the emulator on PATH
bash tools/dev-env.sh --check    # prints what it found; exit 1 if Java 17 or the SDK is missing
```

It keeps a JAVA_HOME or ANDROID_HOME that already works, reads `sdk.dir` from `local.properties`, and puts the right
JDK first on PATH even when another JDK is ahead of it. In Git Bash it also sets `MSYS_NO_PATHCONV=1`, so adb stops
rewriting `/sdcard/...` into a Windows path.

## One session at a time on a real phone

```bash
bash tools/device-claim.sh claim RFGL63GYA9M "page overview fold test"
bash tools/device-claim.sh run   RFGL63GYA9M -- adb -s RFGL63GYA9M install -r app-debug.apk
bash tools/device-claim.sh status
bash tools/device-claim.sh release RFGL63GYA9M
```

A claim is a folder in the shared git directory, so every worktree of the clone sees it. The holder is
`FOLIO_CLAIM_OWNER` or the name of your worktree. A claim expires after two hours (`FOLIO_CLAIM_TTL`), so a session
that ended without releasing does not lock the phone. Only the holder can release; `force-release` is for the
person at the keyboard. Emulators need no claim.

This only works when everyone uses it. The release rules do not check it, and `adb` is not wrapped. Say so in the
session brief ("claim the Fold8 first") until it is a habit.

## Fold and unfold, recorded

```bash
bash tools/fold-log.sh -s RFGL63GYA9M -t 600
```

Read only. It writes the fold state each second, a screenshot of every display on each change and every few
seconds, and any crash or ANR, then a `summary.txt` that says plainly if no fold was seen. Run it while someone
folds the phone, then read the summary and the screenshots.

### The foldable emulators do not fold, but they can play the effect

`Folio_Fold_API_37` (and `_Break`, `_Sweep`) declare a hinge and an outer display in `config.ini`, boot with two
displays (1968x2184 and 984x2184), and accept `adb emu fold`, `adb emu unfold`, `adb emu posture 1|2|3` and
`adb emu sensor set hinge-angle0 N`. All of them return OK. The first three do nothing in the guest: `dumpsys device_state`
stays on one state (`DEFAULT`), the focused window stays on the inner display, and the layout does not change. So a
fold test that waits for the device state passes or fails for the wrong reason.

Two things do reach Folio, and together they play the fold effect (checked 23 Nov 2026):

- `adb emu sensor set hinge-angle0 N` is delivered to the app as the hinge sensor (the guest lists "Goldfish hinge
  sensor0", and Folio's trace shows `raw=` following it). It drives the closing ramp.
- `adb shell wm size 984x2184` then `wm size reset` (emulator only, never a phone) flips Folio between its cover and
  inner layouts, because Folio decides that from the display size. That plays the half after the cut.

```bash
bash tools/emu-fold-test.sh            # one fold and unfold, prints the effect value over time
bash tools/emu-fold-test.sh -n 3 -r    # three, and record a video
```

It turns on Folio's frame trace (`adb shell setprop log.tag.FolioFoldTrace DEBUG`, then restart Folio) and prints the
effect value `m` (0 clean, 1 fully folded) with how long the cover and unfold reveals take. It only runs on an
emulator serial. Limits: each `adb emu` call takes about a second, so the hinge steps are far slower than a hand,
and the emulator renders in software, so read the timings as shape, not speed. A size change is an instant jump,
which is what the Fold8's 0/90/180 reports are too. The stock Home is light and empty and hides the blur, so give
the emulator Folio's own Moon Hills once (Settings > Wallpaper & Appearance > Folio background); it lives in the AVD's
userdata and survives reboots. The emulator image has no root and Folio Dev's fast build is not debuggable, so that
has to be done in Settings.

What was checked, 23 Nov 2026, Emulator on API 37 (`google_apis_playstore`, revision 6):

- the guest product is `emu64xa`, and there is no device-state configuration file under `/vendor/etc/devicestate`,
  `/system/etc/devicestate` or `/data/system/devicestate`;
- the emulator log says it configured a second display "for pixel_fold device" even though the AVD's device name is
  `folio_fold`, and passes `androidboot.qemu.external.displays`;
- Google's "emulator adaptive" post (Android Developers Blog, August 2026) documents `adb emu fold`, `unfold` and
  `posture` for foldable emulators, so the commands are the right ones.

Not established: why this image ignores `fold`, `unfold` and `posture`. Untried: a system image other than API 37
revision 6, or an AVD created from Android Studio's stock "7.6 Fold-in with outer display" profile (`avdmanager` is
not installed here, so that means Android Studio's Device Manager). Real hinge behavior (the Fold8 reports only 0, 90
and 180, and the user's hand sets the speed) still needs the Fold8 or a tester.

## Checks before a push

```bash
python tools/check-translations.py origin/main     # strings this change adds exist in every language
bash tools/install-hooks.sh                        # run that and the release rules on every push
```

`check-translations.py` reads the diff, so it takes a second where lint takes minutes. It caught nothing on its own
yet; it exists because pull request 662 reached CI without its Korean and Chinese strings and failed
`MissingTranslation`. It checks that the names exist, not that the wording is right.

The hook (`tools/hooks/pre-push`) runs the release rules and the translation check, and `FOLIO_PREPUSH_LINT=1` adds
lint. Installing it puts a wrapper in the shared git directory, so it affects every session in the clone from the
next push; that is why `install-hooks.sh` is a step you run, not something a build does. Skip it once with
`FOLIO_SKIP_PREPUSH=1 git push`; remove it with `bash tools/install-hooks.sh --remove`.

## Worktree sprawl

```bash
bash tools/worktree-cleanup.sh                 # list, with each branch's pull request state
bash tools/worktree-cleanup.sh --remove        # remove the ones marked safe
```

`git branch --merged` is wrong here because every pull request is squash-merged. This asks GitHub. A worktree is
removed only when its pull request is merged or closed, it has no uncommitted changes, its HEAD is the commit the
pull request ended on, it has been idle (`--idle-hours`, default 6), it is not the one you are in and it is not the
main checkout. It never deletes a branch and never forces. A branch that was pushed under another name, or never
had a pull request, shows as `none` and is left alone. The list takes about a minute; it runs `git status` in each one.
