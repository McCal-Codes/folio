#!/usr/bin/env python3
"""Build a signed work-in-progress tweaks source from a branch of the folio-tweaks checkout, for the Folio Dev debug source.

    tools/dev-tweaks.py --branch duet --to-folder DIR       write DIR/tweaks/ (the signed source), no network
    tools/dev-tweaks.py --branch duet --to-folder DIR --dry-run

DIR is the same folder tools/dev-publish.py writes builds.json into, so `node tools/dev-source-stub.mjs DIR` serves both and
the real repository's `source` branch has the same shape (tweaks/ beside builds.json).

What it changes, in a temporary copy only (the checkout, its main branch and its publish workflow are never touched):
  - source.json becomes "Folio Dev Tweaks", "Work in progress. Not for release builds.", maxAgeDays 3, nothing featured;
  - every package gets a distinct id `<id>.wip` (decision D2: no first-party status, so a WIP tweak can never stand in for
    a built-in one), the name `WIP: <name>`, the version `<version>-wip.<n>` (n = commits on the branch, so a real release of the
    same version sorts after it), and its depiction opens with "Work in progress from branch X at <sha>".

It signs with the dev publisher key through the checkout's own tools/build.py and refuses Folio's public Community Tweaks key
by key id. The key is only handed to openssl. Nothing here talks to a network.
"""

from __future__ import annotations

import argparse
import importlib.util
import io
import json
import os
import pathlib
import re
import shutil
import subprocess
import sys
import tarfile
import tempfile

HERE = pathlib.Path(__file__).resolve().parent
_spec = importlib.util.spec_from_file_location("dev_publish", HERE / "dev-publish.py")
dp = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(dp)

DEFAULT_CHECKOUT = "~/dev/folio-tweaks"
SOURCE_NAME = "Folio Dev Tweaks"
SOURCE_DESCRIPTION = "Work in progress. Not for release builds."
MAX_AGE_DAYS = 3


REF = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._/-]*$")


def check_ref(name: str) -> str:
    """A branch or ref name that cannot be mistaken for an option or a path."""
    if not REF.match(name) or ".." in name:
        sys.exit(f"{name!r} is not a plain branch name.")
    return name


def git(checkout: pathlib.Path, *args: str) -> bytes:
    return subprocess.run(["git", "-C", str(checkout), *args], capture_output=True, check=True).stdout


def export_ref(checkout: pathlib.Path, ref: str, into: pathlib.Path) -> None:
    """The ref's files, without touching the checkout's working tree, index or other branches.

    Links and special files are refused outright, and extraction uses tarfile's `data` filter where this Python has it.
    """
    data = git(checkout, "archive", "--format=tar", check_ref(ref))
    root = into.resolve()
    with tarfile.open(fileobj=io.BytesIO(data)) as tar:
        for member in tar.getmembers():
            if not (member.isfile() or member.isdir()):
                sys.exit(f"{ref}: {member.name} is a link or special file, which a tweaks branch may not contain")
            target = (into / member.name).resolve()
            if root not in target.parents and target != root:
                sys.exit(f"{member.name} would land outside the work folder")
        if hasattr(tarfile, "data_filter"):
            tar.extractall(into, filter="data")
        else:
            tar.extractall(into)


def wip_id(package_id: str) -> str:
    return package_id if package_id.endswith(".wip") else package_id + ".wip"


def wip_version(version: str, count: int) -> str:
    return f"{version.split('-wip.')[0]}-wip.{count}"


