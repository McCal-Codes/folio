#!/usr/bin/env python3
"""Fails when an R8 build holds a Folio method that needs more than 256 Dalvik registers.

Instructions in a dex file can only name registers v0 to v255 directly; a method that needs more has to move values
down before using them, and R8 writes those moves. In October 2026 it twice wrote one wrong in Home's content lambda
(`LauncherScreen`, 98 captured values): `register v1 has type MainActivity but expected Integer`, a VerifyError when
the optimized build launches, while debug builds and every unit test pass. Whether a build hits it depends on how
R8 lays out that one method, so any change can bring it back. This check makes it depend on a number instead: no
Folio method may cross 256 registers, unless it is in KNOWN below. It reads the APK and R8's mapping file, so it needs
no device. tools/launch-check-fast.sh runs it before installing, and scripts/release-signed.sh after building.

    python3 tools/check-dex-registers.py                  # the fast build's APK and mapping
    python3 tools/check-dex-registers.py --variant release
    python3 tools/check-dex-registers.py --apk x.apk --mapping mapping.txt

A method that fails: split it. In a composable, wrap a large block in `OwnMethod { }` (LauncherScreen.kt) so it
compiles to a method of its own.
"""
import argparse
import collections
import os
import re
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
LIMIT = 256
# Close enough to the limit that the next change may cross it. Warned, not failed.
HEADROOM = 240
APP_PACKAGE = "com.mccal.folio."
# Over the limit before this check existed, and verified by nobody method by method: they launch, but R8 could
# misplace a move in any of them on a later build. Matched as prefixes of the original name. Split them rather than
# adding more; never add LauncherScreen here.
KNOWN = (
    "com.mccal.folio.LauncherModelKt.decodeLauncherState",
    "com.mccal.folio.LauncherState.<init>",
    "com.mccal.folio.BackupController$applyImport$",
    "com.mccal.folio.CustomizationSheetKt.HomeLayoutSettings$lambda$",
    "com.mccal.folio.CustomizationSheetKt$CustomizationSheet$",
    "com.mccal.folio.CustomizationSheetKt.<clinit>",
)

CLASS_LINE = re.compile(r"^(\S+) -> (\S+):$")
# "    12:30:void com.example.Foo.bar(int):45:63 -> a" or "    void bar() -> b"; the original may name another class
# when R8 inlined or moved the method.
METHOD_LINE = re.compile(r"^\s+(?:\d+:\d+:)?\S+ (\S+)\((.*)\)((?::\d+)*) -> (\S+)$")


def parse_dexdump(text):
    """[(class, method, registers)] for every method with code, class in Java form."""
    found = []
    clazz = name = None
    for line in text.splitlines():
        m = re.match(r"\s*Class descriptor\s*:\s*'L(.+);'", line)
        if m:
            clazz = m.group(1).replace("/", ".")
            continue
        m = re.match(r"\s+name\s*:\s*'(.+)'", line)
        if m:
            name = m.group(1)
            continue
        m = re.match(r"\s+registers\s*:\s*(\d+)", line)
        if m and clazz and name:
            found.append((clazz, name, int(m.group(1))))
    return found


def parse_mapping(lines, wanted):
    """For each wanted (residual class, residual method), the original method names R8 folded into it, counted.

    The method itself has a line per range of its code, an inlined callee only a few, so the most common name is the
    method's own. Lines ending in `:0` are R8's own copies of a moved method's signature and are skipped.
    """
    originals = collections.defaultdict(collections.Counter)
    current = None
    for raw in lines:
        line = raw.rstrip("\n")
        if not line or line.startswith("#"):
            continue
        if not line[0].isspace():
            m = CLASS_LINE.match(line)
            current = (m.group(1), m.group(2)) if m else None
            continue
        m = METHOD_LINE.match(line)
        if not m or current is None or (current[1], m.group(4)) not in wanted:
            continue
        if m.group(3) == ":0":
            continue
        original = m.group(1)
        if "." not in original:
            original = f"{current[0]}.{original}"
        originals[(current[1], m.group(4))][original] += 1
    return originals


