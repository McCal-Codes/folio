plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("androidx.baselineprofile")
}

val releaseSigningVariables = listOf(
    "FOLIO_RELEASE_STORE_FILE",
    "FOLIO_RELEASE_STORE_PASSWORD",
    "FOLIO_RELEASE_KEY_ALIAS",
    "FOLIO_RELEASE_KEY_PASSWORD",
)
val releaseSigningValues = releaseSigningVariables.associateWith { name ->
    System.getenv(name)?.takeIf { it.isNotBlank() }
}
val suppliedReleaseSigningVariables = releaseSigningValues.filterValues { it != null }.keys
check(suppliedReleaseSigningVariables.isEmpty() || suppliedReleaseSigningVariables.size == releaseSigningVariables.size) {
    val missing = releaseSigningVariables.filterNot(suppliedReleaseSigningVariables::contains)
    "Release signing is only configured when all four FOLIO_RELEASE_* variables are set. Missing: ${missing.joinToString()}"
}

val releaseStoreFile = releaseSigningValues["FOLIO_RELEASE_STORE_FILE"]?.let { configuredPath ->
    rootProject.file(configuredPath).canonicalFile.also { storeFile ->
        val repositoryRoot = rootProject.projectDir.canonicalFile.toPath()
        check(!storeFile.toPath().startsWith(repositoryRoot)) {
            "FOLIO_RELEASE_STORE_FILE must point outside the repository."
        }
        check(storeFile.isFile && storeFile.canRead()) {
            "FOLIO_RELEASE_STORE_FILE does not point to a readable file."
        }
    }
}

val folioVersion = "0.6.6"

// Bundle the changelog so Folio can show What's New after an update.
val bundleChangelog = tasks.register<Copy>("bundleChangelog") {
    from(rootProject.file("CHANGELOG.md"))
    into(layout.buildDirectory.dir("generated/changelog"))
}
tasks.named("preBuild") { dependsOn(bundleChangelog) }

// Bundle Folio's own source (docs/sdk/source) so the built-in themes and tweaks are real packages, read from the same
// files the SDK documents and the tests check. One copy, not two.
// Sync, not Copy: a file removed from the source (or newly excluded) has to leave the APK as well.
val bundleFolioSource = tasks.register<Sync>("bundleFolioSource") {
    from(rootProject.file("docs/sdk/source")) {
        // The drawings and the script that rasterises them are build sources, not something the phone reads.
        exclude("README.md", "**/*.svg", "**/generate.py")
    }
    into(layout.buildDirectory.dir("generated/market/market/source"))
}
// Write the list of files beside them: an APK's assets can't be listed reliably (and Robolectric can't at all), so the
// source says what it contains instead of Folio guessing from folder names.
val indexFolioSource = tasks.register("indexFolioSource") {
    dependsOn(bundleFolioSource)
    val sourceDir = layout.buildDirectory.dir("generated/market/market/source")
    // Without this the task is "up to date" after a file is added to the source, and files.json quietly stops
    // listing everything that's actually there.
    inputs.dir(rootProject.file("docs/sdk/source")).withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.dir(sourceDir)
    doLast {
        val root = sourceDir.get().asFile
        val paths = root.walkTopDown().filter { it.isFile && it.name != "files.json" }
            .map { it.relativeTo(root).invariantSeparatorsPath }.sorted().toList()
        File(root, "files.json").writeText(paths.joinToString(",", "[", "]") { "\"" + it + "\"" })
    }
}
tasks.named("preBuild") { dependsOn(indexFolioSource) }
/**
 * The profile is recorded from the "fast" build ("Folio Dev", debug-signed) because the release APK can't be
 * installed over the signed Folio on a test phone, and merged into main so the release build ships it.
 */
baselineProfile {
    mergeIntoMain = true
}

/**
 * The two build types the baseline profile plugin adds get their own application id so they install beside the
 * real Folio instead of trying to replace it: the phone used for recording runs a release-signed Folio, which a
 * locally signed build of the same id can't update. A profile is a list of classes and methods, so recording it
 * under another id changes nothing about what ends up in the release APK.
 */
androidComponents {
    onVariants { variant ->
        if (variant.buildType == "nonMinifiedRelease" || variant.buildType == "benchmarkRelease") {
            variant.applicationId.set("com.mccal.folio.profile")
        }
    }
}

android {
    namespace = "com.mccal.folio"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.mccal.folio"
        minSdk = 31
        targetSdk = 36
        // Semantic version; see CHANGELOG.md. versionCode = MAJOR * 10000 + MINOR * 100 + PATCH, so a beta
        // (0.7.0-beta.1) shares its release's code and the release installs over it.
        versionName = folioVersion
        versionCode = folioVersion.substringBefore('-').split('.').let { (major, minor, patch) -> major.toInt() * 10000 + minor.toInt() * 100 + patch.toInt() }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = releaseSigningValues.getValue("FOLIO_RELEASE_STORE_PASSWORD")
                keyAlias = releaseSigningValues.getValue("FOLIO_RELEASE_KEY_ALIAS")
                keyPassword = releaseSigningValues.getValue("FOLIO_RELEASE_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (releaseStoreFile != null) signingConfig = signingConfigs.getByName("release")
            manifestPlaceholders["appLabel"] = "Folio"
        }
        // Optimized like release (R8, no debuggable JIT slowdown) but signed with the local debug key, so it
        // installs over a debug build and keeps Folio's data. Use this to judge real smoothness on the phone.
        create("fast") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
            // Its own app ("Folio Dev") so test builds install next to the signed release instead of over it.
            applicationIdSuffix = ".dev"
            manifestPlaceholders["appLabel"] = "Folio Dev"
        }
        getByName("debug") {
            applicationIdSuffix = ".dev"
            manifestPlaceholders["appLabel"] = "Folio Dev"
        }
    }
    // "fast" uses release's no-op tracing/diagnostic sources.
    sourceSets {
        getByName("fast") { kotlin.directories.add("src/release/java"); res.directories.add("src/dev/res") }
        // Folio Dev (debug and fast builds) gets an amber icon so it's easy to tell apart from the release.
        getByName("debug") { res.directories.add("src/dev/res") }
        getByName("main") {
            assets.srcDir(layout.buildDirectory.dir("generated/changelog").get().asFile)
            assets.srcDir(layout.buildDirectory.dir("generated/market").get().asFile)
        }
    }
    buildFeatures { compose = true }
    // Robolectric needs the app's resources and manifest in unit tests.
    testOptions.unitTests.isIncludeAndroidResources = true
    // Android 13's per-app language picker: AGP builds locales_config.xml from the values-* folders a translation adds.
    androidResources { generateLocaleConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
dependencies {
    implementation(project(":market"))
    baselineProfile(project(":baselineprofile"))
    implementation("androidx.window:window:1.5.1")
    // Installs the baseline profiles that Compose and AndroidX ship, so hot paths are compiled ahead of time.
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    implementation(platform("androidx.compose:compose-bom:2025.06.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    // Images from sources the user added (Apache-2.0). Only coil3 core and the Compose binding: the bytes come from
    // Folio's own HTTPS client, so there is no second network stack in the APK and no second set of rules.
    implementation("io.coil-kt.coil3:coil-compose:3.6.3")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20260814") // real org.json for StatusStyle round-trip tests
    // Renders Compose on the JVM, so a screen can be checked without a phone attached.
    testImplementation("org.robolectric:robolectric:4.17")
    // runTest, so the Market's network calls can be tested without a real dispatcher.
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation(platform("androidx.compose:compose-bom:2025.06.01"))
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.06.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}
