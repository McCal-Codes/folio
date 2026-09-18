#!/usr/bin/env python3
"""folio-pkg — check a Folio package, or a whole source, before anyone installs from it.

    tools/folio-pkg.py validate docs/sdk/source      a source: its index and every package in it
    tools/folio-pkg.py validate path/to/package      one package folder
    tools/folio-pkg.py validate thing.foliopkg       a packed one

It checks three kinds of thing:

* **The schemas.** Every file is validated against `docs/sdk/schema/v1`, which is the contract.
* **What the schemas can't see.** That the index and the packages agree, that every picture a package names is
  actually there, that an externalApp has somewhere to install from, that a signed source's entry matches its index.
* **What a phone will have to do.** Sizes, entry counts and path rules, so a package that no phone would accept is
  caught here instead of there.

No dependencies: a person writing their first tweak should need python3 and nothing else. The schema validator covers
the subset the Folio schemas use, and refuses to run if a schema starts using a keyword it doesn't know - it must
never pass something by ignoring a rule.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import pathlib
import re
import sys
import time
import zipfile

def _schema_dir() -> pathlib.Path:
    """Where the schemas are: a source repository keeps a copy, Folio's own repository has the originals."""
    here = pathlib.Path(__file__).resolve().parent
    candidates = [
        pathlib.Path(os.environ["FOLIO_SCHEMAS"]) if os.environ.get("FOLIO_SCHEMAS") else None,
        here.parent / "schema" / "v1",
        here.parent / "docs" / "sdk" / "schema" / "v1",
    ]
    for candidate in candidates:
        if candidate and (candidate / "manifest.schema.json").is_file():
            return candidate
    raise SystemExit(
        "folio-pkg: can't find the schemas. Keep a copy in schema/v1, or point FOLIO_SCHEMAS at one."
    )


SCHEMA_DIR = _schema_dir()

# The extensions a source or a package may contain. Anything else is rejected on sight by the phone, so it is an
# error here. `.sig` and `.pub` belong to a source rather than a package.
PACKAGE_SUFFIXES = {".json", ".png", ".webp", ".jpg", ".jpeg", ".js"}
SOURCE_SUFFIXES = PACKAGE_SUFFIXES | {".sig", ".pub", ".foliopkg"}
PICTURE_SUFFIXES = {".png", ".webp", ".jpg", ".jpeg"}

MAX_ZIP_COMPRESSED = 20 * 1024 * 1024
MAX_ZIP_UNCOMPRESSED = 50 * 1024 * 1024
MAX_ZIP_ENTRIES = 500
BIG_PICTURE = 1024 * 1024        # a warning: the store shows these before anything is installed
HUGE_PICTURE = 4 * 1024 * 1024   # an error: nobody should download this for a thumbnail

VERSION_RE = re.compile(r"^\d+\.\d+\.\d+(-[A-Za-z0-9.]+)?$")


# ---------------------------------------------------------------------------------------------------------------
# a small JSON Schema validator: the subset the Folio schemas use, and nothing else
# ---------------------------------------------------------------------------------------------------------------

KNOWN_KEYWORDS = {
    "$schema", "$id", "$ref", "$defs", "definitions", "title", "description", "examples", "default",
    "type", "enum", "const", "required", "properties", "additionalProperties", "propertyNames",
    "maxProperties", "minProperties", "dependentRequired", "pattern", "minLength", "maxLength",
    "items", "minItems", "maxItems", "uniqueItems", "contains", "minimum", "maximum", "format",
    "allOf", "anyOf", "oneOf", "not", "if", "then", "else",
}

TYPES = {
    "object": dict, "array": list, "string": str, "boolean": bool,
    "number": (int, float), "integer": int, "null": type(None),
}


