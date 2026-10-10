plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
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
    packaging { resources.merges += "META-INF/services/io.newsbrief.app.runtime.AppFeature" }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
@Suppress("UNCHECKED_CAST")
val features = gradle.extra["newsbriefFeatures"] as List<String>
@Suppress("UNCHECKED_CAST")
val testModules = gradle.extra["newsbriefTestModules"] as List<String>
dependencies {
    implementation(project(":core:runtime"))
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    features.forEach { implementation(project(":features:$it")) }
    testImplementation(project(":core:testing"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16.1")
    testImplementation("androidx.test:core:1.7.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    if ("app-integration" in testModules) {
        androidTestImplementation(platform("androidx.compose:compose-bom:2025.08.01"))
        androidTestImplementation("androidx.compose.ui:ui-test-junit4")
        androidTestImplementation("androidx.test.ext:junit:1.3.0")
    }
}
if ("app-integration" in testModules) {
    android.sourceSets.getByName("androidTest").java.srcDir(rootProject.file("features/app-integration/device-tests"))
}
// Preserve the documented test command while running tests owned by every included library.
tasks.matching { it.name == "testDebugUnitTest" }.configureEach {
    dependsOn(listOf(":core:api:testDebugUnitTest", ":core:runtime:testDebugUnitTest") +
        (features + testModules).map { ":features:$it:testDebugUnitTest" })
}

tasks.withType<Test>().configureEach {
    systemProperty("newsbrief.expectedFeatures", features.joinToString(","))
}
