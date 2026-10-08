plugins {
    id("com.android.test")
    id("androidx.baselineprofile")
}

android {
    namespace = "com.mccal.folio.baselineprofile"
    compileSdk = 36

    defaultConfig {
        // Baseline profiles are generated on a connected device running API 33 or newer; a rooted
        // device isn't needed there, which is why this doesn't have to match the app's minSdk.
        minSdk = 33
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    targetProjectPath = ":app"
}

baselineProfile {
    // Run against whatever phone is plugged in rather than a managed emulator: Folio's startup is
    // worth measuring on a fold, and a Gradle-managed device can't unfold.
    useConnectedDevices = true
}

dependencies {
    implementation("androidx.test.ext:junit:1.2.1")
    implementation("androidx.test.uiautomator:uiautomator:2.4.0")
    implementation("androidx.benchmark:benchmark-macro-junit4:1.5.0")
}
