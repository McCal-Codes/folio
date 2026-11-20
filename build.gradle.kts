plugins {
    id("com.android.application") version "9.5.1" apply false
    id("com.android.library") version "9.5.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
    id("androidx.baselineprofile") version "1.5.0" apply false
}

// Connected tests run on every attached device and then uninstall the app under test, which deletes its saved data. That
// wiped a personal phone's Folio Dev layout on 20 Nov 2026, so they refuse to start unless ANDROID_SERIAL names one device,
// and that device must be an emulator. Pass -Pfolio.allowPhone=true only for a phone kept for testing.
subprojects {
    val serial = providers.environmentVariable("ANDROID_SERIAL").orElse("")
    val allowPhone = providers.gradleProperty("folio.allowPhone").orElse("false")
    tasks.configureEach {
        if (name.startsWith("connected") && name.endsWith("AndroidTest")) {
            doFirst {
                val device = serial.get().trim()
                if (device.isEmpty()) throw GradleException(
                    "$path would run on every attached device and then uninstall the app from each, deleting its saved data.\n" +
                        "Set ANDROID_SERIAL to one emulator first (adb devices), for example ANDROID_SERIAL=emulator-5554. See CONTRIBUTING.md."
                )
                if (!device.startsWith("emulator-") && allowPhone.get() != "true") throw GradleException(
                    "ANDROID_SERIAL=$device is not an emulator, and $path uninstalls the app from it, deleting its saved data.\n" +
                        "Use an emulator, or pass -Pfolio.allowPhone=true for a phone kept only for testing. See CONTRIBUTING.md."
                )
            }
        }
    }
}
