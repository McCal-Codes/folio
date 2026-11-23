#!/usr/bin/env python3
"""Every string a change adds must exist in each language Folio ships, or lint fails the pull request.

    python tools/check-translations.py [base-ref]      # default origin/main; only strings this change adds or edits
    python tools/check-translations.py --all           # every string in the tree (main may have older gaps)

Android lint reports MissingTranslation only after a full Gradle run, which is how a missing Korean and Chinese string
reached pull request 662. This reads the diff instead and takes a second. It checks names, not wording: a stand-in
translation passes, so read the wording yourself or get a native speaker (A11Y-16). Strings marked
translatable="false" are skipped, and so are strings that sit in a values folder with no `values-xx` twin.
"""
import re
import subprocess
import sys
from pathlib import Path

MODULES = ("app/src/main/res", "market/src/main/res")
# values-ko, values-pt-rBR, values-b+zh+Hans: a language folder. values-night, values-v31, values-sw600dp are not.
LOCALE_DIR = re.compile(r"^values-(?:[a-z]{2,3}(?:-r[A-Z]{2})?|b\+[A-Za-z]{2,3}(?:\+[A-Za-z0-9]+)*)$")
NAME_TAG = re.compile(r"<(string|plurals|string-array)\b([^>]*)>")


def names_in(xml_text):
    """Names of translatable resources in a chunk of values XML."""
    found = set()
    for tag, attrs in NAME_TAG.findall(xml_text):
        m = re.search(r'\bname="([^"]+)"', attrs)
        if m and 'translatable="false"' not in attrs:
            found.add(m.group(1))
    return found


def missing_per_locale(added, have_by_locale):
    """{locale: sorted names in `added` that locale lacks}, only for locales that lack something."""
    out = {}
    for locale, have in have_by_locale.items():
        gap = sorted(added - have)
        if gap:
            out[locale] = gap
    return out


def run(*args):
    return subprocess.run(args, capture_output=True, text=True, check=False).stdout


def main(argv):
    top = Path(run("git", "rev-parse", "--show-toplevel").strip() or ".")
    everything = "--all" in argv
    rest = [a for a in argv if not a.startswith("--")]
    base = rest[0] if rest else "origin/main"
    exit_code = 0
    for module in MODULES:
        res = top / module
        base_dir = res / "values"
        if not base_dir.is_dir():
            continue
        locales = sorted(d for d in res.iterdir() if d.is_dir() and LOCALE_DIR.match(d.name))
        if not locales:
            continue
        if everything:
            added = set()
            for f in base_dir.glob("*.xml"):
                added |= names_in(f.read_text(encoding="utf-8"))
        else:
            diff = run("git", "-C", str(top), "diff", "--no-color", "-U0", f"{base}...HEAD", "--", f"{module}/values/")
            diff += run("git", "-C", str(top), "diff", "--no-color", "-U0", "--", f"{module}/values/")  # uncommitted edits
            added = set()
            for line in diff.splitlines():
                if line.startswith("+") and not line.startswith("+++"):
                    added |= names_in(line[1:])
            # A one-line edit to a string's text shows the name too; a multi-line value shows only the tag line.
        have = {}
        for d in locales:
            names = set()
            for f in d.glob("*.xml"):
                names |= names_in(f.read_text(encoding="utf-8"))
            have[d.name] = names
        for locale, gap in missing_per_locale(added, have).items():
            exit_code = 1
            print(f"{module}/{locale} is missing {len(gap)} string(s) this change adds:")
            for name in gap:
                print(f"  {name}")
    if exit_code:
        print("\nAdd them to each language's strings.xml (lint calls this MissingTranslation), or mark the string translatable=\"false\".")
    else:
        print("translations: every added string exists in each language" if not everything else "translations: complete")
    return exit_code


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
