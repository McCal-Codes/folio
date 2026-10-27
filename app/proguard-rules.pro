# DiscoverBounds resolves the public Window Extensions API by class and member name so it can
# retain the split-host fallback on devices whose extension implementation differs. R8 cannot
# infer these references from the strings used by Class.forName/getMethod/Proxy.
-keep class androidx.window.extensions.** { *; }

# Android manifest components and directly constructed widget-host classes are traced by AGP/R8.
# Layout and backup persistence use org.json with explicit keys, so there are no model classes
# that require broad reflection or serialization keep rules.

# The root hinge helper (ADR 0012) is only ever started by name, as `app_process ... com.mccal.folio.RootHingeHelper`, from the
# installed APK, so nothing in the app refers to it and R8 would remove it.
-keep class com.mccal.folio.RootHingeHelper { public static void main(java.lang.String[]); }

# Each of these returns a LauncherState.copy(...), and a copy passes every field (about 150 registers in one range). They
# have one caller, MainActivity's setContent lambda, and R8 inlined all three there; on 27 Oct 2026 the copies stopped
# sharing registers and that lambda needed 307 (over 256, where R8 has written broken moves; tools/check-dex-registers.py).
# Kept out of line, each copy stays in a method of its own. Renaming and removal stay allowed.
-keepclassmembers,allowobfuscation,allowshrinking class com.mccal.folio.FocusPages {
    com.mccal.folio.LauncherState effective(com.mccal.folio.LauncherState);
}
-keepclassmembers,allowobfuscation,allowshrinking class com.mccal.folio.SafeMode {
    com.mccal.folio.LauncherState effective(com.mccal.folio.LauncherState);
}
-keepclassmembers,allowobfuscation,allowshrinking class com.mccal.folio.LauncherModelKt {
    com.mccal.folio.LauncherState withSolidGlass(com.mccal.folio.LauncherState);
}
