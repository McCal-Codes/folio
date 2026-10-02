#!/usr/bin/env python3
"""PERMISSIONS.md has to list exactly the permissions the release manifest declares.

PERMISSIONS.md promises that "nothing is hidden behind a marketing word" and tells readers they can check it against
app/src/main/AndroidManifest.xml. This is that check, run by CI, in both directions: a permission added to the manifest
without a row in the document fails, and so does a row for a permission the manifest no longer declares. Only
`<uses-permission>` entries in the main manifest count; debug and test manifests are not what ships.

    python3 tools/check-permissions.py
"""
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MANIFEST = ROOT / "app/src/main/AndroidManifest.xml"
DOC = ROOT / "PERMISSIONS.md"
ANDROID = "{http://schemas.android.com/apk/res/android}name"


def declared() -> set[str]:
    tree = ET.parse(MANIFEST)
    return {
        el.get(ANDROID).removeprefix("android.permission.")
        for el in tree.getroot().iter("uses-permission")
        if el.get(ANDROID)
    }


def documented() -> set[str]:
    """The names in the first column of the permissions table: rows that start with a backticked name."""
    names: set[str] = set()
    for line in DOC.read_text().splitlines():
        if not line.startswith("| `"):
            continue
        first_column = line.split("|")[1]
        names.update(re.findall(r"`([A-Z][A-Z_]+)`", first_column))
    return names


def main() -> int:
    have, listed = declared(), documented()
    if not have:
        print(f"No uses-permission found in {MANIFEST.relative_to(ROOT)}; has the manifest moved?")
        return 1
    problems = []
    for name in sorted(have - listed):
        problems.append(f"{name} is declared in the manifest but has no row in PERMISSIONS.md")
    for name in sorted(listed - have):
        problems.append(f"{name} has a row in PERMISSIONS.md but the manifest does not declare it")
    if problems:
        print("PERMISSIONS.md and the manifest disagree:")
        for problem in problems:
            print(f"  {problem}")
        return 1
    print(f"PERMISSIONS.md lists exactly the {len(have)} permissions the manifest declares.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
