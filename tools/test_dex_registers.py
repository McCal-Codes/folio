import importlib.util
import pathlib
import unittest

spec = importlib.util.spec_from_file_location("dex_registers", pathlib.Path(__file__).with_name("check-dex-registers.py"))
dr = importlib.util.module_from_spec(spec)
spec.loader.exec_module(dr)

# Trimmed from the 27 Oct 2026 fast build that died at launch: Home's content lambda, moved by R8 into
# kotlinx.coroutines.CoroutineContextKt, with an inlined callee and R8's own `:0` copy of the signature.
MAPPING = """\
# compiler: R8
kotlinx.coroutines.CoroutineContextKt -> q20:
    1:1:kotlin.Unit com.mccal.folio.LauncherScreenKt.LauncherScreen$lambda$173$8$0$7$5(boolean,int):780:780 -> p
    1:1:Unit CoroutineContextKt.LauncherScreen$lambda$173$8$0$7$5(boolean,int):0 -> p
      # {"id":"com.android.tools.r8.synthesized"}
    2:2:kotlin.Unit com.mccal.folio.LauncherScreenKt.LauncherScreen$lambda$173$8$0$7$5(boolean,int):783:783 -> p
    3:3:void com.mccal.folio.HomeKt.inlined(int):12:12 -> p
    3:3:kotlin.Unit com.mccal.folio.LauncherScreenKt.LauncherScreen$lambda$173$8$0$7$5(boolean,int):790:790 -> p
    4:4:java.lang.Object kotlinx.coroutines.CoroutineContextKt.other():5:5 -> q
com.mccal.folio.LauncherState -> ho1:
    1:9:void <init>(java.lang.String):20:28 -> <init>
"""

DEXDUMP = """\
Class #0            -
  Class descriptor  : 'Lq20;'
  Direct methods    -
    #0              : (in Lq20;)
      name          : 'p'
      type          : '(ZI)Ldq3;'
      code          -
      registers     : 258
      ins           : 98
    #1              : (in Lq20;)
      name          : 'q'
      type          : '()Ljava/lang/Object;'
      code          -
      registers     : 300
Class #1            -
  Class descriptor  : 'Lho1;'
  Direct methods    -
    #0              : (in Lho1;)
      name          : '<init>'
      code          -
      registers     : 286
"""


class DexRegistersTest(unittest.TestCase):
    def setUp(self):
        self.methods = dr.parse_dexdump(DEXDUMP)
        self.originals = dr.parse_mapping(MAPPING.splitlines(True), {(c, n) for c, n, _ in self.methods})

    def test_reads_registers_per_method(self):
        self.assertEqual(self.methods, [("q20", "p", 258), ("q20", "q", 300), ("ho1", "<init>", 286)])

    def test_names_the_method_itself_not_what_it_inlined(self):
        top = self.originals[("q20", "p")].most_common(1)[0][0]
        self.assertEqual(top, "com.mccal.folio.LauncherScreenKt.LauncherScreen$lambda$173$8$0$7$5")
        self.assertNotIn("CoroutineContextKt.LauncherScreen$lambda$173$8$0$7$5", self.originals[("q20", "p")])

    def test_unqualified_names_take_the_class(self):
        self.assertIn("com.mccal.folio.LauncherState.<init>", self.originals[("ho1", "<init>")])

    def test_home_lambda_over_the_limit_fails_known_and_library_methods_do_not(self):
        failures, warnings, tolerated = dr.judge(self.methods, self.originals)
        self.assertEqual([f[1] for f in failures], ["com.mccal.folio.LauncherScreenKt.LauncherScreen$lambda$173$8$0$7$5"])
        self.assertEqual([t[1] for t in tolerated], ["com.mccal.folio.LauncherState.<init>"])
        self.assertEqual(warnings, [])

    def test_close_to_the_limit_warns(self):
        failures, warnings, _ = dr.judge([("q20", "p", 253)], self.originals)
        self.assertEqual(failures, [])
        self.assertEqual(len(warnings), 1)


if __name__ == "__main__":
    unittest.main()
