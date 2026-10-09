// Full-app test dependencies only. Runtime composition never requires this feature.
dependencies {
    add("androidTestImplementation", platform("androidx.compose:compose-bom:2025.08.01"))
    add("androidTestImplementation", "androidx.compose.ui:ui-test-junit4")
    add("androidTestImplementation", "androidx.test.ext:junit:1.3.0")
}
