#!/usr/bin/env bash
# One session at a time on a real phone. Sessions and people share this clone, and the only thing that stopped two of
# them installing over each other on the Fold was a message. This is a lock they can all see.
#
#   bash tools/device-claim.sh claim   <serial> [note]    take the device (fails if someone else holds it)
#   bash tools/device-claim.sh release <serial>           give it back (only the holder can)
#   bash tools/device-claim.sh status  [serial]           who holds what, and for how long
#   bash tools/device-claim.sh guard   <serial>           exit 0 if you hold it, 1 if someone else does, 2 if nobody
#   bash tools/device-claim.sh run     <serial> -- <cmd>  run cmd only if you hold it, e.g. -- adb -s SERIAL install -r app.apk
#   bash tools/device-claim.sh force-release <serial>     the user's override, for a holder that is gone
#
# The holder is FOLIO_CLAIM_OWNER, or the name of the worktree you are standing in, so every session has its own.
# A claim expires after FOLIO_CLAIM_TTL seconds (default 7200) so a session that ended without releasing does not
# block the phone for ever. Claims live in the shared git directory, so every worktree of this clone sees them.
# Emulators need no claim: ANDROID_SERIAL names one, and a disposable emulator is nobody's phone.
set -uo pipefail

top=$(git rev-parse --show-toplevel 2>/dev/null) || { echo "run this inside the Folio clone" >&2; exit 2; }
common=$(git -C "$top" rev-parse --path-format=absolute --git-common-dir 2>/dev/null || git -C "$top" rev-parse --git-common-dir)
case "$common" in /*|[A-Za-z]:*) ;; *) common="$top/$common" ;; esac
root="$common/folio-device-claims"
ttl=${FOLIO_CLAIM_TTL:-7200}
me=${FOLIO_CLAIM_OWNER:-$(basename "$top")}
now=$(date +%s)

dir_for() { printf '%s/%s' "$root" "$(printf '%s' "$1" | tr -c 'A-Za-z0-9._-' '_')"; }
read_field() { [ -f "$1/$2" ] && cat "$1/$2" || true; }
age_of() { echo $((now - $(read_field "$1" time))); }

human() {
    local s=$1
    if [ "$s" -ge 3600 ]; then printf '%dh %dm' $((s / 3600)) $((s % 3600 / 60)); elif [ "$s" -ge 60 ]; then printf '%dm' $((s / 60)); else printf '%ds' "$s"; fi
}

# Removes a claim that has outlived its TTL, so it is never reported as held.
expire_if_stale() {
    local d=$1
    [ -d "$d" ] || return 0
    local t; t=$(read_field "$d" time)
    if [ -z "$t" ] || [ $((now - t)) -ge "$ttl" ]; then rm -rf "$d"; fi
}

cmd=${1:-}; serial=${2:-}
case "$cmd" in
claim)
    [ -n "$serial" ] || { echo "usage: device-claim.sh claim <serial> [note]" >&2; exit 2; }
    mkdir -p "$root"; d=$(dir_for "$serial"); expire_if_stale "$d"
    if mkdir "$d" 2>/dev/null; then
        printf '%s' "$me" > "$d/owner"; printf '%s' "$now" > "$d/time"; printf '%s' "${3:-}" > "$d/note"
        echo "claimed $serial for $me (expires in $(human "$ttl") unless renewed)"
    else
        owner=$(read_field "$d" owner)
        if [ "$owner" = "$me" ]; then
            printf '%s' "$now" > "$d/time"; echo "renewed your claim on $serial"
        else
            echo "BUSY: $serial is held by $owner for $(human "$(age_of "$d")")$( [ -s "$d/note" ] && printf ' (%s)' "$(cat "$d/note")")" >&2
            echo "Ask them, or wait. The claim expires $(human $((ttl - $(age_of "$d")))) from now." >&2
            exit 1
        fi
    fi ;;
release)
    [ -n "$serial" ] || { echo "usage: device-claim.sh release <serial>" >&2; exit 2; }
    d=$(dir_for "$serial"); expire_if_stale "$d"
    [ -d "$d" ] || { echo "$serial was not claimed"; exit 0; }
    owner=$(read_field "$d" owner)
    if [ "$owner" = "$me" ]; then rm -rf "$d"; echo "released $serial"
    else echo "NOT RELEASED: $serial is held by $owner, not $me" >&2; exit 1; fi ;;
force-release)
    [ -n "$serial" ] || { echo "usage: device-claim.sh force-release <serial>" >&2; exit 2; }
    d=$(dir_for "$serial"); owner=$(read_field "$d" owner); rm -rf "$d"
    echo "cleared the claim on $serial${owner:+ (was $owner)}" ;;
status)
    [ -d "$root" ] || { echo "no devices are claimed"; exit 0; }
    found=0
    for d in "$root"/*/; do
        d=${d%/}; [ -d "$d" ] || continue
        expire_if_stale "$d"; [ -d "$d" ] || continue
        [ -z "$serial" ] || [ "$d" = "$(dir_for "$serial")" ] || continue
        found=1
        printf '%s  held by %s for %s%s\n' "$(basename "$d")" "$(read_field "$d" owner)" "$(human "$(age_of "$d")")" "$( [ -s "$d/note" ] && printf '  (%s)' "$(cat "$d/note")")"
    done
    [ "$found" = 1 ] || echo "no devices are claimed" ;;
guard)
    [ -n "$serial" ] || { echo "usage: device-claim.sh guard <serial>" >&2; exit 2; }
    d=$(dir_for "$serial"); expire_if_stale "$d"
    [ -d "$d" ] || { echo "nobody holds $serial; claim it first: bash tools/device-claim.sh claim $serial" >&2; exit 2; }
    owner=$(read_field "$d" owner)
    if [ "$owner" = "$me" ]; then exit 0; fi
    echo "BUSY: $serial is held by $owner, not $me" >&2; exit 1 ;;
run)
    [ -n "$serial" ] || { echo "usage: device-claim.sh run <serial> -- <command...>" >&2; exit 2; }
    shift 2; [ "${1:-}" = "--" ] && shift
    [ "$#" -gt 0 ] || { echo "nothing to run after --" >&2; exit 2; }
    bash "$0" guard "$serial" || exit $?
    "$@" ;;
*)
    sed -n '2,20p' "$0" | sed 's/^# \{0,1\}//'; exit 2 ;;
esac
