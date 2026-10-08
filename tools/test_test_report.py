"""Tests for tools/test-report.py. Run: python3 -m unittest discover -s tools -p 'test_*.py'"""
import importlib.util
import json
import tempfile
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location("test_report", Path(__file__).with_name("test-report.py"))
test_report = importlib.util.module_from_spec(spec)
spec.loader.exec_module(test_report)


def write_suite(directory: Path, name: str, tests: int, failures: int = 0, errors: int = 0, skipped: int = 0) -> None:
    directory.mkdir(parents=True, exist_ok=True)
    (directory / f"TEST-{name}.xml").write_text(
        f'<?xml version="1.0"?><testsuite name="{name}" tests="{tests}" skipped="{skipped}" '
        f'failures="{failures}" errors="{errors}"></testsuite>'
    )


class TestReport(unittest.TestCase):
    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp())

    def test_counts_every_suite_in_every_module(self):
        write_suite(self.tmp / "app", "A", 10)
        write_suite(self.tmp / "app" / "nested", "B", 5, skipped=1)
        write_suite(self.tmp / "market", "C", 3)
        report = test_report.build("0.6.8", "abc", {"app": self.tmp / "app", "market": self.tmp / "market"})
        self.assertEqual(report["modules"]["app"]["tests"], 15)
        self.assertEqual(report["modules"]["app"]["suites"], 2)
        self.assertEqual(report["totals"]["tests"], 18)
        self.assertEqual(report["totals"]["passed"], 17)
        self.assertEqual(report["totals"]["skipped"], 1)

    def test_failures_and_errors_are_counted_not_passed(self):
        write_suite(self.tmp / "app", "A", 10, failures=2, errors=1)
        total = test_report.build("0.6.8", "abc", {"app": self.tmp / "app"})["totals"]
        self.assertEqual((total["failures"], total["errors"], total["passed"]), (2, 1, 7))

    def test_a_directory_with_no_results_is_an_error_not_zero(self):
        (self.tmp / "app").mkdir()
        with self.assertRaises(SystemExit):
            test_report.build("0.6.8", "abc", {"app": self.tmp / "app"})

    def test_exit_code_is_nonzero_when_a_test_failed_and_the_file_is_still_written(self):
        write_suite(self.tmp / "app", "A", 4, failures=1)
        out = self.tmp / "report.json"
        code = test_report.main(["--version", "0.6.8", "--commit", "abc", "--module", f"app={self.tmp / 'app'}", "--out", str(out)])
        self.assertEqual(code, 1)
        self.assertEqual(json.loads(out.read_text())["totals"]["failures"], 1)

    def test_clean_run_exits_zero_and_report_is_stable(self):
        write_suite(self.tmp / "app", "A", 4)
        args = ["--version", "0.6.8", "--commit", "abc", "--module", f"app={self.tmp / 'app'}", "--out", str(self.tmp / "r.json")]
        self.assertEqual(test_report.main(args), 0)
        first = (self.tmp / "r.json").read_text()
        test_report.main(args)
        self.assertEqual(first, (self.tmp / "r.json").read_text())


if __name__ == "__main__":
    unittest.main()
