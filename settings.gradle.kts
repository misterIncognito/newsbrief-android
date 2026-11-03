import groovy.json.JsonSlurper

pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "NewsBrief"
include(":app", ":core:api", ":core:runtime", ":core:testing")
@Suppress("UNCHECKED_CAST")
val specs = (JsonSlurper().parse(file("tools/features.json")) as Map<String, Any>)["features"] as Map<String, Map<String, Any>>
// An assembly opts in explicitly. Merely checking out or merging a feature never adds it.
val requested = providers.gradleProperty("newsbriefFeatures").orNull
    ?: file("assembly.features").readText().lineSequence()
        .map { it.substringBefore('#').trim() }.filter { it.isNotEmpty() }.joinToString(",")
val selected = requested.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
require(selected.all { it in specs && it != "app-integration" }) { "Unknown feature selection: $selected" }
selected.forEach { name ->
    require(file("features/$name/build.gradle.kts").isFile) { "Selected feature '$name' is not present in this checkout" }
    val required = specs.getValue(name)["requires"] as List<String>
    val missing = required.filterNot { it in selected }
    require(missing.isEmpty()) { "Feature '$name' requires explicit selection of: $missing" }
}
val testModules = if (providers.gradleProperty("newsbriefIntegrationTests").orNull == "true") {
    require(file("features/app-integration/build.gradle.kts").isFile) { "Integration test module is not present" }
    val required = specs.getValue("app-integration")["testRequires"] as List<String>
    require(required.all { it in selected }) { "Integration tests require selected features: ${required.filterNot { it in selected }}" }
    setOf("app-integration")
} else emptySet()
(selected + testModules).forEach { name -> include(":features:$name") }
gradle.extra["newsbriefFeatures"] = selected.sorted()
gradle.extra["newsbriefTestModules"] = testModules.sorted()
