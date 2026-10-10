package io.newsbrief.app.features

import androidx.compose.runtime.Composable
import io.newsbrief.app.runtime.AppFeature
import io.newsbrief.app.ui.*

class SourceManagementFeature : AppFeature {
    override val id = "source-management"
    override val title = "Источники"
    override val order = 20
    @Composable override fun Screen(vm: AppUi) { SourcesPage(vm) }
}
