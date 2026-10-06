
# The root hinge helper (ADR 0010) is only ever started by name, as `app_process ... com.mccal.folio.RootHingeHelper`, from the
# installed APK, so nothing in the app refers to it and R8 would remove it.
-keep class com.mccal.folio.RootHingeHelper { public static void main(java.lang.String[]); }
