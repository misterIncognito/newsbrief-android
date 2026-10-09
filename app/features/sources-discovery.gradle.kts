check(file("features/storage.gradle.kts").isFile) { "Sources/discovery requires feature/storage (which includes networking)" }
dependencies { add("implementation", "org.jsoup:jsoup:1.21.2") }
