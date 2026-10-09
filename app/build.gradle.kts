import groovy.json.JsonSlurper

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

// Select any available features. Prerequisites are included automatically; all others
// are excluded from main/test compilation, dependency scripts and generated registration.
@Suppress("UNCHECKED_CAST")
val featurePlan = (JsonSlurper().parse(rootProject.file("tools/features.json")) as Map<String, Any>)["features"] as Map<String, Map<String, Any>>
val availableFeatures = featurePlan.keys.filter { file("features/$it.gradle.kts").isFile }.toSet()
val requestedFeatures = providers.gradleProperty("newsbriefFeatures").orNull
val enabledFeatures = (requestedFeatures?.split(',')?.map { it.trim() }?.filter { it.isNotBlank() }
    ?: availableFeatures.filter { it != "app-integration" }).toMutableSet()
require(enabledFeatures.all { it in availableFeatures && it != "app-integration" }) { "Unknown or unavailable feature selection: $enabledFeatures" }
var expanded: Boolean
do {
    val before = enabledFeatures.size
    enabledFeatures.toList().forEach { feature ->
        val required = featurePlan.getValue(feature)["requires"] as List<String>
        require(required.all { it in availableFeatures }) { "$feature needs unavailable features $required" }
        enabledFeatures.addAll(required)
    }
    expanded = before != enabledFeatures.size
} while (expanded)
val integrationTests = "app-integration" in availableFeatures &&
    (featurePlan.getValue("app-integration")["testRequires"] as List<String>).all { it in enabledFeatures }
val excludedFeatureSources = mutableMapOf<String, MutableList<String>>()
featurePlan.forEach { (feature, spec) ->
    if (feature !in enabledFeatures && !(feature == "app-integration" && integrationTests)) {
        (spec["files"] as List<String>).filter { it.endsWith(".kt") }.forEach { path ->
            val set = when {
                "/src/main/" in path -> "main"
                "/src/androidTest/" in path -> "androidTest"
                else -> "test"
            }
            excludedFeatureSources.getOrPut(set) { mutableListOf() }.add(path.substringAfter("/java/"))
        }
    }
}
// Android/Kotlin source providers do not consistently honor Java excludes for .kt files.
// Stage an explicit selected source tree so inactive features cannot reach either compiler.
val selectedSourceDir = layout.buildDirectory.dir("generated/selected-feature-sources")
val stageFeatureSources by tasks.registering(Sync::class) {
    into(selectedSourceDir)
    listOf("main", "test", "androidTest").forEach { set ->
        from("src/$set/java") {
            into(set)
            exclude(excludedFeatureSources[set].orEmpty())
        }
    }
}
listOf("main", "test", "androidTest").forEach { set ->
    val directory = selectedSourceDir.map { it.dir(set) }
    android.sourceSets.getByName(set).java.setSrcDirs(listOf(directory))
    kotlin.sourceSets.getByName(set).kotlin.setSrcDirs(listOf(directory))
}
if ("storage" in enabledFeatures) {
    apply(plugin = "com.google.devtools.ksp")
    extensions.configure<com.google.devtools.ksp.gradle.KspExtension> { arg("room.schemaLocation", "$projectDir/schemas") }
}
(enabledFeatures + if (integrationTests) setOf("app-integration") else emptySet()).forEach { feature ->
    apply(from = file("features/$feature.gradle.kts"))
}
val registrationDir = layout.buildDirectory.dir("generated/feature-registration")
val generateFeatureRegistration by tasks.registering {
    inputs.property("features", enabledFeatures.sorted())
    inputs.property("bindings", enabledFeatures.sorted().map { featurePlan.getValue(it)["binding"].toString() })
    outputs.dir(registrationDir)
    doLast {
        val target = registrationDir.get().file("io/newsbrief/app/runtime/GeneratedFeatures.kt").asFile
        target.parentFile.mkdirs()
        val constructors = enabledFeatures.sorted().mapNotNull { featurePlan.getValue(it)["binding"] as? String }.joinToString(",\n") { "$it()" }
        target.writeText("package io.newsbrief.app.runtime\n\nobject GeneratedFeatures {\nfun create(): List<AppFeature> = listOf(\n$constructors\n)\n}\n")
    }
}
android.sourceSets.getByName("main").java.srcDir(registrationDir)
tasks.matching { it.name.startsWith("compile") || it.name.startsWith("ksp") }.configureEach { dependsOn(generateFeatureRegistration, stageFeatureSources) }

dependencies {
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("org.jsoup:jsoup:1.21.2")
    implementation("androidx.room:room-common:2.8.5")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
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
