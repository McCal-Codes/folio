#!/usr/bin/env python3
"""Keep and switch between Folio Dev builds, one per branch, from this Mac.

    tools/dev-builds.py build                  build this worktree's debug APK and keep a copy
    tools/dev-builds.py build --apk app.apk    keep an APK you already built (its dev-build.json says which branch)
    tools/dev-builds.py list                   what is kept, and which one this script last put on the phone
    tools/dev-builds.py install [NAME]         put a kept build on the phone (latest if NAME is left out)
    tools/dev-builds.py where                  what is on the phone now, and whether something else replaced it
    tools/dev-builds.py source --base-url URL  write a signed Folio source for the kept builds (to host later)

Why a script as well as a source: a debug Folio is about 80 MB, which the Market only accepts since its app limit went to
100 MiB, and a phone-side list still needs somewhere to be hosted. Folio also refuses to install an app from a local source on purpose (T19), and any other
source must be HTTPS with a key Folio pins, so a phone-side list needs somewhere to be hosted. `install` works today
over wireless debugging; `source` makes the files for that later, and publishes nothing.

Builds are kept in ~/.folio-dev-builds (override with FOLIO_DEV_BUILDS), outside every repository. A build is named
<branch>-<commit>. Every build is signed with the local debug key, so any of them installs over any other and keeps
Folio Dev's Home and settings.
"""

from __future__ import annotations

import argparse
import datetime
import hashlib
import json
import os
import pathlib
import re
import shutil
import subprocess
import sys
import time
import zipfile

APP_ID = "com.mccal.folio.dev"
STORE = pathlib.Path(os.environ.get("FOLIO_DEV_BUILDS", "~/.folio-dev-builds")).expanduser()
KEEP = 12
# Folio's Market lists an app up to this size (market/.../SourceFiles.kt MAX_APP_BYTES, raised from 20 MiB on 2026-10-06).
MAX_APP_BYTES = 100 * 1024 * 1024
JAVA_HOME = os.environ.get("JAVA_HOME", "/opt/homebrew/opt/openjdk@17")
TEMPLATE = pathlib.Path(os.environ.get("FOLIO_SOURCE_TEMPLATE", "~/dev/folio-source-template")).expanduser()
REPO = pathlib.Path(__file__).resolve().parent.parent


def slug(text: str) -> str:
    """Lowercase letters, digits and single hyphens: safe in a file name and in a package id."""
    return re.sub(r"-+", "-", re.sub(r"[^a-z0-9]+", "-", text.lower())).strip("-") or "build"


def build_name(branch: str, sha: str, dirty: bool = False) -> str:
    return f"{slug(branch)}-{sha[:8]}" + ("-dirty" if dirty else "")


def package_id(name: str) -> str:
    """The Market identity of one kept build. Valid under the manifest schema: dot-separated, lowercase, hyphens inside."""
    return "dev.mccal.folio-builds." + name


def listing_version(base: str, built_at: str, sha: str) -> str:
    """Always newer than the installed app's own versionName, so the Market offers every kept build as an update.

    Folio Dev's versionName never changes between builds, and the Market only offers a listing that is newer than what
    is installed; a "+dev" suffix sorts after the plain name, and the time keeps a later build above an earlier one.
    """
    stamp = re.sub(r"[^0-9]", "", built_at)[:14] or "0"
    return f"{base}+dev.{stamp}.{sha[:8]}"


def listable(builds: list[dict]) -> tuple[list[dict], list[dict]]:
    """Split kept builds into those the Market could install and those over its size cap."""
    return [b for b in builds if b["size"] <= MAX_APP_BYTES], [b for b in builds if b["size"] > MAX_APP_BYTES]


