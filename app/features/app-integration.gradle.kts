listOf("networking", "storage", "sources-discovery", "reader-browser", "synchronization", "feed", "ai", "article-plugins").forEach {
    check(file("features/$it.gradle.kts").isFile) { "App integration requires $it" }
}
dependencies {
    add("implementation", "androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    add("androidTestImplementation", platform("androidx.compose:compose-bom:2025.08.01"))
    add("androidTestImplementation", "androidx.compose.ui:ui-test-junit4")
    add("androidTestImplementation", "androidx.test.ext:junit:1.3.0")
}
