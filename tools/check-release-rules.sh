#!/usr/bin/env bash
# Checks a change against the parts of the releases standard a machine can see (docs/standards/releases.md).
#
#   bash tools/check-release-rules.sh [base-ref]        # default base: origin/main
#
# Two rules, because both were broken in the week the standard was written:
#
#   REL-7   A change to what ships adds its own line to the Unreleased section of CHANGELOG.md, in the same pull
#           request. Notes written later, or on a branch of their own, are how one version ends up meaning two
#           different things.
#   REL-13  The version bump is its own commit and touches only the version, the notes, the roadmap and the release
#           doc. A pull request that moves folioVersion and changes code at the same time hides the release in a
#           feature diff.
#
# Both can be waived by labelling the pull request, so the escape hatch is visible in the pull request rather than
# hidden in someone's shell:
#
#   no-changelog        this change is invisible to users (REL-9), so it gets no line
#   release-exception   this really is a release and a change together, and that was deliberate
#
# Locally, set them as environment variables: PR_LABELS="no-changelog" bash tools/check-release-rules.sh
set -uo pipefail
# The repository you are standing in, so this reads the right tree when it is run from one worktree against another.
cd "$(git rev-parse --show-toplevel 2>/dev/null)" 2>/dev/null || cd "$(dirname "$0")/.."

base=${1:-origin/main}
labels=${PR_LABELS:-}
failed=0

fail() { printf '\n\033[31mFAILED\033[0m  %s\n' "$1"; failed=1; }
pass() { printf '\033[32mok\033[0m      %s\n' "$1"; }
skip() { printf '\033[33mskipped\033[0m %s\n' "$1"; }

if ! git rev-parse --verify --quiet "$base" >/dev/null; then
    echo "Base ref '$base' not found. Fetch it first, or pass one that exists." >&2
    exit 2
fi

merge_base=$(git merge-base "$base" HEAD)
changed=$(git diff --name-only "$merge_base" HEAD)
if [[ -z "$changed" ]]; then
    echo "Nothing changed against $base."
    exit 0
fi

has_label() { [[ ",$labels," == *",$1,"* ]]; }

# Bullet lines inside the "## [x.y.z] - Unreleased" section of a CHANGELOG, from a given revision.
# `grep -q` closes the pipe early, which makes `git show` die of SIGPIPE, which `pipefail` reads as failure. So read
# each file into a variable first and match against that.
changelog_at() { git show "$1:CHANGELOG.md" 2>/dev/null || true; }

unreleased_bullets() {
    awk '
        /^## \[/ { inside = ($0 ~ /Unreleased/) ; next }
        inside && /^- / { count++ }
        END { print count + 0 }
    ' <<< "$(changelog_at "$1")"
}
has_unreleased() {
    grep -qE '^## \[[^]]+\] - Unreleased' <<< "$(changelog_at "$1")"
}

version_at() {
    git show "$1:app/build.gradle.kts" 2>/dev/null | sed -n 's/^val folioVersion = "\(.*\)"$/\1/p'
}

old_version=$(version_at "$merge_base")
new_version=$(version_at HEAD)
version_changed=no
[[ -n "$new_version" && "$old_version" != "$new_version" ]] && version_changed=yes

# REL-7: what ships gets a line.
ships=$(echo "$changed" | grep -E '^(app|market)/src/main/' || true)
if [[ -z "$ships" ]]; then
    skip "REL-7  nothing under app/src/main or market/src/main changed"
elif has_label no-changelog; then
    skip "REL-7  waived by the no-changelog label"
elif [[ "$version_changed" == yes ]]; then
    skip "REL-7  this is a version bump; REL-13 below covers it"
elif ! has_unreleased HEAD; then
    fail "REL-7  CHANGELOG.md has no '## [x.y.z] - Unreleased' section to add to.
        Open one for the next version and put this change's line in it."
else
    before=$(unreleased_bullets "$merge_base")
    after=$(unreleased_bullets HEAD)
    if (( after > before )); then
        pass "REL-7  $((after - before)) line(s) added to the Unreleased notes"
    else
        fail "REL-7  this changes what ships but adds no line to the Unreleased notes.
        Changed: $(echo "$ships" | head -3 | tr '\n' ' ')$([[ $(echo "$ships" | wc -l) -gt 3 ]] && echo '…')
        Write what a person will see, or label the pull request no-changelog if they will see nothing."
    fi
fi

# REL-13: the version bump is its own commit.
if [[ "$version_changed" != yes ]]; then
    skip "REL-13 folioVersion is unchanged ($old_version)"
elif has_label release-exception; then
    skip "REL-13 waived by the release-exception label"
else
    stray=$(echo "$changed" | grep -vE '^(app/build\.gradle\.kts|CHANGELOG\.md|app/src/main/assets/roadmap\.json|docs/)' || true)
    if [[ -z "$stray" ]]; then
        pass "REL-13 version bump $old_version to $new_version, and nothing else"
    else
        fail "REL-13 folioVersion moves to $new_version in a pull request that also changes:
$(echo "$stray" | head -5 | sed 's/^/          /')
        Land the change first, then bump the version on its own, so the release is one reviewable diff."
    fi
fi

exit $failed
