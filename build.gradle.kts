import com.android.build.gradle.LibraryExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

plugins {
    id("com.android.library") version "8.13.2" apply false
    id("com.android.application") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "2.3.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.20" apply false
    id("com.google.devtools.ksp") version "2.3.10" apply false
}

subprojects {
    pluginManager.withPlugin("org.jetbrains.kotlin.plugin.compose") {
        if (pluginManager.hasPlugin("com.android.library")) {
            extensions.configure<LibraryExtension> {
                namespace = "io.newsbrief.modules." + project.path.trim(':').replace(':', '.').replace('-', '_')
                compileSdk = 36
                defaultConfig { minSdk = 26 }
                buildFeatures { compose = true }
                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_17
                    targetCompatibility = JavaVersion.VERSION_17
                }
                testOptions { unitTests.isIncludeAndroidResources = true }
            }
            extensions.configure<KotlinAndroidProjectExtension> {
                compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
            }
            dependencies {
                add("testImplementation", "junit:junit:4.13.2")
                add("testImplementation", "org.robolectric:robolectric:4.16.1")
                add("testImplementation", "androidx.test:core:1.7.0")
                add("testImplementation", "org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
                add("testImplementation", platform("androidx.compose:compose-bom:2025.08.01"))
                add("testImplementation", "androidx.compose.ui:ui-test-junit4")
                add("debugImplementation", "androidx.compose.ui:ui-test-manifest")
            }
            tasks.withType<Test>().configureEach {
                maxParallelForks = 1
                if (!providers.gradleProperty("verifyDirectoryServices").isPresent) exclude("**/DirectoryServiceLiveTest*")
                if (!providers.gradleProperty("verifyAmpReaders").isPresent) exclude("**/AmpReaderLiveTest*")
            }
        }
    }
}

// Enforce the actual resolved compiler graph, in addition to static source ownership.
tasks.register("verifyModuleInterfaces") {
    doLast {
        val graph = sortedMapOf<String, List<String>>()
        subprojects.filter { it.path.startsWith(":features:") }.forEach { module ->
            val references = module.configurations.getByName("debugCompileClasspath").incoming.resolutionResult
                .allComponents.mapNotNull { component ->
                    (component.id as? org.gradle.api.artifacts.component.ProjectComponentIdentifier)?.projectPath
                }.filter { it != module.path }.distinct().sorted()
            check(references == listOf(":core:api")) { "${module.path} imports implementations: $references" }
            graph[module.path] = references
        }
        val output = layout.buildDirectory.file("reports/module-interfaces.json").get().asFile
        output.parentFile.mkdirs()
        output.writeText(graph.entries.joinToString(",\n", "{\n", "\n}\n") { (name, refs) ->
            "  \"$name\": [" + refs.joinToString(", ") { "\"$it\"" } + "]"
        })
        println("Verified ${graph.size} feature libraries compile against shared interfaces only.")
    }
}
