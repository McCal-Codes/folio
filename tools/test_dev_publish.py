import base64
import importlib.util
import json
import pathlib
import subprocess
import tempfile
import unittest
import zipfile

spec = importlib.util.spec_from_file_location("dev_publish", pathlib.Path(__file__).with_name("dev-publish.py"))
dp = importlib.util.module_from_spec(spec)
spec.loader.exec_module(dp)


def make_key(folder: pathlib.Path) -> pathlib.Path:
    key = folder / "throwaway.pem"
    subprocess.run([dp.openssl(), "ecparam", "-name", "prime256v1", "-genkey", "-noout", "-out", str(key)], check=True, capture_output=True)
    return key


def make_apk(folder: pathlib.Path, sha="abcdef0123456789", branch="duet/x", built="2026-10-24T10:00:00.1234567-04:00") -> pathlib.Path:
    apk = folder / "app.apk"
    info = {"branch": branch, "sha": sha, "builtAt": built, "dirty": False, "commits": [{"sha": sha, "subject": "Try a thing"}], "notes": []}
    with zipfile.ZipFile(apk, "w") as z:
        z.writestr("assets/dev-build.json", json.dumps(info))
        z.writestr("classes.dex", b"dex" * 1000)
    return apk


def verifies(key: pathlib.Path, data: bytes, signature_b64: str, folder: pathlib.Path) -> bool:
    pub = folder / "pub.pem"
    subprocess.run([dp.openssl(), "ec", "-in", str(key), "-pubout", "-out", str(pub)], check=True, capture_output=True)
    (folder / "data").write_bytes(data)
    (folder / "sig").write_bytes(base64.b64decode(signature_b64))
    result = subprocess.run([dp.openssl(), "dgst", "-sha256", "-verify", str(pub), "-signature", str(folder / "sig"), str(folder / "data")], capture_output=True)
    return result.returncode == 0


