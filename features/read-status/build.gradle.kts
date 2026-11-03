plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

dependencies {
    implementation(project(":core:api"))
    implementation("androidx.compose.material:material-icons-core")
    testImplementation(project(":core:testing"))
    testImplementation(project(":core:runtime"))
}
