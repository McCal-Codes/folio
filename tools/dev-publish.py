#!/usr/bin/env python3
"""Publish a Folio Dev build to the debug source: a signed builds.json plus the APK and its checksum.

    tools/dev-publish.py --apk app.apk --to-folder DIR     write exactly what the repo and worker would serve (no network)
    tools/dev-publish.py --to-folder DIR --dry-run          say what would be written and write nothing
    tools/dev-publish.py --upload                           also put it in the private repo (needs `gh`; never done by default)

This extends tools/dev-builds.py (it reuses its build, name and checksum helpers). The folder layout is the one
tools/dev-source-stub.mjs serves and the worker's /dev/ routes read:

    builds.json, builds.json.sig            the list, signed with the dev publisher key (ECDSA P-256, SHA-256, base64 DER)
    releases/<tag>/FolioDev-<name>.apk      the build
    releases/<tag>/SHA256SUMS.txt           its checksum, the shape Software Update already reads

The key is ~/.folio/folio-dev-publisher.pem (override with --key or FOLIO_DEV_PUBLISHER_KEY). It is only handed to
openssl; this script never reads or prints it. It refuses Folio's public Community Tweaks key by key id.

Nothing here talks to a network unless `--upload` is given. Publish from one machine only (decision D4): a build made on
another computer has another debug key, and Android will not replace one with the other.
"""

from __future__ import annotations

import argparse
import base64
import datetime
import hashlib
import importlib.util
import json
import os
import pathlib
import re
import shutil
import subprocess
import sys
import tempfile
import time

HERE = pathlib.Path(__file__).resolve().parent
_spec = importlib.util.spec_from_file_location("dev_builds", HERE / "dev-builds.py")
db = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(db)

REPO_SLUG = "McCal-Codes/folio-dev-builds"
KEEP = 15
FORMAT = 1
MAX_AGE_DAYS_LIMIT = 90
COMMUNITY_KEY_ID = "7028F4F54A35A055"  # the public Community Tweaks key (MarketSession.kt); never signs anything here
DEFAULT_KEY = "~/.folio/folio-dev-publisher.pem"


def version_code(version: str) -> int:
    """app/build.gradle.kts: (MAJOR * 10000 + MINOR * 100 + PATCH) * 10 + HOTFIX, where a pre-release shares its release's code."""
    parts = [int(p) for p in version.split("-")[0].split(".")]
    if len(parts) not in (3, 4):
        sys.exit(f"version {version} needs MAJOR.MINOR.PATCH, and may add .HOTFIX")
    hotfix = parts[3] if len(parts) == 4 else 0
    return (parts[0] * 10000 + parts[1] * 100 + parts[2]) * 10 + hotfix


def utc_iso(text: str) -> str:
    """dev-build.json's builtAt (Java's OffsetDateTime) as UTC with a Z, whatever the offset or fraction digits."""
    cleaned = re.sub(r"(\.\d{6})\d+", r"\1", text)
    stamp = datetime.datetime.fromisoformat(cleaned.replace("Z", "+00:00"))
    if stamp.tzinfo is None:
        stamp = stamp.replace(tzinfo=datetime.timezone.utc)
    return stamp.astimezone(datetime.timezone.utc).replace(microsecond=0).strftime("%Y-%m-%dT%H:%M:%SZ")


def tag_for(branch: str, sha: str, built_at_utc: str) -> str:
    stamp = re.sub(r"[^0-9]", "", built_at_utc)[:12]
    return f"dev-{stamp}-{db.slug(branch)}-{sha[:7]}"


def make_row(apk: pathlib.Path, version: str, build_type: str = "debug") -> dict:
    info = db.read_info(apk)
    built = utc_iso(info.get("builtAt", ""))
    name = db.build_name(info["branch"], info["sha"], bool(info.get("dirty")))
    subject = info["commits"][0]["subject"] if info.get("commits") else ""
    return {
        "name": name, "tag": tag_for(info["branch"], info["sha"], built), "branch": info["branch"], "sha": info["sha"],
        "builtAt": built, "versionCode": version_code(version), "versionName": version,
        "size": apk.stat().st_size, "sha256": db.sha256_of(apk), "subject": subject[:200], "buildType": build_type,
    }


def openssl() -> str:
    return db.openssl_path()


def key_id(key: pathlib.Path) -> str:
    """First 8 bytes of the SHA-256 of the public key, the id Folio uses (SourceKey.keyId). Only the public half is asked for."""
    der = subprocess.run([openssl(), "ec", "-in", str(key), "-pubout", "-outform", "DER"], capture_output=True, check=True).stdout
    return hashlib.sha256(der).hexdigest()[:16].upper()


