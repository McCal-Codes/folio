#!/usr/bin/env bash
# Records what Android itself says Folio cost over a stretch of time, for comparing with the in-app Performance log.
#
#   tools/perf-capture.sh [-p com.mccal.folio] [-d SECONDS] [-s ADB_SERIAL] OUTPUT_DIR
#
#   -p  package to measure (default com.mccal.folio.dev; use com.mccal.folio for the release app)
#   -d  how long to record, in seconds (default 600)
#   -s  adb serial, when more than one device is connected
#
# It is read-only on the phone, with one exception: it runs `dumpsys batterystats --reset` (and `dumpsys gfxinfo
# <package> reset`) at the start, which clear Android's own counters so the numbers cover only this run. It changes no
# setting, installs nothing and needs no root. Unplug the phone from power first (or the battery figures mean little),
# do the thing you want to measure (idle, Home, folding) while it waits, and leave the screen as you want it measured.
#
# Raw output is saved under OUTPUT_DIR; a short summary is printed and saved there as summary.txt.
set -u

package="com.mccal.folio.dev"; seconds=600; serial=""
while getopts "p:d:s:h" opt; do
  case "$opt" in
    p) package="$OPTARG" ;;
    d) seconds="$OPTARG" ;;
    s) serial="$OPTARG" ;;
    h|*) sed -n '2,15p' "$0" | sed 's/^# \{0,1\}//'; exit 2 ;;
  esac
done
shift $((OPTIND - 1))
out="${1:-}"
[ -n "$out" ] || { echo "Give an output folder as the last argument. Run with -h for usage." >&2; exit 2; }
case "$seconds" in ''|*[!0-9]*) echo "-d needs a whole number of seconds." >&2; exit 2 ;; esac

command -v adb >/dev/null || { echo "adb is not on your PATH." >&2; exit 1; }
adb_() { if [ -n "$serial" ]; then adb -s "$serial" "$@"; else adb "$@"; fi; }
sh_() { adb_ shell "$@" | tr -d '\r'; }

adb_ get-state >/dev/null 2>&1 || { echo "No phone found over adb (check the cable, USB debugging, and -s)." >&2; exit 1; }
pid="$(sh_ pidof "$package" | awk '{print $1}')"
[ -n "$pid" ] || { echo "$package is not running. Open it once, then run this again." >&2; exit 1; }
uid="$(sh_ dumpsys package "$package" | awk -F'[= ]+' '/userId=/{print $3; exit}')"

mkdir -p "$out" || exit 1
started="$(date '+%Y-%m-%d %H:%M:%S')"
sh_ dumpsys battery > "$out/battery-before.txt"
if grep -Eq "(AC|USB|Wireless|Dock) powered: true" "$out/battery-before.txt"; then
  echo "Warning: the phone is on power. Battery numbers will not mean much." >&2
fi

echo "Resetting Android's battery and frame counters, then recording $seconds s for $package (pid $pid)."
sh_ dumpsys batterystats --reset >/dev/null
sh_ dumpsys gfxinfo "$package" reset >/dev/null 2>&1
sleep "$seconds"

# The app may have been restarted while we waited; say so instead of reading the wrong process.
pid_after="$(sh_ pidof "$package" | awk '{print $1}')"
[ "$pid_after" = "$pid" ] || echo "Note: $package restarted during the run (pid $pid, now ${pid_after:-none}); the figures cover the new process." >&2

sh_ dumpsys battery > "$out/battery-after.txt"
sh_ dumpsys batterystats --charged "$package" > "$out/batterystats.txt" 2>&1
sh_ dumpsys cpuinfo > "$out/cpuinfo.txt"
sh_ dumpsys gfxinfo "$package" framestats > "$out/gfxinfo-framestats.txt" 2>&1
sh_ dumpsys gfxinfo "$package" > "$out/gfxinfo.txt" 2>&1
sh_ top -H -b -n 2 -d 3 -p "${pid_after:-$pid}" > "$out/top-threads.txt" 2>&1
sh_ getprop ro.product.model > "$out/model.txt"

level() { awk '/level:/{print $2; exit}' "$1"; }
{
  echo "Folio performance capture (Android's own account)"
  echo "Package $package, pid ${pid_after:-$pid}, uid ${uid:-unknown}, model $(cat "$out/model.txt")"
  echo "Started $started, recorded $seconds s"
  echo
  echo "Battery level: $(level "$out/battery-before.txt") % -> $(level "$out/battery-after.txt") % (whole phone, whole percents)"
  echo
  echo "Estimated power use for this app (batterystats; mAh since the reset):"
  if [ -n "${uid:-}" ] && [ "$uid" -ge 10000 ] 2>/dev/null; then
    grep -E "^ *Uid u0a$((uid - 10000)):" "$out/batterystats.txt" | head -3
  else
    echo "  (uid not found)"
  fi
  echo
  echo "CPU (dumpsys cpuinfo, load over the last minutes; user + kernel):"
  grep -E "$package" "$out/cpuinfo.txt" | head -3
  echo
  echo "Frames (dumpsys gfxinfo; counts only frames drawn since the reset):"
  grep -E "Total frames rendered|Janky frames|50th percentile|90th percentile|95th percentile|99th percentile" "$out/gfxinfo.txt"
  echo
  echo "Busiest threads (top -H, the second sample):"
  awk '/^ *PID|^ *TID/{n++} n>=2' "$out/top-threads.txt" | head -8
  echo
  echo "Raw output: $out"
} | tee "$out/summary.txt"