class DevPublishTest(unittest.TestCase):
    def setUp(self):
        self._tmp = tempfile.TemporaryDirectory()
        self.tmp = pathlib.Path(self._tmp.name)
        self.key = make_key(self.tmp)
        self.apk = make_apk(self.tmp)

    def tearDown(self):
        self._tmp.cleanup()

    def test_version_code_matches_the_gradle_formula(self):
        self.assertEqual(dp.version_code("0.6.9"), 6090)
        self.assertEqual(dp.version_code("0.6.9-beta.5"), 6090)
        self.assertEqual(dp.version_code("0.6.7.1"), 6071)

    def test_built_at_is_normalized_to_utc(self):
        self.assertEqual(dp.utc_iso("2026-10-24T10:00:00.1234567-04:00"), "2026-10-24T14:00:00Z")
        self.assertEqual(dp.utc_iso("2026-10-24T14:00:00Z"), "2026-10-24T14:00:00Z")

    def test_row_has_everything_the_app_reads(self):
        row = dp.make_row(self.apk, "0.6.9-beta.5")
        for field in ("name", "tag", "branch", "sha", "builtAt", "versionCode", "versionName", "size", "sha256", "subject", "buildType"):
            self.assertIn(field, row)
        self.assertEqual(row["tag"], "dev-202610241400-duet-x-abcdef0")
        self.assertEqual(row["size"], self.apk.stat().st_size)
        self.assertRegex(row["sha256"], r"^[0-9a-f]{64}$")

    def test_to_folder_writes_the_served_layout_and_a_valid_signature(self):
        out = self.tmp / "out"
        self.assertEqual(dp.main(["--apk", str(self.apk), "--to-folder", str(out), "--key", str(self.key)]), 0)
        data = (out / "builds.json").read_bytes()
        self.assertTrue(verifies(self.key, data, (out / "builds.json.sig").read_text(), self.tmp))
        listing = json.loads(data)
        self.assertEqual(listing["format"], 1)
        self.assertEqual(listing["maxAge"], 30 * 86400)
        row = listing["builds"][0]
        release = out / "releases" / row["tag"]
        apk = release / f"FolioDev-{row['name']}.apk"
        self.assertTrue(apk.is_file())
        self.assertEqual((release / "SHA256SUMS.txt").read_text(), f"{row['sha256']}  {apk.name}\n")
        self.assertNotIn(b"\r\n", data)

    def test_a_tampered_list_does_not_verify(self):
        out = self.tmp / "out"
        dp.main(["--apk", str(self.apk), "--to-folder", str(out), "--key", str(self.key)])
        data = (out / "builds.json").read_bytes().replace(b"Try a thing", b"Try a thang")
        self.assertFalse(verifies(self.key, data, (out / "builds.json.sig").read_text(), self.tmp))

    def test_a_second_publish_keeps_the_first_and_prunes_to_fifteen(self):
        out = self.tmp / "out"
        for n in range(17):
            apk = make_apk(self.tmp, sha=f"{n:08x}abcdef", built=f"2026-10-{(n % 9) + 10}T10:{n:02d}:00Z")
            dp.main(["--apk", str(apk), "--to-folder", str(out), "--key", str(self.key)])
        rows = json.loads((out / "builds.json").read_text())["builds"]
        self.assertEqual(len(rows), 15)
        self.assertEqual(len(list((out / "releases").iterdir())), 15)

    def test_dry_run_writes_nothing(self):
        out = self.tmp / "out"
        self.assertEqual(dp.main(["--apk", str(self.apk), "--to-folder", str(out), "--key", str(self.key), "--dry-run"]), 0)
        self.assertFalse(out.exists())

    def test_the_community_key_is_refused_by_id(self):
        original = dp.COMMUNITY_KEY_ID
        try:
            dp.COMMUNITY_KEY_ID = dp.key_id(self.key)
            with self.assertRaises(SystemExit):
                dp.check_key(self.key)
        finally:
            dp.COMMUNITY_KEY_ID = original

    def test_a_fresh_folder_keeps_the_history_the_repository_already_has(self):
        seed = []
        for n in range(3):
            folder = self.tmp / f"seed{n}"
            folder.mkdir()
            seed.append(dp.make_row(make_apk(folder, sha=f"{n:08x}aa", built=f"2026-09-0{n + 1}T10:00:00Z"), "0.6.9"))
        out = self.tmp / "fresh"
        dp.write_folder(out, self.apk, dp.make_row(self.apk, "0.6.9"), self.key, 1_800_000_000, 30, seed)
        tags = {r["tag"] for r in json.loads((out / "builds.json").read_text())["builds"]}
        self.assertTrue({r["tag"] for r in seed} <= tags)
        self.assertEqual(len(tags), 4)

    def test_only_releases_the_signed_list_dropped_are_deleted(self):
        listed = {"dev-1", "dev-2"}
        self.assertEqual(dp.stale_tags(["dev-1", "dev-2", "dev-0", "v1.0", "notes"], listed), ["dev-0"])
        self.assertEqual(dp.stale_tags(["dev-1"], listed), [])

    def test_upload_asks_before_deleting_and_yes_skips_the_question(self):
        calls = []

        def fake_gh(*args, check=True):
            calls.append(args)
            out = ""
            if args[0] == "repo":
                out = json.dumps({"visibility": "PRIVATE"})
            elif args[:2] == ("release", "list"):
                out = json.dumps([{"tagName": "dev-old"}, {"tagName": "dev-keep"}])
            elif args[0] == "api":
                return subprocess.CompletedProcess(args, 1, "", "404")
            return subprocess.CompletedProcess(args, 0, out, "")

        folder = self.tmp / "o"
        row = dp.make_row(self.apk, "0.6.9")
        row["tag"] = "dev-keep"
        dp.write_folder(folder, self.apk, row, self.key, 1_800_000_000, 30)
        original = dp.gh
        dp.gh = fake_gh
        try:
            dp.upload(folder, row, yes=False)
            self.assertFalse([c for c in calls if c[:2] == ("release", "delete")], "not a terminal, so it left them")
            dp.upload(folder, row, yes=True)
            self.assertEqual([c[2] for c in calls if c[:2] == ("release", "delete")], ["dev-old"])
        finally:
            dp.gh = original

    def test_nothing_is_uploaded_without_the_flag(self):
        called = []
        original = dp.upload
        dp.upload = lambda *a: called.append(a)
        try:
            dp.main(["--apk", str(self.apk), "--to-folder", str(self.tmp / "o"), "--key", str(self.key)])
        finally:
            dp.upload = original
        self.assertEqual(called, [])


if __name__ == "__main__":
    unittest.main()
