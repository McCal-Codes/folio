import importlib.util
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location("check_translations", Path(__file__).with_name("check-translations.py"))
ct = importlib.util.module_from_spec(spec)
spec.loader.exec_module(ct)


class NamesIn(unittest.TestCase):
    def test_reads_strings_plurals_and_arrays(self):
        xml = '<string name="a">x</string><plurals name="b"><item/></plurals><string-array name="c"/>'
        self.assertEqual(ct.names_in(xml), {"a", "b", "c"})

    def test_skips_translatable_false(self):
        self.assertEqual(ct.names_in('<string name="id" translatable="false">x</string><string name="ok">y</string>'), {"ok"})

    def test_ignores_attributes_that_are_not_names(self):
        self.assertEqual(ct.names_in('<string formatted="false" name="n">x</string>'), {"n"})


class Missing(unittest.TestCase):
    def test_reports_only_the_locales_with_a_gap(self):
        have = {"values-b+ko": {"a", "b"}, "values-b+zh+Hans": {"a"}}
        self.assertEqual(ct.missing_per_locale({"a", "b"}, have), {"values-b+zh+Hans": ["b"]})

    def test_nothing_added_means_nothing_missing(self):
        self.assertEqual(ct.missing_per_locale(set(), {"values-b+ko": set()}), {})


class LocaleFolders(unittest.TestCase):
    def test_language_folders_match_and_qualifiers_do_not(self):
        for ok in ("values-ko", "values-pt-rBR", "values-b+ko", "values-b+zh+Hans"):
            self.assertTrue(ct.LOCALE_DIR.match(ok), ok)
        for no in ("values", "values-night", "values-v31", "values-sw600dp", "values-land", "values-xhdpi"):
            self.assertFalse(ct.LOCALE_DIR.match(no), no)


if __name__ == "__main__":
    unittest.main()
