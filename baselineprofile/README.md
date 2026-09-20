# Baseline profile

`app/src/main/generated/baselineProfiles/baseline-prof.txt` is recorded from a real phone and packaged into
every build, so ART compiles Folio's startup path ahead of time instead of interpreting it on the first launch
after an install or an update.

## Regenerating

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:generateBaselineProfile
```

Two things about this project make that command unusual, both handled in the build files:

- The plugin's build types carry the application id `com.mccal.folio.profile`, because a locally signed build
  of `com.mccal.folio` can't install over the release-signed Folio on a test phone.
- `mergeIntoMain` puts the result under `src/main`, so the release build ships a profile recorded from the
  "nonMinifiedRelease" variant.

On the Galaxy Z Fold used here the Gradle task records the profile and then fails while collecting it: the test
itself passes (`failures="0"` in the JUnit XML) but the test runner can't pull the device's
`additional_test_output`, and the uninstall that follows takes the file with it. When that happens, run the
instrumentation directly and pull the profile by hand:

```bash
adb install -r -t baselineprofile/build/outputs/apk/nonMinifiedRelease/baselineprofile-nonMinifiedRelease.apk
adb install -r -t app/build/outputs/apk/nonMinifiedRelease/app-nonMinifiedRelease.apk
adb shell am instrument -w -e class com.mccal.folio.baselineprofile.StartupProfile \
  com.mccal.folio.baselineprofile/androidx.test.runner.AndroidJUnitRunner
adb pull "/storage/emulated/0/Android/media/com.mccal.folio.baselineprofile/StartupProfile_homeAndFirstGestures-baseline-prof.txt" \
  app/src/main/generated/baselineProfiles/baseline-prof.txt
```

Check the result reached the APK with `unzip -l app/build/outputs/apk/fast/app-fast.apk | grep dexopt` —
`assets/dexopt/baseline.prof` should be there.
