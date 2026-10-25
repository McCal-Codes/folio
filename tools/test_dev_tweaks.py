import importlib.util
import json
import pathlib
import shutil
import subprocess
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("dev_tweaks", pathlib.Path(__file__).with_name("dev-tweaks.py"))
dt = importlib.util.module_from_spec(spec)
spec.loader.exec_module(dt)

REAL = pathlib.Path("~/dev/folio-tweaks").expanduser()


def git(repo, *args):
    subprocess.run(["git", "-c", "user.name=t", "-c", "user.email=t@example.invalid", "-C", str(repo), *args], check=True, capture_output=True)


class DevTweaksTest(unittest.TestCase):
    def test_ids_and_versions_are_distinct_and_stable(self):
        self.assertEqual(dt.wip_id("com.mccal.folio.duet"), "com.mccal.folio.duet.wip")
        self.assertEqual(dt.wip_id("com.mccal.folio.duet.wip"), "com.mccal.folio.duet.wip")
        self.assertEqual(dt.wip_version("1.0.0", 7), "1.0.0-wip.7")
        self.assertEqual(dt.wip_version("1.0.0-wip.3", 7), "1.0.0-wip.7")

    @unittest.skipUnless((REAL / "tools/build.py").is_file(), "needs the folio-tweaks checkout")
    def test_a_branch_becomes_a_signed_wip_source(self):
        with tempfile.TemporaryDirectory() as tmp:
            tmp = pathlib.Path(tmp)
            repo = tmp / "tweaks"
            repo.mkdir()
            for name in ("tools", "schema", "assets", "packages", "source.json"):
                src = REAL / name
                if src.is_dir():
                    shutil.copytree(src, repo / name, ignore=shutil.ignore_patterns("_site", ".git"))
                else:
                    shutil.copyfile(src, repo / name)
            git(repo, "init", "-q", "-b", "main")
            git(repo, "add", "-A")
            git(repo, "commit", "-q", "-m", "base")
            # The branch tries to run code with the publisher key in reach: its own build.py writes a marker.
            git(repo, "checkout", "-q", "-b", "duet")
            marker = tmp / "PWNED"
            build = repo / "tools/build.py"
            build.write_text("import pathlib\npathlib.Path(%r).write_text('x')\n" % str(marker), encoding="utf-8")
            git(repo, "commit", "-q", "-am", "evil tools")
            key = tmp / "throwaway.pem"
            subprocess.run([dt.dp.openssl(), "ecparam", "-name", "prime256v1", "-genkey", "-noout", "-out", str(key)], check=True, capture_output=True)
            out = tmp / "out"
            self.assertEqual(dt.main(["--branch", "duet", "--checkout", str(repo), "--to-folder", str(out), "--key", str(key)]), 0)
            site = out / "tweaks"
            index = json.loads((site / "index.json").read_text(encoding="utf-8"))
            self.assertEqual(index["name"], "Folio Dev Tweaks")
            self.assertTrue(index["packages"])
            for package in index["packages"]:
                self.assertTrue(package["id"].endswith(".wip"), package["id"])
                self.assertIn("-wip.", package["version"])
            self.assertTrue((site / "entry.json.sig").is_file())
            self.assertTrue((site / "key.pub").is_file())
            self.assertFalse((repo / "_site").exists(), "the checkout is untouched")
            self.assertFalse(marker.exists(), "the branch's own tools never ran")

    def test_ref_names_cannot_be_options_or_climb(self):
        for bad in ("--output=x", "-x", "a..b", "a b", "", "$(x)"):
            with self.assertRaises(SystemExit):
                dt.check_ref(bad)
        self.assertEqual(dt.check_ref("duet/x-1.2"), "duet/x-1.2")

    @unittest.skipUnless((REAL / "tools/build.py").is_file(), "needs the folio-tweaks checkout")
    def test_dry_run_writes_nothing(self):
        with tempfile.TemporaryDirectory() as tmp:
            tmp = pathlib.Path(tmp)
            key = tmp / "k.pem"
            subprocess.run([dt.dp.openssl(), "ecparam", "-name", "prime256v1", "-genkey", "-noout", "-out", str(key)], check=True, capture_output=True)
            out = tmp / "out"
            self.assertEqual(dt.main(["--branch", "main", "--checkout", str(REAL), "--to-folder", str(out), "--key", str(key), "--dry-run"]), 0)
            self.assertFalse(out.exists())


if __name__ == "__main__":
    unittest.main()
