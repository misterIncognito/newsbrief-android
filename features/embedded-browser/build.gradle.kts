plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

dependencies {
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("org.jsoup:jsoup:1.21.2")
    implementation(project(":core:api"))
    testImplementation(project(":core:testing"))
    testImplementation(project(":core:runtime"))
    testImplementation(project(":features:networking"))
}
