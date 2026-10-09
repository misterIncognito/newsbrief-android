plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp") apply false
}

android {
    namespace = "io.newsbrief.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.newsbrief.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 16
        versionName = "0.4.12"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions { unitTests.isIncludeAndroidResources = true }
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

// Register generated Room sources only while the storage feature is present.
// This also prevents stale KSP output from leaking into a shell build after switching branches.
if (file("features/storage.gradle.kts").isFile) {
    apply(plugin = "com.google.devtools.ksp")
    extensions.configure<com.google.devtools.ksp.gradle.KspExtension> {
        arg("room.schemaLocation", "$projectDir/schemas")
    }
}

// Each branch restores its own dependency script along with implementation and tests.
listOf("networking", "storage", "sources-discovery", "reader-browser", "synchronization").forEach { feature ->
    val script = file("features/$feature.gradle.kts")
    if (script.isFile) apply(from = script)
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.08.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.11.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16.1")
    testImplementation("androidx.test:core:1.7.0")
}

// External-service checks are opt-in; regular unit tests remain deterministic/offline.
tasks.withType<Test>().configureEach {
    if (!providers.gradleProperty("verifyDirectoryServices").isPresent) {
        exclude("**/DirectoryServiceLiveTest*")
    }
    if (!providers.gradleProperty("verifyAmpReaders").isPresent) {
        exclude("**/AmpReaderLiveTest*")
    }
}
