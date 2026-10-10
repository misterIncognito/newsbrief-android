plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

dependencies {
    implementation(project(":core:api"))
    testImplementation(project(":core:testing"))
    testImplementation(project(":core:runtime"))
}
