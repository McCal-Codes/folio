#!/usr/bin/env bash
# Records what a foldable does while someone folds and unfolds it: the fold state each second, a screenshot of every
# display whenever the state changes (and every few seconds besides), and any crash from the app. It only reads; it
# never taps, installs or changes a setting, so it is safe next to anyone else on the phone.
#
#   bash tools/fold-log.sh [-s serial] [-t seconds] [-o out-dir] [-p package] [-e every-n-seconds]
#     defaults: the only attached device, 600 seconds, a new folder under /tmp, com.mccal.folio.dev, 5
#
# Writes states.txt (time, fold state, focused window), PNGs named HHMMSS-<display>.png, logcat.txt and summary.txt.
# A real phone's fold states come from the hinge. The foldable emulators we have do not report any (see
# docs/dev-tooling.md), so on those the log says "DEFAULT" throughout and the summary says no fold was seen.
set -uo pipefail

serial=""; secs=600; out=""; pkg="com.mccal.folio.dev"; every=5
while getopts "s:t:o:p:e:" o; do
    case $o in s) serial=$OPTARG ;; t) secs=$OPTARG ;; o) out=$OPTARG ;; p) pkg=$OPTARG ;; e) every=$OPTARG ;; *) sed -n '2,12p' "$0" | sed 's/^# \{0,1\}//'; exit 2 ;; esac
done
command -v adb >/dev/null || { echo "adb is not on PATH: source tools/dev-env.sh" >&2; exit 2; }
if [ -z "$serial" ]; then
    n=$(adb devices | awk 'NR>1 && $2=="device"' | wc -l)
    [ "$n" = 1 ] || { echo "$n devices attached; pick one with -s <serial>" >&2; adb devices -l >&2; exit 2; }
    serial=$(adb devices | awk 'NR>1 && $2=="device"{print $1}')
fi
a() { adb -s "$serial" "$@"; }
[ -n "$out" ] || out="${TMPDIR:-/tmp}/fold-log-$(date +%Y%m%d-%H%M%S)"
mkdir -p "$out"; : > "$out/states.txt"
displays=$(a shell dumpsys SurfaceFlinger --display-id 2>/dev/null | tr -d '\r' | sed -n 's/^Display \([0-9][0-9]*\).*/\1/p')
[ -n "$displays" ] || displays=""
echo "logging $serial for ${secs}s into $out (displays: ${displays:-default})"

shot() {
    local t id f
    t=$(date +%H%M%S)
    if [ -z "$displays" ]; then displays_run="default"; else displays_run=$displays; fi
    for id in $displays_run; do
        f="$out/$t-$id.png"
        if [ "$id" = default ]; then a exec-out screencap -p > "$f" 2>/dev/null; else a exec-out screencap -p -d "$id" > "$f" 2>/dev/null; fi
        # An off display answers with text, not a PNG; keep only real images.
        [ "$(head -c 4 "$f" 2>/dev/null | od -An -tx1 | tr -d ' \n')" = 89504e47 ] || rm -f "$f"
    done
}

a logcat -c >/dev/null 2>&1
prev=""; changes=0; i=0
while [ "$i" -lt "$secs" ]; do
    t=$(date +%H%M%S)
    st=$(a shell "dumpsys device_state | grep mCommittedState | sed 's/.*name=//; s/,.*//'" 2>/dev/null | tr -d '\r\n')
    focus=$(a shell "dumpsys window displays | grep mCurrentFocus | grep -v null | sed 's/.*{[^ ]* [^ ]* //; s/}.*//'" 2>/dev/null | tr -d '\r' | tr '\n' ' ')
    echo "$t $st $focus" >> "$out/states.txt"
    if [ "$st" != "$prev" ]; then [ -n "$prev" ] && changes=$((changes + 1)); shot; elif [ $((i % every)) -eq 0 ]; then shot; fi
    prev=$st; i=$((i + 1)); sleep 1
done

a logcat -d -v time > "$out/logcat.txt" 2>&1
crashes=$(grep -cE 'FATAL EXCEPTION|ANR in' "$out/logcat.txt")
mine=$(grep -E 'FATAL EXCEPTION|ANR in' -A3 "$out/logcat.txt" | grep -c "$pkg")
states=$(awk '{print $2}' "$out/states.txt" | uniq | sed "s/'//g" | paste -sd' ' -)
{
    echo "device: $serial   package: $pkg   seconds: $secs"
    echo "fold states seen, in order: $states"
    echo "fold state changes: $changes"
    [ "$changes" -gt 0 ] || echo "NO FOLD SEEN: the device never changed state (a foldable emulator may not report fold states, or nobody folded it)"
    echo "crashes or ANRs in the log: $crashes (mentioning $pkg: $mine)"
    echo "screenshots: $(ls "$out"/*.png 2>/dev/null | wc -l)"
} | tee "$out/summary.txt"
