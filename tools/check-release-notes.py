#!/usr/bin/env python3
"""Checks a release's notes before the version bump, while they are still cheap to fix.

    python tools/check-release-notes.py 0.7.0-beta.5 [previous-tag] [--ref HEAD]

Fails (exit 1) when docs/releases/<version>.md is not in the shape scripts/publish-beta.ps1 and the standard expect:
the sections are there, it does not say DRAFT, it carries no hash (the release page adds those from the build), and
the catalog row exists in docs/releases/README.md.

Warns, without failing, about the thing that went wrong in beta 4's first draft: claims with no changelog behind
them. It lists every line added to CHANGELOG.md since the previous tag whose title the notes never mention, so a
change that shipped without a word in the notes shows up, and prints the notes' bold lead phrases that match no
changelog line so an invented one can be questioned. Matching is by shared words, so read both lists; neither is proof.

The previous tag defaults to the newest v* tag reachable from --ref. Run it from the repository (or a worktree) that
holds the notes.
"""
import re
import subprocess
import sys
from pathlib import Path

REQUIRED = ["## What's new", "## Please try first", "## About the betas", "## Verify it"]
STOP = {"with", "from", "that", "this", "your", "have", "into", "when", "than", "them", "then", "more", "only", "also",
        "each", "does", "over", "like", "what", "shows", "show", "now", "the", "and", "for", "are", "its", "one"}


def git(*args):
    return subprocess.run(["git", *args], capture_output=True, text=True, check=True).stdout


def words(text):
    return {w for w in re.findall(r"[a-z0-9]{4,}", text.lower()) if w not in STOP}


def main(argv):
    ref = "HEAD"
    if "--ref" in argv:
        i = argv.index("--ref")
        ref = argv[i + 1]
        del argv[i:i + 2]
    if not argv:
        print(__doc__)
        return 2
    version = argv[0]
    root = Path(git("rev-parse", "--show-toplevel").strip())
    previous = argv[1] if len(argv) > 1 else git("describe", "--tags", "--abbrev=0", "--match", "v*", ref).strip()

    failures, warnings = [], []
    notes_path = root / "docs" / "releases" / f"{version}.md"
    if not notes_path.exists():
        print(f"FAIL: {notes_path} does not exist")
        return 1
    notes = notes_path.read_text(encoding="utf-8")

    for heading in REQUIRED:
        if heading not in notes:
            failures.append(f"missing section {heading!r}")
    if "## Fixed" not in notes:
        warnings.append("no '## Fixed' section (fine if nothing was fixed)")
    if "DRAFT" in notes:
        failures.append("still says DRAFT")
    if re.search(r"\b[0-9a-f]{64}\b", notes):
        failures.append("contains a 64-character hash; the release page adds the APK and certificate hashes from the build")
    if "SHA256SUMS" not in notes:
        warnings.append("'Verify it' does not point at SHA256SUMS.txt")
    if re.search(r"^\*Released \d", notes, re.M) and "planned" not in notes.lower():
        warnings.append("states a release date as fact; before the build, say it as planned (a slipped date needs a docs fix)")
    catalog = (root / "docs" / "releases" / "README.md").read_text(encoding="utf-8")
    if f"{version}.md" not in catalog:
        failures.append("no row for this release in docs/releases/README.md (REL-13 keeps it out of the bump pull request)")

    added = []
    for line in git("diff", previous, ref, "--", "CHANGELOG.md").splitlines():
        if line.startswith("+") and not line.startswith("+++"):
            m = re.match(r"\+- \*\*(.+?)\*\*", line)
            if m:
                added.append((m.group(1).rstrip(":"), line[1:]))
    notes_words = words(notes)
    unmentioned = []
    for title, line in added:
        wanted = words(title)
        if wanted and len(wanted & notes_words) / len(wanted) < 0.5:
            unmentioned.append(title)

    # A lead word (5+ letters) that appears nowhere in the added changelog lines is the signature of an invented claim:
    # beta 4's first draft led with "Battery in the status rail" and "carrier" while the changelog had neither word.
    leads = re.findall(r"^- \*\*(.+?)\*\*", notes, re.M)
    changelog_words = words(" ".join(line for _, line in added))
    unbacked = []
    for lead in leads:
        absent = sorted(w for w in words(lead) if len(w) >= 5 and w not in changelog_words)
        if absent:
            unbacked.append(f"{lead} (not in the changelog: {', '.join(absent)})")

    print(f"{version} notes against {previous}..{ref}: {len(added)} changelog lines added")
    for text in failures:
        print(f"FAIL: {text}")
    for text in warnings:
        print(f"warn: {text}")
    for title in unmentioned:
        print(f"warn: changelog line not mentioned in the notes: {title}")
    for lead in unbacked:
        print(f"warn: notes lead with no changelog line behind it: {lead}")
    if not failures and not unmentioned and not unbacked:
        print("ok: structure is right and the changelog and notes agree on titles")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
