plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

dependencies {
    implementation(project(":core:api"))
    implementation("androidx.work:work-runtime-ktx:2.12.0")
    testImplementation(project(":core:testing"))
    testImplementation(project(":core:runtime"))
}
