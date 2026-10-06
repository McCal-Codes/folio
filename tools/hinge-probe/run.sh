#!/bin/sh
# Can the shell user (what Shizuku runs your code as) listen to the fold sensors? Read-only: lists them, tries to
# register a listener for a few seconds, prints how many events and distinct values came back, and removes itself.
#   tools/hinge-probe/run.sh [seconds]        as the shell user (what Shizuku runs your code as)
#   ROOT=1 tools/hinge-probe/run.sh [seconds]  through su, for a phone with a root manager: it will ask you on the phone first
#   needs the phone on adb, the Android SDK (ANDROID_HOME or local.properties) and a JDK
set -e
cd "$(dirname "$0")"
SDK="${ANDROID_HOME:-$(sed -n 's/^sdk.dir=//p' ../../local.properties)}"
BT="$(ls -d "$SDK"/build-tools/* | tail -1)"
PLATFORM="$(ls -d "$SDK"/platforms/android-* | sort -V | tail -1)/android.jar"
OUT="$(mktemp -d)"
javac --release 11 -cp "$PLATFORM" -d "$OUT" HingeProbe.java 2>&1 | grep -v '^Note' || true
"$BT/d8" --lib "$PLATFORM" --output "$OUT/probe.jar" "$OUT"/*.class
adb push "$OUT/probe.jar" /data/local/tmp/hinge-probe.jar >/dev/null
if [ -n "$ROOT" ]; then
  adb shell "su -c 'CLASSPATH=/data/local/tmp/hinge-probe.jar app_process /system/bin HingeProbe ${1:-6}'" || true
else
  adb shell "CLASSPATH=/data/local/tmp/hinge-probe.jar app_process /system/bin HingeProbe ${1:-6}" || true
fi
adb shell rm -f /data/local/tmp/hinge-probe.jar
rm -rf "$OUT"
