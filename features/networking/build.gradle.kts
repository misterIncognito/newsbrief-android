plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

dependencies {
    implementation(project(":core:api"))
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    testImplementation(project(":core:testing"))
    testImplementation(project(":core:runtime"))
}
