plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android { defaultConfig { consumerProguardFiles("consumer-rules.pro") } }
dependencies {
    api(platform("androidx.compose:compose-bom:2025.08.01"))
    api("androidx.compose.ui:ui")
    api("androidx.compose.foundation:foundation")
    api("androidx.compose.material3:material3")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    api("androidx.room:room-common:2.8.5")
    api("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("org.jsoup:jsoup:1.21.2")
}
