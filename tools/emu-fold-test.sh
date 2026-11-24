#!/usr/bin/env bash
# Plays the fold effect (Duet) on a foldable emulator and reads back how long it ran, from Folio's own frame trace.
#
#   bash tools/emu-fold-test.sh [-s emulator-serial] [-n folds] [-c WxH] [-o out-dir] [-r]
#     defaults: the only attached emulator, 1 fold and unfold, cover size 984x2184, a new folder under /tmp,
#     no video. -r also records the inner display with screenrecord (letterboxed once the size changes).
#
# What it does, on an emulator only (it refuses a phone, and never touches one):
#   1. turns on Folio's frame trace (setprop log.tag.FolioFoldTrace DEBUG) and restarts Folio Dev,
#   2. folds: hinge 180 -> 90 -> 0 with `adb emu sensor set hinge-angle0`, then `wm size` to the cover size,
#   3. unfolds: `wm size reset`, hinge 0 -> 90 -> 180,
#   4. reads the trace and prints the effect value m (0 clean, 1 fully folded) at each step.
# It always puts the hinge back to 180, resets the display size and turns the trace off when it ends.
#
# Limits: each `adb emu` call takes about a second, so the hinge steps are far slower than a hand and the closing
# ramp is not a speed test. The display switch is an instant size change, like the real phone's 0/90/180 reports.
# The stock Home is light and empty, which hides the blur and dim in a video: put Folio's own Moon Hills behind it
# once (Settings > Wallpaper & Appearance > Folio background). It lives in the AVD's userdata, so it stays.
# See docs/dev-tooling.md.
set -uo pipefail

serial=""; folds=1; cover="984x2184"; out=""; record=0
while getopts "s:n:c:o:rh" o; do
    case $o in s) serial=$OPTARG ;; n) folds=$OPTARG ;; c) cover=$OPTARG ;; o) out=$OPTARG ;; r) record=1 ;;
        *) sed -n '2,10p' "$0" | sed 's/^# \{0,1\}//'; exit 2 ;; esac
done
command -v adb >/dev/null || { echo "adb is not on PATH: source tools/dev-env.sh" >&2; exit 2; }
case "$folds" in ''|*[!0-9]*|0) echo "-n needs a whole number of folds, 1 or more." >&2; exit 2 ;; esac
case "$cover" in [0-9]*x[0-9]*) ;; *) echo "-c needs a size like 984x2184." >&2; exit 2 ;; esac
if [ -z "$serial" ]; then
    n=$(adb devices | awk 'NR>1 && $1 ~ /^emulator-/ && $2=="device"' | wc -l)
    [ "$n" = 1 ] || { echo "$n emulators attached; pick one with -s emulator-NNNN" >&2; adb devices -l >&2; exit 2; }
    serial=$(adb devices | awk 'NR>1 && $1 ~ /^emulator-/ && $2=="device"{print $1}')
fi
case "$serial" in emulator-*) ;; *) echo "$serial is not an emulator. This changes the display size and the hinge, so it only runs on one." >&2; exit 2 ;; esac
export MSYS_NO_PATHCONV=1
a() { adb -s "$serial" "$@"; }
py=$(command -v python3 || command -v python) || { echo "python is needed to read the trace" >&2; exit 2; }
a emu sensor status 2>/dev/null | grep -q "hinge-angle0: enabled" || { echo "$serial has no hinge sensor (a foldable AVD such as Folio_Fold_API_37 does)." >&2; exit 2; }
pkg=com.mccal.folio.dev
a shell pm list packages 2>/dev/null | tr -d '\r' | grep -qx "package:$pkg" || { echo "$pkg is not installed on $serial (install the fast build first)." >&2; exit 2; }
[ -n "$out" ] || out="${TMPDIR:-/tmp}/emu-fold-test-$(date +%Y%m%d-%H%M%S)"
mkdir -p "$out"

