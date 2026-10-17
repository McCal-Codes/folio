import argparse
import datetime
import importlib.util
import os
import pathlib
import re
import sys
import tempfile
import time
import unittest

spec = importlib.util.spec_from_file_location("dev_builds", pathlib.Path(__file__).with_name("dev-builds.py"))
db = importlib.util.module_from_spec(spec)
spec.loader.exec_module(db)

# The manifest schema's own patterns (docs/sdk/schema/v1/manifest.schema.json).
ID = re.compile(r"^[a-z][a-z0-9-]*(\.[a-z0-9][a-z0-9-]*)+$")
VERSION = re.compile(r"^([0-9]+:)?[0-9][A-Za-z0-9.+~]*(-[A-Za-z0-9.+~]+)?$")


class DevBuildsTest(unittest.TestCase):
    def test_names_are_safe_for_files_and_ids(self):
        self.assertEqual(db.build_name("capability-broker", "d6bc49af4c1b"), "capability-broker-d6bc49af")
        self.assertEqual(db.build_name("feature/Odd Name!", "abcdef012"), "feature-odd-name-abcdef01")
        self.assertEqual(db.build_name("main", "abcdef01", dirty=True), "main-abcdef01-dirty")
        self.assertEqual(db.slug("///"), "build")

    def test_package_ids_pass_the_manifest_schema(self):
        for name in ("capability-broker-d6bc49af", "main-abcdef01-dirty", db.build_name("Weird/Branch_1", "0123abcd")):
            self.assertRegex(db.package_id(name), ID)

    def test_listing_versions_pass_the_schema_and_sort_by_time(self):
        early = db.listing_version("0.6.8-beta.6", "2026-10-06T15:26:42.7-04:00", "990a3002")
        late = db.listing_version("0.6.8-beta.6", "2026-10-06T15:41:43.7-04:00", "d6bc49af")
        for v in (early, late):
            self.assertRegex(v, VERSION)
            self.assertTrue(v.startswith("0.6.8-beta.6+dev."))
        self.assertLess(early.split("+dev.")[1], late.split("+dev.")[1])

    def test_builds_over_the_market_size_cap_are_split_off(self):
        small = {"name": "a", "size": 5_000_000}
        edge = {"name": "b", "size": db.MAX_APP_BYTES}
        big = {"name": "c", "size": db.MAX_APP_BYTES + 1}
        fits, over = db.listable([small, edge, big])
        self.assertEqual([b["name"] for b in fits], ["a", "b"])
        self.assertEqual([b["name"] for b in over], ["c"])

    def test_listing_version_starts_from_this_checkouts_version(self):
        # It used to be a fixed 0.6.8-beta.6, which sorts below 0.6.9 builds, so the Market never offered them.
        self.assertRegex(db.folio_version(), VERSION)
        self.assertNotEqual(db.folio_version(), "0.0.0")

    def test_phones_are_read_from_adb_mdns(self):
        out = ("List of discovered mdns services\n"
               "adb-R5CX1234-AbCdEf\t_adb-tls-connect._tcp\t192.168.1.228:37581\n"
               "adb-R5CX1234-AbCdEf\t_adb-tls-pairing._tcp\t192.168.1.228:41234\n")
        self.assertEqual(db.mdns_targets(out), ["192.168.1.228:37581"])
        self.assertEqual(db.mdns_targets("List of discovered mdns services\n"), [])

    def test_a_command_that_never_ends_is_stopped(self):
        started = time.time()
        out = db.run_for([sys.executable, "-c", "import time; print('hi', flush=True); time.sleep(30)"], 2)
        self.assertLess(time.time() - started, 15)
        self.assertIn("hi", out)
        self.assertEqual(db.run_for(["no-such-program-anywhere"], 1), "")

    def test_a_tool_off_the_path_is_found_where_it_usually_sits(self):
        with tempfile.TemporaryDirectory() as folder:
            exe = pathlib.Path(folder) / "folio-test-tool.exe"
            exe.write_bytes(b"")
            self.assertEqual(db.tool("folio-test-tool", pathlib.Path(folder)), str(exe))
        self.assertEqual(db.tool("folio-test-tool-missing"), "folio-test-tool-missing")

    def test_only_a_jdk_17_counts_as_17(self):
        with tempfile.TemporaryDirectory() as folder:
            release = pathlib.Path(folder) / "release"
            release.write_text('JAVA_VERSION="17.0.20"\n', encoding="utf-8")
            self.assertTrue(db.is_jdk17(folder))
            release.write_text('JAVA_VERSION="21.0.4"\n', encoding="utf-8")
            self.assertFalse(db.is_jdk17(folder))
        self.assertFalse(db.is_jdk17("/no/such/jdk"))



