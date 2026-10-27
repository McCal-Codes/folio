#!/usr/bin/env bash
# Launches an optimized (R8) Folio build on an emulator and fails if it does not stay up.
#
#   tools/launch-check-fast.sh [-a APK] [-m MAPPING] [-p PACKAGE] [-w SECONDS] EMULATOR_SERIAL
#
#   -a  APK to check (default: build :app:assembleFast and use app/build/outputs/apk/fast/app-fast.apk)
#   -m  R8 mapping for that APK (default: the fast build's; for a release APK, app/build/outputs/mapping/release)
#   -p  package the APK installs as (default com.mccal.folio.dev; com.mccal.folio for a release APK)
#   -w  seconds to wait after launch before judging (default 10)
#
# Debug builds and unit tests never run R8, so a fault R8 writes into the optimized code only shows when that build
# starts on a device: on 24 and 27 Oct 2026 the fast build died at launch with a java.lang.VerifyError
# (docs/standards/releases.md, REL-32). This runs tools/check-dex-registers.py on the APK first, then installs it over
# the package on the emulator, starts MainActivity, waits, and fails if the crash buffer holds a crash for the package
# or the process is gone.
#
# It installs, so it refuses anything but an emulator serial (emulator-NNNN). FOLIO_LAUNCH_CHECK_ANY_DEVICE=1 lifts
# that, for a device you mean to install on.
set -euo pipefail

repository_root=$(cd "$(dirname "$0")/.." && pwd -P)
apk=""; mapping=""; package="com.mccal.folio.dev"; wait_seconds=10
while getopts "a:m:p:w:h" opt; do
    case "$opt" in
        a) apk="$OPTARG" ;;
        m) mapping="$OPTARG" ;;
        p) package="$OPTARG" ;;
        w) wait_seconds="$OPTARG" ;;
        h|*) sed -n '2,18p' "$0" | sed 's/^# \{0,1\}//'; exit 2 ;;
    esac
done
shift $((OPTIND - 1))
serial="${1:-}"
[[ -n "$serial" ]] || { echo "Give the emulator's adb serial (adb devices), e.g. emulator-5554. -h for usage." >&2; exit 2; }
case "$wait_seconds" in ''|*[!0-9]*) echo "-w needs a whole number of seconds." >&2; exit 2 ;; esac
if [[ "$serial" != emulator-* && "${FOLIO_LAUNCH_CHECK_ANY_DEVICE:-}" != 1 ]]; then
    echo "$serial is not an emulator. This installs over $package there; set FOLIO_LAUNCH_CHECK_ANY_DEVICE=1 if you mean it." >&2
    exit 2
fi

sdk_dir=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}
if [[ -z "$sdk_dir" && -f "$repository_root/local.properties" ]]; then
    sdk_dir=$(sed -n 's/^sdk\.dir=//p' "$repository_root/local.properties")
fi
[[ -n "$sdk_dir" ]] || sdk_dir=${LOCALAPPDATA:+$LOCALAPPDATA/Android/Sdk}
adb=$(command -v adb || true)
for candidate in "$sdk_dir/platform-tools/adb" "$sdk_dir/platform-tools/adb.exe"; do
    [[ -n "$adb" ]] && break
    [[ -x "$candidate" ]] && adb="$candidate"
done
[[ -n "$adb" ]] || { echo "adb not found. Put it on PATH or set ANDROID_HOME." >&2; exit 1; }
python=$(command -v python3 || command -v python || true)
[[ -n "$python" ]] || { echo "python3 not found (needed for tools/check-dex-registers.py)." >&2; exit 1; }
adb_() { "$adb" -s "$serial" "$@"; }
sh_() { adb_ shell "$@" | tr -d '\r'; }

if [[ -z "$apk" ]]; then
    ANDROID_HOME="$sdk_dir" "$repository_root/scripts/gradle.sh" :app:assembleFast --console=plain -q
    apk="$repository_root/app/build/outputs/apk/fast/app-fast.apk"
    [[ -n "$mapping" ]] || mapping="$repository_root/app/build/outputs/mapping/fast/mapping.txt"
fi
[[ -f "$apk" ]] || { echo "APK not found: $apk" >&2; exit 1; }

# 1. Without a device: no Folio method may need more registers than R8 handles reliably. A failure here still goes on
# to the launch, so the report says whether this build actually crashes too.
registers_ok=1
if [[ -n "$mapping" ]]; then
    ANDROID_HOME="$sdk_dir" "$python" "$repository_root/tools/check-dex-registers.py" --apk "$apk" --mapping "$mapping" || registers_ok=0
else
    echo "No -m mapping given, so the register check is skipped; the launch below still runs." >&2
fi

# 2. On the emulator: install, start, wait, read the crash buffer.
[[ "$(adb_ get-state 2>/dev/null | tr -d '\r')" == device ]] || { echo "$serial is not connected (adb devices)." >&2; exit 1; }
echo "Installing $(basename "$apk") on $serial as $package"
adb_ install -r "$apk" >/dev/null
adb_ logcat -b crash -c
adb_ shell am force-stop "$package"
adb_ shell am start -W -n "$package/com.mccal.folio.MainActivity" | tr -d '\r' | grep -E '^(Status|Activity|Error)' || true
sleep "$wait_seconds"

pid=$(sh_ pidof "$package" || true)
crashes=$(adb_ logcat -d -b crash | tr -d '\r' | grep -A 30 -E "Process: $package(,|$)" || true)
if [[ -n "$crashes" ]]; then
    echo "FAIL: $package crashed after launch on $serial." >&2
    if grep -q "VerifyError" <<<"$crashes"; then
        echo "It is a VerifyError: R8 wrote code ART rejects. Look for the method in tools/check-dex-registers.py's output." >&2
    fi
    head -40 <<<"$crashes" | cut -c1-400 >&2
    exit 1
fi
if [[ -z "$pid" ]]; then
    echo "FAIL: $package is not running ${wait_seconds}s after launch, and the crash buffer is empty. Check logcat." >&2
    exit 1
fi
echo "$package launched on $serial and is still running after ${wait_seconds}s (pid $pid), no crash recorded."
if [[ "$registers_ok" != 1 ]]; then
    echo "FAIL: it launched this time, but the register check above failed, so the next build may not." >&2
    exit 1
fi
echo "OK"
