#!/usr/bin/env bash
# Checks a change against the parts of the releases standard a machine can see (docs/standards/releases.md).
#
#   bash tools/check-release-rules.sh [base-ref]        # default base: origin/main
#
# Four rules. REL-7 and REL-13 were both broken in the week the standard was written; REL-5 on 28 Sep 2026; REL-4b
# became "the same app" on 2 Oct 2026, so a fix to a tool or a note no longer waits a whole release:
#
#   REL-4b  A stable ships the same app as the last beta of its version: what goes into the APK matches that beta's
#           tag, apart from the version, the roadmap and the notes.
#
#   REL-5   No AI attribution: no co-author, credit line or robot footer in a commit or the description, and no
#           branch named after a tool. Not waivable.
#
#   REL-7   A change to what ships adds its own line to the Unreleased section of CHANGELOG.md, in the same pull
#           request. Notes written later, or on a branch of their own, are how one version ends up meaning two
#           different things.
#   REL-13  The version bump is its own commit and touches only the version, the notes, the roadmap and the release
#           doc. A pull request that moves folioVersion and changes code at the same time hides the release in a
#           feature diff.
#
# REL-7, REL-13 and REL-4b can be waived by labelling the pull request, so the escape hatch is visible in the pull
# request rather than hidden in someone's shell:
#
#   no-changelog        this change is invisible to users (REL-9), so it gets no line
#   release-exception   this really is a release and a change together, or a stable that differs from its beta,
#                       and that was deliberate
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

# REL-5: no AI attribution in commits, the branch name or the pull request. Narrow on purpose: Folio has an "Ask
# Claude" search button, so the name alone is fine; a credit line, a co-author or an agent-named branch is not.
# No label waives this one.
ai_credit='co-authored-by:.*(anthropic|openai|claude|copilot|codex|chatgpt|gemini|cursor)|generated (with|by) \[?(claude|chatgpt|copilot|codex|cursor|gemini)|claude\.com/claude-code|claude\.ai/code|🤖'
credited=$(git log --format='%h %B' "$merge_base..HEAD" | grep -iE "$ai_credit" || true)
branch=${PR_BRANCH:-$(git rev-parse --abbrev-ref HEAD)}
if [[ -n "$credited" ]]; then
    fail "REL-5  a commit credits an AI tool:
$(echo "$credited" | head -5 | sed 's/^/          /')
        Reword the commit without it (git commit --amend, or rebase and reword)."
elif grep -qiE '^(claude|codex|copilot|cursor)[/-]|(^|[/-])agent-' <<< "$branch"; then
    fail "REL-5  the branch '$branch' is named after a tool or an agent. Name it after the change."
elif grep -qiE "$ai_credit" <<< "${PR_BODY:-}"; then
    fail "REL-5  the pull request description credits an AI tool. Edit it out."
else
    pass "REL-5  no AI credit in the commits, the branch name or the description"
fi

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

# REL-7: what ships gets a line. The roadmap is a list of plans rather than a change to what Folio does, and it is
# edited whenever a plan moves, so it does not count as shipping something; REL-14 is what governs it.
ships=$(echo "$changed" | grep -E '^(app|market)/src/main/' | grep -v '^app/src/main/assets/roadmap\.json$' || true)
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

# REL-4b: a stable ships the same app as the last beta of its version. Only what is built into the APK is compared:
# the code and resources under app/ and market/, the Market's built-in source and the Gradle setup. The version, the roadmap and the notes move in
# the bump itself (REL-13); docs, tools and tests may change after the beta, since nobody installs them.
if [[ "$version_changed" != yes || "$new_version" == *-* ]]; then
    skip "REL-4b not a stable release"
else
    tags=$(git tag -l --sort=-v:refname "v${new_version}-beta.*")
    last_beta=${tags%%$'\n'*}
    if [[ -z "$last_beta" ]]; then
        skip "REL-4b no beta of $new_version is tagged to compare with"
    elif has_label release-exception; then
        skip "REL-4b waived by the release-exception label"
    else
        # docs/sdk/source is in the list because the APK bundles it: it is the Market's built-in source.
        app_paths=(app/src/main app/src/release market/src/main docs/sdk/source app/build.gradle.kts market/build.gradle.kts
            build.gradle.kts settings.gradle.kts gradle.properties gradle app/proguard-rules.pro)
        moved=$(git diff --name-only "$last_beta" HEAD -- "${app_paths[@]}" \
            | grep -vE '^(app/build\.gradle\.kts|app/src/main/assets/roadmap\.json)$' || true)
        # The app's build file may differ from the beta's only in the version line.
        build_lines=$(git diff -U0 "$last_beta" HEAD -- app/build.gradle.kts | grep -E '^[+-][^+-]' || true)
        if [[ -n "$build_lines" ]] && grep -qvE '^[+-]val folioVersion = ' <<< "$build_lines"; then
            moved=$(printf '%s\n%s' "$moved" "app/build.gradle.kts, beyond the version" | sed '/^$/d')
        fi
        if [[ -z "$moved" ]]; then
            pass "REL-4b $new_version ships the same app as $last_beta"
        else
            fail "REL-4b $new_version would not ship the same app as $last_beta. Changed since it:
$(echo "$moved" | head -5 | sed 's/^/          /')
        Put it out as another beta first (REL-16), so what goes out stable has been out as a beta."
        fi
    fi
fi

exit $failed
