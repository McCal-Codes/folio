#!/usr/bin/env python3
"""Turn Gradle's JUnit XML results into one small JSON report a release can carry.

The site and the README should not retype how many tests Folio has: the number goes stale the week after, and a number
nobody measured is worth nothing. This reads the results Gradle just wrote (TEST-*.xml) and counts them, so the report
is a measurement and not a sentence.

    python3 tools/test-report.py --version 0.6.8 --commit <sha> \\
        --module app=app/build/test-results/testDebugUnitTest \\
        --module market=market/build/test-results/testDebugUnitTest --out test-report.json

Point each module at ONE variant's results. It counts everything under the directory it is given, so passing the parent
(`app/build/test-results`) would count the debug and fast variants both and report each test twice.

It only counts what ran. A module whose results directory holds no XML is an error, so it cannot report zero tests by
looking in the wrong place. Failures are reported, not hidden: the exit code is 1 when any test failed or errored, so a
release script can refuse to attach a report for a build that did not pass.
"""
import argparse
import json
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


def count(directory: Path) -> dict:
    files = sorted(directory.rglob("TEST-*.xml"))
    if not files:
        raise SystemExit(f"No TEST-*.xml under {directory}. Run the tests first; refusing to report zero by not looking.")
    total = {"suites": 0, "tests": 0, "failures": 0, "errors": 0, "skipped": 0}
    for path in files:
        suite = ET.parse(path).getroot()
        if suite.tag != "testsuite":
            continue
        total["suites"] += 1
        for key in ("tests", "failures", "errors", "skipped"):
            total[key] += int(suite.get(key, "0"))
    total["passed"] = total["tests"] - total["failures"] - total["errors"] - total["skipped"]
    return total


def build(version: str, commit: str, modules: dict[str, Path]) -> dict:
    per_module = {name: count(path) for name, path in modules.items()}
    totals = {key: sum(m[key] for m in per_module.values()) for key in ("suites", "tests", "passed", "failures", "errors", "skipped")}
    return {"format": 1, "version": version, "commit": commit, "modules": per_module, "totals": totals}


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--version", required=True)
    parser.add_argument("--commit", required=True)
    parser.add_argument("--module", action="append", required=True, metavar="NAME=DIR")
    parser.add_argument("--out", required=True)
    args = parser.parse_args(argv)
    modules = {}
    for spec in args.module:
        name, _, directory = spec.partition("=")
        if not name or not directory:
            parser.error(f"--module wants NAME=DIR, got {spec!r}")
        modules[name] = Path(directory)
    report = build(args.version, args.commit, modules)
    Path(args.out).write_text(json.dumps(report, indent=2, sort_keys=True) + "\n")
    totals = report["totals"]
    print(f"{totals['tests']} tests in {totals['suites']} suites: {totals['passed']} passed, {totals['failures']} failed, "
          f"{totals['errors']} errors, {totals['skipped']} skipped.")
    return 1 if totals["failures"] or totals["errors"] else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
