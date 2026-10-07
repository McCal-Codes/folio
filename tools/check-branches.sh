#!/usr/bin/env bash
# Reports how many topic branches are open in this clone and which ones break the branch rules of the releases standard
# (docs/standards/releases.md). Read only: it never changes a branch and never goes online.
#
#   bash tools/check-branches.sh [base-ref]            # default base: origin/main
#   STRICT=1 bash tools/check-branches.sh              # exit 1 when a rule is broken
#   MAX_OPEN=5 MAX_DAYS=2 MAX_STACK=2 STALE_DAYS=14    # the limits, if a day needs different ones
#
#   REL-2   a topic branch merges within MAX_DAYS days of its first commit, or it is split
#   REL-2a  no more than MAX_OPEN open topic branches per clone; at the limit, land or park before starting another
#   REL-3   a branch is not behind main by double figures, and a stack is at most MAX_STACK deep
#
# Several sessions share one clone, each in its own worktree, so the last column says whose branch it is; the limit
# is per session (REL-2a), and each session counts its own checkouts.
#
# Open means: has commits main lacks, and was touched within STALE_DAYS. A branch quiet for longer is "stale" and listed
# apart (delete it or bring it back), so a pile of old branches cannot hide the count that matters.
#
# A squash merge changes patch ids, and a branch that main later edited again no longer merges to nothing, so neither
# `git branch --merged` nor a tree comparison can say whether a branch landed. This reads the squash commit's title
# instead: a branch has landed when every one of its commit subjects is already a subject on main (with or without the
# "(#123)" GitHub appends). It is a good guess, not proof; `gh pr list --state merged` is the proof.
set -uo pipefail
cd "$(git rev-parse --show-toplevel 2>/dev/null)" 2>/dev/null || cd "$(dirname "$0")/.."

base=${1:-origin/main}
max_open=${MAX_OPEN:-5}
max_days=${MAX_DAYS:-2}
max_stack=${MAX_STACK:-2}
stale_days=${STALE_DAYS:-14}
now=$(date +%s)

git rev-parse --verify --quiet "$base" >/dev/null || { echo "Base ref '$base' not found. Fetch it first." >&2; exit 2; }

wt_of() { git worktree list --porcelain | awk -v b="refs/heads/$1" '/^worktree /{p=$2} $1=="branch" && $2==b {print p}'; }

tmp=$(mktemp -d); trap 'rm -rf "$tmp"' EXIT
git log --format=%s "$base" | sed -E 's/ \(#[0-9]+\)$//' | sort -u > "$tmp/base-subjects"

open=(); landed=(); stale=()
while IFS= read -r b; do
    case "$b" in main|master|merged/*|old/*|release-*|hotfix/*) continue ;; esac
    ahead=$(git rev-list --count "$base..$b")
    if [[ "$ahead" -eq 0 ]]; then landed+=("$b|nothing ahead of main"); continue; fi
    git log --format=%s "$base..$b" | sed -E 's/ \(#[0-9]+\)$//' | sort -u > "$tmp/mine"
    if [[ -z "$(comm -23 "$tmp/mine" "$tmp/base-subjects")" ]]; then landed+=("$b|every commit title is already on main"); continue; fi
    tip=$(git log -1 --format=%ct "$b")
    if (( (now - tip) / 86400 > stale_days )); then stale+=("$b|$(( (now - tip) / 86400 ))d quiet, $ahead ahead, $(git rev-list --count "$b..$base") behind"); continue; fi
    open+=("$b")
done < <(git for-each-ref --format='%(refname:short)' refs/heads)

failed=0
printf '\n%-30s %6s %7s %5s %6s  %-26s %s\n' "OPEN BRANCH" AHEAD BEHIND DAYS STACK CHECKS "CHECKOUT (who is on it)"
for b in ${open[@]+"${open[@]}"}; do
    ahead=$(git rev-list --count "$base..$b")
    behind=$(git rev-list --count "$b..$base")
    first=$(git log --reverse --format=%at "$base..$b" | head -1)   # author date, which a rebase keeps
    days=$(( (now - ${first:-$now}) / 86400 ))
    depth=1   # how many open branches this one is built on, counting itself
    for o in "${open[@]}"; do
        [[ "$o" == "$b" ]] && continue
        if git merge-base --is-ancestor "$o" "$b" 2>/dev/null && [[ "$(git rev-parse "$o")" != "$(git rev-parse "$b")" ]]; then depth=$((depth + 1)); fi
    done
    notes=""
    [[ "$days" -gt "$max_days" ]] && notes+="REL-2 ${days}d old; "
    [[ "$behind" -ge 10 ]] && notes+="REL-3 behind by $behind; "
    [[ "$depth" -gt "$max_stack" ]] && notes+="REL-3 stack $depth deep; "
    [[ -n "$notes" ]] && failed=1
    printf '%-30s %6s %7s %5s %6s  %-26s %s\n' "$b" "$ahead" "$behind" "$days" "$depth" "${notes:-ok}" "$(basename "$(wt_of "$b")" 2>/dev/null)"
done

printf '\n%s open topic branch(es), limit %s' "${#open[@]}" "$max_open"
if [[ "${#open[@]}" -gt "$max_open" ]]; then printf '   \033[31mREL-2a over the limit: land or park some before starting another\033[0m\n'; failed=1; else printf '   ok\n'; fi

if [[ "${#landed[@]}" -gt 0 ]]; then
    printf '\nLanded (%s), safe to delete once you have looked:\n' "${#landed[@]}"
    for l in "${landed[@]}"; do printf '  %-32s %-38s %s\n' "${l%%|*}" "${l#*|}" "$(wt_of "${l%%|*}")"; done
fi
if [[ "${#stale[@]}" -gt 0 ]]; then
    printf '\nStale, quiet for more than %s days (%s): finish and land, or delete:\n' "$stale_days" "${#stale[@]}"
    for s in "${stale[@]}"; do printf '  %-32s %s\n' "${s%%|*}" "${s#*|}"; done
fi

printf '\nAlso keep docs/in-flight.md honest (REL-6): a row for every open branch that touches a shared file, deleted when it lands.\n'
[[ "${STRICT:-0}" == "1" && "$failed" -eq 1 ]] && exit 1
exit 0
