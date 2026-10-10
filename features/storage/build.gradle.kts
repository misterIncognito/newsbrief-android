plugins {
    id("com.google.devtools.ksp")
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

ksp { arg("room.schemaLocation", "$projectDir/schemas") }

dependencies {
    implementation(project(":core:api"))
    implementation("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")
    testImplementation("androidx.room:room-testing:2.8.5")
    testImplementation(project(":core:testing"))
    testImplementation(project(":core:runtime"))
}
