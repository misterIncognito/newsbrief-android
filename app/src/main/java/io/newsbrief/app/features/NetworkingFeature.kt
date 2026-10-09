package io.newsbrief.app.features

import androidx.compose.runtime.Composable
import io.newsbrief.app.runtime.AppFeature
import io.newsbrief.app.ui.*

class NetworkingFeature : AppFeature {
    override val id = "networking"
    @Composable override fun Settings(vm: NewsViewModel) { WorkerSettingsSection(vm) }
}