def check_key(key: pathlib.Path) -> None:
    if not key.is_file():
        sys.exit(f"No publisher key at {key}. Make one: openssl ecparam -name prime256v1 -genkey -noout -out {DEFAULT_KEY}")
    if key_id(key) == COMMUNITY_KEY_ID:
        sys.exit("That is the public Community Tweaks key. It never signs a dev build; use the dev publisher key.")


def sign(key: pathlib.Path, data: bytes) -> str:
    """Base64 of the DER ECDSA signature, which is what Folio's SourceKey.verifies reads."""
    with tempfile.TemporaryDirectory() as tmp:
        path = pathlib.Path(tmp, "data")
        path.write_bytes(data)
        out = subprocess.run([openssl(), "dgst", "-sha256", "-sign", str(key), str(path)], capture_output=True, check=True).stdout
    return base64.b64encode(out).decode("ascii")


def list_json(rows: list[dict], now: int, max_age_days: int) -> bytes:
    rows = sorted(rows, key=lambda r: r["builtAt"])[-KEEP:]
    body = {"format": FORMAT, "timestamp": now, "maxAge": max_age_days * 86400, "builds": rows}
    return (json.dumps(body, indent=2) + "\n").encode("utf-8")


def previous_rows(folder: pathlib.Path) -> list[dict]:
    path = folder / "builds.json"
    if not path.is_file():
        return []
    try:
        return list(json.loads(path.read_text(encoding="utf-8")).get("builds", []))
    except (ValueError, OSError):
        return []


def write_folder(folder: pathlib.Path, apk: pathlib.Path, row: dict, key: pathlib.Path, now: int, max_age_days: int,
                 seed: list[dict] | None = None) -> list[str]:
    """Writes the layout and returns the paths written, relative to the folder.

    [seed] is the list the repository already holds (see remote_rows): history is the folder's own list plus that one, so
    a fresh temporary folder never starts from nothing and wipes what is already published.
    """
    known = {r["tag"]: r for r in (seed or [])}
    known.update({r["tag"]: r for r in previous_rows(folder)})
    rows = [r for r in known.values() if r["tag"] != row["tag"]] + [row]
    rows = sorted(rows, key=lambda r: r["builtAt"])[-KEEP:]
    keep_tags = {r["tag"] for r in rows}
    data = list_json(rows, now, max_age_days)
    release = folder / "releases" / row["tag"]
    release.mkdir(parents=True, exist_ok=True)
    apk_name = f"FolioDev-{row['name']}.apk"
    shutil.copyfile(apk, release / apk_name)
    (release / "SHA256SUMS.txt").write_text(f"{row['sha256']}  {apk_name}\n", encoding="utf-8", newline="\n")
    (folder / "builds.json").write_bytes(data)
    (folder / "builds.json.sig").write_text(sign(key, data), encoding="ascii", newline="\n")
    for old in (folder / "releases").iterdir():
        if old.is_dir() and old.name not in keep_tags:
            shutil.rmtree(old)
    return ["builds.json", "builds.json.sig", f"releases/{row['tag']}/{apk_name}", f"releases/{row['tag']}/SHA256SUMS.txt"]


# ---- the upload (only with --upload) ---------------------------------------------------------------------------

def gh(*args: str, check: bool = True) -> subprocess.CompletedProcess:
    return subprocess.run(["gh", *args], text=True, capture_output=True, check=check)


def remote_rows() -> list[dict]:
    """The builds the repository's signed list names now. No list yet is an empty history; any other failure stops the run."""
    answer = gh("api", "-H", "Accept: application/vnd.github.raw+json", f"repos/{REPO_SLUG}/contents/builds.json?ref=source", check=False)
    if answer.returncode != 0:
        if "404" in (answer.stderr + answer.stdout):
            return []
        sys.exit("Could not read the repository's current builds.json, so nothing was uploaded (history would be at risk): "
                 + (answer.stderr or answer.stdout).strip()[:200])
    try:
        return list(json.loads(answer.stdout).get("builds", []))
    except ValueError:
        sys.exit("The repository's builds.json is not readable, so nothing was uploaded.")


def stale_tags(release_tags: list[str], listed: set[str]) -> list[str]:
    """Dev release tags the signed list no longer names. Anything else in the repository is left alone."""
    return sorted(t for t in release_tags if t.startswith("dev-") and t not in listed)


