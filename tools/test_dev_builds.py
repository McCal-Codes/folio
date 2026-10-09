import importlib.util
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


if __name__ == "__main__":
    unittest.main()