class SchemaSet:
    """The schemas, loaded once, with refs resolved against the folder they came from."""

    def __init__(self, folder: pathlib.Path):
        self.folder = folder
        self.by_name: dict[str, dict] = {}
        for path in sorted(folder.glob("*.schema.json")):
            schema = json.loads(path.read_text())
            self._check_keywords(schema, path.name)
            self.by_name[path.name] = schema

    # Which keywords hold schemas, so the walk knows where a schema stops and ordinary data begins: a
    # dependentRequired's keys are field names, not keywords, and an enum's values are just values.
    _SCHEMA_VALUED = ("items", "contains", "not", "if", "then", "else", "additionalProperties", "propertyNames")
    _SCHEMA_LISTS = ("allOf", "anyOf", "oneOf")
    _SCHEMA_MAPS = ("properties", "$defs", "definitions")

    def _check_keywords(self, node, where: str) -> None:
        """A schema using a keyword this validator ignores would pass files it should fail. Refuse instead."""
        if not isinstance(node, dict):
            return
        for key, value in node.items():
            if key not in KNOWN_KEYWORDS:
                raise SystemExit(
                    f"folio-pkg: {where} uses the schema keyword {key!r}, which this tool does not implement.\n"
                    f"Teach it that keyword before trusting it again."
                )
            if key in self._SCHEMA_MAPS and isinstance(value, dict):
                for sub in value.values():
                    self._check_keywords(sub, where)
            elif key in self._SCHEMA_LISTS and isinstance(value, list):
                for sub in value:
                    self._check_keywords(sub, where)
            elif key in self._SCHEMA_VALUED:
                self._check_keywords(value, where)

    def get(self, name: str) -> dict:
        if name not in self.by_name:
            raise SystemExit(f"folio-pkg: no schema called {name} in {self.folder}")
        return self.by_name[name]

    def resolve(self, ref: str, current: dict) -> dict:
        if ref.startswith("#/"):
            node = current
            for part in ref[2:].split("/"):
                node = node[part]
            return node
        return self.get(ref)

    def validate(self, instance, schema: dict, where: str, root: dict | None = None) -> list[str]:
        """Returns a list of problems, each one a sentence about a place in the file."""
        root = root or schema
        out: list[str] = []
        if "$ref" in schema:
            target = self.resolve(schema["$ref"], root)
            new_root = target if not schema["$ref"].startswith("#/") else root
            return self.validate(instance, target, where, new_root)

        if "type" in schema:
            wanted = schema["type"] if isinstance(schema["type"], list) else [schema["type"]]
            # JSON has no separate boolean-as-number, and a bool is an int in Python: keep them apart.
            ok = any(
                isinstance(instance, TYPES[name]) and not (name != "boolean" and isinstance(instance, bool))
                for name in wanted
            )
            if not ok:
                return [f"{where}: expected {' or '.join(wanted)}, found {_name_of(instance)}"]

        if "const" in schema and instance != schema["const"]:
            out.append(f"{where}: must be {schema['const']!r}")
        if "enum" in schema and instance not in schema["enum"]:
            out.append(f"{where}: {instance!r} is not one of {', '.join(map(str, schema['enum']))}")

        if isinstance(instance, str):
            if "pattern" in schema and not re.search(schema["pattern"], instance):
                out.append(f"{where}: {instance!r} does not match {schema['pattern']}")
            if "minLength" in schema and len(instance) < schema["minLength"]:
                out.append(f"{where}: shorter than {schema['minLength']} characters")
            if "maxLength" in schema and len(instance) > schema["maxLength"]:
                out.append(f"{where}: longer than {schema['maxLength']} characters")

        if isinstance(instance, (int, float)) and not isinstance(instance, bool):
            if "minimum" in schema and instance < schema["minimum"]:
                out.append(f"{where}: below {schema['minimum']}")
            if "maximum" in schema and instance > schema["maximum"]:
                out.append(f"{where}: above {schema['maximum']}")

        if isinstance(instance, list):
            if "minItems" in schema and len(instance) < schema["minItems"]:
                out.append(f"{where}: needs at least {schema['minItems']} item(s)")
            if "maxItems" in schema and len(instance) > schema["maxItems"]:
                out.append(f"{where}: more than {schema['maxItems']} items")
            if schema.get("uniqueItems") and _has_duplicates(instance):
                out.append(f"{where}: repeats an item")
            if "items" in schema:
                for i, item in enumerate(instance):
                    out += self.validate(item, schema["items"], f"{where}[{i}]", root)
            if "contains" in schema and not any(
                not self.validate(item, schema["contains"], where, root) for item in instance
            ):
                out.append(f"{where}: nothing in this list is what it needs to contain")

        if isinstance(instance, dict):
            for key in schema.get("required", []):
                if key not in instance:
                    out.append(f"{where}: missing {key}")
            properties = schema.get("properties", {})
            for key, value in instance.items():
                if key in properties:
                    out += self.validate(value, properties[key], f"{where}.{key}", root)
                elif schema.get("additionalProperties") is False:
                    out.append(f"{where}: {key} is not a field this file may have")
                elif isinstance(schema.get("additionalProperties"), dict):
                    out += self.validate(value, schema["additionalProperties"], f"{where}.{key}", root)
                if "propertyNames" in schema:
                    out += self.validate(key, schema["propertyNames"], f"{where}: the name {key!r}", root)
            if "maxProperties" in schema and len(instance) > schema["maxProperties"]:
                out.append(f"{where}: more than {schema['maxProperties']} entries")
            if "minProperties" in schema and len(instance) < schema["minProperties"]:
                out.append(f"{where}: fewer than {schema['minProperties']} entries")
            for key, others in schema.get("dependentRequired", {}).items():
                if key in instance:
                    for other in others:
                        if other not in instance:
                            out.append(f"{where}: {key} also needs {other}")

        for sub in schema.get("allOf", []):
            out += self.validate(instance, sub, where, root)
        if "anyOf" in schema and not any(not self.validate(instance, sub, where, root) for sub in schema["anyOf"]):
            out.append(f"{where}: does not match any of the allowed shapes")
        if "oneOf" in schema:
            matches = sum(1 for sub in schema["oneOf"] if not self.validate(instance, sub, where, root))
            if matches != 1:
                out.append(
                    f"{where}: matches {matches} of the allowed shapes, and should match exactly one"
                )
        if "not" in schema and not self.validate(instance, schema["not"], where, root):
            out.append(f"{where}: is a shape this file may not have")
        if "if" in schema:
            branch = "then" if not self.validate(instance, schema["if"], where, root) else "else"
            if branch in schema:
                out += self.validate(instance, schema[branch], where, root)
        return out


