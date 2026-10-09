listOf("reader-browser", "sources-discovery").forEach {
    check(file("features/$it.gradle.kts").isFile) { "Article plugins require $it" }
}
