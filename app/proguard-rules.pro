# DiscoverBounds resolves the public Window Extensions API by class and member name so it can
# retain the split-host fallback on devices whose extension implementation differs. R8 cannot
# infer these references from the strings used by Class.forName/getMethod/Proxy.
-keep class androidx.window.extensions.** { *; }

# Android manifest components and directly constructed widget-host classes are traced by AGP/R8.
# Layout and backup persistence use org.json with explicit keys, so there are no model classes
# that require broad reflection or serialization keep rules.

# The root hinge helper (ADR 0010) is only ever started by name, as `app_process ... com.mccal.folio.RootHingeHelper`, from the
# installed APK, so nothing in the app refers to it and R8 would remove it.
-keep class com.mccal.folio.RootHingeHelper { public static void main(java.lang.String[]); }
