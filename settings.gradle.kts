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
val available = specs.keys.filter { file("features/$it/build.gradle.kts").isFile }.toSet()
val requested = providers.gradleProperty("newsbriefFeatures").orNull
val selected = (requested?.split(',')?.map { it.trim() }?.filter { it.isNotBlank() }
    ?: available.filter { it != "app-integration" }).toMutableSet()
require(selected.all { it in available && it != "app-integration" }) { "Unavailable feature selection: $selected" }
var changed: Boolean
do {
    val previous = selected.size
    selected.toList().forEach { name ->
        val required = specs.getValue(name)["requires"] as List<String>
        require(required.all { it in available }) { "$name requires missing modules $required" }
        selected.addAll(required)
    }
    changed = previous != selected.size
} while (changed)
val testModules = if ("app-integration" in available &&
    (specs.getValue("app-integration")["testRequires"] as List<String>).all { it in selected }) setOf("app-integration") else emptySet()
(selected + testModules).forEach { name -> include(":features:$name") }
gradle.extra["newsbriefFeatures"] = selected.sorted()
gradle.extra["newsbriefTestModules"] = testModules.sorted()