def _name_of(value) -> str:
    if value is None:
        return "null"
    if isinstance(value, bool):
        return "a boolean"
    if isinstance(value, str):
        return "a string"
    if isinstance(value, (int, float)):
        return "a number"
    if isinstance(value, list):
        return "a list"
    return "an object"


def _has_duplicates(items: list) -> bool:
    seen: list = []
    for item in items:
        if item in seen:
            return True
        seen.append(item)
    return False


# ---------------------------------------------------------------------------------------------------------------
# what was found
# ---------------------------------------------------------------------------------------------------------------

class Report:
    def __init__(self) -> None:
        self.errors: list[str] = []
        self.warnings: list[str] = []
        self.notes: list[str] = []

    def error(self, message: str) -> None:
        self.errors.append(message)

    def warn(self, message: str) -> None:
        self.warnings.append(message)

    def note(self, message: str) -> None:
        self.notes.append(message)

    def print(self, subject: str, counted: str, strict: bool) -> int:
        for message in self.errors:
            print(f"  ✗ {message}")
        for message in self.warnings:
            print(f"  ! {message}")
        for message in self.notes:
            print(f"  · {message}")
        parts = [counted]
        parts.append(f"{len(self.errors)} error" + ("" if len(self.errors) == 1 else "s"))
        parts.append(f"{len(self.warnings)} warning" + ("" if len(self.warnings) == 1 else "s"))
        print(f"{subject}: " + ", ".join(parts))
        if self.errors:
            return 1
        return 1 if strict and self.warnings else 0


# ---------------------------------------------------------------------------------------------------------------
# the checks
# ---------------------------------------------------------------------------------------------------------------

def load_json(path: pathlib.Path, report: Report, where: str) -> dict | None:
    try:
        return json.loads(path.read_text())
    except FileNotFoundError:
        report.error(f"{where}: missing")
    except json.JSONDecodeError as problem:
        report.error(f"{where}: not valid JSON - {problem.msg} on line {problem.lineno}")
    return None