def upload(folder: pathlib.Path, row: dict, yes: bool = False) -> None:
    """Creates the release, then updates builds.json and its signature on the `source` branch, then prunes old releases.

    Written here, run only by a person who passes --upload. It checks the repository is private first, as the worker does.
    """
    visibility = json.loads(gh("repo", "view", REPO_SLUG, "--json", "visibility").stdout)["visibility"]
    if visibility != "PRIVATE":
        sys.exit(f"{REPO_SLUG} is {visibility}, not PRIVATE. Nothing was uploaded.")
    release = folder / "releases" / row["tag"]
    assets = [str(p) for p in sorted(release.iterdir())]
    gh("release", "create", row["tag"], "--repo", REPO_SLUG, "--title", f"{row['branch']} {row['sha']}", "--notes", row["subject"] or row["sha"], *assets)
    for name in ("builds.json", "builds.json.sig"):
        current = gh("api", f"repos/{REPO_SLUG}/contents/{name}?ref=source", check=False)
        sha = json.loads(current.stdout).get("sha") if current.returncode == 0 else None
        args = ["api", "-X", "PUT", f"repos/{REPO_SLUG}/contents/{name}", "-f", f"message=Publish {row['tag']}", "-f", "branch=source",
                "-f", "content=" + base64.b64encode((folder / name).read_bytes()).decode("ascii")]
        if sha:
            args += ["-f", f"sha={sha}"]
        gh(*args)
    keep = {r["tag"] for r in previous_rows(folder)}
    listing = json.loads(gh("release", "list", "--repo", REPO_SLUG, "--limit", "100", "--json", "tagName").stdout)
    old = stale_tags([entry["tagName"] for entry in listing], keep)
    if old and not yes:
        print("Releases the signed list no longer names: " + ", ".join(old))
        answer = input("Delete them? [y/N] ").strip().lower() if sys.stdin.isatty() else ""
        if answer != "y":
            print("Left them in place (pass --yes to delete without asking).")
            return
    for tag in old:
        gh("release", "delete", tag, "--repo", REPO_SLUG, "--yes", "--cleanup-tag")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Sign and lay out a Folio Dev build for the debug source.")
    parser.add_argument("--apk", help="an APK that is already built (otherwise this worktree's debug APK is built)")
    parser.add_argument("--to-folder", help="write the layout the repo and worker would serve into this folder (no network)")
    parser.add_argument("--key", default=os.environ.get("FOLIO_DEV_PUBLISHER_KEY", DEFAULT_KEY), help="the dev publisher key (PEM)")
    parser.add_argument("--max-age-days", type=int, default=30, help="how long the signed list is believed (default 30)")
    parser.add_argument("--dry-run", action="store_true", help="say what would be written and write nothing")
    parser.add_argument("--yes", action="store_true", help="with --upload: delete releases the list no longer names without asking")
    parser.add_argument("--upload", action="store_true", help="also upload to the private repository with gh (never the default)")
    args = parser.parse_args(argv)
    if not 1 <= args.max_age_days <= MAX_AGE_DAYS_LIMIT:
        sys.exit(f"--max-age-days must be 1 to {MAX_AGE_DAYS_LIMIT}")
    if not args.to_folder and not args.upload:
        sys.exit("Say where to write: --to-folder DIR (and --upload to also publish).")
    key = pathlib.Path(args.key).expanduser()
    check_key(key)
    if args.apk:
        apk = pathlib.Path(args.apk)
    else:
        if args.dry_run:
            sys.exit("--dry-run needs --apk (it will not run a build).")
        if db.cmd_build(argparse.Namespace(apk=None)) != 0:
            return 1
        apk = db.REPO / "app/build/outputs/apk/debug/app-debug.apk"
    if not apk.is_file():
        sys.exit(f"{apk} does not exist.")
    row = make_row(apk, db.folio_version())
    print(f"{row['name']}  {row['tag']}  versionCode {row['versionCode']}  {row['size'] / 1e6:.1f} MB  sha256 {row['sha256'][:12]}")
    folder = pathlib.Path(args.to_folder) if args.to_folder else pathlib.Path(tempfile.mkdtemp(prefix="folio-dev-publish-"))
    if args.dry_run:
        print(f"(dry run) would write builds.json, builds.json.sig and releases/{row['tag']}/ into {folder}")
        print(json.dumps(row, indent=2))
        return 0
    seed = remote_rows() if args.upload else []
    written = write_folder(folder, apk, row, key, int(time.time()), args.max_age_days, seed)
    print("Wrote into " + str(folder) + ": " + ", ".join(written))
    if args.upload:
        upload(folder, row, args.yes)
        print(f"Uploaded {row['tag']} to {REPO_SLUG}.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
