check(file("features/networking.gradle.kts").isFile) { "Sources requires networking transport" }
dependencies {
    add("implementation", "org.jsoup:jsoup:1.21.2")
    add("implementation", "androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
}