def check_picture(root: pathlib.Path, named: str, where: str, report: Report) -> None:
    """Pictures are relative paths on the source's own host, and a phone downloads them before installing anything."""
    if named.startswith("/") or named.startswith("http://") or named.startswith("https://"):
        report.error(f"{where}: {named} must be a path inside the source, not an address")
        return
    if ".." in pathlib.PurePosixPath(named).parts:
        report.error(f"{where}: {named} climbs out of the source")
        return
    suffix = pathlib.PurePosixPath(named).suffix.lower()
    if suffix not in PICTURE_SUFFIXES:
        report.error(f"{where}: {named} is not a picture Folio can show ({', '.join(sorted(PICTURE_SUFFIXES))})")
        return
    picture = root / named
    if not picture.is_file():
        report.error(f"{where}: {named} is named but not there")
        return
    size = picture.stat().st_size
    if size > HUGE_PICTURE:
        report.error(f"{where}: {named} is {size // 1024 // 1024} MB; the store shows this before anything is installed")
    elif size > BIG_PICTURE:
        report.warn(f"{where}: {named} is {size // 1024} KB; it is downloaded just to show the package")


def check_package(folder: pathlib.Path, source_root: pathlib.Path, schemas: SchemaSet, report: Report) -> dict | None:
    """One package folder: its manifest, its page, and the files its manifest promises."""
    name = folder.name
    manifest = load_json(folder / "manifest.json", report, f"{name}/manifest.json")
    if manifest is None:
        return None
    for problem in schemas.validate(manifest, schemas.get("manifest.schema.json"), f"{name}/manifest.json"):
        report.error(problem)

    kinds = manifest.get("kind", [])
    if "script" in kinds and not (folder / "script.js").is_file():
        report.error(f"{name}: kind includes script, but there is no script.js")
    if "script" not in kinds and (folder / "script.js").is_file():
        report.error(f"{name}: there is a script.js, but kind does not include script")
    if "tweakBundle" in kinds and not (folder / "tweaks.json").is_file():
        report.error(f"{name}: kind includes tweakBundle, but there is no tweaks.json")

    version = manifest.get("minFolio")
    if isinstance(version, str) and not VERSION_RE.match(version):
        report.error(f"{name}/manifest.json: minFolio {version!r} is not a version like 0.7.0")

    if manifest.get("icon"):
        check_picture(source_root, manifest["icon"], f"{name}/manifest.json: icon", report)

    named_depiction = manifest.get("depiction")
    if named_depiction:
        depiction_path = folder / named_depiction
        depiction = load_json(depiction_path, report, f"{name}/{named_depiction}")
        if depiction is not None:
            for problem in schemas.validate(depiction, schemas.get("depiction.schema.json"), f"{name}/{named_depiction}"):
                report.error(problem)
            for i, block in enumerate(depiction.get("blocks", [])):
                spot = f"{name}/{named_depiction}: blocks[{i}]"
                if block.get("type") == "hero" and block.get("image"):
                    check_picture(source_root, block["image"], spot, report)
                if block.get("type") == "screenshots":
                    for picture in block.get("images", []):
                        check_picture(source_root, picture, spot, report)

    for extra, schema_name in (("tweaks.json", "tweaks.schema.json"), ("settings.json", "settings.schema.json")):
        path = folder / extra
        if path.is_file():
            content = load_json(path, report, f"{name}/{extra}")
            if content is not None:
                for problem in schemas.validate(content, schemas.get(schema_name), f"{name}/{extra}"):
                    report.error(problem)

    for path in sorted(folder.rglob("*")):
        if path.is_file() and path.suffix.lower() not in PACKAGE_SUFFIXES:
            report.error(f"{name}/{path.relative_to(folder)}: a package may not contain this kind of file")
    return manifest