def judge(methods, originals, limit=LIMIT, headroom=HEADROOM, known=KNOWN):
    """(failures, warnings, tolerated): lists of (registers, original name, residual name)."""
    failures, warnings, tolerated = [], [], []
    for clazz, name, registers in sorted(methods, key=lambda m: -m[2]):
        if registers <= headroom:
            continue
        counted = originals.get((clazz, name))
        original = counted.most_common(1)[0][0] if counted else f"{clazz}.{name}"
        if not original.startswith(APP_PACKAGE):
            continue
        row = (registers, original, f"{clazz}.{name}")
        if registers <= limit:
            warnings.append(row)
        elif original.startswith(known):
            tolerated.append(row)
        else:
            failures.append(row)
    return failures, warnings, tolerated


def find_dexdump():
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if not sdk:
        props = ROOT / "local.properties"
        if props.exists():
            m = re.search(r"^sdk\.dir=(.*)$", props.read_text(), re.M)
            sdk = m and m.group(1).strip().replace("\\:", ":").replace("\\\\", "\\")
    if not sdk and os.environ.get("LOCALAPPDATA"):
        sdk = os.path.join(os.environ["LOCALAPPDATA"], "Android", "Sdk")
    tools = Path(sdk or "") / "build-tools"
    versions = sorted((p for p in tools.glob("*") if p.is_dir()),
                      key=lambda p: [int(x) if x.isdigit() else 0 for x in re.split(r"[.-]", p.name)])
    for version in reversed(versions):
        for exe in ("dexdump", "dexdump.exe"):
            if (version / exe).exists():
                return str(version / exe)
    return None


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--variant", default="fast", choices=["fast", "release"])
    parser.add_argument("--apk")
    parser.add_argument("--mapping")
    parser.add_argument("--dexdump")
    args = parser.parse_args()
    apk = Path(args.apk or ROOT / f"app/build/outputs/apk/{args.variant}/app-{args.variant}.apk")
    mapping = Path(args.mapping or ROOT / f"app/build/outputs/mapping/{args.variant}/mapping.txt")
    dexdump = args.dexdump or find_dexdump()
    for label, path in (("APK", apk), ("R8 mapping", mapping)):
        if not path.is_file():
            sys.exit(f"{label} not found: {path}. Build it first (:app:assemble{args.variant.capitalize()}).")
    if not dexdump:
        sys.exit("dexdump not found. Set ANDROID_HOME to an Android SDK with build-tools, or pass --dexdump.")

    methods = []
    with tempfile.TemporaryDirectory() as tmp, zipfile.ZipFile(apk) as z:
        for entry in z.namelist():
            if re.fullmatch(r"classes\d*\.dex", entry):
                dex = z.extract(entry, tmp)
                out = subprocess.run([dexdump, dex], capture_output=True, text=True, errors="replace", check=True)
                methods += parse_dexdump(out.stdout)
    if not methods:
        sys.exit(f"No methods read from {apk}; is it an APK?")
    wanted = {(c, n) for c, n, r in methods if r > HEADROOM}
    with open(mapping, encoding="utf-8") as f:
        originals = parse_mapping(f, wanted)
    failures, warnings, tolerated = judge(methods, originals)

    for registers, original, residual in tolerated:
        print(f"known   {registers:4d} registers  {original}  ({residual})")
    for registers, original, residual in warnings:
        print(f"close   {registers:4d} registers  {original}  ({residual}): split it before it crosses {LIMIT}")
    for registers, original, residual in failures:
        print(f"FAIL    {registers:4d} registers  {original}  ({residual})", file=sys.stderr)
    if failures:
        print(f"\n{len(failures)} Folio method(s) need more than {LIMIT} registers, where R8 has written broken moves "
              f"(VerifyError at launch). Split them; see the top of tools/check-dex-registers.py.", file=sys.stderr)
        return 1
    print(f"OK: no new Folio method over {LIMIT} registers in {apk.name} ({len(methods)} methods read).")
    return 0


if __name__ == "__main__":
    sys.exit(main())