restore() {
    a emu sensor set hinge-angle0 180 >/dev/null 2>&1
    a shell wm size reset >/dev/null 2>&1
    a shell setprop log.tag.FolioFoldTrace INFO >/dev/null 2>&1
}
trap restore EXIT

echo "folding $serial x$folds (cover $cover), output in $out"
a emu sensor set hinge-angle0 180 >/dev/null
a shell setprop log.tag.FolioFoldTrace DEBUG
a shell am force-stop "$pkg"
a shell am start -n "$pkg/com.mccal.folio.MainActivity" >/dev/null 2>&1
sleep 8
a shell input keyevent KEYCODE_HOME; sleep 2
if [ "$record" = 1 ]; then
    display=$(a shell dumpsys SurfaceFlinger --display-id 2>/dev/null | tr -d '\r' | sed -n 's/^Display \([0-9][0-9]*\).*/\1/p' | head -1)
    a shell rm -f /sdcard/emu-fold-test.mp4
    ( a shell screenrecord ${display:+--display-id "$display"} --size 492x546 --bit-rate 8000000 --time-limit $((10 + folds * 12)) /sdcard/emu-fold-test.mp4 >/dev/null 2>&1 & )
    sleep 2
fi
a logcat -c
for i in $(seq "$folds"); do
    a emu sensor set hinge-angle0 90 >/dev/null; sleep 0.3
    a emu sensor set hinge-angle0 0 >/dev/null; sleep 0.4
    a shell wm size "$cover"; sleep 3.5
    a shell wm size reset; a emu sensor set hinge-angle0 90 >/dev/null; sleep 0.4
    a emu sensor set hinge-angle0 180 >/dev/null; sleep 3
done
a logcat -d -s FolioFoldTrace > "$out/trace.txt"
if [ "$record" = 1 ]; then
    sleep $((folds * 2 + 3))
    a pull /sdcard/emu-fold-test.mp4 "$out/fold.mp4" >/dev/null 2>&1 && echo "video: $out/fold.mp4"
fi

summarize=$(cat <<'PY'
import re, sys
rows = []
for line in sys.stdin:
    m = re.search(r"(\d\d):(\d\d):(\d\d\.\d+).*FolioFoldTrace: (.*)", line)
    if m:
        rows.append((int(m[1]) * 3600 + int(m[2]) * 60 + float(m[3]), m[4]))
if not rows:
    print("No FolioFoldTrace lines. Is Folio Dev running, and did the trace property apply?"); sys.exit(1)
t0 = rows[0][0]
frames, events = [], []
for t, s in rows:
    if s.startswith("event"):
        events.append((t - t0, s))
    else:
        d = dict(re.findall(r"(\w+)=(\S+)", s))
        try: frames.append((t - t0, float(d["m"]), d.get("close") == "true", d.get("exp"), d.get("raw")))
        except KeyError: pass
print(f"{len(frames)} traced frames, {len(events)} events\n")
print("effect value m (0 clean, 1 fully folded), changes of more than 0.1 only:")
last = None
for t, m, closing, exp, raw in frames:
    if last is None or abs(m - last) > 0.1:
        print(f"  {t:6.2f}s  m={m:5.3f}  hinge={raw:>6}  closing={str(closing):5}  expanded={exp}"); last = m
for t, s in events:
    if "display switched" in s:
        expanded = "expanded=true" in s.split("raw=")[0]
        after = [(ft, m) for ft, m, *_ in frames if ft >= t]
        start = after[0][1] if after else float("nan")
        clear = next((ft - t for ft, m in after if m < 0.05), None)
        label = "unfold (to inner)" if expanded else "fold (to cover)"
        print(f"\n{label}: display switched at {t:.2f}s, m starts {start:.2f}, clear (m<0.05) after "
              + (f"{clear:.2f}s" if clear is not None else "more than the trace shows"))
PY
)
# The trace goes in on stdin, so a Windows Python never has to understand a Git Bash path.
"$py" -c "$summarize" < "$out/trace.txt" | tee "$out/summary.txt"