def rewrite_source(work: pathlib.Path, branch: str, sha: str, count: int) -> list[str]:
    """Turns the exported branch into the dev source. Returns the new package ids."""
    source_path = work / "source.json"
    source = json.loads(source_path.read_text(encoding="utf-8"))
    source.update({"name": SOURCE_NAME, "description": SOURCE_DESCRIPTION, "maxAgeDays": MAX_AGE_DAYS})
    source.pop("featured", None)
    source_path.write_text(json.dumps(source, indent=2) + "\n", encoding="utf-8", newline="\n")
    ids = []
    for folder in sorted((work / "packages").iterdir()):
        manifest_path = folder / "manifest.json"
        if not manifest_path.is_file():
            continue
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        manifest["id"] = wip_id(manifest["id"])
        manifest["name"] = "WIP: " + manifest["name"].removeprefix("WIP: ")
        manifest["version"] = wip_version(manifest["version"], count)
        note = f"Work in progress from branch {branch} at {sha}."
        manifest["description"] = f"{note} {manifest.get('description', '')}".strip()
        manifest_path.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8", newline="\n")
        depiction_path = folder / manifest.get("depiction", "depiction.json")
        if depiction_path.is_file():
            depiction = json.loads(depiction_path.read_text(encoding="utf-8"))
            depiction["blocks"] = [{"type": "markdown", "text": note}] + list(depiction.get("blocks", []))
            depiction_path.write_text(json.dumps(depiction, indent=2) + "\n", encoding="utf-8", newline="\n")
        ids.append(manifest["id"])
    return ids


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Build a signed WIP tweaks source from a folio-tweaks branch.")
    parser.add_argument("--branch", required=True, help="the folio-tweaks branch to build")
    parser.add_argument("--trusted-ref", default="main", help="the ref whose tools/ build and validate the source (default main); a branch's own tools/ is never run")
    parser.add_argument("--checkout", default=os.environ.get("FOLIO_TWEAKS", DEFAULT_CHECKOUT), help="the folio-tweaks checkout")
    parser.add_argument("--to-folder", required=True, help="write DIR/tweaks/ here (no network)")
    parser.add_argument("--key", default=os.environ.get("FOLIO_DEV_PUBLISHER_KEY", dp.DEFAULT_KEY), help="the dev publisher key (PEM)")
    parser.add_argument("--dry-run", action="store_true", help="say what would be built and write nothing")
    args = parser.parse_args(argv)
    key = pathlib.Path(args.key).expanduser()
    dp.check_key(key)
    checkout = pathlib.Path(args.checkout).expanduser()
    if not (checkout / "tools/build.py").is_file():
        sys.exit(f"{checkout} is not a folio-tweaks checkout (no tools/build.py).")
    branch = check_ref(args.branch)
    trusted = check_ref(args.trusted_ref)
    try:
        sha = git(checkout, "rev-parse", "--verify", "--short=8", branch + "^{commit}").decode().strip()
        count = int(git(checkout, "rev-list", "--count", branch, "--").decode().strip())
        git(checkout, "rev-parse", "--verify", trusted + "^{commit}")
    except subprocess.CalledProcessError:
        sys.exit(f"{branch} or {trusted} is not a ref in {checkout}.")
    out = pathlib.Path(args.to_folder) / "tweaks"
    with tempfile.TemporaryDirectory(prefix="folio-dev-tweaks-") as tmp:
        work = pathlib.Path(tmp) / "src"
        theirs = pathlib.Path(tmp) / "branch"
        work.mkdir()
        theirs.mkdir()
        # The build and the validator come from the trusted ref; the branch supplies package folders and nothing else, so
        # code on a branch can never run with the publisher key in reach.
        export_ref(checkout, trusted, work)
        export_ref(checkout, branch, theirs)
        shutil.rmtree(work / "packages", ignore_errors=True)
        shutil.copytree(theirs / "packages", work / "packages")
        if git(checkout, "diff", "--name-only", trusted, branch, "--", "tools").strip():
            print(f"Note: {branch} changes tools/; those changes are ignored (tools/ comes from {trusted}).")
        ids = rewrite_source(work, args.branch, sha, count)
        if not ids:
            sys.exit(f"Branch {args.branch} has no packages.")
        print(f"{args.branch} at {sha}: " + ", ".join(ids))
        if args.dry_run:
            print(f"(dry run) would build and sign {len(ids)} package(s) into {out}")
            return 0
        # build.py reads its own folder, so it runs from the copy; plain `openssl` must be on its PATH.
        env = dict(os.environ, PATH=str(pathlib.Path(dp.openssl()).parent) + os.pathsep + os.environ.get("PATH", ""))
        env.pop("FOLIO_SOURCE_KEY", None)  # never the public source's key
        build = subprocess.run([sys.executable, "tools/build.py", "--key", str(key)], cwd=work, env=env)  # tools/ is the trusted ref's
        if build.returncode != 0:
            return build.returncode
        check = subprocess.run([sys.executable, "tools/folio-pkg.py", "validate", "_site"], cwd=work, env=env)
        if check.returncode != 0:
            print("folio-pkg validate failed; nothing was written.", file=sys.stderr)
            return check.returncode
        if out.exists():
            shutil.rmtree(out)
        shutil.copytree(work / "_site", out)
    print(f"Wrote {out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
