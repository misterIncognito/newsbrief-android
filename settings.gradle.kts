import java.util.Properties

pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "NewsBrief"
include(":app", ":core:api", ":core:runtime", ":core:testing")
// An assembly opts in explicitly. Merely checking out or merging a feature never adds it.
val requested = providers.gradleProperty("newsbriefFeatures").orNull
    ?: file("assembly.features").readText().lineSequence()
        .map { it.substringBefore('#').trim() }.filter { it.isNotEmpty() }.joinToString(",")
val selected = requested.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
require(selected.all { it.matches(Regex("[a-z0-9]+(?:-[a-z0-9]+)*")) && it != "app-integration" }) { "Invalid feature selection: $selected" }
fun prerequisites(name: String, key: String): List<String> {
    val descriptor = file("features/$name/feature.properties")
    require(descriptor.isFile) { "Feature '$name' has no feature.properties descriptor" }
    val properties = Properties().apply { descriptor.inputStream().use { load(it) } }
    require(properties.containsKey(key)) { "Feature '$name' descriptor is missing $key" }
    return properties.getProperty(key).split(',').map { it.trim() }.filter { it.isNotEmpty() }
}
selected.forEach { name ->
    require(file("features/$name/build.gradle.kts").isFile) { "Selected feature '$name' is not present in this checkout" }
    val required = prerequisites(name, "requires")
    val missing = required.filterNot { it in selected }
    require(missing.isEmpty()) { "Feature '$name' requires explicit selection of: $missing" }
}
val testModules = if (providers.gradleProperty("newsbriefIntegrationTests").orNull == "true") {
    require(file("features/app-integration/build.gradle.kts").isFile) { "Integration test module is not present" }
    val required = prerequisites("app-integration", "testRequires")
    require(required.all { it in selected }) { "Integration tests require selected features: ${required.filterNot { it in selected }}" }
    setOf("app-integration")
} else emptySet()
(selected + testModules).forEach { name -> include(":features:$name") }
gradle.extra["newsbriefFeatures"] = selected.sorted()
gradle.extra["newsbriefTestModules"] = testModules.sorted()