def sha256_of(path: pathlib.Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def read_info(apk: pathlib.Path) -> dict:
    """The dev-build.json the build step put inside the APK (branch, sha, time, commits)."""
    with zipfile.ZipFile(apk) as z:
        try:
            return json.loads(z.read("assets/dev-build.json"))
        except KeyError:
            sys.exit(f"{apk.name} has no assets/dev-build.json: it is not a Folio Dev build from this branch's build step.")


def load_index() -> dict:
    path = STORE / "index.json"
    return json.loads(path.read_text()) if path.exists() else {"builds": [], "installed": None}


def save_index(index: dict) -> None:
    STORE.mkdir(parents=True, exist_ok=True)
    (STORE / "index.json").write_text(json.dumps(index, indent=2) + "\n")


def keep(apk: pathlib.Path) -> dict:
    """Copy an APK into the store under its build name and record it. Returns its entry."""
    info = read_info(apk)
    name = build_name(info["branch"], info["sha"], bool(info.get("dirty")))
    (STORE / "apks").mkdir(parents=True, exist_ok=True)
    target = STORE / "apks" / f"{name}.apk"
    shutil.copyfile(apk, target)
    subject = info["commits"][0]["subject"] if info.get("commits") else ""
    entry = {"name": name, "branch": info["branch"], "sha": info["sha"], "dirty": bool(info.get("dirty")), "builtAt": info.get("builtAt", ""),
             "subject": subject, "size": target.stat().st_size, "sha256": sha256_of(target)}
    index = load_index()
    index["builds"] = [b for b in index["builds"] if b["name"] != name] + [entry]
    index["builds"].sort(key=lambda b: b["builtAt"])
    for old in index["builds"][:-KEEP]:
        (STORE / "apks" / f"{old['name']}.apk").unlink(missing_ok=True)
    index["builds"] = index["builds"][-KEEP:]
    save_index(index)
    return entry


def run(cmd: list[str], **kw) -> subprocess.CompletedProcess:
    return subprocess.run(cmd, text=True, capture_output=True, **kw)


def cmd_build(args) -> int:
    apk = pathlib.Path(args.apk) if args.apk else REPO / "app/build/outputs/apk/debug/app-debug.apk"
    if not args.apk:
        env = dict(os.environ, JAVA_HOME=JAVA_HOME)
        print("Building the debug APK (this is the same build Folio Dev gets)…")
        if subprocess.run(["./gradlew", ":app:assembleDebug", "-q"], cwd=REPO, env=env).returncode != 0:
            return 1
    if not apk.exists():
        sys.exit(f"{apk} does not exist.")
    entry = keep(apk)
    flag = "  (uncommitted changes: commit first if you want it named by a real commit)" if entry["dirty"] else ""
    print(f"Kept {entry['name']}  {entry['size'] / 1e6:.1f} MB{flag}")
    return 0


def cmd_list(_args) -> int:
    index = load_index()
    if not index["builds"]:
        print("Nothing kept yet. Run: tools/dev-builds.py build")
        return 0
    installed = (index.get("installed") or {}).get("name")
    for b in reversed(index["builds"]):
        mark = "on phone" if b["name"] == installed else ""
        print(f"{b['name']:<44} {b['builtAt'][:16].replace('T', ' ')}  {b['subject'][:56]:<56} {mark}")
    return 0


# ---- the phone -------------------------------------------------------------------------------------------------

def adb(*args: str, timeout: int = 60) -> subprocess.CompletedProcess:
    return run(["adb", *args], timeout=timeout)


def find_phone() -> str:
    """A serial for adb: a plugged-in phone, a connected wireless one, or one found on the network by its mDNS name.

    `adb mdns services` is often empty on this Mac, so this asks macOS directly (dns-sd), and restarts the adb server
    when a connect says "No route to host" while the phone still answers ping.
    """
    def devices() -> list[str]:
        out = adb("devices").stdout.splitlines()[1:]
        return [line.split()[0] for line in out if line.strip().endswith("device")]
    if os.environ.get("ANDROID_SERIAL"):
        return os.environ["ANDROID_SERIAL"]
    found = devices()
    if found:
        return found[0]
    browse = run(["timeout", "6", "dns-sd", "-B", "_adb-tls-connect._tcp", "local."]) if shutil.which("dns-sd") else None
    instances = re.findall(r"_adb-tls-connect\._tcp\.\s+(\S+)", (browse.stdout if browse else ""))
    for instance in instances:
        look = run(["timeout", "6", "dns-sd", "-L", instance, "_adb-tls-connect._tcp", "local."])
        host = re.search(r"can be reached at (\S+?):(\d+)", look.stdout)
        if not host:
            continue
        ip = run(["timeout", "5", "dns-sd", "-G", "v4", host.group(1)]).stdout
        address = re.search(r"Add\s+\S+\s+\d+\s+\S+\s+(\d+\.\d+\.\d+\.\d+)", ip)
        if not address:
            continue
        target = f"{address.group(1)}:{host.group(2)}"
        if "connected" not in adb("connect", target).stdout:
            adb("kill-server")
            adb("start-server")
            adb("connect", target)
        if target in devices():
            return target
    sys.exit("No phone found. Plug it in, or turn on Wireless debugging on the Fold and run this again.")


def phone_state(serial: str) -> dict:
    out = adb("-s", serial, "shell", f"dumpsys package {APP_ID} | grep -E 'lastUpdateTime|versionName'").stdout
    updated = re.search(r"lastUpdateTime=(.+)", out)
    return {"updated": updated.group(1).strip() if updated else None}


def cmd_install(args) -> int:
    index = load_index()
    if not index["builds"]:
        sys.exit("Nothing kept yet. Run: tools/dev-builds.py build")
    builds = index["builds"]
    if args.name in (None, "latest"):
        chosen = builds[-1]
    else:
        hits = [b for b in builds if b["name"] == args.name] or [b for b in builds if args.name in b["name"]]
        if len(hits) != 1:
            sys.exit(f"{'No build' if not hits else 'More than one build'} matches '{args.name}'. Run: tools/dev-builds.py list")
        chosen = hits[0]
    apk = STORE / "apks" / f"{chosen['name']}.apk"
    print(f"Installing {chosen['name']}: {chosen['subject']}")
    if args.dry_run:
        print(f"(dry run) would run: adb install -r {apk}")
        return 0
    serial = find_phone()
    before = phone_state(serial)
    started = time.time()
    # Over Wi-Fi a 74 MB APK takes a few minutes; -r keeps Folio Dev's data, so its Home and settings survive.
    result = adb("-s", serial, "install", "-r", str(apk), timeout=1200)
    print((result.stdout + result.stderr).strip().splitlines()[-1] if (result.stdout + result.stderr).strip() else "no output")
    if "Success" not in result.stdout:
        return 1
    after = phone_state(serial)
    index["installed"] = {"name": chosen["name"], "at": datetime.datetime.now().isoformat(timespec="seconds"), "updated": after["updated"]}
    save_index(index)
    print(f"Installed in {time.time() - started:.0f} s. The phone shows a New build page the first time it opens.")
    if before["updated"] == after["updated"]:
        print("Warning: the phone's update time did not change.")
    return 0


def cmd_where(_args) -> int:
    serial = find_phone()
    state = phone_state(serial)
    installed = load_index().get("installed")
    print(f"Folio Dev on {serial}: last updated {state['updated']}")
    if not installed:
        print("This script has not installed anything yet, so it cannot say which build that is.")
        return 0
    print(f"Last installed by this script: {installed['name']} at {installed['at']}")
    if installed.get("updated") and installed["updated"] != state["updated"]:
        print("Something else installed over it since (another session, or Android Studio). The phone's own page names the real build.")
    else:
        print("Nothing has replaced it since.")
    return 0


# ---- the source ------------------------------------------------------------------------------------------------

def cmd_source(args) -> int:
    """Write a signed Folio source for the kept builds. Nothing is uploaded: the folder is yours to host over HTTPS."""
    if not args.base_url.startswith("https://"):
        sys.exit("--base-url must be an https:// address: Folio refuses any other source for an app.")
    builder = TEMPLATE / "tools/build.py"
    if not builder.exists():
        sys.exit(f"Needs the Folio source template at {TEMPLATE} (set FOLIO_SOURCE_TEMPLATE). It signs the source the way Folio checks.")
    index = load_index()
    if not index["builds"]:
        sys.exit("Nothing kept yet. Run: tools/dev-builds.py build")
    fits, big = listable(index["builds"])
    if big:
        print(f"Skipping {len(big)} build(s) over Folio's {MAX_APP_BYTES // 1048576} MiB app limit: " + ", ".join(f"{b['name']} ({b['size'] / 1e6:.0f} MB)" for b in big))
    if not fits:
        sys.exit(f"No kept build is small enough for the Market to install (the Market lists an app up to {MAX_APP_BYTES // 1048576} MiB), "
                 "so a source cannot carry it. Use `install`.")
    index["builds"] = fits
    work = STORE / "source"
    shutil.rmtree(work / "packages", ignore_errors=True)
    for folder in ("tools", "schema", "assets"):
        shutil.copytree(TEMPLATE / folder, work / folder, dirs_exist_ok=True)
    # The template's copy of the index schema still stops an app at 20 MiB; Folio's own now allows 100 MiB for an app.
    schema = work / "schema/v1/index.schema.json"
    if schema.exists():
        schema.write_text(schema.read_text().replace("20971520", str(MAX_APP_BYTES)))
    (work / "packages").mkdir(parents=True, exist_ok=True)
    base = args.base_url.rstrip("/")
    for b in index["builds"]:
        folder = work / "packages" / b["name"]
        folder.mkdir(parents=True, exist_ok=True)
        manifest = {
            "format": 1, "id": package_id(b["name"]), "name": f"Folio Dev: {b['branch']}",
            "version": listing_version("0.6.8-beta.6", b["builtAt"], b["sha"]),
            "author": {"name": "McCal"}, "minFolio": "0.6.6", "section": "tweaks", "kind": ["externalApp"],
            "permissions": [], "screens": ["cover", "inner"], "license": "MIT",
            "description": f"{b['subject']} ({b['sha']}). A debug build of Folio Dev, signed with the local debug key.",
            "via": [{"store": "obtainium", "repoUrl": base, "id": APP_ID}],
        }
        (folder / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
        (folder / "app.json").write_text(json.dumps({"url": f"{base}/apks/{b['name']}.apk", "sha256": b["sha256"], "size": b["size"]}, indent=2) + "\n")
    (work / "source.json").write_text(json.dumps({
        "name": "Folio Dev builds", "description": "Debug builds of Folio Dev, one per branch. Debug-signed: for the person who made them.",
        "icon": "assets/icon.png", "issuesUrl": "https://github.com/McCal-Codes/folio/issues", "maxAgeDays": 14}, indent=2) + "\n")
    key = pathlib.Path(args.key).expanduser() if args.key else STORE / "dev-source.pem"
    if not key.exists():
        # A key for this local source only. It is not Folio's source key and never goes anywhere near a repository.
        run(["openssl", "ecparam", "-name", "prime256v1", "-genkey", "-noout", "-out", str(key)])
        key.chmod(0o600)
        print(f"Made a signing key for this source at {key}. Keep it: a lost key means re-adding the source on the phone.")
    result = subprocess.run([sys.executable, "tools/build.py", "--key", str(key)], cwd=work)
    if result.returncode != 0:
        return result.returncode
    site = work / "_site"
    (site / "apks").mkdir(exist_ok=True)
    for b in index["builds"]:
        shutil.copyfile(STORE / "apks" / f"{b['name']}.apk", site / "apks" / f"{b['name']}.apk")
    print(f"\nSource written to {site}\nHost that folder at {base} over HTTPS (nothing was uploaded), then add {base} under Market > Sources.")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description="Keep and switch between Folio Dev builds.")
    commands = parser.add_subparsers(dest="command", required=True)
    b = commands.add_parser("build", help="build and keep this worktree's debug APK")
    b.add_argument("--apk", help="keep an APK that is already built instead of building")
    b.set_defaults(run=cmd_build)
    commands.add_parser("list", help="the kept builds").set_defaults(run=cmd_list)
    i = commands.add_parser("install", help="put a kept build on the phone")
    i.add_argument("name", nargs="?", help="a build name, part of one, or 'latest' (the default)")
    i.add_argument("--dry-run", action="store_true", help="say what would be installed and stop")
    i.set_defaults(run=cmd_install)
    commands.add_parser("where", help="what is on the phone now").set_defaults(run=cmd_where)
    s = commands.add_parser("source", help="write a signed Folio source for the kept builds")
    s.add_argument("--base-url", required=True, help="the https:// address the folder will be served from")
    s.add_argument("--key", help="a PEM key to sign with (a local one is made if left out)")
    s.set_defaults(run=cmd_source)
    args = parser.parse_args()
    return args.run(args)


if __name__ == "__main__":
    sys.exit(main())
