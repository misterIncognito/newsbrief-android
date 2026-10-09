check(file("features/networking.gradle.kts").isFile) { "Storage requires feature/networking" }
dependencies {
    add("implementation", "org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    add("implementation", "androidx.room:room-runtime:2.8.5")
    add("implementation", "androidx.room:room-ktx:2.8.5")
    add("ksp", "androidx.room:room-compiler:2.8.5")
    add("testImplementation", "org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    add("testImplementation", "androidx.room:room-testing:2.8.5")
}
