#!/usr/bin/env bash
# Launch smoke test for an R8 build (release or fast): installs it on a RUNNING emulator or phone, starts it and fails if it
# crashes. It exists because ART's bytecode verifier only runs when a class loads, so a bad R8 result (24 Oct 2026: a
# VerifyError in a 98-parameter LauncherScreen lambda) passes every unit test and the APK scan and then dies at launch.
#
#   tools/smoke-release-apk.sh path/to/app-release-unsigned.apk [adb serial]
#
# A release APK built without the signing secrets is unsigned, so it is signed here with the local debug key into a temp
# copy (never the release key). An APK that is already signed is installed as it is. The app is installed with `adb install -r`,
# so run it on an emulator (start it with -no-window) or a test phone, not on a phone whose Folio you use every day when the
# APK's package is com.mccal.folio; the fast build is com.mccal.folio.dev and installs over Folio Dev.
#
# Needs: adb, apksigner and aapt2 (Android SDK build-tools), a debug keystore at ~/.android/debug.keystore (password android).
# Exit 0 = the app started and stayed alive; 1 = it crashed or never started; 2 = setup problem.
set -u
apk="${1:-}"; serial="${2:-}"
[ -f "$apk" ] || { echo "Usage: $0 APK [adb serial]"; exit 2; }
sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/AppData/Local/Android/Sdk}}"
[ -d "$sdk" ] || sdk="$HOME/Library/Android/sdk"
adb="$sdk/platform-tools/adb"; [ -x "$adb" ] || adb="$sdk/platform-tools/adb.exe"
bt="$(ls -d "$sdk"/build-tools/* 2>/dev/null | sort -V | tail -1)"
apksigner="$bt/apksigner"; [ -x "$apksigner" ] || apksigner="$bt/apksigner.bat"
aapt2="$bt/aapt2"; [ -x "$aapt2" ] || aapt2="$bt/aapt2.exe"
for t in "$adb" "$apksigner" "$aapt2"; do [ -e "$t" ] || { echo "Missing $t"; exit 2; }; done
A=("$adb"); [ -n "$serial" ] && A+=(-s "$serial")
"${A[@]}" get-state >/dev/null 2>&1 || { echo "No device or emulator is connected."; exit 2; }

pkg="$("$aapt2" dump badging "$apk" 2>/dev/null | sed -n "s/^package: name='\([^']*\)'.*/\1/p" | head -1)"
[ -n "$pkg" ] || { echo "Could not read the package name from $apk"; exit 2; }
tmp="$(mktemp -d)"; trap 'rm -rf "$tmp"' EXIT
install_apk="$apk"
if ! "$apksigner" verify "$apk" >/dev/null 2>&1; then
  ks="$HOME/.android/debug.keystore"; [ -f "$ks" ] || { echo "No debug keystore at $ks"; exit 2; }
  "$apksigner" sign --ks "$ks" --ks-pass pass:android --key-pass pass:android --out "$tmp/signed.apk" "$apk" >/dev/null 2>&1 \
    || { echo "Could not sign the APK with the debug key"; exit 2; }
  install_apk="$tmp/signed.apk"
fi

echo "Installing $pkg ..."
"${A[@]}" install -r "$install_apk" 2>&1 | tail -1 | grep -q Success || { echo "FAIL: install did not succeed"; exit 1; }
started="$("${A[@]}" shell date +%s 2>/dev/null | tr -d '\r')"
"${A[@]}" logcat -c 2>/dev/null
"${A[@]}" shell monkey -p "$pkg" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
sleep 12
alive="$("${A[@]}" shell pidof "$pkg" 2>/dev/null | tr -d '\r')"
crash="$("${A[@]}" shell dumpsys dropbox --print data_app_crash 2>/dev/null | tr -d '\r' | grep -A1 "^Process: $pkg\$" | tail -1)"
verify="$("${A[@]}" logcat -d 2>/dev/null | grep -E "VerifyError|Verifier rejected|FATAL EXCEPTION" | grep -i "$pkg\|mccal" | head -3)"
# Crashes recorded before this run don't count: compare the dropbox timestamps with the start time.
recent_crash=0
while IFS= read -r ts; do
  [ -z "$ts" ] && continue
  epoch="$("${A[@]}" shell date -d "$ts" +%s 2>/dev/null | tr -d '\r')"
  [ -n "$epoch" ] && [ -n "$started" ] && [ "$epoch" -ge "$started" ] && recent_crash=1
done < <("${A[@]}" shell dumpsys dropbox --print data_app_crash 2>/dev/null | tr -d '\r' | awk -v p="$pkg" '/^Process:/{m=($2==p)} m&&/^Timestamp:/{sub(/^Timestamp: /,"");sub(/\.[0-9]+-[0-9]+$/,"");print}')
if [ -n "$verify" ] || [ "$recent_crash" = 1 ] || [ -z "$alive" ]; then
  echo "FAIL: $pkg crashed or is not running."
  [ -n "$verify" ] && echo "$verify"
  exit 1
fi
echo "PASS: $pkg started and is still running after 12 s (pid $alive)."
exit 0
