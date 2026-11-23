#!/usr/bin/env bash
# Lists this clone's worktrees with what happened to their branch's pull request, and removes the ones that are done.
# `git branch --merged` cannot say, because every pull request here is squash-merged and the branch commits never reach
# main. This asks GitHub instead.
#
#   bash tools/worktree-cleanup.sh                  list only (the default; nothing is changed)
#   bash tools/worktree-cleanup.sh --remove         remove the safe ones
#   bash tools/worktree-cleanup.sh --remove --idle-hours 24     only if untouched for 24 hours (default 6)
#
# A worktree is safe to remove when all of these hold: its pull request is MERGED or CLOSED, the worktree has no
# uncommitted changes, its HEAD is the commit the pull request ended on (so nothing was added afterwards), it is not
# the one you are standing in, and nothing in it changed in the last --idle-hours (a session may still be using it).
# It removes the worktree directory only, never the branch, and never with --force; a locked or in-use folder is
# reported and left. Needs gh, signed in.
set -uo pipefail
remove=0; idle=6
while [ $# -gt 0 ]; do
    case $1 in --remove) remove=1 ;; --idle-hours) idle=${2:?hours}; shift ;; *) sed -n '2,16p' "$0" | sed 's/^# \{0,1\}//'; exit 2 ;; esac
    shift
done
command -v gh >/dev/null || { echo "gh is needed to read pull request states" >&2; exit 2; }
here=$(git rev-parse --show-toplevel)
repo=$(git -C "$here" remote get-url origin | sed -E 's#.*github.com[:/]##; s#\.git$##')
now=$(date +%s)
main=$(git -C "$here" worktree list --porcelain | sed -n '1s/^worktree //p')
# One call for every pull request, not one per worktree: "branch state head-commit", newest first.
prs=$(gh pr list --repo "$repo" --state all --limit 500 --json headRefName,state,headRefOid --jq '.[]|"\(.headRefName) \(.state) \(.headRefOid)"' 2>/dev/null)
[ -n "$prs" ] || echo "(could not read pull requests from GitHub; is gh signed in?)" >&2
# Idle time is the later of the last checkout/commit and the last index write in that worktree. A clean tree that
# nothing has touched for a while is one no session is working in; building does not count as touching it.
mtime() { [ -e "$1" ] && stat -c %Y "$1" 2>/dev/null || echo 0; }
printf '%-8s %-6s %-5s %s
' "PR" "DIRTY" "IDLEH" "WORKTREE (branch)"

git -C "$here" worktree list --porcelain | awk '/^worktree /{p=substr($0,10)} /^HEAD /{h=$2} /^branch /{b=$2; sub("refs/heads/","",b)} /^detached/{b="(detached)"} /^$/{if(p)print p"|"h"|"b; p="";h="";b=""} END{if(p)print p"|"h"|"b}' | while IFS='|' read -r path head branch; do
    [ -d "$path" ] || { printf '%-8s %-6s %-5s %s (folder missing; run git worktree prune)
' "-" "-" "-" "$path"; continue; }
    [ "$path" = "$here" ] && { printf '%-8s %-6s %-5s %s (this one)
' "-" "-" "-" "$path"; continue; }
    [ "$path" = "$main" ] && { printf '%-8s %-6s %-5s %s (the main checkout; never removed)
' "-" "-" "-" "$path"; continue; }
    dirty=$(git -C "$path" status --porcelain 2>/dev/null | wc -l | tr -d ' ')
    a=$(mtime "$(git -C "$path" rev-parse --path-format=absolute --git-path logs/HEAD)"); b=$(mtime "$(git -C "$path" rev-parse --path-format=absolute --git-path index)")
    newest=$(( a > b ? a : b )); [ "$newest" -gt 0 ] || newest=$now
    idleh=$(( (now - newest) / 3600 ))
    state="none"; prhead=""
    if [ "$branch" != "(detached)" ] && [ -n "$branch" ]; then
        # Prefer the pull request that ended on this exact commit; otherwise the newest one for the branch name.
        row=$(printf '%s
' "$prs" | awk -v b="$branch" -v h="$head" '$1==b && $3==h {print; found=1; exit} $1==b && !first {first=$0} END{if(!found && first)print first}')
        [ -n "$row" ] && { state=$(echo "$row" | cut -d' ' -f2); prhead=$(echo "$row" | cut -d' ' -f3); }
    fi
    safe=0
    case "$state" in MERGED|CLOSED) [ "$dirty" = 0 ] && [ "$head" = "$prhead" ] && [ "$idleh" -ge "$idle" ] && safe=1 ;; esac
    printf '%-8s %-6s %-5s %s (%s)%s
' "$state" "$dirty" "$idleh" "$path" "$branch" "$([ "$safe" = 1 ] && echo '  <- safe to remove')"
    if [ "$remove" = 1 ] && [ "$safe" = 1 ]; then
        if git -C "$here" worktree remove "$path" 2>/dev/null; then echo "         removed $path"; else echo "         could not remove $path (in use or locked); left it"; fi
    fi
done
[ "$remove" = 1 ] || echo "(list only. Add --remove to remove the ones marked safe.)"