class DriveTest(unittest.TestCase):
    """The drive command, against a temporary store and a temporary Drive folder: never the real one."""

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        root = pathlib.Path(self.tmp.name)
        self.saved = (db.STORE, os.environ.get("FOLIO_DRIVE_BUILDS"))
        db.STORE = root / "store"
        self.drive = root / "Dev Builds"
        self.drive.mkdir()
        os.environ["FOLIO_DRIVE_BUILDS"] = str(self.drive)

    def tearDown(self):
        db.STORE = self.saved[0]
        if self.saved[1] is None:
            os.environ.pop("FOLIO_DRIVE_BUILDS", None)
        else:
            os.environ["FOLIO_DRIVE_BUILDS"] = self.saved[1]
        self.tmp.cleanup()

    def keep_fake(self, dirty=False):
        (db.STORE / "apks").mkdir(parents=True, exist_ok=True)
        name = db.build_name("spike-open-beside", "e9f6515a", dirty)
        apk = db.STORE / "apks" / f"{name}.apk"
        apk.write_bytes(b"not really an apk")
        entry = {"name": name, "branch": "spike-open-beside", "sha": "e9f6515a", "dirty": dirty, "builtAt": "2026-10-11T16:00:00",
                 "subject": "Try opening two apps side by side", "version": "0.6.9-beta.2", "size": apk.stat().st_size,
                 "sha256": db.sha256_of(apk)}
        db.save_index({"builds": [entry], "installed": None})
        return entry

    def args(self, **kw):
        base = dict(name=None, test=None, test_file=None, by=None, pr=None, allow_dirty=False, copy=False)
        base.update(kw)
        return argparse.Namespace(**base)

    def drive_files(self):
        return sorted(p.name for p in self.drive.rglob("*") if p.is_file())

    def test_versions_map_to_codes_and_folders_like_gradle(self):
        self.assertEqual(db.version_code("0.6.9-beta.2"), 6090)
        self.assertEqual(db.version_code("0.6.7.1"), 6071)
        self.assertEqual(db.version_code("0.7.0"), 7000)
        self.assertEqual(db.version_line("0.6.9-beta.2"), "0.6.9")
        self.assertEqual(db.version_line("0.6.9.1"), "0.6.9")

    def test_names_follow_the_dev_builds_readme(self):
        entry = self.keep_fake()
        self.assertEqual(db.drive_stem(entry, "2026-10-11"), "2026-10-11_Folio-Dev_0.6.9-beta.2_spike-open-beside_e9f6515")
        self.assertTrue(db.drive_stem(dict(entry, dirty=True), "2026-10-11").endswith("_e9f6515-dirty"))

    def test_the_note_says_what_it_is_how_to_check_it_and_what_to_test(self):
        entry = self.keep_fake()
        note = db.drive_note(entry, "PC session 3", "1. Open Settings", pr="#365", today=datetime.date(2026, 10, 2))
        self.assertTrue(note.startswith("Folio Dev 0.6.9-beta.2 (versionCode 6090), debug build, package com.mccal.folio.dev\n"))
        self.assertIn(f"SHA-256: {entry['sha256']}", note)
        self.assertIn("Branch: spike-open-beside (PR #365), commit e9f6515", note)
        self.assertIn("Uploaded by: PC session 3, 2 Oct 2026", note)
        self.assertTrue(note.rstrip().endswith("What to test\n1. Open Settings"))
        self.assertIn("uncommitted changes", db.drive_note(dict(entry, dirty=True), "x", "y"))

    def test_without_copy_nothing_is_written(self):
        self.keep_fake()
        self.assertEqual(db.cmd_drive(self.args(test="steps", by="PC session 3")), 0)
        self.assertEqual(self.drive_files(), [])

    def test_copy_needs_steps_and_an_uploader(self):
        self.keep_fake()
        with self.assertRaises(SystemExit):
            db.cmd_drive(self.args(copy=True, by="PC session 3"))
        with self.assertRaises(SystemExit):
            db.cmd_drive(self.args(copy=True, test="steps"))
        self.assertEqual(self.drive_files(), [])

    def test_copy_puts_both_files_in_the_version_folder_and_the_hash_matches(self):
        entry = self.keep_fake()
        self.assertEqual(db.cmd_drive(self.args(copy=True, test="1. Open Settings", by="PC session 3")), 0)
        stem = db.drive_stem(entry, datetime.date.today().isoformat())
        self.assertEqual(self.drive_files(), [f"{stem}.apk", f"{stem}.txt"])
        self.assertEqual(db.sha256_of(self.drive / "0.6.9" / f"{stem}.apk"), entry["sha256"])
        self.assertIn("1. Open Settings", (self.drive / "0.6.9" / f"{stem}.txt").read_text(encoding="utf-8"))

    def test_an_existing_file_is_never_overwritten(self):
        entry = self.keep_fake()
        stem = db.drive_stem(entry, datetime.date.today().isoformat())
        (self.drive / "0.6.9").mkdir()
        (self.drive / "0.6.9" / f"{stem}.txt").write_text("someone else's note", encoding="utf-8")
        with self.assertRaises(SystemExit):
            db.cmd_drive(self.args(copy=True, test="steps", by="PC session 3"))
        self.assertEqual((self.drive / "0.6.9" / f"{stem}.txt").read_text(encoding="utf-8"), "someone else's note")
        self.assertFalse((self.drive / "0.6.9" / f"{stem}.apk").exists())

    def test_a_dirty_build_needs_allow_dirty(self):
        self.keep_fake(dirty=True)
        with self.assertRaises(SystemExit):
            db.cmd_drive(self.args(copy=True, test="steps", by="PC session 3"))
        self.assertEqual(db.cmd_drive(self.args(copy=True, test="steps", by="PC session 3", allow_dirty=True)), 0)
        self.assertEqual(len(self.drive_files()), 2)


if __name__ == "__main__":
    unittest.main()
