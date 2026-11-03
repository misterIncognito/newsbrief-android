plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation(project(":core:api"))
    implementation("org.jsoup:jsoup:1.21.2")
    testImplementation(project(":core:testing"))
    testImplementation(project(":core:runtime"))
    testImplementation(project(":features:networking"))
}