def check_source(root: pathlib.Path, schemas: SchemaSet, report: Report) -> str:
    """A whole source: the index, every package in it, and whether the two agree."""
    index = load_json(root / "index.json", report, "index.json")
    if index is None:
        return "no index"
    for problem in schemas.validate(index, schemas.get("index.schema.json"), "index.json"):
        report.error(problem)

    if index.get("icon"):
        check_picture(root, index["icon"], "index.json: icon", report)

    listed: dict[str, dict] = {}
    for i, entry in enumerate(index.get("packages", [])):
        package_id = entry.get("id")
        if package_id in listed:
            report.error(f"index.json: packages[{i}] lists {package_id} twice")
        listed[package_id] = entry
        manifest = entry.get("manifest", {})
        if manifest.get("id") and manifest["id"] != package_id:
            report.error(f"index.json: packages[{i}] is filed under {package_id} but its manifest says {manifest['id']}")
        if manifest.get("version") and manifest["version"] != entry.get("version"):
            report.error(
                f"index.json: {package_id} is listed as {entry.get('version')} "
                f"but its manifest says {manifest['version']}"
            )

    for i, featured in enumerate(index.get("featured", [])):
        if featured.get("package") not in listed:
            report.error(f"index.json: featured[{i}] points at {featured.get('package')}, which this source doesn't list")
        if featured.get("image"):
            check_picture(root, featured["image"], f"index.json: featured[{i}]", report)

    packages_dir = root / "packages"
    on_disk: dict[str, pathlib.Path] = {}
    if packages_dir.is_dir():
        for folder in sorted(p for p in packages_dir.iterdir() if p.is_dir()):
            manifest = check_package(folder, root, schemas, report)
            if manifest and manifest.get("id"):
                if manifest["id"] in on_disk:
                    report.error(f"{folder.name}: another folder already publishes {manifest['id']}")
                on_disk[manifest["id"]] = folder
                entry = listed.get(manifest["id"])
                if entry is None:
                    report.error(f"{folder.name}: {manifest['id']} is not in index.json, so nobody will see it")
                else:
                    embedded = {k: v for k, v in entry.get("manifest", {}).items() if k != "$schema"}
                    folder_copy = {k: v for k, v in manifest.items() if k != "$schema"}
                    if embedded != folder_copy:
                        report.error(
                            f"{folder.name}: index.json carries a different manifest from the one in the folder"
                        )

    # A published source carries .foliopkg zips rather than folders. Check those too, and check that what is inside
    # each one is what the index advertises - a stale zip beside a fresh index is the classic publishing mistake.
    packed: dict[str, pathlib.Path] = {}
    for archive_path in sorted((root / "packages").glob("*.foliopkg")) if packages_dir.is_dir() else []:
        inside = check_foliopkg(archive_path, schemas, report, standalone=False)
        if inside and inside.get("id"):
            packed[inside["id"]] = archive_path
            entry = listed.get(inside["id"])
            if entry is None:
                report.error(f"packages/{archive_path.name}: {inside['id']} is not in index.json, so nobody will see it")
            else:
                advertised = {k: v for k, v in entry.get("manifest", {}).items() if k != "$schema"}
                actual = {k: v for k, v in inside.items() if k != "$schema"}
                if advertised != actual:
                    report.error(
                        f"packages/{archive_path.name}: what is inside differs from what index.json advertises"
                    )

    for package_id in listed:
        if package_id not in on_disk and package_id not in packed:
            report.error(f"index.json lists {package_id}, but there is no folder or .foliopkg for it here")

    signed = (root / "entry.json").is_file()
    check_entry(root, schemas, report)
    check_revoked(root, schemas, report, signed)

    extras = [
        str(path.relative_to(root))
        for path in sorted(root.rglob("*"))
        if path.is_file() and path.suffix.lower() not in SOURCE_SUFFIXES
    ]
    if extras:
        shown = ", ".join(extras[:3]) + (f" and {len(extras) - 3} more" if len(extras) > 3 else "")
        report.warn(f"{len(extras)} file(s) here can't be published, so keep them out of what you upload: {shown}")

    return f"{len(listed)} package" + ("" if len(listed) == 1 else "s")


def check_entry(root: pathlib.Path, schemas: SchemaSet, report: Report) -> None:
    """The signed pointer to the index, if this source has one yet."""
    entry_path = root / "entry.json"
    if not entry_path.is_file():
        report.note("no entry.json: unsigned, so Folio will call it an unknown developer")
        return
    entry = load_json(entry_path, report, "entry.json")
    if entry is None:
        return
    for problem in schemas.validate(entry, schemas.get("entry.schema.json"), "entry.json"):
        report.error(problem)
    if not (root / "entry.json.sig").is_file():
        report.error("entry.json has no entry.json.sig beside it, so no phone will accept it")
    if not (root / "key.pub").is_file():
        report.warn("no key.pub: whoever adds this source has nothing to pin")

    pointer = entry.get("index", {})
    index_path = root / pointer.get("path", "index.json")
    if index_path.is_file():
        raw = index_path.read_bytes()
        if pointer.get("size") is not None and pointer["size"] != len(raw):
            report.error(f"entry.json: says the index is {pointer['size']} bytes; it is {len(raw)}")
        digest = hashlib.sha256(raw).hexdigest()
        if pointer.get("sha256") and pointer["sha256"].lower() != digest:
            report.error("entry.json: the index's sha256 does not match the index that is here")
    stamp = entry.get("timestamp")
    if isinstance(stamp, int) and stamp > time.time() + 3600:
        report.warn("entry.json: the timestamp is in the future, which a phone will refuse")


