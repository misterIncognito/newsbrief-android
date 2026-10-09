check(file("features/reader-browser.gradle.kts").isFile) { "Synchronization/app integration requires feature/reader-browser" }
dependencies {
    add("implementation", "androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    add("implementation", "androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    add("implementation", "androidx.work:work-runtime-ktx:2.12.0")
    add("androidTestImplementation", platform("androidx.compose:compose-bom:2025.08.01"))
    add("androidTestImplementation", "androidx.compose.ui:ui-test-junit4")
    add("androidTestImplementation", "androidx.test.ext:junit:1.3.0")
}
