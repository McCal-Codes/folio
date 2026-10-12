import importlib.util
import json
import pathlib
import shutil
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("folio_pkg", pathlib.Path(__file__).with_name("folio-pkg.py"))
pkg = importlib.util.module_from_spec(spec)
spec.loader.exec_module(pkg)

ROOT = pathlib.Path(__file__).resolve().parent.parent
SOURCE = ROOT / "docs/sdk/source"


def manifest(*features):
    return {"requires": {"features": list(features)}}


def page(fallback=None):
    block = {"type": "compatibility", "screens": ["cover", "inner"]}
    if fallback:
        block["fallback"] = fallback
    return {"blocks": [block]}


class FallbackTest(unittest.TestCase):
    """ADR 0013: above Standard, a package's page says what still works without the access it needs."""

    def check(self, man, dep, access=None):
        report = pkg.Report()
        pkg.check_fallback(man, dep, "x/manifest.json", report, access)
        return report

    def test_the_tier_is_the_most_any_feature_needs(self):
        self.assertEqual(pkg.package_access([], pkg.ACCESS), "standard")
        self.assertEqual(pkg.package_access(["theme", "wallpaper"], pkg.ACCESS), "standard")
        self.assertEqual(pkg.package_access(["theme", "tweaks.tintMedia"], pkg.ACCESS), "notifications")
        self.assertEqual(pkg.package_access(["tweaks.appPanels"], pkg.ACCESS), "standard")  # only better with it

    def test_above_standard_without_a_fallback_is_an_error(self):
        self.assertEqual(len(self.check(manifest("tweaks.notificationAppRow"), page()).errors), 1)
        self.assertEqual(len(self.check(manifest("tweaks.notificationAppRow"), None).errors), 1)
        self.assertEqual(len(self.check(manifest("tweaks.notificationAppRow"), {"blocks": [{"type": "markdown", "text": "hi"}]}).errors), 1)

    def test_a_fallback_or_standard_access_passes(self):
        self.assertEqual(self.check(manifest("tweaks.notificationAppRow"), page("The row doesn't appear.")).errors, [])
        self.assertEqual(self.check(manifest("tweaks.notificationAppRow"), page({"en": "The row doesn't appear."})).errors, [])
        self.assertEqual(self.check(manifest("theme"), page()).errors, [])
        self.assertEqual(self.check({}, None).errors, [])

    def test_an_old_copy_of_the_schemas_warns_instead_of_failing(self):
        report = pkg.Report()
        saved, pkg.ACCESS = pkg.ACCESS, None
        try:
            pkg.check_fallback(manifest("tweaks.tintMedia"), None, "x/manifest.json", report)
        finally:
            pkg.ACCESS = saved
        self.assertEqual(report.errors, [])
        self.assertEqual(len(report.warnings), 1)

    def test_the_table_covers_exactly_the_features_the_schema_allows(self):
        schema = json.loads((ROOT / "docs/sdk/schema/v1/manifest.schema.json").read_text(encoding="utf-8"))
        allowed = schema["properties"]["requires"]["properties"]["features"]["items"]["enum"]
        self.assertEqual(sorted(pkg.ACCESS["capabilities"]), sorted(allowed))
        for feature, row in pkg.ACCESS["capabilities"].items():
            self.assertIn(row["needs"], pkg.ACCESS["tiers"], feature)
            if "betterWith" in row:
                self.assertGreater(pkg.ACCESS["tiers"].index(row["betterWith"]), pkg.ACCESS["tiers"].index(row["needs"]), feature)

    def test_folios_own_source_needs_its_fallbacks(self):
        with tempfile.TemporaryDirectory() as tmp:
            copy = pathlib.Path(tmp) / "source"
            shutil.copytree(SOURCE, copy)
            schemas = pkg.SchemaSet(pkg.SCHEMA_DIR)
            report = pkg.Report()
            pkg.check_package(copy / "packages/roll-call", copy, schemas, report)
            self.assertEqual(report.errors, [])
            dep_path = copy / "packages/roll-call/depiction.json"
            dep = json.loads(dep_path.read_text(encoding="utf-8"))
            for block in dep["blocks"]:
                block.pop("fallback", None)
            dep_path.write_text(json.dumps(dep), encoding="utf-8")
            report = pkg.Report()
            pkg.check_package(copy / "packages/roll-call", copy, schemas, report)
            self.assertEqual(len(report.errors), 1)
            self.assertIn("needs notifications access", report.errors[0])


if __name__ == "__main__":
    unittest.main()
