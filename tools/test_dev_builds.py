import importlib.util
import pathlib
import re
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


if __name__ == "__main__":
    unittest.main()
