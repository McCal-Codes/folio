plugins {
    id("com.android.library")
}

// The Folio Market: package format, sources, installer and (later) its screens. See docs/sdk/.
android {
    namespace = "com.mccal.folio.market"
    compileSdk = 36
    defaultConfig { minSdk = 31 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    // JUnit 5 runs the Jazzer fuzz tests; the vintage engine keeps the JUnit 4 tests running.
    // Fuzzing mode: JAZZER_FUZZ=1 ./gradlew :market:testDebugUnitTest --tests '*ParserFuzzTest.manifest' (see docs/sdk/README.md).
    testOptions.unitTests.all {
        it.useJUnitPlatform()
        System.getenv("JAZZER_FUZZ")?.let { value -> it.environment("JAZZER_FUZZ", value) }
        // The tests read the published source, schemas and examples straight from docs/sdk. Without declaring them,
        // Gradle reused a cached pass when only those files changed, and CI stayed green over failing counts.
        it.inputs.dir(rootProject.file("docs/sdk")).withPathSensitivity(org.gradle.api.tasks.PathSensitivity.RELATIVE)
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20260814")
    // JUnit 5, not 6: jazzer-junit 0.30.0 is built against the JUnit 5 platform.
    testImplementation(platform("org.junit:junit-bom:5.14.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("com.code-intelligence:jazzer-junit:0.30.0")
    // Checks the parsers against the published JSON Schemas (draft 2020-12). 1.5.x on purpose: 3.x changes both the API
    // and its Jackson version, and this only ever runs in tests.
    testImplementation("com.networknt:json-schema-validator:1.5.9")
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}