def check_revoked(root: pathlib.Path, schemas: SchemaSet, report: Report, signed: bool) -> None:
    path = root / "revoked.json"
    if not path.is_file():
        return
    revoked = load_json(path, report, "revoked.json")
    if revoked is None:
        return
    for problem in schemas.validate(revoked, schemas.get("revoked.schema.json"), "revoked.json"):
        report.error(problem)
    if signed and not (root / "revoked.json.sig").is_file():
        report.error("revoked.json has no signature beside it, so a phone will ignore it")


def check_foliopkg(path: pathlib.Path, schemas: SchemaSet, report: Report, standalone: bool = True):
    """A packed package: the limits a phone applies before it will open one. Returns the manifest inside it."""
    if path.stat().st_size > MAX_ZIP_COMPRESSED:
        report.error(f"{path.name} is over the 20 MB a .foliopkg may be")
    try:
        archive = zipfile.ZipFile(path)
    except zipfile.BadZipFile:
        report.error(f"{path.name}: not a zip archive")
        return None

    names = archive.namelist()
    if len(names) > MAX_ZIP_ENTRIES:
        report.error(f"{path.name}: {len(names)} entries, over the 500 allowed")
    total = 0
    for info in archive.infolist():
        total += info.file_size
        name = info.filename
        if name.startswith("/") or ".." in pathlib.PurePosixPath(name).parts or "\\" in name:
            report.error(f"{name}: a package may not contain a path like this")
        if (info.external_attr >> 16) & 0o170000 == 0o120000:
            report.error(f"{name}: symlinks are not allowed in a package")
        if not info.is_dir() and pathlib.PurePosixPath(name).suffix.lower() not in PACKAGE_SUFFIXES:
            report.error(f"{name}: a package may not contain this kind of file")
    if total > MAX_ZIP_UNCOMPRESSED:
        report.error(f"{path.name}: unpacks to over the 50 MB allowed")
    if "manifest.json" not in names:
        report.error(f"{path.name}: no manifest.json at the top of the archive")
        return None

    try:
        manifest = json.loads(archive.read("manifest.json"))
    except json.JSONDecodeError as problem:
        report.error(f"{path.name}: manifest.json is not valid JSON - {problem.msg}")
        return None
    label = "manifest.json" if standalone else f"packages/{path.name}: manifest.json"
    for problem in schemas.validate(manifest, schemas.get("manifest.schema.json"), label):
        report.error(problem)
    if "depiction" in manifest and manifest["depiction"] not in names:
        report.error(f"{label}: names {manifest['depiction']}, which is not in the archive")
    if standalone:
        report.note("pictures are not checked here: they live on the source, not in the package")
    return manifest


# ---------------------------------------------------------------------------------------------------------------

def validate(target: pathlib.Path, strict: bool) -> int:
    schemas = SchemaSet(SCHEMA_DIR)
    report = Report()
    print(f"{target}")
    if target.is_file() and target.suffix == ".foliopkg":
        check_foliopkg(target, schemas, report)
        counted = "1 package"
    elif (target / "index.json").is_file():
        counted = check_source(target, schemas, report)
    elif (target / "manifest.json").is_file():
        check_package(target, target.parent.parent if target.parent.name == "packages" else target, schemas, report)
        counted = "1 package"
    else:
        print(f"folio-pkg: {target} is neither a source (index.json), a package (manifest.json) nor a .foliopkg")
        return 2
    return report.print(str(target), counted, strict)


def main() -> int:
    parser = argparse.ArgumentParser(description="Check a Folio package or source.")
    commands = parser.add_subparsers(dest="command", required=True)
    check = commands.add_parser("validate", help="check a source, a package folder or a .foliopkg")
    check.add_argument("path", type=pathlib.Path)
    check.add_argument("--strict", action="store_true", help="treat warnings as failures, for CI")
    args = parser.parse_args()
    if args.command == "validate":
        return validate(args.path, args.strict)
    return 2


if __name__ == "__main__":
    sys.exit(main())
